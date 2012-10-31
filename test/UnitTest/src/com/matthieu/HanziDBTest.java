package com.matthieu;

import android.app.Activity;
import android.content.*;
import android.database.Cursor;
import android.test.ActivityInstrumentationTestCase2;
import android.util.Log;
import com.matthieu.widget.DBCheckProgressHandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

public class HanziDBTest extends ActivityInstrumentationTestCase2<EmptyActivity> {

    private static final String TAG = "HanziDBTest";
    protected static final String DbName = "test_db";
    private HanziDB target_db;
    private AndZiApplication app;
    private Activity appContext;

    public HanziDBTest() {
        super("com.matthieu", EmptyActivity.class);
    }

    @Override
    protected void setUp() {
        Log.d(TAG, " setting up");
        app = (AndZiApplication) getActivity().getApplication();
        appContext = getActivity();
        ContextWrapper cw = new ContextWrapper(appContext);
        cw.deleteDatabase(DbName);
        target_db = new HanziDB(app,  DbName);
        Log.d(TAG, " is setup");
    }

    @Override
    protected void tearDown() {
        target_db.close();
        getActivity().finish();
        Log.d(TAG, " is destroyed");
    }

    public void testBareTranslation() {
        assertTrue(HanziDB.bare_translation("abc").equals("abc"));
        assertTrue(HanziDB.bare_translation("a(bc)").equals("a"));
        assertTrue(HanziDB.bare_translation("a(bc)d(ef)").equals("a d"));
    }

    public void testSimpleAdd() {
        target_db.addVocabulary("hello", "你好", "ni3hao3");
        assertTrue(combineListOfTranslations(target_db.getTranslations("你好")).contains("hello"));
        assertTrue(combineListOfTranslations(target_db.getTranslations("你好", "ni3hao3")).contains("hello"));
        assertTrue(checkDB(target_db, false));
    }

    public void testBadSizeAdd() {
        target_db.addVocabulary("hello_bad", "你好", "ni3");
        assertFalse(combineListOfTranslations(target_db.getTranslations("你好")).contains("hello_bad"));
        assertTrue(checkDB(target_db, false));
    }

    public void testAddSameTranslation() {
        target_db.addVocabulary("west wind", "西风", "xi1feng1");
        target_db.addVocabulary("west wind", "西风", "xi1feng1");
        ArrayList<Vocabulary> res = target_db.getTranslations("西风");
        assertTrue(res.size()==1);
        assertTrue(res.get(0).english.size() == 1);
        assertTrue(checkDB(target_db, false));
    }

    public void testMultipleAdd() {
        target_db.addVocabulary(new ArrayList<String>(Arrays.asList("good", "well", "perfect")), "好", "hao3");
        assertTrue(combineListOfTranslations(target_db.getTranslations("好")).contains("good"));
        assertTrue(combineListOfTranslations(target_db.getTranslations("好")).contains("well"));
        assertTrue(combineListOfTranslations(target_db.getTranslations("好")).contains("perfect"));
        assertTrue(checkDB(target_db, false));
    }

    public void testVocabularyAdd() {
        target_db.addVocabulary(new Vocabulary("风", "feng1", new ArrayList<String>(Arrays.asList("wind", "custom"))));
        assertTrue(combineListOfTranslations(target_db.getTranslations("风")).contains("wind"));
        assertTrue(combineListOfTranslations(target_db.getTranslations("风")).contains("custom"));
        assertTrue(checkDB(target_db, false));
    }

    public void testOneCharMultipleWords() {
        target_db.addVocabulary("grow", "长", "zhang3");
        target_db.addVocabulary("long", "长", "chang2");
        assertTrue(combineListOfTranslations(target_db.getTranslations("长","zhang3")).contains("grow"));
        assertFalse(combineListOfTranslations(target_db.getTranslations("长","zhang3")).contains("long"));
        assertTrue(combineListOfTranslations(target_db.getTranslations("长","chang2")).contains("long"));
        assertFalse(combineListOfTranslations(target_db.getTranslations("长","chang2")).contains("grow"));
        assertTrue(checkDB(target_db, false));
    }

    public void testAddListOfVocabulary() {
        ArrayList<Vocabulary> list = new ArrayList<Vocabulary>();
        list.add(new Vocabulary("风", "feng1", new ArrayList<String>(Arrays.asList("wind", "custom"))));
        list.add(new Vocabulary("长", "zhang3", new ArrayList<String>(Arrays.asList("grow"))));
        list.add(new Vocabulary("长", "chang2", new ArrayList<String>(Arrays.asList("long"))));
        list.add(new Vocabulary("大", "da4", new ArrayList<String>(Arrays.asList("big"))));
        list.add(new Vocabulary("伟", "wei3", new ArrayList<String>(Arrays.asList("big"))));
        list.add(new Vocabulary("大家", "da4jia1", new ArrayList<String>(Arrays.asList("everyone"))));
        list.add(new Vocabulary("去去", "qu4qu", new ArrayList<String>(Arrays.asList("duplicated"))));

        target_db.addVocabulary(list);

        assertTrue(combineListOfTranslations(target_db.getTranslations("长", "zhang3")).contains("grow"));
        assertFalse(combineListOfTranslations(target_db.getTranslations("长","zhang3")).contains("long"));
        assertTrue(combineListOfTranslations(target_db.getTranslations("长","chang2")).contains("long"));
        assertFalse(combineListOfTranslations(target_db.getTranslations("长","chang2")).contains("grow"));
        assertTrue(combineListOfTranslations(target_db.getTranslations("风")).contains("wind"));
        assertTrue(combineListOfTranslations(target_db.getTranslations("风")).contains("custom"));
        assertTrue(captureDataFromCursor(target_db.getCTranslations("big")).size()==2);
        assertTrue(checkDB(target_db, false));
    }

    public void testMultipleChinese() {
        target_db.addVocabulary("big", "大", "da4");
        target_db.addVocabulary("big", "伟", "wei3");
        assertTrue(captureDataFromCursor(target_db.getCTranslations("big")).size() == 2);
        assertTrue(checkDB(target_db, false));
    }

    public void testSimpleDelete() {
        target_db.addVocabulary("small", "小", "xiao3");
        assertTrue(combineListOfTranslations(target_db.getTranslations("小")).contains("small"));
        target_db.delVocabulary("small", "小", "xiao3");
        assertFalse(combineListOfTranslations(target_db.getTranslations("小")).contains("small"));
        assertTrue(checkDB(target_db, false));
    }

    public void testMultipleDelete() {
        target_db.addVocabulary(new ArrayList<String>(Arrays.asList("bright", "next", "wise")), "明", "ming2");
        assertTrue(combineListOfTranslations(target_db.getTranslations("明")).contains("bright"));
        assertTrue(combineListOfTranslations(target_db.getTranslations("明")).contains("wise"));
        target_db.delVocabulary("明", "ming2");
        assertFalse(combineListOfTranslations(target_db.getTranslations("明")).contains("bright"));
        assertFalse(combineListOfTranslations(target_db.getTranslations("明")).contains("next"));
        assertFalse(combineListOfTranslations(target_db.getTranslations("明")).contains("wise"));
        assertTrue(checkDB(target_db, false));
    }

    public void testMoreDelete() {
        target_db.addVocabulary(new ArrayList<String>(Arrays.asList("bright", "next", "wise")),"明", "ming2");
        target_db.addVocabulary(new ArrayList<String>(Arrays.asList("name", "famous")), "名", "ming2");
        assertTrue(combineListOfTranslations(target_db.getTranslations("明")).contains("bright"));
        assertTrue(combineListOfTranslations(target_db.getTranslations("明")).contains("wise"));
        assertTrue(combineListOfTranslations(target_db.getTranslations("名")).contains("name"));
        target_db.delVocabulary("name", "名", "ming2");
        assertTrue(combineListOfTranslations(target_db.getTranslations("名")).contains("famous"));
        assertFalse(combineListOfTranslations(target_db.getTranslations("名")).contains("name"));
        target_db.delVocabulary(new ArrayList<String>(Arrays.asList("bright", "next")), "明", "ming2");
        assertFalse(combineListOfTranslations(target_db.getTranslations("明")).contains("bright"));
        assertFalse(combineListOfTranslations(target_db.getTranslations("明")).contains("next"));
        assertTrue(combineListOfTranslations(target_db.getTranslations("明")).contains("wise"));
        assertTrue(checkDB(target_db, false));
    }

    public void testLeftOverFromDelete() {
        target_db.addVocabulary("sky","天", "tian1");
        target_db.addVocabulary("today","今天", "jin1tian1");
        assertTrue(combineListOfTranslations(target_db.getTranslations("天")).contains("sky"));
        target_db.delVocabulary("today", "今天", "jin1tian1");
        assertTrue(combineListOfTranslations(target_db.getTranslations("天")).contains("sky"));
        assertFalse(combineListOfTranslations(target_db.getTranslations("今天")).contains("today"));
        assertTrue(checkDB(target_db, false));
    }

    public void testGetWordsWithPinyin() {
        target_db.addVocabulary("everyone","大家", "da4jia1");
        target_db.addVocabulary("big","大", "da4");
        target_db.addVocabulary("great","大", "da4");
        target_db.addVocabulary("not very","不大", "bu4da4");
        target_db.addVocabulary("doctor","大夫", "dai4fu");
        assertTrue(combineListOfTranslations(target_db.getWordsWithPinyin("大", "da4")).contains("everyone"));
        assertTrue(combineListOfTranslations(target_db.getWordsWithPinyin("大", "da4")).contains("great"));
        assertTrue(combineListOfTranslations(target_db.getWordsWithPinyin("大", "da4")).contains("not very"));
        assertFalse(combineListOfTranslations(target_db.getWordsWithPinyin("大", "da4")).contains("doctor"));
        assertTrue(checkDB(target_db, false));
    }

    public void testGetWordsWithChar() {
        target_db.addVocabulary("to go","去", "qu4");
        target_db.addVocabulary("last year","去年", "qu4nian2");
        target_db.addVocabulary("it seems","看上去", "kan4shangqu");
        assertTrue(combineListOfTranslations(target_db.getWordsWithPinyin("去")).contains("to go"));
        assertTrue(combineListOfTranslations(target_db.getWordsWithPinyin("去")).contains("last year"));
        assertTrue(combineListOfTranslations(target_db.getWordsWithPinyin("去")).contains("it seems"));
        assertTrue(checkDB(target_db, false));
    }

    public void testGetWordsWithCharDuplicated() {
        target_db.addVocabulary("to go","去", "qu4");
        target_db.addVocabulary("last year","去年", "qu4nian2");
        target_db.addVocabulary("it seems","看上去", "kan4shangqu");
        target_db.addVocabulary("duplicated", "去去", "qu4qu");
        assertTrue(target_db.getWordsWithPinyin("去").size()==4);
        assertTrue(checkDB(target_db, false));
    }

    public void testWordCharCount() {
        target_db.addVocabulary("hello", "你好", "ni3hao3");
        assertTrue(target_db.getNumChars()==2);
        assertTrue(target_db.getNumWords()==1);
        target_db.addVocabulary(new ArrayList<String>(Arrays.asList("good", "well", "perfect")), "好", "hao3");
        assertTrue(target_db.getNumChars()==2);
        assertTrue(target_db.getNumWords()==2);
        target_db.addVocabulary("grow", "长", "zhang3");
        assertTrue(target_db.getNumChars()==3);
        assertTrue(target_db.getNumWords()==3);
        target_db.addVocabulary("long", "长", "chang2");
        assertTrue(target_db.getNumChars() == 3);
        assertTrue(target_db.getNumWords() == 4);
        assertTrue(target_db.getAllChars().size()==3);
        target_db.delVocabulary("你好", "ni3hao3");
        assertTrue(target_db.getNumChars()==2);
        assertTrue(target_db.getNumWords()==3);
        assertTrue(target_db.getAllChars().size()==2);
        assertTrue(checkDB(target_db, false));
    }

    public void testPinyinsForChar() {
        target_db.addVocabulary("hello", "你好", "ni3hao3");
        target_db.addVocabulary("grow", "长", "zhang3");
        target_db.addVocabulary("long", "长", "chang2");
        assertTrue(target_db.getPinyinForChar("长").contains("zhang3"));
        assertTrue(target_db.getPinyinForChar("长").contains("chang2"));
        assertTrue(target_db.getPinyinForChar("长").size() == 2);
        assertTrue(target_db.getPinyinForChar("你").contains("ni3"));
        assertTrue(target_db.getPinyinForChar("你").size()==1);
        assertTrue(checkDB(target_db, false));
    }

    public void testGetStartingCTranslations() {
        target_db.addVocabulary("you and hi", "你", "ni3");
        target_db.addVocabulary("hi there", "你好", "ni3hao3");
        target_db.addVocabulary("hi", "喂", "wei4");
        target_db.addVocabulary("hit", "撞", "zhuang1");
        ArrayList<Vocabulary> res = captureDataFromCursor(target_db.getStartingCTranslations("hi"));
        assertTrue(res.size()==3);
        assertTrue(checkDB(target_db, false));
    }

    public void testDecomposedParts() {
        target_db.addVocabulary("I should not see that", "你不看过他", "ni3bu4kan4guo2ta1");
        target_db.addVocabulary("You", "你", "ni3");
        target_db.addVocabulary("Not", "不", "bu4");
        target_db.addVocabulary("Don't see", "不看", "bu4kan4");
        target_db.addVocabulary("Did not see", "不看过", "bu4kan4guo2");
        target_db.addVocabulary("Did not see him", "不看过他", "bu4kan4guo2ta1");
        target_db.addVocabulary("See", "看", "kan4");
        target_db.addVocabulary("Saw", "看过", "kan4guo2");
        target_db.addVocabulary("He", "他", "ta1");
        target_db.addVocabulary("Him", "他", "ta1");
        HanziDB.StringElement ni_char = new HanziDB.StringElement(HanziDB.StringElement.type_t.CHINESE, "你");
        HanziDB.StringElement bu_char = new HanziDB.StringElement(HanziDB.StringElement.type_t.CHINESE, "不");
        HanziDB.StringElement kan_char = new HanziDB.StringElement(HanziDB.StringElement.type_t.CHINESE, "看");
        HanziDB.StringElement guo_char = new HanziDB.StringElement(HanziDB.StringElement.type_t.CHINESE, "过");
        HanziDB.StringElement ta_char = new HanziDB.StringElement(HanziDB.StringElement.type_t.CHINESE, "他");
        ArrayList<Vocabulary> res = captureDataFromCursor(target_db.getDecomposedParts(
                new ArrayList<HanziDB.StringElement>(Arrays.asList(ni_char, bu_char, kan_char, guo_char, ta_char)), 4, false, false));

        assertTrue(res.size() == 8);
        assertTrue(res.get(0).chinese.equals("你"));
        assertTrue(res.get(1).chinese.equals("不看过他"));
        assertTrue(res.get(7).english.size()==2);
        assertTrue(checkDB(target_db, false));
    }

    public void testFilteredDecomposedParts() {
        target_db.addVocabulary("I should not see that", "你不看过他", "ni3bu4kan4guo2ta1");
        target_db.addVocabulary("You", "你", "ni3");
        target_db.addVocabulary("Not", "不", "bu4");
        target_db.addVocabulary("Don't see", "不看", "bu4kan4");
        target_db.addVocabulary("Did not see", "不看过", "bu4kan4guo2");
        target_db.addVocabulary("Did not see him", "不看过他", "bu4kan4guo2ta1");
        target_db.addVocabulary("See", "看", "kan4");
        target_db.addVocabulary("Saw", "看过", "kan4guo2");
        target_db.addVocabulary("He", "他", "ta1");
        target_db.addVocabulary("Him", "他", "ta1");
        HanziDB.StringElement ni_char = new HanziDB.StringElement(HanziDB.StringElement.type_t.CHINESE, "你");
        HanziDB.StringElement bu_char = new HanziDB.StringElement(HanziDB.StringElement.type_t.CHINESE, "不");
        HanziDB.StringElement kan_char = new HanziDB.StringElement(HanziDB.StringElement.type_t.CHINESE, "看");
        HanziDB.StringElement guo_char = new HanziDB.StringElement(HanziDB.StringElement.type_t.CHINESE, "过");
        HanziDB.StringElement ta_char = new HanziDB.StringElement(HanziDB.StringElement.type_t.CHINESE, "他");
        target_db.addLesson("lesson filter");
        target_db.assignWordLesson("lesson filter", "You", "你", "ni3");
        target_db.assignWordLesson("lesson filter", "Did not see", "不看过", "bu4kan4guo2");
        target_db.assignWordLesson("lesson filter", "Saw", "看过", "kan4guo2");
        target_db.assignWordLesson("lesson filter", "He", "他", "ta1");
        target_db.setLessonsToFilter(new ArrayList<String>(Arrays.asList("lesson filter")));
        target_db.enableFilter(true);
        ArrayList<Vocabulary> res = captureDataFromCursor(target_db.getDecomposedParts(
                new ArrayList<HanziDB.StringElement>(Arrays.asList(ni_char, bu_char, kan_char, guo_char, ta_char)), 4, true, false));

        assertTrue(res.size() == 4);
        assertTrue(res.get(0).chinese.equals("你"));
        assertTrue(res.get(2).chinese.equals("看过"));
        assertTrue(res.get(3).english.size()==1);
        assertTrue(checkDB(target_db, false));
    }

    public void testLessonAddDelete() {
        target_db.addLesson("lesson 1");
        target_db.addLesson("lesson 2");
        assertTrue(target_db.getLessons().contains("lesson 1"));
        target_db.delLesson("lesson 1");
        assertFalse(target_db.getLessons().contains("lesson 1"));
        assertTrue(target_db.getLessons().contains("lesson 2"));
        assertTrue(checkDB(target_db, false));
    }

    public void testLessonAssignment() {
        target_db.addLesson("lesson 1");
        target_db.addLesson("lesson 2");
        target_db.addVocabulary(new ArrayList<String>(Arrays.asList("good", "well")), "好", "hao3");
        target_db.addVocabulary("big","大", "da4");
        target_db.addVocabulary("small","小", "xiao3");

        target_db.assignWordLesson("lesson 1", "good", "好", "hao3");
        assertTrue(target_db.getVocabularyInLessons(new ArrayList<String>(Arrays.asList("lesson 1"))).size() == 1);
        assertTrue(captureDataFromCursor(target_db.getVocabularyCursorInLessons(new ArrayList<String>(Arrays.asList("lesson 1")),true)).size() == 1);
        assertTrue(target_db.getVocabularyInLessons(new ArrayList<String>(Arrays.asList("lesson 1", ""))).size() == 3);
        assertTrue(captureDataFromCursor(target_db.getVocabularyCursorInLessons(new ArrayList<String>(Arrays.asList("lesson 1", "")),true)).size() == 3);
        assertTrue(target_db.getVocabularyInLessons(new ArrayList<String>(Arrays.asList(""))).size() == 3);
        assertTrue(captureDataFromCursor(target_db.getVocabularyCursorInLessons(new ArrayList<String>(Arrays.asList("")),true)).size() == 3);
        target_db.assignWordLesson("lesson 2", "small", "小", "xiao3");
        assertTrue(target_db.getVocabularyInLessons(new ArrayList<String>(Arrays.asList("lesson 1"))).size() == 1);
        assertTrue(captureDataFromCursor(target_db.getVocabularyCursorInLessons(new ArrayList<String>(Arrays.asList("lesson 1")),true)).size() == 1);
        assertTrue(target_db.getVocabularyInLessons(new ArrayList<String>(Arrays.asList("lesson 1", ""))).size() == 2);
        assertTrue(captureDataFromCursor(target_db.getVocabularyCursorInLessons(new ArrayList<String>(Arrays.asList("lesson 1", "")),true)).size() == 2);
        assertTrue(target_db.getVocabularyInLessons(new ArrayList<String>(Arrays.asList(""))).size() == 2);
        assertTrue(captureDataFromCursor(target_db.getVocabularyCursorInLessons(new ArrayList<String>(Arrays.asList("")),true)).size() == 2);
        target_db.assignWordLesson("lesson 1", "well", "好", "hao3");
        assertTrue(target_db.getVocabularyInLessons(new ArrayList<String>(Arrays.asList("lesson 1"))).size() == 1);
        assertTrue(captureDataFromCursor(target_db.getVocabularyCursorInLessons(new ArrayList<String>(Arrays.asList("lesson 1")),true)).size() == 1);
        assertTrue(target_db.getVocabularyInLessons(new ArrayList<String>(Arrays.asList("lesson 1", ""))).size() == 2);
        assertTrue(captureDataFromCursor(target_db.getVocabularyCursorInLessons(new ArrayList<String>(Arrays.asList("lesson 1", "")),true)).size() == 2);
        assertTrue(target_db.getVocabularyInLessons(new ArrayList<String>(Arrays.asList(""))).size() == 1);
        assertTrue(captureDataFromCursor(target_db.getVocabularyCursorInLessons(new ArrayList<String>(Arrays.asList("")),true)).size() == 1);
        target_db.assignWordLesson("lesson 1", "big", "大", "da4");
        assertTrue(target_db.getVocabularyInLessons(new ArrayList<String>(Arrays.asList("lesson 1"))).size() == 2);
        assertTrue(captureDataFromCursor(target_db.getVocabularyCursorInLessons(new ArrayList<String>(Arrays.asList("lesson 1")),true)).size() == 2);
        assertTrue(target_db.getVocabularyInLessons(new ArrayList<String>(Arrays.asList("lesson 1", ""))).size() == 2);
        assertTrue(captureDataFromCursor(target_db.getVocabularyCursorInLessons(new ArrayList<String>(Arrays.asList("lesson 1", "")),true)).size() == 2);
        assertTrue(target_db.getVocabularyInLessons(new ArrayList<String>(Arrays.asList(""))).size() == 0);
        assertTrue(captureDataFromCursor(target_db.getVocabularyCursorInLessons(new ArrayList<String>(Arrays.asList("")),true)).size() == 0);

        assertTrue(checkDB(target_db, false));
    }

    public void testLessonRename() {
        target_db.addLesson("lesson 1");
        target_db.addVocabulary("good", "好", "hao3");
        target_db.assignWordLesson("lesson 1", "good", "好", "hao3");
        target_db.renameLesson("lesson 1", "lesson 2");
        assertFalse(target_db.getLessons().contains("lesson 1"));
        assertTrue(target_db.getLessons().contains("lesson 2"));
        assertTrue(target_db.getVocabularyInLessons(new ArrayList<String>(Arrays.asList("lesson 2"))).size() == 1);
    }

    public void testRemoveFromLesson() {
        target_db.addVocabulary(new ArrayList<String>(Arrays.asList("down", "below")), "下", "xia4");
        target_db.addLesson("remove_lesson");
        target_db.assignWordLesson("remove_lesson", "down", "下", "xia4");
        target_db.assignWordLesson("remove_lesson", "below", "下", "xia4");
        assertTrue(combineListOfTranslations(target_db.getVocabularyInLessons(new ArrayList<String>(Arrays.asList("remove_lesson")))).contains("down"));
        assertTrue(combineListOfTranslations(target_db.getVocabularyInLessons(new ArrayList<String>(Arrays.asList("remove_lesson")))).contains("below"));
        target_db.removeWordLesson("remove_lesson", "down", "下", "xia4");
        assertFalse(combineListOfTranslations(target_db.getVocabularyInLessons(new ArrayList<String>(Arrays.asList("remove_lesson")))).contains("down"));
        assertTrue(combineListOfTranslations(target_db.getVocabularyInLessons(new ArrayList<String>(Arrays.asList("remove_lesson")))).contains("below"));
        assertTrue(checkDB(target_db, false));
    }

    public void testLessonsForWord() {
        target_db.addLesson("lesson 1");
        target_db.addLesson("lesson 2");
        target_db.addVocabulary(new ArrayList<String>(Arrays.asList("good", "well", "perfect")), "好", "hao3");
        target_db.assignWordLesson("lesson 1", "good", "好", "hao3");
        target_db.assignWordLesson("lesson 2", "well", "好", "hao3");
        assertTrue(target_db.getLessonForWord("好", "hao3", "good").contains("lesson 1"));
        assertFalse(target_db.getLessonForWord("好", "hao3", "good").contains("lesson 2"));
        assertTrue(target_db.getLessonForWord("好", "hao3", "well").contains("lesson 2"));
        assertFalse(target_db.getLessonForWord("好", "hao3", "well").contains("lesson 1"));
        assertTrue(target_db.getLessonForWord("好", "hao3", "perfect").size() == 0);
        assertTrue(checkDB(target_db, false));
    }

    public void testLessonRemoveAfterAssignment() {
        target_db.addVocabulary(new ArrayList<String>(Arrays.asList("down", "below")), "下", "xia4");
        target_db.addLesson("remove_lesson");
        target_db.assignWordLesson("remove_lesson", new Vocabulary("下", "xia4", new ArrayList<String>(Arrays.asList("down", "below"))));
        assertTrue(combineListOfTranslations(target_db.getVocabularyInLessons(new ArrayList<String>(Arrays.asList("remove_lesson")))).contains("down"));
        assertTrue(combineListOfTranslations(target_db.getVocabularyInLessons(new ArrayList<String>(Arrays.asList("remove_lesson")))).contains("below"));
        assertTrue(target_db.getLessons().size()==1);
        assertTrue(target_db.getLessonForWord("下", "xia4", "down").size() == 1);
        target_db.delLesson("remove_lesson");
        assertTrue(target_db.getLessons().size() == 0);
        assertTrue(target_db.getLessonForWord("下", "xia4","down").size()==0);
    }

    public void testLessonOrder() {
        target_db.addLesson("lesson 1");
        target_db.addLesson("lesson 2");
        target_db.addLesson("lesson 3");
        target_db.addLesson("lesson 4");
        target_db.addVocabulary(new ArrayList<String>(Arrays.asList("good", "well", "perfect")), "好", "hao3");
        target_db.assignWordLesson("lesson 1", "good", "好", "hao3");
        target_db.assignWordLesson("lesson 3", "well", "好", "hao3");
        target_db.sortLessons(new ArrayList<String>(Arrays.asList("lesson 4", "lesson 2", "lesson 3", "lesson 1")));
        assertTrue(target_db.getLessons().get(0).equals("lesson 4"));
        assertTrue(target_db.getLessons().get(1).equals("lesson 2"));
        assertTrue(target_db.getLessons().get(2).equals("lesson 3"));
        assertTrue(target_db.getLessons().get(3).equals("lesson 1"));

        // make sure all the words are there still
        assertTrue(target_db.getLessonForWord("好", "hao3", "good").contains("lesson 1"));
        assertTrue(target_db.getLessonForWord("好", "hao3", "well").contains("lesson 3"));
        assertTrue(target_db.getVocabularyInLessons(new ArrayList<String>(Arrays.asList("lesson 2"))).size()==0);
        assertTrue(target_db.getVocabularyInLessons(new ArrayList<String>(Arrays.asList("lesson 4"))).size()==0);

        target_db.sortLessons(new ArrayList<String>(Arrays.asList("lesson 1", "lesson 2", "lesson 4", "lesson 3")));
        assertTrue(target_db.getLessons().get(0).equals("lesson 1"));
        assertTrue(target_db.getLessons().get(1).equals("lesson 2"));
        assertTrue(target_db.getLessons().get(2).equals("lesson 4"));
        assertTrue(target_db.getLessons().get(3).equals("lesson 3"));
        assertTrue(checkDB(target_db, false));
    }

    public void testFiltersDB() {
        target_db.addVocabulary(new ArrayList<String>(Arrays.asList("good", "well", "perfect")), "好", "hao3");
        target_db.addLesson("lesson filter");
        target_db.assignWordLesson("lesson filter", "good", "好", "hao3");
        target_db.assignWordLesson("lesson filter", "well", "好", "hao3");
        target_db.setLessonsToFilter(new ArrayList<String>(Arrays.asList("lesson filter")));
        target_db.enableFilter(true);
        assertTrue(combineListOfTranslations(target_db.getFilteredTranslations("好")).contains("good"));
        assertTrue(combineListOfTranslations(target_db.getFilteredTranslations("好", "hao3")).contains("good"));
        assertFalse(combineListOfTranslations(target_db.getFilteredTranslations("好", "ni3")).contains("good"));
        assertTrue(combineListOfTranslations(target_db.getFilteredTranslations("好")).contains("well"));
        assertFalse(combineListOfTranslations(target_db.getFilteredTranslations("好")).contains("perfect"));
        assertFalse(combineListOfTranslations(target_db.getFilteredTranslations("好", "hao3")).contains("perfect"));
        assertTrue(captureDataFromCursor(target_db.getFilteredCTranslations("good")).get(0).chinese.equals("好"));
        assertTrue(captureDataFromCursor(target_db.getFilteredCTranslations("perfect")).size()==0);
        assertTrue(checkDB(target_db, false));
    }

    public void testFilteredQueries() {
        target_db.addVocabulary(new ArrayList<String>(Arrays.asList("good", "well", "perfect")), "好", "hao3");
        target_db.addVocabulary(new ArrayList<String>(Arrays.asList("hello", "hi")), "你好", "ni3hao3");
        target_db.addLesson("lesson filter");
        target_db.assignWordLesson("lesson filter", "good", "好", "hao3");
        target_db.assignWordLesson("lesson filter", "well", "好", "hao3");
        target_db.assignWordLesson("lesson filter", "hello", "你好", "ni3hao3");
        target_db.setLessonsToFilter(new ArrayList<String>(Arrays.asList("lesson filter")));
        target_db.enableFilter(true);
        assertTrue(combineListOfTranslations(target_db.getFilteredWordsWithPinyin("好")).contains("good"));
        assertTrue(combineListOfTranslations(target_db.getFilteredWordsWithPinyin("好")).contains("well"));
        assertTrue(combineListOfTranslations(target_db.getFilteredWordsWithPinyin("好")).contains("hello"));
        assertFalse(combineListOfTranslations(target_db.getFilteredWordsWithPinyin("好")).contains("hi"));
        assertTrue(combineListOfTranslations(target_db.getFilteredWordsWithPinyin("好", "hao3")).contains("hello"));
        assertFalse(combineListOfTranslations(target_db.getFilteredWordsWithPinyin("好", "hao3")).contains("perfect"));
        assertFalse(combineListOfTranslations(target_db.getFilteredWordsWithPinyin("好", "hao3")).contains("hi"));
        assertFalse(combineListOfTranslations(target_db.getFilteredWordsWithPinyin("好", "ni3")).contains("hello"));
        assertTrue(checkDB(target_db, false));
    }

    public void testNonDuplicatedVocabularyResults() {
        target_db.addVocabulary(new ArrayList<String>(Arrays.asList("good", "well", "perfect")), "好", "hao3");
        target_db.addLesson("lesson filter");
        target_db.assignWordLesson("lesson filter", "good", "好", "hao3");
        target_db.assignWordLesson("lesson filter", "well", "好", "hao3");
        target_db.setLessonsToFilter(new ArrayList<String>(Arrays.asList("lesson filter")));
        target_db.enableFilter(true);
        assertTrue(target_db.getTranslations("好").size() == 1);
        assertTrue(target_db.getWordsWithPinyin("好", "hao3").size() == 1);
        assertTrue(target_db.getWordsWithPinyin("好").size() == 1);
        assertTrue(target_db.getFilteredTranslations("好").size() == 1);
        assertTrue(target_db.getFilteredWordsWithPinyin("好", "hao3").size() == 1);
        assertTrue(target_db.getFilteredWordsWithPinyin("好").size() == 1);
    }

    private static final long three_hours = 3 * 60 * 60;
    private static final long three_days = 3 * 24 * 60 * 60;
    private static final long three_weeks = 3 * 7 * 24 * 60 * 60;
    private static final long three_months = 3 * 30 * 24 * 60 * 60;

    public void testCharPath() {
        target_db.addVocabulary("hello", "你好", "ni3hao3");
        assertTrue(target_db.getBoxForChar("你") == 0);
        assertTrue(target_db.getNumCharsInBox(0) == 2);
        target_db.betterBox("你");
        assertTrue(target_db.getBoxForChar("你") == 1);
        target_db.betterBox("你");
        assertTrue(target_db.getBoxForChar("你")==2);
        target_db.betterBox("你");
        assertTrue(target_db.getBoxForChar("你")==3);
        target_db.betterBox("你");
        assertTrue(target_db.getBoxForChar("你")==4);
        target_db.betterBox("好");
        assertTrue(target_db.getBoxForChar("好")==1);
        target_db.restartStudy("好");
        assertTrue(target_db.getBoxForChar("好")==0);
        assertTrue(checkDB(target_db, false));
    }

    public void testNextToStudy() {
        target_db.addVocabulary("you", "你", "ni3");
        target_db.addVocabulary("good", "好", "hao3");
        target_db.restartStudy("你");
        assertTrue(target_db.getAllFilteredToStudy().contains("你"));
        assertTrue(target_db.getAllFilteredToStudy().contains("好"));
        target_db.betterBox("好");
        assertTrue(target_db.getAllFilteredToStudy().size() == 1);
        assertTrue(target_db.getAllFilteredToStudy().get(0).equals("你"));
        target_db.betterBox("你");
        assertTrue(target_db.getAllFilteredToStudy().size() == 0);
        target_db.restartStudy("好");
        assertTrue(target_db.getAllFilteredToStudy().size() == 1);
        assertTrue(target_db.getAllFilteredToStudy().get(0).equals("好"));
        assertTrue(checkDB(target_db, false));
    }

    public void testNextToStudyNeverUsed() {
        target_db.addVocabulary("you", "你", "ni3");
        assertTrue(target_db.getAllFilteredToStudy().size() == 1);
        assertTrue(target_db.getAllFilteredToStudy().get(0).equals("你"));
        assertTrue(checkDB(target_db, false));
    }

    private void delay(long millis) {
        long current_time = System.currentTimeMillis();
        while(System.currentTimeMillis() < current_time+millis) {}
    }

    /*
    public void testExpiration() {
        target_db.addVocabulary("hello", new ArrayList<String>(Arrays.asList("你", "好")), new ArrayList<String>(Arrays.asList("ni3", "hao3")));
        assertTrue(target_db.getNumCharsExpiredInBox(0) == 2);
        target_db.betterBox("你");
        assertTrue(target_db.getNumCharsExpiredInBox(0) == 1);
        assertTrue(target_db.getNumCharsExpiredInBox(1) == 0);
        long delay_millis = target_db.getExpiryTime(1);
        delay(delay_millis);
        assertTrue(target_db.getNumCharsExpiredInBox(0) == 1);
        assertTrue(target_db.getNumCharsExpiredInBox(1) == 1);
        target_db.resetTime("你");
        assertTrue(target_db.getNumCharsExpiredInBox(0) == 1);
        assertTrue(target_db.getNumCharsExpiredInBox(1) == 0);
        assertTrue(checkDB(target_db, false));
    }
    */

    public void testBoxDistribution() {
        target_db.addVocabulary("hello", "你好", "ni3hao3");
        target_db.addVocabulary("grow", "长", "zhang3");
        target_db.addVocabulary(new ArrayList<String>(Arrays.asList("bright", "next", "wise")),"明","ming2");
        target_db.addVocabulary("west", "西", "xi1");
        target_db.setBoxForChar("你", 0);
        target_db.setBoxForChar("好", 0);
        assertTrue(target_db.getNumChars() == 5);
        assertTrue(target_db.getNumCharsInBox(0) == 5);
        assertTrue(target_db.getNumCharsInBoxOrAbove(0) == 5);
        target_db.setBoxForChar("西", 1);
        target_db.setBoxForChar("明", 2);
        assertTrue(target_db.getNumCharsInBox(0)==3);
        assertTrue(target_db.getNumCharsInBoxOrAbove(0)==5);
        assertTrue(target_db.getNumCharsInBoxOrAbove(1)==2);
        target_db.setExpirationTimeForChar("西", System.currentTimeMillis() - 100);
        target_db.setExpirationTimeForChar("明", System.currentTimeMillis() - 100);
        target_db.setBoxForChar("长", 1);
        target_db.setExpirationTimeForChar("长", System.currentTimeMillis() + 60 * 60 * 1000);
        assertTrue(target_db.getNumCharsExpiredInBox(0)==2);
        assertTrue(target_db.getNumCharsExpiredInBox(1)==1);
        assertTrue(target_db.getNumCharsExpiredInBoxOrAbove(0) == 4);
        assertTrue(target_db.getNumCharsExpiredInBoxOrAbove(1)==2);
        assertTrue(checkDB(target_db, false));
    }

    public void testNextToStudyFiltered() {
        target_db.addVocabulary(new ArrayList<String>(Arrays.asList("good", "well", "perfect")), "好", "hao3");
        target_db.addVocabulary("you", "你", "ni3");
        target_db.addVocabulary("hello", "你好", "ni3hao3");
        target_db.addLesson("lesson filter");
        target_db.assignWordLesson("lesson filter", "good", "好", "hao3");
        target_db.assignWordLesson("lesson filter", "well", "好", "hao3");
        target_db.setLessonsToFilter(new ArrayList<String>(Arrays.asList("lesson filter")));
        target_db.enableFilter(true);
        // only 好 is in the filter
        // check when leitner is not initialized for those
        assertTrue(target_db.getAllFilteredToStudy().contains("好"));
        assertTrue(voc_to_chinese(captureDataFromCursor(target_db.getCursorFilteredCharsToStudy())).contains("好"));
        assertFalse(target_db.getAllFilteredToStudy().contains("你"));
        assertFalse(voc_to_chinese(captureDataFromCursor(target_db.getCursorFilteredCharsToStudy())).contains("你"));
        target_db.restartStudy("你");
        target_db.restartStudy("好");
        // check when leitner is initialized for those
        assertTrue(target_db.getAllFilteredToStudy().contains("好"));
        assertTrue(voc_to_chinese(captureDataFromCursor(target_db.getCursorFilteredCharsToStudy())).contains("好"));
        assertFalse(target_db.getAllFilteredToStudy().contains("你"));
        assertFalse(voc_to_chinese(captureDataFromCursor(target_db.getCursorFilteredCharsToStudy())).contains("你"));
        // check when a character should not be studied anymore (for now)
        target_db.betterBox("好");
        assertTrue(target_db.getAllFilteredToStudy().size() == 0);
        assertTrue(captureDataFromCursor(target_db.getCursorFilteredCharsToStudy()).size() == 0);
        // check after the filter is disabled
        target_db.enableFilter(false);
        assertTrue(target_db.getAllFilteredToStudy().contains("你"));
        assertTrue(voc_to_chinese(captureDataFromCursor(target_db.getCursorFilteredCharsToStudy())).contains("你"));
        // check again after enabling the filter
        target_db.enableFilter(true);
        assertFalse(target_db.getAllFilteredToStudy().contains("你"));
        assertFalse(voc_to_chinese(captureDataFromCursor(target_db.getCursorFilteredCharsToStudy())).contains("你"));
        // check after adding more into the filtered lessons
        target_db.assignWordLesson("lesson filter", "hello", "你好", "ni3hao3");
        assertTrue(target_db.getAllFilteredToStudy().contains("你"));
        assertTrue(voc_to_chinese(captureDataFromCursor(target_db.getCursorFilteredCharsToStudy())).contains("你"));
        assertTrue(checkDB(target_db, false));
    }

    public void testExportAllNoScores() {
        target_db.addVocabulary(new ArrayList<String>(Arrays.asList("good", "well", "perfect")), "好", "hao3");
        target_db.addVocabulary("you", "你", "ni3");
        target_db.addVocabulary("hello", "你好", "ni3hao3");
        target_db.addVocabulary("grow", "长", "zhang3");
        target_db.addVocabulary("west wind", "西风", "xi1feng1");
        target_db.addLesson("lesson 1");
        target_db.addLesson("lesson 2");
        target_db.assignWordLesson("lesson 1", "good", "好", "hao3");
        target_db.assignWordLesson("lesson 1", "you", "你", "ni3");
        target_db.setBoxForChar("你", 3);
        target_db.setBoxForChar("西", 4);
        try {
            target_db.create_export_task(appContext.getDatabasePath("exported_db").getPath(), false, false, false, null, null).get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        HanziDB export_db = new HanziDB(app, "exported_db");
        assertTrue(export_db.getAllChars().size() == 5);
        assertTrue(export_db.getLessons().size() == 2);
        assertTrue(export_db.getVocabularyInLessons(new ArrayList<String>(Arrays.asList("lesson 1"))).size() == 2);
        assertTrue(export_db.getBoxForChar("你") == 0);
        assertTrue(export_db.getNumCharsInBox(0) == 5);
        assertTrue(checkDB(export_db, false));
        assertTrue(checkDB(target_db, false));
        export_db.close();
        ContextWrapper cw = new ContextWrapper(appContext);
        cw.deleteDatabase("exported_db");
    }

    public void testThingsToStudy() {
        target_db.addVocabulary(new ArrayList<String>(Arrays.asList("good", "well", "perfect")), "好", "hao3");
        target_db.addVocabulary("you", "你", "ni3");
        target_db.addVocabulary("hello", "你好", "ni3hao3");
        assertTrue(captureDataFromCursor(target_db.getCursorToStudy()).size()==3);
        target_db.addVocabulary("west wind", "西风", "xi1feng1");
        assertTrue(captureDataFromCursor(target_db.getCursorToStudy()).size()==6);
    }

    public void testExportAllWithScores() {
        target_db.addVocabulary(new ArrayList<String>(Arrays.asList("good", "well", "perfect")), "好", "hao3");
        target_db.addVocabulary("you", "你", "ni3");
        target_db.addVocabulary("hello", "你好", "ni3hao3");
        target_db.addVocabulary("grow", "长", "zhang3");
        target_db.addVocabulary("west wind", "西风", "xi1feng1");
        target_db.addLesson("lesson 1");
        target_db.addLesson("lesson 2");
        target_db.assignWordLesson("lesson 1", "good", "好", "hao3");
        target_db.assignWordLesson("lesson 1", "well", "好", "hao3");
        target_db.setBoxForChar("你", 3);
        target_db.setBoxForChar("西", 4);
        try {
            target_db.create_export_task(appContext.getDatabasePath("exported_db").getPath(), true, false, false, null, null).get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        HanziDB export_db = new HanziDB(app, "exported_db");
        assertTrue(export_db.getBoxForChar("你")==3);
        assertTrue(export_db.getNumCharsInBox(0)==3);
        assertTrue(checkDB(export_db, false));
        assertTrue(checkDB(target_db, false));
        export_db.close();
        ContextWrapper cw = new ContextWrapper(this.appContext);
        cw.deleteDatabase("exported_db");
    }

    public void testExportSomeLessons() {
        target_db.addVocabulary(new ArrayList<String>(Arrays.asList("good", "well", "perfect")), "好", "hao3");
        target_db.addVocabulary("you", "你", "ni3");
        target_db.addVocabulary("hello", "你好", "ni3hao3");
        target_db.addVocabulary("grow", "长", "zhang3");
        target_db.addVocabulary("west", "西", "xi1");
        target_db.addLesson("lesson 1");
        target_db.addLesson("lesson 2");
        target_db.addLesson("lesson 3");
        target_db.assignWordLesson("lesson 1", "good", "好", "hao3");
        target_db.assignWordLesson("lesson 1", "you", "你", "ni3");
        target_db.assignWordLesson("lesson 2", "grow", "长", "zhang3");
        target_db.assignWordLesson("lesson 3", "west", "西", "xi1");
        target_db.setLessonsToFilter(new ArrayList<String>(Arrays.asList("lesson 1", "lesson 2")));
        try {
            target_db.create_export_task(appContext.getDatabasePath("exported_db").getPath(), false, true, false, null, null).get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        HanziDB export_db = new HanziDB(app, "exported_db");
        assertTrue(export_db.getAllChars().size()==3);
        assertTrue(export_db.getLessons().size()==2);
        assertTrue(export_db.getVocabularyInLessons(new ArrayList<String>(Arrays.asList("lesson 1"))).size() == 2);
        assertTrue(checkDB(export_db, false));
        assertTrue(checkDB(target_db, false));
        export_db.close();
        ContextWrapper cw = new ContextWrapper(appContext);
        cw.deleteDatabase("exported_db");
    }

    public void testMergeDB() {
        target_db.addVocabulary(new ArrayList<String>(Arrays.asList("good", "well", "perfect")), "好", "hao3");
        target_db.addVocabulary("you", "你", "ni3");
        target_db.addVocabulary("grow", "长", "zhang3");
        target_db.addLesson("lesson 1");
        target_db.addLesson("lesson 2");
        target_db.assignWordLesson("lesson 1", "good", "好", "hao3");
        target_db.assignWordLesson("lesson 1", "you", "你", "ni3");
        target_db.assignWordLesson("lesson 2", "grow", "长", "zhang3");

        ContextWrapper cw = new ContextWrapper(appContext);
        cw.deleteDatabase("other_db");
        HanziDB other_db = new HanziDB(app, "other_db");
        other_db.addVocabulary("hello", "你好", "ni3hao3");
        other_db.addVocabulary("grow", "长", "zhang3");
        other_db.addVocabulary("wind", "风", "feng1");
        other_db.addVocabulary("west", "西", "xi1");
        other_db.addLesson("lesson 2");
        other_db.addLesson("lesson 3");
        other_db.assignWordLesson("lesson 2", "hello", "你好", "ni3hao3");
        other_db.assignWordLesson("lesson 3", "grow", "长", "zhang3");
        other_db.assignWordLesson("lesson 3", "west", "西", "xi1");

        try {
            target_db.create_merge_task(other_db.getReadableDatabase().getPath(), false, null, null).get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        assertTrue(target_db.getAllChars().size()==5);
        assertTrue(target_db.getLessons().size()==3);
        assertTrue(target_db.getVocabularyInLessons(new ArrayList<String>(Arrays.asList("lesson 2"))).size()==2);
        assertTrue(target_db.getLessonForWord("长", "zhang3", "grow").size()==2);
        assertTrue(checkDB(target_db, false));
    }

    public void testMergeVoc() {
        ArrayList<Vocabulary> A = new ArrayList<Vocabulary>();
        A.add(new Vocabulary("长", "zhang3", "grow"));
        A.add(new Vocabulary("你好", "ni3hao3", "hello"));
        A.add(new Vocabulary("西", "xi1", "west"));
        ArrayList<Vocabulary> B = new ArrayList<Vocabulary>();
        B.add(new Vocabulary("你好", "ni3hao3", "hi"));
        B.add(new Vocabulary("长", "chang2", "long"));

        HanziDB.merge_vocabulary(A, B);
        assertTrue(A.size() == 4);
    }

    public void testGetSequence() {
        target_db.addVocabulary("you", "你", "ni3");
        target_db.addVocabulary("to be", "是", "shi4");
        target_db.addVocabulary("good", "好", "hao3");
        target_db.addVocabulary("well", "好", "hao3");
        target_db.addVocabulary("very good", "好好", "hao3hao3");
        target_db.addVocabulary("not", "不", "bu4");
        target_db.addVocabulary("hello", "你好", "ni3hao3");
        target_db.addVocabulary("how are you?", "你好吗", "ni3hao3ma");
        target_db.addVocabulary("no good", "不好", "bu4hao3");
        target_db.addVocabulary("you no good", "你不好", "ni3bu4hao3");
        target_db.addVocabulary("tasty", "好吃", "hao3chi1");
        target_db.addVocabulary("all right?", "好不好", "hao3bu4hao3");
        target_db.addVocabulary("no good", "不太好", "bu4tai4hao3");
        target_db.addVocabulary("unattractive", "不好看", "bu4hao3kan4");
        target_db.addVocabulary("hard to say", "不好说", "bu4hao3shuo1");
        target_db.addVocabulary("easy to use", "好使", "hao3shi3");
        target_db.addVocabulary("difficult to use", "不好使", "bu4hao3shi3");
        target_db.addVocabulary("yes or no", "是不是", "shi4bu4shi4");
        target_db.addVocabulary("hershey's", "好时", "hao3shi2");
        target_db.addVocabulary("eager to be first", "好强", "hao3qiang2");
        target_db.addVocabulary("inside", "里边", "li3bian5");
        target_db.addVocabulary("already", "便已", "bian4yi1");
        ArrayList<Vocabulary> res;
        long max_time=0;
        HanziDB.StringElement ni_pinyin = new HanziDB.StringElement(HanziDB.StringElement.type_t.PINYIN_WITH_TONE, "ni3");
        HanziDB.StringElement bu_pinyin = new HanziDB.StringElement(HanziDB.StringElement.type_t.PINYIN_WITH_TONE, "bu4");
        HanziDB.StringElement bu_char = new HanziDB.StringElement(HanziDB.StringElement.type_t.CHINESE, "不");
        HanziDB.StringElement hao_char = new HanziDB.StringElement(HanziDB.StringElement.type_t.CHINESE, "好");
        HanziDB.StringElement hao_pinyin = new HanziDB.StringElement(HanziDB.StringElement.type_t.PINYIN_WITH_TONE, "hao3");
        HanziDB.StringElement shi_no_tone_pinyin = new HanziDB.StringElement(HanziDB.StringElement.type_t.PINYIN_WITHOUT_TONE, "shi");
        HanziDB.StringElement chi_no_tone_pinyin = new HanziDB.StringElement(HanziDB.StringElement.type_t.INCOMPLETE_PINYIN, "chi");
        HanziDB.StringElement b_incomplete_pinyin = new HanziDB.StringElement(HanziDB.StringElement.type_t.INCOMPLETE_PINYIN, "b");

        // ** test only one element
        long current_time = System.currentTimeMillis();
        res = captureDataFromCursor(target_db.getWordsWithSequence(new ArrayList<HanziDB.StringElement>(Arrays.asList(hao_char)), false, false));
        long difference = System.currentTimeMillis()-current_time;
        if (difference > max_time) max_time=difference;
        assertTrue(res.size()==15);
        assertTrue(res.get(0).chinese.equals("好"));
        assertTrue(res.get(0).english.size()==2);
        assertTrue(res.get(7).chinese.equals("不好"));
        current_time = System.currentTimeMillis();
        res = captureDataFromCursor(target_db.getWordsWithSequence(new ArrayList<HanziDB.StringElement>(Arrays.asList(bu_pinyin)), false, false));
        difference = System.currentTimeMillis()-current_time;
        if (difference > max_time) max_time=difference;
        assertTrue(res.size()==9);
        assertTrue(res.get(0).chinese.equals("不"));
        assertTrue(res.get(6).chinese.equals("好不好"));
        current_time = System.currentTimeMillis();
        res = captureDataFromCursor(target_db.getWordsWithSequence(new ArrayList<HanziDB.StringElement>(Arrays.asList(shi_no_tone_pinyin)), false, false));
        difference = System.currentTimeMillis()-current_time;
        if (difference > max_time) max_time=difference;
        assertTrue(res.size()==5);
        assertTrue(res.get(0).chinese.equals("是"));
        assertTrue(res.get(1).chinese.equals("是不是"));
        assertTrue(res.get(4).chinese.equals("不好使"));
        current_time = System.currentTimeMillis();
        res = captureDataFromCursor(target_db.getWordsWithSequence(new ArrayList<HanziDB.StringElement>(Arrays.asList(b_incomplete_pinyin)), false, false));
        difference = System.currentTimeMillis()-current_time;
        if (difference > max_time) max_time=difference;
        assertTrue(res.size()==11);
        assertTrue(res.get(0).chinese.equals("不"));
        assertTrue(res.get(1).chinese.equals("便已"));
        assertTrue(res.get(7).chinese.equals("里边"));

        // Only Chinese
        current_time = System.currentTimeMillis();
        res = captureDataFromCursor(target_db.getWordsWithSequence(new ArrayList<HanziDB.StringElement>(Arrays.asList(bu_char, hao_char)), false, false));
        difference = System.currentTimeMillis()-current_time;
        if (difference > max_time) max_time=difference;
        assertTrue(res.size()==6);
        assertTrue(res.get(0).chinese.equals("不好"));
        assertTrue(res.get(5).chinese.equals("你不好"));

        // Only Pinyin
        current_time = System.currentTimeMillis();
        res = captureDataFromCursor(target_db.getWordsWithSequence(new ArrayList<HanziDB.StringElement>(Arrays.asList(bu_pinyin, hao_pinyin)), false, false));
        difference = System.currentTimeMillis()-current_time;
        if (difference > max_time) max_time=difference;
        assertTrue(res.size()==6);
        assertTrue(res.get(0).chinese.equals("不好"));
        assertTrue(res.get(5).chinese.equals("你不好"));

        // Mixed
        current_time = System.currentTimeMillis();
        res = captureDataFromCursor(target_db.getWordsWithSequence(new ArrayList<HanziDB.StringElement>(Arrays.asList(bu_pinyin, hao_char)), false, false));
        difference = System.currentTimeMillis()-current_time;
        if (difference > max_time) max_time=difference;
        assertTrue(res.size()==6);
        assertTrue(res.get(0).chinese.equals("不好"));
        assertTrue(res.get(5).chinese.equals("你不好"));
        current_time = System.currentTimeMillis();
        res = captureDataFromCursor(target_db.getWordsWithSequence(new ArrayList<HanziDB.StringElement>(Arrays.asList(hao_char, shi_no_tone_pinyin)), false, false));
        difference = System.currentTimeMillis()-current_time;
        if (difference > max_time) max_time=difference;
        assertTrue(res.size()==3);
        assertTrue(res.get(0).chinese.equals("好时"));
        assertTrue(res.get(2).chinese.equals("不好使"));
        current_time = System.currentTimeMillis();
        res = captureDataFromCursor(target_db.getWordsWithSequence(new ArrayList<HanziDB.StringElement>(Arrays.asList(b_incomplete_pinyin, hao_char)), false, false));
        difference = System.currentTimeMillis()-current_time;
        if (difference > max_time) max_time=difference;
        assertTrue(res.size()==6);
        assertTrue(res.get(0).chinese.equals("不好"));
        assertTrue(res.get(5).chinese.equals("你不好"));
        Log.d(TAG, "testGetSequence: Maximum time for a query is "+max_time);
    }

    public void testGetSequenceGrouped() {
        target_db.addVocabulary("you", "你", "ni3");
        target_db.addVocabulary("to be", "是", "shi4");
        target_db.addVocabulary("good", "好", "hao3");
        target_db.addVocabulary("well", "好", "hao3");
        target_db.addVocabulary("very good", "好好", "hao3hao3");
        target_db.addVocabulary("not", "不", "bu4");
        target_db.addVocabulary("hello", "你好", "ni3hao3");
        target_db.addVocabulary("how are you?", "你好吗", "ni3hao3ma");
        target_db.addVocabulary("no good", "不好", "bu4hao3");
        target_db.addVocabulary("you no good", "你不好", "ni3bu4hao3");
        target_db.addVocabulary("tasty", "好吃", "hao3chi1");
        target_db.addVocabulary("all right?", "好不好", "hao3bu4hao3");
        target_db.addVocabulary("no good", "不太好", "bu4tai4hao3");
        target_db.addVocabulary("unattractive", "不好看", "bu4hao3kan4");
        target_db.addVocabulary("hard to say", "不好说", "bu4hao3shuo1");
        target_db.addVocabulary("easy to use", "好使", "hao3shi3");
        target_db.addVocabulary("difficult to use", "不好使", "bu4hao3shi3");
        target_db.addVocabulary("yes or no", "是不是", "shi4bu4shi4");
        target_db.addVocabulary("hershey's", "好时", "hao3shi2");
        target_db.addVocabulary("eager to be first", "好强", "hao3qiang2");
        target_db.addVocabulary("inside", "里边", "li3bian5");
        target_db.addVocabulary("already", "便已", "bian4yi1");
        ArrayList<Vocabulary> res;
        long max_time=0;
        HanziDB.StringElement ni_pinyin = new HanziDB.StringElement(HanziDB.StringElement.type_t.PINYIN_WITH_TONE, "ni3");
        HanziDB.StringElement bu_pinyin = new HanziDB.StringElement(HanziDB.StringElement.type_t.PINYIN_WITH_TONE, "bu4");
        HanziDB.StringElement bu_char = new HanziDB.StringElement(HanziDB.StringElement.type_t.CHINESE, "不");
        HanziDB.StringElement hao_char = new HanziDB.StringElement(HanziDB.StringElement.type_t.CHINESE, "好");
        HanziDB.StringElement hao_pinyin = new HanziDB.StringElement(HanziDB.StringElement.type_t.PINYIN_WITH_TONE, "hao3");
        HanziDB.StringElement shi_no_tone_pinyin = new HanziDB.StringElement(HanziDB.StringElement.type_t.PINYIN_WITHOUT_TONE, "shi");
        HanziDB.StringElement chi_no_tone_pinyin = new HanziDB.StringElement(HanziDB.StringElement.type_t.INCOMPLETE_PINYIN, "chi");
        HanziDB.StringElement b_incomplete_pinyin = new HanziDB.StringElement(HanziDB.StringElement.type_t.INCOMPLETE_PINYIN, "b");

        // ** test only one element
        long current_time = System.currentTimeMillis();
        res = captureDataFromCursor(target_db.getWordsWithSequence(new ArrayList<HanziDB.StringElement>(Arrays.asList(hao_char)), false, true));
        long difference = System.currentTimeMillis()-current_time;
        if (difference > max_time) max_time=difference;
        assertTrue(res.size()==15);
        assertTrue(res.get(0).chinese.equals("好"));
        assertTrue(res.get(0).english.size()==1);
        assertTrue(res.get(7).chinese.equals("不好"));
        current_time = System.currentTimeMillis();
        res = captureDataFromCursor(target_db.getWordsWithSequence(new ArrayList<HanziDB.StringElement>(Arrays.asList(bu_pinyin)), false, true));
        difference = System.currentTimeMillis()-current_time;
        if (difference > max_time) max_time=difference;
        assertTrue(res.size()==9);
        assertTrue(res.get(0).chinese.equals("不"));
        assertTrue(res.get(6).chinese.equals("好不好"));
        current_time = System.currentTimeMillis();
        res = captureDataFromCursor(target_db.getWordsWithSequence(new ArrayList<HanziDB.StringElement>(Arrays.asList(shi_no_tone_pinyin)), false, true));
        difference = System.currentTimeMillis()-current_time;
        if (difference > max_time) max_time=difference;
        assertTrue(res.size()==5);
        assertTrue(res.get(0).chinese.equals("是"));
        assertTrue(res.get(1).chinese.equals("是不是"));
        assertTrue(res.get(4).chinese.equals("不好使"));
        current_time = System.currentTimeMillis();
        res = captureDataFromCursor(target_db.getWordsWithSequence(new ArrayList<HanziDB.StringElement>(Arrays.asList(b_incomplete_pinyin)), false, true));
        difference = System.currentTimeMillis()-current_time;
        if (difference > max_time) max_time=difference;
        assertTrue(res.size()==11);
        assertTrue(res.get(0).chinese.equals("不"));
        assertTrue(res.get(1).chinese.equals("便已"));
        assertTrue(res.get(7).chinese.equals("里边"));

        // Only Chinese
        current_time = System.currentTimeMillis();
        res = captureDataFromCursor(target_db.getWordsWithSequence(new ArrayList<HanziDB.StringElement>(Arrays.asList(bu_char, hao_char)), false, true));
        difference = System.currentTimeMillis()-current_time;
        if (difference > max_time) max_time=difference;
        assertTrue(res.size()==6);
        assertTrue(res.get(0).chinese.equals("不好"));
        assertTrue(res.get(5).chinese.equals("你不好"));

        // Only Pinyin
        current_time = System.currentTimeMillis();
        res = captureDataFromCursor(target_db.getWordsWithSequence(new ArrayList<HanziDB.StringElement>(Arrays.asList(bu_pinyin, hao_pinyin)), false, true));
        difference = System.currentTimeMillis()-current_time;
        if (difference > max_time) max_time=difference;
        assertTrue(res.size()==6);
        assertTrue(res.get(0).chinese.equals("不好"));
        assertTrue(res.get(5).chinese.equals("你不好"));

        // Mixed
        current_time = System.currentTimeMillis();
        res = captureDataFromCursor(target_db.getWordsWithSequence(new ArrayList<HanziDB.StringElement>(Arrays.asList(bu_pinyin, hao_char)), false, true));
        difference = System.currentTimeMillis()-current_time;
        if (difference > max_time) max_time=difference;
        assertTrue(res.size()==6);
        assertTrue(res.get(0).chinese.equals("不好"));
        assertTrue(res.get(5).chinese.equals("你不好"));
        current_time = System.currentTimeMillis();
        res = captureDataFromCursor(target_db.getWordsWithSequence(new ArrayList<HanziDB.StringElement>(Arrays.asList(hao_char, shi_no_tone_pinyin)), false, true));
        difference = System.currentTimeMillis()-current_time;
        if (difference > max_time) max_time=difference;
        assertTrue(res.size()==3);
        assertTrue(res.get(0).chinese.equals("好时"));
        assertTrue(res.get(2).chinese.equals("不好使"));
        current_time = System.currentTimeMillis();
        res = captureDataFromCursor(target_db.getWordsWithSequence(new ArrayList<HanziDB.StringElement>(Arrays.asList(b_incomplete_pinyin, hao_char)), false, true));
        difference = System.currentTimeMillis()-current_time;
        if (difference > max_time) max_time=difference;
        assertTrue(res.size()==6);
        assertTrue(res.get(0).chinese.equals("不好"));
        assertTrue(res.get(5).chinese.equals("你不好"));
        Log.d(TAG, "testGetSequence: Maximum time for a query is "+max_time);
    }

    public void testGetFilteredSequence() {
        target_db.addVocabulary("you", "你", "ni3");
        target_db.addVocabulary("to be", "是", "shi4");
        target_db.addVocabulary("good", "好", "hao3");
        target_db.addVocabulary("well", "好", "hao3");
        target_db.addVocabulary("very good", "好好", "hao3hao3");
        target_db.addVocabulary("not", "不", "bu4");
        target_db.addVocabulary("hello", "你好", "ni3hao3");
        target_db.addVocabulary("how are you?", "你好吗", "ni3hao3ma");
        target_db.addVocabulary("no good", "不好", "bu4hao3");
        target_db.addVocabulary("you no good", "你不好", "ni3bu4hao3");
        target_db.addVocabulary("tasty", "好吃", "hao3chi1");
        target_db.addVocabulary("all right?", "好不好", "hao3bu4hao3");
        target_db.addVocabulary("no good", "不太好", "bu4tai4hao3");
        target_db.addVocabulary("unattractive", "不好看", "bu4hao3kan4");
        target_db.addVocabulary("hard to say", "不好说", "bu4hao3shuo1");
        target_db.addVocabulary("easy to use", "好使", "hao3shi3");
        target_db.addVocabulary("difficult to use", "不好使", "bu4hao3shi3");
        target_db.addVocabulary("yes or no", "是不是", "shi4bu4shi4");
        target_db.addVocabulary("hershey's", "好时", "hao3shi2");
        target_db.addVocabulary("eager to be first", "好强", "hao3qiang2");
        target_db.addVocabulary("inside", "里边", "li3bian5");
        target_db.addVocabulary("already", "便已", "bian4yi1");
        target_db.addLesson("lesson filter");
        target_db.assignWordLesson("lesson filter", "good", "好", "hao3");
        target_db.assignWordLesson("lesson filter", "you", "你", "ni3");
        target_db.assignWordLesson("lesson filter", "you no good", "你不好", "ni3bu4hao3");
        target_db.assignWordLesson("lesson filter", "yes or no", "是不是", "shi4bu4shi4");
        target_db.assignWordLesson("lesson filter",  "no good", "不太好", "bu4tai4hao3");
        target_db.assignWordLesson("lesson filter",  "inside", "里边", "li3bian5");
        target_db.setLessonsToFilter(new ArrayList<String>(Arrays.asList("lesson filter")));
        target_db.enableFilter(true);
        ArrayList<Vocabulary> res;
        long max_time=0;
        HanziDB.StringElement ni_pinyin = new HanziDB.StringElement(HanziDB.StringElement.type_t.PINYIN_WITH_TONE, "ni3");
        HanziDB.StringElement bu_pinyin = new HanziDB.StringElement(HanziDB.StringElement.type_t.PINYIN_WITH_TONE, "bu4");
        HanziDB.StringElement bu_char = new HanziDB.StringElement(HanziDB.StringElement.type_t.CHINESE, "不");
        HanziDB.StringElement hao_char = new HanziDB.StringElement(HanziDB.StringElement.type_t.CHINESE, "好");
        HanziDB.StringElement hao_pinyin = new HanziDB.StringElement(HanziDB.StringElement.type_t.PINYIN_WITH_TONE, "hao3");
        HanziDB.StringElement shi_no_tone_pinyin = new HanziDB.StringElement(HanziDB.StringElement.type_t.PINYIN_WITHOUT_TONE, "shi");
        HanziDB.StringElement chi_no_tone_pinyin = new HanziDB.StringElement(HanziDB.StringElement.type_t.INCOMPLETE_PINYIN, "chi");
        HanziDB.StringElement b_incomplete_pinyin = new HanziDB.StringElement(HanziDB.StringElement.type_t.INCOMPLETE_PINYIN, "b");

        // ** test only one element
        long current_time = System.currentTimeMillis();
        res = captureDataFromCursor(target_db.getWordsWithSequence(new ArrayList<HanziDB.StringElement>(Arrays.asList(hao_char)), true, false));
        long difference = System.currentTimeMillis()-current_time;
        if (difference > max_time) max_time=difference;
        assertTrue(res.size()==3);
        assertTrue(res.get(0).chinese.equals("好"));
        assertTrue(res.get(0).english.size()==1);
        assertTrue(res.get(2).chinese.equals("你不好"));
        current_time = System.currentTimeMillis();
        res = captureDataFromCursor(target_db.getWordsWithSequence(new ArrayList<HanziDB.StringElement>(Arrays.asList(bu_pinyin)), true, false));
        difference = System.currentTimeMillis()-current_time;
        if (difference > max_time) max_time=difference;
        assertTrue(res.size()==3);
        assertTrue(res.get(0).chinese.equals("不太好"));
        assertTrue(res.get(1).chinese.equals("你不好"));
        current_time = System.currentTimeMillis();
        res = captureDataFromCursor(target_db.getWordsWithSequence(new ArrayList<HanziDB.StringElement>(Arrays.asList(shi_no_tone_pinyin)), true, false));
        difference = System.currentTimeMillis()-current_time;
        if (difference > max_time) max_time=difference;
        assertTrue(res.size()==1);
        assertTrue(res.get(0).chinese.equals("是不是"));
        current_time = System.currentTimeMillis();
        res = captureDataFromCursor(target_db.getWordsWithSequence(new ArrayList<HanziDB.StringElement>(Arrays.asList(b_incomplete_pinyin)), true, false));
        difference = System.currentTimeMillis()-current_time;
        if (difference > max_time) max_time=difference;
        assertTrue(res.size()==4);
        assertTrue(res.get(0).chinese.equals("不太好"));
        assertTrue(res.get(2).chinese.equals("你不好"));

        // Only Chinese
        current_time = System.currentTimeMillis();
        res = captureDataFromCursor(target_db.getWordsWithSequence(new ArrayList<HanziDB.StringElement>(Arrays.asList(bu_char, hao_char)), true, false));
        difference = System.currentTimeMillis()-current_time;
        if (difference > max_time) max_time=difference;
        assertTrue(res.size()==1);
        assertTrue(res.get(0).chinese.equals("你不好"));

        // Only Pinyin
        current_time = System.currentTimeMillis();
        res = captureDataFromCursor(target_db.getWordsWithSequence(new ArrayList<HanziDB.StringElement>(Arrays.asList(bu_pinyin, hao_pinyin)), true, false));
        difference = System.currentTimeMillis()-current_time;
        if (difference > max_time) max_time=difference;
        assertTrue(res.size()==1);
        assertTrue(res.get(0).chinese.equals("你不好"));

        // Mixed
        current_time = System.currentTimeMillis();
        res = captureDataFromCursor(target_db.getWordsWithSequence(new ArrayList<HanziDB.StringElement>(Arrays.asList(bu_pinyin, hao_char)), true, false));
        difference = System.currentTimeMillis()-current_time;
        if (difference > max_time) max_time=difference;
        assertTrue(res.size()==1);
        assertTrue(res.get(0).chinese.equals("你不好"));
        current_time = System.currentTimeMillis();
        res = captureDataFromCursor(target_db.getWordsWithSequence(new ArrayList<HanziDB.StringElement>(Arrays.asList(hao_char, shi_no_tone_pinyin)), true, false));
        difference = System.currentTimeMillis()-current_time;
        if (difference > max_time) max_time=difference;
        assertTrue(res.size()==0);
        current_time = System.currentTimeMillis();
        res = captureDataFromCursor(target_db.getWordsWithSequence(new ArrayList<HanziDB.StringElement>(Arrays.asList(b_incomplete_pinyin, hao_char)), true, false));
        difference = System.currentTimeMillis()-current_time;
        if (difference > max_time) max_time=difference;
        assertTrue(res.size()==1);
        assertTrue(res.get(0).chinese.equals("你不好"));
        Log.d(TAG, "testGetFilteredSequence: Maximum time for a query is "+max_time);
    }

    public void testGetFilteredSequenceGrouped() {
        target_db.addVocabulary("you", "你", "ni3");
        target_db.addVocabulary("to be", "是", "shi4");
        target_db.addVocabulary("good", "好", "hao3");
        target_db.addVocabulary("well", "好", "hao3");
        target_db.addVocabulary("very good", "好好", "hao3hao3");
        target_db.addVocabulary("not", "不", "bu4");
        target_db.addVocabulary("hello", "你好", "ni3hao3");
        target_db.addVocabulary("how are you?", "你好吗", "ni3hao3ma");
        target_db.addVocabulary("no good", "不好", "bu4hao3");
        target_db.addVocabulary("you no good", "你不好", "ni3bu4hao3");
        target_db.addVocabulary("tasty", "好吃", "hao3chi1");
        target_db.addVocabulary("all right?", "好不好", "hao3bu4hao3");
        target_db.addVocabulary("no good", "不太好", "bu4tai4hao3");
        target_db.addVocabulary("unattractive", "不好看", "bu4hao3kan4");
        target_db.addVocabulary("hard to say", "不好说", "bu4hao3shuo1");
        target_db.addVocabulary("easy to use", "好使", "hao3shi3");
        target_db.addVocabulary("difficult to use", "不好使", "bu4hao3shi3");
        target_db.addVocabulary("yes or no", "是不是", "shi4bu4shi4");
        target_db.addVocabulary("hershey's", "好时", "hao3shi2");
        target_db.addVocabulary("eager to be first", "好强", "hao3qiang2");
        target_db.addVocabulary("inside", "里边", "li3bian5");
        target_db.addVocabulary("already", "便已", "bian4yi1");
        target_db.addLesson("lesson filter");
        target_db.assignWordLesson("lesson filter", "good", "好", "hao3");
        target_db.assignWordLesson("lesson filter", "you", "你", "ni3");
        target_db.assignWordLesson("lesson filter", "you no good", "你不好", "ni3bu4hao3");
        target_db.assignWordLesson("lesson filter", "yes or no", "是不是", "shi4bu4shi4");
        target_db.assignWordLesson("lesson filter",  "no good", "不太好", "bu4tai4hao3");
        target_db.assignWordLesson("lesson filter",  "inside", "里边", "li3bian5");
        target_db.setLessonsToFilter(new ArrayList<String>(Arrays.asList("lesson filter")));
        target_db.enableFilter(true);
        ArrayList<Vocabulary> res;
        long max_time=0;
        HanziDB.StringElement ni_pinyin = new HanziDB.StringElement(HanziDB.StringElement.type_t.PINYIN_WITH_TONE, "ni3");
        HanziDB.StringElement bu_pinyin = new HanziDB.StringElement(HanziDB.StringElement.type_t.PINYIN_WITH_TONE, "bu4");
        HanziDB.StringElement bu_char = new HanziDB.StringElement(HanziDB.StringElement.type_t.CHINESE, "不");
        HanziDB.StringElement hao_char = new HanziDB.StringElement(HanziDB.StringElement.type_t.CHINESE, "好");
        HanziDB.StringElement hao_pinyin = new HanziDB.StringElement(HanziDB.StringElement.type_t.PINYIN_WITH_TONE, "hao3");
        HanziDB.StringElement shi_no_tone_pinyin = new HanziDB.StringElement(HanziDB.StringElement.type_t.PINYIN_WITHOUT_TONE, "shi");
        HanziDB.StringElement chi_no_tone_pinyin = new HanziDB.StringElement(HanziDB.StringElement.type_t.INCOMPLETE_PINYIN, "chi");
        HanziDB.StringElement b_incomplete_pinyin = new HanziDB.StringElement(HanziDB.StringElement.type_t.INCOMPLETE_PINYIN, "b");

        // ** test only one element
        long current_time = System.currentTimeMillis();
        res = captureDataFromCursor(target_db.getWordsWithSequence(new ArrayList<HanziDB.StringElement>(Arrays.asList(hao_char)), true, true));
        long difference = System.currentTimeMillis()-current_time;
        if (difference > max_time) max_time=difference;
        assertTrue(res.size()==3);
        assertTrue(res.get(0).chinese.equals("好"));
        assertTrue(res.get(0).english.size()==1);
        assertTrue(res.get(2).chinese.equals("你不好"));
        current_time = System.currentTimeMillis();
        res = captureDataFromCursor(target_db.getWordsWithSequence(new ArrayList<HanziDB.StringElement>(Arrays.asList(bu_pinyin)), true, true));
        difference = System.currentTimeMillis()-current_time;
        if (difference > max_time) max_time=difference;
        assertTrue(res.size()==3);
        assertTrue(res.get(0).chinese.equals("不太好"));
        assertTrue(res.get(1).chinese.equals("你不好"));
        current_time = System.currentTimeMillis();
        res = captureDataFromCursor(target_db.getWordsWithSequence(new ArrayList<HanziDB.StringElement>(Arrays.asList(shi_no_tone_pinyin)), true, true));
        difference = System.currentTimeMillis()-current_time;
        if (difference > max_time) max_time=difference;
        assertTrue(res.size()==1);
        assertTrue(res.get(0).chinese.equals("是不是"));
        current_time = System.currentTimeMillis();
        res = captureDataFromCursor(target_db.getWordsWithSequence(new ArrayList<HanziDB.StringElement>(Arrays.asList(b_incomplete_pinyin)), true, true));
        difference = System.currentTimeMillis()-current_time;
        if (difference > max_time) max_time=difference;
        assertTrue(res.size()==4);
        assertTrue(res.get(0).chinese.equals("不太好"));
        assertTrue(res.get(2).chinese.equals("你不好"));

        // Only Chinese
        current_time = System.currentTimeMillis();
        res = captureDataFromCursor(target_db.getWordsWithSequence(new ArrayList<HanziDB.StringElement>(Arrays.asList(bu_char, hao_char)), true, true));
        difference = System.currentTimeMillis()-current_time;
        if (difference > max_time) max_time=difference;
        assertTrue(res.size()==1);
        assertTrue(res.get(0).chinese.equals("你不好"));

        // Only Pinyin
        current_time = System.currentTimeMillis();
        res = captureDataFromCursor(target_db.getWordsWithSequence(new ArrayList<HanziDB.StringElement>(Arrays.asList(bu_pinyin, hao_pinyin)), true, true));
        difference = System.currentTimeMillis()-current_time;
        if (difference > max_time) max_time=difference;
        assertTrue(res.size()==1);
        assertTrue(res.get(0).chinese.equals("你不好"));

        // Mixed
        current_time = System.currentTimeMillis();
        res = captureDataFromCursor(target_db.getWordsWithSequence(new ArrayList<HanziDB.StringElement>(Arrays.asList(bu_pinyin, hao_char)), true, true));
        difference = System.currentTimeMillis()-current_time;
        if (difference > max_time) max_time=difference;
        assertTrue(res.size()==1);
        assertTrue(res.get(0).chinese.equals("你不好"));
        current_time = System.currentTimeMillis();
        res = captureDataFromCursor(target_db.getWordsWithSequence(new ArrayList<HanziDB.StringElement>(Arrays.asList(hao_char, shi_no_tone_pinyin)), true, true));
        difference = System.currentTimeMillis()-current_time;
        if (difference > max_time) max_time=difference;
        assertTrue(res.size()==0);
        current_time = System.currentTimeMillis();
        res = captureDataFromCursor(target_db.getWordsWithSequence(new ArrayList<HanziDB.StringElement>(Arrays.asList(b_incomplete_pinyin, hao_char)), true, true));
        difference = System.currentTimeMillis()-current_time;
        if (difference > max_time) max_time=difference;
        assertTrue(res.size()==1);
        assertTrue(res.get(0).chinese.equals("你不好"));
        Log.d(TAG, "testGetFilteredSequence: Maximum time for a query is "+max_time);
    }

    public void testGetStartingSequence() {
        target_db.addVocabulary("you", "你", "ni3");
        target_db.addVocabulary("to be", "是", "shi4");
        target_db.addVocabulary("good", "好", "hao3");
        target_db.addVocabulary("well", "好", "hao3");
        target_db.addVocabulary("very good", "好好", "hao3hao3");
        target_db.addVocabulary("not", "不", "bu4");
        target_db.addVocabulary("hello", "你好", "ni3hao3");
        target_db.addVocabulary("how are you?", "你好吗", "ni3hao3ma");
        target_db.addVocabulary("no good", "不好", "bu4hao3");
        target_db.addVocabulary("you no good", "你不好", "ni3bu4hao3");
        target_db.addVocabulary("tasty", "好吃", "hao3chi1");
        target_db.addVocabulary("all right?", "好不好", "hao3bu4hao3");
        target_db.addVocabulary("no good", "不太好", "bu4tai4hao3");
        target_db.addVocabulary("unattractive", "不好看", "bu4hao3kan4");
        target_db.addVocabulary("hard to say", "不好说", "bu4hao3shuo1");
        target_db.addVocabulary("easy to use", "好使", "hao3shi3");
        target_db.addVocabulary("difficult to use", "不好使", "bu4hao3shi3");
        target_db.addVocabulary("yes or no", "是不是", "shi4bu4shi4");
        target_db.addVocabulary("hershey's", "好时", "hao3shi2");
        target_db.addVocabulary("eager to be first", "好强", "hao3qiang2");
        target_db.addVocabulary("inside", "里边", "li3bian5");
        target_db.addVocabulary("already", "便已", "bian4yi1");
        ArrayList<Vocabulary> res;
        long max_time=0;
        HanziDB.StringElement ni_pinyin = new HanziDB.StringElement(HanziDB.StringElement.type_t.PINYIN_WITH_TONE, "ni3");
        HanziDB.StringElement bu_pinyin = new HanziDB.StringElement(HanziDB.StringElement.type_t.PINYIN_WITH_TONE, "bu4");
        HanziDB.StringElement bu_char = new HanziDB.StringElement(HanziDB.StringElement.type_t.CHINESE, "不");
        HanziDB.StringElement hao_char = new HanziDB.StringElement(HanziDB.StringElement.type_t.CHINESE, "好");
        HanziDB.StringElement hao_pinyin = new HanziDB.StringElement(HanziDB.StringElement.type_t.PINYIN_WITH_TONE, "hao3");
        HanziDB.StringElement shi_no_tone_pinyin = new HanziDB.StringElement(HanziDB.StringElement.type_t.PINYIN_WITHOUT_TONE, "shi");
        HanziDB.StringElement chi_no_tone_pinyin = new HanziDB.StringElement(HanziDB.StringElement.type_t.INCOMPLETE_PINYIN, "chi");
        HanziDB.StringElement b_incomplete_pinyin = new HanziDB.StringElement(HanziDB.StringElement.type_t.INCOMPLETE_PINYIN, "b");

        // ** test only one element
        long current_time = System.currentTimeMillis();
        res = captureDataFromCursor(target_db.getWordsStartingWithSequence(new ArrayList<HanziDB.StringElement>(Arrays.asList(hao_char)), false, false));
        long difference = System.currentTimeMillis()-current_time;
        if (difference > max_time) max_time=difference;
        assertTrue(res.size()==7);
        assertTrue(res.get(0).chinese.equals("好"));
        assertTrue(res.get(0).english.size()==2);
        assertTrue(res.get(5).chinese.equals("好使"));
        current_time = System.currentTimeMillis();
        res = captureDataFromCursor(target_db.getWordsStartingWithSequence(new ArrayList<HanziDB.StringElement>(Arrays.asList(bu_pinyin)), false, false));
        difference = System.currentTimeMillis()-current_time;
        if (difference > max_time) max_time=difference;
        assertTrue(res.size()==6);
        assertTrue(res.get(0).chinese.equals("不"));
        assertTrue(res.get(5).chinese.equals("不太好"));
        current_time = System.currentTimeMillis();
        res = captureDataFromCursor(target_db.getWordsStartingWithSequence(new ArrayList<HanziDB.StringElement>(Arrays.asList(shi_no_tone_pinyin)), false, false));
        difference = System.currentTimeMillis()-current_time;
        if (difference > max_time) max_time=difference;
        assertTrue(res.size()==2);
        assertTrue(res.get(0).chinese.equals("是"));
        assertTrue(res.get(1).chinese.equals("是不是"));
        current_time = System.currentTimeMillis();
        res = captureDataFromCursor(target_db.getWordsStartingWithSequence(new ArrayList<HanziDB.StringElement>(Arrays.asList(b_incomplete_pinyin)), false, false));
        difference = System.currentTimeMillis()-current_time;
        if (difference > max_time) max_time=difference;
        assertTrue(res.size()==7);
        assertTrue(res.get(0).chinese.equals("不"));
        assertTrue(res.get(1).chinese.equals("便已"));
        assertTrue(res.get(6).chinese.equals("不太好"));

        // Only Chinese
        current_time = System.currentTimeMillis();
        res = captureDataFromCursor(target_db.getWordsStartingWithSequence(new ArrayList<HanziDB.StringElement>(Arrays.asList(bu_char, hao_char)), false, false));
        difference = System.currentTimeMillis()-current_time;
        if (difference > max_time) max_time=difference;
        assertTrue(res.size()==4);
        assertTrue(res.get(0).chinese.equals("不好"));
        assertTrue(res.get(3).chinese.equals("不好说"));

        // Only Pinyin
        current_time = System.currentTimeMillis();
        res = captureDataFromCursor(target_db.getWordsStartingWithSequence(new ArrayList<HanziDB.StringElement>(Arrays.asList(bu_pinyin, hao_pinyin)), false, false));
        difference = System.currentTimeMillis()-current_time;
        if (difference > max_time) max_time=difference;
        assertTrue(res.size()==4);
        assertTrue(res.get(0).chinese.equals("不好"));
        assertTrue(res.get(3).chinese.equals("不好说"));

        // Mixed
        current_time = System.currentTimeMillis();
        res = captureDataFromCursor(target_db.getWordsStartingWithSequence(new ArrayList<HanziDB.StringElement>(Arrays.asList(bu_pinyin, hao_char)), false, false));
        difference = System.currentTimeMillis()-current_time;
        if (difference > max_time) max_time=difference;
        assertTrue(res.size()==4);
        assertTrue(res.get(0).chinese.equals("不好"));
        assertTrue(res.get(3).chinese.equals("不好说"));
        current_time = System.currentTimeMillis();
        res = captureDataFromCursor(target_db.getWordsStartingWithSequence(new ArrayList<HanziDB.StringElement>(Arrays.asList(hao_char, shi_no_tone_pinyin)), false, false));
        difference = System.currentTimeMillis()-current_time;
        if (difference > max_time) max_time=difference;
        assertTrue(res.size()==2);
        assertTrue(res.get(0).chinese.equals("好时"));
        assertTrue(res.get(1).chinese.equals("好使"));
        current_time = System.currentTimeMillis();
        res = captureDataFromCursor(target_db.getWordsStartingWithSequence(new ArrayList<HanziDB.StringElement>(Arrays.asList(b_incomplete_pinyin, hao_char)), false, false));
        difference = System.currentTimeMillis()-current_time;
        if (difference > max_time) max_time=difference;
        assertTrue(res.size()==4);
        assertTrue(res.get(0).chinese.equals("不好"));
        assertTrue(res.get(3).chinese.equals("不好说"));
        Log.d(TAG, "testGetStartingSequence: Maximum time for a query is "+max_time);
    }


    public boolean checkDB(final HanziDB db, final boolean fixme) {
        boolean res = db.checkDatabase(fixme, new DBCheckProgressHandler() {
            @Override
            public void progress(int i) {
                Log.d(TAG, "Checking database, progress " + i);
            }

        });
        return res;
    }

    private static ArrayList<String> combineListOfTranslations(ArrayList<Vocabulary> vocs) {
        ArrayList<String> res=new ArrayList<String>();
        for (Vocabulary voc : vocs) {
            res.addAll(voc.english);
        }
        return res;
    }
    private static ArrayList<String> combineListOfTranslations(Vocabulary voc) {
        if (voc == null)
            return new ArrayList<String>();
        else
            return voc.english;
    }
    private static ArrayList<Vocabulary> captureDataFromCursor(Cursor cur) {
        ArrayList<Vocabulary> res=new ArrayList<Vocabulary>();
        if (!cur.moveToFirst())
            return res;

        int current_cword_id=-1;
        Vocabulary current_voc=null;
        do {
            int cword_id = cur.getInt(cur.getColumnIndex("_id"));
            if (cur.getColumnIndex("english") != -1) {
                String english=cur.getString(cur.getColumnIndex("english"));
                if (current_cword_id==cword_id) {
                    current_voc.english.add(english);
                }
                else {
                    String chinese=cur.getString(cur.getColumnIndex("chinese"));
                    String pinyin =cur.getString(cur.getColumnIndex("pinyin"));
                    if (current_voc!=null) {
                        res.add(current_voc);
                    }
                    current_voc = new Vocabulary(chinese, pinyin);
                    current_cword_id=cword_id;
                    current_voc.english.add(english);
                }
            }
            else {
                String chinese=cur.getString(cur.getColumnIndex("chinese"));
                String pinyin="";
                if (cur.getColumnIndex("pinyin") != -1)
                    pinyin =cur.getString(cur.getColumnIndex("pinyin"));
                res.add(new Vocabulary(chinese, pinyin));
            }
        } while (cur.moveToNext());
        if (current_voc!=null)
            res.add(current_voc);

        cur.close();
        return res;
    }
    private static ArrayList<String> voc_to_chinese(final ArrayList<Vocabulary> voc) {
        ArrayList<String> res = new ArrayList<String>();
        for (Vocabulary v : voc) {
            res.add(v.chinese);
        }
        return res;
    }
}
