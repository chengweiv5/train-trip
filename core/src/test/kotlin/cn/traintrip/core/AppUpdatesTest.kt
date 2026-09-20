package cn.traintrip.core

import kotlinx.coroutines.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class AppUpdatesTest {
    private fun release(version:String,draft:Boolean=false,pre:Boolean=false,assets:Boolean=true,url:String="https://github.com/chengweiv5/train-trip/releases/tag/v$version")="""{"tag_name":"v$version","html_url":"$url","draft":$draft,"prerelease":$pre,"body":"新增收藏\n优化离线","assets":${if(assets)"""[{"name":"train-trip-v$version-release.apk"},{"name":"train-trip-v$version-release.apk.sha256"}]""" else "[]"}}"""
    @Test fun semanticVersionsAndStableReleaseSelection()=runBlocking {
        assertTrue(ReleaseVersion.parse("0.10.0")!!>ReleaseVersion.parse("v0.9.1")!!)
        assertNull(ReleaseVersion.parse("0.5.0-rc1"))
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("[${release("0.5.0")},${release("0.9.0",pre=true)},${release("0.8.0",draft=true)}]").setHeader("Link","<${server.url("/releases?page=2")}>; rel=\"next\""))
            server.enqueue(MockResponse().setBody("[${release("0.10.0")} ]"))
            val latest=GitHubUpdateSource(server.url("/releases").toString()).latest()
            assertEquals(ReleaseVersion(0,10,0),latest.version);assertTrue(latest.downloadable)
            assertEquals(2,server.requestCount)
            assertNull(server.takeRequest().getHeader("Authorization"))
        }
    }
    @Test fun missingAssetsStayVisibleButNotDownloadable()=runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("[${release("0.5.0",assets=false)}]"))
            assertFalse(GitHubUpdateSource(server.url("/releases").toString()).latest().downloadable)
        }
    }
    @Test fun errorsAndUntrustedLinksNeverProduceLatestSuccess()=runBlocking {
        for(body in listOf("[]","not json","[${release("0.5.0",url="https://example.com/release")}]")) {
            MockWebServer().use { server -> server.enqueue(MockResponse().setBody(body));assertTrue(runCatching { GitHubUpdateSource(server.url("/releases").toString()).latest() }.isFailure) }
        }
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(429).setBody("private response"))
            val error=runCatching { GitHubUpdateSource(server.url("/releases").toString()).latest() }.exceptionOrNull()
            assertNotNull(error);assertFalse(error!!.message.orEmpty().contains("private response"))
        }
    }
    @Test fun failedLaterPageDoesNotClaimLatest()=runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("[${release("0.5.0")}]").setHeader("Link","<${server.url("/releases?page=2")}>; rel=\"next\""))
            server.enqueue(MockResponse().setResponseCode(503))
            assertTrue(runCatching { GitHubUpdateSource(server.url("/releases").toString()).latest() }.isFailure)
        }
    }
    @Test fun foreignPaginationIsRejectedBeforeAnyExternalRequest()=runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("[]").setHeader("Link","<https://example.com/releases?page=2>; rel=\"next\""))
            assertTrue(runCatching { GitHubUpdateSource(server.url("/releases").toString()).latest() }.isFailure)
            assertEquals(1,server.requestCount)
        }
    }
    @Test fun cancellationStopsReadingSlowBody()=runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("[]").setBodyDelay(3,java.util.concurrent.TimeUnit.SECONDS))
            val task=launch { GitHubUpdateSource(server.url("/releases").toString()).latest() }
            withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(2,java.util.concurrent.TimeUnit.SECONDS)) }
            val start=System.nanoTime()
            task.cancelAndJoin()
            assertTrue((System.nanoTime()-start)/1_000_000<1000)
        }
    }

}
