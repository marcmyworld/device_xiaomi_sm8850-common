/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.touch

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log

/**
 * Single tap to wake: enables the single tap gesture of the touch controller, which then
 * reports a wake key while the screen is off. The mode is applied again when the screen
 * turns on, in case the touch feature service restarted meanwhile.
 */
class SingleTapService : Service() {

    private val handler = Handler(Looper.getMainLooper())

    private val screenOnReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = update()
        }

    private val settingsObserver =
        object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) = update()
        }

    override fun onCreate() {
        super.onCreate()
        registerReceiver(screenOnReceiver, IntentFilter(Intent.ACTION_SCREEN_ON))
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(SECURE_KEY_SINGLE_TAP),
            false,
            settingsObserver,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        update()
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(screenOnReceiver)
        contentResolver.unregisterContentObserver(settingsObserver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun update() {
        val enabled = Settings.Secure.getInt(contentResolver, SECURE_KEY_SINGLE_TAP, 0) != 0
        if (DEBUG) Log.d(TAG, "Single tap to wake: $enabled")
        TouchFeatureWrapper.setTouchMode(MODE_TOUCH_SINGLE_TAP, if (enabled) 1 else 0)
    }

    companion object {
        private const val TAG = "XiaomiPartsSingleTapService"
        private val DEBUG = Log.isLoggable(TAG, Log.DEBUG)
        const val SECURE_KEY_SINGLE_TAP = "touch_single_tap_enabled"
        // Touch mode the stock touch feature service uses for the single tap gesture
        private const val MODE_TOUCH_SINGLE_TAP = 11
    }
}
