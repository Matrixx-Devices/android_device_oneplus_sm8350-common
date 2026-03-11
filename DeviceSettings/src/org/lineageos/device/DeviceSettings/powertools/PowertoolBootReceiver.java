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

    // Extract hardcoded preference keys to prevent typos
    private static final String PREF_AUTO_THERMAL = "auto_thermal_enable";
    private static final String PREF_POWER_PROFILE = "power_profile_mode";
    private static final String PREF_CPU_ENABLE = "cpu_enable";
    private static final String PREF_GPU_ENABLE = "gpu_enable";
    private static final String PREF_STORAGE_ENABLE = "storage_enable";

    // Reusable single-thread executor for offloading boot I/O
    private static final ExecutorService sExecutor = Executors.newSingleThreadExecutor();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean autoThermal = prefs.getBoolean(PREF_AUTO_THERMAL, false);

        // CRITICAL: Android 12+ strict requirements.
        // We MUST start the Foreground Service synchronously on the main thread here
        // to consume the temporary BOOT_COMPLETED background-start whitelist token.
        // Moving this to the background thread will cause a SecurityException.
        if (autoThermal) {
            Intent svcIntent = new Intent(context, ThermalMonitorService.class);
            context.startForegroundService(svcIntent);
        }

        // Offload all SharedPreferences edits and sysfs hardware writes to prevent Boot ANRs
        final PendingResult pendingResult = goAsync();
        sExecutor.execute(() -> {
            try {
                // Reset power profile mode to Normal (Balance) and disable manual overrides
                prefs.edit()
                     .putString(PREF_POWER_PROFILE, String.valueOf(PowerProfileUtil.MODE_BALANCE))
                     .putBoolean(PREF_CPU_ENABLE, false)
                     .putBoolean(PREF_GPU_ENABLE, false)
                     .putBoolean(PREF_STORAGE_ENABLE, false)
                     .commit(); // Use commit() in background threads for synchronous safety

                // Push default values to the kernel
                PowerProfileUtil profileUtil = new PowerProfileUtil(context);
                profileUtil.setMode(PowerProfileUtil.MODE_BALANCE);
                
            } finally {
                // Tell the system the receiver has finished its background work
                pendingResult.finish();
            }
        });
    }
}