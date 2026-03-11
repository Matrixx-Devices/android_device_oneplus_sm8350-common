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

import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentTransaction;

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

import org.lineageos.device.DeviceSettings.R;

public final class PowertoolsActivity extends CollapsingToolbarBaseActivity {

    private static final String FRAGMENT_TAG = "powertools_settings";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_powertools);

        // Ensure users have a clean exit route when launching directly from 
        // the SystemMonitorWidget or the Quick Settings Tile.
        if (getActionBar() != null) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE) // Premium UI touch
                    .replace(R.id.powertools_fragment_container, 
                             new PowertoolsSettingsFragment(), 
                             FRAGMENT_TAG)
                    .commit();
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            // Use modern predictive back routing instead of the deprecated onBackPressed()
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}