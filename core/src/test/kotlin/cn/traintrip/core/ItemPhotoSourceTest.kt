package cn.traintrip.core

import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class ItemPhotoSourceTest {
    private val city=StationCatalog.bundled().cities.first { it.name=="保定" }
    private val food=PhotoSubject("food","驴肉火烧")
    private val place=PhotoSubject("place","直隶总督署")
    private fun photo(s:PhotoSubject,index:Int)=sourcedPhoto("https://dimg04.c-ctrip.com/images/$index.jpg","保定 · ${s.name}","https://you.ctrip.com/a","test",s)
    private fun guide()=DestinationGuides.all.first().copy(cityId=city.id,name=city.name,
        experiences=listOf(DestinationExperience("p1",place.name,"保定景点","1小时","保定")),foods=listOf(DestinationFood(food.name,"保定美食"))).withPhotos(listOf(photo(place,9)))
    @Test fun foodSearchSkipsExistingPlaceAndRejectsRestaurantEnvironmentAndWrongCity()=runBlocking {
        MockWebServer().use { server ->
            val images = (1..3).map { index -> mapOf("Title" to "保定驴肉火烧成品实拍照片", "Url" to "https://www.hebei.gov.cn/food.html",
                "Image" to mapOf("Url" to photo(food,index).remoteUrl, "Width" to 900, "Height" to 600)) } + listOf(
                mapOf("Title" to "保定驴肉火烧餐厅环境", "Url" to "https://www.hebei.gov.cn/food.html", "Image" to mapOf("Url" to photo(food,5).remoteUrl, "Width" to 900, "Height" to 600)),
                mapOf("Title" to "北京驴肉火烧成品照片", "Url" to "https://www.hebei.gov.cn/food.html", "Image" to mapOf("Url" to photo(food,6).remoteUrl, "Width" to 900, "Height" to 600)))
            server.enqueue(MockResponse().setBody(Gson().toJson(mapOf("ResponseMetadata" to emptyMap<String,Any>(), "Result" to mapOf("ImageResults" to images)))))
            val source=DoubaoGuideSource({"doubao-test-key-123456789"},server.url("/search").toString(),OkHttpClient())
            val result=source.fetch(city,guide()) {}
            assertEquals(1,server.requestCount)
            val query=server.takeRequest().body.readUtf8()
            assertTrue(query.contains("保定驴肉火烧"));assertFalse(query.contains("直隶总督署"))
            assertEquals(3,result.photos.size);assertTrue(result.photos.all { it.subject==food })
            assertEquals(3,GuidePhotoPolicy.select(guide(),result.photos).size)
        }
    }
    @Test fun fullyIllustratedGuideNeverCallsPaidSearch()=runBlocking {
        MockWebServer().use { server ->
            val source=DoubaoGuideSource({"doubao-test-key-123456789"},server.url("/search").toString(),OkHttpClient())
            assertTrue(source.fetch(city,guide().withPhotos(listOf(photo(place,9),photo(food,1)))) {}.photos.isEmpty())
            assertEquals(0,server.requestCount)
        }
    }
}
