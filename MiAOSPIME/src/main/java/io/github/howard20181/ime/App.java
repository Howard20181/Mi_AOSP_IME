package io.github.howard20181.ime;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

public class App extends Application implements XposedServiceHelper.OnServiceListener {
    private volatile static XposedService mService = null;

    @Override
    public void onServiceBind(@NonNull XposedService service) {
        mService = service;
        notifyServiceStateChanged(mService);
    }

    @Override
    public void onServiceDied(@NonNull XposedService service) {
        mService = null;
        notifyServiceStateChanged(mService);
    }

    public interface ServiceStateListener {
        void onServiceStateChanged(@Nullable XposedService service);
    }

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final Set<ServiceStateListener> SERVICE_STATE_LISTENERS = new CopyOnWriteArraySet<>();

    public static void addServiceStateListener(@NonNull ServiceStateListener listener,
                                               boolean notifyImmediately) {
        SERVICE_STATE_LISTENERS.add(listener);
        if (notifyImmediately) {
            dispatchServiceState(listener, mService);
        }
    }

    public static void removeServiceStateListener(@NonNull ServiceStateListener listener) {
        SERVICE_STATE_LISTENERS.remove(listener);
    }

    private static void notifyServiceStateChanged(@Nullable XposedService service) {
        for (var listener : SERVICE_STATE_LISTENERS) {
            dispatchServiceState(listener, service);
        }
    }

    private static void dispatchServiceState(@NonNull ServiceStateListener listener,
                                             @Nullable XposedService service) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            listener.onServiceStateChanged(service);
            return;
        }
        MAIN_HANDLER.post(() -> listener.onServiceStateChanged(service));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        XposedServiceHelper.registerListener(this);
    }
}
