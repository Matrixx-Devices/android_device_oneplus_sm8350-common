/*
 * Copyright (C) 2025 kenrow214
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.device.DeviceSettings.powertools;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.KeyguardManager;
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

    private static final String BATTERY_TEMP    = "/sys/class/power_supply/battery/temp";
    private static final String CPU_TEMP        = "/sys/class/thermal/thermal_zone39/temp";
    private static final String GPU_TEMP        = "/sys/class/thermal/thermal_zone54/temp";
    private static final String GPU_CUR_FREQ    = "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq";
    private static final String GPU_MAX_FREQ_PATH = "/sys/class/kgsl/kgsl-3d0/devfreq/max_freq";

    private static final String[] MODE_LABELS = {"PowerSave", "Normal", "Performance", "", "", "Auto"};

    private static long sLastCpuTotal = 0;
    private static long sLastCpuIdle  = 0;
    private static final ExecutorService sBackgroundExecutor = Executors.newSingleThreadExecutor();


    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        ensureReceiverRegistered(ctx);
        processUpdateAsync(ctx, mgr, ids);
    }

    @Override
    public void onEnabled(Context ctx) {
        super.onEnabled(ctx);
        ensureReceiverRegistered(ctx);
        scheduleNextUpdate(ctx);
    }

    @Override
    public void onDisabled(Context ctx) {
        super.onDisabled(ctx);
        cancelUpdates(ctx);
        if (sScreenReceiver != null) {
            try { ctx.getApplicationContext().unregisterReceiver(sScreenReceiver); } catch (Exception ignored) {}
            sScreenReceiver = null;
        }
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent);
        String action = intent.getAction();
        if (action == null) return;

        switch (action) {
            case Intent.ACTION_BOOT_COMPLETED:
            case "android.intent.action.QUICKBOOT_POWERON": // HTC / some OEMs
                ensureReceiverRegistered(ctx);
                scheduleNextUpdate(ctx);
                break;

            case ACTION_TICK:
                AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
                int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, SystemMonitorWidget.class));
                if (ids != null && ids.length > 0) {
                    processUpdateAsync(ctx, mgr, ids);
                }
                break;
        }
    }


    private static BroadcastReceiver sScreenReceiver;

    private static void ensureReceiverRegistered(Context ctx) {
        if (sScreenReceiver != null) return;

        sScreenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action == null) return;

                switch (action) {
                    case Intent.ACTION_SCREEN_OFF:
                        cancelUpdates(context);
                        showSleepingState(context);
                        break;

                    case Intent.ACTION_SCREEN_ON:
                        if (isKeyguardOrLauncherVisible(context)) {
                            showWakingState(context);
                            new android.os.Handler(android.os.Looper.getMainLooper())
                                    .postDelayed(() -> scheduleNextUpdate(context), 1500);
                        }
                        break;

                    case Intent.ACTION_USER_PRESENT:
                        showWakingState(context);
                        new android.os.Handler(android.os.Looper.getMainLooper())
                                .postDelayed(() -> scheduleNextUpdate(context), 1500);
                        break;

                    case Intent.ACTION_CLOSE_SYSTEM_DIALOGS:
                        String reason = intent.getStringExtra("reason");
                        if ("homekey".equals(reason) || "recentapps".equals(reason)) {
                            showWakingState(context);
                            new android.os.Handler(android.os.Looper.getMainLooper())
                                    .postDelayed(() -> scheduleNextUpdate(context), 1500);
                        }
                        break;
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);      // ← added for lockscreen
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        ctx.getApplicationContext().registerReceiver(sScreenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }


    private static void scheduleNextUpdate(Context ctx) {
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
        if (ids == null || ids.length == 0) return;

        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_system_monitor);
        v.setTextViewText(R.id.widget_mode, "Asleep (\uD83D\uDCA4)");
        v.setTextColor(R.id.widget_mode, 0xFF888888);

        int[] bars = { R.id.widget_cpu_bar, R.id.widget_gpu_bar, R.id.widget_mem_bar,
                R.id.widget_swap_bar, R.id.widget_bat_bar, R.id.widget_storage_bar };
        for (int bar : bars) v.setProgressBar(bar, 100, 0, false);

        v.setTextViewText(R.id.widget_cpu_text,     "--%");
        v.setTextViewText(R.id.widget_gpu_text,     "--%");
        v.setTextViewText(R.id.widget_mem_text,     "--%");
        v.setTextViewText(R.id.widget_swap_text,    "--%");
        v.setTextViewText(R.id.widget_bat_text,     "--%");
        v.setTextViewText(R.id.widget_storage_text, "--%");
        v.setTextViewText(R.id.widget_cpu_temp,     "\u2699\uFE0F C: --\u00b0C");
        v.setTextViewText(R.id.widget_gpu_temp,     "\uD83C\uDFAE G: --\u00b0C");
        v.setTextViewText(R.id.widget_bat_temp,     "\uD83D\uDD0B B: --\u00b0C");

        applyClickIntent(ctx, v);
        mgr.updateAppWidget(ids, v);
    }

    private static void showWakingState(Context ctx) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, SystemMonitorWidget.class));
        if (ids == null || ids.length == 0) return;

        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_system_monitor);
        v.setTextViewText(R.id.widget_mode, "Waking...");
        v.setTextColor(R.id.widget_mode, 0xFFFFCC00);
        mgr.updateAppWidget(ids, v);
    }

    private static void applyClickIntent(Context ctx, RemoteViews v) {
        Intent openApp = new Intent(ctx, PowertoolsActivity.class);
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.widget_root, pi);
    }


    private void processUpdateAsync(Context ctx, AppWidgetManager mgr, int[] ids) {
        final PendingResult pendingResult = goAsync();
        sBackgroundExecutor.execute(() -> {
            try {
                if (isKeyguardOrLauncherVisible(ctx)) {
                    buildAndApplyViews(ctx, mgr, ids);
                    scheduleNextUpdate(ctx);
                } else {
                    cancelUpdates(ctx);
                    showSleepingState(ctx);
                }
            } finally {
                pendingResult.finish();
            }
        });
    }


    /**
     * Returns true when the widget should be actively updating:
     *  - The launcher is in the foreground (home screen), OR
     *  - The keyguard is showing (A16 QPR2 lockscreen widgets are visible)
     */
    private static boolean isKeyguardOrLauncherVisible(Context ctx) {
        try {
            KeyguardManager km = (KeyguardManager) ctx.getSystemService(Context.KEYGUARD_SERVICE);
            if (km != null && km.isKeyguardLocked()) {
                return true;
            }
        } catch (Exception ignored) {}

        return isLauncherForeground(ctx);
    }

    @SuppressWarnings("deprecation")
    private static boolean isLauncherForeground(Context ctx) {
        try {
            android.app.usage.UsageStatsManager usm =
                    (android.app.usage.UsageStatsManager) ctx.getSystemService(Context.USAGE_STATS_SERVICE);

            String foregroundPkg = null;

            if (usm != null) {
                long time = System.currentTimeMillis();
                android.app.usage.UsageEvents events = usm.queryEvents(time - 3000, time + 1000);
                if (events != null) {
                    android.app.usage.UsageEvents.Event event = new android.app.usage.UsageEvents.Event();
                    while (events.hasNextEvent()) {
                        events.getNextEvent(event);
                        if (event.getEventType() == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED) {
                            foregroundPkg = event.getPackageName();
                        } else if (event.getEventType() == android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED) {
                            if (event.getPackageName().equals(foregroundPkg)) {
                                foregroundPkg = null;
                            }
                        }
                    }
                }
            }

            if (foregroundPkg == null) {
                ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
                if (am != null) {
                    List<ActivityManager.RunningAppProcessInfo> apps = am.getRunningAppProcesses();
                    if (apps != null) {
                        for (ActivityManager.RunningAppProcessInfo app : apps) {
                            if (app.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                                foregroundPkg = app.processName;
                                break;
                            }
                        }
                    }
                }
            }

            if (foregroundPkg == null) return true; // unknown → assume visible

            Intent homeIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            android.content.pm.ResolveInfo resolved = ctx.getPackageManager()
                    .resolveActivity(homeIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);

            return resolved != null && resolved.activityInfo.packageName.equals(foregroundPkg);
        } catch (Exception e) {
            return true;
        }
    }


    private void buildAndApplyViews(Context ctx, AppWidgetManager mgr, int[] ids) {
        int cpuPct  = getCpuUsage();
        int[] gpu   = getGpuInfo();
        int[] mem   = getMemoryInfo(ctx);
        int[] swap  = getSwapInfo();
        int bat     = getBatteryLevel(ctx);
        String[] stor = getStorageInfo();
        String modeLabel = getModeLabel(ctx);
        int modeColor    = getModeColor(modeLabel);
        int storPct = Integer.parseInt(stor[2]);

        float batTemp = readTempC(BATTERY_TEMP, 10f);
        float cpuTemp = readTempC(CPU_TEMP,     1000f);
        float gpuTemp = readTempC(GPU_TEMP,     1000f);

        int cpuTempPct = Math.min(100, Math.max(0, (int) cpuTemp));
        int gpuTempPct = Math.min(100, Math.max(0, (int) gpuTemp));
        int batTempPct = Math.min(100, Math.max(0, (int) (batTemp * 100 / 55)));

        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_system_monitor);

        v.setTextViewText(R.id.widget_mode, modeLabel);
        v.setTextColor(R.id.widget_mode, modeColor);

        v.setProgressBar(R.id.widget_cpu_bar, 100, cpuPct, false);
        v.setTextViewText(R.id.widget_cpu_text, cpuPct + "%");
        v.setProgressTintList(R.id.widget_cpu_bar, ColorStateList.valueOf(barTint(cpuPct, 0xFF6C63FF)));
        v.setTextViewText(R.id.widget_cpu_temp, String.format("\u2699\uFE0F C: %.0f\u00b0C", cpuTemp));

        v.setProgressBar(R.id.widget_gpu_bar, 100, gpu[0], false);
        v.setTextViewText(R.id.widget_gpu_text, gpu[0] + "%");
        v.setProgressTintList(R.id.widget_gpu_bar, ColorStateList.valueOf(barTint(gpu[0], 0xFF43E97B)));
        v.setTextViewText(R.id.widget_gpu_temp, String.format("\uD83C\uDFAE G: %.0f\u00b0C", gpuTemp));

        v.setProgressBar(R.id.widget_mem_bar, 100, mem[0], false);
        v.setTextViewText(R.id.widget_mem_text, mem[0] + "%");
        v.setProgressTintList(R.id.widget_mem_bar, ColorStateList.valueOf(barTint(mem[0], 0xFFFFA726)));
        v.setProgressBar(R.id.widget_swap_bar, 100, swap[0], false);
        v.setTextViewText(R.id.widget_swap_text, swap[0] + "%");
        v.setProgressTintList(R.id.widget_swap_bar, ColorStateList.valueOf(barTint(swap[0], 0xFFCE93D8)));

        v.setProgressBar(R.id.widget_bat_bar, 100, bat, false);
        v.setTextViewText(R.id.widget_bat_text, bat + "%");
        v.setProgressTintList(R.id.widget_bat_bar, ColorStateList.valueOf(barTint(bat, 0xFF29B6F6)));
        v.setTextViewText(R.id.widget_bat_temp, String.format("\uD83D\uDD0B B: %.0f\u00b0C", batTemp));

        v.setProgressBar(R.id.widget_storage_bar, 100, storPct, false);
        v.setTextViewText(R.id.widget_storage_text, storPct + "%");
        v.setProgressTintList(R.id.widget_storage_bar, ColorStateList.valueOf(barTint(storPct, 0xFF26C6DA)));

        applyClickIntent(ctx, v);
        mgr.updateAppWidget(ids, v);
    }

    private int barTint(int pct, int normalColor) {
        return pct >= 85 ? 0xFFEF5350 : normalColor;
    }


    private int getCpuUsage() {
        try (RandomAccessFile r = new RandomAccessFile("/proc/stat", "r")) {
            String line = r.readLine();
            if (line == null) return 0;
            String[] p = line.split("\\s+");
            long idle  = Long.parseLong(p[4]) + Long.parseLong(p[5]);
            long total = 0;
            for (int i = 1; i < p.length; i++) total += Long.parseLong(p[i]);
            int pct = 0;
            if (sLastCpuTotal > 0) {
                long dTotal = total - sLastCpuTotal;
                long dIdle  = idle  - sLastCpuIdle;
                pct = dTotal > 0 ? (int) ((dTotal - dIdle) * 100 / dTotal) : 0;
            }
            sLastCpuTotal = total;
            sLastCpuIdle  = idle;
            return Math.max(0, Math.min(100, pct));
        } catch (Exception e) { return 0; }
    }

    private int[] getGpuInfo() {
        try {
            String busyStr = readSysfs("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage");
            int pct = 0;
            if (busyStr != null && !busyStr.isEmpty())
                pct = Integer.parseInt(busyStr.trim().split("\\s+")[0]);
            long max    = Long.parseLong(readSysfs(GPU_MAX_FREQ_PATH));
            int maxMhz  = (int) (max / 1000000);
            return new int[]{Math.min(100, Math.max(0, pct)), maxMhz};
        } catch (Exception e) { return new int[]{0, 0}; }
    }

    private int[] getMemoryInfo(Context ctx) {
        try {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return new int[]{0};
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            long total = mi.totalMem  / (1024 * 1024);
            long used  = total - (mi.availMem / (1024 * 1024));
            return new int[]{(int) (used * 100 / total)};
        } catch (Exception e) { return new int[]{0}; }
    }

    private int[] getSwapInfo() {
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/meminfo"))) {
            long swapTotal = 0, swapFree = 0;
            String line;
            while ((line = br.readLine()) != null) {
                if      (line.startsWith("SwapTotal:")) swapTotal = parseMemLine(line);
                else if (line.startsWith("SwapFree:"))  swapFree  = parseMemLine(line);
            }
            if (swapTotal == 0) return new int[]{0};
            return new int[]{(int) ((swapTotal - swapFree) * 100 / swapTotal)};
        } catch (Exception e) { return new int[]{0}; }
    }

    private long parseMemLine(String line) {
        String[] parts = line.split("\\s+");
        return parts.length >= 2 ? Long.parseLong(parts[1]) : 0;
    }

    private int getBatteryLevel(Context ctx) {
        try {
            BatteryManager bm = (BatteryManager) ctx.getSystemService(Context.BATTERY_SERVICE);
            return bm != null ? bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) : 0;
        } catch (Exception e) { return 0; }
    }

    private String[] getStorageInfo() {
        try {
            StatFs stat  = new StatFs(Environment.getExternalStorageDirectory().getPath());
            long total   = stat.getTotalBytes();
            long used    = total - stat.getAvailableBytes();
            int pct      = total > 0 ? (int) (used * 100 / total) : 0;
            String usedGB  = String.format("%.1f", used  / 1073741824.0);
            String totalGB = String.format("%.0f", total / 1073741824.0);
            return new String[]{usedGB, totalGB, String.valueOf(pct)};
        } catch (Exception e) { return new String[]{"?", "?", "0"}; }
    }

    private String getModeLabel(Context ctx) {
        try {
            android.content.SharedPreferences prefs =
                    androidx.preference.PreferenceManager.getDefaultSharedPreferences(ctx);
            if (prefs.getBoolean("auto_thermal_enable", false)) return "Auto";
            int mode = SystemProperties.getInt("sys.perf_mode_active", 1);
            if (mode >= 0 && mode < MODE_LABELS.length) return MODE_LABELS[mode];
        } catch (Exception ignored) {}
        return "Normal";
    }

    private int getModeColor(String label) {
        switch (label) {
            case "PowerSave":   return 0xFF29B6F6;
            case "Performance": return 0xFF43E97B;
            case "Auto":        return 0xFFFFB74D;
            default:            return 0xFF6C63FF;
        }
    }

    private float readTempC(String path, float divisor) {
        try { return Integer.parseInt(readSysfs(path)) / divisor; }
        catch (Exception e) { return 0f; }
    }

    private String readSysfs(String path) {
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line = br.readLine();
            return line != null ? line.trim() : "0";
        } catch (Exception e) { return "0"; }
    }
}