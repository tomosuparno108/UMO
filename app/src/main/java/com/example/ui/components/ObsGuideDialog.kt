package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyanPrimary

@Composable
fun ObsGuideDialog(
    serverPort: Int,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LiveTv,
                        contentDescription = null,
                        tint = CyanPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "OBS Studio Quick Setup",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                GuideStep(
                    stepNum = "1",
                    title = "Connect USB & Enable Debugging",
                    desc = "Connect Android to PC with USB. Turn on 'USB Debugging' in Developer Options."
                )

                GuideStep(
                    stepNum = "2",
                    title = "Forward Port via ADB",
                    desc = "Open Command Prompt / Terminal on your PC and run:",
                    codeSnippet = "adb forward tcp:$serverPort tcp:$serverPort"
                )

                GuideStep(
                    stepNum = "3",
                    title = "Add Browser Source in OBS",
                    desc = "In OBS Studio Sources dock, click '+' -> choose 'Browser'. Name it 'Android Mirror'."
                )

                GuideStep(
                    stepNum = "4",
                    title = "Configure OBS Browser Settings",
                    desc = "Enter the following values in the Browser Source properties:",
                    bulletPoints = listOf(
                        "URL: http://localhost:$serverPort?obs=true",
                        "Width: 1080, Height: 1920 (Portrait) or 1920x1080 (Landscape)",
                        "FPS: 60",
                        "Control audio via OBS: CHECK (Required for real-time sound!)"
                    )
                )

                GuideStep(
                    stepNum = "5",
                    title = "Hit Start Mirroring",
                    desc = "Click START USB MIRRORING on this app. The low-latency screen and digital audio will immediately stream into OBS!"
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("dismiss_guide_button")
            ) {
                Text(
                    text = "Got It!",
                    color = Color(0xFF001E28),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun GuideStep(
    stepNum: String,
    title: String,
    desc: String,
    codeSnippet: String? = null,
    bulletPoints: List<String>? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(CyanPrimary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stepNum,
                color = Color(0xFF001E28),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = desc,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )

            if (codeSnippet != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF070B14))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = codeSnippet,
                        color = CyanPrimary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            if (bulletPoints != null) {
                Spacer(modifier = Modifier.height(6.dp))
                bulletPoints.forEach { pt ->
                    Text(
                        text = "• $pt",
                        fontSize = 11.sp,
                        fontWeight = if (pt.contains("Control audio")) FontWeight.Bold else FontWeight.Normal,
                        color = if (pt.contains("Control audio")) CyanPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}
