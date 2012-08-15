package com.matthieu;

import android.test.ActivityInstrumentationTestCase2;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
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

    public void testMe() {
        // Wait until the splash screen is gone
        assertTrue(solo.waitForText(getActivity().getResources().getString(R.string.download_dictionary), 1, 10000));

        solo.clickOnButton(getActivity().getResources().getString(R.string.later));
        solo.clickOnButton("Test me!");
        assertTrue(solo.waitForText(getActivity().getResources().getString(R.string.review_list_nothing_to_study)));
        solo.clickOnButton(getActivity().getResources().getString(R.string.ok));
        solo.clickOnButton("Review list");
        assertTrue(solo.waitForText(getActivity().getResources().getString(R.string.review_list_nothing_to_study)));
        solo.clickOnButton(getActivity().getResources().getString(R.string.ok));

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

        assertTrue(solo.searchButton("-?-"));
        solo.clickOnText("-?-");
        assertTrue(solo.waitForText(getActivity().getResources().getString(R.string.add_voc_pick_pinyin) + "你"));
        pinyin_entries = solo.getCurrentEditTexts();
        assertTrue(pinyin_entries.size()==1);
        solo.enterText(pinyin_entries.get(0), "ni2");
        solo.clickOnButton(getActivity().getResources().getString(R.string.ok));
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
        assertTrue(solo.searchButton("nǐ"));

    }

}
