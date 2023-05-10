package launcher.runtime.dialog.overlay;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.Pane;
import launcher.helper.IOHelper;
import launcher.runtime.dialog.Overlay;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.function.Consumer;

import static launcher.runtime.Api.newTask;
import static launcher.runtime.Api.startTask;
import static launcher.runtime.Init.loadFXML;

public class Debug {
    public static Pane overlay = null;
    public static TextArea output = null;
    public static Button copy = null;
    public static Button action = null;
    public static Process process = null;

    public static void initOverlay() throws IOException {
        overlay = loadFXML("dialog/overlay/debug/debug.fxml");

        // Lookup output
        output = (TextArea) overlay.lookup("#output");
        output.setEditable(false);

        // Lookup copy button
        copy = (Button) overlay.lookup("#copy");
        copy.setOnAction(event -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(output.getText());

            // Set clipboard content
            javafx.scene.input.Clipboard.getSystemClipboard().
                    setContent(content);
        });

        /// Lookup action button
        action = (Button) overlay.lookup("#action");
        action.setOnAction(event -> {
            Process process = Debug.process;
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
                updateActionButton(true);
                return;
            }

            // Hide overlay
            Overlay.hide(0, null);
        });
    }

    public static void resetOverlay() {
        output.clear();
        action.setText("");
        action.getStyleClass().remove("kill");
        action.getStyleClass().add("close");
    }

    public static void append(String text) {
        output.appendText(text);
    }

    public static void updateActionButton(boolean forceClose) {
        Process process = Debug.process;
        boolean alive = !forceClose &&
                process != null && process.isAlive();

        // Decide what we update to
        String text = alive ? "Убить" : "Закрыть";
        String addClass = alive ? "kill" : "close";
        String removeClass = alive ? "close" : "kill";

        // Update button
        action.setText(text);
        action.getStyleClass().remove(removeClass);
        action.getStyleClass().add(addClass);
    }

    /* Export functions */
    public static void debugProcess(Process process) {
        Debug.process = process;
        updateActionButton(false);

        // Create new task
        Task<Integer> task = newTask(() -> {
            char[] buffer = IOHelper.newCharBuffer();
            BufferedReader reader = IOHelper.newReader(process.getInputStream(), Charset.defaultCharset());
            Consumer<String> appendFunction = (String line) -> Platform.runLater(() -> Debug.append(line));
            for (int length = reader.read(buffer); length >= 0; length = reader.read(buffer)) {
                appendFunction.accept(new String(buffer, 0, length));
            }

            // So we wait for exit code
            return process.waitFor();
        });

        // Set completion handlers
        task.setOnFailed(event -> {
            updateActionButton(true);
            append(java.lang.System.lineSeparator() + task.getException());
        });
        task.setOnSucceeded(event -> {
            updateActionButton(false);
            append(java.lang.System.lineSeparator() + "Exit code " + task.getValue());
        });

        startTask(task);
    }
}
