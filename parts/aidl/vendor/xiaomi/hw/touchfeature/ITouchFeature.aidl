/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package vendor.xiaomi.hw.touchfeature;

/**
 * Client view of the stock vendor.xiaomi.hw.touchfeature.ITouchFeature/default service
 * (interface version 1). Only the methods used here are declared, with the transaction ids of
 * the stock interface.
 */
interface ITouchFeature {
    int getModeCurValue(int touchId, int mode) = 0;
    int setModeLongValue(int touchId, int mode, int length, in int[] values) = 7;
    int setModeValue(int touchId, int mode, int value) = 8;
}
