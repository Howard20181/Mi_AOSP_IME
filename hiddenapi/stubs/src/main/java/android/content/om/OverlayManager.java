package android.content.om;

import android.annotation.NonNull;
import android.os.UserHandle;

public class OverlayManager {
    public void setEnabled(@NonNull final String packageName, final boolean enable,
                           @NonNull UserHandle user) throws SecurityException, IllegalStateException {
        throw new UnsupportedOperationException("STUB");
    }

    public OverlayInfo getOverlayInfo(@NonNull final String packageName,
                                      @NonNull final UserHandle userHandle) {
        throw new UnsupportedOperationException("STUB");
    }
}
