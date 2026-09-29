package com.matthieu.zi_app;

import junit.framework.TestCase;
import java.util.ArrayList;

/**
 * User: matthieu
 * Date: 6/1/11
 * Time: 4:23 PM
 */
public class PinyinParserTest extends TestCase {

    public void testSimple() {
        ArrayList<String> res=PinyinParser.parse_string("ni3hao3");
        assertTrue(res.size() == 2);
        assertTrue(res.get(0).equals("ni3"));
        assertTrue(res.get(1).equals("hao3"));
    }

    public void testNoTone() {
        ArrayList<String> res=PinyinParser.parse_string("nihao");
        assertTrue(res.size() == 2);
        assertTrue(res.get(0).equals("ni5"));
        assertTrue(res.get(1).equals("hao5"));
    }

    public void testTrickier() {
        ArrayList<String> res=PinyinParser.parse_string("nangong");
        assertTrue(res.size() == 2 );
        assertTrue(res.get(0).equals("nan5"));
        assertTrue(res.get(1).equals("gong5"));
    }

    public void testElementsSimple() {
        ArrayList<HanziDB.StringElement> res;
        res = PinyinParser.parse_string_to_elements("zènfajian2");
        assertTrue(res != null);
        assertTrue(res.size()==3);
        assertTrue(res.get(0).type==HanziDB.StringElement.type_t.PINYIN_WITH_TONE);
        assertTrue(res.get(0).content.equals("zen4"));
        assertTrue(res.get(1).type==HanziDB.StringElement.type_t.PINYIN_WITHOUT_TONE);
        assertTrue(res.get(1).content.equals("fa"));
        assertTrue(res.get(2).type==HanziDB.StringElement.type_t.PINYIN_WITH_TONE);
        assertTrue(res.get(2).content.equals("jian2"));
    }

    public void testMixedElements() {
        ArrayList<HanziDB.StringElement> res;
        res = PinyinParser.parse_mixed_string("中guo2不见dianhuazuiting不dong3fengfe");
        assertTrue(res != null);
        assertTrue(res.size() == 12);
        assertTrue(res.get(0).type==HanziDB.StringElement.type_t.CHINESE);
        assertTrue(res.get(0).content.equals("中"));
        assertTrue(res.get(5).type==HanziDB.StringElement.type_t.PINYIN_WITHOUT_TONE);
        assertTrue(res.get(5).content.equals("hua"));
        assertTrue(res.get(9).type==HanziDB.StringElement.type_t.PINYIN_WITH_TONE);
        assertTrue(res.get(9).content.equals("dong3"));
        assertTrue(res.get(11).type==HanziDB.StringElement.type_t.INCOMPLETE_PINYIN);
        assertTrue(res.get(11).content.equals("fe"));
    }

    public void testCaching() {
        ArrayList<HanziDB.StringElement> res;
        PinyinParser.parse_mixed_string("ma");
        PinyinParser.parse_mixed_string("mama");
        PinyinParser.parse_mixed_string("mamah");
        PinyinParser.parse_mixed_string("mamahuh");
        res = PinyinParser.parse_mixed_string("mamahuhu");
        assertTrue(res != null);
        assertTrue(res.size()==4);
    }
}
