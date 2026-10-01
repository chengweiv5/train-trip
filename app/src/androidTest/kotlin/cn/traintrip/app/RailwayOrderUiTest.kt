package cn.traintrip.app

import android.net.Uri
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import cn.traintrip.app.ui.*
import cn.traintrip.core.*
import cn.traintrip.core.waitlist.*
import java.time.Instant
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

class RailwayOrderUiTest {
    @get:Rule val compose = createComposeRule()
    private val catalog = StationCatalog.bundled()
    private val date = today().plusDays(2)
    private val demand = WaitlistDemand(date, "id-37", "VNP", "JGK", "O")
    private val person = RailwayPassenger("opaque-person", "张**", "•••• 6789", "1", "成人", true, "已核验")
    private val binding = WaitlistBinding("opaque-account", listOf(WaitlistPassenger(person.reference, "1")))
    private val preview = RailwayOrderPreview("preview", binding,
        listOf(RailwayPreparedTrain(demand, "G37", date.atTime(8, 0).atZone(RailwayWaitlistGateway.CHINA).toInstant())),
        listOf(1440, 360, 60), WaitlistLimits(1, 1, 19), Instant.now().plusSeconds(180), false)

    @Test fun confirmationIsReadableAcrossFourThemesAndNeverEnablesUnverifiedSubmit() {
        val trip = Trip(date, "id-37", "G37", catalog.byCode.getValue("VNP"), catalog.byCode.getValue("JGK"),
            LocalTime.of(8, 0), LocalTime.of(10, 0), 120, SaleState.OPEN, "",
            mapOf(SeatType.SECOND to TicketParser.availability("无")), Instant.now(), true, waitlistContextAvailable = true)
        val state = WaitlistUiState(page = WaitlistPage.CONFIRMATION,
            selected = listOf(WaitlistChoice(trip, SeatType.SECOND)),
            account = RailwayAccount(binding.accountReference, "12306 已登录", listOf(person)),
            passengerSelection = setOf(person.reference), orderPreview = preview)
        val theme = mutableStateOf(ThemeChoice.BLUE)
        val source = object : TicketSource {
            override suspend fun initialize() = SourceInfo("query", today(), date, catalog, Instant.now())
            override suspend fun query(unit: QueryUnit): QueryResult = error("No network test")
        }
        val vm = WaitlistViewModel(source, object : WaitlistDraftStore {
            override suspend fun load(): WaitlistFilters? = null
            override suspend fun save(filters: WaitlistFilters) {}
        })
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f)) {
                TrainTripTheme(theme.value) {
                    Box(Modifier.width(320.dp).fillMaxHeight()) { RailwayConfirmationScreen(state, vm) }
                }
            }
        }
        for (value in ThemeChoice.entries) {
            compose.runOnIdle { theme.value = value }
            compose.onNodeWithText("截止兑现时间").performScrollTo().assertExists()
            compose.onNodeWithText("开车前 1 小时").performScrollTo().assertExists()
            compose.onNodeWithTag("railway-order-confirm").performScrollTo().assertIsNotEnabled()
            val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "order-ui-verification")
                .apply { mkdirs() }
            compose.onRoot().captureToImage().asAndroidBitmap().let { image ->
                File(directory, "confirmation-${value.name}.jpg").outputStream().use {
                    image.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it)
                }
            }
            compose.onAllNodes(hasText(""), useUnmergedTree = true).fetchSemanticsNodes().forEach { node ->
                val action = node.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult)
                if (action != null) {
                    val layouts = mutableListOf<TextLayoutResult>()
                    action.action?.invoke(layouts)
                    layouts.forEach { assertFalse("Text clipped", it.hasVisualOverflow) }
                }
            }
        }
    }

    @Test fun verificationResourcePolicyRejectsHttpForeignHostsAndAllOrderOrLoginPaths() {
        assertTrue(riskResourceAllowed(Uri.parse("https://mobile.12306.cn/otsmobile/antcaptcha/ua_rds.js?t=2026093021")))
        assertTrue(riskResourceAllowed(Uri.parse("https://g.alicdn.com/sd/ncpc/nc.js")))
        listOf(
            "http://g.alicdn.com/sd/ncpc/nc.js",
            "https://g.alicdn.com.attacker.example/nc.js",
            "https://kyfw.12306.cn/otn/afterNate/confirmHB",
            "https://kyfw.12306.cn/passport/web/login",
            "https://mobile.12306.cn/otsmobile/login",
            "https://name@g.alicdn.com/nc.js",
            "file:///data/data/cn.traintrip.app/files/session",
        ).forEach { assertFalse(it, riskResourceAllowed(Uri.parse(it))) }
    }
}
