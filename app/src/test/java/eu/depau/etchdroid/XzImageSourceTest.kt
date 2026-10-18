package eu.depau.etchdroid

import eu.depau.etchdroid.service.XzImageSource
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.SeekableInputStream
import org.tukaani.xz.XZOutputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Random

class XzImageSourceTest {
    @Test
    fun decodesXzStreamAndCountsDecodedSize() {
        val image = ByteArray(64 * 1024).also { Random(42).nextBytes(it) }
        val compressed = compressXz(image)

        val decoded = XzImageSource.open(ByteArraySeekableInputStream(compressed))
        assertEquals(image.size.toLong(), decoded.size)
        decoded.stream.use { assertArrayEquals(image, it.readBytes()) }
    }

    @Test
    fun rejectsInvalidXzStream() {
        assertThrows<IOException> {
            XzImageSource.open(ByteArraySeekableInputStream(byteArrayOf(1, 2, 3)))
        }
    }

    private fun compressXz(image: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        XZOutputStream(output, LZMA2Options()).use { it.write(image) }
        return output.toByteArray()
    }
}

internal class ByteArraySeekableInputStream(private val bytes: ByteArray) : SeekableInputStream() {
    private var position = 0

    override fun length(): Long = bytes.size.toLong()

    override fun position(): Long = position.toLong()

    override fun seek(pos: Long) {
        require(pos >= 0)
        position = pos.coerceAtMost(bytes.size.toLong()).toInt()
    }

    override fun read(): Int =
        if (position >= bytes.size) -1 else bytes[position++].toInt() and 0xff

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (position >= bytes.size) return -1
        val read = minOf(length, bytes.size - position)
        bytes.copyInto(buffer, offset, position, position + read)
        position += read
        return read
    }
}