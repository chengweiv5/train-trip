package cn.traintrip.app

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import cn.traintrip.app.ui.*
import cn.traintrip.core.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class B4UiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val catalog=StationCatalog.bundled()
    private val city=catalog.cities.first { it.name=="天津" }
    private val wish=WishCity(city.id,city.name,city.province.name,1L)

    @Test fun headersKeepActionsReachableAtStandardSize()=checkHeaders(false)
    @Test fun headersWrapWithoutOverlapAt320dpAndLargeFont()=checkHeaders(true)

    private fun checkHeaders(large:Boolean) {
        var showWishlist by mutableStateOf(false)
        var settings=0;var adds=0
        compose.setContent {
            val density=LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density,if(large)1.3f else 1f)) {
                TrainTripTheme { Box(Modifier.width(if(large)320.dp else 390.dp).fillMaxHeight().safeDrawingPadding()) {
                    if(showWishlist) WishlistScreen(WishlistState(items=listOf(wish),loading=false),catalog,
                        mapOf(city.id to DestinationGuides.find(city.id)!!),emptyList(),{adds++},{},{},{},{})
                    else FiltersScreen(UiState(catalog,SearchFilters()),{},{},{settings++})
                } }
            }
        }
        val settingsNode=compose.onNodeWithTag("content-settings")
        assertTouchTarget(settingsNode)
        assertSeparate("brand-title","content-settings")
        capture(if(large)"home-large" else "home");assertTextFits()
        compose.onNodeWithTag("search-cities").performScrollTo().assertIsDisplayed()
        settingsNode.assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1,settings);showWishlist=true }
        compose.onNodeWithText("1 个想去城市 · 省内最近收藏优先").assertIsDisplayed()
        val add=compose.onNodeWithTag("wishlist-add")
        assertTouchTarget(add);assertSeparate("brand-title","wishlist-add")
        compose.onNodeWithTag("wish-query-${city.id}").assertIsDisplayed()
        assertTextFits();capture(if(large)"wishlist-large" else "wishlist")
        add.performClick();compose.runOnIdle { assertEquals(1,adds) }
    }

    @Test fun wishlistSummaryDistinguishesLoadingFailureAndEmpty() {
        var state by mutableStateOf(WishlistState())
        compose.setContent { TrainTripTheme {
            WishlistScreen(state,catalog,emptyMap(),emptyList(),{},{},{},{},{})
        } }
        compose.onNodeWithText("正在读取想去清单…").assertIsDisplayed()
        compose.onNodeWithText("先收藏一座想去的城市").assertDoesNotExist()
        compose.runOnIdle { state=WishlistState(loading=false,error="读取失败") }
        compose.onNodeWithText("清单读取失败，请重试").assertIsDisplayed()
        compose.onNodeWithText("先收藏一座想去的城市").assertDoesNotExist()
        compose.runOnIdle { state=WishlistState(loading=false) }
        compose.onNodeWithText("把心动的城市，留给下次出发").assertIsDisplayed()
        compose.onNodeWithText("先收藏一座想去的城市").assertIsDisplayed()
    }

    @Test fun cardAndChoiceRemainVisuallyDistinctAfterSelection() {
        var selected by mutableStateOf(false)
        compose.setContent { TrainTripTheme {
            Column(Modifier.fillMaxSize().background(PageBackground).padding(16.dp).testTag("page-surface")) {
                ContentCard(Modifier.fillMaxWidth().testTag("card-surface")) {
                    Choice("下午",selected,{selected=!selected},Modifier.fillMaxWidth().testTag("choice-surface"))
                }
            }
        } }
        fun pixel(tag:String,x:Int,y:Int)=compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap().getPixel(x,y)
        val density=compose.activity.resources.displayMetrics.density
        val inset=(6*density).toInt()
        val page=compose.onNodeWithTag("page-surface").captureToImage().asAndroidBitmap()
        val pageColor=page.getPixel(0,page.height-1)
        val card=pixel("card-surface",inset,inset)
        val control=pixel("choice-surface",inset,inset)
        assertNotEquals("Card separates from the page",pageColor,card)
        assertNotEquals("Control separates from its card",card,control)
        compose.onNodeWithTag("choice-surface").performClick()
        assertNotEquals("Selection changes the visible control",control,pixel("choice-surface",inset,inset))
        assertTextFits()
    }

    private fun assertTouchTarget(node:SemanticsNodeInteraction) {
        val bounds=node.fetchSemanticsNode().boundsInRoot
        val min=48*compose.activity.resources.displayMetrics.density
        assertTrue("At least 48dp width",bounds.width>=min-1)
        assertTrue("At least 48dp height",bounds.height>=min-1)
    }
    private fun assertSeparate(title:String,action:String) {
        val a=compose.onNodeWithTag(title,useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        val b=compose.onNodeWithTag(action).fetchSemanticsNode().boundsInRoot
        assertTrue("Title and action do not overlap",a.right<=b.left)
    }
    private fun assertTextFits() {
        val nodes=compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),useUnmergedTree=true)
        for(i in nodes.fetchSemanticsNodes().indices) {
            if(!nodes[i].isDisplayed())continue
            nodes[i].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action->
                val layouts=mutableListOf<androidx.compose.ui.text.TextLayoutResult>();action(layouts)
                layouts.forEach { layout->
                    assertFalse("Text ellipsized: ${layout.layoutInput.text}",layout.isLineEllipsized(layout.lineCount-1))
                    for(line in 0 until layout.lineCount) {
                        assertTrue("Text width: ${layout.layoutInput.text} ${layout.size} right=${layout.getLineRight(line)}",
                            layout.getLineRight(line)-layout.getLineLeft(line)<=layout.size.width+1)
                        assertTrue("Text height: ${layout.layoutInput.text} ${layout.size} bottom=${layout.getLineBottom(line)}",
                            layout.getLineBottom(line)<=layout.size.height+1)
                    }
                }
            }
        }
    }
    private fun capture(name:String) {
        val dir=compose.activity.getExternalFilesDir("b4")!!;dir.mkdirs()
        File(dir,"$name.jpg").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.JPEG,90,it)
        }
    }
}
