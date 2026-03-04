package com.vexra.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vexra.app.network.TcpClient
import com.vexra.app.network.UpdateChecker
import com.vexra.app.ui.components.VexraGlowBackground
import com.vexra.app.ui.components.VexraModeToggle
import com.vexra.app.ui.components.VexraSentHistory
import com.vexra.app.ui.components.VexraShortcuts
import com.vexra.app.ui.components.VexraSpecialKeys
import com.vexra.app.ui.components.VexraTextInput
import com.vexra.app.ui.theme.VexraAccent
import com.vexra.app.ui.theme.VexraBg
import com.vexra.app.ui.theme.VexraBorder
import com.vexra.app.ui.theme.VexraCard
import com.vexra.app.ui.theme.VexraGreen
import com.vexra.app.ui.theme.VexraRed
import com.vexra.app.ui.theme.VexraTextDim
import com.vexra.app.ui.theme.VexraTextMuted
import com.vexra.app.ui.theme.VexraTextPrimary
import com.vexra.app.ui.theme.VexraYellow
import com.vexra.app.viewmodel.MainViewModel

/** 3-screen nav state: CONNECTION → CONTROL ⇄ SETTINGS */
private enum class Screen { CONNECTION, CONTROL, SETTINGS }

/**
 * Root composable — routes between three screens:
 * 1. ConnectionScreen → full-screen Stitch pairing UI
 * 2. ControlScreen → trackpad / keyboard / modifier keys
 * 3. SettingsScreen → general (disconnect) + updates (GitHub)
 */
@Composable
fun MainScreen(
    uiState: MainViewModel.UiState,
    updateState: UpdateChecker.UpdateUiState,
    onSendText: (text: String) -> Unit,
    onSpecialKey: (key: String) -> Unit,
    onToggleModifier: (key: String) -> Unit,
    onToggleMode: () -> Unit,
    onTrackpadMove: (dx: Int, dy: Int) -> Unit,
    onTrackpadTap: () -> Unit,
    onTrackpadLongPress: () -> Unit,
    onTrackpadScroll: (dx: Int, dy: Int) -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onTwoFingerTap: () -> Unit,
    onThreeFingerSwipe: (direction: String) -> Unit,
    onThreeFingerTap: () -> Unit,
    onFourFingerSwipe: (direction: String) -> Unit,
    onFourFingerTap: () -> Unit,
    onConnect: (host: String, port: Int, pin: String) -> Unit,
    onDisconnect: () -> Unit,
    onCheckUpdate: () -> Unit,
    onDownloadUpdate: () -> Unit,
) {
    val isConnected = uiState.connectionState == TcpClient.ConnectionState.CONNECTED

    // Auto-navigate: when disconnected, always show ConnectionScreen
    var currentScreen by rememberSaveable { mutableStateOf(Screen.CONNECTION) }
    if (!isConnected && currentScreen != Screen.CONNECTION) {
        currentScreen = Screen.CONNECTION
    }
    if (isConnected && currentScreen == Screen.CONNECTION) {
        currentScreen = Screen.CONTROL
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VexraBg)
            .statusBarsPadding()
            .imePadding(),
    ) {
        VexraGlowBackground()

        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                when {
                    targetState.ordinal > initialState.ordinal ->
                        slideInHorizontally { it } + fadeIn() togetherWith
                            slideOutHorizontally { -it } + fadeOut()
                    else ->
                        slideInHorizontally { -it } + fadeIn() togetherWith
                            slideOutHorizontally { it } + fadeOut()
                }
            },
            label = "screen_switch",
        ) { screen ->
            when (screen) {
                Screen.CONNECTION -> ConnectionScreen(
                    connectionState = uiState.connectionState,
                    initialHost = uiState.hostIp,
                    initialPort = uiState.port,
                    initialPin = uiState.pin,
                    onConnect = onConnect,
                )
                Screen.CONTROL -> ControlScreen(
                    uiState = uiState,
                    onSendText = onSendText,
                    onSpecialKey = onSpecialKey,
                    onToggleModifier = onToggleModifier,
                    onToggleMode = onToggleMode,
                    onTrackpadMove = onTrackpadMove,
                    onTrackpadTap = onTrackpadTap,
                    onTrackpadLongPress = onTrackpadLongPress,
                    onTrackpadScroll = onTrackpadScroll,
                    onDragStart = onDragStart,
                    onDragEnd = onDragEnd,
                    onTwoFingerTap = onTwoFingerTap,
                    onThreeFingerSwipe = onThreeFingerSwipe,
                    onThreeFingerTap = onThreeFingerTap,
                    onFourFingerSwipe = onFourFingerSwipe,
                    onFourFingerTap = onFourFingerTap,
                    onSettingsClick = { currentScreen = Screen.SETTINGS },
                )
                Screen.SETTINGS -> SettingsScreen(
                    connectedHost = uiState.hostIp,
                    updateState = updateState,
                    onBack = { currentScreen = Screen.CONTROL },
                    onDisconnect = onDisconnect,
                    onCheckUpdate = onCheckUpdate,
                    onDownloadUpdate = onDownloadUpdate,
                )
            }
        }
    }
}


// ═══════════════════════════════════════════════════
//  Screen 1: Connection — Stitch design
// ═══════════════════════════════════════════════════

@Composable
private fun ConnectionScreen(
    connectionState: TcpClient.ConnectionState,
    initialHost: String, initialPort: Int, initialPin: String,
    onConnect: (String, Int, String) -> Unit,
) {
    var host by rememberSaveable { mutableStateOf(initialHost.ifEmpty { "192.168.42.186" }) }
    var port by rememberSaveable { mutableStateOf(initialPort.toString()) }
    var pin by rememberSaveable { mutableStateOf(initialPin) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = VexraAccent,
        unfocusedBorderColor = VexraBorder,
        focusedLabelColor = VexraAccent,
        unfocusedLabelColor = VexraTextMuted,
        cursorColor = VexraAccent,
        focusedTextColor = VexraTextPrimary,
        unfocusedTextColor = VexraTextPrimary,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
    )

    val isAuthFailed = connectionState == TcpClient.ConnectionState.AUTH_FAILED
    val isConnecting = connectionState == TcpClient.ConnectionState.CONNECTING

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(60.dp))

        Text("Vexra", color = VexraTextPrimary, fontSize = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
        Spacer(Modifier.height(8.dp))
        Text("Your phone. Your trackpad. No wires.", color = VexraTextMuted, fontSize = 15.sp, textAlign = TextAlign.Center)

        Spacer(Modifier.height(40.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = VexraCard,
            border = BorderStroke(1.dp, VexraBorder),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("IP ADDRESS", color = VexraTextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = host,
                    onValueChange = { newValue ->
                        // Strip everything except digits
                        val digits = newValue.filter { it.isDigit() }.take(12)
                        // Auto-format: insert dot after every 3rd digit, max 4 octets
                        val sb = StringBuilder()
                        var octet = 0
                        var count = 0
                        for (ch in digits) {
                            if (octet >= 4) break
                            sb.append(ch)
                            count++
                            if (count == 3 && octet < 3) {
                                sb.append('.')
                                count = 0
                                octet++
                            }
                        }
                        host = sb.toString()
                    },
                    placeholder = { Text("192.168.1.100", color = VexraTextDim) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors, shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    leadingIcon = { Icon(Icons.Default.Wifi, null, tint = VexraTextDim, modifier = Modifier.size(20.dp)) },
                )

                Spacer(Modifier.height(20.dp))
                Text("PORT", color = VexraTextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = port,
                    onValueChange = { newValue -> port = newValue.filter { it.isDigit() } },
                    placeholder = { Text("5050", color = VexraTextDim) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors, shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    leadingIcon = { Text("<>", color = VexraTextDim, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp)) },
                )

                Spacer(Modifier.height(20.dp))
                Text("PIN", color = VexraTextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { newValue ->
                        val digits = newValue.filter { it.isDigit() }
                        if (digits.length <= 6) pin = digits
                    },
                    placeholder = { Text("Enter 6-digit PIN", color = VexraTextDim) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    colors = if (isAuthFailed) OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VexraRed, unfocusedBorderColor = VexraRed,
                        focusedLabelColor = VexraRed, unfocusedLabelColor = VexraRed,
                        cursorColor = VexraRed,
                        focusedTextColor = VexraTextPrimary, unfocusedTextColor = VexraTextPrimary,
                        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    ) else fieldColors,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    leadingIcon = { Icon(Icons.Default.Lock, null, tint = VexraTextDim, modifier = Modifier.size(20.dp)) },
                )
                if (isAuthFailed) {
                    Spacer(Modifier.height(8.dp))
                    Text("Wrong PIN — check PIN on your desktop", color = VexraRed, fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = { onConnect(host, port.toIntOrNull() ?: 5050, pin) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = VexraAccent),
            shape = RoundedCornerShape(28.dp),
            enabled = !isConnecting,
        ) {
            Icon(Icons.Default.Wifi, null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                if (isConnecting) "Connecting..." else "Connect to PC",
                fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
            )
        }

        if (isConnecting) {
            Spacer(Modifier.height(12.dp))
            Text("Connecting...", color = VexraYellow, fontSize = 13.sp)
        }

        Spacer(Modifier.weight(1f))
        Text("v${UpdateChecker.CURRENT_VERSION}", color = VexraTextDim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 24.dp))
    }
}


// ═══════════════════════════════════════════════════
//  Screen 2: Controls — trackpad / keyboard
// ═══════════════════════════════════════════════════

@Composable
private fun ControlScreen(
    uiState: MainViewModel.UiState,
    onSendText: (String) -> Unit,
    onSpecialKey: (String) -> Unit,
    onToggleModifier: (String) -> Unit,
    onToggleMode: () -> Unit,
    onTrackpadMove: (Int, Int) -> Unit,
    onTrackpadTap: () -> Unit,
    onTrackpadLongPress: () -> Unit,
    onTrackpadScroll: (Int, Int) -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onTwoFingerTap: () -> Unit,
    onThreeFingerSwipe: (String) -> Unit,
    onThreeFingerTap: () -> Unit,
    onFourFingerSwipe: (String) -> Unit,
    onFourFingerTap: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    Box(modifier = Modifier.fillMaxSize()) {
        // ── Ambient glow orbs behind everything ──
        VexraGlowBackground()

        Column(modifier = Modifier.fillMaxSize()) {
            // ── Top bar: Vexra · [Connected pill] ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Vexra",
                    color = Color.White,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                )
                Spacer(Modifier.weight(1f))

                // Connected pill (matches Stitch)
                Row(
                    modifier = Modifier
                        .background(
                            color = Color.White.copy(alpha = 0.05f),
                            shape = RoundedCornerShape(50),
                        )
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.10f),
                            shape = RoundedCornerShape(50),
                        )
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(VexraGreen, CircleShape),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Connected",
                        color = Color(0xFFE2E8F0),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp,
                    )
                }
            }

            // ── Scrollable keyboard controls (only in keyboard mode) ──
            if (uiState.mode == MainViewModel.InputMode.KEYBOARD) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                ) {
                    VexraModifierKeys(uiState.activeModifiers, onToggleModifier)
                    VexraSpecialKeys(onSpecialKey)
                    VexraShortcuts(onSpecialKey, onSendText)

                    if (uiState.sentHistory.isNotEmpty()) {
                        VexraSentHistory(uiState.sentHistory)
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            // ── Trackpad or text input ──
            when (uiState.mode) {
                MainViewModel.InputMode.TRACKPAD -> {
                    TrackpadSurface(
                        onMove = onTrackpadMove, onTap = onTrackpadTap,
                        onLongPress = onTrackpadLongPress, onScroll = onTrackpadScroll,
                        onDragStart = onDragStart, onDragEnd = onDragEnd,
                        onTwoFingerTap = onTwoFingerTap,
                        onThreeFingerSwipe = onThreeFingerSwipe, onThreeFingerTap = onThreeFingerTap,
                        onFourFingerSwipe = onFourFingerSwipe, onFourFingerTap = onFourFingerTap,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
                MainViewModel.InputMode.KEYBOARD -> {
                    VexraTextInput(onSend = onSendText)
                }
            }

            // ── Bottom toggle bar (Stitch pill) ──
            VexraModeToggle(
                currentMode = uiState.mode,
                onToggle = {
                    onToggleMode()
                    if (uiState.mode == MainViewModel.InputMode.KEYBOARD) {
                        keyboardController?.hide()
                    }
                },
                onSettingsClick = onSettingsClick,
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}


// ═══════════════════════════════════════════════════
//  Shared sub-component
// ═══════════════════════════════════════════════════

@Composable
private fun VexraModifierKeys(activeModifiers: Set<String>, onToggle: (String) -> Unit) {
    val modifiers = listOf("Ctrl" to "ctrl", "Shift" to "shift", "Alt" to "alt", "Win" to "win", "Caps" to "caps_lock")
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        modifiers.forEach { (label, key) ->
            val isActive = key in activeModifiers
            val bg = if (isActive) VexraAccent.copy(alpha = 0.3f) else VexraCard
            val fg = if (isActive) VexraAccent else VexraTextMuted
            val border = if (isActive) VexraAccent.copy(alpha = 0.6f) else VexraBorder
            Surface(
                onClick = { onToggle(key) },
                modifier = Modifier.weight(1f).defaultMinSize(minHeight = 42.dp),
                shape = RoundedCornerShape(12.dp), color = bg,
                border = BorderStroke(1.dp, border),
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Spacer(Modifier.height(3.dp))
                    Box(Modifier.size(5.dp).background(if (isActive) VexraAccent else VexraTextDim, CircleShape))
                }
            }
        }
    }
}
