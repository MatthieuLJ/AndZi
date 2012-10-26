/******************************
 * Entry class to pick tests randomly
 */
package com.matthieu;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Debug;
import android.support.v4.app.*;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.View;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.viewpagerindicator.PageIndicator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

public class TestActivity extends SherlockFragmentActivity {
    private ArrayList<TestFragment> char_tests;
    private ArrayList<TestFragment> word_tests;

    private Fragment fragments[];

    private HanziDB myDB;
    private boolean continue_with_more_chars=false;
    private String current_hanzi;

    private static final String TAG="Test_Master";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //Debug.startMethodTracing("testing");
        setTheme(R.style.Theme_Sherlock);
        super.onCreate(savedInstanceState);
        myDB = ((AndZiApplication) getApplication()).getMyDB();

        char_tests = new ArrayList<TestFragment>(Arrays.asList(
                new GuessMissingChar(this), new GuessContaining(this), new GuessPinyin(this), new GuessTone(this)));
        word_tests = new ArrayList<TestFragment>(Arrays.asList(
                new GuessChineseWord(this), new GuessEnglish(this)));

        fragments = new Fragment[3];

        fragments[0] = new StatsFragment();

        setContentView(R.layout.test);
        ViewPager pager = (ViewPager) findViewById(R.id.test_pager);
        if (pager != null) {
            pager.setAdapter(new test_pager(getSupportFragmentManager()));

            PageIndicator mIndicator = (PageIndicator) findViewById(R.id.pager_indicator);
            mIndicator.setViewPager(pager);
        }
        else {
            // When we are on a larger screen without a pager
        }
        start_test();
        if (pager != null) {
            pager.setCurrentItem(1, false);
        }
    }

    private void set_next_char_to_study() {
        ArrayList<String> hanzis=myDB.getAllFilteredToStudy();
        if (continue_with_more_chars)
            hanzis.addAll(myDB.getMostAdvancedExpiring(5, false));

        if (hanzis.size()==0) {
            if (!continue_with_more_chars) {
                AlertDialog.Builder nothing_to_study = new AlertDialog.Builder(TestActivity.this);
                nothing_to_study.setCancelable(false);
                if (myDB.isFilterEnabled()) {
                    nothing_to_study.setMessage(getResources().getString(R.string.test_mastering_disable_filter));
                    nothing_to_study.setPositiveButton(getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int i) {
                            dialog.dismiss();
                            myDB.enableFilter(false);
                            start_test();
                        }
                    });
                } else if (myDB.getNumChars() > 0) {
                    nothing_to_study.setMessage(getResources().getString(R.string.test_mastering));
                    nothing_to_study.setPositiveButton(getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int i) {
                            dialog.dismiss();
                            continue_with_more_chars=true;
                            start_test();
                        }
                    });
                }

                if (myDB.getNumChars() == 0) {
                    nothing_to_study.setMessage(getResources().getString(R.string.review_list_nothing_to_study));
                    nothing_to_study.setPositiveButton(getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int i) {
                            dialog.dismiss();
                            finish();
                        }
                    });
                }
                else {
                    nothing_to_study.setNegativeButton(getResources().getString(R.string.cancel), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int i) {
                            dialog.dismiss();
                            finish();
                        }
                    });
                }
                nothing_to_study.create();
                nothing_to_study.show();
            } else {
                finish();
            }
            current_hanzi=null;
        }
        else {
            current_hanzi = hanzis.remove((int)(hanzis.size()*Math.pow(Math.random(),2)));
            Log.d(TAG, "Will study" + current_hanzi);
        }
    }

    private void start_test() {
        set_next_char_to_study();
        if (current_hanzi == null)
            return;
        ArrayList<TestFragment> tests = new ArrayList<TestFragment>();
        if (current_hanzi.length()==1) {
            tests.addAll(char_tests);
            if (myDB.getWordsWithPinyin(current_hanzi).size()>0)
                tests.addAll(word_tests);
        } else {
            tests.addAll(word_tests);
        }
        Collections.shuffle(tests);
        int current_index;
        for (current_index=0; current_index< tests.size(); current_index++) {
            if (tests.get(current_index).set_hanzi_to_test(current_hanzi)) {
                Fragment old_fragment = fragments[1];

                fragments[1] = tests.get(current_index);

                ((TestFragment) fragments[1]).update_test_view(fragments[1].getView());

                ViewPager pager = (ViewPager) findViewById(R.id.test_pager);
                if (pager != null) {
                    pager.getAdapter().notifyDataSetChanged();
                }
                return;
            }
        }
        finish();
    }

//    int count=0;
    public void new_solution_fragment(Fragment solution, int correctness) {
//        count++;
//        if (count == 4)
//            Debug.stopMethodTracing();
        if (fragments[2] != null) {
            FragmentManager fm = getSupportFragmentManager();
            FragmentTransaction ft = fm.beginTransaction();
            ft.remove(fragments[2]);
            ft.commit();
        }
        fragments[2] = solution;
        start_test();
    }

    public void update_stats() {
        ((StatsFragment) fragments[0]).update_stats();
    }

    public boolean handle_ok(View v) {
        return ((TestFragment)fragments[1]).handle_ok(v);
    }

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
            if (fragment == fragments[0])
                return POSITION_UNCHANGED;
            else if (fragment == fragments[1])
                return POSITION_UNCHANGED;
            else if (fragment == fragments[2])
                return POSITION_UNCHANGED;
            else
                return POSITION_NONE;
        }
    }
}
