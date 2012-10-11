package com.matthieu.widget;

import android.os.Bundle;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.matthieu.AndZiApplication;
import com.matthieu.R;

public class AndZiFragmentActivity extends SherlockFragmentActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_Sherlock);
        super.onCreate(savedInstanceState);
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
