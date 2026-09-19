package cn.traintrip.app

import android.content.Intent
import android.content.ActivityNotFoundException
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RailwayAppLauncherTest {
    @Test fun installedAppUsesResolvedLauncherWithoutBrowserFallback() {
        val intent=Intent(Intent.ACTION_MAIN).setPackage(RAILWAY_PACKAGE)
        var launched:Intent?=null
        assertEquals(AppLaunchResult.OPENED,launchRailwayApp({intent},{launched=it}))
        assertSame(intent,launched)
        assertNull(launched!!.data)
    }
    @Test fun missingPackageDoesNotLaunchAnything() {
        assertEquals(AppLaunchResult.NOT_INSTALLED,launchRailwayApp({null},{error("must not launch")}))
    }
    @Test fun activityAndSecurityFailuresAreReportedWithoutFallback() {
        for(error in listOf(ActivityNotFoundException(),SecurityException())) {
            var attempts=0
            assertEquals(AppLaunchResult.FAILED,launchRailwayApp({Intent(Intent.ACTION_MAIN)},{attempts++;throw error}))
            assertEquals(1,attempts)
        }
        assertEquals(AppLaunchResult.FAILED,launchRailwayApp({throw SecurityException()},{error("must not launch")}))
    }
}
