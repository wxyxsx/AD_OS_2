package com.wxy.ados2;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class Server {
    private Logger log = Logger.getLogger(Server.class.getName());
    private ToolKits[] tool;
    private boolean[] pool;
    private int curid;
    private int port;
    private int tnum;
    private int bfsize;

    private final static int MAXUSER = 10;

    public Server() {
        curid = -1;
        port = 8000;
        tnum = 4;
        bfsize = 1024;
        tool = new ToolKits[MAXUSER];
        pool = new boolean[MAXUSER];
        for (int i = 0; i < MAXUSER; i++) {
            pool[i] = false;
        }
    }

    private void nextid() {
        curid = -1;
        for (int i = 0; i < MAXUSER; i++) {
            if (!pool[i]) {
                curid = i;
                break;
            }
        }
    }

    //for list
    private String explen(Long len) {
        Long size = len;
        String sign = "B";
        for (int i = 0; i < 3; i++) {
            if (size < 1024) {
                break;
            }
            size = size >> 10;
            if (i == 0) sign = "KB";
            else if (i == 1) sign = "MB";
            else if (i == 2) sign = "GB";
        }
        return size + sign;
    }

    public void Start() {
        try {
            ServerSocket ss = new ServerSocket(port);
            log.info(String.format("[SYSTEM] The server listens on port %d", port));
            while (true) {
                Socket s = ss.accept();
                int ctl = new DataInputStream(s.getInputStream()).readInt();
                // user identification
                if (ctl == -1) {
                    nextid();
                    if (curid == -1) {
                        log.info(String.format("[SYSTEM] System can only solve %d users ", MAXUSER));
                        // full
                        s.close();
                        continue;
                    }
                    log.info(String.format("[%d] New user connected to the system", curid));
                    pool[curid] = true;
                    new DataOutputStream(s.getOutputStream()).writeInt(curid);
                    new Thread(new handleuser(s, curid)).start();
                } else if (pool[ctl] == true) {
                    new Thread(new handlethread(s, ctl)).start();
                    // todo update github
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    class handlethread implements Runnable {
        private Socket socket;
        private DataInputStream input;
        private DataOutputStream output;
        private int session;

        public handlethread(Socket socket, int session) {
            this.socket = socket;
            this.session = session;
        }

        public void run() {
            try {
                input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                output = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
                log.info(String.format("[%d] New thread connected to the system", session));
                boolean flag = tool[session].isFlag();
                if (flag == true) {
                    send();
                } else if (flag == false) {
                    receive();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void send() {
            int cur = 0, i = 0, no = 0, offset = 0;
            try {
                no = input.readInt();
                offset = input.readInt();

                if (no != tnum - 1) {
                    cur = tool[session].getPerburden();
                } else {
                    cur = tool[session].getLastburden();
                }
                if (offset == cur) {
                    input.close();
                    return;
                } // Will not trigger ,check on the client side

                RandomAccessFile fin = tool[session].GetInput(no, offset);
                byte[] buffer = new byte[bfsize];
                for (i = 0; i < (cur - offset); i++) {
                    if (tool[session].isStop()) break;
                    int read = fin.read(buffer);
                    if (read == -1) break;
                    output.write(buffer, 0, read);
                    output.flush();
                }

                fin.close();

            } catch (IOException e) {
                e.printStackTrace();
            }
            log.info(String.format("[%d,%d] Send progress %d/%d", session, no, i + offset, cur));
        }

        private void receive() {
            int no = 0, offset = 0, cur = 0, i = 0;
            long total = 0, sum = 0;
            try {
                no = input.readInt(); // 获取文件块的序号
                offset = tool[session].getStatus(no); // 获取保存的进度
                output.writeInt(offset); // 发送给客户端
                output.flush();


                if (no != tnum - 1) {
                    cur = tool[session].getPerburden();
                    total = cur * bfsize;
                } else {
                    cur = tool[session].getLastburden();
                    total = tool[session].getFlen() - (tnum - 1) * tool[session].getPerburden() * bfsize;
                }
                if (offset == cur) {
                    // 如果文件块完成
                    log.info(String.format("[%d,%d] File blocks do not need to be uploaded", session, no));
                    input.close();
                    return;
                }
                sum = ((long) offset) * bfsize;

                RandomAccessFile fout = tool[session].GetOutput(no, offset);
                byte[] buffer = new byte[bfsize];
                while (sum < total) {
                    if (tool[session].isStop()) break;
                    int read = input.read(buffer);
                    if (read == -1) break;
                    // System.out.printf("[%d,%d,%d]test \n",no,sum,total);
                    sum = sum + read;
                    fout.write(buffer, 0, read);
                }

                fout.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            // 防止文件出错
            tool[session].addfinish();
            if (sum == total) {
                tool[session].setStatus(no, cur);
            } else {
                tool[session].setStatus(no, (int) (sum / bfsize));
            }
            log.info(String.format("[%d,%d] Receive progress %d/%d", session, no, i + offset, cur));
        }
    }

    class handleuser implements Runnable {
        private Socket socket;
        private DataInputStream input;
        private DataOutputStream output;
        private int session;

        public handleuser(Socket socket, int session) {
            this.session = session;
            this.socket = socket;
        }

        public void run() {
            try {
                input = new DataInputStream(socket.getInputStream());
                output = new DataOutputStream(socket.getOutputStream());

                while (true) {
                    tool[session] = null;
                    if (socket.isClosed()) {
                        pool[session] = false;
                        log.info(String.format("[%d] User exit", session));
                        break;
                    }
                    int op = input.readInt();
                    // operation id
                    switch (op) {
                        case 0:
                            tool[session] = new ToolKits();
                            send();
                            break;
                        case 1:
                            tool[session] = new ToolKits();
                            receive();
                            break;
                        case 2:
                            //close session
                            input.close();
                            break;
                        case 3:
                            list();
                            break;
                        case 4:
                            delete();
                            break;
                        case 5:
                            rename();
                            break;
                        default:
                            //rename delete list close

                    }

                }
            } catch (IOException e) {
                pool[session] = false;
                log.info(String.format("[%d] User exit -1", session));
                e.printStackTrace();
            }
        }

        private void send() {
            try {
                String fname = input.readUTF();
                tool[session].Sender(fname);

                log.info(String.format("[%d] request to download file %s", session, fname));

                output.writeLong(tool[session].getFlen());
                output.writeUTF(tool[session].getFhash());

                int r = input.readInt();
                // signal
                if (r == 1) {
                    log.info(String.format("[%d] successfully downloaded file %s", session, fname));
                } else if (r == 0) {
                    tool[session].setStop(true);
                    log.info(String.format("[%d] pauses downloading files %s", session, fname));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            // socket.close()
        }

        private void receive() {
            try {
                String fname = input.readUTF();
                long flen = input.readLong();
                String fhash = input.readUTF();

                log.info(String.format("[%d] request to upload file %s", session, fname));

                tool[session].Receriver(fname, flen, fhash);
                output.writeInt(1);
                //ready

                int r = input.readInt();
                if (r == 1) {
                    log.info(String.format("[%d] already upload file %s", session, fname));
                    // 需要等待其它线程结束才能验证
                    while (!tool[session].isfinish()) {
                        Thread.sleep(300);
                    }
                    if (tool[session].CheckMd5()) {
                        tool[session].deltmp();
                        tool[session].rename();
                        log.info(String.format("[%d] File %s verification succeeded", session, fname));
                    } else {
                        log.info(String.format("[%d] File %s verification failed", session, fname));
                    }
                } else if (r == 0) {
                    tool[session].setStop(true);
                    while (!tool[session].isfinish()) {
                        Thread.sleep(300);
                    } // waiting for threads 可以加个tool中的全局变量判断是否所有线程都结束了
                    tool[session].SaveStatus();
                    log.info(String.format("[%d] pauses uploading files %s", session, fname));
                }

            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        private void list() {
            log.info(String.format("[%d] request to view the file list", session));
            File file = new File(".");
            File[] flist = file.listFiles();
            List<String> name = new ArrayList<String>();
            List<String> length = new ArrayList<String>();
            for (File f : flist) {
                // 忽略以.开头，忽略文件夹
                if (!f.getName().startsWith(".") && !f.isDirectory()) {
                    name.add(f.getName());
                    length.add(explen(f.length()));
                }
            }
            try {
                output.writeInt(name.size());
                for (int i = 0; i < name.size(); i++) {
                    output.writeUTF(name.get(i));
                    output.writeUTF(length.get(i));
                }
                int r = input.readInt();
                if (r == 1) {
                    log.info(String.format("[%d] Successfully transferred file list", session));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private boolean ckfstate(String fname) {
            boolean flag = true;
            for (int i = 0; i < MAXUSER; i++) {
                // 正在接收的文件是不会被客户端看到的，正在发送的文件不能被用户重命名或是删除
                if (pool[i] && tool[i] != null && tool[i].isFlag()) {
                    if (fname.equals(tool[i].getFname())) {
                        flag = false;
                        break;
                    }
                }
            }
            return flag;
        }

        private void delete() {
            try {
                String fname = input.readUTF();
                File f = new File(fname);
                if (!f.exists()) { // 如果文件不存在
                    output.writeInt(0);
                    log.info(String.format("[%d] File do not exist %s", session, fname));
                } else if (!ckfstate(fname)) { // 如果文件不是正在下载
                    output.writeInt(0);
                    log.info(String.format("[%d] Some users are downloading files %s", session, fname));
                } else if (!f.delete()) { // 如果文件未能正常删除
                    output.writeInt(0);
                    log.info(String.format("[%d] Failed deleting file %s", session, fname));
                } else {
                    output.writeInt(1);
                    log.info(String.format("[%d] Successfully delete file %s", session, fname));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void rename() {
            try {
                String oldn = input.readUTF();
                String newn = input.readUTF();
                File fo = new File(oldn);
                File fn = new File(newn);
                if (!fo.exists()) { //旧文件必须存在
                    output.writeInt(0);
                    log.info(String.format("[%d] Old named file do not exist %s", session, oldn));
                } else if (fn.exists()) { //新文件不能存在
                    output.writeInt(0);
                    log.info(String.format("[%d] New named file exist %s", session, newn));
                } else if (!ckfstate(oldn)) { //新文件不存在也不可能被使用
                    output.writeInt(0);
                    log.info(String.format("[%d] Some users are downloading files %s", session, oldn));
                } else if(!fo.renameTo(fn)){
                    output.writeInt(0);
                    log.info(String.format("[%d] Failed rename file %s to %s", session, oldn,newn));
                } else {
                    output.writeInt(1);
                    log.info(String.format("[%d] Successfully rename file %s to %s", session, oldn,newn));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String args[]) {
        Server s = new Server();
        s.Start();
    }
}
