package launchserver.response.update;

import launcher.helper.SecurityHelper;
import launcher.serialize.HInput;
import launcher.serialize.HOutput;
import launcher.serialize.signed.SignedBytesHolder;
import launchserver.LaunchServer;
import launchserver.response.Response;

import java.io.IOException;

public final class LauncherResponse extends Response {
    public LauncherResponse(LaunchServer server, String ip, HInput input, HOutput output) {
        super(server, ip, input, output);
    }

    @Override
    public void reply() throws IOException {
        // Resolve launcher binary
        SignedBytesHolder bytes = (input.readBoolean() ? server.launcherEXEBinary : server.launcherBinary).getBytes();
        if (bytes == null) {
            requestError("Missing launcher binary");
            return;
        }
        writeNoError(output);

        // Update launcher binary
        output.writeByteArray(bytes.getSign(), -SecurityHelper.RSA_KEY_LENGTH);
        output.flush();
        if (input.readBoolean()) {
            output.writeByteArray(bytes.getBytes(), 0);
            return; // Launcher will be restarted
        }
    }
}
