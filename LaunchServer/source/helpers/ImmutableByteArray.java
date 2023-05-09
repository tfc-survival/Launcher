package launchserver.helpers;

import java.util.Arrays;

public class ImmutableByteArray {
    public final byte[] content;

    public ImmutableByteArray(byte[] content) {
        this.content = content;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ImmutableByteArray)
            return Arrays.equals(content, ((ImmutableByteArray) obj).content);
        else
            return false;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(content);
    }
}
