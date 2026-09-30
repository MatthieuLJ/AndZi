package com.matthieu.zi_app.data.local.model

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import com.matthieu.zi_app.data.local.entity.CharPinyinEntity
import com.matthieu.zi_app.data.local.entity.ChineseWordEntity
import com.matthieu.zi_app.data.local.entity.EnglishTranslationEntity
import com.matthieu.zi_app.data.local.entity.WordSegmentEntity

/**
 * Composite model representing a Chinese word with its decomposed characters and English translations.
 */
data class WordWithDetails(
    @Embedded
    val word: ChineseWordEntity,

    @Relation(
        parentColumn = "cword_id",
        entityColumn = "cword_id"
    )
    val translations: List<EnglishTranslationEntity> = emptyList(),

    @Relation(
        parentColumn = "cword_id",
        entityColumn = "char_pinyin_id",
        associateBy = Junction(WordSegmentEntity::class)
    )
    val characters: List<CharPinyinEntity> = emptyList()
)
