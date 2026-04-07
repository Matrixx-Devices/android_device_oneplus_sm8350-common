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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class Startup extends BroadcastReceiver {

    private static final String TAG = "DeviceSettingsStartup";

    private static final String ACTION_INITIALIZE =
            "lineageos.intent.action.INITIALIZE_LINEAGE_HARDWARE";

    private static final ExecutorService sExecutor = Executors.newSingleThreadExecutor();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_INITIALIZE.equals(intent.getAction())) {
            return;
        }

        final PendingResult pendingResult = goAsync();

        sExecutor.execute(() -> {
            try {
                DeviceSettings.restoreFastChargeSetting(context);
                DeviceSettings.restoreVibStrengthSetting(context);
            } catch (Exception e) {
                Log.e(TAG, "Failed to restore hardware settings during startup", e);
            } finally {
                pendingResult.finish();
            }
        });
    }
}
