package com.matthieu;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import com.matthieu.widget.AndZiFragmentActivity;

import java.util.ArrayList;

public class ReferenceActivity extends AndZiFragmentActivity {
    String current_details="";
    Fragment fragments[];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.reference);

        fragments = new Fragment[2];
        fragments[0] = new DictionaryFragment();
        fragments[1] = new DetailsFragment();

        ((DetailsFragment) fragments[1]).switch_to_reference(true);

        ViewPager pager = (ViewPager) findViewById(R.id.reference_pager);
        if (pager != null) {
            pager.setAdapter(new reference_pager(getSupportFragmentManager()));
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
}
