package cn.traintrip.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import cn.traintrip.core.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppFlowTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Before fun resetPreferences() {
        compose.activity.getSharedPreferences("travel-filters",android.content.Context.MODE_PRIVATE).edit().clear().commit()
        compose.activityRule.scenario.recreate()
    }

    @Test fun filtersApplyCancelAndPersist() {
        compose.onNodeWithText("有票，就出发。").assertIsDisplayed()
        compose.onNodeWithText("乘车人数").performScrollTo().performClick()
        compose.onNodeWithText("＋").performClick()
        compose.onNodeWithText("2 位成人").assertExists()
        compose.onNodeWithText("完成").performClick()
        compose.onNodeWithText("2 人").assertExists()
        compose.onNodeWithText("乘车人数").performClick()
        compose.onNodeWithText("＋").performClick()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.onNodeWithText("2 人").assertExists()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("乘车人数").performScrollTo()
        compose.onNodeWithText("2 人").assertExists()
    }

    @Test fun timeAndScopeApply() {
        compose.onNodeWithText("自定义").performScrollTo().performClick()
        chooseWheel("开始小时",22)
        chooseWheel("结束小时",6)
        compose.onNodeWithText("完成").performClick()
        compose.onNodeWithText("22:00–06:00 · 所选日期每天适用").assertExists()
        compose.onNodeWithText("查询目的地").performScrollTo().performClick()
        compose.onNodeWithText("清空选择").performClick()
        compose.onNodeWithText("搜索省份或城市").performTextInput("天津")
        compose.onNode(hasText("天津") and !hasSetTextAction()).performClick()
        compose.onNodeWithText("完成").performClick()
        compose.onNodeWithText("1 个城市 · 1 个省级地区").assertExists()
    }

    @Test fun realSearchToTrainAndRecheck() {
        val vm=androidx.lifecycle.ViewModelProvider(compose.activity)[AppViewModel::class.java]
        compose.runOnIdle {
            val s=vm.state.value
            vm.updateFilters(SearchFilters(destinationCityIds=setOf(s.catalog.byCode.getValue("TJP").cityId)))
        }
        capture("01-filters")
        compose.onNodeWithText("找找有票的城市  →").performScrollTo().performClick()
        compose.waitUntil(60000) { !vm.state.value.loading && vm.state.value.progress?.running==false }
        compose.runOnIdle {
            assertNull(vm.state.value.error)
            assertTrue("Real search must finish successfully",vm.state.value.progress?.complete==true)
        }
        compose.onNode(hasText("天津") and !hasSetTextAction()).performScrollTo().assertExists()
        capture("02-cities")
        compose.onNode(hasText("天津") and !hasSetTextAction()).performClick()
        compose.onNodeWithText("去天津").assertExists()
        capture("03-trains")
        compose.runOnIdle {
            val s=vm.state.value;val f=s.applied!!
            val trip=s.progress!!.trips.first { it.confirmed(f) }
            vm.select(trip,f.seats.first { trip.seats[it]?.confirmedFor(f.people)==true })
        }
        compose.onNodeWithText("核验余票并去 12306").performClick()
        compose.waitUntil(60000) { !vm.state.value.rechecking && vm.state.value.notice!=null }
        compose.runOnIdle { assertTrue("Recheck should reach official handoff: ${vm.state.value.notice}",vm.state.value.handoffReady) }
        capture("04-recheck")
        val automation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        val expected=automation.executeShellCommand("cmd package resolve-activity --brief -a android.intent.action.VIEW -d ${OfficialTicketSource.INIT}").use { d->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(d).bufferedReader().readText().lineSequence().last { it.contains('/') }.substringBefore('/')
        }
        compose.onNodeWithText("复制行程并打开 12306").performClick()
        var resumed=""
        repeat(20) {
            android.os.SystemClock.sleep(100)
            automation.executeShellCommand("dumpsys activity activities").use { d->
                resumed=android.os.ParcelFileDescriptor.AutoCloseInputStream(d).bufferedReader().readText().lineSequence().filter { it.contains("mResumedActivity") || it.contains("topResumedActivity") }.joinToString()
            }
            if(resumed.contains(expected)) return@repeat
        }
        assertTrue("Official page did not open: $resumed",resumed.contains(expected))
    }

    private fun capture(name:String) {
        compose.waitForIdle()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(450)
        val bitmap=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val dir=compose.activity.getExternalFilesDir("verification")!!
        dir.mkdirs()
        java.io.File(dir,"$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        val command="cp ${java.io.File(dir,"$name.png").absolutePath} /sdcard/Download/train-trip-$name.png"
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use { descriptor->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).readBytes()
        }
    }

    @Test fun enlargedFontKeepsPrimaryActionReachable() {
        val automation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        fun shell(command:String) { automation.executeShellCommand(command).use { d->android.os.ParcelFileDescriptor.AutoCloseInputStream(d).readBytes() } }
        try {
            shell("settings put system font_scale 1.3")
            compose.activityRule.scenario.recreate()
            compose.onNodeWithText("有票，就出发。").assertIsDisplayed()
            compose.onNodeWithText("找找有票的城市  →").performScrollTo().assertIsDisplayed()
            capture("05-large-font")
        } finally { shell("settings put system font_scale 1.0") }
    }
}

@RunWith(AndroidJUnit4::class)
class LiveSourceTest {
    @Test fun anonymousAndroidQueryParsesRealInventory() = runBlocking {
        val source=OfficialTicketSource()
        val info=source.initialize()
        assertTrue(info.catalog.stations.size>1000)
        val date=today().plusDays(1)
        val unit=QueryUnit(date,info.catalog.byCode.getValue("BJP"),info.catalog.byCode.getValue("TJP"))
        val result=source.query(unit)
        assertTrue("Live query failed: $result",result is QueryResult.Success)
        result as QueryResult.Success
        assertTrue("Expected real train records",result.trips.isNotEmpty())
        val available=result.trips.count { it.confirmed(SearchFilters(startDate=date,endDate=date)) }
        android.util.Log.i("TrainTripLive","date=$date options=${result.trips.size} available=$available at=${result.receivedAt}")
        assertTrue(result.trips.all { it.from.cityName=="北京" && it.to.cityName=="天津" })
        for(code in listOf("SJP","BEP")) {
            kotlinx.coroutines.delay(1500)
            val next=source.query(unit.copy(destination=info.catalog.byCode.getValue(code)))
            assertTrue("Additional city query failed: $next",next is QueryResult.Success)
            next as QueryResult.Success
            android.util.Log.i("TrainTripLive","city=${info.catalog.byCode.getValue(code).cityName} date=$date options=${next.trips.size} available=${next.trips.count { it.confirmed(SearchFilters(startDate=date,endDate=date)) }} at=${next.receivedAt}")
        }
    }
}
