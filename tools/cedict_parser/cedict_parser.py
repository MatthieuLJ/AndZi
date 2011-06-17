#!/usr/bin/python
# -*- coding: utf-8 -*-

import sys
import re
import codecs
import string

pinyin_radicals=("a", "ba", "pa", "ma", "fa", "da", "ta", "na", "la", "za", "ca", "sa", "zha", "cha", "sha", "ga", "ka", "ha",
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
        "yun", "jun", "qun", "xun")

def check_chinese_character(c):
    if ((c < u'\u2e00') or (c > u'\u31ff')) and ((c < u'\u3400') or (c > u'\u4dbf')) and ((c < u'\u4e00') or (c > u'\u9fff')):
        #print "wrong chinese character in ", chinese, " index ", i
        return False
    return True

def check_chinese(chinese):
    """check there is only chinese characters in the list"""
    for i in range(0, len(chinese)):
        c=chinese[i]
        if not check_chinese_character(c):
            #print "wrong chinese character in ", chinese, " index ", i
            return False
    return True

def check_pinyins(pinyin):
    """check for valid pinyin (all radicals with tones)"""
    for i in range(0, len(pinyin)):
        p=pinyin[i]
        if (p.find("u:") != -1):
            pinyin[i]=p.replace("u:", "v")
            p=pinyin[i]
        if (p not in pinyin_radicals) and (p[:-1] not in pinyin_radicals):
            #print p, " does not look like a valid pinyin"
            return False
        if (p in pinyin_radicals):
            pinyin[i] = p+"5"
    return True

def check_translations(translations):
    """remove some stuff we do not need"""
    new_t=[]
    for i in range(0, len(translations)):
        t=translations[i]
        if (re.match("erhua variant of", t) is not None):
            continue
        if (re.match("Taiwan pr.", t) is not None):
            continue
        if (re.match("CL:", t) is not None):
            continue
        if (re.match("old varia", t) is not None):
            continue
        if (re.match("trad.", t) is not None):
            continue
        if (re.match("variant of", t) is not None):
            continue
        if (re.match("archaic variant of", t) is not None):
            continue
        if (re.match("Japanese variant", t) is not None):
            continue
        if (re.match("abbr. of", t) is not None):
            continue
        if (re.match("abbr. for", t) is not None):
            continue
        if (re.match("also written", t) is not None):
            continue
        m = re.match("see (.).+", t)
        if (m is not None) and check_chinese_character(m.groups()[0]):
            continue
        m = re.match("same as (.).+", t)
        if (m is not None) and check_chinese_character(m.groups()[0]):
            continue
        m = re.match("see also (.).+", t)
        if (m is not None) and check_chinese_character(m.groups()[0]):
            continue
        # TODO :replace "traditional|simplified[pinyins]" with "simplified" or "chinese[pinyins]" with "chinese"

        new_t.append(t)
    return new_t

def filter_out(chinese, pinyin, translations):
    """Filter out some Cedict entries (non-chinese characters, wrong pinyins, erhua variants...etc...)"""

    if (len(chinese) != len(pinyin)):
        return
    if (not check_chinese(chinese)):
        return
    if (not check_pinyins(pinyin)):
        return
    new_translations = check_translations(translations)
    if (len(new_translations) == 0):
        return

    return (chinese, pinyin, new_translations)


class CedictParser:
    """A class that can go through a cedict file and iterate through the lines one by one"""

    def __init__(self, filename):
        self.filename=filename
        try:
            self.file = codecs.open(filename, "r", "utf-8")
        except IOError:
            print "cannot open file ",filename
            sys.exit()
        self.current_line=0

    def total_lines(self):
        with open(self.filename, "r") as f:
            for i, l in enumerate(f):
                pass
            return i+1

    def get_current_line(self):
        return self.current_line

    def __iter__(self):
        return self

    def next(self):
        # match a line
        while True:
            while True:
                m=re.match(r"(.+) .+ \[(.+)\] (.+)", self.file.next())
                self.current_line=self.current_line+1
                if m is not None:
                     break

            # extract the different parts
            chinese=[i for i in m.groups()[0]]
            pinyins=m.groups()[1].split(" ")
            try:
                pinyins=[str(i).lower() for i in pinyins]
            except UnicodeEncodeError:
                continue
            translations=m.groups()[2].split("/")[1:-1]

            res = filter_out(chinese, pinyins, translations)
            if (res is not None):
                break;

        return res
    
if __name__ == "__main__":
    if len(sys.argv) != 2:
        print "not enough argument (give me the name of the cedict file)"
        sys.exit()
    parser = CedictParser(sys.argv[1])
    total_lines = parser.total_lines()
    for voc in parser:
        #sys.stdout.write("progress : "+ str(parser.get_current_line())+ " / "+ str(total_lines)+ "\r")
        print voc
    print ""
