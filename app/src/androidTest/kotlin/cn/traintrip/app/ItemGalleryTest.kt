package cn.traintrip.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import cn.traintrip.app.ui.*
import cn.traintrip.core.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ItemGalleryTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private fun fixture():DestinationGuide {
        val base=DestinationGuides.all.first()
        // Distinct bundled files exercise paging offline; labels are isolated test data.
        val assets=DestinationGuides.all.mapNotNull { it.photo }.take(3)
        val subject=PhotoSubject("place",base.experiences.first().name)
        return base.withPhotos(assets.mapIndexed { i,p -> p.copy(description="${base.name} · ${subject.name} · 测试图${i+1}",subject=subject) })
    }
    @Test fun threePhotosStayInTheirCardAndDialogReturnsToCurrentPhoto() {
        val data=fixture();var source=""
        compose.setContent { TrainTripTheme { DestinationGuideScreen(data.name,data,{}, {}, {source=it}) } }
        compose.onNodeWithTag("guide-page-places").performScrollToNode(hasTestTag("gallery-pager"))
        compose.onAllNodesWithTag("destination-gallery").assertCountEquals(1)
        compose.onNodeWithTag("gallery-count").assertTextEquals("1/3")
        compose.onNodeWithTag("gallery-pager").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("gallery-count").assertTextEquals("2/3")
        compose.onNodeWithTag("gallery-source").performScrollTo().performClick()
        assertEquals(data.gallery[1].sourceUrl,source)
        compose.onNodeWithTag("gallery-pager").performScrollTo()
        compose.onNodeWithContentDescription(data.gallery[1].description).performClick()
        compose.onNodeWithText("图片 2/3").assertIsDisplayed()
        compose.onNodeWithTag("gallery-full-pager").performTouchInput { swipeLeft() }
        compose.onNodeWithText("图片 3/3").assertIsDisplayed()
        compose.onNodeWithTag("close-gallery").performClick()
        compose.onNodeWithTag("gallery-count").assertTextEquals("3/3")
        compose.onNodeWithTag("gallery-pager").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("guide-tab-places").assertIsSelected()
        compose.onNodeWithTag("guide-tab-food").performClick()
        compose.onAllNodesWithTag("destination-gallery").assertCountEquals(0)
    }
    @Test fun foodSinglePhotoHasNoCounterAndLargeFontStillReachesTextAndUpdate() {
        val base=DestinationGuides.all.first()
        val food=base.foods.first()
        val data=base.withPhotos(listOf(base.photo!!.copy(subject=PhotoSubject("food",food.name))))
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,1.3f)) {
            TrainTripTheme { Box(Modifier.width(320.dp).fillMaxHeight().safeDrawingPadding()) {
                DestinationGuideScreen(data.name,data,{}, {}, {},runtime=DestinationState(configured=true,tavilyConfigured=true))
            } }
        } }
        compose.onNodeWithTag("guide-tab-food").performClick()
        compose.onNodeWithTag("guide-page-food").performScrollToNode(hasTestTag("gallery-pager"))
        compose.onNodeWithTag("gallery-count").assertDoesNotExist()
        compose.onNodeWithTag("guide-food-0").assertExists()
        compose.onNodeWithTag("guide-page-food").performScrollToNode(hasText(food.description))
        compose.onNodeWithText(food.description).assertIsDisplayed()
        compose.onNodeWithTag("guide-page-food").performScrollToNode(hasTestTag("refresh-guide"))
        compose.onNodeWithTag("refresh-guide").assertIsDisplayed()
        compose.onNodeWithTag("refresh-photos").assertDoesNotExist()
        capture("item-food-large")
    }
    @Test fun unmatchedCityCoverDoesNotCreateAnyGallery() {
        val base=DestinationGuides.all.first()
        val data=base.withPhotos(listOf(base.photo!!.copy(description="${base.name} · 城市资料页配图")))
        compose.setContent { TrainTripTheme { DestinationGuideScreen(data.name,data,{}, {}, {}) } }
        compose.onAllNodesWithTag("destination-gallery").assertCountEquals(0)
    }
    private fun capture(name:String) {
        val bmp=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        java.io.File(compose.activity.getExternalFilesDir(null),"$name.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
    }
}
