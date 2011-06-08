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
public class HanziDBlessonTest extends HanziDBTest {
    private static final String TAG = "HanziDBlessonTest";
    protected static final String DbName = "test_lesson_db";
    private HanziDBlesson lesson_target;

    @Override
    protected void setUp() {
        ContextWrapper cw = new ContextWrapper(this.getContext());
        cw.deleteDatabase(DbName);
        lesson_target = new HanziDBlesson(this.getContext(),  DbName);
        Log.d(TAG, " is setup");
    }

    @Override
    protected void tearDown() {
        lesson_target.close();
    }

    @Override
    protected HanziDB getDB() {
        return lesson_target;
    }

    protected HanziDBlesson getLessonDB() {
        return lesson_target;
    }

    public void testLessonAddDelete() {
        getLessonDB().addLesson("lesson 1");
        getLessonDB().addLesson("lesson 2");
        assertTrue(getLessonDB().getLessons().contains("lesson 1"));
        getLessonDB().delLesson("lesson 1");
        assertFalse(getLessonDB().getLessons().contains("lesson 1"));
        assertTrue(getLessonDB().getLessons().contains("lesson 2"));
        assertTrue(getLessonDB().checkDatabase());
    }

    public void testLessonAssignment() {
        getLessonDB().addLesson("lesson 1");
        getLessonDB().addVocabulary(new ArrayList<String>(Arrays.asList("good", "well", "perfect")), new ArrayList<String>(Arrays.asList("好")), new ArrayList<String>(Arrays.asList("hao3")));
        getLessonDB().assignWordLesson("lesson 1", "good", "好", "hao3");
        assertTrue(getLessonDB().getVocabularyInLessons(new ArrayList<String>(Arrays.asList("lesson 1"))).size() == 1);
        getLessonDB().assignWordLesson("lesson 1", "well", "好", "hao3");
        assertTrue(getLessonDB().getVocabularyInLessons(new ArrayList<String>(Arrays.asList("lesson 1"))).size() == 1);
        getLessonDB().addVocabulary("big",new ArrayList<String>(Arrays.asList("大")), new ArrayList<String>(Arrays.asList("da4")));
        getLessonDB().assignWordLesson("lesson 1", "big", "大", "da4");
        assertTrue(getLessonDB().getVocabularyInLessons(new ArrayList<String>(Arrays.asList("lesson 1"))).size() == 2);
        assertTrue(getLessonDB().checkDatabase());
    }

    public void testRemoveFromLesson() {
        getLessonDB().addVocabulary(new ArrayList<String>(Arrays.asList("down", "below")), new ArrayList<String>(Arrays.asList("下")), new ArrayList<String>(Arrays.asList("xia4")));
        getLessonDB().addLesson("remove_lesson");
        getLessonDB().assignWordLesson("remove_lesson", "down", "下", "xia4");
        getLessonDB().assignWordLesson("remove_lesson", "below", "下", "xia4");
        assertTrue(HanziDB.combineListOfTranslations(getLessonDB().getVocabularyInLessons(new ArrayList<String>(Arrays.asList("remove_lesson")))).contains("down"));
        assertTrue(HanziDB.combineListOfTranslations(getLessonDB().getVocabularyInLessons(new ArrayList<String>(Arrays.asList("remove_lesson")))).contains("below"));
        getLessonDB().removeWordLesson("remove_lesson", "down", "下", "xia4");
        assertFalse(HanziDB.combineListOfTranslations(getLessonDB().getVocabularyInLessons(new ArrayList<String>(Arrays.asList("remove_lesson")))).contains("down"));
        assertTrue(HanziDB.combineListOfTranslations(getLessonDB().getVocabularyInLessons(new ArrayList<String>(Arrays.asList("remove_lesson")))).contains("below"));
        assertTrue(getLessonDB().checkDatabase());
    }

    public void testFiltersDB() {
        getLessonDB().addVocabulary(new ArrayList<String>(Arrays.asList("good", "well", "perfect")), new ArrayList<String>(Arrays.asList("好")), new ArrayList<String>(Arrays.asList("hao3")));
        getLessonDB().addLesson("lesson filter");
        getLessonDB().assignWordLesson("lesson filter", "good", "好", "hao3");
        getLessonDB().assignWordLesson("lesson filter", "well", "好", "hao3");
        getLessonDB().setLessonsToFilter(new ArrayList<String>(Arrays.asList("lesson filter")));
        getLessonDB().enableFilter(true);
        assertTrue(HanziDB.combineListOfTranslations(getLessonDB().getFilteredTranslations("好")).contains("good"));
        assertTrue(HanziDB.combineListOfTranslations(getLessonDB().getFilteredTranslations("好", "hao3")).contains("good"));
        assertFalse(HanziDB.combineListOfTranslations(getLessonDB().getFilteredTranslations("好", "ni3")).contains("good"));
        assertTrue(HanziDB.combineListOfTranslations(getLessonDB().getFilteredTranslations("好")).contains("well"));
        assertFalse(HanziDB.combineListOfTranslations(getLessonDB().getFilteredTranslations("好")).contains("perfect"));
        assertFalse(HanziDB.combineListOfTranslations(getLessonDB().getFilteredTranslations("好", "hao3")).contains("perfect"));
        assertTrue(getLessonDB().getFilteredCTranslations("good").get(0).chinese.equals("好"));
        assertTrue(getLessonDB().getFilteredCTranslations("perfect").size()==0);
        assertTrue(getLessonDB().checkDatabase());
    }
}
