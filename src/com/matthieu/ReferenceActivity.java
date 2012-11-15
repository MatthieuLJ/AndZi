package com.matthieu;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.*;
import android.support.v4.view.ViewPager;
import android.util.Log;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.matthieu.widget.handle_details_request;
import com.viewpagerindicator.PageIndicator;

public class ReferenceActivity extends SherlockFragmentActivity implements handle_details_request {
    final private static int max_details_fragments=3;
    Fragment fragments[];
    final private static String TAG = "RerefenceActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_Sherlock);
        requestWindowFeature(com.actionbarsherlock.view.Window.FEATURE_INDETERMINATE_PROGRESS);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.reference);

        ViewPager pager = (ViewPager) findViewById(R.id.reference_pager);

        fragments = new Fragment[max_details_fragments+1];

        if (savedInstanceState != null) {
            for (int i=0; i<=max_details_fragments; i++) {
                fragments[i] = getSupportFragmentManager().getFragment(savedInstanceState, "fragment"+i);
            }
        }
        if (fragments[0] == null)
            fragments[0] = new DictionaryFragment();

        if (pager != null) {
            pager.setOffscreenPageLimit(max_details_fragments+1);
            pager.setAdapter(new reference_pager(getSupportFragmentManager()));

            PageIndicator mIndicator = (PageIndicator) findViewById(R.id.pager_indicator);
            mIndicator.setViewPager(pager);
        }
        else {
            // When we are on a larger screen without a pager
        }

        String hanzi = getIntent().getStringExtra("Hanzi");
        boolean use_ref = getIntent().getBooleanExtra("use_ref", false);
        if (hanzi!=null) {
            new_details_request(hanzi, use_ref);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        try {
            getSupportFragmentManager().putFragment(outState, "fragment0", fragments[0]);
        } catch (IllegalStateException e) {
            Log.e(TAG, "Got an exception saving fragment 0 " + e);
        }
        for (int i=1; i<=max_details_fragments; i++) {
            if (fragments[i] != null) {
                try {
                    getSupportFragmentManager().putFragment(outState, "fragment"+i, fragments[i]);
                } catch (IllegalStateException e) {
                    Log.e(TAG, "Got an exception saving fragment "+i+" "+e);
                }
            }
        }
        ViewPager pager = (ViewPager) findViewById(R.id.test_pager);
        if (pager != null) {
            outState.putInt("position", pager.getCurrentItem());
        }
    }

    String new_query_to_open = null;
    boolean new_query_use_ref = false;

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String hanzi = intent.getStringExtra("Hanzi");
        boolean use_ref = intent.getBooleanExtra("use_ref", false);
        if (hanzi!=null) {
            new_query_to_open = hanzi;
            new_query_use_ref = use_ref;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (new_query_to_open != null) {
            new_details_request(new_query_to_open, new_query_use_ref);
            new_query_to_open=null;
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
                AlertDialog.Builder too_many_pinned = new AlertDialog.Builder(ReferenceActivity.this);
                too_many_pinned.setCancelable(false);
                too_many_pinned.setMessage(getResources().getString(R.string.reference_too_many_pinned));
                too_many_pinned.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int i) {
                        dialog.dismiss();
                    }
                });
                too_many_pinned.create();
                too_many_pinned.show();
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
        fragments[1] = new DetailsFragment();
        Bundle bundle = new Bundle();
        bundle.putString("hanzi", new_hanzi);
        bundle.putString("pinyin", "");
        bundle.putBoolean("use_ref", use_ref);
        fragments[1].setArguments(bundle);

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

    // use a FragmentPagerAdapter here because we are moving pages around (FragmentStatePagerAdapter does not do that well : http://code.google.com/p/android/issues/detail?id=37990)
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
            for (int i=0; i<max_details_fragments+1; i++) {
                if (fragment.equals(fragments[i])) {
                    res = i;
                    break;
                }
            }

            return res;
        }
    }
}
