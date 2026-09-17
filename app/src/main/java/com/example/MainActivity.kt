package com.example

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.example.ui.MirrorScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.MirrorViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MirrorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                // Media projection screen capture launcher
                val projectionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                        viewModel.startService(result.resultCode, result.data!!, this)
                    } else {
                        Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
                    }
                }

                // Permissions launcher (Audio + Notifications)
                val permissionsLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) {
                    // Once permissions handled, launch screen capture intent
                    val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
                }

                fun requestCapture() {
                    val permissions = mutableListOf<String>()
                    permissions.add(Manifest.permission.RECORD_AUDIO)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    permissionsLauncher.launch(permissions.toTypedArray())
                }

                MirrorScreen(
                    viewModel = viewModel,
                    onRequestMediaProjection = { requestCapture() },
                    onStopMirroring = { viewModel.stopService(this) }
                )
            }
        }
    }
}

@androidx.compose.runtime.Composable
fun Greeting(name: String, modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier) {
    androidx.compose.material3.Text(text = "Hello $name!", modifier = modifier)
}

