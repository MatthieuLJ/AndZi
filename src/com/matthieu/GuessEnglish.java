/******************************
 * Activity to guess the English translation from the Chinese word
 */
package com.matthieu;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

public class GuessEnglish extends Test {
    private String current_test;
    private Vocabulary solution=null;

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

                if (translations.length > 2)
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
        current_test = hanzi;
        Log.d(TAG, "GuessEnglish OK");

        return true;
    }

    @Override
    View get_view() {
        LayoutInflater inflater = activity.getLayoutInflater();
        View res = inflater.inflate(R.layout.guess_english, null);

        EditText guess = (EditText) res.findViewById(R.id.english_test_guess);
        guess.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                if (i == EditorInfo.IME_ACTION_DONE)
                    check_answer();
                return true;
            }
        });
        guess.setText("");
        /*guess.requestFocus();
        InputMethodManager imm = (InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInputFromInputMethod(guess.getWindowToken(), InputMethodManager.SHOW_IMPLICIT);*/

        TextView text = (TextView) res.findViewById(R.id.guess_english_word_voc);
        text.setText(solution.chinese);

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
    public void kill_view(ViewGroup v) {
        EditText guess = (EditText) v.findViewById(R.id.english_test_guess);
        guess.setOnEditorActionListener(null);
        InputMethodManager imm = (InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(guess.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
    }

    protected int check_guess(String guess) {
        String[] guesses=guess.replaceAll("[^A-Za-z ,]", "").toLowerCase(Locale.ENGLISH).split("[, -]");
        for (int i=0; i<guesses.length; i++) {
            if (guesses[i].equals(""))
                continue;
            Log.d(TAG, "Checking "+guesses[i]);
            guesses[i]=guesses[i].trim();
            for (String en:solution.english) {
                Log.d(TAG, "Against "+en);
                en=en.toLowerCase(Locale.ENGLISH);
                if (en.equals(guesses[i].trim())) {
                    Log.d(TAG, "got a match "+en+" and "+guesses[i].trim());
                    return CORRECT;
                }

                String[] ens=HanziDB.bare_translation(en).split("[ -]");
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
                GuessEnglishResultFragment solution_fragment = new GuessEnglishResultFragment();
                Bundle bundle = new Bundle();
                bundle.putInt("good", correctness);
                bundle.putString("answer", guess);
                bundle.putString("question", current_test);
                solution_fragment.setArguments(bundle);
                ((TestActivity)activity).new_solution_fragment(solution_fragment, correctness);
                break;
        }
    }

    public static class GuessEnglishResultFragment extends Test.SolutionFragment {
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            return show_details(inflater, container, true);
        }

        @Override
        public void onDestroyView() {
            hide_details((ViewGroup) getView());
            super.onDestroyView();
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
        }
    }
}
