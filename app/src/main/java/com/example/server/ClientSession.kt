package com.example.server

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class ClientSession(
    val socket: Socket,
    private val onClosed: (ClientSession) -> Unit,
    private val onRequestKeyframe: () -> Unit
) {
    private val TAG = "ClientSession"
    private val isRunning = AtomicBoolean(true)
    private val sessionScope = CoroutineScope(Dispatchers.IO + Job())

    // Dedicated channels for video and audio to enforce zero-buffer accumulation
    // Video channel has capacity CONFLATED / 1: drops stale frame immediately if congested!
    private val videoChannel = Channel<ByteArray>(capacity = 1)
    private val audioChannel = Channel<ByteArray>(capacity = 8)
    private val controlChannel = Channel<ByteArray>(capacity = 4)

    val droppedVideoFrames = AtomicLong(0L)
    val sentVideoFrames = AtomicLong(0L)
    val sentBytes = AtomicLong(0L)

    var prefersH264: Boolean = true

    init {
        try {
            socket.tcpNoDelay = true
            socket.sendBufferSize = 128 * 1024
            socket.receiveBufferSize = 64 * 1024
        } catch (e: Exception) {
            Log.w(TAG, "Failed setting socket options: ${e.message}")
        }
    }

    fun start() {
        // Launch writer coroutine
        sessionScope.launch {
            val outputStream = BufferedOutputStream(socket.getOutputStream(), 64 * 1024)
            try {
                while (isActive && isRunning.get()) {
                    // Prioritize control and audio frames first, then video
                    val frameToSend = kotlinx.coroutines.selects.select<ByteArray?> {
                        controlChannel.onReceive { it }
                        audioChannel.onReceive { it }
                        videoChannel.onReceive { it }
                    }

                    if (frameToSend != null) {
                        outputStream.write(frameToSend)
                        outputStream.flush()
                        sentBytes.addAndGet(frameToSend.size.toLong())
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Writer loop ended: ${e.message}")
            } finally {
                close()
            }
        }

        // Launch reader coroutine
        sessionScope.launch {
            val inputStream = socket.getInputStream()
            try {
                while (isActive && isRunning.get()) {
                    val frame = WebSocketProtocol.readClientFrame(inputStream) ?: break
                    val opcode = frame.first
                    val payload = frame.second

                    when (opcode) {
                        0x01 -> {
                            // Text JSON message from web client
                            val text = String(payload, Charsets.UTF_8)
                            handleClientMessage(text)
                        }
                        0x08 -> {
                            // Close frame
                            break
                        }
                        0x09 -> {
                            // Ping frame -> reply with Pong
                            val pongFrame = WebSocketProtocol.buildFrame(0x0A, payload)
                            controlChannel.trySend(pongFrame)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Reader loop ended: ${e.message}")
            } finally {
                close()
            }
        }
    }

    private fun handleClientMessage(text: String) {
        try {
            val json = JSONObject(text)
            val cmd = json.optString("cmd")
            when (cmd) {
                "ping" -> {
                    val ts = json.optLong("ts", System.currentTimeMillis())
                    val pongJson = JSONObject().apply {
                        put("cmd", "pong")
                        put("ts", ts)
                    }
                    sendTextMessage(pongJson.toString())
                }
                "clientHello" -> {
                    prefersH264 = json.optBoolean("webCodecs", true)
                    onRequestKeyframe()
                }
                "requestKeyFrame" -> {
                    onRequestKeyframe()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error handling client message: ${e.message}")
        }
    }

    /**
     * Sends video frame with anti-lag drop policy:
     * If writer channel is busy, drops the older frame immediately so client stays in real-time.
     */
    fun sendVideoFrame(framePayload: ByteArray) {
        if (!isRunning.get()) return
        val wsFrame = WebSocketProtocol.buildFrame(0x02, framePayload)
        val result = videoChannel.trySend(wsFrame)
        if (result.isSuccess) {
            sentVideoFrames.incrementAndGet()
        } else {
            droppedVideoFrames.incrementAndGet()
        }
    }

    /**
     * Sends audio frame with high priority.
     */
    fun sendAudioFrame(framePayload: ByteArray) {
        if (!isRunning.get()) return
        val wsFrame = WebSocketProtocol.buildFrame(0x02, framePayload)
        audioChannel.trySend(wsFrame)
    }

    fun sendTextMessage(text: String) {
        if (!isRunning.get()) return
        val wsFrame = WebSocketProtocol.buildFrame(0x01, text.toByteArray(Charsets.UTF_8))
        controlChannel.trySend(wsFrame)
    }

    fun close() {
        if (isRunning.compareAndSet(true, false)) {
            try {
                videoChannel.close()
                audioChannel.close()
                controlChannel.close()
                sessionScope.cancel()
                socket.close()
            } catch (e: Exception) {
                // Ignore socket close error
            }
            onClosed(this)
        }
    }
}
