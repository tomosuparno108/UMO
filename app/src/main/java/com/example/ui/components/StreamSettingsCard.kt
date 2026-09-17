package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AudioSourceType
import com.example.model.CodecMode
import com.example.model.Resolution
import com.example.model.StreamSettings
import com.example.ui.theme.CyanPrimary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StreamSettingsCard(
    settings: StreamSettings,
    isStreaming: Boolean,
    onResolutionChanged: (Resolution) -> Unit,
    onFpsChanged: (Int) -> Unit,
    onBitrateChanged: (Int) -> Unit,
    onCodecChanged: (CodecMode) -> Unit,
    onAudioSourceChanged: (AudioSourceType) -> Unit,
    onDropStaleFramesChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("stream_settings_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Settings",
                    tint = CyanPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Stream & Latency Optimization",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (isStreaming) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Some changes take effect after restarting the stream.",
                    color = MaterialTheme.colorScheme.tertiary,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 1. Resolution
            Text(
                text = "RESOLUTION",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Resolution.values().forEach { res ->
                    val isSelected = settings.resolution == res
                    ChipOption(
                        label = when (res) {
                            Resolution.RES_1080P -> "1080p FHD"
                            Resolution.RES_720P -> "720p HD"
                            Resolution.RES_480P -> "480p SD"
                        },
                        isSelected = isSelected,
                        onClick = { onResolutionChanged(res) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Framerate (FPS)
            Text(
                text = "TARGET FRAMERATE",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(60 to "60 FPS (Ultra Smooth)", 30 to "30 FPS (Low CPU)").forEach { (fps, label) ->
                    val isSelected = settings.fps == fps
                    ChipOption(
                        label = label,
                        isSelected = isSelected,
                        onClick = { onFpsChanged(fps) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Bitrate Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TARGET BITRATE",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "${settings.bitrateKbps / 1000} Mbps",
                    color = CyanPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Slider(
                value = (settings.bitrateKbps / 1000).toFloat(),
                onValueChange = { onBitrateChanged((it * 1000).toInt()) },
                valueRange = 2f..20f,
                steps = 8,
                colors = SliderDefaults.colors(
                    thumbColor = CyanPrimary,
                    activeTrackColor = CyanPrimary,
                    inactiveTrackColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 4. Codec Mode
            Text(
                text = "STREAM CODEC ENGINE",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CodecMode.values().forEach { mode ->
                    val isSelected = settings.codecMode == mode
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                else MaterialTheme.colorScheme.surface
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) CyanPrimary else MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onCodecChanged(mode) }
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                text = mode.label,
                                color = if (isSelected) CyanPrimary else MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = mode.description,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 5. Audio Source Mode
            Text(
                text = "REALTIME AUDIO TRANSMISSION",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AudioSourceType.values().forEach { src ->
                    val isSelected = settings.audioSource == src
                    val icon = when (src) {
                        AudioSourceType.INTERNAL -> Icons.Default.Hearing
                        AudioSourceType.MICROPHONE -> Icons.Default.Mic
                        AudioSourceType.MUTED -> Icons.Default.MicOff
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                else MaterialTheme.colorScheme.surface
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) CyanPrimary else MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onAudioSourceChanged(src) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isSelected) CyanPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = src.label,
                                color = if (isSelected) CyanPrimary else MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = src.description,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 6. Anti-Lag Stale Frame Dropper
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Anti-Lag Engine (Zero-Buffer)",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Immediately drops congested frames so latency never accumulates over time.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = settings.dropStaleFrames,
                    onCheckedChange = onDropStaleFramesChanged,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = CyanPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        }
    }
}

@Composable
private fun ChipOption(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isSelected) CyanPrimary else MaterialTheme.colorScheme.surface
            )
            .border(
                width = 1.dp,
                color = if (isSelected) CyanPrimary else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = if (isSelected) Color(0xFF001E28) else MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
