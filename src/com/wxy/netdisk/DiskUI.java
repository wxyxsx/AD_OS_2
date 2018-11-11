package com.wxy.netdisk;

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
    private Button buch = new Button("..");
    private Button bupload = new Button("上传");
    private TextField tdown = new TextField();
    private Button bdch = new Button("..");
    private Button bdown = new Button("下载");
    private Button bstop = new Button("暂停");
    private TextArea tastatus = new TextArea();

    private List<String> length = new ArrayList<String>();
    private List<String> name = new ArrayList<String>();
    private ObservableList<String> lst = FXCollections.observableArrayList();

    private Client client;

    private void refreshlst() {
        Platform.runLater(() -> {
            if (!lv.getSelectionModel().isEmpty()) {
                lv.getSelectionModel().clearSelection();
            } // 取消列表的选中状态
            lst.removeAll(lst);
            lst.addAll(name);
//            for(int i=0;i<name.size();i++){
//                lst.add(name.get(i));
//            }
        });
    }

    public void start(Stage primaryStage) {
        Pane pane = getUI();
        bcon.setOnAction(e -> { // 连接
            client = new Client(tastatus);
            client.setAddr(taddr.getText());
            client.setPort(Integer.parseInt(tport.getText()));
            new Thread(() -> {
                client.Connect();
                client.Listdir();
                name = client.getName();
                length = client.getLength();
                refreshlst();
            }).start();
        });
        bexit.setOnAction(e -> { // 退出
            new Thread(() -> {
                client.Close();
                name.removeAll(name);
                refreshlst();
                Platform.runLater(() -> {
                    tfname.setText("");
                    tfsize.setText("");
                    tupload.setText("");
                });
            }).start();
        });
        brefresh.setOnAction(e -> { // 刷新
            new Thread(() -> {
                if (client.isBusy()) {
                    return;
                }
                client.Listdir();
                name = client.getName();
                length = client.getLength();
                refreshlst();

            }).start();
        });
        brename.setOnAction(e -> {// 重命名
            new Thread(() -> {
                if (client.isBusy()) {
                    return;
                }
                String so = tfname.getText();
                String sn = tnewname.getText();
                if (so.isEmpty() || sn.isEmpty()) {
                    Platform.runLater(() -> {
                        tastatus.appendText("文件名不能为空\n");
                    });
                } else {
                    client.Rename(so, sn);
                    client.Listdir();
                    name = client.getName();
                    length = client.getLength();
                    refreshlst();
                }
            }).start();
        });
        bdown.setOnAction(e -> { // 下载
            new Thread(() -> {
                if (client.isBusy()) {
                    return;
                }
                String sf = tfname.getText();
                String sp = tdown.getText();
                if (sf.isEmpty() || sf.isEmpty()) {
                    Platform.runLater(() -> {
                        tastatus.appendText("请选择文件和文件夹\n");
                    });
                } else {
                    client.setFname(sp + File.separatorChar + sf);
                    // 选择的文件夹+分隔符+文件名
                    client.Download();
                }
            }).start();
        });
        bupload.setOnAction(e -> { // 上传
            new Thread(() -> {
                if (client.isBusy()) {
                    return;
                }
                String s = tupload.getText();
                if (s.isEmpty()) {
                    Platform.runLater(() -> {
                        tastatus.appendText("请选择上传文件\n");
                    });
                } else {
                    client.setFname(s); //文件的绝对路径
                    client.Upload();
                    client.Listdir();
                    name = client.getName();
                    length = client.getLength();
                    refreshlst();
                }
            }).start();
        });
        bdelete.setOnAction(e -> { // 删除
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
        bstop.setOnAction(e -> { // 暂停
            new Thread(() -> {
                client.Stop();
            }).start();
        });
        lv.getSelectionModel().selectedItemProperty().addListener(ov -> { // 查看文件大小
            int i = lv.getSelectionModel().getSelectedIndex();
            tfname.setText(name.get(i));
            tfsize.setText(length.get(i));
        });
        primaryStage.setOnCloseRequest(e -> { // 关闭窗口则关闭连接，好像有bug
            if (client != null) {
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
                tupload.setText(file.getPath()); //获取文件路径
            }
        });
        bdch.setOnAction(e -> {
            File file = dc.showDialog(primaryStage);
            if (file != null) {
                tdown.setText(file.getPath()); //获取文件夹路径
            }
        });
        Scene scene = new Scene(pane, 540, 370);
        primaryStage.setTitle("网盘");
        primaryStage.setScene(scene);
        primaryStage.show();

    }

    private Pane getUI() {
        HBox topbox = new HBox(10);
        taddr.setPrefColumnCount(10);
        tport.setPrefColumnCount(4);
        topbox.getChildren().addAll(new Label("地址:"), taddr, new Label("端口:"), tport, bcon, bexit);
        topbox.setPadding(new Insets(5, 5, 5, 5));
        topbox.setAlignment(Pos.CENTER);

        lv = new ListView<>(lst);
        lv.setPrefSize(200, 300);
        lv.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);

        VBox sidebox = new VBox(10);
        HBox h1 = new HBox(10);
        tfname.setEditable(false);
        tfsize.setEditable(false);
        tfname.setPrefColumnCount(8);
        tfsize.setPrefColumnCount(4);
        h1.getChildren().addAll(new Label("文件名"), tfname, new Label("大小"), tfsize);
        tnewname.setPrefColumnCount(8);
        HBox h2 = new HBox(10);
        h2.getChildren().addAll(brefresh, tnewname, brename, bdelete);
        tupload.setPrefColumnCount(8);
        HBox h3 = new HBox(10);
        h3.getChildren().addAll(new Label("上传"), tupload, buch, bupload);
        tdown.setPrefColumnCount(8);
        tdown.setText(new File("").getAbsolutePath());
        // 文件夹的默认路径为当前文件夹

        HBox h4 = new HBox(10);
        h4.getChildren().addAll(new Label("下载"), tdown, bdch, bdown, bstop);
        h1.setPadding(new Insets(5, 5, 5, 5));
        h1.setAlignment(Pos.CENTER_LEFT);
        h2.setPadding(new Insets(5, 5, 5, 5));
        h2.setAlignment(Pos.CENTER_LEFT);
        h3.setPadding(new Insets(5, 5, 5, 5));
        h3.setAlignment(Pos.CENTER_LEFT);
        h4.setPadding(new Insets(5, 5, 5, 5));
        h4.setAlignment(Pos.CENTER_LEFT);
        tastatus.setPrefSize(300, 120);
        tastatus.setEditable(false);
        sidebox.getChildren().addAll(h1, h2, h3, h4, new ScrollPane(tastatus));

        FlowPane fp = new FlowPane(Orientation.HORIZONTAL, 10, 10);
        fp.setPadding(new Insets(5, 5, 5, 5));
        fp.getChildren().addAll(new ScrollPane(lv), sidebox);

        VBox vb = new VBox(10);
        vb.getChildren().addAll(topbox, fp);
        return vb;
    }
}
