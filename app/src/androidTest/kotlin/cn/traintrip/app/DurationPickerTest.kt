package cn.traintrip.app

import android.graphics.Bitmap
import android.widget.NumberPicker
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.swipeUp
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import cn.traintrip.app.ui.*
import cn.traintrip.core.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DurationPickerTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val base=SearchFilters(destinationCityIds=setOf("120000"),people=3)

    @Test fun presetsApplyThreeFiveHoursAndUnlimitedWithoutChangingOtherFilters() {
        var saved=base
        compose.setContent { TrainTripTheme { FilterSheet("duration",UiState(StationCatalog.bundled(),saved),{}, {saved=it}) } }
        compose.onNodeWithText("4 小时").assertDoesNotExist();compose.onNodeWithText("8 小时").assertDoesNotExist()
        for((tag,expected) in listOf("three" to 180,"five" to 300,"unlimited" to null)) {
            val before=saved
            compose.onNodeWithTag("duration-$tag").performClick()
            compose.runOnIdle { assertEquals(before,saved) }
            compose.onNodeWithTag("apply-duration").performClick()
            compose.runOnIdle { assertEquals(base.copy(maxMinutes=expected),saved) }
        }
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }

    @Test fun legacyDurationCustomWheelsAndCancelPreserveValues() {
        var saved by mutableStateOf(base.copy(maxMinutes=240))
        var open by mutableStateOf(true)
        compose.setContent { TrainTripTheme { if(open)DurationFilterSheet(saved,{open=false},{saved=it;open=false}) } }
        compose.onNodeWithText("最多 4 小时").assertIsDisplayed()
        chooseWheel("车程分钟",1)
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertEquals(240,saved.maxMinutes);saved=saved.copy(maxMinutes=480);open=true }
        compose.onNodeWithText("最多 8 小时").assertIsDisplayed()
        chooseWheel("车程分钟",1)
        compose.onNodeWithTag("duration-three").performClick()
        compose.onNodeWithTag("duration-custom").performClick()
        compose.onNodeWithText("最多 8 小时 1 分").assertIsDisplayed()
        compose.onNodeWithTag("apply-duration").performClick()
        compose.runOnIdle { assertEquals(481,saved.maxMinutes) }
        Preferences(compose.activity).save(saved)
        assertEquals(saved,Preferences(compose.activity).load(StationCatalog.bundled()))
    }

    @Test fun zeroOneMinuteAndHundredHourBoundaries() {
        var seed by mutableIntStateOf(1)
        var saved=base
        compose.setContent { TrainTripTheme { key(seed) { DurationFilterSheet(base.copy(maxMinutes=seed),{}, {saved=it}) } } }
        onView(withContentDescription("车程分钟")).inRoot(isDialog()).perform(object:androidx.test.espresso.ViewAction {
            override fun getDescription()="Scroll minute backward to zero"
            override fun getConstraints()=androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom(NumberPicker::class.java)
            override fun perform(ui:androidx.test.espresso.UiController,view:android.view.View) {
                check(view.accessibilityNodeProvider.performAction(android.view.View.NO_ID,android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD,null))
                ui.loopMainThreadForAtLeast(400)
            }
        })
        compose.onNodeWithTag("apply-duration").assertIsNotEnabled()
        compose.onNodeWithText("请至少选择 1 分钟").assertIsDisplayed()
        chooseWheel("车程分钟",1)
        compose.onNodeWithTag("apply-duration").performClick()
        compose.runOnIdle { assertEquals(1,saved.maxMinutes);seed=5999 }
        chooseWheel("车程小时",100)
        compose.onNodeWithText("最多 100 小时").assertIsDisplayed()
        onView(withContentDescription("车程分钟")).inRoot(isDialog()).check { view,error->
            if(error!=null)throw error
            assertFalse(view.isEnabled);assertEquals(0,(view as NumberPicker).value);assertEquals(0,view.maxValue)
        }
        compose.onNodeWithTag("apply-duration").performClick()
        compose.runOnIdle { assertEquals(6000,saved.maxMinutes) }
        onView(withContentDescription("车程小时")).inRoot(isDialog()).perform(object:androidx.test.espresso.ViewAction {
            override fun getDescription()="Scroll hour backward to 99"
            override fun getConstraints()=androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom(NumberPicker::class.java)
            override fun perform(ui:androidx.test.espresso.UiController,view:android.view.View) {
                check(view.accessibilityNodeProvider.performAction(android.view.View.NO_ID,android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD,null))
                ui.loopMainThreadForAtLeast(400)
            }
        })
        onView(withContentDescription("车程分钟")).inRoot(isDialog()).check { view,error->
            if(error!=null)throw error
            assertTrue(view.isEnabled);assertEquals(59,(view as NumberPicker).maxValue)
        }
    }

    @Test fun draftRestoresAndLargeFontKeepsTextAndActionsVisible() {
        val restore=StateRestorationTester(compose)
        var font by mutableFloatStateOf(1f)
        restore.setContent {
            val density=LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density,font)) {
                TrainTripTheme { DurationFilterSheet(base.copy(maxMinutes=329),{}, {}) }
            }
        }
        chooseWheel("车程分钟",30)
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("最多 5 小时 30 分").assertIsDisplayed()
        capture("custom")
        compose.runOnIdle { font=1.3f }
        compose.onNodeWithTag("apply-duration").assertIsDisplayed()
        compose.onNodeWithText("取消").assertIsDisplayed()
        compose.onNodeWithText("滑动选择小时和分钟").performScrollTo().assertIsDisplayed()
        val texts=compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),useUnmergedTree=true)
        for(i in texts.fetchSemanticsNodes().indices)if(texts[i].isDisplayed()) {
            texts[i].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action->
                val layouts=mutableListOf<androidx.compose.ui.text.TextLayoutResult>();action(layouts)
                for(layout in layouts)for(line in 0 until layout.lineCount) {
                    assertFalse(layout.isLineEllipsized(line))
                    assertTrue(layout.getLineRight(line)-layout.getLineLeft(line)<=layout.size.width+1)
                    assertTrue(layout.getLineBottom(line)<=layout.size.height+1)
                }
            }
        }
        capture("large-font")
    }

    @Test fun swipeChangesDurationWithoutKeyboardAndPresetClearsScrolling() {
        var saved=base
        compose.setContent { TrainTripTheme { DurationFilterSheet(base,{}, {saved=it}) } }
        compose.onNodeWithTag("duration-custom").performClick()
        compose.onNodeWithText("最多 3 小时").assertIsDisplayed()
        onView(withContentDescription("车程分钟")).inRoot(isDialog()).perform(swipeUp())
        compose.waitUntil(5000) { !compose.onNodeWithTag("apply-duration").fetchSemanticsNode().config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled) }
        compose.onNodeWithTag("apply-duration").performClick()
        compose.runOnIdle { assertNotEquals(180,saved.maxMinutes) }
        compose.onNodeWithTag("duration-five").performClick()
        compose.onNodeWithTag("apply-duration").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(300,saved.maxMinutes) }
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }

    private fun capture(name:String) {
        val dir=compose.activity.getExternalFilesDir("duration-picker")!!.apply { mkdirs() }
        File(dir,"$name.jpg").outputStream().use {
            compose.onNodeWithTag("duration-sheet").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.JPEG,90,it)
        }
    }
}
