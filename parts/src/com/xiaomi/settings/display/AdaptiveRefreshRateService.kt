/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.display

import android.app.KeyguardManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.hardware.display.DisplayManager
import android.hardware.input.InputManager
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.os.UserHandle
import android.provider.Settings
import android.telecom.TelecomManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import android.view.Display
import android.view.InputEvent
import android.view.InputEventReceiver
import android.view.InputMonitor
import android.view.MotionEvent
import java.io.File
import kotlin.math.abs

/**
 * Enables 10–120 Hz LTPO Adaptive Refresh Rate on Android 16 ARR displays:
 * - Unlocks MIN_REFRESH_RATE down to 10 Hz and switches panel dimming from DC to PWM
 * - Locks 1 Hz during Always-On Display (DOZE) and steps static screens down cleanly to 10 Hz
 *   without double-cycling, while waking immediately on touch, call/ringtone, or UI redraws
 * - Optionally matches 24/30/60 Hz during steady video playback with touch cooldown protection
 */
class AdaptiveRefreshRateService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    private var proEnabled = false
    private var videoMatchEnabled = true
    private var displayState = Display.STATE_ON
    private var extremeMode = false
    private var callActive = false
    private var appliedPwmDimming: Boolean? = null

    private var panelMinHz = 1.0f
    private var idleFloorHz = DEFAULT_IDLE_FLOOR_HZ
    private var panelMaxHz = 120.0f
    private var userPeakHz = 120.0f

    // Dynamic peak cap state (0f when unlocked)
    private var lockedMode = LockMode.NONE
    private var lockedTargetHz = 0f

    // Idle & video detection counters
    private var idleTicks = 0
    private var idleSettleTicks = 0
    private var consecutiveClassCount = 0
    private var consecutiveOutCount = 0
    private var prevClass = RateClass.NONE
    private var windowMinFps = 0f
    private var lastTouchElapsedMs = 0L
    private var nonTouchWakeUntilMs = 0L

    private var crtcNodes: List<File> = emptyList()
    private var inputMonitor: InputMonitor? = null
    private var inputReceiver: InputEventReceiver? = null
    private var pollRunning = false
    private var telephonyCallback: TelephonyCallback? = null

    private val settingsObserver =
        object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                onSettingChanged(uri)
            }
        }

    private val dimmingReapplyRunnable = Runnable {
        if (displayState == Display.STATE_ON) {
            appliedPwmDimming = null
            applyDimmingMode(enablePwm = proEnabled)
        }
    }

    private val displayListener =
        object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit

            override fun onDisplayRemoved(displayId: Int) = Unit

            override fun onDisplayChanged(displayId: Int) {
                if (displayId != Display.DEFAULT_DISPLAY) return
                val newState = readDefaultDisplayState()
                if (newState != displayState) {
                    onDisplayStateTransition(newState)
                }
            }
        }

    private val screenReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_ON -> onDisplayStateTransition(Display.STATE_ON)
                    Intent.ACTION_USER_PRESENT -> {
                        nonTouchWakeUntilMs = SystemClock.elapsedRealtime() + IDLE_TOUCH_COOLDOWN_MS
                        if (lockedMode != LockMode.VIDEO) {
                            unlockCapIfActive()
                        }
                        resetCounters()
                        appliedPwmDimming = null
                        syncAllState()
                    }
                    Intent.ACTION_SCREEN_OFF -> onDisplayStateTransition(readDefaultDisplayState())
                    Intent.ACTION_SHUTDOWN -> {
                        handler.removeCallbacks(dimmingReapplyRunnable)
                        stopMonitorLoop()
                        unlockCapIfActive()
                        syncMatchContentFrameRate(allowContentSwitching = true)
                    }
                }
            }
        }

    private val pollRunnable =
        object : Runnable {
            override fun run() {
                if (!pollRunning) return
                stepScenarioMonitor()
                if (pollRunning) {
                    handler.postDelayed(this, POLL_INTERVAL_MS)
                }
            }
        }

    override fun onCreate() {
        super.onCreate()
        if (!isSupported(this)) {
            if (DEBUG) Log.i(TAG, "ARR not supported on this display, stopping")
            stopSelf()
            return
        }

        val rates = getSupportedArrRates(this)
        panelMinHz = rates.minOrNull() ?: 1.0f
        idleFloorHz =
            rates.filter { it >= DEFAULT_IDLE_FLOOR_HZ - 0.1f }.minOrNull()
                ?: maxOf(panelMinHz, DEFAULT_IDLE_FLOOR_HZ)
        panelMaxHz = getMaxModeRefreshRate(this)
        crtcNodes = discoverCrtcNodes()
        Log.i(
            TAG,
            "Started ARR Pro service: panel=${panelMinHz}..${panelMaxHz}Hz, idleFloor=${idleFloorHz}Hz, crtcNodes=${crtcNodes.size}",
        )

        initializeDefaultStateIfNeeded()
        recoverOrphanedLowPeakIfNeeded()
        registerObservers()
        displayState = readDefaultDisplayState()
        syncAllState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isSupported(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        syncAllState()
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(dimmingReapplyRunnable)
        stopMonitorLoop()
        unlockCapIfActive()
        syncMatchContentFrameRate(allowContentSwitching = true)
        runCatching { contentResolver.unregisterContentObserver(settingsObserver) }
        runCatching { getSystemService(DisplayManager::class.java)?.unregisterDisplayListener(displayListener) }
        runCatching {
            telephonyCallback?.let { cb ->
                getSystemService(TelephonyManager::class.java)?.unregisterTelephonyCallback(cb)
            }
        }
        runCatching { unregisterReceiver(screenReceiver) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun initializeDefaultStateIfNeeded() {
        if (Settings.Secure.getIntForUser(
                contentResolver, SECURE_KEY_PRO, -1, UserHandle.USER_CURRENT) == -1
        ) {
            val currentMin = readSystemFloat(Settings.System.MIN_REFRESH_RATE, DEFAULT_NON_PRO_MIN_HZ)
            if (currentMin >= DEFAULT_NON_PRO_MIN_HZ) {
                prefs.edit().putFloat(PREF_SAVED_MIN_HZ, currentMin).apply()
            }
            Settings.Secure.putIntForUser(
                contentResolver, SECURE_KEY_PRO, 1, UserHandle.USER_CURRENT)
        }
        if (Settings.Secure.getIntForUser(
                contentResolver, SECURE_KEY_VIDEO_MATCH, -1, UserHandle.USER_CURRENT) == -1
        ) {
            Settings.Secure.putIntForUser(
                contentResolver, SECURE_KEY_VIDEO_MATCH, 1, UserHandle.USER_CURRENT)
        }
    }

    private fun registerObservers() {
        val uris =
            listOf(
                Settings.Secure.getUriFor(SECURE_KEY_PRO),
                Settings.Secure.getUriFor(SECURE_KEY_VIDEO_MATCH),
                Settings.System.getUriFor(Settings.System.MIN_REFRESH_RATE),
                Settings.System.getUriFor(Settings.System.PEAK_REFRESH_RATE),
                Settings.System.getUriFor(Settings.System.EXTREME_REFRESH_RATE),
            )
        uris.forEach { uri ->
            contentResolver.registerContentObserver(uri, false, settingsObserver, UserHandle.USER_ALL)
        }

        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_SHUTDOWN)
            }
        registerReceiver(screenReceiver, filter)

        getSystemService(DisplayManager::class.java)
            ?.registerDisplayListener(displayListener, handler)

        val callback =
            object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    val active = state != TelephonyManager.CALL_STATE_IDLE
                    if (active == callActive) return
                    callActive = active
                    if (active) {
                        if (DEBUG) Log.d(TAG, "Call state=$state: unlocking to ${userPeakHz}Hz")
                        unlockCapIfActive()
                        resetCounters()
                    }
                }
            }
        runCatching {
            getSystemService(TelephonyManager::class.java)
                ?.registerTelephonyCallback(mainExecutor, callback)
            telephonyCallback = callback
        }.onFailure { e ->
            Log.w(TAG, "Failed to register TelephonyCallback", e)
        }
    }

    private fun readDefaultDisplayState(): Int =
        getSystemService(DisplayManager::class.java)
            ?.getDisplay(Display.DEFAULT_DISPLAY)
            ?.state ?: Display.STATE_OFF

    private fun isDozeState(state: Int): Boolean =
        state == Display.STATE_DOZE || state == Display.STATE_DOZE_SUSPEND

    private fun onDisplayStateTransition(newState: Int) {
        displayState = newState
        when {
            newState == Display.STATE_ON -> {
                nonTouchWakeUntilMs = SystemClock.elapsedRealtime() + IDLE_TOUCH_COOLDOWN_MS
                appliedPwmDimming = null
                if (lockedMode == LockMode.DOZE) {
                    unlockCapIfActive()
                }
                syncAllState()
                handler.removeCallbacks(dimmingReapplyRunnable)
                handler.postDelayed(dimmingReapplyRunnable, SCREEN_ON_DIMMING_DELAY_MS)
            }
            isDozeState(newState) -> {
                handler.removeCallbacks(dimmingReapplyRunnable)
                stopMonitorLoop()
                if (proEnabled && !extremeMode && !isCallOrRingtoneActive()) {
                    lockPeakRate(LockMode.DOZE, panelMinHz)
                } else {
                    unlockCapIfActive()
                }
            }
            else -> {
                handler.removeCallbacks(dimmingReapplyRunnable)
                stopMonitorLoop()
                unlockCapIfActive()
            }
        }
    }

    private fun onSettingChanged(uri: Uri?) {
        when (uri?.lastPathSegment) {
            Settings.System.MIN_REFRESH_RATE -> {
                val newMin = readSystemFloat(Settings.System.MIN_REFRESH_RATE, DEFAULT_NON_PRO_MIN_HZ)
                // Respect manual non-ARR minimum selection from Display -> Screen refresh rate.
                if (proEnabled && !extremeMode && newMin >= DEFAULT_NON_PRO_MIN_HZ) {
                    prefs.edit().putFloat(PREF_SAVED_MIN_HZ, newMin).apply()
                    proEnabled = false
                    stopMonitorLoop()
                    unlockCapIfActive()
                    syncMatchContentFrameRate(allowContentSwitching = true)
                    applyDimmingMode(enablePwm = false)
                    Settings.Secure.putIntForUser(
                        contentResolver, SECURE_KEY_PRO, 0, UserHandle.USER_CURRENT)
                }
                return
            }
            Settings.System.PEAK_REFRESH_RATE -> {
                val newPeak = readSystemFloat(Settings.System.PEAK_REFRESH_RATE, panelMaxHz)
                if (lockedTargetHz > 0f && abs(newPeak - lockedTargetHz) < 0.05f) {
                    return
                }
                lockedMode = LockMode.NONE
                lockedTargetHz = 0f
                updateSavedUserPeak(newPeak)
                syncMonitorActivation()
                return
            }
            else -> syncAllState()
        }
    }

    private fun recoverOrphanedLowPeakIfNeeded() {
        val currentPeak = readSystemFloat(Settings.System.PEAK_REFRESH_RATE, panelMaxHz)
        if (currentPeak < MIN_USER_PEAK_HZ) {
            val saved = prefs.getFloat(PREF_SAVED_PEAK_HZ, panelMaxHz)
            val target = if (saved in MIN_USER_PEAK_HZ..panelMaxHz) saved else panelMaxHz
            applyPeakRefreshRate(target)
        }
        if (prefs.getBoolean(PREF_SUPPRESSED_MATCH_CONTENT, false)) {
            syncMatchContentFrameRate(allowContentSwitching = true)
        }
    }

    private fun syncAllState() {
        val wasPro = proEnabled
        proEnabled =
            Settings.Secure.getIntForUser(
                contentResolver, SECURE_KEY_PRO, 1, UserHandle.USER_CURRENT) != 0
        videoMatchEnabled =
            Settings.Secure.getIntForUser(
                contentResolver, SECURE_KEY_VIDEO_MATCH, 1, UserHandle.USER_CURRENT) != 0
        extremeMode =
            Settings.System.getIntForUser(
                contentResolver, Settings.System.EXTREME_REFRESH_RATE, 0, UserHandle.USER_CURRENT) != 0

        if (lockedTargetHz == 0f) {
            val currentPeak = readSystemFloat(Settings.System.PEAK_REFRESH_RATE, panelMaxHz)
            updateSavedUserPeak(currentPeak)
        }

        if (!extremeMode) {
            if (proEnabled) {
                if (!wasPro) {
                    val currentMin = readSystemFloat(
                        Settings.System.MIN_REFRESH_RATE, DEFAULT_NON_PRO_MIN_HZ)
                    if (currentMin >= DEFAULT_NON_PRO_MIN_HZ) {
                        prefs.edit().putFloat(PREF_SAVED_MIN_HZ, currentMin).apply()
                    }
                }
                applyMinRefreshRate(idleFloorHz)
                applyDimmingMode(enablePwm = true)
                syncMatchContentFrameRate()
            } else if (wasPro || appliedPwmDimming == null) {
                stopMonitorLoop()
                unlockCapIfActive()
                syncMatchContentFrameRate()
                val restoreMin = prefs.getFloat(PREF_SAVED_MIN_HZ, DEFAULT_NON_PRO_MIN_HZ)
                applyMinRefreshRate(restoreMin)
                applyDimmingMode(enablePwm = false)
            }
        } else {
            stopMonitorLoop()
            lockedMode = LockMode.NONE
            lockedTargetHz = 0f
            syncMatchContentFrameRate()
            applyDimmingMode(enablePwm = proEnabled)
        }

        syncMonitorActivation()
    }

    private fun syncMonitorActivation() {
        val interactive = displayState == Display.STATE_ON
        val shouldRun =
            proEnabled &&
                interactive &&
                !extremeMode &&
                crtcNodes.isNotEmpty() &&
                userPeakHz > idleFloorHz + 0.5f
        if (shouldRun) {
            startMonitorLoop()
        } else {
            stopMonitorLoop()
            if (!extremeMode) {
                if (isDozeState(displayState) && proEnabled && !isCallOrRingtoneActive()) {
                    lockPeakRate(LockMode.DOZE, panelMinHz)
                } else {
                    unlockCapIfActive()
                }
            }
        }
    }

    private fun startMonitorLoop() {
        if (!pollRunning) {
            pollRunning = true
            resetCounters()
            handler.postDelayed(pollRunnable, POLL_INTERVAL_MS)
        }
        ensureTouchMonitor()
    }

    private fun stopMonitorLoop() {
        pollRunning = false
        handler.removeCallbacks(pollRunnable)
        releaseTouchMonitor()
        resetCounters()
    }

    private fun resetCounters() {
        idleTicks = 0
        idleSettleTicks = 0
        consecutiveClassCount = 0
        consecutiveOutCount = 0
        prevClass = RateClass.NONE
    }

    private fun ensureTouchMonitor() {
        if (inputMonitor != null) return
        val inputManager = getSystemService(InputManager::class.java) ?: return
        runCatching {
            val monitor = inputManager.monitorGestureInput("AdaptiveRefreshRate", Display.DEFAULT_DISPLAY)
            inputMonitor = monitor
            inputReceiver =
                object : InputEventReceiver(monitor.inputChannel, handler.looper) {
                    override fun onInputEvent(event: InputEvent) {
                        if (event is MotionEvent) {
                            onTouchInteraction()
                        }
                        finishInputEvent(event, false)
                    }
                }
        }.onFailure { e ->
            Log.w(TAG, "Failed to register InputMonitor; dynamic rate locking disabled", e)
            releaseTouchMonitor()
        }
    }

    private fun releaseTouchMonitor() {
        runCatching { inputReceiver?.dispose() }
        runCatching { inputMonitor?.dispose() }
        inputReceiver = null
        inputMonitor = null
    }

    private fun onTouchInteraction() {
        lastTouchElapsedMs = SystemClock.elapsedRealtime()
        if (lockedTargetHz > 0f) {
            unlockCapIfActive()
        }
        resetCounters()
    }

    private fun stepScenarioMonitor() {
        val now = SystemClock.elapsedRealtime()
        if (isCallOrRingtoneActive()) {
            if (lockedTargetHz > 0f) unlockCapIfActive()
            resetCounters()
            return
        }

        val sinceTouchMs = if (lastTouchElapsedMs > 0L) now - lastTouchElapsedMs else Long.MAX_VALUE
        val inIdleCooldown = sinceTouchMs < IDLE_TOUCH_COOLDOWN_MS || now < nonTouchWakeUntilMs
        val inVideoTouchCooldown = sinceTouchMs < VIDEO_TOUCH_COOLDOWN_MS
        if (inIdleCooldown) {
            if (lockedTargetHz > 0f) unlockCapIfActive()
            resetCounters()
            return
        }

        val fps = readMaxCrtcFps() ?: return

        // While stepped down for static idle (10 Hz floor), let the 1000 ms DRM window settle
        // before sampling non-touch redraw FPS so pre-lock frames cannot trigger a false wake cycle.
        if (lockedMode == LockMode.IDLE) {
            if (idleSettleTicks > 0) {
                idleSettleTicks--
                return
            }
            if (fps >= IDLE_WAKE_FPS) {
                if (DEBUG) Log.d(TAG, "Waking from ${lockedTargetHz}Hz idle on redraw: fps=$fps")
                nonTouchWakeUntilMs = now + IDLE_TOUCH_COOLDOWN_MS
                unlockCapIfActive()
                resetCounters()
                return
            }
            return
        }

        // Video playback rate control outside the 4s touch cooldown:
        // - Match video ON: match exact steady rate (24/30/60 Hz + SF content detection)
        // - Match video OFF: suppress 10/20/24 Hz matching, but cap <=30 Hz to 30 Hz and <=60 Hz to 60 Hz
        if (!inVideoTouchCooldown && userPeakHz > 30f) {
            val rateClass =
                if (videoMatchEnabled) {
                    when {
                        fps in 20.0f..31.5f -> RateClass.LOW
                        fps in 41.5f..62.5f && userPeakHz > 60.5f -> RateClass.HIGH
                        else -> RateClass.NONE
                    }
                } else {
                    when {
                        fps in 8.5f..31.5f -> RateClass.LOW
                        fps in 31.5f..62.5f && userPeakHz > 60.5f -> RateClass.HIGH
                        else -> RateClass.NONE
                    }
                }

            if (rateClass != RateClass.NONE) {
                idleTicks = 0
                val jumpedAboveCap =
                    lockedMode == LockMode.VIDEO &&
                        ((lockedTargetHz < 35f && fps >= 33.0f) || fps >= 63.0f)
                consecutiveOutCount = if (jumpedAboveCap) VIDEO_EXIT_CONFIRM_TICKS else 0
                if (rateClass == prevClass) {
                    consecutiveClassCount++
                } else {
                    consecutiveClassCount = 1
                    windowMinFps = fps
                }
                if (fps < windowMinFps) windowMinFps = fps
                prevClass = rateClass

                val neededTicks = if (rateClass == RateClass.HIGH) 6 else 4
                if (lockedMode == LockMode.VIDEO && consecutiveOutCount >= VIDEO_EXIT_CONFIRM_TICKS) {
                    unlockCapIfActive()
                    resetCounters()
                    return
                } else if (consecutiveClassCount >= neededTicks) {
                    val targetHz =
                        when {
                            rateClass == RateClass.HIGH -> 60.0f
                            !videoMatchEnabled -> 30.0f
                            windowMinFps <= 27.5f -> 24.0f
                            else -> 30.0f
                        }
                    if (targetHz < userPeakHz - 0.5f &&
                        (lockedMode == LockMode.NONE ||
                            (lockedMode == LockMode.VIDEO && abs(targetHz - lockedTargetHz) > 0.5f))
                    ) {
                        lockPeakRate(LockMode.VIDEO, targetHz)
                        return
                    }
                }
                if (lockedMode == LockMode.VIDEO) return
            } else {
                prevClass = RateClass.NONE
                consecutiveClassCount = 0
                if (lockedMode == LockMode.VIDEO) {
                    val jumpedAboveCap = (lockedTargetHz < 35f && fps >= 33.0f) || fps >= 63.0f
                    consecutiveOutCount =
                        if (jumpedAboveCap) VIDEO_EXIT_CONFIRM_TICKS else consecutiveOutCount + 1
                    if (consecutiveOutCount >= VIDEO_EXIT_CONFIRM_TICKS) {
                        unlockCapIfActive()
                        resetCounters()
                    } else {
                        return
                    }
                }
            }
        } else if (lockedMode == LockMode.VIDEO) {
            unlockCapIfActive()
            resetCounters()
            return
        }

        // Static idle step-down to 10 Hz floor (1 Hz stays reserved for DOZE).
        if (lockedMode == LockMode.NONE && userPeakHz > idleFloorHz + 0.5f) {
            if (fps < IDLE_ENTER_FPS && !isMediaAudioActive()) {
                idleTicks++
                val enterTicks =
                    if (isKeyguardLocked()) LOCKSCREEN_IDLE_TICKS else UNLOCKED_IDLE_TICKS
                if (idleTicks >= enterTicks) {
                    idleTicks = 0
                    idleSettleTicks = IDLE_SETTLE_TICKS
                    lockPeakRate(LockMode.IDLE, idleFloorHz)
                }
            } else {
                idleTicks = 0
            }
        }
    }

    private fun isKeyguardLocked(): Boolean =
        getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true

    private fun isMediaAudioActive(): Boolean =
        getSystemService(AudioManager::class.java)?.isMusicActive == true

    private fun isCallOrRingtoneActive(): Boolean {
        if (callActive) return true
        val telecom = getSystemService(TelecomManager::class.java)
        if (telecom != null && runCatching { telecom.isInCall || telecom.isRinging }.getOrDefault(false)) {
            return true
        }
        val audioManager = getSystemService(AudioManager::class.java) ?: return false
        return audioManager.mode == AudioManager.MODE_RINGTONE ||
            audioManager.mode == AudioManager.MODE_IN_CALL ||
            audioManager.mode == AudioManager.MODE_IN_COMMUNICATION
    }

    private fun applyDimmingMode(enablePwm: Boolean) {
        if (appliedPwmDimming == enablePwm) return
        appliedPwmDimming = enablePwm
        val value = if (enablePwm) DIMMING_MODE_PWM else DIMMING_MODE_DC
        if (DEBUG) Log.d(TAG, "Setting display dimming mode: enablePwm=$enablePwm (value=$value)")
        DisplayFeatureWrapper.setFeature(DISPLAY_FEATURE_DIMMING_MODE, value, DISPLAY_FEATURE_COOKIE)
    }

    private fun lockPeakRate(mode: LockMode, targetHz: Float) {
        if (DEBUG) Log.d(TAG, "Locking mode=$mode peak=${targetHz}Hz")
        lockedMode = mode
        lockedTargetHz = targetHz
        syncMatchContentFrameRate()
        val floorHz = if (mode == LockMode.DOZE) panelMinHz else idleFloorHz
        applyMinRefreshRate(minOf(floorHz, targetHz))
        applyPeakRefreshRate(targetHz)
    }

    private fun unlockCapIfActive() {
        if (lockedTargetHz == 0f) return
        if (DEBUG) Log.d(TAG, "Unlocking mode=$lockedMode peak back to ${userPeakHz}Hz")
        lockedMode = LockMode.NONE
        lockedTargetHz = 0f
        if (!extremeMode) {
            applyPeakRefreshRate(userPeakHz)
            if (proEnabled) {
                applyMinRefreshRate(idleFloorHz)
            }
        }
        syncMatchContentFrameRate()
    }

    private fun shouldAllowContentSwitching(): Boolean =
        !proEnabled ||
            extremeMode ||
            videoMatchEnabled ||
            lockedMode == LockMode.IDLE ||
            lockedMode == LockMode.DOZE

    private fun syncMatchContentFrameRate(allowContentSwitching: Boolean = shouldAllowContentSwitching()) {
        val current =
            Settings.Secure.getIntForUser(
                contentResolver,
                Settings.Secure.MATCH_CONTENT_FRAME_RATE,
                -1,
                UserHandle.USER_CURRENT,
            )
        if (!allowContentSwitching) {
            if (current > 0) {
                prefs.edit().putInt(PREF_SAVED_MATCH_CONTENT, current).apply()
            }
            prefs.edit().putBoolean(PREF_SUPPRESSED_MATCH_CONTENT, true).apply()
            if (current != MATCH_CONTENT_FRAME_RATE_NONE) {
                Settings.Secure.putIntForUser(
                    contentResolver,
                    Settings.Secure.MATCH_CONTENT_FRAME_RATE,
                    MATCH_CONTENT_FRAME_RATE_NONE,
                    UserHandle.USER_CURRENT,
                )
            }
        } else if (prefs.getBoolean(PREF_SUPPRESSED_MATCH_CONTENT, false)) {
            val saved = prefs.getInt(PREF_SAVED_MATCH_CONTENT, MATCH_CONTENT_FRAME_RATE_DEFAULT)
            val restore = if (saved > 0) saved else MATCH_CONTENT_FRAME_RATE_DEFAULT
            prefs.edit().putBoolean(PREF_SUPPRESSED_MATCH_CONTENT, false).apply()
            if (current != restore) {
                Settings.Secure.putIntForUser(
                    contentResolver,
                    Settings.Secure.MATCH_CONTENT_FRAME_RATE,
                    restore,
                    UserHandle.USER_CURRENT,
                )
            }
        }
    }

    private fun updateSavedUserPeak(rawPeak: Float) {
        val normalized = if (rawPeak < MIN_USER_PEAK_HZ || rawPeak > panelMaxHz) panelMaxHz else rawPeak
        userPeakHz = normalized
        prefs.edit().putFloat(PREF_SAVED_PEAK_HZ, normalized).apply()
    }

    private fun applyMinRefreshRate(hz: Float) {
        val current = readSystemFloat(Settings.System.MIN_REFRESH_RATE, -1f)
        if (abs(current - hz) < 0.05f) return
        Settings.System.putFloatForUser(
            contentResolver, Settings.System.MIN_REFRESH_RATE, hz, UserHandle.USER_CURRENT)
    }

    private fun applyPeakRefreshRate(hz: Float) {
        val current = readSystemFloat(Settings.System.PEAK_REFRESH_RATE, -1f)
        if (abs(current - hz) < 0.05f) return
        Settings.System.putFloatForUser(
            contentResolver, Settings.System.PEAK_REFRESH_RATE, hz, UserHandle.USER_CURRENT)
    }

    private fun readSystemFloat(key: String, fallback: Float): Float =
        Settings.System.getFloatForUser(contentResolver, key, fallback, UserHandle.USER_CURRENT)

    private fun discoverCrtcNodes(): List<File> {
        val nodes = mutableListOf<File>()
        for (i in 0 until MAX_CRTC_INDEX) {
            val sysfsClassNode = File("$DRM_CLASS_DIR/card0-sde-crtc-$i/measured_fps")
            if (canReadLine(sysfsClassNode)) {
                nodes.add(sysfsClassNode)
                continue
            }
            val qcomNode = File("$QCOM_MDP_DRM_DIR/card0-sde-crtc-$i/measured_fps")
            if (canReadLine(qcomNode)) {
                nodes.add(qcomNode)
            }
        }
        return nodes
    }

    private fun canReadLine(file: File): Boolean =
        runCatching { file.bufferedReader().use { it.readLine() != null } }.getOrDefault(false)

    private fun readMaxCrtcFps(): Float? {
        var best = -1f
        for (node in crtcNodes) {
            val line = runCatching { node.bufferedReader().use { it.readLine() } }.getOrNull() ?: continue
            val value = FPS_REGEX.find(line)?.groupValues?.getOrNull(1)?.toFloatOrNull() ?: continue
            if (value > best) best = value
        }
        return if (best >= 0f) best else null
    }

    private enum class LockMode {
        NONE,
        DOZE,
        IDLE,
        VIDEO,
    }

    private enum class RateClass {
        NONE,
        LOW,
        HIGH,
    }

    companion object {
        private const val TAG = "XiaomiPartsAdaptiveRR"
        private val DEBUG = Log.isLoggable(TAG, Log.DEBUG)

        const val SECURE_KEY_PRO = "adaptive_refresh_rate_pro"
        const val SECURE_KEY_VIDEO_MATCH = "adaptive_refresh_rate_video_match"

        private const val PREFS_NAME = "adaptive_refresh_rate_prefs"
        private const val PREF_SAVED_MIN_HZ = "saved_min_hz"
        private const val PREF_SAVED_PEAK_HZ = "saved_peak_hz"
        private const val PREF_SAVED_MATCH_CONTENT = "saved_match_content"
        private const val PREF_SUPPRESSED_MATCH_CONTENT = "suppressed_match_content"
        private const val DEFAULT_NON_PRO_MIN_HZ = 60.0f
        private const val MIN_USER_PEAK_HZ = 59.5f
        private const val MATCH_CONTENT_FRAME_RATE_NONE = 0
        private const val MATCH_CONTENT_FRAME_RATE_DEFAULT = 1

        private const val DISPLAY_FEATURE_DIMMING_MODE = 20
        private const val DISPLAY_FEATURE_COOKIE = 255
        private const val DIMMING_MODE_PWM = 0
        private const val DIMMING_MODE_DC = 1

        private const val POLL_INTERVAL_MS = 500L
        private const val SCREEN_ON_DIMMING_DELAY_MS = 350L
        private const val IDLE_TOUCH_COOLDOWN_MS = 1000L
        private const val VIDEO_TOUCH_COOLDOWN_MS = 4000L
        private const val DEFAULT_IDLE_FLOOR_HZ = 10.0f
        private const val IDLE_ENTER_FPS = 1.5f
        private const val IDLE_WAKE_FPS = 2.5f
        private const val IDLE_SETTLE_TICKS = 3
        private const val LOCKSCREEN_IDLE_TICKS = 1
        private const val UNLOCKED_IDLE_TICKS = 1
        private const val VIDEO_EXIT_CONFIRM_TICKS = 4
        private const val MAX_CRTC_INDEX = 3
        private const val DRM_CLASS_DIR = "/sys/class/drm"
        private const val QCOM_MDP_DRM_DIR = "/sys/devices/platform/soc/9800000.qcom,mdss_mdp/drm/card0"
        private val FPS_REGEX = Regex("([0-9]+(?:\\.[0-9]+)?)")

        fun getSupportedArrRates(context: Context): List<Float> {
            val display =
                context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
                    ?: return emptyList()
            if (!display.hasArrSupport()) return emptyList()
            return display.supportedRefreshRates.toList()
        }

        fun getMaxModeRefreshRate(context: Context): Float {
            val display =
                context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
                    ?: return 120.0f
            return display.supportedModes.maxOfOrNull { it.refreshRate } ?: 120.0f
        }

        fun isSupported(context: Context): Boolean {
            val rates = getSupportedArrRates(context)
            return rates.isNotEmpty() && (rates.minOrNull() ?: 60.0f) < 60.0f
        }
    }
}
