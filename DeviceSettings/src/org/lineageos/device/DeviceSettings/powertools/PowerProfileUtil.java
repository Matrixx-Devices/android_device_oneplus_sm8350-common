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

public class PowerProfileUtil {

    private static final String TAG = "PowerProfileUtil";
    private static final String SYS_PROP = "sys.perf_mode_active";

    private static final String FILE_GAME = "/proc/touchpanel/game_switch_enable";
    private static final String FILE_EDGE = "/proc/touchpanel/oplus_tp_direction";
    private static final String KEY_LAST_PROFILE = "powertools_last_profile";

    public static final int MODE_BATTERY_SAVER = 0;
    public static final int MODE_BALANCE = 1;
    public static final int MODE_PERFORMANCE = 2;
    public static final int MODE_MANUAL = 3;
    public static final int MODE_UNKNOWN = 4;
    public static final int MODE_AUTO = 5;

    private final Context mContext;
    private int mCurrentMode = MODE_BALANCE;
    private final String[] mModes;

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

    // Required for PowertoolsSettingsFragment persistence logic
    public static final String[] PERSIST_KEYS = {
        KEY_CPU_LITTLE_MIN_FREQ, KEY_CPU_LITTLE_MAX_FREQ, KEY_CPU_LITTLE_GOVERNOR,
        KEY_CPU_BIG_MIN_FREQ, KEY_CPU_BIG_MAX_FREQ, KEY_CPU_BIG_GOVERNOR,
        KEY_CPU_PRIME_MIN_FREQ, KEY_CPU_PRIME_MAX_FREQ, KEY_CPU_PRIME_GOVERNOR,
        KEY_GPU_MIN_FREQ, KEY_GPU_MAX_FREQ, KEY_GPU_GOVERNOR,
        KEY_IO_SCHEDULER
    };

    public PowerProfileUtil(Context context) {
        mContext = context;
        mModes = new String[]{
                mContext.getString(R.string.powerprofile_mode_battery_saver),
                mContext.getString(R.string.powerprofile_mode_balance),
                mContext.getString(R.string.powerprofile_mode_performance),
                mContext.getString(R.string.powerprofile_mode_manual),
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
        boolean success = true;
        mCurrentMode = mode;
        saveLastProfile(mode);

        if (mode == MODE_MANUAL) {
            success &= setPerformanceModeActive(MODE_BALANCE);
        } else {
            success &= setPerformanceModeActive(mode);
            syncUiToMode(mode);
        }

        applyUserTouchPanel();

        if (mode == MODE_PERFORMANCE) {
            SysfsUtils.writeValue("/proc/sys/vm/drop_caches", "3");
        }

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

        editor.putString(KEY_CPU_LITTLE_MIN_FREQ, getStockValueForMode(mode, KEY_CPU_LITTLE_MIN_FREQ));
        editor.putString(KEY_CPU_LITTLE_MAX_FREQ, getStockValueForMode(mode, KEY_CPU_LITTLE_MAX_FREQ));
        editor.putString(KEY_CPU_LITTLE_GOVERNOR, getStockValueForMode(mode, KEY_CPU_LITTLE_GOVERNOR));
        
        editor.putString(KEY_CPU_BIG_MIN_FREQ, getStockValueForMode(mode, KEY_CPU_BIG_MIN_FREQ));
        editor.putString(KEY_CPU_BIG_MAX_FREQ, getStockValueForMode(mode, KEY_CPU_BIG_MAX_FREQ));
        editor.putString(KEY_CPU_BIG_GOVERNOR, getStockValueForMode(mode, KEY_CPU_BIG_GOVERNOR));
        
        editor.putString(KEY_CPU_PRIME_MIN_FREQ, getStockValueForMode(mode, KEY_CPU_PRIME_MIN_FREQ));
        editor.putString(KEY_CPU_PRIME_MAX_FREQ, getStockValueForMode(mode, KEY_CPU_PRIME_MAX_FREQ));
        editor.putString(KEY_CPU_PRIME_GOVERNOR, getStockValueForMode(mode, KEY_CPU_PRIME_GOVERNOR));
        
        editor.putString(KEY_GPU_MIN_FREQ, getStockValueForMode(mode, KEY_GPU_MIN_FREQ));
        editor.putString(KEY_GPU_MAX_FREQ, getStockValueForMode(mode, KEY_GPU_MAX_FREQ));
        editor.putString(KEY_GPU_GOVERNOR, getStockValueForMode(mode, KEY_GPU_GOVERNOR));
        
        editor.putString(KEY_IO_SCHEDULER, getStockValueForMode(mode, KEY_IO_SCHEDULER));
        editor.apply();
    }

    public String getPersistenceKey(int mode, String baseKey) {
        return "mode_" + mode + "_" + baseKey;
    }

    public String getStockValueForMode(int mode, String key) {
        switch (mode) {
            case MODE_BATTERY_SAVER:
                // Must match init.performance.rc: on property:sys.perf_mode_active=0
                if (KEY_CPU_LITTLE_GOVERNOR.equals(key)) return "schedutil";
                if (KEY_CPU_BIG_GOVERNOR.equals(key)) return "schedutil";
                if (KEY_CPU_PRIME_GOVERNOR.equals(key)) return "schedutil";
                if (KEY_GPU_GOVERNOR.equals(key)) return "userspace";
                if (KEY_IO_SCHEDULER.equals(key)) return "bfq";
                if (KEY_CPU_LITTLE_MIN_FREQ.equals(key)) return "300000";
                if (KEY_CPU_BIG_MIN_FREQ.equals(key)) return "710400";
                if (KEY_CPU_PRIME_MIN_FREQ.equals(key)) return "844800";
                if (KEY_CPU_LITTLE_MAX_FREQ.equals(key)) return "1804800";
                if (KEY_CPU_BIG_MAX_FREQ.equals(key)) return "2212000";
                if (KEY_CPU_PRIME_MAX_FREQ.equals(key)) return "2592000";
                if (KEY_GPU_MIN_FREQ.equals(key)) return "315000000";
                if (KEY_GPU_MAX_FREQ.equals(key)) return "579000000";
                break;
            case MODE_PERFORMANCE:
                // Must match init.performance.rc: on property:sys.perf_mode_active=2
                if (KEY_CPU_LITTLE_GOVERNOR.equals(key)) return "performance";
                if (KEY_CPU_BIG_GOVERNOR.equals(key)) return "performance";
                if (KEY_CPU_PRIME_GOVERNOR.equals(key)) return "performance";
                if (KEY_GPU_GOVERNOR.equals(key)) return "performance";
                if (KEY_IO_SCHEDULER.equals(key)) return "kyber";
                if (KEY_CPU_LITTLE_MIN_FREQ.equals(key)) return "300000";
                if (KEY_CPU_BIG_MIN_FREQ.equals(key)) return "844800";
                if (KEY_CPU_PRIME_MIN_FREQ.equals(key)) return "917100";
                if (KEY_CPU_LITTLE_MAX_FREQ.equals(key)) return "1804800";
                if (KEY_CPU_BIG_MAX_FREQ.equals(key)) return "2419200";
                if (KEY_CPU_PRIME_MAX_FREQ.equals(key)) return "2841600";
                if (KEY_GPU_MIN_FREQ.equals(key)) return "676000000";
                if (KEY_GPU_MAX_FREQ.equals(key)) return "840000000";
                break;
        }

        // Default Balance (1) values — matches init.performance.rc: on property:sys.perf_mode_active=1
        if (KEY_CPU_LITTLE_MIN_FREQ.equals(key)) return "300000";
        if (KEY_CPU_LITTLE_MAX_FREQ.equals(key)) return "1804800";
        if (KEY_CPU_LITTLE_GOVERNOR.equals(key)) return "schedutil";
        if (KEY_CPU_BIG_MIN_FREQ.equals(key)) return "710400";
        if (KEY_CPU_BIG_MAX_FREQ.equals(key)) return "2419200";
        if (KEY_CPU_BIG_GOVERNOR.equals(key)) return "schedutil";
        if (KEY_CPU_PRIME_MIN_FREQ.equals(key)) return "844800";
        if (KEY_CPU_PRIME_MAX_FREQ.equals(key)) return "2841600";
        if (KEY_CPU_PRIME_GOVERNOR.equals(key)) return "schedutil";
        if (KEY_GPU_MIN_FREQ.equals(key)) return "315000000";
        if (KEY_GPU_MAX_FREQ.equals(key)) return "840000000";
        if (KEY_GPU_GOVERNOR.equals(key)) return "simple_ondemand";
        if (KEY_IO_SCHEDULER.equals(key)) return "bfq";

        return "";
    }

    private void applyUserTouchPanel() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        boolean game;
        boolean edge;

        switch (mCurrentMode) {
            case MODE_PERFORMANCE:
                // Force both ON in Performance — user cannot override
                game = true;
                edge = true;
                prefs.edit().putBoolean("game_mode", true).putBoolean("edge_touch", true).apply();
                break;
            case MODE_BATTERY_SAVER:
                // Force both OFF in PowerSave — user cannot override
                game = false;
                edge = false;
                prefs.edit().putBoolean("game_mode", false).putBoolean("edge_touch", false).apply();
                break;
            default:
                // Normal/Manual/Auto — user controls freely
                game = prefs.getBoolean("game_mode", false);
                edge = prefs.getBoolean("edge_touch", false);
                break;
        }

        if (Utils.fileWritable(FILE_GAME)) Utils.writeValue(FILE_GAME, game ? "1" : "0");
        if (Utils.fileWritable(FILE_EDGE)) Utils.writeValue(FILE_EDGE, edge ? "1" : "0");
    }

    public int getManagedMode() {
        if (isAutoModeEnabled()) return MODE_AUTO;
        return getCurrentMode();
    }

    public String getModeLabel() {
        int mode = getCurrentMode();
        if (mode >= 0 && mode < mModes.length) return mModes[mode];
        return mModes[MODE_UNKNOWN];
    }

    public void toggleMode() {
        int currentMode = getManagedMode();
        int newMode = (currentMode == MODE_BALANCE) ? MODE_PERFORMANCE : 
                      (currentMode == MODE_PERFORMANCE) ? MODE_BATTERY_SAVER : MODE_BALANCE;
        setMode(newMode);
    }

    private boolean setPerformanceModeActive(int mode) {
        try {
            // Bounce to -1 first to force init.rc property triggers to re-fire
            // even when the target value equals the current value
            SystemProperties.set(SYS_PROP, "-1");
            Thread.sleep(50);
            SystemProperties.set(SYS_PROP, String.valueOf(mode));
            SystemProperties.set("persist.sys.perf_mode_saved", String.valueOf(mode));
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}