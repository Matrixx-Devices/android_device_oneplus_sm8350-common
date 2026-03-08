/*
 * Copyright (C) 2025 kenrow214
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.device.DeviceSettings.powertools;

import android.app.ActivityManager;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemProperties;
import android.util.Log;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import org.lineageos.device.DeviceSettings.R;
import org.lineageos.device.DeviceSettings.Utils;

import java.util.List;

public class PowerProfileUtil {

    private static final String TAG = "PowerProfileUtil";
    private static final String SYS_PROP = "sys.perf_mode_active";

    private static final String FILE_GAME = "/proc/touchpanel/game_switch_enable";
    private static final String FILE_EDGE = "/proc/touchpanel/oplus_tp_direction";

    private static final String KEY_GAME_ORIG = "powertools_game_mode_original";
    private static final String KEY_EDGE_ORIG = "powertools_edge_touch_original";
    private static final String KEY_LAST_PROFILE = "powertools_last_profile";

    public static final int MODE_BALANCE = 0;
    public static final int MODE_PERFORMANCE = 1;
    public static final int MODE_BATTERY_SAVER = 2;
    public static final int MODE_MANUAL = 3;
    public static final int MODE_UNKNOWN = 4;
    public static final int MODE_AUTO = 5;

    private final Context mContext;
    private final Handler mHandler = new Handler();

    private int mCurrentMode = MODE_BALANCE;
    private final String[] mModes;


    public PowerProfileUtil(Context context) {
        mContext = context;

        mModes = new String[]{
                mContext.getString(R.string.powerprofile_mode_balance),
                mContext.getString(R.string.powerprofile_mode_performance),
                mContext.getString(R.string.powerprofile_mode_battery_saver),
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
        int val = SystemProperties.getInt(SYS_PROP, 1);
        switch (val) {
            case 1:
                return MODE_BALANCE;
            case 2:
                return MODE_PERFORMANCE;
            case 0:
                return MODE_BATTERY_SAVER;
            default:
                return MODE_BALANCE;
        }
    }

    public boolean setMode(int mode) {
        boolean success = true;
        mCurrentMode = mode;
        saveLastProfile(mode);

        switch (mode) {

            case MODE_BALANCE:
                // Set prop to trigger init.rc which handles cache clearing and stock tuning
                success &= setPerformanceModeActive(1);
                // Apply default tuning with msm-adreno-tz GPU governor
                applyHardwareTuning(MODE_BALANCE);
                saveCurrentModeFrequencies(MODE_BALANCE);
                restoreAllTouchPanel();
                break;

            case MODE_PERFORMANCE:
                success &= setPerformanceModeActive(2);
                applyHardwareTuning(MODE_PERFORMANCE);
                saveCurrentModeFrequencies(MODE_PERFORMANCE);
                enableAllTouchPanel();
                
                // PERFORMANCE optimizations
                triggerMemoryTrim(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW);
                break;

            case MODE_BATTERY_SAVER:
                success &= setPerformanceModeActive(0);
                applyHardwareTuning(MODE_BATTERY_SAVER);
                saveCurrentModeFrequencies(MODE_BATTERY_SAVER);
                disableAllTouchPanel();
                
                // POWERSAVE optimizations
                triggerMemoryTrim(ComponentCallbacks2.TRIM_MEMORY_COMPLETE);
                break;

            case MODE_MANUAL:
                success &= setPerformanceModeActive(1);
                restoreAllTouchPanel();
                break;

            default:
                success &= setPerformanceModeActive(1);
                restoreAllTouchPanel();
                break;
        }

        return success;
    }

    private void saveLastProfile(int mode) {
        SharedPreferences prefs = mContext.getSharedPreferences(
                mContext.getPackageName() + "_preferences",
                Context.MODE_PRIVATE);

        prefs.edit().putString(KEY_LAST_PROFILE, String.valueOf(mode)).apply();
    }

    /**
     * Saves the hardcoded frequencies to SharedPreferences so UI can display them
     */
    private void saveCurrentModeFrequencies(int mode) {
        SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(mContext);
        SharedPreferences.Editor editor = prefs.edit();

        String lMin = "300000", lMax = "1804800";
        String bMin = "710400", bMax = "2419200";
        String pMin = "844800", pMax = "2841600";
        String gMin = "315000000", gMax = "840000000";

        if (mode == MODE_BATTERY_SAVER) {
            bMax = "2212000";
            pMax = "2592000";
            gMax = "579000000";
        } else if (mode == MODE_PERFORMANCE) {
            lMin = "640800";
            bMin = "844800";
            pMin = "917100";
            gMin = "676000000";
        }

        // Save CPU Little frequencies
        editor.putString("cpu_little_min_frequency", lMin);
        editor.putString("cpu_little_max_frequency", lMax);

        // Save CPU Big frequencies
        editor.putString("cpu_big_min_frequency", bMin);
        editor.putString("cpu_big_max_frequency", bMax);

        // Save CPU Prime frequencies
        editor.putString("cpu_prime_min_frequency", pMin);
        editor.putString("cpu_prime_max_frequency", pMax);

        // Save GPU frequencies
        editor.putString("gpu_min_frequency", gMin);
        editor.putString("gpu_max_frequency", gMax);

        editor.apply();
    }

    private void enableAllTouchPanel() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        if (Utils.fileWritable(FILE_GAME)) {
            prefs.edit().putBoolean(KEY_GAME_ORIG, Utils.getFileValueAsBoolean(FILE_GAME, false)).apply();
            Utils.writeValue(FILE_GAME, "1");
        }
        if (Utils.fileWritable(FILE_EDGE)) {
            prefs.edit().putBoolean(KEY_EDGE_ORIG, Utils.getFileValueAsBoolean(FILE_EDGE, false)).apply();
            Utils.writeValue(FILE_EDGE, "1");
        }
    }

    private void disableAllTouchPanel() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        if (Utils.fileWritable(FILE_GAME)) {
            prefs.edit().putBoolean(KEY_GAME_ORIG, Utils.getFileValueAsBoolean(FILE_GAME, false)).apply();
            Utils.writeValue(FILE_GAME, "0");
        }
        if (Utils.fileWritable(FILE_EDGE)) {
            prefs.edit().putBoolean(KEY_EDGE_ORIG, Utils.getFileValueAsBoolean(FILE_EDGE, false)).apply();
            Utils.writeValue(FILE_EDGE, "0");
        }
    }

    private void restoreAllTouchPanel() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        if (Utils.fileWritable(FILE_GAME)) {
            Utils.writeValue(FILE_GAME, prefs.getBoolean(KEY_GAME_ORIG, false) ? "1" : "0");
        }
        if (Utils.fileWritable(FILE_EDGE)) {
            Utils.writeValue(FILE_EDGE, prefs.getBoolean(KEY_EDGE_ORIG, false) ? "1" : "0");
        }
    }

    private void triggerMemoryTrim(int level) {
        ActivityManager am = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return;
        try {
            am.getClass().getMethod("trimMemory", int.class).invoke(am, level);
            Log.d(TAG, "Memory trim executed at level: " + level);
        } catch (Exception e) {
            Log.e(TAG, "Failed to execute trimMemory", e);
        }
    }



   private void applyHardwareTuning(int mode) {
        String lGov = "schedutil", bGov = "schedutil", pGov = "schedutil";
        String gpuGov = "msm-adreno-tz"; 
        String gMin = "315000000", gMax = "840000000";
        String ioSched = "bfq";
        boolean clkScale = false; // 0 = disabled (better performance)

        if (mode == MODE_BATTERY_SAVER) {
            gpuGov = "powersave";
            gMax = "579000000";
            ioSched = "bfq";
            clkScale = true; // 1 = enabled (saves battery)
        } else if (mode == MODE_PERFORMANCE) {
            gpuGov = "msm-adreno-tz";
            gMin = "676000000";
            lGov = "performance";
            bGov = "performance";
            pGov = "performance";
            ioSched = "kyber"; // Max I/O throughput
        }

        CPUUtils.setCPULittleFreq("300000", "1804800", lGov);
        CPUUtils.setCPUBigFreq("710400", "2419200", bGov);
        CPUUtils.setCPUPrimeFreq("844800", "2841600", pGov);
        GPUUtils.setGPUMinFrequency(gMin);
        GPUUtils.setGPUMaxFrequency(gMax);
        GPUUtils.setGPUGovernor(gpuGov);
        
        // Push Storage Settings
        StorageUtils.setIoScheduler(ioSched);
        StorageUtils.setUfsClkScale(clkScale);

        // Scheduler and input boost tuning
        if (mode == MODE_BATTERY_SAVER) {
            SysfsUtils.writeValue("/proc/sys/kernel/sched_upmigrate", "90 90");
            SysfsUtils.writeValue("/proc/sys/kernel/sched_downmigrate", "85 85");
            SysfsUtils.writeValue("/proc/sys/kernel/sched_boost", "0");
            SysfsUtils.writeValue("/sys/devices/system/cpu/cpu_boost/input_boost_freq", "0:1094400");
            SysfsUtils.writeValue("/sys/devices/system/cpu/cpu_boost/input_boost_ms", "80");
        } else if (mode == MODE_PERFORMANCE) {
            SysfsUtils.writeValue("/proc/sys/kernel/sched_upmigrate", "85 85");
            SysfsUtils.writeValue("/proc/sys/kernel/sched_downmigrate", "70 70");
            SysfsUtils.writeValue("/proc/sys/kernel/sched_boost", "1");
            SysfsUtils.writeValue("/sys/devices/system/cpu/cpu_boost/input_boost_freq", "0:1305600");
            SysfsUtils.writeValue("/sys/devices/system/cpu/cpu_boost/input_boost_ms", "200");
        } else if (mode == MODE_BALANCE) {
            SysfsUtils.writeValue("/proc/sys/kernel/sched_upmigrate", "95 95");
            SysfsUtils.writeValue("/proc/sys/kernel/sched_downmigrate", "85 85");
            SysfsUtils.writeValue("/proc/sys/kernel/sched_boost", "0");
            SysfsUtils.writeValue("/sys/devices/system/cpu/cpu_boost/input_boost_freq", "0:1305600");
            SysfsUtils.writeValue("/sys/devices/system/cpu/cpu_boost/input_boost_ms", "120");
        }
    }

    public int getManagedMode() {
        if (isAutoModeEnabled()) {
            mCurrentMode = MODE_AUTO;
            return MODE_AUTO;
        }
        mCurrentMode = getCurrentMode();
        return mCurrentMode;
    }

    public String getModeLabel() {
        if (mCurrentMode >= 0 && mCurrentMode < mModes.length)
            return mModes[mCurrentMode];

        return mModes[MODE_UNKNOWN];
    }

    public void toggleMode() {
        int currentMode = getManagedMode();
        int newMode;

        switch (currentMode) {
            case MODE_BALANCE:
                newMode = MODE_PERFORMANCE;
                break;
            case MODE_PERFORMANCE:
                newMode = MODE_BATTERY_SAVER;
                break;
            default:
                newMode = MODE_BALANCE;
                break;
        }

        setMode(newMode);
    }

    private boolean setPerformanceModeActive(int mode) {
        try {
            SystemProperties.set(SYS_PROP, String.valueOf(mode));
            SystemProperties.set("persist.sys.perf_mode_saved", String.valueOf(mode));
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}