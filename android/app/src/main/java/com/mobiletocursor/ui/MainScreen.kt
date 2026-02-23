package com.mobiletocursor.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobiletocursor.network.TcpClient
import com.mobiletocursor.viewmodel.MainViewModel

/**
 * Main screen — fully scrollable, compose-then-send keyboard mode.
 */
@Composable
fun MainScreen(
    uiState: MainViewModel.UiState,
    onSendText: (text: String) -> Unit,
    onSpecialKey: (key: String) -> Unit,
    onToggleModifier: (key: String) -> Unit,
    onToggleMode: () -> Unit,
    onTrackpadMove: (dx: Int, dy: Int) -> Unit,
    onTrackpadTap: () -> Unit,
    onTrackpadLongPress: () -> Unit,
    onTrackpadScroll: (dx: Int, dy: Int) -> Unit,
    onPinchZoom: (zoomIn: Boolean) -> Unit,
    onTwoFingerTap: () -> Unit,
    onThreeFingerSwipe: (direction: String) -> Unit,
    onThreeFingerTap: () -> Unit,
    onFourFingerSwipe: (direction: String) -> Unit,
    onFourFingerTap: () -> Unit,
    onConnect: (host: String, port: Int, pin: String) -> Unit,
    onDisconnect: () -> Unit,
    onToggleSettings: () -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .imePadding(),
    ) {
        // ── Fixed top bar ──
        TopBar(
            connectionState = uiState.connectionState,
            onSettingsClick = onToggleSettings,
        )

        // ── SCROLLABLE middle section ──
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            // Settings panel (collapsible)
            AnimatedVisibility(
                visible = uiState.showSettings,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut(),
            ) {
                ConnectionPanel(
                    connectionState = uiState.connectionState,
                    initialHost = uiState.hostIp,
                    initialPort = uiState.port,
                    initialPin = uiState.pin,
                    onConnect = onConnect,
                    onDisconnect = onDisconnect,
                )
            }

            // Keyboard-specific rows inside scroll
            if (uiState.mode == MainViewModel.InputMode.KEYBOARD) {
                ModifierKeysRow(
                    activeModifiers = uiState.activeModifiers,
                    onToggle = onToggleModifier,
                )
                SpecialKeysRow(onSpecialKey = onSpecialKey)
                ShortcutsRow(onSpecialKey = onSpecialKey, onSendText = onSendText)
            }

            // Sent history
            if (uiState.sentHistory.isNotEmpty()) {
                SentHistory(items = uiState.sentHistory)
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        // ── FIXED bottom: mode toggle ──
        ModeToggleBar(
            currentMode = uiState.mode,
            onToggle = {
                onToggleMode()
                if (uiState.mode == MainViewModel.InputMode.KEYBOARD) {
                    keyboardController?.hide()
                }
            },
        )

        // ── FIXED bottom: trackpad or text input (NOT inside scroll) ──
        when (uiState.mode) {
            MainViewModel.InputMode.TRACKPAD -> {
                TrackpadSurface(
                    onMove = onTrackpadMove,
                    onTap = onTrackpadTap,
                    onLongPress = onTrackpadLongPress,
                    onScroll = onTrackpadScroll,
                    onPinchZoom = onPinchZoom,
                    onTwoFingerTap = onTwoFingerTap,
                    onThreeFingerSwipe = onThreeFingerSwipe,
                    onThreeFingerTap = onThreeFingerTap,
                    onFourFingerSwipe = onFourFingerSwipe,
                    onFourFingerTap = onFourFingerTap,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            MainViewModel.InputMode.KEYBOARD -> {
                ComposeAndSendInput(onSend = onSendText)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
    }
}


// ═════════════════════════════════════════════
//  Sub-components
// ═════════════════════════════════════════════

@Composable
private fun TopBar(
    connectionState: TcpClient.ConnectionState,
    onSettingsClick: () -> Unit,
) {
    val statusColor = when (connectionState) {
        TcpClient.ConnectionState.CONNECTED -> Color(0xFF4CAF50)
        TcpClient.ConnectionState.CONNECTING -> Color(0xFFFFC107)
        TcpClient.ConnectionState.DISCONNECTED -> Color(0xFFCF6679)
        TcpClient.ConnectionState.AUTH_FAILED -> Color(0xFFFF5252)
    }
    val statusText = when (connectionState) {
        TcpClient.ConnectionState.CONNECTED -> "Connected"
        TcpClient.ConnectionState.CONNECTING -> "Connecting..."
        TcpClient.ConnectionState.DISCONNECTED -> "Disconnected"
        TcpClient.ConnectionState.AUTH_FAILED -> "Wrong PIN"
    }
    val statusIcon = when (connectionState) {
        TcpClient.ConnectionState.CONNECTED -> Icons.Default.Link
        else -> Icons.Default.LinkOff
    }

    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(statusIcon, null, tint = statusColor, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(statusText, color = statusColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text("Mobile to Cursor", color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Settings, "Settings", tint = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}


@Composable
private fun ConnectionPanel(
    connectionState: TcpClient.ConnectionState,
    initialHost: String, initialPort: Int, initialPin: String,
    onConnect: (String, Int, String) -> Unit, onDisconnect: () -> Unit,
) {
    var host by rememberSaveable { mutableStateOf(initialHost.ifEmpty { "192.168.42.186" }) }
    var port by rememberSaveable { mutableStateOf(initialPort.toString()) }
    var pin by rememberSaveable { mutableStateOf(initialPin) }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant, shadowElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Connection Settings", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(12.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = host, onValueChange = { host = it },
                    label = { Text("Laptop IP") }, placeholder = { Text("192.168.42.186") },
                    singleLine = true, modifier = Modifier.weight(2f),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary),
                )
                OutlinedTextField(
                    value = port, onValueChange = { port = it },
                    label = { Text("Port") }, singleLine = true, modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary),
                )
            }

            Spacer(Modifier.height(8.dp))

            // PIN input
            OutlinedTextField(
                value = pin, onValueChange = { if (it.length <= 6) pin = it },
                label = { Text("🔑 PIN (from desktop)") },
                placeholder = { Text("Enter 6-digit PIN") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = if (connectionState == TcpClient.ConnectionState.AUTH_FAILED)
                        Color(0xFFFF5252) else MaterialTheme.colorScheme.primary,
                ),
            )

            // Auth failed warning
            if (connectionState == TcpClient.ConnectionState.AUTH_FAILED) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Wrong PIN — check the PIN shown in the desktop terminal",
                    color = Color(0xFFFF5252), fontSize = 12.sp,
                )
            }

            Spacer(Modifier.height(12.dp))

            val isConnected = connectionState == TcpClient.ConnectionState.CONNECTED
            Button(
                onClick = {
                    if (isConnected) onDisconnect()
                    else onConnect(host, port.toIntOrNull() ?: 5050, pin)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isConnected) Color(0xFFCF6679) else MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(if (isConnected) "Disconnect" else "Connect", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}


/**
 * Modifier keys with LED toggle — Ctrl, Shift, Alt, Win, CapsLock.
 * GREEN = key is held down on desktop, DARK = key is off.
 * Uses Surface instead of Button to avoid Material3 internal color overrides.
 */
@Composable
private fun ModifierKeysRow(
    activeModifiers: Set<String>,
    onToggle: (String) -> Unit,
) {
    val modifiers = listOf(
        "Ctrl" to "ctrl",
        "Shift" to "shift",
        "Alt" to "alt",
        "Win" to "win",
        "Caps" to "caps_lock",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        modifiers.forEach { (label, key) ->
            val isActive = key in activeModifiers

            // Direct colors — no animation trickery that Material can override
            val bg = if (isActive) Color(0xFF00C853) else Color(0xFF2A2A2A)
            val fg = if (isActive) Color.Black else Color(0xFFAAAAAA)
            val borderColor = if (isActive) Color(0xFF00E676) else Color(0xFF444444)

            Surface(
                onClick = { onToggle(key) },
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 42.dp),
                shape = RoundedCornerShape(10.dp),
                color = bg,
                border = BorderStroke(1.dp, borderColor),
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = label,
                        color = fg,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    // Tiny LED dot under the label
                    Spacer(modifier = Modifier.height(3.dp))
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                color = if (isActive) Color(0xFF00E676) else Color(0xFF555555),
                                shape = CircleShape,
                            ),
                    )
                }
            }
        }
    }
}


/**
 * Navigation & editing keys with press flash.
 * Button briefly lights up when tapped for visual confirmation.
 */
@Composable
private fun SpecialKeysRow(onSpecialKey: (String) -> Unit) {
    val keys = listOf(
        "⏎" to "enter", "⌫" to "backspace", "Tab" to "tab",
        "Esc" to "escape", "Del" to "delete", "Space" to "space",
        "←" to "left", "→" to "right", "↑" to "up", "↓" to "down",
        "Home" to "home", "End" to "end",
        "PgUp" to "page_up", "PgDn" to "page_down",
        "Ins" to "insert", "PrtSc" to "print_screen",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        keys.forEach { (label, key) ->
            var isPressed by remember { mutableStateOf(false) }

            val btnColor by animateColorAsState(
                targetValue = if (isPressed) MaterialTheme.colorScheme.primary else Color.Transparent,
                animationSpec = tween(if (isPressed) 50 else 300),
                label = "key_flash",
            )
            val contentColor by animateColorAsState(
                targetValue = if (isPressed) Color.White else MaterialTheme.colorScheme.onSurface,
                animationSpec = tween(if (isPressed) 50 else 300),
                label = "key_text_flash",
            )

            // Auto-reset after 200ms
            if (isPressed) {
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(200)
                    isPressed = false
                }
            }

            OutlinedButton(
                onClick = {
                    isPressed = true
                    onSpecialKey(key)
                },
                modifier = Modifier.defaultMinSize(minHeight = 40.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = btnColor,
                    contentColor = contentColor,
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
        }
    }
}


/**
 * Common keyboard shortcuts — Ctrl+C, Ctrl+V, Ctrl+Z, Ctrl+A, Ctrl+S, Ctrl+X.
 * Each sends: press ctrl → tap key → release ctrl.
 */
@Composable
private fun ShortcutsRow(
    onSpecialKey: (String) -> Unit,
    onSendText: (String) -> Unit,
) {
    data class Shortcut(val label: String, val modifier: String, val key: String)

    val shortcuts = listOf(
        Shortcut("Ctrl+C", "ctrl", "c"),
        Shortcut("Ctrl+V", "ctrl", "v"),
        Shortcut("Ctrl+Z", "ctrl", "z"),
        Shortcut("Ctrl+A", "ctrl", "a"),
        Shortcut("Ctrl+S", "ctrl", "s"),
        Shortcut("Ctrl+X", "ctrl", "x"),
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        shortcuts.forEach { shortcut ->
            Button(
                onClick = {
                    onSpecialKey("${shortcut.modifier}+${shortcut.key}")
                },
                modifier = Modifier.defaultMinSize(minHeight = 40.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2E7D32).copy(alpha = 0.2f),
                    contentColor = Color(0xFF66BB6A),
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(shortcut.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}


/** Shows last sent messages so user can track what was pushed. */
@Composable
private fun SentHistory(items: List<String>) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Sent History", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        items.take(10).forEach { text ->
            Surface(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface,
            ) {
                Text(text, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}


@Composable
private fun ModeToggleBar(currentMode: MainViewModel.InputMode, onToggle: () -> Unit) {
    val isKeyboard = currentMode == MainViewModel.InputMode.KEYBOARD
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(Modifier.fillMaxWidth().padding(4.dp), Arrangement.Center, Alignment.CenterVertically) {
            Button(
                onClick = { if (!isKeyboard) onToggle() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isKeyboard) MaterialTheme.colorScheme.primary else Color.Transparent,
                    contentColor = if (isKeyboard) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                ),
                shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Keyboard, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Keyboard", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(4.dp))
            Button(
                onClick = { if (isKeyboard) onToggle() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (!isKeyboard) MaterialTheme.colorScheme.primary else Color.Transparent,
                    contentColor = if (!isKeyboard) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                ),
                shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.TouchApp, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Trackpad", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}


/** Compose-then-send input — type freely, tap Send to push to desktop. */
@Composable
private fun ComposeAndSendInput(onSend: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicTextField(
            value = text, onValueChange = { text = it },
            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .weight(1f)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(24.dp))
                .padding(horizontal = 20.dp, vertical = 14.dp),
            decorationBox = { innerTextField ->
                if (text.isEmpty()) {
                    Text("Type here, then tap Send →", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f), fontSize = 16.sp)
                }
                innerTextField()
            },
        )
        FilledIconButton(
            onClick = { onSend(text); text = "" },
            modifier = Modifier.size(48.dp), shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}
