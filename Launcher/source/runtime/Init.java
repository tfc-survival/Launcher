package launcher.runtime;

import javafx.fxml.FXMLLoader;
import javafx.scene.layout.Pane;
import launcher.Launcher;
import launcher.helper.IOHelper;
import launcher.helper.JVMHelper;
import launcher.helper.LogHelper;

import java.io.IOException;

import static launcher.runtime.LauncherApp.jvmDirName;

public class Init {


    public static void start(String... args) {
        // Set JVM dir name
        LogHelper.debug("Setting JVM dir name");
        switch (JVMHelper.OS_TYPE) {
            case MUSTDIE:
                jvmDirName = JVMHelper.OS_BITS == 32 ? "-win32" : JVMHelper.OS_BITS == 64 ? "-win64" : "-unknown";
                break; // 64-bit Mustdie
            case LINUX:
                jvmDirName = JVMHelper.OS_BITS == 32 ? "-linux32" : JVMHelper.OS_BITS == 64 ? "-linux64" : "-unknown";
                break; // 64-bit Linux
            case MACOSX:
                jvmDirName = JVMHelper.OS_BITS == 64 ? "-macosx" : "-unknown";
                break; // 64-bit MacOSX
            default:
                jvmDirName = "-unknown";
                LogHelper.warning("Unknown OS: '%s'", JVMHelper.OS_TYPE.name);
                break; // Unknown OS
        }

        // Set font rendering properties
        LogHelper.debug("Setting FX properties");
        java.lang.System.setProperty("prism.lcdtext", "false");

        // Start laucher JavaFX stage
        LogHelper.debug("Launching JavaFX application");
        javafx.application.Application.launch(LauncherApp.class, args);
    }

    public static Pane loadFXML(String name) throws IOException {
        FXMLLoader loader = new FXMLLoader(Launcher.getResourceURL(name));
        loader.setCharset(IOHelper.UNICODE_CHARSET);
        return loader.load();
    }
}
