package launcher.runtime;

import javafx.concurrent.Task;
import launcher.helper.CommonHelper;
import launcher.request.Request;

import java.net.URISyntaxException;
import java.net.URL;
import java.util.concurrent.Callable;

import static launcher.runtime.LauncherApp.app;

public class Api {

    public static abstract class PublicTask<A> extends Task<A> {
        @Override
        public void updateMessage(String message) {
            super.updateMessage(message);
        }

        @Override
        public void updateProgress(long workDone, long max) {
            super.updateProgress(workDone, max);
        }
    }

    public static <A> PublicTask<A> newTask(Callable<A> r) {
        return new PublicTask<A>() {
            @Override
            protected A call() throws Exception {
                return r.call();
            }
        };
    }

    public static PublicTask<Void> newTask(Runnable r) {
        return new PublicTask<Void>() {
            @Override
            protected Void call() throws Exception {
                r.run();
                return null;
            }
        };
    }

    public static <A> PublicTask<A> newRequestTask(Request<A> request) {
        return newTask(() -> {
            try {
                return request.request();
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        });
    }

    public static <A> void startTask(Task<A> task) {
        CommonHelper.newThread("FX Task Thread", true, task).start();
    }

    public static void openURL(URL url) throws URISyntaxException {
        app.getHostServices().showDocument(String.valueOf(url.toURI()));
    }
}
