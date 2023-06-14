package launcher.request.update;

import launcher.Launcher;
import launcher.Launcher.Config;
import launcher.LauncherAPI;
import launcher.client.ClientLauncher;
import launcher.helper.IOHelper;
import launcher.helper.JVMHelper;
import launcher.helper.LogHelper;
import launcher.helper.SecurityHelper;
import launcher.request.Request;
import launcher.request.update.LauncherRequest.Result;
import launcher.serialize.HInput;
import launcher.serialize.HOutput;

import java.io.IOException;
import java.nio.file.Path;
import java.security.SignatureException;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.List;

public final class LauncherRequest extends Request<Result> {
    @LauncherAPI
    public static final Path BINARY_PATH = IOHelper.getCodeSource(Launcher.class);
    @LauncherAPI
    public static final boolean EXE_BINARY = IOHelper.hasExtension(BINARY_PATH, "exe");

    @LauncherAPI
    public LauncherRequest(Config config) {
        super(config);
    }

    @LauncherAPI
    public LauncherRequest() {
        this(null);
    }

    @LauncherAPI
    public static void update(Config config, Result result) throws SignatureException, IOException {
        SecurityHelper.verifySign(result.binary, result.sign, config.publicKey);

        // Prepare process builder to start new instance (java -jar works for Launch4J's EXE too)
        List<String> args = new ArrayList<>(8);
        args.add(IOHelper.resolveJavaBin(null).toString());
        if (LogHelper.isDebugEnabled()) {
            args.add(ClientLauncher.jvmProperty(LogHelper.DEBUG_PROPERTY, Boolean.toString(LogHelper.isDebugEnabled())));
        }
        if (Config.ADDRESS_OVERRIDE != null) {
            args.add(ClientLauncher.jvmProperty(Config.ADDRESS_OVERRIDE_PROPERTY, Config.ADDRESS_OVERRIDE));
        }
        args.add("-jar");
        args.add(BINARY_PATH.toString());
        ProcessBuilder builder = new ProcessBuilder(args.toArray(new String[args.size()]));
        builder.inheritIO();

        // Rewrite and start new instance
        IOHelper.write(BINARY_PATH, result.binary);
        builder.start();

        // Kill current instance
        JVMHelper.RUNTIME.exit(255);
        throw new AssertionError("Why Launcher wasn't restarted?!");
    }

    @Override
    public Type getType() {
        return Type.LAUNCHER;
    }

    @Override
    @SuppressWarnings("CallToSystemExit")
    protected Result requestDo(HInput input, HOutput output) throws Throwable {
        output.writeBoolean(EXE_BINARY);
        output.flush();
        readError(input);

        // Verify launcher sign
        RSAPublicKey publicKey = config.publicKey;
        byte[] sign = input.readByteArray(-SecurityHelper.RSA_KEY_LENGTH);
        boolean shouldUpdate = !SecurityHelper.isValidSign_1(BINARY_PATH, sign, publicKey);

        // Update launcher if need
        output.writeBoolean(shouldUpdate);
        output.flush();
        if (shouldUpdate) {
            byte[] binary = input.readByteArray(0);
            SecurityHelper.verifySign(binary, sign, config.publicKey);
            return new Result(binary, sign);
        }

        // Return request result
        return new Result(null, sign);
    }

    public static final class Result {
        private final byte[] binary;
        private final byte[] sign;

        public Result(byte[] binary, byte[] sign) {
            this.binary = binary == null ? null : binary.clone();
            this.sign = sign.clone();
        }

        @LauncherAPI
        public byte[] getBinary() {
            return binary == null ? null : binary.clone();
        }

        @LauncherAPI
        public byte[] getSign() {
            return sign.clone();
        }
    }
}
