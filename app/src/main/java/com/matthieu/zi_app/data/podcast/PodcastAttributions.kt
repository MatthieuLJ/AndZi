package com.matthieu.zi_app.data.podcast

data class PodcastAttribution(
    val title: String,
    val chineseTitle: String,
    val author: String,
    val license: String,
    val website: String,
    val description: String
)

object PodcastAttributions {
    val attributions = listOf(
        PodcastAttribution(
            title = "Slow Chinese",
            chineseTitle = "慢速中文",
            author = "Slow Chinese Team & Community",
            license = "Creative Commons Attribution-NonCommercial-ShareAlike (CC BY-NC-SA)",
            website = "https://kitchenknif.github.io/SlowChinese/",
            description = "Cultural audio essays written by native Chinese speakers and narrated at a slow, clear pace for language learners."
        ),
        PodcastAttribution(
            title = "Reading China",
            chineseTitle = "中文天天读",
            author = "Slow Chinese / Cultural Reading Project",
            license = "Creative Commons (CC BY-NC-SA)",
            website = "https://kitchenknif.github.io/SlowChinese/",
            description = "Short graded readings exploring everyday Chinese culture, idioms, and contemporary society."
        ),
        PodcastAttribution(
            title = "MaoMi Chinese",
            chineseTitle = "猫咪中文",
            author = "MaoMi Chinese",
            license = "Free educational podcast. All original audio & text rights belong to MaoMi Chinese.",
            website = "https://maomichinese.com",
            description = "Natural Mandarin podcast episodes designed for lower-intermediate to intermediate learners."
        ),
        PodcastAttribution(
            title = "TeaTime Chinese",
            chineseTitle = "茶歇中文",
            author = "Nathan",
            license = "Free educational podcast. All original audio & text rights belong to TeaTime Chinese.",
            website = "https://teatimechinese.com",
            description = "Comprehensible input podcast in conversational Mandarin discussing history, daily life, and philosophy."
        )
    )

    fun getAttributionForChannel(title: String?): PodcastAttribution? {
        if (title.isNullOrBlank()) return null
        return attributions.find {
            it.title.equals(title.trim(), ignoreCase = true) ||
                    it.chineseTitle.equals(title.trim(), ignoreCase = true)
        }
    }
}

