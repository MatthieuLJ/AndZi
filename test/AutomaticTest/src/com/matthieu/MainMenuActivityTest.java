package com.matthieu;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.test.ActivityInstrumentationTestCase2;
import android.widget.*;
import com.jayway.android.robotium.solo.Solo;

import java.util.ArrayList;

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

    public MainMenuActivityTest() {
        super("com.matthieu", MainMenuActivity.class);
    }

    public void setUp() {
        solo = new Solo(getInstrumentation(), getActivity());
    }

    private void clearEverything(boolean installCedict) {
        if (solo.waitForText(getActivity().getResources().getString(R.string.download_dictionary), 1, 10000)) {
            solo.clickOnButton(getActivity().getResources().getString(R.string.later));
            solo.waitForDialogToClose(5);
        }
        solo.clickOnButton("Settings");
        solo.clickOnText(getActivity().getResources().getString(R.string.settings_db_clear), 0, true);
        assertTrue(solo.waitForText(getActivity().getResources().getString(R.string.settings_confirm_clear_db)));
        solo.clickOnButton(getActivity().getResources().getString(R.string.yes));
        solo.waitForDialogToClose(2000);
        solo.clickOnText(getActivity().getResources().getString(R.string.settings_db_reset_scores), 0, true);
        assertTrue(solo.waitForText(getActivity().getResources().getString(R.string.settings_confirm_reset_scores)));
        solo.clickOnButton(getActivity().getResources().getString(R.string.yes));
        solo.waitForDialogToClose(2000);
        solo.clickOnText(getActivity().getResources().getString(R.string.settings_db_forget_progress), 0, true);
        assertTrue(solo.waitForText(getActivity().getResources().getString(R.string.settings_trim_progress)));
        ArrayList<ProgressBar> progress = solo.getCurrentProgressBars();
        assertTrue(progress.size()==1);
        solo.setProgressBar(progress.get(0), progress.get(0).getMax());
        assertTrue(solo.waitForText(getActivity().getResources().getString(R.string.settings_trim_all)));
        solo.clickOnButton(getActivity().getResources().getString(R.string.ok));
        solo.waitForDialogToClose(2000);
        if (!installCedict) {
            solo.clickOnText(getActivity().getResources().getString(R.string.settings_ref_db_erase), 0, true);
            if (solo.waitForText(getActivity().getResources().getString(R.string.settings_confirm_ref_db_erase))) {
                // TODO
            }
        }
        else
            setupCedictDict();

        solo.goBack();
        solo.waitForActivity("MainMenuActivity");
        solo.getCurrentActivity().finish();

        // quit it
        solo.finishOpenedActivities();

        startIt();
    }

    public void setupCedictDict() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplication());
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("ref_enabled", true);
        if (((AndZiApplication)getActivity().getApplication()).getRefDB()==null)
            ((AndZiApplication)getActivity().getApplication()).installRefDB("/mnt/sdcard/cedict_db", false);
    }

    public void startIt() {
        // restart it
        this.launchActivity("com.matthieu", MainMenuActivity.class, null);
    }

    public void testAddVocabularyEmptyApp() {
        /*
        // First wipe out everything...
        clearEverything(false);

        // Wait until the splash screen is gone
        if(solo.waitForText(getActivity().getResources().getString(R.string.download_dictionary), 1, 10000)) {
            solo.clickOnButton(getActivity().getResources().getString(R.string.later));
            solo.waitForDialogToClose(2000);
        }

        // there should not be anything to test on
        solo.clickOnButton("Test me!");
        assertTrue(solo.waitForText(getActivity().getResources().getString(R.string.review_list_nothing_to_study)));
        solo.clickOnButton(getActivity().getResources().getString(R.string.ok));
        solo.waitForActivity("MainMenuActivity");
        // Neither anything to review
        solo.clickOnButton("Review list");
        assertTrue(solo.waitForText(getActivity().getResources().getString(R.string.review_list_nothing_to_study)));
        solo.clickOnButton(getActivity().getResources().getString(R.string.ok));
        solo.waitForActivity("MainMenuActivity");

        // Let's add some vocabulary
        solo.clickOnButton("Add Vocabulary");
        assertTrue(solo.waitForText(getActivity().getResources().getString(R.string.add_voc_enter_chinese)));
        ArrayList<EditText> entries = solo.getCurrentEditTexts();
        assertTrue(entries.size()==1);
        solo.enterText(entries.get(0), "abc");
        assertTrue(solo.waitForText(getActivity().getResources().getString(R.string.add_voc_only_chinese)));

        solo.clearEditText(entries.get(0));
        solo.enterText(entries.get(0), "你");
        assertTrue(solo.searchButton("-?-"));
        solo.clickOnText("-?-");
        assertTrue(solo.waitForText(getActivity().getResources().getString(R.string.add_voc_pick_pinyin) + "你"));
        ArrayList<EditText> pinyin_entries = solo.getCurrentEditTexts();
        assertTrue(pinyin_entries.size()==1);
        solo.enterText(pinyin_entries.get(0), "pyu3");
        solo.clickOnButton(getActivity().getResources().getString(R.string.ok));
        solo.waitForDialogToClose(2000);

        assertTrue(solo.searchButton("-?-"));
        solo.clickOnText("-?-");
        assertTrue(solo.waitForText(getActivity().getResources().getString(R.string.add_voc_pick_pinyin) + "你"));
        pinyin_entries = solo.getCurrentEditTexts();
        assertTrue(pinyin_entries.size()==1);
        solo.enterText(pinyin_entries.get(0), "ni2");
        solo.clickOnButton(getActivity().getResources().getString(R.string.ok));
        solo.waitForDialogToClose(2000);
        assertFalse(solo.searchButton("-?-"));
        assertTrue(solo.searchButton("ní"));

        solo.clickOnText("ní");
        ArrayList<ListView> pinyin_lists = solo.getCurrentListViews();
        assertTrue(pinyin_lists.size()==1);
        assertTrue(solo.waitForText(getActivity().getResources().getString(R.string.add_voc_pick_pinyin) + "你"));
        pinyin_entries = solo.getCurrentEditTexts();
        assertTrue(pinyin_entries.size()==1);
        solo.enterText(pinyin_entries.get(0), "ni3");
        solo.clickOnButton(getActivity().getResources().getString(R.string.ok));
        solo.waitForDialogToClose(2000);
        assertTrue(solo.searchButton("nǐ"));

        assertFalse(solo.waitForText(getActivity().getResources().getString(R.string.add_voc_local_db),1,2000));
        assertFalse(solo.waitForText(getActivity().getResources().getString(R.string.add_voc_ref_db),1,2000));

        ArrayList<EditText> all_entries = solo.getCurrentEditTexts();
        assertTrue(all_entries.size()==2);
        solo.enterText(all_entries.get(1), "You");
        Button add_translation_ok = (Button) solo.getView(R.id.add_new_translation_ok);
        solo.clickOnView(add_translation_ok);
        ArrayList<ListView> translation_lists = solo.getCurrentListViews();
        assertTrue(translation_lists.size()==1);
        assertTrue(solo.waitForText(getActivity().getResources().getString(R.string.add_voc_local_db)));
        assertTrue(solo.waitForText("You"));

        Button add_ok = (Button) solo.getView(R.id.add_vocabulary_submit);
        solo.clickOnView(add_ok);

        solo.enterText(entries.get(0), "你");
        assertTrue(solo.waitForText(getActivity().getResources().getString(R.string.add_voc_local_db)));
        assertFalse(solo.waitForText(getActivity().getResources().getString(R.string.add_voc_ref_db), 1, 2000));
        assertTrue(solo.waitForText("You"));
        assertTrue(solo.searchButton("nǐ"));

        solo.clearEditText(entries.get(0));
        solo.enterText(entries.get(0), "你好");
        assertTrue(solo.searchButton("nǐ"));
        assertTrue(solo.searchButton("-?-"));
        solo.clickOnText("-?-");
        assertTrue(solo.waitForText(getActivity().getResources().getString(R.string.add_voc_pick_pinyin) + "好"));
        pinyin_entries = solo.getCurrentEditTexts();
        assertTrue(pinyin_entries.size()==1);
        solo.enterText(pinyin_entries.get(0), "hao3");
        solo.clickOnButton(getActivity().getResources().getString(R.string.ok));
        solo.waitForDialogToClose(2000);
        assertTrue(solo.searchButton("hǎo"));
        all_entries = solo.getCurrentEditTexts();
        assertTrue(all_entries.size()==2);
        solo.enterText(all_entries.get(1), "Hello");
        add_translation_ok = (Button) solo.getView(R.id.add_new_translation_ok);
        solo.clickOnView(add_translation_ok);
        add_ok = (Button) solo.getView(R.id.add_vocabulary_submit);
        solo.clickOnView(add_ok);
        */
    }

    public void testAddVocabularyWithDict() {
        /*
        // First wipe out everything...
        //clearEverything(true);

        assertFalse(solo.waitForText(getActivity().getResources().getString(R.string.download_dictionary), 1, 2000));
        // Let's add some vocabulary
        solo.clickOnButton("Add Vocabulary");
        assertTrue(solo.waitForText(getActivity().getResources().getString(R.string.add_voc_enter_chinese)));
        ArrayList<EditText> entries = solo.getCurrentEditTexts();
        assertTrue(entries.size()==1);

        // Add custom "you" translation for 你
        solo.enterText(entries.get(0), "你");
        assertTrue(solo.searchButton("nǐ"));
        assertTrue(solo.waitForText(getActivity().getResources().getString(R.string.add_voc_ref_db),1,2000));
        assertTrue(solo.waitForText("you", 1, 2000, true));
        ArrayList<EditText> all_entries = solo.getCurrentEditTexts();
        assertTrue(all_entries.size()==2);
        solo.enterText(all_entries.get(1), "you");
        Button add_translation_ok = (Button) solo.getView(R.id.add_new_translation_ok);
        solo.clickOnView(add_translation_ok);
        assertFalse(solo.waitForText(getActivity().getResources().getString(R.string.add_voc_ref_db),1,2000));
        Button add_ok = (Button) solo.getView(R.id.add_vocabulary_submit);
        solo.clickOnView(add_ok);

        // add dictionary translations for 你好 (Hello! and Hi!)
        assertTrue(solo.waitForText(getActivity().getResources().getString(R.string.add_voc_enter_chinese)));
        entries = solo.getCurrentEditTexts();
        solo.enterText(entries.get(0), "你");
        solo.enterText(entries.get(0), "好");
        assertTrue(solo.waitForText("Hello!", 1, 5000, true));
        assertTrue(solo.waitForText("Hi!", 1, 1000, true));
        ArrayList<android.widget.ListView> lists = solo.getCurrentListViews();
        assertTrue(lists.size()==1);
        ArrayList<View> views_in_list = solo.getViews(lists.get(0));
        View line = ViewFinder.find_view(views_in_list, R.id.add_line_item, R.id.add_word, "Hello!");
        CheckBox cb = (CheckBox) line.findViewById(R.id.add_check);
        solo.clickOnView(cb);
        assertTrue(solo.waitForText("Hello!", 1, 5000, true));
        views_in_list = solo.getViews(lists.get(0));
        line = ViewFinder.find_view(views_in_list, R.id.add_line_item, R.id.add_word, "Hi!");
        cb = (CheckBox) line.findViewById(R.id.add_check);
        solo.clickOnView(cb);
        add_ok = (Button) solo.getView(R.id.add_vocabulary_submit);
        solo.clickOnView(add_ok);

        assertTrue(solo.waitForText(getActivity().getResources().getString(R.string.add_voc_enter_chinese)));
        HanziDB myDB = ((AndZiApplication)getActivity().getApplication()).getMyDB();
        assertTrue(myDB.getNumWords()==2);
        assertTrue(myDB.getNumChars()==2);

        // remove translations for 你好
        solo.enterText(entries.get(0), "你");
        solo.enterText(entries.get(0), "好");
        assertTrue(solo.waitForText("Hello!", 1, 5000, true));
        assertTrue(solo.waitForText("Hi!", 1, 1000, true));
        lists = solo.getCurrentListViews();
        assertTrue(lists.size()==1);
        assertTrue(solo.waitForText("Hello!", 1, 5000, true));
        views_in_list = solo.getViews(lists.get(0));
        line = ViewFinder.find_view(views_in_list, R.id.add_line_item, R.id.add_word, "Hello!");
        cb = (CheckBox) line.findViewById(R.id.add_check);
        solo.clickOnView(cb);
        assertTrue(solo.waitForText("Hello!", 1, 5000, true));
        views_in_list = solo.getViews(lists.get(0));
        line = ViewFinder.find_view(views_in_list, R.id.add_line_item, R.id.add_word, "Hi!");
        cb = (CheckBox) line.findViewById(R.id.add_check);
        solo.clickOnView(cb);
        add_ok = (Button) solo.getView(R.id.add_vocabulary_submit);
        solo.clickOnView(add_ok);
        assertTrue(solo.waitForText(getActivity().getResources().getString(R.string.add_voc_enter_chinese)));
        assertTrue(myDB.getNumWords()==1);
        assertTrue(myDB.getNumChars()==1);
        */
    }
}
