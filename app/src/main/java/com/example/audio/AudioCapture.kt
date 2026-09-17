package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Log
import com.example.model.AudioSourceType
import com.example.model.StreamSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

class AudioCapture(
    private val mediaProjection: MediaProjection,
    private val settings: StreamSettings,
    private val onAudioAvailable: (ByteArray) -> Unit
) {
    private val TAG = "AudioCapture"
    private val isRunning = AtomicBoolean(false)
    private val captureScope = CoroutineScope(Dispatchers.IO + Job())
    private var audioRecord: AudioRecord? = null

    @SuppressLint("MissingPermission")
    fun start() {
        if (settings.audioSource == AudioSourceType.MUTED) {
            Log.i(TAG, "Audio is muted by user settings.")
            return
        }

        if (!isRunning.compareAndSet(false, true)) return

        captureScope.launch {
            try {
                val sampleRate = settings.audioSampleRate
                val channelConfig = AudioFormat.CHANNEL_IN_STEREO
                val audioFormat = AudioFormat.ENCODING_PCM_16BIT
                val channels = 2

                val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
                val bufferSize = minBufferSize.coerceAtLeast(sampleRate * channels * 2 / 20) // ~50ms buffer

                var record: AudioRecord? = null

                if (settings.audioSource == AudioSourceType.INTERNAL && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        val playbackConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                            .addMatchingUsage(AudioAttributes.USAGE_GAME)
                            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                            .build()

                        val format = AudioFormat.Builder()
                            .setEncoding(audioFormat)
                            .setSampleRate(sampleRate)
                            .setChannelMask(channelConfig)
                            .build()

                        record = AudioRecord.Builder()
                            .setAudioPlaybackCaptureConfig(playbackConfig)
                            .setAudioFormat(format)
                            .setBufferSizeInBytes(bufferSize)
                            .build()

                        Log.i(TAG, "AudioPlaybackCapture initialized for digital internal audio.")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to create internal audio capture, falling back to MIC: ${e.message}")
                    }
                }

                if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
                    record = AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        sampleRate,
                        channelConfig,
                        audioFormat,
                        bufferSize
                    )
                    Log.i(TAG, "AudioRecord initialized using MIC.")
                }

                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord failed to initialize.")
                    return@launch
                }

                audioRecord = record
                record.startRecording()
                Log.i(TAG, "Audio recording started at $sampleRate Hz Stereo.")

                // Chunk size: ~20ms of audio (48000 * 2 channels * 2 bytes * 0.02s = 3840 bytes)
                val chunkSize = (sampleRate * channels * 2 / 50).coerceAtLeast(1024)
                val audioBuffer = ByteArray(chunkSize)

                while (isActive && isRunning.get()) {
                    val readBytes = record.read(audioBuffer, 0, audioBuffer.size)
                    if (readBytes > 0) {
                        // Audio Packet:
                        // [0x02 (tag: audio), sampleRate (4 bytes), channels (1 byte), timestamp (8 bytes), ...pcmBytes]
                        val packet = ByteBuffer.allocate(14 + readBytes).order(ByteOrder.BIG_ENDIAN)
                        packet.put(0x02.toByte())
                        packet.putInt(sampleRate)
                        packet.put(channels.toByte())
                        packet.putLong(System.currentTimeMillis())
                        packet.put(audioBuffer, 0, readBytes)

                        onAudioAvailable(packet.array())
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Audio recording error: ${e.message}", e)
            } finally {
                stopInternal()
            }
        }
    }

    private fun stopInternal() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (ignored: Exception) {}
    }

    fun stop() {
        if (isRunning.compareAndSet(true, false)) {
            captureScope.cancel()
            stopInternal()
        }
    }
}
