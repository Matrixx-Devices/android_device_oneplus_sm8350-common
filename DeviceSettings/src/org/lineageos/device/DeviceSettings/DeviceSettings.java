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
import android.widget.Toast;

import androidx.preference.ListPreference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.device.DeviceSettings.Constants;
import org.lineageos.device.DeviceSettings.powertools.PowerProfileUtil;
import org.lineageos.internal.util.FileUtils;

public class DeviceSettings extends SettingsBasePreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String FILE_GAME = "/proc/touchpanel/game_switch_enable";
    private static final String FILE_EDGE = "/proc/touchpanel/oplus_tp_direction";
    private static final String FILE_FAST_CHARGE = "/sys/module/oplus_chg/parameters/force_fast_charge";
    private static final String FILE_LEVEL = "/sys/devices/platform/soc/88c000.i2c/i2c-6/6-005a/leds/vibrator/level";

    private static final String KEY_GAME_SWITCH = "game_mode";
    private static final String KEY_EDGE_TOUCH = "edge_touch";
    private static final String KEY_USB2_SWITCH = "usb2_fast_charge";
    private static final String KEY_VIBSTRENGTH = "vib_strength";

    private static final long[] TEST_VIB_PATTERN = { 0, 5 };
    private static final String DEFAULT_VIB_LEVEL = "3";

    private SwitchPreferenceCompat mGameModeSwitch;
    private SwitchPreferenceCompat mEdgeTouchSwitch;
    private SwitchPreferenceCompat mUSB2FastChargeModeSwitch;
    private CustomSeekBarPreference mVibratorStrengthPreference;
    private Vibrator mVibrator;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.main, rootKey);

        mVibrator = getContext().getSystemService(Vibrator.class);
        
        mGameModeSwitch = bindSwitchPref(KEY_GAME_SWITCH, FILE_GAME);
        mEdgeTouchSwitch = bindSwitchPref(KEY_EDGE_TOUCH, FILE_EDGE);
        mUSB2FastChargeModeSwitch = bindSwitchPref(KEY_USB2_SWITCH, FILE_FAST_CHARGE);

        mVibratorStrengthPreference = (CustomSeekBarPreference) findPreference(KEY_VIBSTRENGTH);
        if (Utils.fileWritable(FILE_LEVEL)) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
            mVibratorStrengthPreference.setValue(prefs.getInt(KEY_VIBSTRENGTH, Integer.parseInt(Utils.getFileValue(FILE_LEVEL, DEFAULT_VIB_LEVEL))));
            mVibratorStrengthPreference.setOnPreferenceChangeListener(this);
        } else {
            mVibratorStrengthPreference.setEnabled(false);
        }

        initNotificationSliderPreference();
    }

    private SwitchPreferenceCompat bindSwitchPref(String key, String sysfsPath) {
        SwitchPreferenceCompat pref = (SwitchPreferenceCompat) findPreference(key);
        if (pref != null) {
            if (Utils.fileWritable(sysfsPath)) {
                pref.setEnabled(true);
                pref.setChecked(PreferenceManager.getDefaultSharedPreferences(getContext())
                        .getBoolean(key, Utils.getFileValueAsBoolean(sysfsPath, false)));
                pref.setOnPreferenceChangeListener(this);
            } else {
                pref.setEnabled(false);
            }
        }
        return pref;
    }

    @Override
    public void onResume() {
        super.onResume();
        enforceTouchPanelPolicy();
        enforceVibPowersaveCap();
        if (getActivity() != null) {
            getActivity().setTitle(R.string.device_title);
        }
    }

    private void enforceVibPowersaveCap() {
        if (mVibratorStrengthPreference == null || !mVibratorStrengthPreference.isEnabled()) return;
        
        boolean isPowersave = SystemProperties.getInt("persist.sys.perf_mode_saved", 1) == 0;
        int currentMax = isPowersave ? 2 : 3; 
        mVibratorStrengthPreference.setMaxValue(currentMax);
        
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        int currentVal = sharedPrefs.getInt(KEY_VIBSTRENGTH, 3);
        
        if (isPowersave && currentVal > 2) {
            mVibratorStrengthPreference.setValue(2);
            sharedPrefs.edit().putInt(KEY_VIBSTRENGTH, 2).apply();
            Utils.writeValue(FILE_LEVEL, "2");
        }
    }

    private void initNotificationSliderPreference() {
        String[] keys = { Constants.NOTIF_SLIDER_ACTION_TOP_KEY, Constants.NOTIF_SLIDER_ACTION_MIDDLE_KEY, Constants.NOTIF_SLIDER_ACTION_BOTTOM_KEY };
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
        String key = preference.getKey();
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(getContext()).edit();

        if (preference == mGameModeSwitch) return applySwitch(editor, KEY_GAME_SWITCH, FILE_GAME, (Boolean) newValue);
        if (preference == mEdgeTouchSwitch) return applySwitch(editor, KEY_EDGE_TOUCH, FILE_EDGE, (Boolean) newValue);
        if (preference == mUSB2FastChargeModeSwitch) return applySwitch(editor, KEY_USB2_SWITCH, FILE_FAST_CHARGE, (Boolean) newValue);
        
        if (preference == mVibratorStrengthPreference) {
            int value = Integer.parseInt(newValue.toString());
            if (SystemProperties.getInt("persist.sys.perf_mode_saved", 1) == 0 && value > 2) {
                Toast.makeText(getContext(), "Vibration capped at level 2 in Powersave mode", Toast.LENGTH_SHORT).show();
                return false;
            }
            editor.putInt(KEY_VIBSTRENGTH, value).apply();
            Utils.writeValue(FILE_LEVEL, String.valueOf(value));
            if (mVibrator != null) mVibrator.vibrate(TEST_VIB_PATTERN, -1);
            return true;
        }

        if (isSliderActionKey(key)) {
            String valStr = (String) newValue;
            if (isDuplicateSliderAction(key, valStr)) {
                Toast.makeText(getContext(), "This action is already assigned to another position", Toast.LENGTH_SHORT).show();
                return false;
            }
            sendSliderBroadcast(key, valStr);
            return true;
        }

        String node = Constants.sBooleanNodePreferenceMap.get(key);
        if (!TextUtils.isEmpty(node) && FileUtils.isFileWritable(node)) {
            FileUtils.writeLine(node, (Boolean) newValue ? "1" : "0");
            return true;
        }
        
        node = Constants.sStringNodePreferenceMap.get(key);
        if (!TextUtils.isEmpty(node) && FileUtils.isFileWritable(node)) {
            FileUtils.writeLine(node, (String) newValue);
            return true;
        }

        return false;
    }

    private boolean applySwitch(SharedPreferences.Editor editor, String prefKey, String sysfsPath, boolean enabled) {
        editor.putBoolean(prefKey, enabled).apply();
        Utils.writeValue(sysfsPath, enabled ? "1" : "0");
        return true;
    }

    private boolean isSliderActionKey(String key) {
        return Constants.NOTIF_SLIDER_ACTION_TOP_KEY.equals(key) ||
               Constants.NOTIF_SLIDER_ACTION_MIDDLE_KEY.equals(key) ||
               Constants.NOTIF_SLIDER_ACTION_BOTTOM_KEY.equals(key);
    }

    private void sendSliderBroadcast(String changedKey, String changedValue) {
        int[] actions = {
            getSliderValueWithOverride(Constants.NOTIF_SLIDER_ACTION_TOP_KEY, "50", changedKey, changedValue),
            getSliderValueWithOverride(Constants.NOTIF_SLIDER_ACTION_MIDDLE_KEY, "51", changedKey, changedValue),
            getSliderValueWithOverride(Constants.NOTIF_SLIDER_ACTION_BOTTOM_KEY, "52", changedKey, changedValue)
        };
        sendUpdateBroadcast(getContext(), actions);
    }

    private int getSliderValueWithOverride(String key, String fallback, String changedKey, String changedValue) {
        if (key.equals(changedKey)) return Integer.parseInt(changedValue);
        ListPreference p = (ListPreference) findPreference(key);
        return p != null && p.getValue() != null ? Integer.parseInt(p.getValue()) : Integer.parseInt(fallback);
    }

    private boolean isDuplicateSliderAction(String changedKey, String newValue) {
        String[] keys = { Constants.NOTIF_SLIDER_ACTION_TOP_KEY, Constants.NOTIF_SLIDER_ACTION_MIDDLE_KEY, Constants.NOTIF_SLIDER_ACTION_BOTTOM_KEY };
        for (String key : keys) {
            if (key.equals(changedKey)) continue;
            ListPreference p = (ListPreference) findPreference(key);
            if (p != null && newValue.equals(p.getValue())) return true;
        }
        return false;
    }

    private void enforceTouchPanelPolicy() {
        if (mGameModeSwitch == null || mEdgeTouchSwitch == null) return;
        
        int profile = SystemProperties.getInt("sys.perf_mode_active", PowerProfileUtil.MODE_BALANCE);
        
        if (profile == PowerProfileUtil.MODE_PERFORMANCE) {
            mGameModeSwitch.setChecked(true);
            mEdgeTouchSwitch.setChecked(true);
            mGameModeSwitch.setEnabled(false);
            mEdgeTouchSwitch.setEnabled(false);
        } else if (profile == PowerProfileUtil.MODE_BATTERY_SAVER) {
            mGameModeSwitch.setChecked(false);
            mEdgeTouchSwitch.setChecked(false);
            mGameModeSwitch.setEnabled(false);
            mEdgeTouchSwitch.setEnabled(false);
        } else {
            mGameModeSwitch.setEnabled(true);
            mEdgeTouchSwitch.setEnabled(true);
        }
    }

    @Override
    public void setPreferencesFromResource(int preferencesResId, String rootKey) {
        super.setPreferencesFromResource(preferencesResId, rootKey);
        
        for (String pref : Constants.sBooleanNodePreferenceMap.keySet()) {
            SwitchPreferenceCompat b = (SwitchPreferenceCompat) findPreference(pref);
            if (b == null) continue;
            String node = Constants.sBooleanNodePreferenceMap.get(pref);
            if (FileUtils.isFileReadable(node)) {
                b.setChecked("1".equals(FileUtils.readOneLine(node)));
                b.setOnPreferenceChangeListener(this);
            } else {
                removePref(b);
            }
        }
        
        for (String pref : Constants.sStringNodePreferenceMap.keySet()) {
            ListPreference l = (ListPreference) findPreference(pref);
            if (l == null) continue;
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
        if (parent != null) {
            parent.removePreference(pref);
            if (parent.getPreferenceCount() == 0) removePref(parent);
        }
    }

    public static void sendUpdateBroadcast(Context context, int[] actions) {
        Intent intent = new Intent(Constants.ACTION_UPDATE_SLIDER_SETTINGS);
        intent.putExtra(Constants.EXTRA_SLIDER_ACTIONS, actions);
        intent.putExtra("is_hardware", false); 
        intent.setFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
        context.sendBroadcastAsUser(intent, UserHandle.CURRENT);
    }

    public static void restoreSliderStates(Context context) {
        Resources res = context.getResources();
        SharedPreferences prefs = context.getSharedPreferences(context.getPackageName() + "_preferences", Context.MODE_PRIVATE);
        String[] defaults = res.getStringArray(R.array.config_defaultSliderActions);
        if (defaults.length != 3) return;

        String actionTop = prefs.getString(Constants.NOTIF_SLIDER_ACTION_TOP_KEY, defaults[0]);
        String actionMiddle = prefs.getString(Constants.NOTIF_SLIDER_ACTION_MIDDLE_KEY, defaults[1]);
        String actionBottom = prefs.getString(Constants.NOTIF_SLIDER_ACTION_BOTTOM_KEY, defaults[2]);

        prefs.edit()
             .putString(Constants.NOTIF_SLIDER_ACTION_TOP_KEY, actionTop)
             .putString(Constants.NOTIF_SLIDER_ACTION_MIDDLE_KEY, actionMiddle)
             .putString(Constants.NOTIF_SLIDER_ACTION_BOTTOM_KEY, actionBottom)
             .commit();

        sendUpdateBroadcast(context, new int[] { Integer.parseInt(actionTop), Integer.parseInt(actionMiddle), Integer.parseInt(actionBottom) });
    }

    public static void restoreFastChargeSetting(Context context) {
        if (Utils.fileWritable(FILE_FAST_CHARGE)) {
            boolean value = PreferenceManager.getDefaultSharedPreferences(context)
                    .getBoolean(KEY_USB2_SWITCH, Utils.getFileValueAsBoolean(FILE_FAST_CHARGE, false));
            Utils.writeValue(FILE_FAST_CHARGE, value ? "1" : "0");
        }
    }

    public static void restoreVibStrengthSetting(Context context) {
        if (Utils.fileWritable(FILE_LEVEL)) {
            int value = PreferenceManager.getDefaultSharedPreferences(context)
                    .getInt(KEY_VIBSTRENGTH, Integer.parseInt(Utils.getFileValue(FILE_LEVEL, DEFAULT_VIB_LEVEL)));
            Utils.writeValue(FILE_LEVEL, String.valueOf(value));
        }
    }
}