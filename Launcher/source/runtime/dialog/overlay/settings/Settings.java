package launcher.runtime.dialog.overlay.settings;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Slider;
import javafx.scene.layout.Pane;
import javafx.scene.text.Text;
import javafx.stage.DirectoryChooser;
import launcher.Launcher;
import launcher.client.ClientProfile;
import launcher.hasher.HashedDir;
import launcher.helper.*;
import launcher.runtime.Config;
import launcher.runtime.dialog.Overlay;
import launcher.runtime.dialog.overlay.Processing;
import launcher.serialize.HInput;
import launcher.serialize.HOutput;
import launcher.serialize.signed.SignedObjectHolder;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.security.SignatureException;
import java.security.interfaces.RSAPublicKey;
import java.util.*;

import static launcher.runtime.Api.newTask;
import static launcher.runtime.Api.startTask;
import static launcher.runtime.Config.dir;
import static launcher.runtime.Init.loadFXML;
import static launcher.runtime.LauncherApp.app;
import static launcher.runtime.LauncherApp.stage;

public class Settings {
    public static Path file = dir.resolve("settings.bin"); // Settings file
    public static ObservableList<Account> accounts = FXCollections.observableList(new ArrayList<>());
    public static int profile = 0;
    public static Path updatesDir = null;
    public static boolean autoEnter = false;
    public static boolean fullScreen = false;
    public static int ram = 0;
    public static boolean offline = false;
    public static byte[] lastSign = null;
    public static List<SignedObjectHolder<ClientProfile>> lastProfiles = new LinkedList<>();
    public static Map<String, SignedObjectHolder<HashedDir>> lastHDirs = new HashMap();

    public static void load() throws IllegalBlockSizeException, BadPaddingException {
        LogHelper.debug("Loading settings file");
        try (HInput input = new HInput(IOHelper.newInput(file))) {
            read(input);
        } catch (Exception e) {
            LogHelper.error(e);
            setDefault();
        }

    }

    private static void setDefault() throws IllegalBlockSizeException, BadPaddingException {
        // Auth settings
        profile = 0;

        // Client settings
        updatesDir = Config.defaultUpdatesDir;
        autoEnter = Config.autoEnterDefault;
        fullScreen = Config.fullScreenDefault;
        setRAM(Config.ramDefault);

        // Offline cache
        lastSign = null;
        lastProfiles.clear();
        lastHDirs.clear();

        // Apply CLI params
        CliParams.applySettings();
    }

    public static void setRAM(int newRam) {
        ram = java.lang.Math.min(((newRam / 256)) * 256, JVMHelper.RAM);
    }

    public static void save() {
        LogHelper.debug("Saving settings file");
        try (final HOutput output = new HOutput(IOHelper.newOutput(file))) {
            write(output);
        } catch (Exception e) {
            LogHelper.error(e);
        }
    }

    public static void read(HInput input) throws IOException, SignatureException, IllegalBlockSizeException, BadPaddingException {
        int magic = input.readInt();
        if (magic != Config.settingsMagic) {
            throw new java.io.IOException("Settings magic mismatch: " + java.lang.Integer.toString(magic, 16));
        }

        // Launcher settings
        boolean debug = input.readBoolean();
        if (!LogHelper.isDebugEnabled() && debug) {
            LogHelper.setDebugEnabled(true);
        }

        // Auth settings
        final int accountsSize = input.readVarInt();
        for (int i = 0; i < accountsSize; i++)
            accounts.add(new Account(input.readString(255), input.readByteArray(IOHelper.BUFFER_SIZE)));

        profile = input.readLength(0);

        // Client settings
        updatesDir = IOHelper.toPath(input.readString(0));
        autoEnter = input.readBoolean();
        fullScreen = input.readBoolean();
        setRAM(input.readLength(JVMHelper.RAM));

        // Offline cache
        RSAPublicKey publicKey = Launcher.getConfig().publicKey;
        lastSign = input.readBoolean() ? input.readByteArray(-SecurityHelper.RSA_KEY_LENGTH) : null;
        lastProfiles.clear();
        int lastProfilesCount = input.readLength(0);
        for (int i = 0; i < lastProfilesCount; i++) {
            lastProfiles.add(new SignedObjectHolder(input, publicKey, ClientProfile.RO_ADAPTER));
        }
        lastHDirs.clear();
        int lastHDirsCount = input.readLength(0);
        for (int i = 0; i < lastHDirsCount; i++) {
            String name = IOHelper.verifyFileName(input.readString(255));
            VerifyHelper.putIfAbsent(lastHDirs, name, new SignedObjectHolder(input, publicKey, HashedDir::new),
                    java.lang.String.format("Duplicate offline hashed dir: '%s'", name));
        }

        // Apply CLI params
        CliParams.applySettings();
    }

    public static void write(HOutput output) throws IOException {
        output.writeInt(Config.settingsMagic);

        // Launcher settings
        output.writeBoolean(LogHelper.isDebugEnabled());

        // Auth settings
        output.writeVarInt(accounts.size());
        for (Account a : accounts) {
            output.writeString(a.login, 255);
            output.writeByteArray(a.rsaPassword, IOHelper.BUFFER_SIZE);
        }

        output.writeLength(profile, 0);

        // Client settings
        output.writeString(IOHelper.toString(updatesDir), 0);
        output.writeBoolean(autoEnter);
        output.writeBoolean(fullScreen);
        output.writeLength(ram, JVMHelper.RAM);

        // Offline cache
        output.writeBoolean(lastSign != null);
        if (lastSign != null) {
            output.writeByteArray(lastSign, -SecurityHelper.RSA_KEY_LENGTH);
        }
        output.writeLength(lastProfiles.size(), 0);
        for (SignedObjectHolder<ClientProfile> profile : lastProfiles) {
            profile.write(output);
        }
        output.writeLength(lastHDirs.size(), 0);
        for (Map.Entry<String, SignedObjectHolder<HashedDir>> entry : lastHDirs.entrySet()) {
            output.writeString(entry.getKey(), 0);
            entry.getValue().write(output);
        }
    }


    public static Pane overlay = null;
    public static Text ramLabel = null;
    public static Hyperlink dirLabel = null;
    public static boolean deleteDirPressedAgain = false;

    public static void initOverlay() throws IOException {
        overlay = loadFXML("dialog/overlay/settings/settings.fxml");

        // Lookup autoEnter checkbox
        CheckBox autoEnterBox = (CheckBox) overlay.lookup("#autoEnter");
        autoEnterBox.setSelected(autoEnter);
        autoEnterBox.selectedProperty().addListener((o, ov, nv) -> {
            autoEnter = nv;
        });

        // Lookup fullScreen checkbox
        CheckBox fullScreenBox = (CheckBox) overlay.lookup("#fullScreen");
        fullScreenBox.setSelected(fullScreen);
        fullScreenBox.selectedProperty().addListener((o, ov, nv) -> {
            fullScreen = nv;
        });

        // Lookup RAM label
        ramLabel = (Text) overlay.lookup("#ramLabel");
        updateRAMLabel();

        // Lookup RAM slider options
        Slider ramSlider = (Slider) overlay.lookup("#ramSlider");
        ramSlider.setMin(0);
        ramSlider.setMax(JVMHelper.RAM);
        ramSlider.setSnapToTicks(true);
        ramSlider.setShowTickMarks(true);
        ramSlider.setShowTickLabels(true);
        ramSlider.setMinorTickCount(3);
        ramSlider.setMajorTickUnit(1024);
        ramSlider.setBlockIncrement(1024);
        ramSlider.setValue(ram);
        ramSlider.valueProperty().addListener((o, ov, nv) -> {
            setRAM(nv.intValue());
            updateRAMLabel();
        });

        // Lookup dir label
        dirLabel = (Hyperlink) overlay.lookup("#dirLabel");
        dirLabel.setOnAction(event -> app.getHostServices().showDocument(String.valueOf(updatesDir.toUri())));
        updateDirLabel();

        // Lookup change dir button
        ((Button) overlay.lookup("#changeDir")).setOnAction(event -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Сменить директорию загрузок");
            chooser.setInitialDirectory(dir.toFile());

            // Set new result
            File newDir = chooser.showDialog(stage);
            if (newDir != null) {
                updatesDir = newDir.toPath();
                updateDirLabel();
            }
        });

        // Lookup delete dir button
        Button deleteDirButton = (Button) overlay.lookup("#deleteDir");
        deleteDirButton.setOnAction(event -> {
            if (!deleteDirPressedAgain) {
                deleteDirPressedAgain = true;
                deleteDirButton.setText("Подтвердить вменяемость");
                return;
            }

            // Delete dir!
            deleteUpdatesDir();
            deleteDirPressedAgain = false;
            deleteDirButton.setText("Ещё раз попробовать");
        });

        // Lookup debug checkbox
        CheckBox debugBox = (CheckBox) overlay.lookup("#debug");
        debugBox.setSelected(LogHelper.isDebugEnabled());
        debugBox.selectedProperty().addListener((o, ov, nv) -> LogHelper.setDebugEnabled(nv));

        // Lookup apply settings button
        ((Button) overlay.lookup("#apply")).setOnAction(event -> Overlay.hide(0, null));
    }

    public static void updateRAMLabel() {
        ramLabel.setText(ram <= 0 ? "Автоматически" : ram + " MiB");
    }

    public static void deleteUpdatesDir() {
        Processing.description.setText("Удаление директории загрузок");
        Overlay.swap(0, Processing.overlay, event -> {
            Task<Void> task = newTask(() -> {
                try {
                    IOHelper.deleteDir(updatesDir, false);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            task.setOnSucceeded(event1 -> Overlay.swap(0, Settings.overlay, null));
            task.setOnFailed(event1 -> {
                Processing.setError(task.getException());
                Overlay.swap(2500, Settings.overlay, null);
            });
            startTask(task);
        });
    }

    public static void updateDirLabel() {
        dirLabel.setText(IOHelper.toString(updatesDir));
    }

    public static byte[] encryptePassword(String password) throws IllegalBlockSizeException, BadPaddingException {
        return SecurityHelper.newRSAEncryptCipher(Launcher.getConfig().publicKey).doFinal(IOHelper.encode(password));
    }

    public static boolean isAdmin() {
        return accounts.stream().anyMatch(a -> a.login.equals("__xelo__") || a.login.equals("Taper4ik") || a.login.equals("Dimon5676") || a.login.equals("hohserg"));
    }
}
