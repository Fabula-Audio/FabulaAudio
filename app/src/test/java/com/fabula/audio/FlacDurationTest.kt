package com.fabula.audio

import java.io.DataOutputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class FlacDurationTest {
    @Test fun readsSampleAccurateDurationForAllPcmDepths() {
        for (rate in listOf(16000, 44100, 48000)) for (bits in listOf(16, 24)) {
            val file = File.createTempFile("duration", ".flac")
            try {
                val frames = rate * 107L + 17
                DataOutputStream(file.outputStream()).use {
                    it.writeInt(0x664c6143)
                    it.writeInt(0x80000022.toInt())
                    it.write(ByteArray(10))
                    it.writeLong((rate.toLong() shl 44) or (1L shl 41) or
                        ((bits - 1).toLong() shl 36) or frames)
                    it.write(ByteArray(16))
                }
                assertEquals(frames.toDouble() / rate, FlacDuration.read(file), 1e-10)
            } finally { file.delete() }
        }
    }

    @Test(expected = IllegalStateException::class)
    fun rejectsNonFlacInsteadOfReportingZeroDuration() {
        val file = File.createTempFile("duration", ".flac")
        try {
            file.writeText("not a FLAC")
            FlacDuration.read(file)
        } finally { file.delete() }
    }
}
