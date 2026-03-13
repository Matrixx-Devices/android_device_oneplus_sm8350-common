/*
 * Copyright (C) 2025 kenrow214
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.device.DeviceSettings.powertools;

import android.os.SystemProperties;
import android.util.Log;

public final class CPUUtils {

    private static final String TAG = "CPUUtils";
    private static final String SAFE_DUMMY_GOV = "schedutil";

    public static void setCPULittleFreq(String minFreq, String maxFreq, String governor) {
        new Thread(() -> {
            try {
                if (governor.equals(SystemProperties.get("persist.sys.parts.cpu.little.governor"))) {
                    SystemProperties.set("persist.sys.parts.cpu.little.governor", SAFE_DUMMY_GOV);
                }
                SystemProperties.set("persist.sys.parts.cpu.little.min_frequency", minFreq);
                SystemProperties.set("persist.sys.parts.cpu.little.max_frequency", maxFreq);
                SystemProperties.set("persist.sys.parts.cpu.little.governor", governor);
            } catch (Exception e) {
                Log.e(TAG, "Failed to set CPU Little Freq", e);
            }
        }).start();
    }

    public static void setCPUBigFreq(String minFreq, String maxFreq, String governor) {
        new Thread(() -> {
            try {
                if (governor.equals(SystemProperties.get("persist.sys.parts.cpu.big.governor"))) {
                    SystemProperties.set("persist.sys.parts.cpu.big.governor", SAFE_DUMMY_GOV);
                }
                SystemProperties.set("persist.sys.parts.cpu.big.min_frequency", minFreq);
                SystemProperties.set("persist.sys.parts.cpu.big.max_frequency", maxFreq);
                SystemProperties.set("persist.sys.parts.cpu.big.governor", governor);
            } catch (Exception e) {
                Log.e(TAG, "Failed to set CPU Big Freq", e);
            }
        }).start();
    }

    public static void setCPUPrimeFreq(String minFreq, String maxFreq, String governor) {
        new Thread(() -> {
            try {
                if (governor.equals(SystemProperties.get("persist.sys.parts.cpu.prime.governor"))) {
                    SystemProperties.set("persist.sys.parts.cpu.prime.governor", SAFE_DUMMY_GOV);
                }
                SystemProperties.set("persist.sys.parts.cpu.prime.min_frequency", minFreq);
                SystemProperties.set("persist.sys.parts.cpu.prime.max_frequency", maxFreq);
                SystemProperties.set("persist.sys.parts.cpu.prime.governor", governor);
            } catch (Exception e) {
                Log.e(TAG, "Failed to set CPU Prime Freq", e);
            }
        }).start();
    }
}