package launcher.runtime.dialog.overlay;

import javafx.concurrent.Task;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import launcher.Launcher;
import launcher.client.ClientLauncher;
import launcher.client.ClientProfile;
import launcher.client.PlayerProfile;
import launcher.hasher.HashedDir;
import launcher.helper.LogHelper;
import launcher.helper.SecurityHelper;
import launcher.helper.VerifyHelper;
import launcher.request.Request;
import launcher.request.auth.AuthRequest;
import launcher.request.update.LauncherRequest;
import launcher.runtime.dialog.Overlay;
import launcher.runtime.dialog.overlay.settings.Settings;
import launcher.serialize.signed.SignedObjectHolder;

import java.io.IOException;
import java.nio.file.Path;
import java.security.SignatureException;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import static launcher.runtime.Api.*;
import static launcher.runtime.Init.loadFXML;

public class Processing {
    public static Pane overlay = null;
    public static ImageView spinner = null;
    public static Label description = null;
    public static Image processingImage = null;
    public static Image errorImage = null;

    public static void initOverlay() throws IOException {
        overlay = loadFXML("dialog/overlay/processing/processing.fxml");

        // Lookup nodes
        spinner = (ImageView) overlay.lookup("#spinner");
        description = (Label) overlay.lookup("#description");

        // Set images
        processingImage = new Image(Launcher.getResourceURL("dialog/overlay/processing/spinner.gif").toString());
        errorImage = new Image(Launcher.getResourceURL("dialog/overlay/processing/error.png").toString());
    }

    public static void resetOverlay() {
        spinner.setImage(processingImage);
        description.getStyleClass().remove("error");
        description.setText("...");
    }

    public static void setError(Throwable e) {
        LogHelper.error(e);
        description.textProperty().unbind();
        spinner.setImage(errorImage);
        description.getStyleClass().add("error");
        description.setText(e.toString());
    }

    public static <A> void setTaskProperties(Task<A> task, Consumer<A> callback, Runnable errorCallback, boolean hide) {
        description.textProperty().bind(task.messageProperty());
        task.setOnFailed(event -> {
            description.textProperty().unbind();
            setError(task.getException());
            if (hide) {
                Overlay.hide(2500, errorCallback);
            } else if (errorCallback != null) {
                errorCallback.run();
            }
        });
        task.setOnSucceeded(event -> {
            description.textProperty().unbind();
            if (callback != null) {
                callback.accept(task.getValue());
            }
        });
    }


    public static LauncherRequest.Result offlineLauncherRequest() throws IOException, SignatureException {
        if (Settings.lastSign == null || Settings.lastProfiles.isEmpty()) {
            Request.requestError("Запуск в оффлайн-режиме невозможен");
        }

        // Verify launcher signature
        SecurityHelper.verifySign(LauncherRequest.BINARY_PATH, Settings.lastSign, Launcher.getConfig().publicKey);

        // Return last sign and profiles
        return new LauncherRequest.Result(null, Settings.lastSign);
    }

    public static Callable<AuthRequest.Result> offlineAuthRequest(String login) {
        return () -> {
            if (!VerifyHelper.isValidUsername(login)) {
                Request.requestError("Имя пользователя некорректно");
            }

            // Return offline profile and random access token
            return new AuthRequest.Result(PlayerProfile.newOfflineProfile(login), SecurityHelper.randomStringToken(), Settings.lastProfiles);
        };
    }

    /* Export functions */
    public static void makeLauncherRequest(Consumer<LauncherRequest.Result> callback) {
        PublicTask<LauncherRequest.Result> task = Settings.offline ? newTask(Processing::offlineLauncherRequest) :
                newRequestTask(new LauncherRequest());

        // Set task properties and start
        Processing.setTaskProperties(task, callback, () -> {
            if (Settings.offline) {
                return;
            }

            // Repeat request, but in offline mode
            Settings.offline = true;
            Overlay.swap(2500, Processing.overlay, e -> makeLauncherRequest(callback));
        }, false);
        task.updateMessage("Обновление списка серверов");
        startTask(task);
    }

    public static void makeAuthRequest(String login, byte[] rsaPassword, Consumer<AuthRequest.Result> onSuccess, Runnable onError) {
        PublicTask<AuthRequest.Result> task = rsaPassword == null ? newTask(offlineAuthRequest(login)) : newRequestTask(new AuthRequest(login, rsaPassword));
        Processing.setTaskProperties(task, onSuccess, onError, true);
        task.updateMessage("Авторизация на сервере");
        startTask(task);
    }

    public static void launchClient(Path jvmDir, SignedObjectHolder<HashedDir> jvmHDir, SignedObjectHolder<HashedDir> assetHDir, SignedObjectHolder<HashedDir> clientHDir, SignedObjectHolder<ClientProfile> profile, ClientLauncher.Params params, Consumer<Process> callback) {
        PublicTask<Process> task = newTask(() -> {
            try {
                return ClientLauncher.launch(jvmDir, jvmHDir, assetHDir, clientHDir, profile, params, LogHelper.isDebugEnabled());
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        });
        Processing.setTaskProperties(task, callback, null, true);
        task.updateMessage("Запуск выбранного клиента");
        startTask(task);
    }
}
