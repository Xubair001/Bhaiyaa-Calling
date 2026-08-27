package com.codeaza.bhaiyaaa.ai.model

import android.content.Context
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Where downloaded models live on disk, and how archives are unpacked.
 *
 * Everything goes under the app's private files dir, so models are removed
 * with the app and are not readable by other apps.
 */
class ModelStorage(private val context: Context) {

    private val root: File get() = File(context.filesDir, "models").apply { mkdirs() }

    fun dirFor(modelId: String): File = File(root, modelId)

    fun isInstalled(modelId: String): Boolean {
        val dir = dirFor(modelId)
        return dir.isDirectory && (dir.listFiles()?.isNotEmpty() == true)
    }

    fun sizeOnDisk(modelId: String): Long = dirFor(modelId).walkBottomUp()
        .filter { it.isFile }
        .sumOf { it.length() }

    fun delete(modelId: String): Boolean = dirFor(modelId).deleteRecursively()

    fun totalSizeOnDisk(): Long = root.walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    /**
     * Unpacks a model zip into [dirFor].
     *
     * Zip entries are validated against path traversal ("../" entries that would
     * otherwise let a tampered archive write outside the model directory) before
     * anything is written.
     */
    fun unzipInto(modelId: String, input: InputStream, onProgress: (Long) -> Unit = {}) {
        val target = dirFor(modelId)
        target.deleteRecursively()
        target.mkdirs()
        val canonicalTarget = target.canonicalPath
        var written = 0L

        ZipInputStream(input.buffered()).use { zip ->
            var entry = zip.nextEntry
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (entry != null) {
                val outFile = File(target, entry.name)
                if (!outFile.canonicalPath.startsWith(canonicalTarget + File.separator) &&
                    outFile.canonicalPath != canonicalTarget
                ) {
                    throw SecurityException("Zip entry escapes model directory: ${entry.name}")
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().buffered().use { out ->
                        while (true) {
                            val read = zip.read(buffer)
                            if (read <= 0) break
                            out.write(buffer, 0, read)
                            written += read
                            onProgress(written)
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        // Vosk archives contain a single top-level folder. Flatten it so the
        // recogniser always finds the model files at a predictable path.
        val children = target.listFiles().orEmpty()
        if (children.size == 1 && children[0].isDirectory) {
            val inner = children[0]
            inner.listFiles()?.forEach { child ->
                child.renameTo(File(target, child.name))
            }
            inner.deleteRecursively()
        }
    }
}
