package com.sessionshelf;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import com.sessionshelf.dao.DatabaseManager;
import com.sessionshelf.controller.MainController;
import com.sessionshelf.config.AppConfig;

/**
 * Sessionshelf - 主应用类
 * 负责初始化 JavaFX 应用程序窗口和核心服务
 */
public class App extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        // 初始化数据库
        DatabaseManager.getInstance().initialize();

        // 加载主界面 FXML
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
        Parent root = loader.load();
        MainController controller = loader.getController();

        // 从配置读取窗口大小
        AppConfig config = AppConfig.getInstance();
        double width = config.getInt(AppConfig.WINDOW_WIDTH, 1200);
        double height = config.getInt(AppConfig.WINDOW_HEIGHT, 800);

        // 配置主窗口
        Scene scene = new Scene(root, width, height);
        // 根据主题设置加载对应 CSS
        String theme = config.getString("app.theme", "light");
        String cssFile = "dark".equals(theme) ? "/css/dark.css" : "/css/style.css";
        scene.getStylesheets().add(getClass().getResource(cssFile).toExternalForm());

        primaryStage.setTitle("Sessionshelf");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(1000);
        primaryStage.setMinHeight(600);

        // 设置窗口图标
        try {
            Image logo = new Image(getClass().getResourceAsStream("/images/logo.png"));
            if (logo != null) {
                primaryStage.getIcons().add(logo);
            }
        } catch (Exception ignored) {
            // 图标文件不存在时使用默认图标
        }

        // 窗口关闭时保存配置和布局位置
        primaryStage.setOnCloseRequest(event -> {
            config.setInt(AppConfig.WINDOW_WIDTH, (int) primaryStage.getWidth());
            config.setInt(AppConfig.WINDOW_HEIGHT, (int) primaryStage.getHeight());
            controller.saveLayoutPositions();
            config.save();
        });

        primaryStage.show();
    }

    @Override
    public void stop() throws Exception {
        // 关闭数据库连接
        DatabaseManager.getInstance().close();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
