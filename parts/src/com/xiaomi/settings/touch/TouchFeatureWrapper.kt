/*
 * Copyright (C) 2023-2024 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.touch

import android.os.IBinder
import android.util.Log
import vendor.xiaomi.hw.touchfeature.ITouchFeature

object TouchFeatureWrapper {

    private const val TAG = "TouchFeatureWrapper"
    private val DEBUG = Log.isLoggable(TAG, Log.DEBUG)
    private const val TOUCH_ID_DEFAULT = 0

    @Volatile private var touchFeature: ITouchFeature? = null

    private val deathRecipient =
        IBinder.DeathRecipient {
            if (DEBUG) Log.d(TAG, "serviceDied")
            touchFeature = null
        }

    @Synchronized
    private fun getTouchFeature(): ITouchFeature? =
        touchFeature
            ?: runCatching {
                val fqName = "${ITouchFeature.DESCRIPTOR}/default"
                val binder = android.os.Binder.allowBlocking(
                    android.os.ServiceManager.waitForDeclaredService(fqName)
                )
                ITouchFeature.Stub.asInterface(binder).apply {
                    asBinder().linkToDeath(deathRecipient, 0)
                }
            }
            .onSuccess { touchFeature = it }
            .onFailure { e -> Log.e(TAG, "getTouchFeature failed!", e) }
            .getOrNull()

    fun setTouchMode(mode: Int, value: Int) {
        val touchFeature =
            getTouchFeature()
                ?: run {
                    Log.e(TAG, "setTouchMode: touchFeature is null!")
                    return
                }
        if (DEBUG) Log.d(TAG, "setTouchMode: mode=$mode value=$value")
        runCatching { touchFeature.setModeValue(TOUCH_ID_DEFAULT, mode, value) }
            .onSuccess { ret -> if (DEBUG) Log.d(TAG, "setModeValue returned $ret") }
            .onFailure { e -> Log.e(TAG, "setTouchMode failed!", e) }
    }
}
