package com.example.farmapp.supplier

import android.content.Context
import java.security.MessageDigest

enum class SupplyOperation { CREATE, UPDATE, UPLOAD, GALLERY }
data class PendingSupplyOperation(val kind: SupplyOperation, val listingId: Long? = null)
interface SupplierOperationStore {
    fun read(accountKey: String): PendingSupplyOperation?
    fun write(accountKey: String, pending: PendingSupplyOperation?)
}
/** Durable uncertainty marker only: no credentials, form data, customer details, URLs or image bytes. */
class SupplierPreferences(context: Context) : SupplierOperationStore {
    private val preferences = context.applicationContext.getSharedPreferences("supplier_operation_markers", Context.MODE_PRIVATE)
    private fun key(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
    override fun read(accountKey: String): PendingSupplyOperation? {
        val value = preferences.getString(key(accountKey), null) ?: return null
        val parts = value.split(':')
        val kind = SupplyOperation.entries.firstOrNull { it.name == parts.firstOrNull() }
            ?: throw SupplierFailure("A saved submission marker could not be read. Contact support before creating another listing.")
        return PendingSupplyOperation(kind, parts.getOrNull(1)?.toLongOrNull())
    }
    override fun write(accountKey: String, pending: PendingSupplyOperation?) {
        val edit = preferences.edit()
        if (pending == null) edit.remove(key(accountKey)) else edit.putString(key(accountKey), "${pending.kind.name}:${pending.listingId.orEmptyId()}")
        if (!edit.commit()) throw SupplierFailure("Submission safety could not be saved. No new submission should be started.")
    }
    private fun Long?.orEmptyId() = this?.toString().orEmpty()
}
