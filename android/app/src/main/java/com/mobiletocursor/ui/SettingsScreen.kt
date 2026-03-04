package com.mobiletocursor.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobiletocursor.network.UpdateChecker
import com.mobiletocursor.ui.components.VexraGlowBackground
import com.mobiletocursor.ui.theme.VexraAccent
import com.mobiletocursor.ui.theme.VexraBg
import com.mobiletocursor.ui.theme.VexraBorder
import com.mobiletocursor.ui.theme.VexraCard
import com.mobiletocursor.ui.theme.VexraGreen
import com.mobiletocursor.ui.theme.VexraRed
import com.mobiletocursor.ui.theme.VexraTextDim
import com.mobiletocursor.ui.theme.VexraTextMuted
import com.mobiletocursor.ui.theme.VexraTextPrimary

/**
 * Settings screen — Stitch design with glass cards.
 * Sections: General (disconnect), Updates (check for updates from GitHub).
 */
@Composable
fun SettingsScreen(
    connectedHost: String,
    updateState: UpdateChecker.UpdateUiState,
    onBack: () -> Unit,
    onDisconnect: () -> Unit,
    onCheckUpdate: () -> Unit,
    onDownloadUpdate: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VexraBg),
    ) {
        VexraGlowBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // ── Top bar ──
            SettingsTopBar(onBack)

            Spacer(Modifier.height(12.dp))

            // ── GENERAL section ──
            SectionLabel("GENERAL")
            GeneralCard(connectedHost, onDisconnect)

            Spacer(Modifier.height(24.dp))

            // ── UPDATES section ──
            SectionLabel("UPDATES")
            UpdatesCard(updateState, onCheckUpdate, onDownloadUpdate)

            Spacer(Modifier.weight(1f))

            // ── Footer ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "VEXRA",
                    color = VexraAccent,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "© 2025 Vexra. All rights reserved.",
                    color = VexraTextDim,
                    fontSize = 11.sp,
                )
            }
        }
    }
}


// ═══════════════════════════════════════════════════
//  Sub-components
// ═══════════════════════════════════════════════════

@Composable
private fun SettingsTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                "Back",
                tint = VexraTextPrimary,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            "Settings",
            color = VexraTextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.weight(1f))
        // Invisible spacer to center the title
        Spacer(Modifier.size(48.dp))
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text(
        label,
        color = VexraTextDim,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

@Composable
private fun GeneralCard(connectedHost: String, onDisconnect: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        color = VexraCard,
        border = BorderStroke(1.dp, VexraBorder),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Status row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = VexraGreen.copy(alpha = 0.15f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .size(12.dp)
                                .background(VexraGreen, CircleShape),
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Status", color = VexraTextMuted, fontSize = 12.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).background(VexraGreen, CircleShape))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Connected to $connectedHost",
                            color = VexraTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Disconnect button
            Button(
                onClick = onDisconnect,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VexraRed.copy(alpha = 0.15f),
                    contentColor = VexraRed,
                ),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Default.WifiOff, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Disconnect", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun UpdatesCard(
    updateState: UpdateChecker.UpdateUiState,
    onCheckUpdate: () -> Unit,
    onDownloadUpdate: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        color = VexraCard,
        border = BorderStroke(1.dp, VexraBorder),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Version row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = VexraAccent.copy(alpha = 0.15f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Update, null, tint = VexraAccent, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Current Version", color = VexraTextMuted, fontSize = 12.sp)
                    Text(
                        "v${UpdateChecker.CURRENT_VERSION}",
                        color = VexraTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Text(
                    "v${UpdateChecker.CURRENT_VERSION}",
                    color = VexraTextDim,
                    fontSize = 13.sp,
                )
            }

            Spacer(Modifier.height(16.dp))

            // Action based on state
            when (updateState.state) {
                UpdateChecker.UpdateState.IDLE, UpdateChecker.UpdateState.NO_UPDATE -> {
                    UpdateButton("Check for Updates", VexraAccent, onCheckUpdate)
                    if (updateState.state == UpdateChecker.UpdateState.NO_UPDATE) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "You're on the latest version!",
                            color = VexraGreen,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                UpdateChecker.UpdateState.CHECKING -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            color = VexraAccent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("Checking...", color = VexraTextMuted, fontSize = 14.sp)
                    }
                }

                UpdateChecker.UpdateState.UPDATE_AVAILABLE -> {
                    Text(
                        "v${updateState.info.latestVersion} available!",
                        color = VexraGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(10.dp))
                    UpdateButton("Download & Install", VexraAccent, onDownloadUpdate, icon = true)
                }

                UpdateChecker.UpdateState.DOWNLOADING -> {
                    Text(
                        "Downloading... ${updateState.progress}%",
                        color = VexraTextMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { updateState.progress / 100f },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = VexraAccent,
                        trackColor = VexraBorder,
                    )
                }

                UpdateChecker.UpdateState.READY_TO_INSTALL -> {
                    Text(
                        "APK downloaded — installing...",
                        color = VexraGreen,
                        fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }

                UpdateChecker.UpdateState.ERROR -> {
                    Text(
                        updateState.errorMessage,
                        color = VexraRed,
                        fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(10.dp))
                    UpdateButton("Retry", VexraAccent, onCheckUpdate)
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "Auto-download from GitHub releases",
                color = VexraTextDim,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun UpdateButton(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    icon: Boolean = false,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = RoundedCornerShape(14.dp),
    ) {
        if (icon) {
            Icon(Icons.Default.CloudDownload, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}
