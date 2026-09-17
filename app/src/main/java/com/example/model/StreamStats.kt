package com.example.model

data class StreamStats(
    val isStreaming: Boolean = false,
    val activeClients: Int = 0,
    val fps: Double = 0.0,
    val bitrateMbps: Double = 0.0,
    val totalFramesSent: Long = 0L,
    val droppedFrames: Long = 0L,
    val audioActive: Boolean = false,
    val audioBytesSent: Long = 0L,
    val serverPort: Int = 8080,
    val deviceIpAddress: String = "",
    val usbLocalhostUrl: String = "http://localhost:8080",
    val uptimeSeconds: Long = 0L,
    val lastError: String? = null
)
