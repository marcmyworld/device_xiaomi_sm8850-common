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
 * The switches are stored in Settings.Secure (not in the shared preferences), under their
 * preference keys, where TouchPollingRateService and SingleTapService read them.
 */
class TouchSettingsFragment : PreferenceFragment(), Preference.OnPreferenceChangeListener {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.settings_touch)
        for (key in SECURE_KEYS) {
            (findPreference(key) as? SwitchPreferenceCompat)?.apply {
                isPersistent = false
                isChecked = Settings.Secure.getInt(context.contentResolver, key, 0) != 0
                onPreferenceChangeListener = this@TouchSettingsFragment
            }
        }
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        Settings.Secure.putInt(
            preference.context.contentResolver,
            preference.key,
            if (newValue as Boolean) 1 else 0,
        )
        return true
    }

    companion object {
        private val SECURE_KEYS =
            listOf(
                TouchPollingRateService.SECURE_KEY_POLLING,
                SingleTapService.SECURE_KEY_SINGLE_TAP,
            )
    }
}
