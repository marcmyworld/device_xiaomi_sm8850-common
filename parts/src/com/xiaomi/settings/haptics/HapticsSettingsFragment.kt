/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.haptics

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragment
import androidx.preference.SwitchPreferenceCompat
import com.android.settingslib.widget.ActionButtonsPreference
import com.android.settingslib.widget.FooterPreference
import com.android.settingslib.widget.SelectorWithWidgetPreference
import com.android.settingslib.widget.SliderPreference
import com.android.settingslib.widget.TopIntroPreference
import com.google.android.material.slider.LabelFormatter
import com.google.android.material.slider.Slider
import com.xiaomi.settings.R
import java.util.concurrent.Executors
import vendor.xiaomi.hardware.vibratortuner.VibratorConfig

/** Interactive Settings screen for tuning vibratorfeature-wrapper waveforms and strength caps. */
class HapticsSettingsFragment :
    PreferenceFragment(),
    Preference.OnPreferenceChangeListener,
    SelectorWithWidgetPreference.OnClickListener {

    private data class EffectBinding(
        val prefKey: String,
        val secureKey: String,
        val defaultValue: Int,
        val previewPrimitive: Int,
        val previewScale: Float,
        val entriesResId: Int,
        val valuesResId: Int,
        val illustrationResId: Int,
        val descriptionResId: Int,
    )

    private data class StrengthBinding(
        val prefKey: String,
        val secureKey: String,
        val previewPrimitive: Int,
        val previewScale: Float,
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioExecutor = Executors.newSingleThreadExecutor()

    private val settingsObserver =
        object : ContentObserver(mainHandler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                refreshUi()
            }
        }

    private var introPref: TopIntroPreference? = null
    private var stockSafetySwitch: SwitchPreferenceCompat? = null
    private var waveformGuidePref: Preference? = null
    private var testCategory: PreferenceCategory? = null
    private var customStrengthCategory: PreferenceCategory? = null
    private var customWaveformCategory: PreferenceCategory? = null
    private var savedProfilesCategory: PreferenceCategory? = null
    private var savedProfileSelectPref: Preference? = null
    private var savedProfileActions: ActionButtonsPreference? = null
    private var scaleBiasSlider: SliderPreference? = null
    private var hardwareInfoPref: FooterPreference? = null
    private var pendingExportProfile: VibratorTunerWrapper.SavedCustomProfile? = null

    private val profileCards = mutableListOf<Pair<SelectorWithWidgetPreference, Int>>()
    private val effectPrefs = mutableMapOf<String, Pair<Preference, EffectBinding>>()
    private val strengthPrefs = mutableMapOf<String, Pair<SliderPreference, StrengthBinding>>()

    private var vibrator: Vibrator? = null
    private var supported = false

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.settings_haptics)

        vibrator = context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        supported = VibratorTunerWrapper.isSupported(context)

        introPref = findPreference(KEY_INTRO)
        stockSafetySwitch =
            (findPreference(KEY_STOCK_SLIDER_SAFETY) as? SwitchPreferenceCompat)?.apply {
                isPersistent = false
                onPreferenceChangeListener = this@HapticsSettingsFragment
            }
        testCategory = findPreference(KEY_TEST_CATEGORY) as? PreferenceCategory
        customStrengthCategory = findPreference(KEY_CUSTOM_STRENGTH_CATEGORY) as? PreferenceCategory
        customWaveformCategory = findPreference(KEY_CUSTOM_WAVEFORM_CATEGORY) as? PreferenceCategory
        savedProfilesCategory = findPreference(KEY_SAVED_PROFILES_CATEGORY) as? PreferenceCategory
        savedProfileSelectPref = findPreference(KEY_SAVED_PROFILE_SELECT)
        savedProfileActions = findPreference(KEY_SAVED_PROFILE_ACTIONS) as? ActionButtonsPreference
        hardwareInfoPref = findPreference(KEY_HARDWARE_INFO) as? FooterPreference

        for ((prefKey, profileId) in PROFILE_ENTRIES) {
            (findPreference(prefKey) as? SelectorWithWidgetPreference)?.let { pref ->
                pref.isPersistent = false
                pref.setOnClickListener(this)
                profileCards += pref to profileId
            }
        }

        for (binding in STRENGTH_BINDINGS) {
            (findPreference(binding.prefKey) as? SliderPreference)?.let { pref ->
                pref.isPersistent = false
                pref.min = SLIDER_OFF_TICK
                pref.max = VibratorConfig.STRENGTH_STRONG
                pref.sliderIncrement = 1
                pref.setTickVisible(false)
                pref.setShowSliderValue(true)
                pref.setUpdatesContinuously(false)
                pref.setLabelFormater(LabelFormatter { value -> strengthShortLabel(value.toInt()) })
                pref.onPreferenceChangeListener = this
                strengthPrefs[binding.prefKey] = pref to binding
            }
        }

        waveformGuidePref =
            findPreference<Preference>(KEY_WAVEFORM_GUIDE)?.apply {
                setOnPreferenceClickListener {
                    showWaveformGuideDialog()
                    true
                }
            }

        for (binding in EFFECT_BINDINGS) {
            findPreference<Preference>(binding.prefKey)?.let { pref ->
                pref.isPersistent = false
                pref.setOnPreferenceClickListener {
                    showWaveformPickerDialog(pref, binding)
                    true
                }
                effectPrefs[binding.prefKey] = pref to binding
            }
        }

        scaleBiasSlider =
            (findPreference(KEY_SCALE_BIAS) as? SliderPreference)?.apply {
                isPersistent = false
                min = -2
                max = 2
                sliderIncrement = 1
                setTickVisible(true)
                setShowSliderValue(true)
                setLabelFormater(
                    LabelFormatter { value ->
                        val pct = value.toInt() * 10
                        if (pct > 0) "+$pct%" else "$pct%"
                    }
                )
                onPreferenceChangeListener = this@HapticsSettingsFragment
            }

        setupTestPad()
        setupSavedProfileControls()
        applySupportState()
        refreshUi()
    }

    override fun onResume() {
        super.onResume()
        val cr = context.contentResolver
        for (key in VibratorTunerWrapper.ALL_SECURE_KEYS) {
            cr.registerContentObserver(
                Settings.Secure.getUriFor(key),
                false,
                settingsObserver,
                UserHandle.USER_ALL,
            )
        }
        refreshUi()
        loadHardwareTelemetry()
    }

    override fun onPause() {
        runCatching { context.contentResolver.unregisterContentObserver(settingsObserver) }
        super.onPause()
    }

    override fun onDestroy() {
        ioExecutor.shutdownNow()
        super.onDestroy()
    }

    override fun onRadioButtonClicked(emiter: SelectorWithWidgetPreference) {
        val profileId = profileCards.firstOrNull { it.first == emiter }?.second ?: return
        putSecureInt(VibratorTunerWrapper.SECURE_KEY_PROFILE, profileId)
        pushCurrentConfigToHal()
        playTestPattern(TestPattern.KEY_TAP)
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        when (preference.key) {
            KEY_STOCK_SLIDER_SAFETY -> {
                putSecureInt(
                    VibratorTunerWrapper.SECURE_KEY_STOCK_SLIDER_SAFETY,
                    if (newValue as Boolean) 1 else 0,
                )
                pushCurrentConfigToHal()
            }
            KEY_SCALE_BIAS -> {
                putSecureInt(
                    VibratorTunerWrapper.SECURE_KEY_SCALE_BIAS,
                    (newValue as Int).coerceIn(-2, 2),
                )
                pushCurrentConfigToHal()
            }
            in strengthPrefs -> {
                val (pref, binding) = strengthPrefs[preference.key] ?: return false
                val gainPct = sliderTickToGainPct(newValue as Int)
                putSecureInt(binding.secureKey, gainPct)
                pref.summary = strengthSummaryLabel(gainPct)
                pushCurrentConfigToHal()
                mainHandler.postDelayed(
                    { playSinglePrimitive(binding.previewPrimitive, binding.previewScale) },
                    60L,
                )
            }
            else -> return false
        }
        return true
    }

    private fun setupTestPad() {
        (findPreference(KEY_TEST_ACTIONS_1) as? ActionButtonsPreference)?.apply {
            setButton1Text(R.string.haptics_test_tick)
            setButton1Icon(R.drawable.ic_haptics_tick)
            setButton1OnClickListener { playTestPattern(TestPattern.TICK) }

            setButton2Text(R.string.haptics_test_key_press)
            setButton2Icon(R.drawable.ic_haptics_tap)
            setButton2OnClickListener { playTestPattern(TestPattern.KEY_PRESS) }

            setButton3Text(R.string.haptics_test_key_tap)
            setButton3Icon(R.drawable.ic_haptics_pulse)
            setButton3OnClickListener { playTestPattern(TestPattern.KEY_TAP) }

            setButton4Text(R.string.haptics_test_double_tap)
            setButton4Icon(R.drawable.ic_haptics_double)
            setButton4OnClickListener { playTestPattern(TestPattern.DOUBLE_TAP) }
        }

        (findPreference(KEY_TEST_ACTIONS_2) as? ActionButtonsPreference)?.apply {
            setButton1Text(R.string.haptics_test_gesture)
            setButton1Icon(R.drawable.ic_haptics_gesture)
            setButton1OnClickListener { playTestPattern(TestPattern.GESTURE) }

            setButton2Text(R.string.haptics_test_confirm)
            setButton2Icon(R.drawable.ic_haptics_double)
            setButton2OnClickListener { playTestPattern(TestPattern.CONFIRM) }

            setButton3Text(R.string.haptics_test_heavy)
            setButton3Icon(R.drawable.ic_haptics_heavy)
            setButton3OnClickListener { playTestPattern(TestPattern.HEAVY) }

            setButton4Text(R.string.haptics_test_reset)
            setButton4Icon(R.drawable.ic_haptics_reset)
            setButton4OnClickListener { resetCalibration() }
        }

        (findPreference(KEY_TEST_SLIDER) as? SliderPreference)?.apply {
            isPersistent = false
            min = 0
            max = 20
            sliderIncrement = 1
            value = 10
            setTickVisible(false)
            setShowSliderValue(false)
            setExtraChangeListener(
                Slider.OnChangeListener { _, _, fromUser ->
                    if (fromUser) playTestPattern(TestPattern.SLIDER_TICK)
                }
            )
        }
    }

    private fun applySupportState() {
        if (supported) return
        introPref?.title = getString(R.string.haptics_unavailable_summary)
        for ((card, _) in profileCards) card.isEnabled = false
        stockSafetySwitch?.isEnabled = false
        waveformGuidePref?.isEnabled = false
        testCategory?.isEnabled = false
        customStrengthCategory?.isEnabled = false
        customWaveformCategory?.isEnabled = false
        savedProfilesCategory?.isEnabled = false
    }

    private fun refreshUi() {
        if (!supported) return
        val cfg = VibratorTunerWrapper.readConfig(context.contentResolver)

        for ((card, profileId) in profileCards) {
            card.isChecked = (cfg.profile == profileId)
        }

        stockSafetySwitch?.apply {
            isVisible = (cfg.profile == VibratorConfig.PROFILE_STOCK)
            isChecked = cfg.stockSliderSafety
        }

        val isCustom = (cfg.profile == VibratorConfig.PROFILE_CUSTOM)
        waveformGuidePref?.isVisible = isCustom
        customStrengthCategory?.isVisible = isCustom
        customWaveformCategory?.isVisible = isCustom
        savedProfilesCategory?.isVisible = isCustom

        val secureValues =
            mapOf(
                VibratorTunerWrapper.SECURE_KEY_CLICK_EFFECT to cfg.clickEffectId,
                VibratorTunerWrapper.SECURE_KEY_CLICK_STRENGTH to cfg.clickMaxStrength,
                VibratorTunerWrapper.SECURE_KEY_TICK_EFFECT to cfg.tickEffectId,
                VibratorTunerWrapper.SECURE_KEY_TICK_STRENGTH to cfg.tickMaxStrength,
                VibratorTunerWrapper.SECURE_KEY_HEAVY_EFFECT to cfg.heavyEffectId,
                VibratorTunerWrapper.SECURE_KEY_HEAVY_STRENGTH to cfg.heavyMaxStrength,
                VibratorTunerWrapper.SECURE_KEY_SLIDER_EFFECT to cfg.sliderEffectId,
                VibratorTunerWrapper.SECURE_KEY_SLIDER_STRENGTH to cfg.sliderStrength,
                VibratorTunerWrapper.SECURE_KEY_QUICK_RISE_EFFECT to cfg.quickRiseEffectId,
                VibratorTunerWrapper.SECURE_KEY_QUICK_RISE_STRENGTH to cfg.quickRiseMaxStrength,
                VibratorTunerWrapper.SECURE_KEY_QUICK_FALL_EFFECT to cfg.quickFallEffectId,
                VibratorTunerWrapper.SECURE_KEY_QUICK_FALL_STRENGTH to cfg.quickFallMaxStrength,
                VibratorTunerWrapper.SECURE_KEY_SLOW_RISE_EFFECT to cfg.slowRiseEffectId,
                VibratorTunerWrapper.SECURE_KEY_SLOW_RISE_STRENGTH to cfg.slowRiseMaxStrength,
                VibratorTunerWrapper.SECURE_KEY_SPIN_EFFECT to cfg.spinEffectId,
                VibratorTunerWrapper.SECURE_KEY_SPIN_STRENGTH to cfg.spinMaxStrength,
            )

        for ((pref, binding) in strengthPrefs.values) {
            val gainPct =
                VibratorTunerWrapper.normalizeGainPct(
                    secureValues[binding.secureKey] ?: VibratorConfig.STRENGTH_MEDIUM
                )
            pref.value = gainPctToSliderTick(gainPct)
            pref.summary = strengthSummaryLabel(gainPct)
        }

        for ((pref, binding) in effectPrefs.values) {
            val effectId = secureValues[binding.secureKey] ?: binding.defaultValue
            pref.summary = entryLabelForEffect(binding, effectId)
        }

        scaleBiasSlider?.value = cfg.scaleBias.coerceIn(-2, 2)

        refreshSavedProfileSummary()
    }

    private fun loadHardwareTelemetry() {
        if (!supported) {
            hardwareInfoPref?.title = getString(R.string.haptics_hardware_info_fallback)
            return
        }
        ioExecutor.execute {
            val telemetry = VibratorTunerWrapper.getHardwareTelemetry()
            mainHandler.post {
                if (!isAdded) return@post
                hardwareInfoPref?.title =
                    when {
                        telemetry != null && telemetry.first > 0f && telemetry.second > 0f ->
                            getString(
                                R.string.haptics_hardware_info_summary,
                                telemetry.first,
                                telemetry.second,
                            )
                        telemetry != null && telemetry.first > 0f ->
                            getString(R.string.haptics_hardware_info_f0_only, telemetry.first)
                        else -> getString(R.string.haptics_hardware_info_fallback)
                    }
            }
        }
    }

    private fun resetCalibration() {
        VibratorTunerWrapper.resetSettings(context.contentResolver)
        pushCurrentConfigToHal()
        playTestPattern(TestPattern.CONFIRM)
    }

    private fun pushCurrentConfigToHal() {
        val cfg = VibratorTunerWrapper.readConfig(context.contentResolver)
        ioExecutor.execute { VibratorTunerWrapper.applyConfig(cfg, waitForDeclared = false) }
    }

    private fun putSecureInt(key: String, value: Int) {
        Settings.Secure.putIntForUser(context.contentResolver, key, value, UserHandle.USER_CURRENT)
    }

    private fun sliderTickToGainPct(tick: Int): Int =
        if (tick <= SLIDER_OFF_TICK) VibratorConfig.STRENGTH_OFF
        else tick.coerceIn(VibratorConfig.STRENGTH_LIGHT, VibratorConfig.STRENGTH_STRONG)

    private fun gainPctToSliderTick(gainPct: Int): Int =
        if (gainPct <= VibratorConfig.STRENGTH_OFF) SLIDER_OFF_TICK
        else gainPct.coerceIn(VibratorConfig.STRENGTH_LIGHT, VibratorConfig.STRENGTH_STRONG)

    private fun strengthShortLabel(sliderTick: Int): String {
        val gainPct = sliderTickToGainPct(sliderTick)
        return if (gainPct <= VibratorConfig.STRENGTH_OFF) "0%" else "$gainPct%"
    }

    private fun strengthSummaryLabel(gainPct: Int): String =
        when (val normalized = VibratorTunerWrapper.normalizeGainPct(gainPct)) {
            VibratorConfig.STRENGTH_OFF -> getString(R.string.haptics_strength_off)
            VibratorConfig.STRENGTH_LIGHT -> getString(R.string.haptics_strength_light)
            VibratorConfig.STRENGTH_MEDIUM -> getString(R.string.haptics_strength_medium)
            VibratorConfig.STRENGTH_STRONG -> getString(R.string.haptics_strength_strong)
            else -> getString(R.string.haptics_strength_custom, normalized)
        }

    private fun entryLabelForEffect(binding: EffectBinding, effectId: Int): String {
        val entries = resources.getStringArray(binding.entriesResId)
        val values = resources.getStringArray(binding.valuesResId)
        val target = effectId.toString()
        val index = values.indexOf(target).let { if (it >= 0) it else 0 }
        return entries.getOrNull(index) ?: target
    }

    private fun showWaveformPickerDialog(pref: Preference, binding: EffectBinding) {
        val ctx = context ?: return
        val view =
            LayoutInflater.from(ctx).inflate(R.layout.dialog_haptics_waveform_picker, null)
        view.findViewById<ImageView>(R.id.waveform_illustration)
            .setImageResource(binding.illustrationResId)
        view.findViewById<TextView>(R.id.waveform_description)
            .setText(binding.descriptionResId)

        val radioGroup = view.findViewById<RadioGroup>(R.id.waveform_radio_group)
        val entries = resources.getStringArray(binding.entriesResId)
        val values = resources.getStringArray(binding.valuesResId)
        val currentValue =
            Settings.Secure.getIntForUser(
                    ctx.contentResolver,
                    binding.secureKey,
                    binding.defaultValue,
                    UserHandle.USER_CURRENT,
                )
                .toString()

        entries.forEachIndexed { idx, label ->
            val valueStr = values.getOrNull(idx) ?: return@forEachIndexed
            val rb =
                RadioButton(ctx).apply {
                    id = idx + 1000
                    text = label
                    isChecked = (valueStr == currentValue)
                    setPadding(16, 20, 8, 20)
                }
            radioGroup.addView(rb)
            rb.setOnClickListener {
                val intValue = valueStr.toIntOrNull() ?: return@setOnClickListener
                putSecureInt(binding.secureKey, intValue)
                pref.summary = label
                pushCurrentConfigToHal()
                mainHandler.postDelayed(
                    { playSinglePrimitive(binding.previewPrimitive, binding.previewScale) },
                    60L,
                )
            }
        }

        AlertDialog.Builder(ctx)
            .setTitle(pref.title)
            .setView(view)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showWaveformGuideDialog() {
        val ctx = context ?: return
        val inflater = LayoutInflater.from(ctx)
        val root = inflater.inflate(R.layout.dialog_haptics_waveform_guide, null)
        val container = root.findViewById<LinearLayout>(R.id.waveform_guide_container)
        val cfg = VibratorTunerWrapper.readConfig(ctx.contentResolver)
        val currentIds =
            mapOf(
                VibratorTunerWrapper.SECURE_KEY_CLICK_EFFECT to cfg.clickEffectId,
                VibratorTunerWrapper.SECURE_KEY_TICK_EFFECT to cfg.tickEffectId,
                VibratorTunerWrapper.SECURE_KEY_HEAVY_EFFECT to cfg.heavyEffectId,
                VibratorTunerWrapper.SECURE_KEY_SLIDER_EFFECT to cfg.sliderEffectId,
                VibratorTunerWrapper.SECURE_KEY_QUICK_RISE_EFFECT to cfg.quickRiseEffectId,
                VibratorTunerWrapper.SECURE_KEY_QUICK_FALL_EFFECT to cfg.quickFallEffectId,
                VibratorTunerWrapper.SECURE_KEY_SLOW_RISE_EFFECT to cfg.slowRiseEffectId,
                VibratorTunerWrapper.SECURE_KEY_SPIN_EFFECT to cfg.spinEffectId,
            )

        for (binding in EFFECT_BINDINGS) {
            val item = inflater.inflate(R.layout.item_haptics_waveform_guide, container, false)
            item.findViewById<ImageView>(R.id.guide_illustration)
                .setImageResource(binding.illustrationResId)
            item.findViewById<TextView>(R.id.guide_title).text =
                effectPrefs[binding.prefKey]?.first?.title ?: binding.prefKey
            val effectId = currentIds[binding.secureKey] ?: binding.defaultValue
            item.findViewById<TextView>(R.id.guide_current_effect).text =
                entryLabelForEffect(binding, effectId)
            item.findViewById<TextView>(R.id.guide_description)
                .setText(binding.descriptionResId)
            item.setOnClickListener {
                playSinglePrimitive(binding.previewPrimitive, binding.previewScale)
            }
            container.addView(item)
        }

        AlertDialog.Builder(ctx)
            .setTitle(R.string.haptics_waveform_guide_title)
            .setView(root)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun setupSavedProfileControls() {
        savedProfileSelectPref?.apply {
            isPersistent = false
            setOnPreferenceClickListener { pref ->
                showSavedProfilePopupMenu(pref)
                true
            }
        }

        savedProfileActions?.apply {
            setButton1Text(R.string.haptics_action_save)
            setButton1Icon(R.drawable.ic_haptics_save)
            setButton1OnClickListener { showSaveProfileDialog() }

            setButton2Text(R.string.haptics_action_export)
            setButton2Icon(R.drawable.ic_haptics_export)
            setButton2OnClickListener { showExportProfileDialog() }

            setButton3Text(R.string.haptics_action_import)
            setButton3Icon(R.drawable.ic_haptics_import)
            setButton3OnClickListener { showImportProfileDialog() }

            setButton4Text(R.string.haptics_action_delete)
            setButton4Icon(R.drawable.ic_haptics_delete)
            setButton4OnClickListener { showDeleteProfileDialog() }
        }
    }

    private fun refreshSavedProfileSummary() {
        val ctx = context ?: return
        val saved = VibratorTunerWrapper.getSavedProfiles(ctx.contentResolver)
        val activeName = VibratorTunerWrapper.getActiveSavedProfileName(ctx.contentResolver)
        val hasActive = saved.any { it.name.equals(activeName, ignoreCase = true) }

        savedProfileSelectPref?.summary =
            when {
                saved.isEmpty() -> getString(R.string.haptics_saved_profile_none)
                hasActive ->
                    getString(R.string.haptics_saved_profile_summary_active, activeName, saved.size)
                else -> getString(R.string.haptics_saved_profile_summary_pick, saved.size)
            }
        savedProfileActions?.setButton4Enabled(saved.isNotEmpty())
    }

    private fun findPreferenceRowView(pref: Preference): View? {
        val rv = listView ?: return null
        val targetTitle = pref.title?.toString() ?: return rv
        for (i in 0 until rv.childCount) {
            val child = rv.getChildAt(i)
            val titleView = child.findViewById<TextView>(android.R.id.title)
            if (titleView?.text?.toString() == targetTitle) {
                return child
            }
        }
        return rv
    }

    private fun showSavedProfilePopupMenu(pref: Preference) {
        val ctx = context ?: return
        val saved = VibratorTunerWrapper.getSavedProfiles(ctx.contentResolver)
        val activeName = VibratorTunerWrapper.getActiveSavedProfileName(ctx.contentResolver)
        val anchor = findPreferenceRowView(pref) ?: return
        val popup = PopupMenu(ctx, anchor)
        val menu = popup.menu

        saved.forEachIndexed { index, item ->
            val label =
                if (item.name.equals(activeName, ignoreCase = true)) {
                    "\u2713 ${item.name}"
                } else {
                    item.name
                }
            menu.add(Menu.NONE, index, index, label)
        }
        menu.add(Menu.NONE, MENU_ID_SAVE_NEW, saved.size, R.string.haptics_saved_profile_add_menu)

        popup.setOnMenuItemClickListener { menuItem ->
            if (menuItem.itemId == MENU_ID_SAVE_NEW) {
                showSaveProfileDialog()
                return@setOnMenuItemClickListener true
            }
            val chosen = saved.getOrNull(menuItem.itemId) ?: return@setOnMenuItemClickListener false
            VibratorTunerWrapper.applySavedProfile(ctx.contentResolver, chosen)
            pushCurrentConfigToHal()
            refreshUi()
            playTestPattern(TestPattern.CONFIRM)
            Toast.makeText(
                    ctx,
                    getString(R.string.haptics_loaded_toast, chosen.name),
                    Toast.LENGTH_SHORT,
                )
                .show()
            true
        }
        popup.show()
    }

    private fun buildWrappedInputBox(initialText: String, hintRes: Int, singleLine: Boolean): Pair<FrameLayout, EditText> {
        val ctx = context!!
        val padH = (20 * resources.displayMetrics.density).toInt()
        val padV = (8 * resources.displayMetrics.density).toInt()
        val container =
            FrameLayout(ctx).apply {
                setPadding(padH, padV, padH, 0)
            }
        val input =
            EditText(ctx).apply {
                setHint(hintRes)
                setText(initialText)
                isSingleLine = singleLine
                if (!singleLine) {
                    minLines = 5
                    maxLines = 10
                }
                layoutParams =
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
            }
        container.addView(input)
        return container to input
    }

    private fun showSaveProfileDialog() {
        val ctx = context ?: return
        val cr = ctx.contentResolver
        val activeName = VibratorTunerWrapper.getActiveSavedProfileName(cr)
        val savedCount = VibratorTunerWrapper.getSavedProfiles(cr).size
        val defaultName =
            activeName.ifBlank { "Custom ${savedCount + 1}" }
        val (box, input) = buildWrappedInputBox(defaultName, R.string.haptics_save_dialog_hint, true)
        input.setSelection(defaultName.length)

        AlertDialog.Builder(ctx)
            .setTitle(R.string.haptics_save_dialog_title)
            .setView(box)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text.toString().trim().ifEmpty { defaultName }
                val cfg = VibratorTunerWrapper.readConfig(cr)
                val saved = VibratorTunerWrapper.savedProfileFromConfig(name, cfg)
                VibratorTunerWrapper.saveCustomProfile(cr, saved)
                refreshSavedProfileSummary()
                Toast.makeText(
                        ctx,
                        getString(R.string.haptics_save_toast, saved.name),
                        Toast.LENGTH_SHORT,
                    )
                    .show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun resolveTargetSavedProfile(): VibratorTunerWrapper.SavedCustomProfile {
        val cr = context.contentResolver
        val saved = VibratorTunerWrapper.getSavedProfiles(cr)
        val activeName = VibratorTunerWrapper.getActiveSavedProfileName(cr)
        return saved.firstOrNull { it.name.equals(activeName, ignoreCase = true) }
            ?: VibratorTunerWrapper.savedProfileFromConfig(
                activeName.ifBlank { "Custom calibration" },
                VibratorTunerWrapper.readConfig(cr),
            )
    }

    private fun showExportProfileDialog() {
        val ctx = context ?: return
        val cr = ctx.contentResolver
        val saved = VibratorTunerWrapper.getSavedProfiles(cr)
        if (saved.size > 1) {
            val activeName = VibratorTunerWrapper.getActiveSavedProfileName(cr)
            val labels = saved.map { it.name }.toTypedArray()
            val preChecked =
                saved.indexOfFirst { it.name.equals(activeName, ignoreCase = true) }.coerceAtLeast(0)
            var chosenIdx = preChecked
            AlertDialog.Builder(ctx)
                .setTitle(R.string.haptics_action_export)
                .setSingleChoiceItems(labels, preChecked) { _, which -> chosenIdx = which }
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    showExportMethodDialog(saved[chosenIdx])
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            return
        }
        showExportMethodDialog(resolveTargetSavedProfile())
    }

    private fun showExportMethodDialog(profile: VibratorTunerWrapper.SavedCustomProfile) {
        val ctx = context ?: return
        val jsonText = profile.toJson().toString(2)
        val options =
            arrayOf(
                getString(R.string.haptics_export_save_file),
                getString(R.string.haptics_export_share),
                getString(R.string.haptics_export_copy),
            )
        AlertDialog.Builder(ctx)
            .setTitle(getString(R.string.haptics_export_dialog_title, profile.name))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        pendingExportProfile = profile
                        val slug =
                            profile.name
                                .lowercase()
                                .replace(Regex("[^a-z0-9]+"), "_")
                                .trim('_')
                                .ifEmpty { "custom" }
                        val intent =
                            Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "application/json"
                                putExtra(Intent.EXTRA_TITLE, "haptics_$slug.json")
                            }
                        runCatching { startActivityForResult(intent, REQ_EXPORT_FILE) }
                    }
                    1 -> {
                        val shareIntent =
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Haptic profile: ${profile.name}")
                                putExtra(Intent.EXTRA_TEXT, jsonText)
                            }
                        startActivity(Intent.createChooser(shareIntent, profile.name))
                    }
                    2 -> {
                        val cb = ctx.getSystemService(ClipboardManager::class.java)
                        cb?.setPrimaryClip(ClipData.newPlainText(profile.name, jsonText))
                        Toast.makeText(
                                ctx,
                                getString(R.string.haptics_export_copied, profile.name),
                                Toast.LENGTH_SHORT,
                            )
                            .show()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showImportProfileDialog() {
        val ctx = context ?: return
        val options =
            arrayOf(
                getString(R.string.haptics_import_from_file),
                getString(R.string.haptics_import_paste),
            )
        AlertDialog.Builder(ctx)
            .setTitle(R.string.haptics_import_dialog_title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val intent =
                            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "*/*"
                                putExtra(
                                    Intent.EXTRA_MIME_TYPES,
                                    arrayOf("application/json", "text/plain"),
                                )
                            }
                        runCatching { startActivityForResult(intent, REQ_IMPORT_FILE) }
                    }
                    1 -> showPasteImportDialog()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showPasteImportDialog() {
        val ctx = context ?: return
        val cb = ctx.getSystemService(ClipboardManager::class.java)
        val clipText = cb?.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty().trim()
        val initial = if (clipText.startsWith("{")) clipText else ""
        val (box, input) = buildWrappedInputBox(initial, R.string.haptics_import_paste_hint, false)

        AlertDialog.Builder(ctx)
            .setTitle(R.string.haptics_import_paste)
            .setView(box)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                importProfileJson(input.text.toString())
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun importProfileJson(raw: String) {
        val ctx = context ?: return
        val parsed = VibratorTunerWrapper.parseSavedProfileJson(raw)
        if (parsed == null) {
            Toast.makeText(ctx, R.string.haptics_import_invalid, Toast.LENGTH_SHORT).show()
            return
        }
        val cr = ctx.contentResolver
        VibratorTunerWrapper.saveCustomProfile(cr, parsed)
        VibratorTunerWrapper.applySavedProfile(cr, parsed)
        pushCurrentConfigToHal()
        refreshUi()
        playTestPattern(TestPattern.CONFIRM)
        Toast.makeText(
                ctx,
                getString(R.string.haptics_import_success, parsed.name),
                Toast.LENGTH_SHORT,
            )
            .show()
    }

    private fun showDeleteProfileDialog() {
        val ctx = context ?: return
        val cr = ctx.contentResolver
        val saved = VibratorTunerWrapper.getSavedProfiles(cr)
        if (saved.isEmpty()) return
        val activeName = VibratorTunerWrapper.getActiveSavedProfileName(cr)
        val target =
            saved.firstOrNull { it.name.equals(activeName, ignoreCase = true) } ?: saved.first()

        AlertDialog.Builder(ctx)
            .setTitle(getString(R.string.haptics_delete_dialog_title, target.name))
            .setMessage(R.string.haptics_delete_dialog_message)
            .setPositiveButton(R.string.haptics_action_delete) { _, _ ->
                VibratorTunerWrapper.deleteSavedProfile(cr, target.name)
                refreshSavedProfileSummary()
                Toast.makeText(
                        ctx,
                        getString(R.string.haptics_delete_toast, target.name),
                        Toast.LENGTH_SHORT,
                    )
                    .show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val ctx = context ?: return
        val uri = data?.data
        if (resultCode != Activity.RESULT_OK || uri == null) {
            if (requestCode == REQ_EXPORT_FILE) pendingExportProfile = null
            return
        }
        when (requestCode) {
            REQ_EXPORT_FILE -> {
                val profile = pendingExportProfile ?: resolveTargetSavedProfile()
                pendingExportProfile = null
                val ok =
                    runCatching {
                            ctx.contentResolver.openOutputStream(uri)?.use { os ->
                                os.write(profile.toJson().toString(2).toByteArray(Charsets.UTF_8))
                                true
                            } ?: false
                        }
                        .getOrDefault(false)
                Toast.makeText(
                        ctx,
                        if (ok) getString(R.string.haptics_export_file_saved, profile.name)
                        else getString(R.string.haptics_export_file_failed),
                        Toast.LENGTH_SHORT,
                    )
                    .show()
            }
            REQ_IMPORT_FILE -> {
                val raw =
                    runCatching {
                            ctx.contentResolver.openInputStream(uri)?.use {
                                it.bufferedReader(Charsets.UTF_8).readText()
                            }
                        }
                        .getOrNull()
                        .orEmpty()
                importProfileJson(raw)
            }
        }
    }

    private fun playSinglePrimitive(primitiveId: Int, scale: Float) {
        val vib = vibrator ?: return
        val effect = VibrationEffect.startComposition().addPrimitive(primitiveId, scale).compose()
        runCatching { vib.vibrate(effect, HARDWARE_ATTRS) }
    }

    private fun playTestPattern(pattern: TestPattern) {
        val vib = vibrator ?: return
        val (effect, attrs) =
            when (pattern) {
                TestPattern.TICK ->
                    VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.62f)
                        .compose() to TOUCH_ATTRS
                TestPattern.KEY_PRESS ->
                    VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.62f)
                        .compose() to TOUCH_ATTRS
                TestPattern.KEY_TAP ->
                    VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.62f)
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.62f, 45)
                        .compose() to TOUCH_ATTRS
                TestPattern.DOUBLE_TAP ->
                    VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.70f)
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.70f, 70)
                        .compose() to TOUCH_ATTRS
                TestPattern.GESTURE ->
                    VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.75f)
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.75f, 70)
                        .compose() to TOUCH_ATTRS
                TestPattern.CONFIRM ->
                    VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.65f)
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.70f, 52)
                        .compose() to HARDWARE_ATTRS
                TestPattern.HEAVY ->
                    VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f)
                        .compose() to HARDWARE_ATTRS
                TestPattern.SLIDER_TICK ->
                    VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.13f)
                        .compose() to TOUCH_ATTRS
            }
        runCatching { vib.vibrate(effect, attrs) }
    }

    private enum class TestPattern {
        TICK,
        KEY_PRESS,
        KEY_TAP,
        DOUBLE_TAP,
        GESTURE,
        CONFIRM,
        HEAVY,
        SLIDER_TICK,
    }

    companion object {
        private const val KEY_INTRO = "haptics_intro"
        private const val KEY_STOCK_SLIDER_SAFETY = "haptics_stock_slider_safety"
        private const val KEY_TEST_CATEGORY = "haptics_test_category"
        private const val KEY_TEST_ACTIONS_1 = "haptics_test_actions_1"
        private const val KEY_TEST_ACTIONS_2 = "haptics_test_actions_2"
        private const val KEY_TEST_SLIDER = "haptics_test_slider"
        private const val KEY_CUSTOM_STRENGTH_CATEGORY = "haptics_custom_strength_category"
        private const val KEY_CUSTOM_WAVEFORM_CATEGORY = "haptics_custom_waveform_category"
        private const val KEY_WAVEFORM_GUIDE = "haptics_waveform_guide"
        private const val KEY_SAVED_PROFILES_CATEGORY = "haptics_saved_profiles_category"
        private const val KEY_SAVED_PROFILE_SELECT = "haptics_saved_profile_select"
        private const val KEY_SAVED_PROFILE_ACTIONS = "haptics_saved_profile_actions"
        private const val KEY_SCALE_BIAS = "haptics_scale_bias"
        private const val KEY_HARDWARE_INFO = "haptics_hardware_info"
        private const val SLIDER_OFF_TICK = 29
        private const val MENU_ID_SAVE_NEW = 9001
        private const val REQ_EXPORT_FILE = 4101
        private const val REQ_IMPORT_FILE = 4102

        private val PROFILE_ENTRIES =
            listOf(
                "haptics_profile_stock" to VibratorConfig.PROFILE_STOCK,
                "haptics_profile_balanced" to VibratorConfig.PROFILE_BALANCED,
                "haptics_profile_crisp_tight" to VibratorConfig.PROFILE_CRISP_TIGHT,
                "haptics_profile_thin_light" to VibratorConfig.PROFILE_THIN_LIGHT,
                "haptics_profile_custom" to VibratorConfig.PROFILE_CUSTOM,
            )

        private val STRENGTH_BINDINGS =
            listOf(
                StrengthBinding(
                    "haptics_click_strength",
                    VibratorTunerWrapper.SECURE_KEY_CLICK_STRENGTH,
                    VibrationEffect.Composition.PRIMITIVE_CLICK,
                    1.0f,
                ),
                StrengthBinding(
                    "haptics_tick_strength",
                    VibratorTunerWrapper.SECURE_KEY_TICK_STRENGTH,
                    VibrationEffect.Composition.PRIMITIVE_TICK,
                    1.0f,
                ),
                StrengthBinding(
                    "haptics_heavy_strength",
                    VibratorTunerWrapper.SECURE_KEY_HEAVY_STRENGTH,
                    VibrationEffect.Composition.PRIMITIVE_THUD,
                    1.0f,
                ),
                StrengthBinding(
                    "haptics_slider_strength",
                    VibratorTunerWrapper.SECURE_KEY_SLIDER_STRENGTH,
                    VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
                    0.15f,
                ),
                StrengthBinding(
                    "haptics_quick_rise_strength",
                    VibratorTunerWrapper.SECURE_KEY_QUICK_RISE_STRENGTH,
                    VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,
                    1.0f,
                ),
                StrengthBinding(
                    "haptics_quick_fall_strength",
                    VibratorTunerWrapper.SECURE_KEY_QUICK_FALL_STRENGTH,
                    VibrationEffect.Composition.PRIMITIVE_QUICK_FALL,
                    1.0f,
                ),
                StrengthBinding(
                    "haptics_slow_rise_strength",
                    VibratorTunerWrapper.SECURE_KEY_SLOW_RISE_STRENGTH,
                    VibrationEffect.Composition.PRIMITIVE_SLOW_RISE,
                    1.0f,
                ),
                StrengthBinding(
                    "haptics_spin_strength",
                    VibratorTunerWrapper.SECURE_KEY_SPIN_STRENGTH,
                    VibrationEffect.Composition.PRIMITIVE_SPIN,
                    1.0f,
                ),
            )

        private val EFFECT_BINDINGS =
            listOf(
                EffectBinding(
                    "haptics_click_effect",
                    VibratorTunerWrapper.SECURE_KEY_CLICK_EFFECT,
                    88,
                    VibrationEffect.Composition.PRIMITIVE_CLICK,
                    1.0f,
                    R.array.haptics_click_effect_entries,
                    R.array.haptics_click_effect_values,
                    R.drawable.haptics_illus_click,
                    R.string.haptics_desc_click,
                ),
                EffectBinding(
                    "haptics_tick_effect",
                    VibratorTunerWrapper.SECURE_KEY_TICK_EFFECT,
                    86,
                    VibrationEffect.Composition.PRIMITIVE_TICK,
                    1.0f,
                    R.array.haptics_tick_effect_entries,
                    R.array.haptics_tick_effect_values,
                    R.drawable.haptics_illus_tick,
                    R.string.haptics_desc_tick,
                ),
                EffectBinding(
                    "haptics_heavy_effect",
                    VibratorTunerWrapper.SECURE_KEY_HEAVY_EFFECT,
                    163,
                    VibrationEffect.Composition.PRIMITIVE_THUD,
                    1.0f,
                    R.array.haptics_heavy_effect_entries,
                    R.array.haptics_heavy_effect_values,
                    R.drawable.haptics_illus_heavy,
                    R.string.haptics_desc_heavy,
                ),
                EffectBinding(
                    "haptics_slider_effect",
                    VibratorTunerWrapper.SECURE_KEY_SLIDER_EFFECT,
                    92,
                    VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
                    0.15f,
                    R.array.haptics_slider_effect_entries,
                    R.array.haptics_slider_effect_values,
                    R.drawable.haptics_illus_slider,
                    R.string.haptics_desc_slider,
                ),
                EffectBinding(
                    "haptics_quick_rise_effect",
                    VibratorTunerWrapper.SECURE_KEY_QUICK_RISE_EFFECT,
                    162,
                    VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,
                    1.0f,
                    R.array.haptics_quick_rise_effect_entries,
                    R.array.haptics_quick_rise_effect_values,
                    R.drawable.haptics_illus_quick_rise,
                    R.string.haptics_desc_quick_rise,
                ),
                EffectBinding(
                    "haptics_quick_fall_effect",
                    VibratorTunerWrapper.SECURE_KEY_QUICK_FALL_EFFECT,
                    188,
                    VibrationEffect.Composition.PRIMITIVE_QUICK_FALL,
                    1.0f,
                    R.array.haptics_quick_fall_effect_entries,
                    R.array.haptics_quick_fall_effect_values,
                    R.drawable.haptics_illus_quick_fall,
                    R.string.haptics_desc_quick_fall,
                ),
                EffectBinding(
                    "haptics_slow_rise_effect",
                    VibratorTunerWrapper.SECURE_KEY_SLOW_RISE_EFFECT,
                    57,
                    VibrationEffect.Composition.PRIMITIVE_SLOW_RISE,
                    1.0f,
                    R.array.haptics_slow_rise_effect_entries,
                    R.array.haptics_slow_rise_effect_values,
                    R.drawable.haptics_illus_slow_rise,
                    R.string.haptics_desc_slow_rise,
                ),
                EffectBinding(
                    "haptics_spin_effect",
                    VibratorTunerWrapper.SECURE_KEY_SPIN_EFFECT,
                    170,
                    VibrationEffect.Composition.PRIMITIVE_SPIN,
                    1.0f,
                    R.array.haptics_spin_effect_entries,
                    R.array.haptics_spin_effect_values,
                    R.drawable.haptics_illus_spin,
                    R.string.haptics_desc_spin,
                ),
            )

        private val TOUCH_ATTRS =
            VibrationAttributes.Builder()
                .setUsage(VibrationAttributes.USAGE_TOUCH)
                .build()

        private val HARDWARE_ATTRS =
            VibrationAttributes.Builder()
                .setUsage(VibrationAttributes.USAGE_HARDWARE_FEEDBACK)
                .build()
    }
}
