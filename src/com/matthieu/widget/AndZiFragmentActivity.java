package com.matthieu.widget;

import android.support.v4.app.FragmentActivity;
import com.matthieu.AndZiApplication;

public class AndZiFragmentActivity extends FragmentActivity {
    @Override
    protected void onPause() {
        super.onPause();
        ((AndZiApplication)getApplication()).activityPaused();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ((AndZiApplication)getApplication()).activityResumed();
    }
}
