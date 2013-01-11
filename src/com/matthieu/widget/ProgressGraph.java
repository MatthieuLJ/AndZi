/******************************
 * Graph showing progress, allow to zoom in and out and slide around
 */
package com.matthieu.widget;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import com.matthieu.AndZiApplication;
import com.matthieu.ProgressDB;
import com.matthieu.ProgressDB.ProgressData;
import com.matthieu.R;
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
    private Paint p = new Paint();
    Rect r=new Rect();
    private Rect left_axis_r=new Rect();
    private Rect bottom_axis_r=new Rect();
    ProgressData recent_data;

    public ProgressGraph(Context context) {
        super(context);
        recent_data = ((AndZiApplication) getContext().getApplicationContext()).getCurrentProgress();
    }

    public ProgressGraph(Context context, AttributeSet attrs) {
        super(context, attrs);
        recent_data = ((AndZiApplication) getContext().getApplicationContext()).getCurrentProgress();
    }

    public ProgressGraph(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        recent_data = ((AndZiApplication) getContext().getApplicationContext()).getCurrentProgress();
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
                    if (force)
                        recent_data = ((AndZiApplication) getContext().getApplicationContext()).getCurrentProgress();
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

        ProgressDB.ProgressData new_data = ((AndZiApplication) getContext().getApplicationContext()).getCurrentProgress();
        this.data0.add(new_data);
        int current_char_count = new_data.num_in_box[0] + new_data.num_in_box[1] + new_data.num_in_box[2] + new_data.num_in_box[3] + new_data.num_in_box[4];

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
            Log.d(TAG, "zoom is "+current_zoom+" out of "+max_zoom);
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
        if (((delta_x > 0) && (current_offset_x == -right_size)) || ((delta_x < 0) && (current_offset_x == max_offset_x)))
            return false;
        return true;
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
        bottom_size= (int) android.util.FloatMath.ceil(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2 + 2 + 4 + 1, getResources().getDisplayMetrics())) + bottom_axis_r.height();

        String max_label=Integer.toString(max_scale);
        p.getTextBounds(max_label, 0,max_label.length(), left_axis_r);
        // This one is for the left axis and labels
        left_size= (int) android.util.FloatMath.ceil(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2 + 2 + 4 + 1, getResources().getDisplayMetrics())) + left_axis_r.width();
        // This is for the current number indicators
        right_size= (int) android.util.FloatMath.ceil(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4 + 6 + 2 +2, getResources().getDisplayMetrics())) + left_axis_r.width() + left_axis_r.height();
        // This is only the printed part of those current indicators
        label_size = (int) android.util.FloatMath.ceil(left_axis_r.height()/2);


    }

    static public int getMax_zoom(long min_date, long max_date) {
        long one_day = 1000 * 60 * 60 * 24;
        return (int) android.util.FloatMath.floor((float)(log((max_date-min_date)/one_day)/log(2)));
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
            canvas.drawText("Not enough data", getMeasuredWidth()/2, getMeasuredHeight()/2, p);

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
            canvas.drawLine((int) android.util.FloatMath.floor(left_size - TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4 + 1, getResources().getDisplayMetrics())), 0, left_size-1, 0, p);
            canvas.drawText(Integer.toString(max_scale),(int) android.util.FloatMath.floor(left_size - TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4 + 1 + 2, getResources().getDisplayMetrics())),
                    left_axis_r.height(), p);
            last_offset=left_axis_r.height();

            // draw the bottom of the scale
            canvas.drawLine((int) android.util.FloatMath.floor(left_size - TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4 + 1, getResources().getDisplayMetrics())),
                    getMeasuredHeight()-bottom_size, left_size-1, getMeasuredHeight()-bottom_size, p);
            canvas.drawText("0",(int) android.util.FloatMath.floor(left_size - TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4 + 1 + 2, getResources().getDisplayMetrics())),
                    getMeasuredHeight()-bottom_size, p);
            first_offset=getMeasuredHeight()-bottom_size-left_axis_r.height();

            // draw some intermediary values
            int int_value= 0;
            while(int_value < max_scale) {
                canvas.drawLine((int) android.util.FloatMath.floor(left_size - TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4 + 1, getResources().getDisplayMetrics())),
                        (float) ((1.0-log_scale(int_value, max_scale)))*(getMeasuredHeight()-bottom_size), left_size-1, (float) ((1-log_scale(int_value, max_scale))*(getMeasuredHeight()-bottom_size)), p);
                if (((int) ((1-log_scale(int_value, max_scale))*(getMeasuredHeight()-bottom_size))-left_axis_r.height()/2 > last_offset) &&
                        ((int) ((1-log_scale(int_value, max_scale))*(getMeasuredHeight()-bottom_size))+left_axis_r.height()/2<first_offset)) {
                    canvas.drawText(Integer.toString(int_value),(int) android.util.FloatMath.floor(left_size - TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4 + 1 + 2, getResources().getDisplayMetrics())),
                            (int) ((1-log_scale(int_value, max_scale))*(getMeasuredHeight()-bottom_size))+left_axis_r.height()/2, p);
                    first_offset = (int) ((1-log_scale(int_value, max_scale))*(getMeasuredHeight()-bottom_size))-left_axis_r.height()/2;
                }
                int_value += pow(10, scale_magnitude);
            }

            // draw a line along
            canvas.drawLine(left_size-1, 0, left_size-1, getMeasuredHeight()-bottom_size, p);
        }

        synchronized (ProgressGraph.this) {
            ArrayList<ProgressData> data_to_use;
            if ((!finger_down) && (current_range.date_min == range_date_min) && (current_range.date_max == range_date_max) && (current_range.zoom == current_zoom) && (current_data != null)) {
                data_to_use = current_data;
            }
            else {
                data_to_use = data0;
                data_to_use.add(recent_data);
            }

            int first_date_index=0, last_date_index=data_to_use.size()-1;
            for (int i=0; i<data_to_use.size(); i++) {
                if (data_to_use.get(i).date < range_date_min)
                    first_date_index = i+1;
                if (data_to_use.get(data_to_use.size()-i-1).date > range_date_max)
                    last_date_index  = data_to_use.size()-i-2;
            }
            ProgressData data_at_origin;
            if (first_date_index == 0) {
                data_at_origin = data_to_use.get(0);
            }
            else {
                data_at_origin = store_progress_at_origin;
                data_at_origin.date = range_date_min;
                for (int i=0; i<5; i++) {
                    data_at_origin.num_in_box[i] = (int) (data_to_use.get(first_date_index-1).num_in_box[i] +
                            (float)(data_to_use.get(first_date_index).num_in_box[i] - data_to_use.get(first_date_index-1).num_in_box[i]) *
                                    (range_date_min - data_to_use.get(first_date_index-1).date) / (data_to_use.get(first_date_index).date - data_to_use.get(first_date_index-1).date));

                    data_at_origin.num_expired_in_box[i] = (int) (data_to_use.get(first_date_index-1).num_expired_in_box[i] +
                            (float)(data_to_use.get(first_date_index).num_expired_in_box[i] - data_to_use.get(first_date_index-1).num_expired_in_box[i]) *
                                    (range_date_min - data_to_use.get(first_date_index-1).date) / (data_to_use.get(first_date_index).date - data_to_use.get(first_date_index-1).date));
                }
            }
            ProgressData data_at_edge;
            if (last_date_index == data_to_use.size()-1) {
                data_at_edge = data_to_use.get(data_to_use.size()-1);
            }
            else {
                data_at_edge = store_progress_at_edge;
                data_at_edge.date = range_date_max;
                for (int i=0; i<5; i++) {
                    //Log.d(TAG, "last_date_index: "+last_date_index+", size of data_to_use: "+data_to_use.size());
                    data_at_edge.num_in_box[i] = (int) (data_to_use.get(last_date_index).num_in_box[i] +
                            (float)(data_to_use.get(last_date_index+1).num_in_box[i] - data_to_use.get(last_date_index).num_in_box[i]) *
                                    (range_date_max - data_to_use.get(last_date_index).date) / (data_to_use.get(last_date_index+1).date - data_to_use.get(last_date_index).date));

                    data_at_edge.num_expired_in_box[i] = (int) (data_to_use.get(last_date_index).num_expired_in_box[i] +
                            (float)(data_to_use.get(last_date_index+1).num_expired_in_box[i] - data_to_use.get(last_date_index).num_expired_in_box[i]) *
                                    (range_date_max - data_to_use.get(last_date_index).date) / (data_to_use.get(last_date_index+1).date - data_to_use.get(last_date_index).date));
                }
            }

            //
            // Draw the bottom axis
            //
            {
                canvas.drawLine(left_size, getMeasuredHeight()-bottom_size, Math.min(getMeasuredWidth(), getMeasuredWidth()+current_offset_x), getMeasuredHeight()-bottom_size, p);

                // draw the bottom labels
                int last_offset = getMeasuredWidth()+Math.min(0, current_offset_x);
                int first_offset = left_size;
                int average_half_width;
                String date_string;

                // Draw the leftmost one
                p.setTextAlign(Paint.Align.LEFT);
                date_string= DateFormat.getDateInstance(DateFormat.MEDIUM).format(range_date_min)+" - ";
                canvas.drawLine(
                        left_size,
                        getMeasuredHeight()-bottom_size,
                        left_size,
                        getMeasuredHeight()-bottom_size+TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, getResources().getDisplayMetrics()), p);
                canvas.drawText(date_string, left_size,
                        getMeasuredHeight()-bottom_size+bottom_axis_r.height()+TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4+2, getResources().getDisplayMetrics()), p);
                p.getTextBounds(date_string, 0, date_string.length(), r);
                first_offset += r.width();

                // Draw the rightmost one
                p.setTextAlign(Paint.Align.RIGHT);
                date_string= " - "+DateFormat.getDateInstance(DateFormat.MEDIUM).format(range_date_max);
                canvas.drawLine(
                        last_offset,
                        getMeasuredHeight()-bottom_size,
                        last_offset,
                        getMeasuredHeight()-bottom_size+TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, getResources().getDisplayMetrics()), p);
                canvas.drawText(date_string, last_offset,
                        getMeasuredHeight()-bottom_size+bottom_axis_r.height()+TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4+2, getResources().getDisplayMetrics()), p);
                p.getTextBounds(date_string, 0, date_string.length(), r);
                last_offset -= r.width();

                average_half_width = ((first_offset - left_size) + (getMeasuredWidth()+Math.min(0, current_offset_x) - last_offset))/2;

                if (!finger_down) {
                    p.setTextAlign(Paint.Align.CENTER);
                    for (int i=last_date_index; i>=first_date_index; i--) {
                        if ((getMeasuredWidth()+Math.min(0, current_offset_x)-left_size)*((float)data_to_use.get(i).date-range_date_min)/(range_date_max-range_date_min)+average_half_width >= last_offset)
                            continue;
                        if ((getMeasuredWidth()+Math.min(0, current_offset_x)-left_size)*((float)data_to_use.get(i).date-range_date_min)/(range_date_max-range_date_min)-average_half_width <= first_offset)
                            break;

                        date_string= " - "+DateFormat.getDateInstance(DateFormat.MEDIUM).format(data_to_use.get(i).date)+" - ";
                        p.getTextBounds(date_string, 0, date_string.length(), r);

                        if ((getMeasuredWidth()+Math.min(0, current_offset_x)-left_size)*((float)data_to_use.get(i).date-range_date_min)/(range_date_max-range_date_min)+r.width()/2 >= last_offset)
                            continue;
                        if ((getMeasuredWidth()+Math.min(0, current_offset_x)-left_size)*((float)data_to_use.get(i).date-range_date_min)/(range_date_max-range_date_min)-r.width()/2 <= first_offset)
                            break;

                        canvas.drawLine(
                                (getMeasuredWidth()+Math.min(0, current_offset_x)-left_size)*((float)data_to_use.get(i).date-range_date_min)/(range_date_max-range_date_min),
                                getMeasuredHeight()-bottom_size,
                                (getMeasuredWidth()+Math.min(0, current_offset_x)-left_size)*((float)data_to_use.get(i).date-range_date_min)/(range_date_max-range_date_min),
                                getMeasuredHeight()-bottom_size+TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, getResources().getDisplayMetrics()), p);
                        canvas.drawText(date_string, (getMeasuredWidth()+Math.min(0, current_offset_x)-left_size)*((float)data_to_use.get(i).date-range_date_min)/(range_date_max-range_date_min),
                                getMeasuredHeight()-bottom_size+bottom_axis_r.height()+TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4+2, getResources().getDisplayMetrics()), p);
                        last_offset=(int) ((getMeasuredWidth()+Math.min(0, current_offset_x)-left_size)*((float)data_to_use.get(i).date-range_date_min)/(range_date_max-range_date_min)-r.width()/2);
                    }
                }
            }

            //
            // Draw the graph
            //
            {
                // draw the graphs
                // in the order:
                //  - red (box 0 / untested)
                //  - orange (box 1)
                //  - light green (box 2)
                //  - medium green (box 3)
                //  - dark green (box 4)

                int value[]=new int[data_to_use.size()+1];
                float score_y;
                int total_now=0;

                for (int graph_color = 0; graph_color < 5; graph_color++) {
                    graph_path.reset();
                    graph_path.moveTo(left_size, getMeasuredHeight() - bottom_size);
                    if (first_date_index>0) {
                        switch(graph_color) {
                            case 0:
                                value[0] = data_at_origin.num_in_box[0]+data_at_origin.num_in_box[1]+data_at_origin.num_in_box[2]+data_at_origin.num_in_box[3]+data_at_origin.num_in_box[4];
                                break;
                            case 1:
                                value[0] -= data_at_origin.num_in_box[0];
                                break;
                            case 2:
                                value[0] -= data_at_origin.num_in_box[1];
                                break;
                            case 3:
                                value[0] -= data_at_origin.num_in_box[2];
                                break;
                            case 4:
                                value[0] -= data_at_origin.num_in_box[3];
                                break;
                        }

                        graph_path.lineTo(left_size ,(float) ((1.0-log_scale(value[0], max_scale))*(getMeasuredHeight()-bottom_size)));
                    }
                    for (int i=first_date_index; i<data_to_use.size(); i++) {
                        switch(graph_color) {
                            case 0:
                                value[i] = data_to_use.get(i).num_in_box[0]+data_to_use.get(i).num_in_box[1]+data_to_use.get(i).num_in_box[2]+data_to_use.get(i).num_in_box[3]+data_to_use.get(i).num_in_box[4];
                                break;
                            case 1:
                                value[i] -= data_to_use.get(i).num_in_box[0];
                                break;
                            case 2:
                                value[i] -= data_to_use.get(i).num_in_box[1];
                                break;
                            case 3:
                                value[i] -= data_to_use.get(i).num_in_box[2];
                                break;
                            case 4:
                                value[i] -= data_to_use.get(i).num_in_box[3];
                                break;
                        }
                        float x= left_size + (getMeasuredWidth()+Math.min(0, current_offset_x)-left_size)*((float)data_to_use.get(i).date-range_date_min)/(range_date_max-range_date_min);
                        float y= (float) ((1.0-log_scale(value[i], max_scale))*(getMeasuredHeight()-bottom_size));
                        //Log.d(TAG, "Line to "+x+", "+y);
                        graph_path.lineTo(x ,y);
                    }
                    if (last_date_index<data_to_use.size()) {
                        switch(graph_color) {
                            case 0:
                                value[data_to_use.size()] = data_at_edge.num_in_box[0]+data_at_edge.num_in_box[1]+data_at_edge.num_in_box[2]+data_at_edge.num_in_box[3]+data_at_edge.num_in_box[4];
                                break;
                            case 1:
                                value[data_to_use.size()] -= data_at_edge.num_in_box[0];
                                break;
                            case 2:
                                value[data_to_use.size()] -= data_at_edge.num_in_box[1];
                                break;
                            case 3:
                                value[data_to_use.size()] -= data_at_edge.num_in_box[2];
                                break;
                            case 4:
                                value[data_to_use.size()] -= data_at_edge.num_in_box[3];
                                break;
                        }

                        graph_path.lineTo(getMeasuredWidth()+Math.min(0, current_offset_x) ,(float) ((1.0-log_scale(value[data_to_use.size()], max_scale))*(getMeasuredHeight()-bottom_size)));
                    }
                    graph_path.lineTo(getMeasuredWidth()+Math.min(0, current_offset_x), getMeasuredHeight()-bottom_size);
                    switch(graph_color) {
                        case 0:
                            p.setColor(getResources().getColor(R.color.graph_box0));
                            break;
                        case 1:
                            p.setColor(getResources().getColor(R.color.graph_box1));
                            break;
                        case 2:
                            p.setColor(getResources().getColor(R.color.graph_box2));
                            break;
                        case 3:
                            p.setColor(getResources().getColor(R.color.graph_box3));
                            break;
                        case 4:
                            p.setColor(getResources().getColor(R.color.graph_box4));
                            break;
                    }
                    p.setStyle(Paint.Style.FILL);
                    canvas.drawPath(graph_path, p);

                    if (current_offset_x<0) {
                        graph_path.reset();

                        switch(graph_color) {
                            case 0:
                                total_now = data_to_use.get(data_to_use.size()-1).num_in_box[0]+data_to_use.get(data_to_use.size()-1).num_in_box[1]+
                                        data_to_use.get(data_to_use.size()-1).num_in_box[2]+data_to_use.get(data_to_use.size()-1).num_in_box[3]+
                                        data_to_use.get(data_to_use.size()-1).num_in_box[4];
                                break;
                            case 1:
                                total_now -= data_to_use.get(data_to_use.size()-1).num_in_box[0];
                                break;
                            case 2:
                                total_now -= data_to_use.get(data_to_use.size()-1).num_in_box[1];
                                break;
                            case 3:
                                total_now -= data_to_use.get(data_to_use.size()-1).num_in_box[2];
                                break;
                            case 4:
                                total_now -= data_to_use.get(data_to_use.size()-1).num_in_box[3];
                                break;
                        }
                        score_y = (float) ((1.0-log_scale(total_now, max_scale))*(getMeasuredHeight()-bottom_size));
                        graph_path.moveTo(getMeasuredWidth()+current_offset_x+4, score_y);
                        graph_path.lineTo(getMeasuredWidth()+current_offset_x+6+3*label_size, score_y+3*label_size+6);
                        graph_path.lineTo(getMeasuredWidth()+current_offset_x+6+3*label_size, score_y+label_size+2);
                        graph_path.lineTo(getMeasuredWidth()+current_offset_x+right_size-2, score_y+label_size+2);
                        graph_path.lineTo(getMeasuredWidth()+current_offset_x+right_size-2, score_y-label_size-2);
                        graph_path.lineTo(getMeasuredWidth()+current_offset_x+6+3*label_size, score_y-label_size-2);
                        graph_path.lineTo(getMeasuredWidth()+current_offset_x+6+3*label_size, score_y-3*label_size-6);
                        graph_path.lineTo(getMeasuredWidth()+current_offset_x+4, score_y);
                        canvas.drawPath(graph_path, p);
                        p.setTextAlign(Paint.Align.RIGHT);
                        p.setColor(Color.WHITE);
                        canvas.drawText(Integer.toString(total_now), getMeasuredWidth()+current_offset_x+right_size-4, score_y+label_size, p);
                    }
                }
            }
        }
    }
}
