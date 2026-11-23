/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.esim

import android.content.Context
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.telephony.UiccSlotMapping
import android.util.Log

/**
 * Switches the physical SIM slot that is shared between a SIM tray and the embedded SIM.
 *
 * The modem reports a slot that supports both types through the radio config HAL; the framework
 * changes its active type when a slot mapping carries a new SIM type.
 */
class EsimSlotController(context: Context) {

    private val telephonyManager = context.getSystemService(TelephonyManager::class.java)
    private val subscriptionManager = context.getSystemService(SubscriptionManager::class.java)

    /** Physical index of the slot that can be switched to the eSIM, or -1 if there is none. */
    fun getSwitchableSlot(): Int {
        val slots = telephonyManager?.uiccSlotsInfo ?: return -1
        return slots.indexOfFirst { slot ->
            slot != null &&
                slot.supportedSimTypes.contains(TelephonyManager.SIM_TYPE_PHYSICAL) &&
                slot.supportedSimTypes.contains(TelephonyManager.SIM_TYPE_EMBEDDED)
        }
    }

    fun isEsimEnabled(): Boolean {
        val slot = getSwitchableSlot()
        if (slot < 0) return false
        return telephonyManager.uiccSlotsInfo[slot]?.simType == TelephonyManager.SIM_TYPE_EMBEDDED
    }

    fun hasActiveEsimProfile(): Boolean =
        subscriptionManager?.activeSubscriptionInfoList?.any { it.isEmbedded } ?: false

    /** Blocking: call off the main thread. Returns true when the modem accepted the change. */
    fun setEsimEnabled(enabled: Boolean): Boolean {
        val target = getSwitchableSlot()
        if (target < 0) return false
        val slots = telephonyManager.uiccSlotsInfo
        val mapping =
            telephonyManager.simSlotMapping.map { current ->
                val physical = current.physicalSlotIndex
                val simType =
                    if (physical == target) {
                        if (enabled) TelephonyManager.SIM_TYPE_EMBEDDED
                        else TelephonyManager.SIM_TYPE_PHYSICAL
                    } else {
                        slots.getOrNull(physical)?.simType ?: TelephonyManager.SIM_TYPE_PHYSICAL
                    }
                UiccSlotMapping(
                    current.portIndex,
                    physical,
                    current.logicalSlotIndex,
                    simType,
                )
            }
        return try {
            telephonyManager.setSimSlotMapping(mapping)
            true
        } catch (e: RuntimeException) {
            Log.e(TAG, "Failed to switch slot $target to ${if (enabled) "eSIM" else "SIM"}", e)
            false
        }
    }

    companion object {
        private const val TAG = "EsimSlotController"
    }
}
