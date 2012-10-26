/******************************
 * Activity to guess Chinese words containing a particular character
 */
package com.matthieu;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

import java.util.ArrayList;

public class GuessContaining extends TestFragment {
    protected ArrayList<String> correct_answers=new ArrayList<String>();

    private final String TAG="GuessContaining";

    public GuessContaining(TestActivity testActivity) {
        super(testActivity);
    }

    @Override
    protected boolean set_hanzi_to_test(String hanzi) {
        super.set_hanzi_to_test(hanzi);

        ArrayList<Vocabulary> words = myDB.getFilteredWordsWithPinyin(hanzi);
        for (int i=words.size()-1; i>=0; i--) {
            Vocabulary w = words.get(i);
            if (w.chinese.equals(hanzi+"子") || w.chinese.equals(hanzi+"儿") || w.chinese.replace(hanzi, "").equals("") || w.chinese.codePointCount(0, w.chinese.length())>4)
                words.remove(i);
        }
        if (words.size() == 0) {
            return false;
        }
        Log.d(TAG, "GuessContaining OK");

        return true;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View res = inflater.inflate(R.layout.guess_containing, container, false);
        update_test_view(res);
        return res;
    }

    @Override
    public void update_test_view(View v) {
        if (v==null)
            return;

        EditText word_guess = (EditText) v.findViewById(R.id.guess_containing_word_guess);
        word_guess.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                if (i == EditorInfo.IME_ACTION_DONE)
                    handle_ok(textView);
                return true;
            }
        });
        word_guess.setText("");
        word_guess.requestFocus();

        TextView clue= (TextView) v.findViewById(R.id.guess_containing_chinese);
        clue.setText(hanzi);
        v.invalidate();
    }

    protected int check_guess(String guess) {
        int res = INCORRECT;
        correct_answers.clear();
        String[] guesses=guess.split("[,， ]");
        for (int i=0; i<guesses.length; i++) {
            if (guesses[i].equals(""))
                continue;
            Log.d(TAG, "Checking "+guesses[i]);
            guesses[i]=guesses[i].trim();
            if (!guesses[i].contains(hanzi))
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
        return res;
    }

    public boolean handle_ok(View v) {
        EditText word_guess = (EditText) getView().findViewById(R.id.guess_containing_word_guess);
        if (word_guess==null)
            return false;
        InputMethodManager imm = (InputMethodManager)getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(word_guess.getWindowToken(), 0);

        String guess = word_guess.getText().toString();

        int correctness = check_guess(guess);
        GuessContainingResultFragment solution_fragment = new GuessContainingResultFragment(hanzi, guess, hanzi, correctness, correct_answers);
        ((TestActivity)getActivity()).new_solution_fragment(solution_fragment, correctness);
        return true;
    }

    protected class GuessContainingResultFragment extends TestFragment.SolutionFragment {
        ArrayList<String> correct_answers;
        public GuessContainingResultFragment(String question, String answer, String hanzi, int good, ArrayList<String> correct_answers) {
            super(question, answer, hanzi, good);
            this.correct_answers = correct_answers;
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            return show_details(inflater, container, false);
        }

        @Override
        public void onDestroyView() {
            hide_details((ViewGroup) getView());
            super.onDestroyView();
        }

        @Override
        public void update_score() {
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
                        myDB.betterBox(answers);
                    }
                    for (String c:good_chars) {
                        myDB.resetTime(c);
                    }
                    myDB.betterBox(hanzi);
                    break;
                case INCORRECT:
                    myDB.worseBox(hanzi);
                    break;
                case OTHER:
                    myDB.resetTime(hanzi);
                    break;
            }
        }
    }
}
