/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package vendor.xiaomi.hardware.vibratortuner;

import vendor.xiaomi.hardware.vibratortuner.VibratorConfig;

@VintfStability
interface IVibratorTuner {
    VibratorConfig getConfig();
    void setConfig(in VibratorConfig config);
    void resetToDefault();
    float getResonantFrequency();
    float getQFactor();
}
