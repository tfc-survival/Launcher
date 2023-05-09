package launchserver.command.hwid;

import launchserver.LaunchServer;
import launchserver.command.Command;

import java.util.Locale;

public class HWIDBanCommand extends Command {
    public HWIDBanCommand(LaunchServer server) {
        super(server);
    }

    @Override
    public String getArgsDescription() {
        return "<nickname>";
    }

    @Override
    public String getUsageDescription() {
        return "Deny player to play";
    }

    @Override
    public void invoke(String... args) throws Throwable {
        verifyArgs(args, 1);
        server.config.hwidHandler.banUser(args[0].toLowerCase());
    }
}
