/*
 * Copyright (C) 2025 kenrow214
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.device.DeviceSettings.powertools;

import android.app.ActivityManager;
import android.app.GameManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.preference.PreferenceManager;

import java.util.List;

/**
 * Coordinates between GameSpace and PowerTools mode state.
 *
 * Polls the foreground task every 3s to detect when a game-registered package
 * becomes active (via GameManager.getGameMode). On game start, saves the current
 * profile and boosts to Performance. On game exit, restores the saved profile.
 *
 * Uses only GameManager.GAME_MODE_* integer constants which exist in all AOSP
 * versions where GameManager is present — avoids ACTION_GAME_MODE_CHANGED which
 * is not available in this build's framework SDK.
 */
public class GameModeCoordinator {

    private static final String TAG = "GameModeCoordinator";
    private static final String KEY_PRE_GAME_MODE = "powertools_pre_game_mode";
    private static final long POLL_INTERVAL_MS = 3000;
    private static final long RESTORE_DELAY_MS = 600;

    private final Context mContext;
    private final PowerProfileUtil mPowerProfileUtil;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private Listener mListener;
    private boolean mGameSessionActive = false;
    private boolean mPolling = false;

    public interface Listener {
        void onGameSessionStarted();
        void onGameSessionEnded();
    }

    public GameModeCoordinator(Context context, PowerProfileUtil util) {
        mContext = context.getApplicationContext();
        mPowerProfileUtil = util;
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public boolean isGameSessionActive() {
        return mGameSessionActive;
    }

    public void register() {
        if (mPolling) return;
        mPolling = true;
        mHandler.postDelayed(mPollRunnable, POLL_INTERVAL_MS);
        Log.i(TAG, "GameMode polling started");
    }

    public void unregister() {
        mPolling = false;
        mHandler.removeCallbacks(mPollRunnable);
        Log.i(TAG, "GameMode polling stopped");
    }

    private final Runnable mPollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mPolling) return;
            checkForegroundGameMode();
            mHandler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    @SuppressWarnings("deprecation")
    private void checkForegroundGameMode() {
        try {
            GameManager gm = (GameManager) mContext.getSystemService(Context.GAME_SERVICE);
            if (gm == null) return;

            ActivityManager am = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return;
            List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
            if (tasks == null || tasks.isEmpty()) return;
            String topPkg = tasks.get(0).topActivity.getPackageName();

            int mode = gm.getGameMode(topPkg);
            boolean isGameActive = (mode == GameManager.GAME_MODE_PERFORMANCE
                    || mode == GameManager.GAME_MODE_STANDARD);

            if (isGameActive && !mGameSessionActive) {
                onGameStart(topPkg, mode);
            } else if (!isGameActive && mGameSessionActive) {
                onGameStop();
            }
        } catch (Exception e) {
            Log.w(TAG, "GameMode check failed: " + e.getMessage());
        }
    }

    private void onGameStart(String pkg, int gameMode) {
        mGameSessionActive = true;
        int currentMode = mPowerProfileUtil.getCurrentMode();
        savePreGameMode(currentMode);
        Log.i(TAG, "Game started: " + pkg + " mode=" + gameMode + ". Saved=" + currentMode + ", boosting to Perf.");
        mPowerProfileUtil.setMode(PowerProfileUtil.MODE_PERFORMANCE);
        if (mListener != null) mListener.onGameSessionStarted();
    }

    private void onGameStop() {
        mHandler.postDelayed(() -> {
            int savedMode = loadPreGameMode();
            Log.i(TAG, "Game ended. Restoring mode=" + savedMode);
            mPowerProfileUtil.setMode(savedMode);
            mGameSessionActive = false;
            if (mListener != null) mListener.onGameSessionEnded();
        }, RESTORE_DELAY_MS);
    }

    private void savePreGameMode(int mode) {
        PreferenceManager.getDefaultSharedPreferences(mContext)
                .edit().putInt(KEY_PRE_GAME_MODE, mode).apply();
    }

    private int loadPreGameMode() {
        return PreferenceManager.getDefaultSharedPreferences(mContext)
                .getInt(KEY_PRE_GAME_MODE, PowerProfileUtil.MODE_BALANCE);
    }
}
