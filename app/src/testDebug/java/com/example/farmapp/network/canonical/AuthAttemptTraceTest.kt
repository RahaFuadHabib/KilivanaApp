package com.example.farmapp.network.canonical
import com.example.farmapp.BuildConfig
import org.junit.Assert.*
import org.junit.Test
class AuthAttemptTraceTest {
    @Test fun optInIsRequiredAndCacheIsBoundedMetadataOnly() {
        AuthAttemptTrace.clear()
        val event=AuthAttemptEvent(AuthTraceOperation.LOGIN,AuthTraceStep.HTTP_RESPONSE,401,format=AuthResponseFormat.JSON)
        AuthAttemptTrace.record(event)
        assertEquals("No active user-armed authentication report.",AuthAttemptTrace.readReport())
        AuthAttemptTrace.arm()
        repeat(100){AuthAttemptTrace.record(event)}
        val report=AuthAttemptTrace.readReport()
        if(BuildConfig.ENABLE_AUTH_DIAGNOSTICS){assertTrue(report.contains("Events: 64"));assertTrue(report.contains("HTTP=401"));assertFalse(report.contains("http://"))}
        else assertEquals("No active user-armed authentication report.",report)
        AuthAttemptTrace.clear()
    }
}
