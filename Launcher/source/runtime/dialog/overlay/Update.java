package launcher.runtime.dialog.overlay;

import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.Pane;
import launcher.client.ClientLauncher;
import launcher.hasher.FileNameMatcher;
import launcher.hasher.HashedDir;
import launcher.helper.LogHelper;
import launcher.request.Request;
import launcher.request.update.UpdateRequest;
import launcher.runtime.dialog.Overlay;
import launcher.runtime.dialog.overlay.settings.Settings;
import launcher.serialize.signed.SignedObjectHolder;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import static launcher.runtime.Api.*;
import static launcher.runtime.Init.loadFXML;

public class Update {
    public static Pane overlay = null;
    public static Label title = null;
    public static Label description = null;
    public static ProgressBar progress = null;


    /* State and overlay functions */
    public static void initOverlay() throws IOException {
        overlay = loadFXML("dialog/overlay/update/update.fxml");

        // Lookup nodes
        title = (Label) overlay.lookup("#utitle");
        description = (Label) overlay.lookup("#description");
        progress = (ProgressBar) overlay.lookup("#progress");
    }

    public static void resetOverlay(String title) {
        Update.title.setText(title);
        description.getStyleClass().remove("error");
        description.setText("...");
        progress.setProgress(-1.0);
    }

    public static void setError(Throwable e) {
        LogHelper.error(e);

        // Set error description
        description.getStyleClass().add("error");
        description.setText(e.toString());
    }

    public static void stateCallback(PublicTask<?> task, UpdateRequest.State state) {
        double bps = state.getBps();
        Duration estimated = state.getEstimatedTime();
        long estimatedSeconds = estimated == null ? 0 : estimated.getSeconds();
        long estimatedHH = (estimatedSeconds / 3600) | 0;
        long estimatedMM = ((estimatedSeconds % 3600) / 60) | 0;
        long estimatedSS = (estimatedSeconds % 60) | 0;
        task.updateMessage(java.lang.String.format(
                "Файл: %s%n" + // File line
                        "Загружено (Файл): %.2f / %.2f MiB.%n" + // File downloaded line
                        "Загружено (Всего): %.2f / %.2f MiB.%n" + // Total downloaded line
                        "%n" +
                        "Средняя скорость: %.1f Kbps%n" + // Speed line
                        "Примерно осталось: %d:%02d:%02d%n", // Estimated line

                // Formatting
                state.filePath, // File path
                state.getFileDownloadedMiB() + /* Fuck nashorn */ 0.0, state.getFileSizeMiB() + 0.0, // File downloaded
                state.getTotalDownloadedMiB() + /* Fuck nashorn */ 0.0, state.getTotalSizeMiB() + 0.0, // Total downloaded
                bps <= 0.0 ? 0.0 : bps / 1024.0, // Speed
                estimatedHH, estimatedMM, estimatedSS // Estimated (hh:mm:ss)
        ));
        task.updateProgress(state.totalDownloaded, state.totalSize);
    }

    public static void setTaskProperties(PublicTask<SignedObjectHolder<HashedDir>> task, UpdateRequest request, Consumer<SignedObjectHolder<HashedDir>> callback) {
        description.textProperty().bind(task.messageProperty());
        progress.progressProperty().bind(task.progressProperty());
        if (request != null)
            request.setStateCallback(state -> stateCallback(task, state));
        task.setOnFailed(event -> {
            description.textProperty().unbind();
            progress.progressProperty().unbind();
            setError(task.getException());
            Overlay.hide(2500, null);
        });
        task.setOnSucceeded(event -> {
            description.textProperty().unbind();
            progress.progressProperty().unbind();
            if (callback != null) {
                callback.accept(task.getValue());
            }
        });
    }


    public static Callable<SignedObjectHolder<HashedDir>> offlineUpdateRequest(String dirName, Path dir, FileNameMatcher matcher, boolean digest) {
        return () -> {
            SignedObjectHolder<HashedDir> hdir = Settings.lastHDirs.get(dirName);
            if (hdir == null) {
                Request.requestError(java.lang.String.format("Директории '%s' нет в кэше", dirName));
            }

            // Verify dir with matcher using ClientLauncher's API
            ClientLauncher.verifyHDir(dir, hdir.object, matcher, digest);
            return hdir;
        };
    }

    /* Export functions */
    public static void makeUpdateRequest(String dirName, Path dir, FileNameMatcher matcher, boolean digest, Consumer<SignedObjectHolder<HashedDir>> callback) {
        UpdateRequest request = Settings.offline ? null : new UpdateRequest(dirName, dir, matcher, digest);

        PublicTask<SignedObjectHolder<HashedDir>> task = Settings.offline ? newTask(offlineUpdateRequest(dirName, dir, matcher, digest)) : newRequestTask(request);

        // Set task properties and start
        setTaskProperties(task, request, callback);
        task.updateMessage("Состояние: Хеширование");
        task.updateProgress(-1, -1);
        startTask(task);
    }
}
