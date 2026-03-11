/*
 * Copyright (C) 2025 kenrow214
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.device.DeviceSettings.powertools;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import androidx.preference.PreferenceManager;

public class BlurUtils {

    private static final String PREF_USER_BLUR = "user_blur_disabled_state";
    private static final String SETTING_BLUR = "disable_window_blurs";

    public static void setBlurDisabled(Context context, boolean disable) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        try {
            if (disable) {
                int currentState = Settings.Global.getInt(context.getContentResolver(), SETTING_BLUR, 0);
                if (currentState == 0) prefs.edit().putInt(PREF_USER_BLUR, currentState).apply();
                Settings.Global.putInt(context.getContentResolver(), SETTING_BLUR, 1);
            } else {
                int savedState = prefs.getInt(PREF_USER_BLUR, 0);
                Settings.Global.putInt(context.getContentResolver(), SETTING_BLUR, savedState);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
