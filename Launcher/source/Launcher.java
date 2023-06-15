package launcher;

import launcher.helper.*;
import launcher.helper.SecurityHelper.DigestAlgorithm;
import launcher.runtime.Init;

import java.io.IOException;
import java.net.URL;
import java.nio.file.NoSuchFileException;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Launcher {
    // Constants
    @LauncherAPI
    public static final String RUNTIME_DIR = "runtime";
    @LauncherAPI
    public static final String INIT_SCRIPT_FILE = "init.js";
    // Instance
    private final AtomicBoolean started = new AtomicBoolean(false);

    private Launcher() {
    }

    @LauncherAPI
    public static URL getResourceURL(String name) throws IOException {
        Config config = Config.getConfig();
        URL url = IOHelper.getResourceURL(RUNTIME_DIR + '/' + name);
        if (!CommonHelper.dev) {
            byte[] validDigest = config.runtime.get(name);
            if (validDigest == null) { // No such resource digest
                throw new NoSuchFileException(name);
            }

            // Resolve URL and verify digest
            if (!Arrays.equals(validDigest, SecurityHelper.digest(DigestAlgorithm.MD5, url))) {
                throw new NoSuchFileException(name); // Digest mismatch
            }
        }

        // Return verified URL
        return url;
    }

    @LauncherAPI
    @SuppressWarnings({"SameReturnValue", "MethodReturnAlwaysConstant"})
    public static String getVersion() {
        return CommonHelper.VERSION; // Because Java constants are known at compile-time
    }

    public static void main(String... args) throws Throwable {
        JVMHelper.verifySystemProperties(Launcher.class, true);
        LogHelper.printVersion("Launcher");

        // Start Launcher
        try {
            new Launcher().start(args);
        } catch (Throwable exc) {
            LogHelper.error(exc);
        }
    }

    @LauncherAPI
    public void start(String... args) throws Throwable {
        Objects.requireNonNull(args, "args");
        if (started.getAndSet(true)) {
            throw new IllegalStateException("Launcher has been already started");
        }

        // Load init.js script
        LogHelper.info("Invoking start() function");
        Init.start(args);
    }

}
