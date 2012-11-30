/******************************
 * Activity to guess a Chinese word based on the English translations
 */
package com.matthieu;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;

public class GuessChineseWord extends TestFragment {
    private Vocabulary solution;
    public ArrayList<String> translations, more_translations,previous_guess;
    private boolean showing_more_voc;

    private final String TAG="GuessChineseWord";

    @Override
    protected boolean set_hanzi_to_test(String hanzi) {
        super.set_hanzi_to_test(hanzi);
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
        if ((refDB != null) && (refDB.getTranslations(hanzi, solution.pinyin) != null)) {
            more_translations = refDB.getTranslations(hanzi, solution.pinyin).english;
        } else {
            more_translations = new ArrayList<String>();
        }

        showing_more_voc=false;

        Log.d(TAG, "GuessChineseWord OK");
        return true;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.guess_chinese_word, container, false);
    }

    @Override
    public void onResume() {
        super.onResume();
        update_test_view(getView());
    }

    @Override
    public void update_test_view(View v) {
        if (v==null)
            return;

        ArrayList<String> bare_translations = new ArrayList<String>();

        EditText word_guess = (EditText) v.findViewById(R.id.test_guess);
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

        EditText guess = (EditText) v.findViewById(R.id.test_guess);
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

    public void handle_ok(View v) {
        EditText word_guess = (EditText) getView().findViewById(R.id.test_guess);
        if (word_guess==null)
            return;

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
                GuessChineseWordResultFragment solution_fragment = new GuessChineseWordResultFragment();
                Bundle bundle = new Bundle();
                bundle.putInt("good", correctness);
                bundle.putString("answer", guess);
                bundle.putString("question", hanzi);
                bundle.putStringArrayList("previous_guess", previous_guess);
                solution_fragment.setArguments(bundle);
                ((TestActivity)getActivity()).new_solution_fragment(solution_fragment, correctness);
                break;
        }
    }

    public static class GuessChineseWordResultFragment extends TestFragment.SolutionFragment {
        ArrayList<String> previous_guess;

        @Override
        protected void get_arguments() {
            super.get_arguments();
            Bundle bundle = getArguments();
            if (bundle != null) {
                previous_guess = bundle.getStringArrayList("previous_guess");
            }
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            return show_details(inflater,container, true);
        }

        @Override
        public void onDestroyView() {
            hide_details((ViewGroup) getView());
            super.onDestroyView();
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
        }
    }
}
