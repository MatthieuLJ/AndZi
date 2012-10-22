/******************************
 * Parent class for all tests
 */
package com.matthieu;

import android.app.Activity;
import android.content.Context;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public abstract class TestFragment extends Fragment {
    protected String hanzi;

    protected HanziDB myDB=null;
    protected HanziDB refDB=null;

    private static final String TAG="TestFragment";

    final static protected int CORRECT=1;
    final static protected int INCORRECT=2;
    final static protected int OTHER=3;

    public TestFragment(Activity activity) {
        myDB = ((AndZiApplication) activity.getApplication()).getMyDB();
        refDB = ((AndZiApplication) activity.getApplication()).getRefDB();
    }

    protected boolean set_hanzi_to_test(String hanzi) {
        this.hanzi = hanzi;
        return true;
    }

    abstract public boolean handle_ok(View v);
    abstract public void update_test_view(View v);

    protected abstract class SolutionFragment extends Fragment {
        int good;
        String question, answer, hanzi;

        public SolutionFragment(String question, String answer, String hanzi, int good) {
            this.good = good;
            this.answer = answer;
            this.question = question;
            this.hanzi = hanzi;
        }

        protected SolutionFragment() {
        }

        @Override
        public void onDetach() {
            Log.d(TAG, "Updating scores for "+hanzi);
            update_score();
            super.onDetach();
        }

        abstract public void update_score();

        protected View show_details(LayoutInflater inflater, ViewGroup container, boolean show_accept) {
            final View res = inflater.inflate(R.layout.test_result, container, false);

            DetailsFragment.populate_details_view(this, (ViewGroup) res.findViewById(R.id.test_results), hanzi, myDB);
            if ((good == INCORRECT) && (!answer.equals(""))) {
                LinearLayout bad_guess = (LinearLayout) res.findViewById(R.id.test_result_wrong_guess);
                TextView bad_guess_text = new TextView(getActivity());
                bad_guess_text.setText("You guessed: "+answer);
                bad_guess_text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
                bad_guess_text.setTextColor(0xffdd0000);
                bad_guess_text.setGravity(Gravity.CENTER_HORIZONTAL);
                bad_guess.addView(bad_guess_text, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                FrameLayout bad_guess_details = new FrameLayout(getActivity());
                DetailsFragment.populate_details_view(this, bad_guess_details, answer, myDB);
                bad_guess.addView(bad_guess_details, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                (res.findViewById(R.id.test_result_wrong)).setVisibility(View.VISIBLE);
                if (show_accept) {
                    (res.findViewById(R.id.test_result_accept)).setVisibility(View.VISIBLE);
                    (res.findViewById(R.id.test_result_accept)).setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            handle_good(view);
                        }
                    });
                }
            }

            return res;
        }

        public void handle_good(View v) {
            v.setEnabled(false);
            good = CORRECT;
        }

    }
}
