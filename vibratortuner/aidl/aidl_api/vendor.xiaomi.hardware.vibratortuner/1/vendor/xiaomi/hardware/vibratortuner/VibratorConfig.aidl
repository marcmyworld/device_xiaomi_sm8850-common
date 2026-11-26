/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
///////////////////////////////////////////////////////////////////////////////
// THIS FILE IS IMMUTABLE. DO NOT EDIT IN ANY CASE.                          //
///////////////////////////////////////////////////////////////////////////////

// This file is a snapshot of an AIDL file. Do not edit it manually. There are
// two cases:
// 1). this is a frozen version file - do not edit this in any case.
// 2). this is a 'current' file. If you make a backwards compatible change to
//     the interface (from the latest frozen version), the build system will
//     prompt you to update this file with `m <name>-update-api`.
//
// You must not make a backward incompatible change to any AIDL file built
// with the aidl_interface module type with versions property set. The module
// type is used to build AIDL files in a way that they can be used across
// independently updatable components of the system. If a device is shipped
// with such a backward incompatible change, it has a high risk of breaking
// later when a module using the interface is updated, e.g., Mainline modules.

package vendor.xiaomi.hardware.vibratortuner;
@VintfStability
parcelable VibratorConfig {
  int profile = PROFILE_STOCK /* 1 */;
  boolean stockSliderSafety = true;
  int scaleBias = 0;
  int clickEffectId = 88;
  int clickMaxStrength = STRENGTH_MEDIUM /* 50 */;
  int tickEffectId = 86;
  int tickMaxStrength = STRENGTH_MEDIUM /* 50 */;
  int heavyEffectId = 163;
  int heavyMaxStrength = STRENGTH_STRONG /* 100 */;
  int sliderEffectId = 92;
  int sliderStrength = STRENGTH_LIGHT /* 30 */;
  int quickRiseEffectId = 162;
  int quickRiseMaxStrength = STRENGTH_STRONG /* 100 */;
  int quickFallEffectId = 188;
  int quickFallMaxStrength = STRENGTH_MEDIUM /* 50 */;
  int slowRiseEffectId = 57;
  int slowRiseMaxStrength = STRENGTH_MEDIUM /* 50 */;
  int spinEffectId = 170;
  int spinMaxStrength = STRENGTH_MEDIUM /* 50 */;
  const int PROFILE_BALANCED = 0;
  const int PROFILE_STOCK = 1;
  const int PROFILE_CRISP_TIGHT = 2;
  const int PROFILE_THIN_LIGHT = 3;
  const int PROFILE_CUSTOM = 5;
  const int STRENGTH_OFF = 0;
  const int STRENGTH_LIGHT = 30;
  const int STRENGTH_MEDIUM = 50;
  const int STRENGTH_STRONG = 100;
}
