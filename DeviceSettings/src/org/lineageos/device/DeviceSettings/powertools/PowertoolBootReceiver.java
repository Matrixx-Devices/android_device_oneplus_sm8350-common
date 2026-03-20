/*
 * Copyright (C) 2025 kenrow214
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.device.DeviceSettings.powertools;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PowertoolBootReceiver extends BroadcastReceiver {

    private static final String TAG = "PowertoolBootReceiver";

    private static final String PREF_AUTO_THERMAL   = "auto_thermal_enable";
    private static final String PREF_POWER_PROFILE  = "power_profile_mode";
    private static final String PREF_CPU_ENABLE     = "cpu_enable";
    private static final String PREF_GPU_ENABLE     = "gpu_enable";
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
                // Reset all manual override toggles — always Normal on boot
                prefs.edit()
                     .putString(PREF_POWER_PROFILE, String.valueOf(PowerProfileUtil.MODE_BALANCE))
                     .putBoolean(PREF_CPU_ENABLE, false)
                     .putBoolean(PREF_GPU_ENABLE, false)
                     .putBoolean(PREF_STORAGE_ENABLE, false)
                     .commit();

                // Apply Normal mode — this calls BlurUtils.setBlurDisabled(false),
                // which is now a no-op unless a powersave backup exists in prefs.
                // Settings.Global (disable_window_blurs) persists across reboots
                // on its own, so the user's blur preference is already intact.
                PowerProfileUtil profileUtil = new PowerProfileUtil(context);
                profileUtil.setMode(PowerProfileUtil.MODE_BALANCE);

                Log.i(TAG, "Boot: Normal mode applied");
            } finally {
                pendingResult.finish();
            }
        });
    }
}