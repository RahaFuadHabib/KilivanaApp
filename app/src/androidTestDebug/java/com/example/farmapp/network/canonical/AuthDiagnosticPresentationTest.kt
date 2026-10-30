package com.example.farmapp.network.canonical
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.example.farmapp.BuildConfig
import org.junit.Assert.*
import org.junit.After
import org.junit.Test
class AuthDiagnosticPresentationTest {
    @After fun clear(){AuthAttemptTrace.clear()}
    @Test fun developerRecorderRemainsExplicitAndProviderReadsOnlyMetadata() {
        AuthAttemptTrace.clear()
        if(!BuildConfig.ENABLE_AUTH_DIAGNOSTICS) return
        assertEquals("No active user-armed authentication report.",AuthAttemptTrace.readReport())
        // Isolated instrumentation-process opt-in, no UI entry and no HTTP requests.
        AuthAttemptTrace.arm()
        assertTrue(AuthAttemptTrace.readReport().contains("Events: 0"))
        AuthAttemptTrace.record(AuthAttemptEvent(AuthTraceOperation.LOGIN,AuthTraceStep.HTTP_RESPONSE,403,format=AuthResponseFormat.JSON))
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        context.contentResolver.query(Uri.parse("content://${context.packageName}.auth-diagnostics/report"),null,null,null,null)!!.use {
            assertTrue(it.moveToFirst());val text=it.getString(0)
            assertTrue(text.contains("HTTP=403"));assertFalse(text.contains("Bearer"));assertFalse(text.contains("@"))
        }
    }
}
