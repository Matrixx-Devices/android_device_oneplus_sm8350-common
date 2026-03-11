/*
 * Copyright (C) 2025 kenrow214
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.device.DeviceSettings.powertools;

import android.os.SystemProperties;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class StorageUtils {
    
    private static final String TAG = "StorageUtils";
    private static final String SAFE_DUMMY_SCHED = "mq-deadline";
    private static final String PROP_SCHEDULER = "persist.sys.parts.storage.scheduler";
    private static final String PROP_CLKSCALE = "persist.sys.parts.storage.clkscale";

    // Use a single background thread queue to process property writes safely and sequentially
    private static final ExecutorService sExecutor = Executors.newSingleThreadExecutor();

    // Prevent instantiation of utility class
    private StorageUtils() {}

    public static void setIoScheduler(String scheduler) {
        if (scheduler == null || scheduler.isEmpty()) return;

        sExecutor.execute(() -> {
            try {
                String currentSched = SystemProperties.get(PROP_SCHEDULER, "");
                
                // Force an edge transition for init.rc if the target is already active
                if (scheduler.equals(currentSched)) {
                    SystemProperties.set(PROP_SCHEDULER, SAFE_DUMMY_SCHED);
                    // MUST sleep briefly, otherwise init property service coalesces the writes
                    // and misses the trigger entirely.
                    Thread.sleep(50); 
                }
                
                SystemProperties.set(PROP_SCHEDULER, scheduler);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Restore interrupted state
                Log.w(TAG, "Interrupted while bouncing IO Scheduler", e);
            } catch (Exception e) {
                Log.e(TAG, "Failed to set IO Scheduler", e);
            }
        });
    }

    public static void setUfsClkScale(boolean enable) {
        sExecutor.execute(() -> {
            try {
                // 1 = Enabled (Powersave), 0 = Disabled (Performance)
                SystemProperties.set(PROP_CLKSCALE, enable ? "1" : "0");
            } catch (Exception e) {
                Log.e(TAG, "Failed to set UFS Clock Scaling", e);
            }
        });
    }
}