package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.AudioSourceType
import com.example.model.CodecMode
import com.example.model.Resolution
import com.example.model.StreamSettings
import com.example.model.StreamStats
import com.example.server.MirrorService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MirrorViewModel(application: Application) : AndroidViewModel(application) {

    private val _settings = MutableStateFlow(StreamSettings())
    val settings: StateFlow<StreamSettings> = _settings.asStateFlow()

    val isStreaming: StateFlow<Boolean> = MirrorService.isRunningFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    val stats: StateFlow<StreamStats> = MirrorService.statsFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, StreamStats())

    private val _uiNotice = MutableStateFlow<String?>(null)
    val uiNotice: StateFlow<String?> = _uiNotice.asStateFlow()

    fun clearNotice() {
        _uiNotice.value = null
    }

    fun showNotice(msg: String) {
        _uiNotice.value = msg
    }

    fun updateResolution(resolution: Resolution) {
        _settings.value = _settings.value.copy(resolution = resolution)
        MirrorService.currentSettings = _settings.value
    }

    fun updateFps(fps: Int) {
        _settings.value = _settings.value.copy(fps = fps)
        MirrorService.currentSettings = _settings.value
    }

    fun updateBitrate(bitrateKbps: Int) {
        _settings.value = _settings.value.copy(bitrateKbps = bitrateKbps)
        MirrorService.currentSettings = _settings.value
    }

    fun updateCodec(codec: CodecMode) {
        _settings.value = _settings.value.copy(codecMode = codec)
        MirrorService.currentSettings = _settings.value
    }

    fun updateAudioSource(source: AudioSourceType) {
        _settings.value = _settings.value.copy(audioSource = source)
        MirrorService.currentSettings = _settings.value
    }

    fun updateDropStaleFrames(drop: Boolean) {
        _settings.value = _settings.value.copy(dropStaleFrames = drop)
        MirrorService.currentSettings = _settings.value
    }

    fun getAdbCommand(): String {
        return "adb forward tcp:${_settings.value.port} tcp:${_settings.value.port}"
    }

    fun startService(resultCode: Int, data: Intent, context: Context) {
        MirrorService.currentSettings = _settings.value
        val intent = Intent(context, MirrorService::class.java).apply {
            action = MirrorService.ACTION_START
            putExtra(MirrorService.EXTRA_RESULT_CODE, resultCode)
            putExtra(MirrorService.EXTRA_RESULT_DATA, data)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stopService(context: Context) {
        val intent = Intent(context, MirrorService::class.java).apply {
            action = MirrorService.ACTION_STOP
        }
        context.startService(intent)
    }
}
