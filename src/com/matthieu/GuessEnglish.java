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
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;

public class GuessEnglish extends TestActivity {
    protected HanziDB myDB=null;
    protected HanziDB refDB=null;
    private Vocabulary solution=null;

    private final String TAG="GuessEnglish";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        myDB = ((AndZiApplication) getApplication()).getMyDB();
        refDB = ((AndZiApplication) getApplication()).getRefDB();
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
        ArrayList<String> hanzis = myDB.getAllFilteredToStudy();
        String hanzi;
        solution=null;

        while ((hanzis.size() >0) && (solution == null)) {
            hanzi = hanzis.remove((int)(hanzis.size()*Math.pow(Math.random(),2)));
            Log.d(TAG, "will study " + hanzi);
            ArrayList<Vocabulary> words = myDB.getWordsWithPinyin(hanzi);
            if (words.size() == 0) {
                Log.d(TAG, "we have a problem");
            }

            while ((words.size() > 0) && (solution == null)) {
                candidate = words.remove((int) (words.size()*Math.random())).chinese;
                ArrayList<Vocabulary> voc = myDB.getTranslations(candidate);
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
        }
        if (solution == null) {
            Toast.makeText(this, "Sorry, nothing to test you on", 5).show();
            this.finish();
            return;
        }
        display_test();
    }

    @Override
    protected void display_test() {
        setContentView(R.layout.guess_english);

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
        Log.d(TAG, "updating scores "+good);

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
        }
        else {
            for (int i=0; i<solution.chinese.length();) {
                String hanzi = solution.chinese.substring(i, i+Character.charCount(solution.chinese.codePointAt(i)));
                myDB.restartStudy(hanzi);
                Log.d(TAG, hanzi +" starts over");
                i+=Character.charCount(solution.chinese.codePointAt(i));
            }
        }
    }

    public void handle_ok(View v) {
        EditText word_guess = (EditText) findViewById(R.id.guess_english_word_guess);
        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(word_guess.getWindowToken(), 0);

        String guess = word_guess.getText().toString();

        if (handle_submit(guess) == -1)
            word_guess.setText("");
    }

    @Override
    protected void show_solution(String guess, int good) {
        setContentView(R.layout.test_result);
        DetailsWord.populate_word_details_view(GuessEnglish.this, (ViewGroup) findViewById(R.id.test_results), solution.chinese, false);
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
        else {
            update_scores(good);
        }
    }

    public void handle_good(View v) {
        update_scores(CORRECT);
        set_next_study();
    }
}
