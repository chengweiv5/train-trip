package cn.traintrip.core

import com.google.gson.GsonBuilder
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import java.io.File
import java.util.concurrent.TimeUnit

/** Manual live verification. Keys arrive through stdin; outputs contain no request headers. */
object LiveRouteRepairSmoke {
    @JvmStatic fun main(args: Array<String>) = runBlocking {
        val key = requireNotNull(readlnOrNull()).trim()
        val gson = GsonBuilder().setPrettyPrinting().create()
        val directory = File(args.last()).apply { mkdirs() }
        var modelCalls = 0
        val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .retryOnConnectionFailure(false).readTimeout(90, TimeUnit.SECONDS).callTimeout(120, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request()
                if (request.url.host == "api.deepseek.com") {
                    modelCalls++
                    val response = if (args[0].startsWith("replay") && modelCalls == 1) {
                        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("Recorded response")
                            .body(File(args[2]).readText().toResponseBody("application/json".toMediaType())).build()
                    } else chain.proceed(request)
                    File(directory, "model-response-$modelCalls.json").writeText(response.peekBody(512 * 1024).string())
                    response
                } else chain.proceed(request)
            }.build()
        try {
            val material = if (args[0].startsWith("replay")) gson.fromJson(File(args[1]).readText(), GuideMaterial::class.java)
            else {
                val searchKey = requireNotNull(readlnOrNull()).trim()
                val city = StationCatalog.bundled().cities.first { it.name == args[1] }
                DoubaoGuideSource({ searchKey }, DoubaoGuideSource.ENDPOINT, client).fetch(city) { println(it) }
            }
            File(directory, "material.json").writeText(gson.toJson(material))
            val previous = if (args[0].endsWith("-refresh"))
                gson.fromJson(File(args[args.size - 2]).readText(), DestinationGuide::class.java) else null
            val guide = DeepSeekGuideGenerator("https://api.deepseek.com/chat/completions", client).refresh(material, key, previous)
            File(directory, "guide.json").writeText(gson.toJson(guide))
            require(guide.plans.isNotEmpty()) { "No grounded routes generated" }
            guide.plans.forEach { p -> p.schedule.forEach { day ->
                val doc = material.documents.first { it.url == day.sourceUrl }
                require(doc.content.replace(Regex("\\s+"), "").contains(requireNotNull(day.evidence).replace(Regex("\\s+"), "")))
                require(day.experienceIds.all { id -> guide.experiences.any { it.id == id } })
            } }
            val summary = mapOf("mode" to args[0], "places" to guide.experiences.size, "foods" to guide.foods.size,
                "plans" to guide.plans.size, "modelCalls" to modelCalls, "evidenceVerified" to true)
            File(directory, "summary.json").writeText(gson.toJson(summary))
            println(gson.toJson(summary))
        } finally { client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
    }
}
