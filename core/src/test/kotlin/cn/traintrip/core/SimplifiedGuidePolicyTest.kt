package cn.traintrip.core

import org.junit.Assert.*
import org.junit.Test

class SimplifiedGuidePolicyTest {
    private fun guide() = DestinationGuides.all.first()

    @Test fun simplifiedAndSharedCharactersRemainValid() {
        for (text in listOf("保定野三坡，位于涞水县北部，山水风光适合游览。", "乾隆、著名、佛寺、余杭、云台山", "AAAAA 级景区 123")) {
            assertTrue(text, SimplifiedGuidePolicy.textAllowed(text))
        }
        assertEquals(5, DestinationGuides.all.size)
        assertTrue(DestinationGuides.all.all(SimplifiedGuidePolicy::guideAllowed))
    }

    @Test fun baodingEvidenceAndMixedTraditionalTextAreRejected() {
        val quote = "有世界地質公園、國家級風景名勝區稱號的野三坡位於淶水縣北部。"
        assertFalse(SimplifiedGuidePolicy.textAllowed(quote))
        assertFalse(SimplifiedGuidePolicy.textAllowed("保定文化名城待卿來"))
        assertFalse(SimplifiedGuidePolicy.textAllowed("简体正文".repeat(500) + "風"))
    }

    @Test fun traditionalLanguageVersionsAreRejectedEvenWithSimplifiedSnippet() {
        for (url in listOf("http://he.people.com.cn/BIG5/n2/2021/1003/c192235-34942447.html",
            "https://example.cn/%42%49%47%35/a", "https://example.cn/zh-Hant/a", "https://example.cn/a?lang=zh_TW",
            "https://example.cn/zh-hk/a", "https://big5.example.cn/a", "https://example.cn/gb2big5/a",
            "https://www.people.com.cn/a?lang=zh-TW#top", "https://www.people.com.cn/BIG5#top")) {
            assertFalse(url, SimplifiedGuidePolicy.urlAllowed(url))
        }
        assertTrue(SimplifiedGuidePolicy.urlAllowed("https://he.people.com.cn/n2/2021/1003/c192235-34942447.html"))
        assertTrue(SimplifiedGuidePolicy.urlAllowed("https://example.cn/a?lang=zh-cn"))
    }

    @Test fun everyDisplayAreaAndEvidenceHasLanguageValidation() {
        val g=guide()
        val invalid=listOf(g.copy(tagline="遊覽保定"),g.copy(tags=listOf("風光","古城")),
            g.copy(experiences=g.experiences.mapIndexed { i,e -> if(i==0)e.copy(evidence="國家公園") else e }),
            g.copy(foods=g.foods.mapIndexed { i,f -> if(i==0)f.copy(description="傳統美食") else f }),
            g.copy(plans=g.plans.mapIndexed { i,p -> if(i==0)p.copy(schedule=p.schedule.map { it.copy(label="當天") }) else p }),
            g.copy(sources=g.sources.mapIndexed { i,s -> if(i==0)s.copy(title="文化名城待卿來") else s }),
            g.copy(photo=g.photo!!.copy(credit="攝影者")),
            g.copy(photo=g.photo.copy(sourceUrl="https://he.people.com.cn/BIG5/a")))
        invalid.forEach { assertFalse(SimplifiedGuidePolicy.guideAllowed(it)) }
    }
}
