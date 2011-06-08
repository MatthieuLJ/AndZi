package com.matthieu;

import android.content.ContextWrapper;
import android.test.AndroidTestCase;
import android.util.Log;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * User: matthieu
 * Date: 5/24/11
 * Time: 5:54 PM
 */
public class HanziDBTest extends AndroidTestCase {

    private static final String TAG = "HanziDBTest";
    protected static final String DbName = "test_db";
    private HanziDB target;

    @Override
    protected void setUp() {
        ContextWrapper cw = new ContextWrapper(this.getContext());
        cw.deleteDatabase(DbName);
        target = new HanziDB(this.getContext(),  DbName);
        Log.d(TAG, " is setup");
    }

    protected HanziDB getDB() {
        return target;
    }


    @Override
    protected void tearDown() {
        getDB().close();
    }

    public void testSimpleAdd() {
        getDB().addVocabulary("hello", new ArrayList<String>(Arrays.asList("你", "好")), new ArrayList<String>(Arrays.asList("ni3", "hao3")));
        assertTrue(HanziDB.combineListOfTranslations(getDB().getTranslations("你好")).contains("hello"));
        assertTrue(getDB().checkDatabase());
    }

    public void testBadSizeAdd() {
        getDB().addVocabulary("hello_bad", new ArrayList<String>(Arrays.asList("你", "好")), new ArrayList<String>(Arrays.asList("ni3")));
        assertFalse(HanziDB.combineListOfTranslations(getDB().getTranslations("你好")).contains("hello_bad"));
        assertTrue(getDB().checkDatabase());
    }

    public void testAddSameTranslation() {
        getDB().addVocabulary("west wind", new ArrayList<String>(Arrays.asList("西", "风")), new ArrayList<String>(Arrays.asList("xi1", "feng1")));
        getDB().addVocabulary("west wind", new ArrayList<String>(Arrays.asList("西", "风")), new ArrayList<String>(Arrays.asList("xi1", "feng1")));
        ArrayList<Vocabulary> res = getDB().getTranslations("西风");
        assertTrue(res.size()==1);
        assertTrue(res.get(0).english.size() == 1);
        assertTrue(getDB().checkDatabase());
    }

    public void testMultipleAdd() {
        getDB().addVocabulary(new ArrayList<String>(Arrays.asList("good", "well", "perfect")), new ArrayList<String>(Arrays.asList("好")), new ArrayList<String>(Arrays.asList("hao3")));
        assertTrue(HanziDB.combineListOfTranslations(getDB().getTranslations("好")).contains("good"));
        assertTrue(HanziDB.combineListOfTranslations(getDB().getTranslations("好")).contains("well"));
        assertTrue(HanziDB.combineListOfTranslations(getDB().getTranslations("好")).contains("perfect"));
        assertTrue(getDB().checkDatabase());
    }

    public void testVocabularyAdd() {
        getDB().addVocabulary(new Vocabulary("风", "feng1", new ArrayList<String>(Arrays.asList("wind", "custom"))));
        assertTrue(HanziDB.combineListOfTranslations(getDB().getTranslations("风")).contains("wind"));
        assertTrue(HanziDB.combineListOfTranslations(getDB().getTranslations("风")).contains("custom"));
        assertTrue(getDB().checkDatabase());
    }

    public void testOneCharMultipleWords() {
        getDB().addVocabulary("grow", new ArrayList<String>(Arrays.asList("长")), new ArrayList<String>(Arrays.asList("zhang3")));
        getDB().addVocabulary("long", new ArrayList<String>(Arrays.asList("长")), new ArrayList<String>(Arrays.asList("chang2")));
        assertTrue(HanziDB.combineListOfTranslations(getDB().getTranslations("长","zhang3")).contains("grow"));
        assertFalse(HanziDB.combineListOfTranslations(getDB().getTranslations("长","zhang3")).contains("long"));
        assertTrue(HanziDB.combineListOfTranslations(getDB().getTranslations("长","chang2")).contains("long"));
        assertFalse(HanziDB.combineListOfTranslations(getDB().getTranslations("长","chang2")).contains("grow"));
        assertTrue(getDB().checkDatabase());
    }

    public void testMultipleChinese() {
        getDB().addVocabulary("big",new ArrayList<String>(Arrays.asList("大")), new ArrayList<String>(Arrays.asList("da4")));
        getDB().addVocabulary("big",new ArrayList<String>(Arrays.asList("伟")), new ArrayList<String>(Arrays.asList("wei3")));
        assertTrue(getDB().getCTranslations("big").size()==2);
        assertTrue(getDB().checkDatabase());
    }

    public void testSimpleDelete() {
        getDB().addVocabulary("small",new ArrayList<String>(Arrays.asList("小")), new ArrayList<String>(Arrays.asList("xiao3")));
        assertTrue(HanziDB.combineListOfTranslations(getDB().getTranslations("小")).contains("small"));
        getDB().delVocabulary("small",new ArrayList<String>(Arrays.asList("小")), new ArrayList<String>(Arrays.asList("xiao3")));
        assertFalse(HanziDB.combineListOfTranslations(getDB().getTranslations("小")).contains("small"));
        assertTrue(getDB().checkDatabase());
    }

    public void testMultipleDelete() {
        getDB().addVocabulary(new ArrayList<String>(Arrays.asList("bright", "next", "wise")),new ArrayList<String>(Arrays.asList("明")), new ArrayList<String>(Arrays.asList("ming2")));
        assertTrue(HanziDB.combineListOfTranslations(getDB().getTranslations("明")).contains("bright"));
        assertTrue(HanziDB.combineListOfTranslations(getDB().getTranslations("明")).contains("wise"));
        getDB().delVocabulary(new ArrayList<String>(Arrays.asList("明")), new ArrayList<String>(Arrays.asList("ming2")));
        assertFalse(HanziDB.combineListOfTranslations(getDB().getTranslations("明")).contains("bright"));
        assertFalse(HanziDB.combineListOfTranslations(getDB().getTranslations("明")).contains("next"));
        assertFalse(HanziDB.combineListOfTranslations(getDB().getTranslations("明")).contains("wise"));
        assertTrue(getDB().checkDatabase());
    }

    public void testLeftOverFromDelete() {
        getDB().addVocabulary("sky",new ArrayList<String>(Arrays.asList("天")), new ArrayList<String>(Arrays.asList("tian1")));
        getDB().addVocabulary("today",new ArrayList<String>(Arrays.asList("今","天")), new ArrayList<String>(Arrays.asList("jin1","tian1")));
        assertTrue(HanziDB.combineListOfTranslations(getDB().getTranslations("天")).contains("sky"));
        getDB().delVocabulary("today",new ArrayList<String>(Arrays.asList("今","天")), new ArrayList<String>(Arrays.asList("jin1","tian1")));
        assertTrue(HanziDB.combineListOfTranslations(getDB().getTranslations("天")).contains("sky"));
        assertFalse(HanziDB.combineListOfTranslations(getDB().getTranslations("今天")).contains("today"));
        assertTrue(getDB().checkDatabase());
    }

    public void testGetWordsWithPinyin() {
        getDB().addVocabulary("everyone",new ArrayList<String>(Arrays.asList("大","家")), new ArrayList<String>(Arrays.asList("da4", "jia1")));
        getDB().addVocabulary("big",new ArrayList<String>(Arrays.asList("大")), new ArrayList<String>(Arrays.asList("da4")));
        getDB().addVocabulary("great",new ArrayList<String>(Arrays.asList("大")), new ArrayList<String>(Arrays.asList("da4")));
        getDB().addVocabulary("not very",new ArrayList<String>(Arrays.asList("不","大")), new ArrayList<String>(Arrays.asList("bu4", "da4")));
        getDB().addVocabulary("doctor",new ArrayList<String>(Arrays.asList("大","夫")), new ArrayList<String>(Arrays.asList("dai4", "fu")));
        assertTrue(HanziDB.combineListOfTranslations(getDB().getWordsWithPinyin("大", "da4")).contains("everyone"));
        assertTrue(HanziDB.combineListOfTranslations(getDB().getWordsWithPinyin("大", "da4")).contains("great"));
        assertTrue(HanziDB.combineListOfTranslations(getDB().getWordsWithPinyin("大", "da4")).contains("not very"));
        assertFalse(HanziDB.combineListOfTranslations(getDB().getWordsWithPinyin("大", "da4")).contains("doctor"));
        assertTrue(getDB().checkDatabase());
    }

    public void testGetWordsWithChar() {
        getDB().addVocabulary("to go",new ArrayList<String>(Arrays.asList("去")), new ArrayList<String>(Arrays.asList("qu4")));
        getDB().addVocabulary("last year",new ArrayList<String>(Arrays.asList("去","年")), new ArrayList<String>(Arrays.asList("qu4", "nian2")));
        getDB().addVocabulary("it seems",new ArrayList<String>(Arrays.asList("看","上","去")), new ArrayList<String>(Arrays.asList("kan4", "shang", "qu")));
        assertTrue(HanziDB.combineListOfTranslations(getDB().getWordsWithPinyin("去")).contains("to go"));
        assertTrue(HanziDB.combineListOfTranslations(getDB().getWordsWithPinyin("去")).contains("last year"));
        assertTrue(HanziDB.combineListOfTranslations(getDB().getWordsWithPinyin("去")).contains("it seems"));
        assertTrue(getDB().checkDatabase());
    }
}
