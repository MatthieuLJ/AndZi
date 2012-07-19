/******************************
 * Parent class for all tests
 */
package com.matthieu;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;
import com.matthieu.widget.AndZiActivity;

public abstract class TestActivity extends AndZiActivity {
    protected final int STATE_UNKNOWN=0;
    protected final int STATE_ASKED=1;
    protected final int STATE_ANSWERED=2;

    protected int test_state = STATE_UNKNOWN;
    protected int show_result=1;
    protected String last_guess="";
    protected int last_good=0;
    protected int last_result;
    protected String hanzi;

    protected HanziDB myDB=null;
    protected HanziDB refDB=null;

    private static final String TAG="TestActivity";

    final static protected int CORRECT=1;
    final static protected int INCORRECT=2;
    final static protected int OTHER=3;
    final static protected int NOTHING_TO_TEST=4;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        myDB = ((AndZiApplication) getApplication()).getMyDB();
        refDB = ((AndZiApplication) getApplication()).getRefDB();

        hanzi=getIntent().getStringExtra("hanzi");
        if ((hanzi==null) || (hanzi.equals(""))) {
            nothing_to_test();
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

        if (test_state == STATE_UNKNOWN) {
            set_next_study();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        switch (test_state) {
            case STATE_UNKNOWN:
                another_test_or_quit();
                break;
            case STATE_ASKED:
                display_test();
                break;
            case STATE_ANSWERED:
                show_solution(last_guess, last_good);
                break;
        }
    }

    protected void set_next_study() {
        test_state = STATE_ASKED;
    }
    protected void nothing_to_test() {
        setResult(NOTHING_TO_TEST);
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

        test_state = STATE_ANSWERED;

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
                    toast = Toast.makeText(this, "Wrong answer...", Toast.LENGTH_SHORT);
                }
                else {
                    toast = Toast.makeText(this, "Well done !", Toast.LENGTH_SHORT);
                }
                toast.show();
                another_test_or_quit();
            }
        }
        return last_result;
    }

    public void another_test_or_quit() {
        setResult(last_result);
        finish();
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
