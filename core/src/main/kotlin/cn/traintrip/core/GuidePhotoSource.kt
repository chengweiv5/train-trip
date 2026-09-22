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
        return first.copy(photos = GuidePhotoPolicy.candidates(guide, first.photos), fallbackAvailable = true)
    }

    override suspend fun fetchFallback(city: City, guide: DestinationGuide, stage: (String) -> Unit): PhotoCandidates =
        if (GuidePhotoPolicy.missing(guide).isEmpty()) PhotoCandidates(emptyList())
        else optional { fallback.fetch(city, guide, stage) } ?: PhotoCandidates(emptyList(), true)
}

internal fun supportedPhoto(url: String): Boolean = GuideNetwork.isPhotoUrl(url) &&
    SimplifiedGuidePolicy.urlAllowed(url) && !TavilyGuideSource.DECORATION.containsMatchIn(url)

internal fun sourcedPhoto(url: String, description: String, sourceUrl: String, credit: String, subject: PhotoSubject? = null): DestinationPhoto {
    val hash = MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }.take(24)
    return DestinationPhoto("remote_$hash.jpg", description, credit, sourceUrl, remoteUrl = url, subject = subject)
}
