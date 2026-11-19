/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.touch

import android.os.Bundle
import android.provider.Settings
import androidx.preference.Preference
import androidx.preference.PreferenceFragment
import androidx.preference.SwitchPreferenceCompat
import com.xiaomi.settings.R

/**
 * The switch is stored in Settings.Secure (not in the shared preferences), where
 * TouchPollingRateService reads it.
 */
class TouchSettingsFragment : PreferenceFragment(), Preference.OnPreferenceChangeListener {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.settings_touch)
        (findPreference(TouchPollingRateService.SECURE_KEY_POLLING) as? SwitchPreferenceCompat)
            ?.apply {
                isPersistent = false
                isChecked =
                    Settings.Secure.getInt(
                        context.contentResolver,
                        TouchPollingRateService.SECURE_KEY_POLLING,
                        0,
                    ) != 0
                onPreferenceChangeListener = this@TouchSettingsFragment
            }
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        Settings.Secure.putInt(
            preference.context.contentResolver,
            TouchPollingRateService.SECURE_KEY_POLLING,
            if (newValue as Boolean) 1 else 0,
        )
        return true
    }
}
