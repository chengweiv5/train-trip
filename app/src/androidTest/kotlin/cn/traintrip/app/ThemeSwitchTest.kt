package cn.traintrip.app

import android.graphics.Bitmap
import android.util.AtomicFile
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
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
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class ThemeSwitchTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private class Store(var choice:ThemeChoice=ThemeChoice.BLUE):ThemePreference {
        var fail=false
        override fun read()=choice
        override fun save(choice:ThemeChoice) { if(fail)throw IOException("test failure");this.choice=choice }
    }
    private val expected=listOf(Color(0xFF0073AA),Color(0xFF246B50),Color(0xFFA64B18),Color(0xFF7052A3))

    @Test fun allThemesApplyToPreviewAndHomeAndSettingsKeepSecureBoundary() {
        val store=Store();val vm=ThemeViewModel(store)
        var home by mutableStateOf(false)
        val catalog=StationCatalog.bundled()
        val filters=SearchFilters(startDate=LocalDate.of(2026,10,1),endDate=LocalDate.of(2026,10,3),people=2,
            maxMinutes=180,seats=setOf(SeatType.SECOND,SeatType.FIRST),destinationCityIds=setOf("120000"))
            .withDeparturePeriods(setOf(DeparturePeriod.MORNING,DeparturePeriod.AFTERNOON))
        compose.setContent {
            val state by vm.state.collectAsState()
            TrainTripTheme(state.choice) {
                if(home) Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                    Box(Modifier.weight(1f)) { FiltersScreen(UiState(catalog,filters),{},{},{home=false}) }
                    RootNavigation(Page.FILTERS,{})
                } else SettingsScreen(DestinationState(),{home=true},{_,_,_->},{_,_->},{},{},
                    themeState=state,onThemeSelect=vm::select,onThemeRetry=vm::reload,onThemeDismiss=vm::clearFeedback)
            }
        }
        compose.waitUntil(5000){vm.state.value.ready}
        for((index,choice) in ThemeChoice.entries.withIndex()) {
            compose.onNodeWithTag("settings-theme").performClick()
            compose.onNodeWithTag("theme-${choice.id}").performClick()
            compose.waitUntil(5000){!vm.state.value.saving}
            ThemeChoice.entries.forEach { option->
                if(option==choice)compose.onNodeWithTag("theme-${option.id}").assertIsSelected()
                else compose.onNodeWithTag("theme-${option.id}").assertIsNotSelected()
            }
            compose.onNodeWithTag("theme-preview-button").assertHasNoClickAction()
            val pixels=compose.onNodeWithTag("theme-preview-button").captureToImage().toPixelMap()
            assertColor(expected[index],pixels[12,pixels.height/2])
            assertTextFits();capture("picker-${choice.id}")
            compose.runOnIdle { assertEquals(0,compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) }
            compose.onNodeWithTag("settings-back").performClick()
            compose.onNode(hasText(choice.label) and hasAnyAncestor(hasTestTag("settings-theme")),useUnmergedTree=true).assertExists()
            compose.onNodeWithTag("settings-back").performClick()
            compose.onNodeWithTag("home-headline",useUnmergedTree=true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { get->
                val result=mutableListOf<androidx.compose.ui.text.TextLayoutResult>();get(result)
                assertEquals(expected[index],result.single().layoutInput.style.color)
            }
            assertTextFits();capture("home-${choice.id}")
            compose.onNodeWithTag("content-settings").performClick()
        }
        compose.onNodeWithTag("settings-model").performClick()
        compose.runOnIdle { assertTrue(compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE!=0) }
        compose.onNodeWithTag("settings-back").performClick()
        compose.onNodeWithTag("settings-theme").performClick()
        compose.runOnIdle { assertEquals(0,compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) }
    }

    @Test fun failedSaveRestoresRadioAndPaletteThenRetryWorks() {
        val store=Store(ThemeChoice.GREEN);val vm=ThemeViewModel(store)
        compose.setContent { val state by vm.state.collectAsState();TrainTripTheme(state.choice) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) { ThemeSelectionScreen(state,vm::select,vm::reload,vm::clearFeedback) }
        } }
        compose.waitUntil(5000){vm.state.value.ready}
        store.fail=true
        compose.onNodeWithTag("theme-orange").performClick()
        compose.waitUntil(5000){vm.state.value.error!=null}
        compose.onNodeWithTag("theme-green").assertIsSelected()
        compose.onNodeWithTag("theme-orange").assertIsNotSelected()
        compose.onNodeWithTag("theme-feedback").performScrollTo()
        compose.onNodeWithText("ⓘ 未能保存主题，请重试").assertIsDisplayed()
        assertEquals(ThemeChoice.GREEN,store.choice)
        capture("save-failed")
        store.fail=false
        compose.onNodeWithTag("theme-orange").performScrollTo().performClick()
        compose.waitUntil(5000){!vm.state.value.saving}
        compose.onNodeWithTag("theme-orange").assertIsSelected()
        assertEquals(ThemeChoice.ORANGE,store.choice)
    }

    @Test fun narrowLargeTextUsesSingleColumnAndEveryChoiceRemainsReachable() {
        var choice by mutableStateOf(ThemeChoice.GREEN)
        compose.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.3f)) {
                TrainTripTheme(choice) { Box(Modifier.width(320.dp).fillMaxHeight().safeDrawingPadding()) {
                    ThemeSelectionScreen(ThemeUiState(choice=choice),{choice=it},{},{})
                } }
            }
        }
        compose.onNodeWithTag("theme-list").assertExists()
        compose.onNodeWithTag("theme-grid").assertDoesNotExist()
        assertTextFits();capture("narrow-large-top")
        ThemeChoice.entries.forEach {
            compose.onNodeWithTag("theme-${it.id}").performScrollTo().performClick().assertIsSelected()
            assertTextFits()
        }
        capture("narrow-large-bottom")
    }

    @Test fun persistedFileReopensAndMissingOrUnknownChoiceDefaultsWithoutTouchingOtherData() {
        val directory=File(compose.activity.cacheDir,"theme-storage-${System.nanoTime()}").apply { mkdirs() }
        try {
            val file=File(directory,"theme-choice")
            val unrelated=File(directory,"other").apply { writeText("keep me") }
            val first=AndroidThemePreference(AtomicFile(file))
            assertEquals(ThemeChoice.BLUE,first.read())
            ThemeChoice.entries.forEach { choice->
                first.save(choice)
                assertEquals(choice,AndroidThemePreference(AtomicFile(file)).read())
            }
            file.writeText("future-theme")
            assertEquals(ThemeChoice.BLUE,first.read())
            assertEquals("future-theme",file.readText())
            assertEquals("keep me",unrelated.readText())
        } finally { directory.deleteRecursively() }
    }

    @Test fun changingPaletteKeepsTrainMarkAndFixedAvailabilityMeaning() {
        val catalog=StationCatalog.bundled();val date=LocalDate.of(2026,10,1)
        val origin=catalog.byCode.getValue("VNP");val destination=catalog.byCode.getValue("TJP")
        val trip=Trip(date,"C2007","C2007",origin,destination,LocalTime.of(8,12),LocalTime.of(8,45),33,SaleState.OPEN,"",
            mapOf(SeatType.SECOND to SeatAvailability("21",AvailabilityKind.COUNT,21)),Instant.now())
        val unit=QueryUnit(date,origin,destination)
        val filters=SearchFilters(startDate=date,endDate=date,destinationCityIds=setOf(destination.cityId))
        var state by mutableStateOf(UiState(catalog,filters,applied=filters,cityId=destination.cityId,
            progress=SearchProgress(listOf(unit),mapOf(unit.key to QueryResult.Success(listOf(trip),Instant.now())))))
        var choice by mutableStateOf(ThemeChoice.BLUE)
        compose.setContent { TrainTripTheme(choice) { Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            DetailScreen(state,{},{state=state.copy(selectedTripKey=it.key)},{},{},{},{})
        } } }
        compose.onNodeWithTag("trip-${trip.key}").performClick().assertIsSelected()
        ThemeChoice.entries.forEach {
            compose.runOnIdle { choice=it }
            compose.onNodeWithTag("trip-${trip.key}").assertIsSelected()
            compose.onNodeWithTag("selected-summary").assertIsDisplayed()
            compose.onNodeWithText("二等座 21 张",useUnmergedTree=true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { get->
                val result=mutableListOf<androidx.compose.ui.text.TextLayoutResult>();get(result)
                assertEquals(Color(0xFF0F7952),result.single().layoutInput.style.color)
            }
            assertEquals(trip.key,state.selectedTripKey)
        }
        capture("trains-purple")
    }

    @Test fun interruptedOrBlockedDiskWritePreservesLastChoice() {
        val directory=File(compose.activity.cacheDir,"theme-failure-${System.nanoTime()}").apply { mkdirs() }
        try {
            val file=File(directory,"theme-choice")
            val atomic=AtomicFile(file)
            val store=AndroidThemePreference(atomic)
            store.save(ThemeChoice.GREEN)
            atomic.startWrite().use { it.write("orange".toByteArray()) }
            assertEquals(ThemeChoice.GREEN,AndroidThemePreference(AtomicFile(file)).read())
            val pending=File(directory,"theme-choice.new").apply { mkdirs() }
            File(pending,"block").writeText("test fixture")
            try {
                store.save(ThemeChoice.PURPLE)
                fail("A blocked write must fail")
            } catch (_: IOException) { }
            assertEquals("green",file.readText())
            assertEquals(ThemeChoice.GREEN,AndroidThemePreference(AtomicFile(file)).read())
            pending.deleteRecursively()
            store.save(ThemeChoice.PURPLE)
            assertEquals(ThemeChoice.PURPLE,AndroidThemePreference(AtomicFile(file)).read())
        } finally { directory.deleteRecursively() }
    }

    private fun assertColor(expected:Color,actual:Color) {
        assertEquals(expected.red,actual.red,.015f);assertEquals(expected.green,actual.green,.015f);assertEquals(expected.blue,actual.blue,.015f)
    }
    private fun assertTextFits() {
        val nodes=compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),useUnmergedTree=true)
        for(i in nodes.fetchSemanticsNodes().indices) {
            if(!nodes[i].isDisplayed())continue
            nodes[i].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { get->
                val results=mutableListOf<androidx.compose.ui.text.TextLayoutResult>();get(results)
                results.forEach { layout->
                    for(line in 0 until layout.lineCount) {
                        assertFalse("Ellipsized: ${layout.layoutInput.text}",layout.isLineEllipsized(line))
                        assertTrue("Text too wide: ${layout.layoutInput.text}",layout.getLineRight(line)-layout.getLineLeft(line)<=layout.size.width+1)
                        assertTrue("Text too tall: ${layout.layoutInput.text}",layout.getLineBottom(line)<=layout.size.height+1)
                    }
                }
            }
        }
    }
    private fun capture(name:String) {
        val dir=compose.activity.getExternalFilesDir("v1.0-themes")!!.apply { mkdirs() }
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap->
            File(dir,"$name.jpg").outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG,90,it) }
        }
    }
}

@RunWith(AndroidJUnit4::class)
class MainActivityThemeTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun themeSwitchAndRecreationPreserveFiltersAndRootPalette() {
        val vm=androidx.lifecycle.ViewModelProvider(compose.activity)[AppViewModel::class.java]
        val themes=androidx.lifecycle.ViewModelProvider(compose.activity)[ThemeViewModel::class.java]
        compose.waitUntil(5000){themes.state.value.ready}
        val filters=vm.state.value.filters.copy(people=3,maxMinutes=120)
        compose.runOnIdle { vm.updateFilters(filters) }
        compose.onNodeWithTag("content-settings").performClick()
        compose.onNodeWithTag("settings-theme").performClick()
        compose.onNodeWithTag("theme-purple").performClick()
        compose.waitUntil(5000){!themes.state.value.saving}
        compose.onNodeWithTag("theme-purple").assertIsSelected()
        compose.onNodeWithTag("settings-back").performClick()
        compose.onNodeWithTag("settings-back").performClick()
        compose.runOnIdle { assertEquals(filters,vm.state.value.filters);assertEquals(Page.FILTERS,vm.state.value.page) }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("home-headline").assertIsDisplayed()
        assertEquals(ThemeChoice.PURPLE,AndroidThemePreference(compose.activity).read())
        compose.onNodeWithText("乘车人数").performScrollTo()
        compose.onNodeWithText("3 人").assertIsDisplayed()
        compose.onNodeWithTag("content-settings").performClick()
        compose.onNodeWithTag("settings-theme").performClick()
        compose.onNodeWithTag("theme-purple").assertIsSelected()
    }
}
