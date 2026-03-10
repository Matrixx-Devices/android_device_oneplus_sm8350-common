/*
 * Copyright (C) 2025 kenrow214
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.device.DeviceSettings.powertools;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import org.lineageos.device.DeviceSettings.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PowertoolsSettingsFragment extends PreferenceFragmentCompat
        implements Preference.OnPreferenceChangeListener {

    private static final String KEY_AUTO_THERMAL = "auto_thermal_enable";
    private static final String KEY_AUTO_STATUS = "auto_thermal_status";
    private SwitchPreferenceCompat mAutoThermalPref;
    private Preference mAutoStatusPref;

    private static final String KEY_POWER_PROFILE_MODE = "power_profile_mode";
    private static final String KEY_MODE_STATUS = "mode_status_info";
    private ListPreference mPowerProfilePref;
    private Preference mModeStatusPref;
    private PowerProfileUtil mPowerProfileUtil;

    // STORAGE
    private static final String KEY_STORAGE_ENABLE = "storage_enable";
    private SwitchPreferenceCompat mStorageEnablePref;
    private ListPreference mIoSchedulerPref;
    private static final String IO_DEFAULT_SCHED = "bfq";

    // GPU
    private static final String KEY_GPU_ENABLE = "gpu_enable";
    private SwitchPreferenceCompat mGpuEnablePref;
    private ListPreference mGpuMinFreqPref, mGpuMaxFreqPref, mGpuGovernorPref;
    private static final String GPU_DEFAULT_MIN = "315000000";
    private static final String GPU_DEFAULT_MAX = "840000000";
    private static final String GPU_DEFAULT_GOV = "userspace";

    // CPU
    private static final String KEY_CPU_ENABLE = "cpu_enable";
    private SwitchPreferenceCompat mCpuEnablePref;
    private static final String CPU_LITTLE_DEFAULT_MIN = "300000";
    private static final String CPU_LITTLE_DEFAULT_MAX = "1804800";
    private static final String CPU_LITTLE_DEFAULT_GOV = "schedutil";
    private static final String CPU_BIG_DEFAULT_MIN = "710400";
    private static final String CPU_BIG_DEFAULT_MAX = "2419200";
    private static final String CPU_BIG_DEFAULT_GOV = "schedutil";
    private static final String CPU_PRIME_DEFAULT_MIN = "844800";
    private static final String CPU_PRIME_DEFAULT_MAX = "2841600";
    private static final String CPU_PRIME_DEFAULT_GOV = "schedutil";

    private static final String KEY_RESET_ON_BOOT = "reset_on_boot";
    private static final String[] PERSIST_KEYS = PowerProfileUtil.PERSIST_KEYS;

    private static final String KEY_CPU_LITTLE_MIN_FREQ = PowerProfileUtil.KEY_CPU_LITTLE_MIN_FREQ;
    private static final String KEY_CPU_LITTLE_MAX_FREQ = PowerProfileUtil.KEY_CPU_LITTLE_MAX_FREQ;
    private static final String KEY_CPU_LITTLE_GOVERNOR = PowerProfileUtil.KEY_CPU_LITTLE_GOVERNOR;
    private static final String KEY_CPU_BIG_MIN_FREQ = PowerProfileUtil.KEY_CPU_BIG_MIN_FREQ;
    private static final String KEY_CPU_BIG_MAX_FREQ = PowerProfileUtil.KEY_CPU_BIG_MAX_FREQ;
    private static final String KEY_CPU_BIG_GOVERNOR = PowerProfileUtil.KEY_CPU_BIG_GOVERNOR;
    private static final String KEY_CPU_PRIME_MIN_FREQ = PowerProfileUtil.KEY_CPU_PRIME_MIN_FREQ;
    private static final String KEY_CPU_PRIME_MAX_FREQ = PowerProfileUtil.KEY_CPU_PRIME_MAX_FREQ;
    private static final String KEY_CPU_PRIME_GOVERNOR = PowerProfileUtil.KEY_CPU_PRIME_GOVERNOR;
    private static final String KEY_GPU_MIN_FREQ = PowerProfileUtil.KEY_GPU_MIN_FREQ;
    private static final String KEY_GPU_MAX_FREQ = PowerProfileUtil.KEY_GPU_MAX_FREQ;
    private static final String KEY_GPU_GOVERNOR = PowerProfileUtil.KEY_GPU_GOVERNOR;
    private static final String KEY_IO_SCHEDULER = PowerProfileUtil.KEY_IO_SCHEDULER;

    private ListPreference mCpuLittleMinFreqPref, mCpuLittleMaxFreqPref, mCpuLittleGovernorPref;
    private ListPreference mCpuBigMinFreqPref, mCpuBigMaxFreqPref, mCpuBigGovernorPref;
    private ListPreference mCpuPrimeMinFreqPref, mCpuPrimeMaxFreqPref, mCpuPrimeGovernorPref;

    private static Handler sMainHandler;
    private List<Preference> mAllControlPrefs;
    private List<Preference> mCpuGpuPrefs;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.powertools_settings, rootKey);

        mAutoThermalPref = findPreference(KEY_AUTO_THERMAL);
        mAutoStatusPref = findPreference(KEY_AUTO_STATUS);
        if (mAutoThermalPref != null)
            mAutoThermalPref.setOnPreferenceChangeListener(this);

        mPowerProfilePref = findPreference(KEY_POWER_PROFILE_MODE);
        mModeStatusPref = findPreference(KEY_MODE_STATUS);
        
        mPowerProfileUtil = new PowerProfileUtil(requireContext());
        if (mPowerProfilePref != null)
            mPowerProfilePref.setOnPreferenceChangeListener(this);

        // STORAGE
        mStorageEnablePref = findPreference(KEY_STORAGE_ENABLE);
        mIoSchedulerPref = findPreference(KEY_IO_SCHEDULER);
        if (mStorageEnablePref != null) mStorageEnablePref.setOnPreferenceChangeListener(this);
        if (mIoSchedulerPref != null) mIoSchedulerPref.setOnPreferenceChangeListener(this);

        // GPU
        mGpuEnablePref = findPreference(KEY_GPU_ENABLE);
        mGpuMinFreqPref = findPreference(KEY_GPU_MIN_FREQ);
        mGpuMaxFreqPref = findPreference(KEY_GPU_MAX_FREQ);
        mGpuGovernorPref = findPreference(KEY_GPU_GOVERNOR);
        if (mGpuEnablePref != null) mGpuEnablePref.setOnPreferenceChangeListener(this);
        if (mGpuMinFreqPref != null) mGpuMinFreqPref.setOnPreferenceChangeListener(this);
        if (mGpuMaxFreqPref != null) mGpuMaxFreqPref.setOnPreferenceChangeListener(this);
        if (mGpuGovernorPref != null) mGpuGovernorPref.setOnPreferenceChangeListener(this);

        // CPU
        mCpuEnablePref = findPreference(KEY_CPU_ENABLE);
        if (mCpuEnablePref != null) mCpuEnablePref.setOnPreferenceChangeListener(this);

        mCpuLittleMinFreqPref = findPreference(KEY_CPU_LITTLE_MIN_FREQ);
        mCpuLittleMaxFreqPref = findPreference(KEY_CPU_LITTLE_MAX_FREQ);
        mCpuLittleGovernorPref = findPreference(KEY_CPU_LITTLE_GOVERNOR);
        mCpuBigMinFreqPref = findPreference(KEY_CPU_BIG_MIN_FREQ);
        mCpuBigMaxFreqPref = findPreference(KEY_CPU_BIG_MAX_FREQ);
        mCpuBigGovernorPref = findPreference(KEY_CPU_BIG_GOVERNOR);
        mCpuPrimeMinFreqPref = findPreference(KEY_CPU_PRIME_MIN_FREQ);
        mCpuPrimeMaxFreqPref = findPreference(KEY_CPU_PRIME_MAX_FREQ);
        mCpuPrimeGovernorPref = findPreference(KEY_CPU_PRIME_GOVERNOR);

        setChangeListeners(mCpuLittleMinFreqPref, mCpuLittleMaxFreqPref, mCpuLittleGovernorPref,
                mCpuBigMinFreqPref, mCpuBigMaxFreqPref, mCpuBigGovernorPref,
                mCpuPrimeMinFreqPref, mCpuPrimeMaxFreqPref, mCpuPrimeGovernorPref);

        initializePreferenceLists();
    }

    private void initializePreferenceLists() {
        mAllControlPrefs = new ArrayList<>();
        mCpuGpuPrefs = new ArrayList<>();
        
        addIfFound(mAllControlPrefs, KEY_POWER_PROFILE_MODE, "power_profile_category",
                "power_profile_footer", KEY_MODE_STATUS,
                KEY_STORAGE_ENABLE, "storage_category", KEY_IO_SCHEDULER,
                KEY_GPU_ENABLE, "gpu_freq_category", KEY_GPU_MIN_FREQ, KEY_GPU_MAX_FREQ, KEY_GPU_GOVERNOR,
                KEY_CPU_ENABLE, "cpu_little_category", KEY_CPU_LITTLE_MIN_FREQ,
                KEY_CPU_LITTLE_MAX_FREQ, KEY_CPU_LITTLE_GOVERNOR,
                "cpu_big_category", KEY_CPU_BIG_MIN_FREQ, KEY_CPU_BIG_MAX_FREQ,
                KEY_CPU_BIG_GOVERNOR, "cpu_prime_category", KEY_CPU_PRIME_MIN_FREQ,
                KEY_CPU_PRIME_MAX_FREQ, KEY_CPU_PRIME_GOVERNOR);

        addIfFound(mCpuGpuPrefs, KEY_IO_SCHEDULER, KEY_GPU_MIN_FREQ, KEY_GPU_MAX_FREQ, KEY_GPU_GOVERNOR,
                KEY_CPU_LITTLE_MIN_FREQ, KEY_CPU_LITTLE_MAX_FREQ, KEY_CPU_LITTLE_GOVERNOR,
                KEY_CPU_BIG_MIN_FREQ, KEY_CPU_BIG_MAX_FREQ, KEY_CPU_BIG_GOVERNOR,
                KEY_CPU_PRIME_MIN_FREQ, KEY_CPU_PRIME_MAX_FREQ, KEY_CPU_PRIME_GOVERNOR);
    }

    private void setChangeListeners(Preference... prefs) {
        for (Preference p : prefs) {
            if (p != null) p.setOnPreferenceChangeListener(this);
        }
    }

    private void addIfFound(List<Preference> list, String... keys) {
        for (String key : keys) {
            Preference p = findPreference(key);
            if (p != null) list.add(p);
        }
    }

    private static Handler getMainHandler() {
        if (sMainHandler == null) {
            sMainHandler = new Handler(Looper.getMainLooper());
        }
        return sMainHandler;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mPowerProfilePref != null && mPowerProfileUtil != null) {
            String activeMode = String.valueOf(mPowerProfileUtil.getCurrentMode());
            if (!activeMode.equals(mPowerProfilePref.getValue())) {
                mPowerProfilePref.setValue(activeMode);
            }
        }
        refreshUI();
    }

    @Override
    public void onPause() {
        super.onPause();
        // Prevent memory leaks when leaving the fragment
        if (sMainHandler != null) {
            sMainHandler.removeCallbacksAndMessages(null);
        }
    }

    private void refreshUI() {
        boolean autoOn = (mAutoThermalPref != null && mAutoThermalPref.isChecked());
        if (autoOn) {
            setAllControlsEnabled(false);
            startTempUpdater();
            if (mAutoStatusPref != null) mAutoStatusPref.setVisible(true);
            if (mPowerProfilePref != null) {
                mPowerProfilePref.setVisible(true);
                mPowerProfilePref.setEnabled(false);
                mPowerProfilePref.setSummary("Auto");
            }
            updateModeStatus(PowerProfileUtil.MODE_AUTO, true);
        } else {
            getMainHandler().removeCallbacksAndMessages(null);
            setAllControlsEnabled(true);
            if (mAutoStatusPref != null) mAutoStatusPref.setVisible(false);
            if (mPowerProfilePref != null) mPowerProfilePref.setVisible(true);
            refreshModeState();
        }
    }

    private void refreshModeState() {
        boolean manual = isManualActive();
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        
        // Sync ListPreference UI to display current SharedPreferences 
        // (which are updated by init/PowerProfileUtil for preset modes)
        syncListPrefToData(mIoSchedulerPref, prefs, PowerProfileUtil.KEY_IO_SCHEDULER);
        syncListPrefToData(mGpuMinFreqPref, prefs, PowerProfileUtil.KEY_GPU_MIN_FREQ);
        syncListPrefToData(mGpuMaxFreqPref, prefs, PowerProfileUtil.KEY_GPU_MAX_FREQ);
        syncListPrefToData(mGpuGovernorPref, prefs, PowerProfileUtil.KEY_GPU_GOVERNOR);
        syncListPrefToData(mCpuLittleMinFreqPref, prefs, PowerProfileUtil.KEY_CPU_LITTLE_MIN_FREQ);
        syncListPrefToData(mCpuLittleMaxFreqPref, prefs, PowerProfileUtil.KEY_CPU_LITTLE_MAX_FREQ);
        syncListPrefToData(mCpuLittleGovernorPref, prefs, PowerProfileUtil.KEY_CPU_LITTLE_GOVERNOR);
        syncListPrefToData(mCpuBigMinFreqPref, prefs, PowerProfileUtil.KEY_CPU_BIG_MIN_FREQ);
        syncListPrefToData(mCpuBigMaxFreqPref, prefs, PowerProfileUtil.KEY_CPU_BIG_MAX_FREQ);
        syncListPrefToData(mCpuBigGovernorPref, prefs, PowerProfileUtil.KEY_CPU_BIG_GOVERNOR);
        syncListPrefToData(mCpuPrimeMinFreqPref, prefs, PowerProfileUtil.KEY_CPU_PRIME_MIN_FREQ);
        syncListPrefToData(mCpuPrimeMaxFreqPref, prefs, PowerProfileUtil.KEY_CPU_PRIME_MAX_FREQ);
        syncListPrefToData(mCpuPrimeGovernorPref, prefs, PowerProfileUtil.KEY_CPU_PRIME_GOVERNOR);

        if (manual) {
            if (mPowerProfilePref != null) {
                mPowerProfilePref.setSummary(getString(R.string.powerprofile_mode_manual));
                mPowerProfilePref.setEnabled(false);
            }
            updateModeStatus(PowerProfileUtil.MODE_MANUAL, false);
            
            boolean storageOn = (mStorageEnablePref != null && mStorageEnablePref.isChecked());
            if (mIoSchedulerPref != null) mIoSchedulerPref.setEnabled(storageOn);
            
            boolean gpuOn = (mGpuEnablePref != null && mGpuEnablePref.isChecked());
            if (mGpuMinFreqPref != null) mGpuMinFreqPref.setEnabled(gpuOn);
            if (mGpuMaxFreqPref != null) mGpuMaxFreqPref.setEnabled(gpuOn);
            if (mGpuGovernorPref != null) mGpuGovernorPref.setEnabled(gpuOn);
            
            boolean cpuOn = (mCpuEnablePref != null && mCpuEnablePref.isChecked());
            updateCpuSubPrefsEnabled(cpuOn);
            
            // Re-populate governor lists to show ALL options when in manual mode
            updateGovernorLists(PowerProfileUtil.MODE_BALANCE);
        } else {
            // Preset modes driven by init.rc
            boolean autoOn = (mAutoThermalPref != null && mAutoThermalPref.isChecked());
            int mode = PowerProfileUtil.MODE_BALANCE;
            String val = (mPowerProfilePref != null) ? mPowerProfilePref.getValue() : "1";
            try { mode = Integer.parseInt(val); } catch (NumberFormatException ignored) { }

            if (mPowerProfilePref != null) {
                if (autoOn) {
                    mPowerProfilePref.setEnabled(false);
                    mPowerProfilePref.setSummary("Auto");
                } else {
                    mPowerProfilePref.setEnabled(true);
                    CharSequence entry = mPowerProfilePref.getEntry();
                    mPowerProfilePref.setSummary(entry != null ? entry : "");
                }
            }

            updateModeStatus(mode, autoOn);
            
            // Allow master toggles to be turned on
            if (mStorageEnablePref != null) mStorageEnablePref.setEnabled(true);
            if (mGpuEnablePref != null) mGpuEnablePref.setEnabled(true);
            if (mCpuEnablePref != null) mCpuEnablePref.setEnabled(true);
            
            // Dynamically swap the available dropdown items based on the active mode
            updateGovernorLists(mode);

            // Lock ONLY the frequencies so they require Manual Mode
            if (mGpuMinFreqPref != null) mGpuMinFreqPref.setEnabled(false);
            if (mGpuMaxFreqPref != null) mGpuMaxFreqPref.setEnabled(false);
            if (mCpuLittleMinFreqPref != null) mCpuLittleMinFreqPref.setEnabled(false);
            if (mCpuLittleMaxFreqPref != null) mCpuLittleMaxFreqPref.setEnabled(false);
            if (mCpuBigMinFreqPref != null) mCpuBigMinFreqPref.setEnabled(false);
            if (mCpuBigMaxFreqPref != null) mCpuBigMaxFreqPref.setEnabled(false);
            if (mCpuPrimeMinFreqPref != null) mCpuPrimeMinFreqPref.setEnabled(false);
            if (mCpuPrimeMaxFreqPref != null) mCpuPrimeMaxFreqPref.setEnabled(false);

            // Keep the Governors unlocked so you can tweak them dynamically
            if (mGpuGovernorPref != null) mGpuGovernorPref.setEnabled(true);
            if (mCpuLittleGovernorPref != null) mCpuLittleGovernorPref.setEnabled(true);
            if (mCpuBigGovernorPref != null) mCpuBigGovernorPref.setEnabled(true);
            if (mCpuPrimeGovernorPref != null) mCpuPrimeGovernorPref.setEnabled(true);
            if (mIoSchedulerPref != null) mIoSchedulerPref.setEnabled(true);
        }
    }

    private void syncListPrefToData(ListPreference pref, SharedPreferences prefs, String key) {
        if (pref != null) {
            String val = prefs.getString(key, "");
            if (!val.isEmpty()) {
                pref.setValue(val);
                CharSequence entry = pref.getEntry();
                pref.setSummary(entry != null ? entry : val);
            }
        }
    }

    private void updateModeStatus(int mode, boolean isAuto) {
        if (mModeStatusPref != null) {
            if (isAuto) {
                mModeStatusPref.setSummary("Dynamically throttling based on device temperature");
            } else {
                switch (mode) {
                    case PowerProfileUtil.MODE_PERFORMANCE: mModeStatusPref.setSummary(R.string.mode_status_performance); break;
                    case PowerProfileUtil.MODE_BATTERY_SAVER: mModeStatusPref.setSummary(R.string.mode_status_battery_saver); break;
                    case PowerProfileUtil.MODE_MANUAL: mModeStatusPref.setSummary(R.string.mode_status_manual); break;
                    default: mModeStatusPref.setSummary(R.string.mode_status_balanced); break;
                }
            }
        }
        updateModeCard(mode, isAuto);
    }

    private void updateModeCard(int mode, boolean isAuto) {
        Preference card = findPreference("mode_card_header");
        if (card == null) return;

        if (isAuto) {
            card.setTitle("Auto Thermal");
            card.setSummary("Adaptive throttling based on temperature • Manages all CPU/GPU via base Normal mode");
            card.setIcon(R.drawable.ic_thermal_balance); 
            return;
        }

        switch (mode) {
            case PowerProfileUtil.MODE_PERFORMANCE:
                card.setTitle("Performance");
                card.setSummary("Max CPU/GPU • Kyber I/O • Background apps cleared");
                card.setIcon(R.drawable.ic_thermal_performance);
                break;
            case PowerProfileUtil.MODE_BATTERY_SAVER:
                card.setTitle("Powersave");
                card.setSummary("Conservative scaling • BFQ I/O • Background restricted");
                card.setIcon(R.drawable.ic_thermal_battery_saver);
                break;
            case PowerProfileUtil.MODE_MANUAL:
                card.setTitle("Manual");
                card.setSummary("Custom frequencies & governors • Full control over settings");
                card.setIcon(R.drawable.ic_cpu_chip);
                break;
            default:
                card.setTitle("Normal");
                card.setSummary("Balanced mode • Dynamic CPU scaling • Thermal throttle enabled");
                card.setIcon(R.drawable.ic_thermal_balance);
                break;
        }
    }

    private void setAllControlsEnabled(boolean enabled) {
        for (Preference p : mAllControlPrefs) p.setEnabled(enabled);
        if (!enabled && mAutoThermalPref != null) mAutoThermalPref.setEnabled(true);
    }

    private void startTempUpdater() {
        getMainHandler().removeCallbacksAndMessages(null);
        Runnable updater = new Runnable() {
            @Override
            public void run() {
                if (mAutoThermalPref == null || !mAutoThermalPref.isChecked()) return;
                float battC = ThermalMonitorService.getBatteryTempC();
                int state = ThermalMonitorService.getCurrentState();
                String stateStr = (state == ThermalMonitorService.STATE_HEAVY) ? "Heavy throttle (\u2265 55\u00b0C)"
                        : (state == ThermalMonitorService.STATE_MEDIUM) ? "Medium throttle (\u2265 49\u00b0C)"
                        : (state == ThermalMonitorService.STATE_LIGHT) ? "Light throttle (\u2265 45\u00b0C)"
                        : "Normal (no throttle)";

                if (mAutoStatusPref != null) mAutoStatusPref.setSummary("Monitoring");
                if (mAutoThermalPref != null) {
                    mAutoThermalPref.setSummary(getString(R.string.auto_thermal_live_summary, String.format("%.1f\u00b0C", battC), stateStr));
                }
                getMainHandler().postDelayed(this, 2500);
            }
        };
        getMainHandler().post(updater);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();

        if (KEY_AUTO_THERMAL.equals(key)) {
            boolean enable = (Boolean) newValue;
            Intent svc = new Intent(requireContext(), ThermalMonitorService.class);
            if (enable) {
                mPowerProfileUtil.setMode(PowerProfileUtil.MODE_BALANCE);
                requireContext().startForegroundService(svc);
                showToast("Auto Thermal Management enabled");
            } else {
                requireContext().stopService(svc);
                // Re-trigger Balance mode to ensure init.rc restores governors properly
                mPowerProfileUtil.setMode(PowerProfileUtil.MODE_BALANCE);
                showToast("Auto Thermal disabled");
            }
            getMainHandler().postDelayed(this::refreshUI, 150);
            return true;
        }

        if (KEY_POWER_PROFILE_MODE.equals(key)) {
            int mode = Integer.parseInt((String) newValue);
            mPowerProfileUtil.setMode(mode); // Set prop to trigger init
            
            if (mPowerProfilePref != null) {
                mPowerProfilePref.setValue((String) newValue);
                getMainHandler().post(() -> {
                    mPowerProfilePref.setSummary(mPowerProfilePref.getEntry());
                    showToast(mPowerProfilePref.getEntry() + " mode applied");
                    refreshUI(); // Refreshes UI to show the synced init values
                });
            }
            return true;
        }

        if (KEY_STORAGE_ENABLE.equals(key) || KEY_GPU_ENABLE.equals(key) || KEY_CPU_ENABLE.equals(key)) {
            boolean enabled = (Boolean) newValue;
            if (!enabled) {
                showToast("Restoring profile defaults...");
                checkPresetModeFallback();
            } else {
                mPowerProfileUtil.setMode(PowerProfileUtil.MODE_MANUAL);
                // 1. Wipe old manual saves and reset sliders to default Balance values
                resetManualModeToDefaults();
                // 2. Instantly push those fresh defaults to the hardware
                pushManualSettingsToHardware();
                
                showToast("Manual control enabled");
            }
            getMainHandler().postDelayed(this::refreshModeState, 100);
            return true;
        }

        // Check governor/scheduler restrictions in preset modes
        if (!isManualActive()) {
            int currentMode = PowerProfileUtil.MODE_BALANCE;
            String modeVal = (mPowerProfilePref != null) ? mPowerProfilePref.getValue() : "1";
            try { currentMode = Integer.parseInt(modeVal); } catch (NumberFormatException ignored) { }

            boolean isCpuGov = (preference == mCpuLittleGovernorPref || preference == mCpuBigGovernorPref || preference == mCpuPrimeGovernorPref);
            boolean isGpuGov = (preference == mGpuGovernorPref);
            boolean isIoSched = (preference == mIoSchedulerPref);

            if (isCpuGov || isGpuGov || isIoSched) {
                String restriction = getRestrictionMessage(currentMode, newValue.toString(), isCpuGov, isGpuGov, isIoSched);
                if (restriction != null) {
                    showToast(restriction);
                    return false;
                }
            }
        }

        // Apply specific settings to hardware using Utils (Works when manual is active or tweaking Governors in preset modes)
        if (preference == mIoSchedulerPref) {
            StorageUtils.setIoScheduler(newValue.toString());
        } else if (preference == mGpuMinFreqPref) {
            GPUUtils.setGPUMinFrequency(newValue.toString());
        } else if (preference == mGpuMaxFreqPref) {
            GPUUtils.setGPUMaxFrequency(newValue.toString());
        } else if (preference == mGpuGovernorPref) {
            GPUUtils.setGPUGovernor(newValue.toString());
        } else if (preference == mCpuLittleMinFreqPref || preference == mCpuLittleMaxFreqPref || preference == mCpuLittleGovernorPref) {
            CPUUtils.setCPULittleFreq(
                preference == mCpuLittleMinFreqPref ? newValue.toString() : (mCpuLittleMinFreqPref != null ? mCpuLittleMinFreqPref.getValue() : CPU_LITTLE_DEFAULT_MIN),
                preference == mCpuLittleMaxFreqPref ? newValue.toString() : (mCpuLittleMaxFreqPref != null ? mCpuLittleMaxFreqPref.getValue() : CPU_LITTLE_DEFAULT_MAX),
                preference == mCpuLittleGovernorPref ? newValue.toString() : (mCpuLittleGovernorPref != null ? mCpuLittleGovernorPref.getValue() : CPU_LITTLE_DEFAULT_GOV)
            );
        } else if (preference == mCpuBigMinFreqPref || preference == mCpuBigMaxFreqPref || preference == mCpuBigGovernorPref) {
            CPUUtils.setCPUBigFreq(
                preference == mCpuBigMinFreqPref ? newValue.toString() : (mCpuBigMinFreqPref != null ? mCpuBigMinFreqPref.getValue() : CPU_BIG_DEFAULT_MIN),
                preference == mCpuBigMaxFreqPref ? newValue.toString() : (mCpuBigMaxFreqPref != null ? mCpuBigMaxFreqPref.getValue() : CPU_BIG_DEFAULT_MAX),
                preference == mCpuBigGovernorPref ? newValue.toString() : (mCpuBigGovernorPref != null ? mCpuBigGovernorPref.getValue() : CPU_BIG_DEFAULT_GOV)
            );
        } else if (preference == mCpuPrimeMinFreqPref || preference == mCpuPrimeMaxFreqPref || preference == mCpuPrimeGovernorPref) {
            CPUUtils.setCPUPrimeFreq(
                preference == mCpuPrimeMinFreqPref ? newValue.toString() : (mCpuPrimeMinFreqPref != null ? mCpuPrimeMinFreqPref.getValue() : CPU_PRIME_DEFAULT_MIN),
                preference == mCpuPrimeMaxFreqPref ? newValue.toString() : (mCpuPrimeMaxFreqPref != null ? mCpuPrimeMaxFreqPref.getValue() : CPU_PRIME_DEFAULT_MAX),
                preference == mCpuPrimeGovernorPref ? newValue.toString() : (mCpuPrimeGovernorPref != null ? mCpuPrimeGovernorPref.getValue() : CPU_PRIME_DEFAULT_GOV)
            );
        }

        // Explicitly update the ListPreference summary after governor/scheduler changes
        // (syncListPrefToData overrides the %s auto-format, so we must do this manually)
        if (preference instanceof ListPreference) {
            ListPreference lp = (ListPreference) preference;
            lp.setValue(newValue.toString());
            CharSequence entry = lp.getEntry();
            lp.setSummary(entry != null ? entry : newValue.toString());
            showToast(entry != null ? entry + " applied" : newValue + " applied");
        }

        // Also update SharedPreferences so refreshModeState sees the new value
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        prefs.edit().putString(key, newValue.toString()).apply();

        // Save to mode persistence if it's one of the manual keys
        if (Arrays.asList(PERSIST_KEYS).contains(key)) {
            saveToModePersistence(key, newValue);
        }

        return false; // We already called setValue manually
    }

    private void resetManualModeToDefaults() {
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        SharedPreferences.Editor editor = prefs.edit();

        // Loop through all keys and force them to stock Balance defaults
        for (String baseKey : PERSIST_KEYS) {
            String defaultValue = mPowerProfileUtil.getStockValueForMode(PowerProfileUtil.MODE_BALANCE, baseKey);
            editor.putString(baseKey, defaultValue);

            // Wipe out any previously saved manual states so they don't resurrect
            String persistKey = mPowerProfileUtil.getPersistenceKey(PowerProfileUtil.MODE_MANUAL, baseKey);
            editor.remove(persistKey);

            // Update the UI dropdowns immediately
            Preference p = findPreference(baseKey);
            if (p instanceof ListPreference) {
                ((ListPreference) p).setValue(defaultValue);
            }
        }
        editor.apply();
    }

    private void pushManualSettingsToHardware() {
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        
        if (mStorageEnablePref != null && mStorageEnablePref.isChecked()) {
            StorageUtils.setIoScheduler(prefs.getString(KEY_IO_SCHEDULER, "bfq"));
        }
        
        if (mGpuEnablePref != null && mGpuEnablePref.isChecked()) {
            GPUUtils.setGPUMinFrequency(prefs.getString(KEY_GPU_MIN_FREQ, "315000000"));
            GPUUtils.setGPUMaxFrequency(prefs.getString(KEY_GPU_MAX_FREQ, "840000000"));
            GPUUtils.setGPUGovernor(prefs.getString(KEY_GPU_GOVERNOR, "msm-adreno-tz"));
        }
        
        if (mCpuEnablePref != null && mCpuEnablePref.isChecked()) {
            CPUUtils.setCPULittleFreq(
                prefs.getString(KEY_CPU_LITTLE_MIN_FREQ, "300000"),
                prefs.getString(KEY_CPU_LITTLE_MAX_FREQ, "1804800"),
                prefs.getString(KEY_CPU_LITTLE_GOVERNOR, "schedutil")
            );
            CPUUtils.setCPUBigFreq(
                prefs.getString(KEY_CPU_BIG_MIN_FREQ, "710400"),
                prefs.getString(KEY_CPU_BIG_MAX_FREQ, "2419200"),
                prefs.getString(KEY_CPU_BIG_GOVERNOR, "schedutil")
            );
            CPUUtils.setCPUPrimeFreq(
                prefs.getString(KEY_CPU_PRIME_MIN_FREQ, "844800"),
                prefs.getString(KEY_CPU_PRIME_MAX_FREQ, "2841600"),
                prefs.getString(KEY_CPU_PRIME_GOVERNOR, "schedutil")
            );
        }
    }

    private void saveToModePersistence(String key, Object value) {
        int mode = mPowerProfileUtil.getCurrentMode();
        // Since preset modes are driven by init.rc, we only want to persist custom manual configurations
        if (mode != PowerProfileUtil.MODE_MANUAL) return;

        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        String persistKey = mPowerProfileUtil.getPersistenceKey(mode, key);
        if (value instanceof String) {
            prefs.edit().putString(persistKey, (String) value).apply();
        } else if (value instanceof Boolean) {
            prefs.edit().putBoolean(persistKey, (Boolean) value).apply();
        }
    }

    private void checkPresetModeFallback() {
        if ((mCpuEnablePref == null || !mCpuEnablePref.isChecked()) && 
            (mGpuEnablePref == null || !mGpuEnablePref.isChecked()) &&
            (mStorageEnablePref == null || !mStorageEnablePref.isChecked())) {
            
            // If all manual switches are turned off, drop out of Manual mode
            // and trigger the init.rc for whatever base mode was selected in the top dropdown
            int selectedMode = PowerProfileUtil.MODE_BALANCE;
            try { selectedMode = Integer.parseInt(mPowerProfilePref.getValue()); } catch (Exception ignored) {}
            mPowerProfileUtil.setMode(selectedMode);
        }
    }

    private void updateCpuSubPrefsEnabled(boolean enabled) {
        if (mCpuLittleMinFreqPref != null) mCpuLittleMinFreqPref.setEnabled(enabled);
        if (mCpuLittleMaxFreqPref != null) mCpuLittleMaxFreqPref.setEnabled(enabled);
        if (mCpuLittleGovernorPref != null) mCpuLittleGovernorPref.setEnabled(enabled);
        if (mCpuBigMinFreqPref != null) mCpuBigMinFreqPref.setEnabled(enabled);
        if (mCpuBigMaxFreqPref != null) mCpuBigMaxFreqPref.setEnabled(enabled);
        if (mCpuBigGovernorPref != null) mCpuBigGovernorPref.setEnabled(enabled);
        if (mCpuPrimeMinFreqPref != null) mCpuPrimeMinFreqPref.setEnabled(enabled);
        if (mCpuPrimeMaxFreqPref != null) mCpuPrimeMaxFreqPref.setEnabled(enabled);
        if (mCpuPrimeGovernorPref != null) mCpuPrimeGovernorPref.setEnabled(enabled);
    }

    private boolean isManualActive() {
        return (mCpuEnablePref != null && mCpuEnablePref.isChecked()) ||
               (mGpuEnablePref != null && mGpuEnablePref.isChecked()) ||
               (mStorageEnablePref != null && mStorageEnablePref.isChecked());
    }

    private void showToast(String message) {
        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
    }

    private void updateGovernorLists(int mode) {
        // Always use the full governor/scheduler arrays — restriction is handled in onPreferenceChange
        setListPreferenceData(mCpuLittleGovernorPref, R.array.cpu_governor_entries, R.array.cpu_governor_values);
        setListPreferenceData(mCpuBigGovernorPref, R.array.cpu_governor_entries, R.array.cpu_governor_values);
        setListPreferenceData(mCpuPrimeGovernorPref, R.array.cpu_governor_entries, R.array.cpu_governor_values);
        setListPreferenceData(mGpuGovernorPref, R.array.gpu_governor_entries, R.array.gpu_governor_values);
        setListPreferenceData(mIoSchedulerPref, R.array.io_scheduler_entries, R.array.io_scheduler_values);
    }

    private String getRestrictionMessage(int mode, String value, boolean isCpuGov, boolean isGpuGov, boolean isIoSched) {
        if (mode == PowerProfileUtil.MODE_BATTERY_SAVER) {
            if (isCpuGov && "performance".equals(value)) {
                return getString(R.string.governor_restricted_powersave, "Performance");
            }
            if (isGpuGov && ("performance".equals(value) || "msm-adreno-tz".equals(value))) {
                String label = "msm-adreno-tz".equals(value) ? "MSM Adreno TZ" : "Performance";
                return getString(R.string.governor_restricted_powersave, label);
            }
            if (isIoSched && "kyber".equals(value)) {
                return getString(R.string.governor_restricted_powersave, "Kyber");
            }
        } else if (mode == PowerProfileUtil.MODE_PERFORMANCE) {
            if (isCpuGov && ("conservative".equals(value) || "powersave".equals(value))) {
                String label = "conservative".equals(value) ? "Conservative" : "Powersave";
                return getString(R.string.governor_restricted_performance, label);
            }
            if (isGpuGov && ("userspace".equals(value) || "powersave".equals(value))) {
                String label = "userspace".equals(value) ? "Userspace" : "Powersave";
                return getString(R.string.governor_restricted_performance, label);
            }
            if (isIoSched && "bfq".equals(value)) {
                return getString(R.string.governor_restricted_performance, "BFQ");
            }
        }
        return null;
    }

    private void setListPreferenceData(ListPreference pref, int entriesResId, int valuesResId) {
        if (pref != null) {
            pref.setEntries(entriesResId);
            pref.setEntryValues(valuesResId);
        }
    }
}