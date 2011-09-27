package com.matthieu;

import android.os.Bundle;
import android.preference.PreferenceActivity;

public class AndZiPreferences extends PreferenceActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);    //To change body of overridden methods use File | Settings | File Templates.
        addPreferencesFromResource(R.xml.andzipreferences);
    }
}
