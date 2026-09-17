package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.StreamStats
import com.example.ui.components.HeroStatusCard
import com.example.ui.components.ObsGuideDialog
import com.example.ui.components.StreamSettingsCard
import com.example.ui.components.TelemetryGrid
import com.example.ui.components.UsbAdbCard
import com.example.ui.theme.CyanPrimary
import com.example.viewmodel.MirrorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MirrorScreen(
    viewModel: MirrorViewModel,
    onRequestMediaProjection: () -> Unit,
    onStopMirroring: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isStreaming by viewModel.isStreaming.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val uiNotice by viewModel.uiNotice.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var showGuideDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiNotice) {
        uiNotice?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearNotice()
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(CyanPrimary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ScreenShare,
                                contentDescription = null,
                                tint = CyanPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "USB Mirror OBS",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Ultra Low-Latency & Realtime Audio",
                                fontSize = 11.sp,
                                color = CyanPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showGuideDialog = true },
                        modifier = Modifier.testTag("help_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.HelpOutline,
                            contentDescription = "OBS Setup Guide",
                            tint = CyanPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Live Hero Status & Start/Stop Button
                item {
                    HeroStatusCard(
                        isStreaming = isStreaming,
                        stats = stats,
                        onToggleStreaming = {
                            if (isStreaming) {
                                onStopMirroring()
                            } else {
                                onRequestMediaProjection()
                            }
                        }
                    )
                }

                // 2. USB PC Connection & ADB Command Card
                item {
                    UsbAdbCard(
                        adbCommand = viewModel.getAdbCommand(),
                        stats = stats,
                        onShowNotice = { viewModel.showNotice(it) }
                    )
                }

                // 3. Telemetry & Metrics Grid (FPS, Bitrate, Drops, Audio)
                item {
                    TelemetryGrid(
                        stats = stats
                    )
                }

                // 4. Stream Optimization & Settings
                item {
                    StreamSettingsCard(
                        settings = settings,
                        isStreaming = isStreaming,
                        onResolutionChanged = { viewModel.updateResolution(it) },
                        onFpsChanged = { viewModel.updateFps(it) },
                        onBitrateChanged = { viewModel.updateBitrate(it) },
                        onCodecChanged = { viewModel.updateCodec(it) },
                        onAudioSourceChanged = { viewModel.updateAudioSource(it) },
                        onDropStaleFramesChanged = { viewModel.updateDropStaleFrames(it) }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    if (showGuideDialog) {
        ObsGuideDialog(
            serverPort = settings.port,
            onDismiss = { showGuideDialog = false }
        )
    }
}
