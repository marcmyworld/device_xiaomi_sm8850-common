/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.haptics

import android.os.Bundle
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity
import com.android.settingslib.collapsingtoolbar.R

class HapticsSettingsActivity : CollapsingToolbarBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (fragmentManager.findFragmentById(R.id.content_frame) == null) {
            fragmentManager
                .beginTransaction()
                .add(R.id.content_frame, HapticsSettingsFragment(), TAG)
                .commit()
        }
    }

    companion object {
        private const val TAG = "haptics"
    }
}
