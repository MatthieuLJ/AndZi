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
}
