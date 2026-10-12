package com.fabula.audio

import java.io.DataInputStream
import java.io.File

/** FFmpeg writes STREAMINFO first and finalizes total samples on closing a local FLAC. */
object FlacDuration {
    fun read(file: File): Double = DataInputStream(file.inputStream().buffered()).use { input ->
        check(input.readInt() == 0x664c6143) { "Некорректный FLAC: ${file.name}" }
        val header = input.readInt()
        check((header ushr 24 and 0x7f) == 0 && (header and 0xffffff) == 34) {
            "Отсутствует STREAMINFO: ${file.name}"
        }
        input.readFully(ByteArray(10))
        val info = input.readLong()
        val rate = info ushr 44
        val frames = info and 0xfffffffffL
        check(rate > 0 && frames > 0) { "Неизвестная длительность FLAC: ${file.name}" }
        frames.toDouble() / rate
    }
}
