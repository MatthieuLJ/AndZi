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
    private static final String DbName = "test_db";
    protected HanziDB target;

    @Override
    protected void setUp() {
        ContextWrapper cw = new ContextWrapper(this.getContext());
        //cw.deleteDatabase(DbName);
        target = new HanziDB(this.getContext(),  DbName);
        Log.d(TAG, " is setup");
    }

    protected void tearDown() {
        target.close();
    }

    public void testSimpleAdd() {
        target.addVocabulary("hello", new ArrayList<String>(Arrays.asList("你", "好")), new ArrayList<String>(Arrays.asList("ni3", "hao3")));
        assertTrue(target.getTranslations("你好").contains("hello"));
        assertTrue(target.checkDatabase());
    }

    public void testBadSizeAdd() {
        target.addVocabulary("hello_bad", new ArrayList<String>(Arrays.asList("你", "好")), new ArrayList<String>(Arrays.asList("ni3")));
        assertFalse(target.getTranslations("你好").contains("hello_bad"));
        assertTrue(target.checkDatabase());
    }

    public void testMultipleAdd() {
        target.addVocabulary(new ArrayList<String>(Arrays.asList("good", "well", "perfect")), new ArrayList<String>(Arrays.asList("好")), new ArrayList<String>(Arrays.asList("hao3")));
        assertTrue(target.getTranslations("好").contains("good"));
        assertTrue(target.getTranslations("好").contains("well"));
        assertTrue(target.getTranslations("好").contains("perfect"));
        assertTrue(target.checkDatabase());
    }

    public void testOneCharMultipleWords() {
        target.addVocabulary("grow", new ArrayList<String>(Arrays.asList("长")), new ArrayList<String>(Arrays.asList("zhang3")));
        target.addVocabulary("long", new ArrayList<String>(Arrays.asList("长")), new ArrayList<String>(Arrays.asList("chang2")));
        assertTrue(target.getTranslations("长","zhang3").contains("grow"));
        assertFalse(target.getTranslations("长","zhang3").contains("long"));
        assertTrue(target.getTranslations("长","chang2").contains("long"));
        assertFalse(target.getTranslations("长","chang2").contains("grow"));
        assertTrue(target.checkDatabase());
    }

}
