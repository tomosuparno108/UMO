package com.example.server

import android.util.Log
import com.example.model.StreamStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class MirrorServer(
    val port: Int = 8080,
    private val onRequestKeyframe: () -> Unit
) {
    private val TAG = "MirrorServer"
    private var serverSocket: ServerSocket? = null
    private val isRunning = AtomicBoolean(false)
    private val serverScope = CoroutineScope(Dispatchers.IO + Job())

    private val clients = CopyOnWriteArrayList<ClientSession>()

    private val _statsFlow = MutableStateFlow(StreamStats(serverPort = port))
    val statsFlow = _statsFlow.asStateFlow()

    private val totalFramesCounter = AtomicLong(0L)
    private val audioBytesCounter = AtomicLong(0L)
    private var startTimeMs: Long = 0L

    fun start() {
        if (!isRunning.compareAndSet(false, true)) return
        startTimeMs = System.currentTimeMillis()

        serverScope.launch {
            try {
                val ss = ServerSocket(port, 50, InetAddress.getByName("0.0.0.0"))
                ss.reuseAddress = true
                serverSocket = ss
                Log.i(TAG, "MirrorServer listening on port $port")

                launchStatsMonitor()

                while (isActive && isRunning.get()) {
                    try {
                        val clientSocket = ss.accept()
                        handleIncomingConnection(clientSocket)
                    } catch (e: Exception) {
                        if (!isRunning.get()) break
                        Log.w(TAG, "Socket accept error: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind server socket on port $port: ${e.message}", e)
                _statsFlow.value = _statsFlow.value.copy(lastError = e.message)
            }
        }
    }

    private fun handleIncomingConnection(socket: Socket) {
        serverScope.launch {
            try {
                socket.soTimeout = 8000
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                val output = socket.getOutputStream()

                // Read request line
                val requestLine = reader.readLine() ?: run {
                    socket.close()
                    return@launch
                }

                // Read headers
                val headers = mutableMapOf<String, String>()
                var headerLine = reader.readLine()
                while (!headerLine.isNullOrEmpty()) {
                    val colonIdx = headerLine.indexOf(':')
                    if (colonIdx > 0) {
                        val key = headerLine.substring(0, colonIdx).trim().lowercase()
                        val value = headerLine.substring(colonIdx + 1).trim()
                        headers[key] = value
                    }
                    headerLine = reader.readLine()
                }

                val isWebSocket = headers["upgrade"]?.equals("websocket", ignoreCase = true) == true
                val path = requestLine.split(" ").getOrNull(1) ?: "/"

                if (isWebSocket) {
                    socket.soTimeout = 0 // Disable timeout for continuous WebSocket
                    val secKey = headers["sec-websocket-key"]
                    if (secKey == null) {
                        sendHttpResponse(output, 400, "Bad Request", "Missing Sec-WebSocket-Key")
                        socket.close()
                        return@launch
                    }

                    val acceptKey = WebSocketProtocol.computeAcceptKey(secKey)
                    val handshake = ("HTTP/1.1 101 Switching Protocols\r\n" +
                            "Upgrade: websocket\r\n" +
                            "Connection: Upgrade\r\n" +
                            "Sec-WebSocket-Accept: $acceptKey\r\n\r\n")
                    output.write(handshake.toByteArray(Charsets.UTF_8))
                    output.flush()

                    val session = ClientSession(
                        socket = socket,
                        onClosed = { closedSession ->
                            clients.remove(closedSession)
                            updateClientCount()
                        },
                        onRequestKeyframe = onRequestKeyframe
                    )
                    clients.add(session)
                    session.start()
                    updateClientCount()
                    onRequestKeyframe()
                } else {
                    // Regular HTTP Request
                    when {
                        path.startsWith("/status") || path.startsWith("/health") -> {
                            val json = JSONObject().apply {
                                put("status", "running")
                                put("clients", clients.size)
                                put("port", port)
                                put("fps", _statsFlow.value.fps)
                                put("bitrateMbps", _statsFlow.value.bitrateMbps)
                                put("droppedFrames", _statsFlow.value.droppedFrames)
                            }.toString()
                            sendHttpResponse(output, 200, "OK", json, "application/json")
                            socket.close()
                        }
                        else -> {
                            // Serve HTML client
                            val html = WebClientHtml.getHtml(port)
                            sendHttpResponse(output, 200, "OK", html, "text/html; charset=utf-8")
                            socket.close()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error handling incoming client: ${e.message}")
                try { socket.close() } catch (ignored: Exception) {}
            }
        }
    }

    private fun sendHttpResponse(
        output: OutputStream,
        code: Int,
        statusText: String,
        body: String,
        contentType: String = "text/plain"
    ) {
        val bodyBytes = body.toByteArray(Charsets.UTF_8)
        val response = ("HTTP/1.1 $code $statusText\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${bodyBytes.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n")
        output.write(response.toByteArray(Charsets.UTF_8))
        output.write(bodyBytes)
        output.flush()
    }

    fun broadcastVideo(payload: ByteArray) {
        totalFramesCounter.incrementAndGet()
        for (client in clients) {
            client.sendVideoFrame(payload)
        }
    }

    fun broadcastAudio(payload: ByteArray) {
        audioBytesCounter.addAndGet(payload.size.toLong())
        for (client in clients) {
            client.sendAudioFrame(payload)
        }
    }

    private fun updateClientCount() {
        _statsFlow.value = _statsFlow.value.copy(activeClients = clients.size)
    }

    private fun launchStatsMonitor() {
        serverScope.launch {
            var lastTotalFrames = 0L
            var lastTotalBytes = 0L
            var lastCheckTime = System.currentTimeMillis()

            while (isActive && isRunning.get()) {
                delay(1000)
                val now = System.currentTimeMillis()
                val deltaSec = (now - lastCheckTime).toDouble() / 1000.0
                if (deltaSec <= 0) continue

                val currentFrames = totalFramesCounter.get()
                val fps = ((currentFrames - lastTotalFrames) / deltaSec).coerceAtLeast(0.0)
                lastTotalFrames = currentFrames

                var currentTotalBytes = 0L
                var totalDropped = 0L
                for (c in clients) {
                    currentTotalBytes += c.sentBytes.get()
                    totalDropped += c.droppedVideoFrames.get()
                }

                val deltaBits = (currentTotalBytes - lastTotalBytes) * 8.0
                val bitrateMbps = ((deltaBits / deltaSec) / 1_000_000.0).coerceAtLeast(0.0)
                lastTotalBytes = currentTotalBytes
                lastCheckTime = now

                val uptime = (now - startTimeMs) / 1000L
                val localIp = getDeviceIpAddress()

                _statsFlow.value = _statsFlow.value.copy(
                    isStreaming = true,
                    activeClients = clients.size,
                    fps = String.format("%.1f", fps).toDoubleOrNull() ?: fps,
                    bitrateMbps = String.format("%.2f", bitrateMbps).toDoubleOrNull() ?: bitrateMbps,
                    totalFramesSent = currentFrames,
                    droppedFrames = totalDropped,
                    audioActive = audioBytesCounter.get() > 0,
                    audioBytesSent = audioBytesCounter.get(),
                    uptimeSeconds = uptime,
                    deviceIpAddress = localIp,
                    usbLocalhostUrl = "http://localhost:$port"
                )
            }
        }
    }

    private fun getDeviceIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return "127.0.0.1"
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                val addrs = intf.inetAddresses
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr.address.size == 4) {
                        return addr.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (e: Exception) {
            // fallback
        }
        return "127.0.0.1"
    }

    fun stop() {
        if (isRunning.compareAndSet(true, false)) {
            for (client in clients) {
                client.close()
            }
            clients.clear()
            try {
                serverSocket?.close()
            } catch (ignored: Exception) {}
            serverScope.cancel()
            _statsFlow.value = _statsFlow.value.copy(isStreaming = false, activeClients = 0)
        }
    }
}
