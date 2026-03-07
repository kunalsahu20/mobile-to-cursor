package com.vexra.app.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vexra.app.network.EventProtocol
import com.vexra.app.network.TcpClient
import com.vexra.app.network.UpdateChecker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Main ViewModel — owns the TcpClient and all UI state.
 *
 * Keyboard mode uses "compose-then-send": the user types freely
 * on the phone, edits/corrects typos, then taps Send to push
 * the final text to the desktop. This avoids backspace being
 * forwarded accidentally.
 *
 * Saves the last-used IP/port to SharedPreferences and auto-connects
 * on next app launch. Falls back to showing settings if connection fails.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    enum class InputMode { KEYBOARD, TRACKPAD }

    data class UiState(
        val mode: InputMode = InputMode.KEYBOARD,
        val connectionState: TcpClient.ConnectionState = TcpClient.ConnectionState.DISCONNECTED,
        val hostIp: String = "",
        val port: Int = 5050,
        val pin: String = "",
        val showSettings: Boolean = true,
        val sentHistory: List<String> = emptyList(),
        val activeModifiers: Set<String> = emptySet(),
    )

    private val prefs = application.getSharedPreferences("mobile_to_cursor", Context.MODE_PRIVATE)
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val tcpClient = TcpClient(viewModelScope)
    private val updateChecker = UpdateChecker(application)
    val updateState = updateChecker.state

    /** Track whether we attempted auto-connect so we can show settings on failure. */
    private var autoConnectAttempted = false

    init {
        // Watch connection state changes
        viewModelScope.launch {
            tcpClient.state.collect { connState ->
                _uiState.value = _uiState.value.copy(
                    connectionState = connState,
                    // Auto-hide settings when connected, auto-show otherwise
                    showSettings = connState != TcpClient.ConnectionState.CONNECTED,
                )
                autoConnectAttempted = false
            }
        }

        // Load saved IP/port/PIN and auto-connect
        val savedHost = prefs.getString("last_host", "") ?: ""
        val savedPort = prefs.getInt("last_port", 5050)
        val savedPin = prefs.getString("last_pin", "") ?: ""
        if (savedHost.isNotBlank() && savedPin.isNotBlank()) {
            _uiState.value = _uiState.value.copy(
                hostIp = savedHost,
                port = savedPort,
                pin = savedPin,
                showSettings = false,  // Hide settings while auto-connecting
            )
            autoConnectAttempted = true
            tcpClient.connect(savedHost, savedPort, savedPin)
        }
    }

    // ── Connection ──────────────────────────────

    fun connect(host: String, port: Int, pin: String) {
        _uiState.value = _uiState.value.copy(
            hostIp = host,
            port = port,
            pin = pin,
            showSettings = false,
        )
        // Persist for next app launch
        prefs.edit()
            .putString("last_host", host)
            .putInt("last_port", port)
            .putString("last_pin", pin)
            .apply()
        tcpClient.connect(host, port, pin)
    }

    fun disconnect() {
        tcpClient.disconnect()
    }

    fun toggleSettings() {
        _uiState.value = _uiState.value.copy(
            showSettings = !_uiState.value.showSettings
        )
    }

    // ── Mode toggle ─────────────────────────────

    fun toggleMode() {
        val newMode = when (_uiState.value.mode) {
            InputMode.KEYBOARD -> InputMode.TRACKPAD
            InputMode.TRACKPAD -> InputMode.KEYBOARD
        }
        _uiState.value = _uiState.value.copy(mode = newMode)
    }

    // ── Keyboard: compose-then-send ─────────────

    /**
     * Send composed text to the desktop (fallback for paste).
     * Called when the user taps the Send button for bulk text.
     */
    fun sendText(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            tcpClient.send(EventProtocol.keyInput(text))
        }
        // Add to sent history (keep last 20)
        val history = _uiState.value.sentHistory.toMutableList()
        history.add(0, text)
        if (history.size > 20) history.removeAt(history.size - 1)
        _uiState.value = _uiState.value.copy(sentHistory = history)
    }

    /**
     * Send a single keystroke (or small fragment) in real-time.
     * Called on every onValueChange diff. No history tracking to avoid spam.
     */
    fun sendKeyStroke(text: String) {
        if (text.isEmpty()) return
        viewModelScope.launch {
            tcpClient.send(EventProtocol.keyInput(text))
        }
    }

    /** Send a backspace key press for real-time deletion. */
    fun sendBackspace() {
        viewModelScope.launch {
            tcpClient.send(EventProtocol.keyPress("backspace"))
        }
    }

    /** Send a special key (Enter, Tab, Backspace on desktop, arrows, etc). */
    fun onSpecialKey(key: String) {
        viewModelScope.launch {
            tcpClient.send(EventProtocol.keyPress(key))
        }
    }

    /**
     * Toggle a modifier key on/off — like a keyboard LED.
     *
     * HOLD-type (Ctrl, Shift, Alt): sends "press" on activate, "release" on deactivate.
     *   User holds these while typing other keys.
     *
     * TAP-type (Win, CapsLock): sends a single "tap" each time.
     *   Win: tap opens Start menu, held+released also opens it (bad UX).
     *   CapsLock: real keyboard toggles on single press, not hold/release.
     */
    companion object {
        private val TAP_KEYS = setOf("win", "caps_lock")
    }

    fun toggleModifier(key: String) {
        val current = _uiState.value.activeModifiers
        val isActive = key in current

        viewModelScope.launch {
            if (key in TAP_KEYS) {
                // Tap-type: always send a single tap, LED just tracks state
                tcpClient.send(EventProtocol.keyPress(key, "tap"))
            } else {
                // Hold-type: press on activate, release on deactivate
                if (isActive) {
                    tcpClient.send(EventProtocol.keyPress(key, "release"))
                } else {
                    tcpClient.send(EventProtocol.keyPress(key, "press"))
                }
            }
        }

        _uiState.value = _uiState.value.copy(
            activeModifiers = if (isActive) current - key else current + key
        )
    }

    /** Release all active modifiers — cleanup after switching modes etc. */
    fun releaseAllModifiers() {
        val current = _uiState.value.activeModifiers
        viewModelScope.launch {
            current.forEach { key ->
                tcpClient.send(EventProtocol.keyPress(key, "release"))
            }
        }
        _uiState.value = _uiState.value.copy(activeModifiers = emptySet())
    }

    // ── Trackpad events ─────────────────────────

    fun onTrackpadMove(dx: Int, dy: Int) {
        viewModelScope.launch {
            tcpClient.send(EventProtocol.mouseMove(dx, dy))
        }
    }

    fun onTrackpadTap() {
        viewModelScope.launch {
            tcpClient.send(EventProtocol.mouseClick("left", "tap"))
        }
    }

    fun onTrackpadLongPress() {
        viewModelScope.launch {
            tcpClient.send(EventProtocol.mouseClick("right", "tap"))
        }
    }

    fun onTrackpadScroll(dx: Int, dy: Int) {
        viewModelScope.launch {
            tcpClient.send(EventProtocol.mouseScroll(dx, dy))
        }
    }

    /**
     * Pinch zoom — sends a single ZOOM event.
     * Desktop handles Ctrl+Scroll atomically (no timing issues).
     */
    /** 2-finger drag start — hold left mouse button. */
    fun onDragStart() {
        viewModelScope.launch {
            tcpClient.send(EventProtocol.mouseClick("left", "down"))
        }
    }

    /** 2-finger drag end — release left mouse button. */
    fun onDragEnd() {
        viewModelScope.launch {
            tcpClient.send(EventProtocol.mouseClick("left", "up"))
        }
    }

    /** 2-finger tap → right click (context menu) */
    fun onTwoFingerTap() {
        viewModelScope.launch {
            tcpClient.send(EventProtocol.mouseClick("right", "tap"))
        }
    }

    /**
     * 3-finger gestures — mirrors Windows precision trackpad:
     *   up    → Task View (Win+Tab)
     *   down  → Show Desktop (Win+D)
     *   left  → Switch to previous app (Alt+Shift+Tab)
     *   right → Switch to next app (Alt+Tab)
     */
    fun onThreeFingerSwipe(direction: String) {
        viewModelScope.launch {
            when (direction) {
                "up" -> tcpClient.send(EventProtocol.keyPress("win+tab"))
                "down" -> tcpClient.send(EventProtocol.keyPress("win+d"))
                "left" -> tcpClient.send(EventProtocol.keyPress("alt+shift+tab"))
                "right" -> tcpClient.send(EventProtocol.keyPress("alt+tab"))
            }
        }
    }

    /** 3-finger tap → Open Windows Search (Win key) */
    fun onThreeFingerTap() {
        viewModelScope.launch {
            tcpClient.send(EventProtocol.keyPress("win", "tap"))
        }
    }

    /**
     * 4-finger gestures:
     *   up    → Volume Up
     *   down  → Volume Down
     *   left  → Previous virtual desktop (Win+Ctrl+Left)
     *   right → Next virtual desktop (Win+Ctrl+Right)
     */
    fun onFourFingerSwipe(direction: String) {
        viewModelScope.launch {
            when (direction) {
                "up" -> tcpClient.send(EventProtocol.keyPress("volume_up"))
                "down" -> tcpClient.send(EventProtocol.keyPress("volume_down"))
                "left" -> tcpClient.send(EventProtocol.keyPress("win+ctrl+left"))
                "right" -> tcpClient.send(EventProtocol.keyPress("win+ctrl+right"))
            }
        }
    }

    /** 4-finger tap → Notification Center (Win+N) */
    fun onFourFingerTap() {
        viewModelScope.launch {
            tcpClient.send(EventProtocol.keyPress("win+n"))
        }
    }

    // ── Updates ──────────────────────────────────

    fun checkForUpdate() {
        viewModelScope.launch {
            updateChecker.checkForUpdate()
        }
    }

    fun downloadUpdate() {
        viewModelScope.launch {
            updateChecker.downloadAndInstall()
        }
    }

    override fun onCleared() {
        super.onCleared()
        tcpClient.disconnect()
    }
}
