package cn.traintrip.core

import com.google.gson.GsonBuilder
import kotlinx.coroutines.runBlocking
import java.io.File

/** Manual, opt-in smoke check. Credential is supplied over stdin, never argv or a file. */
object LiveDestinationSmoke {
    @JvmStatic fun main(args: Array<String>) = runBlocking {
        require(args.size in 2..3)
        val key = requireNotNull(readlnOrNull()).trim()
        val city = StationCatalog.bundled().cities.first { it.name == args[0] }
        val source = if (args.size == 3) CtripGuideSource { url ->
            // Optional captured public pages isolate model verification from source availability.
            val snapshots = GsonBuilder().create().fromJson(File(args[2]).readText(), com.google.gson.JsonObject::class.java)
            val path = snapshots[url]?.asString ?: throw java.io.IOException("No captured page")
            File(path).readText()
        } else CtripGuideSource()
        val material = source.fetch(city) { println(it) }
        println("sources=${material.sources.size}, places=${material.places.size}, foods=${material.foods.size}")
        val guide = DeepSeekGuideGenerator().generate(material,key)
        File(args[1]).writeText(GsonBuilder().setPrettyPrinting().create().toJson(guide))
        println("generated city=${guide.cityId}, model=${guide.model}, places=${guide.experiences.size}, foods=${guide.foods.size}, plans=${guide.plans.size}, photo=${guide.photo != null}")
    }
}
