package com.matthieu;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.viewpagerindicator.PageIndicator;


public class VocabularyEditorActivity extends SherlockFragmentActivity {
    Fragment fragments[];

    HanziDB refDB, myDB;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_Sherlock);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.vocabulary_editor);

        refDB = ((AndZiApplication) getApplication()).getRefDB();
        myDB = ((AndZiApplication) getApplication()).getMyDB();

        String hanzi = getIntent().getStringExtra("Hanzi");

        fragments = new Fragment[3];
        if (hanzi != null)
            fragments[0] = new AddVocabularyFragment(hanzi);
        else
            fragments[0] = new AddVocabularyFragment();
        fragments[1] = new EditLessonsFragment();
        fragments[2] = null;

        ViewPager pager = (ViewPager) findViewById(R.id.voc_editor_pager);
        if (pager != null) {
            pager.setOffscreenPageLimit(2);
            pager.setAdapter(new editor_pager(getSupportFragmentManager()));

            PageIndicator mIndicator = (PageIndicator) findViewById(R.id.pager_indicator);
            mIndicator.setViewPager(pager);
        }
        else {
            // When we are on a larger screen without a pager
        }
    }

    private class editor_pager extends FragmentPagerAdapter {
        public editor_pager(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int i) {
            return fragments[i];
        }

        @Override
        public int getCount() {
            return fragments[2]==null?2:3;
        }
    }
}
