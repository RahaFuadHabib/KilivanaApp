package com.example.farmapp.network

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.farmapp.data.model.AuthResponse
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import java.time.Instant
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class EncryptedSessionPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val suffix = UUID.randomUUID().toString()
    private val alias = "kilivana.auth.test.$suffix"
    private val fileName = "auth-test-$suffix.enc"
    private lateinit var persistence: EncryptedSessionPersistence
    private fun auth() = AuthResponse(
        "eb1f7b4e-8392-4bf4-8cdc-f96083411000", "test@example.test", "Test Person", "BUYER",
        "sensitive-access-test-value", "sensitive-refresh-test-value",
        Instant.now().plusSeconds(86400).toString()
    )

    @Before fun setup() { persistence = EncryptedSessionPersistence(context, alias, fileName) }
    @After fun cleanup() { persistence.clear() }

    @Test fun recreationRestoresAllFieldsFromCiphertextWithANonExportableKey() {
        val expected = auth()
        persistence.write(expected)
        val bytes = File(context.noBackupFilesDir, fileName).readBytes()
        val contents = String(bytes, Charsets.ISO_8859_1)
        assertFalse(contents.contains(expected.accessToken))
        assertFalse(contents.contains(expected.refreshToken))
        assertFalse(contents.contains(expected.email))
        val restored = EncryptedSessionPersistence(context, alias, fileName).read()
        assertEquals(expected, restored)
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        assertNull(store.getKey(alias, null).encoded)
    }

    @Test fun logoutDeletesTheFileAndKey() {
        persistence.write(auth())
        persistence.clear()
        assertNull(EncryptedSessionPersistence(context, alias, fileName).read())
        assertFalse(File(context.noBackupFilesDir, fileName).exists())
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        assertFalse(store.containsAlias(alias))
    }

    @Test fun tamperedCiphertextFailsClosed() {
        persistence.write(auth())
        val file = File(context.noBackupFilesDir, fileName)
        val bytes = file.readBytes()
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        file.writeBytes(bytes)
        assertNull(persistence.read())
        assertFalse(file.exists())
    }

    @Test fun lostKeystoreKeyDoesNotRestoreOldSession() {
        persistence.write(auth())
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
        assertNull(persistence.read())
    }
}
