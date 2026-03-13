/*
 * Copyright (C) 2025 kenrow214
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.device.DeviceSettings.powertools;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PowertoolBootReceiver extends BroadcastReceiver {

    private static final String PREF_AUTO_THERMAL = "auto_thermal_enable";
    private static final String PREF_POWER_PROFILE = "power_profile_mode";
    private static final String PREF_CPU_ENABLE = "cpu_enable";
    private static final String PREF_GPU_ENABLE = "gpu_enable";
    private static final String PREF_STORAGE_ENABLE = "storage_enable";

    private static final ExecutorService sExecutor = Executors.newSingleThreadExecutor();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean autoThermal = prefs.getBoolean(PREF_AUTO_THERMAL, false);

        if (autoThermal) {
            Intent svcIntent = new Intent(context, ThermalMonitorService.class);
            context.startForegroundService(svcIntent);
        }

        final PendingResult pendingResult = goAsync();
        sExecutor.execute(() -> {
            try {
                prefs.edit()
                     .putString(PREF_POWER_PROFILE, String.valueOf(PowerProfileUtil.MODE_BALANCE))
                     .putBoolean(PREF_CPU_ENABLE, false)
                     .putBoolean(PREF_GPU_ENABLE, false)
                     .putBoolean(PREF_STORAGE_ENABLE, false)
                     .commit(); // Use commit() in background threads for synchronous safety

                PowerProfileUtil profileUtil = new PowerProfileUtil(context);
                profileUtil.setMode(PowerProfileUtil.MODE_BALANCE);
                
            } finally {
                pendingResult.finish();
            }
        });
    }
}