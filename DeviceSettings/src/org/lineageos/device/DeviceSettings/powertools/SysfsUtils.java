/*
 * Copyright (C) 2025 kenrow214
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.device.DeviceSettings.powertools;

import android.util.Log;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;

public final class SysfsUtils {

    private static final String TAG = "SysfsUtils";

    private SysfsUtils() {}

    public static boolean isReadable(String path) {
        try {
            new FileReader(path).close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean isWritable(String path) {
        try {
            new FileOutputStream(path, true).close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static String readLine(String path) {
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            return br.readLine();
        } catch (IOException e) {
            return null;
        }
    }

    public static int readInt(String path, int defaultValue) {
        String val = readLine(path);
        if (val == null) return defaultValue;
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static boolean writeValue(String path, String value) {
        try (FileOutputStream fos = new FileOutputStream(path, false)) {
            fos.write(value.getBytes());
            return true;
        } catch (IOException e) {
            Log.w(TAG, "writeValue failed: " + path, e);
            return false;
        }
    }
}