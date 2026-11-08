/******************************
 * Class for Pinyin, holds basic Pinyin structures
 * and provides different formatting of the same pinyin
 * Also offers static function to verify a pinyin
 */
package com.matthieu.zi_app

import java.util.Locale

class Pinyin {
    var lame: String = ""
        private set
    var radical: String = ""
        private set
    var tone: Int = -1
        private set
    var print: String = ""
        private set
    var html_color_print: String = ""
        private set

    constructor()

    constructor(entry: String) {
        if (!verify_pinyin(entry)) return

        for (i in 0..5) {
            if (entry.endsWith(i.toString())) {
                val temp = entry.substring(0, entry.length - 1)
                for (j in pinyin_list_notone.indices) {
                    if (temp == pinyin_list_notone[j]) {
                        this.lame = temp + i
                        this.radical = temp
                        this.tone = if (i == 0) 5 else i
                        when (this.tone) {
                            5 -> {
                                this.print = pinyin_list_tone5[j]
                                this.html_color_print = this.print
                            }
                            1 -> {
                                this.print = pinyin_list_tone1[j]
                                this.html_color_print = "<font color=\"red\">${this.print}</font>"
                            }
                            2 -> {
                                this.print = pinyin_list_tone2[j]
                                this.html_color_print = "<font color=\"#ff8000\">${this.print}</font>"
                            }
                            3 -> {
                                this.print = pinyin_list_tone3[j]
                                this.html_color_print = "<font color=\"green\">${this.print}</font>"
                            }
                            4 -> {
                                this.print = pinyin_list_tone4[j]
                                this.html_color_print = "<font color=\"blue\">${this.print}</font>"
                            }
                        }
                        return
                    }
                }
            }
        }

        ensureMapsInitialized()
        val vToTone = vowels_to_tone ?: emptyMap()
        val lVowels = lame_vowels ?: emptyMap()

        for (v in vToTone.keys) {
            if (entry.contains(v)) {
                this.tone = vToTone[v] ?: 5
                this.print = entry
                when (this.tone) {
                    1 -> this.html_color_print = "<font color=\"red\">${this.print}</font>"
                    2 -> this.html_color_print = "<font color=\"#ff8000\">${this.print}</font>"
                    3 -> this.html_color_print = "<font color=\"green\">${this.print}</font>"
                    4 -> this.html_color_print = "<font color=\"blue\">${this.print}</font>"
                }
                val replacement = lVowels[v] ?: ""
                this.radical = entry.replace(v, replacement)
                this.lame = "${this.radical}${this.tone}"
                return
            }
        }

        for (i in pinyin_list_notone.indices) {
            if (entry == pinyin_list_notone[i] || entry == pinyin_list_tone5[i]) {
                this.lame = "${pinyin_list_notone[i]}5"
                this.radical = pinyin_list_notone[i]
                this.tone = 0
                this.print = pinyin_list_tone5[i]
                this.html_color_print = pinyin_list_tone5[i]
                return
            }
        }
    }

    constructor(entry: String, incomplete: Boolean) {
        if (entry.isEmpty()) return

        ensureMapsInitialized()

        val initial = entry.substring(0, 1)
        val candidates = pinyin_by_first_letter[initial] ?: return

        for (candidate in candidates) {
            if (candidate.startsWith(entry)) {
                val vToTone = vowels_to_tone ?: emptyMap()
                val lVowels = lame_vowels ?: emptyMap()
                for (v in vToTone.keys) {
                    if (entry.contains(v)) {
                        this.tone = vToTone[v] ?: 0
                        this.lame = entry.replace(v, lVowels[v] ?: "")
                        return
                    }
                }
                this.tone = 0
                this.lame = entry
                return
            }
        }
    }

    fun get_tone(): Int = if (tone == 0) 5 else tone

    fun get_lame(): String = lame

    fun get_radical(): String = radical

    fun get_pretty(): String = print

    fun get_color(): String = html_color_print

    fun tone_is_given(): Boolean = tone != 0

    fun equals(pinyin: Pinyin): Boolean =
        pinyin.lame == lame && pinyin.get_tone() == get_tone()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Pinyin) return false
        return equals(other)
    }

    override fun hashCode(): Int {
        var result = lame.hashCode()
        result = 31 * result + get_tone()
        return result
    }

    companion object {
        @Volatile
        private var map_initialized = false

        private val pinyin_by_first_letter = HashMap<String, ArrayList<String>>()
        private var vowels_to_tone: Map<String, Int>? = null
        private var lame_vowels: Map<String, String>? = null

        @JvmStatic
        fun prepare_pinyin_process() {
            ensureMapsInitialized()
        }

        private fun ensureMapsInitialized() {
            if (map_initialized) return
            synchronized(pinyin_by_first_letter) {
                if (!map_initialized) {
                    create_pinyin_map()
                }
            }
        }

        private fun create_pinyin_map() {
            val allLists = arrayOf(
                pinyin_list_notone,
                pinyin_list_tone1,
                pinyin_list_tone2,
                pinyin_list_tone3,
                pinyin_list_tone4,
                pinyin_list_tone5
            )

            for (list in allLists) {
                for (pin in list) {
                    val first = pin.substring(0, 1)
                    pinyin_by_first_letter.getOrPut(first) { ArrayList() }.add(pin)
                }
            }

            val vMap = HashMap<String, Int>()
            val firstToneVowels = arrayOf("ā", "ō", "ē", "ī", "ū", "ǖ")
            val secondToneVowels = arrayOf("á", "ó", "é", "í", "ú", "ǘ")
            val thirdToneVowels = arrayOf("ǎ", "ǒ", "ě", "ǐ", "ǔ", "ǚ")
            val fourthToneVowels = arrayOf("à", "ò", "è", "ì", "ù", "ǜ")
            for (s in firstToneVowels) vMap[s] = 1
            for (s in secondToneVowels) vMap[s] = 2
            for (s in thirdToneVowels) vMap[s] = 3
            for (s in fourthToneVowels) vMap[s] = 4
            vowels_to_tone = vMap

            val lMap = HashMap<String, String>()
            val aVowels = arrayOf("ā", "á", "ǎ", "à")
            val oVowels = arrayOf("ō", "ó", "ǒ", "ò")
            val eVowels = arrayOf("ē", "é", "ě", "è")
            val iVowels = arrayOf("ī", "í", "ǐ", "ì")
            val uVowels = arrayOf("ū", "ú", "ǔ", "ù")
            val vVowels = arrayOf("ǖ", "ǘ", "ǚ", "ǜ")
            for (s in aVowels) lMap[s] = "a"
            for (s in oVowels) lMap[s] = "o"
            for (s in eVowels) lMap[s] = "e"
            for (s in iVowels) lMap[s] = "i"
            for (s in uVowels) lMap[s] = "u"
            for (s in vVowels) lMap[s] = "v"
            lame_vowels = lMap

            map_initialized = true
        }

        @JvmStatic
        fun verify_pinyin(entry: String): Boolean {
            if (entry.isEmpty()) return false

            ensureMapsInitialized()

            var temp = ""
            for (i in 0..5) {
                if (entry.endsWith(i.toString())) {
                    temp = entry.substring(0, entry.length - 1).lowercase(Locale.ENGLISH)
                }
            }
            if (temp.isEmpty()) {
                temp = entry.lowercase(Locale.ENGLISH)
            }

            if (temp.isEmpty()) return false
            val initial = temp.substring(0, 1)
            val list = pinyin_by_first_letter[initial] ?: return false
            return list.contains(temp)
        }

        @JvmField
        val pinyin_list_notone = arrayOf(
            "a", "ba", "pa", "ma", "fa", "da", "ta", "na", "la", "za", "ca", "sa", "zha", "cha", "sha", "ga", "ka", "ha",
            "o", "bo", "po", "mo", "fo",
            "me", "de", "te", "ne", "le", "ze", "ce", "se", "zhe", "che", "she", "re", "ge", "ke", "he",
            "zi", "ci", "si", "zhi", "chi", "shi", "ri",
            "e",
            "er", "r",
            "ai", "bai", "pai", "mai", "dai", "tai", "nai", "lai", "zai", "cai", "sai", "zhai", "chai", "shai", "gai", "kai", "hai",
            "ei", "bei", "pei", "mei", "fei", "dei", "nei", "lei", "zei", "zhei", "shei", "gei", "kei", "hei",
            "ao", "bao", "pao", "mao", "dao", "tao", "nao", "lao", "zao", "cao", "sao", "zhao", "chao", "shao", "rao", "gao", "kao", "hao",
            "ou", "pou", "mou", "fou", "dou", "tou", "nou", "lou", "zou", "cou", "sou", "zhou", "chou", "shou", "rou", "gou", "kou", "hou",
            "an", "ban", "pan", "man", "fan", "dan", "tan", "nan", "lan", "zan", "can", "san", "zhan", "chan", "shan", "ran", "gan", "kan", "han",
            "en", "ben", "pen", "men", "fen", "den", "nen", "zen", "cen", "sen", "zhen", "chen", "shen", "ren", "gen", "ken", "hen",
            "ang", "bang", "pang", "mang", "fang", "dang", "tang", "nang", "lang", "zang", "cang", "sang", "zhang", "chang", "shang", "rang", "gang", "kang", "hang",
            "eng", "beng", "peng", "meng", "feng", "deng", "teng", "neng", "leng", "zeng", "ceng", "seng", "zheng", "cheng", "sheng", "reng", "geng", "keng", "heng",
            "dong", "tong", "nong", "long", "zong", "cong", "song", "zhong", "chong", "rong", "gong", "kong", "hong",
            "yi", "bi", "pi", "mi", "di", "ti", "ni", "li", "ji", "qi", "xi",
            "ya", "lia", "jia", "qia", "xia",
            "yao", "biao", "piao", "miao", "diao", "tiao", "niao", "liao", "jiao", "qiao", "xiao",
            "ye", "bie", "pie", "mie", "die", "tie", "nie", "lie", "jie", "qie", "xie",
            "you", "miu", "diu", "niu", "liu", "jiu", "qiu", "xiu",
            "yan", "bian", "pian", "mian", "dian", "tian", "nian", "lian", "jian", "qian", "xian",
            "yin", "bin", "pin", "min", "nin", "lin", "jin", "qin", "xin",
            "yang", "niang", "liang", "jiang", "qiang", "xiang",
            "ying", "bing", "ping", "ming", "ding", "ting", "ning", "ling", "jing", "qing", "xing",
            "yong", "jiong", "qiong", "xiong",
            "wu", "bu", "pu", "mu", "fu", "du", "tu", "nu", "lu", "zu", "cu", "su", "zhu", "chu", "shu", "ru", "gu", "ku", "hu",
            "wa", "zhua", "chua", "shua", "rua", "gua", "kua", "hua",
            "wo", "duo", "tuo", "nuo", "luo", "zuo", "cuo", "suo", "zhuo", "chuo", "shuo", "ruo", "guo", "kuo", "huo",
            "wai", "zhuai", "chuai", "shuai", "guai", "kuai", "huai",
            "wei", "dui", "tui", "zui", "cui", "sui", "zhui", "chui", "shui", "rui", "gui", "kui", "hui",
            "wan", "duan", "tuan", "nuan", "luan", "zuan", "cuan", "suan", "zhuan", "chuan", "shuan", "ruan", "guan", "kuan", "huan",
            "wen", "dun", "tun", "lun", "zun", "cun", "sun", "zhun", "chun", "shun", "run", "gun", "kun", "hun",
            "wang", "zhuang", "chuang", "shuang", "guang", "kuang", "huang", "weng",
            "yu", "nv", "lv", "ju", "qu", "xu",
            "yue", "nve", "lve", "jue", "que", "xue",
            "yuan", "juan", "quan", "xuan",
            "yun", "jun", "qun", "xun"
        )

        @JvmField
        val pinyin_list_tone1 = arrayOf(
            "ā", "bā", "pā", "mā", "fā", "dā", "tā", "nā", "lā", "zā", "cā", "sā", "zhā", "chā", "shā", "gā", "kā", "hā",
            "ō", "bō", "pō", "mō", "fō",
            "mē", "dē", "tē", "nē", "lē", "zē", "cē", "sē", "zhē", "chē", "shē", "rē", "gē", "kē", "hē",
            "zī", "cī", "sī", "zhī", "chī", "shī", "rī",
            "ē",
            "ēr", "r",
            "āi", "bāi", "pāi", "māi", "dāi", "tāi", "nāi", "lāi", "zāi", "cāi", "sāi", "zhāi", "chāi", "shāi", "gāi", "kāi", "hāi",
            "ēi", "bēi", "pēi", "mēi", "fēi", "dēi", "nēi", "lēi", "zēi", "zhēi", "shēi", "gēi", "kēi", "hēi",
            "āo", "bāo", "pāo", "māo", "dāo", "tāo", "nāo", "lāo", "zāo", "cāo", "sāo", "zhāo", "chāo", "shāo", "rāo", "gāo", "kāo", "hāo",
            "ōu", "pōu", "mōu", "fōu", "dōu", "tōu", "nōu", "lōu", "zōu", "cōu", "sōu", "zhōu", "chōu", "shōu", "rōu", "gōu", "kōu", "hōu",
            "ān", "bān", "pān", "mān", "fān", "dān", "tān", "nān", "lān", "zān", "cān", "sān", "zhān", "chān", "shān", "rān", "gān", "kān", "hān",
            "ēn", "bēn", "pēn", "mēn", "fēn", "dēn", "nēn", "zēn", "cēn", "sēn", "zhēn", "chēn", "shēn", "rēn", "gēn", "kēn", "hēn",
            "āng", "bāng", "pāng", "māng", "fāng", "dāng", "tāng", "nāng", "lāng", "zāng", "cāng", "sāng", "zhāng", "chāng", "shāng", "rāng", "gāng", "kāng", "hāng",
            "ēng", "bēng", "pēng", "mēng", "fēng", "dēng", "tēng", "nēng", "lēng", "zēng", "cēng", "sēng", "zhēng", "chēng", "shēng", "rēng", "gēng", "kēng", "hēng",
            "dōng", "tōng", "nōng", "lōng", "zōng", "cōng", "sōng", "zhōng", "chōng", "rōng", "gōng", "kōng", "hōng",
            "yī", "bī", "pī", "mī", "dī", "tī", "nī", "lī", "jī", "qī", "xī",
            "yā", "liā", "jiā", "qiā", "xiā",
            "yāo", "biāo", "piāo", "miāo", "diāo", "tiāo", "niāo", "liāo", "jiāo", "qiāo", "xiāo",
            "yē", "biē", "piē", "miē", "diē", "tiē", "niē", "liē", "jiē", "qiē", "xiē",
            "yōu", "miū", "diū", "niū", "liū", "jiū", "qiū", "xiū",
            "yān", "biān", "piān", "miān", "diān", "tiān", "niān", "liān", "jiān", "qiān", "xiān",
            "yīn", "bīn", "pīn", "mīn", "nīn", "līn", "jīn", "qīn", "xīn",
            "yāng", "niāng", "liāng", "jiāng", "qiāng", "xiāng",
            "yīng", "bīng", "pīng", "mīng", "dīng", "tīng", "nīng", "līng", "jīng", "qīng", "xīng",
            "yōng", "jiōng", "qiōng", "xiōng",
            "wū", "bū", "pū", "mū", "fū", "dū", "tū", "nū", "lū", "zū", "cū", "sū", "zhū", "chū", "shū", "rū", "gū", "kū", "hū",
            "wā", "zhuā", "chuā", "shuā", "ruā", "guā", "kuā", "huā",
            "wō", "duō", "tuō", "nuō", "luō", "zuō", "cuō", "suō", "zhuō", "chuō", "shuō", "ruō", "guō", "kuō", "huō",
            "wāi", "zhuāi", "chuāi", "shuāi", "guāi", "kuāi", "huāi",
            "wēi", "duī", "tuī", "zuī", "cuī", "suī", "zhuī", "chuī", "shuī", "ruī", "guī", "kuī", "huī",
            "wān", "duān", "tuān", "nuān", "luān", "zuān", "cuān", "suān", "zhuān", "chuān", "shuān", "ruān", "guān", "kuān", "huān",
            "wēn", "dūn", "tūn", "lūn", "zūn", "cūn", "sūn", "zhūn", "chūn", "shūn", "rūn", "gūn", "kūn", "hūn",
            "wāng", "zhuāng", "chuāng", "shuāng", "guāng", "kuāng", "huāng", "wēng",
            "yū", "nǖ", "lǖ", "jū", "qū", "xū",
            "yuē", "nüē", "lüē", "juē", "quē", "xuē",
            "yuān", "juān", "quān", "xuān",
            "yūn", "jūn", "qūn", "xūn"
        )

        @JvmField
        val pinyin_list_tone2 = arrayOf(
            "á", "bá", "pá", "má", "fá", "dá", "tá", "ná", "lá", "zá", "cá", "sá", "zhá", "chá", "shá", "gá", "ká", "há",
            "ó", "bó", "pó", "mó", "fó",
            "mé", "dé", "té", "né", "lé", "zé", "cé", "sé", "zhé", "ché", "shé", "ré", "gé", "ké", "hé",
            "zí", "cí", "sí", "zhí", "chí", "shí", "rí",
            "é",
            "ér", "r",
            "ái", "bái", "pái", "mái", "dái", "tái", "nái", "lái", "zái", "cái", "sái", "zhái", "chái", "shái", "gái", "kái", "hái",
            "éi", "béi", "péi", "méi", "féi", "déi", "néi", "léi", "zéi", "zhéi", "shéi", "géi", "kéi", "héi",
            "áo", "báo", "páo", "máo", "dáo", "táo", "náo", "láo", "záo", "cáo", "sáo", "zháo", "cháo", "sháo", "ráo", "gáo", "káo", "háo",
            "óu", "póu", "móu", "fóu", "dóu", "tóu", "nóu", "lóu", "zóu", "cóu", "sóu", "zhóu", "chóu", "shóu", "róu", "góu", "kóu", "hóu",
            "án", "bán", "pán", "mán", "fán", "dán", "tán", "nán", "lán", "zán", "cán", "sán", "zhán", "chán", "shán", "rán", "gán", "kán", "hán",
            "én", "bén", "pén", "mén", "fén", "dén", "nén", "zén", "cén", "sén", "zhén", "chén", "shén", "rén", "gén", "kén", "hén",
            "áng", "báng", "páng", "máng", "fáng", "dáng", "táng", "náng", "láng", "záng", "cáng", "sáng", "zháng", "cháng", "sháng", "ráng", "gáng", "káng", "háng",
            "éng", "béng", "péng", "méng", "féng", "déng", "téng", "néng", "léng", "zéng", "céng", "séng", "zhéng", "chéng", "shéng", "réng", "géng", "kéng", "héng",
            "dóng", "tóng", "nóng", "lóng", "zóng", "cóng", "sóng", "zhóng", "chóng", "róng", "góng", "kóng", "hóng",
            "yí", "bí", "pí", "mí", "dí", "tí", "ní", "lí", "jí", "qí", "xí",
            "yá", "liá", "jiá", "qiá", "xiá",
            "yáo", "biáo", "piáo", "miáo", "diáo", "tiáo", "niáo", "liáo", "jiáo", "qiáo", "xiáo",
            "yé", "bié", "pié", "mié", "dié", "tié", "nié", "lié", "jié", "qié", "xié",
            "yóu", "miú", "diú", "niú", "liú", "jiú", "qiú", "xiú",
            "yán", "bián", "pián", "mián", "dián", "tián", "nián", "lián", "jián", "qián", "xián",
            "yín", "bín", "pín", "mín", "nín", "lín", "jín", "qín", "xín",
            "yáng", "niáng", "liáng", "jiáng", "qiáng", "xiáng",
            "yíng", "bíng", "píng", "míng", "díng", "tíng", "níng", "líng", "jíng", "qíng", "xíng",
            "yóng", "jióng", "qióng", "xióng",
            "wú", "bú", "pú", "mú", "fú", "dú", "tú", "nú", "lú", "zú", "cú", "sú", "zhú", "chú", "shú", "rú", "gú", "kú", "hú",
            "wá", "zhuá", "chuá", "shuá", "ruá", "guá", "kuá", "huá",
            "wó", "duó", "tuó", "nuó", "luó", "zuó", "cuó", "suó", "zhuó", "chuó", "shuó", "ruó", "guó", "kuó", "huó",
            "wái", "zhuái", "chuái", "shuái", "guái", "kuái", "huái",
            "wéi", "duí", "tuí", "zuí", "cuí", "suí", "zhuí", "chuí", "shuí", "ruí", "guí", "kuí", "huí",
            "wán", "duán", "tuán", "nuán", "luán", "zuán", "cuán", "suán", "zhuán", "chuán", "shuán", "ruán", "guán", "kuán", "huán",
            "wén", "dún", "tún", "lún", "zún", "cún", "sún", "zhún", "chún", "shún", "rún", "gún", "kún", "hún",
            "wáng", "zhuáng", "chuáng", "shuáng", "guáng", "kuáng", "huáng", "wéng",
            "yú", "nǘ", "lǘ", "jú", "qú", "xú",
            "yué", "nüé", "lüé", "jué", "qué", "xué",
            "yuán", "juán", "quán", "xuán",
            "yún", "jún", "qún", "xún"
        )

        @JvmField
        val pinyin_list_tone3 = arrayOf(
            "ǎ", "bǎ", "pǎ", "mǎ", "fǎ", "dǎ", "tǎ", "nǎ", "lǎ", "zǎ", "cǎ", "sǎ", "zhǎ", "chǎ", "shǎ", "gǎ", "kǎ", "hǎ",
            "ǒ", "bǒ", "pǒ", "mǒ", "fǒ",
            "mě", "dě", "tě", "ně", "lě", "zě", "cě", "sě", "zhě", "chě", "shě", "rě", "gě", "kě", "hě",
            "zǐ", "cǐ", "sǐ", "zhǐ", "chǐ", "shǐ", "rǐ",
            "ě",
            "ěr", "r",
            "ǎi", "bǎi", "pǎi", "mǎi", "dǎi", "tǎi", "nǎi", "lǎi", "zǎi", "cǎi", "sǎi", "zhǎi", "chǎi", "shǎi", "gǎi", "kǎi", "hǎi",
            "ěi", "běi", "pěi", "měi", "fěi", "děi", "něi", "lěi", "zěi", "zhěi", "shěi", "gěi", "kěi", "hěi",
            "ǎo", "bǎo", "pǎo", "mǎo", "dǎo", "tǎo", "nǎo", "lǎo", "zǎo", "cǎo", "sǎo", "zhǎo", "chǎo", "shǎo", "rǎo", "gǎo", "kǎo", "hǎo",
            "ǒu", "pǒu", "mǒu", "fǒu", "dǒu", "tǒu", "nǒu", "lǒu", "zǒu", "cǒu", "sǒu", "zhǒu", "chǒu", "shǒu", "rǒu", "gǒu", "kǒu", "hǒu",
            "ǎn", "bǎn", "pǎn", "mǎn", "fǎn", "dǎn", "tǎn", "nǎn", "lǎn", "zǎn", "cǎn", "sǎn", "zhǎn", "chǎn", "shǎn", "rǎn", "gǎn", "kǎn", "hǎn",
            "ěn", "běn", "pěn", "měn", "fěn", "děn", "něn", "zěn", "cěn", "sěn", "zhěn", "chěn", "shěn", "rěn", "gěn", "kěn", "hěn",
            "ǎng", "bǎng", "pǎng", "mǎng", "fǎng", "dǎng", "tǎng", "nǎng", "lǎng", "zǎng", "cǎng", "sǎng", "zhǎng", "chǎng", "shǎng", "rǎng", "gǎng", "kǎng", "hǎng",
            "ěng", "běng", "pěng", "měng", "fěng", "děng", "těng", "něng", "lěng", "zěng", "cěng", "sěng", "zhěng", "chěng", "shěng", "rěng", "gěng", "kěng", "hěng",
            "dǒng", "tǒng", "nǒng", "lǒng", "zǒng", "cǒng", "sǒng", "zhǒng", "chǒng", "rǒng", "gǒng", "kǒng", "hǒng",
            "yǐ", "bǐ", "pǐ", "mǐ", "dǐ", "tǐ", "nǐ", "lǐ", "jǐ", "qǐ", "xǐ",
            "yǎ", "liǎ", "jiǎ", "qiǎ", "xiǎ",
            "yǎo", "biǎo", "piǎo", "miǎo", "diǎo", "tiǎo", "niǎo", "liǎo", "jiǎo", "qiǎo", "xiǎo",
            "yě", "biě", "piě", "miě", "diě", "tiě", "niě", "liě", "jiě", "qiě", "xiě",
            "yǒu", "miǔ", "diǔ", "niǔ", "liǔ", "jiǔ", "qiǔ", "xiǔ",
            "yǎn", "biǎn", "piǎn", "miǎn", "diǎn", "tiǎn", "niǎn", "liǎn", "jiǎn", "qiǎn", "xiǎn",
            "yǐn", "bǐn", "pǐn", "mǐn", "nǐn", "lǐn", "jǐn", "qǐn", "xǐn",
            "yǎng", "niǎng", "liǎng", "jiǎng", "qiǎng", "xiǎng",
            "yǐng", "bǐng", "pǐng", "mǐng", "dǐng", "tǐng", "nǐng", "lǐng", "jǐng", "qǐng", "xǐng",
            "yǒng", "jiǒng", "qiǒng", "xiǒng",
            "wǔ", "bǔ", "pǔ", "mǔ", "fǔ", "dǔ", "tǔ", "nǔ", "lǔ", "zǔ", "cǔ", "sǔ", "zhǔ", "chǔ", "shǔ", "rǔ", "gǔ", "kǔ", "hǔ",
            "wǎ", "zhuǎ", "chuǎ", "shuǎ", "ruǎ", "guǎ", "kuǎ", "huǎ",
            "wǒ", "duǒ", "tuǒ", "nuǒ", "luǒ", "zuǒ", "cuǒ", "suǒ", "zhuǒ", "chuǒ", "shuǒ", "ruǒ", "guǒ", "kuǒ", "huǒ",
            "wǎi", "zhuǎi", "chuǎi", "shuǎi", "guǎi", "kuǎi", "huǎi",
            "wěi", "duǐ", "tuǐ", "zuǐ", "cuǐ", "suǐ", "zhuǐ", "chuǐ", "shuǐ", "ruǐ", "guǐ", "kuǐ", "huǐ",
            "wǎn", "duǎn", "tuǎn", "nuǎn", "luǎn", "zuǎn", "cuǎn", "suǎn", "zhuǎn", "chuǎn", "shuǎn", "ruǎn", "guǎn", "kuǎn", "huǎn",
            "wěn", "dǔn", "tǔn", "lǔn", "zǔn", "cǔn", "sǔn", "zhǔn", "chǔn", "shǔn", "rǔn", "gǔn", "kǔn", "hǔn",
            "wǎng", "zhuǎng", "chuǎng", "shuǎng", "guǎng", "kuǎng", "huǎng", "wěng",
            "yǔ", "nǚ", "lǚ", "jǔ", "qǔ", "xǔ",
            "yuě", "nüě", "lüě", "juě", "quě", "xuě",
            "yuǎn", "juǎn", "quǎn", "xuǎn",
            "yǔn", "jǔn", "qǔn", "xǔn"
        )

        @JvmField
        val pinyin_list_tone4 = arrayOf(
            "à", "bà", "pà", "mà", "fà", "dà", "tà", "nà", "là", "zà", "cà", "sà", "zhà", "chà", "shà", "gà", "kà", "hà",
            "ò", "bò", "pò", "mò", "fò",
            "mè", "dè", "tè", "nè", "lè", "zè", "cè", "sè", "zhè", "chè", "shè", "rè", "gè", "kè", "hè",
            "zì", "cì", "sì", "zhì", "chì", "shì", "rì",
            "è",
            "èr", "r",
            "ài", "bài", "pài", "mài", "dài", "tài", "nài", "lài", "zài", "cài", "sài", "zhài", "chài", "shài", "gài", "kài", "hài",
            "èi", "bèi", "pèi", "mèi", "fèi", "dèi", "nèi", "lèi", "zèi", "zhèi", "shèi", "gèi", "kèi", "hèi",
            "ào", "bào", "pào", "mào", "dào", "tào", "nào", "lào", "zào", "cào", "sào", "zhào", "chào", "shào", "rào", "gào", "kào", "hào",
            "òu", "pòu", "mòu", "fòu", "dòu", "tòu", "nòu", "lòu", "zòu", "còu", "sòu", "zhòu", "chòu", "shòu", "ròu", "gòu", "kòu", "hòu",
            "àn", "bàn", "pàn", "màn", "fàn", "dàn", "tàn", "nàn", "làn", "zàn", "càn", "sàn", "zhàn", "chàn", "shàn", "ràn", "gàn", "kàn", "hàn",
            "èn", "bèn", "pèn", "mèn", "fèn", "dèn", "nèn", "zèn", "cèn", "sèn", "zhèn", "chèn", "shèn", "rèn", "gèn", "kèn", "hèn",
            "àng", "bàng", "pàng", "màng", "fàng", "dàng", "tàng", "nàng", "làng", "zàng", "càng", "sàng", "zhàng", "chàng", "shàng", "ràng", "gàng", "kàng", "hàng",
            "èng", "bèng", "pèng", "mèng", "fèng", "dèng", "tèng", "nèng", "lèng", "zèng", "cèng", "sèng", "zhèng", "chèng", "shèng", "rèng", "gèng", "kèng", "hèng",
            "dòng", "tòng", "nòng", "lòng", "zòng", "còng", "sòng", "zhòng", "chòng", "ròng", "gòng", "kòng", "hòng",
            "yì", "bì", "pì", "mì", "dì", "tì", "nì", "lì", "jì", "qì", "xì",
            "yà", "lià", "jià", "qià", "xià",
            "yào", "biào", "piào", "miào", "diào", "tiào", "niào", "liào", "jiào", "qiào", "xiào",
            "yè", "biè", "piè", "miè", "diè", "tiè", "niè", "liè", "jiè", "qiè", "xiè",
            "yòu", "miù", "diù", "niù", "liù", "jiù", "qiù", "xiù",
            "yàn", "biàn", "piàn", "miàn", "diàn", "tiàn", "niàn", "liàn", "jiàn", "qiàn", "xiàn",
            "yìn", "bìn", "pìn", "mìn", "nìn", "lìn", "jìn", "qìn", "xìn",
            "yàng", "niàng", "liàng", "jiàng", "qiàng", "xiàng",
            "yìng", "bìng", "pìng", "mìng", "dìng", "tìng", "nìng", "lìng", "jìng", "qìng", "xìng",
            "yòng", "jiòng", "qiòng", "xiòng",
            "wù", "bù", "pù", "mù", "fù", "dù", "tù", "nù", "lù", "zù", "cù", "sù", "zhù", "chù", "shù", "rù", "gù", "kù", "hù",
            "wà", "zhuà", "chuà", "shuà", "ruà", "guà", "kuà", "huà",
            "wò", "duò", "tuò", "nuò", "luò", "zuò", "cuò", "suò", "zhuò", "chuò", "shuò", "ruò", "guò", "kuò", "huò",
            "wài", "zhuài", "chuài", "shuài", "guài", "kuài", "huài",
            "wèi", "duì", "tuì", "zuì", "cuì", "suì", "zhuì", "chuì", "shuì", "ruì", "guì", "kuì", "huì",
            "wàn", "duàn", "tuàn", "nuàn", "luàn", "zuàn", "cuàn", "suàn", "zhuàn", "chuàn", "shuàn", "ruàn", "guàn", "kuàn", "huàn",
            "wèn", "dùn", "tùn", "lùn", "zùn", "cùn", "sùn", "zhùn", "chùn", "shùn", "rùn", "gùn", "kùn", "hùn",
            "wàng", "zhuàng", "chuàng", "shuàng", "guàng", "kuàng", "huàng", "wèng",
            "yù", "nǜ", "lǜ", "jù", "qù", "xù",
            "yuè", "nüè", "lüè", "juè", "què", "xuè",
            "yuàn", "juàn", "quàn", "xuàn",
            "yùn", "jùn", "qùn", "xùn"
        )

        @JvmField
        val pinyin_list_tone5 = arrayOf(
            "a", "ba", "pa", "ma", "fa", "da", "ta", "na", "la", "za", "ca", "sa", "zha", "cha", "sha", "ga", "ka", "ha",
            "o", "bo", "po", "mo", "fo",
            "me", "de", "te", "ne", "le", "ze", "ce", "se", "zhe", "che", "she", "re", "ge", "ke", "he",
            "zi", "ci", "si", "zhi", "chi", "shi", "ri",
            "e",
            "er", "r",
            "ai", "bai", "pai", "mai", "dai", "tai", "nai", "lai", "zai", "cai", "sai", "zhai", "chai", "shai", "gai", "kai", "hai",
            "ei", "bei", "pei", "mei", "fei", "dei", "nei", "lei", "zei", "zhei", "shei", "gei", "kei", "hei",
            "ao", "bao", "pao", "mao", "dao", "tao", "nao", "lao", "zao", "cao", "sao", "zhao", "chao", "shao", "rao", "gao", "kao", "hao",
            "ou", "pou", "mou", "fou", "dou", "tou", "nou", "lou", "zou", "cou", "sou", "zhou", "chou", "shou", "rou", "gou", "kou", "hou",
            "an", "ban", "pan", "man", "fan", "dan", "tan", "nan", "lan", "zan", "can", "san", "zhan", "chan", "shan", "ran", "gan", "kan", "han",
            "en", "ben", "pen", "men", "fen", "den", "nen", "zen", "cen", "sen", "zhen", "chen", "shen", "ren", "gen", "ken", "hen",
            "ang", "bang", "pang", "mang", "fang", "dang", "tang", "nang", "lang", "zang", "cang", "sang", "zhang", "chang", "shang", "rang", "gang", "kang", "hang",
            "eng", "beng", "peng", "meng", "feng", "deng", "teng", "neng", "leng", "zeng", "ceng", "seng", "zheng", "cheng", "sheng", "reng", "geng", "keng", "heng",
            "dong", "tong", "nong", "long", "zong", "cong", "song", "zhong", "chong", "rong", "gong", "kong", "hong",
            "yi", "bi", "pi", "mi", "di", "ti", "ni", "li", "ji", "qi", "xi",
            "ya", "lia", "jia", "qia", "xia",
            "yao", "biao", "piao", "miao", "diao", "tiao", "niao", "liao", "jiao", "qiao", "xiao",
            "ye", "bie", "pie", "mie", "die", "tie", "nie", "lie", "jie", "qie", "xie",
            "you", "miu", "diu", "niu", "liu", "jiu", "qiu", "xiu",
            "yan", "bian", "pian", "mian", "dian", "tian", "nian", "lian", "jian", "qian", "xian",
            "yin", "bin", "pin", "min", "nin", "lin", "jin", "qin", "xin",
            "yang", "niang", "liang", "jiang", "qiang", "xiang",
            "ying", "bing", "ping", "ming", "ding", "ting", "ning", "ling", "jing", "qing", "xing",
            "yong", "jiong", "qiong", "xiong",
            "wu", "bu", "pu", "mu", "fu", "du", "tu", "nu", "lu", "zu", "cu", "su", "zhu", "chu", "shu", "ru", "gu", "ku", "hu",
            "wa", "zhua", "chua", "shua", "rua", "gua", "kua", "hua",
            "wo", "duo", "tuo", "nuo", "luo", "zuo", "cuo", "suo", "zhuo", "chuo", "shuo", "ruo", "guo", "kuo", "huo",
            "wai", "zhuai", "chuai", "shuai", "guai", "kuai", "huai",
            "wei", "dui", "tui", "zui", "cui", "sui", "zhui", "chui", "shui", "rui", "gui", "kui", "hui",
            "wan", "duan", "tuan", "nuan", "luan", "zuan", "cuan", "suan", "zhuan", "chuan", "shuan", "ruan", "guan", "kuan", "huan",
            "wen", "dun", "tun", "lun", "zun", "cun", "sun", "zhun", "chun", "shun", "run", "gun", "kun", "hun",
            "wang", "zhuang", "chuang", "shuang", "guang", "kuang", "huang", "weng",
            "yu", "nü", "lü", "ju", "qu", "xu",
            "yue", "nüe", "lüe", "jue", "que", "xue",
            "yuan", "juan", "quan", "xuan",
            "yun", "jun", "qun", "xun"
        )
    }
}

/**
 * Splits a string into individual Unicode code-point characters as 1- or 2-char Strings.
 * Correctly preserves supplementary plane characters (surrogate pairs like CJK Extension B+).
 */
fun String.toCodePointStrings(): List<String> {
    if (isEmpty()) return emptyList()
    val result = ArrayList<String>(length)
    var idx = 0
    while (idx < length) {
        val cp = codePointAt(idx)
        result.add(String(Character.toChars(cp)))
        idx += Character.charCount(cp)
    }
    return result
}
