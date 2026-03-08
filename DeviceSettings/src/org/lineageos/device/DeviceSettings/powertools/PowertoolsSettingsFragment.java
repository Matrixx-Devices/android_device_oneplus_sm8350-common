/*
 * Copyright (C) 2025 kenrow214
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.device.DeviceSettings.powertools;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import org.lineageos.device.DeviceSettings.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    private static final String KEY_IO_SCHEDULER = "io_scheduler";
    private SwitchPreferenceCompat mStorageEnablePref;
    private ListPreference mIoSchedulerPref;
    private static final String IO_DEFAULT_SCHED = "bfq";

    // GPU
    private static final String KEY_GPU_ENABLE = "gpu_enable";
    private static final String KEY_GPU_MIN_FREQ = "gpu_min_frequency";
    private static final String KEY_GPU_MAX_FREQ = "gpu_max_frequency";
    private static final String KEY_GPU_GOVERNOR = "gpu_governor";
    private SwitchPreferenceCompat mGpuEnablePref;
    private ListPreference mGpuMinFreqPref, mGpuMaxFreqPref, mGpuGovernorPref;
    private static final String GPU_DEFAULT_MIN = "315000000";
    private static final String GPU_DEFAULT_MAX = "840000000";
    private static final String GPU_DEFAULT_GOV = "userspace";

    // CPU
    private static final String KEY_CPU_ENABLE = "cpu_enable";
    private static final String KEY_CPU_LITTLE_MIN_FREQ = "cpu_little_min_frequency";
    private static final String KEY_CPU_LITTLE_MAX_FREQ = "cpu_little_max_frequency";
    private static final String KEY_CPU_LITTLE_GOVERNOR = "cpu_little_governor";
    private static final String KEY_CPU_BIG_MIN_FREQ = "cpu_big_min_frequency";
    private static final String KEY_CPU_BIG_MAX_FREQ = "cpu_big_max_frequency";
    private static final String KEY_CPU_BIG_GOVERNOR = "cpu_big_governor";
    private static final String KEY_CPU_PRIME_MIN_FREQ = "cpu_prime_min_frequency";
    private static final String KEY_CPU_PRIME_MAX_FREQ = "cpu_prime_max_frequency";
    private static final String KEY_CPU_PRIME_GOVERNOR = "cpu_prime_governor";
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

    // Per-mode persistence keys
    private static final String KEY_CPU_LITTLE_GOV_POWERSAVE = "cpu_little_gov_powersave";
    private static final String KEY_CPU_BIG_GOV_POWERSAVE = "cpu_big_gov_powersave";
    private static final String KEY_CPU_PRIME_GOV_POWERSAVE = "cpu_prime_gov_powersave";
    private static final String KEY_GPU_GOV_POWERSAVE = "gpu_gov_powersave";
    private static final String KEY_IO_SCHED_POWERSAVE = "io_sched_powersave";
    
    private static final String KEY_CPU_LITTLE_GOV_BALANCE = "cpu_little_gov_balance";
    private static final String KEY_CPU_BIG_GOV_BALANCE = "cpu_big_gov_balance";
    private static final String KEY_CPU_PRIME_GOV_BALANCE = "cpu_prime_gov_balance";
    private static final String KEY_GPU_GOV_BALANCE = "gpu_gov_balance";
    private static final String KEY_IO_SCHED_BALANCE = "io_sched_balance";
    
    private static final String KEY_CPU_LITTLE_GOV_PERFORMANCE = "cpu_little_gov_performance";
    private static final String KEY_CPU_BIG_GOV_PERFORMANCE = "cpu_big_gov_performance";
    private static final String KEY_CPU_PRIME_GOV_PERFORMANCE = "cpu_prime_gov_performance";
    private static final String KEY_GPU_GOV_PERFORMANCE = "gpu_gov_performance";
    private static final String KEY_IO_SCHED_PERFORMANCE = "io_sched_performance";

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

        mGpuEnablePref = findPreference(KEY_GPU_ENABLE);
        mGpuMinFreqPref = findPreference(KEY_GPU_MIN_FREQ);
        mGpuMaxFreqPref = findPreference(KEY_GPU_MAX_FREQ);
        mGpuGovernorPref = findPreference(KEY_GPU_GOVERNOR);
        if (mGpuEnablePref != null) mGpuEnablePref.setOnPreferenceChangeListener(this);
        if (mGpuMinFreqPref != null) mGpuMinFreqPref.setOnPreferenceChangeListener(this);
        if (mGpuMaxFreqPref != null) mGpuMaxFreqPref.setOnPreferenceChangeListener(this);
        if (mGpuGovernorPref != null) mGpuGovernorPref.setOnPreferenceChangeListener(this);

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

        addIfFound(mCpuGpuPrefs, KEY_STORAGE_ENABLE, "storage_category", KEY_IO_SCHEDULER,
                KEY_GPU_ENABLE, "gpu_freq_category", KEY_GPU_MIN_FREQ, KEY_GPU_MAX_FREQ, KEY_GPU_GOVERNOR,
                KEY_CPU_ENABLE, "cpu_little_category", KEY_CPU_LITTLE_MIN_FREQ,
                KEY_CPU_LITTLE_MAX_FREQ, KEY_CPU_LITTLE_GOVERNOR,
                "cpu_big_category", KEY_CPU_BIG_MIN_FREQ, KEY_CPU_BIG_MAX_FREQ,
                KEY_CPU_BIG_GOVERNOR, "cpu_prime_category", KEY_CPU_PRIME_MIN_FREQ,
                KEY_CPU_PRIME_MAX_FREQ, KEY_CPU_PRIME_GOVERNOR);
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

    // --- END APP KILLER LOGIC ---

    @Override
    public void onResume() {
        super.onResume();
        if (mPowerProfilePref != null && mPowerProfileUtil != null) {
            String activeMode = String.valueOf(mPowerProfileUtil.getCurrentMode());
            if (!activeMode.equals(mPowerProfilePref.getValue())) {
                mPowerProfilePref.setValue(activeMode);
            }
        }
        refreshFrequencyDisplays();
        refreshGovernorDisplays();
        
        if (!isResetOnBootEnabled()) {
            int currentMode = mPowerProfileUtil != null ? mPowerProfileUtil.getCurrentMode() : PowerProfileUtil.MODE_BALANCE;
            getMainHandler().postDelayed(() -> applyGovernorsForMode(currentMode), 3000);
        }
        
        refreshUI();
    }

    private boolean isResetOnBootEnabled() {
        SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext());
        return prefs.getBoolean(KEY_RESET_ON_BOOT, false);
    }

    @Override
    public void onPause() {
        super.onPause();
        getMainHandler().removeCallbacks(null);
    }

    private boolean isManualActive() {
        boolean cpu = (mCpuEnablePref != null && mCpuEnablePref.isChecked());
        boolean gpu = (mGpuEnablePref != null && mGpuEnablePref.isChecked());
        boolean storage = (mStorageEnablePref != null && mStorageEnablePref.isChecked());
        return cpu || gpu || storage;
    }

    private void showToast(String message) {
        if (getContext() != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
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
        if (manual) {
            if (mPowerProfilePref != null) {
                mPowerProfilePref.setSummary(getString(R.string.powerprofile_mode_manual));
                mPowerProfilePref.setEnabled(false);
            }
            updateModeStatus(PowerProfileUtil.MODE_MANUAL, false);
            for (Preference p : mCpuGpuPrefs) p.setEnabled(true);
            
            boolean storageOn = (mStorageEnablePref != null && mStorageEnablePref.isChecked());
            if (mIoSchedulerPref != null) mIoSchedulerPref.setEnabled(storageOn);
            
            boolean gpuOn = (mGpuEnablePref != null && mGpuEnablePref.isChecked());
            if (mGpuMinFreqPref != null) mGpuMinFreqPref.setEnabled(gpuOn);
            if (mGpuMaxFreqPref != null) mGpuMaxFreqPref.setEnabled(gpuOn);
            if (mGpuGovernorPref != null) mGpuGovernorPref.setEnabled(gpuOn);
            
            boolean cpuOn = (mCpuEnablePref != null && mCpuEnablePref.isChecked());
            updateCpuSubPrefsEnabled(cpuOn);
        } else {
            boolean autoOn = (mAutoThermalPref != null && mAutoThermalPref.isChecked());
            int mode = 0;
            String val = (mPowerProfilePref != null) ? mPowerProfilePref.getValue() : "0";
            if (val == null) val = "0";
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
            
            if (mStorageEnablePref != null) mStorageEnablePref.setEnabled(true);
            if (mGpuEnablePref != null) mGpuEnablePref.setEnabled(true);
            if (mCpuEnablePref != null) mCpuEnablePref.setEnabled(true);
            
            if (mIoSchedulerPref != null) mIoSchedulerPref.setEnabled(true);
            if (mGpuGovernorPref != null) mGpuGovernorPref.setEnabled(true);
            if (mCpuLittleGovernorPref != null) mCpuLittleGovernorPref.setEnabled(true);
            if (mCpuBigGovernorPref != null) mCpuBigGovernorPref.setEnabled(true);
            if (mCpuPrimeGovernorPref != null) mCpuPrimeGovernorPref.setEnabled(true);
            
            if (mGpuMinFreqPref != null) mGpuMinFreqPref.setEnabled(false);
            if (mGpuMaxFreqPref != null) mGpuMaxFreqPref.setEnabled(false);
            if (mCpuLittleMinFreqPref != null) mCpuLittleMinFreqPref.setEnabled(false);
            if (mCpuLittleMaxFreqPref != null) mCpuLittleMaxFreqPref.setEnabled(false);
            if (mCpuBigMinFreqPref != null) mCpuBigMinFreqPref.setEnabled(false);
            if (mCpuBigMaxFreqPref != null) mCpuBigMaxFreqPref.setEnabled(false);
            if (mCpuPrimeMinFreqPref != null) mCpuPrimeMinFreqPref.setEnabled(false);
            if (mCpuPrimeMaxFreqPref != null) mCpuPrimeMaxFreqPref.setEnabled(false);
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

    private void checkPresetModeFallback() {
        if ((mCpuEnablePref == null || !mCpuEnablePref.isChecked()) && 
            (mGpuEnablePref == null || !mGpuEnablePref.isChecked()) &&
            (mStorageEnablePref == null || !mStorageEnablePref.isChecked())) {
            int selectedMode = 0;
            try { selectedMode = Integer.parseInt(mPowerProfilePref.getValue() != null ? mPowerProfilePref.getValue() : "0"); } 
            catch (NumberFormatException ignored) {}
            mPowerProfileUtil.setMode(selectedMode);
            getMainHandler().post(() -> {
                refreshFrequencyDisplays();
                refreshGovernorDisplays();
            });
        }
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
                showToast("Auto Thermal disabled");
            }
            getMainHandler().postDelayed(this::refreshUI, 150);
            return true;
        }

        if (KEY_POWER_PROFILE_MODE.equals(key)) {
            int mode = Integer.parseInt((String) newValue);
            boolean success = mPowerProfileUtil.setMode(mode);
            if (mPowerProfilePref != null) {
                mPowerProfilePref.setValue((String) newValue);
                getMainHandler().post(() -> {
                    mPowerProfilePref.setSummary(mPowerProfilePref.getEntry());
                    refreshFrequencyDisplays();
                    refreshGovernorDisplays();
                    applyGovernorsForMode(mode);
                    showToast(mPowerProfilePref.getEntry() + " mode applied");
                });
            }
            updateModeStatus(mode, false);
            return true;
        }

        if (KEY_STORAGE_ENABLE.equals(key)) {
            if (!(Boolean) newValue) {
                StorageUtils.setIoScheduler(IO_DEFAULT_SCHED);
                showToast("Restoring Storage defaults...");
                checkPresetModeFallback();
            } else {
                mPowerProfileUtil.setMode(PowerProfileUtil.MODE_MANUAL);
                showToast("Manual Storage control enabled");
            }
            getMainHandler().postDelayed(this::refreshModeState, 100);
            return true;
        }

        if (KEY_RESET_ON_BOOT.equals(key)) {
            return true;
        }

        if (preference == mIoSchedulerPref) {
            int currentMode = mPowerProfileUtil.getCurrentMode();
            if (!isValidIoSchedulerForMode(newValue.toString(), currentMode)) {
                showToast(newValue.toString() + " scheduler is not allowed in this mode");
                getMainHandler().postDelayed(() -> mIoSchedulerPref.setValue(mIoSchedulerPref.getValue()), 100);
                return false;
            }
            StorageUtils.setIoScheduler(newValue.toString());
            saveGovernorsForMode(currentMode, mCpuLittleGovernorPref.getValue(), mCpuBigGovernorPref.getValue(),
                mCpuPrimeGovernorPref.getValue(), mGpuGovernorPref.getValue(), newValue.toString());
            showToast("I/O Scheduler applied");
            return true;
        }

        if (KEY_GPU_ENABLE.equals(key)) {
            if (!(Boolean) newValue) {
                GPUUtils.setGPUMinFrequency(GPU_DEFAULT_MIN);
                GPUUtils.setGPUMaxFrequency(GPU_DEFAULT_MAX);
                showToast("Restoring GPU defaults...");
                checkPresetModeFallback();
            } else {
                mPowerProfileUtil.setMode(PowerProfileUtil.MODE_MANUAL);
                showToast("Manual GPU control enabled");
            }
            getMainHandler().postDelayed(this::refreshModeState, 100);
            return true;
        }

        if (KEY_CPU_ENABLE.equals(key)) {
            if (!(Boolean) newValue) {
                CPUUtils.setCPULittleFreq(CPU_LITTLE_DEFAULT_MIN, CPU_LITTLE_DEFAULT_MAX, CPU_LITTLE_DEFAULT_GOV);
                CPUUtils.setCPUBigFreq(CPU_BIG_DEFAULT_MIN, CPU_BIG_DEFAULT_MAX, CPU_BIG_DEFAULT_GOV);
                CPUUtils.setCPUPrimeFreq(CPU_PRIME_DEFAULT_MIN, CPU_PRIME_DEFAULT_MAX, CPU_PRIME_DEFAULT_GOV);
                showToast("Restoring CPU defaults...");
                checkPresetModeFallback();
            } else {
                mPowerProfileUtil.setMode(PowerProfileUtil.MODE_MANUAL);
                showToast("Manual CPU control enabled");
            }
            getMainHandler().postDelayed(this::refreshModeState, 100);
            return true;
        }

        if (preference == mGpuMinFreqPref) { GPUUtils.setGPUMinFrequency(newValue.toString()); return true; }
        if (preference == mGpuMaxFreqPref) { GPUUtils.setGPUMaxFrequency(newValue.toString()); return true; }

        if (preference == mGpuGovernorPref) {
            int currentMode = mPowerProfileUtil.getCurrentMode();
            if (!isValidGpuGovernorForMode(newValue.toString(), currentMode)) {
                showToast(newValue.toString() + " is restricted here");
                getMainHandler().postDelayed(() -> mGpuGovernorPref.setValue(mGpuGovernorPref.getValue()), 100);
                return false;
            }
            GPUUtils.setGPUGovernor(newValue.toString());
            saveGovernorsForMode(currentMode, mCpuLittleGovernorPref.getValue(), mCpuBigGovernorPref.getValue(),
                mCpuPrimeGovernorPref.getValue(), newValue.toString(), mIoSchedulerPref.getValue());
            return true;
        }

        if (preference == mCpuLittleMinFreqPref) { CPUUtils.setCPULittleFreq(newValue.toString(), mCpuLittleMaxFreqPref.getValue(), mCpuLittleGovernorPref.getValue()); return true; }
        if (preference == mCpuLittleMaxFreqPref) { CPUUtils.setCPULittleFreq(mCpuLittleMinFreqPref.getValue(), newValue.toString(), mCpuLittleGovernorPref.getValue()); return true; }
        if (preference == mCpuLittleGovernorPref) {
            int currentMode = mPowerProfileUtil.getCurrentMode();
            if (!isValidCpuGovernorForMode(newValue.toString(), currentMode)) {
                getMainHandler().postDelayed(() -> mCpuLittleGovernorPref.setValue(mCpuLittleGovernorPref.getValue()), 100);
                return false;
            }
            CPUUtils.setCPULittleFreq(mCpuLittleMinFreqPref.getValue(), mCpuLittleMaxFreqPref.getValue(), newValue.toString());
            saveGovernorsForMode(currentMode, newValue.toString(), mCpuBigGovernorPref.getValue(), mCpuPrimeGovernorPref.getValue(), mGpuGovernorPref.getValue(), mIoSchedulerPref.getValue());
            return true;
        }

        if (preference == mCpuBigMinFreqPref) { CPUUtils.setCPUBigFreq(newValue.toString(), mCpuBigMaxFreqPref.getValue(), mCpuBigGovernorPref.getValue()); return true; }
        if (preference == mCpuBigMaxFreqPref) { CPUUtils.setCPUBigFreq(mCpuBigMinFreqPref.getValue(), newValue.toString(), mCpuBigGovernorPref.getValue()); return true; }
        if (preference == mCpuBigGovernorPref) {
            int currentMode = mPowerProfileUtil.getCurrentMode();
            if (!isValidCpuGovernorForMode(newValue.toString(), currentMode)) {
                getMainHandler().postDelayed(() -> mCpuBigGovernorPref.setValue(mCpuBigGovernorPref.getValue()), 100);
                return false;
            }
            CPUUtils.setCPUBigFreq(mCpuBigMinFreqPref.getValue(), mCpuBigMaxFreqPref.getValue(), newValue.toString());
            saveGovernorsForMode(currentMode, mCpuLittleGovernorPref.getValue(), newValue.toString(), mCpuPrimeGovernorPref.getValue(), mGpuGovernorPref.getValue(), mIoSchedulerPref.getValue());
            return true;
        }

        if (preference == mCpuPrimeMinFreqPref) { CPUUtils.setCPUPrimeFreq(newValue.toString(), mCpuPrimeMaxFreqPref.getValue(), mCpuPrimeGovernorPref.getValue()); return true; }
        if (preference == mCpuPrimeMaxFreqPref) { CPUUtils.setCPUPrimeFreq(mCpuPrimeMinFreqPref.getValue(), newValue.toString(), mCpuPrimeGovernorPref.getValue()); return true; }
        if (preference == mCpuPrimeGovernorPref) {
            int currentMode = mPowerProfileUtil.getCurrentMode();
            if (!isValidCpuGovernorForMode(newValue.toString(), currentMode)) {
                getMainHandler().postDelayed(() -> mCpuPrimeGovernorPref.setValue(mCpuPrimeGovernorPref.getValue()), 100);
                return false;
            }
            CPUUtils.setCPUPrimeFreq(mCpuPrimeMinFreqPref.getValue(), mCpuPrimeMaxFreqPref.getValue(), newValue.toString());
            saveGovernorsForMode(currentMode, mCpuLittleGovernorPref.getValue(), mCpuBigGovernorPref.getValue(), newValue.toString(), mGpuGovernorPref.getValue(), mIoSchedulerPref.getValue());
            return true;
        }

        return false;
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

    private void saveGovernorsForMode(int mode, String cpuLittleGov, String cpuBigGov, String cpuPrimeGov, String gpuGov, String ioSched) {
        SharedPreferences.Editor editor = androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext()).edit();
        switch (mode) {
            case PowerProfileUtil.MODE_BATTERY_SAVER:
                editor.putString(KEY_CPU_LITTLE_GOV_POWERSAVE, cpuLittleGov);
                editor.putString(KEY_CPU_BIG_GOV_POWERSAVE, cpuBigGov);
                editor.putString(KEY_CPU_PRIME_GOV_POWERSAVE, cpuPrimeGov);
                editor.putString(KEY_GPU_GOV_POWERSAVE, gpuGov);
                editor.putString(KEY_IO_SCHED_POWERSAVE, ioSched);
                break;
            case PowerProfileUtil.MODE_BALANCE:
                editor.putString(KEY_CPU_LITTLE_GOV_BALANCE, cpuLittleGov);
                editor.putString(KEY_CPU_BIG_GOV_BALANCE, cpuBigGov);
                editor.putString(KEY_CPU_PRIME_GOV_BALANCE, cpuPrimeGov);
                editor.putString(KEY_GPU_GOV_BALANCE, gpuGov);
                editor.putString(KEY_IO_SCHED_BALANCE, ioSched);
                break;
            case PowerProfileUtil.MODE_PERFORMANCE:
                editor.putString(KEY_CPU_LITTLE_GOV_PERFORMANCE, cpuLittleGov);
                editor.putString(KEY_CPU_BIG_GOV_PERFORMANCE, cpuBigGov);
                editor.putString(KEY_CPU_PRIME_GOV_PERFORMANCE, cpuPrimeGov);
                editor.putString(KEY_GPU_GOV_PERFORMANCE, gpuGov);
                editor.putString(KEY_IO_SCHED_PERFORMANCE, ioSched);
                break;
        }
        editor.apply();
    }

    private String[] getGovernorsForMode(int mode) {
        SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext());
        String cpuLittleGov, cpuBigGov, cpuPrimeGov, gpuGov, ioSched;

        switch (mode) {
            case PowerProfileUtil.MODE_BATTERY_SAVER:
                cpuLittleGov = prefs.getString(KEY_CPU_LITTLE_GOV_POWERSAVE, "schedutil");
                cpuBigGov = prefs.getString(KEY_CPU_BIG_GOV_POWERSAVE, "schedutil");
                cpuPrimeGov = prefs.getString(KEY_CPU_PRIME_GOV_POWERSAVE, "schedutil");
                gpuGov = prefs.getString(KEY_GPU_GOV_POWERSAVE, "powersave");
                ioSched = prefs.getString(KEY_IO_SCHED_POWERSAVE, "bfq");
                break;
            case PowerProfileUtil.MODE_PERFORMANCE:
                cpuLittleGov = prefs.getString(KEY_CPU_LITTLE_GOV_PERFORMANCE, "performance");
                cpuBigGov = prefs.getString(KEY_CPU_BIG_GOV_PERFORMANCE, "performance");
                cpuPrimeGov = prefs.getString(KEY_CPU_PRIME_GOV_PERFORMANCE, "performance");
                gpuGov = prefs.getString(KEY_GPU_GOV_PERFORMANCE, "msm-adreno-tz");
                ioSched = prefs.getString(KEY_IO_SCHED_PERFORMANCE, "kyber");
                break;
            case PowerProfileUtil.MODE_BALANCE:
            default:
                cpuLittleGov = prefs.getString(KEY_CPU_LITTLE_GOV_BALANCE, "schedutil");
                cpuBigGov = prefs.getString(KEY_CPU_BIG_GOV_BALANCE, "schedutil");
                cpuPrimeGov = prefs.getString(KEY_CPU_PRIME_GOV_BALANCE, "schedutil");
                gpuGov = prefs.getString(KEY_GPU_GOV_BALANCE, "msm-adreno-tz");
                ioSched = prefs.getString(KEY_IO_SCHED_BALANCE, "bfq");
                break;
        }
        return new String[]{cpuLittleGov, cpuBigGov, cpuPrimeGov, gpuGov, ioSched};
    }

    private void refreshGovernorDisplays() {
        int currentMode = mPowerProfileUtil != null ? mPowerProfileUtil.getCurrentMode() : PowerProfileUtil.MODE_BALANCE;
        String[] governors = getGovernorsForMode(currentMode);

        if (mCpuLittleGovernorPref != null) mCpuLittleGovernorPref.setValue(governors[0]);
        if (mCpuBigGovernorPref != null) mCpuBigGovernorPref.setValue(governors[1]);
        if (mCpuPrimeGovernorPref != null) mCpuPrimeGovernorPref.setValue(governors[2]);
        if (mGpuGovernorPref != null) mGpuGovernorPref.setValue(governors[3]);
        if (mIoSchedulerPref != null) mIoSchedulerPref.setValue(governors[4]);
    }

    private void applyGovernorsForMode(int mode) {
        String[] governors = getGovernorsForMode(mode);
        if (governors.length < 5) return;

        CPUUtils.setCPULittleFreq(mCpuLittleMinFreqPref != null ? mCpuLittleMinFreqPref.getValue() : CPU_LITTLE_DEFAULT_MIN, mCpuLittleMaxFreqPref != null ? mCpuLittleMaxFreqPref.getValue() : CPU_LITTLE_DEFAULT_MAX, governors[0]);
        CPUUtils.setCPUBigFreq(mCpuBigMinFreqPref != null ? mCpuBigMinFreqPref.getValue() : CPU_BIG_DEFAULT_MIN, mCpuBigMaxFreqPref != null ? mCpuBigMaxFreqPref.getValue() : CPU_BIG_DEFAULT_MAX, governors[1]);
        CPUUtils.setCPUPrimeFreq(mCpuPrimeMinFreqPref != null ? mCpuPrimeMinFreqPref.getValue() : CPU_PRIME_DEFAULT_MIN, mCpuPrimeMaxFreqPref != null ? mCpuPrimeMaxFreqPref.getValue() : CPU_PRIME_DEFAULT_MAX, governors[2]);
        GPUUtils.setGPUGovernor(governors[3]);
        StorageUtils.setIoScheduler(governors[4]);
    }

    private void refreshFrequencyDisplays() {
        int currentMode = mPowerProfileUtil != null ? mPowerProfileUtil.getCurrentMode() : PowerProfileUtil.MODE_BALANCE;
        String cpuLittleMin = "300000", cpuLittleMax = "1804800", cpuBigMin = "710400", cpuBigMax = "2419200", cpuPrimeMin = "844800", cpuPrimeMax = "2841600", gpuMin = "315000000", gpuMax = "840000000";

        if (currentMode == PowerProfileUtil.MODE_BATTERY_SAVER) {
            cpuBigMax = "2212000"; cpuPrimeMax = "2592000"; gpuMax = "579000000";
        } else if (currentMode == PowerProfileUtil.MODE_PERFORMANCE) {
            cpuBigMin = "844800"; cpuPrimeMin = "917100"; gpuMin = "676000000";
        }

        if (mCpuLittleMinFreqPref != null) mCpuLittleMinFreqPref.setValue(cpuLittleMin);
        if (mCpuLittleMaxFreqPref != null) mCpuLittleMaxFreqPref.setValue(cpuLittleMax);
        if (mCpuBigMinFreqPref != null) mCpuBigMinFreqPref.setValue(cpuBigMin);
        if (mCpuBigMaxFreqPref != null) mCpuBigMaxFreqPref.setValue(cpuBigMax);
        if (mCpuPrimeMinFreqPref != null) mCpuPrimeMinFreqPref.setValue(cpuPrimeMin);
        if (mCpuPrimeMaxFreqPref != null) mCpuPrimeMaxFreqPref.setValue(cpuPrimeMax);
        if (mGpuMinFreqPref != null) mGpuMinFreqPref.setValue(gpuMin);
        if (mGpuMaxFreqPref != null) mGpuMaxFreqPref.setValue(gpuMax);
    }

    private boolean isValidCpuGovernorForMode(String governor, int mode) {
        int arrayId = (mode == PowerProfileUtil.MODE_PERFORMANCE) ? R.array.cpu_governor_performance_values 
                    : (mode == PowerProfileUtil.MODE_BATTERY_SAVER) ? R.array.cpu_governor_powersave_values 
                    : R.array.cpu_governor_values;
        for (String valid : getResources().getStringArray(arrayId)) { if (valid.equals(governor)) return true; }
        return false;
    }

    private boolean isValidGpuGovernorForMode(String governor, int mode) {
        int arrayId = (mode == PowerProfileUtil.MODE_PERFORMANCE) ? R.array.gpu_governor_performance_values 
                    : (mode == PowerProfileUtil.MODE_BATTERY_SAVER) ? R.array.gpu_governor_powersave_values 
                    : R.array.gpu_governor_values;
        for (String valid : getResources().getStringArray(arrayId)) { if (valid.equals(governor)) return true; }
        return false;
    }

    private boolean isValidIoSchedulerForMode(String scheduler, int mode) {
        int arrayId = R.array.io_scheduler_values;
        for (String valid : getResources().getStringArray(arrayId)) { if (valid.equals(scheduler)) return true; }
        return false;
    }
}