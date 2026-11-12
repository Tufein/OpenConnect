package nl.tufein.openconnect.watch

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import nl.tufein.openconnect.core.watch.FileTree
import nl.tufein.openconnect.core.watch.FolderWatchSource
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Reads a folder the user picked with Android's own folder picker (the Storage Access Framework), so no storage
 * permission is needed: the picker's answer is the permission. The picker also lists a watch that is plugged in as a
 * storage device, and apps that keep a copy of the watch's files.
 *
 * Like [UsbWatchLink] this is the part that needs a phone to try; the folder walk and everything after it is covered by
 * tests in the core with an in-memory tree.
 */
class SafFolderLink(private val context: Context) : FolderLink {
    override suspend fun open(treeUri: String): OpenedWatch {
        val uri = try {
            Uri.parse(treeUri)
        } catch (_: Exception) {
            throw WatchLinkException(WatchLinkException.Reason.NOT_FOUND)
        }
        if (!DocumentsContract.isTreeUri(uri)) throw WatchLinkException(WatchLinkException.Reason.NOT_FOUND)
        val tree = SafFileTree(context.contentResolver, uri)
        return OpenedWatch(FolderWatchSource(tree, tree.rootName()), onClose = {})
    }
}

internal class SafFileTree(private val resolver: ContentResolver, private val tree: Uri) : FileTree {
    /** A handle is the position of a document id in this list, because the core wants plain numbers. */
    private val ids = ArrayList<String>()

    fun rootName(): String {
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, rootId)
        return try {
            resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (_: Exception) {
            null
        } ?: "folder"
    }

    override fun children(parent: Long?): List<FileTree.Entry> {
        val parentId = if (parent == null) DocumentsContract.getTreeDocumentId(tree) else ids[parent.toInt()]
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
        )
        val entries = ArrayList<FileTree.Entry>()
        try {
            resolver.query(uri, columns, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    ids.add(id)
                    entries.add(
                        FileTree.Entry(
                            name = c.getString(1).orEmpty(),
                            isDirectory = c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR,
                            size = if (c.isNull(3)) 0 else c.getLong(3),
                            handle = (ids.size - 1).toLong(),
                        ),
                    )
                }
            }
        } catch (e: SecurityException) {
            throw IOException("No access to the folder: ${e.message}")
        } catch (e: IllegalArgumentException) {
            throw IOException("The folder cannot be listed: ${e.message}")
        }
        return entries
    }

    override fun read(handle: Long, size: Long): ByteArray {
        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, ids[handle.toInt()])
        val input = try {
            resolver.openInputStream(uri)
        } catch (e: SecurityException) {
            throw IOException("No access to the file: ${e.message}")
        } ?: throw IOException("The file cannot be opened")
        input.use {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = it.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
                if (out.size() > MAX_FILE_BYTES) throw IOException("The file is larger than ${MAX_FILE_BYTES / (1024 * 1024)} MB")
            }
            return out.toByteArray()
        }
    }

    private companion object {
        const val MAX_FILE_BYTES = 25 * 1024 * 1024
    }
}
