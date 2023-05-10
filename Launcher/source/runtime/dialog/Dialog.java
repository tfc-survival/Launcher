package launcher.runtime.dialog;

import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import launcher.Launcher;
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
import launcher.runtime.dialog.overlay.settings.Account;
import launcher.runtime.dialog.overlay.settings.CliParams;
import launcher.runtime.dialog.overlay.settings.Settings;
import launcher.serialize.config.entry.StringConfigEntry;
import launcher.serialize.signed.SignedObjectHolder;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
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
import static launcher.runtime.dialog.overlay.Processing.*;
import static launcher.runtime.dialog.overlay.Update.makeUpdateRequest;

public class Dialog {
    public static Pane rootPane;
    public static WebView news;
    public static ListView<Account> authList;
    public static Pane authPane;
    public static Pane dimPane;

    public static TextField loginField;
    public static PasswordField passwordField;
    public static CheckBox savePasswordBox;
    public static ComboBox<SignedObjectHolder<ClientProfile>> profilesBox;

    public static Map<ClientProfile, ServerPinger> pingers = new HashMap<>();

    public static void initDialog() throws IOException {
        // Lookup news WebView
        news = (WebView) rootPane.lookup("#news");
        WebEngine newsEngine = news.getEngine();
        newsEngine.setUserDataDirectory(Config.dir.resolve("webview").toFile());
        newsEngine.load(Config.newsURL);

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

        ((Button) rootPane.lookup("#goSettings")).setOnAction(Dialog::goSettings);

        // Init overlays
        Debug.initOverlay();
        Processing.initOverlay();
        Settings.initOverlay();
        Update.initOverlay();

        // Verify launcher & make request
        verifyLauncher();
    }

    private static void initAuthPane(Pane rootPane) {
        authList = (ListView<Account>) rootPane.lookup("#authList");
        authPane = (Pane) rootPane.lookup("#authPane");

        authList.setCellFactory(Dialog::newAccauntCell);
        authList.setItems(Settings.accounts);

        // Lookup login field
        loginField = (TextField) authPane.lookup("#login");
        loginField.setOnAction(Dialog::addNewAccount);


        // Lookup password field
        passwordField = (PasswordField) authPane.lookup("#password");
        passwordField.setOnAction(Dialog::addNewAccount);

        // Lookup action buttons
        ((Button) authPane.lookup("#addAccount")).setOnAction(Dialog::addNewAccount);
    }

    public static ListCell<Account> newAccauntCell(ListView<Account> listView) {
        final Pane authPane;
        try {
            authPane = loadFXML("dialog/authPane.fxml");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Lookup labels
        ImageView skin = (ImageView) authPane.lookup("#skin");
        Label login = (Label) authPane.lookup("#login");
        Button goAuth = (Button) authPane.lookup("#goAuth");
        Button removeAcc = (Button) authPane.lookup("#removeAcc");

        // Create and return new cell
        ListCell<Account> cell = new ListCell<Account>() {
            @Override
            protected void updateItem(Account item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : authPane);
                if (!empty) {
                    Image image = new Image("https://tfc.su/static/skins/" + item.login + ".png");
                    double w = image.getWidth();
                    double h = image.getHeight();
                    double slicedX = 8d / 64 * w;
                    double slicesY = 8d / 64 * h;

                    if (slicedX < 32) {
                        int scale = 4;
                        image = new Image("https://tfc.su/static/skins/" + item.login + ".png", w * scale, h * scale, false, false);
                        slicedX *= scale;
                        slicesY *= scale;
                    }

                    skin.setImage(image);
                    skin.setViewport(new Rectangle2D(slicedX, slicesY, slicedX, slicesY));
                    login.setText(item.login);
                    goAuth.setOnAction(goAuth(item));
                    authPane.setOnMouseClicked(predicate(goAuth(item), mouseEvent -> mouseEvent.getClickCount() == 2));
                    removeAcc.setOnAction(removeAcc(item, removeAcc));
                }
            }
        };
        cell.setText(null);
        return cell;
    }

    private static boolean removeAccPressedOnce = false;

    private static EventHandler<ActionEvent> removeAcc(Account account, Button removeAcc) {
        final String original = removeAcc.getText();
        return event -> {
            if (!removeAccPressedOnce) {
                removeAccPressedOnce = true;
                removeAcc.setText("Подтвердить вменяемость");
            } else {
                removeAccPressedOnce = false;
                Settings.accounts.remove(account);
                removeAcc.setText(original);
            }
        };
    }

    private static <A extends Event> EventHandler<A> predicate(EventHandler<A> f, Predicate<A> p) {
        return e -> {
            if (p.test(e))
                f.handle(e);
        };
    }

    private static <A extends Event> EventHandler<A> goAuth(Account account) {
        return event -> {
            try {
                // Verify there's no other overlays
                if (Overlay.current != null) {
                    return;
                }

                // Get profile
                SignedObjectHolder<ClientProfile> profile = profilesBox.getSelectionModel().getSelectedItem();
                if (profile == null) {
                    return; // No profile selected
                }

                doAuth(profile, account.login, account.rsaPassword);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
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
        if (!VerifyHelper.isValidUsername(Settings.accounts.get(0).login)) {
            loginField.setText(""); // Reset if not valid
        }

        // Disable password field
        passwordField.setDisable(true);
        passwordField.setPromptText("Недоступно");
        passwordField.setText("");

        // Switch news view to offline page
        URL offlineURL = Launcher.getResourceURL("dialog/offline/offline.html");
        news.getEngine().load(offlineURL.toString());
    }


    public static void addNewAccount(ActionEvent actionEvent) {
        try {
            // Verify there's no other overlays
            if (Overlay.current != null) {
                return;
            }

            // Get login
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
                        Settings.accounts.add(new Account(login, rsaPassword));
                        loginField.setText("");
                        passwordField.setText("");
                    }
                }
            }
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static void doAuth(SignedObjectHolder<ClientProfile> profile, String login, byte[] rsaPassword) {
        Processing.resetOverlay();
        Overlay.show(Processing.overlay, event -> makeAuthRequest(login, rsaPassword, result -> doUpdate(profile, result.pp, result.accessToken)));
    }


    public static void doUpdate(SignedObjectHolder<ClientProfile> profile, PlayerProfile pp, String accessToken) {
        boolean digest = profile.object.isUpdateFastCheck();

        // Update JVM dir
        Update.resetOverlay("Обновление файлов JVM");
        String jvmCustomDir = profile.object.block.getEntryValue("jvmVersion", StringConfigEntry.class) + jvmDirName;
        Overlay.swap(0, Update.overlay, event -> {
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
        SingleSelectionModel sm = profilesBox.getSelectionModel();
        sm.selectedIndexProperty().addListener(
                (o, ov, nv) -> Settings.profile = nv.intValue()); // Store selected profile index

        // Restore selected item
        int i = Settings.profile;
        sm.select(i < profiles.size() ? i : 0);
    }

    public static void verifyLauncher() {
        Processing.resetOverlay();
        Overlay.show(Processing.overlay, event -> makeLauncherRequest(result -> {
            if (result.getBinary() != null) {
                try {
                    LauncherRequest.update(Launcher.getConfig(), result);
                } catch (SignatureException | IOException e) {
                    throw new RuntimeException(e);
                }
                return;
            }

            // Parse response
            Settings.lastSign = result.getSign();
            Settings.lastProfiles = result.profiles;
            if (Settings.offline) {
                try {
                    initOffline();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            // Update profiles list and hide overlay
            updateProfilesList(result.profiles);
            Overlay.hide(0, event1 -> {
                if (CliParams.autoLogin)
                    goAuth(null);
            });
        }));
    }
}
