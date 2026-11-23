/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.esim

import android.app.AlertDialog
import android.os.Bundle
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.PreferenceFragment
import androidx.preference.SwitchPreferenceCompat
import com.xiaomi.settings.R
import java.util.concurrent.Executors

class EsimSettingsFragment : PreferenceFragment(), Preference.OnPreferenceChangeListener {

    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var controller: EsimSlotController
    private var switchPreference: SwitchPreferenceCompat? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.settings_esim)
        controller = EsimSlotController(context)
        switchPreference =
            (findPreference(KEY_ESIM_ENABLED) as? SwitchPreferenceCompat)?.apply {
                isPersistent = false
                onPreferenceChangeListener = this@EsimSettingsFragment
            }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }

    private fun refresh() {
        val preference = switchPreference ?: return
        val available = controller.getSwitchableSlot() >= 0
        preference.isEnabled = available
        preference.isChecked = available && controller.isEsimEnabled()
        if (!available) preference.setSummary(R.string.esim_unavailable_summary)
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        val enable = newValue as Boolean
        if (!enable && controller.hasActiveEsimProfile()) {
            AlertDialog.Builder(context)
                .setTitle(R.string.esim_in_use_title)
                .setMessage(R.string.esim_in_use_message)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return false
        }
        preference.isEnabled = false
        val appContext = context.applicationContext
        executor.execute {
            val ok = controller.setEsimEnabled(enable)
            activity?.runOnUiThread {
                if (!ok) {
                    Toast.makeText(appContext, R.string.esim_switch_failed, Toast.LENGTH_LONG)
                        .show()
                }
                refresh()
            }
        }
        return true
    }

    companion object {
        private const val KEY_ESIM_ENABLED = "esim_enabled"
    }
}
