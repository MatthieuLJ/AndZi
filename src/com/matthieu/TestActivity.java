package com.matthieu;

import android.app.Activity;
import android.content.Intent;
import android.widget.Toast;

import java.util.ArrayList;

public abstract class TestActivity extends Activity {
    @Override
    protected void onResume() {
        super.onResume();
        set_next_study();
    }

    abstract protected void set_next_study();
    abstract protected int check_guess(String guess);
    abstract protected void update_scores(boolean good);

    public synchronized void onActivityResult(final int requestCode, int resultCode, final Intent data) {
        if (requestCode != 0)
            return;

        if (resultCode == 1) {
            update_scores(true);
        } else {
            update_scores(false);
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

    protected int handle_submit(String correct_answer, String guess, int show_result) {
        Toast toast;
        int res = check_guess(guess);

        if (res == -1)
            return res;

        if (((show_result == 2) || (show_result ==1)) && (res == 0)) {
            if (!guess.equals(""))
                show_solution(0, correct_answer, guess, true);
            else {
                show_solution(0, correct_answer, guess, false);
                update_scores(false);
            }
        }
        else {
            if (res == 0)
                update_scores(false);
            else
                update_scores(true);

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

    protected int handle_submit(String correct_answer_hanzi, ArrayList<String> correct_answer_pinyin, String guess, int show_result) {
        Toast toast;
        int res = check_guess(guess);

        if (res == -1)
            return res;

        if (((show_result == 2) || (show_result ==1)) && (res == 0)) {
            if (!guess.equals(""))
                show_solution(1, correct_answer_hanzi, correct_answer_pinyin, guess, true);
            else {
                show_solution(1, correct_answer_hanzi, correct_answer_pinyin, guess, false);
                update_scores(false);
            }
        }
        else {
            if (res == 0)
                update_scores(false);
            else
                update_scores(true);

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
