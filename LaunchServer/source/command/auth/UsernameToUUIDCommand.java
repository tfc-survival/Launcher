package launchserver.command.auth;

import launcher.helper.LogHelper;
import launchserver.LaunchServer;
import launchserver.command.Command;
import launchserver.command.CommandException;

import java.io.IOException;
import java.util.UUID;

public final class UsernameToUUIDCommand extends Command {
    public UsernameToUUIDCommand(LaunchServer server) {
        super(server);
    }

    @Override
    public String getArgsDescription() {
        return "<username>";
    }

    @Override
    public String getUsageDescription() {
        return "Convert player username to UUID";
    }

    @Override
    public void invoke(String... args) throws CommandException, IOException {
        verifyArgs(args, 1);
        String username = parseUsername(args[0]);

        // Get UUID by username
        UUID uuid = server.config.authHandler.usernameToUUID(username);
        if (uuid == null) {
            throw new CommandException(String.format("Unknown username: '%s'", username));
        }

        // Print UUID
        LogHelper.subInfo("UUID of player '%s': %s", username, uuid);
    }
}
