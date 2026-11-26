/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.haptics

import android.app.Service
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.UserHandle
import android.provider.Settings
import android.util.Log

/** Syncs persisted haptic calibration settings to vibratorfeature-wrapper's IVibratorTuner. */
class HapticsService : Service() {

    private lateinit var workerThread: HandlerThread
    private lateinit var workerHandler: Handler

    private val settingsObserver by lazy {
        object : ContentObserver(workerHandler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                syncConfig(waitForDeclared = false)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (!VibratorTunerWrapper.isSupported(this)) {
            Log.i(TAG, "IVibratorTuner not supported on this device, stopping")
            stopSelf()
            return
        }
        workerThread = HandlerThread("HapticsTunerSync").apply { start() }
        workerHandler = Handler(workerThread.looper)

        for (key in VibratorTunerWrapper.ALL_SECURE_KEYS) {
            contentResolver.registerContentObserver(
                Settings.Secure.getUriFor(key),
                false,
                settingsObserver,
                UserHandle.USER_ALL,
            )
        }
        workerHandler.post { syncConfig(waitForDeclared = true) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        if (::workerThread.isInitialized) {
            runCatching { contentResolver.unregisterContentObserver(settingsObserver) }
            workerThread.quitSafely()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun syncConfig(waitForDeclared: Boolean) {
        val config = VibratorTunerWrapper.readConfig(contentResolver)
        if (!VibratorTunerWrapper.applyConfig(config, waitForDeclared = waitForDeclared)) {
            Log.w(TAG, "Failed to push haptic calibration to IVibratorTuner")
        }
    }

    companion object {
        private const val TAG = "XiaomiPartsHaptics"
    }
}
