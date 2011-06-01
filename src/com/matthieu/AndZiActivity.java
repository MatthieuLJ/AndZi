package com.matthieu;

import android.app.Activity;
import android.os.Bundle;

public class AndZiActivity extends Activity
{
    HanziDB hanDB;
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        hanDB = new HanziDB(this);
    }
}
