package com.matthieu;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
import android.widget.ScrollView;

import java.util.Timer;
import java.util.TimerTask;

public class Credits extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.credits);
        WebView wv = (WebView) findViewById(R.id.credits);
        wv.loadUrl("file:///android_asset/credits.html");

        final ScrollView sv = (ScrollView) findViewById(R.id.credits_scroll);
        sv.setSmoothScrollingEnabled(true);

        Timer t = new Timer();
        TimerTask scroller = new TimerTask() {
            public void run() {
                Credits.this.runOnUiThread(new Runnable() {
                    public void run() {
                        sv.smoothScrollBy(10,10);
                    }
                });
            };
        };
        t.schedule(scroller, 200, 200);
    }
}
