package cn.traintrip.core

import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

interface GuideStore {
    fun read(cityId: String): DestinationGuide?
    fun attempted(cityId: String): Boolean
    fun markAttempted(cityId: String)
    fun save(guide: DestinationGuide)
}

class GuideRepository(private val source: GuideMaterialSource, private val generator: GuideGenerator, private val store: GuideStore) {
    fun cached(cityId: String): DestinationGuide? = store.read(cityId)?.takeIf(SimplifiedGuidePolicy::guideAllowed)
        ?.let(GuideItemPolicy::limit) ?: DestinationGuides.find(cityId)
    fun attempted(cityId: String): Boolean = store.attempted(cityId)
    suspend fun generate(city: City, key: String, stage: (String) -> Unit, prepare: suspend (DestinationGuide) -> DestinationGuide = { it }): DestinationGuide {
        store.markAttempted(city.id)
        val material = source.fetch(city, stage)
        stage("DeepSeek 正在整理目的地介绍…")
        val guide = DestinationGuides.validateCached(generator.refresh(material, key, cached(city.id)), city.id)
        require(guide.name == city.name)
        SimplifiedGuidePolicy.requireGuide(guide)
        stage("正在保存离线内容…")
        val prepared = prepare(guide)
        SimplifiedGuidePolicy.requireGuide(prepared)
        coroutineContext.ensureActive()
        store.save(prepared)
        return prepared
    }
}
