/*
 * Copyright (C) 2018-2022 crDroid Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lineageos.device.DeviceSettings;

import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public final class Utils {

    private static final String TAG = "DeviceSettingsUtils";

    private Utils() {}

    /**
     * Write a string value to the specified file using fast NIO.
     */
    public static void writeValue(String filename, String value) {
        if (filename == null || value == null) return;
        
        try {
            Files.write(Paths.get(filename), value.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            Log.e(TAG, "Failed to write value to " + filename, e);
        }
    }

    /**
     * Reads the first line of a file, automatically handling stream closures.
     */
    public static String readLine(String filename) {
        if (filename == null) return null;
        
        try {
            List<String> lines = Files.readAllLines(Paths.get(filename), StandardCharsets.UTF_8);
            return lines.isEmpty() ? null : lines.get(0).trim();
        } catch (IOException e) {
            return null;
        }
    }

    public static String getFileValue(String filename, String defValue) {
        String fileValue = readLine(filename);
        return fileValue != null ? fileValue : defValue;
    }

    public static boolean getFileValueAsBoolean(String filename, boolean defValue) {
        String fileValue = readLine(filename);
        if (fileValue != null) {
            return !"0".equals(fileValue);
        }
        return defValue;
    }

    public static boolean fileExists(String filename) {
        return filename != null && new File(filename).exists();
    }

    public static boolean fileWritable(String filename) {
        return filename != null && new File(filename).canWrite();
    }
}