package com.matthieu;


import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

import java.util.ArrayList;

public class GuessContaining extends TestActivity {
    protected HanziDB myDB=null;
    protected HanziDB refDB=null;
    protected String hanzi=null;
    protected ArrayList<String> correct_answers;

    private final String TAG="GuessContaining";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        myDB = ((AndZiApplication) getApplication()).getMyDB();
        refDB = ((AndZiApplication) getApplication()).getRefDB();
        correct_answers=new ArrayList<String>();
    }

    @Override
    protected void set_next_study() {
        super.set_next_study();
        String candidate, candidate_word;
        ArrayList<String> hanzis = myDB.getAllFilteredToStudy();
        hanzi=null;

        while ((hanzis.size() >0) && (hanzi == null)) {
            candidate = hanzis.remove((int)(hanzis.size()*Math.pow(Math.random(),2)));
            Log.d(TAG, "will study " + candidate);
            ArrayList<Vocabulary> words = myDB.getWordsWithPinyin(candidate);
            if (words.size() == 0) {
                Log.d(TAG, "we have a problem");
            }
            while ((words.size() > 0) && (hanzi == null)) {
                candidate_word=words.remove(0).chinese;
                if (candidate_word.codePointCount(0, candidate_word.length())>1) {
                    hanzi=candidate;
                }
            }

        }
        if (hanzi == null) {
            nothing_to_test();
            return;
        }
        display_test();
    }

    @Override
    protected void display_test() {
        setContentView(R.layout.guess_containing);

        EditText word_guess = (EditText) findViewById(R.id.guess_containing_word_guess);
        word_guess.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                if (i == EditorInfo.IME_ACTION_DONE)
                    handle_ok(textView);
                return true;
            }
        });
        word_guess.setText("");
        word_guess.requestFocus();

        TextView clue= (TextView) findViewById(R.id.guess_containing_chinese);
        clue.setText("Find words containing: "+hanzi);
    }

    @Override
    protected int check_guess(String guess) {
        int res = INCORRECT;
        correct_answers.clear();
        String[] guesses=guess.split("[,， ]");
        for (int i=0; i<guesses.length; i++) {
            if (guesses[i].equals(""))
                continue;
            Log.d(TAG, "Checking "+guesses[i]);
            guesses[i]=guesses[i].trim();
            if (guesses[i].indexOf(hanzi) == -1)
                continue;
            if (guesses[i].codePointCount(0, guesses[i].length())==1)
                continue;
            if (myDB.getTranslations(guesses[i]).size()==0) {
                if ((refDB != null) && (refDB.getTranslations(guesses[i]).size() != 0) && (res!=CORRECT)) {
                    res=OTHER;
                }
            }
            else {
                Log.d(TAG, "Good answer");
                res = CORRECT;
                correct_answers.add(guesses[i]);
            }

        }
        Log.d(TAG, "Answer is "+res);
        return res;
    }

    @Override
    protected void update_scores(int good) {
        Log.d(TAG, "updating scores "+good);
        
        switch(good) {
            case CORRECT:
                ArrayList<String> good_chars=new ArrayList<String>();
                for (String answers:correct_answers) {
                    for (int i=0; i<answers.length();) {
                        String c = answers.substring(i, i+Character.charCount(answers.codePointAt(i)));
                        if ((!good_chars.contains(c)) && (!c.equals(hanzi)))
                            good_chars.add(c);
                        i+=Character.charCount(answers.codePointAt(i));
                    }
                }
                for (String c:good_chars) {
                    Log.d(TAG, "Reset time for "+c);
                    myDB.resetTime(c);
                }
                Log.d(TAG, "Better box for "+hanzi);
                myDB.betterBox(hanzi);
                break;
            case INCORRECT:
                myDB.restartStudy(hanzi);
                break;
            case OTHER:
                myDB.resetTime(hanzi);
                break;
        }
    }

    public void handle_ok(View v) {
        EditText word_guess = (EditText) findViewById(R.id.guess_containing_word_guess);
        if (word_guess==null)
            return;
        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(word_guess.getWindowToken(), 0);

        String guess = word_guess.getText().toString();

        if (handle_submit(guess) == OTHER) {
            if ((show_result == 2) || (show_result == 1)) {
                show_solution(guess, CORRECT);
            }
            else {
                update_scores(OTHER);
                Toast.makeText(this, "Wrong tone", 2).show();
                set_next_study();
            }
        }
    }

    public void show_solution(String guess, int good) {
        setContentView(R.layout.test_result);
        Details.populate_details_view(GuessContaining.this, (ViewGroup) findViewById(R.id.test_results), hanzi, false);
        if ((good == INCORRECT) && (!guess.equals(""))) {
            LinearLayout bad_guess = (LinearLayout) findViewById(R.id.test_result_wrong_guess);
            TextView bad_guess_text = new TextView(GuessContaining.this);
            bad_guess_text.setText("You guessed: "+guess);
            bad_guess_text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
            bad_guess_text.setTextColor(0xffdd0000);
            bad_guess_text.setGravity(Gravity.CENTER_HORIZONTAL);
            bad_guess.addView(bad_guess_text, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            (findViewById(R.id.test_result_wrong)).setVisibility(View.VISIBLE);
        }
    }
}
