package com.wxy.ados2;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;


public class DiskUI extends Application {
    private TextField taddr = new TextField("222.29.196.155");
    private TextField tport = new TextField("8000");
    private Button bcon = new Button("连接");
    private Button bexit = new Button("断开");
    private ListView<String> lv;
    private TextField tfname = new TextField();
    private TextField tfsize = new TextField();
    private Button brefresh = new Button("刷新");
    private TextField tnewname = new TextField();
    private Button brename = new Button("重命名");
    private Button bdelete = new Button("删除");
    private TextField tupload = new TextField();
    private Button buch = new Button ("..");
    private Button bupload = new Button("上传");
    private TextField tdown = new TextField();
    private Button bdch = new Button ("..");
    private Button bdown = new Button("下载");
    private Button bstop = new Button("暂停");
    private TextArea tastatus = new TextArea();

    private List<String> length = new ArrayList<String>();
    private List<String> name = new ArrayList<String>();
    private ObservableList<String> lst = FXCollections.observableArrayList();

    private Client client;

    private void refreshlst(){
        Platform.runLater(() -> {
            if(!lv.getSelectionModel().isEmpty()){
                lv.getSelectionModel().clearSelection();
            }
            lst.removeAll(lst);
            for(int i=0;i<name.size();i++){
                lst.add(name.get(i));
            }
        });
    }

    public void start(Stage primaryStage) {
        Pane pane = getUI();
        bcon.setOnAction(e->{
            client = new Client(tastatus);
            client.setAddr(taddr.getText());
            client.setPort(Integer.parseInt(tport.getText()));
            new Thread(()->{
                client.Connect();
                client.Listdir();
                name = client.getName();
                length = client.getLength();
                refreshlst();
            }).start();
        });
        bexit.setOnAction(e->{
            new Thread(()->{
                client.Close();
                name.removeAll(name);
                refreshlst();
                tfname.setText("");
                tfsize.setText("");
                // other reset operation
            }).start();
        });
        brefresh.setOnAction(e->{

            new Thread(()->{
                if (client.isBusy()) {
                    return;
                }
                client.Listdir();
                name = client.getName();
                length = client.getLength();
                refreshlst();

            }).start();
        });
        brename.setOnAction(e->{
            new Thread(()->{
                if (client.isBusy()) {
                    return;
                }
                String so = tfname.getText();
                String sn = tnewname.getText();
                if(so.isEmpty()||sn.isEmpty()){
                    Platform.runLater(()->{
                        tastatus.appendText("文件名不能为空\n");
                    });
                } else {
                    client.Rename(so,sn);
                    client.Listdir();
                    name = client.getName();
                    length = client.getLength();
                    refreshlst();
                }
            }).start();
        });
        bdown.setOnAction(e->{
            new Thread(()->{
                if (client.isBusy()) {
                    return;
                }
                String s = tfname.getText();
                if(s.isEmpty()){
                    Platform.runLater(()->{
                        tastatus.appendText("文件名不能为空\n");
                    });
                } else {
                    client.setFname(s);
                    client.Download();
                }
            }).start();
        });
        bupload.setOnAction(e->{
            new Thread(()->{
                if (client.isBusy()) {
                    return;
                }
                String s = tupload.getText();
                if(s.isEmpty()){
                    Platform.runLater(()->{
                        tastatus.appendText("请选择上传文件\n");
                    });
                } else {
                    client.setFname(s);
                    client.Upload();
                    client.Listdir();
                    name = client.getName();
                    length = client.getLength();
                    refreshlst();
                }
            }).start();
        });
        bdelete.setOnAction(e -> {
            new Thread(() -> {
                if (client.isBusy()) {
                    return;
                }
                String s = tfname.getText();
                if (s.isEmpty()) {
                    Platform.runLater(() -> {
                        tastatus.appendText("删除文件名不能为空\n");
                    });
                } else {
                    client.Delete(s);
                    client.Listdir();
                    name = client.getName();
                    length = client.getLength();
                    refreshlst();
                }
            }).start();
        });
        bstop.setOnAction(e->{
            new Thread(()->{
                if (client.isBusy()) {
                    return;
                }
                client.Stop();
            }).start();
        });
        lv.getSelectionModel().selectedItemProperty().addListener(ov->{
            int i=lv.getSelectionModel().getSelectedIndex();
            tfname.setText(name.get(i));
            tfsize.setText(length.get(i));
        });
        primaryStage.setOnCloseRequest(e->{
            if(client!=null){
                client.Close();
            }
            System.exit(0);
        });
        FileChooser fc = new FileChooser();
        fc.setTitle("选择要上传的文件");
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("选择要保存的文件夹");
        buch.setOnAction(e -> {
            File file = fc.showOpenDialog(primaryStage);
            if (file != null) {
                // String str = file.getPath();
//                Path path = Paths.get(str);
//                tastatus.appendText(str);
                tupload.setText(file.getPath());
            }
        });
        bdch.setOnAction(e -> {
            File file = dc.showDialog(primaryStage);
            if (file != null) {
                tdown.setText(file.getPath());
            }
        });
        Scene scene = new Scene(pane,540,370);
        primaryStage.setTitle("网盘");
        primaryStage.setScene(scene);
        primaryStage.show();

    }

    private Pane getUI(){
        HBox topbox = new HBox(10);
        taddr.setPrefColumnCount(10);
        tport.setPrefColumnCount(4);
        topbox.getChildren().addAll(new Label("地址:"),taddr,new Label("端口:"),tport,bcon,bexit);
        topbox.setPadding(new Insets(5,5,5,5));
        topbox.setAlignment(Pos.CENTER);

        //name.add("file1");
        lv = new ListView<>(lst);
        lv.setPrefSize(200,300);
        lv.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);

        VBox sidebox = new VBox(10);
        HBox h1 = new HBox(10);
        tfname.setEditable(false);
        tfsize.setEditable(false);
        tfname.setPrefColumnCount(8);
        tfsize.setPrefColumnCount(4);
        h1.getChildren().addAll(new Label("文件名"),tfname,new Label("大小"),tfsize);
        tnewname.setPrefColumnCount(8);
        HBox h2 = new HBox(10);
        h2.getChildren().addAll(brefresh,tnewname,brename,bdelete);
        tupload.setPrefColumnCount(8);
        HBox h3 = new HBox(10);
        h3.getChildren().addAll(new Label("上传"),tupload,buch,bupload);
        tdown.setPrefColumnCount(8);
        HBox h4 = new HBox(10);
        h4.getChildren().addAll(new Label("下载"),tdown,bdch,bdown,bstop);
        h1.setPadding(new Insets(5,5,5,5));
        h1.setAlignment(Pos.CENTER_LEFT);
        h2.setPadding(new Insets(5,5,5,5));
        h2.setAlignment(Pos.CENTER_LEFT);
        h3.setPadding(new Insets(5,5,5,5));
        h3.setAlignment(Pos.CENTER_LEFT);
        h4.setPadding(new Insets(5,5,5,5));
        h4.setAlignment(Pos.CENTER_LEFT);
        tastatus.setPrefSize(300,120);
        tastatus.setEditable(false);
        sidebox.getChildren().addAll(h1,h2,h3,h4,new ScrollPane(tastatus));

        FlowPane fp = new FlowPane(Orientation.HORIZONTAL,10,10);
        fp.setPadding(new Insets(5,5,5,5));
        fp.getChildren().addAll(new ScrollPane(lv),sidebox);

        VBox vb = new VBox(10);
        vb.getChildren().addAll(topbox,fp);
        return vb;
    }
}
