package io.github.howard20181.ime;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceFragment;
import android.util.Log;

import androidx.annotation.Nullable;

import io.github.libxposed.service.XposedService;

public class SettingsActivity extends Activity {
    private static XposedService mService = null;
    public static final String BACK = "back";
    public static final String HOME_HANDLE = "home_handle";
    public static final String IME_SWITCHER = "ime_switcher";
    private static final String CONFIG_NAV_BAR_LAYOUT_HANDLE =
            "back[70AC];home_handle;ime_switcher[70AC]";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        setContentView(R.layout.settings);
        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction()
                    .add(R.id.fragment_container, new SettingsFragment()).commit();
        }
    }

    public static class SettingsFragment extends PreferenceFragment implements App.ServiceStateListener {
        private ListPreference startPref;
        private ListPreference endPref;

        private void applyServiceStateToPrefs(XposedService service) {
            mService = service;
            if (startPref == null || endPref == null) {
                return;
            }

            if (service == null) {
                startPref.setEnabled(false);
                endPref.setEnabled(false);
                return;
            }

            var remotePrefs = service.getRemotePreferences("conf");
            startPref.setValue(remotePrefs.getString("nav_bar_layout_start", BACK));
            startPref.setEnabled(true);
            endPref.setValue(remotePrefs.getString("nav_bar_layout_end", IME_SWITCHER));
            endPref.setEnabled(true);
        }

        private String computeNavBarLayoutHandle(String start, String end) {
            if (!start.isBlank() && !end.isBlank()) {
                return start + "[70AC];" + HOME_HANDLE + ";" + end + "[70AC]";
            }
            return CONFIG_NAV_BAR_LAYOUT_HANDLE;
        }

        private void pushRemoteConfig(String changedKey, String newValue) {
            if (mService == null) return;

            var remotePrefs = mService.getRemotePreferences("conf");

            String start = startPref != null ? startPref.getValue() : BACK;
            String end = endPref != null ? endPref.getValue() : IME_SWITCHER;

            if ("nav_bar_layout_start".equals(changedKey)) {
                start = newValue;
            } else if ("nav_bar_layout_end".equals(changedKey)) {
                end = newValue;
            }

            remotePrefs.edit()
                    .putString(changedKey, newValue)
                    .putString("nav_bar_layout_handle", computeNavBarLayoutHandle(start, end))
                    .apply();
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences);
            startPref = (ListPreference) findPreference("nav_bar_layout_start");
            endPref = (ListPreference) findPreference("nav_bar_layout_end");
            if (startPref != null) {
                startPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    String value = String.valueOf(newValue).trim();
                    pushRemoteConfig("nav_bar_layout_start", value);
                    return true;
                });
            }

            if (endPref != null) {
                endPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    String value = String.valueOf(newValue).trim();
                    pushRemoteConfig("nav_bar_layout_end", value);
                    return true;
                });
            }
        }

        @Override
        public void onStart() {
            super.onStart();
            App.addServiceStateListener(this, true);
        }

        @Override
        public void onStop() {
            App.removeServiceStateListener(this);
            super.onStop();
        }

        @Override
        public void onServiceStateChanged(@Nullable XposedService service) {
            Log.d("SettingsFragment", "onServiceStateChanged: " + service);
            applyServiceStateToPrefs(service);
        }
    }
}
