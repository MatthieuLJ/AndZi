package com.matthieu;

import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import com.matthieu.widget.AndZiActivity;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Arrays;

public class Help extends AndZiActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.help);
        ViewPager pager = (ViewPager) findViewById(R.id.help);
        pager.setAdapter(new HelpPageAdapter());
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        ViewPager pager = (ViewPager) findViewById(R.id.help);
        PagerAdapter adapter = pager.getAdapter();
        setContentView(R.layout.help);
        pager = (ViewPager) findViewById(R.id.help);
        pager.setAdapter(adapter);
    }

    private class HelpPageAdapter extends PagerAdapter {
        SparseArray<SoftReference<View>> view_storage = new SparseArray<SoftReference<View>>();
        ArrayList<String> pages = new ArrayList<String>(Arrays.asList(
                "file:///android_asset/help_introduction.html",
                "file:///android_asset/help_installation.html",
                "file:///android_asset/help_add_vocabulary.html",
                "file:///android_asset/help_lessons.html",
                "file:///android_asset/help_review.html",
                "file:///android_asset/help_tests.html",
                "file:///android_asset/help_test_tone.html",
                "file:///android_asset/help_test_pinyin.html",
                "file:///android_asset/help_test_missing.html",
                "file:///android_asset/help_test_containing.html",
                "file:///android_asset/help_test_chinese.html",
                "file:///android_asset/help_test_english.html",
                "file:///android_asset/help_dictionary1.html",
                "file:///android_asset/help_dictionary2.html",
                "file:///android_asset/help_dictionary3.html",
                "file:///android_asset/help_stats.html",
                "file:///android_asset/credits.html"));

        @Override
        public int getCount() {
            return pages.size();
        }

        @Override
        public boolean isViewFromObject(View view, Object o) {
            return view == o;
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            View new_view=null;
            if (view_storage.get(position) != null) {
                new_view = view_storage.get(position).get();
            }

            if (new_view == null) {
                LayoutInflater inflater = getLayoutInflater();
                new_view = inflater.inflate(R.layout.help_page, null);
                WebView wv = (WebView) new_view.findViewById(R.id.help_content);
                wv.getSettings().setJavaScriptEnabled(true);
                wv.loadUrl(pages.get(position));
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
