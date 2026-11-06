package io.github.tomervancohen.rambleflow

import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.FileDescriptor
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest

/** MP4 needs its finalized sample table. Other audio needs a stable whole-file fingerprint. */
object FolderAudioEvidence {
    fun inspect(fd: FileDescriptor, channel: FileChannel): FinalizedMp4.Evidence? = try {
        val size = channel.size()
        require(size in 1..Policy.MAX_SOURCE)
        val finalized = FinalizedMp4.inspect(channel)
        if (finalized != null) finalized else {
            val header = ByteBuffer.allocate(12)
            channel.position(0); channel.read(header)
            // Never weaken completion checks for a paused/unfinalized MP4 recording.
            require(header.position() < 8 || String(header.array(), 4, 4, Charsets.US_ASCII) != "ftyp")
            val extractor = MediaExtractor()
            val duration = try {
                extractor.setDataSource(fd)
                val formats = (0 until extractor.trackCount).map { extractor.getTrackFormat(it) }
                require(formats.size == 1)
                val format = formats.single()
                require(format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true)
                require(format.containsKey(MediaFormat.KEY_DURATION))
                (format.getLong(MediaFormat.KEY_DURATION) / 1000).also {
                    require(it in 1..Policy.MAX_DURATION_MS)
                }
            } finally { extractor.release() }
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteBuffer.allocate(65536)
            channel.position(0)
            var total = 0L
            while (true) {
                buffer.clear()
                val count = channel.read(buffer)
                if (count < 0) break
                total += count
                require(total <= size)
                buffer.flip(); digest.update(buffer)
            }
            require(total == size && channel.size() == size)
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            FinalizedMp4.Evidence(size, duration, null, "audio-sha256:$hash")
        }
    } catch (_: Exception) { null }
}
