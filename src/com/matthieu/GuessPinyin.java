/******************************
 * Activity to guess the pinyin of a character
 */
package com.matthieu;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

import java.util.ArrayList;

public class GuessPinyin extends Test implements TextWatcher {
    private String current_test;
    private String pinyin;
    private ArrayList<Vocabulary> words;
    private boolean show_words=false;

    private static final String TAG="GuessPinyin";

    @Override
    protected boolean set_hanzi_to_test(String hanzi) {
        show_words=false;

        ArrayList<String> pinyins = myDB.getPinyinForChar(hanzi);

        if ((pinyins.size() > 1) && (!myDB.is_char_toneless_pair(hanzi))) {
            show_words=true;

            ArrayList<Pinyin> pins=new ArrayList<Pinyin>();
            // remove the ones without tone that have equivalents with tones...
            for (String p:pinyins) {
                Pinyin pin = new Pinyin(p);
                pins.add(pin);
            }
            for (int i=0; i<pins.size(); i++) {
                Pinyin pin = pins.get(i);
                if (pin.get_tone()!=5)
                    continue;
                for (Pinyin pin2:pins) {
                    if ((pin2.get_radical().equals(pin.get_radical())) && (pin2.get_tone() != 5)) {
                        pins.remove(i);
                        i=0;
                        break;
                    }
                }
            }
            pinyin=pins.get((int)(Math.random()*pins.size())).get_lame();
            words = myDB.getWordsWithPinyin(hanzi, pinyin);
        } else  {
            if (myDB.is_char_toneless_pair(hanzi)) {
                pinyin = myDB.get_toned_pinyin_in_pair(hanzi);
            } else {
                pinyin = pinyins.get(0);
            }
            words = myDB.getWordsWithPinyin(hanzi);
        }

        boolean found=false;
        for (Vocabulary word : words) {
            if (!word.chinese.replace(hanzi, "").equals("")) {
                found = true;
                break;
            }
        }
        if (!found) {
            return false;
        }

        Log.d(TAG, "GuessPinyin OK");
        current_test = hanzi;

        return true;
    }

    @Override
    View get_view() {
        LayoutInflater inflater = activity.getLayoutInflater();
        View res = inflater.inflate(R.layout.guess_pinyin, null);

        EditText guess = (EditText) res.findViewById(R.id.pinyin_test_guess);

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

        Log.d(TAG, "Testing for character " + current_test + " got " + words.size() + " words");

        TextView chinese_hint = (TextView) res.findViewById(R.id.guess_pinyin_chinese);
        chinese_hint.setText(current_test);

        final TableLayout tl = (TableLayout) res.findViewById(R.id.guess_pinyin_word_grid);
        tl.removeAllViews();
        TableRow.LayoutParams trlp;
        for (Vocabulary w : words) {
            TableRow tr = new TableRow(activity);

            TextView chinese = new TextView(activity);
            chinese.setTextAppearance(activity, android.R.style.TextAppearance_Medium_Inverse);
            chinese.setClickable(false);
            chinese.setText(w.chinese);
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
            trlp = new TableRow.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            trlp.setMargins(5,5,5,5);
            trans.setLayoutParams(trlp);
            tr.addView(trans);

            tl.addView(tr);
        }
        Button show_words_button = (Button) res.findViewById(R.id.guess_pinyin_show_words);
        show_words_button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                tl.setVisibility(View.VISIBLE);
                view.setClickable(false);
            }
        });

        if (show_words) {
            tl.setVisibility(View.VISIBLE);
            show_words_button.setClickable(false);
        }
        else {
            tl.setVisibility(View.GONE);
            show_words_button.setClickable(true);
        }

        Button submit_answer = (Button) res.findViewById(R.id.guess_pinyin_ok);
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
        EditText guess = (EditText) v.findViewById(R.id.pinyin_test_guess);
        guess.setOnEditorActionListener(null);
        guess.removeTextChangedListener(this);
        InputMethodManager imm = (InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(guess.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
    }

    protected int check_guess(String guess) {
        Pinyin guess_pinyin = new Pinyin(guess);
        Pinyin solution = new Pinyin(pinyin);
        if (guess_pinyin.get_tone() == -1) // invalid Pinyin
            return INCORRECT;

        if (guess_pinyin.equals(solution))
            return CORRECT;

        if (guess_pinyin.get_radical().equals(solution.get_radical()))
            return OTHER;

        return INCORRECT;
    }

    public void check_answer() {
        EditText word_guess = (EditText) activity.findViewById(R.id.pinyin_test_guess);
        if (word_guess==null)
            return;

        String guess = word_guess.getText().toString();

        int correctness = check_guess(guess);
        GuessPinyinResultFragment solution_fragment = new GuessPinyinResultFragment();
        Bundle bundle = new Bundle();
        bundle.putInt("good", correctness);
        bundle.putString("answer", guess);
        bundle.putString("question", current_test);
        bundle.putString("pinyin", pinyin);
        solution_fragment.setArguments(bundle);
        ((TestActivity)activity).new_solution_fragment(solution_fragment, correctness);
    }

    public static class GuessPinyinResultFragment extends Test.SolutionFragment {
        String pinyin;

        @Override
        protected void get_arguments() {
            super.get_arguments();
            Bundle bundle = getArguments();
            if (bundle != null) {
                pinyin = bundle.getString("pinyin");
            }
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            final View res = inflater.inflate(R.layout.test_result, container, false);

            DetailsFragment.populate_details_view(this, (ViewGroup) res.findViewById(R.id.test_results), question, pinyin, false, (TestActivity) GuessPinyinResultFragment.this.getActivity());
            if ((good == INCORRECT) && (!answer.equals(""))) {
                LinearLayout bad_guess = (LinearLayout) res.findViewById(R.id.test_result_wrong_guess);
                TextView bad_guess_text = new TextView(getActivity());
                bad_guess_text.setText("You guessed: "+answer);
                bad_guess_text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
                bad_guess_text.setTextColor(0xffdd0000);
                bad_guess_text.setGravity(Gravity.CENTER_HORIZONTAL);
                bad_guess.addView(bad_guess_text, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                FrameLayout bad_guess_details = new FrameLayout(getActivity());
                bad_guess_details.setId(R.id.test_bad_guess);
                DetailsFragment.populate_details_view(this, bad_guess_details, answer, false, (TestActivity) GuessPinyinResultFragment.this.getActivity());
                bad_guess.addView(bad_guess_details, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                (res.findViewById(R.id.test_result_wrong)).setVisibility(View.VISIBLE);
            }

            return res;
        }

        @Override
        public void onDestroyView() {
            hide_details((ViewGroup) getView());
            super.onDestroyView();
        }

        @Override
        public void update_score() {
            switch(good) {
                case INCORRECT:
                    myDB.worseBox(question);
                    break;
                case CORRECT:
                    myDB.betterBox(question);
                    break;
                case OTHER:
                    myDB.resetTime(question);
                    break;
            }
        }
    }

    public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
    }

    public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        if ((charSequence.toString().codePointCount(0, charSequence.toString().length()) > 0) && (!Pinyin.verify_pinyin(charSequence.toString()))) {
            activity.findViewById(R.id.guess_pinyin_ok).setEnabled(false);
        } else {
            activity.findViewById(R.id.guess_pinyin_ok).setEnabled(true);
        }
    }

    public void afterTextChanged(Editable editable) {
    }
}
