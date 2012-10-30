package com.matthieu;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.ViewPager;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.matthieu.widget.handle_details_request;
import com.viewpagerindicator.PageIndicator;

public class ReferenceActivity extends SherlockFragmentActivity implements handle_details_request {
    final private static int max_details_fragments=3;
    Fragment fragments[];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_Sherlock);
        requestWindowFeature(com.actionbarsherlock.view.Window.FEATURE_INDETERMINATE_PROGRESS);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.reference);

        fragments = new Fragment[max_details_fragments+1];
        fragments[0] = new DictionaryFragment();
        for (int i=0; i<max_details_fragments; i++)
            fragments[i+1] = null;

        ViewPager pager = (ViewPager) findViewById(R.id.reference_pager);
        if (pager != null) {
            pager.setOffscreenPageLimit(max_details_fragments+1);
            pager.setAdapter(new reference_pager(getSupportFragmentManager()));

            PageIndicator mIndicator = (PageIndicator) findViewById(R.id.pager_indicator);
            mIndicator.setViewPager(pager);
        }
        else {
            // When we are on a larger screen without a pager
        }
    }

    @Override
    public void new_details_request(String new_hanzi, boolean use_ref) {
        // Check if that request does not exist already
        for (int i=1; i<=max_details_fragments; i++) {
            if (fragments[i] == null)
                break;
            if ((((DetailsFragment)fragments[i]).get_use_ref() == use_ref) &&
                ((DetailsFragment)fragments[i]).get_hanzi().equals(new_hanzi)) {
                ViewPager pager = (ViewPager) findViewById(R.id.reference_pager);
                if (pager != null) {
                    pager.setCurrentItem(i, true);
                }
                return;
            }

        }

        // Insert the new fragment
        int max;
        if (fragments[max_details_fragments] == null) {
            // we still have space, just have to shift everything else
            max = max_details_fragments;
        } else {
            // find the rightmost fragment not pinned
            int index;
            for (index = max_details_fragments; index>0; index--)
                if (!((DetailsFragment)fragments[index]).isPinned())
                    break;
            if (index == 0) {
                // failed, let's just put up a note...
                return;
            }

            FragmentManager fm = getSupportFragmentManager();
            FragmentTransaction ft = fm.beginTransaction();
            ft.remove(fragments[index]);
            ft.commit();

            max = index;

        }

        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        // remove the top fragment
        if (fragments[max] != null) {
            ft.remove(fragments[max]);
        }
        for (int i=max; i>1; i--) {
            // shift
            fragments[i] = fragments[i-1];
        }

        // insert the new one
        fragments[1] = new DetailsFragment(new_hanzi, use_ref);

        ft.add(R.id.reference_pager, fragments[1]);
        ft.commit();

        // flush (?)
        fm.executePendingTransactions();

        // refresh
        ViewPager pager = (ViewPager) findViewById(R.id.reference_pager);
        if (pager != null) {
            pager.getAdapter().notifyDataSetChanged();
            pager.setCurrentItem(1, true);
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
        public long getItemId(int position) {
            return fragments[position].hashCode();
        }

        @Override
        public int getCount() {
            int max;
            for (max=1; max<=max_details_fragments; max++) {
                if (fragments[max] == null)
                    break;
            }
            return max;
        }


        @Override
        public int getItemPosition(Object object) {
            int res = POSITION_NONE;
            Fragment fragment = (Fragment) object;
            for (int i=0; i<max_details_fragments+1; i++)
                if (fragment.equals(fragments[i])) {
                    res = i;
                    break;
                }

            return res;
        }
    }
}
