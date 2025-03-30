package launcher.runtime.dialog;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
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
import java.io.InputStream;
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
    public static Pane currentPane;

    public static Pane authPane;

    public static Pane accounts;

    //public static HBox authList;
    public static Pane dimPane;
    public static Pane toolBar;

    public static TextField loginField;
    public static PasswordField passwordField;
    public static HBox profilesBox;
    private static Button authButton;
    private static Button selectAccountButton;
    private static ImageView selectAccountButtonSkin;
    //private static Button playButton;

    public static Map<ClientProfile, ServerPinger> pingers = new HashMap<>();

    private static PlayerProfile playerProfile;
    private static String accessToken;

    private static boolean mousePressed = false;
    private static double relMouseX;
    private static double relMouseY;
    private static Button newAccountButton;
    private static Button cancelAuthButton;

    enum State {
        auth {
            @Override
            public void enter() {
                selectAccountButton.setVisible(false);
                authPane.setVisible(true);
                authPane.requestFocus();
                cancelAuthButton.setVisible(Settings.accounts.size() > 0);
            }

            @Override
            public void exit() {
                authPane.setVisible(false);
            }
        },
        profileSelection {
            @Override
            public void enter() {
                selectAccountButton.setVisible(true);
                setSkinImage(selectAccountButtonSkin, Settings.lastSelectedAcc);
                selectAccountButton.setText(Settings.lastSelectedAcc);
                profilesBox.setVisible(true);
                profilesBox.requestFocus();
            }

            @Override
            public void exit() {
                profilesBox.setVisible(false);
            }
        },
        accountSelection {
            @Override
            public void enter() {
                selectAccountButton.setVisible(false);
                accounts.setVisible(true);
                accounts.requestFocus();
                if (Settings.accounts.size() >= 3) {
                    accounts.getChildren().remove(newAccountButton);
                }
            }

            @Override
            public void exit() {
                accounts.setVisible(false);
            }
        };

        public abstract void enter();

        public abstract void exit();
    }

    public static State state = State.auth;

    public static void changeState(State next) {
        state.exit();
        state = next;
        state.enter();
    }


    public static void initDialog() throws IOException {
        rootPane = loadFXML("dialog/dialog.fxml");
        authPane = (Pane) rootPane.lookup("#authPane");
        accounts = (Pane) rootPane.lookup("#accounts");
        toolBar = (Pane) rootPane.lookup("#toolBar");

        //authList = (HBox) rootPane.lookup("#authList");
        Settings.accounts.keySet().forEach(Dialog::addAccButton);
        newAccountButton = (Button) accounts.lookup("#newAccountButton");
        newAccountButton.setOnAction(event -> changeState(State.auth));

        cancelAuthButton = (Button) authPane.lookup("#cancelAuthButton");
        cancelAuthButton.setOnAction(event -> changeState(State.accountSelection));

        //authList.setSpacing(5);

        // Lookup login field
        loginField = (TextField) authPane.lookup("#login");
        loginField.setOnAction(Dialog::goAuth);


        // Lookup password field
        passwordField = (PasswordField) authPane.lookup("#password");
        passwordField.setOnAction(Dialog::goAuth);

        // Lookup action buttons
        authButton = (Button) authPane.lookup("#goAuth");
        authButton.setOnAction(Dialog::goAuth);

        selectAccountButton = (Button) rootPane.lookup("#selectAccountButton");
        selectAccountButton.setOnAction(event -> changeState(State.accountSelection));
        selectAccountButton.setText("" + Settings.lastSelectedAcc);
        selectAccountButtonSkin = (ImageView) ((Pane) selectAccountButton.getGraphic()).getChildren().get(0);
        //playButton = (Button) authPane.lookup("#goPlay");
        //playButton.setOnAction(Dialog::goPlay);

        rootPane.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
            mousePressed = true;
            relMouseX = event.getX();
            relMouseY = event.getY();
        });

        rootPane.addEventHandler(MouseEvent.MOUSE_RELEASED, event -> {
            mousePressed = false;
        });
        rootPane.addEventHandler(MouseEvent.MOUSE_DRAGGED, event -> {
            rootPane.getScene().getWindow().setX(event.getScreenX() - relMouseX);
            rootPane.getScene().getWindow().setY(event.getScreenY() - relMouseY);
        });

        dimPane = (Pane) rootPane.lookup("#dim");

        // Lookup profiles combobox
        profilesBox = (HBox) rootPane.lookup("#profilesBox");


        // Lookup hyperlink text and actions
        Hyperlink link = (Hyperlink) rootPane.lookup("#link");
        link.setOnAction(event -> {
            try {
                app.getHostServices().showDocument(String.valueOf(Config.linkURL.toURI()));
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
        });

        Button goSettings = (Button) rootPane.lookup("#goSettings");
        //goSettings.setGraphic(new ImageView(new Image(Dialog.class.getResourceAsStream("/runtime/dialog/settings.png"))));
        goSettings.setOnAction(Dialog::goSettings);

        Button close = (Button) rootPane.lookup("#close");
        //close.setGraphic(new ImageView(new Image(Dialog.class.getResourceAsStream("/runtime/dialog/close.png"))));
        close.setOnAction(Dialog::close);

        // Init overlays
        Debug.initOverlay();
        Processing.initOverlay();
        Settings.initOverlay();
        Update.initOverlay();

        // Verify launcher & make request
        verifyLauncher();

    }

    private static void close(ActionEvent actionEvent) {
        Platform.exit();
    }

    private static void addAccount(String login, byte[] rsaPassword) {
        if (!Settings.accounts.containsKey(login)) {
            Settings.accounts.put(login, rsaPassword);
            addAccButton(login);
        }
    }

    private static void setSkinImage(ImageView to, String login) {
        Image image = new Image("https://tfc.su/static/skins/" + login + ".png");
        double w = image.getWidth();
        double h = image.getHeight();
        double slicedX = 8d / 64 * w;
        double slicesY = 8d / 64 * h;

        if (slicedX < 48) {
            int scale = 6;
            image = new Image("https://tfc.su/static/skins/" + login + ".png", w * scale, h * scale, false, false);
            slicedX *= scale;
            slicesY *= scale;
        }

        to.setImage(image);
        if (image.isError()) {
            to.setImage(new Image(Dialog.class.getResourceAsStream("/runtime/steve.png"), 48, 48, false, false));
        }
        to.setViewport(new Rectangle2D(slicedX, slicesY, slicedX, slicesY));
        to.setFitHeight(48);
        to.setFitWidth(48);
    }

    private static void addAccButton(String login) {
        try {
            Button accountElement = loadFXML("dialog/accountButton.fxml");
            Pane graphic = (Pane) accountElement.getGraphic();

            Rectangle rect = new Rectangle(200, 300);
            rect.setArcHeight(20);
            rect.setArcWidth(20);

            setSkinImage((ImageView) graphic.lookup("#skin"), login);

            ((Label) graphic.lookup("#nickname")).setText(login);

            accountElement.setOnAction(selectAccount(login));

            accounts.getChildren().add(0, accountElement);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private static void removeAccount(String login) {
        if (Settings.accounts.containsKey(login)) {
            Settings.accounts.remove(login);
            Settings.lastSelectedAcc = null;
            accounts.getChildren().removeIf(e -> {
                Node maybeNickname = ((Button) e).getGraphic().lookup("#nickname");
                if (maybeNickname instanceof Label)
                    return login.equals(((Label) maybeNickname).getText());
                else
                    return false;
            });
            if (Settings.accounts.isEmpty())
                changeState(State.auth);
            else
                changeState(State.accountSelection);
        }
    }

    private static EventHandler<ActionEvent> selectAccount(String login) {
        return __ -> {
            Settings.lastSelectedAcc = login;
            doAuth(Settings.lastSelectedAcc, Settings.accounts.get(Settings.lastSelectedAcc));
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

    public static void pingServer(Circle statusCircle, SignedObjectHolder<ClientProfile> profile) {
        setServerStatus(statusCircle, javafx.scene.paint.Color.GREY, "...");
        Task<ServerPinger.Result> task = newTask(() -> pingers.get(profile.object).ping());
        task.setOnSucceeded((event) -> {
            ServerPinger.Result result = task.getValue();
            Color color = result.isOverfilled() ? Color.YELLOW : Color.GREEN;
            setServerStatus(statusCircle, color, java.lang.String.format("%d из %d", result.onlinePlayers, result.maxPlayers));
        });
        task.setOnFailed(event -> setServerStatus(statusCircle, javafx.scene.paint.Color.RED, "Недоступен"));
        startTask(task);
    }

    public static void setServerStatus(Circle statusCircle, javafx.scene.paint.Color color, String description) {
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

//        SignedObjectHolder<ClientProfile> profile = profilesBox.getSelectionModel().getSelectedItem();
//        if (profile == null) {
//            return;
//        }
//
//        doUpdate(profile, playerProfile, accessToken);
    }

    public static void goAuth(ActionEvent actionEvent) {
        try {
            // Verify there's no other overlays
            if (Overlay.current != null) {
                return;
            }

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
            // Update profiles list and hide overlay
            Settings.lastProfiles = result.profiles;
            //setPasswordSaved();
            loginField.setText("");
            passwordField.setText("");
            updateProfilesList(result.profiles);
            Overlay.hide(0, null);
            changeState(State.profileSelection);
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
        Image defaultLogo = new Image(Dialog.class.getResourceAsStream("/runtime/profiles/default.png"));
        profilesBox.getChildren().clear();
        for (SignedObjectHolder<ClientProfile> profile : profiles) {
            pingers.put(profile.object, new ServerPinger(profile.object.getServerSocketAddress(), profile.object.getVersion()));

            String title = profile.object.getTitle();

            try {
                VBox statusBox = loadFXML("dialog/profileCell.fxml");
                InputStream maybeLogo = Dialog.class.getResourceAsStream("/runtime/profiles/" + title + ".png");
                ((ImageView) statusBox.lookup("#logo")).setImage(maybeLogo == null ? defaultLogo : new Image(maybeLogo));
                ((Label) statusBox.lookup("#title")).setText(title);

                statusBox.setOnMouseClicked(event -> {
                    if (Overlay.current == null)
                        doUpdate(profile, playerProfile, accessToken);
                });

                pingServer(((Circle) statusBox.lookup("#serverStatus")), profile);

                Rectangle rect = new Rectangle(200, 300);
                rect.setArcHeight(20);
                rect.setArcWidth(20);
                statusBox.setClip(rect);
                profilesBox.getChildren().add(statusBox);

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
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

            //Overlay.hide(0, null);
            doAuthLast();
        }));
    }

    public static void doAuthLast() {
        if (Settings.lastSelectedAcc != null) {
            selectAccount(Settings.lastSelectedAcc).handle(null);
        } else {
            Overlay.hide(0, null);
            if (Settings.accounts.isEmpty())
                changeState(State.auth);
            else
                changeState(State.accountSelection);
        }
    }
}
