package eu.depau.etchdroid.service

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import eu.depau.etchdroid.utils.ktexts.getDisplayName
import eu.depau.etchdroid.utils.ktexts.getFileSize
import org.tukaani.xz.SeekableInputStream
import org.tukaani.xz.SeekableXZInputStream
import org.tukaani.xz.XZInputStream
import java.io.IOException
import java.io.InputStream

internal data class OpenedImage(val stream: InputStream, val size: Long)

internal object XzImageSource {
    fun open(context: Context, uri: Uri): OpenedImage {
        if (!isXz(context, uri)) {
            val source = context.contentResolver.openInputStream(uri)
                ?: throw IOException("Failed to open image file")
            return try {
                OpenedImage(source, uri.getFileSize(context))
            } catch (exception: Exception) {
                source.close()
                throw exception
            }
        }

        val descriptor = try {
            context.contentResolver.openFileDescriptor(uri, "r")
        } catch (_: Exception) {
            null
        }
        if (descriptor != null) {
            val source = DescriptorSeekableInputStream(descriptor)
            if (source.isSeekable()) return open(source)
            source.close()
        }

        return openSequential(context, uri)
    }

    fun open(source: SeekableInputStream): OpenedImage {
        val decoded = try {
            SeekableXZInputStream(source)
        } catch (exception: Exception) {
            source.close()
            throw exception
        }
        return OpenedImage(decoded, decoded.length())
    }

    private fun openSequential(context: Context, uri: Uri): OpenedImage {
        val size = openXzStream(context, uri).use(::countBytes)
        return OpenedImage(openXzStream(context, uri), size)
    }

    private fun openXzStream(context: Context, uri: Uri): InputStream {
        val source = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Failed to open image file")
        return try {
            XZInputStream(source)
        } catch (exception: Exception) {
            source.close()
            throw exception
        }
    }

    internal fun countBytes(source: InputStream): Long {
        val buffer = ByteArray(8 * 1024)
        var totalBytes = 0L
        while (true) {
            val read = source.read(buffer)
            if (read == -1) return totalBytes
            totalBytes += read
        }
    }

    private fun isXz(context: Context, uri: Uri): Boolean {
        val displayName = runCatching { uri.getDisplayName(context) }.getOrNull()
        if (listOfNotNull(displayName, uri.lastPathSegment)
                .any { it.endsWith(".xz", ignoreCase = true) }
        ) return true

        return runCatching { context.contentResolver.getType(uri) }.getOrNull()
            ?.lowercase()
            ?.let { it == "application/x-xz" || it == "application/xz" }
            ?: false
    }

    private class DescriptorSeekableInputStream(descriptor: ParcelFileDescriptor) :
        SeekableInputStream() {
        private val stream = ParcelFileDescriptor.AutoCloseInputStream(descriptor)
        private val channel = stream.channel

        fun isSeekable(): Boolean = try {
            channel.size()
            channel.position()
            channel.position(0)
            true
        } catch (_: Exception) {
            false
        }

        override fun length(): Long = channel.size()

        override fun position(): Long = channel.position()

        override fun seek(pos: Long) {
            channel.position(pos)
        }

        override fun read(): Int = stream.read()

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            stream.read(buffer, offset, length)

        override fun close() = stream.close()
    }
}