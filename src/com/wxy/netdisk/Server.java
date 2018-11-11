package com.wxy.netdisk;

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
    private int port;
    private int tnum;
    private int bfsize;

    private final static int MAXUSER = 10;

    public Server() {
        port = 8000;
        tnum = 4;
        bfsize = 1024;
        tool = new ToolKits[MAXUSER];
        pool = new boolean[MAXUSER];
        for (int i = 0; i < MAXUSER; i++) {
            pool[i] = false;
        }
    }

    // 找出下一未使用的id
    private int nextid() {
        int curid = -1;
        for (int i = 0; i < MAXUSER; i++) {
            if (!pool[i]) {
                curid = i;
                break;
            }
        }
        return curid;
    }

    // 把文件长度转化为更直观的形式
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
            log.info(String.format("[系统信息] 服务器正在监听端口 %d", port));

            while (true) {
                Socket s = ss.accept();
                int ctl = new DataInputStream(s.getInputStream()).readInt();
                // 获取用户发送的标识
                if (ctl == -1) {
                    int curid = nextid();
                    new DataOutputStream(s.getOutputStream()).writeInt(curid); // 向用户发送它的标识

                    if (curid == -1) {
                        log.info(String.format("[系统信息] 系统无法处理超过 %d 个用户", MAXUSER));
                        // 表示nextid()找不到空的标识，服务人数已满
                        s.close();
                        continue;
                    }
                    log.info(String.format("[用户%d] 新的用户建立连接", curid));
                    pool[curid] = true; // pool中标记标识已经被使用

                    new Thread(new handleuser(s, curid)).start();
                } else if (pool[ctl] == true) {
                    new Thread(new handlethread(s, ctl)).start();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 处理线程
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
                boolean flag = tool[session].isFlag(); // 判断是发送还是接收（由控制信道确定）
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
                log.info(String.format("[%d，%d] 新的线程连接系统，请求数据%d/%d", session, no, offset, cur));
                if (offset == cur) {
                    input.close();
                    return;
                } // 这句条件判断不会触发，因为客户端已经做出了判断

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
            log.info(String.format("[%d,%d] 线程运行结束，发送进度 %d/%d", session, no, i + offset, cur));
        }

        private void receive() {
            int no = 0, offset = 0, cur = 0, i = 0;
            long total = 0, sum = 0;
            RandomAccessFile fout = null;
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
                if (offset == cur) { // 如果文件块完成
                    tool[session].addfinish(); //还是要告诉finish此线程已经结束
                    log.info(String.format("[%d,%d] 文件块不需要上传", session, no));
                    input.close();
                    return;
                }

                log.info(String.format("[%d，%d] 新的线程连接系统，发送数据%d/%d", session, no, offset, cur));

                sum = ((long) offset) * bfsize;
                fout = tool[session].GetOutput(no, offset);
                byte[] buffer = new byte[bfsize];
                while (sum < total) {
                    if (tool[session].isStop()) break;
                    int read = input.read(buffer);
                    if (read == -1) break;
                    sum = sum + read;
                    fout.write(buffer, 0, read);
                }
                input.close();
                // 始终是接收方关闭通道
            } catch (IOException e) {
                e.printStackTrace();
            }
            // 防止暂停时程序在read=input.read上出错，所以部分操作需要放在try-catch以外
            try {
                fout.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            tool[session].addfinish();
            if (sum == total) {
                tool[session].setStatus(no, cur);
            } else {
                tool[session].setStatus(no, (int) (sum / bfsize));
            }
            log.info(String.format("[%d,%d] 线程运行结束，接收进度 %d/%d", session, no, i + offset, cur));
        }
    }

    // 处理用户
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
                    // 每个session占用一个tool，同时每次操作都用新的tool
                    if (socket.isClosed()) {
                        pool[session] = false; // 如果用户退出
                        log.info(String.format("[用户%d] 退出系统", session));
                        break;
                    }
                    int op = input.readInt();
                    // 获取操作ID
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
                            // 用户退出
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

                    }

                }
            } catch (IOException e) {
                pool[session] = false;
                // 出现错误同样要回收 是不是应该放在finally中？
                log.info(String.format("[用户%d] 异常退出", session));
                e.printStackTrace();
            }
        }

        private void send() {
            try {
                String fname = input.readUTF();
                tool[session].Sender(fname);

                log.info(String.format("[用户%d] 请求下载文件 %s", session, fname));

                output.writeLong(tool[session].getFlen());
                output.writeUTF(tool[session].getFhash());

                int r = input.readInt();
                // 获取信号
                if (r == 1) {
                    log.info(String.format("[用户%d] 成功下载文件 %s", session, fname));
                } else if (r == 0) {
                    // 用户发出暂停命令
                    tool[session].setStop(true);
                    log.info(String.format("[用户%d] 暂停下载文件 %s", session, fname));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void receive() {
            try {
                String fname = input.readUTF();
                long flen = input.readLong();
                String fhash = input.readUTF();

                log.info(String.format("[用户%d] 请求上传文件 %s", session, fname));

                tool[session].Receriver(fname, flen, fhash);
                output.writeInt(1);
                // 提醒客户端 服务端已经准备接收

                int r = input.readInt();
                if (r == 1) {
                    log.info(String.format("[用户%d] 已经上传文件，等待验证 %s", session, fname));
                    // 需要等待其它线程结束才能验证
                    while (!tool[session].isfinish()) {
                        Thread.sleep(300);
                    }
                    if (tool[session].CheckMd5()) {
                        tool[session].deltmp();
                        tool[session].rename();
                        log.info(String.format("[用户%d] 文件 %s 验证成功", session, fname));
                    } else {
                        log.info(String.format("[用户%d] 文件 %s 验证失败", session, fname));
                    }
                } else if (r == 0) {
                    tool[session].setStop(true);
                    while (!tool[session].isfinish()) {
                        Thread.sleep(300);
                    }
                    tool[session].SaveStatus();
                    log.info(String.format("[用户%d] 暂停上传文件 %s", session, fname));
                }

            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        private void list() {
            log.info(String.format("[用户%d] 请求文件列表", session));
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
                output.writeInt(name.size()); // 发送要接收的文件数量
                for (int i = 0; i < name.size(); i++) {
                    output.writeUTF(name.get(i));
                    output.writeUTF(length.get(i));
                }
                int r = input.readInt();
                if (r == 1) {
                    log.info(String.format("[用户%d] 成功接收文件列表", session));
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
                if (!f.exists()) {  // 如果文件不存在
                    output.writeInt(0);
                    log.info(String.format("[用户%d] 文件 %s 不存在", session, fname));
                } else if (!ckfstate(fname)) { // 如果文件不是正在下载
                    output.writeInt(0);
                    log.info(String.format("[用户%d] 文件 %s 正在被下载", session, fname));
                } else if (!f.delete()) { // 如果文件未能正常删除
                    output.writeInt(0);
                    log.info(String.format("[用户%d] 文件 %s 删除失败", session, fname));
                } else {
                    output.writeInt(1);
                    log.info(String.format("[用户%d] 成功删除文件 %s", session, fname));
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
                    log.info(String.format("[用户%d] 需要重命名的文件 %s 不存在", session, oldn));
                } else if (fn.exists()) { //新文件不能存在
                    output.writeInt(0);
                    log.info(String.format("[用户%d] 新文件名 %s 已经被使用", session, newn));
                } else if (!ckfstate(oldn)) { //新文件不存在也不可能被使用
                    output.writeInt(0);
                    log.info(String.format("[用户%d] 文件 %s 正在被下载", session, oldn));
                } else if (!fo.renameTo(fn)) {
                    output.writeInt(0);
                    log.info(String.format("[用户%d] 重命名 %s 为 %s 失败", session, oldn, newn));
                } else {
                    output.writeInt(1);
                    log.info(String.format("[用户%d] 重命名 %s 为 %s 成功", session, oldn, newn));
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
