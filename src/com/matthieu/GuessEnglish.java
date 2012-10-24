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
import android.widget.EditText;
import android.widget.TextView;
import java.util.ArrayList;

public class GuessEnglish extends TestFragment {
    private Vocabulary solution=null;

    private final String TAG="GuessEnglish";

    public GuessEnglish(TestActivity testActivity) {
        super(testActivity);
    }

    @Override
    protected boolean set_hanzi_to_test(String hanzi) {
        super.set_hanzi_to_test(hanzi);

        ArrayList<Vocabulary> voc = myDB.getFilteredTranslations(hanzi);
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
        Log.d(TAG, "GuessEnglish OK");

        return true;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View res = inflater.inflate(R.layout.guess_english, container, false);
        update_test_view(res);
        return res;
    }

    @Override
    public void update_test_view(View v) {
        if (v==null)
            return;

        EditText word_guess = (EditText) v.findViewById(R.id.guess_english_word_guess);
        word_guess.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                if (i == EditorInfo.IME_ACTION_DONE)
                    handle_ok(textView);
                return true;
            }
        });
        word_guess.setText("");
        word_guess.requestFocus();

        TextView text = (TextView) v.findViewById(R.id.guess_english_word_voc);
        text.setText(solution.chinese);
        v.invalidate();
    }

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

    public boolean handle_ok(View v) {
        EditText word_guess = (EditText) getView().findViewById(R.id.guess_english_word_guess);
        if (word_guess==null)
            return false;
        InputMethodManager imm = (InputMethodManager)getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(word_guess.getWindowToken(), 0);

        String guess = word_guess.getText().toString();

        int correctness = check_guess(guess);
        switch(correctness) {
            case OTHER:
                word_guess.setText("");
                break;
            case CORRECT:
            case INCORRECT:
                GuessEnglishResultFragment solution_fragment = new GuessEnglishResultFragment(hanzi, guess, hanzi, correctness);
                ((TestActivity)getActivity()).new_solution_fragment(solution_fragment, correctness);
                break;
        }
        return true;
    }

    private class GuessEnglishResultFragment extends SolutionFragment {

        public GuessEnglishResultFragment(String question, String answer, String hanzi, int good) {
            super(question, answer, hanzi, good);
        }

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
                myDB.betterBox(hanzi);
                int word_box = myDB.getBoxForChar(hanzi);
                for (int i=0; i<hanzi.length();) {
                    int new_box = myDB.getBoxForChar(hanzi.substring(i, i+Character.charCount(hanzi.codePointAt(i))));
                    if (new_box < min_box) {
                        min_box = new_box;
                    }

                    i+=Character.charCount(hanzi.codePointAt(i));
                }
                for (int i=0; i<hanzi.length();) {
                    String character = hanzi.substring(i, i+Character.charCount(hanzi.codePointAt(i)));
                    int box = myDB.getBoxForChar(character);
                    if ((box == min_box) && (min_box < word_box)) {
                        myDB.betterBox(character);
                    }
                    else {
                        myDB.resetTime(character);
                    }

                    i+=Character.charCount(hanzi.codePointAt(i));
                }
            }
            else {
                for (int i=0; i<hanzi.length();) {
                    String character = hanzi.substring(i, i+Character.charCount(hanzi.codePointAt(i)));
                    myDB.worseBox(character);
                    i+=Character.charCount(hanzi.codePointAt(i));
                }
                myDB.worseBox(hanzi);
            }
        }
    }
}
