package com.matthieu;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.widget.Toast;

import java.util.ArrayList;

public abstract class TestActivity extends Activity {
    @Override
    protected void onResume() {
        super.onResume();
        set_next_study();
    }

    abstract protected void set_next_study();
    // returns 0 for failure, 1 for success and -1 for special handling
    abstract protected int check_guess(String guess);
    abstract protected void update_scores(int good);

    public synchronized void onActivityResult(final int requestCode, int resultCode, final Intent data) {
        if (requestCode != 0)
            return;

        if (resultCode == 1) {
            update_scores(1);
        } else {
            update_scores(0);
        }

    }

    protected void show_solution(int mode, String correct_answer_hanzi) {
        Intent i = new Intent(this, TestResultActivity.class);
		i.putExtra("Hanzi", correct_answer_hanzi);
        i.putExtra("Mode", mode);
        startActivity(i);
    }

    protected void show_solution(int mode, String correct_answer_hanzi, String guess, boolean offer_override) {
        Intent i = new Intent(this, TestResultActivity.class);
		i.putExtra("Hanzi", correct_answer_hanzi);
        i.putExtra("Mode", mode);
        i.putExtra("WrongGuess", guess);
        if (offer_override) {
            i.putExtra("FixAnswer", true);
            startActivityForResult(i, 0);
        }
        else {
            startActivity(i);
        }
    }

    protected void show_solution(int mode, String correct_answer_hanzi, ArrayList<String> correct_answer_pinyin) {
        Intent i = new Intent(this, TestResultActivity.class);
		i.putExtra("Hanzi", correct_answer_hanzi);
        i.putExtra("Pinyins", correct_answer_pinyin);
        i.putExtra("Mode", mode);
        startActivity(i);
    }

    protected void show_solution(int mode, String correct_answer_hanzi, ArrayList<String> correct_answer_pinyin, String guess, boolean offer_override) {
        Intent i = new Intent(this, TestResultActivity.class);
		i.putExtra("Hanzi", correct_answer_hanzi);
        i.putExtra("Pinyins", correct_answer_pinyin);
        i.putExtra("Mode", mode);
        i.putExtra("WrongGuess", guess);
        if (offer_override) {
            i.putExtra("FixAnswer", true);
            startActivityForResult(i, 0);
        }
        else {
            startActivity(i);
        }
    }

    // for Chinese guesses
    protected int handle_submit(String correct_answer, String guess, boolean show_bad_guess) {
        Toast toast;
        int res = check_guess(guess);
        int show_result;

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

        if (res == -1)
            return res;

        if (((show_result == 2) || (show_result == 1)) && (res == 0)) {
            if ((!guess.equals("")) && (show_bad_guess))
                show_solution(0, correct_answer, guess, true);
            else {
                show_solution(0, correct_answer);
                update_scores(res);
            }
        }
        else {
            update_scores(res);

            if (show_result==1) {
                show_solution(0, correct_answer);
            }
            else {
                if (res == 0) {
                    toast = Toast.makeText(this, "Sorry, the correct answer was " + correct_answer, 2);
                }
                else {
                    toast = Toast.makeText(this, "Well done !", 2);
                }
                toast.show();
                set_next_study();
            }
        }
        return res;
    }

    protected int handle_submit(String correct_answer_hanzi, ArrayList<String> correct_answer_pinyin, String guess, boolean show_bad_guess) {
        Toast toast;
        int res = check_guess(guess);
        int show_result;

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

        if (res == -1)
            return res;

        if (((show_result == 2) || (show_result ==1)) && (res == 0)) {
            if ((!guess.equals("")) && (show_bad_guess))
                show_solution(1, correct_answer_hanzi, correct_answer_pinyin, guess, true);
            else {
                show_solution(1, correct_answer_hanzi, correct_answer_pinyin);
                update_scores(res);
            }
        }
        else {
            update_scores(res);

            if (show_result==1) {
                show_solution(1, correct_answer_hanzi, correct_answer_pinyin);
            }
            else {
                if (res == 0) {
                    toast = Toast.makeText(this, "Sorry, this is wrong", 2);
                }
                else {
                    toast = Toast.makeText(this, "Well done !", 2);
                }
                toast.show();
                set_next_study();
            }
        }
        return res;
    }
}
