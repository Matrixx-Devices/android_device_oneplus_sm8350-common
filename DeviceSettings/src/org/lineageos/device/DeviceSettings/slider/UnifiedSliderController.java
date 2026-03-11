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

package org.lineageos.device.DeviceSettings.slider;

import android.app.NotificationManager;
import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;

import org.lineageos.device.DeviceSettings.Constants;
import org.lineageos.device.DeviceSettings.SliderControllerBase;

public final class UnifiedSliderController extends SliderControllerBase {

    public static final int ID = 99;
    private static final String TAG = "UnifiedSliderController";
    private static final long BLINK_INTERVAL_MS = 250L;
    private static final int CHANGE_DELAY_MS = 100;
    private static final long WAKELOCK_TIMEOUT_MS = 60000L; 

    // --- ACTION CATEGORIES ---
    private static final int CAT_NOTIF = 10;
    private static final int CAT_FLASHLIGHT = 20;
    private static final int CAT_BRIGHTNESS = 30;
    private static final int CAT_ROTATION = 40;
    private static final int CAT_RINGER = 50;
    private static final int CAT_NOTIF_RINGER = 60;

    private final AudioManager mAudioManager;
    private final NotificationManager mNotificationManager;
    private final CameraManager mCameraManager;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Handler mBlinkHandler = new Handler(Looper.getMainLooper());
    private final PowerManager.WakeLock mWakeLock;

    private String mCameraId;
    private boolean mTorchEnabled = false;
    private int mZenMode, mRingMode;

    // --- SAVED STATES ---
    private int mSavedBrightnessMode = -1;
    private int mSavedBrightnessLevel = -1;
    private int mSavedRotationAuto = -1;
    private int mSavedRotationValue = -1;
    private int mActiveCategory = -1; 

    private final Runnable mBlinkRunnable = new Runnable() {
        @Override
        public void run() {
            if (setTorchMode(!mTorchEnabled)) {
                mBlinkHandler.postDelayed(this, BLINK_INTERVAL_MS);
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
        mWakeLock = context.getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
    }

    private boolean isPersistentCategory(int category) {
        return category == CAT_NOTIF || category == CAT_RINGER || category == CAT_NOTIF_RINGER;
    }

    @Override
    protected int processAction(int action) {
        // Mathematical grouping: e.g., action 42 -> (42/10)*10 = 40 (CAT_ROTATION)
        int newCategory = (action / 10) * 10;

        // CRITICAL BUG FIX: Unconditionally kill the blink handler and force torch off 
        // whenever we transition OUT of the flashlight category.
        if (mActiveCategory == CAT_FLASHLIGHT && newCategory != CAT_FLASHLIGHT) {
            stopFlashlightBlink();
            setTorchMode(false);
        }

        // Restore previous temporary states if moving to a new category
        if (mActiveCategory != -1 && mActiveCategory != newCategory 
                && !isPersistentCategory(mActiveCategory) && mActiveCategory != CAT_FLASHLIGHT) {
            restorePreviousState(mActiveCategory);
        }

        // Snapshot current state before applying temporary slider overrides
        if (!isPersistentCategory(newCategory) && newCategory != mActiveCategory) {
            saveCurrentState(newCategory);
        }

        mActiveCategory = newCategory;

        // Route to the appropriate logic block
        switch (newCategory) {
            case CAT_NOTIF: return processNotification(action);
            case CAT_FLASHLIGHT: return processFlashlight(action);
            case CAT_BRIGHTNESS: return processBrightness(action);
            case CAT_ROTATION: return processRotation(action);
            case CAT_RINGER: return processRinger(action);
            case CAT_NOTIF_RINGER: return processNotificationRinger(action);
            default: return 0;
        }
    }

    private void saveCurrentState(int category) {
        try {
            if (category == CAT_BRIGHTNESS) {
                mSavedBrightnessMode = getSystemInt(Settings.System.SCREEN_BRIGHTNESS_MODE, 0);
                mSavedBrightnessLevel = getSystemInt(Settings.System.SCREEN_BRIGHTNESS, 128);
            } else if (category == CAT_ROTATION) {
                mSavedRotationAuto = getSystemInt(Settings.System.ACCELEROMETER_ROTATION, 0);
                mSavedRotationValue = getSystemInt(Settings.System.USER_ROTATION, 0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to save state", e);
        }
    }

    private void restorePreviousState(int category) {
        if (category == CAT_FLASHLIGHT) {
            stopFlashlightBlink();
            setTorchMode(false);
        } else if (category == CAT_BRIGHTNESS && mSavedBrightnessMode != -1) {
            writeSettings(Settings.System.SCREEN_BRIGHTNESS_MODE, mSavedBrightnessMode);
            if (mSavedBrightnessLevel != -1) writeSettings(Settings.System.SCREEN_BRIGHTNESS, mSavedBrightnessLevel);
            mSavedBrightnessMode = mSavedBrightnessLevel = -1;
        } else if (category == CAT_ROTATION && mSavedRotationAuto != -1) {
            writeRotation(mSavedRotationAuto == 1, mSavedRotationValue != -1 ? mSavedRotationValue : 0);
            mSavedRotationAuto = mSavedRotationValue = -1;
        }
    }

    @Override
    public void reset() {
        if (mActiveCategory != -1 && !isPersistentCategory(mActiveCategory)) {
            restorePreviousState(mActiveCategory);
        }
        mActiveCategory = -1;
        mAudioManager.setRingerModeInternal(AudioManager.RINGER_MODE_NORMAL);
        mNotificationManager.setZenMode(Settings.Global.ZEN_MODE_OFF, null, TAG);
        setTorchMode(false);
        stopFlashlightBlink();
    }

    private void stopFlashlightBlink() {
        mBlinkHandler.removeCallbacksAndMessages(null);
        setTorchMode(false);
        if (mWakeLock.isHeld()) mWakeLock.release();
    }

    // --- SUB-CONTROLLER LOGIC BLOCKS ---

    private int processNotification(int action) {
        mZenMode = action;
        if (action == 13) {
            mNotificationManager.setZenMode(Settings.Global.ZEN_MODE_OFF, null, TAG);
            mAudioManager.setRingerModeInternal(AudioManager.RINGER_MODE_NORMAL);
            return Constants.MODE_NONE;
        }
        
        mAudioManager.setRingerModeInternal(AudioManager.RINGER_MODE_NORMAL);
        mHandler.postDelayed(() -> {
            if (mZenMode == action) {
                int mode = (action == 10) ? Settings.Global.ZEN_MODE_NO_INTERRUPTIONS : Settings.Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS;
                mNotificationManager.setZenMode(mode, null, TAG);
            }
        }, CHANGE_DELAY_MS);
        
        return (action == 10) ? Constants.MODE_TOTAL_SILENCE : Constants.MODE_PRIORITY_ONLY;
    }

    private int processFlashlight(int action) {
        stopFlashlightBlink();
        switch (action) {
            case 20: return setTorchMode(false) ? Constants.MODE_FLASHLIGHT_OFF : 0;
            case 21: return setTorchMode(true) ? Constants.MODE_FLASHLIGHT_ON : 0;
            case 22:
                if (setTorchMode(true)) {
                    mWakeLock.acquire(WAKELOCK_TIMEOUT_MS); 
                    mBlinkHandler.postDelayed(mBlinkRunnable, BLINK_INTERVAL_MS);
                    return Constants.MODE_FLASHLIGHT_BLINK;
                }
        }
        return 0;
    }

    private int processBrightness(int action) {
        if (action == 30 && writeSettings(Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)) {
            return Constants.MODE_BRIGHTNESS_AUTO;
        }
        
        if (writeSettings(Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)) {
            if (action == 31 && writeSettings(Settings.System.SCREEN_BRIGHTNESS, 255)) return Constants.MODE_BRIGHTNESS_BRIGHT;
            if (action == 32 && writeSettings(Settings.System.SCREEN_BRIGHTNESS, 0)) return Constants.MODE_BRIGHTNESS_DARK;
        }
        return 0;
    }

    private int processRotation(int action) {
        if (action == 40) return writeRotation(true, 0) ? Constants.MODE_ROTATION_AUTO : 0;
        if (action == 41) return writeRotation(false, 0) ? Constants.MODE_ROTATION_0 : 0;
        if (action == 42) return writeRotation(false, 1) ? Constants.MODE_ROTATION_90 : 0;
        if (action == 43) return writeRotation(false, 3) ? Constants.MODE_ROTATION_270 : 0;
        return 0;
    }

    private int processRinger(int action) {
        int mode = (action == 50) ? AudioManager.RINGER_MODE_NORMAL : 
                   (action == 51) ? AudioManager.RINGER_MODE_VIBRATE : AudioManager.RINGER_MODE_SILENT;
        mAudioManager.setRingerModeInternal(mode);
        
        return (action == 50) ? Constants.MODE_RING : (action == 51) ? Constants.MODE_VIBRATE : Constants.MODE_SILENT;
    }

    private int processNotificationRinger(int action) {
        if (action == 60 || action == 62) {
            mZenMode = action;
            mAudioManager.setRingerModeInternal(action == 60 ? AudioManager.RINGER_MODE_SILENT : AudioManager.RINGER_MODE_NORMAL);
            mHandler.postDelayed(() -> {
                if (mZenMode == action) {
                    int mode = (action == 60) ? Settings.Global.ZEN_MODE_NO_INTERRUPTIONS : Settings.Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS;
                    mNotificationManager.setZenMode(mode, null, TAG);
                }
            }, CHANGE_DELAY_MS);
            return (action == 60) ? Constants.MODE_TOTAL_SILENCE : Constants.MODE_PRIORITY_ONLY;
        }
        
        mRingMode = action;
        mNotificationManager.setZenMode(Settings.Global.ZEN_MODE_OFF, null, TAG);
        mHandler.postDelayed(() -> {
            if (mRingMode == action) {
                int mode = (action == 63) ? AudioManager.RINGER_MODE_NORMAL : 
                           (action == 64) ? AudioManager.RINGER_MODE_VIBRATE : AudioManager.RINGER_MODE_SILENT;
                mAudioManager.setRingerModeInternal(mode);
            }
        }, CHANGE_DELAY_MS);
        
        return (action == 63) ? Constants.MODE_NONE : (action == 64) ? Constants.MODE_VIBRATE : Constants.MODE_SILENT;
    }

    // --- HARDWARE / SYSTEM UTILS ---

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
                if (Boolean.TRUE.equals(c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)) &&
                    c.get(CameraCharacteristics.LENS_FACING) != null && 
                    c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) {
                    return id;
                }
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Camera error", e);
        }
        return null;
    }

    private int getSystemInt(String key, int def) {
        return Settings.System.getIntForUser(mContext.getContentResolver(), key, def, UserHandle.USER_CURRENT);
    }

    private boolean writeSettings(String key, int value) {
        return Settings.System.putIntForUser(mContext.getContentResolver(), key, value, UserHandle.USER_CURRENT);
    }

    private boolean writeRotation(boolean auto, int rotation) {
        return writeSettings(Settings.System.ACCELEROMETER_ROTATION, auto ? 1 : 0) &&
               writeSettings(Settings.System.USER_ROTATION, rotation);
    }
}