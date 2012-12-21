/******************************
 * Entry class to pick tests randomly
 */
package com.matthieu;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.*;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.MenuItem;
import com.matthieu.widget.handle_details_request;
import com.viewpagerindicator.PageIndicator;

import java.util.*;

public class TestActivity extends SherlockFragmentActivity  implements handle_details_request {
    private Fragment fragments[];

    private static final String TAG="Test_Master";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_Sherlock);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.test);
        ViewPager pager = (ViewPager) findViewById(R.id.test_pager);
        LinearLayout linear = (LinearLayout) findViewById(R.id.test_linear);

        fragments = new Fragment[3];

        if (savedInstanceState != null) {
            fragments[0] = getSupportFragmentManager().getFragment(savedInstanceState, "fragment0");
            fragments[1] = getSupportFragmentManager().getFragment(savedInstanceState, "fragment1");
            fragments[2] = getSupportFragmentManager().getFragment(savedInstanceState, "fragment2");
            if (pager != null) {
                pager.setCurrentItem(savedInstanceState.getInt("position"));
            }
        }
        if (fragments[0] == null) {
            fragments[0] = new StatsFragment();
            if (linear != null) {
                FragmentManager fm = getSupportFragmentManager();
                FragmentTransaction ft = fm.beginTransaction();
                ft.add(R.id.test_fragment0, fragments[0]);
                ft.commit();
            }
        }
        if (fragments[1] == null) {
            fragments[1] = new TestFragment();
            if (linear != null) {
                FragmentManager fm = getSupportFragmentManager();
                FragmentTransaction ft = fm.beginTransaction();
                ft.add(R.id.test_fragment1, fragments[1]);
                ft.commit();
            }
        }

        if (linear != null) {
            FrameLayout solution_frame = (FrameLayout) findViewById(R.id.test_fragment2);
            solution_frame.setVisibility(fragments[2]==null ? View.GONE : View.VISIBLE);
        }

        if (pager != null) {
            pager.setOffscreenPageLimit(2);
            pager.setAdapter(new test_pager(getSupportFragmentManager()));

            PageIndicator mIndicator = (PageIndicator) findViewById(R.id.pager_indicator);
            mIndicator.setViewPager(pager);

            mIndicator.setOnPageChangeListener(new keyboard_handle());
        }

        TextView result_overlay = (TextView) findViewById(R.id.test_result);
        result_overlay.setVisibility(View.GONE);
        if (pager != null) {
            pager.setCurrentItem(1, false);
        }

        getSupportActionBar().setDisplayShowTitleEnabled(false);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        try {
            getSupportFragmentManager().putFragment(outState, "fragment0", fragments[0]);
        } catch (IllegalStateException e) {
            Log.e(TAG, "Got an exception saving fragment 0 "+e);
        }
        if (fragments[1] != null) {
            try {
                getSupportFragmentManager().putFragment(outState, "fragment1", fragments[1]);
            } catch (IllegalStateException e) {
                Log.e(TAG, "Got an exception saving fragment 1 "+e);
            }
        }
        if (fragments[2] != null) {
            try {
                getSupportFragmentManager().putFragment(outState, "fragment2", fragments[2]);
            } catch (IllegalStateException e) {
                Log.e(TAG, "Got an exception saving fragment 2 "+e);
            }
        }
        ViewPager pager = (ViewPager) findViewById(R.id.test_pager);
        if (pager != null) {
            outState.putInt("position", pager.getCurrentItem());
        }
    }

    @Override
    public void new_details_request(String hanzi, boolean use_ref) {
        Intent i=new Intent(this, ReferenceActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        i.putExtra("Hanzi", hanzi);
        i.putExtra("use_ref", false);
        startActivity(i);
    }

    @Override
    protected void onStart() {
        super.onStart();
        ((TestFragment)fragments[1]).resume_test();
    }

    private class keyboard_handle implements ViewPager.OnPageChangeListener {
        @Override
        public void onPageScrolled(int i, float v, int i1) {

        }

        @Override
        public void onPageSelected(int i) {
            if (fragments[1] == null)
                return;
            View v = fragments[1].getView();
            if (v == null)
                return;
            /*View entry = v.findViewById(R.id.test_guess);
            if (i==1) {
                if (entry!=null) {
                    entry.requestFocus();
                }
            } else*/ if (i==0) {
                update_stats();
            }
            /*else {
                //TestActivity.this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
                if (entry!=null) {
                    InputMethodManager imm = (InputMethodManager)TestActivity.this.getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(entry.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
                }
            }*/
        }

        @Override
        public void onPageScrollStateChanged(int i) {
        }
    }

    public void new_solution_fragment(Fragment solution, int correctness) {
        if (fragments[2] != null) {
            FragmentManager fm = getSupportFragmentManager();
            FragmentTransaction ft = fm.beginTransaction();
            ft.remove(fragments[2]);
            ft.commit();
        }

        LinearLayout linear = (LinearLayout) findViewById(R.id.test_linear);
        if (linear!= null) {
            FragmentManager fm = getSupportFragmentManager();
            FragmentTransaction ft = fm.beginTransaction();
            ft.add(R.id.test_fragment2, solution);
            ft.commit();
        }

        fragments[2] = solution;
        FrameLayout solution_frame = (FrameLayout) findViewById(R.id.test_fragment2);
        if (solution_frame != null)
            solution_frame.setVisibility(View.VISIBLE);

        final TextView result_overlay = (TextView) findViewById(R.id.test_result);
        switch(correctness) {
            case Test.INCORRECT:
                result_overlay.setText("We'll have to study that again");
                break;
            case Test.CORRECT:
                result_overlay.setText("Well Done!");
                break;
            case Test.OTHER:
                result_overlay.setText("Almost");
                break;
        }
        result_overlay.setVisibility(View.VISIBLE);
        TimerTask hide_timer = new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        result_overlay.setVisibility(View.GONE);
                    }
                });
            }
        };
        new Timer().schedule(hide_timer, 4000);

        ViewPager pager = (ViewPager) findViewById(R.id.test_pager);
        if (pager != null) {
            pager.getAdapter().notifyDataSetChanged();
        }

        ((TestFragment)fragments[1]).new_test();
    }

    public void update_stats() {
        ((StatsFragment) fragments[0]).update_stats();
    }

    @Override
    public void onBackPressed() {
        ViewPager pager = (ViewPager) findViewById(R.id.test_pager);
        if ((pager != null) && (pager.getCurrentItem() != 1))
            pager.setCurrentItem(1, true);
        else
            super.onBackPressed();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }
        return false;
    }

    // using a FragmentStatePagerAdapter here because some Fragments go away and come back (for the test)
    // The FragmentPagerAdapter does not resume well old fragments
    private class test_pager extends FragmentStatePagerAdapter {
        public test_pager(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int i) {
            return fragments[i];
        }

        @Override
        public int getCount() {
            return fragments[2] == null ? 2 : 3;
        }

        @Override
        public int getItemPosition(Object object) {
            Fragment fragment = (Fragment) object;
            for (int i=0; i<3; i++)
                if (fragment.equals(fragments[i]))
                    return POSITION_UNCHANGED;

            return POSITION_NONE;
        }
    }
}
