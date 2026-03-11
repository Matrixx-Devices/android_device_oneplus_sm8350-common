package org.lineageos.device.DeviceSettings.powertools;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

public class GameSpaceSyncReceiver extends BroadcastReceiver {
    private static final String TAG = "GameSpaceSyncReceiver";
    private static final String ACTION_GAME_START = "io.chaldeaprjkt.gamespace.action.GAME_START";
    private static final String ACTION_GAME_STOP = "io.chaldeaprjkt.gamespace.action.GAME_STOP";
    private static final String KEY_PRE_GAME_MODE = "pre_game_mode";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        SharedPreferences prefs = context.getSharedPreferences(
                context.getPackageName() + "_preferences", Context.MODE_PRIVATE);
        PowerProfileUtil util = new PowerProfileUtil(context);

        if (ACTION_GAME_START.equals(action)) {
            Log.i(TAG, "Game Start broadcast received. Triggering Gaming overrides.");
            
            // Only save the current mode if we aren't already in Performance
            int currentMode = util.getCurrentMode();
            if (currentMode != PowerProfileUtil.MODE_PERFORMANCE) {
                prefs.edit().putInt(KEY_PRE_GAME_MODE, currentMode).apply();
            }
            
            // Force Performance globally. 
            // This propagates instantly to init.performance.rc (max clocks, Kyber I/O),
            // and updates the UI/Widget perfectly.
            util.setMode(PowerProfileUtil.MODE_PERFORMANCE);
            
        } else if (ACTION_GAME_STOP.equals(action)) {
            Log.i(TAG, "Game Stop broadcast received. Restoring base profile.");
            
            int preGameMode = prefs.getInt(KEY_PRE_GAME_MODE, PowerProfileUtil.MODE_BALANCE);
            util.setMode(preGameMode);
            prefs.edit().remove(KEY_PRE_GAME_MODE).apply();
        }
    }
}
