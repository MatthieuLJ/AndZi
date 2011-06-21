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
    private HanziDB target_db;

    @Override
    protected void setUp() {
        ContextWrapper cw = new ContextWrapper(this.getContext());
        cw.deleteDatabase(DbName);
        target_db = new HanziDB(this.getContext(),  DbName);
        Log.d(TAG, " is setup");
    }

    @Override
    protected void tearDown() {
        target_db.close();
    }
    
    public void testSimpleAdd() {
        target_db.addVocabulary("hello", new ArrayList<String>(Arrays.asList("你", "好")), new ArrayList<String>(Arrays.asList("ni3", "hao3")));
        assertTrue(HanziDB.combineListOfTranslations(target_db.getTranslations("你好")).contains("hello"));
        assertTrue(target_db.checkDatabase());
    }

    public void testBadSizeAdd() {
        target_db.addVocabulary("hello_bad", new ArrayList<String>(Arrays.asList("你", "好")), new ArrayList<String>(Arrays.asList("ni3")));
        assertFalse(HanziDB.combineListOfTranslations(target_db.getTranslations("你好")).contains("hello_bad"));
        assertTrue(target_db.checkDatabase());
    }

    public void testAddSameTranslation() {
        target_db.addVocabulary("west wind", new ArrayList<String>(Arrays.asList("西", "风")), new ArrayList<String>(Arrays.asList("xi1", "feng1")));
        target_db.addVocabulary("west wind", new ArrayList<String>(Arrays.asList("西", "风")), new ArrayList<String>(Arrays.asList("xi1", "feng1")));
        ArrayList<Vocabulary> res = target_db.getTranslations("西风");
        assertTrue(res.size()==1);
        assertTrue(res.get(0).english.size() == 1);
        assertTrue(target_db.checkDatabase());
    }

    public void testMultipleAdd() {
        target_db.addVocabulary(new ArrayList<String>(Arrays.asList("good", "well", "perfect")), new ArrayList<String>(Arrays.asList("好")), new ArrayList<String>(Arrays.asList("hao3")));
        assertTrue(HanziDB.combineListOfTranslations(target_db.getTranslations("好")).contains("good"));
        assertTrue(HanziDB.combineListOfTranslations(target_db.getTranslations("好")).contains("well"));
        assertTrue(HanziDB.combineListOfTranslations(target_db.getTranslations("好")).contains("perfect"));
        assertTrue(target_db.checkDatabase());
    }

    public void testVocabularyAdd() {
        target_db.addVocabulary(new Vocabulary("风", "feng1", new ArrayList<String>(Arrays.asList("wind", "custom"))));
        assertTrue(HanziDB.combineListOfTranslations(target_db.getTranslations("风")).contains("wind"));
        assertTrue(HanziDB.combineListOfTranslations(target_db.getTranslations("风")).contains("custom"));
        assertTrue(target_db.checkDatabase());
    }

    public void testOneCharMultipleWords() {
        target_db.addVocabulary("grow", new ArrayList<String>(Arrays.asList("长")), new ArrayList<String>(Arrays.asList("zhang3")));
        target_db.addVocabulary("long", new ArrayList<String>(Arrays.asList("长")), new ArrayList<String>(Arrays.asList("chang2")));
        assertTrue(HanziDB.combineListOfTranslations(target_db.getTranslations("长","zhang3")).contains("grow"));
        assertFalse(HanziDB.combineListOfTranslations(target_db.getTranslations("长","zhang3")).contains("long"));
        assertTrue(HanziDB.combineListOfTranslations(target_db.getTranslations("长","chang2")).contains("long"));
        assertFalse(HanziDB.combineListOfTranslations(target_db.getTranslations("长","chang2")).contains("grow"));
        assertTrue(target_db.checkDatabase());
    }

    public void testMultipleChinese() {
        target_db.addVocabulary("big",new ArrayList<String>(Arrays.asList("大")), new ArrayList<String>(Arrays.asList("da4")));
        target_db.addVocabulary("big",new ArrayList<String>(Arrays.asList("伟")), new ArrayList<String>(Arrays.asList("wei3")));
        assertTrue(target_db.getCTranslations("big").size()==2);
        assertTrue(target_db.checkDatabase());
    }

    public void testSimpleDelete() {
        target_db.addVocabulary("small",new ArrayList<String>(Arrays.asList("小")), new ArrayList<String>(Arrays.asList("xiao3")));
        assertTrue(HanziDB.combineListOfTranslations(target_db.getTranslations("小")).contains("small"));
        target_db.delVocabulary("small",new ArrayList<String>(Arrays.asList("小")), new ArrayList<String>(Arrays.asList("xiao3")));
        assertFalse(HanziDB.combineListOfTranslations(target_db.getTranslations("小")).contains("small"));
        assertTrue(target_db.checkDatabase());
    }

    public void testMultipleDelete() {
        target_db.addVocabulary(new ArrayList<String>(Arrays.asList("bright", "next", "wise")),new ArrayList<String>(Arrays.asList("明")), new ArrayList<String>(Arrays.asList("ming2")));
        assertTrue(HanziDB.combineListOfTranslations(target_db.getTranslations("明")).contains("bright"));
        assertTrue(HanziDB.combineListOfTranslations(target_db.getTranslations("明")).contains("wise"));
        target_db.delVocabulary(new ArrayList<String>(Arrays.asList("明")), new ArrayList<String>(Arrays.asList("ming2")));
        assertFalse(HanziDB.combineListOfTranslations(target_db.getTranslations("明")).contains("bright"));
        assertFalse(HanziDB.combineListOfTranslations(target_db.getTranslations("明")).contains("next"));
        assertFalse(HanziDB.combineListOfTranslations(target_db.getTranslations("明")).contains("wise"));
        assertTrue(target_db.checkDatabase());
    }

    public void testLeftOverFromDelete() {
        target_db.addVocabulary("sky",new ArrayList<String>(Arrays.asList("天")), new ArrayList<String>(Arrays.asList("tian1")));
        target_db.addVocabulary("today",new ArrayList<String>(Arrays.asList("今","天")), new ArrayList<String>(Arrays.asList("jin1","tian1")));
        assertTrue(HanziDB.combineListOfTranslations(target_db.getTranslations("天")).contains("sky"));
        target_db.delVocabulary("today",new ArrayList<String>(Arrays.asList("今","天")), new ArrayList<String>(Arrays.asList("jin1","tian1")));
        assertTrue(HanziDB.combineListOfTranslations(target_db.getTranslations("天")).contains("sky"));
        assertFalse(HanziDB.combineListOfTranslations(target_db.getTranslations("今天")).contains("today"));
        assertTrue(target_db.checkDatabase());
    }

    public void testGetWordsWithPinyin() {
        target_db.addVocabulary("everyone",new ArrayList<String>(Arrays.asList("大","家")), new ArrayList<String>(Arrays.asList("da4", "jia1")));
        target_db.addVocabulary("big",new ArrayList<String>(Arrays.asList("大")), new ArrayList<String>(Arrays.asList("da4")));
        target_db.addVocabulary("great",new ArrayList<String>(Arrays.asList("大")), new ArrayList<String>(Arrays.asList("da4")));
        target_db.addVocabulary("not very",new ArrayList<String>(Arrays.asList("不","大")), new ArrayList<String>(Arrays.asList("bu4", "da4")));
        target_db.addVocabulary("doctor",new ArrayList<String>(Arrays.asList("大","夫")), new ArrayList<String>(Arrays.asList("dai4", "fu")));
        assertTrue(HanziDB.combineListOfTranslations(target_db.getWordsWithPinyin("大", "da4")).contains("everyone"));
        assertTrue(HanziDB.combineListOfTranslations(target_db.getWordsWithPinyin("大", "da4")).contains("great"));
        assertTrue(HanziDB.combineListOfTranslations(target_db.getWordsWithPinyin("大", "da4")).contains("not very"));
        assertFalse(HanziDB.combineListOfTranslations(target_db.getWordsWithPinyin("大", "da4")).contains("doctor"));
        assertTrue(target_db.checkDatabase());
    }

    public void testGetWordsWithChar() {
        target_db.addVocabulary("to go",new ArrayList<String>(Arrays.asList("去")), new ArrayList<String>(Arrays.asList("qu4")));
        target_db.addVocabulary("last year",new ArrayList<String>(Arrays.asList("去","年")), new ArrayList<String>(Arrays.asList("qu4", "nian2")));
        target_db.addVocabulary("it seems",new ArrayList<String>(Arrays.asList("看","上","去")), new ArrayList<String>(Arrays.asList("kan4", "shang", "qu")));
        assertTrue(HanziDB.combineListOfTranslations(target_db.getWordsWithPinyin("去")).contains("to go"));
        assertTrue(HanziDB.combineListOfTranslations(target_db.getWordsWithPinyin("去")).contains("last year"));
        assertTrue(HanziDB.combineListOfTranslations(target_db.getWordsWithPinyin("去")).contains("it seems"));
        assertTrue(target_db.checkDatabase());
    }

    public void testWordCharCount() {
        target_db.addVocabulary("hello", new ArrayList<String>(Arrays.asList("你", "好")), new ArrayList<String>(Arrays.asList("ni3", "hao3")));
        assertTrue(target_db.getNumChars()==2);
        assertTrue(target_db.getNumWords()==1);
        target_db.addVocabulary(new ArrayList<String>(Arrays.asList("good", "well", "perfect")), new ArrayList<String>(Arrays.asList("好")), new ArrayList<String>(Arrays.asList("hao3")));
        assertTrue(target_db.getNumChars()==2);
        assertTrue(target_db.getNumWords()==2);
        target_db.addVocabulary("grow", new ArrayList<String>(Arrays.asList("长")), new ArrayList<String>(Arrays.asList("zhang3")));
        assertTrue(target_db.getNumChars()==3);
        assertTrue(target_db.getNumWords()==3);
        target_db.addVocabulary("long", new ArrayList<String>(Arrays.asList("长")), new ArrayList<String>(Arrays.asList("chang2")));
        assertTrue(target_db.getNumChars()==3);
        assertTrue(target_db.getNumWords()==4);
        target_db.delVocabulary(new ArrayList<String>(Arrays.asList("你", "好")), new ArrayList<String>(Arrays.asList("ni3", "hao3")));
        assertTrue(target_db.getNumChars()==2);
        assertTrue(target_db.getNumWords()==3);
        assertTrue(target_db.checkDatabase());
    }
    
    public void testLessonAddDelete() {
        target_db.addLesson("lesson 1");
        target_db.addLesson("lesson 2");
        assertTrue(target_db.getLessons().contains("lesson 1"));
        target_db.delLesson("lesson 1");
        assertFalse(target_db.getLessons().contains("lesson 1"));
        assertTrue(target_db.getLessons().contains("lesson 2"));
        assertTrue(target_db.checkDatabase());
    }

    public void testLessonAssignment() {
        target_db.addLesson("lesson 1");
        target_db.addVocabulary(new ArrayList<String>(Arrays.asList("good", "well", "perfect")), new ArrayList<String>(Arrays.asList("好")), new ArrayList<String>(Arrays.asList("hao3")));
        target_db.assignWordLesson("lesson 1", "good", "好", "hao3");
        assertTrue(target_db.getVocabularyInLessons(new ArrayList<String>(Arrays.asList("lesson 1"))).size() == 1);
        target_db.assignWordLesson("lesson 1", "well", "好", "hao3");
        assertTrue(target_db.getVocabularyInLessons(new ArrayList<String>(Arrays.asList("lesson 1"))).size() == 1);
        target_db.addVocabulary("big",new ArrayList<String>(Arrays.asList("大")), new ArrayList<String>(Arrays.asList("da4")));
        target_db.assignWordLesson("lesson 1", "big", "大", "da4");
        assertTrue(target_db.getVocabularyInLessons(new ArrayList<String>(Arrays.asList("lesson 1"))).size() == 2);
        assertTrue(target_db.checkDatabase());
    }

    public void testRemoveFromLesson() {
        target_db.addVocabulary(new ArrayList<String>(Arrays.asList("down", "below")), new ArrayList<String>(Arrays.asList("下")), new ArrayList<String>(Arrays.asList("xia4")));
        target_db.addLesson("remove_lesson");
        target_db.assignWordLesson("remove_lesson", "down", "下", "xia4");
        target_db.assignWordLesson("remove_lesson", "below", "下", "xia4");
        assertTrue(HanziDB.combineListOfTranslations(target_db.getVocabularyInLessons(new ArrayList<String>(Arrays.asList("remove_lesson")))).contains("down"));
        assertTrue(HanziDB.combineListOfTranslations(target_db.getVocabularyInLessons(new ArrayList<String>(Arrays.asList("remove_lesson")))).contains("below"));
        target_db.removeWordLesson("remove_lesson", "down", "下", "xia4");
        assertFalse(HanziDB.combineListOfTranslations(target_db.getVocabularyInLessons(new ArrayList<String>(Arrays.asList("remove_lesson")))).contains("down"));
        assertTrue(HanziDB.combineListOfTranslations(target_db.getVocabularyInLessons(new ArrayList<String>(Arrays.asList("remove_lesson")))).contains("below"));
        assertTrue(target_db.checkDatabase());
    }

    public void testFiltersDB() {
        target_db.addVocabulary(new ArrayList<String>(Arrays.asList("good", "well", "perfect")), new ArrayList<String>(Arrays.asList("好")), new ArrayList<String>(Arrays.asList("hao3")));
        target_db.addLesson("lesson filter");
        target_db.assignWordLesson("lesson filter", "good", "好", "hao3");
        target_db.assignWordLesson("lesson filter", "well", "好", "hao3");
        target_db.setLessonsToFilter(new ArrayList<String>(Arrays.asList("lesson filter")));
        target_db.enableFilter(true);
        assertTrue(HanziDB.combineListOfTranslations(target_db.getFilteredTranslations("好")).contains("good"));
        assertTrue(HanziDB.combineListOfTranslations(target_db.getFilteredTranslations("好", "hao3")).contains("good"));
        assertFalse(HanziDB.combineListOfTranslations(target_db.getFilteredTranslations("好", "ni3")).contains("good"));
        assertTrue(HanziDB.combineListOfTranslations(target_db.getFilteredTranslations("好")).contains("well"));
        assertFalse(HanziDB.combineListOfTranslations(target_db.getFilteredTranslations("好")).contains("perfect"));
        assertFalse(HanziDB.combineListOfTranslations(target_db.getFilteredTranslations("好", "hao3")).contains("perfect"));
        assertTrue(target_db.getFilteredCTranslations("good").get(0).chinese.equals("好"));
        assertTrue(target_db.getFilteredCTranslations("perfect").size()==0);
        assertTrue(target_db.checkDatabase());
    }
    
    private static final long three_hours = 3 * 60 * 60;
    private static final long three_days = 3 * 24 * 60 * 60;
    private static final long three_weeks = 3 * 7 * 24 * 60 * 60;
    private static final long three_months = 3 * 30 * 24 * 60 * 60;
    
    public void testCharPath() {
        target_db.addVocabulary("hello", new ArrayList<String>(Arrays.asList("你", "好")), new ArrayList<String>(Arrays.asList("ni3", "hao3")));
        assertTrue(target_db.getBoxForChar("你")==0);
        assertTrue(target_db.getNumCharsInBox(0)==2);
        target_db.betterBox("你");
        assertTrue(target_db.getBoxForChar("你")==1);
        target_db.betterBox("你");
        assertTrue(target_db.getBoxForChar("你")==2);
        target_db.betterBox("你");
        assertTrue(target_db.getBoxForChar("你")==3);
        target_db.betterBox("你");
        assertTrue(target_db.getBoxForChar("你")==4);
        target_db.betterBox("你");
        assertTrue(target_db.getBoxForChar("你")==4);
        target_db.betterBox("好");
        assertTrue(target_db.getBoxForChar("好")==1);
        target_db.restartStudy("好");
        assertTrue(target_db.getBoxForChar("好")==0);
        target_db.checkDatabase();
    }

    public void testNextToStudy() {
        target_db.addVocabulary("you", new ArrayList<String>(Arrays.asList("你")), new ArrayList<String>(Arrays.asList("ni3")));
        target_db.resetTime("你");
        target_db.addVocabulary("good", new ArrayList<String>(Arrays.asList("好")), new ArrayList<String>(Arrays.asList("hao3")));
        target_db.resetTime("好");
        assertTrue(target_db.getNextToStudy().equals("你"));
        target_db.resetTime("你");
        assertTrue(target_db.getNextToStudy().equals("好"));
        target_db.betterBox("好");
        assertTrue(target_db.getNextToStudy().equals("你"));
        target_db.betterBox("你");
        assertTrue(target_db.getNextToStudy() == null);
        target_db.restartStudy("好");
        assertTrue(target_db.getNextToStudy().equals("好"));
    }

    public void testNextToStudyNeverUsed() {
        target_db.addVocabulary("you", new ArrayList<String>(Arrays.asList("你")), new ArrayList<String>(Arrays.asList("ni3")));
        assertTrue(target_db.getNextToStudy().equals("你"));
    }

    /*
    public void testExpiration() {
        target_db.addVocabulary("hello", new ArrayList<String>(Arrays.asList("你", "好")), new ArrayList<String>(Arrays.asList("ni3", "hao3")));
        assertTrue(target_db.getNumCharsExpiredInBox(0) == 2);
        target_db.betterBox("你");
        assertTrue(target_db.getNumCharsExpiredInBox(0) == 1);
        assertTrue(target_db.getNumCharsExpiredInBox(1) == 0);
        //SystemClock.setCurrentTimeMillis(System.currentTimeMillis()+three_hours);
        try {
            Runtime.getRuntime().exec("date \"$((" + three_hours + " + $(date +%s)))\"").waitFor();
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (InterruptedException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        assertTrue(target_db.getNumCharsExpiredInBox(0) == 1);
        assertTrue(target_db.getNumCharsExpiredInBox(1) == 1);
        target_db.resetTime("你");
        assertTrue(target_db.getNumCharsExpiredInBox(0) == 1);
        assertTrue(target_db.getNumCharsExpiredInBox(1) == 0);
        target_db.checkDatabase();
    }
    */
}
