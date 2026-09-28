package com.example.farmapp.network

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.example.farmapp.data.model.AuthResponse
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** TokenManager's disk adapter, not a second session owner. Invoke on an IO thread. */
class EncryptedSessionPersistence(
    context: Context,
    private val alias: String = "kilivana.session.aes.v1",
    fileName: String = "kilivana-session.enc"
) : SessionPersistence {
    private val file = AtomicFile(File(context.applicationContext.noBackupFilesDir, fileName))
    private val gson = Gson()

    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun key(create: Boolean): SecretKey {
        (keyStore().getKey(alias, null) as? SecretKey)?.let { return it }
        if (!create) throw IOException("Session encryption key unavailable")
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build())
        }.generateKey()
    }

    override fun read(): AuthResponse? {
        val bytes = try { file.openRead().use { it.readBytes() } } catch (_: FileNotFoundException) { return null }
        return try {
            if (bytes.size < 29 || bytes[0] != 1.toByte()) throw IOException("Invalid session file")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(false), GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
            cipher.updateAAD("kilivana-session-v1".toByteArray(Charsets.UTF_8))
            val plaintext = cipher.doFinal(bytes.copyOfRange(13, bytes.size))
            try {
                AuthValidation.validate(gson.fromJson(String(plaintext, Charsets.UTF_8), AuthResponse::class.java))
            } finally { plaintext.fill(0) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Corrupt files or invalidated Keystore keys require sign-in, never plaintext fallback.
            clear()
            null
        }
    }

    override fun write(auth: AuthResponse) {
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key(true))
            cipher.updateAAD("kilivana-session-v1".toByteArray(Charsets.UTF_8))
            val plaintext = gson.toJson(auth).toByteArray(Charsets.UTF_8)
            val ciphertext = try { cipher.doFinal(plaintext) } finally { plaintext.fill(0) }
            check(cipher.iv.size == 12)
            val output = file.startWrite()
            try {
                output.write(byteArrayOf(1) + cipher.iv + ciphertext)
                file.finishWrite(output)
            } catch (e: Exception) {
                file.failWrite(output)
                throw e
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            throw IOException("Unable to securely save the session")
        }
    }

    override fun clear() {
        // Invalidate the encryption key too, so a leftover file cannot restore a logged-out session.
        try {
            keyStore().deleteEntry(alias)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            throw IOException("Unable to clear the session encryption key")
        } finally {
            file.delete()
        }
    }
}
