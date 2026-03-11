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
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Environment;
import android.os.StatFs;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.util.Log;
import android.widget.RemoteViews;

import org.lineageos.device.DeviceSettings.R;

import android.content.res.ColorStateList;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.RandomAccessFile;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SystemMonitorWidget extends AppWidgetProvider {

    private static final String TAG = "SystemMonitorWidget";
    private static final String ACTION_TICK = "org.lineageos.device.DeviceSettings.WIDGET_TICK";
    
    private static final long UPDATE_INTERVAL_MS = 3000;

    // Sysfs Paths
    private static final String BATTERY_TEMP = "/sys/class/power_supply/battery/temp";
    private static final String CPU_TEMP = "/sys/class/thermal/thermal_zone0/temp";
    private static final String GPU_TEMP = "/sys/class/thermal/thermal_zone20/temp";
    private static final String GPU_CUR_FREQ = "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq";
    private static final String GPU_MAX_FREQ_PATH = "/sys/class/kgsl/kgsl-3d0/devfreq/max_freq";

    private static final String[] MODE_LABELS = {"PowerSave", "Normal", "Performance", "", "", "Auto"};

    // State & Threading
    private static long sLastCpuTotal = 0;
    private static long sLastCpuIdle = 0;
    private static final ExecutorService sBackgroundExecutor = Executors.newSingleThreadExecutor();

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        processUpdateAsync(ctx, mgr, ids);
        scheduleNextUpdate(ctx);
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent);
        if (ACTION_TICK.equals(intent.getAction())) {
            AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
            int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, SystemMonitorWidget.class));
            if (ids != null && ids.length > 0) {
                processUpdateAsync(ctx, mgr, ids);
                scheduleNextUpdate(ctx);
            }
        }
    }

    private static BroadcastReceiver sScreenReceiver;

    @Override
    public void onEnabled(Context ctx) {
        super.onEnabled(ctx);
        if (sScreenReceiver != null) {
            try { ctx.getApplicationContext().unregisterReceiver(sScreenReceiver); } catch (Exception ignored) {}
        }
        sScreenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                    cancelUpdates(context);
                    showSleepingState(context);
                } else {
                    scheduleNextUpdate(context);
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        ctx.getApplicationContext().registerReceiver(sScreenReceiver, filter);
        scheduleNextUpdate(ctx);
    }


    @Override
    public void onDisabled(Context ctx) {
        super.onDisabled(ctx);
        cancelUpdates(ctx);
        
        // Clean up the receiver when the user deletes the widget
        if (sScreenReceiver != null) {
            ctx.getApplicationContext().unregisterReceiver(sScreenReceiver);
            sScreenReceiver = null;
        }
    }

    private void scheduleNextUpdate(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            am.setExact(AlarmManager.ELAPSED_REALTIME,
                    SystemClock.elapsedRealtime() + UPDATE_INTERVAL_MS, getTickPI(ctx));
        }
    }

    private static void cancelUpdates(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(getTickPI(ctx));
    }

    private static PendingIntent getTickPI(Context ctx) {
        Intent i = new Intent(ctx, SystemMonitorWidget.class).setAction(ACTION_TICK);
        return PendingIntent.getBroadcast(ctx, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void showSleepingState(Context ctx) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, SystemMonitorWidget.class));
        if (ids != null && ids.length > 0) {
            RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_system_monitor);
            v.setTextViewText(R.id.widget_mode, "Asleep (\uD83D\uDCA4)");
            v.setTextColor(R.id.widget_mode, 0xFF888888);
            
            v.setProgressBar(R.id.widget_cpu_bar, 100, 0, false);
            v.setTextViewText(R.id.widget_cpu_text, "--%");
            v.setProgressBar(R.id.widget_gpu_bar, 100, 0, false);
            v.setTextViewText(R.id.widget_gpu_text, "--MHz");
            v.setProgressBar(R.id.widget_mem_bar, 100, 0, false);
            v.setTextViewText(R.id.widget_mem_text, "--%");
            v.setProgressBar(R.id.widget_swap_bar, 100, 0, false);
            v.setTextViewText(R.id.widget_swap_text, "--%");
            v.setProgressBar(R.id.widget_bat_bar, 100, 0, false);
            v.setTextViewText(R.id.widget_bat_text, "--%");
            v.setProgressBar(R.id.widget_storage_bar, 100, 0, false);
            v.setTextViewText(R.id.widget_storage_text, "--/--G");
            
            v.setTextViewText(R.id.widget_bat_temp, "\uD83D\uDD0B --\u00b0C");
            v.setTextViewText(R.id.widget_cpu_temp, "\u2699\uFE0F --\u00b0C");
            v.setTextViewText(R.id.widget_gpu_temp, "\uD83C\uDFAE --\u00b0C");
            
            Intent openApp = new Intent(ctx, PowertoolsActivity.class);
            PendingIntent pi = PendingIntent.getActivity(ctx, 0, openApp,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            v.setOnClickPendingIntent(R.id.widget_root, pi);

            mgr.updateAppWidget(ids, v);
        }
    }

    /**
     * Pushes the heavy sysfs reading to a background thread while keeping the
     * BroadcastReceiver alive using goAsync().
     */
    private void processUpdateAsync(Context ctx, AppWidgetManager mgr, int[] ids) {
        final PendingResult pendingResult = goAsync();
        sBackgroundExecutor.execute(() -> {
            try {
                buildAndApplyViews(ctx, mgr, ids);
            } finally {
                pendingResult.finish();
            }
        });
    }

    private void buildAndApplyViews(Context ctx, AppWidgetManager mgr, int[] ids) {
        if (!isLauncherVisible(ctx)) return;
        int cpuPct = getCpuUsage();
        int[] gpu = getGpuInfo();
        int[] mem = getMemoryInfo(ctx);
        int[] swap = getSwapInfo();
        int bat = getBatteryLevel(ctx);
        String[] stor = getStorageInfo();
        String modeLabel = getModeLabel(ctx);
        int modeColor = getModeColor(modeLabel);
        int storPct = Integer.parseInt(stor[2]);

        float batTemp = readTempC(BATTERY_TEMP, 10f);
        float cpuTemp = readTempC(CPU_TEMP, 1000f);
        float gpuTemp = readTempC(GPU_TEMP, 1000f);

        // Map temps to 0-100 progress: CPU/GPU max scale = 100°C, Battery max scale = 55°C
        int cpuTempPct = Math.min(100, Math.max(0, (int) cpuTemp));
        int gpuTempPct = Math.min(100, Math.max(0, (int) gpuTemp));
        int batTempPct = Math.min(100, Math.max(0, (int) (batTemp * 100 / 55)));

        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_system_monitor);

        v.setTextViewText(R.id.widget_mode, modeLabel);
        v.setTextColor(R.id.widget_mode, modeColor);

        // CPU usage + TCPU temp bar
        v.setProgressBar(R.id.widget_cpu_bar, 100, cpuPct, false);
        v.setTextViewText(R.id.widget_cpu_text, cpuPct + "%");
        v.setProgressTintList(R.id.widget_cpu_bar, ColorStateList.valueOf(barTint(cpuPct, 0xFF6C63FF)));
        v.setProgressBar(R.id.widget_cpu_temp_bar, 100, cpuTempPct, false);
        v.setTextViewText(R.id.widget_cpu_temp, String.format("%.0f\u00b0C", cpuTemp));
        v.setProgressTintList(R.id.widget_cpu_temp_bar, ColorStateList.valueOf(barTint(cpuTempPct, 0xFF6C63FF)));

        // GPU usage + TGPU temp bar
        v.setProgressBar(R.id.widget_gpu_bar, 100, gpu[0], false);
        v.setTextViewText(R.id.widget_gpu_text, gpu[0] + "%  " + gpu[1] + "M");
        v.setProgressTintList(R.id.widget_gpu_bar, ColorStateList.valueOf(barTint(gpu[0], 0xFF43E97B)));
        v.setProgressBar(R.id.widget_gpu_temp_bar, 100, gpuTempPct, false);
        v.setTextViewText(R.id.widget_gpu_temp, String.format("%.0f\u00b0C", gpuTemp));
        v.setProgressTintList(R.id.widget_gpu_temp_bar, ColorStateList.valueOf(barTint(gpuTempPct, 0xFF43E97B)));

        // RAM + SWAP
        v.setProgressBar(R.id.widget_mem_bar, 100, mem[0], false);
        v.setTextViewText(R.id.widget_mem_text, mem[0] + "%");
        v.setProgressTintList(R.id.widget_mem_bar, ColorStateList.valueOf(barTint(mem[0], 0xFFFFA726)));
        v.setProgressBar(R.id.widget_swap_bar, 100, swap[0], false);
        v.setTextViewText(R.id.widget_swap_text, swap[0] + "%");
        v.setProgressTintList(R.id.widget_swap_bar, ColorStateList.valueOf(barTint(swap[0], 0xFFCE93D8)));

        // Battery + TBAT temp bar
        v.setProgressBar(R.id.widget_bat_bar, 100, bat, false);
        v.setTextViewText(R.id.widget_bat_text, bat + "%");
        v.setProgressTintList(R.id.widget_bat_bar, ColorStateList.valueOf(barTint(bat, 0xFF29B6F6)));
        v.setProgressBar(R.id.widget_bat_temp_bar, 100, batTempPct, false);
        v.setTextViewText(R.id.widget_bat_temp, String.format("%.0f\u00b0C", batTemp));
        v.setProgressTintList(R.id.widget_bat_temp_bar, ColorStateList.valueOf(barTint(batTempPct, 0xFF29B6F6)));

        // Storage
        v.setProgressBar(R.id.widget_storage_bar, 100, storPct, false);
        v.setTextViewText(R.id.widget_storage_text, storPct + "%");
        v.setProgressTintList(R.id.widget_storage_bar, ColorStateList.valueOf(barTint(storPct, 0xFF26C6DA)));

        Intent openApp = new Intent(ctx, PowertoolsActivity.class);
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.widget_root, pi);

        mgr.updateAppWidget(ids, v);
    }

    /** Returns red when pct >= 85, normal color otherwise. */
    private int barTint(int pct, int normalColor) {
        return pct >= 85 ? 0xFFEF5350 : normalColor;
    }

    // ---- Stat readers (Now safe to run, as they are on a background thread) ----

    @SuppressWarnings("deprecation")
    private boolean isLauncherVisible(Context ctx) {
        try {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return true;
            List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
            if (tasks == null || tasks.isEmpty()) return true;
            String foregroundPkg = tasks.get(0).topActivity.getPackageName();
            Intent homeIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            android.content.pm.ResolveInfo resolved = ctx.getPackageManager()
                    .resolveActivity(homeIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
            return resolved != null && resolved.activityInfo.packageName.equals(foregroundPkg);
        } catch (Exception e) {
            return true;
        }
    }

    private int getCpuUsage() {
        // try-with-resources ensures the file descriptor is closed even if parsing fails
        try (RandomAccessFile r = new RandomAccessFile("/proc/stat", "r")) {
            String line = r.readLine();
            if (line == null) return 0;

            String[] p = line.split("\\s+");
            long idle = Long.parseLong(p[4]) + Long.parseLong(p[5]); // idle + iowait
            long total = 0;
            
            for (int i = 1; i < p.length; i++) {
                total += Long.parseLong(p[i]);
            }

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
            // Read actual hardware busy percentage (0-100) instead of freq ratio
            String busyStr = readSysfs("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage");
            int pct = 0;
            if (busyStr != null && !busyStr.isEmpty()) {
                pct = Integer.parseInt(busyStr.trim().split("\\s+")[0]);
            }
            
            // Still read max_freq just to log it if needed, but return pct for the bar
            long max = Long.parseLong(readSysfs(GPU_MAX_FREQ_PATH));
            int maxMhz = (int) (max / 1000000); 
            
            return new int[]{Math.min(100, Math.max(0, pct)), maxMhz};
        } catch (Exception e) {
            return new int[]{0, 0};
        }
    }

    private int[] getMemoryInfo(Context ctx) {
        try {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return new int[]{0};
            
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            
            long total = mi.totalMem / (1024 * 1024);
            long used = total - (mi.availMem / (1024 * 1024));
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
        return parts.length >= 2 ? Long.parseLong(parts[1]) : 0;
    }

    private int getBatteryLevel(Context ctx) {
        try {
            BatteryManager bm = (BatteryManager) ctx.getSystemService(Context.BATTERY_SERVICE);
            return bm != null ? bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private String[] getStorageInfo() {
        try {
            StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
            long total = stat.getTotalBytes();
            long used = total - stat.getAvailableBytes();
            int pct = total > 0 ? (int) (used * 100 / total) : 0;
            String usedGB = String.format("%.1f", used / 1073741824.0);
            String totalGB = String.format("%.0f", total / 1073741824.0);
            return new String[]{usedGB, totalGB, String.valueOf(pct)};
        } catch (Exception e) {
            return new String[]{"?", "?", "0"};
        }
    }

    private String getModeLabel(Context ctx) {
        try {
            android.content.SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(ctx);
            if (prefs.getBoolean("auto_thermal_enable", false)) return "Auto";
            int mode = SystemProperties.getInt("sys.perf_mode_active", 1);
            if (mode >= 0 && mode < MODE_LABELS.length) return MODE_LABELS[mode];
        } catch (Exception ignored) {}
        return "Normal";
    }

    private int getModeColor(String label) {
        switch (label) {
            case "PowerSave":    return 0xFF29B6F6; // blue
            case "Performance":  return 0xFF43E97B; // green
            case "Auto":         return 0xFFFFB74D; // orange
            default:             return 0xFF6C63FF; // purple (Normal)
        }
    }

    private float readTempC(String path, float divisor) {
        try {
            return Integer.parseInt(readSysfs(path)) / divisor;
        } catch (Exception e) {
            return 0f;
        }
    }

    private String readSysfs(String path) {
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line = br.readLine();
            return line != null ? line.trim() : "0";
        } catch (Exception e) {
            return "0";
        }
    }
}