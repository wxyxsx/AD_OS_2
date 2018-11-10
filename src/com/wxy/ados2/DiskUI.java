package com.wxy.ados2;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;


public class DiskUI extends Application {
    private TextField taddr = new TextField();
    private TextField tport = new TextField();
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

    private List<String> name = new ArrayList<String>();
    private List<String> size = new ArrayList<String>();


    public void start(Stage primaryStage) {
        Pane pane = getUI();
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
        lv = new ListView<>(FXCollections.observableArrayList(name));
        ListView<String> lv = new ListView<>(FXCollections.observableArrayList(name));
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
