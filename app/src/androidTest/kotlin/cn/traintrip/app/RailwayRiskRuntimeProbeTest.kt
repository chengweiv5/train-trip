package cn.traintrip.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import cn.traintrip.app.ui.RailwayRiskVerification
import cn.traintrip.app.ui.TrainTripTheme
import cn.traintrip.core.waitlist.RailwayRiskChallenge
import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Opt-in anonymous widget probe. Never runs by default and has no account/order transport. */
class RailwayRiskRuntimeProbeTest {
    @get:Rule val compose = createComposeRule()
    @Test fun officialRuntimeLoadsOrFailsClosedWithoutCredentialsOrOrderRequest() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("railwayRiskProbe") == "true")
        val outcome = mutableStateOf("WAITING")
        compose.setContent {
            TrainTripTheme {
                Box(Modifier.fillMaxWidth()) {
                    if (outcome.value == "WAITING")
                        RailwayRiskVerification(RailwayRiskChallenge("anonymous-runtime-probe", false, ""),
                            { outcome.value = "RUNTIME_AVAILABLE" }, { outcome.value = "RUNTIME_UNAVAILABLE" })
                }
            }
        }
        compose.waitUntil(45_000) { outcome.value != "WAITING" }
        assertTrue(outcome.value in setOf("RUNTIME_AVAILABLE", "RUNTIME_UNAVAILABLE"))
        // Never export json_ua, session IDs or signatures. The status is the only output.
        File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "railway-risk-probe.txt")
            .writeText("status=${outcome.value}\n")
    }
}
