package cn.traintrip.core

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class GuidePhotoSourceTest {
    private val city=StationCatalog.bundled().cities.first { it.name=="承德" }
    private val guide=DestinationGuides.all.first().copy(cityId=city.id,name=city.name).withPhotos(emptyList())
    @Test fun failedPrimaryUsesFallbackAndKeepsFailureVisible()=runBlocking {
        val photo=DestinationGuides.all.first().photo!!.copy(description="${city.name} · ${guide.experiences.first().name}");var calls=0
        val source=FallbackPhotoSource(GuidePhotoSource { _,_,_ -> throw java.io.IOException() },GuidePhotoSource { _,_,_ -> calls++;PhotoCandidates(listOf(photo)) })
        val first=source.fetch(city,guide) {}
        assertEquals(0,calls);assertTrue(first.fallbackAvailable)
        val result=source.fetchFallback(city,guide) {}
        assertEquals(1,calls);assertEquals(listOf(photo),result.photos);assertTrue(first.failed)
    }
    @Test fun fullPrimarySkipsPaidSearchAndCancellationNeverFallsBack()=runBlocking {
        var calls=0;val fallback=GuidePhotoSource { _,_,_ -> calls++;PhotoCandidates(emptyList()) }
        val photos=(1..5).map { DestinationGuides.all.first().photo!!.copy(description="${city.name} · ${guide.experiences.first().name}",assetName="a$it.jpg",remoteUrl="https://dimg.c-ctrip.com/$it.jpg") }
        assertEquals(5,FallbackPhotoSource(GuidePhotoSource { _,_,_ -> PhotoCandidates(photos) },fallback).fetch(city,guide) {}.photos.size)
        try { FallbackPhotoSource(GuidePhotoSource { _,_,_ -> throw CancellationException() },fallback).fetch(city,guide) {};fail() } catch(_:CancellationException) {}
        assertEquals(0,calls)
    }
}
