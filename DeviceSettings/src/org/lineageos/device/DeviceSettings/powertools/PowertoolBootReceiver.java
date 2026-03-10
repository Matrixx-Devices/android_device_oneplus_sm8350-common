/*
 * Copyright (C) 2025 kenrow214
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.device.DeviceSettings.powertools;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemProperties;

import androidx.preference.PreferenceManager;

public class PowertoolBootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()))
            return;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean autoThermal = prefs.getBoolean("auto_thermal_enable", false);

        if (autoThermal) {
            // Android 12+ requires Foreground Services triggered by boot to start immediately.
            // We moved the 60-second delay into the ThermalMonitorService's background thread
            // so the system doesn't kill this BroadcastReceiver.
            Intent svcIntent = new Intent(context, ThermalMonitorService.class);
            context.startForegroundService(svcIntent);
        } else {
            // The init.rc handles applying the boot settings via persist.sys.perf_mode_saved.
            // We just need to make sure the UI preferences are synced to whatever mode booted.
            int savedMode = SystemProperties.getInt("persist.sys.perf_mode_saved", PowerProfileUtil.MODE_BALANCE);
            PowerProfileUtil profileUtil = new PowerProfileUtil(context);
            profileUtil.syncUiToMode(savedMode);
        }
    }
}