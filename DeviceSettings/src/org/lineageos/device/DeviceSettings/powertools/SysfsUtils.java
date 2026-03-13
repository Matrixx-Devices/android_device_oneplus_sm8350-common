/*
 * Copyright (C) 2025 kenrow214
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.device.DeviceSettings.powertools;

import android.os.SystemProperties;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public final class SysfsUtils {

    private static final String TAG = "SysfsUtils";

    private SysfsUtils() {}

    public static boolean isReadable(String path) {
        return new File(path).canRead();
    }

    public static boolean isWritable(String path) {
        return new File(path).canWrite();
    }

    public static String readLine(String path) {
        try {
            List<String> lines = Files.readAllLines(Paths.get(path), StandardCharsets.UTF_8);
            return lines.isEmpty() ? null : lines.get(0).trim();
        } catch (IOException e) {
            return null;
        }
    }

    public static int readInt(String path, int defaultValue) {
        String val = readLine(path);
        if (val == null || val.isEmpty()) return defaultValue;
        
        try {
            return Integer.parseInt(val); // trim() is already handled in readLine()
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static boolean writeValue(String path, String value) {
        if (value == null) return false;
        
        try {
            Files.write(Paths.get(path), value.getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException e) {
            Log.w(TAG, "writeValue failed: " + path, e);
            return false;
        }
    }

    public static void writeProperty(String key, String value) {
        try {
            SystemProperties.set(key, value);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set property " + key, e);
        }
    }
}