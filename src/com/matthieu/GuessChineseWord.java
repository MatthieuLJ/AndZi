/******************************
 * Activity to guess a Chinese word based on the English translations
 */
package com.matthieu;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public class GuessChineseWord extends TestFragment {
    private Vocabulary solution;
    public ArrayList<String> translations, more_translations,previous_guess;
    private boolean showing_more_voc;

    private final String TAG="GuessChineseWord";

    public GuessChineseWord(TestActivity testActivity) {
        super(testActivity);
    }

    @Override
    protected boolean set_hanzi_to_test(String hanzi) {
        super.set_hanzi_to_test(hanzi);
        solution=null;

        if (hanzi.codePointCount(0, hanzi.length())>3) {
            return false;
        }

        // need some translations
        ArrayList<Vocabulary> voc = myDB.getFilteredTranslations(hanzi);
        if (voc.size() == 0) {
            return false;
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

        Log.d(TAG, "GuessChineseWord OK");
        return true;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View res = inflater.inflate(R.layout.guess_chinese_word, container, false);
        update_test_view(res);
        return res;
    }

    @Override
    public void update_test_view(View v) {
        if (v==null)
            return;

        ArrayList<String> bare_translations = new ArrayList<String>();

        EditText word_guess = (EditText) v.findViewById(R.id.guess_chinese_word_guess);
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

        TextView text = (TextView) v.findViewById(R.id.guess_chinese_word_voc);
        text.setText("");
        for (int i=0; i<translations.size(); i++) {
            text.setText(text.getText()+translations.get(i));
            if (i<translations.size()-1)
                text.setText(text.getText()+"\n");
        }
        if (!showing_more_voc) {
            TextView more_text = (TextView) v.findViewById(R.id.guess_chinese_word_more_voc);
            more_text.setText("");

            ScrollView scroll = (ScrollView) v.findViewById(R.id.guess_chinese_word_more_voc_scroll);
            scroll.setVisibility(View.GONE);

            Button more = (Button) v.findViewById(R.id.guess_chinese_word_more_voc_button);
            View more_spacer = v.findViewById(R.id.guess_chinese_word_more_spacer);
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
        TextView previous = (TextView) v.findViewById(R.id.guess_chinese_word_previous_guess);
        previous.setText("");

        EditText guess = (EditText) v.findViewById(R.id.guess_chinese_word_guess);
        StringBuffer hint = new StringBuffer();
        for (int i=0; i<solution.chinese.codePointCount(0, solution.chinese.length()); i++) {
            hint.append("_ ");
        }
        guess.setHint(hint.toString());
        v.invalidate();
    }

    protected int check_guess(String guess) {
        if (guess.equals(solution.chinese)) {
            return CORRECT;
        }
        if (guess.equals(""))
            return INCORRECT;

        if ((solution.chinese.contains(guess)) || (guess.contains(solution.chinese))) {
            return OTHER;
        }

        // check if the word guessed is in the database and relates somewhat...
        ArrayList<Vocabulary> guess_translations = myDB.getTranslations(guess);
        if (refDB != null) {
            guess_translations.addAll(refDB.getTranslations(guess));
        }

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
        Button more = (Button) getView().findViewById(R.id.guess_chinese_word_more_voc_button);
        more.setVisibility(View.GONE);

        TextView more_text = (TextView) getView().findViewById(R.id.guess_chinese_word_more_voc);
        more_text.setText("");
        for (int i=0; i<more_translations.size(); i++) {
            more_text.setText(more_text.getText()+more_translations.get(i));
            if (i<more_translations.size()-1)
                more_text.setText(more_text.getText()+"\n");
        }
        ScrollView scroll = (ScrollView) getView().findViewById(R.id.guess_chinese_word_more_voc_scroll);
        scroll.setVisibility(View.VISIBLE);
        showing_more_voc=true;
    }

    public boolean handle_ok(View v) {
        EditText word_guess = (EditText) getView().findViewById(R.id.guess_chinese_word_guess);
        if (word_guess==null)
            return false;
        InputMethodManager imm = (InputMethodManager)getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(word_guess.getWindowToken(), 0);
        
        String guess = word_guess.getText().toString();

        int correctness = check_guess(guess);
        switch(correctness) {
            case OTHER:
                if (!previous_guess.contains(guess)) {
                    previous_guess.add(guess);
                    TextView previous = (TextView) getView().findViewById(R.id.guess_chinese_word_previous_guess);
                    previous.setText("Previous try:\n");
                    for (int i=0; i<previous_guess.size(); i++) {
                        previous.setText(previous.getText() + previous_guess.get(i));
                        if (i<previous_guess.size()-1)
                            previous.setText(previous.getText() + ",\n");
                    }
                    previous.invalidate();
                }
                word_guess.setText("");
                break;
            case CORRECT:
            case INCORRECT:
                GuessChineseWordResultFragment solution_fragment = new GuessChineseWordResultFragment(hanzi, guess, hanzi, correctness);
                ((TestActivity)getActivity()).new_solution_fragment(solution_fragment, correctness);
                break;
        }
        return true;
    }

    protected class GuessChineseWordResultFragment extends TestFragment.SolutionFragment {
        public GuessChineseWordResultFragment(String question, String answer, String hanzi, int good) {
            super(question, answer, hanzi, good);
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            return show_details(inflater,container, true);
        }

        @Override
        public void update_score() {
            int min_box = -1;

            for (String tries : previous_guess) {
                myDB.resetTime(tries);
            }

            if (good == CORRECT) {
                myDB.betterBox(hanzi);
                int word_box = myDB.getBoxForChar(hanzi);
                for (int i=0; i<hanzi.length();) {
                    int new_box = myDB.getBoxForChar(hanzi.substring(i, i+Character.charCount(hanzi.codePointAt(i))));
                    if ((min_box == -1) || (new_box < min_box)) {
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
            else if (good == INCORRECT) {
                for (int i=0; i<hanzi.length();) {
                    String character = hanzi.substring(i, i+Character.charCount(hanzi.codePointAt(i)));
                    myDB.worseBox(character);
                    i+=Character.charCount(hanzi.codePointAt(i));
                }
                myDB.worseBox(hanzi);
            }
            else if (good == OTHER) {
                for (int i=0; i<hanzi.length();) {
                    String character = hanzi.substring(i, i+Character.charCount(hanzi.codePointAt(i)));
                    myDB.resetTime(character);
                    i+=Character.charCount(hanzi.codePointAt(i));
                }
            }
        }
    }
}
