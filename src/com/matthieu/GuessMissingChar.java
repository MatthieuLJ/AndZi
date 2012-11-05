/******************************
 * Activity to test guessing a Chinese character
 */
package com.matthieu;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import java.util.ArrayList;

public class GuessMissingChar extends TestFragment implements TextWatcher {
    private ArrayList<Vocabulary> words=new ArrayList<Vocabulary>();
    private ArrayList<String> previous_guess=new ArrayList<String>();

    private final String TAG="GuessMissingChar";

    protected boolean set_hanzi_to_test(String hanzi) {
        super.set_hanzi_to_test(hanzi);

        words = myDB.getFilteredWordsWithPinyin(hanzi);
        for (int i=words.size()-1; i>=0; i--) {
            Vocabulary w = words.get(i);
            if (w.chinese.equals(hanzi) || w.chinese.equals(hanzi+hanzi) || w.chinese.equals(hanzi+"子") || w.chinese.equals(hanzi+"儿") || (w.chinese.replace(hanzi, "").equals("")))
                words.remove(i);
        }
        if (words.size() == 0) {
            return false;
        }

        Log.d(TAG, "GuessMissingChar OK");

        return true;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.guess_missing_char, container, false);
    }

    @Override
    public void onResume() {
        super.onResume();
        update_test_view(getView());
    }

    @Override
    public void onDestroyView() {
        EditText guess = (EditText) getView().findViewById(R.id.test_guess);
        guess.setOnEditorActionListener(null);
        guess.removeTextChangedListener(this);
        super.onDestroyView();
    }

    @Override
    public void update_test_view(View v) {
        if (v==null)
            return;

        EditText guess = (EditText) v.findViewById(R.id.test_guess);
        guess.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                if (i == EditorInfo.IME_ACTION_DONE)
                    handle_ok(textView);
                return true;
            }
        });
        guess.removeTextChangedListener(this);
        guess.setText("");
        guess.addTextChangedListener(this);
        guess.requestFocus();

        TextView previous = (TextView) v.findViewById(R.id.char_finder_previous_guess);
        previous.setText("");
        previous_guess.clear();

        while(words.size()>5) {
            words.remove(5);
        }

        Log.d(TAG, "Testing for character "+hanzi+" got "+words.size()+" words");

        TableLayout tl = (TableLayout) v.findViewById(R.id.char_finder_word_grid);
        tl.removeAllViews();
        TableRow.LayoutParams trlp;
        for (Vocabulary w : words) {
            TableRow tr = new TableRow(getActivity());

            TextView chinese = new TextView(getActivity());
            chinese.setTextAppearance(getActivity(), android.R.style.TextAppearance_Medium_Inverse);
            chinese.setClickable(false);
            chinese.setText(w.chinese.replaceAll(hanzi, "\\?"));
            trlp = new TableRow.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            trlp.setMargins(5,5,5,5);
            chinese.setLayoutParams(trlp);
            tr.addView(chinese);

            TextView trans = new TextView(getActivity());
            trans.setTextAppearance(getActivity(), android.R.style.TextAppearance_Medium_Inverse);
            trans.setClickable(false);
            String en="";
            for (int j=0; j<w.english.size(); j++) {
                en += w.english.get(j);
                if (j<w.english.size()-1)
                    en += ", ";
            }
            trans.setText(en);
            trlp = new TableRow.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            trlp.setMargins(5,5,5,5);
            trans.setLayoutParams(trlp);
            tr.addView(trans);

            TableLayout.LayoutParams tlp = new TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT);
            tr.setLayoutParams(tlp);
            tl.addView(tr);
        }
        //tl.invalidate();
        v.invalidate();
    }

    protected int check_guess(String guess) {
        int res=INCORRECT;

        if (guess.equals(hanzi)) {
            res=CORRECT;
        }
        else {
            //check if that character could have been good also...
            if (guess.length()==1) {
                res=OTHER;
                for (Vocabulary w:words) {
                    String tmp_word = w.chinese;
                    tmp_word = tmp_word.replaceAll(hanzi, guess);

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
                TextView previous = (TextView) getView().findViewById(R.id.char_finder_previous_guess);
                EditText edit_guess = (EditText) getView().findViewById(R.id.test_guess);
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

    public boolean handle_ok(View v) {
        EditText word_guess = (EditText) getView().findViewById(R.id.test_guess);
        if (word_guess==null)
            return false;

        String guess = word_guess.getText().toString();

        int correctness = check_guess(guess);
        switch(correctness) {
            case OTHER:
                word_guess.setText("");
                break;
            case CORRECT:
            case INCORRECT:
                GuessMissinCharResultFragment solution_fragment = new GuessMissinCharResultFragment(hanzi, guess, hanzi, words, correctness);
                ((TestActivity)getActivity()).new_solution_fragment(solution_fragment, correctness);
                break;
        }
        return true;
    }

    protected static class GuessMissinCharResultFragment extends SolutionFragment {
        ArrayList<Vocabulary> words;
        public GuessMissinCharResultFragment(String question, String answer, String hanzi, ArrayList<Vocabulary> words, int good) {
            super(question, answer, hanzi, good);
            this.words = words;
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
                myDB.betterBox(hanzi);
            }
            else {
                myDB.worseBox(hanzi);
            }

            // reset the time for all characters displayed or restart study
            for (Vocabulary v:words) {
                for (int i=0; i<v.chinese.length();) {
                    String other_hanzi = v.chinese.substring(i, i+Character.charCount(v.chinese.codePointAt(i)));
                    i+=Character.charCount(v.chinese.codePointAt(i));
                    if (other_hanzi.equals(hanzi))
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
            getView().findViewById(R.id.ok).setEnabled(false);
        } else {
            getView().findViewById(R.id.ok).setEnabled(true);
        }

    }

    public void afterTextChanged(Editable editable) {
    }
}
