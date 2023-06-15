package launcher;

import launcher.helper.IOHelper;
import launcher.helper.LogHelper;
import launcher.helper.SecurityHelper;
import launcher.helper.VerifyHelper;
import launcher.serialize.HInput;
import launcher.serialize.HOutput;
import launcher.serialize.stream.StreamObject;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public final class Config extends StreamObject {
    @LauncherAPI
    public static final String ADDRESS_OVERRIDE_PROPERTY = "launcher.addressOverride";
    @LauncherAPI
    public static final String ADDRESS_OVERRIDE = System.getProperty(ADDRESS_OVERRIDE_PROPERTY, null);
    @LauncherAPI
    public static final String CONFIG_FILE = "config.bin";
    private static final AtomicReference<Config> CONFIG = new AtomicReference<>();

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

    @Override
    public void write(HOutput output) throws IOException {
        output.writeASCII(address.getHostString(), 255);
        output.writeLength(address.getPort(), 65535);
        output.writeByteArray(publicKey.getEncoded(), SecurityHelper.CRYPTO_MAX_LENGTH);

        // Write signed runtime
        Set<Map.Entry<String, byte[]>> entrySet = runtime.entrySet();
        output.writeLength(entrySet.size(), 0);
        for (Map.Entry<String, byte[]> entry : runtime.entrySet()) {
            output.writeString(entry.getKey(), 255);
            output.writeByteArray(entry.getValue(), SecurityHelper.CRYPTO_MAX_LENGTH);
        }
    }
}
