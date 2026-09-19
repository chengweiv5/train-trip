package cn.traintrip.core

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class DestinationCatalogTest {
    private val c=StationCatalog.bundled()
    @Test fun municipalitiesAndCountiesNormalizeWithoutLosingStations() {
        for(id in listOf("11","12","31","50")) assertEquals(listOf(id+"0000"),c.cities.filter { it.province.id==id }.map { it.id })
        assertEquals("500000",c.resolveCityId("3102"))
        assertEquals("330100",c.resolveCityId("0914"))
        assertEquals("370100",c.resolveCityId("0615"))
        assertEquals("433100",c.resolveCityId("1402"))
        assertTrue(c.byCity.getValue("500000").stations.any { it.name=="万州" })
        assertEquals("469026",c.resolveCityId("2519"))
        assertEquals("540400",c.byCode.getValue("GAO").cityId)
        assertEquals("659002",c.byCode.getValue("AOR").cityId)
        assertTrue(c.stations.size>3300)
        assertTrue(c.unmappedStations.isEmpty())
    }
    @Test fun migrationKeepsFiltersAndMergesDuplicateDestinations() {
        val old=SearchFilters(originCityId="0357",originStations=setOf("VNP"),people=3,startMinute=1320,endMinute=360,
            destinationCityIds=setOf("1717","3102","0357","0914","missing"))
        val result=c.normalize(old)
        assertEquals(old.copy(originCityId="110000",destinationCityIds=setOf("500000","330100")),result)
        val split=c.normalize(old.copy(originCityId="2409",originStations=setOf("AOR"),destinationCityIds=setOf("2409","2407")))
        assertEquals("659002",split.originCityId)
        assertEquals(setOf("AOR"),split.originStations)
        assertEquals(setOf("652900","652700","659007"),split.destinationCityIds)
    }
    @Test fun provinceSearchIncludesEntireDirectoryAndPinyinSearchWorks() {
        val hebei=c.cities.filter { it.province.id=="13" }
        assertEquals(11,hebei.size)
        assertEquals(hebei,c.searchDestinations("河北"))
        assertEquals(hebei,c.searchDestinations("hebeisheng"))
        assertEquals("350200",c.searchDestinations("xiamen").single().id)
        assertEquals(hebei,c.searchDestinations(" He Bei "))
        assertEquals(listOf("130100"),c.searchDestinations("shijiazhuang").map { it.id })
        assertTrue(c.searchDestinations("青海").any { !it.supported })
        assertEquals("chongqing",c.provinces.first { it.id=="50" }.pinyin)
    }
    @Test fun queryPlanRetainsMergedRailwayCoverageAndDoesNotDuplicate() {
        val f=SearchFilters(destinationCityIds=setOf("500000","370100"))
        val plan=c.plan(f)
        assertTrue(plan.any { it.destination.name=="万州" })
        assertTrue(plan.any { it.destination.name=="莱芜西" })
        assertEquals(plan.size,plan.map { it.key }.distinct().size)
        assertTrue(plan.all { it.destination.cityId in f.destinationCityIds })
    }
    @Test fun provinceSortAndPartialStatesStaySeparate() {
        val date=today().plusDays(1)
        val f=SearchFilters(startDate=date,destinationCityIds=setOf("130100","370100"))
        val plan=c.plan(f)
        val t=Trip(date,"1","G1",c.byCode.getValue("BJP"),c.byCode.getValue("SJP"),LocalTime.NOON,LocalTime.of(13,0),60,SaleState.OPEN,"",mapOf(SeatType.SECOND to TicketParser.availability("有")),Instant.now())
        val p=SearchProgress(plan,mapOf(plan.first { it.destination.cityId=="130100" }.key to QueryResult.Success(listOf(t),Instant.now())),running=true)
        val groups=groupResults(c,f,p)
        assertEquals(listOf("13","37"),groups.map { it.province.id })
        assertEquals(1,groups.first().cities.size)
        assertTrue(groups.last().status.contains("仍在查询"))
        val allFailed=p.copy(outcomes=plan.associate { it.key to QueryResult.Failure("network") },running=false)
        assertTrue(groupResults(c,f,allFailed).all { it.status.contains("失败") && it.incomplete })
        val unopened=p.copy(outcomes=plan.associate { it.key to QueryResult.NotOnSale("not yet") },running=false)
        assertTrue(groupResults(c,f,unopened).all { it.status.contains("尚未开售") })
        val empty=p.copy(outcomes=plan.associate { it.key to QueryResult.Success(emptyList(),Instant.now()) },running=false)
        assertTrue(groupResults(c,f,empty).all { it.status.contains("暂无符合") && !it.incomplete })
        val multiPerson=groupResults(c,f.copy(people=2),p)
        assertEquals(listOf("130100"),multiPerson.flatMap { it.cities }.map { it.cityId })
        assertTrue(multiPerson.last().status.contains("仍在查询"))
    }
    @Test fun splitRailwayGroupAcceptsProtocolButOnlyIncludesSelectedAdministrativeCity() {
        val day=today().plusDays(1)
        val unit=QueryUnit(day,c.byCode.getValue("BJP"),c.byCode.getValue("ASR"))
        val fields=MutableList(56) { "" }
        fields[2]="same-railway-group";fields[3]="K1";fields[6]="BJP";fields[7]="AOR"
        fields[8]="12:00";fields[9]="13:00";fields[10]="01:00";fields[11]="Y";fields[30]="8"
        val payload="{\"status\":true,\"data\":{\"result\":[\"${fields.joinToString("|")}\"]}}"
        val result=TicketParser.parse(payload,unit,c,Instant.now()) as QueryResult.Success
        assertEquals("659002",result.trips.single().to.cityId)
        val filters=SearchFilters(startDate=day,destinationCityIds=setOf("652900"))
        assertTrue(aggregate(result.trips,filters).isEmpty())
        assertEquals("659002",aggregate(result.trips,filters.copy(destinationCityIds=setOf("659002"))).single().cityId)
    }
    @Test fun unrecognizedLiveGroupIsQuarantined() {
        val next=StationCatalog(listOf(Station("ZZZ","新站","new-city","新城市","xin")))
        assertEquals(1,next.unmappedStations.size)
        assertFalse(next.cities.any { it.name=="新城市" })
    }
}
