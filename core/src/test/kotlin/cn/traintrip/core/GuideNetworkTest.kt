package cn.traintrip.core

import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class GuideNetworkTest {
    private fun client(reply: (Request) -> Response) = OkHttpClient.Builder().addInterceptor { reply(it.request()) }.build()
    private fun response(request: Request, bytes: ByteArray, type: String) = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").header("Content-Type", type)
        .body(bytes.toResponseBody(type.toMediaType())).build()

    @Test fun htmlUsesDesktopUserAgentAndImagesRemainCredentialFree() = runBlocking {
        val requests = mutableListOf<Request>()
        val network = GuideNetwork(client { request ->
            requests += request
            response(request, byteArrayOf(1), if (request.url.host == "you.ctrip.com") "text/html" else "image/jpeg")
        })
        network.html("https://you.ctrip.com/place")
        network.photo("https://dimg04.ctrip.com/a.jpg")
        network.photo("https://p11-volcsearch-sign.byteimg.com/a.jpeg?x-signature=test")
        assertEquals("Mozilla/5.0", requests[0].header("User-Agent"))
        assertTrue(requests[1].header("User-Agent")!!.contains("Android"))
        assertTrue(requests.all { it.header("Authorization") == null && it.header("Cookie") == null })
    }

    @Test fun searchImageCdnAllowsOnlyOfficialHttpsHosts() {
        assertTrue(GuideNetwork.isPhotoUrl("https://p26-volcsearch-sign.byteimg.com/a.jpeg?x-expires=123"))
        for (url in listOf("http://p11-volcsearch-sign.byteimg.com/a.jpg", "https://p11-volcsearch-sign.byteimg.com.evil.test/a.jpg",
            "https://evil.byteimg.com/a.jpg", "https://p11-volcsearch-sign.byteimg.com:8443/a.jpg", "https://user:secret@p11-volcsearch-sign.byteimg.com/a.jpg")) {
            assertFalse(url, GuideNetwork.isPhotoUrl(url))
        }
    }

    @Test fun photosAllowSixMiBButEnforceTenMiBCapAndHtmlKeepsFiveMiB() = runBlocking {
        val six = ByteArray(6 * 1024 * 1024)
        val network = GuideNetwork(client { response(it, six, "image/jpeg") })
        assertEquals(six.size, network.photo("https://dimg.c-ctrip.com/a.jpg").size)
        assertTrue(runCatching { network.html("https://you.ctrip.com/place") }.isFailure)
        val large = GuideNetwork(client { response(it, ByteArray(10 * 1024 * 1024 + 1), "image/jpeg") })
        assertTrue(runCatching { large.photo("https://dimg.c-ctrip.com/a.jpg") }.isFailure)
    }

    @Test fun verificationAndUntrustedRedirectsStopBeforeAnotherRequest() = runBlocking {
        for (target in listOf("https://verify.ctrip.com/", "https://you.ctrip.com.evil.test/", "http://you.ctrip.com/place")) {
            var calls = 0
            val network = GuideNetwork(client { request ->
                calls++
                response(request, byteArrayOf(), "text/html").newBuilder().code(302).header("Location", target).build()
            })
            assertTrue(runCatching { network.html("https://you.ctrip.com/place") }.isFailure)
            assertEquals(1, calls)
        }
        val invalid = GuideNetwork(client { response(it, "verification page".toByteArray(), "text/html") })
        assertTrue(runCatching { invalid.photo("https://dimg.c-ctrip.com/a.jpg") }.isFailure)
    }
}
