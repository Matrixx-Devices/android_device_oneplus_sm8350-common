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

import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

public final class DeviceSettingsActivity extends CollapsingToolbarBaseActivity
        implements PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    private static final String TAG_MAIN = "main_prefs";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Ensure users have a clean exit route back to the main Android Settings app
        if (getActionBar() != null) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE) // Smooth entry
                    .replace(com.android.settingslib.collapsingtoolbar.R.id.content_frame,
                            new DeviceSettings(), TAG_MAIN)
                    .commit();
        }
    }

    @Override
    public boolean onPreferenceStartFragment(@NonNull PreferenceFragmentCompat caller, @NonNull Preference pref) {
        final String fragmentClass = pref.getFragment();
        if (fragmentClass == null) return false;

        final Fragment fragment = getSupportFragmentManager().getFragmentFactory()
                .instantiate(getClassLoader(), fragmentClass);
        
        fragment.setArguments(pref.getExtras());
        
        // Target tracking allows the new fragment to send results back to the caller if needed
        fragment.setTargetFragment(caller, 0);

        // Premium UX: Add standard Android "Open" animations for nested preference screens
        getSupportFragmentManager().beginTransaction()
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .replace(com.android.settingslib.collapsingtoolbar.R.id.content_frame, fragment)
                .addToBackStack(null)
                .commit();

        // Dynamically update the Collapsing Toolbar title to match the nested sub-screen
        if (pref.getTitle() != null) {
            setTitle(pref.getTitle());
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            // Support Android 13/14+ predictive back gestures instead of deprecated onBackPressed()
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}