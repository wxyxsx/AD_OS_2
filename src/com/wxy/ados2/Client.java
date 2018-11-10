package com.wxy.ados2;

import javafx.application.Platform;
import javafx.scene.control.TextArea;

import java.io.*;
import java.net.Socket;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

public class Client {
    //    private Logger log = Logger.getLogger(Client.class.getName());
    private ToolKits tool;
    private Socket socket;
    private int tnum;
    private int bfsize;
    private int session;
    private String addr;
    private int port;

    public boolean isBusy() {
        return busy;
    }

    public void setBusy(boolean busy) {
        this.busy = busy;
    }

    private boolean busy;

    public void setAddr(String addr) {
        this.addr = addr;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setFname(String s) {
        // fname依然只有文件名
        // fpath是文件真实位置
        Path path = Paths.get(s);
        this.fpath = s;
        this.fname = path.getFileName().toString();

    }

    private String fpath;
    private String fname;
    private Thread[] th;
    DataInputStream input;
    DataOutputStream output;

    private TextArea loga;
    List<String> name;
    List<String> length;

    public void setLoga(TextArea loga) {
        this.loga = loga;
    }

    public List<String> getName() {
        return name;
    }

    public List<String> getLength() {
        return length;
    }

    public Client(TextArea loga) {
        busy = false;
        this.loga = loga;
        tnum = 4;
        bfsize = 1024;
        session = -1;
        addr = "localhost";
        port = 8000;
    }

    public void Connect() {
        try {
            busy = true;
            socket = new Socket(addr, port);
            input = new DataInputStream(socket.getInputStream());
            output = new DataOutputStream(socket.getOutputStream());
            output.writeInt(session);
            if (socket.isClosed()) {
                Platform.runLater(() -> {
                    loga.appendText("服务器服务人数已满\n");
                });
                return;
            }
            session = input.readInt();
            Platform.runLater(() -> {
                loga.appendText("从服务器获得标识 " + session + "\n");
            });
            busy = false;

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void Close() {
        // ?
        try {
            if (socket.isClosed()) {
                return;
            }
            Platform.runLater(() -> {
                loga.appendText("断开服务器连接\n");
            });
            output.writeInt(2);
            output.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void Download() {
        try {
            busy = true;
            tool = new ToolKits();
            output.writeInt(0);
            output.writeUTF(fname);

            Long flen = input.readLong();
            String fhash = input.readUTF();
            tool.Receriver(fpath, flen, fhash, true);
            //todo
            th = new Thread[tnum];
            for (int i = 0; i < tnum; i++) {
                th[i] = new Thread(new receiver(i));
                th[i].start();
            }
            for (int i = 0; i < tnum; i++) {
                th[i].join();
            }
            if (tool.isStop()) {
                tool.SaveStatus();
                Platform.runLater(() -> {
                    loga.appendText(fname + " 文件下载暂停\n");
                });
            } else if (tool.CheckMd5()) {
                output.writeInt(1);
                Platform.runLater(() -> {
                    loga.appendText(fname + " 文件下载成功\n");
                });
                tool.deltmp();
                tool.rename(true);
            } else {
                output.writeInt(1);
                Platform.runLater(() -> {
                    loga.appendText(fname + " 下载文件校验错误\n");
                });
            }
            tool = null;
            busy = false;
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void Upload() {
        try {
            busy = true;
            tool = new ToolKits();
            output.writeInt(1);
            tool.Sender(fpath, true);
            //todo

            output.writeUTF(fname);
            output.writeLong(tool.getFlen());
            output.writeUTF(tool.getFhash());

            int tp = input.readInt();
            th = new Thread[tnum];
            for (int i = 0; i < tnum; i++) {
                th[i] = new Thread(new sender(i));
                th[i].start();
            }
            for (int i = 0; i < tnum; i++) {
                th[i].join();
            }
            Platform.runLater(() -> {
                loga.appendText(fname + " 文件上传成功\n");
            });
            if (!tool.isStop()) {
                output.writeInt(1);
            }
            tool = null;
            busy = false;
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void Stop() {
        if (busy == true) {
            Platform.runLater(() -> {
                loga.appendText("暂停上传/下载\n");
            });
            try {
                tool.setStop(true); //暂停
                output.writeInt(0); //向服务端发送暂停
                busy = false;
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            Platform.runLater(() -> {
                loga.appendText("没有操作需要暂停\n");
            });
        }
    }

    public void Listdir() {
        Platform.runLater(() -> {
            loga.appendText("请求文件列表\n");
        });
        name = new ArrayList<String>();
        length = new ArrayList<String>();
        try {
            busy = true;
            output.writeInt(3);
            int len = input.readInt();
            for (int i = 0; i < len; i++) {
                name.add(input.readUTF());
                length.add(input.readUTF());
            }
            output.writeInt(1);
            Platform.runLater(() -> {
                loga.appendText("文件列表刷新成功\n");
            });
            busy = false;
//            for(int i=0;i<name.size();i++){
//                System.out.println(name.get(i)+" "+length.get(i));
//            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void Delete(String name) {
        Platform.runLater(() -> {
            loga.appendText("请求删除文件 " + name + "\n");
        });
        try {
            busy = true;
            output.writeInt(4);
            output.writeUTF(name);
            int r = input.readInt();
            if (r == 1) {
                Platform.runLater(() -> {
                    loga.appendText("成功删除文件 " + name + "\n");
                });
            } else if (r == 0) {
                Platform.runLater(() -> {
                    loga.appendText("删除文件失败 " + name + "\n");
                });
            }
            busy = false;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void Rename(String oldn, String newn) {
        Platform.runLater(() -> {
            loga.appendText(String.format("请求重命名文件 %s 为 %s\n", oldn, newn));
        });
        try {
            busy = true;
            output.writeInt(5);
            output.writeUTF(oldn);
            output.writeUTF(newn);
            int r = input.readInt();
            if (r == 1) {
                Platform.runLater(() -> {
                    loga.appendText(String.format("重命名文件 %s 为 %s 成功\n", oldn, newn));
                });
            } else if (r == 0) {
                Platform.runLater(() -> {
                    loga.appendText(String.format("重命名文件 %s 为 %s 失败\n", oldn, newn));
                });
            }
            busy = false;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    class receiver implements Runnable {
        private int no;

        public receiver(int no) {
            this.no = no;
        }

        public void run() {

            int cur;
            long total;
            if (no != tnum - 1) {
                cur = tool.getPerburden();
                total = cur * bfsize;
            } else {
                cur = tool.getLastburden();
                total = tool.getFlen() - (tnum - 1) * tool.getPerburden() * bfsize;
            }
            int offset = tool.getStatus(no);

            Platform.runLater(() -> {
                loga.appendText(String.format("[%d,%d,%d]启动线程\n", no, offset, cur));
            });
            if (offset == cur) {
                Platform.runLater(() -> {
                    loga.appendText(String.format("第%d块文件不需要下载", no + "\n"));
                });
                return;
            }

            long sum = ((long) offset) * bfsize;

            try {
                Socket socket = new Socket(addr, port);
                DataInputStream tinput = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                DataOutputStream toutput = new DataOutputStream(socket.getOutputStream());
                toutput.writeInt(session);

                toutput.writeInt(no);
                toutput.writeInt(offset);
                RandomAccessFile fout = tool.GetOutput(no, offset);
                byte[] buffer = new byte[bfsize];
                int i = 0;
                while (sum < total) {
                    if (tool.isStop()) break;
                    int read = tinput.read(buffer); // 上一句结束了才变stop
                    if (read == -1) break;
                    sum = sum + read;
                    fout.write(buffer, 0, read);
                }
                fout.close();
                tinput.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (sum == total) {
                tool.setStatus(no, cur);
            } else {
                tool.setStatus(no, (int) (sum / bfsize));
            }
            int tp = tool.getStatus(no);
            Platform.runLater(() -> {
                loga.appendText(String.format("[%d] 文件块下载进度 %d/%d\n", no, tp, cur));
            });
        }
    }

    class sender implements Runnable {
        private int no;

        public sender(int no) {
            this.no = no;
        }

        public void run() {
            Platform.runLater(() -> {
                loga.appendText("启动线程 " + no + "\n");
            });
            Socket socket = null;
            int i = 0, offset = 0, cur = 0;
            try {
                socket = new Socket(addr, port);
                DataInputStream tinput = new DataInputStream(socket.getInputStream());
                DataOutputStream toutput = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
                toutput.writeInt(session);
                toutput.flush();
                toutput.writeInt(no);
                toutput.flush();
                offset = tinput.readInt();
                if (no != tnum - 1) {
                    cur = tool.getPerburden();
                } else {
                    cur = tool.getLastburden();
                }
                if (offset == cur) {
                    Platform.runLater(() -> {
                        loga.appendText(String.format("第%d块文件不需要上传\n", no));
                    });
                    tinput.close();
                }

                RandomAccessFile fin = tool.GetInput(no, offset);
                byte[] buffer = new byte[bfsize];
                for (i = 0; i < (cur - offset); i++) {
                    if (tool.isStop()) break;
                    int read = fin.read(buffer);
                    if (read == -1) break;
                    toutput.write(buffer, 0, read);
                    toutput.flush();
                }
                fin.close();
                toutput.close();

            } catch (IOException e) {
                e.printStackTrace();
            }
            int t1 = i + offset;
            int t2 = cur;
            Platform.runLater(() -> {
                loga.appendText(String.format("[%d] 上传进度 %d/%d\n", no, t1, t2));
            });
        }
    }

//    public static void main(String args[]) {
//        Client c = new Client();
//        c.Connect();
//        //c.Listdir();
//        //c.Delete("test.txt");
//        c.Rename("test.pdf","new.pdf");
//        //c.Upload();
//        c.Close();
//    }
}