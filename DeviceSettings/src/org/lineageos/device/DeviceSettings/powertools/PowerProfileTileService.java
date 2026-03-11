/*
 * Copyright (C) 2025 kenrow214
 * Adapted for OnePlus 9 (SD888 / SM8350)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package org.lineageos.device.DeviceSettings.powertools;

import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import androidx.preference.PreferenceManager;

import org.lineageos.device.DeviceSettings.R;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PowerProfileTileService extends TileService {

    private PowerProfileUtil mManager;
    
    // Use a single-threaded executor to prevent thread-spamming if the user mashes the QS tile
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    // --- DATA-DRIVEN UI MATRIX ---
    // Indexes strictly match PowerProfileUtil.MODE_* constants
    // [0] BATTERY_SAVER, [1] BALANCE, [2] PERFORMANCE, [3] MANUAL, [4] UNKNOWN, [5] AUTO
    
    private static final int[] TILE_STATES = {
        Tile.STATE_INACTIVE, // 0: Saver
        Tile.STATE_INACTIVE, // 1: Balance
        Tile.STATE_ACTIVE,   // 2: Performance
        Tile.STATE_INACTIVE, // 3: Unused
        Tile.STATE_INACTIVE, // 4: Unknown
        Tile.STATE_ACTIVE    // 5: Auto
    };

    private static final int[] TILE_ICONS = {
        R.drawable.ic_thermal_battery_saver, // 0: Saver
        R.drawable.ic_thermal_balance,       // 1: Balance
        R.drawable.ic_thermal_performance,   // 2: Performance
        R.drawable.ic_thermal_balance,       // 3: Unused
        R.drawable.ic_thermal_balance,       // 4: Unknown
        R.drawable.ic_thermal_balance        // 5: Auto
    };

    @Override
    public void onCreate() {
        super.onCreate();
        mManager = new PowerProfileUtil(this);
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        if (mManager != null) {
            updateTile();
        }
    }

    @Override
    public void onClick() {
        if (mManager == null || mManager.isAutoModeEnabled()) {
            return; // Lock out manual QS toggling when Auto Thermal is active
        }
        
        // Push the toggle logic to our dedicated background queue
        mExecutor.execute(() -> {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

            // Cycle the mode natively and sync the preference key
            mManager.toggleMode();
            prefs.edit().putString("power_profile_mode", String.valueOf(mManager.getCurrentMode())).apply();

            updateTile(); 
        });
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) return;

        int mode = mManager.getManagedMode();
        
        // Safety bound check in case a weird mode integer gets passed
        if (mode < 0 || mode >= TILE_STATES.length) {
            mode = PowerProfileUtil.MODE_UNKNOWN; 
        }

        tile.setState(TILE_STATES[mode]);
        tile.setIcon(Icon.createWithResource(this, TILE_ICONS[mode]));
        tile.setLabel(getString(R.string.powerprofile_tile_label));
        tile.setSubtitle(mManager.getModeLabel());
        
        tile.updateTile();
    }

    @Override
    public void onDestroy() {
        // Prevent memory leaks by shutting down the executor when the service is destroyed
        mExecutor.shutdown();
        super.onDestroy();
    }
}