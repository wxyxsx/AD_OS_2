package com.wxy.netdisk;

import java.io.*;
import java.math.BigInteger;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Scanner;

public class ToolKits {
    private String fname;
    // 对于服务端是文件名，对于客户端是文件的绝对路径
    private long flen;
    // 文件长度，大部分情况下int足够

    public String getFname() {
        return fname;
    }

    public long getFlen() {
        return flen;
    }

    public String getFhash() {
        return fhash;
    }

    public int getPerburden() {
        return perburden;
    }

    public int getLastburden() {
        return lastburden;
    }

    public boolean isStop() {
        return stop;
    }

    public boolean isFlag() {
        return flag;
    }

    public int getStatus(int i) {
        return status[i];
    }

    private String fhash;
    // 文件的md5值
    private int perburden;
    // 每个线程平均要发送的块数
    private int lastburden;
    // 最后一个线程发送的文件块数
    private boolean stop;
    // 暂停标志
    private boolean flag;
    // true表示正在发送，false表示正在接收，在创建tool时就已经处理好了
    private int[] status;
    // 记录每个线程已经接收的块数

    public void setStop(boolean stop) {
        this.stop = stop;
    }

    // unused
    public void setFlag(boolean flag) {
        this.flag = flag;
    }

    public void setStatus(int i, int data) {
        status[i] = data;
    }

    private int tnum;
    // 线程的数目 todo 协商线程的数量
    private int bfsize;
    // 每次读入文件/发送数据的大小

    private final static String prefix = ".";
    // 正在上传或下载的文件
    private final static String suffix = ".tmp";
    // 记录进度的文件

    private int finish;
    // 供服务端用检查线程是否运行结束
    // todo 可以extends两个类

    public boolean isfinish() {
        return finish == tnum;
    }

    public void addfinish() {
        finish++;
    }

    public ToolKits() {
        finish = 0;
        stop = false;
        tnum = 4;
        bfsize = 1024;
        status = new int[tnum];
    }

    public void Sender(String filename) {
        fname = filename;
        File f = new File(fname);
        flen = f.length();
        fhash = genmd5();
        flag = true;
        genburden();
    }

    // 客户端使用 Sender不需要拆分，不过为了方便阅读
    public void Sender(String rlpath, boolean sign) {
        // 不影响getFname，因为它只在服务端使用
        fname = rlpath;
        File f = new File(fname);
        flen = f.length();
        fhash = genmd5();
        flag = true;
        genburden();
    }

    // sender no是线程的编号，offset是分块中已经下载的文件量
    public RandomAccessFile GetInput(int no, int offset) {
        RandomAccessFile rf = null;
        try {
            rf = new RandomAccessFile(fname, "r");
            rf.seek((long) (no * perburden + offset) * bfsize);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return rf;
    }

    // receiver
    public RandomAccessFile GetOutput(int no, int offset) {
        RandomAccessFile rf = null;
        try {
            rf = new RandomAccessFile(fname, "rw");
            rf.seek((long) (no * perburden + offset) * bfsize);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return rf;
    }

    // for server
    public void Receriver(String filename, Long filelen, String filehash) {
        fname = prefix + filename;
        flen = filelen;
        fhash = filehash;
        flag = false;
        genburden();
        readstatus();
    }

    // for client
    public void Receriver(String filepath, Long filelen, String filehash, boolean sign) {
        Path path = Paths.get(filepath);
        fname = path.getParent().toString()
                + File.separator
                + prefix
                + path.getFileName().toString();
        // 为了给文件名前加prefix需要拆分路径
        flen = filelen;
        fhash = filehash;
        flag = false;
        genburden();
        readstatus();
    }

    // receiver
    private void readstatus() {
        File f = new File(fname + suffix);
        if (f.exists()) {
            // 如果文件存在则表示有保存的记录
            try {
                Scanner input = new Scanner(f);
                for (int i = 0; i < tnum; i++) {
                    status[i] = input.nextInt();
                }
                input.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        } else {
            // 如果文件不存在则创建空文件
            emptyfile();
            for (int i = 0; i < tnum; i++) {
                status[i] = 0;
            }
        }
    }

    // receiver 创建flen大小的空文件
    private void emptyfile() {
        try {
            RandomAccessFile rfile = new RandomAccessFile(fname, "rw");
            rfile.setLength(flen);
            rfile.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //receiver 保存记录断点的文件
    public void SaveStatus() {
        try {
            PrintWriter output = new PrintWriter(fname + suffix);
            for (int i : status) {
                output.println(i);
            }
            output.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    //receiver 校验文件的md5与接收的md5值是否相等
    public boolean CheckMd5() {
        return fhash.equals(genmd5());
    }

    //receiver 如果存在则删除tmp文件
    public void deltmp() {
        File f = new File(fname + suffix);
        if (f.exists()) {
            f.delete();
        }
    }

    // server 删除文件名中的prefix
    public void rename() {
        File f = new File(fname);
        File nf = new File(fname.substring(1));
        f.renameTo(nf);
    }

    // client 客户端删除prefix时要做拆分
    public void rename(boolean sign) {
        File f = new File(fname);
        Path path = Paths.get(fname);
        String str = path.getParent().toString()
                + File.separator
                + path.getFileName().toString().substring(1);
        File nf = new File(str);
        f.renameTo(nf);
    }

    // 根据文件大小，线程数和发送块的大小做任务拆分
    private void genburden() {
        int total = (int) (flen / bfsize);
        if (flen % bfsize != 0) total++;
        perburden = total / tnum;
        lastburden = perburden + total % tnum;
    }

    // 从网上找的代码
    private String genmd5() {
        File f = new File(fname);
        MessageDigest digest = null;
        FileInputStream in;
        byte[] buffer = new byte[1024];
        int len;
        try {
            digest = MessageDigest.getInstance("MD5");
            in = new FileInputStream(f);
            while ((len = in.read(buffer, 0, 1024)) != -1) {
                digest.update(buffer, 0, len);
            }
            in.close();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        BigInteger bigint = new BigInteger(1, digest.digest());
        return bigint.toString(16);
    }
}