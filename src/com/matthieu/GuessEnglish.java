/******************************
 * Activity to guess the English translation from the Chinese word
 */
package com.matthieu;

import android.content.Context;
import android.content.res.Configuration;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;

public class GuessEnglish extends TestActivity {
    private Vocabulary solution=null;
    private int current_view=0;
    private String last_guess="";
    private int last_good=0;

    private final String TAG="GuessEnglish";

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        if (current_view==R.layout.guess_english) {
            EditText word_guess = (EditText) findViewById(R.id.guess_english_word_guess);
            String guess = word_guess.getText().toString();
            super.onConfigurationChanged(newConfig);
            word_guess = (EditText) findViewById(R.id.guess_english_word_guess);
            word_guess.setText(guess);
        } else if (current_view == R.layout.test_result) {
            super.onConfigurationChanged(newConfig);
            show_solution(last_guess, last_good);
        } else {
            super.onConfigurationChanged(newConfig);
        }
    }

    @Override
    protected int check_guess(String guess) {
        String[] guesses=guess.replaceAll("[^A-Za-z ,]", "").toLowerCase().split("[, ]");
        for (int i=0; i<guesses.length; i++) {
            if (guesses[i].equals(""))
                continue;
            Log.d(TAG, "Checking "+guesses[i]);
            guesses[i]=guesses[i].trim();
            for (String en:solution.english) {
                Log.d(TAG, "Against "+en);
                en=en.toLowerCase();
                if (en.equals(guesses[i].trim())) {
                    Log.d(TAG, "got a match "+en+" and "+guesses[i].trim());
                    return CORRECT;
                }

                String[] ens=HanziDB.bare_translation(en).split(" ");
                for (String item:ens) {
                    String tmp=item.replaceAll("[^A-Za-z]", "");
                    if ((tmp.length()>2) && (tmp.equals(guesses[i].trim()))) {
                        Log.d(TAG, "got a match "+tmp+" and "+guesses[i].trim());
                        return CORRECT;
                    }
                }
            }
        }
        return INCORRECT;
    }


    @Override
    protected void set_next_study() {
        super.set_next_study();
        String candidate;
        solution=null;

        ArrayList<Vocabulary> words = myDB.getFilteredWordsWithPinyinForTesting(hanzi);

        while ((words.size() > 0) && (solution == null)) {
            candidate = words.remove(0).chinese;
            ArrayList<Vocabulary> voc = myDB.getFilteredTranslations(candidate);
            if ((voc.size() == 0) || (voc.size() > 1)) // one case has multiple pinyins...
                continue;
            solution = voc.get((int) (voc.size()*Math.random()));

            // find a translation that's not too complicated... but still enough
            int i,j;
            for (i=0; i<solution.english.size(); i++) {
                String[] translations=HanziDB.bare_translation(solution.english.get(i)).split(" ");

                if (translations.length > 2)
                    continue;
                for (j=0; j<translations.length; j++) {
                    if (translations[j].length() > 2)
                        break;
                }
                if (j==translations.length)
                    continue;

                break;
            }
            if (i<solution.english.size())
                break;

            solution=null;
        }
        if (solution == null) {
            nothing_to_test();
            return;
        }
        display_test();
    }

    @Override
    protected void display_test() {
        setContentView(R.layout.guess_english);
        current_view=R.layout.guess_english;

        EditText word_guess = (EditText) findViewById(R.id.guess_english_word_guess);
        word_guess.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                if (i == EditorInfo.IME_ACTION_DONE)
                    handle_ok(textView);
                return true;
            }
        });
        word_guess.setText("");
        word_guess.requestFocus();

        TextView text = (TextView) findViewById(R.id.guess_english_word_voc);
        text.setText(solution.chinese);
    }

    @Override
    protected void update_scores(int good) {
        Log.d(TAG, "update_scores ("+hanzi+") "+good);

        int min_box = 5;

        if (good == CORRECT) {
            for (int i=0; i<solution.chinese.length();) {
                int new_box = myDB.getBoxForChar(solution.chinese.substring(i, i+Character.charCount(solution.chinese.codePointAt(i))));
                if (new_box < min_box) {
                    min_box = new_box;
                }

                i+=Character.charCount(solution.chinese.codePointAt(i));
            }
            for (int i=0; i<solution.chinese.length();) {
                String hanzi = solution.chinese.substring(i, i+Character.charCount(solution.chinese.codePointAt(i)));
                int box = myDB.getBoxForChar(hanzi);
                if (box == min_box) {
                    myDB.betterBox(hanzi);
                    Log.d(TAG, hanzi +" moves up");
                }
                else {
                    myDB.resetTime(hanzi);
                    Log.d(TAG, hanzi +" will come back later");
                }

                i+=Character.charCount(solution.chinese.codePointAt(i));
            }
            myDB.guessedWord(true, solution.chinese);
        }
        else {
            for (int i=0; i<solution.chinese.length();) {
                String hanzi = solution.chinese.substring(i, i+Character.charCount(solution.chinese.codePointAt(i)));
                myDB.restartStudy(hanzi);
                Log.d(TAG, hanzi +" starts over");
                i+=Character.charCount(solution.chinese.codePointAt(i));
            }
            myDB.guessedWord(false, solution.chinese);
        }
    }

    public void handle_ok(View v) {
        EditText word_guess = (EditText) findViewById(R.id.guess_english_word_guess);
        if (word_guess==null)
            return;
        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(word_guess.getWindowToken(), 0);

        String guess = word_guess.getText().toString();

        if (handle_submit(guess) == -1)
            word_guess.setText("");
    }

    @Override
    protected void show_solution(String guess, int good) {
        setContentView(R.layout.test_result);
        current_view=R.layout.test_result;
        last_guess=guess;
        last_good=good;
        Details.populate_details_view(GuessEnglish.this, (ViewGroup) findViewById(R.id.test_results), solution.chinese, myDB);
        if ((good == INCORRECT) && (!guess.equals(""))) {
            LinearLayout bad_guess = (LinearLayout) findViewById(R.id.test_result_wrong_guess);
            TextView bad_guess_text = new TextView(GuessEnglish.this);
            bad_guess_text.setText("You guessed: "+guess);
            bad_guess_text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
            bad_guess_text.setTextColor(0xffdd0000);
            bad_guess_text.setGravity(Gravity.CENTER_HORIZONTAL);
            bad_guess.addView(bad_guess_text, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            (findViewById(R.id.test_result_wrong)).setVisibility(View.VISIBLE);
            (findViewById(R.id.test_result_accept)).setVisibility(View.VISIBLE);
        }
    }
}
