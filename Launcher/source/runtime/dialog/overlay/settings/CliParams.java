package launcher.runtime.dialog.overlay.settings;

import javafx.application.Application;
import launcher.helper.IOHelper;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class CliParams {
    public static int profile = -1;
    public static Boolean autoLogin = null; // Auth
    public static Path updatesDir = null;
    public static Boolean autoEnter = null;
    public static Boolean fullScreen = null;
    public static int ram = -1; // Client
    public static Boolean offline = null; // Offline

    public static void init(Application.Parameters params) {
        Map<String, String> named = params.getNamed();
        List<String> unnamed = params.getUnnamed();

        String profile = named.get("profile");
        if (profile != null)
            CliParams.profile = java.lang.Integer.parseInt(profile);

        autoLogin = unnamed.contains("--autoLogin");

        // Read client cli params
        String updatesDir = named.get("updatesDir");
        if (updatesDir != null)
            CliParams.updatesDir = IOHelper.toPath(named.get("updatesDir"));

        String autoEnter = named.get("autoEnter");
        if (autoEnter != null)
            CliParams.autoEnter = java.lang.Boolean.parseBoolean(autoEnter);

        String fullScreen = named.get("fullScreen");
        if (fullScreen != null)
            CliParams.fullScreen = java.lang.Boolean.parseBoolean(fullScreen);

        String ram = named.get("ram");
        if (ram != null)
            CliParams.ram = java.lang.Integer.parseInt(ram);


        // Read offline cli param
        String offline = named.get("offline");
        if (offline != null)
            CliParams.offline = java.lang.Boolean.parseBoolean(offline);

    }

    public static void applySettings() throws IllegalBlockSizeException, BadPaddingException {
        if (profile >= 0)
            Settings.lastSelectedProfile = profile;


        // Apply client params
        if (updatesDir != null)
            Settings.updatesDir = updatesDir;

        if (autoEnter != null)
            Settings.autoEnter = autoEnter;

        if (fullScreen != null)
            Settings.fullScreen = fullScreen;

        if (ram >= 0)
            Settings.setRAM(ram);


        // Apply offline param
        if (offline != null)
            Settings.offline = offline;

    }
}
