/*
 * Copyright (C) 2025 kenrow214
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.device.DeviceSettings.powertools;

import android.os.SystemProperties;
import android.util.Log;

public final class StorageUtils {
    
    private static final String TAG = "StorageUtils";
    private static final String SAFE_DUMMY_SCHED = "mq-deadline";

    public static void setIoScheduler(String scheduler) {
        new Thread(() -> {
            try {
                // Force an init.rc trigger
                if (scheduler.equals(SystemProperties.get("persist.sys.parts.storage.scheduler"))) {
                    SystemProperties.set("persist.sys.parts.storage.scheduler", SAFE_DUMMY_SCHED);
                }
                SystemProperties.set("persist.sys.parts.storage.scheduler", scheduler);
            } catch (Exception e) {
                Log.e(TAG, "Failed to set IO Scheduler", e);
            }
        }).start();
    }

    public static void setUfsClkScale(boolean enable) {
        new Thread(() -> {
            try {
                // 1 = Enabled (Powersave), 0 = Disabled (Performance)
                SystemProperties.set("persist.sys.parts.storage.clkscale", enable ? "1" : "0");
            } catch (Exception e) {
                Log.e(TAG, "Failed to set UFS Clock Scaling", e);
            }
        }).start();
    }
}