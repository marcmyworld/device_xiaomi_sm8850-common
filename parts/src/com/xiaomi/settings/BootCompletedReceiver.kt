/*
 * Copyright (C) 2023-2024 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.UserHandle
import android.util.Log
import com.xiaomi.settings.display.ColorModeService
import com.xiaomi.settings.touch.TouchOrientationService
import com.xiaomi.settings.touch.TouchPollingRateService

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (DEBUG) Log.i(TAG, "Received intent: ${intent.action}")
        if (intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return

        context.startServiceAsUser(Intent(context, ColorModeService::class.java), UserHandle.CURRENT)
        context.startServiceAsUser(
            Intent(context, TouchOrientationService::class.java),
            UserHandle.CURRENT,
        )
        context.startServiceAsUser(
            Intent(context, TouchPollingRateService::class.java),
            UserHandle.CURRENT,
        )
    }

    companion object {
        private const val TAG = "XiaomiParts"
        private val DEBUG = Log.isLoggable(TAG, Log.DEBUG)
    }
}
