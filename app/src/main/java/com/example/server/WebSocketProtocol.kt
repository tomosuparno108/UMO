package com.example.server

import android.util.Base64
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

object WebSocketProtocol {
    private const val GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"

    fun computeAcceptKey(clientKey: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        val hash = md.digest((clientKey.trim() + GUID).toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    /**
     * Builds a WebSocket frame byte array for Server -> Client (unmasked).
     * Opcode: 0x01 (Text), 0x02 (Binary), 0x08 (Close), 0x09 (Ping), 0x0A (Pong)
     */
    fun buildFrame(opcode: Int, payload: ByteArray): ByteArray {
        val payloadLen = payload.size
        val headerLen = when {
            payloadLen <= 125 -> 2
            payloadLen <= 65535 -> 4
            else -> 10
        }

        val frame = ByteBuffer.allocate(headerLen + payloadLen).order(ByteOrder.BIG_ENDIAN)
        // FIN bit set (0x80) | opcode
        frame.put((0x80 or (opcode and 0x0F)).toByte())

        when {
            payloadLen <= 125 -> {
                frame.put(payloadLen.toByte()) // Mask bit is 0
            }
            payloadLen <= 65535 -> {
                frame.put(126.toByte())
                frame.putShort(payloadLen.toShort())
            }
            else -> {
                frame.put(127.toByte())
                frame.putLong(payloadLen.toLong())
            }
        }

        frame.put(payload)
        return frame.array()
    }

    /**
     * Reads a single incoming client frame (Client -> Server, masked).
     * Returns a pair of (opcode, unmaskedPayload) or null if stream closed.
     */
    fun readClientFrame(inputStream: InputStream): Pair<Int, ByteArray>? {
        val b0 = inputStream.read()
        if (b0 == -1) return null
        val opcode = b0 and 0x0F

        val b1 = inputStream.read()
        if (b1 == -1) return null
        val masked = (b1 and 0x80) != 0
        var payloadLen = (b1 and 0x7F).toLong()

        if (payloadLen == 126L) {
            val byte1 = inputStream.read()
            val byte2 = inputStream.read()
            if (byte1 == -1 || byte2 == -1) return null
            payloadLen = ((byte1 shl 8) or byte2).toLong()
        } else if (payloadLen == 127L) {
            var len = 0L
            for (i in 0 until 8) {
                val b = inputStream.read()
                if (b == -1) return null
                len = (len shl 8) or (b.toLong() and 0xFF)
            }
            payloadLen = len
        }

        val mask = ByteArray(4)
        if (masked) {
            var bytesRead = 0
            while (bytesRead < 4) {
                val r = inputStream.read(mask, bytesRead, 4 - bytesRead)
                if (r == -1) return null
                bytesRead += r
            }
        }

        if (payloadLen > 10 * 1024 * 1024) {
            // Safety cap: ignore frames larger than 10MB
            return null
        }

        val payload = ByteArray(payloadLen.toInt())
        var totalRead = 0
        while (totalRead < payload.size) {
            val r = inputStream.read(payload, totalRead, payload.size - totalRead)
            if (r == -1) return null
            totalRead += r
        }

        if (masked) {
            for (i in payload.indices) {
                payload[i] = (payload[i].toInt() xor mask[i % 4].toInt()).toByte()
            }
        }

        return Pair(opcode, payload)
    }
}
