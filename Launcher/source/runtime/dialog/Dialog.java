package launcher.runtime.dialog;

import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import launcher.ConfigBin;
import launcher.client.ClientLauncher;
import launcher.client.ClientProfile;
import launcher.client.PlayerProfile;
import launcher.client.ServerPinger;
import launcher.hasher.FileNameMatcher;
import launcher.hasher.HashedDir;
import launcher.helper.LogHelper;
import launcher.helper.VerifyHelper;
import launcher.request.update.LauncherRequest;
import launcher.runtime.Config;
import launcher.runtime.dialog.overlay.Debug;
import launcher.runtime.dialog.overlay.Processing;
import launcher.runtime.dialog.overlay.Update;
import launcher.runtime.dialog.overlay.settings.Settings;
import launcher.serialize.config.entry.StringConfigEntry;
import launcher.serialize.signed.SignedObjectHolder;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.security.SignatureException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static launcher.runtime.Api.newTask;
import static launcher.runtime.Api.startTask;
import static launcher.runtime.Init.loadFXML;
import static launcher.runtime.LauncherApp.*;
import static launcher.runtime.dialog.overlay.Processing.launchClient;
import static launcher.runtime.dialog.overlay.Processing.makeLauncherRequest;
import static launcher.runtime.dialog.overlay.Update.makeUpdateRequest;

public class Dialog {
    public static Pane rootPane;
    public static HBox authList;
    public static Pane authPane;
    public static Pane dimPane;

    public static TextField loginField;
    public static PasswordField passwordField;
    public static ComboBox<SignedObjectHolder<ClientProfile>> profilesBox;
    private static Button authButton;
    private static Button playButton;

    public static Map<ClientProfile, ServerPinger> pingers = new HashMap<>();

    private static PlayerProfile playerProfile;
    private static String accessToken;


    public static void initDialog() throws IOException {
        // Lookup auth pane and dim
        initAuthPane(rootPane);
        dimPane = (Pane) rootPane.lookup("#dim");

        // Lookup profiles combobox
        profilesBox = (ComboBox<SignedObjectHolder<ClientProfile>>) rootPane.lookup("#profiles");
        profilesBox.setCellFactory(Dialog::newProfileCell);
        profilesBox.setButtonCell(newProfileCell(null));

        // Lookup hyperlink text and actions
        Hyperlink link = (Hyperlink) rootPane.lookup("#link");
        link.setText(Config.linkText);
        link.setOnAction(event -> {
            try {
                app.getHostServices().showDocument(String.valueOf(Config.linkURL.toURI()));
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
        });

        Button goSettings = (Button) rootPane.lookup("#goSettings");
        goSettings.setGraphic(new ImageView(new Image(Dialog.class.getResourceAsStream("/runtime/dialog/settings.png"))));
        goSettings.setOnAction(Dialog::goSettings);

        // Init overlays
        Debug.initOverlay();
        Processing.initOverlay();
        Settings.initOverlay();
        Update.initOverlay();

        // Verify launcher & make request
        verifyLauncher();

    }

    private static void addAccount(String login, byte[] rsaPassword) {
        if (!Settings.accounts.containsKey(login)) {
            Settings.accounts.put(login, rsaPassword);
            addAccButton(login);
        }
    }

    private static void addAccButton(String login) {
        Button accountButton = new Button();
        accountButton.setOnAction(selectAccount(login));
        accountButton.setTooltip(new Tooltip(login));
        accountButton.setPadding(new Insets(1, 1, 1, 1));
        accountButton.setContextMenu(new ContextMenu(new MenuItem("удалить") {{
            setOnAction(e -> removeAccount(login));
        }}));

        ImageView skin = new ImageView();
        Image image = new Image("https://tfc.su/static/skins/" + login + ".png");
        double w = image.getWidth();
        double h = image.getHeight();
        double slicedX = 8d / 64 * w;
        double slicesY = 8d / 64 * h;

        if (slicedX < 32) {
            int scale = 4;
            image = new Image("https://tfc.su/static/skins/" + login + ".png", w * scale, h * scale, false, false);
            slicedX *= scale;
            slicesY *= scale;
        }

        skin.setImage(image);
        skin.setViewport(new Rectangle2D(slicedX, slicesY, slicedX, slicesY));
        skin.setFitHeight(32);
        skin.setFitWidth(32);

        accountButton.setGraphic(skin);
        authList.getChildren().add(accountButton);

        if (image.isError()) {
            skin.setImage(new Image(Dialog.class.getResourceAsStream("/steve.png"), 32, 32, false, false));
        }
    }

    private static void removeAccount(String login) {
        if (Settings.accounts.containsKey(login)) {
            Settings.accounts.remove(login);
            Settings.lastSelectedAcc = null;
            authList.getChildren().removeIf(n -> n instanceof Button && ((Button) n).getTooltip().getText().equals(login));
        }
    }

    private static void initAuthPane(Pane rootPane) {
        authList = (HBox) rootPane.lookup("#authList");
        authPane = (Pane) rootPane.lookup("#authPane");
        Settings.accounts.keySet().forEach(Dialog::addAccButton);
        authList.setSpacing(5);

        // Lookup login field
        loginField = (TextField) authPane.lookup("#login");
        loginField.setOnAction(Dialog::goAuth);
        loginField.setOnKeyPressed(Dialog::unselectAccount);


        // Lookup password field
        passwordField = (PasswordField) authPane.lookup("#password");
        passwordField.setOnAction(Dialog::goAuth);
        passwordField.setOnKeyPressed(Dialog::unselectAccount);

        // Lookup action buttons
        authButton = (Button) authPane.lookup("#goAuth");
        authButton.setOnAction(Dialog::goAuth);
        playButton = (Button) authPane.lookup("#goPlay");
        playButton.setOnAction(Dialog::goPlay);
    }

    private static void unselectAccount(KeyEvent event) {
        Settings.lastSelectedAcc = null;
        profilesBox.setVisible(false);
        authButton.setVisible(true);
        playButton.setVisible(false);
    }

    private static EventHandler<ActionEvent> selectAccount(String login) {
        return __ -> {
            loginField.setText(login);
            loginField.setAlignment(Pos.CENTER);
            setPasswordSaved();
            Settings.lastSelectedAcc = login;
            profilesBox.setVisible(false);
            authButton.setVisible(true);
            playButton.setVisible(false);
        };
    }

    private static void setPasswordSaved() {
        passwordField.getStyleClass().add("hasSaved");
        passwordField.setText("*** Сохранённый ***");
        passwordField.setAlignment(Pos.CENTER);
    }

    private static <A extends Event> EventHandler<A> predicate(EventHandler<A> f, Predicate<A> p) {
        return e -> {
            if (p.test(e))
                f.handle(e);
        };
    }

    public static ListCell<SignedObjectHolder<ClientProfile>> newProfileCell(ListView<SignedObjectHolder<ClientProfile>> listView) {
        final Pane statusBox;
        try {
            statusBox = loadFXML("dialog/profileCell.fxml");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Lookup labels
        Label title = (Label) statusBox.lookup("#profileTitle");
        Label status = (Label) statusBox.lookup("#serverStatus");
        Circle statusCircle = (Circle) title.getGraphic();

        // Create and return new cell
        ListCell<SignedObjectHolder<ClientProfile>> cell = new ListCell<SignedObjectHolder<ClientProfile>>() {
            @Override
            protected void updateItem(SignedObjectHolder<ClientProfile> item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : statusBox);
                if (empty) { // No need to update state
                    return;
                }

                // Update title and server status
                title.setText(item.object.getTitle());
                pingServer(status, statusCircle, item);
            }
        };
        cell.setText(null);
        return cell;
    }

    public static void pingServer(Label status, Circle statusCircle, SignedObjectHolder<ClientProfile> profile) {
        setServerStatus(status, statusCircle, javafx.scene.paint.Color.GREY, "...");
        Task<ServerPinger.Result> task = newTask(() -> pingers.get(profile.object).ping());
        task.setOnSucceeded((event) -> {
            ServerPinger.Result result = task.getValue();
            Color color = result.isOverfilled() ? Color.YELLOW : Color.GREEN;
            setServerStatus(status, statusCircle, color, java.lang.String.format("%d из %d", result.onlinePlayers, result.maxPlayers));
        });
        task.setOnFailed(event -> setServerStatus(status, statusCircle, javafx.scene.paint.Color.RED, "Недоступен"));
        startTask(task);
    }

    public static void setServerStatus(Label status, Circle statusCircle, javafx.scene.paint.Color color, String description) {
        status.setText(description);
        statusCircle.setFill(color);
    }

    public static void goSettings(ActionEvent event) {
        // Verify there's no other overlays
        if (Overlay.current != null)
            return;

        // Show settings overlay
        Overlay.show(Settings.overlay, null);
    }

    public static void initOffline() throws IOException {
        // Update title
        stage.setTitle(Config.title + " [Offline]");

        // Set login field as username field
        loginField.setPromptText("Имя пользователя");
        if (!VerifyHelper.isValidUsername("")) {
            loginField.setText(""); // Reset if not valid
        }

        // Disable password field
        passwordField.setDisable(true);
        passwordField.setPromptText("Недоступно");
        passwordField.setText("");
    }


    public static void goPlay(ActionEvent actionEvent) {
        if (Overlay.current != null) {
            return;
        }

        SignedObjectHolder<ClientProfile> profile = profilesBox.getSelectionModel().getSelectedItem();
        if (profile == null) {
            return;
        }

        doUpdate(profile, playerProfile, accessToken);
    }

    public static void goAuth(ActionEvent actionEvent) {
        try {
            // Verify there's no other overlays
            if (Overlay.current != null) {
                return;
            }

            if (Settings.lastSelectedAcc != null) {
                doAuth(Settings.lastSelectedAcc, Settings.accounts.get(Settings.lastSelectedAcc));

            } else {
                String login = loginField.getText();
                if (!login.isEmpty()) {
                    String password = passwordField.getText();
                    if (!password.isEmpty()) {
                        byte[] rsaPassword = Settings.encryptePassword(password);
                        if (Settings.accounts.size() == 3 && !Settings.isAdmin()) {
                            Processing.setError(new Exception("Превышено число аккаунтов") {
                                @Override
                                public String toString() {
                                    return "Превышено число аккаунтов";
                                }
                            });
                            Overlay.show(Processing.overlay, null);
                            Overlay.hide(2000, null);
                        } else {
                            addAccount(login, rsaPassword);
                            Settings.lastSelectedAcc = login;
                            doAuth(login, rsaPassword);
                        }
                    }
                }
            }
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static void doAuth(String login, byte[] rsaPassword) {
        Processing.resetOverlay();
        Overlay.show(Processing.overlay, event -> doAuthRequest(login, rsaPassword));
    }

    private static void doAuthRequest(String login, byte[] rsaPassword) {
        Processing.makeAuthRequest(login, rsaPassword, result -> {
            playerProfile = result.pp;
            accessToken = result.accessToken;
            profilesBox.setVisible(true);
            authButton.setVisible(false);
            playButton.setVisible(true);
            // Update profiles list and hide overlay
            Settings.lastProfiles = result.profiles;
            //setPasswordSaved();
            updateProfilesList(result.profiles);
            Overlay.hide(0, null);
        }, () -> {
            removeAccount(login);
        });
    }


    public static void doUpdate(SignedObjectHolder<ClientProfile> profile, PlayerProfile pp, String accessToken) {
        boolean digest = profile.object.isUpdateFastCheck();

        // Update JVM dir
        Update.resetOverlay("Обновление файлов JVM");
        String jvmCustomDir = profile.object.block.getEntryValue("jvmVersion", StringConfigEntry.class) + jvmDirName;
        Overlay.show(Update.overlay, event -> {
            Path jvmDir = Settings.updatesDir.resolve(jvmCustomDir);
            makeUpdateRequest(jvmCustomDir, jvmDir, null, digest, jvmHDir -> {
                Settings.lastHDirs.put(jvmDirName, jvmHDir);

                // Update asset dir
                Update.resetOverlay("Обновление файлов ресурсов");
                String assetDirName = profile.object.block.getEntryValue("assetDir", StringConfigEntry.class);
                Path assetDir = Settings.updatesDir.resolve(assetDirName);
                FileNameMatcher assetMatcher = profile.object.getAssetUpdateMatcher();
                makeUpdateRequest(assetDirName, assetDir, assetMatcher, digest, assetHDir -> {
                    Settings.lastHDirs.put(assetDirName, assetHDir);

                    // Update client dir
                    Update.resetOverlay("Обновление файлов клиента");
                    String clientDirName = profile.object.block.getEntryValue("dir", StringConfigEntry.class);
                    Path clientDir = Settings.updatesDir.resolve(clientDirName);
                    FileNameMatcher clientMatcher = profile.object.getClientUpdateMatcher();
                    makeUpdateRequest(clientDirName, clientDir, clientMatcher, digest, clientHDir -> {
                        Settings.lastHDirs.put(clientDirName, clientHDir);
                        doLaunchClient(jvmDir, jvmHDir, assetDir, assetHDir, clientDir, clientHDir, profile, pp, accessToken);
                    });
                });
            });
        });
    }

    public static void doLaunchClient(Path jvmDir, SignedObjectHolder<HashedDir> jvmHDir, Path assetDir, SignedObjectHolder<HashedDir> assetHDir, Path clientDir, SignedObjectHolder<HashedDir> clientHDir, SignedObjectHolder<ClientProfile> profile, PlayerProfile pp, String accessToken) {
        Processing.resetOverlay();
        Overlay.swap(0, Processing.overlay, event ->
                launchClient(jvmDir, jvmHDir, assetHDir, clientHDir, profile, new ClientLauncher.Params(Settings.lastSign, assetDir, clientDir, pp, accessToken, Settings.autoEnter, Settings.fullScreen, Settings.ram, 0, 0), Dialog::doDebugClient)
        );
    }

    public static void doDebugClient(Process process) {
        if (!LogHelper.isDebugEnabled()) {
            javafx.application.Platform.exit();
            return;
        }

        // Switch to debug overlay
        Debug.resetOverlay();
        Overlay.swap(0, Debug.overlay, event -> Debug.debugProcess(process));
    }

    public static void updateProfilesList(List<SignedObjectHolder<ClientProfile>> profiles) {
        // Set profiles items
        profilesBox.setItems(FXCollections.observableList(profiles));
        for (SignedObjectHolder<ClientProfile> profile : profiles) {
            pingers.put(profile.object, new ServerPinger(profile.object.getServerSocketAddress(), profile.object.getVersion()));
        }

        // Set profiles selection model
        SingleSelectionModel<SignedObjectHolder<ClientProfile>> sm = profilesBox.getSelectionModel();
        // Store selected profile index
        sm.selectedIndexProperty().addListener((o, ov, nv) -> Settings.lastSelectedProfile = nv.intValue());
        // Restore selected item
        sm.select(Settings.lastSelectedProfile < profiles.size() ? Settings.lastSelectedProfile : 0);
    }

    public static void verifyLauncher() {
        Processing.resetOverlay();
        Overlay.show(Processing.overlay, event -> makeLauncherRequest(result -> {
            if (result.getBinary() != null) {
                try {
                    LauncherRequest.update(ConfigBin.getConfig(), result);
                } catch (SignatureException | IOException e) {
                    throw new RuntimeException(e);
                }
                return;
            }

            // Parse response
            Settings.lastSign = result.getSign();
            if (Settings.offline) {
                try {
                    initOffline();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            doAuthLast();
            Overlay.hide(0, null);
        }));
    }

    public static void doAuthLast() {
        if (Settings.lastSelectedAcc != null) {
            selectAccount(Settings.lastSelectedAcc).handle(null);
            doAuthRequest(Settings.lastSelectedAcc, Settings.accounts.get(Settings.lastSelectedAcc));
        }
    }
}
