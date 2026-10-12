package com.fabula.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** RIFF/WAVE header for interleaved signed 16-bit little-endian PCM. */
object PcmWav {
    const val MAX_DATA_BYTES = 0xffffffffL - 36L

    fun header(dataBytes: Long, sampleRate: Int, channels: Int): ByteArray {
        require(channels in 1..2 && sampleRate > 0)
        require(dataBytes in 0..MAX_DATA_BYTES && dataBytes % (channels * 2) == 0L)
        return ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt((36 + dataBytes).toInt())
            put("WAVEfmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1)
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(sampleRate * channels * 2)
            putShort((channels * 2).toShort())
            putShort(16)
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataBytes.toInt())
        }.array()
    }
}
