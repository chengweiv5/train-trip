package cn.traintrip.app

import android.graphics.Bitmap
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.NumberPicker
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.espresso.action.ViewActions.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import cn.traintrip.app.ui.*
import cn.traintrip.core.SearchFilters
import org.hamcrest.Matcher
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

internal fun chooseWheel(label:String,target:Int) {
    onView(withContentDescription(label)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(object:ViewAction {
        override fun getDescription()="Scroll $label to $target"
        override fun getConstraints():Matcher<View> = isAssignableFrom(NumberPicker::class.java)
        override fun perform(ui:UiController,view:View) {
            val picker=view as NumberPicker
            repeat(61) {
                if(picker.value==target) return
                check(picker.accessibilityNodeProvider.performAction(View.NO_ID,AccessibilityNodeInfo.ACTION_SCROLL_FORWARD,null))
                ui.loopMainThreadForAtLeast(350)
            }
            error("$label did not reach $target")
        }
    })
}

@RunWith(AndroidJUnit4::class)
class TimeRangePickerTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()

    @Test fun fingerSwipeChangesTimeWithoutOpeningKeyboard() {
        var saved=SearchFilters(startMinute=8*60,endMinute=18*60)
        compose.setContent { TrainTripTheme { TimeFilterSheet(saved,{}, {saved=it}) } }
        onView(withContentDescription("开始分钟")).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(swipeUp())
        compose.waitUntil(5000) { !compose.onNodeWithTag("apply-time").fetchSemanticsNode().config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled) }
        compose.onNodeWithTag("apply-time").performClick()
        compose.runOnIdle { assertNotEquals(8*60,saved.startMinute);assertEquals(18*60,saved.endMinute) }
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }

    @Test fun bothWheelsApplyTogetherAndCancelKeepsPreviousValues() {
        var saved=SearchFilters(startMinute=21*60,endMinute=6*60+29)
        var open by mutableStateOf(true)
        compose.setContent { TrainTripTheme { if(open) TimeFilterSheet(saved,{open=false},{saved=it;open=false}) } }
        compose.onNodeWithText("开始").assertIsDisplayed()
        compose.onNodeWithText("结束").assertIsDisplayed()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        chooseWheel("开始小时",22)
        chooseWheel("结束分钟",30)
        compose.onNodeWithText("22:00 – 06:30").assertIsDisplayed()
        capture("time-range")
        compose.onNodeWithTag("apply-time").performClick()
        compose.runOnIdle { assertEquals(1320,saved.startMinute);assertEquals(390,saved.endMinute);open=true }
        compose.onNodeWithTag("time-sheet").assertIsDisplayed()
        chooseWheel("开始分钟",1)
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertEquals(1320,saved.startMinute);assertEquals(390,saved.endMinute) }
    }

    @Test fun midnightAndEqualTimesHaveValidBoundaries() {
        var saved=SearchFilters(startMinute=23*60+59,endMinute=23*60+59)
        compose.setContent { TrainTripTheme { TimeFilterSheet(saved,{}, {saved=it}) } }
        compose.onNodeWithTag("apply-time").assertIsNotEnabled()
        chooseWheel("结束小时",24)
        compose.onNodeWithText("23:59 – 24:00").assertIsDisplayed()
        onView(withContentDescription("结束分钟")).check { view,exception->if(exception!=null) throw exception;assertFalse(view.isEnabled);assertEquals(0,(view as NumberPicker).value) }
        compose.onNodeWithTag("apply-time").performClick()
        compose.runOnIdle { assertEquals(1440,saved.endMinute) }
        chooseWheel("结束小时",0)
        onView(withContentDescription("结束分钟")).check { view,exception->if(exception!=null) throw exception;assertTrue(view.isEnabled) }
        chooseWheel("结束分钟",1)
        compose.onNodeWithText("23:59 – 00:01").assertIsDisplayed()
        compose.onNodeWithText("恢复全天 00:00–24:00").performClick()
        compose.onNodeWithTag("apply-time").performClick()
        compose.runOnIdle { assertEquals(0,saved.startMinute);assertEquals(1440,saved.endMinute) }
    }

    @Test fun draftRestoresAndLargeFontKeepsActionsVisible() {
        val restore=StateRestorationTester(compose)
        var font by mutableFloatStateOf(1f)
        restore.setContent {
            val density=LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density,font)) {
                TrainTripTheme { TimeFilterSheet(SearchFilters(startMinute=8*60,endMinute=18*60),{}, {}) }
            }
        }
        chooseWheel("开始分钟",1)
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("08:01 – 18:00").assertIsDisplayed()
        compose.runOnIdle { font=1.3f }
        compose.onNodeWithTag("apply-time").assertIsDisplayed()
        compose.onNodeWithText("结束").performScrollTo().assertIsDisplayed()
        capture("time-large-font")
    }

    private fun capture(name:String) {
        val bmp=compose.onNodeWithTag("time-sheet").captureToImage().asAndroidBitmap()
        val dir=compose.activity.getExternalFilesDir("time-picker")!!.apply {mkdirs()}
        File(dir,"$name.png").outputStream().use {bmp.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
}
