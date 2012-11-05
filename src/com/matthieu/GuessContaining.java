/******************************
 * Activity to guess Chinese words containing a particular character
 */
package com.matthieu;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;

import java.util.ArrayList;

public class GuessContaining extends TestFragment {
    protected ArrayList<String> correct_answers=new ArrayList<String>();
    protected ArrayList<String> bad_answers=new ArrayList<String>();
    protected ArrayList<String> new_answers=new ArrayList<String>();

    private final String TAG="GuessContaining";

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
        return inflater.inflate(R.layout.guess_containing, container, false);
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

        TextView clue= (TextView) v.findViewById(R.id.guess_containing_chinese);
        clue.setText(hanzi);
        v.invalidate();
    }

    protected int check_guess(String guess) {
        int res = INCORRECT;
        correct_answers.clear();
        bad_answers.clear();
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
                    new_answers.add(guesses[i]);
                    res=OTHER;
                } else {
                    bad_answers.add(guesses[i]);
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
        EditText word_guess = (EditText) getView().findViewById(R.id.test_guess);
        if (word_guess==null)
            return false;

        String guess = word_guess.getText().toString();

        int correctness = check_guess(guess);
        GuessContainingResultFragment solution_fragment = new GuessContainingResultFragment(hanzi, guess, hanzi, correctness, correct_answers, bad_answers, new_answers);
        ((TestActivity)getActivity()).new_solution_fragment(solution_fragment, correctness);
        return true;
    }

    protected static class GuessContainingResultFragment extends TestFragment.SolutionFragment {
        ArrayList<String> correct_answers, bad_answers, new_answers;
        public GuessContainingResultFragment(String question, String answer, String hanzi, int good,
                                             ArrayList<String> correct_answers, ArrayList<String> bad_answers, ArrayList<String> new_answers) {
            super(question, answer, hanzi, good);
            this.correct_answers = correct_answers;
            this.bad_answers = bad_answers;
            this.new_answers = new_answers;
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            final View res = inflater.inflate(R.layout.test_result, container, false);

            DetailsFragment.populate_details_view(this, (ViewGroup) res.findViewById(R.id.test_results), hanzi, false, (TestActivity) GuessContainingResultFragment.this.getActivity());
            if (correct_answers.size() + bad_answers.size() + new_answers.size() > 0) {
                LinearLayout bad_guess = (LinearLayout) res.findViewById(R.id.test_result_wrong_guess);
                TableLayout tl = new TableLayout(getActivity());
                //tl.setStretchAllColumns(true);
                int table=0, offset_in_table=0, offset_in_column=0, table_size=bad_answers.size();
                TableRow tr=null;
                while(true) {
                    while ((offset_in_table >= table_size) && (table < 3)){
                        table++;
                        offset_in_table=0;
                        switch(table) {
                            case 1:
                                table_size = new_answers.size();
                                break;
                            case 2:
                                table_size = correct_answers.size();
                                break;
                        }
                    }

                    if (table == 3)
                        break;

                    String answer=null;
                    String label=null;
                    switch(table) {
                        case 0:
                            answer = bad_answers.get(offset_in_table);
                            label = "Wrong";
                            break;
                        case 1:
                            answer = new_answers.get(offset_in_table);
                            label = "New";
                            break;
                        case 2:
                            answer = correct_answers.get(offset_in_table);
                            label = "Correct";
                            break;
                    }
                    offset_in_table++;

                    if (offset_in_column == 0) {
                        tr = new TableRow(getActivity());
                        tr.setLayoutParams(new TableLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    }
                    LinearLayout cell = new LinearLayout(getActivity());
                    cell.setOrientation(LinearLayout.HORIZONTAL);
                    TextView label_box = new TextView(getActivity());
                    label_box.setText(label);
                    label_box.setTextColor(Color.BLACK);
                    label_box.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                    cell.addView(label_box);
                    TextView answer_box = new TextView(getActivity());
                    answer_box.setText(answer);
                    answer_box.setTextColor(Color.BLACK);
                    answer_box.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2));
                    final String final_answer = answer;
                    answer_box.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            /* TODO
                            Intent i=new Intent(getActivity(), ReferenceActivity.class);
                            i.putExtra("Hanzi", final_answer);
                            startActivity(i);
                            */
                        }
                    });
                    cell.addView(answer_box);
                    cell.setLayoutParams(new TableRow.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                    tr.addView(cell);

                    offset_in_column++;
                    if (offset_in_column == 2) {

                        tl.addView(tr);
                        offset_in_column = 0;
                    }
                }
                if (offset_in_column != 0)
                    tl.addView(tr);

                tl.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                bad_guess.addView(tl);
                /*
                TextView bad_guess_text = new TextView(getActivity());
                bad_guess_text.setText("You guessed: "+answer);
                bad_guess_text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
                bad_guess_text.setTextColor(0xffdd0000);
                bad_guess_text.setGravity(Gravity.CENTER_HORIZONTAL);
                bad_guess.addView(bad_guess_text, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                FrameLayout bad_guess_details = new FrameLayout(getActivity());
                bad_guess_details.setId(R.id.test_bad_guess);
                DetailsFragment.populate_details_view(this, bad_guess_details, answer, false, (TestActivity) TestFragment.this.getActivity());
                bad_guess.addView(bad_guess_details, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                */
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
