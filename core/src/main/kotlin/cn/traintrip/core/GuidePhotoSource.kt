package cn.traintrip.core

import java.security.MessageDigest

/** Candidates are un-downloaded images; a source failure must not invalidate the text guide. */
data class PhotoCandidates(val photos: List<DestinationPhoto>, val failed: Boolean = false, val fallbackAvailable: Boolean = false)
fun interface GuidePhotoSource {
    suspend fun fetch(city: City, guide: DestinationGuide, stage: (String) -> Unit): PhotoCandidates
    suspend fun fetchFallback(city: City, guide: DestinationGuide, stage: (String) -> Unit): PhotoCandidates = PhotoCandidates(emptyList())
}

class FallbackPhotoSource(private val primary: GuidePhotoSource, private val fallback: GuidePhotoSource) : GuidePhotoSource {
    override suspend fun fetch(city: City, guide: DestinationGuide, stage: (String) -> Unit): PhotoCandidates {
        val first = optional { primary.fetch(city, guide, stage) } ?: PhotoCandidates(emptyList(), true)
        val distinct = first.photos.distinctBy { it.remoteUrl ?: it.assetName }
        if (distinct.size >= 5) return first.copy(photos = distinct.take(12), fallbackAvailable = true)
        val second = fetchFallback(city, guide, stage)
        return PhotoCandidates((first.photos + second.photos).distinctBy { it.remoteUrl }.take(12), first.failed || second.failed)
    }

    override suspend fun fetchFallback(city: City, guide: DestinationGuide, stage: (String) -> Unit): PhotoCandidates =
        optional { fallback.fetch(city, guide, stage) } ?: PhotoCandidates(emptyList(), true)
}

internal fun supportedPhoto(url: String): Boolean = GuideNetwork.isPhotoUrl(url) &&
    SimplifiedGuidePolicy.urlAllowed(url) && !TavilyGuideSource.DECORATION.containsMatchIn(url)

internal fun sourcedPhoto(url: String, description: String, sourceUrl: String, credit: String): DestinationPhoto {
    val hash = MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }.take(24)
    return DestinationPhoto("remote_$hash.jpg", description, credit, sourceUrl, remoteUrl = url)
}
