package com.matthieu;

import android.test.ActivityInstrumentationTestCase2;

/**
 * This is a simple framework for a test of an Application.  See
 * {@link android.test.ApplicationTestCase ApplicationTestCase} for more information on
 * how to write and extend Application tests.
 * <p/>
 * To run this test, you can type:
 * adb shell am instrument -w \
 * -e class com.matthieu.MyDbActivityTest \
 * com.matthieu.tests/android.test.InstrumentationTestRunner
 */
public class MyDbActivityTest extends ActivityInstrumentationTestCase2<MyDbActivity> {

    public MyDbActivityTest() {
        super("com.matthieu", MyDbActivity.class);
    }

}
