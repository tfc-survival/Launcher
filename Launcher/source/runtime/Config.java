package launcher.runtime;

import launcher.helper.IOHelper;
import launcher.helper.JVMHelper;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;

public class Config {
    public static String dir1 = "tfc-survival"; // Launcher directory
    public static String title = "tfc-survival launcher"; // Window title
    public static String[] icons = {"favicon.png"}; // Window icon paths

    // Auth config
    public static String newsURL = "https://tfc.su"; // News WebView URL
    public static String linkText = "Наш сайт"; // Text for link under "Auth" button

    public static URL linkURL; // URL for link under "Auth" button

    static {
        try {
            linkURL = new URL("https://tfc.su");
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    // Settings defaults
    public static int settingsMagic = 0xC0DE7; // Ancient magic, don't touch
    public static boolean autoEnterDefault = false; // Should autoEnter be enabled by default?
    public static boolean fullScreenDefault = false; // Should fullScreen be enabled by default?
    public static int ramDefault = 4096; // Default RAM amount (0 for auto)

    public static Path dir;

    public static Path defaultUpdatesDir;

    static {
        dir = IOHelper.HOME_DIR.resolve(dir1);
        if (JVMHelper.OS_TYPE == JVMHelper.OS.WINDOWS) {
            dir = IOHelper.HOME_DIR_WIN.resolve(dir1);
        }
        if (!IOHelper.isDir(dir)) {
            try {
                java.nio.file.Files.createDirectory(dir);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        defaultUpdatesDir = dir.resolve("updates");
        if (!IOHelper.isDir(defaultUpdatesDir)) {
            try {
                java.nio.file.Files.createDirectory(defaultUpdatesDir);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
