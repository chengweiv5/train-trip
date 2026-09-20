package cn.traintrip.app

import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import cn.traintrip.app.ui.AboutScreen
import cn.traintrip.app.ui.TrainTripTheme
import cn.traintrip.core.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReleaseNotesLinkTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private fun clickNotes(state:UpdateState):Intent {
        var opened:Intent?=null
        val context=object:ContextWrapper(compose.activity) {
            override fun startActivity(intent:Intent) { opened=intent }
        }
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context) {
                TrainTripTheme { AboutScreen(state,{}, {error("notes must not trigger update checks")}) }
            }
        }
        compose.onNodeWithText("更新说明").performScrollTo().performClick()
        compose.runOnIdle { assertNotNull("Notes must open a usable release page",opened) }
        assertEquals(Intent.ACTION_VIEW,opened!!.action)
        return opened!!
    }
    @Test fun uncheckedLocalVersionOpensReleaseListInsteadOfInventingTag() {
        assertEquals("$PROJECT_URL/releases",clickNotes(UpdateState()).dataString)
    }
    @Test fun newerLocalBuildDoesNotOpenMissingTagOrOlderVersionNotes() {
        val remote=AppRelease(ReleaseVersion(0,4,0),releaseUrl("0.4.0"),"",true)
        assertEquals("$PROJECT_URL/releases",clickNotes(UpdateState(release=remote)).dataString)
    }
    @Test fun verifiedCurrentVersionOpensItsActualRelease() {
        val version=compose.activity.packageManager.getPackageInfo(compose.activity.packageName,0).versionName!!
        val remote=AppRelease(ReleaseVersion.parse(version)!!,releaseUrl(version),"",true)
        assertEquals(remote.url,clickNotes(UpdateState(release=remote)).dataString)
    }
    @Test fun failedCheckStillAllowsReleaseList() {
        assertEquals("$PROJECT_URL/releases",clickNotes(UpdateState(error="网络不可用")).dataString)
    }
    @Test fun newerRemoteVersionKeepsCurrentNotesSeparateFromDownload() {
        val remote=AppRelease(ReleaseVersion(99,0,0),releaseUrl("99.0.0"),"",true)
        assertEquals("$PROJECT_URL/releases",clickNotes(UpdateState(release=remote)).dataString)
    }
}
