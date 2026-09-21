package cn.traintrip.app

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import cn.traintrip.app.ui.*
import cn.traintrip.core.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DestinationWishlistTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val catalog=StationCatalog.bundled()
    private val base=SearchFilters(destinationCityIds=setOf("120000"),people=3,maxMinutes=300)
    private fun wish(id:String,time:Long=1)=catalog.byCity.getValue(id).let { WishCity(id,it.name,it.province.name,time) }

    @Test fun pinnedWishesShareSelectionWithProvincesAndKeepRealProvinceCount() {
        var applied=base
        val browser=DestinationBrowserState("13")
        val wishes=WishlistState(items=listOf(wish("130800",1),wish("130600",2),wish("130600",0)),loading=false)
        compose.setContent { TrainTripTheme { DestinationSelector(UiState(catalog,base),{},{applied=it},browser,wishes) } }
        compose.onNodeWithTag("province-nav-wishlist").assertIsDisplayed().assertIsNotSelected()
        compose.onNodeWithTag("province-navigation").performScrollToNode(hasTestTag("province-nav-37"))
        compose.onNodeWithTag("province-nav-wishlist").assertIsDisplayed().performClick().assertIsSelected()
        val baoding=compose.onNodeWithTag("destination-130600")
        val chengde=compose.onNodeWithTag("destination-130800")
        assertTrue(baoding.fetchSemanticsNode().boundsInRoot.top<chengde.fetchSemanticsNode().boundsInRoot.top)
        compose.onNodeWithText("2 个城市").assertExists()
        baoding.performClick().assertIsOn()
        compose.onNodeWithTag("province-navigation").performScrollToNode(hasTestTag("province-nav-13"))
        compose.onNodeWithTag("province-nav-13").performClick()
        compose.onNodeWithTag("destination-city-list").performScrollToNode(hasTestTag("destination-130600"))
        compose.onNodeWithTag("destination-130600").assertIsOn()
        compose.onNodeWithTag("province-nav-wishlist").performClick()
        compose.onNodeWithTag("wish-select-all").performClick()
        compose.onNodeWithTag("destination-130800").assertIsOn()
        compose.onNodeWithText("已选 2 个省级地区 · 3 个城市  ›").assertExists()
        capture("wishes")
        compose.onNodeWithTag("apply-destinations").performClick()
        compose.runOnIdle { assertEquals(base.copy(destinationCityIds=setOf("120000","130600","130800")),applied) }
        compose.onNodeWithTag("wish-select-all").performClick()
        compose.onNodeWithTag("apply-destinations").performClick()
        compose.runOnIdle { assertEquals(base,applied) }
    }

    @Test fun selectAllSkipsOriginUnavailableAndUnknownCities() {
        var applied=base
        val entries=listOf(wish("130600",5),wish("110000",4),wish("623000",3),WishCity("999999","旧城市","旧省份",2))
        compose.setContent { TrainTripTheme { DestinationSelector(UiState(catalog,base),{},{applied=it},DestinationBrowserState(WISH_DESTINATION_GROUP),WishlistState(entries,loading=false)) } }
        compose.onNodeWithTag("destination-110000").assertIsNotEnabled()
        compose.onNodeWithTag("destination-623000").assertIsNotEnabled()
        compose.onNodeWithTag("destination-wishlist-list").performScrollToNode(hasTestTag("destination-999999"))
        compose.onNodeWithText("当前目录暂未收录此城市").assertIsDisplayed()
        compose.onNodeWithTag("destination-wishlist-list").performScrollToIndex(0)
        compose.onNodeWithTag("wish-select-all").performClick()
        compose.onNodeWithTag("apply-destinations").performClick()
        compose.runOnIdle { assertEquals(setOf("120000","130600"),applied.destinationCityIds) }
    }

    @Test fun loadingErrorRetryEmptyAndLiveUpdatesStayDistinct() {
        var state by mutableStateOf(WishlistState())
        var reloads=0
        val browser=DestinationBrowserState(WISH_DESTINATION_GROUP)
        compose.setContent { TrainTripTheme { DestinationSelector(UiState(catalog,base),{},{},browser,state,{reloads++}) } }
        compose.onNodeWithText("正在读取想去清单…").assertIsDisplayed()
        compose.onNodeWithText("还没有想去的城市").assertDoesNotExist()
        compose.onNodeWithTag("wish-select-all").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1,reloads);state=state.copy(loading=false,error="想去清单暂时无法读取，请重试") }
        compose.onNodeWithText("还没有想去的城市").assertDoesNotExist()
        compose.onNodeWithTag("wish-retry").performClick()
        compose.runOnIdle { assertEquals(2,reloads);state=WishlistState(loading=false) }
        compose.onNodeWithText("还没有想去的城市").assertIsDisplayed()
        capture("empty")
        compose.runOnIdle { state=state.copy(items=listOf(wish("130600")),loading=true) }
        compose.onNodeWithTag("destination-130600").assertIsNotEnabled()
        compose.onNodeWithText("当前出发城市").assertDoesNotExist()
        compose.runOnIdle { state=state.copy(loading=false) }
        compose.onNodeWithTag("destination-130600").assertIsEnabled().performClick().assertIsOn()
        compose.runOnIdle { state=state.copy(items=listOf(wish("130800"))) }
        compose.onNodeWithTag("destination-130600").assertDoesNotExist()
        compose.onNodeWithTag("destination-130800").assertIsDisplayed().assertIsOff()
        compose.onNodeWithText("已选 2 个省级地区 · 2 个城市  ›").assertExists()
    }

    @Test fun searchSelectedViewRestoreAndCancelKeepWishlistDraftIsolated() {
        val restoration=StateRestorationTester(compose)
        var open by mutableStateOf(true)
        var applied=base
        restoration.setContent { TrainTripTheme {
            if(open)DestinationSelector(UiState(catalog,base),{open=false},{applied=it},wishlist=WishlistState(listOf(wish("130600")),loading=false))
        } }
        compose.onNodeWithTag("province-nav-wishlist").performClick()
        compose.onNodeWithTag("destination-130600").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("province-nav-wishlist").assertIsSelected()
        compose.onNodeWithTag("destination-130600").assertIsOn()
        compose.onNodeWithTag("destination-search").performTextInput("济南")
        compose.onNodeWithTag("destination-370100").performClick()
        compose.onNodeWithText("清除",useUnmergedTree=true).performClick()
        compose.onNodeWithTag("destination-130600").assertIsOn()
        compose.onNodeWithText("已选 3 个省级地区 · 3 个城市  ›").performClick()
        compose.onNodeWithText("‹ 继续选择").performClick()
        compose.onNodeWithTag("province-nav-wishlist").assertIsSelected()
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertEquals(base,applied);open=true }
        compose.onNodeWithTag("province-nav-wishlist").performClick()
        compose.onNodeWithTag("destination-130600").assertIsOff()
    }

    @Test fun largeFontDropdownStartsWithWishesAndTextFits() {
        compose.setContent {
            val density=LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density,1.3f)) {
                TrainTripTheme { DestinationSelector(UiState(catalog,base),{},{},wishlist=WishlistState(listOf(wish("130600"),wish("130800")),loading=false)) }
            }
        }
        compose.onNodeWithTag("province-dropdown").performClick()
        compose.onNodeWithTag("province-menu-wishlist").assertIsDisplayed().performClick()
        compose.onNodeWithTag("destination-130600").performClick().assertIsOn()
        compose.onNodeWithTag("apply-destinations").assertIsDisplayed()
        val texts=compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),useUnmergedTree=true)
        for(i in texts.fetchSemanticsNodes().indices)if(texts[i].isDisplayed())texts[i].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action->
            val layouts=mutableListOf<androidx.compose.ui.text.TextLayoutResult>();action(layouts)
            for(layout in layouts)for(line in 0 until layout.lineCount) {
                assertFalse(layout.isLineEllipsized(line))
                assertTrue(layout.getLineRight(line)-layout.getLineLeft(line)<=layout.size.width+1)
                assertTrue(layout.getLineBottom(line)<=layout.size.height+1)
            }
        }
        capture("large-font")
    }

    private fun capture(name:String) {
        val dir=compose.activity.getExternalFilesDir("wish-destinations")!!.apply { mkdirs() }
        File(dir,"$name.jpg").outputStream().use { compose.onNodeWithTag("destination-selector").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.JPEG,90,it) }
    }
}
