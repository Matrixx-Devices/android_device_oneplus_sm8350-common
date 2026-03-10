/*
 * Copyright (C) 2025 kenrow214
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.device.DeviceSettings.powertools;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;
import android.os.Environment;
import android.os.StatFs;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.util.Log;
import android.widget.RemoteViews;

import org.lineageos.device.DeviceSettings.R;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.RandomAccessFile;

public class SystemMonitorWidget extends AppWidgetProvider {

    private static final String TAG = "SystemMonitorWidget";
    private static final String ACTION_TICK = "org.lineageos.device.DeviceSettings.WIDGET_TICK";
    private static final long UPDATE_INTERVAL_MS = 3000;

    // Sysfs
    private static final String BATTERY_TEMP = "/sys/class/power_supply/battery/temp";
    private static final String CPU_TEMP = "/sys/class/thermal/thermal_zone0/temp";
    private static final String GPU_TEMP = "/sys/class/thermal/thermal_zone20/temp";
    private static final String GPU_CUR_FREQ = "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq";
    private static final String GPU_MAX_FREQ_PATH = "/sys/class/kgsl/kgsl-3d0/devfreq/max_freq";

    // Persistent CPU idle tracking between ticks
    private static long sLastCpuTotal = 0;
    private static long sLastCpuIdle = 0;

    private static final String[] MODE_LABELS = {"PowerSave", "Normal", "Performance", "Manual", "", "Auto"};

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        updateAllWidgets(ctx, mgr, ids);
        scheduleNextUpdate(ctx);
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent);
        if (ACTION_TICK.equals(intent.getAction())) {
            AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
            int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, SystemMonitorWidget.class));
            if (ids != null && ids.length > 0) {
                updateAllWidgets(ctx, mgr, ids);
                scheduleNextUpdate(ctx);
            }
        }
    }

    @Override
    public void onEnabled(Context ctx) {
        super.onEnabled(ctx);
        scheduleNextUpdate(ctx);
    }

    @Override
    public void onDisabled(Context ctx) {
        super.onDisabled(ctx);
        cancelUpdates(ctx);
    }

    private void scheduleNextUpdate(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        am.setExact(AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + UPDATE_INTERVAL_MS, getTickPI(ctx));
    }

    private void cancelUpdates(Context ctx) {
        ((AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE)).cancel(getTickPI(ctx));
    }

    private PendingIntent getTickPI(Context ctx) {
        Intent i = new Intent(ctx, SystemMonitorWidget.class).setAction(ACTION_TICK);
        return PendingIntent.getBroadcast(ctx, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void updateAllWidgets(Context ctx, AppWidgetManager mgr, int[] ids) {
        int cpuPct = getCpuUsage();
        int[] gpu = getGpuInfo();
        int[] mem = getMemoryInfo(ctx);
        int[] swap = getSwapInfo();
        int bat = getBatteryLevel(ctx);
        int[] stor = getStorageInfo();
        String modeLabel = getModeLabel();

        float batTemp = readTempC(BATTERY_TEMP, 10f);
        float cpuTemp = readTempC(CPU_TEMP, 1000f);
        float gpuTemp = readTempC(GPU_TEMP, 1000f);

        for (int id : ids) {
            RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_system_monitor);

            // Mode badge
            v.setTextViewText(R.id.widget_mode, modeLabel);

            // Temps
            v.setTextViewText(R.id.widget_bat_temp, String.format("\uD83D\uDD0B %.0f°C", batTemp));
            v.setTextViewText(R.id.widget_cpu_temp, String.format("\uD83D\uDDA5 %.0f°C", cpuTemp));
            v.setTextViewText(R.id.widget_gpu_temp, String.format("\uD83C\uDFAE %.0f°C", gpuTemp));

            // CPU
            v.setProgressBar(R.id.widget_cpu_bar, 100, cpuPct, false);
            v.setTextViewText(R.id.widget_cpu_text, cpuPct + "%");

            // GPU
            v.setProgressBar(R.id.widget_gpu_bar, 100, gpu[0], false);
            v.setTextViewText(R.id.widget_gpu_text, gpu[1] + "MHz");

            // RAM
            v.setProgressBar(R.id.widget_mem_bar, 100, mem[0], false);
            v.setTextViewText(R.id.widget_mem_text, mem[0] + "%");

            // SWAP
            v.setProgressBar(R.id.widget_swap_bar, 100, swap[0], false);
            v.setTextViewText(R.id.widget_swap_text, swap[0] + "%");

            // Battery
            v.setProgressBar(R.id.widget_bat_bar, 100, bat, false);
            v.setTextViewText(R.id.widget_bat_text, bat + "%");

            // Storage
            v.setProgressBar(R.id.widget_storage_bar, 100, stor[0], false);
            v.setTextViewText(R.id.widget_storage_text, stor[0] + "%");

            // Click → open PowerTools
            Intent openApp = new Intent(ctx, PowertoolsActivity.class);
            v.setOnClickPendingIntent(R.id.widget_root,
                    PendingIntent.getActivity(ctx, 0, openApp,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

            mgr.updateAppWidget(id, v);
        }
    }

    // ---- Stat readers ----

    /** CPU usage via /proc/stat diff between consecutive widget ticks (no sleep needed) */
    private int getCpuUsage() {
        try {
            RandomAccessFile r = new RandomAccessFile("/proc/stat", "r");
            String line = r.readLine();
            r.close();

            String[] p = line.split("\\s+");
            long idle = Long.parseLong(p[4]) + Long.parseLong(p[5]); // idle + iowait
            long total = 0;
            for (int i = 1; i < p.length; i++) total += Long.parseLong(p[i]);

            int pct = 0;
            if (sLastCpuTotal > 0) {
                long dTotal = total - sLastCpuTotal;
                long dIdle = idle - sLastCpuIdle;
                pct = dTotal > 0 ? (int) ((dTotal - dIdle) * 100 / dTotal) : 0;
            }
            sLastCpuTotal = total;
            sLastCpuIdle = idle;
            return Math.max(0, Math.min(100, pct));
        } catch (Exception e) {
            return 0;
        }
    }

    private int[] getGpuInfo() {
        try {
            long cur = Long.parseLong(readSysfs(GPU_CUR_FREQ).trim());
            long max = Long.parseLong(readSysfs(GPU_MAX_FREQ_PATH).trim());
            return new int[]{max > 0 ? (int) (cur * 100 / max) : 0, (int) (cur / 1000000)};
        } catch (Exception e) {
            return new int[]{0, 0};
        }
    }

    private int[] getMemoryInfo(Context ctx) {
        try {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            long total = mi.totalMem / (1024 * 1024);
            long used = total - mi.availMem / (1024 * 1024);
            return new int[]{(int) (used * 100 / total)};
        } catch (Exception e) {
            return new int[]{0};
        }
    }

    private int[] getSwapInfo() {
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/meminfo"))) {
            long swapTotal = 0, swapFree = 0;
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("SwapTotal:")) swapTotal = parseMemLine(line);
                else if (line.startsWith("SwapFree:")) swapFree = parseMemLine(line);
            }
            if (swapTotal == 0) return new int[]{0};
            return new int[]{(int) ((swapTotal - swapFree) * 100 / swapTotal)};
        } catch (Exception e) {
            return new int[]{0};
        }
    }

    private long parseMemLine(String line) {
        String[] parts = line.split("\\s+");
        return parts.length >= 2 ? Long.parseLong(parts[1]) : 0; // value in kB
    }

    private int getBatteryLevel(Context ctx) {
        try {
            BatteryManager bm = (BatteryManager) ctx.getSystemService(Context.BATTERY_SERVICE);
            return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        } catch (Exception e) {
            return 0;
        }
    }

    private int[] getStorageInfo() {
        try {
            StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
            long total = stat.getTotalBytes();
            long used = total - stat.getAvailableBytes();
            return new int[]{total > 0 ? (int) (used * 100 / total) : 0};
        } catch (Exception e) {
            return new int[]{0};
        }
    }

    private String getModeLabel() {
        try {
            int mode = SystemProperties.getInt("sys.perf_mode_active", 1);
            if (mode >= 0 && mode < MODE_LABELS.length) return MODE_LABELS[mode];
        } catch (Exception ignored) {}
        return "Normal";
    }

    private float readTempC(String path, float divisor) {
        try {
            return Integer.parseInt(readSysfs(path).trim()) / divisor;
        } catch (Exception e) {
            return 0;
        }
    }

    private String readSysfs(String path) {
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            return br.readLine();
        } catch (Exception e) {
            return "0";
        }
    }
}
