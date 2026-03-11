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
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PowertoolsSettingsFragment extends PreferenceFragmentCompat
        implements Preference.OnPreferenceChangeListener {

    // --- CONSTANTS ---
    private static final String KEY_AUTO_THERMAL = "auto_thermal_enable";
    private static final String KEY_AUTO_STATUS = "auto_thermal_status";
    private static final String KEY_POWER_PROFILE_MODE = "power_profile_mode";
    private static final String KEY_MODE_STATUS = "mode_status_info";

    private static final String KEY_STORAGE_ENABLE = "storage_enable";
    private static final String KEY_IO_SCHEDULER = PowerProfileUtil.KEY_IO_SCHEDULER;
    private static final String IO_DEFAULT_SCHED = "bfq";

    private static final String KEY_GPU_ENABLE = "gpu_enable";
    private static final String KEY_GPU_MIN_FREQ = PowerProfileUtil.KEY_GPU_MIN_FREQ;
    private static final String KEY_GPU_MAX_FREQ = PowerProfileUtil.KEY_GPU_MAX_FREQ;
    private static final String KEY_GPU_GOVERNOR = PowerProfileUtil.KEY_GPU_GOVERNOR;
    private static final String GPU_DEFAULT_MIN = "315000000";
    private static final String GPU_DEFAULT_MAX = "840000000";
    private static final String GPU_DEFAULT_GOV = "msm-adreno-tz";

    private static final String KEY_CPU_ENABLE = "cpu_enable";
    private static final String KEY_CPU_LITTLE_MIN_FREQ = PowerProfileUtil.KEY_CPU_LITTLE_MIN_FREQ;
    private static final String KEY_CPU_LITTLE_MAX_FREQ = PowerProfileUtil.KEY_CPU_LITTLE_MAX_FREQ;
    private static final String KEY_CPU_LITTLE_GOVERNOR = PowerProfileUtil.KEY_CPU_LITTLE_GOVERNOR;
    private static final String KEY_CPU_BIG_MIN_FREQ = PowerProfileUtil.KEY_CPU_BIG_MIN_FREQ;
    private static final String KEY_CPU_BIG_MAX_FREQ = PowerProfileUtil.KEY_CPU_BIG_MAX_FREQ;
    private static final String KEY_CPU_BIG_GOVERNOR = PowerProfileUtil.KEY_CPU_BIG_GOVERNOR;
    private static final String KEY_CPU_PRIME_MIN_FREQ = PowerProfileUtil.KEY_CPU_PRIME_MIN_FREQ;
    private static final String KEY_CPU_PRIME_MAX_FREQ = PowerProfileUtil.KEY_CPU_PRIME_MAX_FREQ;
    private static final String KEY_CPU_PRIME_GOVERNOR = PowerProfileUtil.KEY_CPU_PRIME_GOVERNOR;

    private static final String CPU_LITTLE_DEFAULT_MIN = "300000";
    private static final String CPU_LITTLE_DEFAULT_MAX = "1804800";
    private static final String CPU_LITTLE_DEFAULT_GOV = "schedutil";
    private static final String CPU_BIG_DEFAULT_MIN = "710400";
    private static final String CPU_BIG_DEFAULT_MAX = "2419200";
    private static final String CPU_BIG_DEFAULT_GOV = "schedutil";
    private static final String CPU_PRIME_DEFAULT_MIN = "844800";
    private static final String CPU_PRIME_DEFAULT_MAX = "2841600";
    private static final String CPU_PRIME_DEFAULT_GOV = "schedutil";

    // --- UI COMPONENTS ---
    private SwitchPreferenceCompat mAutoThermalPref, mStorageEnablePref, mGpuEnablePref, mCpuEnablePref;
    private Preference mAutoStatusPref, mModeStatusPref;
    private ListPreference mPowerProfilePref, mIoSchedulerPref;
    private ListPreference mGpuMinFreqPref, mGpuMaxFreqPref, mGpuGovernorPref;
    private ListPreference mCpuLittleMinFreqPref, mCpuLittleMaxFreqPref, mCpuLittleGovernorPref;
    private ListPreference mCpuBigMinFreqPref, mCpuBigMaxFreqPref, mCpuBigGovernorPref;
    private ListPreference mCpuPrimeMinFreqPref, mCpuPrimeMaxFreqPref, mCpuPrimeGovernorPref;

    // --- STATE ---
    private PowerProfileUtil mPowerProfileUtil;
    private GameModeCoordinator mGameModeCoordinator;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final List<Preference> mAllControlPrefs = new ArrayList<>();
    private boolean mApplying = false;
    
    private final Runnable mThermalUpdater = new Runnable() {
        @Override
        public void run() {
            updateThermalLiveData();
        }
    };

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.powertools_settings, rootKey);
        mPowerProfileUtil = new PowerProfileUtil(requireContext());

        // Bind and setup all preferences efficiently
        mAutoThermalPref = bindPref(KEY_AUTO_THERMAL);
        mAutoStatusPref = findPreference(KEY_AUTO_STATUS);
        
        mPowerProfilePref = bindPref(KEY_POWER_PROFILE_MODE);
        mModeStatusPref = findPreference(KEY_MODE_STATUS);

        mStorageEnablePref = bindPref(KEY_STORAGE_ENABLE);
        mIoSchedulerPref = bindPref(KEY_IO_SCHEDULER);

        mGpuEnablePref = bindPref(KEY_GPU_ENABLE);
        mGpuMinFreqPref = bindPref(KEY_GPU_MIN_FREQ);
        mGpuMaxFreqPref = bindPref(KEY_GPU_MAX_FREQ);
        mGpuGovernorPref = bindPref(KEY_GPU_GOVERNOR);

        mCpuEnablePref = bindPref(KEY_CPU_ENABLE);
        mCpuLittleMinFreqPref = bindPref(KEY_CPU_LITTLE_MIN_FREQ);
        mCpuLittleMaxFreqPref = bindPref(KEY_CPU_LITTLE_MAX_FREQ);
        mCpuLittleGovernorPref = bindPref(KEY_CPU_LITTLE_GOVERNOR);
        mCpuBigMinFreqPref = bindPref(KEY_CPU_BIG_MIN_FREQ);
        mCpuBigMaxFreqPref = bindPref(KEY_CPU_BIG_MAX_FREQ);
        mCpuBigGovernorPref = bindPref(KEY_CPU_BIG_GOVERNOR);
        mCpuPrimeMinFreqPref = bindPref(KEY_CPU_PRIME_MIN_FREQ);
        mCpuPrimeMaxFreqPref = bindPref(KEY_CPU_PRIME_MAX_FREQ);
        mCpuPrimeGovernorPref = bindPref(KEY_CPU_PRIME_GOVERNOR);

        initializeControlGroups();
    }

    @SuppressWarnings("unchecked")
    private <T extends Preference> T bindPref(String key) {
        T pref = findPreference(key);
        if (pref != null) {
            pref.setOnPreferenceChangeListener(this);
        }
        return pref;
    }

    private void initializeControlGroups() {
        String[] controlKeys = {
            KEY_POWER_PROFILE_MODE, "power_profile_category", "power_profile_footer", KEY_MODE_STATUS,
            KEY_STORAGE_ENABLE, "storage_category", KEY_IO_SCHEDULER,
            KEY_GPU_ENABLE, "gpu_freq_category", KEY_GPU_MIN_FREQ, KEY_GPU_MAX_FREQ, KEY_GPU_GOVERNOR,
            KEY_CPU_ENABLE, "cpu_little_category", KEY_CPU_LITTLE_MIN_FREQ, KEY_CPU_LITTLE_MAX_FREQ, KEY_CPU_LITTLE_GOVERNOR,
            "cpu_big_category", KEY_CPU_BIG_MIN_FREQ, KEY_CPU_BIG_MAX_FREQ, KEY_CPU_BIG_GOVERNOR,
            "cpu_prime_category", KEY_CPU_PRIME_MIN_FREQ, KEY_CPU_PRIME_MAX_FREQ, KEY_CPU_PRIME_GOVERNOR
        };

        for (String key : controlKeys) {
            Preference p = findPreference(key);
            if (p != null) mAllControlPrefs.add(p);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mGameModeCoordinator == null) {
            mGameModeCoordinator = new GameModeCoordinator(requireContext(), mPowerProfileUtil);
            mGameModeCoordinator.setListener(new GameModeCoordinator.Listener() {
                @Override
                public void onGameSessionStarted() {
                    mMainHandler.post(() -> lockForApply("Game session active — Performance boosted"));
                }
                @Override
                public void onGameSessionEnded() {
                    mMainHandler.postDelayed(() -> unlockAfterApply("Game ended · Profile restored"), 200);
                }
            });
        }
        mGameModeCoordinator.register();
        syncActiveModeUI();
        refreshUI();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mGameModeCoordinator != null) mGameModeCoordinator.unregister();
        mMainHandler.removeCallbacksAndMessages(null);
    }

    // --- UI UPDATES ---

    private void syncActiveModeUI() {
        if (mPowerProfilePref == null || mPowerProfileUtil == null) return;
        String activeMode = String.valueOf(mPowerProfileUtil.getCurrentMode());
        if (!activeMode.equals(mPowerProfilePref.getValue())) {
            mPowerProfilePref.setValue(activeMode);
        }
    }

    private void refreshUI() {
        boolean autoOn = isChecked(mAutoThermalPref);
        
        setControlsEnabled(mAllControlPrefs, !autoOn);
        if (mAutoThermalPref != null) mAutoThermalPref.setEnabled(true); // Always keep master toggle responsive
        
        if (mAutoStatusPref != null) mAutoStatusPref.setVisible(autoOn);
        if (mPowerProfilePref != null) mPowerProfilePref.setVisible(true);

        if (autoOn) {
            startTempUpdater();
            if (mPowerProfilePref != null) {
                mPowerProfilePref.setEnabled(false);
                mPowerProfilePref.setSummary("Auto");
            }
            updateModeDisplays(PowerProfileUtil.MODE_AUTO, true);
        } else {
            mMainHandler.removeCallbacks(mThermalUpdater);
            refreshModeState();
        }
    }

    private void refreshModeState() {
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        syncAllListPrefsToData(prefs);
        configurePresetModeUI();
    }

    // Manual mode UI loop removed. We rely strictly on configurePresetModeUI now.

    private void configurePresetModeUI() {
        boolean autoOn = isChecked(mAutoThermalPref);
        int mode = getCurrentProfileMode();

        if (mPowerProfilePref != null) {
            mPowerProfilePref.setEnabled(!autoOn);
            mPowerProfilePref.setSummary(autoOn ? "Auto" : mPowerProfilePref.getEntry());
        }

        updateModeDisplays(mode, autoOn);

        boolean cpuEnabled = !autoOn && isChecked(mCpuEnablePref);
        boolean gpuEnabled = !autoOn && isChecked(mGpuEnablePref);
        boolean storageEnabled = !autoOn && isChecked(mStorageEnablePref);

        safeSetEnabled(mCpuEnablePref, !autoOn);
        safeSetEnabled(mGpuEnablePref, !autoOn);
        safeSetEnabled(mStorageEnablePref, !autoOn);

        updateGovernorDropdowns(mode);

        safeSetEnabled(mCpuLittleMinFreqPref, cpuEnabled);
        safeSetEnabled(mCpuLittleMaxFreqPref, cpuEnabled);
        safeSetEnabled(mCpuLittleGovernorPref, cpuEnabled);
        safeSetEnabled(mCpuBigMinFreqPref, cpuEnabled);
        safeSetEnabled(mCpuBigMaxFreqPref, cpuEnabled);
        safeSetEnabled(mCpuBigGovernorPref, cpuEnabled);
        safeSetEnabled(mCpuPrimeMinFreqPref, cpuEnabled);
        safeSetEnabled(mCpuPrimeMaxFreqPref, cpuEnabled);
        safeSetEnabled(mCpuPrimeGovernorPref, cpuEnabled);

        safeSetEnabled(mGpuMinFreqPref, gpuEnabled);
        safeSetEnabled(mGpuMaxFreqPref, gpuEnabled);
        safeSetEnabled(mGpuGovernorPref, gpuEnabled);

        safeSetEnabled(mIoSchedulerPref, storageEnabled);

        if (!cpuEnabled) resetHardwareCategoryToDefaults(KEY_CPU_ENABLE, mode);
        if (!gpuEnabled) resetHardwareCategoryToDefaults(KEY_GPU_ENABLE, mode);
        if (!storageEnabled) resetHardwareCategoryToDefaults(KEY_STORAGE_ENABLE, mode);
    }


    // --- THERMAL MONITORING ---

    private void startTempUpdater() {
        mMainHandler.removeCallbacks(mThermalUpdater);
        mMainHandler.post(mThermalUpdater);
    }

    private void updateThermalLiveData() {
        if (!isChecked(mAutoThermalPref)) return;
        
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
        
        mMainHandler.postDelayed(mThermalUpdater, 2500);
    }

    // --- EVENT ROUTING ---

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();
        String newValStr = newValue.toString();

        switch (key) {
            case KEY_AUTO_THERMAL:
                handleAutoThermalToggle((Boolean) newValue);
                return true;

            case KEY_POWER_PROFILE_MODE:
                handleProfileModeChange(newValStr);
                return true;

            case KEY_STORAGE_ENABLE:
            case KEY_GPU_ENABLE:
            case KEY_CPU_ENABLE:
                handleHardwareToggleChange(key, (Boolean) newValue);
                return true;
                
            default:
                return handleHardwareValueChange(preference, key, newValStr);
        }
    }

    private void handleAutoThermalToggle(boolean enable) {
        if (mApplying) return;
        lockForApply("Applying Auto Thermal...");
        Intent svc = new Intent(requireContext(), ThermalMonitorService.class);
        mPowerProfileUtil.setMode(PowerProfileUtil.MODE_BALANCE);

        if (enable) {
            mPowerProfileUtil.syncUiToMode(PowerProfileUtil.MODE_BALANCE);
            requireContext().startForegroundService(svc);
        } else {
            requireContext().stopService(svc);
            mPowerProfileUtil.syncUiToMode(getCurrentProfileMode());
        }
        mMainHandler.postDelayed(() -> {
            refreshModeState();
            unlockAfterApply(enable ? "Auto Thermal enabled" : "Auto Thermal disabled");
        }, 1500);
    }


    private void handleProfileModeChange(String newValue) {
        if (mApplying) return;
        lockForApply("Applying...");
        int mode = Integer.parseInt(newValue);

        if (mCpuEnablePref != null) mCpuEnablePref.setChecked(false);
        if (mGpuEnablePref != null) mGpuEnablePref.setChecked(false);
        if (mStorageEnablePref != null) mStorageEnablePref.setChecked(false);

        mPowerProfileUtil.setMode(mode);

        if (mPowerProfilePref != null) {
            mPowerProfilePref.setValue(newValue);
            mMainHandler.post(() -> mPowerProfilePref.setSummary(mPowerProfilePref.getEntry()));
        }

        mMainHandler.postDelayed(() -> {
            refreshModeState();
            String label = mPowerProfilePref != null ? mPowerProfilePref.getEntry().toString() : "Mode";
            unlockAfterApply(label + " applied");
        }, 1500);
    }

    private void handleHardwareToggleChange(String key, boolean enabled) {
        int mode = getCurrentProfileMode();
        
        if (!enabled) {
            // Turning OFF a toggle hard-resets that specific sub-category to default mode parameters
            resetHardwareCategoryToDefaults(key, mode);
            pushHardwareSettingsCategory(key);
            showToast("Restored default parameters");
        } else {
            showToast("Freestyle tweaking unlocked");
        }
        
        mMainHandler.postDelayed(this::refreshUI, 150);
    }

    private boolean handleHardwareValueChange(Preference preference, String key, String newValue) {
        String restriction = checkRestrictions(preference, newValue);
        if (restriction != null) {
            showToast(restriction);
            return false;
        }

        applyHardwareSetting(preference, key, newValue);
        updateListPreferenceSafely(preference, newValue);

        // Commit to SharedPreferences explicitly 
        getPreferenceManager().getSharedPreferences().edit().putString(key, newValue).apply();
        return false; // Handled manually
    }

    // --- HARDWARE APPLICATION ---

    private void applyHardwareSetting(Preference preference, String key, String newValue) {
        if (preference == mIoSchedulerPref) {
            StorageUtils.setIoScheduler(newValue);
        } else if (preference == mGpuMinFreqPref) {
            GPUUtils.setGPUMinFrequency(newValue);
        } else if (preference == mGpuMaxFreqPref) {
            GPUUtils.setGPUMaxFrequency(newValue);
        } else if (preference == mGpuGovernorPref) {
            GPUUtils.setGPUGovernor(newValue);
        } else if (isCpuLittlePref(preference)) {
            CPUUtils.setCPULittleFreq(
                resolveVal(key, KEY_CPU_LITTLE_MIN_FREQ, newValue, mCpuLittleMinFreqPref, CPU_LITTLE_DEFAULT_MIN),
                resolveVal(key, KEY_CPU_LITTLE_MAX_FREQ, newValue, mCpuLittleMaxFreqPref, CPU_LITTLE_DEFAULT_MAX),
                resolveVal(key, KEY_CPU_LITTLE_GOVERNOR, newValue, mCpuLittleGovernorPref, CPU_LITTLE_DEFAULT_GOV)
            );
        } else if (isCpuBigPref(preference)) {
            CPUUtils.setCPUBigFreq(
                resolveVal(key, KEY_CPU_BIG_MIN_FREQ, newValue, mCpuBigMinFreqPref, CPU_BIG_DEFAULT_MIN),
                resolveVal(key, KEY_CPU_BIG_MAX_FREQ, newValue, mCpuBigMaxFreqPref, CPU_BIG_DEFAULT_MAX),
                resolveVal(key, KEY_CPU_BIG_GOVERNOR, newValue, mCpuBigGovernorPref, CPU_BIG_DEFAULT_GOV)
            );
        } else if (isCpuPrimePref(preference)) {
            CPUUtils.setCPUPrimeFreq(
                resolveVal(key, KEY_CPU_PRIME_MIN_FREQ, newValue, mCpuPrimeMinFreqPref, CPU_PRIME_DEFAULT_MIN),
                resolveVal(key, KEY_CPU_PRIME_MAX_FREQ, newValue, mCpuPrimeMaxFreqPref, CPU_PRIME_DEFAULT_MAX),
                resolveVal(key, KEY_CPU_PRIME_GOVERNOR, newValue, mCpuPrimeGovernorPref, CPU_PRIME_DEFAULT_GOV)
            );
        }
    }

    private void pushHardwareSettingsCategory(String categoryKey) {
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        
        switch (categoryKey) {
            case KEY_STORAGE_ENABLE:
                StorageUtils.setIoScheduler(prefs.getString(KEY_IO_SCHEDULER, IO_DEFAULT_SCHED));
                break;
            case KEY_GPU_ENABLE:
                GPUUtils.setGPUMinFrequency(prefs.getString(KEY_GPU_MIN_FREQ, GPU_DEFAULT_MIN));
                GPUUtils.setGPUMaxFrequency(prefs.getString(KEY_GPU_MAX_FREQ, GPU_DEFAULT_MAX));
                GPUUtils.setGPUGovernor(prefs.getString(KEY_GPU_GOVERNOR, GPU_DEFAULT_GOV));
                break;
            case KEY_CPU_ENABLE:
                CPUUtils.setCPULittleFreq(
                    prefs.getString(KEY_CPU_LITTLE_MIN_FREQ, CPU_LITTLE_DEFAULT_MIN),
                    prefs.getString(KEY_CPU_LITTLE_MAX_FREQ, CPU_LITTLE_DEFAULT_MAX),
                    prefs.getString(KEY_CPU_LITTLE_GOVERNOR, CPU_LITTLE_DEFAULT_GOV)
                );
                CPUUtils.setCPUBigFreq(
                    prefs.getString(KEY_CPU_BIG_MIN_FREQ, CPU_BIG_DEFAULT_MIN),
                    prefs.getString(KEY_CPU_BIG_MAX_FREQ, CPU_BIG_DEFAULT_MAX),
                    prefs.getString(KEY_CPU_BIG_GOVERNOR, CPU_BIG_DEFAULT_GOV)
                );
                CPUUtils.setCPUPrimeFreq(
                    prefs.getString(KEY_CPU_PRIME_MIN_FREQ, CPU_PRIME_DEFAULT_MIN),
                    prefs.getString(KEY_CPU_PRIME_MAX_FREQ, CPU_PRIME_DEFAULT_MAX),
                    prefs.getString(KEY_CPU_PRIME_GOVERNOR, CPU_PRIME_DEFAULT_GOV)
                );
                break;
        }
    }

    private void resetHardwareCategoryToDefaults(String categoryKey, int targetMode) {
        SharedPreferences.Editor editor = getPreferenceManager().getSharedPreferences().edit();
        List<String> keysToReset = new ArrayList<>();

        switch (categoryKey) {
            case KEY_STORAGE_ENABLE:
                keysToReset.add(KEY_IO_SCHEDULER);
                break;
            case KEY_GPU_ENABLE:
                keysToReset.addAll(Arrays.asList(KEY_GPU_MIN_FREQ, KEY_GPU_MAX_FREQ, KEY_GPU_GOVERNOR));
                break;
            case KEY_CPU_ENABLE:
                keysToReset.addAll(Arrays.asList(
                    KEY_CPU_LITTLE_MIN_FREQ, KEY_CPU_LITTLE_MAX_FREQ, KEY_CPU_LITTLE_GOVERNOR,
                    KEY_CPU_BIG_MIN_FREQ, KEY_CPU_BIG_MAX_FREQ, KEY_CPU_BIG_GOVERNOR,
                    KEY_CPU_PRIME_MIN_FREQ, KEY_CPU_PRIME_MAX_FREQ, KEY_CPU_PRIME_GOVERNOR
                ));
                break;
        }

        for (String baseKey : keysToReset) {
            String defaultVal = mPowerProfileUtil.getStockValueForMode(targetMode, baseKey);
            editor.putString(baseKey, defaultVal);

            Preference p = findPreference(baseKey);
            if (p instanceof ListPreference) {
                ListPreference lp = (ListPreference) p;
                lp.setValue(defaultVal);
                CharSequence entry = lp.getEntry();
                lp.setSummary(entry != null ? entry : defaultVal);
            }
        }
        editor.apply();
    }

    // --- HELPERS & UTILITIES ---

    private void lockForApply(String status) {
        mApplying = true;
        setControlsEnabled(mAllControlPrefs, false);
        if (mModeStatusPref != null) mModeStatusPref.setSummary(status);
    }

    private void unlockAfterApply(String toast) {
        mApplying = false;
        refreshUI();
        showToast(toast);
    }

    private String resolveVal(String targetKey, String matchKey, String newValue, ListPreference pref, String fallback) {
        if (targetKey.equals(matchKey)) return newValue;
        return (pref != null && pref.getValue() != null) ? pref.getValue() : fallback;
    }

    private void updateModeDisplays(int mode, boolean isAuto) {
        if (mModeStatusPref != null) {
            mModeStatusPref.setSummary(isAuto ? "Dynamically throttling based on device temperature" 
                                              : getString(getStatusSummaryForMode(mode)));
        }
        updateModeCard(mode, isAuto);
    }

    private int getStatusSummaryForMode(int mode) {
        switch (mode) {
            case PowerProfileUtil.MODE_PERFORMANCE: return R.string.mode_status_performance;
            case PowerProfileUtil.MODE_BATTERY_SAVER: return R.string.mode_status_battery_saver;
            default: return R.string.mode_status_balanced;
        }
    }

    private void updateModeCard(int mode, boolean isAuto) {
        Preference card = findPreference("mode_card_header");
        if (card == null) return;

        if (isAuto) {
            card.setTitle("Auto Thermal");
            card.setSummary("Adaptive throttling based on temperature \u2022 Manages all CPU/GPU via base Normal mode");
            card.setIcon(R.drawable.ic_thermal_balance); 
            return;
        }

        switch (mode) {
            case PowerProfileUtil.MODE_PERFORMANCE:
                card.setTitle("Performance");
                card.setSummary("Max CPU/GPU \u2022 Kyber I/O \u2022 Background apps cleared");
                card.setIcon(R.drawable.ic_thermal_performance);
                break;
            case PowerProfileUtil.MODE_BATTERY_SAVER:
                card.setTitle("Powersave");
                card.setSummary("Conservative scaling \u2022 BFQ I/O \u2022 Background restricted");
                card.setIcon(R.drawable.ic_thermal_battery_saver);
                break;
            default:
                card.setTitle("Normal");
                card.setSummary("Balanced mode \u2022 Dynamic CPU scaling \u2022 Thermal throttle enabled");
                card.setIcon(R.drawable.ic_thermal_balance);
                break;
        }
    }

    private String checkRestrictions(Preference preference, String value) {
        int mode = getCurrentProfileMode();
        boolean isCpuGov = isCpuGovernorPref(preference);
        boolean isGpuGov = (preference == mGpuGovernorPref);
        boolean isIoSched = (preference == mIoSchedulerPref);

        if (preference instanceof ListPreference && preference.getKey() != null && preference.getKey().contains("_freq")) {
            try {
                long freqVal = Long.parseLong(value);
                if (mode == PowerProfileUtil.MODE_BATTERY_SAVER && preference.getKey().contains("max_freq")) {
                    long defaultMax = Long.parseLong(mPowerProfileUtil.getStockValueForMode(mode, preference.getKey()));
                    if (freqVal > defaultMax) return "Mode restriction: Cannot exceed Powersave max frequency";
                } else if (mode == PowerProfileUtil.MODE_PERFORMANCE && preference.getKey().contains("min_freq")) {
                    long defaultMin = Long.parseLong(mPowerProfileUtil.getStockValueForMode(mode, preference.getKey()));
                    if (freqVal < defaultMin) return "Mode restriction: Cannot go below Performance minimum";
                }
            } catch (NumberFormatException ignored) {}
        }

        if (mode == PowerProfileUtil.MODE_BATTERY_SAVER) {
            if (isCpuGov && "performance".equals(value)) return getString(R.string.governor_restricted_powersave, "Performance");
            if (isGpuGov && ("performance".equals(value) || "msm-adreno-tz".equals(value))) {
                return getString(R.string.governor_restricted_powersave, "msm-adreno-tz".equals(value) ? "MSM Adreno TZ" : "Performance");
            }
            if (isIoSched && "kyber".equals(value)) return getString(R.string.governor_restricted_powersave, "Kyber");
            
        } else if (mode == PowerProfileUtil.MODE_PERFORMANCE) {
            if (isCpuGov && ("conservative".equals(value) || "powersave".equals(value))) {
                return getString(R.string.governor_restricted_performance, "conservative".equals(value) ? "Conservative" : "Powersave");
            }
            if (isGpuGov && ("userspace".equals(value) || "powersave".equals(value))) {
                return getString(R.string.governor_restricted_performance, "userspace".equals(value) ? "Userspace" : "Powersave");
            }
            if (isIoSched && "bfq".equals(value)) return getString(R.string.governor_restricted_performance, "BFQ");
        }
        return null;
    }

    private void syncAllListPrefsToData(SharedPreferences prefs) {
        syncListPrefToData(mIoSchedulerPref, prefs, KEY_IO_SCHEDULER);
        syncListPrefToData(mGpuMinFreqPref, prefs, KEY_GPU_MIN_FREQ);
        syncListPrefToData(mGpuMaxFreqPref, prefs, KEY_GPU_MAX_FREQ);
        syncListPrefToData(mGpuGovernorPref, prefs, KEY_GPU_GOVERNOR);
        syncListPrefToData(mCpuLittleMinFreqPref, prefs, KEY_CPU_LITTLE_MIN_FREQ);
        syncListPrefToData(mCpuLittleMaxFreqPref, prefs, KEY_CPU_LITTLE_MAX_FREQ);
        syncListPrefToData(mCpuLittleGovernorPref, prefs, KEY_CPU_LITTLE_GOVERNOR);
        syncListPrefToData(mCpuBigMinFreqPref, prefs, KEY_CPU_BIG_MIN_FREQ);
        syncListPrefToData(mCpuBigMaxFreqPref, prefs, KEY_CPU_BIG_MAX_FREQ);
        syncListPrefToData(mCpuBigGovernorPref, prefs, KEY_CPU_BIG_GOVERNOR);
        syncListPrefToData(mCpuPrimeMinFreqPref, prefs, KEY_CPU_PRIME_MIN_FREQ);
        syncListPrefToData(mCpuPrimeMaxFreqPref, prefs, KEY_CPU_PRIME_MAX_FREQ);
        syncListPrefToData(mCpuPrimeGovernorPref, prefs, KEY_CPU_PRIME_GOVERNOR);
    }

    private void syncListPrefToData(ListPreference pref, SharedPreferences prefs, String key) {
        if (pref == null) return;
        String val = prefs.getString(key, "");
        if (!val.isEmpty()) {
            pref.setValue(val);
            CharSequence entry = pref.getEntry();
            pref.setSummary(entry != null ? entry : val);
        }
    }

    private void updateListPreferenceSafely(Preference preference, String newValue) {
        if (preference instanceof ListPreference) {
            ListPreference lp = (ListPreference) preference;
            lp.setValue(newValue);
            CharSequence entry = lp.getEntry();
            lp.setSummary(entry != null ? entry : newValue);
            showToast((entry != null ? entry : newValue) + " applied");
        }
    }

    private void updateGovernorDropdowns(int mode) {
        setListPreferenceData(mCpuLittleGovernorPref, R.array.cpu_governor_entries, R.array.cpu_governor_values);
        setListPreferenceData(mCpuBigGovernorPref, R.array.cpu_governor_entries, R.array.cpu_governor_values);
        setListPreferenceData(mCpuPrimeGovernorPref, R.array.cpu_governor_entries, R.array.cpu_governor_values);
        setListPreferenceData(mGpuGovernorPref, R.array.gpu_governor_entries, R.array.gpu_governor_values);
        setListPreferenceData(mIoSchedulerPref, R.array.io_scheduler_entries, R.array.io_scheduler_values);
    }

    private void updateCpuSubPrefsEnabled(boolean enabled) {
        safeSetEnabled(mCpuLittleMinFreqPref, enabled);
        safeSetEnabled(mCpuLittleMaxFreqPref, enabled);
        safeSetEnabled(mCpuLittleGovernorPref, enabled);
        safeSetEnabled(mCpuBigMinFreqPref, enabled);
        safeSetEnabled(mCpuBigMaxFreqPref, enabled);
        safeSetEnabled(mCpuBigGovernorPref, enabled);
        safeSetEnabled(mCpuPrimeMinFreqPref, enabled);
        safeSetEnabled(mCpuPrimeMaxFreqPref, enabled);
        safeSetEnabled(mCpuPrimeGovernorPref, enabled);
    }

    private void setListPreferenceData(ListPreference pref, int entriesResId, int valuesResId) {
        if (pref != null) {
            pref.setEntries(entriesResId);
            pref.setEntryValues(valuesResId);
        }
    }

    private void setControlsEnabled(List<Preference> prefs, boolean enabled) {
        for (Preference p : prefs) safeSetEnabled(p, enabled);
    }

    private void safeSetEnabled(Preference pref, boolean enabled) {
        if (pref != null) pref.setEnabled(enabled);
    }

    private boolean isChecked(SwitchPreferenceCompat pref) {
        return pref != null && pref.isChecked();
    }

    private int getCurrentProfileMode() {
        if (mPowerProfilePref == null) return PowerProfileUtil.MODE_BALANCE;
        try {
            return Integer.parseInt(mPowerProfilePref.getValue());
        } catch (NumberFormatException ignored) {
            return PowerProfileUtil.MODE_BALANCE;
        }
    }

    private boolean isManualActive() {
        return isChecked(mCpuEnablePref) || isChecked(mGpuEnablePref) || isChecked(mStorageEnablePref);
    }

    private boolean isCpuGovernorPref(Preference p) {
        return p == mCpuLittleGovernorPref || p == mCpuBigGovernorPref || p == mCpuPrimeGovernorPref;
    }
    
    private boolean isCpuLittlePref(Preference p) { return p == mCpuLittleMinFreqPref || p == mCpuLittleMaxFreqPref || p == mCpuLittleGovernorPref; }
    private boolean isCpuBigPref(Preference p) { return p == mCpuBigMinFreqPref || p == mCpuBigMaxFreqPref || p == mCpuBigGovernorPref; }
    private boolean isCpuPrimePref(Preference p) { return p == mCpuPrimeMinFreqPref || p == mCpuPrimeMaxFreqPref || p == mCpuPrimeGovernorPref; }

    private void showToast(String message) {
        if (getContext() != null) Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
    }
}