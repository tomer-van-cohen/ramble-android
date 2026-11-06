package io.github.tomervancohen.rambleflow

import android.media.*
import android.os.SystemClock
import java.io.File

/** Real Android AAC/MPEG4 writer; no microphone, captured voice or external provider. */
object NativeM4aFixture {
    fun write(file: File, samples: Int = 16000, beforeClose: (File) -> Unit = {}) {
        val codec = MediaCodec.createEncoderByType("audio/mp4a-latm")
        val format =
            MediaFormat.createAudioFormat("audio/mp4a-latm", 16000, 1).apply {
                setInteger(
                    MediaFormat.KEY_AAC_PROFILE,
                    MediaCodecInfo.CodecProfileLevel.AACObjectLC,
                )
                setInteger(MediaFormat.KEY_BIT_RATE, 64000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 4096)
            }
        val muxer = MediaMuxer(file.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var started = false
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            val info = MediaCodec.BufferInfo()
            var fed = 0
            var ended = false
            var inputEnd = false
            var track = -1
            val deadline = SystemClock.elapsedRealtime() + maxOf(30000L,samples.toLong()/16000*200)
            while (!ended) {
                check(SystemClock.elapsedRealtime() < deadline)
                if (!inputEnd) {
                    val i = codec.dequeueInputBuffer(10000)
                    if (i >= 0) {
                        val b = codec.getInputBuffer(i)!!
                        b.clear()
                        val n = minOf(1024, samples - fed)
                        if (n == 0) {
                            codec.queueInputBuffer(
                                i,
                                0,
                                0,
                                fed * 1000000L / 16000,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputEnd = true
                        } else {
                            repeat(n) { b.putShort(0) }
                            codec.queueInputBuffer(i, 0, n * 2, fed * 1000000L / 16000, 0)
                            fed += n
                        }
                    }
                }
                val i = codec.dequeueOutputBuffer(info, 10000)
                if (i == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    track = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    started = true
                }
                if (i >= 0) {
                    if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0)
                        muxer.writeSampleData(track, codec.getOutputBuffer(i)!!, info)
                    ended = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    codec.releaseOutputBuffer(i, false)
                }
            }
            beforeClose(file)
        } finally {
            if (started) muxer.stop()
            muxer.release()
            codec.stop()
            codec.release()
        }
    }
}
