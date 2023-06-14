package launcher;

import launcher.helper.*;
import launcher.helper.SecurityHelper.DigestAlgorithm;
import launcher.runtime.Init;
import launcher.serialize.HInput;
import launcher.serialize.HOutput;
import launcher.serialize.stream.StreamObject;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.ComputerSystem;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class Launcher {

    public static final boolean dev = false;
    // Version info
    @LauncherAPI
    public static final String VERSION = "1.7.5.2";
    @LauncherAPI
    public static final String BUILD = readBuildNumber();
    @LauncherAPI
    public static final int PROTOCOL_MAGIC = 0x724724_00 + 23;
    // Constants
    @LauncherAPI
    public static final String RUNTIME_DIR = "runtime";
    @LauncherAPI
    public static final String CONFIG_FILE = "config.bin";
    @LauncherAPI
    public static final String INIT_SCRIPT_FILE = "init.js";
    private static final AtomicReference<Config> CONFIG = new AtomicReference<>();
    // Instance
    private final AtomicBoolean started = new AtomicBoolean(false);

    private Launcher() {
    }

    public static byte[] hash1(String e) {
        try {
            MessageDigest md1 = MessageDigest.getInstance("SHA-512");
            md1.update(new byte[]{-10, 127, 90, 126, -78, 119, -13, -30, -101, 104, -94, 119, -66, 80, -17, 36});
            return md1.digest(e.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exc) {
            throw new RuntimeException(exc);
        }
    }

    @LauncherAPI
    public static byte[] getHWID() {
        SystemInfo systemInfo = new SystemInfo();
        CentralProcessor.ProcessorIdentifier processorIdentifier = systemInfo.getHardware().getProcessor().getProcessorIdentifier();
        ComputerSystem computerSystem = systemInfo.getHardware().getComputerSystem();

        String processorID = processorIdentifier.getProcessorID();
        String processorName = processorIdentifier.getName();
        String hardwareUUID = computerSystem.getHardwareUUID();
        String boardSerialNumber = computerSystem.getBaseboard().getSerialNumber();

        return
                hash1(processorID + "/" +
                        processorName + "/" +
                        hardwareUUID + "/" +
                        boardSerialNumber);
    }

    @LauncherAPI
    public static Config getConfig() {
        Config config = CONFIG.get();
        if (config == null) {
            try (HInput input = new HInput(IOHelper.newInput(IOHelper.getResourceURL(CONFIG_FILE)))) {
                config = new Config(input);
            } catch (IOException | InvalidKeySpecException e) {
                throw new SecurityException(e);
            }
            CONFIG.set(config);
        }
        return config;
    }

    @LauncherAPI
    public static URL getResourceURL(String name) throws IOException {
        Config config = getConfig();
        URL url = IOHelper.getResourceURL(RUNTIME_DIR + '/' + name);
        if (!dev) {
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
        return VERSION; // Because Java constants are known at compile-time
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

    private static String readBuildNumber() {
        try {
            return IOHelper.request(IOHelper.getResourceURL("buildnumber"));
        } catch (IOException ignored) {
            return "dev"; // Maybe dev env?
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

    public static final class Config extends StreamObject {
        @LauncherAPI
        public static final String ADDRESS_OVERRIDE_PROPERTY = "launcher.addressOverride";
        @LauncherAPI
        public static final String ADDRESS_OVERRIDE = System.getProperty(ADDRESS_OVERRIDE_PROPERTY, null);

        // Instance
        @LauncherAPI
        public final InetSocketAddress address;
        @LauncherAPI
        public final RSAPublicKey publicKey;
        @LauncherAPI
        public final Map<String, byte[]> runtime;

        @LauncherAPI
        @SuppressWarnings("AssignmentToCollectionOrArrayFieldFromParameter")
        public Config(String address, int port, RSAPublicKey publicKey, Map<String, byte[]> runtime) {
            this.address = InetSocketAddress.createUnresolved(address, port);
            this.publicKey = Objects.requireNonNull(publicKey, "publicKey");
            this.runtime = Collections.unmodifiableMap(new HashMap<>(runtime));
        }

        @LauncherAPI
        public Config(HInput input) throws IOException, InvalidKeySpecException {
            String localAddress = input.readASCII(255);
            address = InetSocketAddress.createUnresolved(
                    ADDRESS_OVERRIDE == null ? localAddress : ADDRESS_OVERRIDE, input.readLength(65535));
            publicKey = SecurityHelper.toPublicRSAKey(input.readByteArray(SecurityHelper.CRYPTO_MAX_LENGTH));

            // Read signed runtime
            int count = input.readLength(0);
            Map<String, byte[]> localResources = new HashMap<>(count);
            for (int i = 0; i < count; i++) {
                String name = input.readString(255);
                VerifyHelper.putIfAbsent(localResources, name,
                        input.readByteArray(SecurityHelper.CRYPTO_MAX_LENGTH),
                        String.format("Duplicate runtime resource: '%s'", name));
            }
            runtime = Collections.unmodifiableMap(localResources);

            // Print warning if address override is enabled
            if (ADDRESS_OVERRIDE != null) {
                LogHelper.warning("Address override is enabled: '%s'", ADDRESS_OVERRIDE);
            }
        }

        @Override
        public void write(HOutput output) throws IOException {
            output.writeASCII(address.getHostString(), 255);
            output.writeLength(address.getPort(), 65535);
            output.writeByteArray(publicKey.getEncoded(), SecurityHelper.CRYPTO_MAX_LENGTH);

            // Write signed runtime
            Set<Entry<String, byte[]>> entrySet = runtime.entrySet();
            output.writeLength(entrySet.size(), 0);
            for (Entry<String, byte[]> entry : runtime.entrySet()) {
                output.writeString(entry.getKey(), 255);
                output.writeByteArray(entry.getValue(), SecurityHelper.CRYPTO_MAX_LENGTH);
            }
        }
    }
}
