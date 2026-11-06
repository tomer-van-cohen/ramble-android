package io.github.tomervancohen.rambleflow

import android.media.*
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToLong
import kotlinx.coroutines.ensureActive

/** Independent timestamp-aligned windows make decoding resumable without replaying a long prefix. */
class AudioPlanner {
    data class Part(val file: File, val start: Long, val samples: Int)
    private class Yield : RuntimeException()
    private data class Window(val part: Part?, val ended: Boolean)

    suspend fun plan(input: File, directory: File): List<Part> {
        val parts = mutableListOf<Part>()
        check(resume(input, directory, 0, { true }) { _, part -> parts += part })
        return parts
    }

    /** Callback commits each completed window before starting the next one. False means yield, not error. */
    suspend fun resume(input: File, directory: File, ordinal: Int,
        shouldContinue: () -> Boolean, onPart: suspend (Int, Part) -> Unit): Boolean {
        var next = ordinal
        try {
            while (true) {
                coroutineContext.ensureActive()
                if (!shouldContinue()) return false
                val result = decode(input, directory, next, shouldContinue)
                result.part?.let { onPart(next, it); next++ }
                if (result.ended) return true
                check(result.part != null) { "unsupported_audio" }
            }
        } catch (_: Yield) { return false }
    }

    suspend fun restore(input: File, directory: File, ordinal: Int, shouldContinue: () -> Boolean): Part? =
        try { decode(input,directory,ordinal,shouldContinue).part } catch (_: Yield) { null }

    private suspend fun decode(input: File, directory: File, ordinal: Int,
        shouldContinue: () -> Boolean): Window {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var wav: Wav? = null
        val start = ordinal.toLong() * Policy.CHUNK_SAMPLES
        val end = start + Policy.CHUNK_SAMPLES
        var emitted = 0
        var sum = 0L
        var count = 0
        try {
            extractor.setDataSource(input.path)
            val tracks = (0 until extractor.trackCount).filter {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            }
            require(tracks.size == 1) { if (tracks.isEmpty()) "unsupported_audio" else "multiple_tracks" }
            val format = extractor.getTrackFormat(tracks.single())
            val duration = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION)/1000 else 0L
            require(duration in 1..Policy.MAX_DURATION_MS) { "unsupported_duration" }
            require(Policy.canStageAudio(input.length(),duration*32+44)) { "audio_budget" }
            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var encoding = AudioFormat.ENCODING_PCM_16BIT
            extractor.selectTrack(tracks.single())
            val originUs=extractor.sampleTime.coerceAtLeast(0)
            // Preroll warms the decoder and averaging filter. Output is trimmed by absolute PCM timestamps.
            if (start > 0) extractor.seekTo(originUs + (start * 1_000_000 / 16000 - 1_000_000).coerceAtLeast(0), MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(format, null, null, 0); codec.start()
            var sentEnd = false
            var finished = false
            var reachedBoundary = false
            val info = MediaCodec.BufferInfo()
            directory.mkdirs()
            while (!finished && !reachedBoundary) {
                coroutineContext.ensureActive()
                if (!shouldContinue()) throw Yield()
                if (!sentEnd) {
                    val index = codec.dequeueInputBuffer(10_000)
                    if (index >= 0) {
                        val n = extractor.readSampleData(codec.getInputBuffer(index)!!, 0)
                        if (n < 0) { codec.queueInputBuffer(index,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM); sentEnd = true }
                        else { codec.queueInputBuffer(index,0,n,extractor.sampleTime,0); extractor.advance() }
                    }
                }
                val index = codec.dequeueOutputBuffer(info, 10_000)
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val f = codec.outputFormat
                    rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE); channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    encoding = if (f.containsKey(MediaFormat.KEY_PCM_ENCODING)) f.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                }
                require(rate in 8000..192000 && channels in 1..8) { "unsupported_audio" }
                if (index >= 0) {
                    try {
                        val b = codec.getOutputBuffer(index)!!.duplicate().order(ByteOrder.LITTLE_ENDIAN)
                        b.position(info.offset); b.limit(info.offset+info.size)
                        require(encoding in setOf(AudioFormat.ENCODING_PCM_16BIT,AudioFormat.ENCODING_PCM_FLOAT)) { "unsupported_pcm" }
                        val bytes = if (encoding == AudioFormat.ENCODING_PCM_FLOAT) 4 else 2
                        var frame = ((info.presentationTimeUs-originUs).toDouble()*rate/1_000_000).roundToLong()
                        while (b.remaining() >= bytes*channels && !reachedBoundary) {
                            var value = 0.0
                            repeat(channels) { value += if (bytes == 4) b.float.toDouble().coerceIn(-1.0,1.0)*32767 else b.short.toDouble() }
                            sum += (value/channels).toInt().coerceIn(-32768,32767); count++
                            val before = Math.floorDiv(frame*16000,rate.toLong())
                            val after = Math.floorDiv((frame+1)*16000,rate.toLong())
                            if (after > before) {
                                val sample = (sum/count).toShort()
                                for (output in before until after) {
                                    if (output >= end) { reachedBoundary = true; break }
                                    if (output < start) continue
                                    require(output == start+emitted) { "audio_timestamp_gap" }
                                    if (wav == null) {
                                        require(ordinal < (Policy.MAX_DURATION_MS/300000).toInt()) { "unsupported_duration" }
                                        wav = Wav(File(directory,"$ordinal.wav.partial"))
                                    }
                                    wav!!.sample(sample); emitted++
                                }
                                sum = 0; count = 0
                            }
                            frame++
                        }
                        finished = !reachedBoundary && info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    } finally { codec.releaseOutputBuffer(index,false) }
                }
            }
            require(finished || emitted == Policy.CHUNK_SAMPLES) { "audio_timestamp_gap" }
            val part = wav?.let { Part(it.finish(),start,it.samples) }
            wav = null
            require(part == null || part.file.length() < Policy.MAX_UPLOAD) { "unsupported_audio" }
            return Window(part,finished)
        } finally {
            wav?.abort()
            codec?.let { runCatching { it.stop() }; it.release() }
            extractor.release()
        }
    }

    private class Wav(val partial: File) {
        private val out: RandomAccessFile
        var samples = 0
        private val buffer = ByteBuffer.allocate(64 * 1024).order(ByteOrder.LITTLE_ENDIAN)

        init {
            val used =
                partial.parentFile!!
                    .parentFile!!
                    .walkTopDown()
                    .filter { it.isFile }
                    .sumOf { it.length() }
            require(
                Policy.canStageAudio(used, Policy.CHUNK_SAMPLES * 2L + 44) &&
                    android.os.StatFs(partial.parentFile!!.path).availableBytes >
                        Policy.CHUNK_SAMPLES * 2L + 256L * 1024 * 1024
            ) {
                "storage"
            }
            out = RandomAccessFile(partial, "rw")
            out.setLength(0)
            out.write(ByteArray(44))
        }

        fun sample(value: Short) {
            if (buffer.remaining() < 2) flush()
            buffer.putShort(value)
            samples++
        }

        private fun flush() {
            out.write(buffer.array(), 0, buffer.position())
            buffer.clear()
        }

        fun finish(): File {
            flush()
            val bytes = samples * 2
            val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            h.put("RIFF".toByteArray())
            h.putInt(36 + bytes)
            h.put("WAVEfmt ".toByteArray())
            h.putInt(16)
            h.putShort(1)
            h.putShort(1)
            h.putInt(16000)
            h.putInt(32000)
            h.putShort(2)
            h.putShort(16)
            h.put("data".toByteArray())
            h.putInt(bytes)
            out.seek(0)
            out.write(h.array())
            out.fd.sync()
            out.close()
            val target = File(partial.parentFile, partial.name.removeSuffix(".partial"))
            check(partial.renameTo(target))
            return target
        }

        fun abort() {
            runCatching { out.close() }
            partial.delete()
        }
    }
}
