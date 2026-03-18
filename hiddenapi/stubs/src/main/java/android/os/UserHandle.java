package android.os;

import android.annotation.NonNull;

public final class UserHandle {
    public static final int USER_CURRENT = -2;
    public static final @NonNull UserHandle CURRENT = new UserHandle(USER_CURRENT);

    public UserHandle(int h) {
        throw new RuntimeException("STUB");
    }

    public int getIdentifier() {
        throw new UnsupportedOperationException("STUB");
    }

    public static int myUserId() {
        throw new UnsupportedOperationException("STUB");
    }
}
