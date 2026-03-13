package org.lineageos.device.DeviceSettings.powertools;

import android.app.GameManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

public class GameSpaceSyncReceiver extends BroadcastReceiver {
    private static final String TAG = "GameSpaceSyncReceiver";
    private static final String ACTION_GAME_START = "io.chaldeaprjkt.gamespace.action.GAME_START";
    private static final String ACTION_GAME_STOP = "io.chaldeaprjkt.gamespace.action.GAME_STOP";
    private static final String EXTRA_PACKAGE_NAME = "package_name";
    private static final String KEY_PRE_GAME_MODE = "pre_game_mode";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        SharedPreferences prefs = context.getSharedPreferences(
                context.getPackageName() + "_preferences", Context.MODE_PRIVATE);
        PowerProfileUtil util = new PowerProfileUtil(context);

        if (ACTION_GAME_START.equals(action)) {
            String pkg = intent.getStringExtra(EXTRA_PACKAGE_NAME);
            int targetMode = PowerProfileUtil.MODE_PERFORMANCE; // fallback

            try {
                if (pkg != null) {
                    String gamesList = android.provider.Settings.System.getString(
                            context.getContentResolver(), "gamespace_game_list");
                    
                    if (gamesList != null) {
                        String[] games = gamesList.split(";");
                        for (String gameEntry : games) {
                            if (gameEntry.startsWith(pkg + "=")) {
                                String[] parts = gameEntry.split("=");
                                if (parts.length == 2) {
                                    int gameMode = Integer.parseInt(parts[1]);
                                    targetMode = mapGameMode(gameMode);
                                    Log.i(TAG, "Game Start: " + pkg + " gsMode=" + gameMode + " -> ptMode=" + targetMode);
                                }
                                break;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to get GameMode mapping, falling back to Perf mode", e);
            }

            int currentMode = util.getCurrentMode();
            if (currentMode != targetMode) {
                if (!prefs.contains(KEY_PRE_GAME_MODE)) {
                   prefs.edit().putInt(KEY_PRE_GAME_MODE, currentMode).apply();
                }
            }
            
            util.setMode(targetMode);
            
        } else if (ACTION_GAME_STOP.equals(action)) {
            Log.i(TAG, "Game Stop broadcast received. Restoring base profile.");
            
            int preGameMode = prefs.getInt(KEY_PRE_GAME_MODE, PowerProfileUtil.MODE_BALANCE);
            util.setMode(preGameMode);
            prefs.edit().remove(KEY_PRE_GAME_MODE).apply();
        }
    }

    private int mapGameMode(int gameMode) {
        switch (gameMode) {
            case GameManager.GAME_MODE_PERFORMANCE: return PowerProfileUtil.MODE_PERFORMANCE;
            case GameManager.GAME_MODE_BATTERY:     return PowerProfileUtil.MODE_BATTERY_SAVER;
            case GameManager.GAME_MODE_STANDARD:
            default:                                return PowerProfileUtil.MODE_BALANCE;
        }
    }
}
