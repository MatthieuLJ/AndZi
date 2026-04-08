/******************************
 * Graph showing progress, allow to zoom in and out and slide around
 */
package com.matthieu.flash.widget;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import com.matthieu.flash.AndZiApplication;
import com.matthieu.flash.ProgressDB;
import com.matthieu.flash.ProgressDB.ProgressData;
import com.matthieu.flash.R;
import java.text.DateFormat;
import java.util.ArrayList;

import static java.lang.Math.pow;
import static java.lang.Math.log;

public class ProgressGraph extends View {
    ArrayList<ProgressData> data0=null; // that's the rougher data we keep in memory
    ArrayList<ProgressData> current_data=null;
    ProgressDB db;
    range current_range= new range();

    private static final String TAG="ProgressGraph";
    private final Paint p = new Paint();
    Rect r=new Rect();
    private final Rect left_axis_r=new Rect();
    private final Rect bottom_axis_r=new Rect();
    ProgressData recent_data;

    public ProgressGraph(Context context) {
        super(context);
    }

    public ProgressGraph(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ProgressGraph(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    static private class range {
        long date_min=0;
        long date_max=0;
        int zoom=0;
    }

    private class update_delayer extends Thread {
        range query;
        boolean force;

        public update_delayer(range query, boolean force) {
            this.query = query;
            this.force = force;
        }
        @Override
        public void run() {
            if (!force && ((query.date_min != range_date_min) || (query.date_max != range_date_max) || (query.zoom != current_zoom)))
                return;

            synchronized (ProgressGraph.this) {
                if (((query.date_min == range_date_min) && (query.date_max == range_date_max) && (query.zoom == current_zoom)) &&
                    (force || (current_range.date_min != query.date_min) || (current_range.date_max != query.date_max) || (current_range.zoom != query.zoom))) {
                    ArrayList<ProgressData> tmp_data = db.get_data(query.zoom, query.date_min, query.date_max);
                    if (force) {
                        recent_data = ((AndZiApplication) getContext().getApplicationContext()).getCurrentProgress();
                        recent_data.date = max_date;
                    }
                    if (tmp_data.size() > 2) {
                        current_data = tmp_data;
                        current_data.add(recent_data);
                        current_range = query;
                    }
                }
            }

            postInvalidate();
        }
    }

    private long min_date, max_date;
    private int max_scale;
    private int scale_magnitude; // scale_magnitude is the largest number such that 10^(sm+1) < max_score
                                 // max_scale       is the next "round" number above max_score. eg if max_score = 251, max_scale=260

    public void set_data(ProgressDB db) {
        // assumes the data is sorted chronologically
        this.db = db;
        this.data0=db.get_data(0);

        recent_data = ((AndZiApplication) getContext().getApplicationContext()).getCurrentProgress();
        this.data0.add(recent_data);
        int current_char_count = recent_data.num_in_box[0] + recent_data.num_in_box[1] + recent_data.num_in_box[2] + recent_data.num_in_box[3] + recent_data.num_in_box[4];

        min_date=data0.get(0).date;
        max_date=System.currentTimeMillis();
        max_zoom = getMax_zoom(min_date, max_date);

        int max_score = db.get_max_chars();
        if (current_char_count > max_score)
            max_score = current_char_count;

        scale_magnitude = 1;
        while (max_score > pow(10,scale_magnitude)) {
            scale_magnitude++;
        }
        scale_magnitude = Math.max(scale_magnitude-2, 1);
        max_scale = 0;
        while (max_score > max_scale) {
            max_scale += pow(10,scale_magnitude);
        }
        if (max_score > 2*pow(10, scale_magnitude+1))
            scale_magnitude++;

        current_zoom=0;
        current_offset_x=0;
        if (getMeasuredWidth()!=0) {
            range_date_max = (long) Math.min(max_date, max_date-current_offset_x*(max_date-min_date)/((getMeasuredWidth()-left_size)*Math.pow(2,current_zoom)));
            range_date_min = (long) (max_date-(current_offset_x+(getMeasuredWidth()-left_size))*(max_date-min_date)/((getMeasuredWidth()-left_size)*Math.pow(2,current_zoom)));
        }
        else {
            range_date_max=-1;
            range_date_min=-1;
        }
        update_graph(false);
    }

    private int current_zoom=0; // the power of 2, so 0 is normal zoom (show everything), 1 is 2x, 2 is 4x...
    private int max_zoom=0;
    private int current_offset_x=0;
    private int max_offset_x;
    private float x_down_event;

    public boolean can_zoom_in() {
        return ((max_zoom==0) || !(current_zoom==max_zoom));
    }
    public void zoom_in() {
        if ((max_zoom==0) ||(current_zoom<max_zoom)) {
            current_zoom++;
            if (current_offset_x>0)
                // stay centered
                current_offset_x=2*current_offset_x+(getMeasuredWidth()-left_size)/2;
            update_graph(false);
        }
    }
    public boolean can_zoom_out() {
        return (current_zoom>0);
    }
    public void zoom_out() {
        if (current_zoom>0) {
            current_zoom--;
            if (current_offset_x>0)
                // stay centered
                current_offset_x=Math.max(-right_size,current_offset_x/2-(getMeasuredWidth()-left_size)/4);
            update_graph(false);
        }
    }

    private boolean finger_down=false;

    public boolean canScrollHorizontally(int delta_x) {
        return !(((delta_x > 0) && (current_offset_x == -right_size)) || ((delta_x < 0) && (current_offset_x == max_offset_x)));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final int action=event.getAction();
        switch(action) {
            case MotionEvent.ACTION_DOWN:
                x_down_event=event.getX();
                finger_down=true;
                break;
            case MotionEvent.ACTION_MOVE:
                final int delta_x=(int) (x_down_event - event.getX());
                if (((delta_x > 0) && (current_offset_x == -right_size)) || ((delta_x < 0) && (current_offset_x == max_offset_x)))
                    return false;
                x_down_event = event.getX();
                current_offset_x -= delta_x;
                if (current_offset_x < -right_size)
                    current_offset_x = -right_size;
                if (current_offset_x > max_offset_x)
                    current_offset_x = max_offset_x;
                finger_down=true;
                update_graph(false);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                finger_down=false;
                update_graph(false);
                break;

        }
        return true;
    }

    public void update_graph(boolean force) {
        if (getMeasuredWidth()==0)
            return;

        max_offset_x = (int) ((getMeasuredWidth()-left_size)*(Math.pow(2,current_zoom)-1));
        if (current_offset_x > max_offset_x) {
            current_offset_x = max_offset_x;
        }

        max_date = System.currentTimeMillis();
        range_date_max = (long) Math.min(max_date, max_date-current_offset_x*(max_date-min_date)/((getMeasuredWidth()-left_size)*Math.pow(2,current_zoom)));
        range_date_min = (long) (max_date-(current_offset_x+(getMeasuredWidth()-left_size))*(max_date-min_date)/((getMeasuredWidth()-left_size)*Math.pow(2,current_zoom)));

        range new_query = new range();
        new_query.date_min = range_date_min;
        new_query.date_max = range_date_max;
        new_query.zoom = current_zoom;

        new update_delayer(new_query, force).start();
    }

    private double log_scale(double y, double max) {
        return (Math.pow(10,y/max)-1)/9;
    }

    // bottom size is the height of the bottom scale
    // left_size is the width of the left scale
    // right_size is the width of the indicators on the right
    // main_size is the width of the main graph including the indicators on the right (so everything that will move left-right)
    // label_size is the height of the text in the current arrows
    int bottom_size, left_size, right_size, label_size;
    Path graph_path=new Path();
    long range_date_min, range_date_max;

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        p.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12, getResources().getDisplayMetrics()));

        String date_label="00/00/00";
        p.getTextBounds(date_label, 0, date_label.length(), bottom_axis_r);
        bottom_size= (int) java.lang.Math.ceil(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2 + 2 + 4 + 1, getResources().getDisplayMetrics())) + bottom_axis_r.height();

        String max_label=Integer.toString(max_scale);
        p.getTextBounds(max_label, 0,max_label.length(), left_axis_r);
        // This one is for the left axis and labels
        left_size= (int) java.lang.Math.ceil(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2 + 2 + 4 + 1, getResources().getDisplayMetrics())) + left_axis_r.width();
        // This is for the current number indicators
        right_size= (int) (java.lang.Math.ceil(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4 + 6 + 2 + 2, getResources().getDisplayMetrics())) + left_axis_r.width() + left_axis_r.height());
        // This is only the printed part of those current indicators
        label_size = (int) java.lang.Math.ceil(left_axis_r.height()/2.0);


    }

    static public int getMax_zoom(long min_date, long max_date) {
        long one_day = 1000 * 60 * 60 * 24;
        return (int) java.lang.Math.floor((float)(log((max_date-min_date)/one_day)/log(2)));
        // the logic is: one_day x 2^(max_zoom) <= max_date - min_date
    }

    ProgressData store_progress_at_origin = new ProgressData();
    ProgressData store_progress_at_edge = new ProgressData();

    @Override
    protected void onDraw(Canvas canvas) {
        // erase everything
        canvas.drawColor(Color.TRANSPARENT);

        p.setColor(Color.BLACK);

        if (data0 == null)
            return;

        if (data0.size() <=1) {
            p.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 24, getResources().getDisplayMetrics()));
            p.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(getResources().getString(R.string.not_enough_progress_data), getMeasuredWidth()/2, getMeasuredHeight()/2, p);

            return;
        }

        if ((range_date_max==-1) || (range_date_min==-1)) {
            range_date_max = (long) Math.min(max_date, max_date-current_offset_x*(max_date-min_date)/((getMeasuredWidth()-left_size)*Math.pow(2,current_zoom)));
            range_date_min = (long) (max_date-(current_offset_x+(getMeasuredWidth()-left_size))*(max_date-min_date)/((getMeasuredWidth()-left_size)*Math.pow(2,current_zoom)));
        }

        p.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12, getResources().getDisplayMetrics()));

        //
        // Draw the left axis
        //
        {
            p.setTextAlign(Paint.Align.RIGHT);
            int last_offset, first_offset;

            // left axis is (2 points) LABEL (2 points) (horizontal line of 4 points) (vertical line of 1 point width)

            // draw the top of the scale
            canvas.drawText(Integer.toString(max_scale), left_size-7, (int)(p.getTextSize()), p);
            canvas.drawLine(left_size-4, (int)(p.getTextSize()/2), left_size, (int)(p.getTextSize()/2), p);

            // draw the bottom of the scale
            canvas.drawText("0", left_size-7, getMeasuredHeight()-bottom_size, p);
            canvas.drawLine(left_size-4, getMeasuredHeight()-bottom_size, left_size, getMeasuredHeight()-bottom_size, p);

            // vertical line
            canvas.drawLine(left_size, (int)(p.getTextSize()/2), left_size, getMeasuredHeight()-bottom_size, p);

            // other lines
            int val = (int) pow(10, scale_magnitude);
            while (val < max_scale) {
                int y = (int) (getMeasuredHeight()-bottom_size - (getMeasuredHeight()-bottom_size-p.getTextSize()/2)*val/max_scale);
                canvas.drawText(Integer.toString(val), left_size-7, y+left_axis_r.height()/2, p);
                canvas.drawLine(left_size-4, y, left_size, y, p);

                val += pow(10, scale_magnitude);
            }
        }

        //
        // Draw the bottom axis
        //
        {
            p.setTextAlign(Paint.Align.CENTER);
            canvas.drawLine(left_size, getMeasuredHeight()-bottom_size, getMeasuredWidth(), getMeasuredHeight()-bottom_size, p);

            long one_day = 1000 * 60 * 60 * 24;
            long val_date;
            DateFormat df = android.text.format.DateFormat.getDateFormat(getContext());

            // the logic is: we want a label every 100 pixels approx
            // label_step is the time between two labels
            long label_step = (long) (100. * (range_date_max - range_date_min) / (getMeasuredWidth()-left_size));
            // now round label_step to a power of 2 of days
            if (label_step < one_day)
                label_step = one_day;
            else {
                float days = (float)label_step / one_day;
                label_step = (long) (pow(2, Math.ceil(log(days)/log(2))) * one_day);
            }

            val_date = (range_date_min / label_step + 1) * label_step;

            while (val_date < range_date_max) {
                int x = (int) (left_size + (getMeasuredWidth()-left_size) * (val_date - range_date_min) / (range_date_max - range_date_min));
                canvas.drawText(df.format(val_date), x, getMeasuredHeight()-4, p);
                canvas.drawLine(x, getMeasuredHeight()-bottom_size, x, getMeasuredHeight()-bottom_size+4, p);

                val_date += label_step;
            }
        }


        //
        // Draw the graph itself
        //
        canvas.save();
        canvas.clipRect(left_size+1, 0, getMeasuredWidth(), getMeasuredHeight()-bottom_size-1);

        if (current_data != null) {
            synchronized (this) {
                // it is possible the range has changed since the thread started
                // so we re-interpolate the data to the current range
                // this is also what we do if we are currently scrolling/zooming

                // first find the two data points that are just outside the range
                int first_data_idx=0;
                while ((first_data_idx < current_data.size()) && (current_data.get(first_data_idx).date < range_date_min))
                    first_data_idx++;
                if (first_data_idx > 0)
                    first_data_idx--;

                int last_data_idx=current_data.size()-1;
                while ((last_data_idx >= 0) && (current_data.get(last_data_idx).date > range_date_max))
                    last_data_idx--;
                if (last_data_idx < current_data.size()-1)
                    last_data_idx++;

                if (last_data_idx <= first_data_idx) {
                    canvas.restore();
                    return;
                }

                int x0, y0;
                int x1, y1;
                int h = (int) (getMeasuredHeight() - bottom_size - p.getTextSize() / 2);

                for (int b = 0; b < 5; b++) {
                    graph_path.reset();
                    switch (b) {
                        case 0: p.setColor(getContext().getResources().getColor(R.color.graph_box0)); break;
                        case 1: p.setColor(getContext().getResources().getColor(R.color.graph_box1)); break;
                        case 2: p.setColor(getContext().getResources().getColor(R.color.graph_box2)); break;
                        case 3: p.setColor(getContext().getResources().getColor(R.color.graph_box3)); break;
                        case 4: p.setColor(getContext().getResources().getColor(R.color.graph_box4)); break;
                    }

                    x0 = (int) (left_size + (getMeasuredWidth() - left_size) * (current_data.get(first_data_idx).date - range_date_min) / (range_date_max - range_date_min));
                    y0 = getMeasuredHeight() - bottom_size - h * get_box_total(first_data_idx, b) / max_scale;
                    graph_path.moveTo(x0, y0);

                    for (int i = first_data_idx + 1; i <= last_data_idx; i++) {
                        x1 = (int) (left_size + (getMeasuredWidth() - left_size) * (current_data.get(i).date - range_date_min) / (range_date_max - range_date_min));
                        y1 = getMeasuredHeight() - bottom_size - h * get_box_total(i, b) / max_scale;
                        graph_path.lineTo(x1, y1);
                    }
                    // close the path
                    x1 = (int) (left_size + (getMeasuredWidth() - left_size) * (current_data.get(last_data_idx).date - range_date_min) / (range_date_max - range_date_min));
                    graph_path.lineTo(x1, getMeasuredHeight() - bottom_size);
                    x0 = (int) (left_size + (getMeasuredWidth() - left_size) * (current_data.get(first_data_idx).date - range_date_min) / (range_date_max - range_date_min));
                    graph_path.lineTo(x0, getMeasuredHeight() - bottom_size);
                    graph_path.close();

                    canvas.drawPath(graph_path, p);
                }
            }
        }
        canvas.restore();

        //
        // Draw the current indicators
        //
        if (current_data != null) {
            int h = (int) (getMeasuredHeight() - bottom_size - p.getTextSize() / 2);
            int x = (int) (left_size + (getMeasuredWidth() - left_size) * (current_data.get(current_data.size()-1).date - range_date_min) / (range_date_max - range_date_min));
            if ((x > left_size) && (x < getMeasuredWidth() + right_size)) {
                p.setTextAlign(Paint.Align.LEFT);
                for (int b = 0; b < 5; b++) {
                    int y = getMeasuredHeight() - bottom_size - h * get_box_total(current_data.size() - 1, b) / max_scale;
                    int val = current_data.get(current_data.size() - 1).num_in_box[b];

                    switch (b) {
                        case 0: p.setColor(getContext().getResources().getColor(R.color.graph_box0)); break;
                        case 1: p.setColor(getContext().getResources().getColor(R.color.graph_box1)); break;
                        case 2: p.setColor(getContext().getResources().getColor(R.color.graph_box2)); break;
                        case 3: p.setColor(getContext().getResources().getColor(R.color.graph_box3)); break;
                        case 4: p.setColor(getContext().getResources().getColor(R.color.graph_box4)); break;
                    }

                    graph_path.reset();
                    graph_path.moveTo(x, y);
                    graph_path.lineTo(x + 6, y - label_size);
                    graph_path.lineTo(x + right_size, y - label_size);
                    graph_path.lineTo(x + right_size, y + label_size);
                    graph_path.lineTo(x + 6, y + label_size);
                    graph_path.close();
                    canvas.drawPath(graph_path, p);

                    p.setColor(Color.BLACK);
                    canvas.drawText(Integer.toString(val), x + 8, y + label_size - 2, p);
                }
            }
        }
    }

    private int get_box_total(int data_idx, int box_idx) {
        int total=0;
        for (int i=box_idx; i<5; i++)
            total += current_data.get(data_idx).num_in_box[i];
        return total;
    }
}
