/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.display

import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import androidx.preference.Preference
import androidx.preference.PreferenceFragment
import androidx.preference.SwitchPreferenceCompat
import com.android.settingslib.widget.MainSwitchPreference
import com.xiaomi.settings.R
import kotlin.math.roundToInt

/** Settings screen for LTPO Adaptive refresh rate Pro and scenario video rate matching. */
class AdaptiveRefreshRateSettingsFragment :
    PreferenceFragment(), Preference.OnPreferenceChangeListener {

    private val observerHandler = Handler(Looper.getMainLooper())
    private val settingsObserver =
        object : ContentObserver(observerHandler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                refresh()
            }
        }

    private var proSwitch: MainSwitchPreference? = null
    private var videoMatchSwitch: SwitchPreferenceCompat? = null
    private var supportedPref: Preference? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.settings_adaptive_refresh_rate)

        proSwitch =
            (findPreference(AdaptiveRefreshRateService.SECURE_KEY_PRO) as? MainSwitchPreference)
                ?.apply {
                    isPersistent = false
                    onPreferenceChangeListener = this@AdaptiveRefreshRateSettingsFragment
                }

        videoMatchSwitch =
            (findPreference(AdaptiveRefreshRateService.SECURE_KEY_VIDEO_MATCH)
                    as? SwitchPreferenceCompat)
                ?.apply {
                    isPersistent = false
                    onPreferenceChangeListener = this@AdaptiveRefreshRateSettingsFragment
                }

        supportedPref = findPreference(KEY_SUPPORTED_RATES)
    }

    override fun onResume() {
        super.onResume()
        val cr = context.contentResolver
        cr.registerContentObserver(
            Settings.Secure.getUriFor(AdaptiveRefreshRateService.SECURE_KEY_PRO),
            false,
            settingsObserver,
            UserHandle.USER_ALL,
        )
        cr.registerContentObserver(
            Settings.Secure.getUriFor(AdaptiveRefreshRateService.SECURE_KEY_VIDEO_MATCH),
            false,
            settingsObserver,
            UserHandle.USER_ALL,
        )
        refresh()
    }

    override fun onPause() {
        context.contentResolver.unregisterContentObserver(settingsObserver)
        super.onPause()
    }

    private fun refresh() {
        val supported = AdaptiveRefreshRateService.isSupported(context)
        val proEnabled =
            supported &&
                Settings.Secure.getIntForUser(
                    context.contentResolver,
                    AdaptiveRefreshRateService.SECURE_KEY_PRO,
                    1,
                    UserHandle.USER_CURRENT,
                ) != 0
        val videoMatchEnabled =
            Settings.Secure.getIntForUser(
                context.contentResolver,
                AdaptiveRefreshRateService.SECURE_KEY_VIDEO_MATCH,
                1,
                UserHandle.USER_CURRENT,
            ) != 0

        proSwitch?.apply {
            isEnabled = supported
            isChecked = proEnabled
        }

        videoMatchSwitch?.apply {
            isEnabled = supported && proEnabled
            isChecked = videoMatchEnabled
        }

        supportedPref?.summary =
            if (supported) {
                val rates =
                    AdaptiveRefreshRateService.getSupportedArrRates(context)
                        .map { it.roundToInt() }
                        .distinct()
                        .sorted()
                val min = rates.firstOrNull() ?: 1
                val max = rates.lastOrNull() ?: 120
                getString(
                    R.string.adaptive_refresh_rate_status_summary,
                    min,
                    max,
                    rates.joinToString(", "),
                )
            } else {
                getString(R.string.adaptive_refresh_rate_unavailable_summary)
            }
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        val enabled = newValue as Boolean
        Settings.Secure.putIntForUser(
            preference.context.contentResolver,
            preference.key,
            if (enabled) 1 else 0,
            UserHandle.USER_CURRENT,
        )
        if (preference.key == AdaptiveRefreshRateService.SECURE_KEY_PRO) {
            videoMatchSwitch?.isEnabled = enabled
        }
        return true
    }

    companion object {
        private const val KEY_SUPPORTED_RATES = "adaptive_refresh_rate_supported"
    }
}
