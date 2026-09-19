package cn.traintrip.core

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class CtripGuideSourceTest {
    @Test fun cityDirectoryMatchesChineseIdentityAndOnlyCtripPages() {
        val html="""<a href="http://you.ctrip.com/place/suzhou11.html">苏州旅游攻略</a><a href="/place/suzhou672.html">宿州旅游攻略</a><a href="https://evil.invalid/place/suzhou1.html">苏州</a><a href="https://you.ctrip.com@evil.invalid/place/suzhou1.html">苏州</a>"""
        assertEquals(listOf("https://you.ctrip.com/place/suzhou11.html"),CtripGuideSource.cityLinks(html,"苏州"))
        assertEquals(listOf("https://you.ctrip.com/place/suzhou672.html"),CtripGuideSource.cityLinks(html,"宿州"))
    }
    @Test fun foreignOrWrongProvinceCannotMatchCity() {
        val city=StationCatalog.bundled().cities.first { it.name=="苏州" }
        fun d(province:String,domestic:Boolean=true)=JsonParser.parseString("""{"name":"苏州","parentDistrictName":"$province","isInChina":$domestic}""").asJsonObject
        assertTrue(CtripGuideSource.matchesCity(d("江苏"),city))
        assertFalse(CtripGuideSource.matchesCity(d("安徽"),city))
        assertFalse(CtripGuideSource.matchesCity(d("江苏",false),city))
    }
    @Test fun embeddedDataMissingOrBrokenFailsClearly() {
        assertTrue(runCatching { CtripGuideSource.state("login page") }.isFailure)
        val state=CtripGuideSource.state("""<script id="__NEXT_DATA__" type="application/json">{"props":{"pageProps":{"initialState":{"districtInfo":{"name":"苏州"}}}}}</script>""")
        assertEquals("苏州",state.obj("districtInfo").text("name"))
    }
    @Test fun sourceAndImageUrlsRejectCredentialAndUntrustedHost() {
        assertTrue(GuideNetwork.isPageUrl("https://you.ctrip.com/place/suzhou11.html"))
        assertFalse(GuideNetwork.isPageUrl("https://you.ctrip.com.evil.invalid/a"))
        assertFalse(GuideNetwork.isPageUrl("https://name@you.ctrip.com/a"))
        assertFalse(GuideNetwork.isPageUrl("http://you.ctrip.com/a"))
        assertTrue(GuideNetwork.isPhotoUrl("https://dimg04.c-ctrip.com/images/a.jpg"))
        assertFalse(GuideNetwork.isPhotoUrl("https://evil-c-ctrip.com/a.jpg"))
        assertFalse(GuideNetwork.isPhotoUrl("file:///tmp/a.jpg"))
    }
    @Test fun verificationRedirectStopsBeforeFetchingChallengeAndNeverSendsCredentials() = kotlinx.coroutines.runBlocking {
        var calls=0
        val client=okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
            calls++
            assertNull(chain.request().header("Authorization"))
            okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1)
                .code(302).message("Found").header("Location","https://verify.ctrip.com/static/ctripVerify.html")
                .body(okhttp3.ResponseBody.create(null,byteArrayOf())).build()
        }.build()
        val failure=runCatching { GuideNetwork(client).html("https://you.ctrip.com/place") }.exceptionOrNull()
        assertTrue(failure?.message.orEmpty().contains("网页验证"))
        assertEquals(1,calls)
    }
}
