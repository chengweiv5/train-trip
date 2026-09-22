package cn.traintrip.core

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class GuidePhotoPolicyTest {
    private val base = DestinationGuides.all.first()
    private fun photo(subject:PhotoSubject,index:Int)=DestinationPhoto("p${subject.name.hashCode().toUInt()}_$index.jpg","${base.name} · ${subject.name}","test","https://you.ctrip.com/a",remoteUrl="https://dimg.c-ctrip.com/${subject.name}/$index.jpg",subject=subject)
    @Test fun perItemThreeAndCityHundredWithoutStarvingFirstPhotos() {
        val subjects=(1..34).map { PhotoSubject(if(it%2==0)"food" else "place","条目$it") }
        val chosen=GuidePhotoPolicy.bounded(subjects.flatMap { s -> (1..4).map { photo(s,it) } })
        assertEquals(100,chosen.size)
        assertTrue(chosen.groupingBy { it.subject }.eachCount().values.all { it<=3 })
        assertEquals(subjects,chosen.take(34).map { it.subject })
        assertEquals(6,GuidePhotoPolicy.bounded(subjects.take(2).flatMap { s -> (1..4).map { photo(s,it) } }).size)
    }
    @Test fun legacyOwnershipNeedsExactCityAndUniqueItemAndSurvivesIdChange() {
        val place=base.experiences.first();val subject=PhotoSubject("place",place.name)
        val legacy=photo(subject,1).copy(subject=null)
        assertEquals(subject,GuidePhotoPolicy.subject(base,legacy))
        assertEquals(subject,GuidePhotoPolicy.subject(base.copy(experiences=listOf(place.copy(id="new"))),legacy))
        assertNull(GuidePhotoPolicy.subject(base,legacy.copy(description="其他城市 · ${place.name}")))
        assertNull(GuidePhotoPolicy.subject(base,legacy.copy(description="${base.name} · 城市资料页配图")))
        assertNull(GuidePhotoPolicy.subject(base.copy(experiences=base.experiences+place.copy(id="other")),legacy))
        assertNull(GuidePhotoPolicy.subject(base.copy(experiences=emptyList()),legacy))
    }
    @Test fun existingSinglePhotoSkipsItsSubjectButNotOthers() {
        val place=base.experiences.first();val s=PhotoSubject("place",place.name)
        val guide=base.withPhotos(listOf(photo(s,1)))
        assertFalse(GuidePhotoPolicy.missing(guide).contains(s))
        assertEquals(base.experiences.size+base.foods.size-1,GuidePhotoPolicy.missing(guide).size)
        assertEquals(1,GuidePhotoPolicy.forItem(guide,"place",place.name).size)
    }
    @Test fun candidatesFollowGuideOrderEvenWhenSourcesReturnFoodFirst() {
        val subjects=GuidePhotoPolicy.subjects(base)
        val candidates=subjects.reversed().flatMap { s -> (1..4).map { photo(s,it) } }
        assertEquals(subjects,GuidePhotoPolicy.candidates(base,candidates).take(subjects.size).map { it.subject })
        assertEquals(subjects,GuidePhotoPolicy.select(base,candidates).take(subjects.size).map { it.subject })
    }
    @Test fun repeatedUrlsAndAssetsNeverCountTwice() {
        val a=PhotoSubject("place","甲地点");val b=PhotoSubject("food","乙美食")
        val first=photo(a,1)
        val chosen=GuidePhotoPolicy.bounded(listOf(first,first.copy(subject=b),first.copy(assetName="other.jpg",subject=b),photo(b,2)))
        assertEquals(2,chosen.size)
    }
    @Test fun oldJsonWithoutSubjectRemainsReadableAndOverfullNewAlbumRejected() {
        val old=Gson().fromJson(Gson().toJson(base),DestinationGuide::class.java)
        assertEquals(base.gallery,old.gallery)
        val subject=PhotoSubject("place",base.experiences.first().name)
        val generated=base.copy(generatedAt="2026-09-22T00:00:00Z",model="test").withPhotos((1..4).map { photo(subject,it) })
        assertTrue(runCatching { DestinationGuides.validateGenerated(generated,base.cityId) }.isFailure)
        assertEquals(3,GuidePhotoPolicy.select(generated).size)
    }
}
