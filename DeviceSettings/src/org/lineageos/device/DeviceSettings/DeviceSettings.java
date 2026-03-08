/*
 * Copyright (C) 2018-2022 crDroid Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lineageos.device.DeviceSettings;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.Vibrator;
import android.text.TextUtils;
import android.view.MenuItem;
import android.widget.Toast;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.device.DeviceSettings.Constants;
import org.lineageos.internal.util.FileUtils;

public class DeviceSettings extends SettingsBasePreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String KEY_GAME_SWITCH = "game_mode";
    private static final String KEY_EDGE_TOUCH = "edge_touch";

    private static final String FILE_GAME = "/proc/touchpanel/game_switch_enable";
    private static final String FILE_EDGE = "/proc/touchpanel/oplus_tp_direction";

    private static final String KEY_USB2_SWITCH = "usb2_fast_charge";
    private static final String KEY_VIBSTRENGTH = "vib_strength";

    private static final String FILE_FAST_CHARGE = "/sys/module/oplus_chg/parameters/force_fast_charge";
    private static final String FILE_LEVEL = "/sys/devices/platform/soc/88c000.i2c/i2c-6/6-005a/leds/vibrator/level";
    private static final long testVibrationPattern[] = { 0, 5 };
    private static final String DEFAULT = "3";

    private SwitchPreferenceCompat mGameModeSwitch;
    private SwitchPreferenceCompat mEdgeTouchSwitch;
    private SwitchPreferenceCompat mUSB2FastChargeModeSwitch;

    private CustomSeekBarPreference mVibratorStrengthPreference;

    private Vibrator mVibrator;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.main, rootKey);

        mVibrator = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());

        mGameModeSwitch = (SwitchPreferenceCompat) findPreference(KEY_GAME_SWITCH);
        if (Utils.fileWritable(FILE_GAME)) {
            mGameModeSwitch.setEnabled(true);
            mGameModeSwitch.setChecked(sharedPrefs.getBoolean(KEY_GAME_SWITCH,
                    Utils.getFileValueAsBoolean(FILE_GAME, false)));
            mGameModeSwitch.setOnPreferenceChangeListener(this);
        } else {
            mGameModeSwitch.setEnabled(false);
        }

        mEdgeTouchSwitch = (SwitchPreferenceCompat) findPreference(KEY_EDGE_TOUCH);
        if (Utils.fileWritable(FILE_EDGE)) {
            mEdgeTouchSwitch.setEnabled(true);
            mEdgeTouchSwitch.setChecked(sharedPrefs.getBoolean(KEY_EDGE_TOUCH,
                    Utils.getFileValueAsBoolean(FILE_EDGE, false)));
            mEdgeTouchSwitch.setOnPreferenceChangeListener(this);
        } else {
            mEdgeTouchSwitch.setEnabled(false);
        }

        mUSB2FastChargeModeSwitch = (SwitchPreferenceCompat) findPreference(KEY_USB2_SWITCH);
        if (Utils.fileWritable(FILE_FAST_CHARGE)) {
            mUSB2FastChargeModeSwitch.setEnabled(true);
            mUSB2FastChargeModeSwitch.setChecked(sharedPrefs.getBoolean(KEY_USB2_SWITCH,
                    Utils.getFileValueAsBoolean(FILE_FAST_CHARGE, false)));
            mUSB2FastChargeModeSwitch.setOnPreferenceChangeListener(this);
        } else {
            mUSB2FastChargeModeSwitch.setEnabled(false);
        }

        mVibratorStrengthPreference = (CustomSeekBarPreference) findPreference(KEY_VIBSTRENGTH);
        if (Utils.fileWritable(FILE_LEVEL)) {
            mVibratorStrengthPreference.setValue(sharedPrefs.getInt(KEY_VIBSTRENGTH,
                    Integer.parseInt(Utils.getFileValue(FILE_LEVEL, DEFAULT))));
            mVibratorStrengthPreference.setOnPreferenceChangeListener(this);
        } else {
            mVibratorStrengthPreference.setEnabled(false);
        }

        initNotificationSliderPreference();
    }

    @Override
    public void onResume() {
        super.onResume();
        enforceTouchPanelPolicy();
        enforceVibPowersaveCap();
    }

    /** Clamp the vibration seekbar to max 2 while Powersave profile is active. */
    private void enforceVibPowersaveCap() {
        if (mVibratorStrengthPreference == null || !mVibratorStrengthPreference.isEnabled())
            return;
        // persist.sys.perf_mode_saved: 0=Powersave, 1=Normal, 2=Performance
        int savedMode = SystemProperties.getInt("persist.sys.perf_mode_saved", 1);
        boolean isPowersave = (savedMode == 0);
        int currentMax = isPowersave ? 2 : 3; // vibrator has 3 levels (1-3), cap at 2 in powersave
        mVibratorStrengthPreference.setMaxValue(currentMax);
        // If current value exceeds new cap, silently clamp it
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        int currentVal = sharedPrefs.getInt(KEY_VIBSTRENGTH, 3);
        if (isPowersave && currentVal > 2) {
            mVibratorStrengthPreference.setValue(2);
            sharedPrefs.edit().putInt(KEY_VIBSTRENGTH, 2).apply();
            Utils.writeValue(FILE_LEVEL, "2");
        }
    }

    private void initNotificationSliderPreference() {
        String[] keys = {
                Constants.NOTIF_SLIDER_ACTION_TOP_KEY,
                Constants.NOTIF_SLIDER_ACTION_MIDDLE_KEY,
                Constants.NOTIF_SLIDER_ACTION_BOTTOM_KEY
        };
        for (String key : keys) {
            ListPreference p = (ListPreference) findPreference(key);
            if (p != null) {
                p.setOnPreferenceChangeListener(this);
                p.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
            }
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mGameModeSwitch) {
            boolean enabled = (Boolean) newValue;
            SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());
            sharedPrefs.edit().putBoolean(KEY_GAME_SWITCH, enabled).commit();
            Utils.writeValue(FILE_GAME, enabled ? "1" : "0");
            return true;
        } else if (preference == mEdgeTouchSwitch) {
            boolean enabled = (Boolean) newValue;
            SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());
            sharedPrefs.edit().putBoolean(KEY_EDGE_TOUCH, enabled).commit();
            Utils.writeValue(FILE_EDGE, enabled ? "1" : "0");
            return true;
        } else if (preference == mUSB2FastChargeModeSwitch) {
            boolean enabled = (Boolean) newValue;
            SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());
            sharedPrefs.edit().putBoolean(KEY_USB2_SWITCH, enabled).commit();
            Utils.writeValue(FILE_FAST_CHARGE, enabled ? "1" : "0");
            return true;
        } else if (preference == mVibratorStrengthPreference) {
            int value = Integer.parseInt(newValue.toString());
            // Powersave mode: cap vibration at level 2
            int savedMode = SystemProperties.getInt("persist.sys.perf_mode_saved", 1);
            if (savedMode == 0 && value > 2) {
                Toast.makeText(getContext(),
                        "Vibration capped at level 2 in Powersave mode", Toast.LENGTH_SHORT).show();
                return false;
            }
            SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());
            sharedPrefs.edit().putInt(KEY_VIBSTRENGTH, value).commit();
            Utils.writeValue(FILE_LEVEL, String.valueOf(value));
            mVibrator.vibrate(testVibrationPattern, -1);
            return true;
        }

        String key = preference.getKey();
        switch (key) {
            case Constants.NOTIF_SLIDER_ACTION_TOP_KEY:
            case Constants.NOTIF_SLIDER_ACTION_MIDDLE_KEY:
            case Constants.NOTIF_SLIDER_ACTION_BOTTOM_KEY:
                sendSliderBroadcast(key, (String) newValue);
                return true;
            default:
                break;
        }

        String node = Constants.sBooleanNodePreferenceMap.get(key);
        if (!TextUtils.isEmpty(node) && FileUtils.isFileWritable(node)) {
            Boolean value = (Boolean) newValue;
            FileUtils.writeLine(node, value ? "1" : "0");
            return true;
        }
        node = Constants.sStringNodePreferenceMap.get(key);
        if (!TextUtils.isEmpty(node) && FileUtils.isFileWritable(node)) {
            FileUtils.writeLine(node, (String) newValue);
            return true;
        }

        return false;
    }

    private void sendSliderBroadcast(String changedKey, String changedValue) {
        int[] actions = new int[3];
        actions[0] = getSliderValueWithOverride(Constants.NOTIF_SLIDER_ACTION_TOP_KEY, "50",
                changedKey, changedValue);
        actions[1] = getSliderValueWithOverride(Constants.NOTIF_SLIDER_ACTION_MIDDLE_KEY, "51",
                changedKey, changedValue);
        actions[2] = getSliderValueWithOverride(Constants.NOTIF_SLIDER_ACTION_BOTTOM_KEY, "52",
                changedKey, changedValue);
        sendUpdateBroadcast(getActivity().getApplicationContext(), actions);
    }

    private int getSliderValueWithOverride(String key, String fallback,
            String changedKey, String changedValue) {
        if (key.equals(changedKey))
            return Integer.parseInt(changedValue);
        ListPreference p = (ListPreference) findPreference(key);
        if (p == null)
            return Integer.parseInt(fallback);
        String val = p.getValue();
        return val != null ? Integer.parseInt(val) : Integer.parseInt(fallback);
    }

    private void enforceTouchPanelPolicy() {
        if (mGameModeSwitch == null || mEdgeTouchSwitch == null)
            return;
        String profileVal = getContext().getSharedPreferences(
                getContext().getPackageName() + "_preferences", Context.MODE_PRIVATE)
                .getString("powertools_last_profile", null);
        int profile;
        try {
            if (profileVal != null) {
                profile = Integer.parseInt(profileVal);
            } else {
                // Map sys.perf_mode_active (0=powersave,1=balanced,2=performance)
                // to PowerProfileUtil constants (0=balance,1=performance,2=powersave)
                int sysProp = android.os.SystemProperties.getInt("sys.perf_mode_active", 1);
                if (sysProp == 2)
                    profile = 1; // performance
                else if (sysProp == 0)
                    profile = 2; // powersave
                else
                    profile = 0; // balanced
            }
        } catch (NumberFormatException e) {
            profile = 0;
        }
        // PowerProfileUtil constants: 0=Normal, 1=Performance, 2=Powersave, 3=Manual
        switch (profile) {
            case 1: // Performance: both ON and locked
                mGameModeSwitch.setChecked(true);
                mGameModeSwitch.setEnabled(false);
                mEdgeTouchSwitch.setChecked(true);
                mEdgeTouchSwitch.setEnabled(false);
                break;
            case 2: // Powersave: both OFF and locked
                mGameModeSwitch.setChecked(false);
                mGameModeSwitch.setEnabled(false);
                mEdgeTouchSwitch.setChecked(false);
                mEdgeTouchSwitch.setEnabled(false);
                break;
            default: // Normal or Manual: game_mode ON (user can toggle), edge_touch free
                mGameModeSwitch.setChecked(true);
                mGameModeSwitch.setEnabled(true);
                mEdgeTouchSwitch.setEnabled(true);
                break;
        }
    }

    @Override
    public void setPreferencesFromResource(int preferencesResId, String rootKey) {
        super.setPreferencesFromResource(preferencesResId, rootKey);
        // Initialize node preferences
        for (String pref : Constants.sBooleanNodePreferenceMap.keySet()) {
            SwitchPreferenceCompat b = (SwitchPreferenceCompat) findPreference(pref);
            if (b == null)
                continue;
            String node = Constants.sBooleanNodePreferenceMap.get(pref);
            if (FileUtils.isFileReadable(node)) {
                String curNodeValue = FileUtils.readOneLine(node);
                b.setChecked(curNodeValue.equals("1"));
                b.setOnPreferenceChangeListener(this);
            } else {
                removePref(b);
            }
        }
        for (String pref : Constants.sStringNodePreferenceMap.keySet()) {
            ListPreference l = (ListPreference) findPreference(pref);
            if (l == null)
                continue;
            String node = Constants.sStringNodePreferenceMap.get(pref);
            if (FileUtils.isFileReadable(node)) {
                l.setValue(FileUtils.readOneLine(node));
                l.setOnPreferenceChangeListener(this);
            } else {
                removePref(l);
            }
        }
    }

    private void removePref(Preference pref) {
        PreferenceGroup parent = pref.getParent();
        if (parent == null) {
            return;
        }
        parent.removePreference(pref);
        if (parent.getPreferenceCount() == 0) {
            removePref(parent);
        }
    }

    public static void sendUpdateBroadcast(Context context, int[] actions) {
        Intent intent = new Intent(Constants.ACTION_UPDATE_SLIDER_SETTINGS);
        intent.putExtra(Constants.EXTRA_SLIDER_ACTIONS, actions);
        intent.setFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
        context.sendBroadcastAsUser(intent, UserHandle.CURRENT);
    }

    public static void restoreSliderStates(Context context) {
        Resources res = context.getResources();
        SharedPreferences prefs = context.getSharedPreferences(
                context.getPackageName() + "_preferences", Context.MODE_PRIVATE);

        String[] defaults = res.getStringArray(R.array.config_defaultSliderActions);
        if (defaults.length != 3)
            return;

        String actionTop = prefs.getString(
                Constants.NOTIF_SLIDER_ACTION_TOP_KEY, defaults[0]);
        String actionMiddle = prefs.getString(
                Constants.NOTIF_SLIDER_ACTION_MIDDLE_KEY, defaults[1]);
        String actionBottom = prefs.getString(
                Constants.NOTIF_SLIDER_ACTION_BOTTOM_KEY, defaults[2]);

        prefs.edit()
                .putString(Constants.NOTIF_SLIDER_ACTION_TOP_KEY, actionTop)
                .putString(Constants.NOTIF_SLIDER_ACTION_MIDDLE_KEY, actionMiddle)
                .putString(Constants.NOTIF_SLIDER_ACTION_BOTTOM_KEY, actionBottom)
                .commit();

        sendUpdateBroadcast(context, new int[] {
                Integer.parseInt(actionTop),
                Integer.parseInt(actionMiddle),
                Integer.parseInt(actionBottom)
        });
    }

    public static void restoreFastChargeSetting(Context context) {
        if (Utils.fileWritable(FILE_FAST_CHARGE)) {
            SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
            boolean value = sharedPrefs.getBoolean(KEY_USB2_SWITCH,
                    Utils.getFileValueAsBoolean(FILE_FAST_CHARGE, false));
            Utils.writeValue(FILE_FAST_CHARGE, value ? "1" : "0");
        }
    }

    public static void restoreVibStrengthSetting(Context context) {
        if (Utils.fileWritable(FILE_LEVEL)) {
            SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
            int value = sharedPrefs.getInt(KEY_VIBSTRENGTH,
                    Integer.parseInt(Utils.getFileValue(FILE_LEVEL, DEFAULT)));
            Utils.writeValue(FILE_LEVEL, String.valueOf(value));
        }
    }

}
