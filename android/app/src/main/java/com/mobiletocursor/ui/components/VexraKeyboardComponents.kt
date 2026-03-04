package com.mobiletocursor.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobiletocursor.ui.theme.VexraAccent
import com.mobiletocursor.ui.theme.VexraBorder
import com.mobiletocursor.ui.theme.VexraCard
import com.mobiletocursor.ui.theme.VexraTextDim
import com.mobiletocursor.ui.theme.VexraTextMuted
import com.mobiletocursor.ui.theme.VexraTextPrimary
import com.mobiletocursor.viewmodel.MainViewModel

/**
 * Special keys row — navigation & editing keys with press flash.
 */
@Composable
fun VexraSpecialKeys(onSpecialKey: (String) -> Unit) {
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
                targetValue = if (isPressed) VexraAccent else Color.Transparent,
                animationSpec = tween(if (isPressed) 50 else 300),
                label = "key_flash",
            )
            val contentColor by animateColorAsState(
                targetValue = if (isPressed) Color.White else VexraTextMuted,
                animationSpec = tween(if (isPressed) 50 else 300),
                label = "key_text_flash",
            )

            if (isPressed) {
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(200)
                    isPressed = false
                }
            }

            OutlinedButton(
                onClick = { isPressed = true; onSpecialKey(key) },
                modifier = Modifier.defaultMinSize(minHeight = 40.dp),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, if (isPressed) VexraAccent else VexraBorder),
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
 * Keyboard shortcuts — Ctrl+C, Ctrl+V, etc.
 */
@Composable
fun VexraShortcuts(
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
                onClick = { onSpecialKey("${shortcut.modifier}+${shortcut.key}") },
                modifier = Modifier.defaultMinSize(minHeight = 40.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VexraAccent.copy(alpha = 0.15f),
                    contentColor = VexraAccent,
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(shortcut.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}


/** Shows last sent messages in glass-morphism cards. */
@Composable
fun VexraSentHistory(items: List<String>) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Sent History", fontSize = 12.sp, color = VexraTextDim, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        items.take(10).forEach { text ->
            Surface(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                shape = RoundedCornerShape(10.dp),
                color = VexraCard,
                border = BorderStroke(1.dp, VexraBorder),
            ) {
                Text(
                    text, fontSize = 14.sp, color = VexraTextMuted,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}


/** Mode toggle — Keyboard / Trackpad with purple highlight. */
@Composable
fun VexraModeToggle(currentMode: MainViewModel.InputMode, onToggle: () -> Unit) {
    val isKeyboard = currentMode == MainViewModel.InputMode.KEYBOARD

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        color = VexraCard,
        border = BorderStroke(1.dp, VexraBorder),
    ) {
        Row(Modifier.fillMaxWidth().padding(4.dp), Arrangement.Center, Alignment.CenterVertically) {
            Button(
                onClick = { if (!isKeyboard) onToggle() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isKeyboard) VexraAccent else Color.Transparent,
                    contentColor = if (isKeyboard) Color.White else VexraTextDim,
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Keyboard, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Keyboard", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(4.dp))
            Button(
                onClick = { if (isKeyboard) onToggle() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (!isKeyboard) VexraAccent else Color.Transparent,
                    contentColor = if (!isKeyboard) Color.White else VexraTextDim,
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.TouchApp, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Trackpad", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}


/** Compose-then-send text input with glass field and purple send button. */
@Composable
fun VexraTextInput(onSend: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicTextField(
            value = text, onValueChange = { text = it },
            textStyle = TextStyle(color = VexraTextPrimary, fontSize = 16.sp),
            cursorBrush = SolidColor(VexraAccent),
            modifier = Modifier
                .weight(1f)
                .background(VexraCard, RoundedCornerShape(24.dp))
                .padding(horizontal = 20.dp, vertical = 14.dp),
            decorationBox = { innerTextField ->
                if (text.isEmpty()) {
                    Text("Type here, then tap Send →", color = VexraTextDim, fontSize = 16.sp)
                }
                innerTextField()
            },
        )
        FilledIconButton(
            onClick = { onSend(text); text = "" },
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = VexraAccent),
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}
