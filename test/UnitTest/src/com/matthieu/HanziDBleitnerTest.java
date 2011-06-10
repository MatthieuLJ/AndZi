package com.matthieu;

import android.content.ContextWrapper;
import android.os.SystemClock;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * User: matthieu
 * Date: 6/8/11
 * Time: 2:06 PM
 */
public class HanziDBleitnerTest extends HanziDBlessonTest {
    private static final String TAG = "HanziDBleitnerTest";
    protected static final String DbName = "test_leitner_db";
    private HanziDBleitner leitner_target;

    private static final long three_hours = 3 * 60 * 60;
    private static final long three_days = 3 * 24 * 60 * 60;
    private static final long three_weeks = 3 * 7 * 24 * 60 * 60;
    private static final long three_months = 3 * 30 * 24 * 60 * 60;

    @Override
    protected void setUp() {
        ContextWrapper cw = new ContextWrapper(this.getContext());
        cw.deleteDatabase(DbName);
        leitner_target = new HanziDBleitner(this.getContext(),  DbName);
        Log.d(TAG, " is setup");
    }

    @Override
    protected void tearDown() {
        leitner_target.close();
    }

    @Override
    protected HanziDB getDB() {
        return leitner_target;
    }

    protected HanziDBlesson getLessonDB() {
        return leitner_target;
    }

    protected HanziDBleitner getLeitnerDB() {
        return leitner_target;
    }

    public void testCharPath() {
        getDB().addVocabulary("hello", new ArrayList<String>(Arrays.asList("你", "好")), new ArrayList<String>(Arrays.asList("ni3", "hao3")));
        assertTrue(getLeitnerDB().getBoxForChar("你")==0);
        assertTrue(getLeitnerDB().getNumCharsInBox(0)==2);
        getLeitnerDB().betterBox("你");
        assertTrue(getLeitnerDB().getBoxForChar("你")==1);
        getLeitnerDB().betterBox("你");
        assertTrue(getLeitnerDB().getBoxForChar("你")==2);
        getLeitnerDB().betterBox("你");
        assertTrue(getLeitnerDB().getBoxForChar("你")==3);
        getLeitnerDB().betterBox("你");
        assertTrue(getLeitnerDB().getBoxForChar("你")==4);
        getLeitnerDB().betterBox("你");
        assertTrue(getLeitnerDB().getBoxForChar("你")==4);
        getLeitnerDB().betterBox("好");
        assertTrue(getLeitnerDB().getBoxForChar("好")==1);
        getLeitnerDB().restartStudy("好");
        assertTrue(getLeitnerDB().getBoxForChar("好")==0);
        getLeitnerDB().checkDatabase();
    }

    /*
    public void testExpiration() {
        getDB().addVocabulary("hello", new ArrayList<String>(Arrays.asList("你", "好")), new ArrayList<String>(Arrays.asList("ni3", "hao3")));
        assertTrue(getLeitnerDB().getNumCharsExpiredInBox(0) == 2);
        getLeitnerDB().betterBox("你");
        assertTrue(getLeitnerDB().getNumCharsExpiredInBox(0) == 1);
        assertTrue(getLeitnerDB().getNumCharsExpiredInBox(1) == 0);
        //SystemClock.setCurrentTimeMillis(System.currentTimeMillis()+three_hours);
        try {
            Runtime.getRuntime().exec("date \"$((" + three_hours + " + $(date +%s)))\"").waitFor();
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (InterruptedException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        assertTrue(getLeitnerDB().getNumCharsExpiredInBox(0) == 1);
        assertTrue(getLeitnerDB().getNumCharsExpiredInBox(1) == 1);
        getLeitnerDB().resetTime("你");
        assertTrue(getLeitnerDB().getNumCharsExpiredInBox(0) == 1);
        assertTrue(getLeitnerDB().getNumCharsExpiredInBox(1) == 0);
        getLeitnerDB().checkDatabase();
    }
    */

}
