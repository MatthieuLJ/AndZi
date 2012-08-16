package com.matthieu;

import android.test.AndroidTestCase;
import android.util.Log;
import java.util.ArrayList;

/**
 * User: matthieu
 * Date: 6/1/11
 * Time: 4:23 PM
 */
public class PinyinParserTest extends AndroidTestCase {
    private static final String TAG="PinyinParserTest";

    @Override
    protected void setUp() {
        Log.d(TAG, " is setup");
    }

    @Override
    protected void tearDown() {
    }

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
        assertTrue(res.get(0).type==HanziDB.StringElement.PINYIN_WITH_TONE);
        assertTrue(res.get(0).content.equals("zen4"));
        assertTrue(res.get(1).type==HanziDB.StringElement.PINYIN_WITHOUT_TONE);
        assertTrue(res.get(1).content.equals("fa"));
        assertTrue(res.get(2).type==HanziDB.StringElement.PINYIN_WITH_TONE);
        assertTrue(res.get(2).content.equals("jian2"));
    }

    public void testMixedElements() {
        ArrayList<HanziDB.StringElement> res;
        res = PinyinParser.parse_mixed_string("中guo2不见dianhuazuiting不dong3fengfe");
        assertTrue(res != null);
        assertTrue(res.size() == 12);
        assertTrue(res.get(0).type==HanziDB.StringElement.CHINESE);
        assertTrue(res.get(0).content.equals("中"));
        assertTrue(res.get(5).type==HanziDB.StringElement.PINYIN_WITHOUT_TONE);
        assertTrue(res.get(5).content.equals("hua"));
        assertTrue(res.get(9).type==HanziDB.StringElement.PINYIN_WITH_TONE);
        assertTrue(res.get(9).content.equals("dong3"));
        assertTrue(res.get(11).type==HanziDB.StringElement.INCOMPLETE_PINYIN);
        assertTrue(res.get(11).content.equals("fe"));
    }

    public void testComparePinyins() {
        Pinyin wang3 = new Pinyin("wang3");
        Pinyin ang2 = new Pinyin("ang2");
        Pinyin bang2 = new Pinyin("bang2");
        Pinyin yi1 = new Pinyin("yi1");
        Pinyin ji1 = new Pinyin("ji1");
        Pinyin ju3 = new Pinyin("ju3");
        Pinyin pen5 = new Pinyin("pen5");
        Pinyin man3 = new Pinyin("man3");
        Pinyin pen3 = new Pinyin("pen3");
        Pinyin mang3 = new Pinyin("mang3");

        assertTrue(wang3.distanceTo(ang2)==4);
        assertTrue(ju3.distanceTo(yi1)==6);
        assertTrue(yi1.distanceTo(ji1)==3);
        assertTrue(ang2.distanceTo(bang2)==3);
        assertTrue(pen5.distanceTo(wang3)==6);
        assertTrue(man3.distanceTo(ang2)==5);
        assertTrue(man3.distanceTo(man3)==0);
        assertTrue(pen3.distanceTo(pen5)==1);
        assertTrue(wang3.distanceTo(mang3)==5);
    }
}
