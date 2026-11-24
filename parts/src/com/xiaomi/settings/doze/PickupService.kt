/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.doze

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.AmbientDisplayConfiguration
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.UserHandle
import android.provider.Settings
import android.util.Log

/**
 * Lift to wake: wakes the device, or shows the ambient display when "Show ambient display" is
 * chosen for the gesture, when the Xiaomi pickup sensor reports that it was picked up.
 *
 * The sensor uses the special trigger reporting mode, so SystemUI's doze triggers (which only
 * take one-shot sensors) cannot use it; it is listened to here while the screen is off and
 * "Lift to check phone" is on.
 */
class PickupService : Service(), SensorEventListener {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var ambientConfig: AmbientDisplayConfiguration
    private lateinit var powerManager: PowerManager
    private lateinit var sensorManager: SensorManager
    private var sensor: Sensor? = null
    private var listening = false
    private var lastWakeTime = 0L

    private val screenReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = update()
        }

    private val settingsObserver =
        object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) = update()
        }

    override fun onCreate() {
        super.onCreate()
        ambientConfig = AmbientDisplayConfiguration(this)
        powerManager = getSystemService(PowerManager::class.java)!!
        sensorManager = getSystemService(SensorManager::class.java)!!
        // Prefer the wake-up variant, which also reports while the device is suspended
        sensor =
            sensorManager.getSensorList(Sensor.TYPE_ALL)
                .filter { it.stringType == SENSOR_TYPE_PICKUP }
                .let { sensors -> sensors.firstOrNull { it.isWakeUpSensor } ?: sensors.firstOrNull() }
        if (sensor == null) {
            Log.w(TAG, "No $SENSOR_TYPE_PICKUP sensor")
            return
        }
        registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
        )
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.DOZE_PICK_UP_GESTURE),
            false,
            settingsObserver,
            UserHandle.USER_ALL,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        update()
        return START_STICKY
    }

    override fun onDestroy() {
        if (sensor != null) {
            unregisterReceiver(screenReceiver)
            contentResolver.unregisterContentObserver(settingsObserver)
            setListening(false)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun update() {
        setListening(
            sensor != null &&
                !powerManager.isInteractive &&
                ambientConfig.pickupGestureEnabled(UserHandle.USER_CURRENT)
        )
    }

    private fun setListening(listen: Boolean) {
        if (listening == listen) return
        listening = listen
        if (DEBUG) Log.d(TAG, "Listening: $listen")
        if (listen) {
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL, handler)
        } else {
            sensorManager.unregisterListener(this)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (DEBUG) Log.d(TAG, "Pickup event: ${event.values.joinToString()}")
        if (event.values.firstOrNull() != VALUE_PICKED_UP || powerManager.isInteractive) return
        val now = SystemClock.uptimeMillis()
        if (now - lastWakeTime < MIN_WAKE_INTERVAL_MS) return
        lastWakeTime = now
        if (ambientConfig.pickupGestureAmbient(UserHandle.USER_CURRENT)) {
            sendBroadcastAsUser(Intent(ACTION_DOZE_PULSE), UserHandle.CURRENT)
        } else {
            powerManager.wakeUp(now, PowerManager.WAKE_REASON_LIFT, TAG)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

    companion object {
        private const val TAG = "XiaomiPartsPickupService"
        private val DEBUG = Log.isLoggable(TAG, Log.DEBUG)
        private const val SENSOR_TYPE_PICKUP = "xiaomi.sensor.pickup"
        // Handled by SystemUI's DozeTriggers, which pulses the ambient display
        private const val ACTION_DOZE_PULSE = "com.android.systemui.doze.pulse"
        // The sensor reports 1 when the device is picked up and 0 or 2 when it is put down
        private const val VALUE_PICKED_UP = 1f
        private const val MIN_WAKE_INTERVAL_MS = 2500L
    }
}
