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
/*
 * Copyright (C) 2018-2022 crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.device.DeviceSettings.slider;

import android.app.NotificationManager;
import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.os.Handler;
import android.os.PowerManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;

import org.lineageos.device.DeviceSettings.Constants;
import org.lineageos.device.DeviceSettings.SliderControllerBase;

public final class UnifiedSliderController extends SliderControllerBase {

    public static final int ID = 99;
    private static final String TAG = "UnifiedSliderController";
    private static final long BLINK_INTERVAL = 250L;
    private static final int CHANGE_DELAY = 100;
    private static final long WAKELOCK_TIMEOUT = 60000; // 1 minute safety timeout

    private final AudioManager mAudioManager;
    private final NotificationManager mNotificationManager;
    private final CameraManager mCameraManager;
    private final Handler mHandler;
    private final Handler mBlinkHandler;
    private PowerManager.WakeLock mWakeLock;

    private String mCameraId;
    private boolean mTorchEnabled = false;
    private int mZenMode;
    private int mRingMode;

    private int mSavedBrightnessMode = -1;
    private int mSavedBrightnessLevel = -1;
    private int mSavedRotationAuto = -1;
    private int mSavedRotationValue = -1;

    private int mActiveActionCategory = -1; 

    private final Runnable mBlinkRunnable = new Runnable() {
        @Override
        public void run() {
            if (setTorchMode(!mTorchEnabled)) {
                mBlinkHandler.postDelayed(this, BLINK_INTERVAL);
            } else if (mWakeLock.isHeld()) {
                mWakeLock.release();
            }
        }
    };

    public UnifiedSliderController(Context context) {
        super(context);
        mAudioManager = context.getSystemService(AudioManager.class);
        mNotificationManager = context.getSystemService(NotificationManager.class);
        mCameraManager = context.getSystemService(CameraManager.class);
        mHandler = new Handler();
        mBlinkHandler = new Handler();
        PowerManager pm = context.getSystemService(PowerManager.class);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
    }

    private int getActionCategory(int action) {
        if (action >= 10 && action < 20) return 10;
        if (action >= 20 && action < 30) return 20;
        if (action >= 30 && action < 40) return 30;
        if (action >= 40 && action < 50) return 40;
        if (action >= 50 && action < 60) return 50;
        if (action >= 60 && action < 70) return 60;
        return -1;
    }

    private boolean isPersistentCategory(int category) {
        return category == 10 || category == 50 || category == 60;
    }

    @Override
    protected int processAction(int action) {
        int newCategory = getActionCategory(action);

        // Always turn off torch immediately when moving to any non-flashlight action
        if (newCategory != 20 && mTorchEnabled) {
            mBlinkHandler.removeCallbacksAndMessages(null);
            if (mWakeLock.isHeld()) mWakeLock.release();
            setTorchMode(false);
        }

        // Restore previous state (brightness/rotation) when leaving temporary position
        if (mActiveActionCategory != -1 && mActiveActionCategory != newCategory
                && !isPersistentCategory(mActiveActionCategory)
                && mActiveActionCategory != 20) {
            restorePreviousState(mActiveActionCategory);
        }

        // Save state before applying temporary action
        if (!isPersistentCategory(newCategory) && newCategory != mActiveActionCategory) {
            saveCurrentState(newCategory);
        }

        mActiveActionCategory = newCategory;

        if (action >= 10 && action < 20) return processNotification(action);
        if (action >= 20 && action < 30) return processFlashlight(action);
        if (action >= 30 && action < 40) return processBrightness(action);
        if (action >= 40 && action < 50) return processRotation(action);
        if (action >= 50 && action < 60) return processRinger(action);
        if (action >= 60 && action < 70) return processNotificationRinger(action);
        return 0;
    }

    private void saveCurrentState(int category) {
        try {
            switch (category) {
                case 30: 
                    mSavedBrightnessMode = Settings.System.getIntForUser(mContext.getContentResolver(),
                            Settings.System.SCREEN_BRIGHTNESS_MODE, 0, UserHandle.USER_CURRENT);
                    mSavedBrightnessLevel = Settings.System.getIntForUser(mContext.getContentResolver(),
                            Settings.System.SCREEN_BRIGHTNESS, 128, UserHandle.USER_CURRENT);
                    break;
                case 40:
                    mSavedRotationAuto = Settings.System.getIntForUser(mContext.getContentResolver(),
                            Settings.System.ACCELEROMETER_ROTATION, 0, UserHandle.USER_CURRENT);
                    mSavedRotationValue = Settings.System.getIntForUser(mContext.getContentResolver(),
                            Settings.System.USER_ROTATION, 0, UserHandle.USER_CURRENT);
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to save state", e);
        }
    }

    private void restorePreviousState(int category) {
        switch (category) {
            case 20:
                mBlinkHandler.removeCallbacksAndMessages(null);
                if (mWakeLock.isHeld()) mWakeLock.release();
                setTorchMode(false);
                break;
            case 30:
                if (mSavedBrightnessMode != -1) {
                    writeSettings(Settings.System.SCREEN_BRIGHTNESS_MODE, mSavedBrightnessMode);
                    if (mSavedBrightnessLevel != -1) writeSettings(Settings.System.SCREEN_BRIGHTNESS, mSavedBrightnessLevel);
                    mSavedBrightnessMode = -1; mSavedBrightnessLevel = -1;
                }
                break;
            case 40:
                if (mSavedRotationAuto != -1) {
                    writeRotation(mSavedRotationAuto == 1, mSavedRotationValue != -1 ? mSavedRotationValue : 0);
                    mSavedRotationAuto = -1; mSavedRotationValue = -1;
                }
                break;
        }
    }

    @Override
    public void reset() {
        if (mActiveActionCategory != -1 && !isPersistentCategory(mActiveActionCategory)) {
            restorePreviousState(mActiveActionCategory);
        }
        mActiveActionCategory = -1;
        mAudioManager.setRingerModeInternal(AudioManager.RINGER_MODE_NORMAL);
        mNotificationManager.setZenMode(Settings.Global.ZEN_MODE_OFF, null, TAG);
        setTorchMode(false);
        mBlinkHandler.removeCallbacksAndMessages(null);
        if (mWakeLock.isHeld()) mWakeLock.release();
    }

    private int processNotification(int action) {
        switch (action) {
            case 10:
                mZenMode = action;
                mAudioManager.setRingerModeInternal(AudioManager.RINGER_MODE_NORMAL);
                mHandler.postDelayed(() -> {
                    if (mZenMode == action)
                        mNotificationManager.setZenMode(Settings.Global.ZEN_MODE_NO_INTERRUPTIONS, null, TAG);
                }, CHANGE_DELAY);
                return Constants.MODE_TOTAL_SILENCE;
            case 12:
                mZenMode = action;
                mAudioManager.setRingerModeInternal(AudioManager.RINGER_MODE_NORMAL);
                mHandler.postDelayed(() -> {
                    if (mZenMode == action)
                        mNotificationManager.setZenMode(Settings.Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS, null, TAG);
                }, CHANGE_DELAY);
                return Constants.MODE_PRIORITY_ONLY;
            case 13:
                mNotificationManager.setZenMode(Settings.Global.ZEN_MODE_OFF, null, TAG);
                mAudioManager.setRingerModeInternal(AudioManager.RINGER_MODE_NORMAL);
                return Constants.MODE_NONE;
        }
        return 0;
    }

    private int processFlashlight(int action) {
        mBlinkHandler.removeCallbacksAndMessages(null);
        if (mWakeLock.isHeld()) mWakeLock.release();
        switch (action) {
            case 20:
                return setTorchMode(false) ? Constants.MODE_FLASHLIGHT_OFF : 0;
            case 21:
                mCameraId = getCameraId();
                return setTorchMode(true) ? Constants.MODE_FLASHLIGHT_ON : 0;
            case 22:
                mCameraId = getCameraId();
                if (setTorchMode(true)) {
                    mWakeLock.acquire(WAKELOCK_TIMEOUT); 
                    mBlinkHandler.postDelayed(mBlinkRunnable, BLINK_INTERVAL);
                    return Constants.MODE_FLASHLIGHT_BLINK;
                }
                return 0;
        }
        return 0;
    }

    private int processBrightness(int action) {
        switch (action) {
            case 30:
                if (writeSettings(Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC))
                    return Constants.MODE_BRIGHTNESS_AUTO;
                break;
            case 31:
                if (writeSettings(Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL) &&
                    writeSettings(Settings.System.SCREEN_BRIGHTNESS, 255))
                    return Constants.MODE_BRIGHTNESS_BRIGHT;
                break;
            case 32:
                if (writeSettings(Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL) &&
                    writeSettings(Settings.System.SCREEN_BRIGHTNESS, 0))
                    return Constants.MODE_BRIGHTNESS_DARK;
                break;
        }
        return 0;
    }

    private int processRotation(int action) {
        switch (action) {
            case 40:
                return writeRotation(true, 0) ? Constants.MODE_ROTATION_AUTO : 0;
            case 41:
                return writeRotation(false, 0) ? Constants.MODE_ROTATION_0 : 0;
            case 42:
                return writeRotation(false, 1) ? Constants.MODE_ROTATION_90 : 0;
            case 43:
                return writeRotation(false, 3) ? Constants.MODE_ROTATION_270 : 0;
        }
        return 0;
    }

    private int processRinger(int action) {
        switch (action) {
            case 50:
                mAudioManager.setRingerModeInternal(AudioManager.RINGER_MODE_NORMAL);
                return Constants.MODE_RING;
            case 51:
                mAudioManager.setRingerModeInternal(AudioManager.RINGER_MODE_VIBRATE);
                return Constants.MODE_VIBRATE;
            case 52:
                mAudioManager.setRingerModeInternal(AudioManager.RINGER_MODE_SILENT);
                return Constants.MODE_SILENT;
        }
        return 0;
    }

    private int processNotificationRinger(int action) {
        switch (action) {
            case 60:
                mZenMode = action;
                mAudioManager.setRingerModeInternal(AudioManager.RINGER_MODE_SILENT);
                mHandler.postDelayed(() -> {
                    if (mZenMode == action)
                        mNotificationManager.setZenMode(Settings.Global.ZEN_MODE_NO_INTERRUPTIONS, null, TAG);
                }, CHANGE_DELAY);
                return Constants.MODE_TOTAL_SILENCE;
            case 62:
                mZenMode = action;
                mAudioManager.setRingerModeInternal(AudioManager.RINGER_MODE_NORMAL);
                mHandler.postDelayed(() -> {
                    if (mZenMode == action)
                        mNotificationManager.setZenMode(Settings.Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS, null, TAG);
                }, CHANGE_DELAY);
                return Constants.MODE_PRIORITY_ONLY;
            case 63:
                mRingMode = action;
                mNotificationManager.setZenMode(Settings.Global.ZEN_MODE_OFF, null, TAG);
                mHandler.postDelayed(() -> {
                    if (mRingMode == action)
                        mAudioManager.setRingerModeInternal(AudioManager.RINGER_MODE_NORMAL);
                }, CHANGE_DELAY);
                return Constants.MODE_NONE;
            case 64:
                mRingMode = action;
                mNotificationManager.setZenMode(Settings.Global.ZEN_MODE_OFF, null, TAG);
                mHandler.postDelayed(() -> {
                    if (mRingMode == action)
                        mAudioManager.setRingerModeInternal(AudioManager.RINGER_MODE_VIBRATE);
                }, CHANGE_DELAY);
                return Constants.MODE_VIBRATE;
            case 65:
                mRingMode = action;
                mNotificationManager.setZenMode(Settings.Global.ZEN_MODE_OFF, null, TAG);
                mHandler.postDelayed(() -> {
                    if (mRingMode == action)
                        mAudioManager.setRingerModeInternal(AudioManager.RINGER_MODE_SILENT);
                }, CHANGE_DELAY);
                return Constants.MODE_SILENT;
        }
        return 0;
    }

    private boolean setTorchMode(boolean enabled) {
        if (mCameraId == null) mCameraId = getCameraId();
        if (mCameraId == null) return false;
        try {
            mCameraManager.setTorchMode(mCameraId, enabled);
            mTorchEnabled = enabled;
            return true;
        } catch (CameraAccessException e) {
            Log.e(TAG, "Flashlight error", e);
            return false;
        }
    }

    private String getCameraId() {
        try {
            for (String id : mCameraManager.getCameraIdList()) {
                CameraCharacteristics c = mCameraManager.getCameraCharacteristics(id);
                if (c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) &&
                    c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK)
                    return id;
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Camera error", e);
        }
        return null;
    }

    private boolean writeSettings(String key, int value) {
        return Settings.System.putIntForUser(mContext.getContentResolver(),
                key, value, UserHandle.USER_CURRENT);
    }

    private boolean writeRotation(boolean auto, int rotation) {
        return Settings.System.putIntForUser(mContext.getContentResolver(),
                Settings.System.ACCELEROMETER_ROTATION, auto ? 1 : 0, UserHandle.USER_CURRENT) &&
               Settings.System.putIntForUser(mContext.getContentResolver(),
                Settings.System.USER_ROTATION, rotation, UserHandle.USER_CURRENT);
    }
}