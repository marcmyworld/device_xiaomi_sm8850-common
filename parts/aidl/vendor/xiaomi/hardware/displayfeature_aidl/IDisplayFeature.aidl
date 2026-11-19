/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package vendor.xiaomi.hardware.displayfeature_aidl;

/**
 * Client view of the stock vendor.xiaomi.hardware.displayfeature_aidl.IDisplayFeature/default
 * service (interface version 2). Only the methods used here are declared, with the transaction
 * ids of the stock interface.
 */
interface IDisplayFeature {
    void setFeature(int displayId, int mode, int value, int cookie) = 6;
}
