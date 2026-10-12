package com.fabula.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class PcmWavTest {
    @Test fun stereoHeaderDescribesExactPcmPayload() {
        val frames = 44117
        val bytes = PcmWav.header(frames * 4L, 44100, 2)
        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(44, bytes.size)
        fun fourcc(offset: Int) = String(bytes, offset, 4, Charsets.US_ASCII)
        assertEquals("RIFF", fourcc(0))
        assertEquals(36 + frames * 4, header.getInt(4))
        assertEquals("WAVE", fourcc(8))
        assertEquals("fmt ", fourcc(12))
        assertEquals(16, header.getInt(16))
        assertEquals(1, header.getShort(20).toInt())
        assertEquals(2, header.getShort(22).toInt())
        assertEquals(44100, header.getInt(24))
        assertEquals(176400, header.getInt(28))
        assertEquals(4, header.getShort(32).toInt())
        assertEquals(16, header.getShort(34).toInt())
        assertEquals("data", fourcc(36))
        assertEquals(frames * 4, header.getInt(40))
    }

    @Test fun unsignedRiffSizeDoesNotOverflowEarly() {
        val data = (PcmWav.MAX_DATA_BYTES / 4) * 4
        val header = ByteBuffer.wrap(PcmWav.header(data, 44100, 2)).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(data + 36, header.getInt(4).toLong() and 0xffffffffL)
        assertEquals(data, header.getInt(40).toLong() and 0xffffffffL)
    }

    @Test fun rejectsTruncatedStereoFrameAndOversizeFile() {
        for (size in listOf(-4L, 2L, PcmWav.MAX_DATA_BYTES + 1)) {
            assertThrows(IllegalArgumentException::class.java) { PcmWav.header(size, 44100, 2) }
        }
    }
}
