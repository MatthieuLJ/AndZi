/******************************
 * Activity to test guessing a Chinese character
 */
package com.matthieu;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.util.ArrayList;
import java.util.Collections;

public class GuessMissingChar extends Test implements TextWatcher {
    private String current_test;
    private ArrayList<Vocabulary> words=new ArrayList<Vocabulary>();
    private ArrayList<String> previous_guess=new ArrayList<String>();

    private final String TAG="GuessMissingChar";

    protected boolean set_hanzi_to_test(String hanzi) {
        words = myDB.getWordsWithPinyin(hanzi);
        for (int i=words.size()-1; i>=0; i--) {
            Vocabulary w = words.get(i);
            if (w.chinese.equals(hanzi) || w.chinese.equals(hanzi+hanzi) || w.chinese.equals(hanzi+"子") || w.chinese.equals(hanzi+"儿") || (w.chinese.replace(hanzi, "").equals("")))
                words.remove(i);
        }
        if (words.size() == 0) {
            return false;
        }

        current_test = hanzi;
        Log.d(TAG, "GuessMissingChar OK");

        return true;
    }

    @Override
    View get_view() {
        LayoutInflater inflater = activity.getLayoutInflater();
        View res = inflater.inflate(R.layout.guess_missing_char, null);

        EditText guess = (EditText) res.findViewById(R.id.missing_test_guess);
        guess.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                if (i == EditorInfo.IME_ACTION_DONE)
                    check_answer();
                return true;
            }
        });
        guess.setText("");
        guess.addTextChangedListener(this);
        /*guess.requestFocus();
        InputMethodManager imm = (InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInputFromInputMethod(guess.getWindowToken(), InputMethodManager.SHOW_IMPLICIT);*/

        TextView previous = (TextView) res.findViewById(R.id.char_finder_previous_guess);
        previous.setText("");
        previous_guess.clear();

        if(words.size()>5) {
            Collections.shuffle(words);
            words = new ArrayList<Vocabulary>(words.subList(0,5));
        }

        Log.d(TAG, "Testing for character " + current_test + " got " + words.size() + " words");

        TableLayout tl = (TableLayout) res.findViewById(R.id.char_finder_word_grid);
        tl.removeAllViews();
        TableRow.LayoutParams trlp;
        for (Vocabulary w : words) {
            TableRow tr = new TableRow(activity);

            TextView chinese = new TextView(activity);
            chinese.setTextAppearance(activity, android.R.style.TextAppearance_Medium_Inverse);
            chinese.setClickable(false);
            chinese.setText(w.chinese.replaceAll(current_test, "\\?"));
            trlp = new TableRow.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            trlp.setMargins(5,5,5,5);
            chinese.setLayoutParams(trlp);
            tr.addView(chinese);

            TextView trans = new TextView(activity);
            trans.setTextAppearance(activity, android.R.style.TextAppearance_Medium_Inverse);
            trans.setClickable(false);
            String en="";
            for (int j=0; j<w.english.size(); j++) {
                en += w.english.get(j);
                if (j<w.english.size()-1)
                    en += ", ";
            }
            trans.setText(en);
            trlp = new TableRow.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            trlp.setMargins(5,5,5,5);
            trans.setLayoutParams(trlp);
            tr.addView(trans);

            TableLayout.LayoutParams tlp = new TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT);
            tr.setLayoutParams(tlp);
            tl.addView(tr);
        }

        Button submit_answer = (Button) res.findViewById(R.id.guess_missing_char_ok);
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
        EditText guess = (EditText) v.findViewById(R.id.missing_test_guess);
        guess.setOnEditorActionListener(null);
        guess.removeTextChangedListener(this);
        InputMethodManager imm = (InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(guess.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
    }


    protected int check_guess(String guess) {
        int res=INCORRECT;

        if (guess.equals(current_test)) {
            res=CORRECT;
        }
        else {
            //check if that character could have been good also...
            if (guess.length()==1) {
                res=OTHER;
                for (Vocabulary w:words) {
                    String tmp_word = w.chinese;
                    tmp_word = tmp_word.replaceAll(current_test, guess);

                    ArrayList<String> tmp_translations=new ArrayList<String>();
                    for (Vocabulary tmp_v:myDB.getTranslations(tmp_word)) {
                        tmp_translations.addAll(tmp_v.english);
                    }
                    if (refDB != null) {
                        for (Vocabulary tmp_v:refDB.getTranslations(tmp_word)) {
                            tmp_translations.addAll(tmp_v.english);
                        }
                    }
                    boolean found = false;
                    if (tmp_translations.size()>0) {
                        for (String en:w.english) {
                            for (String tmp_en:tmp_translations) {
                                if (HanziDB.compare_translations(en, tmp_en))
                                    found=true;
                            }
                        }
                    }
                    if(!found) {
                        res=INCORRECT;
                        break;
                    }
                }
            }
        }

        if (res == OTHER) {
                if (!previous_guess.contains(guess))
                    previous_guess.add(guess);
                TextView previous = (TextView) activity.findViewById(R.id.char_finder_previous_guess);
                EditText edit_guess = (EditText) activity.findViewById(R.id.missing_test_guess);
                previous.setText("Previous try:\n");
                for (int i=0; i<previous_guess.size(); i++) {
                    previous.setText(previous.getText() + previous_guess.get(i));
                    if (i<previous_guess.size()-1)
                        previous.setText(previous.getText() + ",\n");
                }
                edit_guess.setText("");
                previous.invalidate();
        }

        return res;
    }

    public void check_answer() {
        EditText word_guess = (EditText) activity.findViewById(R.id.missing_test_guess);
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
                GuessMissinCharResultFragment solution_fragment = new GuessMissinCharResultFragment();
                Bundle bundle = new Bundle();
                bundle.putInt("good", correctness);
                bundle.putString("answer", guess);
                bundle.putString("question", current_test);
                bundle.putParcelableArrayList("words", words);
                solution_fragment.setArguments(bundle);
                ((TestActivity)activity).new_solution_fragment(solution_fragment, correctness);
                break;
        }
    }

    public static class GuessMissinCharResultFragment extends Test.SolutionFragment {
        ArrayList<Vocabulary> words;

        @Override
        protected void get_arguments() {
            super.get_arguments();
            Bundle bundle = getArguments();
            if (bundle != null) {
                words = bundle.getParcelableArrayList("words");
            }
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
            if (good==CORRECT) {
                myDB.betterBox(question);
            }
            else {
                myDB.worseBox(question);
            }

            // reset the time for all characters displayed or restart study
            for (Vocabulary v:words) {
                for (int i=0; i<v.chinese.length();) {
                    String other_hanzi = v.chinese.substring(i, i+Character.charCount(v.chinese.codePointAt(i)));
                    i+=Character.charCount(v.chinese.codePointAt(i));
                    if (other_hanzi.equals(question))
                        continue;
                    if (good==CORRECT) {
                        myDB.resetTime(other_hanzi);
                    }
                    else
                        myDB.worseBox(other_hanzi);
                }
            }
        }
    }

    public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
    }

    public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        if (charSequence.toString().codePointCount(0, charSequence.toString().length()) > 1) {
            activity.findViewById(R.id.guess_missing_char_ok).setEnabled(false);
        } else {
            activity.findViewById(R.id.guess_missing_char_ok).setEnabled(true);
        }

    }

    public void afterTextChanged(Editable editable) {
    }
}
