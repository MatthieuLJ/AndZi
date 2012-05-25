package com.matthieu.settings;

/* The following code was written by Matthew Wiggins
 * and is released under the APACHE 2.0 license
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */
// keep
// nothing
// 1 day
// 3 days
// 1 week
// 3 weeks
// 1 month
// 3 months
// 1 year

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.preference.DialogPreference;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.LinearLayout;
import com.matthieu.AndZiApplication;
import com.matthieu.ProgressDB;

public class TrimProgressPreference extends DialogPreference implements SeekBar.OnSeekBarChangeListener
{
    private SeekBar mSeekBar;
    private TextView mSplashText,mValueText;
    private Context mContext;
    private ProgressDB progress_db;

    private final static String TAG="TrimProgressPreference";

    private int mValue;

    private String []labels = {"Delete everything", "Keep the last day", "Keep the last 3 days",
            "Keep the last week", "Keep the last 3 weeks", "Keep the last month",
            "Keep the last 3 months", "Keep the last year"};
    /*
    private long []delays = {1L*24*60*60*1000, 3L*24*60*60*1000, 1L*7*24*60*60*
            1L*30*24*60*60*1000, 3L*30*24*60*60*1000, 1L*365*24*60*60*1000};
            */
    private long []delays = {86400000, 259200000, 604800000, 1814400000,
            2592000000L, 7776000000L, 31536000000L};
    private int max_range;


    public TrimProgressPreference(Context context, AttributeSet attrs) {
        super(context,attrs);
        mContext = context;
        progress_db = ((AndZiApplication)context.getApplicationContext()).getProgress();

        long oldest = progress_db.get_data().get(0).date;
        long current = System.currentTimeMillis();
        if (current - oldest > delays[6]) {
            max_range=7;
        } else if (current - oldest > delays[5]) {
            max_range=6;
        } else if (current - oldest > delays[4]) {
            max_range=5;
        } else if (current - oldest > delays[3]) {
            max_range=4;
        } else if (current - oldest > delays[2]) {
            max_range=3;
        } else if (current - oldest > delays[1]) {
            max_range=2;
        } else if (current - oldest > delays[0]) {
            max_range=1;
        } else {
            max_range=0;
        }

    }
    @Override
    protected View onCreateDialogView() {
        LinearLayout.LayoutParams params;
        LinearLayout layout = new LinearLayout(mContext);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(6,6,6,6);

        mSplashText = new TextView(mContext);
        mSplashText.setText("Deleting recent progress data...");
        layout.addView(mSplashText);

        mValueText = new TextView(mContext);
        mValueText.setGravity(Gravity.LEFT);
        params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.FILL_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        mValueText.setPadding(5,0,0,0);
        layout.addView(mValueText, params);

        mSeekBar = new SeekBar(mContext);
        mSeekBar.setOnSeekBarChangeListener(this);
        layout.addView(mSeekBar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        mValue=max_range;
        mSeekBar.setMax(max_range);
        mSeekBar.setProgress(mValue);

        return layout;
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);
        if (positiveResult) {
            Log.d(TAG, "Preference done, chose "+mValue+" out of "+max_range);
            if (mValue == max_range)
                progress_db.delete_data(-1);
            else
                progress_db.delete_data(delays[max_range-mValue-1]);
        }
    }

    @Override
    protected void onBindDialogView(View v) {
        super.onBindDialogView(v);
        mSeekBar.setMax(max_range);
        mSeekBar.setProgress(mValue);
        mValueText.setText(labels[max_range-mValue]);
    }

    public void onProgressChanged(SeekBar seek, int value, boolean fromTouch)
    {
        mValueText.setText(labels[max_range - value]);
        mValue=value;
        callChangeListener(value);
    }
    public void onStartTrackingTouch(SeekBar seek) {}
    public void onStopTrackingTouch(SeekBar seek) {}
}