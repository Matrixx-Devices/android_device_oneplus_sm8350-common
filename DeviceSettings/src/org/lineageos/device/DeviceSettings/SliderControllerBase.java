/*
 * Copyright (C) 2018-2022 crDroid Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lineageos.device.DeviceSettings;

import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;
import android.os.Vibrator;
import android.util.Log;

import lineageos.providers.LineageSettings;

import org.lineageos.internal.util.FileUtils;
import org.lineageos.device.DeviceSettings.Constants;

public abstract class SliderControllerBase {

    private static final String TAG = "SliderControllerBase";

    protected final Context mContext;

    private Vibrator mVibrator;

    protected int[] mActions = null;
    protected boolean mIsHardware = false;

    public SliderControllerBase(Context context) {
        mContext = context;
        mVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (mVibrator == null || !mVibrator.hasVibrator()) {
            mVibrator = null;
        }
    }

    public final void update(int[] actions) {
        if (actions != null && actions.length == 3) {
            mActions = actions;
        }
    }

    protected abstract int processAction(int action);

    public abstract void reset();

    public final int processEvent(Context context, boolean isHardware) {
        mIsHardware = isHardware;
        int result = restoreState(context, true);
        if (result > 0) {
            doHapticFeedback();
        }

        return result;
    }

    public final int processEvent(Context context) {
        return processEvent(context, true);
    }

    public static void sendUpdateBroadcast(Context context, int position, int result) {
        Intent intent = new Intent(Constants.ACTION_UPDATE_SLIDER_POSITION);
        intent.putExtra(Constants.EXTRA_SLIDER_POSITION, position);
        intent.putExtra(Constants.EXTRA_SLIDER_POSITION_VALUE, result);
        context.sendBroadcastAsUser(intent, UserHandle.CURRENT);
        intent.setFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
        Log.d(TAG, "slider change to positon " + position);
    }

    public final int restoreState(Context context, boolean notify) {
        int ret = 0;
        if (mActions == null) {
            return ret;
        }

        try {
            String stateString = FileUtils.readOneLine(Constants.SLIDER_STATE);
            if (stateString == null) {
                Log.w(TAG, "Tri-state node not ready or empty");
                return ret;
            }
            
            int state = Integer.parseInt(stateString.trim());
            
            if (state < 1 || state > mActions.length) {
                Log.w(TAG, "Invalid slider state reported by kernel: " + state);
                return ret;
            }
            
            ret = processAction(mActions[state - 1]);
            if (ret > 0 && notify) {
                sendUpdateBroadcast(context, state - 1, ret);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to restore slider state", e);
        }
        return ret;
    }

    public final int restoreState(Context context, boolean notify, boolean isHardware) {
        mIsHardware = isHardware;
        return restoreState(context, notify);
    }

    private void doHapticFeedback() {
        if (mVibrator == null) {
            return;
        }
        boolean enabled = LineageSettings.System.getIntForUser(mContext.getContentResolver(),
                LineageSettings.System.TOUCHSCREEN_GESTURE_HAPTIC_FEEDBACK, 1, UserHandle.USER_CURRENT) != 0;
        if (enabled) {
            mVibrator.vibrate(50);
        }
    }
}