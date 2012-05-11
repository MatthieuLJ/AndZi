/******************************
 * Parent class for all tests
 */
package com.matthieu;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

public abstract class TestActivity extends Activity {
    protected int answered=-1;
    protected int show_result=1;
    protected int last_result;
    protected boolean single_test;
    protected String hanzi;

    private static final String TAG="TestActivity";

    final static protected int CORRECT=1;
    final static protected int INCORRECT=2;
    final static protected int OTHER=3;
    final static protected int NOTHING_TO_TEST=4;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        single_test=getIntent().getBooleanExtra("SingleTest", false);
        if (single_test)
            Log.d(TAG, "Running single test");
        else
            Log.d(TAG, "Running continuous test");

        hanzi=getIntent().getStringExtra("hanzi");
        if ((hanzi==null) || (hanzi.equals(""))) {
            nothing_to_test();
            return;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);

        if (sp.getString("show_result", "always").equals("always")) {
            show_result = 1;
        }
        else if (sp.getString("show_result", "always").equals("sometimes")) {
            show_result = 2;
        }
        else {
            show_result = 3;
        }

        if (answered==1) {
            another_test_or_quit();
        }
        else if (answered==-1) {
            set_next_study();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        display_test();
    }

    protected void set_next_study() {
        answered=0;
    }
    protected void nothing_to_test() {
        if (single_test) {
            setResult(NOTHING_TO_TEST);
        }
        else {
            Toast.makeText(this, "Sorry, nothing to test you on", 5).show();
        }
        finish();
    }
    abstract protected void display_test();
    abstract protected int check_guess(String guess);
    abstract protected void update_scores(int good);
    abstract protected void show_solution(String guess, int good);

    public synchronized void onActivityResult(final int requestCode, int resultCode, final Intent data) {
        if (requestCode != 0)
            return;

        if (resultCode == CORRECT) {
            update_scores(CORRECT);
        } else {
            update_scores(INCORRECT);
        }
    }

    protected int handle_submit(String guess) {
        Toast toast;
        last_result = check_guess(guess);

        if (last_result == OTHER)
            return last_result;

        answered = 1;

        if (((show_result == 2) || (show_result == 1)) && (last_result == INCORRECT)) {
            show_solution(guess, last_result);
        }
        else {
            if (show_result==1) {
                show_solution(guess, last_result);
            }
            else {
                update_scores(last_result);

                if (last_result == INCORRECT) {
                    toast = Toast.makeText(this, "Wrong answer...", 2);
                }
                else {
                    toast = Toast.makeText(this, "Well done !", 2);
                }
                toast.show();
                another_test_or_quit();
            }
        }
        return last_result;
    }

    public void another_test_or_quit() {
        if (single_test) {
            setResult(last_result);
            finish();
        }
        else
            set_next_study();
    }

    public void handle_next(View v) {
        update_scores(last_result);
        another_test_or_quit();
    }

    public void handle_good(View v) {
        update_scores(CORRECT);
        another_test_or_quit();
    }
}
