package com.example.farmapp.buyer

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

/** DUMP-permission protected debug opt-in. Reads only already-sanitized volatile text; never initiates a request. */
class DebugCartStructureProvider : ContentProvider() {
    override fun onCreate() = true
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        require(uri.path == "/report" && uri.query == null)
        return MatrixCursor(arrayOf("sanitized_structure")).apply { addRow(arrayOf(DebugCartReportCache.read())) }
    }
    override fun getType(uri: Uri) = "text/plain"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException("Read-only")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = throw UnsupportedOperationException("Read-only")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = throw UnsupportedOperationException("Read-only")
}
