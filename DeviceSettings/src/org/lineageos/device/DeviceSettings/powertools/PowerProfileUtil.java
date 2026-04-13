/*
 * Copyright (C) 2025 kenrow214
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.device.DeviceSettings.powertools;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemProperties;
import android.util.Log;

import androidx.preference.PreferenceManager;

import org.lineageos.device.DeviceSettings.R;
import org.lineageos.device.DeviceSettings.Utils;

import java.util.HashMap;
import java.util.Map;

public class PowerProfileUtil {

    private static final String TAG = "PowerProfileUtil";
    private static final String SYS_PROP = "sys.perf_mode_active";

    private static final String FILE_GAME = "/proc/touchpanel/game_switch_enable";
    private static final String FILE_EDGE = "/proc/touchpanel/oplus_tp_direction";
    private static final String KEY_LAST_PROFILE = "powertools_last_profile";

    public static final int MODE_BATTERY_SAVER = 0;
    public static final int MODE_BALANCE = 1;
    public static final int MODE_PERFORMANCE = 2;
    public static final int MODE_UNKNOWN = 4;
    public static final int MODE_AUTO = 5;

    public static final String KEY_GPU_MIN_FREQ = "gpu_min_frequency";
    public static final String KEY_GPU_MAX_FREQ = "gpu_max_frequency";
    public static final String KEY_GPU_GOVERNOR = "gpu_governor";
    public static final String KEY_CPU_LITTLE_MIN_FREQ = "cpu_little_min_frequency";
    public static final String KEY_CPU_LITTLE_MAX_FREQ = "cpu_little_max_frequency";
    public static final String KEY_CPU_LITTLE_GOVERNOR = "cpu_little_governor";
    public static final String KEY_CPU_BIG_MIN_FREQ = "cpu_big_min_frequency";
    public static final String KEY_CPU_BIG_MAX_FREQ = "cpu_big_max_frequency";
    public static final String KEY_CPU_BIG_GOVERNOR = "cpu_big_governor";
    public static final String KEY_CPU_PRIME_MIN_FREQ = "cpu_prime_min_frequency";
    public static final String KEY_CPU_PRIME_MAX_FREQ = "cpu_prime_max_frequency";
    public static final String KEY_CPU_PRIME_GOVERNOR = "cpu_prime_governor";
    public static final String KEY_IO_SCHEDULER = "io_scheduler";

    public static final String[] PERSIST_KEYS = {
        KEY_CPU_LITTLE_MIN_FREQ, KEY_CPU_LITTLE_MAX_FREQ, KEY_CPU_LITTLE_GOVERNOR,
        KEY_CPU_BIG_MIN_FREQ, KEY_CPU_BIG_MAX_FREQ, KEY_CPU_BIG_GOVERNOR,
        KEY_CPU_PRIME_MIN_FREQ, KEY_CPU_PRIME_MAX_FREQ, KEY_CPU_PRIME_GOVERNOR,
        KEY_GPU_MIN_FREQ, KEY_GPU_MAX_FREQ, KEY_GPU_GOVERNOR,
        KEY_IO_SCHEDULER
    };

    private static final Map<String, String[]> PROFILE_DEFAULTS = new HashMap<>();
    static {
        PROFILE_DEFAULTS.put(KEY_CPU_LITTLE_GOVERNOR, new String[]{"schedutil", "schedutil", "performance"});
        PROFILE_DEFAULTS.put(KEY_CPU_BIG_GOVERNOR,    new String[]{"schedutil", "schedutil", "schedutil"});
        PROFILE_DEFAULTS.put(KEY_CPU_PRIME_GOVERNOR,  new String[]{"schedutil", "schedutil", "schedutil"});
        PROFILE_DEFAULTS.put(KEY_GPU_GOVERNOR,        new String[]{"userspace", "msm-adreno-tz", "performance"});
        PROFILE_DEFAULTS.put(KEY_IO_SCHEDULER,        new String[]{"bfq", "bfq", "kyber"});
        
        PROFILE_DEFAULTS.put(KEY_CPU_LITTLE_MIN_FREQ, new String[]{"300000", "300000", "300000"});
        PROFILE_DEFAULTS.put(KEY_CPU_BIG_MIN_FREQ,    new String[]{"710400", "710400", "844800"});
        PROFILE_DEFAULTS.put(KEY_CPU_PRIME_MIN_FREQ,  new String[]{"844800", "844800", "960000"});
        
        PROFILE_DEFAULTS.put(KEY_CPU_LITTLE_MAX_FREQ, new String[]{"1804800", "1804800", "1804800"});
        PROFILE_DEFAULTS.put(KEY_CPU_BIG_MAX_FREQ,    new String[]{"2227200", "2419200", "2419200"});
        PROFILE_DEFAULTS.put(KEY_CPU_PRIME_MAX_FREQ,  new String[]{"2592000", "2841600", "2841600"});
        
        PROFILE_DEFAULTS.put(KEY_GPU_MIN_FREQ,        new String[]{"315000000", "315000000", "315000000"});
        PROFILE_DEFAULTS.put(KEY_GPU_MAX_FREQ,        new String[]{"579000000", "840000000", "840000000"});
    }

    private final Context mContext;
    private int mCurrentMode = MODE_BALANCE;
    private final String[] mModes;

    public PowerProfileUtil(Context context) {
        mContext = context;
        mModes = new String[]{
                mContext.getString(R.string.powerprofile_mode_battery_saver),
                mContext.getString(R.string.powerprofile_mode_balance),
                mContext.getString(R.string.powerprofile_mode_performance),
                "", // Blank placeholder for index 3
                mContext.getString(R.string.powerprofile_mode_unknown),
                "Auto"
        };
    }

    public boolean isAutoModeEnabled() {
        return PreferenceManager.getDefaultSharedPreferences(mContext)
                .getBoolean("auto_thermal_enable", false);
    }

    public int getCurrentMode() {
        return SystemProperties.getInt(SYS_PROP, MODE_BALANCE);
    }

    public boolean setMode(int mode) {
        mCurrentMode = mode;
        saveLastProfile(mode);

        boolean success = setPerformanceModeActive(mode);
        syncUiToMode(mode);

        applyUserTouchPanel();
        BlurUtils.setBlurDisabled(mContext, mode == MODE_BATTERY_SAVER);

        return success;
    }

    /**
     * Same as {@link #setMode(int)} but does NOT touch blur.
     * Used on boot so that Settings.Global.disable_window_blurs
     * is left exactly as the system persisted it across reboot.
     */
    public boolean setModeOnBoot(int mode) {
        mCurrentMode = mode;
        saveLastProfile(mode);

        boolean success = setPerformanceModeActive(mode);
        syncUiToMode(mode);

        applyUserTouchPanel();
        // Intentionally skip BlurUtils — let Settings.Global persist naturally.

        return success;
    }


    private void saveLastProfile(int mode) {
        SharedPreferences prefs = mContext.getSharedPreferences(
                mContext.getPackageName() + "_preferences", Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LAST_PROFILE, String.valueOf(mode)).apply();
    }

    public void syncUiToMode(int mode) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        SharedPreferences.Editor editor = prefs.edit();

        for (String key : PERSIST_KEYS) {
            editor.putString(key, getStockValueForMode(mode, key));
        }
        
        editor.apply();
    }

    public String getStockValueForMode(int mode, String key) {
        int targetIndex = (mode == MODE_BATTERY_SAVER || mode == MODE_PERFORMANCE) ? mode : MODE_BALANCE;
        
        String[] values = PROFILE_DEFAULTS.get(key);
        return values != null ? values[targetIndex] : "";
    }

    private void applyUserTouchPanel() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        boolean isGameEnabled;
        boolean isEdgeEnabled;

        if (mCurrentMode == MODE_PERFORMANCE) {
            isGameEnabled = isEdgeEnabled = true;
        } else if (mCurrentMode == MODE_BATTERY_SAVER) {
            isGameEnabled = isEdgeEnabled = false;
        } else {
            isGameEnabled = prefs.getBoolean("game_mode", false);
            isEdgeEnabled = prefs.getBoolean("edge_touch", false);
        }

        if (mCurrentMode == MODE_PERFORMANCE || mCurrentMode == MODE_BATTERY_SAVER) {
            prefs.edit()
                 .putBoolean("game_mode", isGameEnabled)
                 .putBoolean("edge_touch", isEdgeEnabled)
                 .apply();
        }

        if (Utils.fileWritable(FILE_GAME)) Utils.writeValue(FILE_GAME, isGameEnabled ? "1" : "0");
        if (Utils.fileWritable(FILE_EDGE)) Utils.writeValue(FILE_EDGE, isEdgeEnabled ? "1" : "0");
    }

    public int getManagedMode() {
        return isAutoModeEnabled() ? MODE_AUTO : getCurrentMode();
    }

    public String getModeLabel() {
        int mode = getManagedMode();
        if (mode == MODE_AUTO) return "Auto";
        if (mode == MODE_BATTERY_SAVER) return "PowerSave";
        if (mode == MODE_BALANCE) return "Normal";
        return (mode >= 0 && mode < mModes.length) ? mModes[mode] : mModes[MODE_UNKNOWN];
    }

    public void toggleMode() {
        int currentMode = getManagedMode();
        int newMode = (currentMode == MODE_BALANCE) ? MODE_PERFORMANCE : 
                      (currentMode == MODE_PERFORMANCE) ? MODE_BATTERY_SAVER : MODE_BALANCE;
        setMode(newMode);
    }

    private boolean setPerformanceModeActive(int mode) {
        try {
            SystemProperties.set(SYS_PROP, "-1");
            Thread.sleep(50);
            
            SystemProperties.set(SYS_PROP, String.valueOf(mode));
            SystemProperties.set("persist.sys.perf_mode_saved", String.valueOf(mode));
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Best practice: restore interrupted state
            Log.e(TAG, "Interrupted while bouncing performance mode property", e);
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Failed to set performance mode system properties", e);
            return false;
        }
    }
}