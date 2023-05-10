package launcher.runtime;

import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import launcher.Launcher;
import launcher.helper.js.JSApplication;
import launcher.runtime.dialog.Dialog;
import launcher.runtime.dialog.overlay.settings.CliParams;
import launcher.runtime.dialog.overlay.settings.Settings;

import static launcher.runtime.Init.loadFXML;

public class LauncherApp extends JSApplication {
    public static JSApplication app;
    public static Stage stage;
    public static Scene scene;
    public static String jvmDirName;

    @Override

    public void init() throws Exception {
        app = JSApplication.getInstance();
        CliParams.init(app.getParameters());
        Settings.load();
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        stage = primaryStage;
        stage.setResizable(false);
        stage.setTitle(Config.title);

        // Set icons
        for (String icon : Config.icons) {
            String iconURL = Launcher.getResourceURL(icon).toString();
            stage.getIcons().add(new Image(iconURL));
        }

        // Load dialog FXML
        Dialog.rootPane = loadFXML("dialog/dialog.fxml");
        Dialog.initDialog();

        // Set scene
        scene = new Scene(Dialog.rootPane);
        stage.setScene(scene);

        // Center and show stage
        stage.sizeToScene();
        stage.centerOnScreen();
        stage.show();
    }

    @Override
    public void stop() throws Exception {
        Settings.save();
    }
}
