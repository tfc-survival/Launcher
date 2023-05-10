package launcher.runtime.dialog;

import javafx.animation.FadeTransition;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.util.Duration;

import static launcher.runtime.dialog.Dialog.*;

public class Overlay {
    public static Node current = null;

    public static void show(Pane newOverlay, EventHandler<ActionEvent> onFinished) {
        // Freeze root pane
        news.setDisable(true);
        authList.setDisable(true);
        current = newOverlay;

        // Show dim pane
        dimPane.setVisible(true);
        dimPane.toFront();

        // Fade dim pane
        fade(dimPane, 0, 0.0, 1.0, event -> {
            dimPane.requestFocus();
            dimPane.getChildren().add(newOverlay);

            // Fix overlay position
            newOverlay.setLayoutX((dimPane.getPrefWidth() - newOverlay.getPrefWidth()) / 2.0);
            newOverlay.setLayoutY((dimPane.getPrefHeight() - newOverlay.getPrefHeight()) / 2.0);

            // Fade in
            fade(newOverlay, 0, 0.0, 1.0, onFinished);
        });
    }

    public static void hide(int delay, EventHandler<ActionEvent> onFinished) {
        fade(current, delay, 1.0, 0.0, event -> {
            dimPane.getChildren().remove(current);
            fade(dimPane, 0, 1.0, 0.0, event1 -> {
                dimPane.setVisible(false);

                // Unfreeze root pane
                news.setDisable(false);
                authList.setDisable(false);
                rootPane.requestFocus();

                // Reset overlay state
                current = null;
                if (onFinished != null) {
                    onFinished.handle(event1);
                }
            });
        });
    }

    public static void swap(int delay, Pane newOverlay, EventHandler<ActionEvent> onFinished) {
        dimPane.toFront();
        fade(current, delay, 1.0, 0.0, event -> {
            dimPane.requestFocus();

            // Hide old overlay
            if (current != newOverlay) {
                ObservableList<Node> child = dimPane.getChildren();
                child.set(child.indexOf(current), newOverlay);
            }

            // Fix overlay position
            newOverlay.setLayoutX((dimPane.getPrefWidth() - newOverlay.getPrefWidth()) / 2.0);
            newOverlay.setLayoutY((dimPane.getPrefHeight() - newOverlay.getPrefHeight()) / 2.0);

            // Show new overlay
            current = newOverlay;
            fade(newOverlay, 0, 0.0, 1.0, onFinished);
        });
    }

    public static void fade(Node region, int delay, double from, double to, EventHandler<ActionEvent> onFinished) {
        FadeTransition transition = new FadeTransition(Duration.millis(100), region);
        if (onFinished != null)
            transition.setOnFinished(onFinished);

        // Launch transition
        transition.setDelay(Duration.millis(delay));
        transition.setFromValue(from);
        transition.setToValue(to);
        transition.play();
    }
}
