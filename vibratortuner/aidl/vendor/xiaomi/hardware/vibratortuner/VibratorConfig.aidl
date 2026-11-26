/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package vendor.xiaomi.hardware.vibratortuner;

/**
 * Runtime tuning configuration for vibratorfeature-wrapper.
 * Presets select curated waveform/gain profiles; PROFILE_CUSTOM applies the
 * per-category waveform ids, maximum hardware strength ceilings, and scaleBias.
 */
@VintfStability
parcelable VibratorConfig {
    const int PROFILE_BALANCED = 0;
    const int PROFILE_STOCK = 1;
    const int PROFILE_CRISP_TIGHT = 2;
    const int PROFILE_THIN_LIGHT = 3;
    const int PROFILE_CUSTOM = 5;

    const int STRENGTH_OFF = 0;
    const int STRENGTH_LIGHT = 30;
    const int STRENGTH_MEDIUM = 50;
    const int STRENGTH_STRONG = 100;

    int profile = PROFILE_STOCK;
    boolean stockSliderSafety = true;
    int scaleBias = 0;

    int clickEffectId = 88;
    int clickMaxStrength = STRENGTH_MEDIUM;

    int tickEffectId = 86;
    int tickMaxStrength = STRENGTH_MEDIUM;

    int heavyEffectId = 163;
    int heavyMaxStrength = STRENGTH_STRONG;

    int sliderEffectId = 92;
    int sliderStrength = STRENGTH_LIGHT;

    int quickRiseEffectId = 162;
    int quickRiseMaxStrength = STRENGTH_STRONG;

    int quickFallEffectId = 188;
    int quickFallMaxStrength = STRENGTH_MEDIUM;

    int slowRiseEffectId = 57;
    int slowRiseMaxStrength = STRENGTH_MEDIUM;

    int spinEffectId = 170;
    int spinMaxStrength = STRENGTH_MEDIUM;
}
