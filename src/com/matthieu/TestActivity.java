/******************************
 * Entry class to pick tests randomly
 */
package com.matthieu;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.*;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.matthieu.widget.handle_details_request;
import com.viewpagerindicator.PageIndicator;

import java.util.*;

public class TestActivity extends SherlockFragmentActivity  implements handle_details_request {
    private ArrayList<TestFragment> char_tests;
    private ArrayList<TestFragment> word_tests;

    private Fragment fragments[];

    private HanziDB myDB;
    ArrayList<String> hanzis_to_test;
    boolean table_filled;
    private boolean continue_with_more_chars=false;
    private String current_hanzi;

    private static final String TAG="Test_Master";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
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

            mIndicator.setOnPageChangeListener(new keyboard_handle());
        }
        else {
            // When we are on a larger screen without a pager
        }
        TextView result_overlay = (TextView) findViewById(R.id.test_result);
        result_overlay.setVisibility(View.GONE);
        fill_hanzis_to_test();
        start_test();
        if (pager != null) {
            pager.setCurrentItem(1, false);
        }
    }

    final static int size_limit = 10;
    final static int size_threshold = 5;

    private void fill_hanzis_to_test() {
        synchronized (TestActivity.this) {
            hanzis_to_test = myDB.getAllFilteredToStudy(size_limit);
            if ((continue_with_more_chars) && (hanzis_to_test.size() < size_limit))
                hanzis_to_test.addAll(myDB.getMostAdvancedExpiring(size_limit - hanzis_to_test.size(), false));
            table_filled=true;
        }
    }

    private void trim_hanzis_to_test(String hanzi) {
        synchronized (TestActivity.this) {
            for (int offset=0; offset<hanzi.length();) {
                String c = hanzi.substring(offset, offset + Character.charCount(hanzi.codePointAt(offset)));
                for (int i=0; i<hanzis_to_test.size(); i++) {
                    String h = hanzis_to_test.get(i);
                    if (h.contains(c)) {
                        hanzis_to_test.remove(i);
                        i--;
                    }
                }
                offset += Character.charCount(hanzi.codePointAt(offset));
            }
        }
        if (hanzis_to_test.size() < size_threshold)
            fill_hanzis_to_test();
    }

    private void set_next_char_to_study() {
        if ((hanzis_to_test.size()==0) && (table_filled)) {
            current_hanzi=null;
        }
        else if (hanzis_to_test.size()==0) {
            fill_hanzis_to_test();
            set_next_char_to_study();
        }
        else {
            current_hanzi = hanzis_to_test.remove((int)(hanzis_to_test.size()*Math.pow(Math.random(),2)));
            Log.d(TAG, "Will study" + current_hanzi);
        }
    }

    private void start_test() {
        Fragment new_fragment1=null;
        Fragment old_fragment1 = fragments[1];
        boolean new_fragment1_is_test=false;
        while(new_fragment1 == null) {
            set_next_char_to_study();
            if (current_hanzi == null) {
                Cursor filtered = myDB.getCursorFilteredToStudy();
                Cursor non_filtered = myDB.getCursorToStudy();
                boolean can_disable_filter=((myDB.isFilterEnabled()) && (filtered.getCount() > non_filtered.getCount()));
                boolean empty_db=(myDB.getNumChars()==0);

                new_fragment1 = new done_testing(can_disable_filter, empty_db);
                new_fragment1_is_test = false;

                filtered.close();
                non_filtered.close();
                break;
            }
            ArrayList<TestFragment> tests = new ArrayList<TestFragment>();
            if (current_hanzi.length()==1) {
                tests.addAll(char_tests);
                if (myDB.getTranslations(current_hanzi).size()>0)
                    tests.addAll(word_tests);
            } else {
                tests.addAll(word_tests);
            }
            Collections.shuffle(tests);
            int current_index;
            for (current_index=0; current_index< tests.size(); current_index++) {
                if (tests.get(current_index).set_hanzi_to_test(current_hanzi)) {
                    new_fragment1 = tests.get(current_index);
                    new_fragment1_is_test = true;
                    break;
                }
            }
        }

        if ((old_fragment1 != null) && (old_fragment1 != new_fragment1)) {
            FragmentManager fm = getSupportFragmentManager();
            fm.beginTransaction().remove(old_fragment1).commit();
        }

        fragments[1] = new_fragment1;

        if (new_fragment1_is_test)
            ((TestFragment) fragments[1]).update_test_view(fragments[1].getView());

        ViewPager pager = (ViewPager) findViewById(R.id.test_pager);
        if (pager != null) {
            pager.getAdapter().notifyDataSetChanged();
        }

        if (new_fragment1_is_test) {
            table_filled = false;
            Thread filler = new Thread() {
                @Override
                public void run() {
                    trim_hanzis_to_test(current_hanzi);
                }
            };
            filler.start();
        }
    }

    @Override
    public void new_details_request(String hanzi, boolean use_ref) {
        // TODO
    }

    private class done_testing extends Fragment {
        boolean empty_db;
        boolean filter_option;
        public done_testing(boolean filter_option, boolean empty_db) {
            this.empty_db = empty_db;
            this.filter_option = filter_option;

        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            final View res = inflater.inflate(R.layout.test_done, container, false);
            TextView message = (TextView) res.findViewById(R.id.test_done_text);
            if (empty_db) {
                message.setText("Your vocabulary list is empty");
            }
            else if (filter_option) {
                message.setText("You can disable the learning filter");
            }
            else {
                message.setText("You can continue anyway");
            }

            Button OK_button = (Button) res.findViewById(R.id.ok);
            OK_button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (filter_option) {
                        myDB.enableFilter(false);
                    } else {
                        TestActivity.this.continue_with_more_chars = true;
                    }
                    TestActivity.this.table_filled = false;
                    TestActivity.this.start_test();
                }
            });

            Button Cancel_button = (Button) res.findViewById(R.id.cancel);
            if (empty_db)
                Cancel_button.setVisibility(View.GONE);
            Cancel_button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    TestActivity.this.finish();
                }
            });

            return res;
        }
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
            View entry = v.findViewById(R.id.test_guess);
            if (i==1) {
                if (entry!=null) {
                    entry.requestFocus();
                }
            }
            else {
                //TestActivity.this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
                if (entry!=null) {
                    InputMethodManager imm = (InputMethodManager)TestActivity.this.getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(entry.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
                }
            }
        }

        @Override
        public void onPageScrollStateChanged(int i) {

        }
    }

    public void new_solution_fragment(Fragment solution, int correctness) {
        if (fragments[1] != null) {
            View v = fragments[1].getView();
            if (v != null) {
                View entry = v.findViewById(R.id.test_guess);
                if (entry != null) {
                    InputMethodManager imm = (InputMethodManager)TestActivity.this.getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(entry.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
                }
            }
        }

        if (fragments[2] != null) {
            FragmentManager fm = getSupportFragmentManager();
            FragmentTransaction ft = fm.beginTransaction();
            ft.remove(fragments[2]);
            ft.commit();
        }
        fragments[2] = solution;
        final TextView result_overlay = (TextView) findViewById(R.id.test_result);
        switch(correctness) {
            case TestFragment.INCORRECT:
                result_overlay.setText("We'll have to study that again");
                break;
            case TestFragment.CORRECT:
                result_overlay.setText("Well Done!");
                break;
            case TestFragment.OTHER:
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
            for (int i=0; i<3; i++)
                if (fragment.equals(fragments[i]))
                    return POSITION_UNCHANGED;

            return POSITION_NONE;
        }
    }
}
