/******************************
 * Activity to guess the pinyin of a character
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
import android.widget.*;
import java.util.ArrayList;

public class GuessPinyin extends TestActivity {
    private String pinyin;
    private ArrayList<Vocabulary> words=new ArrayList<Vocabulary>();
    private int current_view=0;
    private String last_guess="";
    private int last_good=0;
    private boolean show_words=false;

    private static final String TAG="GuessPinyin";

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        if (current_view==R.layout.guess_pinyin) {
            EditText word_guess = (EditText) findViewById(R.id.guess_pinyin_guess);
            String guess = word_guess.getText().toString();
            super.onConfigurationChanged(newConfig);
            word_guess = (EditText) findViewById(R.id.guess_pinyin_guess);
            word_guess.setText(guess);
        } else if (current_view == R.layout.test_result) {
            super.onConfigurationChanged(newConfig);
            show_solution(last_guess, last_good);
        } else {
            super.onConfigurationChanged(newConfig);
        }
    }

    @Override
    protected void set_next_study() {
        super.set_next_study();

        show_words=false;

        ArrayList<String> pinyins = myDB.getPinyinForChar(hanzi);

        if (pinyins.size() > 1) {
            show_words=true;

            ArrayList<Pinyin> pins=new ArrayList<Pinyin>();
            // remove the ones without tone that have equivalents with tones...
            for (String p:pinyins) {
                Pinyin pin = new Pinyin(p);
                pins.add(pin);
            }
            for (int i=0; i<pins.size(); i++) {
                Pinyin pin = pins.get(i);
                if (pin.get_tone()!=5)
                    continue;
                for (Pinyin pin2:pins) {
                    if ((pin2.get_radical().equals(pin.get_radical())) && (pin2.get_tone() != 5)) {
                        pins.remove(i);
                        i=0;
                        break;
                    }
                }
            }
            pinyin=pins.get((int)(Math.random()*pins.size())).get_lame();
        }
        else {
            pinyin = pinyins.get(0);
        }
        words = myDB.getFilteredWordsWithPinyin(hanzi, pinyin);
        boolean found=false;
        for (Vocabulary word : words) {
            if (!word.chinese.replace(hanzi, "").equals(""))
                found = true;
        }
        if (!found) {
            nothing_to_test();
            return;
        }

        display_test();
    }

    @Override
    protected void display_test() {
        setContentView(R.layout.guess_pinyin);
        current_view=R.layout.guess_pinyin;

        EditText guess = (EditText) findViewById(R.id.guess_pinyin_guess);
        guess.setText("");
        guess.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                if (i == EditorInfo.IME_ACTION_DONE)
                    handle_ok(textView);
                return true;
            }
        });
        guess.requestFocus();

        Log.d(TAG, "Testing for character " + hanzi + " got " + words.size() + " words");

        TextView chinese_hint = (TextView) findViewById(R.id.guess_pinyin_chinese);
        chinese_hint.setText(hanzi);

        final TableLayout tl = (TableLayout) findViewById(R.id.guess_pinyin_word_grid);
        tl.removeAllViews();
        TableRow.LayoutParams trlp;
        for (Vocabulary w : words) {
            TableRow tr = new TableRow(this);

            TextView chinese = new TextView(this);
            chinese.setTextAppearance(GuessPinyin.this, android.R.style.TextAppearance_Medium_Inverse);
            chinese.setClickable(false);
            chinese.setText(w.chinese);
            trlp = new TableRow.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            trlp.setMargins(5,5,5,5);
            chinese.setLayoutParams(trlp);
            tr.addView(chinese);

            TextView trans = new TextView(this);
            trans.setTextAppearance(GuessPinyin.this, android.R.style.TextAppearance_Medium_Inverse);
            trans.setClickable(false);
            String en="";
            for (int j=0; j<w.english.size(); j++) {
                en += w.english.get(j);
                if (j<w.english.size()-1)
                    en += ", ";
            }
            trans.setText(en);
            trlp = new TableRow.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            trlp.setMargins(5,5,5,5);
            trans.setLayoutParams(trlp);
            tr.addView(trans);

            tl.addView(tr);
        }
        Button show_words_button = (Button) findViewById(R.id.guess_pinyin_show_words);
        show_words_button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                tl.setVisibility(View.VISIBLE);
                view.setClickable(false);
            }
        });

        if (show_words) {
            tl.setVisibility(View.VISIBLE);
            show_words_button.setClickable(false);
        }
        else {
            tl.setVisibility(View.GONE);
            show_words_button.setClickable(true);
        }
        tl.invalidate();
    }

    @Override
    protected int check_guess(String guess) {
        Pinyin guess_pinyin = new Pinyin(guess);
        Pinyin solution = new Pinyin(pinyin);
        if (guess_pinyin.get_tone() == -1) // invalid Pinyin
            return INCORRECT;

        if (guess_pinyin.equals(solution))
            return CORRECT;

        if (guess_pinyin.get_radical().equals(solution.get_radical()))
            return OTHER;

        return INCORRECT;
    }

    @Override
    protected void update_scores(int good) {
        Log.d(TAG, "update_scores ("+hanzi+") "+good);
        switch(good) {
            case INCORRECT:
                myDB.restartStudy(hanzi);
                break;
            case CORRECT:
                myDB.betterBox(hanzi);
                break;
            case OTHER:
                myDB.resetTime(hanzi);
                break;
        }
    }

    public void handle_ok(View v) {
        EditText word_guess = (EditText) findViewById(R.id.guess_pinyin_guess);
        if (word_guess==null)
            return;
        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(word_guess.getWindowToken(), 0);

        String guess = word_guess.getText().toString();

        if (handle_submit(guess) == OTHER) {
            if ((show_result == 2) || (show_result ==1)) {
                show_solution(guess, INCORRECT);
            }
            else {
                update_scores(OTHER);
                Toast toast = Toast.makeText(this, "Wrong tone", 2);
                toast.show();
                another_test_or_quit();
            }
        }
    }

    protected void show_solution(String guess, int good) {
        setContentView(R.layout.test_result);
        current_view=R.layout.test_result;
        last_guess=guess;
        last_good=good;
        Details.populate_details_view(GuessPinyin.this, (ViewGroup) findViewById(R.id.test_results), hanzi, myDB);
        if ((good == INCORRECT) && (!guess.equals(""))) {
            LinearLayout bad_guess = (LinearLayout) findViewById(R.id.test_result_wrong_guess);
            TextView bad_guess_text = new TextView(GuessPinyin.this);
            bad_guess_text.setText("You guessed: "+guess);
            bad_guess_text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
            bad_guess_text.setTextColor(0xffdd0000);
            bad_guess_text.setGravity(Gravity.CENTER_HORIZONTAL);
            bad_guess.addView(bad_guess_text, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            (findViewById(R.id.test_result_wrong)).setVisibility(View.VISIBLE);
        }
    }
}
