package com.example.farmapp.ui.screens.auth

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.Manifest
import android.os.Bundle
import com.example.farmapp.BuildConfig
import com.example.farmapp.network.canonical.AuthAttemptTrace

/** Reads only the already sanitized cache; never initiates HTTP or reads the session. */
class AuthDiagnosticProvider : ContentProvider() {
    override fun onCreate() = true
    /** Explicit developer opt-in, no customer-screen entry point and no automatic recording. */
    override fun call(method:String,arg:String?,extras:Bundle?):Bundle {
        require(BuildConfig.DEBUG && BuildConfig.ENABLE_AUTH_DIAGNOSTICS)
        requireNotNull(context).enforceCallingOrSelfPermission(Manifest.permission.DUMP,"Developer diagnostics permission required")
        require(method=="arm10Minutes" && arg==null && (extras==null || extras.isEmpty))
        AuthAttemptTrace.arm()
        return Bundle().apply{putBoolean("armed",true)}
    }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        require(uri.path in setOf("/report","/buyer-ui") && uri.query == null)
        return MatrixCursor(arrayOf("sanitized_auth")).apply { addRow(arrayOf(if(uri.path=="/buyer-ui") com.example.farmapp.buyers.BrianUiPresence.report() else AuthAttemptTrace.readReport())) }
    }
    override fun getType(uri: Uri) = "text/plain"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException("Read-only")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = throw UnsupportedOperationException("Read-only")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = throw UnsupportedOperationException("Read-only")
}
