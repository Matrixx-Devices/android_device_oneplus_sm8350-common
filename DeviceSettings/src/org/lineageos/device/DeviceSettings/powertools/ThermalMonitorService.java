/*
 * Copyright (C) 2025 kenrow214
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.device.DeviceSettings.powertools;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ComponentCallbacks2;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Process;
import android.provider.Settings;
import android.util.Log;

import androidx.preference.PreferenceManager;

import org.lineageos.device.DeviceSettings.R;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ThermalMonitorService extends Service {

    private static final String TAG = "ThermalMonitorService";

    private static final String NOTIF_CHANNEL = "thermal_monitor";
    private static final int NOTIF_ID = 2001;

    private static final String BATTERY_TEMP_PATH = "/sys/class/power_supply/battery/temp";
    private static final String CPU_TEMP_PATH = "/sys/class/thermal/thermal_zone0/temp";
    private static final String GPU_TEMP_PATH = "/sys/class/thermal/thermal_zone20/temp";

    private static final String CPU_LITTLE_MAX = "/sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq";
    private static final String CPU_BIG_MAX = "/sys/devices/system/cpu/cpufreq/policy4/scaling_max_freq";
    private static final String CPU_PRIME_MAX = "/sys/devices/system/cpu/cpufreq/policy7/scaling_max_freq";
    private static final String GPU_MAX_FREQ = "/sys/class/kgsl/kgsl-3d0/devfreq/max_freq";

    public static final int THRESH_LIGHT = 45;
    public static final int THRESH_MEDIUM = 49;
    public static final int THRESH_HEAVY = 55;

    private static final String LITTLE_NORMAL = "1804800";
    private static final String LITTLE_LIGHT = "1555200";
    private static final String LITTLE_MEDIUM = "1324800";
    private static final String LITTLE_HEAVY = "1132800";

    private static final String BIG_NORMAL = "2419200";
    private static final String BIG_LIGHT = "2112000";
    private static final String BIG_MEDIUM = "1881600";
    private static final String BIG_HEAVY = "1555200";

    private static final String PRIME_NORMAL = "2841600";
    private static final String PRIME_LIGHT = "2476800";
    private static final String PRIME_MEDIUM = "2131200";
    private static final String PRIME_HEAVY = "1766400";

    private static final String GPU_NORMAL = "840000000";
    private static final String GPU_LIGHT = "676000000";
    private static final String GPU_MEDIUM = "540000000";
    private static final String GPU_HEAVY = "379000000";

    public static final int STATE_NORMAL = 0;
    public static final int STATE_LIGHT = 1;
    public static final int STATE_MEDIUM = 2;
    public static final int STATE_HEAVY = 3;

    private static volatile int sCurrentState = STATE_NORMAL;
    private static volatile float sBatteryTempC = 0f;
    private static volatile float sCpuTempC = 0f;
    private static volatile float sGpuTempC = 0f;

    private HandlerThread mWorkerThread;
    private Handler mHandler;
    private Runnable mMonitorRunnable;
    private boolean mFirstTick = true; 

    public static int getCurrentState() {
        return sCurrentState;
    }

    public static float getBatteryTempC() {
        return sBatteryTempC;
    }

    public static float getCpuTempC() {
        return sCpuTempC;
    }

    public static float getGpuTempC() {
        return sGpuTempC;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Starting thermal service");
        setupNotificationChannel();
        
        // Note: You may need to specify foregroundServiceType depending on your target SDK
        startForeground(NOTIF_ID, buildNotification("Thermal Monitor", "Starting..."));
        
        // Start a dedicated background thread for hardware polling
        mWorkerThread = new HandlerThread("ThermalMonitorThread", Process.THREAD_PRIORITY_BACKGROUND);
        mWorkerThread.start();
        mHandler = new Handler(mWorkerThread.getLooper());
        
        startMonitoring();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopMonitoring();
        resetFrequencies();
        
        // Force-kill the orphaned notification
        stopForeground(true); 
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.cancel(NOTIF_ID);
        }

        if (mWorkerThread != null) {
            mWorkerThread.quitSafely();
        }
        Log.i(TAG, "Stopped thermal service");
        super.onDestroy();
    }

    private void startMonitoring() {
        sCurrentState = -1;
        mFirstTick = true;
        mMonitorRunnable = new Runnable() {
            @Override
            public void run() {
                readAllTemperatures();
                int batteryC = (int) sBatteryTempC;
                int newState = stateForTemp(batteryC);
                applyStateIfChanged(newState);

                if (mFirstTick) {
                    mFirstTick = false;
                    updateNotificationTemp();
                }

                int delayMs = (batteryC >= THRESH_HEAVY) ? 1500
                        : (batteryC >= THRESH_MEDIUM) ? 2000
                        : (batteryC >= THRESH_LIGHT)  ? 2500
                        : 4000;
                mHandler.postDelayed(this, delayMs);
            }
        };
        mHandler.post(mMonitorRunnable);
    }

    private void stopMonitoring() {
        if (mHandler != null && mMonitorRunnable != null) {
            mHandler.removeCallbacks(mMonitorRunnable);
        }
    }

    private void readAllTemperatures() {
        int rawBattery = SysfsUtils.readInt(BATTERY_TEMP_PATH, 0);
        sBatteryTempC = rawBattery / 10f;

        int rawCPU = SysfsUtils.readInt(CPU_TEMP_PATH, 0);
        sCpuTempC = rawCPU > 1000 ? rawCPU / 1000f : rawCPU / 10f;

        int rawGPU = SysfsUtils.readInt(GPU_TEMP_PATH, 0);
        sGpuTempC = rawGPU > 1000 ? rawGPU / 1000f : rawGPU / 10f;
    }

    private int stateForTemp(int tempC) {
        if (tempC >= THRESH_HEAVY)
            return STATE_HEAVY;
        if (tempC >= THRESH_MEDIUM)
            return STATE_MEDIUM;
        if (tempC >= THRESH_LIGHT)
            return STATE_LIGHT;
        return STATE_NORMAL;
    }

    private void applyStateIfChanged(int newState) {
        if (newState == sCurrentState)
            return;
        sCurrentState = newState;

        // Initialize variables to defaults to prevent compilation errors
        String little = LITTLE_NORMAL;
        String big = BIG_NORMAL;
        String prime = PRIME_NORMAL;
        String gpu = GPU_NORMAL;
        String label = "no throttle";

        switch (newState) {
            case STATE_HEAVY:
                little = LITTLE_HEAVY;
                big = BIG_HEAVY;
                prime = PRIME_HEAVY;
                gpu = GPU_HEAVY;
                label = "HEAVY throttle (\u226555\u00b0C)";
                
                SysfsUtils.writeValue("/dev/cpuset/foreground/cpus", "0-3");
                SysfsUtils.writeValue("/dev/cpuset/system-background/cpus", "0-1");
                SysfsUtils.writeValue("/sys/devices/system/cpu/cpu4/core_ctl/min_cpus", "0");
                SysfsUtils.writeValue("/sys/devices/system/cpu/cpu7/core_ctl/min_cpus", "0");
                SysfsUtils.writeValue("/proc/sys/kernel/sched_upmigrate", "98 98");
                SysfsUtils.writeValue("/proc/sys/kernel/sched_downmigrate", "95 95");
                
                // Enforce Battery Saver & Disable Blur
                try {
                    Settings.Global.putInt(getContentResolver(), "low_power", 1);
                    Settings.Global.putInt(getContentResolver(), "disable_window_blurs", 1);
                } catch (Exception ignored) {}
                break;
            case STATE_MEDIUM:
                little = LITTLE_MEDIUM;
                big = BIG_MEDIUM;
                prime = PRIME_MEDIUM;
                gpu = GPU_MEDIUM;
                label = "MEDIUM throttle (\u226549\u00b0C)";
                
                SysfsUtils.writeValue("/dev/cpuset/foreground/cpus", "0-6");
                SysfsUtils.writeValue("/dev/cpuset/system-background/cpus", "0-3");
                SysfsUtils.writeValue("/sys/devices/system/cpu/cpu4/core_ctl/min_cpus", "1");
                SysfsUtils.writeValue("/sys/devices/system/cpu/cpu7/core_ctl/min_cpus", "0");
                SysfsUtils.writeValue("/proc/sys/kernel/sched_upmigrate", "95 95");
                SysfsUtils.writeValue("/proc/sys/kernel/sched_downmigrate", "90 90");
                
                // Disable Blur, keep Battery Saver normal
                try {
                    Settings.Global.putInt(getContentResolver(), "low_power", 0);
                    Settings.Global.putInt(getContentResolver(), "disable_window_blurs", 1);
                } catch (Exception ignored) {}
                break;
            case STATE_LIGHT:
                little = LITTLE_LIGHT;
                big = BIG_LIGHT;
                prime = PRIME_LIGHT;
                gpu = GPU_LIGHT;
                label = "LIGHT throttle (\u226545\u00b0C)";
                
                SysfsUtils.writeValue("/dev/cpuset/foreground/cpus", "0-6");
                SysfsUtils.writeValue("/dev/cpuset/system-background/cpus", "0-3");
                SysfsUtils.writeValue("/sys/devices/system/cpu/cpu4/core_ctl/min_cpus", "2");
                SysfsUtils.writeValue("/sys/devices/system/cpu/cpu7/core_ctl/min_cpus", "0");
                SysfsUtils.writeValue("/proc/sys/kernel/sched_upmigrate", "95 95");
                SysfsUtils.writeValue("/proc/sys/kernel/sched_downmigrate", "85 85");
                
                // Keep Blur normal, Battery Saver normal
                try {
                    Settings.Global.putInt(getContentResolver(), "low_power", 0);
                    Settings.Global.putInt(getContentResolver(), "disable_window_blurs", 0);
                } catch (Exception ignored) {}
                break;
            case STATE_NORMAL:
            default:
                // Values are already initialized to NORMAL defaults above
                label = "no throttle";
                
                // Restoring typical normal behavior (adjust cpusets based on your specific device tree)
                SysfsUtils.writeValue("/dev/cpuset/foreground/cpus", "0-7");
                SysfsUtils.writeValue("/dev/cpuset/system-background/cpus", "0-3");
                SysfsUtils.writeValue("/sys/devices/system/cpu/cpu4/core_ctl/min_cpus", "2");
                SysfsUtils.writeValue("/sys/devices/system/cpu/cpu7/core_ctl/min_cpus", "0");
                SysfsUtils.writeValue("/proc/sys/kernel/sched_upmigrate", "95 95");
                SysfsUtils.writeValue("/proc/sys/kernel/sched_downmigrate", "85 85");
                
                // Restore Blur and Battery Saver to off
                try {
                    Settings.Global.putInt(getContentResolver(), "low_power", 0);
                    Settings.Global.putInt(getContentResolver(), "disable_window_blurs", 0);
                } catch (Exception ignored) {}
                break;
        }

        SysfsUtils.writeValue(CPU_LITTLE_MAX, little);
        SysfsUtils.writeValue(CPU_BIG_MAX, big);
        SysfsUtils.writeValue(CPU_PRIME_MAX, prime);
        SysfsUtils.writeValue(GPU_MAX_FREQ, gpu);

        Log.i(TAG, String.format("Auto Thermal: Battery=%.1f°C -> %s", sBatteryTempC, label));

        updateNotification(label, String.format("Bat:%.0f\u00b0C CPU:%.0f\u00b0C GPU:%.0f\u00b0C",
                sBatteryTempC, sCpuTempC, sGpuTempC));
    }

    private void resetFrequencies() {
        // We use the worker thread to handle the reset so onDestroy returns instantly
        if (mHandler != null) {
            mHandler.post(() -> {
                SysfsUtils.writeValue(CPU_LITTLE_MAX, LITTLE_NORMAL);
                SysfsUtils.writeValue(CPU_BIG_MAX, BIG_NORMAL);
                SysfsUtils.writeValue(CPU_PRIME_MAX, PRIME_NORMAL);
                SysfsUtils.writeValue(GPU_MAX_FREQ, GPU_NORMAL);
                
                SysfsUtils.writeValue("/dev/cpuset/foreground/cpus", "0-7");
                SysfsUtils.writeValue("/dev/cpuset/system-background/cpus", "0-3");
                SysfsUtils.writeValue("/sys/devices/system/cpu/cpu4/core_ctl/min_cpus", "2");
                SysfsUtils.writeValue("/sys/devices/system/cpu/cpu7/core_ctl/min_cpus", "0");
                SysfsUtils.writeValue("/proc/sys/kernel/sched_upmigrate", "95 95");
                SysfsUtils.writeValue("/proc/sys/kernel/sched_downmigrate", "85 85");
                sCurrentState = STATE_NORMAL;
            });
        }
    }

    private void setupNotificationChannel() {
        NotificationChannel ch = new NotificationChannel(
                NOTIF_CHANNEL,
                getString(R.string.auto_thermal_notif_channel),
                NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    private Notification buildNotification(String title, String text) {
        return new Notification.Builder(this, NOTIF_CHANNEL)
                .setSmallIcon(R.drawable.ic_thermal_balance)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String state, String temp) {
        Notification n = buildNotification(
                getString(R.string.auto_thermal_notif_title),
                getString(R.string.auto_thermal_notif_text, temp, state));
        getSystemService(NotificationManager.class).notify(NOTIF_ID, n);
    }

    private void updateNotificationTemp() {
        String stateLabel;
        switch (sCurrentState) {
            case STATE_HEAVY:  stateLabel = "Heavy \u226555\u00b0C";  break;
            case STATE_MEDIUM: stateLabel = "Medium \u226549\u00b0C"; break;
            case STATE_LIGHT:  stateLabel = "Light \u226545\u00b0C";  break;
            default:           stateLabel = "Normal";         break;
        }
        String temps = String.format("Bat:%.0f\u00b0C  CPU:%.0f\u00b0C  GPU:%.0f\u00b0C",
                sBatteryTempC, sCpuTempC, sGpuTempC);
        updateNotification(stateLabel, temps);
    }
}