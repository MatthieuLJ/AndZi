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
import com.matthieu.ProgressDB.ProgressData;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;

import static java.lang.Math.pow;
import static java.lang.Math.log;

public class ProgressGraph extends View {
    ArrayList<ProgressData> data=null;
    ArrayList<Date> dates=null;

    private static final String TAG="ProgressGraph";
    private Paint p = new Paint();
    Rect r=new Rect();
    private Rect left_axis_r=new Rect();
    private Rect bottom_axis_r=new Rect();
    private boolean reset_offset_x=true;
    private Rect origin=new Rect(), dest=new Rect();

    public ProgressGraph(Context context) {
        super(context);
    }

    public ProgressGraph(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ProgressGraph(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    private long min_date, max_date, second_to_max_date;
    private int max_scale;
    private int scale_magnitude; // scale_magnitude is the largest number such that 10^sm < max_score
                                 // max_scale       is the next "round" number above max_score. eg if max_score = 250, max_scale=300
    public void set_data(ArrayList<ProgressData> data) {
        // assumes the data is sorted chronologically
        this.data=data;
        if (data == null) {
            return;
        }
        min_date=data.get(0).date;
        max_date=data.get(data.size()-1).date;
        int i;
        for (i=data.size()-1; i>=0; i--) {
            if (data.get(i).date < max_date - ((long)24*60*60*1000)) {
                second_to_max_date=data.get(i).date;
                break;
            }
        }
        if (i<0) {
            second_to_max_date=min_date;
        }
        dates = new ArrayList<Date>();
        for (ProgressData item:data) {
            dates.add(new Date(item.date));
        }

        int max_score = data.get(0).num_chars_in_box[0] + data.get(0).num_chars_in_box[1] + data.get(0).num_chars_in_box[2] + data.get(0).num_chars_in_box[3] + data.get(0).num_chars_in_box[4];
        for (ProgressData d : data) {
            int num_chars=d.num_chars_in_box[0]+d.num_chars_in_box[1]+d.num_chars_in_box[2]+d.num_chars_in_box[3]+d.num_chars_in_box[4];
            if (num_chars> max_score)
                max_score =num_chars;
        }
        scale_magnitude = 1;
        while (max_score > pow(10,scale_magnitude)) {
            scale_magnitude++;
        }
        scale_magnitude--;
        max_scale = 0;
        while (max_score > max_scale) {
            max_scale += pow(10,scale_magnitude);
        }

        left_scale=null;
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
            reset_offset_x=true;
            this.invalidate();
        }
    }
    public boolean can_zoom_out() {
        return (current_zoom>0);
    }
    public void zoom_out() {
        if (current_zoom>0) {
            current_zoom--;
            reset_offset_x=true;
            this.invalidate();
        }
    }

    Bitmap left_scale=null, graph=null;
    int cached_height=0, cached_width=0, cached_zoom=-1;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final int action=event.getAction();
        switch(action) {
            case MotionEvent.ACTION_DOWN:
                x_down_event=event.getX();
                break;
            case MotionEvent.ACTION_MOVE:
                final int delta_x=(int) (x_down_event - event.getX());
                x_down_event = event.getX();
                current_offset_x -= delta_x;
                if (current_offset_x < 0)
                    current_offset_x=0;
                if (current_offset_x > max_offset_x)
                    current_offset_x = max_offset_x;
                this.invalidate();
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                break;

        }
        return true;
    }

    private double log_scale(double y, double max) {
        return (Math.pow(10,y/max)-1)/9;
    }

    // bottom size is the height of the bottom scale
    // left_size is the width of the left scale
    // right_size is the width of the indicators on the right
    // main_size is the width of the main graph including the indicators on the right (so everything that will move left-right)
    // label_size is the height of the text in the current arrows
    int bottom_size, left_size, right_size, main_size, label_size;
    Canvas left_scale_canvas;
    Canvas graph_canvas;
    Path graph_path;

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        p.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12, getResources().getDisplayMetrics()));

        String date_label="01/01/01";
        p.getTextBounds(date_label, 0, date_label.length(), bottom_axis_r);
        bottom_size= (int) android.util.FloatMath.ceil(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2 + 2 + 4 + 1, getResources().getDisplayMetrics())) + bottom_axis_r.height();

        String max_label=Integer.toString(max_scale);
        p.getTextBounds(max_label, 0,max_label.length(), left_axis_r);
        left_size= (int) android.util.FloatMath.ceil(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2 + 2 + 4 + 1, getResources().getDisplayMetrics())) + left_axis_r.width();
        right_size= (int) android.util.FloatMath.ceil(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4 + 6 + 2 +2, getResources().getDisplayMetrics())) + left_axis_r.width() + left_axis_r.height();
        label_size = (int) android.util.FloatMath.ceil(left_axis_r.height()/2);
        main_size= (getMeasuredWidth()-left_size)*2 + right_size;

        if ((left_scale == null) || (cached_height != getMeasuredHeight()) || (cached_width != getMeasuredWidth())) {
            if(graph != null) {
                graph.recycle();
            }
            graph=Bitmap.createBitmap(main_size, getMeasuredHeight(), Bitmap.Config.ARGB_8888);
            graph_canvas = new Canvas(graph);
            graph_path=new Path();
            if (left_scale != null) {
                left_scale.recycle();
            }
            left_scale=Bitmap.createBitmap(left_size, getMeasuredHeight(), Bitmap.Config.ARGB_8888);
            left_scale_canvas = new Canvas(left_scale);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        p.setColor(Color.BLACK);

        if (data.size() <=1) {
            p.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 24, getResources().getDisplayMetrics()));
            p.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("Not enough data", getMeasuredWidth()/2, getMeasuredHeight()/2, p);

            return;
        }

        p.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12, getResources().getDisplayMetrics()));

        max_zoom = 1+ (int) android.util.FloatMath.floor((float)(log((double) (max_date-min_date)/(max_date-second_to_max_date))/log(2)));

        if ((left_scale == null) || (cached_height != getMeasuredHeight()) || (cached_width != getMeasuredWidth())) {
            if (left_scale == null) {
                left_scale=Bitmap.createBitmap(left_size, getMeasuredHeight(), Bitmap.Config.ARGB_8888);
                left_scale_canvas = new Canvas(left_scale);
            }
            p.setTextAlign(Paint.Align.RIGHT);
            int last_offset, first_offset;

            // left axis is (2 points) LABEL (2 points) (horizontal line of 4 points) (vertical line of 1 point width)

            // draw the top of the scale
            left_scale_canvas.drawLine((int) android.util.FloatMath.floor(left_size - TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4 + 1, getResources().getDisplayMetrics())), 0, left_size-1, 0, p);
            left_scale_canvas.drawText(Integer.toString(max_scale),(int) android.util.FloatMath.floor(left_size - TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4 + 1 + 2, getResources().getDisplayMetrics())),
                    left_axis_r.height(), p);
            last_offset=left_axis_r.height();

            // draw the bottom of the scale
            left_scale_canvas.drawLine((int) android.util.FloatMath.floor(left_size - TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4 + 1, getResources().getDisplayMetrics())),
                    getMeasuredHeight()-bottom_size, left_size-1, getMeasuredHeight()-bottom_size, p);
            left_scale_canvas.drawText("0",(int) android.util.FloatMath.floor(left_size - TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4 + 1 + 2, getResources().getDisplayMetrics())),
                    getMeasuredHeight()-bottom_size, p);
            first_offset=getMeasuredHeight()-bottom_size-left_axis_r.height();

            // draw some intermediary values
            int int_value= (int) (max_scale-pow(10, scale_magnitude));
            while(int_value >0) {
                left_scale_canvas.drawLine((int) android.util.FloatMath.floor(left_size - TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4 + 1, getResources().getDisplayMetrics())),
                        (float) ((1.0-log_scale(int_value, max_scale)))*(getMeasuredHeight()-bottom_size), left_size-1, (float) ((1-log_scale(int_value, max_scale))*(getMeasuredHeight()-bottom_size)), p);
                if (((int) ((1-log_scale(int_value, max_scale))*(getMeasuredHeight()-bottom_size))-left_axis_r.height()/2 > last_offset) &&
                        ((int) ((1-log_scale(int_value, max_scale))*(getMeasuredHeight()-bottom_size))+left_axis_r.height()/2<first_offset)) {
                    left_scale_canvas.drawText(Integer.toString(int_value),(int) android.util.FloatMath.floor(left_size - TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4 + 1 + 2, getResources().getDisplayMetrics())),
                            (int) ((1-log_scale(int_value, max_scale))*(getMeasuredHeight()-bottom_size))+left_axis_r.height()/2, p);
                    last_offset = (int) ((1-log_scale(int_value, max_scale))*(getMeasuredHeight()-bottom_size))+left_axis_r.height()/2;
                }
                int_value -= pow(10, scale_magnitude);
            }

            // draw a line along
            left_scale_canvas.drawLine(left_size-1, 0, left_size-1, getMeasuredHeight()-bottom_size, p);
        }

        canvas.drawBitmap(left_scale, 0, 0, p);

        if ((graph == null) || (cached_height != getMeasuredHeight()) || (cached_width != getMeasuredWidth()) || (cached_zoom != current_zoom)) {
            // Erase the whole graph

            if (graph == null) {
                graph=Bitmap.createBitmap(main_size, getMeasuredHeight(), Bitmap.Config.ARGB_8888);
                graph_canvas = new Canvas(graph);
                graph_path=new Path();
            }
            graph_path.reset();
            graph_path.moveTo(0, 0);
            graph_path.lineTo(0, getMeasuredHeight());
            graph_path.lineTo(main_size-right_size,getMeasuredHeight());
            graph_path.lineTo(main_size-right_size,0);
            p.setAlpha(0);
            p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
            p.setStyle(Paint.Style.FILL);
            graph_canvas.drawPath(graph_path, p);

            p.setAlpha(0xff);
            p.setXfermode(null);

            // draw a line along the bottom
            graph_canvas.drawLine(0, getMeasuredHeight()-bottom_size, main_size-right_size, getMeasuredHeight()-bottom_size, p);

            // draw the bottom labels
            int last_offset=0, first_offset;
            long cut_off_date;
            int first_date_index=0;
            boolean first_left_aligned;
            String date_string;
            ProgressData data0;
            float score_y;

            if (current_zoom==0) {
                cut_off_date = min_date;
                date_string= DateFormat.getDateInstance(DateFormat.MEDIUM).format(dates.get(0));
                p.getTextBounds(date_string, 0, date_string.length(), r);
                first_offset=r.width();
                first_left_aligned=true;
                data0 = data.get(0);
            }
            else {
                cut_off_date = (long) (max_date - pow(2, 0-current_zoom) * ((double)(max_date-min_date)));
                for (int i=0; i<data.size(); i++) {
                    if (data.get(i).date >= cut_off_date) {
                        first_date_index=i;
                        break;
                    }
                }
                if (first_date_index == 0) {
                    data0 = data.get(0);
                }
                else {
                    Log.d(TAG, "first date index is "+first_date_index+"out of "+data.size());
                    data0 = new ProgressData();
                    data0.date = cut_off_date;
                    for (int i=0; i<5; i++) {
                        data0.num_chars_in_box[i] = (int) (data.get(first_date_index-1).num_chars_in_box[i] +
                                (float)(data.get(first_date_index).num_chars_in_box[i] - data.get(first_date_index-1).num_chars_in_box[i]) *
                                        (cut_off_date - data.get(first_date_index-1).date) / (data.get(first_date_index).date - data.get(first_date_index-1).date));
                        data0.num_chars_expired_in_box[i] = (int) (data.get(first_date_index-1).num_chars_expired_in_box[i] +
                                (float)(data.get(first_date_index).num_chars_expired_in_box[i] - data.get(first_date_index-1).num_chars_expired_in_box[i]) *
                                        (cut_off_date - data.get(first_date_index-1).date) / (data.get(first_date_index).date - data.get(first_date_index-1).date));
                    }
                }
                date_string= DateFormat.getDateInstance(DateFormat.MEDIUM).format(dates.get(first_date_index));
                p.getTextBounds(date_string, 0, date_string.length(), r);

                if (((data.get(first_date_index).date - cut_off_date)*(main_size-right_size))/(max_date-cut_off_date) <= r.width()) {
                    first_left_aligned=true;
                    first_offset=r.width();
                }
                else {
                    first_left_aligned=false;
                    first_offset= (int) (((data.get(first_date_index).date - cut_off_date)*(main_size-right_size))/(max_date-cut_off_date) + r.width()/2);
                }
            }

            // Draw the date labels
            p.setColor(Color.BLACK);
            for (int i=data.size()-1; i>=first_date_index; i--) {
                if (i==data.size()-1) {
                    p.setTextAlign(Paint.Align.RIGHT);
                    date_string= " - "+DateFormat.getDateInstance(DateFormat.MEDIUM).format(dates.get(i));
                    graph_canvas.drawLine(
                            (main_size-right_size)*((float)data.get(i).date-cut_off_date)/(max_date-cut_off_date),
                            getMeasuredHeight()-bottom_size,
                            (main_size-right_size)*((float)data.get(i).date-cut_off_date)/(max_date-cut_off_date),
                            getMeasuredHeight()-bottom_size+TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, getResources().getDisplayMetrics()), p);
                    graph_canvas.drawText(date_string, main_size-right_size,
                            getMeasuredHeight()-bottom_size+bottom_axis_r.height()+TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4+2, getResources().getDisplayMetrics()), p);
                    p.getTextBounds(date_string, 0, date_string.length(), r);
                    last_offset=main_size-right_size-r.width();
                } else if ((i>first_date_index) || (!first_left_aligned)) {
                    p.setTextAlign(Paint.Align.CENTER);
                    date_string= " - "+DateFormat.getDateInstance(DateFormat.MEDIUM).format(dates.get(i))+" - ";
                    p.getTextBounds(date_string, 0, date_string.length(), r);
                    if (i>first_date_index) {
                        if ((main_size-right_size)*((float)data.get(i).date-cut_off_date)/(max_date-cut_off_date)+r.width()/2 > last_offset)
                            continue;
                        if ((main_size-right_size)*((float)data.get(i).date-cut_off_date)/(max_date-cut_off_date)-r.width()/2 <= first_offset)
                            continue;
                    }
                    graph_canvas.drawLine(
                            (main_size-right_size)*((float)data.get(i).date-cut_off_date)/(max_date-cut_off_date),
                            getMeasuredHeight()-bottom_size,
                            (main_size-right_size)*((float)data.get(i).date-cut_off_date)/(max_date-cut_off_date),
                            getMeasuredHeight()-bottom_size+TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, getResources().getDisplayMetrics()), p);
                    graph_canvas.drawText(date_string, (main_size-right_size)*((float)data.get(i).date-cut_off_date)/(max_date-cut_off_date),
                            getMeasuredHeight()-bottom_size+bottom_axis_r.height()+TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4+2, getResources().getDisplayMetrics()), p);
                    last_offset=(int) ((main_size-right_size)*((float)data.get(i).date-cut_off_date)/(max_date-cut_off_date)-r.width()/2);
                } else {
                    p.setTextAlign(Paint.Align.LEFT);
                    date_string= DateFormat.getDateInstance(DateFormat.MEDIUM).format(dates.get(i))+" - ";
                    graph_canvas.drawLine((main_size-right_size)*((float)data.get(i).date-cut_off_date)/(max_date-cut_off_date), getMeasuredHeight()-bottom_size,
                        (main_size-right_size)*((float)data.get(i).date-cut_off_date)/(max_date-cut_off_date),
                        getMeasuredHeight()-bottom_size+TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, getResources().getDisplayMetrics()), p);
                    graph_canvas.drawText(date_string, 0,
                            getMeasuredHeight()-bottom_size+bottom_axis_r.height()+TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4+2, getResources().getDisplayMetrics()), p);
                }
            }

            // draw the graphs
            graph_path.reset();
            graph_path.moveTo(0, getMeasuredHeight() - bottom_size);
            int value[]=new int[data.size()];
            if (first_date_index>0) {
                value[0] = data0.num_chars_in_box[0]+data0.num_chars_in_box[1]+data0.num_chars_in_box[2]+data0.num_chars_in_box[3]+data0.num_chars_in_box[4];
                graph_path.lineTo(0 ,(float) ((1.0-log_scale(value[0], max_scale))*(getMeasuredHeight()-bottom_size)));
            }
            for (int i=first_date_index; i<data.size(); i++) {
                value[i] = data.get(i).num_chars_in_box[0]+data.get(i).num_chars_in_box[1]+data.get(i).num_chars_in_box[2]+data.get(i).num_chars_in_box[3]+data.get(i).num_chars_in_box[4];
                float x= (float) ((main_size-right_size)* ((i==data.size()-1)?1.0:((float)data.get(i).date-cut_off_date)/(max_date-cut_off_date)));
                float y= (float) ((1.0-log_scale(value[i], max_scale))*(getMeasuredHeight()-bottom_size));
                //Log.d(TAG, "Line to "+x+", "+y);
                graph_path.lineTo(x ,y);
            }
            graph_path.lineTo(main_size-right_size, getMeasuredHeight()-bottom_size);
            p.setColor(Color.RED);
            p.setStyle(Paint.Style.FILL);
            graph_canvas.drawPath(graph_path, p);

            graph_path.reset();
            score_y = (float) ((1.0-log_scale(value[data.size()-1], max_scale))*(getMeasuredHeight()-bottom_size));
            graph_path.moveTo(main_size-right_size+4, score_y);
            graph_path.lineTo(main_size-right_size+6+3*label_size, score_y+3*label_size+6);
            graph_path.lineTo(main_size-right_size+6+3*label_size, score_y+label_size+2);
            graph_path.lineTo(main_size-2, score_y+label_size+2);
            graph_path.lineTo(main_size-2, score_y-label_size-2);
            graph_path.lineTo(main_size-right_size+6+3*label_size, score_y-label_size-2);
            graph_path.lineTo(main_size-right_size+6+3*label_size, score_y-3*label_size-6);
            graph_path.lineTo(main_size-right_size+4, score_y);
            graph_canvas.drawPath(graph_path, p);
            p.setTextAlign(Paint.Align.RIGHT);
            p.setColor(Color.WHITE);
            graph_canvas.drawText(Integer.toString(value[data.size()-1]), main_size-4, score_y+label_size, p);

            graph_path.reset();
            graph_path.moveTo(0, getMeasuredHeight()-bottom_size);
            if (first_date_index>0) {
                value[0] -= data0.num_chars_in_box[0]+data0.num_chars_expired_in_box[1]+data0.num_chars_expired_in_box[2]+data0.num_chars_expired_in_box[3]+data0.num_chars_expired_in_box[4];
                graph_path.lineTo(0 ,(float) ((1.0-log_scale(value[0], max_scale))*(getMeasuredHeight()-bottom_size)));
            }
            for (int i=first_date_index; i<data.size(); i++) {
                value[i] -= data.get(i).num_chars_in_box[0]+data.get(i).num_chars_expired_in_box[1]+data.get(i).num_chars_expired_in_box[2]+data.get(i).num_chars_expired_in_box[3]+data.get(i).num_chars_expired_in_box[4];
                float x= (float) ((main_size-right_size)* ((i==data.size()-1)?1.0:((float)data.get(i).date-cut_off_date)/(max_date-cut_off_date)));
                float y= (float) ((1.0-log_scale(value[i], max_scale))*(getMeasuredHeight()-bottom_size));
                //Log.d(TAG, "Line to "+x+", "+y);
                graph_path.lineTo(x ,y);
            }
            graph_path.lineTo(main_size-right_size, getMeasuredHeight()-bottom_size);
            p.setColor(Color.rgb(255,128,0));
            p.setStyle(Paint.Style.FILL);
            graph_canvas.drawPath(graph_path, p);

            graph_path.reset();
            score_y = (float) ((1.0-log_scale(value[data.size()-1], max_scale))*(getMeasuredHeight()-bottom_size));
            graph_path.moveTo(main_size-right_size+4, score_y);
            graph_path.lineTo(main_size-right_size+6+3*label_size, score_y+3*label_size+6);
            graph_path.lineTo(main_size-right_size+6+3*label_size, score_y+label_size+2);
            graph_path.lineTo(main_size-2, score_y+label_size+2);
            graph_path.lineTo(main_size-2, score_y-label_size-2);
            graph_path.lineTo(main_size-right_size+6+3*label_size, score_y-label_size-2);
            graph_path.lineTo(main_size-right_size+6+3*label_size, score_y-3*label_size-6);
            graph_path.lineTo(main_size-right_size+4, score_y);
            graph_canvas.drawPath(graph_path, p);
            p.setTextAlign(Paint.Align.RIGHT);
            p.setColor(Color.WHITE);
            graph_canvas.drawText(Integer.toString(value[data.size()-1]), main_size-4, score_y+label_size, p);

            graph_path.reset();
            graph_path.moveTo(0, getMeasuredHeight()-bottom_size);
            if (first_date_index>0) {
                value[0] -= data0.num_chars_in_box[1]-data0.num_chars_expired_in_box[1];
                graph_path.lineTo(0 ,(float) ((1.0-log_scale(value[0], max_scale))*(getMeasuredHeight()-bottom_size)));
            }
            for (int i=first_date_index; i<data.size(); i++) {
                value[i] -= data.get(i).num_chars_in_box[1]-data.get(i).num_chars_expired_in_box[1];
                float x= (float) ((main_size-right_size)* ((i==data.size()-1)?1.0:((float)data.get(i).date-cut_off_date)/(max_date-cut_off_date)));
                float y= (float) ((1.0-log_scale(value[i], max_scale))*(getMeasuredHeight()-bottom_size));
                //Log.d(TAG, "Line to "+x+", "+y);
                graph_path.lineTo(x ,y);
            }
            graph_path.lineTo(main_size-right_size, getMeasuredHeight()-bottom_size);
            p.setColor(Color.rgb(0,255,0));
            p.setStyle(Paint.Style.FILL);
            graph_canvas.drawPath(graph_path, p);

            graph_path.reset();
            score_y = (float) ((1.0-log_scale(value[data.size()-1], max_scale))*(getMeasuredHeight()-bottom_size));
            graph_path.moveTo(main_size-right_size+4, score_y);
            graph_path.lineTo(main_size-right_size+6+3*label_size, score_y+3*label_size+6);
            graph_path.lineTo(main_size-right_size+6+3*label_size, score_y+label_size+2);
            graph_path.lineTo(main_size-2, score_y+label_size+2);
            graph_path.lineTo(main_size-2, score_y-label_size-2);
            graph_path.lineTo(main_size-right_size+6+3*label_size, score_y-label_size-2);
            graph_path.lineTo(main_size-right_size+6+3*label_size, score_y-3*label_size-6);
            graph_path.lineTo(main_size-right_size+4, score_y);
            graph_canvas.drawPath(graph_path, p);
            p.setTextAlign(Paint.Align.RIGHT);
            p.setColor(Color.WHITE);
            graph_canvas.drawText(Integer.toString(value[data.size()-1]), main_size-4, score_y+label_size, p);

            graph_path.reset();
            graph_path.moveTo(0, getMeasuredHeight()-bottom_size);
            if (first_date_index>0) {
                value[0] -= data0.num_chars_in_box[2]-data0.num_chars_expired_in_box[2];
                graph_path.lineTo(0 ,(float) ((1.0-log_scale(value[0], max_scale))*(getMeasuredHeight()-bottom_size)));
            }
            for (int i=first_date_index; i<data.size(); i++) {
                value[i] -= data.get(i).num_chars_in_box[2]-data.get(i).num_chars_expired_in_box[2];
                float x= (float) ((main_size-right_size)* ((i==data.size()-1)?1.0:((float)data.get(i).date-cut_off_date)/(max_date-cut_off_date)));
                float y= (float) ((1.0-log_scale(value[i], max_scale))*(getMeasuredHeight()-bottom_size));
                //Log.d(TAG, "Line to "+x+", "+y);
                graph_path.lineTo(x ,y);
            }
            graph_path.lineTo(main_size-right_size, getMeasuredHeight()-bottom_size);
            p.setColor(Color.rgb(0,170,0));
            p.setStyle(Paint.Style.FILL);
            graph_canvas.drawPath(graph_path, p);

            graph_path.reset();
            score_y = (float) ((1.0-log_scale(value[data.size()-1], max_scale))*(getMeasuredHeight()-bottom_size));
            graph_path.moveTo(main_size-right_size+4, score_y);
            graph_path.lineTo(main_size-right_size+6+3*label_size, score_y+3*label_size+6);
            graph_path.lineTo(main_size-right_size+6+3*label_size, score_y+label_size+2);
            graph_path.lineTo(main_size-2, score_y+label_size+2);
            graph_path.lineTo(main_size-2, score_y-label_size-2);
            graph_path.lineTo(main_size-right_size+6+3*label_size, score_y-label_size-2);
            graph_path.lineTo(main_size-right_size+6+3*label_size, score_y-3*label_size-6);
            graph_path.lineTo(main_size-right_size+4, score_y);
            graph_canvas.drawPath(graph_path, p);
            p.setTextAlign(Paint.Align.RIGHT);
            p.setColor(Color.WHITE);
            graph_canvas.drawText(Integer.toString(value[data.size()-1]), main_size-4, score_y+label_size, p);

            graph_path.reset();
            graph_path.moveTo(0, getMeasuredHeight()-bottom_size);
            if (first_date_index>0) {
                value[0] -= data0.num_chars_in_box[3]-data0.num_chars_expired_in_box[3];
                graph_path.lineTo(0 ,(float) ((1.0-log_scale(value[0], max_scale))*(getMeasuredHeight()-bottom_size)));
            }
            for (int i=first_date_index; i<data.size(); i++) {
                value[i] -= data.get(i).num_chars_in_box[3]-data.get(i).num_chars_expired_in_box[3];
                float x= (float) ((main_size-right_size)* ((i==data.size()-1)?1.0:((float)data.get(i).date-cut_off_date)/(max_date-cut_off_date)));
                float y= (float) ((1.0-log_scale(value[i], max_scale))*(getMeasuredHeight()-bottom_size));
                //Log.d(TAG, "Line to "+x+", "+y);
                graph_path.lineTo(x ,y);
            }
            graph_path.lineTo(main_size-right_size, getMeasuredHeight()-bottom_size);
            p.setColor(Color.rgb(0,80,0));
            p.setStyle(Paint.Style.FILL);
            graph_canvas.drawPath(graph_path, p);

            graph_path.reset();
            score_y = (float) ((1.0-log_scale(value[data.size()-1], max_scale))*(getMeasuredHeight()-bottom_size));
            graph_path.moveTo(main_size-right_size+4, score_y);
            graph_path.lineTo(main_size-right_size+6+3*label_size, score_y+3*label_size+6);
            graph_path.lineTo(main_size-right_size+6+3*label_size, score_y+label_size+2);
            graph_path.lineTo(main_size-2, score_y+label_size+2);
            graph_path.lineTo(main_size-2, score_y-label_size-2);
            graph_path.lineTo(main_size-right_size+6+3*label_size, score_y-label_size-2);
            graph_path.lineTo(main_size-right_size+6+3*label_size, score_y-3*label_size-6);
            graph_path.lineTo(main_size-right_size+4, score_y);
            graph_canvas.drawPath(graph_path, p);
            p.setTextAlign(Paint.Align.RIGHT);
            p.setColor(Color.WHITE);
            graph_canvas.drawText(Integer.toString(value[data.size()-1]), main_size-4, score_y+label_size, p);

            max_offset_x=main_size-getMeasuredWidth()+left_size;
        }

        if (reset_offset_x) {
            current_offset_x= right_size;
            reset_offset_x=false;
        }

        origin.left = main_size-current_offset_x-getMeasuredWidth()+left_size;
        origin.top=0;
        origin.right=main_size-current_offset_x;
        origin.bottom=getMeasuredHeight();

        dest.left=left_size;
        dest.top=0;
        dest.right = getMeasuredWidth();
        dest.bottom = getMeasuredHeight();

        canvas.drawBitmap(graph, origin, dest, p);

        cached_height=getMeasuredHeight();
        cached_width=getMeasuredWidth();
        cached_zoom=current_zoom;
    }
}
