/******************************
 * Activity to guess Chinese words containing a particular character
 */
package com.matthieu.flash;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.util.Log;
import android.util.TypedValue;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import com.matthieu.flash.R;

import java.util.ArrayList;

public class GuessContaining extends Test {
    private String current_test;
    protected ArrayList<String> correct_answers=new ArrayList<String>();
    protected ArrayList<String> bad_answers=new ArrayList<String>();
    protected ArrayList<String> new_answers=new ArrayList<String>();

    private View[] master_view =null;
    private int current_view=0;

    private GuessContainingResultView solution_view;

    private static final boolean DEBUG = false;
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
        if (DEBUG) Log.d(TAG, "GuessContaining OK");

        solution_view = new GuessContainingResultView(current_test, activity);
        solution_view.prepare_view(activity.getLayoutInflater());

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

        EditText guess = res.findViewById(R.id.containing_test_guess);
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

        LinearLayout focusable = res.findViewById(R.id.containing_focusable_linear);
        focusable.requestFocus();

        TextView clue= res.findViewById(R.id.guess_containing_chinese);
        clue.setText(current_test);
        clue.setTypeface(((AndZiApplication) activity.getApplication()).get_chinese_font());

        Button submit_answer = res.findViewById(R.id.guess_containing_ok);
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
        EditText guess = v.findViewById(R.id.containing_test_guess);
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
            if (DEBUG) Log.d(TAG, "Checking "+guesses[i]);
            guesses[i]=guesses[i].trim();
            if (!guesses[i].contains(current_test))
                continue;
            if (guesses[i].codePointCount(0, guesses[i].length())==1)
                continue;
            if (myDB.getTranslations(guesses[i]).size()==0) {
                if (refDB.getTranslations(guesses[i]).size() != 0) {
                    new_answers.add(guesses[i]);
                    if (res != CORRECT)
                        res=OTHER;
                } else {
                    bad_answers.add(guesses[i]);
                }
            }
            else {
                if (DEBUG) Log.d(TAG, "Good answer");
                res = CORRECT;
                correct_answers.add(guesses[i]);
            }
        }
        return res;
    }

    public void check_answer() {
        EditText word_guess = activity.findViewById(R.id.containing_test_guess);
        if (word_guess==null)
            return;

        String guess = word_guess.getText().toString();

        int correctness = check_guess(guess);
        solution_view.set_answer(correctness, current_test,correct_answers, bad_answers, new_answers);
        activity.new_solution_fragment(solution_view);
    }

    public static class GuessContainingResultView extends SolutionView {
        ArrayList<String> correct_answers, bad_answers, new_answers;

        public GuessContainingResultView(String question, TestActivity parent) {
            super(question, parent);
        }

        public void set_answer(int good, String answer, ArrayList<String> correct_answers, ArrayList<String> bad_answers, ArrayList<String> new_answers) {
            super.set_answer(good, answer);
            this.correct_answers = correct_answers;
            this.bad_answers = bad_answers;
            this.new_answers = new_answers;
        }

        @Override
        public View get_view(LayoutInflater inflater, ViewGroup container) {
            if (correct_answers.size() + bad_answers.size() + new_answers.size() > 0) {
                LinearLayout bad_guess_container = result_view.findViewById(R.id.test_result_wrong);
                bad_guess_container.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0));

                LinearLayout bad_guess = result_view.findViewById(R.id.test_result_wrong_guess);
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
                    int color=0;
                    switch(table) {
                        case 0:
                            answer = bad_answers.get(offset_in_table);
                            color = parent_activity.getResources().getColor(R.color.test_failure);
                            break;
                        case 1:
                            answer = new_answers.get(offset_in_table);
                            color = Color.BLACK;
                            break;
                        case 2:
                            answer = correct_answers.get(offset_in_table);
                            color = parent_activity.getResources().getColor(R.color.test_success);
                            break;
                    }
                    offset_in_table++;

                    if (offset_in_column == 0) {
                        tr = new TableRow(parent_activity);
                        tr.setLayoutParams(new TableLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    }

                    TextView answer_box = new TextView(parent_activity);
                    answer_box.setText(answer);
                    answer_box.setTypeface(((AndZiApplication) parent_activity.getApplication()).get_chinese_font());
                    answer_box.setGravity(Gravity.CENTER);
                    answer_box.setTextColor(color);
                    answer_box.setTextSize(TypedValue.COMPLEX_UNIT_PX, parent_activity.getResources().getDimension(R.dimen.large_test_text));
                    answer_box.setLayoutParams(new TableRow.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                    answer_box.setPadding(4, 0, 4, 4);
                    final String final_answer = answer;
                    final boolean use_ref = (table != 2);
                    answer_box.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            Intent i=new Intent(parent_activity, ReferenceActivity.class);
                            i.setFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);
                            i.putExtra("Hanzi", final_answer);
                            i.putExtra("use_ref", use_ref);
                            parent_activity.startActivity(i);
                        }
                    });
                    tr.addView(answer_box);

                    offset_in_column++;
                    if (offset_in_column == 2) {
                        tl.addView(tr);
                        offset_in_column = 0;
                    }
                }
                if (offset_in_column != 0) {
                    LinearLayout empty_cell = new LinearLayout(parent_activity);
                    empty_cell.setLayoutParams(new TableRow.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                    tr.addView(empty_cell);
                    tl.addView(tr);
                }

                tl.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                bad_guess.addView(tl);
                (result_view.findViewById(R.id.test_result_wrong)).setVisibility(View.VISIBLE);
            }

            return result_view;
        }

        @Override
        public void kill_view(View v) {
            hide_details((ViewGroup) v);
        }

        @Override
        public void update_score() {
            switch(good) {
                case CORRECT:
                    myDB.betterBox(question);
                    int word_box = myDB.getBoxForChar(question);
                    int min_box=-1;
                    ArrayList<String> good_chars=new ArrayList<String>();
                    for (String answers:correct_answers) {
                        for (int i=0; i<answers.length();) {
                            String c = answers.substring(i, i+Character.charCount(answers.codePointAt(i)));
                            if ((!good_chars.contains(c)) && (!c.equals(question)))
                                good_chars.add(c);
                            i+=Character.charCount(answers.codePointAt(i));
                        }
                        int this_box=myDB.getBoxForChar(answers);
                        if ((min_box == -1) || (this_box<min_box))
                            min_box = this_box;
                    }
                    for (String answers:correct_answers) {
                        int this_box=myDB.getBoxForChar(answers);
                        if ((this_box == min_box) && (min_box < word_box))
                            myDB.betterBox(answers);
                        else
                            myDB.resetTime(answers);
                    }
                    for (String c:good_chars) {
                        myDB.resetTime(c);
                    }
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
