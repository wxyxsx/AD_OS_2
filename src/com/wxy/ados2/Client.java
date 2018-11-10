package com.wxy.ados2;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class Client {
    private Logger log = Logger.getLogger(Client.class.getName());
    private ToolKits tool;
    private Socket socket;
    private int tnum;
    private int bfsize;
    private int session;
    private String addr;
    private int port;
    private String fname;
    private Thread[] th;
    DataInputStream input;
    DataOutputStream output;

    public Client() {
        tnum = 4;
        bfsize = 1024;
        session = -1;
        addr = "localhost";
        port = 8000;
        fname = "test.pdf";
    }

    public void Connect() {
        try {
            socket = new Socket(addr, port);
            input = new DataInputStream(socket.getInputStream());
            output = new DataOutputStream(socket.getOutputStream());
            output.writeInt(session);
            if (socket.isClosed()) {
                log.info("Server is busy");
                return;
            }
            session = input.readInt();

            log.info(String.format("Get a session %d", session));

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void Close() {
        // ?
        try {
            log.info("Close session");
            output.writeInt(2);
            output.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void Download() {
        try {
            tool = new ToolKits();
            output.writeInt(0);
            output.writeUTF(fname);

            Long flen = input.readLong();
            String fhash = input.readUTF();
            tool.Receriver(fname, flen, fhash);
            th = new Thread[tnum];
            for (int i = 0; i < tnum; i++) {
                th[i] = new Thread(new receiver(i));
                th[i].start();
            }
            for (int i = 0; i < tnum; i++) {
                th[i].join();
            }
            if (tool.CheckMd5()) {
                log.info(fname + " File download succeeded");
            } else {
                log.info(fname + " File download failed");
            }
            if (!tool.isStop()) {
                output.writeInt(1);
            }
            tool = null;
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void Upload(){
        try {
            tool = new ToolKits();
            output.writeInt(1);
            tool.Sender(fname);

            output.writeUTF(fname);
            output.writeLong(tool.getFlen());
            output.writeUTF(tool.getFhash());

            int tp = input.readInt();
            th = new Thread[tnum];
            for( int i=0;i<tnum;i++){
                th[i] = new Thread(new sender(i));
                th[i].start();
            }
            for(int i=0;i<tnum;i++){
                th[i].join();
            }
            log.info(fname + " File upload finished");
            if (!tool.isStop()) {
                output.writeInt(1);
            }
            tool = null;
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void Listdir(){
        log.info("Request file list");

        List<String> name = new ArrayList<String>();
        List<String> length = new ArrayList<String>();
        try {
            output.writeInt(3);
            int len = input.readInt();
            for(int i=0;i<len;i++){
                name.add(input.readUTF());
                length.add(input.readUTF());
            }
            output.writeInt(1);
            for(int i=0;i<name.size();i++){
                System.out.println(name.get(i)+" "+length.get(i));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void Delete(String fname){
        log.info("Delete file "+fname);
        try {
            output.writeInt(4);
            output.writeUTF(fname);
            int r = input.readInt();
            if(r==1){
                System.out.println("Successfully delete file "+fname);
            } else if(r==0){
                System.out.println("Failed delete file "+fname);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void Rename(String oldn,String newn){
        log.info(String.format("rename file %s to %s",oldn,newn));
        try{
            output.writeInt(5);
            output.writeUTF(oldn);
            output.writeUTF(newn);
            int r = input.readInt();
            if(r==1){
                log.info(String.format("Successfully rename file %s to %s",oldn,newn));
            } else if(r==0){
                log.info(String.format("Failed rename file %s to %s",oldn,newn));
            }
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
            if (no != tnum - 1) {
                cur = tool.getPerburden();
            } else {
                cur = tool.getLastburden();
            }
            int offset = tool.getStatus(no);
            if (offset == cur) {
                log.info(String.format("[%d] don't need to download", no));
                return;
            }

            try {
                Socket socket = new Socket(addr, port);
                DataInputStream tinput = new DataInputStream(socket.getInputStream());
                DataOutputStream toutput = new DataOutputStream(socket.getOutputStream());
                toutput.writeInt(session);

                toutput.writeInt(no);
                toutput.writeInt(offset);
                RandomAccessFile fout = tool.GetOutput(no, offset);
                byte[] buffer = new byte[bfsize];
                int i;
                for (i = 0; i < (cur - offset); i++) {
                    if (tool.isStop()) break;
                    int read = tinput.read(buffer);
                    if (read == -1) break;
                    fout.write(buffer, 0, read);
                }
                tool.setStatus(no, offset + i);
                log.info(String.format("[%d] download %d/%d", no, i, cur-offset));
                fout.close();
                tinput.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    class sender implements Runnable {
        private int no;

        public sender(int no) {
            this.no = no;
        }

        public void run(){
            Socket socket = null;
            try {
                socket = new Socket(addr, port);
                DataInputStream tinput = new DataInputStream(socket.getInputStream());
                DataOutputStream toutput = new DataOutputStream(socket.getOutputStream());
                toutput.writeInt(session);

                toutput.writeInt(no);
                int offset = tinput.readInt();
                int cur;
                if (no != tnum - 1) {
                    cur = tool.getPerburden();
                } else {
                    cur = tool.getLastburden();
                }
                if(offset == cur){
                    tinput.close();
                }

                RandomAccessFile fin = tool.GetInput(no,offset);
                byte[] buffer = new byte[bfsize];
                int i;
                for (i = 0; i < (cur - offset); i++) {
                    if (tool.isStop()) break;
                    int read = fin.read(buffer);
                    if (read == -1) break;
                    toutput.write(buffer, 0, read);
                }
                log.info(String.format("[%d] upload %d/%d", no, i, cur-offset));
                fin.close();;
                toutput.close();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String args[]) {
        Client c = new Client();
        c.Connect();
        //c.Listdir();
        //c.Delete("test.txt");
        c.Rename("test.pdf","new.pdf");
        //c.Upload();
        c.Close();
    }
}
