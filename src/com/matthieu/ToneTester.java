package com.matthieu;

import android.app.Activity;
import android.os.Bundle;

public class ToneTester extends Activity{
    protected HanziDB myDB=null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        myDB = ((AndZiApplication) getApplication()).myDB;

        setContentView(R.layout.tone_tester);
    }
}
