/******************************
 * Parent class for all tests
 */
package com.matthieu;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ViewFlipper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;

public class TestFragment extends Fragment {
    private ArrayList<Test> tests;

    private static final int num_char_tests = 4;
    private Animation animFlipIn,animFlipOut;

    private Test current_test_type;
    private String current_test_hanzi;
    private int test_index;

    private Handler my_handler;

    private HanziDB myDB;

    ArrayList<String> hanzis_to_test = new ArrayList<String>();
    boolean table_filled=false;
    private boolean continue_with_more_chars=false;

    final static int size_limit = 30;
    final static int size_threshold = 5;

    private static final String TAG = "TestFragment";

    public TestFragment() {
        tests = new ArrayList<Test>(Arrays.asList(
                new GuessMissingChar(), new GuessContaining(), new GuessPinyin(), new GuessTone(), // characters
                new GuessChineseWord(), new GuessEnglish() // words
        ));
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        animFlipIn = AnimationUtils.loadAnimation(activity, R.anim.flipin);
        animFlipOut = AnimationUtils.loadAnimation(activity, R.anim.flipout);
        for (Test t:tests)
            t.attach(activity);
        myDB = ((AndZiApplication)activity.getApplication()).getMyDB();
        my_handler = new Handler();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View res = inflater.inflate(R.layout.test_flipper, container, false);
        View test = start_test(inflater, container, savedInstanceState);
        ViewFlipper flipper = (ViewFlipper) res.findViewById(R.id.test_flipper);
        flipper.addView(test);

        flipper.setInAnimation(animFlipIn);
        flipper.setOutAnimation(animFlipOut);
        return res;
    }

    @Override
    public void onDestroyView() {
        if (current_test_type != null) {
            ViewGroup container = (ViewGroup) getView();
            current_test_type.kill_view(container);
            ((ViewFlipper)container.findViewById(R.id.test_flipper)).removeAllViews();
        }

        super.onDestroyView();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("test_index", test_index);
        outState.putString("test_hanzi", current_test_hanzi);
    }

    public void new_test() {
        if (getView() == null)
            return;
        ViewFlipper flipper = (ViewFlipper) getView().findViewById(R.id.test_flipper);

        if (getActivity() == null)
            return;
        LayoutInflater inflater = getActivity().getLayoutInflater();

        Test old_test_type = current_test_type;
        View new_test_view = start_test(inflater, flipper, null);

        flipper.addView(new_test_view);

        if (flipper.getChildCount() > 1) {
            flipper.showNext();
            if (old_test_type != null) {
                old_test_type.kill_view(flipper.getChildAt(0));
            }
            flipper.removeViewAt(0);
        }
    }


    private void fill_hanzis_to_test() {
        Log.d(TAG, "fill_hanzis_to_test");
        synchronized (TestFragment.this) {
            hanzis_to_test = myDB.getAllToStudy(size_limit);
            if ((continue_with_more_chars) && (hanzis_to_test.size() < size_limit))
                hanzis_to_test.addAll(myDB.getMostAdvancedExpiring(size_limit - hanzis_to_test.size(), false));
            table_filled=true;
        }
    }

    private void trim_hanzis_to_test(String hanzi) {
        Log.d(TAG, "trim_hanzis_to_test "+hanzi);
        synchronized (TestFragment.this) {
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
        if ((hanzis_to_test.size() < size_threshold) && (!table_filled)) {
            fill_hanzis_to_test();
            trim_hanzis_to_test(hanzi);
        }
    }

    private void set_next_char_to_study() {
        Log.d(TAG, "set_next_char_to_study");
        if ((hanzis_to_test.size()==0) && (table_filled)) {
            current_test_hanzi =null;
        }
        else if (hanzis_to_test.size()==0) {
            fill_hanzis_to_test();
            if (current_test_hanzi != null)
                trim_hanzis_to_test(current_test_hanzi);
            set_next_char_to_study();
        }
        else {
            current_test_hanzi = hanzis_to_test.remove((int)(hanzis_to_test.size()*Math.pow(Math.random(),2)));
            Log.d(TAG, "Will study " + current_test_hanzi);
        }
    }

    public void resume_test() {
        if (current_test_type == null)
            new_test();
    }

    public View start_test(LayoutInflater inflater, ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (savedInstanceState == null)
            set_next_char_to_study();
        else
            current_test_hanzi = savedInstanceState.getString("test_hanzi");

        if (current_test_hanzi == null) {
            return done_testing(myDB.getNumChars()==0, inflater, container);
        }
        int min_test, max_test;
        if (current_test_hanzi.length()==1) {
            min_test=0;
            if (myDB.getTranslations(current_test_hanzi).size()>0)
                max_test = tests.size();
            else
                max_test = num_char_tests;
        } else {
            min_test = num_char_tests;
            max_test = tests.size();
        }

        if (savedInstanceState == null) {
            test_index = min_test + (int)Math.floor(Math.random()*(max_test-min_test));
            current_test_type = null;
            for (; ; test_index = ((test_index+1) % (max_test - min_test)) + min_test) {
                if (tests.get(test_index).set_hanzi_to_test(current_test_hanzi)) {
                    break;
                }
            }
        } else {
            test_index = savedInstanceState.getInt("test_index");
            tests.get(test_index).set_hanzi_to_test(current_test_hanzi);
        }
        current_test_type = tests.get(test_index);

        table_filled = false;
        my_handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                trim_hanzis_to_test(current_test_hanzi);
            }
        }, 500);

        return current_test_type.get_view();
    }


    private View done_testing(boolean empty_db, final LayoutInflater inflater, final ViewGroup container) {
        final View res = inflater.inflate(R.layout.test_done, container, false);
        TextView message = (TextView) res.findViewById(R.id.test_done_text);
        if (empty_db) {
            message.setText(R.string.test_empty_db);
        }
        else {
            message.setText(R.string.test_mastering);
        }

        Button OK_button = (Button) res.findViewById(R.id.ok);
        OK_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                continue_with_more_chars = true;
                table_filled = false;
                new_test();
            }
        });

        Button Cancel_button = (Button) res.findViewById(R.id.cancel);
        if (empty_db)
            Cancel_button.setVisibility(View.GONE);
        Cancel_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getActivity().finish();
            }
        });

        return res;
    }
}
