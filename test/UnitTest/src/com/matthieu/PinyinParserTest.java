package com.matthieu;

import android.content.ContextWrapper;
import android.test.AndroidTestCase;
import android.util.Log;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;

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
        ArrayList<Pinyin> res=PinyinParser.parse_string("ni3hao3");
        assertTrue(res.size() == 2);
        assertTrue(res.get(0).get_lame().equals("ni3"));
        assertTrue(res.get(1).get_lame().equals("hao3"));
    }
}
