package cn.traintrip.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import android.os.ParcelFileDescriptor
import android.os.SystemClock

/** Explicit opt-in because this brings another installed application to the foreground. */
@RunWith(AndroidJUnit4::class)
class NativeAppLaunchTest {
    @Test fun installedRailwayAppOpensAndReturnsToTrainTrip() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("realRailwayLaunch")=="true")
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        fun resumed():String = instrumentation.uiAutomation.executeShellCommand("dumpsys activity activities").use { descriptor ->
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().readText().lineSequence()
                .filter { it.contains("mResumedActivity") || it.contains("topResumedActivity") }.joinToString()
        }
        fun awaitPackage(name:String) {
            val deadline=SystemClock.elapsedRealtime()+15000
            while(SystemClock.elapsedRealtime()<deadline) {
                if(resumed().contains(name)) return
                SystemClock.sleep(200)
            }
            fail("Expected foreground package $name; got ${resumed()}")
        }
        try {
            assertNotNull(context.packageManager.getLaunchIntentForPackage(RAILWAY_PACKAGE))
            assertEquals(AppLaunchResult.OPENED,launchRailwayApp(context))
            awaitPackage(RAILWAY_PACKAGE)
        } finally {
            context.startActivity(context.packageManager.getLaunchIntentForPackage(context.packageName)!!)
        }
        awaitPackage(context.packageName)
    }
}
