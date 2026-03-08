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
import android.content.IntentFilter;
import android.hardware.input.InputManager;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;

import androidx.annotation.Keep;

import com.android.internal.os.DeviceKeyHandler;

import org.lineageos.device.DeviceSettings.Constants;
import org.lineageos.device.DeviceSettings.slider.UnifiedSliderController;

@Keep
public class KeyHandler implements DeviceKeyHandler {
    private static final String TAG = KeyHandler.class.getSimpleName();

    private final Context mContext;
    private final UnifiedSliderController mSliderController;
    private final InputManager mInputManager;

    private final BroadcastReceiver mSliderUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int[] actions = intent.getIntArrayExtra(Constants.EXTRA_SLIDER_ACTIONS);
            if (actions == null) {
                Log.w(TAG, "Received UPDATE_SLIDER_SETTINGS with null actions, ignoring");
                return;
            }
            mSliderController.update(actions);
            mSliderController.restoreState(context, false);
        }
    };

    public KeyHandler(Context context) {
        mContext = context;
        mSliderController = new UnifiedSliderController(mContext);
        mContext.registerReceiver(mSliderUpdateReceiver,
                new IntentFilter(Constants.ACTION_UPDATE_SLIDER_SETTINGS));
        mInputManager = mContext.getSystemService(InputManager.class);
    }

    @Override
    public KeyEvent handleKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return event;
        }

        // Null-safe device lookup
        InputDevice device = (mInputManager != null)
                ? mInputManager.getInputDevice(event.getDeviceId()) : null;
        if (device == null) {
            return event;
        }

        String name = device.getName();
        if (name == null || !name.equals("oplus,hall_tri_state_key")) {
            return event;
        }

        mSliderController.processEvent(mContext);
        return null;
    }
}
