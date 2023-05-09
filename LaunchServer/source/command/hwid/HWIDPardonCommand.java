package launchserver.command.hwid;

import launchserver.LaunchServer;
import launchserver.command.Command;

public class HWIDPardonCommand extends Command {
    public HWIDPardonCommand(LaunchServer server) {
        super(server);
    }

    @Override
    public String getArgsDescription() {
        return "<nickname>";
    }

    @Override
    public String getUsageDescription() {
        return "Allow player to play";
    }

    @Override
    public void invoke(String... args) throws Throwable {
        verifyArgs(args, 1);
        server.config.hwidHandler.pardonUser(args[0].toLowerCase());
    }
}
