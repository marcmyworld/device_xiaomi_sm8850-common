/*
 * Copyright (C) 2023-2024 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.UserHandle
import android.util.Log
import com.xiaomi.settings.display.AdaptiveRefreshRateService
import com.xiaomi.settings.display.AdaptiveRefreshRateSettingsActivity
import com.xiaomi.settings.display.ColorModeService
import com.xiaomi.settings.doze.PickupService
import com.xiaomi.settings.haptics.HapticsService
import com.xiaomi.settings.haptics.HapticsSettingsActivity
import com.xiaomi.settings.haptics.VibratorTunerWrapper
import com.xiaomi.settings.touch.SingleTapService
import com.xiaomi.settings.touch.TouchOrientationService
import com.xiaomi.settings.touch.TouchPollingRateService

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (DEBUG) Log.i(TAG, "Received intent: ${intent.action}")
        if (intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return

        val arrSupported = AdaptiveRefreshRateService.isSupported(context)
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context, AdaptiveRefreshRateSettingsActivity::class.java),
            if (arrSupported) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
        if (arrSupported) {
            context.startServiceAsUser(
                Intent(context, AdaptiveRefreshRateService::class.java),
                UserHandle.CURRENT,
            )
        }
        context.startServiceAsUser(Intent(context, ColorModeService::class.java), UserHandle.CURRENT)
        context.startServiceAsUser(
            Intent(context, TouchOrientationService::class.java),
            UserHandle.CURRENT,
        )
        context.startServiceAsUser(
            Intent(context, TouchPollingRateService::class.java),
            UserHandle.CURRENT,
        )
        context.startServiceAsUser(Intent(context, SingleTapService::class.java), UserHandle.CURRENT)
        context.startServiceAsUser(Intent(context, PickupService::class.java), UserHandle.CURRENT)

        val hapticsSupported = VibratorTunerWrapper.isSupported(context)
        runCatching {
            context.packageManager.setComponentEnabledSetting(
                ComponentName(context, HapticsSettingsActivity::class.java),
                if (hapticsSupported) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }.onFailure { e -> Log.w(TAG, "Failed to update HapticsSettingsActivity state", e) }
        if (hapticsSupported) {
            context.startServiceAsUser(Intent(context, HapticsService::class.java), UserHandle.CURRENT)
        }
    }

    companion object {
        private const val TAG = "XiaomiParts"
        private val DEBUG = Log.isLoggable(TAG, Log.DEBUG)
    }
}
