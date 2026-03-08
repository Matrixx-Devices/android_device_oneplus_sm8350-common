/*
 * Copyright (C) 2025 kenrow214
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.device.DeviceSettings.powertools;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.preference.PreferenceManager;

public class PowertoolBootReceiver extends BroadcastReceiver {

    // Delay before applying user settings to avoid conflicts with kernel init scripts
    private static final int BOOT_DELAY_MS = 60000; // 60 seconds

    private static final String GPU_DEFAULT_MIN = "315000000";
    private static final String GPU_DEFAULT_MAX = "840000000";
    private static final String CPU_LITTLE_DEFAULT_MIN = "691200"; 
    private static final String CPU_LITTLE_DEFAULT_MAX = "1804800";
    private static final String CPU_LITTLE_DEFAULT_GOV = "schedutil";
    private static final String CPU_BIG_DEFAULT_MIN = "710400";
    private static final String CPU_BIG_DEFAULT_MAX = "2419200";
    private static final String CPU_BIG_DEFAULT_GOV = "schedutil";
    private static final String CPU_PRIME_DEFAULT_MIN = "844800";
    private static final String CPU_PRIME_DEFAULT_MAX = "2841600";
    private static final String CPU_PRIME_DEFAULT_GOV = "schedutil";

    // Power Profile Governor Keys
    private static final String KEY_CPU_LITTLE_GOV_POWERSAVE = "cpu_little_gov_powersave";
    private static final String KEY_CPU_BIG_GOV_POWERSAVE = "cpu_big_gov_powersave";
    private static final String KEY_CPU_PRIME_GOV_POWERSAVE = "cpu_prime_gov_powersave";
    private static final String KEY_GPU_GOV_POWERSAVE = "gpu_gov_powersave";
    
    private static final String KEY_CPU_LITTLE_GOV_BALANCE = "cpu_little_gov_balance";
    private static final String KEY_CPU_BIG_GOV_BALANCE = "cpu_big_gov_balance";
    private static final String KEY_CPU_PRIME_GOV_BALANCE = "cpu_prime_gov_balance";
    private static final String KEY_GPU_GOV_BALANCE = "gpu_gov_balance";
    
    private static final String KEY_CPU_LITTLE_GOV_PERFORMANCE = "cpu_little_gov_performance";
    private static final String KEY_CPU_BIG_GOV_PERFORMANCE = "cpu_big_gov_performance";
    private static final String KEY_CPU_PRIME_GOV_PERFORMANCE = "cpu_prime_gov_performance";
    private static final String KEY_GPU_GOV_PERFORMANCE = "gpu_gov_performance";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()))
            return;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        boolean autoThermal = prefs.getBoolean("auto_thermal_enable", false);
        if (autoThermal) {
            // Auto thermal starts after delay so kernel init scripts settle first
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                Intent svcIntent = new Intent(context, ThermalMonitorService.class);
                context.startForegroundService(svcIntent);
            }, BOOT_DELAY_MS);
            return;
        }

        // Delay applying user freq/profile settings until kernel init scripts have settled
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            new Thread(() -> applyBootSettings(context, prefs)).start();
        }, BOOT_DELAY_MS);
    }

    private void applyBootSettings(Context context, SharedPreferences prefs) {
        boolean resetOnBoot = prefs.getBoolean("reset_on_boot", false);
        if (resetOnBoot) {
            GPUUtils.setGPUMinFrequency(GPU_DEFAULT_MIN);
            GPUUtils.setGPUMaxFrequency(GPU_DEFAULT_MAX);
            CPUUtils.setCPULittleFreq(CPU_LITTLE_DEFAULT_MIN, CPU_LITTLE_DEFAULT_MAX, CPU_LITTLE_DEFAULT_GOV);
            CPUUtils.setCPUBigFreq(CPU_BIG_DEFAULT_MIN, CPU_BIG_DEFAULT_MAX, CPU_BIG_DEFAULT_GOV);
            CPUUtils.setCPUPrimeFreq(CPU_PRIME_DEFAULT_MIN, CPU_PRIME_DEFAULT_MAX, CPU_PRIME_DEFAULT_GOV);
            
            // Storage Reset
            SysfsUtils.writeValue("/sys/block/sda/queue/scheduler", "bfq");
            SysfsUtils.writeValue("/sys/block/sdb/queue/scheduler", "bfq");
            SysfsUtils.writeValue("/sys/block/sdc/queue/scheduler", "bfq");
            SysfsUtils.writeValue("/sys/block/sdd/queue/scheduler", "bfq");
            SysfsUtils.writeValue("/sys/block/sde/queue/scheduler", "bfq");
            SysfsUtils.writeValue("/sys/block/sdf/queue/scheduler", "bfq");
            return;
        }

        // 1. Always enforce the correct system power profile mode on boot
        PowerProfileUtil profileUtil = new PowerProfileUtil(context);
        int currentMode = profileUtil.getCurrentMode();
        profileUtil.setMode(currentMode);

        // 2. Handle GPU Settings (Manual or Preset)
        boolean gpuEnabled = prefs.getBoolean("gpu_enable", false);
        if (gpuEnabled) {
            GPUUtils.setGPUMinFrequency(prefs.getString("gpu_min_frequency", GPU_DEFAULT_MIN));
            GPUUtils.setGPUMaxFrequency(prefs.getString("gpu_max_frequency", GPU_DEFAULT_MAX));
            GPUUtils.setGPUGovernor(prefs.getString("gpu_governor", "msm-adreno-tz"));
        } else {
            String gpuGov = getGpuGovForMode(prefs, currentMode);
            GPUUtils.setGPUGovernor(gpuGov);
        }

        // 3. Handle CPU Settings (Manual or Preset)
        boolean cpuEnabled = prefs.getBoolean("cpu_enable", false);
        if (cpuEnabled) {
            CPUUtils.setCPULittleFreq(
                    prefs.getString("cpu_little_min_frequency", CPU_LITTLE_DEFAULT_MIN),
                    prefs.getString("cpu_little_max_frequency", CPU_LITTLE_DEFAULT_MAX),
                    prefs.getString("cpu_little_governor", CPU_LITTLE_DEFAULT_GOV));
            CPUUtils.setCPUBigFreq(
                    prefs.getString("cpu_big_min_frequency", CPU_BIG_DEFAULT_MIN),
                    prefs.getString("cpu_big_max_frequency", CPU_BIG_DEFAULT_MAX),
                    prefs.getString("cpu_big_governor", CPU_BIG_DEFAULT_GOV));
            CPUUtils.setCPUPrimeFreq(
                    prefs.getString("cpu_prime_min_frequency", CPU_PRIME_DEFAULT_MIN),
                    prefs.getString("cpu_prime_max_frequency", CPU_PRIME_DEFAULT_MAX),
                    prefs.getString("cpu_prime_governor", CPU_PRIME_DEFAULT_GOV));
        } else {
            applyCpuGovernorsForMode(prefs, currentMode);
        }
    }

    private String getGpuGovForMode(SharedPreferences prefs, int mode) {
        switch (mode) {
            case PowerProfileUtil.MODE_BATTERY_SAVER:
                return prefs.getString(KEY_GPU_GOV_POWERSAVE, "powersave");
            case PowerProfileUtil.MODE_PERFORMANCE:
                return prefs.getString(KEY_GPU_GOV_PERFORMANCE, "msm-adreno-tz");
            case PowerProfileUtil.MODE_BALANCE:
            default:
                return prefs.getString(KEY_GPU_GOV_BALANCE, "msm-adreno-tz");
        }
    }

    private void applyCpuGovernorsForMode(SharedPreferences prefs, int mode) {
        String littleGov, bigGov, primeGov;

        switch (mode) {
            case PowerProfileUtil.MODE_BATTERY_SAVER:
                littleGov = prefs.getString(KEY_CPU_LITTLE_GOV_POWERSAVE, "conservative");
                bigGov = prefs.getString(KEY_CPU_BIG_GOV_POWERSAVE, "conservative");
                primeGov = prefs.getString(KEY_CPU_PRIME_GOV_POWERSAVE, "conservative");
                break;
            case PowerProfileUtil.MODE_PERFORMANCE:
                littleGov = prefs.getString(KEY_CPU_LITTLE_GOV_PERFORMANCE, "performance");
                bigGov = prefs.getString(KEY_CPU_BIG_GOV_PERFORMANCE, "performance");
                primeGov = prefs.getString(KEY_CPU_PRIME_GOV_PERFORMANCE, "performance");
                break;
            case PowerProfileUtil.MODE_BALANCE:
            default:
                littleGov = prefs.getString(KEY_CPU_LITTLE_GOV_BALANCE, "schedutil");
                bigGov = prefs.getString(KEY_CPU_BIG_GOV_BALANCE, "schedutil");
                primeGov = prefs.getString(KEY_CPU_PRIME_GOV_BALANCE, "schedutil");
                break;
        }

        // We only push the governors here. The frequencies are handled by init.rc when 
        // profileUtil.setMode() is called above.
        CPUUtils.setCPULittleFreq(CPU_LITTLE_DEFAULT_MIN, CPU_LITTLE_DEFAULT_MAX, littleGov);
        CPUUtils.setCPUBigFreq(CPU_BIG_DEFAULT_MIN, CPU_BIG_DEFAULT_MAX, bigGov);
        CPUUtils.setCPUPrimeFreq(CPU_PRIME_DEFAULT_MIN, CPU_PRIME_DEFAULT_MAX, primeGov);
    }
}