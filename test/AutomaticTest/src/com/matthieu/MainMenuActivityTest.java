package com.matthieu;

import android.test.ActivityInstrumentationTestCase2;
import android.widget.EditText;
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

    }

}
