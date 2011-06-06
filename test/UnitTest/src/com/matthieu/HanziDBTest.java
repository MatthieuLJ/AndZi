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
    protected HanziDB target;

    @Override
    protected void setUp() {
        ContextWrapper cw = new ContextWrapper(this.getContext());
        cw.deleteDatabase(DbName);
        target = new HanziDB(this.getContext(),  DbName);
        Log.d(TAG, " is setup");
    }

    @Override
    protected void tearDown() {
        target.close();
    }

    public void testSimpleAdd() {
        target.addVocabulary("hello", new ArrayList<String>(Arrays.asList("你", "好")), new ArrayList<String>(Arrays.asList("ni3", "hao3")));
        assertTrue(HanziDB.combineListOfTranslations(target.getTranslations("你好")).contains("hello"));
        assertTrue(target.checkDatabase());
    }

    public void testBadSizeAdd() {
        target.addVocabulary("hello_bad", new ArrayList<String>(Arrays.asList("你", "好")), new ArrayList<String>(Arrays.asList("ni3")));
        assertFalse(HanziDB.combineListOfTranslations(target.getTranslations("你好")).contains("hello_bad"));
        assertTrue(target.checkDatabase());
    }

    public void testAddSameTranslation() {
        target.addVocabulary("west wind", new ArrayList<String>(Arrays.asList("西", "风")), new ArrayList<String>(Arrays.asList("xi1", "feng1")));
        target.addVocabulary("west wind", new ArrayList<String>(Arrays.asList("西", "风")), new ArrayList<String>(Arrays.asList("xi1", "feng1")));
        ArrayList<Vocabulary> res = target.getTranslations("西风");
        assertTrue(res.size()==1);
        assertTrue(res.get(0).english.size() == 1);
        assertTrue(target.checkDatabase());
    }

    public void testMultipleAdd() {
        target.addVocabulary(new ArrayList<String>(Arrays.asList("good", "well", "perfect")), new ArrayList<String>(Arrays.asList("好")), new ArrayList<String>(Arrays.asList("hao3")));
        assertTrue(HanziDB.combineListOfTranslations(target.getTranslations("好")).contains("good"));
        assertTrue(HanziDB.combineListOfTranslations(target.getTranslations("好")).contains("well"));
        assertTrue(HanziDB.combineListOfTranslations(target.getTranslations("好")).contains("perfect"));
        assertTrue(target.checkDatabase());
    }

    public void testVocabularyAdd() {
        target.addVocabulary(new Vocabulary("风", "feng1", new ArrayList<String>(Arrays.asList("wind", "custom"))));
        assertTrue(HanziDB.combineListOfTranslations(target.getTranslations("风")).contains("wind"));
        assertTrue(HanziDB.combineListOfTranslations(target.getTranslations("风")).contains("custom"));
        assertTrue(target.checkDatabase());
    }

    public void testOneCharMultipleWords() {
        target.addVocabulary("grow", new ArrayList<String>(Arrays.asList("长")), new ArrayList<String>(Arrays.asList("zhang3")));
        target.addVocabulary("long", new ArrayList<String>(Arrays.asList("长")), new ArrayList<String>(Arrays.asList("chang2")));
        assertTrue(HanziDB.combineListOfTranslations(target.getTranslations("长","zhang3")).contains("grow"));
        assertFalse(HanziDB.combineListOfTranslations(target.getTranslations("长","zhang3")).contains("long"));
        assertTrue(HanziDB.combineListOfTranslations(target.getTranslations("长","chang2")).contains("long"));
        assertFalse(HanziDB.combineListOfTranslations(target.getTranslations("长","chang2")).contains("grow"));
        assertTrue(target.checkDatabase());
    }

    public void testMultipleChinese() {
        target.addVocabulary("big",new ArrayList<String>(Arrays.asList("大")), new ArrayList<String>(Arrays.asList("da4")));
        target.addVocabulary("big",new ArrayList<String>(Arrays.asList("伟")), new ArrayList<String>(Arrays.asList("wei3")));
        assertTrue(target.getCTranslations("big").size()==2);
        assertTrue(target.checkDatabase());
    }

    public void testSimpleDelete() {
        target.addVocabulary("small",new ArrayList<String>(Arrays.asList("小")), new ArrayList<String>(Arrays.asList("xiao3")));
        assertTrue(HanziDB.combineListOfTranslations(target.getTranslations("小")).contains("small"));
        target.delVocabulary("small",new ArrayList<String>(Arrays.asList("小")), new ArrayList<String>(Arrays.asList("xiao3")));
        assertFalse(HanziDB.combineListOfTranslations(target.getTranslations("小")).contains("small"));
        assertTrue(target.checkDatabase());
    }

    public void testMultipleDelete() {
        target.addVocabulary(new ArrayList<String>(Arrays.asList("bright", "next", "wise")),new ArrayList<String>(Arrays.asList("明")), new ArrayList<String>(Arrays.asList("ming2")));
        assertTrue(HanziDB.combineListOfTranslations(target.getTranslations("明")).contains("bright"));
        assertTrue(HanziDB.combineListOfTranslations(target.getTranslations("明")).contains("wise"));
        target.delVocabulary(new ArrayList<String>(Arrays.asList("明")), new ArrayList<String>(Arrays.asList("ming2")));
        assertFalse(HanziDB.combineListOfTranslations(target.getTranslations("明")).contains("bright"));
        assertFalse(HanziDB.combineListOfTranslations(target.getTranslations("明")).contains("next"));
        assertFalse(HanziDB.combineListOfTranslations(target.getTranslations("明")).contains("wise"));
        assertTrue(target.checkDatabase());
    }

    public void testLeftOverFromDelete() {
        target.addVocabulary("sky",new ArrayList<String>(Arrays.asList("天")), new ArrayList<String>(Arrays.asList("tian1")));
        target.addVocabulary("today",new ArrayList<String>(Arrays.asList("今","天")), new ArrayList<String>(Arrays.asList("jin1","tian1")));
        assertTrue(HanziDB.combineListOfTranslations(target.getTranslations("天")).contains("sky"));
        target.delVocabulary("today",new ArrayList<String>(Arrays.asList("今","天")), new ArrayList<String>(Arrays.asList("jin1","tian1")));
        assertTrue(HanziDB.combineListOfTranslations(target.getTranslations("天")).contains("sky"));
        assertFalse(HanziDB.combineListOfTranslations(target.getTranslations("今天")).contains("today"));
        assertTrue(target.checkDatabase());
    }
}
