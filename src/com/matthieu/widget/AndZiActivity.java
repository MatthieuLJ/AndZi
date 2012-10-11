package com.matthieu.widget;

import android.os.Bundle;
import com.actionbarsherlock.app.SherlockActivity;
import com.matthieu.AndZiApplication;
import com.matthieu.R;

public class AndZiActivity extends SherlockActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(R.style.Theme_Sherlock);
    }

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
