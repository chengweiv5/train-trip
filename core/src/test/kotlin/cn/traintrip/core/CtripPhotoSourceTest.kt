package cn.traintrip.core

import com.google.gson.Gson
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class CtripPhotoSourceTest {
    private val city = StationCatalog.bundled().cities.first { it.name == "保定" }
    private val page = "https://you.ctrip.com/place/baoding459.html"
    private val detail = "https://you.ctrip.com/sight/baoding459/16580.html"
    private val image = "https://dimg04.com.invalid/photo.jpg"
    private val photo = "https://dimg04.c-ctrip.com/images/photo.jpg"
    private fun guide(place:String="直隶总督署")=DestinationGuides.all.first().copy(cityId=city.id,name=city.name,
        experiences=listOf(DestinationExperience("p1",place,"保定景点介绍","1小时","保定",detail)),generatedAt="2026-09-22T00:00:00Z",model="test").withPhotos(emptyList())
    private fun html(state:Map<String,Any>)="<script id=\"__NEXT_DATA__\">${Gson().toJson(mapOf("props" to mapOf("pageProps" to mapOf("initialState" to state))))}</script>"
    private fun pages(parent:String="河北",district:String="459",address:String="保定市莲池区",id:String="79303",name:String="直隶总督署",cover:String="")=mapOf(
        "https://you.ctrip.com/place" to "<a href='$page'>保定旅游攻略</a>",
        page to html(mapOf("districtInfo" to mapOf("name" to "保定","parentDistrictName" to parent,"districtId" to "459","isInChina" to true,"coverImage" to cover),
            "moduleList" to listOf(mapOf("name" to "mustDo","mustDoModule" to mapOf("mustDoTabList" to listOf(mapOf("tabType" to "SIGHT","poiList" to listOf(mapOf("poiId" to "79303","name" to name,"jumpUrl" to detail,"coverImage" to photo))))))))),
        detail to html(mapOf("poiDetail" to mapOf("poiId" to id,"poiName" to name,"districtId" to district,"address" to address,"imageInfo" to mapOf("poiPhotoImageList" to listOf(mapOf("imageUrl" to photo),mapOf("imageUrl" to image),mapOf("imageUrl" to "https://dimg04.c-ctrip.com/logo.jpg"))))))
    )
    @Test fun structuredImagesNeedNoDescriptionAndKeepOriginalPage()=runBlocking {
        val pages=pages();val result=CtripPhotoSource { pages.getValue(it) }.fetch(city,guide()) {}
        assertFalse(result.failed);assertEquals(1,result.photos.size)
        assertEquals(photo,result.photos.single().remoteUrl);assertEquals(detail,result.photos.single().sourceUrl)
    }
    @Test fun wrongProvinceWrongIdAndNearbyCityAreExcluded()=runBlocking {
        for(pages in listOf(pages(parent="山西"),pages(id="999"),pages(district="412",address="安阳市文峰区"))) {
            assertTrue(CtripPhotoSource { pages.getValue(it) }.fetch(city,guide()) {}.photos.isEmpty())
        }
    }
    @Test fun countyAddressAndParenthesizedAliasAreAccepted()=runBlocking {
        val pages=pages(district="2549",address="河北省保定市涞源县",name="莲池书院博物馆(古莲花池)")
        assertEquals(1,CtripPhotoSource { pages.getValue(it) }.fetch(city,guide("古莲花池")) {}.photos.size)
        assertFalse(CtripPhotoSource.samePlace("山", "白石山"))
        assertFalse(CtripPhotoSource.samePlace("故宫", "故宫博物院附近餐厅"))
    }
    @Test fun cityCoverIsHonestFallbackWhenNoPlaceMatches()=runBlocking {
        val pages=pages(cover=photo);val result=CtripPhotoSource { pages.getValue(it) }.fetch(city,guide("不在此页的景点")) {}
        assertEquals(1,result.photos.size);assertEquals(page,result.photos.single().sourceUrl)
        assertTrue(result.photos.single().description.contains("城市资料页配图"))
    }
    @Test fun failuresReportAndCancellationPropagates()=runBlocking {
        assertTrue(CtripPhotoSource { throw java.io.IOException() }.fetch(city,guide()) {}.failed)
        try { CtripPhotoSource { throw CancellationException() }.fetch(city,guide()) {};fail() } catch(_:CancellationException) {}
    }
}
