package cn.traintrip.core

import com.google.gson.GsonBuilder
import kotlinx.coroutines.runBlocking
import java.io.File
import javax.imageio.ImageIO

/** Manual, credential-free verification of the public photo source. */
object LivePhotoSmoke {
    @JvmStatic fun main(args:Array<String>):Unit=runBlocking {
        val output=File(args.single()).apply { mkdirs() }
        val source=CtripPhotoSource { url ->
            try { GuideNetwork().html(url).also { println("html $url bytes=${it.length}") } }
            catch(e:Exception) { println("html failed $url: ${e.message}");throw e }
        }
        val cases=mapOf("邯郸" to "邯郸市博物馆","保定" to "直隶总督署","承德" to "避暑山庄")
        val records=mutableListOf<Map<String,Any>>()
        for((name,place) in cases) {
            val city=StationCatalog.bundled().cities.first { it.name==name }
            val guide=DestinationGuides.all.first().copy(cityId=city.id,name=name,
                experiences=listOf(DestinationExperience("p1",place,"验收仅验证图片来源，不生成介绍正文。","1小时",name)),
                generatedAt="2026-09-22T00:00:00Z",model="source-verification").withPhotos(emptyList())
            val result=source.fetch(city,guide) { println(it) }
            check(result.photos.isNotEmpty()) { "$name no photos" }
            val dimensions=mutableListOf<String>()
            for((index,photo) in GuidePhotoPolicy.select(guide,result.photos).withIndex()) {
                val bytes=GuideNetwork().photo(requireNotNull(photo.remoteUrl))
                val bitmap=requireNotNull(ImageIO.read(bytes.inputStream()))
                check(bitmap.width>100 && bitmap.height>100)
                File(output,"${city.id}-$index.jpg").writeBytes(bytes)
                println("decoded $name $index ${bytes.size} bytes")
                dimensions+="${bitmap.width}x${bitmap.height}"
            }
            println("PASS $name: ${result.photos.size} candidates, ${dimensions.size} decoded $dimensions")
            records+=mapOf("city" to name,"photos" to result.photos,"dimensions" to dimensions,"sourceFailed" to result.failed)
        }
        File(output,"results.json").writeText(GsonBuilder().setPrettyPrinting().create().toJson(records))
        kotlin.system.exitProcess(0)
    }
}
