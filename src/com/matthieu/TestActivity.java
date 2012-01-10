package com.matthieu;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

public abstract class TestActivity extends Activity {
    protected boolean answered=true;
    protected int show_result=1;
    protected int last_result;

    final static protected int CORRECT=1;
    final static protected int INCORRECT=0;
    final static protected int OTHER=-1;

    @Override
    protected void onResume() {
        super.onResume();

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        if (answered) {
            set_next_study();
            answered=false;
        }
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
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        display_test();
    }

    protected void set_next_study() {
        answered=false;
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

    // for Chinese guesses
    protected int handle_submit(String guess) {
        Toast toast;
        last_result = check_guess(guess);

        if (last_result == OTHER)
            return last_result;

        answered = true;

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
                set_next_study();
            }
        }
        return last_result;
    }

    public void handle_next(View v) {
        update_scores(last_result);
        set_next_study();
    }
}
