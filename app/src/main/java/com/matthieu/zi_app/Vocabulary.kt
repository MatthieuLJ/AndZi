package com.matthieu.zi_app

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Basic entity representing a Chinese vocabulary item with Hanzi, Pinyin, and English translations.
 */
@Parcelize
data class Vocabulary @JvmOverloads constructor(
    @JvmField var chinese: String = "",
    @JvmField var pinyin: String = "",
    @JvmField var english: ArrayList<String> = ArrayList(),
    @JvmField var hskLevel: Int = 0
) : Parcelable {

    constructor(ch: String, pi: String, en: String) : this(
        chinese = ch,
        pinyin = pi,
        english = arrayListOf(en),
        hskLevel = 0
    )

    fun same_word(other: Vocabulary?): Boolean {
        if (other == null) return false
        return other.chinese == chinese && other.pinyin == pinyin
    }
}
