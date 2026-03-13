/*
 * Copyright (C) 2025 kenrow214
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.device.DeviceSettings.powertools;

import android.os.SystemProperties;
import android.util.Log;

public final class GPUUtils {
    
    private static final String TAG = "GPUUtils";
    private static final String SAFE_DUMMY_GOV = "msm-adreno-tz";

    public static void setGPUMinFrequency(String frequencyHz) {
        new Thread(() -> {
            try {
                SystemProperties.set("persist.sys.parts.gpu.min_frequency", frequencyHz);
                long freqLong = Long.parseLong(frequencyHz);
                SystemProperties.set("persist.sys.parts.gpu.min_clock", String.valueOf(freqLong / 1000000));
            } catch (Exception e) {
                Log.e(TAG, "Failed to set GPU Min Freq", e);
            }
        }).start();
    }

    public static void setGPUMaxFrequency(String frequencyHz) {
        new Thread(() -> {
            try {
                SystemProperties.set("persist.sys.parts.gpu.max_frequency", frequencyHz);
                long freqLong = Long.parseLong(frequencyHz);
                SystemProperties.set("persist.sys.parts.gpu.max_clock", String.valueOf(freqLong / 1000000));
            } catch (Exception e) {
                Log.e(TAG, "Failed to set GPU Max Freq", e);
            }
        }).start();
    }

    public static void setGPUGovernor(String governor) {
        new Thread(() -> {
            try {
                if (governor.equals(SystemProperties.get("persist.sys.parts.gpu.governor"))) {
                    SystemProperties.set("persist.sys.parts.gpu.governor", SAFE_DUMMY_GOV);
                }
                SystemProperties.set("persist.sys.parts.gpu.governor", governor);
            } catch (Exception e) {
                Log.e(TAG, "Failed to set GPU Governor", e);
            }
        }).start();
    }
}