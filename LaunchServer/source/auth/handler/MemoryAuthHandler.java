package launchserver.auth.handler;

import launcher.helper.IOHelper;
import launcher.helper.LogHelper;
import launcher.helper.VerifyHelper;
import launcher.serialize.config.entry.BlockConfigEntry;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.UUID;

public final class MemoryAuthHandler extends CachedAuthHandler {
    MemoryAuthHandler(BlockConfigEntry block) {
        super(block);
        LogHelper.warning("Usage of MemoryAuthHandler isn't recommended!");
    }

    private static UUID toUUID(String username) {
        ByteBuffer buffer = ByteBuffer.wrap(Arrays.copyOf(IOHelper.encodeASCII(username), 16));
        return new UUID(buffer.getLong(), buffer.getLong()); // MOST, LEAST
    }

    private static String toUsername(UUID uuid) {
        byte[] bytes = ByteBuffer.allocate(16).
                putLong(uuid.getMostSignificantBits()).
                putLong(uuid.getLeastSignificantBits()).array();

        // Find username end
        int length = 0;
        while (length < bytes.length && bytes[length] != 0) {
            length++;
        }

        // Decode and verify
        return VerifyHelper.verifyUsername(new String(bytes, 0, length, IOHelper.ASCII_CHARSET));
    }

    @Override
    public void close() {
        // Do nothing
    }

    @Override
    protected Entry fetchEntry(UUID uuid) {
        return new Entry(uuid, toUsername(uuid), null, null);
    }

    @Override
    protected Entry fetchEntry(String username) {
        return new Entry(toUUID(username), username, null, null);
    }

    @Override
    protected boolean updateAuth(UUID uuid, String username, String accessToken) {
        return true; // Do nothing
    }

    @Override
    protected boolean updateServerID(UUID uuid, String serverID) {
        return true; // Do nothing
    }
}
