package com.matthieu.robotium;

import android.support.v4.view.ViewPager;
import android.test.ActivityInstrumentationTestCase2;
import android.util.Log;
import android.view.View;
import android.widget.*;
import com.jayway.android.robotium.solo.Solo;
import com.matthieu.*;
import com.matthieu.widget.InsideScrollPager;
import com.matthieu.widget.LockableViewPager;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * This is a simple framework for a test of an Application.  See
 * {@link android.test.ApplicationTestCase ApplicationTestCase} for more information on
 * how to write and extend Application tests.
 * <p/>
 * To run this test, you can type:
 * adb shell am instrument -w \
 * -e class com.matthieu.MainMenuActivityTest \
 * com.matthieu.tests/android.test.InstrumentationTestRunner
 */
public class MainMenuActivityTest extends ActivityInstrumentationTestCase2<MainMenuActivity> {
    private Solo solo;

    private final static String TAG="Robotium";

    private final static int very_long_delay=10000;
    private final static int long_delay=5000;

    public MainMenuActivityTest() {
        super(com.matthieu.MainMenuActivity.class);
    }

    public void setUp() {
        solo = new Solo(getInstrumentation(), getActivity());
    }

    private void clearEverything() {
        solo.waitForActivity("MainMenuActivity", 2000); // it takes a while if we are installing Cedict
        solo.waitForText(getActivity().getResources().getString(R.string.main_menu_settings), 1, 60000);

        solo.clickOnButton(getActivity().getResources().getString(R.string.main_menu_settings));
        solo.waitForText(getActivity().getResources().getString(R.string.settings_db_clear), 1, very_long_delay);
        solo.clickOnText(getActivity().getResources().getString(R.string.settings_db_clear), 1, true);
        assertTrue(solo.waitForText(getActivity().getResources().getString(R.string.settings_confirm_clear_db)));
        solo.clickOnButton(getActivity().getResources().getString(R.string.yes));
        solo.waitForDialogToClose(long_delay);
        if (solo.waitForText(getActivity().getResources().getString(R.string.settings_db_clear_progress), 1, long_delay)) {
            solo.waitForDialogToClose(long_delay);
        }
        solo.waitForText(getActivity().getResources().getString(R.string.settings_db_reset_scores), 1, very_long_delay);
        solo.clickOnText(getActivity().getResources().getString(R.string.settings_db_reset_scores), 1, true);
        assertTrue(solo.waitForText(getActivity().getResources().getString(R.string.settings_confirm_reset_scores)));
        solo.clickOnButton(getActivity().getResources().getString(R.string.yes));
        solo.waitForDialogToClose(long_delay);
        solo.waitForText(getActivity().getResources().getString(R.string.settings_db_forget_progress), 1, very_long_delay);
        solo.clickOnText(getActivity().getResources().getString(R.string.settings_db_forget_progress), 1, true);
        assertTrue(solo.waitForText(getActivity().getResources().getString(R.string.settings_trim_progress)));
        ArrayList<ProgressBar> progress = solo.getCurrentViews(ProgressBar.class);
        assertTrue(progress.size()==1);
        solo.setProgressBar(progress.get(0), progress.get(0).getMax());
        assertTrue(solo.waitForText(getActivity().getResources().getString(R.string.settings_trim_all)));
        solo.clickOnButton(getActivity().getResources().getString(R.string.ok));
        solo.waitForDialogToClose(long_delay);
        solo.waitForText(getActivity().getResources().getString(R.string.settings_db_forget_progress), 1, very_long_delay);

        solo.goBack();
        solo.waitForActivity("MainMenuActivity");

        /*solo.getCurrentActivity().finish();

        // quit it
        solo.finishOpenedActivities();

        ((AndZiApplication)getActivity().getApplication()).showSplash(true);

        startIt();*/

        solo.setActivityOrientation(Solo.PORTRAIT);
    }


    private void startIt() {
        // restart it
        launchActivity("com.matthieu", MainMenuActivity.class, null);

        solo.waitForActivity("MainMenuActivity", 2000); // it takes a while if we are installing Cedict
        solo.waitForText(getActivity().getResources().getString(R.string.main_menu_settings), 1, 60000);
    }

    private void install_db(String path_name) {
        solo.clickOnButton(getActivity().getResources().getString(R.string.main_menu_settings));
        solo.waitForText(getActivity().getResources().getString(R.string.settings_db_import), 1, very_long_delay);
        solo.clickOnText(getActivity().getResources().getString(R.string.settings_db_import), 1, true);
        solo.waitForText(path_name, 1, very_long_delay);
        solo.clickOnText(path_name, 1, true);
        solo.clickOnButton("Select");

        solo.waitForText(getActivity().getResources().getString(R.string.db_importing),1,long_delay);
        solo.waitForDialogToClose(very_long_delay);
        solo.goBack();

        solo.waitForActivity("MainMenuActivity", 2000); // it takes a while if we are installing Cedict
        solo.waitForText(getActivity().getResources().getString(R.string.main_menu_settings), 1, 60000);
    }

    public void testFirstTimeAppOpening() {
        Log.d(TAG, "testFirstTimeAppOpening");

        clearEverything();

        solo.clickOnButton(getActivity().getResources().getString(R.string.main_menu_test));
        InsideScrollPager pager = (InsideScrollPager) solo.getView(R.id.test_pager);
        assertFalse(pager.isPagingEnabled());
    }

    public void testAddVocabularyEmptyApp() {
        // First wipe out everything...
        clearEverything();

        // Let's go add some vocabulary
        solo.clickOnButton(getActivity().getResources().getString(R.string.main_menu_add_voc));

        assertTrue(solo.waitForText(getActivity().getResources().getString(R.string.add_voc_enter_chinese)));
        EditText master_chinese = (EditText) solo.getView(R.id.add_vocabulary_chinese);
        assertTrue(master_chinese!=null);
        solo.enterText(master_chinese, "abc");
        assertTrue(solo.waitForText(getActivity().getResources().getString(R.string.add_voc_only_chinese)));

        solo.clearEditText(master_chinese);
        solo.enterText(master_chinese, "你");

        solo.setActivityOrientation(Solo.LANDSCAPE);

        master_chinese = (EditText) solo.getView(R.id.add_vocabulary_chinese);
        assertTrue(master_chinese!=null);
        assertTrue(master_chinese.getText().toString().equals("你"));

        solo.setActivityOrientation(Solo.PORTRAIT);

        master_chinese = (EditText) solo.getView(R.id.add_vocabulary_chinese);
        assertTrue(master_chinese!=null);

        assertTrue(solo.searchButton("nǐ"));
        solo.clickOnText("nǐ");
        assertTrue(solo.waitForText(getActivity().getResources().getString(R.string.add_voc_pick_pinyin) + "你"));
        EditText pinyin_entry = (EditText) solo.getView(R.id.edit_new_pinyin);
        assertTrue(pinyin_entry!=null);
        solo.enterText(pinyin_entry, "pyu3");
        solo.clickOnButton(getActivity().getResources().getString(R.string.ok));
        solo.waitForDialogToClose(long_delay);

        assertTrue(solo.searchButton("nǐ"));
        solo.clickOnText("nǐ");
        assertTrue(solo.waitForText(getActivity().getResources().getString(R.string.add_voc_pick_pinyin) + "你"));
        pinyin_entry = (EditText) solo.getView(R.id.edit_new_pinyin);
        assertTrue(pinyin_entry!=null);
        solo.enterText(pinyin_entry, "ni2");
        solo.clickOnButton(getActivity().getResources().getString(R.string.ok));
        solo.waitForDialogToClose(long_delay);
        assertFalse(solo.searchButton("nǐ"));
        assertTrue(solo.searchButton("ní"));

        assertFalse(solo.waitForText(getActivity().getResources().getString(R.string.add_voc_ref_db),1,long_delay));
        EditText new_translation = (EditText) solo.getView(R.id.add_new_translation);
        assertTrue(new_translation.getVisibility()==View.VISIBLE);
        ImageView add_new_translation_plus = (ImageView) solo.getView(R.id.add_new_translation_plus);
        assertTrue(add_new_translation_plus.getVisibility()==View.GONE);

        solo.clickOnText("ní");
        assertTrue(solo.waitForText(getActivity().getResources().getString(R.string.add_voc_pick_pinyin) + "你"));
        ListView pinyin_list = (ListView) solo.getView(R.id.pinyin_dialog_choices);
        assertTrue(pinyin_list!= null);
        pinyin_entry = (EditText) solo.getView(R.id.edit_new_pinyin);
        assertTrue(pinyin_entry!=null);
        solo.enterText(pinyin_entry, "ni3");
        solo.clickOnButton(getActivity().getResources().getString(R.string.ok));
        solo.waitForDialogToClose(long_delay);
        assertTrue(solo.searchButton("nǐ"));

        assertFalse(solo.waitForText(getActivity().getResources().getString(R.string.add_voc_local_db),1,long_delay));
        assertTrue(solo.waitForText(getActivity().getResources().getString(R.string.add_voc_ref_db),1,long_delay));

        View add_new_translation_button = solo.getView(R.id.add_new_translation_plus);
        solo.clickOnView(add_new_translation_button);
        
        assertTrue(solo.waitForText(getActivity().getResources().getString(R.string.add_voc_new_translation)));
        EditText new_translation_edit_text = (EditText) solo.getView(R.id.add_new_translation);
        assertTrue(new_translation_edit_text!=null);
        assertTrue(new_translation_edit_text.getVisibility()== View.VISIBLE);
        solo.enterText(new_translation_edit_text, "You");
        Button add_translation_ok = (Button) solo.getView(R.id.add_new_translation_ok);
        assertTrue(add_translation_ok != null);
        solo.clickOnView(add_translation_ok);
        assertTrue(solo.waitForText(getActivity().getResources().getString(R.string.add_voc_local_db), 1, long_delay));
        assertTrue(solo.waitForText("You"));

        solo.setActivityOrientation(Solo.LANDSCAPE);

        master_chinese = (EditText) solo.getView(R.id.add_vocabulary_chinese);
        assertTrue(master_chinese!=null);
        assertTrue(master_chinese.getText().toString().equals("你"));
        assertTrue(solo.waitForText(getActivity().getResources().getString(R.string.add_voc_local_db)));
        assertTrue(solo.waitForText("You"));

        solo.setActivityOrientation(Solo.PORTRAIT);
        master_chinese = (EditText) solo.getView(R.id.add_vocabulary_chinese);
        assertTrue(master_chinese!=null);
        assertTrue(solo.waitForText(getActivity().getResources().getString(R.string.add_voc_local_db)));
        assertTrue(solo.waitForText("You"));
        assertTrue(solo.searchButton("nǐ"));

        //getInstrumentation().invokeMenuActionSync(solo.getActivityMonitor().getLastActivity(), R.id.add_vocabulary_done, 0);
        View add_vocabulary_done_button = solo.getView(R.id.add_vocabulary_done);
        solo.clickOnView(add_vocabulary_done_button);

        master_chinese = (EditText) solo.getView(R.id.add_vocabulary_chinese);
        solo.clearEditText(master_chinese);
        solo.enterText(master_chinese, "你");
        assertTrue(solo.waitForText(getActivity().getResources().getString(R.string.add_voc_local_db), 1 , long_delay));
        assertTrue(solo.waitForText("You"));
        assertTrue(solo.searchButton("nǐ"));

        solo.clearEditText(master_chinese);
        solo.enterText(master_chinese, "你好");
        assertTrue(solo.searchButton("nǐ"));
        assertTrue(solo.searchButton("hǎo"));
        new_translation_edit_text = (EditText) solo.getView(R.id.add_new_translation);
        assertTrue(new_translation_edit_text!=null);
        solo.enterText(new_translation_edit_text, "Hello");
        add_translation_ok = (Button) solo.getView(R.id.add_new_translation_ok);
        solo.clickOnView(add_translation_ok);

        //getInstrumentation().invokeMenuActionSync(solo.getActivityMonitor().getLastActivity(), R.id.add_vocabulary_done, 0);
        add_vocabulary_done_button = solo.getView(R.id.add_vocabulary_done);
        solo.clickOnView(add_vocabulary_done_button);
    }

    public void testAddVocabularyWithDict() {
        // First wipe out everything...
        clearEverything();

        // Let's add some vocabulary
        solo.clickOnButton(getActivity().getResources().getString(R.string.main_menu_add_voc));

        assertTrue(solo.waitForText(getActivity().getResources().getString(R.string.add_voc_enter_chinese)));
        EditText master_chinese = (EditText) solo.getView(R.id.add_vocabulary_chinese);
        assertTrue(master_chinese!=null);
        solo.enterText(master_chinese, "你");

        assertTrue(solo.searchButton("nǐ"));

        assertFalse(solo.waitForText(getActivity().getResources().getString(R.string.add_voc_local_db),1,long_delay));
        assertTrue(solo.waitForText(getActivity().getResources().getString(R.string.add_voc_ref_db),1,long_delay));
    }

    /* test with small DB
    DB:
    你好 ni3hao3  Hello!, Hi!
    很好 hen3hao3 very good
    好 hao3 good
    我 wo3  I, me
    吗 ma5  question tag
    也 ye3  also
    他 ta1  he
    他们 ta1men5 they
    我们 wo3men5 we, us
    朋友 peng2you5 friend


    Possible questions
       tone pinyin english          containing
    你  3   ni3     X               你好
    好  3   hao3    good            你好，很好
    我  3   wo3     I, me           我们
    很  3   hen3    X               很好
    吗  X   ma      question tag   X
    他  1   ta1     he              他们
    们  X   men     X               他们，我们
    朋  2   peng2   X               朋友
    友  X   you     X               朋友

    Hello!, Hi!
    very good
    good
    I, me
    question tag
    also
    he
    they
    we, us
    friend

    你好 Hello!, Hi!
    很好 very good
    他们 they
    我们 we, us
    朋友 friend

    你？ / 很？
    ？好
    ？好
    他？
    我？
    ？们
    ？们
    朋？
    ？友
     */

    public void testKeepGettingItWrong() {
        final int num_tests = 30;

        Log.d(TAG, "testKeepGettingItWrong");

        // First wipe out everything... and install cedict
        clearEverything();

        install_db("my-test-db.azdb");

        solo.clickOnButton(getActivity().getResources().getString(R.string.main_menu_test));

        test_chinese_orientation = true;
        test_containing_orientation = true;
        test_english_orientation = true;
        test_missing_orientation = true;
        test_pinyin_orientation = true;
        test_tone_orientation = true;

        ViewPager pager = (ViewPager) solo.getView(R.id.test_pager);
        if (pager != null)
            solo.scrollToSide(Solo.RIGHT);
        TextView to_study = (TextView) solo.getView(R.id.stats_to_study);
        int current_to_study = Integer.valueOf(to_study.getText().toString());


        for (int i=0; i<num_tests; i++) {
            if (pager != null)
                solo.scrollToSide(Solo.LEFT);

            // check what test we're in
            if (solo.getView(R.id.chinese_word_test_guess) != null) {
                handle_chinese_test(false);
            } else if (solo.getView(R.id.containing_test_guess) != null) {
                handle_containing_test(false);
            } else if (solo.getView(R.id.english_test_guess) != null) {
                handle_english_test(false);
            } else if (solo.getView(R.id.missing_test_guess) != null) {
                handle_missing_test(false);
            } else if (solo.getView(R.id.pinyin_test_guess) != null) {
                handle_pinyin_test(false);
            } else if (solo.getView(R.id.guess_tone_ok) != null) {
                handle_tone_test(false);
            }

            if (pager != null)
                solo.scrollToSide(Solo.RIGHT);
            int new_to_study = Integer.valueOf(to_study.getText().toString());
            assertTrue(new_to_study >= current_to_study);
            current_to_study = new_to_study;
        }
    }

    public void testKeepGettingItRight() {
        Log.d(TAG, "testKeepGettingItRight");

        install_db("my-test-db.azdb");

        solo.clickOnButton(getActivity().getResources().getString(R.string.main_menu_test));

        test_chinese_orientation = true;
        test_containing_orientation = true;
        test_english_orientation = true;
        test_missing_orientation = true;
        test_pinyin_orientation = true;
        test_tone_orientation = true;

        InsideScrollPager pager = (InsideScrollPager) solo.getView(R.id.test_pager);
        if (pager != null)
            solo.scrollToSide(Solo.RIGHT);
        solo.waitForText(getActivity().getResources().getString(R.string.num_to_study), 1, 500);
        TextView to_study = (TextView) solo.getView(R.id.stats_to_study);
        int current_to_study = Integer.valueOf(to_study.getText().toString());
        int total_tests = current_to_study;

        while(true) {
            total_tests--;
            assertFalse("Did more tests than we should have", total_tests < 0);

            if (pager != null)
                solo.scrollToSide(Solo.LEFT);

            if (solo.searchText(getActivity().getResources().getString(R.string.test_mastering)))
                break;

            // check what test we're in
            if (solo.getView(R.id.chinese_word_test_guess) != null) {
                handle_chinese_test(true);
            } else if (solo.getView(R.id.containing_test_guess) != null) {
                handle_containing_test(true);
            } else if (solo.getView(R.id.english_test_guess) != null) {
                handle_english_test(true);
            } else if (solo.getView(R.id.missing_test_guess) != null) {
                handle_missing_test(true);
            } else if (solo.getView(R.id.pinyin_test_guess) != null) {
                handle_pinyin_test(true);
            } else if (solo.getView(R.id.guess_tone_ok) != null) {
                handle_tone_test(true);
            }

            if (pager != null)
                solo.scrollToSide(Solo.RIGHT);
            solo.sleep(2000);

            int new_to_study = Integer.valueOf(to_study.getText().toString());
            assertTrue("Score is not getting better("+new_to_study+" / "+current_to_study+")", new_to_study <= current_to_study);
            current_to_study = new_to_study;
        }
        if (pager != null)
            assertFalse(pager.isPagingEnabled());
    }

    boolean test_chinese_orientation = true;
    boolean test_containing_orientation = true;
    boolean test_english_orientation = true;
    boolean test_missing_orientation = true;
    boolean test_pinyin_orientation = true;
    boolean test_tone_orientation = true;

    HashMap<String, String> chinese_solutions = new HashMap<String, String>() {{
        put("Hello!", "你好");
        put("Hi!", "你好");
        put("very", "很好");
        put("good", "好");
        put("I", "我");
        put("me", "我");
        put("question", "吗");
        put("also", "也");
        put("he", "他");
        put("they", "他们");
        put("we", "我们");
        put("us", "我们");
        put("friend", "朋友");
    }};
    private void handle_chinese_test(boolean good) {
        TextView question = (TextView) solo.getView(R.id.guess_chinese_word_voc);
        assertTrue(question!=null);
        String hint = question.getText().toString().split("[ ,\n]")[0];
        assertTrue("Cannot find solution for ["+hint+"]", chinese_solutions.containsKey(hint));

        EditText entry = (EditText) solo.getView(R.id.chinese_word_test_guess);
        assertTrue(entry.getText().toString().equals(""));
        if (good) {
            solo.enterText(entry, chinese_solutions.get(hint));
            solo.sleep(100);
        }

        // when testing orientation, check for deployment for extra vocabulary if it is there

        Button ok_button = (Button) solo.getView(R.id.guess_chinese_word_ok);
        solo.clickOnView(ok_button);

        while (solo.getView(R.id.test_result_next) == null)
            solo.sleep(500);

        solo.clickOnView(solo.getView(R.id.test_result_next));

        solo.sleep(500);
    }

    HashMap<String, String> containing_solutions = new HashMap<String, String>() {{
        put("你","你好");
        put("好","你好");
        put("我","我们");
        put("很","很好");
        put("他","他们");
        put("们","他们");
        put("朋","朋友");
        put("友","朋友");
    }};
    private void handle_containing_test(boolean good) {
        TextView question = (TextView) solo.getView(R.id.guess_containing_chinese);
        assertTrue(question!=null);
        String hint = question.getText().toString();
        assertTrue("Cannot find solution for "+hint, containing_solutions.containsKey(hint));

        EditText entry = (EditText) solo.getView(R.id.containing_test_guess);
        assertTrue(entry.getText().toString().equals(""));
        if (good) {
            solo.enterText(entry, containing_solutions.get(hint));
            solo.sleep(500);
        }

        Button ok_button = (Button) solo.getView(R.id.guess_containing_ok);
        solo.clickOnView(ok_button);

        while (solo.getView(R.id.test_result_next) == null)
            solo.sleep(500);

        solo.clickOnView(solo.getView(R.id.test_result_next));

        solo.sleep(500);
    }

    HashMap<String, String> english_solutions = new HashMap<String, String>() {{
        put("你好", "Hello!");
        put("很好", "very good");
        put("好", "good");
        put("我", "I");
        put("吗", "question");
        put("也", "also");
        put("他", "he");
        put("他们", "they");
        put("我们", "we,us");
        put("朋友", "friend");
    }};
    private void handle_english_test(boolean good) {
        TextView question = (TextView) solo.getView(R.id.guess_english_word_voc);
        assertTrue(question!=null);
        String hint = question.getText().toString();
        assertTrue("Cannot find solution for "+hint, english_solutions.containsKey(hint));

        EditText entry = (EditText) solo.getView(R.id.english_test_guess);
        assertTrue(entry.getText().toString().equals(""));
        if (good) {
            solo.enterText(entry, english_solutions.get(hint));
            solo.sleep(500);
        }

        Button ok_button = (Button) solo.getView(R.id.guess_english_ok);
        solo.clickOnView(ok_button);

        while (solo.getView(R.id.test_result_next) == null)
            solo.sleep(500);

        solo.clickOnView(solo.getView(R.id.test_result_next));

        solo.sleep(500);
    }

    HashMap<String[], String> missing_solutions = new HashMap<String[], String>() {{
        put(new String[]{"?好", "Hello!"}, "你");
        put(new String[]{"?好", "Hi!"}, "你");
        put(new String[]{"很?", "very"}, "好");
        put(new String[]{"?好", "very"}, "很");
        put(new String[]{"他?", "they"}, "们");
        put(new String[]{"?们", "they"}, "他");
        put(new String[]{"?们", "we"}, "我");
        put(new String[]{"?们", "us"}, "我");
        put(new String[]{"朋?", "friend"}, "友");
        put(new String[]{"?友", "friend"}, "朋");
    }};
    private void handle_missing_test(boolean good) {
        TableLayout question = (TableLayout) solo.getView(R.id.char_finder_word_grid);
        assertTrue(question!=null);
        String solution="";
        boolean found_solution=false;

        for (int i=0; i<question.getChildCount(); i++) {
            TableRow tr = (TableRow) question.getChildAt(i);

            String chinese_part = ((TextView)tr.getChildAt(0)).getText().toString();
            String english_part = ((TextView)tr.getChildAt(1)).getText().toString().split("[, ]")[0];
            Log.d(TAG, "Looking for "+chinese_part+" / "+english_part);
        }
        Log.d(TAG, "That's all");

        for (int i=0; i<question.getChildCount(); i++) {
            TableRow tr = (TableRow) question.getChildAt(i);
            for (String[] k : missing_solutions.keySet()) {
                String chinese_part = ((TextView)tr.getChildAt(0)).getText().toString();
                if (!k[0].equals(chinese_part))
                    continue;
                String english_part = ((TextView)tr.getChildAt(1)).getText().toString().split("[, ]")[0];
                if (!k[1].equals(english_part))
                    continue;
                found_solution = true;
                solution = missing_solutions.get(k);
                break;
            }
            if (found_solution)
                break;
        }

        assertTrue("Cannot find solution for missing char test", found_solution);
        EditText entry = (EditText) solo.getView(R.id.missing_test_guess);
        assertTrue(entry.getText().toString().equals(""));
        if (good) {
            solo.enterText(entry, solution);
            solo.sleep(500);
        }

        Button ok_button = (Button) solo.getView(R.id.guess_missing_char_ok);
        solo.clickOnView(ok_button);

        while (solo.getView(R.id.test_result_next) == null)
            solo.sleep(500);

        solo.clickOnView(solo.getView(R.id.test_result_next));

        solo.sleep(500);
    }

    HashMap<String, String> pinyin_solutions = new HashMap<String, String>() {{
        put("你", "ni3");
        put("好", "hao3");
        put("很", "hen3");
        put("我", "wo3");
        put("吗", "ma");
        put("也", "ye3");
        put("他", "ta1");
        put("们", "men");
        put("朋", "peng2");
        put("友", "you");
    }};
    private void handle_pinyin_test(boolean good) {
        TextView question = (TextView) solo.getView(R.id.guess_pinyin_chinese);
        assertTrue(question!=null);
        String hint = question.getText().toString();
        assertTrue("Cannot find solution for "+hint, pinyin_solutions.containsKey(hint));

        EditText entry = (EditText) solo.getView(R.id.pinyin_test_guess);
        assertTrue(entry.getText().toString().equals(""));
        if (good)                                     {
            solo.enterText(entry, pinyin_solutions.get(hint));
            solo.sleep(500);
        }

        // when testing orientation, check for "as in..."

        Button ok_button = (Button) solo.getView(R.id.guess_pinyin_ok);
        solo.clickOnView(ok_button);

        while (solo.getView(R.id.test_result_next) == null)
            solo.sleep(500);

        solo.clickOnView(solo.getView(R.id.test_result_next));

        solo.sleep(500);
    }

    HashMap<String, Integer> tone_solutions = new HashMap<String, Integer>() {{
        put("你", 3);
        put("好", 3);
        put("很", 3);
        put("我", 3);
        put("也", 3);
        put("他", 1);
        put("朋", 2);
    }};
    private void handle_tone_test(boolean good) {
        TextView question = (TextView) solo.getView(R.id.guess_tone_chinese);
        assertTrue(question!=null);
        String hint = question.getText().toString();
        assertTrue("Cannot find solution for "+hint, tone_solutions.containsKey(hint));

        if (good) {
            CheckBox cb=null;
            switch(tone_solutions.get(hint)) {
                case 1:
                    cb = (CheckBox) solo.getView(R.id.guess_tone_first_tone);
                    break;
                case 2:
                    cb = (CheckBox) solo.getView(R.id.guess_tone_second_tone);
                    break;
                case 3:
                    cb = (CheckBox) solo.getView(R.id.guess_tone_third_tone);
                    break;
                case 4:
                    cb = (CheckBox) solo.getView(R.id.guess_tone_fourth_tone);
                    break;
                default:
                    assertTrue(false);
            }
            solo.clickOnView(cb);
            solo.sleep(500);
            assertTrue(cb.isChecked());
        }

        // when testing orientation, check for "box is still checked"

        Button ok_button = (Button) solo.getView(R.id.guess_tone_ok);
        solo.clickOnView(ok_button);

        while (solo.getView(R.id.test_result_next) == null)
            solo.sleep(500);

        solo.clickOnView(solo.getView(R.id.test_result_next));

        solo.sleep(500);
    }


    /* manipulating lessons with small DB
        DB:
        你好 ni3hao3  Hello!, Hi!  -> Lesson 1
        很好 hen3hao3 very good    -> Lesson 1
        好 hao3 good               -> Lesson 1
        我 wo3  I, me              -> Lesson 2
        吗 ma5  question tag       -> Lesson 2
        也 ye3  also               -> Lesson 2
        他 ta1  he                 -> Lesson 3
        他们 ta1men5 they          -> Lesson 3
        我们 wo3men5 we, us        -> Lesson 3
        朋友 peng2you5 friend      -> Lesson 3
        */
    public void testLessonShuffle() {
        Log.d(TAG, "testLessonShuffle");

        // First wipe out everything... and install cedict
        clearEverything();

        install_db("my-test-db.azdb");

        solo.clickOnButton(getActivity().getResources().getString(R.string.main_menu_add_voc));
        LockableViewPager pager = (LockableViewPager) solo.getView(R.id.voc_editor_pager);
        if (pager != null)
            solo.scrollToSide(Solo.RIGHT);
        assertTrue(solo.waitForText("Lesson 1", 1, long_delay));

        // check the content of one list
        if (pager != null)
            assertTrue(pager.getAdapter().getCount()==2);
        solo.clickOnText("Lesson 1");
        if (pager != null) {
            assertTrue(pager.getAdapter().getCount()==3);
            solo.scrollToSide(Solo.RIGHT);
        }
        assertTrue(solo.waitForText("Hello!, Hi!", 1, long_delay));
        assertTrue(solo.waitForText("好"));
        assertFalse(solo.waitForText("they"));
        if (pager != null) {
            solo.scrollToSide(Solo.LEFT);
        }
        solo.clickOnText("Lesson 3");
        if (pager != null) {
            solo.scrollToSide(Solo.RIGHT);
        }
        assertTrue(solo.waitForText("Hello!, Hi!", 1, long_delay));
        assertTrue(solo.waitForText("好"));
        assertTrue(solo.waitForText("they"));
        assertFalse(solo.waitForText("also"));
        if (pager != null) {
            solo.scrollToSide(Solo.LEFT);
        }
        solo.clickOnText("Lesson 1");
        solo.clickOnText("Lesson 3");
        if (pager != null)
            assertTrue(pager.getAdapter().getCount()==2);

        solo.clickOnText("Lesson 1");
        solo.clickOnText("Lesson 3");

        View merge_lesson_button = solo.getView(R.id.add_vocabulary_lesson_merge);
        if (merge_lesson_button == null)
            solo.clickOnMenuItem(getActivity().getResources().getString(R.string.add_voc_menu_merge_lesson));
        else
            solo.clickOnView(merge_lesson_button);
        assertTrue(solo.waitForText(getActivity().getResources().getString(R.string.add_voc_combined_lesson)));

        solo.clickOnText(getActivity().getResources().getString(R.string.add_voc_combined_lesson));
        if (pager != null) {
            solo.scrollToSide(Solo.RIGHT);
        }
        assertTrue(solo.waitForText("Hello!, Hi!", 1, long_delay));
        assertTrue(solo.waitForText("好"));
        assertTrue(solo.waitForText("they"));
        assertFalse(solo.waitForText("also"));
        if (pager != null) {
            solo.scrollToSide(Solo.LEFT);
        }

        View delete_lesson_button = solo.getView(R.id.add_vocabulary_lesson_delete);
        if (delete_lesson_button == null)
            solo.clickOnMenuItem(getActivity().getResources().getString(R.string.add_voc_menu_delete_lesson));
        else
            solo.clickOnView(delete_lesson_button);
        assertFalse(solo.waitForText(getActivity().getResources().getString(R.string.add_voc_combined_lesson)));
        assertTrue(solo.waitForText("Lesson 1", 1, long_delay));

        solo.clickOnText("Lesson 1");
        View rename_lesson_button = solo.getView(R.id.add_vocabulary_lesson_rename);
        if (rename_lesson_button == null)
            solo.clickOnMenuItem(getActivity().getResources().getString(R.string.add_voc_menu_rename_lesson));
        else
            solo.clickOnView(rename_lesson_button);
        solo.waitForText(getActivity().getResources().getString(R.string.add_voc_rename_lesson_dialog_query));
        EditText new_name = (EditText) solo.getView(R.id.lesson_rename_edit_box);
        solo.clearEditText(new_name);
        solo.enterText(new_name,"New Lesson");
        solo.clickOnButton(getActivity().getResources().getString(R.string.ok));
        assertTrue(solo.waitForText("New Lesson", 1, long_delay));
    }
}
