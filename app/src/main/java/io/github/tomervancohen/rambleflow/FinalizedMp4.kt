package io.github.tomervancohen.rambleflow

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.SeekableByteChannel
import java.security.MessageDigest

/** Recognizes a closed, non-fragmented AAC recording; stability alone is never evidence. */
object FinalizedMp4 {
    data class Evidence(
        val bytes: Long,
        val durationMillis: Long,
        val createdMillis: Long?,
        val proof: String,
    )

    private data class Box(val type: String, val data: ByteBuffer)

    private data class Range(val first: Long, val end: Long)

    private const val MAX_MOOV = 32 * 1024 * 1024
    private const val MAX_SAMPLES = 2_000_000

    fun inspect(channel: SeekableByteChannel): Evidence? =
        try {
            parse(channel)
        } catch (_: Exception) {
            null
        }

    private fun read(c: SeekableByteChannel, position: Long, n: Int): ByteBuffer {
        require(n >= 0)
        val b = ByteBuffer.allocate(n).order(ByteOrder.BIG_ENDIAN)
        c.position(position)
        while (b.hasRemaining()) require(c.read(b) > 0)
        b.flip()
        return b
    }

    private fun uint(b: ByteBuffer, p: Int): Long {
        require(p >= 0 && p <= b.limit() - 4)
        return b.getInt(p).toLong() and 0xffffffffL
    }

    private fun ulong(b: ByteBuffer, p: Int): Long {
        require(p >= 0 && p <= b.limit() - 8)
        return b.getLong(p).also { require(it >= 0) }
    }

    private fun type(b: ByteBuffer, p: Int): String {
        require(p >= 0 && p <= b.limit() - 4)
        return ByteArray(4) { b.get(p + it) }.toString(Charsets.ISO_8859_1)
    }

    private fun children(b: ByteBuffer): List<Box> {
        val result = mutableListOf<Box>()
        var p = 0
        while (p < b.limit()) {
            require(result.size < 10_000 && b.limit() - p >= 8)
            val size = uint(b, p)
            val head = if (size == 1L) 16 else 8
            val n = if (head == 16) ulong(b, p + 8) else size
            require(n >= head && n <= b.limit() - p)
            val data =
                b.duplicate()
                    .apply {
                        position(p + head)
                        limit(p + n.toInt())
                    }
                    .slice()
                    .order(ByteOrder.BIG_ENDIAN)
            result += Box(type(b, p + 4), data)
            p += n.toInt()
        }
        return result
    }

    private fun List<Box>.one(name: String) = filter { it.type == name }.single().data

    private data class Clock(val created: Long?, val scale: Long, val ticks: Long) {
        val millis: Long
            get() = Math.multiplyExact(ticks, 1000) / scale
    }

    private fun clock(b: ByteBuffer): Clock {
        require(b.limit() >= 20)
        val version = b.get(0).toInt() and 255
        require(version == 0 || version == 1)
        val created = if (version == 0) uint(b, 4) else ulong(b, 4)
        val scale = uint(b, if (version == 0) 12 else 20)
        val ticks = if (version == 0) uint(b, 16) else ulong(b, 24)
        require(scale > 0 && ticks > 0 && ticks <= scale * (Policy.MAX_DURATION_MS / 1000 + 10))
        val unix = created - 2_082_844_800L
        val date = unix.takeIf { it >= 946684800L && it <= 4102444800L }?.times(1000)
        return Clock(date, scale, ticks)
    }

    private fun parse(c: SeekableByteChannel): Evidence {
        val size = c.size()
        require(size in 32..Policy.MAX_SOURCE)
        var p = 0L
        var count = 0
        var ftyp = false
        var moov: ByteBuffer? = null
        val media = mutableListOf<Range>()
        while (p < size) {
            require(++count <= 1024 && size - p >= 8)
            val h = read(c, p, 8)
            val short = uint(h, 0)
            val kind = type(h, 4)
            val head = if (short == 1L) 16 else 8
            val n = if (head == 16) ulong(read(c, p + 8, 8), 0) else short
            // Size zero/unknown extended mdat length belongs to an open writer, not a closed file.
            require(n >= head && n <= size - p && kind !in setOf("moof", "mfra", "styp"))
            when (kind) {
                "ftyp" -> {
                    require(!ftyp && n - head in 8..1024)
                    val b = read(c, p + head, (n - head).toInt())
                    ftyp =
                        (0 until b.limit() step 4).any {
                            it != 4 &&
                                type(b, it) in setOf("M4A ", "mp42", "isom", "iso2", "3gp4", "3gp6")
                        }
                }
                "mdat" -> {
                    require(n > head)
                    media += Range(p + head, p + n)
                }
                "moov" -> {
                    require(moov == null && n - head <= MAX_MOOV)
                    moov = read(c, p + head, (n - head).toInt())
                }
            }
            p += n
        }
        require(ftyp && media.isNotEmpty() && moov != null && c.size() == size)
        val movie = children(moov!!)
        require(movie.none { it.type == "mvex" })
        val movieClock = clock(movie.one("mvhd"))
        val tracks = movie.filter { it.type == "trak" }
        require(tracks.size == 1)
        val mdia = children(children(tracks.single().data).one("mdia"))
        require(type(mdia.one("hdlr"), 8) == "soun")
        val audioClock = clock(mdia.one("mdhd"))
        require(kotlin.math.abs(movieClock.millis - audioClock.millis) <= 3000)
        val table = children(children(mdia.one("minf")).one("stbl"))
        val stsd = table.one("stsd")
        require(uint(stsd, 4) == 1L && type(stsd, 12) == "mp4a")
        val sizes = table.one("stsz")
        val fixed = uint(sizes, 4)
        val samples = uint(sizes, 8).toInt()
        require(samples in 1..MAX_SAMPLES)
        require(fixed > 0 || sizes.limit().toLong() == 12L + samples * 4L)
        fun sample(i: Int) = if (fixed > 0) fixed else uint(sizes, 12 + i * 4)
        val timing = table.one("stts")
        val runs = uint(timing, 4).toInt()
        require(runs in 1..MAX_SAMPLES && timing.limit().toLong() == 8L + runs * 8L)
        var timedSamples = 0L
        var ticks = 0L
        repeat(runs) {
            val n = uint(timing, 8 + it * 8)
            val delta = uint(timing, 12 + it * 8)
            require(n > 0 && delta > 0)
            timedSamples = Math.addExact(timedSamples, n)
            ticks = Math.addExact(ticks, Math.multiplyExact(n, delta))
        }
        require(
            timedSamples == samples.toLong() &&
                kotlin.math.abs(ticks - audioClock.ticks) <= audioClock.scale * 3
        )
        val offsetBox = table.single { it.type == "stco" || it.type == "co64" }
        val offsets = offsetBox.data
        val width = if (offsetBox.type == "co64") 8 else 4
        val chunks = uint(offsets, 4).toInt()
        require(
            chunks in 1..MAX_SAMPLES && offsets.limit().toLong() == 8L + chunks * width.toLong()
        )
        val mapping = table.one("stsc")
        val maps = uint(mapping, 4).toInt()
        require(maps in 1..chunks && mapping.limit().toLong() == 8L + maps * 12L)
        val first = LongArray(maps) { uint(mapping, 8 + it * 12) }
        val per = LongArray(maps) { uint(mapping, 12 + it * 12) }
        require(
            first[0] == 1L &&
                first.asList().zipWithNext().all { it.first < it.second } &&
                first.all { it <= chunks } &&
                per.all { it in 1..MAX_SAMPLES }
        )
        repeat(maps) { require(uint(mapping, 16 + it * 12) == 1L) }
        var map = 0
        var sampleIndex = 0
        var lastEnd = 0L
        repeat(chunks) { chunk ->
            while (map + 1 < maps && first[map + 1] <= chunk + 1) map++
            val offset =
                if (width == 8) ulong(offsets, 8 + chunk * 8) else uint(offsets, 8 + chunk * 4)
            require(offset >= lastEnd)
            val range =
                media.singleOrNull { offset >= it.first && offset < it.end }
                    ?: error("sample_offset")
            var end = offset
            repeat(per[map].toInt()) {
                require(sampleIndex < samples)
                val bytes = sample(sampleIndex++)
                require(bytes > 0 && bytes <= range.end - end)
                end += bytes
            }
            lastEnd = end
        }
        require(sampleIndex == samples && c.size() == size)
        val proof =
            MessageDigest.getInstance("SHA-256")
                .apply {
                    update(ByteBuffer.allocate(8).putLong(size).array())
                    update(moov!!.duplicate())
                }
                .digest()
                .joinToString("") { "%02x".format(it) }
        return Evidence(size, audioClock.millis, movieClock.created, proof)
    }
}
