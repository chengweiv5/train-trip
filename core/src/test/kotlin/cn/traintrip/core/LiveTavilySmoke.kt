package cn.traintrip.core

import com.google.gson.GsonBuilder
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.LocalDate

/** Manual verification; credentials only arrive via stdin, never command arguments or files. */
object LiveTavilySmoke {
    @JvmStatic fun main(args: Array<String>) = runBlocking {
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
                TavilyGuideSource.parse(File("${args[2]}-$kind.json").readText(),city,kind).take(5)
            }.mapIndexed { i,d -> d.copy(id="s${i+1}") }
            GuideMaterial(city.id,city.name,city.province.name,emptyList(),emptyList(),documents.distinctBy { it.url }
                .map { GuideSource(it.title,it.url,LocalDate.now(BEIJING_ZONE).toString()) },documents)
        } else {
            val searchKey = requireNotNull(readlnOrNull()).trim()
            TavilyGuideSource { searchKey }.fetch(city) { println(it) }
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
