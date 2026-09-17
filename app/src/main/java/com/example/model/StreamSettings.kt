package com.example.model

enum class Resolution(val width: Int, val height: Int, val label: String) {
    RES_1080P(1920, 1080, "1080p FHD (1920x1080)"),
    RES_720P(1280, 720, "720p HD (1280x720)"),
    RES_480P(854, 480, "480p SD (854x480)")
}

enum class CodecMode(val label: String, val description: String) {
    H264_HARDWARE("H.264 Hardware (WebCodecs)", "Ultra-low latency (<25ms), high efficiency, 60 FPS"),
    TURBO_JPEG("Turbo Canvas (JPEG)", "Zero-buffer stream, 100% universal browser compatibility")
}

enum class AudioSourceType(val label: String, val description: String) {
    INTERNAL("Internal Device Audio", "Record game & app sounds digitally (Android 10+)"),
    MICROPHONE("Microphone Audio", "Record external voice & surroundings"),
    MUTED("Audio Disabled", "Video stream only")
}

data class StreamSettings(
    val port: Int = 8080,
    val resolution: Resolution = Resolution.RES_1080P,
    val fps: Int = 60,
    val bitrateKbps: Int = 8000,
    val codecMode: CodecMode = CodecMode.H264_HARDWARE,
    val audioSource: AudioSourceType = AudioSourceType.INTERNAL,
    val audioSampleRate: Int = 48000,
    val audioSyncOffsetMs: Int = 0,
    val dropStaleFrames: Boolean = true
)
