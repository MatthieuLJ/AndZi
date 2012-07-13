package com.matthieu.widget;

import android.app.Activity;
import com.matthieu.AndZiApplication;

public class AndZiActivity extends Activity {
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
