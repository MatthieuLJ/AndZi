/******************************
 * Class to parse a string into a list of Pinyins
 */
package com.matthieu.zi_app

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.Locale
import java.util.WeakHashMap

enum class ParsedElementType {
    CHINESE,
    PINYIN_WITH_TONE,
    PINYIN_WITHOUT_TONE,
    INCOMPLETE_PINYIN
}

data class ParsedElement(
    val type: ParsedElementType,
    val content: String
)

object PinyinParser {
    private const val DEBUG = false
    private const val TAG = "PinyinParser"
    private const val pinyin_chars = "[bpmfdtnlgkhzcsrjqxaoeiuvwyāōēīūǖáóéíúǘǎǒěǐǔǚàòèìùǜ\'0-5]*"

    private val cache = WeakHashMap<String, ArrayList<ParsedElement>>()

    @JvmStatic
    fun parse_string(pinyin_string: String): ArrayList<String>? {
        var splitIndex: Int

        // Try to find strophe to split the string
        splitIndex = pinyin_string.indexOf("'")
        if (splitIndex != -1) {
            if (splitIndex == 0) return null
            if (splitIndex == pinyin_string.length) {
                return parse_string(pinyin_string.substring(0, pinyin_string.length - 1))
            }
            val temp1 = parse_string(pinyin_string.substring(0, splitIndex)) ?: return null
            val temp2 = parse_string(pinyin_string.substring(splitIndex + 1)) ?: return null
            val result = ArrayList(temp1)
            result.addAll(temp2)
            return result
        }

        // digits are tones at the end of pinyins... can split there too
        var i = 0
        while (i < 6) {
            val tmpSplitIndex = pinyin_string.indexOf(i.toString())
            if (tmpSplitIndex != -1 && (splitIndex == -1 || tmpSplitIndex < splitIndex)) {
                splitIndex = tmpSplitIndex
            }
            i++
        }
        if (splitIndex != -1 && splitIndex != pinyin_string.length - 1) {
            if (splitIndex == 0) {
                return parse_string(pinyin_string.substring(1))
            }
            val temp1 = parse_string(pinyin_string.substring(0, splitIndex + 1)) ?: return null
            val temp2 = parse_string(pinyin_string.substring(splitIndex + 1)) ?: return null
            val result = ArrayList(temp1)
            result.addAll(temp2)
            return result
        }

        val maxLen = minOf(7, pinyin_string.length)
        for (k in maxLen downTo 0) {
            val tryPinyin = Pinyin(pinyin_string.substring(0, k))
            if (tryPinyin.lame.isNotEmpty()) {
                val result: ArrayList<String>
                if (k < pinyin_string.length) {
                    val nextStep = pinyin_string.substring(k)
                    val temp = parse_string(nextStep) ?: continue
                    result = ArrayList(temp)
                } else {
                    result = ArrayList()
                }
                result.add(0, tryPinyin.lame)
                return result
            }
        }

        return null
    }

    @JvmStatic
    fun parse_string_to_pinyin(pinyin_string: String): ArrayList<Pinyin>? {
        var splitIndex: Int

        // Try to find strophe to split the string
        splitIndex = pinyin_string.indexOf("'")
        if (splitIndex != -1) {
            if (splitIndex == 0) return null
            if (splitIndex == pinyin_string.length) {
                return parse_string_to_pinyin(pinyin_string.substring(0, pinyin_string.length - 1))
            }
            val temp1 = parse_string_to_pinyin(pinyin_string.substring(0, splitIndex)) ?: return null
            val temp2 = parse_string_to_pinyin(pinyin_string.substring(splitIndex + 1)) ?: return null
            val result = ArrayList(temp1)
            result.addAll(temp2)
            return result
        }

        // digits are tones at the end of pinyins... can split there too
        var i = 0
        while (i < 6) {
            val tmpSplitIndex = pinyin_string.indexOf(i.toString())
            if (tmpSplitIndex != -1 && (splitIndex == -1 || tmpSplitIndex < splitIndex)) {
                splitIndex = tmpSplitIndex
            }
            i++
        }
        if (splitIndex != -1 && splitIndex != pinyin_string.length - 1) {
            if (splitIndex == 0) {
                return parse_string_to_pinyin(pinyin_string.substring(1))
            }
            val temp1 = parse_string_to_pinyin(pinyin_string.substring(0, splitIndex + 1)) ?: return null
            val temp2 = parse_string_to_pinyin(pinyin_string.substring(splitIndex + 1)) ?: return null
            val result = ArrayList(temp1)
            result.addAll(temp2)
            return result
        }

        val maxLen = minOf(7, pinyin_string.length)
        for (k in maxLen downTo 0) {
            val tryPinyin = pinyin_string.substring(0, k)
            if (Pinyin.verify_pinyin(tryPinyin)) {
                val result: ArrayList<Pinyin>
                if (k < pinyin_string.length) {
                    val nextStep = pinyin_string.substring(k)
                    val temp = parse_string_to_pinyin(nextStep) ?: continue
                    result = ArrayList(temp)
                } else {
                    result = ArrayList()
                }
                result.add(0, Pinyin(tryPinyin))
                return result
            }
        }

        return null
    }

    private fun add_to_cache(entry: String, result: ArrayList<ParsedElement>) {
        synchronized(cache) {
            if (!cache.containsKey(entry)) {
                cache[entry] = ArrayList(result)
                if (DEBUG) Log.d(TAG, "Save to cache for [$entry] with ${result.size} items")
            }
        }
    }

    private fun query_cache(entry: String): ArrayList<ParsedElement>? {
        synchronized(cache) {
            return if (cache.containsKey(entry)) {
                if (DEBUG) Log.d(TAG, "Found entry for $entry in cache")
                cache[entry]?.let { ArrayList(it) }
            } else {
                null
            }
        }
    }

    @JvmStatic
    fun parse_string_to_elements(pinyin_string: String): ArrayList<ParsedElement>? {
        val cached = query_cache(pinyin_string)
        if (cached != null) return cached

        if (!pinyin_string.matches(Regex(pinyin_chars))) return null

        var splitIndex: Int

        // Try to find strophe to split the string
        splitIndex = pinyin_string.indexOf("'")
        if (splitIndex != -1) {
            if (splitIndex == 0) return null
            if (splitIndex == pinyin_string.length) {
                return parse_string_to_elements(pinyin_string.substring(0, pinyin_string.length - 1))
            }
            val temp1 = parse_string_to_elements(pinyin_string.substring(0, splitIndex))
            if (temp1 == null || temp1.lastOrNull()?.type == ParsedElementType.INCOMPLETE_PINYIN) {
                return null
            }
            val temp2 = parse_string_to_elements(pinyin_string.substring(splitIndex + 1)) ?: return null
            val result = ArrayList(temp1)
            result.addAll(temp2)
            return result
        }

        // digits are tones at the end of pinyins... can split there too
        var i = 0
        while (i < 6) {
            val tmpSplitIndex = pinyin_string.indexOf(i.toString())
            if (tmpSplitIndex != -1 && (splitIndex == -1 || tmpSplitIndex < splitIndex)) {
                splitIndex = tmpSplitIndex
            }
            i++
        }
        if (splitIndex != -1 && splitIndex != pinyin_string.length - 1) {
            if (splitIndex == 0) return null

            val temp1 = parse_string_to_elements(pinyin_string.substring(0, splitIndex + 1)) ?: return null
            val temp2 = parse_string_to_elements(pinyin_string.substring(splitIndex + 1)) ?: return null
            val result = ArrayList(temp1)
            result.addAll(temp2)
            add_to_cache(pinyin_string, result)
            return result
        }

        val maxLen = minOf(7, pinyin_string.length)
        for (k in maxLen downTo 1) {
            val tryPinyin = Pinyin(pinyin_string.substring(0, k))
            if (tryPinyin.lame.isNotEmpty()) {
                val result: ArrayList<ParsedElement>
                if (k < pinyin_string.length) {
                    val nextStep = pinyin_string.substring(k)
                    val temp = parse_string_to_elements(nextStep) ?: continue
                    result = ArrayList(temp)
                } else {
                    result = ArrayList()
                }

                if (tryPinyin.tone_is_given()) {
                    result.add(0, ParsedElement(ParsedElementType.PINYIN_WITH_TONE, tryPinyin.lame))
                } else {
                    result.add(0, ParsedElement(ParsedElementType.PINYIN_WITHOUT_TONE, tryPinyin.radical))
                }
                add_to_cache(pinyin_string, result)
                return result
            }
        }

        if (pinyin_string.length < 7) {
            val incompletePinyin = Pinyin(pinyin_string, true)
            if (incompletePinyin.lame.isNotEmpty()) {
                val result = ArrayList<ParsedElement>()
                result.add(ParsedElement(ParsedElementType.INCOMPLETE_PINYIN, incompletePinyin.lame))
                add_to_cache(pinyin_string, result)
                return result
            }
        }

        return null
    }

    @JvmStatic
    fun parse_mixed_string(input: String): ArrayList<ParsedElement>? {
        val cached = query_cache(input)
        if (cached != null) return cached

        val res = ArrayList<ParsedElement>()
        var idx = 0
        while (idx < input.length) {
            val codePoint = input.codePointAt(idx)
            val charCount = Character.charCount(codePoint)

            if ((codePoint in 0x4E00..0x9FFF) ||
                (codePoint in 0x2E00..0x31FF) ||
                (codePoint in 0x3400..0x4DBF)
            ) {
                res.add(ParsedElement(ParsedElementType.CHINESE, input.substring(idx, idx + charCount)))
                idx += charCount
            } else {
                val singleChar = input.substring(idx, idx + 1).lowercase(Locale.ENGLISH)
                if (!singleChar.matches(Regex(pinyin_chars))) {
                    res.clear()
                    return res
                }
                var j = idx + 1
                while (j <= input.length && input.substring(idx, j).lowercase(Locale.ENGLISH).matches(Regex(pinyin_chars))) {
                    j++
                }
                val pinyins = input.substring(idx, j - 1)
                val more = parse_string_to_elements(pinyins) ?: return null
                res.addAll(more)
                idx = j - 1
            }
        }

        if (DEBUG) Log.d(TAG, "Parsed string [$input], got ${res.size} items")
        add_to_cache(input, res)
        return res
    }

    @JvmStatic
    fun convert_to_pretty_version(input: String): String {
        if (input.isEmpty()) return ""
        val parsed = parse_string_to_pinyin(input) ?: return input
        val result = StringBuilder()
        for (p in parsed) {
            result.append(p.print)
        }
        return result.toString()
    }

    @JvmStatic
    fun convert_to_html_version(input: String): String {
        if (input.isEmpty()) return ""
        val parsed = parse_string_to_pinyin(input) ?: return input
        val result = StringBuilder()
        for (p in parsed) {
            result.append(p.html_color_print)
        }
        return result.toString()
    }

    @JvmStatic
    fun convert_to_spannable_version(input: String, context: Context): SpannableStringBuilder {
        if (input.isEmpty()) return SpannableStringBuilder("")
        val parsed = parse_string_to_pinyin(input) ?: return SpannableStringBuilder("")

        val resultBuilder = StringBuilder()
        for (p in parsed) {
            resultBuilder.append(p.print)
        }
        val result = SpannableStringBuilder(resultBuilder.toString())
        var offset = 0
        for (p in parsed) {
            val size = p.print.length
            val colorRes = when (p.get_tone()) {
                1 -> R.color.first_tone
                2 -> R.color.second_tone
                3 -> R.color.third_tone
                4 -> R.color.fourth_tone
                else -> null
            }

            if (colorRes != null) {
                val color = ForegroundColorSpan(ContextCompat.getColor(context, colorRes))
                result.setSpan(color, offset, offset + size, 0)
            }
            offset += size
        }
        return result
    }

    @JvmStatic
    fun convert_to_lame_version(input: String): String {
        if (input.isEmpty()) return ""
        val parsed = parse_string_to_pinyin(input) ?: return input
        val result = StringBuilder()
        for (p in parsed) {
            result.append(p.lame)
        }
        return result.toString()
    }

    @JvmStatic
    fun convert_chinese_to_html_version(input: String, pinyins: ArrayList<Pinyin>): String {
        val res = StringBuilder()
        var index = 0
        var i = 0
        while (i < input.length) {
            val codePoint = input.codePointAt(i)
            val charCount = Character.charCount(codePoint)
            val hanzi = input.substring(i, i + charCount)
            if (pinyins.size < index + 1) return ""
            when (pinyins[index].get_tone()) {
                1 -> res.append("<font color=\"red\">").append(hanzi).append("</font>")
                2 -> res.append("<font color=\"#ff8000\">").append(hanzi).append("</font>")
                3 -> res.append("<font color=\"green\">").append(hanzi).append("</font>")
                4 -> res.append("<font color=\"blue\">").append(hanzi).append("</font>")
                5 -> res.append(hanzi)
            }
            index++
            i += charCount
        }
        return res.toString()
    }
}
