/******************************
 * Activity to guess a Chinese word based on the English translations
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
import com.matthieu.widget.VocabularyCursorAdapter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public class GuessChineseWord extends TestActivity {
    private Vocabulary solution;
    public ArrayList<String> translations, more_translations,previous_guess;
    private boolean showing_more_voc;

    private final String TAG="GuessChineseWord";

    protected void set_next_study() {
        super.set_next_study();
        solution=null;

        if (hanzi.codePointCount(0, hanzi.length())>3) {
            nothing_to_test();
            return;
        }

        // need some translations
        ArrayList<Vocabulary> voc = myDB.getFilteredTranslations(hanzi);
        if (voc.size() == 0) {
            nothing_to_test();
            return;
        }
        solution = voc.get((int) (voc.size()*Math.random()));

        translations = solution.english;
        if (myDB.isFilterEnabled()) {
            more_translations = myDB.getTranslations(hanzi, solution.pinyin).english;
        }
        else {
            more_translations = new ArrayList<String>();
        }
        if ((refDB != null) && (refDB.getTranslations(hanzi, solution.pinyin) != null)) {
            more_translations.addAll(refDB.getTranslations(hanzi, solution.pinyin).english);
        }

        showing_more_voc=false;
        display_test();
    }

    @Override
    protected void display_test() {
        ArrayList<String> bare_translations = new ArrayList<String>();

        setContentView(R.layout.guess_chinese_word);
        EditText word_guess = (EditText) findViewById(R.id.guess_chinese_word_guess);
        word_guess.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                if (i == EditorInfo.IME_ACTION_DONE)
                    handle_ok(textView);
                return true;
            }
        });
        word_guess.setText("");
        word_guess.requestFocus();

        for (String s : translations) {
            bare_translations.add(HanziDB.bare_translation(s));
        }
        for (int j=more_translations.size()-1; j>=0; j--) {
            if (bare_translations.contains(HanziDB.bare_translation(more_translations.get(j))))
                more_translations.remove(j);
        }

        TextView text = (TextView) findViewById(R.id.guess_chinese_word_voc);
        text.setText("");
        for (int i=0; i<translations.size(); i++) {
            text.setText(text.getText()+translations.get(i));
            if (i<translations.size()-1)
                text.setText(text.getText()+"\n");
        }
        if (!showing_more_voc) {
            TextView more_text = (TextView) findViewById(R.id.guess_chinese_word_more_voc);
            more_text.setText("");

            ScrollView scroll = (ScrollView) findViewById(R.id.guess_chinese_word_more_voc_scroll);
            scroll.setVisibility(View.GONE);

            Button more = (Button) findViewById(R.id.guess_chinese_word_more_voc_button);
            View more_spacer = findViewById(R.id.guess_chinese_word_more_spacer);
            if (more_translations.size() == 0) {
                more.setVisibility(View.GONE);
                more_spacer.setVisibility(View.GONE);
            }
            else {
                more.setVisibility(View.VISIBLE);
                more_spacer.setVisibility(View.VISIBLE);
            }
        }
        else {
            more_voc(null);
        }
        
        previous_guess = new ArrayList<String>();
        TextView previous = (TextView) findViewById(R.id.guess_chinese_word_previous_guess);
        previous.setText("");

        EditText guess = (EditText) findViewById(R.id.guess_chinese_word_guess);
        StringBuffer hint = new StringBuffer();
        for (int i=0; i<solution.chinese.codePointCount(0, solution.chinese.length()); i++) {
            hint.append("_ ");
        }
        guess.setHint(hint.toString());
    }

    protected int check_guess(String guess) {
        if (guess.equals(solution.chinese)) {
            return CORRECT;
        }
        else {
            // check if the word guessed is in the database and relates somewhat...
            ArrayList<Vocabulary> guess_translations = myDB.getTranslations(guess);
            if (refDB != null) {
                guess_translations.addAll(refDB.getTranslations(guess));
            }

            for (String hint:solution.english) {
                for(Vocabulary voc : guess_translations) {
                    for (String en : voc.english) {
                        if (HanziDB.compare_translations(en, hint)) {
                            if ((solution.chinese.contains(guess)) || (guess.contains(solution.chinese))) {
                                return OTHER;
                            }
                            if (!previous_guess.contains(guess))
                                previous_guess.add(guess);
                            TextView previous = (TextView) findViewById(R.id.guess_chinese_word_previous_guess);
                            previous.setText("Previous try:\n");
                            for (int i=0; i<previous_guess.size(); i++) {
                                previous.setText(previous.getText() + previous_guess.get(i));
                                if (i<previous_guess.size()-1)
                                    previous.setText(previous.getText() + ",\n");
                            }
                            previous.invalidate();
                            return OTHER;
                        }
                    }
                }
            }
        }

        return INCORRECT;
    }

    protected void update_scores(int good) {
        int min_box = -1;

        for (String tries : previous_guess) {
            myDB.resetTime(tries);
        }

        if (good == CORRECT) {
            for (int i=0; i<solution.chinese.length();) {
                int new_box = myDB.getBoxForChar(solution.chinese.substring(i, i+Character.charCount(solution.chinese.codePointAt(i))));
                if ((min_box == -1) || (new_box < min_box)) {
                    min_box = new_box;
                }

                i+=Character.charCount(solution.chinese.codePointAt(i));
            }
            for (int i=0; i<solution.chinese.length();) {
                String hanzi = solution.chinese.substring(i, i+Character.charCount(solution.chinese.codePointAt(i)));
                int box = myDB.getBoxForChar(hanzi);
                if (box == min_box) {
                    myDB.betterBox(hanzi);
                }
                else {
                    myDB.resetTime(hanzi);
                }

                i+=Character.charCount(solution.chinese.codePointAt(i));
            }
            myDB.betterBox(solution.chinese);
        }
        else if (good == INCORRECT) {
            for (int i=0; i<solution.chinese.length();) {
                String hanzi = solution.chinese.substring(i, i+Character.charCount(solution.chinese.codePointAt(i)));
                myDB.worseBox(hanzi);
                i+=Character.charCount(solution.chinese.codePointAt(i));
            }
            myDB.restartStudy(solution.chinese);
        }
        else if (good == OTHER) {
            for (int i=0; i<solution.chinese.length();) {
                String hanzi = solution.chinese.substring(i, i+Character.charCount(solution.chinese.codePointAt(i)));
                myDB.resetTime(hanzi);
                i+=Character.charCount(solution.chinese.codePointAt(i));
            }
        }
    }

    public void more_voc(@Nullable View v) {
        Button more = (Button) findViewById(R.id.guess_chinese_word_more_voc_button);
        more.setVisibility(View.GONE);

        TextView more_text = (TextView) findViewById(R.id.guess_chinese_word_more_voc);
        more_text.setText("");
        for (int i=0; i<more_translations.size(); i++) {
            more_text.setText(more_text.getText()+more_translations.get(i));
            if (i<more_translations.size()-1)
                more_text.setText(more_text.getText()+"\n");
        }
        ScrollView scroll = (ScrollView) findViewById(R.id.guess_chinese_word_more_voc_scroll);
        scroll.setVisibility(View.VISIBLE);
        showing_more_voc=true;
    }

    public void handle_ok(View v) {
        EditText word_guess = (EditText) findViewById(R.id.guess_chinese_word_guess);
        if (word_guess==null)
            return;
        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(word_guess.getWindowToken(), 0);
        
        String guess = word_guess.getText().toString();

        if (handle_submit(guess) == OTHER) {
            word_guess.setText("");
            if (!previous_guess.contains(guess)) {
                show_or_next(guess);
            }
        }
    }

    @Override
    protected void show_solution(String guess, int good) {
        super.show_solution(guess, good);
        Details.populate_details_view(GuessChineseWord.this, (ViewGroup) findViewById(R.id.test_results), solution.chinese, myDB);
        if ((good == INCORRECT) && (!guess.equals(""))) {
            LinearLayout bad_guess = (LinearLayout) findViewById(R.id.test_result_wrong_guess);
            TextView bad_guess_text = new TextView(GuessChineseWord.this);
            bad_guess_text.setText("You guessed: "+guess);
            bad_guess_text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
            bad_guess_text.setTextColor(0xffdd0000);
            bad_guess_text.setGravity(Gravity.CENTER_HORIZONTAL);
            bad_guess.addView(bad_guess_text, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            FrameLayout bad_guess_details = new FrameLayout(this);
            Details.populate_details_view(GuessChineseWord.this, bad_guess_details, guess, refDB==null?myDB:refDB);
            bad_guess.addView(bad_guess_details, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            (findViewById(R.id.test_result_wrong)).setVisibility(View.VISIBLE);
            (findViewById(R.id.test_result_accept)).setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void close_solution() {
        super.close_solution();

        final LinearLayout bad_guess = (LinearLayout) findViewById(R.id.test_result_wrong_guess);
        ListView list= (ListView) bad_guess.findViewById(R.id.details_list);
        if (list != null) {
            VocabularyCursorAdapter adapter = (VocabularyCursorAdapter) list.getAdapter();
            if (adapter != null) {
                adapter.changeCursor(null);
            }
        }
    }
}
