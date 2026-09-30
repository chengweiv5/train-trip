package cn.traintrip.app

import androidx.test.platform.app.InstrumentationRegistry
import cn.traintrip.core.*
import cn.traintrip.core.waitlist.*
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in anonymous GET smoke; never authenticates, reads passengers, or creates orders. */
class WaitlistLiveQueryTest {
    @Test fun anonymousSeatRestrictionsParseOnOfficialQuery() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("waitlistLive") == "true")
        val source = OfficialTicketSource()
        val info = source.initialize()
        val filters = WaitlistFilters(originStations = setOf("VNP"), destinationStations = setOf("JGK"),
            startDate = today().plusDays(1), onlyAvailable = false)
        val plan = filters.plan(info)
        assertEquals(1, plan.size)
        val result = source.query(plan.single())
        assertTrue("Anonymous query must return a parsed success, not failure or unopened", result is QueryResult.Success)
        val choices = filters.choices((result as QueryResult.Success).trips)
        val report = "receivedAt=${result.receivedAt}\nroute=${info.queryPath}\nsale=${info.saleStart}..${info.saleEnd}\n" +
            "query=${plan.single().date}/VNP/JGK\nreturnedTrips=${result.trips.size}\nexactRouteChoices=${choices.size}\n" +
            WaitlistEligibility.entries.joinToString("\n") { "${it.name}=${choices.count { c -> c.eligibility == it }}" }
        File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("v2.0-integration"),
            "live-query.txt").writeText(report)
    }
}
