package com.example.farmapp

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.example.farmapp.network.TokenManager
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** Records only lifecycle classes, booleans and window types. No UI text or account fields. */
class RedactedDeviceDiagnostics : TestWatcher() {
    private val lifecycle = androidx.test.runner.lifecycle.ActivityLifecycleCallback { activity, stage ->
        Log.i("Batch3Diagnostics", "lifecycle ${activity.javaClass.simpleName} instance=${System.identityHashCode(activity)} stage=$stage finishing=${activity.isFinishing} destroyed=${activity.isDestroyed} focus=${activity.hasWindowFocus()} changingConfiguration=${activity.isChangingConfigurations}")
    }

    override fun starting(description: Description) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().addLifecycleCallback(lifecycle)
            Log.i("Batch3Diagnostics", "starting ${description.className}.${description.methodName}")
        }
    }

    override fun finished(description: Description) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().removeLifecycleCallback(lifecycle)
        }
    }

    override fun failed(error: Throwable, description: Description) = capture(description, "failed")
    override fun succeeded(description: Description) = capture(description, "passed")

    private fun capture(description: Description, result: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val monitor = ActivityLifecycleMonitorRegistry.getInstance()
            for (stage in Stage.values()) {
                monitor.getActivitiesInStage(stage).forEach { activity ->
                    Log.i("Batch3Diagnostics", "${description.methodName} $result ${activity.javaClass.simpleName} stage=$stage finishing=${activity.isFinishing} destroyed=${activity.isDestroyed} focus=${activity.hasWindowFocus()}")
                }
            }
            val session = TokenManager.session.value
            Log.i("Batch3Diagnostics", "${description.methodName} sessionPresent=${session.auth != null} buyerRole=${session.auth?.role == "BUYER"} demo=${session.isDemo} loggingOut=${session.loggingOut}")
        }
        runCatching {
            instrumentation.uiAutomation.windows.forEach { window ->
                Log.i("Batch3Diagnostics", "window type=${window.type} active=${window.isActive} focused=${window.isFocused}")
            }
        }.onFailure { Log.i("Batch3Diagnostics", "window inspection unavailable") }
    }
}
