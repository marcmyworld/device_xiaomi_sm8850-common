/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.haptics

import android.content.ContentResolver
import android.content.Context
import android.os.Binder
import android.os.IBinder
import android.os.ServiceManager
import android.os.UserHandle
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import vendor.xiaomi.hardware.vibratortuner.IVibratorTuner
import vendor.xiaomi.hardware.vibratortuner.VibratorConfig

/** Compatibility-checked AIDL client for vibratorfeature-wrapper's IVibratorTuner service. */
object VibratorTunerWrapper {

    private const val TAG = "VibratorTunerWrapper"
    private val DEBUG = Log.isLoggable(TAG, Log.DEBUG)
    private val FQ_NAME = "${IVibratorTuner.DESCRIPTOR}/default"

    const val SECURE_KEY_PROFILE = "haptics_tuner_profile"
    const val SECURE_KEY_STOCK_SLIDER_SAFETY = "haptics_tuner_stock_slider_safety"
    const val SECURE_KEY_SCALE_BIAS = "haptics_tuner_scale_bias"

    const val SECURE_KEY_CLICK_EFFECT = "haptics_tuner_click_effect"
    const val SECURE_KEY_CLICK_STRENGTH = "haptics_tuner_click_strength"
    const val SECURE_KEY_TICK_EFFECT = "haptics_tuner_tick_effect"
    const val SECURE_KEY_TICK_STRENGTH = "haptics_tuner_tick_strength"
    const val SECURE_KEY_HEAVY_EFFECT = "haptics_tuner_heavy_effect"
    const val SECURE_KEY_HEAVY_STRENGTH = "haptics_tuner_heavy_strength"
    const val SECURE_KEY_SLIDER_EFFECT = "haptics_tuner_slider_effect"
    const val SECURE_KEY_SLIDER_STRENGTH = "haptics_tuner_slider_strength"
    const val SECURE_KEY_QUICK_RISE_EFFECT = "haptics_tuner_quick_rise_effect"
    const val SECURE_KEY_QUICK_RISE_STRENGTH = "haptics_tuner_quick_rise_strength"
    const val SECURE_KEY_QUICK_FALL_EFFECT = "haptics_tuner_quick_fall_effect"
    const val SECURE_KEY_QUICK_FALL_STRENGTH = "haptics_tuner_quick_fall_strength"
    const val SECURE_KEY_SLOW_RISE_EFFECT = "haptics_tuner_slow_rise_effect"
    const val SECURE_KEY_SLOW_RISE_STRENGTH = "haptics_tuner_slow_rise_strength"
    const val SECURE_KEY_SPIN_EFFECT = "haptics_tuner_spin_effect"
    const val SECURE_KEY_SPIN_STRENGTH = "haptics_tuner_spin_strength"
    const val SECURE_KEY_ACTIVE_SAVED_PROFILE = "haptics_tuner_active_saved_profile"
    const val SECURE_KEY_SAVED_PROFILES = "haptics_tuner_saved_profiles_json"
    private const val SECURE_KEY_PCT_MIGRATED = "haptics_tuner_pct_migrated"
    private const val PROFILE_JSON_SCHEMA = 1

    private val ALLOWED_EFFECT_IDS =
        setOf(
            -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 11, 57, 84, 86, 88, 91, 92, 102, 162, 163, 165, 166,
            167, 168, 169, 170, 171, 172, 181, 183, 185, 186, 187, 188, 190, 203,
        )

    data class SavedCustomProfile(
        val name: String,
        val scaleBias: Int,
        val clickEffectId: Int,
        val clickMaxStrength: Int,
        val tickEffectId: Int,
        val tickMaxStrength: Int,
        val heavyEffectId: Int,
        val heavyMaxStrength: Int,
        val sliderEffectId: Int,
        val sliderStrength: Int,
        val quickRiseEffectId: Int,
        val quickRiseMaxStrength: Int,
        val quickFallEffectId: Int,
        val quickFallMaxStrength: Int,
        val slowRiseEffectId: Int,
        val slowRiseMaxStrength: Int,
        val spinEffectId: Int,
        val spinMaxStrength: Int,
    ) {
        fun toJson(): JSONObject =
            JSONObject().apply {
                put("schema", PROFILE_JSON_SCHEMA)
                put("name", name)
                put("scaleBias", scaleBias.coerceIn(-2, 2))
                put("click", pairJson(clickEffectId, clickMaxStrength))
                put("tick", pairJson(tickEffectId, tickMaxStrength))
                put("heavy", pairJson(heavyEffectId, heavyMaxStrength))
                put("slider", pairJson(sliderEffectId, sliderStrength))
                put("quickRise", pairJson(quickRiseEffectId, quickRiseMaxStrength))
                put("quickFall", pairJson(quickFallEffectId, quickFallMaxStrength))
                put("slowRise", pairJson(slowRiseEffectId, slowRiseMaxStrength))
                put("spin", pairJson(spinEffectId, spinMaxStrength))
            }

        private fun pairJson(effectId: Int, strengthPct: Int): JSONObject =
            JSONObject().apply {
                put("effectId", effectId)
                put("strengthPct", normalizeGainPct(strengthPct))
            }
    }

    private val STRENGTH_KEYS =
        listOf(
            SECURE_KEY_CLICK_STRENGTH,
            SECURE_KEY_TICK_STRENGTH,
            SECURE_KEY_HEAVY_STRENGTH,
            SECURE_KEY_SLIDER_STRENGTH,
            SECURE_KEY_QUICK_RISE_STRENGTH,
            SECURE_KEY_QUICK_FALL_STRENGTH,
            SECURE_KEY_SLOW_RISE_STRENGTH,
            SECURE_KEY_SPIN_STRENGTH,
        )

    val ALL_SECURE_KEYS =
        listOf(
            SECURE_KEY_PROFILE,
            SECURE_KEY_STOCK_SLIDER_SAFETY,
            SECURE_KEY_SCALE_BIAS,
            SECURE_KEY_CLICK_EFFECT,
            SECURE_KEY_CLICK_STRENGTH,
            SECURE_KEY_TICK_EFFECT,
            SECURE_KEY_TICK_STRENGTH,
            SECURE_KEY_HEAVY_EFFECT,
            SECURE_KEY_HEAVY_STRENGTH,
            SECURE_KEY_SLIDER_EFFECT,
            SECURE_KEY_SLIDER_STRENGTH,
            SECURE_KEY_QUICK_RISE_EFFECT,
            SECURE_KEY_QUICK_RISE_STRENGTH,
            SECURE_KEY_QUICK_FALL_EFFECT,
            SECURE_KEY_QUICK_FALL_STRENGTH,
            SECURE_KEY_SLOW_RISE_EFFECT,
            SECURE_KEY_SLOW_RISE_STRENGTH,
            SECURE_KEY_SPIN_EFFECT,
            SECURE_KEY_SPIN_STRENGTH,
            SECURE_KEY_ACTIVE_SAVED_PROFILE,
            SECURE_KEY_SAVED_PROFILES,
        )

    @Volatile private var tuner: IVibratorTuner? = null

    private val deathRecipient =
        IBinder.DeathRecipient {
            if (DEBUG) Log.d(TAG, "IVibratorTuner service died")
            tuner = null
        }

    fun isSupported(context: Context): Boolean {
        val vibrator = context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        if (vibrator?.hasVibrator() != true) return false
        return runCatching {
                ServiceManager.isDeclared(FQ_NAME) || ServiceManager.checkService(FQ_NAME) != null
            }
            .getOrDefault(false)
    }

    @Synchronized
    private fun getTuner(waitForDeclared: Boolean): IVibratorTuner? {
        tuner?.let { return it }
        return runCatching {
                val raw =
                    if (waitForDeclared && ServiceManager.isDeclared(FQ_NAME)) {
                        ServiceManager.waitForDeclaredService(FQ_NAME)
                    } else {
                        ServiceManager.checkService(FQ_NAME)
                    } ?: return@runCatching null
                val binder = Binder.allowBlocking(raw)
                IVibratorTuner.Stub.asInterface(binder)?.apply {
                    asBinder().linkToDeath(deathRecipient, 0)
                }
            }
            .onSuccess { tuner = it }
            .onFailure { e -> Log.w(TAG, "Failed to bind $FQ_NAME", e) }
            .getOrNull()
    }

    fun applyConfig(config: VibratorConfig, waitForDeclared: Boolean = false): Boolean {
        val service = getTuner(waitForDeclared) ?: return false
        return runCatching {
                service.setConfig(config)
                true
            }
            .onFailure { e ->
                Log.w(TAG, "setConfig failed", e)
                tuner = null
            }
            .getOrDefault(false)
    }

    fun getHardwareTelemetry(): Pair<Float, Float>? {
        val service = getTuner(waitForDeclared = false) ?: return null
        return runCatching {
                val f0 = service.resonantFrequency
                val q = service.qFactor
                Pair(f0, q)
            }
            .getOrNull()
    }

    fun readConfig(cr: ContentResolver, userId: Int = UserHandle.USER_CURRENT): VibratorConfig {
        migrateLegacyStrengthRanksIfNeeded(cr, userId)
        return VibratorConfig().apply {
            val rawProfile = Settings.Secure.getIntForUser(
                cr, SECURE_KEY_PROFILE, VibratorConfig.PROFILE_STOCK, userId
            )
            profile =
                if (
                    rawProfile == VibratorConfig.PROFILE_BALANCED ||
                        rawProfile == VibratorConfig.PROFILE_STOCK ||
                        rawProfile == VibratorConfig.PROFILE_CRISP_TIGHT ||
                        rawProfile == VibratorConfig.PROFILE_THIN_LIGHT ||
                        rawProfile == VibratorConfig.PROFILE_CUSTOM
                ) {
                    rawProfile
                } else {
                    VibratorConfig.PROFILE_STOCK
                }
            stockSliderSafety =
                Settings.Secure.getIntForUser(cr, SECURE_KEY_STOCK_SLIDER_SAFETY, 1, userId) != 0
            scaleBias = Settings.Secure.getIntForUser(cr, SECURE_KEY_SCALE_BIAS, 0, userId)

            clickEffectId = Settings.Secure.getIntForUser(cr, SECURE_KEY_CLICK_EFFECT, 88, userId)
            clickMaxStrength = Settings.Secure.getIntForUser(
                cr, SECURE_KEY_CLICK_STRENGTH, VibratorConfig.STRENGTH_MEDIUM, userId
            )
            tickEffectId = Settings.Secure.getIntForUser(cr, SECURE_KEY_TICK_EFFECT, 86, userId)
            tickMaxStrength = Settings.Secure.getIntForUser(
                cr, SECURE_KEY_TICK_STRENGTH, VibratorConfig.STRENGTH_MEDIUM, userId
            )
            heavyEffectId = Settings.Secure.getIntForUser(cr, SECURE_KEY_HEAVY_EFFECT, 163, userId)
            heavyMaxStrength = Settings.Secure.getIntForUser(
                cr, SECURE_KEY_HEAVY_STRENGTH, VibratorConfig.STRENGTH_STRONG, userId
            )
            sliderEffectId = Settings.Secure.getIntForUser(cr, SECURE_KEY_SLIDER_EFFECT, 92, userId)
            sliderStrength = Settings.Secure.getIntForUser(
                cr, SECURE_KEY_SLIDER_STRENGTH, VibratorConfig.STRENGTH_LIGHT, userId
            )
            quickRiseEffectId = Settings.Secure.getIntForUser(
                cr, SECURE_KEY_QUICK_RISE_EFFECT, 162, userId
            )
            quickRiseMaxStrength = Settings.Secure.getIntForUser(
                cr, SECURE_KEY_QUICK_RISE_STRENGTH, VibratorConfig.STRENGTH_STRONG, userId
            )
            quickFallEffectId = Settings.Secure.getIntForUser(
                cr, SECURE_KEY_QUICK_FALL_EFFECT, 188, userId
            )
            quickFallMaxStrength = Settings.Secure.getIntForUser(
                cr, SECURE_KEY_QUICK_FALL_STRENGTH, VibratorConfig.STRENGTH_MEDIUM, userId
            )
            slowRiseEffectId = Settings.Secure.getIntForUser(
                cr, SECURE_KEY_SLOW_RISE_EFFECT, 57, userId
            )
            slowRiseMaxStrength = Settings.Secure.getIntForUser(
                cr, SECURE_KEY_SLOW_RISE_STRENGTH, VibratorConfig.STRENGTH_MEDIUM, userId
            )
            spinEffectId = Settings.Secure.getIntForUser(cr, SECURE_KEY_SPIN_EFFECT, 170, userId)
            spinMaxStrength = Settings.Secure.getIntForUser(
                cr, SECURE_KEY_SPIN_STRENGTH, VibratorConfig.STRENGTH_MEDIUM, userId
            )
        }
    }

    private fun migrateLegacyStrengthRanksIfNeeded(cr: ContentResolver, userId: Int) {
        if (Settings.Secure.getIntForUser(cr, SECURE_KEY_PCT_MIGRATED, 0, userId) == 1) return
        for (key in STRENGTH_KEYS) {
            val raw = Settings.Secure.getIntForUser(cr, key, Int.MIN_VALUE, userId)
            if (raw in -1..2) {
                val migrated =
                    when (raw) {
                        -1 -> VibratorConfig.STRENGTH_OFF
                        0 -> VibratorConfig.STRENGTH_LIGHT
                        2 -> VibratorConfig.STRENGTH_STRONG
                        else -> VibratorConfig.STRENGTH_MEDIUM
                    }
                Settings.Secure.putIntForUser(cr, key, migrated, userId)
            }
        }
        Settings.Secure.putIntForUser(cr, SECURE_KEY_PCT_MIGRATED, 1, userId)
    }

    fun normalizeGainPct(pct: Int): Int =
        if (pct <= VibratorConfig.STRENGTH_OFF) VibratorConfig.STRENGTH_OFF
        else pct.coerceIn(VibratorConfig.STRENGTH_LIGHT, VibratorConfig.STRENGTH_STRONG)

    fun savedProfileFromConfig(name: String, cfg: VibratorConfig): SavedCustomProfile =
        SavedCustomProfile(
            name = name.trim().ifEmpty { "Custom" },
            scaleBias = cfg.scaleBias.coerceIn(-2, 2),
            clickEffectId = sanitizeEffectId(cfg.clickEffectId, 88),
            clickMaxStrength = normalizeGainPct(cfg.clickMaxStrength),
            tickEffectId = sanitizeEffectId(cfg.tickEffectId, 86),
            tickMaxStrength = normalizeGainPct(cfg.tickMaxStrength),
            heavyEffectId = sanitizeEffectId(cfg.heavyEffectId, 163),
            heavyMaxStrength = normalizeGainPct(cfg.heavyMaxStrength),
            sliderEffectId = sanitizeEffectId(cfg.sliderEffectId, 92),
            sliderStrength = normalizeGainPct(cfg.sliderStrength),
            quickRiseEffectId = sanitizeEffectId(cfg.quickRiseEffectId, 162),
            quickRiseMaxStrength = normalizeGainPct(cfg.quickRiseMaxStrength),
            quickFallEffectId = sanitizeEffectId(cfg.quickFallEffectId, 188),
            quickFallMaxStrength = normalizeGainPct(cfg.quickFallMaxStrength),
            slowRiseEffectId = sanitizeEffectId(cfg.slowRiseEffectId, 57),
            slowRiseMaxStrength = normalizeGainPct(cfg.slowRiseMaxStrength),
            spinEffectId = sanitizeEffectId(cfg.spinEffectId, 170),
            spinMaxStrength = normalizeGainPct(cfg.spinMaxStrength),
        )

    fun getSavedProfiles(cr: ContentResolver, userId: Int = UserHandle.USER_CURRENT): List<SavedCustomProfile> {
        val raw = Settings.Secure.getStringForUser(cr, SECURE_KEY_SAVED_PROFILES, userId)
            ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { obj -> parseSavedProfile(obj)?.let(::add) }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveCustomProfile(
        cr: ContentResolver,
        profile: SavedCustomProfile,
        userId: Int = UserHandle.USER_CURRENT,
    ) {
        val updated = getSavedProfiles(cr, userId).filterNot { it.name.equals(profile.name, ignoreCase = true) } + profile
        writeSavedProfiles(cr, updated, profile.name, userId)
    }

    fun deleteSavedProfile(
        cr: ContentResolver,
        name: String,
        userId: Int = UserHandle.USER_CURRENT,
    ) {
        val updated = getSavedProfiles(cr, userId).filterNot { it.name.equals(name, ignoreCase = true) }
        val active = getActiveSavedProfileName(cr, userId)
        val nextActive = if (active.equals(name, ignoreCase = true)) "" else active
        writeSavedProfiles(cr, updated, nextActive, userId)
    }

    fun getActiveSavedProfileName(cr: ContentResolver, userId: Int = UserHandle.USER_CURRENT): String =
        Settings.Secure.getStringForUser(cr, SECURE_KEY_ACTIVE_SAVED_PROFILE, userId).orEmpty()

    fun setActiveSavedProfileName(
        cr: ContentResolver,
        name: String,
        userId: Int = UserHandle.USER_CURRENT,
    ) {
        Settings.Secure.putStringForUser(cr, SECURE_KEY_ACTIVE_SAVED_PROFILE, name, userId)
    }

    fun applySavedProfile(
        cr: ContentResolver,
        profile: SavedCustomProfile,
        userId: Int = UserHandle.USER_CURRENT,
    ) {
        val pairs =
            listOf(
                SECURE_KEY_PROFILE to VibratorConfig.PROFILE_CUSTOM,
                SECURE_KEY_SCALE_BIAS to profile.scaleBias.coerceIn(-2, 2),
                SECURE_KEY_CLICK_EFFECT to sanitizeEffectId(profile.clickEffectId, 88),
                SECURE_KEY_CLICK_STRENGTH to normalizeGainPct(profile.clickMaxStrength),
                SECURE_KEY_TICK_EFFECT to sanitizeEffectId(profile.tickEffectId, 86),
                SECURE_KEY_TICK_STRENGTH to normalizeGainPct(profile.tickMaxStrength),
                SECURE_KEY_HEAVY_EFFECT to sanitizeEffectId(profile.heavyEffectId, 163),
                SECURE_KEY_HEAVY_STRENGTH to normalizeGainPct(profile.heavyMaxStrength),
                SECURE_KEY_SLIDER_EFFECT to sanitizeEffectId(profile.sliderEffectId, 92),
                SECURE_KEY_SLIDER_STRENGTH to normalizeGainPct(profile.sliderStrength),
                SECURE_KEY_QUICK_RISE_EFFECT to sanitizeEffectId(profile.quickRiseEffectId, 162),
                SECURE_KEY_QUICK_RISE_STRENGTH to normalizeGainPct(profile.quickRiseMaxStrength),
                SECURE_KEY_QUICK_FALL_EFFECT to sanitizeEffectId(profile.quickFallEffectId, 188),
                SECURE_KEY_QUICK_FALL_STRENGTH to normalizeGainPct(profile.quickFallMaxStrength),
                SECURE_KEY_SLOW_RISE_EFFECT to sanitizeEffectId(profile.slowRiseEffectId, 57),
                SECURE_KEY_SLOW_RISE_STRENGTH to normalizeGainPct(profile.slowRiseMaxStrength),
                SECURE_KEY_SPIN_EFFECT to sanitizeEffectId(profile.spinEffectId, 170),
                SECURE_KEY_SPIN_STRENGTH to normalizeGainPct(profile.spinMaxStrength),
            )
        for ((key, value) in pairs) {
            Settings.Secure.putIntForUser(cr, key, value, userId)
        }
        setActiveSavedProfileName(cr, profile.name, userId)
    }

    fun parseSavedProfileJson(rawJson: String): SavedCustomProfile? =
        runCatching { parseSavedProfile(JSONObject(rawJson.trim())) }.getOrNull()

    private fun writeSavedProfiles(
        cr: ContentResolver,
        profiles: List<SavedCustomProfile>,
        activeName: String,
        userId: Int,
    ) {
        val arr = JSONArray()
        profiles.forEach { arr.put(it.toJson()) }
        Settings.Secure.putStringForUser(cr, SECURE_KEY_SAVED_PROFILES, arr.toString(), userId)
        setActiveSavedProfileName(cr, activeName, userId)
    }

    private fun parseSavedProfile(obj: JSONObject): SavedCustomProfile? {
        val name = obj.optString("name", "").trim()
        if (name.isEmpty()) return null
        fun pair(key: String, defId: Int, defPct: Int): Pair<Int, Int> {
            val sub = obj.optJSONObject(key)
            val id = sanitizeEffectId(sub?.optInt("effectId", defId) ?: defId, defId)
            val pct = normalizeGainPct(sub?.optInt("strengthPct", defPct) ?: defPct)
            return id to pct
        }
        val (clickId, clickPct) = pair("click", 88, VibratorConfig.STRENGTH_MEDIUM)
        val (tickId, tickPct) = pair("tick", 86, VibratorConfig.STRENGTH_MEDIUM)
        val (heavyId, heavyPct) = pair("heavy", 163, VibratorConfig.STRENGTH_STRONG)
        val (sliderId, sliderPct) = pair("slider", 92, VibratorConfig.STRENGTH_LIGHT)
        val (riseId, risePct) = pair("quickRise", 162, VibratorConfig.STRENGTH_STRONG)
        val (fallId, fallPct) = pair("quickFall", 188, VibratorConfig.STRENGTH_MEDIUM)
        val (slowId, slowPct) = pair("slowRise", 57, VibratorConfig.STRENGTH_MEDIUM)
        val (spinId, spinPct) = pair("spin", 170, VibratorConfig.STRENGTH_MEDIUM)
        return SavedCustomProfile(
            name = name.take(40),
            scaleBias = obj.optInt("scaleBias", 0).coerceIn(-2, 2),
            clickEffectId = clickId,
            clickMaxStrength = clickPct,
            tickEffectId = tickId,
            tickMaxStrength = tickPct,
            heavyEffectId = heavyId,
            heavyMaxStrength = heavyPct,
            sliderEffectId = sliderId,
            sliderStrength = sliderPct,
            quickRiseEffectId = riseId,
            quickRiseMaxStrength = risePct,
            quickFallEffectId = fallId,
            quickFallMaxStrength = fallPct,
            slowRiseEffectId = slowId,
            slowRiseMaxStrength = slowPct,
            spinEffectId = spinId,
            spinMaxStrength = spinPct,
        )
    }

    private fun sanitizeEffectId(effectId: Int, fallback: Int): Int =
        if (effectId in ALLOWED_EFFECT_IDS) effectId else fallback

    fun resetSettings(cr: ContentResolver, userId: Int = UserHandle.USER_CURRENT) {
        val d = VibratorConfig()
        val pairs =
            listOf(
                SECURE_KEY_PROFILE to d.profile,
                SECURE_KEY_STOCK_SLIDER_SAFETY to if (d.stockSliderSafety) 1 else 0,
                SECURE_KEY_SCALE_BIAS to d.scaleBias,
                SECURE_KEY_CLICK_EFFECT to d.clickEffectId,
                SECURE_KEY_CLICK_STRENGTH to d.clickMaxStrength,
                SECURE_KEY_TICK_EFFECT to d.tickEffectId,
                SECURE_KEY_TICK_STRENGTH to d.tickMaxStrength,
                SECURE_KEY_HEAVY_EFFECT to d.heavyEffectId,
                SECURE_KEY_HEAVY_STRENGTH to d.heavyMaxStrength,
                SECURE_KEY_SLIDER_EFFECT to d.sliderEffectId,
                SECURE_KEY_SLIDER_STRENGTH to d.sliderStrength,
                SECURE_KEY_QUICK_RISE_EFFECT to d.quickRiseEffectId,
                SECURE_KEY_QUICK_RISE_STRENGTH to d.quickRiseMaxStrength,
                SECURE_KEY_QUICK_FALL_EFFECT to d.quickFallEffectId,
                SECURE_KEY_QUICK_FALL_STRENGTH to d.quickFallMaxStrength,
                SECURE_KEY_SLOW_RISE_EFFECT to d.slowRiseEffectId,
                SECURE_KEY_SLOW_RISE_STRENGTH to d.slowRiseMaxStrength,
                SECURE_KEY_SPIN_EFFECT to d.spinEffectId,
                SECURE_KEY_SPIN_STRENGTH to d.spinMaxStrength,
            )
        for ((key, value) in pairs) {
            Settings.Secure.putIntForUser(cr, key, value, userId)
        }
    }
}
