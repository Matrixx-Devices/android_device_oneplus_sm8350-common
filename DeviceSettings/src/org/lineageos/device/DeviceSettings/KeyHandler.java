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
public final class KeyHandler implements DeviceKeyHandler {
    private static final String TAG = KeyHandler.class.getSimpleName();
    private static final String ALERT_SLIDER_NODE = "oplus,hall_tri_state_key";

    private final Context mContext;
    private final UnifiedSliderController mSliderController;
    private final InputManager mInputManager;
    
    private int mAlertSliderDeviceId = -1;

    private final BroadcastReceiver mSliderUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int[] actions = intent.getIntArrayExtra(Constants.EXTRA_SLIDER_ACTIONS);
            boolean isHardware = intent.getBooleanExtra("is_hardware", true);
            
            if (actions == null) {
                Log.w(TAG, "Received UPDATE_SLIDER_SETTINGS with null actions, ignoring");
                return;
            }
            
            mSliderController.update(actions);
            mSliderController.restoreState(context, isHardware);
        }
    };

    public KeyHandler(Context context) {
        mContext = context;
        mSliderController = new UnifiedSliderController(mContext);
        mInputManager = context.getSystemService(InputManager.class);
        
        mContext.registerReceiver(mSliderUpdateReceiver,
                new IntentFilter(Constants.ACTION_UPDATE_SLIDER_SETTINGS), 
                Context.RECEIVER_EXPORTED);
    }

    @Override
    public KeyEvent handleKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return event;
        }

        int currentDeviceId = event.getDeviceId();

        if (mAlertSliderDeviceId == -1 || mAlertSliderDeviceId != currentDeviceId) {
            
            InputDevice device = (mInputManager != null) ? mInputManager.getInputDevice(currentDeviceId) : null;
            
            if (device != null && ALERT_SLIDER_NODE.equals(device.getName())) {
                mAlertSliderDeviceId = currentDeviceId; // Cache it for all future presses!
            } else {
                return event; // Not the alert slider, pass it to the OS instantly
            }
        }

        mSliderController.processEvent(mContext, true);
        return null; // Consume the event so the OS doesn't try to process it
    }
}