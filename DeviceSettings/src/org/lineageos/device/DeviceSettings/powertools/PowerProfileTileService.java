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

public class PowerProfileTileService extends TileService {

    private PowerProfileUtil mManager;

    @Override
    public void onCreate() {
        super.onCreate();
        mManager = new PowerProfileUtil(this);
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        // Update the tile state whenever the user pulls down the QS panel
        if (mManager != null) {
            updateTile();
        }
    }

    @Override
    public void onClick() {
        if (mManager == null || mManager.isAutoModeEnabled()) {
            // Do not allow changing profiles from QS tile if Auto is controlling it
            return;
        }
        
        // Push the mode toggle to a background thread to guarantee the 
        // notification shade never stutters when tapped.
        new Thread(() -> {
            int currentMode = mManager.getManagedMode();
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            
            // If we are currently in Manual mode and tapping the tile to cycle to Balance,
            // we MUST turn off the manual toggles in preferences so the UI stays synced.
            if (currentMode == PowerProfileUtil.MODE_MANUAL) {
                prefs.edit()
                     .putBoolean("cpu_enable", false)
                     .putBoolean("gpu_enable", false)
                     .apply();
            }

            // Cycle the mode natively
            mManager.toggleMode();
            
            // Sync the new mode back to the Fragment's preference key
            int newMode = mManager.getCurrentMode();
            prefs.edit().putString("power_profile_mode", String.valueOf(newMode)).apply();

            updateTile(); 
        }).start();
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null)
            return;

        int currentMode = mManager.getManagedMode();
        switch (currentMode) {
            case PowerProfileUtil.MODE_AUTO:
                tile.setState(Tile.STATE_ACTIVE);
                tile.setIcon(Icon.createWithResource(this, R.drawable.ic_thermal_balance));
                break;
            case PowerProfileUtil.MODE_PERFORMANCE:
                tile.setState(Tile.STATE_ACTIVE);
                tile.setIcon(Icon.createWithResource(this, R.drawable.ic_thermal_performance));
                break;
            case PowerProfileUtil.MODE_MANUAL:
                // Added explicit support for Manual Mode UI in the Quick Settings panel
                tile.setState(Tile.STATE_ACTIVE);
                tile.setIcon(Icon.createWithResource(this, R.drawable.ic_cpu_chip));
                break;
            case PowerProfileUtil.MODE_BATTERY_SAVER:
                tile.setState(Tile.STATE_INACTIVE);
                tile.setIcon(Icon.createWithResource(this, R.drawable.ic_thermal_battery_saver));
                break;
            case PowerProfileUtil.MODE_BALANCE:
            default:
                tile.setState(Tile.STATE_INACTIVE);
                tile.setIcon(Icon.createWithResource(this, R.drawable.ic_thermal_balance));
                break;
        }
        
        tile.setLabel(getString(R.string.powerprofile_tile_label));
        tile.setSubtitle(mManager.getModeLabel());
        tile.updateTile();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}