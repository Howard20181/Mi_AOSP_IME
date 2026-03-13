package io.github.howard20181.ime;

import android.app.Application;

import androidx.annotation.NonNull;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

public class App extends Application {
    public static XposedService mService = null;

    @Override
    public void onCreate() {
        super.onCreate();
        XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
            @Override
            public void onServiceBind(@NonNull XposedService service) {
                mService = service;
            }

            @Override
            public void onServiceDied(@NonNull XposedService service) {
                mService = null;
            }
        });
    }
}
