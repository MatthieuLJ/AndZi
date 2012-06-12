package com.matthieu;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

import java.lang.ref.SoftReference;
import java.util.HashMap;

public class Help extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.help);
        ViewPager pager = (ViewPager) findViewById(R.id.help);
        pager.setAdapter(new HelpPageAdapter());
    }

    private class HelpPageAdapter extends PagerAdapter {
        HashMap<Integer, SoftReference<View>> view_storage = new HashMap<Integer, SoftReference<View>>();

        @Override
        public int getCount() {
            return 2;
        }

        @Override
        public boolean isViewFromObject(View view, Object o) {
            return view == o;
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            View new_view=null;
            if (view_storage.containsKey(position)) {
                new_view = view_storage.get(position).get();
            }

            if (new_view == null) {
                LayoutInflater inflater = getLayoutInflater();
                new_view = inflater.inflate(R.layout.help_page, null);
                WebView wv = (WebView) new_view.findViewById(R.id.help_content);
                switch(position) {
                    case 0:
                        wv.loadUrl("file:///android_asset/help.html");
                        break;
                    case 1:
                        wv.loadUrl("file:///android_asset/credits.html");
                        break;
                    default:
                        break;
                }
                view_storage.put(position, new SoftReference<View>(new_view));
            }

            container.addView(new_view);
            return new_view;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView((View) object);
        }

    }
}
