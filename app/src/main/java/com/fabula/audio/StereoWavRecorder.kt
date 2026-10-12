package com.fabula.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/** Records actual two-channel PCM; never substitutes an AAC file with a WAV extension. */
class StereoWavRecorder {
    private var audio: AudioRecord? = null
    private var worker: Thread? = null
    @Volatile private var running = false
    @Volatile private var failure: Exception? = null
    @Volatile var maxAmplitude: Int = 0
        private set

    fun start(file: File) {
        check(audio == null) { "Запись уже запущена" }
        val minimum = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT
        )
        check(minimum > 0) { "Микрофон не поддерживает стереозапись WAV 44,1 кГц" }
        val bufferBytes = maxOf(minimum * 2, 16384).let { (it + 3) / 4 * 4 }
        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_STEREO,
            AudioFormat.ENCODING_PCM_16BIT, bufferBytes
        )
        audio = record
        try {
            check(record.state == AudioRecord.STATE_INITIALIZED && record.channelCount == 2) {
                "Не удалось включить стереомикрофон. Подключите устройство с поддержкой двух каналов."
            }
            val output = RandomAccessFile(file, "rw")
            try {
                output.setLength(0)
                output.write(PcmWav.header(0, SAMPLE_RATE, 2))
                record.startRecording()
                check(record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    "Не удалось начать запись микрофона"
                }
            } catch (e: Exception) {
                output.close()
                throw e
            }
            failure = null
            maxAmplitude = 0
            running = true
            worker = Thread({
                var bytesWritten = 0L
                try {
                    val samples = ShortArray(bufferBytes / 2)
                    val bytes = ByteArray(bufferBytes)
                    val pcm = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                    while (running) {
                        val count = record.read(samples, 0, samples.size, AudioRecord.READ_BLOCKING)
                        if (count < 0) {
                            if (!running) break
                            error("Ошибка чтения микрофона: $count")
                        }
                        if (count == 0) continue
                        check(count % 2 == 0) { "Микрофон вернул неполный стереокадр" }
                        check(bytesWritten + count * 2L <= PcmWav.MAX_DATA_BYTES) {
                            "Достигнут предел размера WAV (4 ГБ)"
                        }
                        pcm.clear()
                        var peak = 0
                        for (i in 0 until count) {
                            pcm.putShort(samples[i])
                            peak = maxOf(peak, abs(samples[i].toInt()))
                        }
                        output.write(bytes, 0, count * 2)
                        bytesWritten += count * 2L
                        maxAmplitude = peak.coerceAtMost(32767)
                    }
                } catch (e: Exception) {
                    failure = e
                } finally {
                    running = false
                    try {
                        output.seek(0)
                        output.write(PcmWav.header(bytesWritten, SAMPLE_RATE, 2))
                    } catch (e: Exception) {
                        failure = e
                    } finally {
                        try { output.close() } catch (e: Exception) { failure = e }
                    }
                }
            }, "Fabula-WAV-recording").also { it.start() }
        } catch (e: Exception) {
            release()
            throw e
        }
    }

    fun stop() {
        running = false
        try { audio?.stop() } catch (_: IllegalStateException) {}
        worker?.join()
        worker = null
        audio?.release()
        audio = null
        failure?.let { throw it }
    }

    fun release() {
        try { stop() } catch (_: Exception) {}
    }

    companion object {
        const val SAMPLE_RATE = 44100
    }
}
