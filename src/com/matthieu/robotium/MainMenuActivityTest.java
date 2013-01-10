package com.matthieu.robotium;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v4.view.ViewPager;
import android.test.ActivityInstrumentationTestCase2;
import android.view.View;
import android.widget.*;
import com.jayway.android.robotium.solo.Solo;
import com.matthieu.*;

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
        super(com.matthieu.MainMenuActivity.class);
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
            if (solo.waitForText(getActivity().getResources().getString(R.string.settings_confirm_ref_db_erase),1, 1000)) {
                // TODO
                assertTrue(false);
            }
        }
        else
            setupCedictDict();

        solo.goBack();
        solo.waitForActivity("MainMenuActivity");
        solo.getCurrentActivity().finish();

        // quit it
        solo.finishOpenedActivities();

        ((AndZiApplication)getActivity().getApplication()).showSplash(true);

        startIt();
        solo.setActivityOrientation(Solo.PORTRAIT);
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
        launchActivity("com.matthieu", MainMenuActivity.class, null);
    }

    public void testFirstTimeAppOpening() {
        clearEverything(false);

        // should offer to download the dictionary
        assertTrue(solo.waitForText(getActivity().getResources().getString(R.string.download_dictionary), 1, 10000));
        solo.setActivityOrientation(Solo.LANDSCAPE);

        // dialog should still be there after rotating the screen
        assertTrue(solo.waitForText(getActivity().getResources().getString(R.string.download_dictionary), 1, 10000));
        solo.setActivityOrientation(Solo.PORTRAIT);

        // come back up
        assertTrue(solo.waitForText(getActivity().getResources().getString(R.string.download_dictionary), 1, 10000));
        solo.clickOnButton(getActivity().getResources().getString(R.string.later));
        solo.waitForDialogToClose(5);

        solo.clickOnButton(getActivity().getResources().getString(R.string.main_menu_test));

        /*
        Instrumentation instrumentation = getInstrumentation();

        // Moving to another activity
        Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(TestActivity.class.getName(), null, false);
        Activity currentActivity = getInstrumentation().waitForMonitorWithTimeout(monitor, 2000);
        assertTrue(currentActivity!=null);*/

        assertTrue(solo.waitForText(getActivity().getResources().getString(R.string.test_empty_db)));

        ViewPager pager = (ViewPager) solo.getView(R.id.test_pager);
        if (pager != null)
            solo.scrollToSide(Solo.RIGHT);

        // All the scores should be at 0
        TextView tv = (TextView) solo.getView(R.id.stats_chars);
        assertTrue(tv != null);
        assertTrue(tv.getText().equals("0"));
        tv = (TextView) solo.getView(R.id.stats_words);
        assertTrue(tv != null);
        assertTrue(tv.getText().equals("0"));
        tv = (TextView) solo.getView(R.id.stats_to_study);
        assertTrue(tv != null);
        assertTrue(tv.getText().equals("0"));

        solo.setActivityOrientation(Solo.LANDSCAPE);

        tv = (TextView) solo.getView(R.id.stats_chars);
        assertTrue(tv != null);
        assertTrue(tv.getText().equals("0"));
        tv = (TextView) solo.getView(R.id.stats_words);
        assertTrue(tv != null);
        assertTrue(tv.getText().equals("0"));
        tv = (TextView) solo.getView(R.id.stats_to_study);
        assertTrue(tv != null);
        assertTrue(tv.getText().equals("0"));

        if (pager != null)
            solo.scrollToSide(Solo.LEFT);

        assertTrue(solo.waitForText(getActivity().getResources().getString(R.string.test_empty_db)));

        solo.setActivityOrientation(Solo.PORTRAIT);

        solo.goBack();
        solo.waitForActivity("MainMenuActivity");
        assertFalse(solo.waitForText(getActivity().getResources().getString(R.string.download_dictionary), 1, 10000));

        solo.finishOpenedActivities();

        ((AndZiApplication)getActivity().getApplication()).showSplash(true);
        
        startIt();
        assertTrue(solo.waitForText(getActivity().getResources().getString(R.string.download_dictionary), 1, 10000));
        solo.clickOnButton(getActivity().getResources().getString(R.string.later));
        solo.waitForDialogToClose(5);
    }

    public void testAddVocabularyEmptyApp() {
        // First wipe out everything...
        clearEverything(false);

        // Wait until the splash screen is gone
        if(solo.waitForText(getActivity().getResources().getString(R.string.download_dictionary), 1, 10000)) {
            solo.clickOnButton(getActivity().getResources().getString(R.string.later));
            solo.waitForDialogToClose(2000);
        }

        // Let's go add some vocabulary
        solo.clickOnButton(getActivity().getResources().getString(R.string.main_menu_add_voc));

        Instrumentation.ActivityMonitor monitor = getInstrumentation().addMonitor(VocabularyEditorActivity.class.getName(), null, false);
        Activity currentActivity = getInstrumentation().waitForMonitorWithTimeout(monitor, 2000);
        assertTrue(currentActivity!=null);

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

        assertTrue(solo.searchButton("-?-"));
        solo.clickOnText("-?-");
        assertTrue(solo.waitForText(getActivity().getResources().getString(R.string.add_voc_pick_pinyin) + "你"));
        EditText pinyin_entry = (EditText) solo.getView(R.id.edit_new_pinyin);
        assertTrue(pinyin_entry!=null);
        solo.enterText(pinyin_entry, "pyu3");
        solo.clickOnButton(getActivity().getResources().getString(R.string.ok));
        solo.waitForDialogToClose(2000);

        assertTrue(solo.searchButton("-?-"));
        solo.clickOnText("-?-");
        assertTrue(solo.waitForText(getActivity().getResources().getString(R.string.add_voc_pick_pinyin) + "你"));
        pinyin_entry = (EditText) solo.getView(R.id.edit_new_pinyin);
        assertTrue(pinyin_entry!=null);
        solo.enterText(pinyin_entry, "ni2");
        solo.clickOnButton(getActivity().getResources().getString(R.string.ok));
        solo.waitForDialogToClose(2000);
        assertFalse(solo.searchButton("-?-"));
        assertTrue(solo.searchButton("ní"));

        solo.clickOnText("ní");
        assertTrue(solo.waitForText(getActivity().getResources().getString(R.string.add_voc_pick_pinyin) + "你"));
        ListView pinyin_list = (ListView) solo.getView(R.id.pinyin_dialog_choices);
        assertTrue(pinyin_list!= null);
        pinyin_entry = (EditText) solo.getView(R.id.edit_new_pinyin);
        assertTrue(pinyin_entry!=null);
        solo.enterText(pinyin_entry, "ni3");
        solo.clickOnButton(getActivity().getResources().getString(R.string.ok));
        solo.waitForDialogToClose(2000);
        assertTrue(solo.searchButton("nǐ"));

        assertFalse(solo.waitForText(getActivity().getResources().getString(R.string.add_voc_local_db),1,2000));
        assertFalse(solo.waitForText(getActivity().getResources().getString(R.string.add_voc_ref_db),1,2000));

        View add_new_translation_button = solo.getView(R.id.add_vocabulary_show_new_translation);
        solo.clickOnView(add_new_translation_button);
        
        assertTrue(solo.waitForText(getActivity().getResources().getString(R.string.add_voc_new_translation)));
        EditText new_translation_edit_text = (EditText) solo.getView(R.id.add_new_translation);
        assertTrue(new_translation_edit_text!=null);
        assertTrue(new_translation_edit_text.getVisibility()== View.VISIBLE);
        solo.enterText(new_translation_edit_text, "You");
        Button add_translation_ok = (Button) solo.getView(R.id.add_new_translation_ok);
        assertTrue(add_translation_ok != null);
        solo.clickOnView(add_translation_ok);
        assertTrue(solo.waitForText(getActivity().getResources().getString(R.string.add_voc_local_db), 1, 2000));
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

        //getInstrumentation().invokeMenuActionSync(solo.getActivityMonitor().getLastActivity(), R.id.add_vocabulary_done, 0);
        View add_vocabulary_done_button = solo.getView(R.id.add_vocabulary_done);
        solo.clickOnView(add_vocabulary_done_button);

        solo.enterText(master_chinese, "你");
        assertTrue(solo.waitForText(getActivity().getResources().getString(R.string.add_voc_local_db)));
        assertFalse(solo.waitForText(getActivity().getResources().getString(R.string.add_voc_ref_db), 1, 2000));
        assertTrue(solo.waitForText("You"));
        assertTrue(solo.searchButton("nǐ"));

        solo.clearEditText(master_chinese);
        solo.enterText(master_chinese, "你好");
        assertTrue(solo.searchButton("nǐ"));
        assertTrue(solo.searchButton("-?-"));
        solo.clickOnText("-?-");
        assertTrue(solo.waitForText(getActivity().getResources().getString(R.string.add_voc_pick_pinyin) + "好"));
        pinyin_entry = (EditText) solo.getView(R.id.edit_new_pinyin);
        assertTrue(pinyin_entry!=null);
        solo.enterText(pinyin_entry, "hao3");
        solo.clickOnButton(getActivity().getResources().getString(R.string.ok));
        solo.waitForDialogToClose(2000);
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

    /*
    public void testAddVocabularyWithDict() {
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
    }
    */
}
