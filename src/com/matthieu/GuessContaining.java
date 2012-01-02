package com.matthieu;


import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Arrays;

public class GuessContaining extends TestActivity {
    protected HanziDB myDB=null;
    protected HanziDB refDB=null;
    protected String hanzi=null;

    private final String TAG="GuessContaining";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        myDB = ((AndZiApplication) getApplication()).getMyDB();
        refDB = ((AndZiApplication) getApplication()).getRefDB();
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
            Toast.makeText(this, "Sorry, nothing to test you on", 5).show();
            this.finish();
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

        TextView clue= (TextView) findViewById(R.id.guess_containing_chinese);
        clue.setText("Find words containing: "+hanzi);
    }

    @Override
    protected int check_guess(String guess) {
        int res = INCORRECT;
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
                if ((refDB != null) && (refDB.getTranslations(guesses[i]).size() != 0)) {
                    res=OTHER;
                }
            }
            else {
                Log.d(TAG, "Good answer");
                return CORRECT;
            }

        }
        Log.d(TAG, "Answer is "+res);
        return res;
    }

    @Override
    protected void update_scores(int good) {
        switch(good) {
            case CORRECT:
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
        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(word_guess.getWindowToken(), 0);

        String guess = word_guess.getText().toString();

        if (handle_submit(hanzi, guess, false) == OTHER) {
            update_scores(OTHER);
            if ((show_result == 2) || (show_result ==1)) {
                show_solution(TestResultActivity.MODE_CHARACTER, hanzi);
            }
            else {
                Toast.makeText(this, "Wrong tone", 2).show();
                set_next_study();
            }
        }
    }
}
