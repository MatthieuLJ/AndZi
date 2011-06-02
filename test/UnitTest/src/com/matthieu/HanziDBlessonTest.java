package com.matthieu;

import android.content.ContextWrapper;
import android.test.AndroidTestCase;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * User: matthieu
 * Date: 5/31/11
 * Time: 5:14 PM
 */
public class HanziDBlessonTest extends AndroidTestCase {
    private static final String TAG = "HanziDBlessonTest";
    protected static final String DbName = "test_lesson_db";
    protected HanziDBlesson target;

    @Override
    protected void setUp() {
        ContextWrapper cw = new ContextWrapper(this.getContext());
        cw.deleteDatabase(DbName);
        target = new HanziDBlesson(this.getContext(),  DbName);
        Log.d(TAG, " is setup");
    }

    @Override
    protected void tearDown() {
        target.close();
    }

    public void testLessonAddDelete() {
        target.addLesson("lesson 1");
        target.addLesson("lesson 2");
        assertTrue(target.getLessons().contains("lesson 1"));
        target.delLesson("lesson 1");
        assertFalse(target.getLessons().contains("lesson 1"));
        assertTrue(target.getLessons().contains("lesson 2"));
        assertTrue(target.checkDatabase());
    }

    public void testLessonAssignment() {
        target.addLesson("lesson 1");
        target.addVocabulary(new ArrayList<String>(Arrays.asList("good", "well", "perfect")), new ArrayList<String>(Arrays.asList("好")), new ArrayList<String>(Arrays.asList("hao3")));
        target.assignWordLesson("lesson 1", "good", "好", "hao3");
        assertTrue(target.getVocabularyInLessons(new ArrayList<String>(Arrays.asList("lesson 1"))).size() == 1);
        target.assignWordLesson("lesson 1", "well", "好", "hao3");
        assertTrue(target.getVocabularyInLessons(new ArrayList<String>(Arrays.asList("lesson 1"))).size() == 1);
        target.addVocabulary("big",new ArrayList<String>(Arrays.asList("大")), new ArrayList<String>(Arrays.asList("da4")));
        target.assignWordLesson("lesson 1", "big", "大", "da4");
        assertTrue(target.getVocabularyInLessons(new ArrayList<String>(Arrays.asList("lesson 1"))).size() == 2);
        assertTrue(target.checkDatabase());
    }
    
}
