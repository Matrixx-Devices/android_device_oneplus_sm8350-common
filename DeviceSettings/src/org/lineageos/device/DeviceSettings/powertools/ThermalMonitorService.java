/*
 * Copyright (C) 2025 kenrow214
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.device.DeviceSettings.powertools;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Process;
import android.os.SystemProperties;
import android.provider.Settings;
import android.util.Log;

import org.lineageos.device.DeviceSettings.R;

public class ThermalMonitorService extends Service {

    private static final String TAG = "ThermalMonitorService";

    private static final String NOTIF_CHANNEL = "thermal_monitor";
    private static final int NOTIF_ID = 2001;

    private static final String BATTERY_TEMP_PATH = "/sys/class/power_supply/battery/temp";
    private static final String CPU_TEMP_PATH = "/sys/class/thermal/thermal_zone39/temp";
    private static final String GPU_TEMP_PATH = "/sys/class/thermal/thermal_zone54/temp";

    public static final int STATE_NORMAL = 0;
    public static final int STATE_LIGHT = 1;
    public static final int STATE_MEDIUM = 2;
    public static final int STATE_HEAVY = 3;

    public static final int THRESH_LIGHT = 45;
    public static final int THRESH_MEDIUM = 49;
    public static final int THRESH_HEAVY = 55;


    private static final int[] SETTING_LOW_POWER = {0, 0, 0, 1}; // 1 at HEAVY
    private static final int[] SETTING_BLUR_DISABLE = {0, 0, 1, 1}; // 1 at MEDIUM and HEAVY
    
    private static final String[] STATE_LABELS = {
        "no throttle",
        "LIGHT throttle (\u226545\u00b0C)",
        "MEDIUM throttle (\u226549\u00b0C)",
        "HEAVY throttle (\u226555\u00b0C)"
    };

    private static volatile int sCurrentState = -1; // -1 forces initial application
    private static volatile float sBatteryTempC = 0f;
    private static volatile float sCpuTempC = 0f;
    private static volatile float sGpuTempC = 0f;

    private HandlerThread mWorkerThread;
    private Handler mHandler;
    private Runnable mMonitorRunnable;
    private boolean mFirstTick = true;

    public static int getCurrentState() { return Math.max(0, sCurrentState); }
    public static float getBatteryTempC() { return sBatteryTempC; }
    public static float getCpuTempC() { return sCpuTempC; }
    public static float getGpuTempC() { return sGpuTempC; }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Starting thermal service");
        setupNotificationChannel();
        startForegroundServiceSafe();
        
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
        
        if (mHandler != null) {
            mHandler.post(this::resetHardwareToNormal);
            mWorkerThread.quitSafely();
        }

        stopForeground(true);
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(NOTIF_ID);

        Log.i(TAG, "Stopped thermal service");
        super.onDestroy();
    }


    private void startMonitoring() {
        mFirstTick = true;
        mMonitorRunnable = new Runnable() {
            @Override
            public void run() {
                readAllTemperatures();
                int batteryC = (int) sBatteryTempC;
                int newState = calculateState(batteryC);
                
                applyStateIfChanged(newState);

                if (mFirstTick) {
                    mFirstTick = false;
                    updateNotificationTemp();
                }

                mHandler.postDelayed(this, getPollingDelayMs(batteryC));
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
        sBatteryTempC = SysfsUtils.readInt(BATTERY_TEMP_PATH, 0) / 10f;
        
        int rawCPU = SysfsUtils.readInt(CPU_TEMP_PATH, 0);
        sCpuTempC = rawCPU > 1000 ? rawCPU / 1000f : rawCPU / 10f;

        int rawGPU = SysfsUtils.readInt(GPU_TEMP_PATH, 0);
        sGpuTempC = rawGPU > 1000 ? rawGPU / 1000f : rawGPU / 10f;
    }

    private int calculateState(int tempC) {
        if (tempC >= THRESH_HEAVY) return STATE_HEAVY;
        if (tempC >= THRESH_MEDIUM) return STATE_MEDIUM;
        if (tempC >= THRESH_LIGHT) return STATE_LIGHT;
        return STATE_NORMAL;
    }

    private int getPollingDelayMs(int batteryC) {
        if (batteryC >= THRESH_HEAVY) return 1500;
        if (batteryC >= THRESH_MEDIUM) return 2000;
        if (batteryC >= THRESH_LIGHT) return 2500;
        return 4000; // Normal polling interval
    }

    private void applyStateIfChanged(int targetState) {
        if (targetState == sCurrentState) return;
        sCurrentState = targetState;

        applyProfileToHardware(targetState);
        updateGlobalSettings(targetState);

        Log.i(TAG, String.format("Auto Thermal: Battery=%.1f\u00b0C -> %s", sBatteryTempC, STATE_LABELS[targetState]));

        updateNotification(STATE_LABELS[targetState], 
            String.format("Bat:%.0f\u00b0C CPU:%.0f\u00b0C GPU:%.0f\u00b0C", sBatteryTempC, sCpuTempC, sGpuTempC));
    }

    private void applyProfileToHardware(int stateIndex) {
        try {
            SystemProperties.set("sys.thermal_state", String.valueOf(stateIndex));
        } catch (Exception e) {
            Log.e(TAG, "Failed to set thermal profile property", e);
        }
    }

    private void updateGlobalSettings(int stateIndex) {
        try {
            Settings.Global.putInt(getContentResolver(), "low_power", SETTING_LOW_POWER[stateIndex]);
            BlurUtils.setBlurDisabled(this, SETTING_BLUR_DISABLE[stateIndex] == 1);
        } catch (Exception e) {
            Log.e(TAG, "Failed to apply global settings", e);
        }
    }

    private void resetHardwareToNormal() {
        applyProfileToHardware(STATE_NORMAL);
        updateGlobalSettings(STATE_NORMAL);
        sCurrentState = STATE_NORMAL;

        try {
            SystemProperties.set("sys.perf_mode_active", String.valueOf(PowerProfileUtil.MODE_BALANCE));
        } catch (Exception e) {
            Log.e(TAG, "Failed to restore normal perf mode on thermal stop", e);
        }
    }


    private void startForegroundServiceSafe() {
        Notification notification = buildNotification("Thermal Monitor", "Starting...");
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, notification, 0); // 0 = no specific type
        } else {
            startForeground(NOTIF_ID, notification);
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

    private void updateNotification(String stateLabel, String tempValues) {
        Notification n = buildNotification(
                getString(R.string.auto_thermal_notif_title),
                getString(R.string.auto_thermal_notif_text, tempValues, stateLabel));
        getSystemService(NotificationManager.class).notify(NOTIF_ID, n);
    }

    private void updateNotificationTemp() {
        int state = getCurrentState();
        String temps = String.format("Bat:%.0f\u00b0C  CPU:%.0f\u00b0C  GPU:%.0f\u00b0C", 
                                     sBatteryTempC, sCpuTempC, sGpuTempC);
        
        String label = (state >= 0 && state < STATE_LABELS.length) ? STATE_LABELS[state] : "Normal";
        
        if (label.contains("(")) label = label.substring(0, label.indexOf("(")).trim();
        
        updateNotification(label, temps);
    }
}