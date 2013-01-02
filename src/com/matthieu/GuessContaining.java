/******************************
 * Activity to guess Chinese words containing a particular character
 */
package com.matthieu;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

import java.util.ArrayList;

public class GuessContaining extends Test {
    private String current_test;
    protected ArrayList<String> correct_answers=new ArrayList<String>();
    protected ArrayList<String> bad_answers=new ArrayList<String>();
    protected ArrayList<String> new_answers=new ArrayList<String>();

    private View master_view[]=null;
    private int current_view=0;

    private final String TAG="GuessContaining";

    @Override
    protected boolean set_hanzi_to_test(String hanzi) {
        ArrayList<Vocabulary> words = myDB.getWordsWithPinyin(hanzi);
        for (int i=words.size()-1; i>=0; i--) {
            Vocabulary w = words.get(i);
            if (w.chinese.equals(hanzi+"子") || w.chinese.equals(hanzi+"儿") || w.chinese.replace(hanzi, "").equals("") || w.chinese.codePointCount(0, w.chinese.length())>3)
                words.remove(i);
            else
                break;
        }
        if (words.size() == 0) {
            return false;
        }
        current_test = hanzi;
        Log.d(TAG, "GuessContaining OK");

        return true;
    }

    @Override
    View get_view() {
        if (master_view == null) {
            master_view = new View[2];
            LayoutInflater inflater = activity.getLayoutInflater();
            master_view[0] = inflater.inflate(R.layout.guess_containing, null);
            master_view[1] = inflater.inflate(R.layout.guess_containing, null);
        }

        current_view = 1-current_view;
        View res = master_view[current_view];

        EditText guess = (EditText) res.findViewById(R.id.containing_test_guess);
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

        LinearLayout focusable = (LinearLayout) res.findViewById(R.id.containing_focusable_linear);
        focusable.requestFocus();

        TextView clue= (TextView) res.findViewById(R.id.guess_containing_chinese);
        clue.setText(current_test);

        Button submit_answer = (Button) res.findViewById(R.id.guess_containing_ok);
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
        EditText guess = (EditText) v.findViewById(R.id.containing_test_guess);
        if (guess==null)
            return;
        guess.setOnEditorActionListener(null);
        InputMethodManager imm = (InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        //imm.hideSoftInputFromWindow(guess.getWindowToken(), InputMethodManager.HIDE_IMPLICIT_ONLY);
        imm.hideSoftInputFromWindow(guess.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
    }

    protected int check_guess(String guess) {
        int res = INCORRECT;
        correct_answers.clear();
        new_answers.clear();
        bad_answers.clear();
        String[] guesses=guess.split("[,， ]");
        for (int i=0; i<guesses.length; i++) {
            if (guesses[i].equals(""))
                continue;
            Log.d(TAG, "Checking "+guesses[i]);
            guesses[i]=guesses[i].trim();
            if (!guesses[i].contains(current_test))
                continue;
            if (guesses[i].codePointCount(0, guesses[i].length())==1)
                continue;
            if (myDB.getTranslations(guesses[i]).size()==0) {
                if ((refDB != null) && (refDB.getTranslations(guesses[i]).size() != 0)) {
                    new_answers.add(guesses[i]);
                    if (res != CORRECT)
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

    public void check_answer() {
        EditText word_guess = (EditText) activity.findViewById(R.id.containing_test_guess);
        if (word_guess==null)
            return;

        String guess = word_guess.getText().toString();

        int correctness = check_guess(guess);
        GuessContainingResultView solution_fragment = new GuessContainingResultView(correctness, current_test, guess,activity,
                correct_answers, bad_answers, new_answers);
        ((TestActivity)activity).new_solution_fragment(solution_fragment);
    }

    public static class GuessContainingResultView extends SolutionView {
        ArrayList<String> correct_answers, bad_answers, new_answers;

        public GuessContainingResultView(int good, String question, String answer, TestActivity parent,
                                         ArrayList<String> correct_answers, ArrayList<String> bad_answers, ArrayList<String> new_answers) {
            super(good, question, answer, parent);
            this.correct_answers = correct_answers;
            this.bad_answers = bad_answers;
            this.new_answers = new_answers;
        }

        @Override
        public View get_view(LayoutInflater inflater, ViewGroup container) {
            final View res = inflater.inflate(R.layout.test_result, container, false);

            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    DetailsFragment.populate_details_view(parent_activity, (ViewGroup) res.findViewById(R.id.test_results), question, false, parent_activity);
                }
            }, 500);

            if (correct_answers.size() + bad_answers.size() + new_answers.size() > 0) {
                LinearLayout bad_guess = (LinearLayout) res.findViewById(R.id.test_result_wrong_guess);
                bad_guess.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                TableLayout tl = new TableLayout(parent_activity);
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
                        tr = new TableRow(parent_activity);
                        tr.setLayoutParams(new TableLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    }
                    LinearLayout cell = new LinearLayout(parent_activity);
                    cell.setOrientation(LinearLayout.HORIZONTAL);
                    TextView label_box = new TextView(parent_activity);
                    label_box.setText(label);
                    label_box.setGravity(Gravity.LEFT);
                    label_box.setTextColor(Color.BLACK);
                    label_box.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                    cell.addView(label_box);
                    TextView answer_box = new TextView(parent_activity);
                    answer_box.setText(answer);
                    answer_box.setGravity(Gravity.RIGHT);
                    answer_box.setTextColor(Color.BLACK);
                    answer_box.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2));
                    final String final_answer = answer;
                    answer_box.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            Intent i=new Intent(parent_activity, ReferenceActivity.class);
                            i.setFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);
                            i.putExtra("Hanzi", final_answer);
                            i.putExtra("use_ref", false);
                            parent_activity.startActivity(i);
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
                (res.findViewById(R.id.test_result_wrong)).setVisibility(View.VISIBLE);
            }

            return res;
        }

        @Override
        public void kill_view(View v) {
            hide_details((ViewGroup) v);
        }

        @Override
        public void update_score() {
            switch(good) {
                case CORRECT:
                    ArrayList<String> good_chars=new ArrayList<String>();
                    for (String answers:correct_answers) {
                        for (int i=0; i<answers.length();) {
                            String c = answers.substring(i, i+Character.charCount(answers.codePointAt(i)));
                            if ((!good_chars.contains(c)) && (!c.equals(question)))
                                good_chars.add(c);
                            i+=Character.charCount(answers.codePointAt(i));
                        }
                        myDB.betterBox(answers);
                    }
                    for (String c:good_chars) {
                        myDB.resetTime(c);
                    }
                    myDB.betterBox(question);
                    break;
                case INCORRECT:
                    myDB.worseBox(question);
                    break;
                case OTHER:
                    myDB.resetTime(question);
                    break;
            }
            super.update_score();
        }
    }
}
