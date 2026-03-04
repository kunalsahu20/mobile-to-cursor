package com.mobiletocursor.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobiletocursor.network.TcpClient
import com.mobiletocursor.ui.components.VexraGlowBackground
import com.mobiletocursor.ui.components.VexraModeToggle
import com.mobiletocursor.ui.components.VexraSentHistory
import com.mobiletocursor.ui.components.VexraShortcuts
import com.mobiletocursor.ui.components.VexraSpecialKeys
import com.mobiletocursor.ui.components.VexraTextInput
import com.mobiletocursor.ui.theme.VexraAccent
import com.mobiletocursor.ui.theme.VexraBg
import com.mobiletocursor.ui.theme.VexraBorder
import com.mobiletocursor.ui.theme.VexraCard
import com.mobiletocursor.ui.theme.VexraGreen
import com.mobiletocursor.ui.theme.VexraRed
import com.mobiletocursor.ui.theme.VexraTextDim
import com.mobiletocursor.ui.theme.VexraTextMuted
import com.mobiletocursor.ui.theme.VexraTextPrimary
import com.mobiletocursor.ui.theme.VexraYellow
import com.mobiletocursor.viewmodel.MainViewModel

/**
 * Main screen — premium minimalist design matching Stitch mockup.
 * Pure black background, animated glow orbs, glass-morphism cards.
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VexraBg)
            .statusBarsPadding()
            .imePadding(),
    ) {
        // Animated glow orbs behind everything
        VexraGlowBackground()

        Column(modifier = Modifier.fillMaxSize()) {
            // ── Slim top bar ──
            VexraTopBar(
                connectionState = uiState.connectionState,
                onSettingsClick = onToggleSettings,
            )

            // ── Scrollable middle ──
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                AnimatedVisibility(
                    visible = uiState.showSettings,
                    enter = slideInVertically() + fadeIn(),
                    exit = slideOutVertically() + fadeOut(),
                ) {
                    VexraConnectionPanel(
                        connectionState = uiState.connectionState,
                        initialHost = uiState.hostIp,
                        initialPort = uiState.port,
                        initialPin = uiState.pin,
                        onConnect = onConnect,
                        onDisconnect = onDisconnect,
                    )
                }

                if (uiState.mode == MainViewModel.InputMode.KEYBOARD) {
                    VexraModifierKeys(uiState.activeModifiers, onToggleModifier)
                    VexraSpecialKeys(onSpecialKey)
                    VexraShortcuts(onSpecialKey, onSendText)
                }

                if (uiState.sentHistory.isNotEmpty()) {
                    VexraSentHistory(uiState.sentHistory)
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            // ── Mode toggle ──
            VexraModeToggle(
                currentMode = uiState.mode,
                onToggle = {
                    onToggleMode()
                    if (uiState.mode == MainViewModel.InputMode.KEYBOARD) {
                        keyboardController?.hide()
                    }
                },
            )

            // ── Trackpad or keyboard input ──
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
                    VexraTextInput(onSend = onSendText)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}


// ═══════════════════════════════════════════════════
//  Sub-components — glass-morphism design
// ═══════════════════════════════════════════════════

@Composable
private fun VexraTopBar(
    connectionState: TcpClient.ConnectionState,
    onSettingsClick: () -> Unit,
) {
    val statusColor = when (connectionState) {
        TcpClient.ConnectionState.CONNECTED -> VexraGreen
        TcpClient.ConnectionState.CONNECTING -> VexraYellow
        TcpClient.ConnectionState.DISCONNECTED -> VexraTextDim
        TcpClient.ConnectionState.AUTH_FAILED -> VexraRed
    }
    val statusText = when (connectionState) {
        TcpClient.ConnectionState.CONNECTED -> "Connected"
        TcpClient.ConnectionState.CONNECTING -> "Connecting..."
        TcpClient.ConnectionState.DISCONNECTED -> "Disconnected"
        TcpClient.ConnectionState.AUTH_FAILED -> "Wrong PIN"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Vexra",
            color = VexraTextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
        )
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(statusColor, CircleShape),
        )
        Spacer(Modifier.width(8.dp))
        Text(statusText, color = statusColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(12.dp))
        IconButton(onClick = onSettingsClick, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Settings, "Settings", tint = VexraTextMuted, modifier = Modifier.size(20.dp))
        }
    }
}


@Composable
private fun VexraConnectionPanel(
    connectionState: TcpClient.ConnectionState,
    initialHost: String, initialPort: Int, initialPin: String,
    onConnect: (String, Int, String) -> Unit, onDisconnect: () -> Unit,
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

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        color = VexraCard,
        border = BorderStroke(1.dp, VexraBorder),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = host, onValueChange = { host = it },
                    label = { Text("IP Address") },
                    placeholder = { Text("192.168.1.100", color = VexraTextDim) },
                    singleLine = true, modifier = Modifier.weight(2f),
                    colors = fieldColors,
                    shape = RoundedCornerShape(14.dp),
                    leadingIcon = { Icon(Icons.Default.Wifi, null, tint = VexraTextDim, modifier = Modifier.size(18.dp)) },
                )
                OutlinedTextField(
                    value = port, onValueChange = { port = it },
                    label = { Text("Port") },
                    singleLine = true, modifier = Modifier.weight(1f),
                    colors = fieldColors,
                    shape = RoundedCornerShape(14.dp),
                )
            }

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = pin, onValueChange = { if (it.length <= 6) pin = it },
                label = { Text("PIN (from desktop)") },
                placeholder = { Text("Enter 6-digit PIN", color = VexraTextDim) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors.let {
                    if (connectionState == TcpClient.ConnectionState.AUTH_FAILED) {
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VexraRed,
                            unfocusedBorderColor = VexraRed,
                            focusedLabelColor = VexraRed,
                            unfocusedLabelColor = VexraRed,
                            cursorColor = VexraRed,
                            focusedTextColor = VexraTextPrimary,
                            unfocusedTextColor = VexraTextPrimary,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                        )
                    } else it
                },
                shape = RoundedCornerShape(14.dp),
            )

            if (connectionState == TcpClient.ConnectionState.AUTH_FAILED) {
                Spacer(Modifier.height(6.dp))
                Text("Wrong PIN — check the PIN on your desktop", color = VexraRed, fontSize = 12.sp)
            }

            Spacer(Modifier.height(16.dp))

            val isConnected = connectionState == TcpClient.ConnectionState.CONNECTED
            Button(
                onClick = {
                    if (isConnected) onDisconnect()
                    else onConnect(host, port.toIntOrNull() ?: 5050, pin)
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isConnected) VexraRed.copy(alpha = 0.8f) else VexraAccent,
                ),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(
                    if (isConnected) Icons.Default.WifiOff else Icons.Default.Wifi,
                    null, modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isConnected) "Disconnect" else "Connect to PC",
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}


@Composable
private fun VexraModifierKeys(
    activeModifiers: Set<String>,
    onToggle: (String) -> Unit,
) {
    val modifiers = listOf("Ctrl" to "ctrl", "Shift" to "shift", "Alt" to "alt", "Win" to "win", "Caps" to "caps_lock")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        modifiers.forEach { (label, key) ->
            val isActive = key in activeModifiers
            val bg = if (isActive) VexraAccent.copy(alpha = 0.3f) else VexraCard
            val fg = if (isActive) VexraAccent else VexraTextMuted
            val border = if (isActive) VexraAccent.copy(alpha = 0.6f) else VexraBorder

            Surface(
                onClick = { onToggle(key) },
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 42.dp),
                shape = RoundedCornerShape(12.dp),
                color = bg,
                border = BorderStroke(1.dp, border),
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Spacer(Modifier.height(3.dp))
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .background(if (isActive) VexraAccent else VexraTextDim, CircleShape),
                    )
                }
            }
        }
    }
}
