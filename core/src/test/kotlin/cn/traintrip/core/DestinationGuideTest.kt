package cn.traintrip.core

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.URI
import com.google.gson.Gson
import com.google.gson.JsonParser
import javax.imageio.ImageIO

class DestinationGuideTest {
    @Test fun fiveGuidesMatchStationIdentitiesAndPackageRealPhotos() {
        val catalogue = StationCatalog.bundled()
        val expected = setOf("天津", "济南", "青岛", "大同", "洛阳")
        assertEquals(expected, DestinationGuides.all.map { it.name }.toSet())
        assertEquals(5, DestinationGuides.all.size)
        DestinationGuides.all.forEach { guide ->
            assertEquals(guide.name, catalogue.byCity[guide.cityId]?.name)
            assertSame(guide, DestinationGuides.find(guide.cityId))
            val photo = File("../app/src/main/assets/destinations/${guide.photo.assetName}")
            assertTrue("Missing ${photo.path}", photo.isFile && photo.length() > 1000)
            val bytes = photo.readBytes()
            assertEquals(0xff, bytes[0].toInt() and 255)
            assertEquals(0xd8, bytes[1].toInt() and 255)
            assertNotNull("Undecodable ${photo.path}", ImageIO.read(photo))
        }
        val other = catalogue.cities.first { it.name == "上海" }
        assertNull(DestinationGuides.find(other.id))
        assertNull(DestinationGuides.find("unknown"))
    }

    @Test fun incompleteOrCorruptContentFallsBackWithoutBreakingTicketSearch() {
        listOf("", "null", "not json", "[{}]", "[{\"cityId\":\"x\"}]").forEach {
            assertTrue(DestinationGuides.parse(it.reader()).isEmpty())
        }
    }

    @Test fun itinerariesReferenceOnlyDescribedExperiencesAndRemainIndependentOfDates() {
        assertFalse(DestinationGuides.all.isEmpty())
        DestinationGuides.all.forEach { guide ->
            assertEquals(listOf(1, 2), guide.plans.map { it.days }.sorted())
            val ids = guide.experiences.map { it.id }.toSet()
            guide.plans.forEach { plan ->
                assertEquals(plan.days, plan.schedule.size)
                assertTrue(plan.schedule.flatMap { it.experienceIds }.all { it in ids })
            }
            assertTrue(guide.sources.all { URI(it.url).host.endsWith(".gov.cn") && it.checkedOn == "2026-09-19" })
            assertTrue(URI(guide.photo.sourceUrl).host.endsWith(".gov.cn"))
            assertTrue(guide.photo.credit.isNotBlank())
            assertNull(guide.photo.license)
            assertNull(guide.photo.licenseUrl)
        }
    }

    @Test fun optionalPhotoLicenseAndPublicWebLinksAreValidated() {
        val json = Gson().toJson(DestinationGuides.all)
        val array = JsonParser.parseString(json).asJsonArray
        val guide = array[0].asJsonObject
        val source = guide["sources"].asJsonArray[0].asJsonObject
        val photo = guide["photo"].asJsonObject
        source.addProperty("url", "http://example.gov.cn/guide")
        assertEquals(5, DestinationGuides.parse(array.toString().reader()).size)
        photo.addProperty("license", "CC BY 4.0")
        assertTrue(DestinationGuides.parse(array.toString().reader()).isEmpty())
        photo.addProperty("licenseUrl", "https://creativecommons.org/licenses/by/4.0/")
        assertEquals(5, DestinationGuides.parse(array.toString().reader()).size)
        photo.remove("license")
        assertTrue(DestinationGuides.parse(array.toString().reader()).isEmpty())
        photo.remove("licenseUrl")
        listOf("javascript:alert(1)", "https:///missing-host", "https://name@example.gov.cn/guide").forEach {
            source.addProperty("url", it)
            assertTrue("Accepted invalid URL: $it", DestinationGuides.parse(array.toString().reader()).isEmpty())
        }
    }
}
