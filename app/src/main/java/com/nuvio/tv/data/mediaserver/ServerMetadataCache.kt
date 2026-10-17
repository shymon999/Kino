package com.nuvio.tv.data.mediaserver

import android.content.Context
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.lang.reflect.Type
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerMetadataCache @Inject constructor(@ApplicationContext context: Context) {
    internal val disk = BoundedMetadataDiskCache(File(context.cacheDir, "server_metadata_v1"), report = { bytes, entries ->
        android.util.Log.i("KinoMetadataCache", "bytes=$bytes entries=$entries limitBytes=67108864")
    })
}

/** Disposable metadata only. The shared budget includes library, detail, person and poster entries. */
internal class BoundedMetadataDiskCache(
    private val directory: File,
    private val maxBytes: Long = 64L * 1024 * 1024,
    private val maxEntries: Int = 512,
    private val now: () -> Long = System::currentTimeMillis,
    private val reserveBytes: Long = 16L * 1024 * 1024,
    private val report: (Long, Int) -> Unit = { _, _ -> }
) {
    data class Entry<T>(val value: T, val storedAt: Long)
    private val gson = Gson()
    private var reportedRead = false

    @Synchronized
    fun <T> get(scope: String, key: String, type: Type, maxAgeMs: Long): Entry<T>? {
        val file = file(scope, key)
        if (!file.isFile) return null
        return try {
            if (file.length() > MAX_ENTRY_BYTES) error("Oversized cache entry")
            DataInputStream(file.inputStream().buffered()).use { input ->
                val storedAt = input.readLong()
                if (now() - storedAt !in 0..maxAgeMs) error("Expired cache entry")
                val bytes = GZIPInputStream(input).use { compressed ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = compressed.read(buffer)
                        if (count < 0) break
                        if (output.size() + count > MAX_JSON_BYTES) error("Oversized cache payload")
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
                val value = gson.fromJson<T>(bytes.toString(Charsets.UTF_8), type) ?: error("Empty cache entry")
                file.setLastModified(now())
                if (!reportedRead) {
                    reportStats()
                    reportedRead = true
                }
                Entry(value, storedAt)
            }
        } catch (_: Exception) {
            file.delete()
            null
        }
    }

    @Synchronized
    fun put(scope: String, key: String, value: Any, storedAt: Long = now(), allowed: () -> Boolean = { true }) {
        var temporary: File? = null
        try {
            if (!allowed()) return
            val json = gson.toJson(value).toByteArray(Charsets.UTF_8)
            if (json.size > MAX_JSON_BYTES) return
            val output = ByteArrayOutputStream()
            DataOutputStream(output).writeLong(storedAt)
            GZIPOutputStream(output).use { it.write(json) }
            val bytes = output.toByteArray()
            if (bytes.size > minOf(MAX_ENTRY_BYTES, maxBytes)) return
            if (!directory.isDirectory && !directory.mkdirs()) return
            val destination = file(scope, key)
            val entries = directory.listFiles().orEmpty().filter { it.isFile && it != destination }
                .sortedWith(compareBy<File> { it.name.contains("-library-") }.thenBy { it.lastModified() }).toMutableList()
            var used = entries.sumOf { it.length() }
            while (entries.isNotEmpty() && (used + bytes.size > maxBytes || entries.size >= maxEntries ||
                    directory.usableSpace < bytes.size + reserveBytes)) {
                val oldest = entries.removeAt(0)
                val size = oldest.length()
                if (!oldest.delete()) return
                used -= size
            }
            if (used + bytes.size > maxBytes || directory.usableSpace < bytes.size + reserveBytes) return
            // Account for the old destination and its replacement while the temporary file exists.
            if (destination.exists() && !destination.delete()) return
            temporary = File(directory, destination.name + ".tmp")
            temporary.outputStream().use { it.write(bytes) }
            if (!temporary.renameTo(destination)) temporary.delete()
            destination.setLastModified(now())
            reportStats()
        } catch (_: Exception) {
            temporary?.delete()
        }
    }

    @Synchronized
    fun remove(scope: String, key: String) { file(scope, key).delete() }

    @Synchronized
    fun clear(scope: String) {
        val prefix = hash(scope) + "-"
        directory.listFiles().orEmpty().filter { it.name.startsWith(prefix) }.forEach { it.delete() }
        reportStats()
    }

    @Synchronized
    fun sizeBytes(): Long = directory.listFiles().orEmpty().filter { it.isFile }.sumOf { it.length() }

    private fun reportStats() {
        val files = directory.listFiles().orEmpty().filter { it.isFile }
        report(files.sumOf { it.length() }, files.size)
    }

    private fun file(scope: String, key: String) = File(directory,
        "${hash(scope)}-${if (key.startsWith("library:")) "library-" else ""}${hash(key)}.gz")
    private fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val MAX_ENTRY_BYTES = 8L * 1024 * 1024
        const val MAX_JSON_BYTES = 32 * 1024 * 1024
    }
}
