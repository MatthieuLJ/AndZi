package com.matthieu;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.viewpagerindicator.PageIndicator;

public class ReferenceActivity extends SherlockFragmentActivity {
    String current_details="";
    Fragment fragments[];

    HanziDB refDB, myDB;
    boolean dic_ref_db;
    boolean dic_chinese_to_english=true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_Sherlock);
        requestWindowFeature(com.actionbarsherlock.view.Window.FEATURE_INDETERMINATE_PROGRESS);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.reference);
        setSupportProgressBarIndeterminateVisibility(true);

        refDB = ((AndZiApplication) getApplication()).getRefDB();
        myDB = ((AndZiApplication) getApplication()).getMyDB();
        dic_ref_db = (refDB != null);

        fragments = new Fragment[2];
        fragments[0] = new DictionaryFragment(dic_ref_db?refDB:myDB);
        fragments[1] = new DetailsFragment();

        ((DetailsFragment) fragments[1]).switch_to_reference(true);

        ViewPager pager = (ViewPager) findViewById(R.id.reference_pager);
        if (pager != null) {
            pager.setAdapter(new reference_pager(getSupportFragmentManager()));

            PageIndicator mIndicator = (PageIndicator) findViewById(R.id.pager_indicator);
            mIndicator.setViewPager(pager);
        }
        else {
            // When we are on a larger screen without a pager
        }
    }

    private class reference_pager extends FragmentPagerAdapter {
        public reference_pager(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int i) {
            return fragments[i];
        }

        @Override
        public int getCount() {
            return ReferenceActivity.this.current_details.equals("")?1:2;
        }
    }

    public void update_details(String new_hanzi) {
        ((DetailsFragment) fragments[1]).change_chinese(new_hanzi);
        current_details = new_hanzi;
        ViewPager pager = (ViewPager) findViewById(R.id.reference_pager);
        if (pager != null) {
            pager.getAdapter().notifyDataSetChanged();
            pager.setCurrentItem(1, true);
        }
    }

    private class fragment_type {
        boolean pinned=false;
        String details;
        boolean from_ref;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getSupportMenuInflater();
        inflater.inflate(R.menu.dictionary_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem item = menu.findItem(R.id.dictionary_switch_dic);
        if (refDB == null)
            item.setVisible(false);
        else {
            item.setVisible(true);
            if(dic_ref_db)
                item.setTitle("Ref");
            else
                item.setTitle("List");
        }
        item = menu.findItem(R.id.dictionary_switch_direction);
        if (dic_chinese_to_english)
            item.setTitle("中->Eng");
        else
            item.setTitle("Eng->中");

        return true;
    }
}
