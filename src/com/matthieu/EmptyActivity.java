package com.matthieu;

import android.app.Activity;
import android.os.Bundle;

public class EmptyActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Pinyin.prepare_pinyin_process();
    }
}
