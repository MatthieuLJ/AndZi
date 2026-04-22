/******************************
 * Entry class to pick tests randomly
 */
package com.matthieu.flash;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.fragment.app.FragmentActivity;
import android.util.Log;
import android.widget.LinearLayout;
import com.matthieu.flash.widget.handle_details_request;
import android.view.MenuItem;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

public class TestActivity extends FragmentActivity  implements handle_details_request {
    private Fragment[] fragments;

    private static final boolean DEBUG = false;
    private static final String TAG="Test_Master";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // TODO: setTheme(R.style.Theme_Sherlock);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.test);
        ViewPager2 pager = findViewById(R.id.test_pager);
        LinearLayout linear = findViewById(R.id.test_linear);

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
            pager.setOffscreenPageLimit(2);
            pager.setAdapter(new test_pager_adapter(getSupportFragmentManager()));

            if (savedInstanceState == null)
                pager.setCurrentItem(0, false);
        }


        OnBackPressedCallback backCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                ViewPager2 pager = findViewById(R.id.test_pager);
                if ((pager != null) && (pager.getCurrentItem() != 0) && (test_ready))
                    pager.setCurrentItem(0, true);
                else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        };

        getOnBackPressedDispatcher().addCallback(this, backCallback);

    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
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
        ViewPager2 pager = findViewById(R.id.test_pager);
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
        if (fragments[0] != null) {
            ((TestFragment)fragments[0]).resume_test();
        }
    }

    private class keyboard_handle extends ViewPager2.OnPageChangeCallback {
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
        if (fragments[0] != null) {
            ((TestFragment) fragments[0]).show_solution(solution);
        }
    }

    public void score_changed() {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ViewPager2 pager = findViewById(R.id.test_pager);
                if ((pager == null) || (pager.getCurrentItem()==1))
                    update_stats();
            }
        });
    }

    public void update_stats() {
        if (fragments[1] != null) {
            ((StatsFragment) fragments[1]).update_stats();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            finish();
            return true;
        }
        return false;
    }

    boolean test_ready=true;
    public void test_done(boolean done) {
        ViewPager2 pager = findViewById(R.id.test_pager);
        if (pager != null) {
            if (done)
                pager.setCurrentItem(1, true);
            pager.setUserInputEnabled(!done);
        }
        if ((!test_ready) && (!done) && (fragments[0] != null))
            ((TestFragment) fragments[0]).new_test();
        test_ready =!done;
    }

    // using a FragmentStatePagerAdapter here because some Fragments go away and come back (for the test)
    // The FragmentPagerAdapter does not resume well old fragments
    private class test_pager_adapter extends FragmentStateAdapter {
        public test_pager_adapter(FragmentManager fm) {
            super(fm, TestActivity.this.getLifecycle() );
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return fragments[position];
        }

        @Override
        public int getItemCount() {
            return 2;
        }
    }
}
