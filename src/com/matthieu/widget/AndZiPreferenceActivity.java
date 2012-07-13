package com.matthieu.widget;

import android.preference.PreferenceActivity;
import com.matthieu.AndZiApplication;

public class AndZiPreferenceActivity extends PreferenceActivity {
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
