package com.wxy.ados2;

import java.io.*;
import java.math.BigInteger;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Scanner;

public class ToolKits {
    private String fname;
    private long flen;

    // private String serverDir;

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
    private int perburden;
    private int lastburden;
    private boolean stop;
    private boolean flag;
    // true send false receive
    private int[] status;

    public void setStop(boolean stop) {
        this.stop = stop;
    }

    public void setFlag(boolean flag) {
        this.flag = flag;
    }

    public void setStatus(int i, int data) {
        status[i] = data;
    }

    private int tnum;
    private int bfsize;

    private final static String prefix = ".";
    private final static String suffix = ".tmp";

    private int finish;

    public boolean isfinish() {
        return finish == tnum;
    }

    public void addfinish() {
        finish++;
    }

    public ToolKits() {
        //serverDir = new File("").getPath();
        finish = 0;
        stop = false;
        tnum = 4;
        bfsize = 1024;
        status = new int[tnum];
    }

    public void Sender(String filename) {
        fname = filename;
        System.out.println(fname);
        File f = new File(fname);
        flen = f.length();
        fhash = genmd5();
        flag = true;
        genburden();
    }

    // for client
    public void Sender(String rlpath, boolean sign) {
        //getFname只有在server才用到
        fname = rlpath;
        File f = new File(fname);
        flen = f.length();
        fhash = genmd5();
        flag = true;
        genburden();
    }

    //sender
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

    //receiver
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

    // for client
    public void Receriver(String filename, Long filelen, String filehash) {
        fname = prefix + filename;
        flen = filelen;
        fhash = filehash;
        flag = false;
        genburden();
        readstatus();
    }

    public void Receriver(String filepath, Long filelen, String filehash, boolean sign) {
        Path path = Paths.get(filepath);
        fname = path.getParent().toString()
                + File.separator
                + prefix
                + path.getFileName().toString();
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
            emptyfile();
            for (int i = 0; i < tnum; i++) {
                status[i] = 0;
            }
        }
    }

    // receiver
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

    //receiver
    public void SaveStatus() {
        PrintWriter output = null;
        try {
            output = new PrintWriter(fname + suffix);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        for (int i : status) {
            output.println(i);
        }
        output.close();
    }

    //receiver
    public boolean CheckMd5() {
        return fhash.equals(genmd5());
    }

    //unused
    public void deltmp() {
        // 之前没能删除是忘了关闭tmp文件
        File f = new File(fname + suffix);
        //    System.out.println(fname + suffix);
        if (f.exists()) {
            f.delete();
        }
    }

    //todo
    public void rename() {
        File f = new File(fname);
        File nf = new File(fname.substring(1));
        f.renameTo(nf);
    }

    public void rename(boolean sign) {
        File f = new File(fname);
        Path path = Paths.get(fname);
        String str = path.getParent().toString()
                + File.separator
                + path.getFileName().toString().substring(1);
        //      System.out.println(str);
        File nf = new File(str);
        f.renameTo(nf);
    }

    private void genburden() {
        int total = (int) (flen / bfsize);
        if (flen % bfsize != 0) total++;
        perburden = total / tnum;
        lastburden = perburden + total % tnum;
    }

    // code from Internet
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


    //public static void main(String args[]){ }
}