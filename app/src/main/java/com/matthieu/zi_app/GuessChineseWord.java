/******************************
 * Activity to guess a Chinese word based on the English translations
 */
package com.matthieu.zi_app;

import android.content.Context;
import android.util.Log;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import com.matthieu.zi_app.R;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;

public class GuessChineseWord extends Test {
    private Vocabulary solution;
    public ArrayList<String> translations, more_translations,previous_guess;
    private boolean showing_more_voc;
    private View[] master_view=null;
    private int current_view=0;
    private GuessChineseWordResultView solution_view;

    private static final boolean DEBUG = false;
    private final String TAG="GuessChineseWord";

    @Override
    public boolean set_hanzi_to_test(String hanzi) {
        solution=null;

        if (hanzi.codePointCount(0, hanzi.length())>3) {
            return false;
        }

        // need some translations
        ArrayList<Vocabulary> voc = myDB.getTranslations(hanzi);
        if (voc.size() == 0) {
            return false;
        }
        solution = voc.get((int) (voc.size()*Math.random()));

        translations = solution.english;
        if (refDB.getTranslations(hanzi, solution.pinyin) != null) {
            more_translations = refDB.getTranslations(hanzi, solution.pinyin).english;
        } else {
            more_translations = new ArrayList<String>();
        }

        showing_more_voc=false;

        if (DEBUG) Log.d(TAG, "GuessChineseWord OK");

        solution_view = new GuessChineseWordResultView(hanzi, activity);
        solution_view.prepare_view(activity.getLayoutInflater());
        return true;
    }

    @Override
    View get_view() {
        if (master_view == null) {
            master_view = new View[2];
            LayoutInflater inflater = activity.getLayoutInflater();
            master_view[0] = inflater.inflate(R.layout.guess_chinese_word, null);
            master_view[1] = inflater.inflate(R.layout.guess_chinese_word, null);
        }

        current_view = 1-current_view;
        View res = master_view[current_view];

        ArrayList<String> bare_translations = new ArrayList<String>();

        EditText guess = res.findViewById(R.id.chinese_word_test_guess);
        guess.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                if (i == EditorInfo.IME_ACTION_DONE)
                    check_answer();
                return true;
            }
        });
        guess.setText("");
        guess.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                InputMethodManager imm = (InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.showSoftInputFromInputMethod(view.getWindowToken(), InputMethodManager.SHOW_IMPLICIT);
                return false;
            }
        });

        /*guess.requestFocus();
        InputMethodManager imm = (InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInputFromInputMethod(guess.getWindowToken(), InputMethodManager.SHOW_IMPLICIT);*/
        LinearLayout focusable = res.findViewById(R.id.chinese_work_focusable_linear);
        focusable.requestFocus();

        for (String s : translations) {
            bare_translations.add(HanziDB.bare_translation(s));
        }
        for (int j=more_translations.size()-1; j>=0; j--) {
            if (bare_translations.contains(HanziDB.bare_translation(more_translations.get(j))))
                more_translations.remove(j);
        }

        TextView text = res.findViewById(R.id.guess_chinese_word_voc);
        text.setText("");
        for (int i=0; i<translations.size(); i++) {
            text.setText(text.getText()+translations.get(i));
            if (i<translations.size()-1)
                text.setText(text.getText()+"\n");
        }
        if (!showing_more_voc) {
            TextView more_text = res.findViewById(R.id.guess_chinese_word_more_voc);
            more_text.setText("");
            more_text.setVisibility(View.GONE);

            Button more = res.findViewById(R.id.guess_chinese_word_more_voc_button);
            View more_spacer = res.findViewById(R.id.guess_chinese_word_more_spacer);
            if (more_translations.size() == 0) {
                more.setVisibility(View.GONE);
                more_spacer.setVisibility(View.GONE);
            }
            else {
                more.setVisibility(View.VISIBLE);
                more_spacer.setVisibility(View.VISIBLE);
                more.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        more_voc(view);
                    }
                });
            }
        }
        else {
            more_voc(null);
        }

        previous_guess = new ArrayList<String>();
        TextView previous = res.findViewById(R.id.guess_chinese_word_previous_guess);
        previous.setText("");
        previous.setVisibility(View.GONE);

        StringBuilder hint = new StringBuilder();
        for (int i=0; i<solution.chinese.codePointCount(0, solution.chinese.length()); i++) {
            hint.append("_ ");
        }
        guess.setHint(hint.toString());

        Button submit_answer = res.findViewById(R.id.guess_chinese_word_ok);
        submit_answer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                check_answer();
            }
        });

        return res;
    }

    @Override
    public void kill_view(View v) {
        EditText guess = v.findViewById(R.id.chinese_word_test_guess);
        if (guess==null)
            return;
        guess.setOnEditorActionListener(null);
        InputMethodManager imm = (InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        //imm.hideSoftInputFromWindow(guess.getWindowToken(), InputMethodManager.HIDE_IMPLICIT_ONLY);
        imm.hideSoftInputFromWindow(guess.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
    }

    protected int check_guess(String guess) {
        if (guess.equals(solution.chinese)) {
            return CORRECT;
        }
        if (guess.equals("")) {
            return INCORRECT;
        }
        if ((solution.chinese.contains(guess)) || (guess.contains(solution.chinese))) {
            return OTHER;
        }

        // check if the word guessed is in the database and relates somewhat...
        ArrayList<Vocabulary> guess_translations = myDB.getTranslations(guess);
        guess_translations.addAll(refDB.getTranslations(guess));

        for (String hint:solution.english) {
            for(Vocabulary voc : guess_translations) {
                for (String en : voc.english) {
                    if (HanziDB.compare_translations(en, hint)) {
                        return OTHER;
                    }
                }
            }
        }

        return INCORRECT;
    }

    public void more_voc(@Nullable View v) {
        Button more = activity.findViewById(R.id.guess_chinese_word_more_voc_button);
        more.setVisibility(View.GONE);

        TextView more_text = activity.findViewById(R.id.guess_chinese_word_more_voc);
        more_text.setText("");
        for (int i=0; i<more_translations.size(); i++) {
            more_text.setText(more_text.getText()+more_translations.get(i));
            if (i<more_translations.size()-1)
                more_text.setText(more_text.getText()+"\n");
        }
        more_text.setVisibility(View.VISIBLE);
        showing_more_voc=true;
    }

    void check_answer() {
        EditText word_guess = activity.findViewById(R.id.chinese_word_test_guess);
        if (word_guess==null)
            return;

        String guess = word_guess.getText().toString();

        int correctness = check_guess(guess);
        switch(correctness) {
            case OTHER:
                if (!previous_guess.contains(guess)) {
                    previous_guess.add(guess);
                    TextView previous = activity.findViewById(R.id.guess_chinese_word_previous_guess);
                    previous.setText("Previous try:\n");
                    for (int i=0; i<previous_guess.size(); i++) {
                        previous.setText(previous.getText() + previous_guess.get(i));
                        if (i<previous_guess.size()-1)
                            previous.setText(previous.getText() + ",\n");
                    }
                    previous.setTypeface(((AndZiApplication) activity.getApplication()).get_chinese_font());
                    previous.setVisibility(View.VISIBLE);
                    previous.invalidate();
                }
                word_guess.setText("");
                break;
            case CORRECT:
            case INCORRECT:
                solution_view.set_answer(correctness, guess, previous_guess);
                activity.new_solution_fragment(solution_view);
                break;
        }
    }

    public static class GuessChineseWordResultView extends SolutionView {
        ArrayList<String> previous_guess;

        public GuessChineseWordResultView(String question, TestActivity parent) {
            super(question, parent);
        }

        public void set_answer(int good, String answer, ArrayList<String> previous) {
            super.set_answer(good, answer);
            this.previous_guess = previous;
        }

        @Override
        public View get_view(LayoutInflater inflater, ViewGroup container) {
            return show_solution(true);
        }

        @Override
        public void kill_view(View v) {
            hide_details((ViewGroup) v);
        }

        @Override
        public void update_score() {
            int min_box = -1;

            for (String tries : previous_guess) {
                myDB.resetTime(tries);
            }

            if (good == CORRECT) {
                myDB.betterBox(question);
                int word_box = myDB.getBoxForChar(question);
                for (int i=0; i<question.length();) {
                    int new_box = myDB.getBoxForChar(question.substring(i, i+Character.charCount(question.codePointAt(i))));
                    if ((min_box == -1) || (new_box < min_box)) {
                        min_box = new_box;
                    }

                    i+=Character.charCount(question.codePointAt(i));
                }
                for (int i=0; i<question.length();) {
                    String character = question.substring(i, i+Character.charCount(question.codePointAt(i)));
                    int box = myDB.getBoxForChar(character);
                    if ((box == min_box) && (min_box < word_box)) {
                        myDB.betterBox(character);
                    }
                    else {
                        myDB.resetTime(character);
                    }

                    i+=Character.charCount(question.codePointAt(i));
                }
            }
            else if (good == INCORRECT) {
                old_scores = new HashMap<String, saved_score>();
                if (previous_guess.size()==0)
                    for (int i=0; i<question.length();) {
                        String character = question.substring(i, i+Character.charCount(question.codePointAt(i)));
                        old_scores.put(character, new saved_score(myDB.getBoxForChar(character), myDB.getExpirationTimeForChar(character)));
                        myDB.worseBox(character);
                        i+=Character.charCount(question.codePointAt(i));
                    }
                old_scores.put(question, new saved_score(myDB.getBoxForChar(question), myDB.getExpirationTimeForChar(question)));
                myDB.worseBox(question);
            }
            else if (good == OTHER) {
                for (int i=0; i<question.length();) {
                    String character = question.substring(i, i+Character.charCount(question.codePointAt(i)));
                    myDB.resetTime(character);
                    i+=Character.charCount(question.codePointAt(i));
                }
            }
            super.update_score();
        }
    }
}
