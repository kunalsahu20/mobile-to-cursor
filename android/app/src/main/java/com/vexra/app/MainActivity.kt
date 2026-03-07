package com.vexra.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vexra.app.ui.MainScreen
import com.vexra.app.ui.theme.MobileToCursorTheme
import com.vexra.app.viewmodel.MainViewModel

/**
 * Single Activity — sets up Compose with the dark theme
 * and wires MainScreen to the ViewModel.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MobileToCursorTheme {
                val viewModel: MainViewModel = viewModel()
                val uiState by viewModel.uiState.collectAsState()
                val updateState by viewModel.updateState.collectAsState()

                MainScreen(
                    uiState = uiState,
                    updateState = updateState,
                    onSendText = viewModel::sendText,
                    onKeyStroke = viewModel::sendKeyStroke,
                    onBackspace = viewModel::sendBackspace,
                    onCursorMove = viewModel::sendCursorMove,
                    onSpecialKey = viewModel::onSpecialKey,
                    onToggleModifier = viewModel::toggleModifier,
                    onToggleMode = viewModel::toggleMode,
                    onTrackpadMove = viewModel::onTrackpadMove,
                    onTrackpadTap = viewModel::onTrackpadTap,
                    onTrackpadLongPress = viewModel::onTrackpadLongPress,
                    onTrackpadScroll = viewModel::onTrackpadScroll,
                    onDragStart = viewModel::onDragStart,
                    onDragEnd = viewModel::onDragEnd,
                    onTwoFingerTap = viewModel::onTwoFingerTap,
                    onThreeFingerSwipe = viewModel::onThreeFingerSwipe,
                    onThreeFingerTap = viewModel::onThreeFingerTap,
                    onFourFingerSwipe = viewModel::onFourFingerSwipe,
                    onFourFingerTap = viewModel::onFourFingerTap,
                    onConnect = viewModel::connect,
                    onDisconnect = viewModel::disconnect,
                    onCheckUpdate = viewModel::checkForUpdate,
                    onDownloadUpdate = viewModel::downloadUpdate,
                )
            }
        }
    }
}
