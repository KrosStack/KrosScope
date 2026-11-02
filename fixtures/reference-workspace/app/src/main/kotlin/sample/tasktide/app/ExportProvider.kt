package sample.tasktide.app

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import sample.tasktide.core.legacy.ExportRegistry
import sample.tasktide.nativebridge.Checksum

class ExportProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        val exporter = uri.lastPathSegment?.let { ExportRegistry.find(it) } ?: return null
        val store = (context?.applicationContext as TaskTideApp).taskStore
        val body = exporter.export(runBlocking { store.observeAll().first() })
        return MatrixCursor(arrayOf(COLUMN_BODY, COLUMN_CHECKSUM)).apply {
            addRow(arrayOf<Any>(body, Checksum.crc32(body.encodeToByteArray())))
        }
    }

    override fun getType(uri: Uri): String = "text/plain"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        const val COLUMN_BODY = "body"
        const val COLUMN_CHECKSUM = "checksum"
    }
}
