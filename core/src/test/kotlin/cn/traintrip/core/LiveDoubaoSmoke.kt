package cn.traintrip.core

import com.google.gson.GsonBuilder
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.LocalDate

/** Manual verification; credentials only arrive via stdin, never command arguments or files. */
object LiveDoubaoSmoke {
    @JvmStatic fun main(args: Array<String>) = runBlocking {
        if (args.size == 3 && args[0] == "verify") {
            val deepSeekKey = requireNotNull(readlnOrNull()).trim()
            val searchKey = requireNotNull(readlnOrNull()).trim()
            val city = StationCatalog.bundled().cities.first { it.name == args[1] }
            val directory = File(args[2]).apply { mkdirs() }
            val gson = GsonBuilder().setPrettyPrinting().create()
            val calls = mutableListOf<Map<String, Any>>()
            val client = okhttp3.OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
                .retryOnConnectionFailure(false).readTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
                .callTimeout(120, java.util.concurrent.TimeUnit.SECONDS).addInterceptor { chain ->
                    val request = chain.request()
                    val started = System.nanoTime()
                    val response = chain.proceed(request)
                    if (request.url.host == "api.deepseek.com" && response.isSuccessful)
                        File(directory, "model-response.json").writeText(response.peekBody(512 * 1024).string())
                    calls += mapOf("host" to request.url.host, "status" to response.code,
                        "elapsedMs" to (System.nanoTime() - started) / 1_000_000)
                    response
                }.build()
            try {
                val source = DoubaoGuideSource({ searchKey }, DoubaoGuideSource.ENDPOINT, client)
                val material = source.fetch(city) { println(it) }
                File(directory, "material.json").writeText(gson.toJson(material))
                println("documents=${material.documents.size}, sources=${material.sources.size}")
                val guide = DeepSeekGuideGenerator("https://api.deepseek.com/chat/completions", client).generate(material, deepSeekKey)
                File(directory, "guide.json").writeText(gson.toJson(guide))
                println("generated city=${city.name}, places=${guide.experiences.size}, foods=${guide.foods.size}")
                require(guide.experiences.all { item -> material.documents.any { it.kind == "places" && it.url == item.sourceUrl &&
                    it.content.replace(Regex("\\s+"), "").contains(requireNotNull(item.evidence).replace(Regex("\\s+"), "")) } })
                require(guide.foods.all { item -> material.documents.any { it.kind == "food" && it.url == item.sourceUrl &&
                    it.content.replace(Regex("\\s+"), "").contains(requireNotNull(item.evidence).replace(Regex("\\s+"), "")) } })
                val photos = source.fetch(city, guide) { println(it) }
                File(directory, "photos.json").writeText(gson.toJson(photos))
                val selected = GuidePhotoPolicy.select(guide, photos.photos)
                val downloaded = mutableListOf<Map<String, Any>>()
                for ((index, photo) in selected.take(3).withIndex()) {
                    val bytes = GuideNetwork(client).photo(requireNotNull(photo.remoteUrl))
                    val decoded = requireNotNull(javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(bytes)))
                    File(directory, "image-${index + 1}.jpg").writeBytes(bytes)
                    downloaded += mapOf("subject" to requireNotNull(photo.subject).name, "bytes" to bytes.size,
                        "width" to decoded.width, "height" to decoded.height)
                }
                val summary = mapOf("city" to city.name, "documents" to material.documents.size,
                    "places" to guide.experiences.size, "foods" to guide.foods.size, "evidenceVerified" to true,
                    "photoCandidates" to photos.photos.size, "photoSourceFailed" to photos.failed,
                    "selectedPhotos" to selected.size, "downloaded" to downloaded, "calls" to calls)
                File(directory, "summary.json").writeText(gson.toJson(summary))
                println(gson.toJson(summary))
            } finally { client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
            return@runBlocking
        }
        if (args.size == 4 && args[0] == "decode") {
            val gson=GsonBuilder().setPrettyPrinting().create()
            val material=gson.fromJson(File(args[1]).readText(),GuideMaterial::class.java)
            val response=com.google.gson.JsonParser.parseString(File(args[2]).readText()).asJsonObject
            val guide=SearchGuideDecoder.decode(response.objects("choices").first().obj("message").text("content"),material)
            File(args[3]).writeText(gson.toJson(guide))
            println("decoded city=${guide.name}, experiences=${guide.experiences.size}, foods=${guide.foods.size}, plans=${guide.plans.size}")
            return@runBlocking
        }
        if (args.size == 3 && args[0] == "generate") {
            val key=requireNotNull(readlnOrNull()).trim()
            val gson=GsonBuilder().setPrettyPrinting().create()
            val material=gson.fromJson(File(args[1]).readText(),GuideMaterial::class.java)
            val client=okhttp3.OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
                .readTimeout(90,java.util.concurrent.TimeUnit.SECONDS).callTimeout(120,java.util.concurrent.TimeUnit.SECONDS)
                .addInterceptor { chain -> val response=chain.proceed(chain.request());
                    if(response.isSuccessful) File("${args[2]}.response.json").writeText(response.peekBody(512*1024).string());response }.build()
            val guide=try { DeepSeekGuideGenerator("https://api.deepseek.com/chat/completions",client).generate(material,key) }
                finally { client.dispatcher.executorService.shutdown();client.connectionPool.evictAll() }
            File(args[2]).writeText(gson.toJson(guide))
            println("generated city=${guide.name}, experiences=${guide.experiences.size}, foods=${guide.foods.size}, plans=${guide.plans.size}, photo=${guide.photo!=null}")
            return@runBlocking
        }
        require(args.size in 2..3)
        val deepSeekKey = requireNotNull(readlnOrNull()).trim()
        val city = StationCatalog.bundled().cities.first { it.name == args[0] }
        val material = if (args.size == 3) {
            val documents = listOf("places","food").flatMap { kind ->
                DoubaoGuideSource.parse(File("${args[2]}-$kind.json").readText(),city,kind).take(5)
            }.mapIndexed { i,d -> d.copy(id="s${i+1}") }
            GuideMaterial(city.id,city.name,city.province.name,emptyList(),emptyList(),documents.distinctBy { it.url }
                .map { GuideSource(it.title,it.url,LocalDate.now(BEIJING_ZONE).toString()) },documents)
        } else {
            val searchKey = requireNotNull(readlnOrNull()).trim()
            DoubaoGuideSource { searchKey }.fetch(city) { println(it) }
        }
        val gson = GsonBuilder().setPrettyPrinting().create()
        File("${args[1]}.material.json").writeText(gson.toJson(material))
        println("documents=${material.documents.size}, sources=${material.sources.size}")
        val client = okhttp3.OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .retryOnConnectionFailure(false).readTimeout(90,java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(120,java.util.concurrent.TimeUnit.SECONDS).addInterceptor { chain ->
                val response = chain.proceed(chain.request())
                if(response.isSuccessful) File("${args[1]}.response.json").writeText(response.peekBody(512*1024).string())
                response
            }.build()
        val guide = DeepSeekGuideGenerator("https://api.deepseek.com/chat/completions",client).generate(material,deepSeekKey)
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
        File(args[1]).writeText(gson.toJson(guide))
        println("city=${guide.name}, experiences=${guide.experiences.size}, foods=${guide.foods.size}, plans=${guide.plans.size}, photo=${guide.photo!=null}")
    }
}
