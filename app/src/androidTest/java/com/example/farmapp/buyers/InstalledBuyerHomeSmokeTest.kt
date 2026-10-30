package com.example.farmapp.buyers

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test

/** Actual installed app/session. Only launches MainActivity and activates the Home navigation item.
 * Never reads credentials, dumps the UI tree, logs account fields, or calls any transaction action.
 */
class InstalledBuyerHomeSmokeTest {
    @Test fun retainedBuyerSessionOpensActualBrianHome() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        context.startActivity(Intent().setClassName(context.packageName,"com.example.farmapp.MainActivity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        val deadline=SystemClock.uptimeMillis()+15_000
        var activated=false
        while(SystemClock.uptimeMillis()<deadline) {
            if(BrianUiPresence.report().contains("Screen: HOME"))break
            if(!activated && BrianUiPresence.report().contains("Integrated Buyer active: true")) {
                val root=instrumentation.uiAutomation.rootInActiveWindow
                val matches=root?.findAccessibilityNodeInfosByText("Home").orEmpty()
                val label=matches.firstOrNull{it.packageName?.toString()==context.packageName && it.isVisibleToUser && (it.text?.toString()=="Home" || it.contentDescription?.toString()=="Home")}
                var target=label
                while(target!=null && !target.isClickable)target=target.parent
                if(target!=null && target.packageName?.toString()==context.packageName && target.isEnabled) {
                    activated=target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
            }
            SystemClock.sleep(250)
        }
        val report=BrianUiPresence.report()
        assertTrue("The retained session did not open Brian's Buyer Home; no credential or purchase action was performed.",
            report.contains("Integrated Buyer active: true") && report.contains("Screen: HOME"))
        instrumentation.sendStatus(0,Bundle().apply{putString("buyer_ui","Brian integrated Buyer HOME confirmed; read-only navigation only")})
    }
}
