package io.github.howard20181.ime;

import android.app.Activity;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceFragment;

import androidx.annotation.Nullable;

public class SettingsActivity extends Activity {
    public static final String BACK = "back";
    public static final String HOME_HANDLE = "home_handle";
    public static final String IME_SWITCHER = "ime_switcher";
    private static final String CONFIG_NAV_BAR_LAYOUT_HANDLE =
            "back[70AC];home_handle;ime_switcher[70AC]";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings);
        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction()
                    .add(R.id.fragment_container, new SettingsFragment()).commit();
        }
    }

    public static class SettingsFragment extends PreferenceFragment {
        private ListPreference startPref;
        private ListPreference endPref;

        private String computeNavBarLayoutHandle(String start, String end) {
            if (!start.isBlank() && !end.isBlank()) {
                return start + "[70AC];" + HOME_HANDLE + ";" + end + "[70AC]";
            }
            return CONFIG_NAV_BAR_LAYOUT_HANDLE;
        }

        private void pushRemoteConfig(String changedKey, String newValue) {
            if (App.mService == null) return;

            var remotePrefs = App.mService.getRemotePreferences("conf");

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

        private void processServiceBind() {
            if (App.mService != null) {
                var remotePrefs = App.mService.getRemotePreferences("conf");
                if (startPref != null) {
                    startPref.setValue(remotePrefs.getString("nav_bar_layout_start", BACK));
                    startPref.setEnabled(true);
                }
                if (endPref != null) {
                    endPref.setValue(remotePrefs.getString("nav_bar_layout_end", IME_SWITCHER));
                    endPref.setEnabled(true);
                }
            } else {
                if (startPref != null) {
                    startPref.setEnabled(false);
                }
                if (endPref != null) {
                    endPref.setEnabled(false);
                }
            }
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
        public void onResume() {
            super.onResume();
            processServiceBind();
        }
    }
}
