/******************************
 * Entry class to pick tests randomly
 */
package com.matthieu.zi_app;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.fragment.app.FragmentTransaction;
import androidx.viewpager.widget.ViewPager;
import android.util.Log;
import android.view.MenuItem;
import android.widget.LinearLayout;
import com.matthieu.zi_app.widget.InsideScrollPager;
import com.matthieu.zi_app.widget.handle_details_request;
import com.matthieu.zi_app.R;
import com.viewpagerindicator.PageIndicator;

public class TestActivity extends AppCompatActivity implements handle_details_request {
    private Fragment fragments[];

    private static final boolean DEBUG = false;
    private static final String TAG="Test_Master";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_Sherlock);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.test);
        InsideScrollPager pager = (InsideScrollPager) findViewById(R.id.test_pager);
        LinearLayout linear = (LinearLayout) findViewById(R.id.test_linear);

        fragments = new Fragment[2];

        if (savedInstanceState != null) {
            fragments[0] = getSupportFragmentManager().getFragment(savedInstanceState, "fragment0");
            fragments[1] = getSupportFragmentManager().getFragment(savedInstanceState, "fragment1");
            if (pager != null) {
                pager.setCurrentItem(savedInstanceState.getInt("position"), false);
            }
        }
        if (fragments[0] == null) {
            fragments[0] = new TestFragment();
            if (linear != null) {
                FragmentManager fm = getSupportFragmentManager();
                FragmentTransaction ft = fm.beginTransaction();
                ft.add(R.id.test_fragment0, fragments[0]);
                ft.commit();
            }
        }
        if (fragments[1] == null) {
            fragments[1] = new StatsFragment();
            if (linear != null) {
                FragmentManager fm = getSupportFragmentManager();
                FragmentTransaction ft = fm.beginTransaction();
                ft.add(R.id.test_fragment1, fragments[1]);
                ft.commit();
            }
        }

        if (pager != null) {
            pager.set_max_pages(2);
            pager.setBackgroundAsset(R.raw.bamboo);
            pager.setOffscreenPageLimit(2);

            pager.setAdapter(new test_pager_adapter(getSupportFragmentManager()));

            PageIndicator mIndicator = (PageIndicator) findViewById(R.id.pager_indicator);
            mIndicator.setViewPager(pager);

            mIndicator.setOnPageChangeListener(new keyboard_handle());
            if (savedInstanceState == null)
                pager.setCurrentItem(0, false);
        }

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        try {
            getSupportFragmentManager().putFragment(outState, "fragment0", fragments[0]);
        } catch (IllegalStateException e) {
            if (DEBUG) Log.e(TAG, "Got an exception saving fragment 0 "+e);
        }
        if (fragments[1] != null) {
            try {
                getSupportFragmentManager().putFragment(outState, "fragment1", fragments[1]);
            } catch (IllegalStateException e) {
                if (DEBUG) Log.e(TAG, "Got an exception saving fragment 1 "+e);
            }
        }
        ViewPager pager = (ViewPager) findViewById(R.id.test_pager);
        if (pager != null) {
            outState.putInt("position", pager.getCurrentItem());
        } else {
            outState.putInt("position", 0);
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
        ((TestFragment)fragments[0]).resume_test();
    }

    private class keyboard_handle implements ViewPager.OnPageChangeListener {
        @Override
        public void onPageScrolled(int i, float v, int i1) {

        }

        @Override
        public void onPageSelected(int i) {
            if (i==1) {
                update_stats();
            }
        }

        @Override
        public void onPageScrollStateChanged(int i) {
        }
    }

    public void new_solution_fragment(Test.SolutionView solution) {
        ((TestFragment) fragments[0]).show_solution(solution);
    }

    public void score_changed() {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ViewPager pager = (ViewPager) findViewById(R.id.test_pager);
                if ((pager == null) || (pager.getCurrentItem()==1))
                    update_stats();
            }
        });
    }

    public void update_stats() {
        ((StatsFragment) fragments[1]).update_stats();
    }

    @Override
    public void onBackPressed() {
        ViewPager pager = (ViewPager) findViewById(R.id.test_pager);
        if ((pager != null) && (pager.getCurrentItem() != 0) && (test_ready))
            pager.setCurrentItem(0, true);
        else
            super.onBackPressed();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return false;
    }

    boolean test_ready=true;
    public void test_done(boolean done) {
        InsideScrollPager pager = (InsideScrollPager) findViewById(R.id.test_pager);
        if (pager != null) {
            if (done)
                pager.setCurrentItem(1, true);
            pager.setPagingEnabled(!done);
        }
        if ((!test_ready) && (!done))
            ((TestFragment) fragments[0]).new_test();
        test_ready =!done;
    }

    // using a FragmentStatePagerAdapter here because some Fragments go away and come back (for the test)
    // The FragmentPagerAdapter does not resume well old fragments
    private class test_pager_adapter extends FragmentStatePagerAdapter {
        public test_pager_adapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int i) {
            return fragments[i];
        }

        @Override
        public int getCount() {
            return 2;
        }

        @Override
        public int getItemPosition(Object object) {
            Fragment fragment = (Fragment) object;
            for (int i=0; i<2; i++)
                if (fragment.equals(fragments[i]))
                    return POSITION_UNCHANGED;

            return POSITION_NONE;
        }
    }
}
