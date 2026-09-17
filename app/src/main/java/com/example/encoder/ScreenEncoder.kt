package com.example.encoder

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.example.model.CodecMode
import com.example.model.StreamSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

class ScreenEncoder(
    private val mediaProjection: MediaProjection,
    private val settings: StreamSettings,
    private val densityDpi: Int,
    private val onFrameAvailable: (ByteArray) -> Unit
) {
    private val TAG = "ScreenEncoder"
    private val isRunning = AtomicBoolean(false)
    private val encoderScope = CoroutineScope(Dispatchers.IO + Job())

    private var virtualDisplay: VirtualDisplay? = null
    private var mediaCodec: MediaCodec? = null
    private var imageReader: ImageReader? = null
    private var spsPpsData: ByteArray? = null

    fun start() {
        if (!isRunning.compareAndSet(false, true)) return

        if (settings.codecMode == CodecMode.H264_HARDWARE) {
            startH264Encoder()
        } else {
            startJpegEncoder()
        }
    }

    private fun startH264Encoder() {
        encoderScope.launch {
            try {
                val width = settings.resolution.width
                val height = settings.resolution.height
                val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)

                format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                format.setInteger(MediaFormat.KEY_BIT_RATE, settings.bitrateKbps * 1000)
                format.setInteger(MediaFormat.KEY_FRAME_RATE, settings.fps)
                format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1) // 1s keyframe interval
                format.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)
                format.setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 1_000_000L / settings.fps)
                format.setInteger(MediaFormat.KEY_PRIORITY, 0) // Realtime

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        format.setInteger(MediaFormat.KEY_LATENCY, 0)
                        format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
                    } catch (e: Exception) {
                        Log.w(TAG, "Low latency keys not supported: ${e.message}")
                    }
                }

                val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
                mediaCodec = codec
                codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                val inputSurface = codec.createInputSurface()
                codec.start()

                virtualDisplay = mediaProjection.createVirtualDisplay(
                    "USB_Mirror_H264",
                    width,
                    height,
                    densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    inputSurface,
                    null,
                    null
                )

                Log.i(TAG, "H.264 Hardware encoder started: ${width}x${height} @ ${settings.fps}fps, ${settings.bitrateKbps}kbps")

                val bufferInfo = MediaCodec.BufferInfo()

                while (isActive && isRunning.get()) {
                    val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000L) // 10ms timeout
                    if (outIndex >= 0) {
                        val outBuf = codec.getOutputBuffer(outIndex)
                        if (outBuf != null) {
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)

                            val isConfig = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                            val isKeyframe = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0

                            if (isConfig) {
                                val configBytes = ByteArray(bufferInfo.size)
                                outBuf.get(configBytes)
                                spsPpsData = configBytes
                            } else {
                                val nalBytes = ByteArray(bufferInfo.size)
                                outBuf.get(nalBytes)

                                // Frame packet:
                                // [0x01 (tag: video), 0x01 (codec: H.264), isKeyframe (0/1), timestamp (8 bytes), payload...]
                                val headerSize = 11
                                val prependSps = isKeyframe && spsPpsData != null
                                val spsSize = if (prependSps) spsPpsData!!.size else 0
                                val totalSize = headerSize + spsSize + nalBytes.size

                                val packet = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)
                                packet.put(0x01.toByte()) // Video tag
                                packet.put(0x01.toByte()) // H.264
                                packet.put((if (isKeyframe) 1 else 0).toByte())
                                packet.putLong(System.currentTimeMillis())

                                if (prependSps) {
                                    packet.put(spsPpsData!!)
                                }
                                packet.put(nalBytes)

                                onFrameAvailable(packet.array())
                            }
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "H264 encoder error: ${e.message}", e)
            } finally {
                stopInternal()
            }
        }
    }

    private fun startJpegEncoder() {
        val width = settings.resolution.width
        val height = settings.resolution.height

        try {
            val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            imageReader = reader

            virtualDisplay = mediaProjection.createVirtualDisplay(
                "USB_Mirror_JPEG",
                width,
                height,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                null
            )

            reader.setOnImageAvailableListener({ ir ->
                val image = ir.acquireLatestImage() ?: return@setOnImageAvailableListener
                if (!isRunning.get()) {
                    image.close()
                    return@setOnImageAvailableListener
                }

                encoderScope.launch {
                    try {
                        val planes = image.planes
                        val buffer = planes[0].buffer
                        val pixelStride = planes[0].pixelStride
                        val rowStride = planes[0].rowStride
                        val rowPadding = rowStride - pixelStride * width

                        val bitmap = Bitmap.createBitmap(
                            width + rowPadding / pixelStride,
                            height,
                            Bitmap.Config.ARGB_8888
                        )
                        bitmap.copyPixelsFromBuffer(buffer)
                        image.close()

                        // Crop if rowPadding exists
                        val finalBitmap = if (rowPadding > 0) {
                            Bitmap.createBitmap(bitmap, 0, 0, width, height)
                        } else {
                            bitmap
                        }

                        val baos = ByteArrayOutputStream()
                        finalBitmap.compress(Bitmap.CompressFormat.JPEG, 75, baos)
                        val jpegBytes = baos.toByteArray()

                        if (finalBitmap != bitmap) {
                            finalBitmap.recycle()
                        }
                        bitmap.recycle()

                        // Packet: [0x01 (tag: video), 0x02 (codec: JPEG), 1 (keyframe), timestamp (8 bytes), jpegBytes...]
                        val packet = ByteBuffer.allocate(11 + jpegBytes.size).order(ByteOrder.BIG_ENDIAN)
                        packet.put(0x01.toByte())
                        packet.put(0x02.toByte())
                        packet.put(1.toByte())
                        packet.putLong(System.currentTimeMillis())
                        packet.put(jpegBytes)

                        onFrameAvailable(packet.array())
                    } catch (e: Exception) {
                        Log.w(TAG, "JPEG encode error: ${e.message}")
                    }
                }
            }, null)

            Log.i(TAG, "Turbo JPEG encoder started: ${width}x${height}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start JPEG encoder: ${e.message}", e)
        }
    }

    fun requestKeyframe() {
        val codec = mediaCodec ?: return
        try {
            val params = Bundle()
            params.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
            codec.setParameters(params)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to request sync frame: ${e.message}")
        }
    }

    private fun stopInternal() {
        try {
            virtualDisplay?.release()
            virtualDisplay = null
        } catch (ignored: Exception) {}

        try {
            mediaCodec?.stop()
            mediaCodec?.release()
            mediaCodec = null
        } catch (ignored: Exception) {}

        try {
            imageReader?.close()
            imageReader = null
        } catch (ignored: Exception) {}
    }

    fun stop() {
        if (isRunning.compareAndSet(true, false)) {
            encoderScope.cancel()
            stopInternal()
        }
    }
}
