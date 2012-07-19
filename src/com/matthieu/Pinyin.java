/******************************
 * Class for Pinyin, holds basic Pinyin structures
 * and provides different formatting of the same pinyin
 * Also offers static function to verify a pinyin
 */
package com.matthieu;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class Pinyin {
    private String lame="";
    private String radical="";
    private int tone=-1;
    private String print="";
    private String html_color_print="";

    private static boolean map_initialized=false;

    private static final Object init_sync = new Object();

    private static Map<String, ArrayList<String>> pinyin_by_first_letter=null;
    private static Map<String, Integer> vowels_to_tone=null;
    private static Map<String, String> lame_vowels=null;
    private static Map<String, ArrayList<String>> similar_radicals=null;

    public  Pinyin(String entry) {
        int i,j;
        String temp;

        if (!verify_pinyin(entry))
            return;

        for (i = 0; i < 6; i++) {
            if (entry.endsWith(""+i)) {
                temp=entry.substring(0, entry.length()-1);
                for (j = 0; j < pinyin_list_notone.length; j++) {
                    if (temp.equals(pinyin_list_notone[j])) {
                        this.lame = temp+i;
                        this.radical = temp;
                        this.tone = i;
                        if (i==0)
                            this.tone=5;
                        switch(tone) {
                            case 5:
                                this.print=pinyin_list_tone5[j];
                                this.html_color_print=this.print;
                                break;
                            case 1:
                                this.print=pinyin_list_tone1[j];
                                this.html_color_print="<font color=\"red\">"+this.print+"</font>";
                                break;
                            case 2:
                                this.print=pinyin_list_tone2[j];
                                this.html_color_print="<font color=\"#ff8000\">"+this.print+"</font>";
                                break;
                            case 3:
                                this.print=pinyin_list_tone3[j];
                                this.html_color_print="<font color=\"green\">"+this.print+"</font>";
                                break;
                            case 4:
                                this.print=pinyin_list_tone4[j];
                                this.html_color_print="<font color=\"blue\">"+this.print+"</font>";
                                break;
                        }
                        return;
                    }
                }
            }
        }
        for (String v:vowels_to_tone.keySet()) {
            if (entry.contains(v)) {
                this.tone = vowels_to_tone.get(v);
                this.print = entry;
                switch(tone) {
                    case 1:
                        this.html_color_print="<font color=\"red\">"+this.print+"</font>";
                        break;
                    case 2:
                        this.html_color_print="<font color=\"#ff8000\">"+this.print+"</font>";
                        break;
                    case 3:
                        this.html_color_print="<font color=\"green\">"+this.print+"</font>";
                        break;
                    case 4:
                        this.html_color_print="<font color=\"blue\">"+this.print+"</font>";
                        break;
                }
                this.radical=entry.replaceAll(v, lame_vowels.get(v));
                this.lame = this.radical + this.tone;
                return;
            }
        }

        for (i = 0; i < pinyin_list_notone.length; i++) {
            if ((entry.equals(pinyin_list_notone[i])) || (entry.equals(pinyin_list_tone5[i]))) {
                this.lame=pinyin_list_notone[i]+"5";
                this.radical=pinyin_list_notone[i];
                this.tone=0;
                this.print=pinyin_list_tone5[i];
                this.html_color_print=pinyin_list_tone5[i];
                return;
            }
        }
    }

    public Pinyin(String entry, boolean incomplete) {
        if (entry.length()==0)
            return;

        prepare_pinyin_process();

        String initial = entry.substring(0,1);
        if (!pinyin_by_first_letter.containsKey(initial))
            return;

        for (String candidate:pinyin_by_first_letter.get(initial)) {
            if (candidate.startsWith(entry)) {
                for (String v:vowels_to_tone.keySet()) {
                    if (entry.contains(v)) {
                        this.tone = vowels_to_tone.get(v);
                        this.lame = entry.replaceAll(v, lame_vowels.get(v));
                        return;
                    }
                }
                this.tone=0;
                this.lame=entry;
                return;

            }
        }
    }

    public float distanceTo(Pinyin other) {
        if ((tone==-1) || (other.tone==-1))
            return -1;

        if (html_color_print.equals(other.html_color_print))
            return 0;

        // same radical different tone ?
        if (radical.equals(other.radical))
            return 1;

        int diff=1;
        if (!get_initial().equals(other.get_initial())) {
            if (similar_radicals.get(get_initial()).contains(other.get_initial()))
                diff++;
            else
                diff += 2;
        }
        if (!get_final().equals(other.get_final())) {
        if (similar_radicals.get(get_final()).contains(other.get_final()))
            diff++;
        else
            diff += 2;
        }
        if (get_tone() != other.get_tone())
            diff++;

        return diff;
    }

    public float distanceTo(ArrayList<Pinyin> others) {
        float min=-1;
        for (Pinyin p : others) {
            float dist = distanceTo(p);
            if ((min==-1) || (dist<min))
                min=dist;
        }
        return min;
    }

    public static float distanceBetween(ArrayList<Pinyin> list1, ArrayList<Pinyin> list2) {
        float min=-1;
        for (Pinyin p : list1) {
            float dist = p.distanceTo(list2);
            if ((min==-1) || (dist<min))
                min=dist;
        }
        return min;
    }

    public static void prepare_pinyin_process() {
        synchronized (init_sync) {
            if (!map_initialized) {
                create_pinyin_map();
                create_similar_pinyin_map();
            }
        }
    }

    private static void create_pinyin_map() {
        String first;
        ArrayList<String> list;

        if (map_initialized)
            return;

        pinyin_by_first_letter = new HashMap<String, ArrayList<String>>();

        for (String pin:pinyin_list_notone) {
            first = pin.substring(0,1);
            if (pinyin_by_first_letter.containsKey(first)) {
                list = pinyin_by_first_letter.get(first);
                list.add(pin);
            }
            else {
                list = new ArrayList<String>();
                list.add(pin);
                pinyin_by_first_letter.put(first, list);
            }
        }
        for (String pin:pinyin_list_tone1) {
            first = pin.substring(0,1);
            if (pinyin_by_first_letter.containsKey(first)) {
                list = pinyin_by_first_letter.get(first);
                list.add(pin);
            }
            else {
                list = new ArrayList<String>();
                list.add(pin);
                pinyin_by_first_letter.put(first, list);
            }
        }
        for (String pin:pinyin_list_tone2) {
            first = pin.substring(0,1);
            if (pinyin_by_first_letter.containsKey(first)) {
                list = pinyin_by_first_letter.get(first);
                list.add(pin);
            }
            else {
                list = new ArrayList<String>();
                list.add(pin);
                pinyin_by_first_letter.put(first, list);
            }
        }
        for (String pin:pinyin_list_tone3) {
            first = pin.substring(0,1);
            if (pinyin_by_first_letter.containsKey(first)) {
                list = pinyin_by_first_letter.get(first);
                list.add(pin);
            }
            else {
                list = new ArrayList<String>();
                list.add(pin);
                pinyin_by_first_letter.put(first, list);
            }
        }
        for (String pin:pinyin_list_tone4) {
            first = pin.substring(0,1);
            if (pinyin_by_first_letter.containsKey(first)) {
                list = pinyin_by_first_letter.get(first);
                list.add(pin);
            }
            else {
                list = new ArrayList<String>();
                list.add(pin);
                pinyin_by_first_letter.put(first, list);
            }
        }
        for (String pin:pinyin_list_tone5) {
            first = pin.substring(0,1);
            if (pinyin_by_first_letter.containsKey(first)) {
                list = pinyin_by_first_letter.get(first);
                list.add(pin);
            }
            else {
                list = new ArrayList<String>();
                list.add(pin);
                pinyin_by_first_letter.put(first, list);
            }
        }

        vowels_to_tone = new HashMap<String, Integer>();
        final String[] first_tone_vowels = {"ā", "ō", "ē", "ī", "ū", "ǖ"};
        final String[] second_tone_vowels = {"á", "ó", "é", "í", "ú", "ǘ"};
        final String[] third_tone_vowels = {"ǎ", "ǒ", "ě", "ǐ", "ǔ", "ǚ"};
        final String[] fourth_tone_vowels = {"à", "ò", "è", "ì", "ù", "ǜ"};
        for(String s:first_tone_vowels)
            vowels_to_tone.put(s,1);
        for(String s:second_tone_vowels)
            vowels_to_tone.put(s,2);
        for(String s:third_tone_vowels)
            vowels_to_tone.put(s,3);
        for(String s:fourth_tone_vowels)
            vowels_to_tone.put(s,4);

        lame_vowels = new HashMap<String, String>();
        final String[] a_vowels={"ā", "á", "ǎ", "à"};
        final String[] o_vowels={"ō", "ó", "ǒ", "ò"};
        final String[] e_vowels={"ē", "é", "ě", "è"};
        final String[] i_vowels={"ī", "í", "ǐ", "ì"};
        final String[] u_vowels={"ū", "ú", "ǔ", "ù"};
        final String[] v_vowels={"ǖ", "ǘ", "ǚ", "ǜ"};
        for(String s:a_vowels)
            lame_vowels.put(s, "a");
        for(String s:o_vowels)
            lame_vowels.put(s, "o");
        for(String s:e_vowels)
            lame_vowels.put(s, "e");
        for(String s:i_vowels)
            lame_vowels.put(s, "i");
        for(String s:u_vowels)
            lame_vowels.put(s, "u");
        for(String s:v_vowels)
            lame_vowels.put(s, "v");

        map_initialized=true;
    }

    private static void create_similar_pinyin_map() {
        similar_radicals = new HashMap<String, ArrayList<String>>();

        //finals
        similar_radicals.put("a", new ArrayList<String>(Arrays.asList("ai", "ao", "an")));
        similar_radicals.put("o", new ArrayList<String>(Arrays.asList("ao", "ou")));
        similar_radicals.put("e", new ArrayList<String>(Arrays.asList("ei", "en", "ie", "ve")));
        similar_radicals.put("ai", new ArrayList<String>(Arrays.asList("a", "ian", "uai")));
        similar_radicals.put("ei", new ArrayList<String>(Arrays.asList("e", "ie")));
        similar_radicals.put("ao", new ArrayList<String>(Arrays.asList("a", "o", "iao")));
        similar_radicals.put("ou", new ArrayList<String>(Arrays.asList("o", "uo", "u")));
        similar_radicals.put("an", new ArrayList<String>(Arrays.asList("a", "ang", "uan")));
        similar_radicals.put("ang", new ArrayList<String>(Arrays.asList("an", "iang")));
        similar_radicals.put("en", new ArrayList<String>(Arrays.asList("e", "eng")));
        similar_radicals.put("eng", new ArrayList<String>(Arrays.asList("en")));
        similar_radicals.put("ong", new ArrayList<String>(Arrays.asList("iong")));
        similar_radicals.put("u", new ArrayList<String>(Arrays.asList("ou", "uo", "iu", "v")));
        similar_radicals.put("ua", new ArrayList<String>(Arrays.asList("uan")));
        similar_radicals.put("uo", new ArrayList<String>(Arrays.asList("u", "ou")));
        similar_radicals.put("uai", new ArrayList<String>(Arrays.asList("ai")));
        similar_radicals.put("ui", new ArrayList<String>(Arrays.asList("i")));
        similar_radicals.put("uan", new ArrayList<String>(Arrays.asList("an", "ua", "uang", "van")));
        similar_radicals.put("uang", new ArrayList<String>(Arrays.asList("uan")));
        similar_radicals.put("un", new ArrayList<String>(Arrays.asList("ueng", "vn")));
        similar_radicals.put("ueng", new ArrayList<String>(Arrays.asList("un")));
        similar_radicals.put("i", new ArrayList<String>(Arrays.asList("ui")));
        similar_radicals.put("ia", new ArrayList<String>(Arrays.asList("ian")));
        similar_radicals.put("ie", new ArrayList<String>(Arrays.asList("e", "ei")));
        similar_radicals.put("iao", new ArrayList<String>(Arrays.asList("ao")));
        similar_radicals.put("iu", new ArrayList<String>(Arrays.asList("u")));
        similar_radicals.put("ian", new ArrayList<String>(Arrays.asList("ia", "ai", "iang")));
        similar_radicals.put("iang", new ArrayList<String>(Arrays.asList("ian", "ang")));
        similar_radicals.put("in", new ArrayList<String>(Arrays.asList("ing")));
        similar_radicals.put("ing", new ArrayList<String>(Arrays.asList("in")));
        similar_radicals.put("iong", new ArrayList<String>(Arrays.asList("ong")));
        similar_radicals.put("v", new ArrayList<String>(Arrays.asList("u")));
        similar_radicals.put("ve", new ArrayList<String>(Arrays.asList("e")));
        similar_radicals.put("van", new ArrayList<String>(Arrays.asList("uan")));
        similar_radicals.put("vn", new ArrayList<String>(Arrays.asList("un")));

        // initials
        similar_radicals.put("b", new ArrayList<String>(Arrays.asList("p")));
        similar_radicals.put("p", new ArrayList<String>(Arrays.asList("b")));
        similar_radicals.put("m", new ArrayList<String>(Arrays.asList("n")));
        similar_radicals.put("f", new ArrayList<String>());
        similar_radicals.put("d", new ArrayList<String>(Arrays.asList("t")));
        similar_radicals.put("t", new ArrayList<String>(Arrays.asList("d")));
        similar_radicals.put("n", new ArrayList<String>(Arrays.asList("m")));
        similar_radicals.put("l", new ArrayList<String>());
        similar_radicals.put("g", new ArrayList<String>(Arrays.asList("k")));
        similar_radicals.put("k", new ArrayList<String>(Arrays.asList("g")));
        similar_radicals.put("h", new ArrayList<String>(Arrays.asList("", "r")));
        similar_radicals.put("z", new ArrayList<String>(Arrays.asList("zh", "j")));
        similar_radicals.put("c", new ArrayList<String>(Arrays.asList("ch", "q")));
        similar_radicals.put("s", new ArrayList<String>(Arrays.asList("sh", "x")));
        similar_radicals.put("zh", new ArrayList<String>(Arrays.asList("z", "j")));
        similar_radicals.put("ch", new ArrayList<String>(Arrays.asList("c", "q")));
        similar_radicals.put("sh", new ArrayList<String>(Arrays.asList("s", "x")));
        similar_radicals.put("r", new ArrayList<String>(Arrays.asList("", "h")));
        similar_radicals.put("j", new ArrayList<String>(Arrays.asList("z", "zh")));
        similar_radicals.put("q", new ArrayList<String>(Arrays.asList("c", "ch")));
        similar_radicals.put("x", new ArrayList<String>(Arrays.asList("s", "sh")));
        similar_radicals.put("", new ArrayList<String>(Arrays.asList("h", "r")));
    }

    private String get_initial() {
        String two_letters_initials[] = {"ch", "sh", "zh"};
        for (String i:two_letters_initials)
            if (radical.startsWith(i))
                return i;

        if ((radical.startsWith("w")) || (radical.startsWith("y")))
            return "";
        if ((radical.startsWith("a")) || (radical.startsWith("o")) || (radical.startsWith("e")))
            return "";

        return radical.substring(0, 1);
    }

    private String get_final() {
        String two_letters_initials[] = {"ch", "sh", "zh"};
        String res="";
        for (String i:two_letters_initials)
            if (radical.startsWith(i))
                return radical.substring(2);

        if ((radical.startsWith("w")) || (radical.startsWith("y"))) {
            if (radical.equals("wu"))
                return "u";
            if (radical.equals("wa"))
                return "ua";
            if (radical.equals("wo"))
                return "uo";
            if (radical.equals("wai"))
                return "uai";
            if (radical.equals("wei"))
                return "ui";
            if (radical.equals("wan"))
                return "uan";
            if (radical.equals("wang"))
                return "uang";
            if (radical.equals("wen"))
                return "un";
            if (radical.equals("weng"))
                return "ueng";
            if (radical.equals("yi"))
                return "i";
            if (radical.equals("ya"))
                return "ia";
            if (radical.equals("ye"))
                return "ie";
            if (radical.equals("yao"))
                return "iao";
            if (radical.equals("you"))
                return "iu";
            if (radical.equals("yan"))
                return "ian";
            if (radical.equals("yang"))
                return "iang";
            if (radical.equals("yin"))
                return "in";
            if (radical.equals("ying"))
                return "ing";
            if (radical.equals("yong"))
                return "iong";
            if (radical.equals("yu"))
                return "v";
            if (radical.equals("yue"))
                return "ve";
            if (radical.equals("yuan"))
                return "van";
            if (radical.equals("yun"))
                return "vn";
        }
        if ((radical.startsWith("a")) || (radical.startsWith("o")) || (radical.startsWith("e")))
            return radical;
        if (((radical.startsWith("j")) || (radical.startsWith("q")) || (radical.startsWith("x"))) && (radical.substring(1,2).equals("u"))) {
            return "v"+radical.substring(2);
        }

        return radical.substring(1);
    }

    public static boolean verify_pinyin(String entry) {
        int i;
        String temp="";

        prepare_pinyin_process();

        if (entry.length()==0)
            return false;

        for (i = 0; i < 6; i++) {
            if (entry.endsWith(""+i)) {
                temp=entry.substring(0, entry.length()-1).toLowerCase();
            }
        }
        if (temp.equals(""))
            temp=entry.toLowerCase();

        if (!pinyin_by_first_letter.containsKey(temp.substring(0,1)))
            return false;

        return pinyin_by_first_letter.get(temp.substring(0, 1)).contains(temp);

    }

    public int get_tone () {
        return tone==0?5:tone;
    }

    public String get_lame() {
        return lame;
    }

    public String get_radical() {
        return radical;
    }

    public String get_pretty() {
        return print;
    }

    public String get_color() {
        return html_color_print;
    }

    public boolean equals(Pinyin pinyin) {
        return (pinyin.get_lame().equals(get_lame())) && (pinyin.get_tone() == get_tone());
    }

    public boolean tone_is_given() {
        return !(tone==0);
    }

    private static final String[] pinyin_list_notone = {
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
    };
    private static final String[] pinyin_list_tone1 = {
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
    };
    private static final String[] pinyin_list_tone2 = {
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
    };
    private static final String[] pinyin_list_tone3 = {
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
    };
    private static final String[] pinyin_list_tone4 = {
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
            "yǜ", "nǜ", "lǜ", "jǜ", "qù", "xù",
            "yuè", "nüè", "lüè", "juè", "què", "xuè",
            "yuàn", "juàn", "quàn", "xuàn",
            "yǜn", "jǜn", "qǜn", "xǜn"
    };
    private static final String[] pinyin_list_tone5 = {
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
    };
}