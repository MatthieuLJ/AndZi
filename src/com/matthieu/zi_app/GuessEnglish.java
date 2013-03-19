/******************************
 * Activity to guess the English translation from the Chinese word
 */
package com.matthieu.zi_app;

import android.content.Context;
import android.util.Log;
import android.util.TypedValue;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import com.matthieu.zi_app.R;

import java.util.*;

public class GuessEnglish extends Test {
    private Vocabulary solution=null;
    private View master_view[]=null;
    private int current_view=0;
    private GuessEnglishResultView solution_view;

    private static final boolean DEBUG = false;
    private final String TAG="GuessEnglish";

    @Override
    protected boolean set_hanzi_to_test(String hanzi) {
        ArrayList<Vocabulary> voc = myDB.getTranslations(hanzi);
        if ((voc.size() == 0) || (voc.size() > 1)) { // one case has multiple pinyins...
            return false;
        }
        do {
            solution = voc.remove((int) (voc.size()*Math.random()));

            // find a translation that's not too complicated... but still enough
            int i,j;
            for (i=0; i<solution.english.size(); i++) {
                String[] translations=HanziDB.bare_translation(solution.english.get(i)).split(" ");

                if (translations.length > 3)
                    // too many words in this translation
                    continue;
                for (j=0; j<translations.length; j++) {
                    if (translations[j].length() > 2)
                        break;
                }
                if (j==translations.length)
                    // only short words (2 or less letters)
                    continue;

                // if we get here, we found something good
                break;
            }
            if (i<solution.english.size())
                break;
        } while(voc.size()>0);

        if (solution == null) {
            return false;
        }
        if (DEBUG) Log.d(TAG, "GuessEnglish OK");

        solution_view = new GuessEnglishResultView(hanzi, activity);
        solution_view.prepare_view(activity.getLayoutInflater());

        return true;
    }

    @Override
    View get_view() {
        if (master_view == null) {
            master_view = new View[2];
            LayoutInflater inflater = activity.getLayoutInflater();
            master_view[0] = inflater.inflate(R.layout.guess_english, null);
            master_view[1] = inflater.inflate(R.layout.guess_english, null);
        }

        current_view = 1-current_view;
        View res = master_view[current_view];

        EditText guess = (EditText) res.findViewById(R.id.english_test_guess);
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

        LinearLayout focusable = (LinearLayout) res.findViewById(R.id.english_test_focusable_linear);
        focusable.requestFocus();

        TextView text = (TextView) res.findViewById(R.id.guess_english_word_voc);
        text.setText(solution.chinese);
        text.setTypeface(((AndZiApplication) activity.getApplication()).get_chinese_font());

        Button submit_answer = (Button) res.findViewById(R.id.guess_english_ok);
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
        EditText guess = (EditText) v.findViewById(R.id.english_test_guess);
        if (guess==null)
            return;
        guess.setOnEditorActionListener(null);
        InputMethodManager imm = (InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        //imm.hideSoftInputFromWindow(guess.getWindowToken(), InputMethodManager.HIDE_IMPLICIT_ONLY);
        imm.hideSoftInputFromWindow(guess.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
    }

    protected int check_guess(String guess) {
        String[] guesses=guess.replaceAll("[^A-Za-z ,]", "").toLowerCase(Locale.ENGLISH).split("[, -']");
        for (int i=0; i<guesses.length; i++) {
            if (guesses[i].equals(""))
                continue;
            if (DEBUG) Log.d(TAG, "Checking "+guesses[i]);
            guesses[i]=guesses[i].trim();
            for (String en:solution.english) {
                if (DEBUG) Log.d(TAG, "Against "+en);
                en=en.toLowerCase(Locale.ENGLISH);
                if (en.equals(guesses[i].trim())) {
                    if (DEBUG) Log.d(TAG, "got a match "+en+" and "+guesses[i].trim());
                    return CORRECT;
                }

                String[] ens=HanziDB.bare_translation(en).split("[ -']");
                for (String item:ens) {
                    String tmp=item.replaceAll("[^A-Za-z]", "");
                    if ((tmp.length()>=2) && (tmp.equals(guesses[i].trim()))) {
                        if (DEBUG) Log.d(TAG, "got a match "+tmp+" and "+guesses[i].trim());
                        return CORRECT;
                    }
                }
            }
        }
        return INCORRECT;
    }

    public void check_answer() {
        EditText word_guess = (EditText) activity.findViewById(R.id.english_test_guess);
        if (word_guess==null)
            return;

        String guess = word_guess.getText().toString();

        int correctness = check_guess(guess);
        switch(correctness) {
            case OTHER:
                word_guess.setText("");
                break;
            case CORRECT:
            case INCORRECT:
                solution_view.set_answer(correctness, guess);
                activity.new_solution_fragment(solution_view);
                break;
        }
    }

    public static class GuessEnglishResultView extends SolutionView {
        public GuessEnglishResultView(String question, TestActivity parent) {
            super(question, parent);
        }

        @Override
        public View get_view(LayoutInflater inflater, ViewGroup container) {
            if ((good == Test.INCORRECT) && (!answer.equals(""))) {
                LinearLayout bad_guess = (LinearLayout) result_view.findViewById(R.id.test_result_wrong_guess);
                TextView bad_guess_text = new TextView(parent_activity);
                bad_guess_text.setText("You guessed: "+answer);
                bad_guess_text.setTextSize(TypedValue.COMPLEX_UNIT_PX, parent_activity.getResources().getDimension(R.dimen.large_test_text));
                bad_guess_text.setTextColor(parent_activity.getResources().getColor(R.color.test_failure));
                bad_guess_text.setGravity(Gravity.CENTER_HORIZONTAL);
                bad_guess.addView(bad_guess_text, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                (result_view.findViewById(R.id.test_result_wrong)).setVisibility(View.VISIBLE);

                (result_view.findViewById(R.id.test_result_accept)).setVisibility(View.VISIBLE);
                (result_view.findViewById(R.id.test_result_accept)).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        handle_good(view);
                    }
                });
            }

            return result_view;
        }

        @Override
        public void kill_view(View v) {
            hide_details((ViewGroup) v);
        }

        @Override
        public void update_score() {
            int min_box = 5;

            if (good == CORRECT) {
                myDB.betterBox(question);
                int word_box = myDB.getBoxForChar(question);
                for (int i=0; i<question.length();) {
                    int new_box = myDB.getBoxForChar(question.substring(i, i+Character.charCount(question.codePointAt(i))));
                    if (new_box < min_box) {
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
            else {
                old_scores = new HashMap<String, saved_score>();
                for (int i=0; i<question.length();) {
                    String character = question.substring(i, i+Character.charCount(question.codePointAt(i)));
                    old_scores.put(character, new saved_score(myDB.getBoxForChar(character), myDB.getExpirationTimeForChar(character)));
                    myDB.worseBox(character);
                    i+=Character.charCount(question.codePointAt(i));
                }
                old_scores.put(question, new saved_score(myDB.getBoxForChar(question), myDB.getExpirationTimeForChar(question)));
                myDB.worseBox(question);
            }
            super.update_score();
        }
    }
}
