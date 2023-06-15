package launchserver.command.basic;

import launcher.helper.CommonHelper;
import launcher.helper.LogHelper;
import launchserver.LaunchServer;
import launchserver.command.Command;

public final class VersionCommand extends Command {
    public VersionCommand(LaunchServer server) {
        super(server);
    }

    @Override
    public String getArgsDescription() {
        return null;
    }

    @Override
    public String getUsageDescription() {
        return "Print LaunchServer version";
    }

    @Override
    public void invoke(String... args) {
        LogHelper.subInfo("LaunchServer version: %s (build #%s)", CommonHelper.VERSION, CommonHelper.BUILD);
    }
}
