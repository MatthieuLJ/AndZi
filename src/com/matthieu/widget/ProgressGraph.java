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
import com.matthieu.ProgressDB;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;

import static java.lang.Math.ceil;
import static java.lang.Math.floor;
import static java.lang.Math.pow;

public class ProgressGraph extends View {
    ArrayList<ProgressDB.ProgressData> data=null;

    private static final String TAG="ProgressGraph";

    public ProgressGraph(Context context) {
        super(context);
    }

    public ProgressGraph(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ProgressGraph(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    private long min_date, max_date;
    private int max_score, max_scale, scale_magnitude;
    public void set_data(ArrayList<ProgressDB.ProgressData> data) {
        // assumes the data is sorted chronologically
        this.data=data;
        if (data == null) {
            return;
        }
        min_date=data.get(0).date;
        max_date=data.get(data.size()-1).date;
        max_score= data.get(0).num_chars_in_box[0]+data.get(0).num_chars_in_box[1]+data.get(0).num_chars_in_box[2]+data.get(0).num_chars_in_box[3]+data.get(0).num_chars_in_box[4];
        for (ProgressDB.ProgressData d : data) {
            int num_chars=d.num_chars_in_box[0]+d.num_chars_in_box[1]+d.num_chars_in_box[2]+d.num_chars_in_box[3]+d.num_chars_in_box[4];
            if (num_chars>max_score)
                max_score=num_chars;
        }
        scale_magnitude = 1;
        while (max_score > pow(10,scale_magnitude)) {
            scale_magnitude++;
        }
        scale_magnitude--;
        max_scale = (int) ((1+ (int)floor(max_score/pow(10, scale_magnitude)))*pow(10, scale_magnitude));

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
            current_offset_x=0;
            this.invalidate();
        }
    }
    public boolean can_zoom_out() {
        return (current_zoom>0);
    }
    public void zoom_out() {
        if (current_zoom>0) {
            current_zoom--;
            current_offset_x=0;
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

    private double log_scale(double y) {
        return (Math.pow(10,y/max_scale)-1)/9;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Paint p=new Paint();
        p.setColor(Color.BLACK);
        Rect r=new Rect();

        if ((data == null) || (data.size() <=1)) {
            p.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 24, getResources().getDisplayMetrics()));
            p.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("Not enough data", getMeasuredWidth()/2, getMeasuredHeight()/2, p);

            return;
        }

        Rect left_axis_r=new Rect();
        Rect bottom_axis_r=new Rect();
        int bottom_size, left_size;

        p.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12, getResources().getDisplayMetrics()));

        String date_label="01/01/01";
        p.getTextBounds(date_label, 0, date_label.length(), bottom_axis_r);
        bottom_size= (int) ceil(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2 + 2 + 4 + 1, getResources().getDisplayMetrics())) + bottom_axis_r.height();

        String max_label=Integer.toString(max_scale);
        p.getTextBounds(max_label, 0,max_label.length(), left_axis_r);
        left_size= (int) ceil(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2 + 2 + 4 + 1, getResources().getDisplayMetrics())) + left_axis_r.width();

        if ((left_scale == null) || (cached_height != getMeasuredHeight()) || (cached_width != getMeasuredWidth())) {
            if (left_scale != null) {
                left_scale.recycle();
                left_scale=null;
            }
            left_scale=Bitmap.createBitmap(left_size, getMeasuredHeight(), Bitmap.Config.ARGB_8888);
            Canvas left_scale_canvas = new Canvas(left_scale);
            p.setTextAlign(Paint.Align.RIGHT);
            int last_offset=0, first_offset;

            // draw the top of the scale
            left_scale_canvas.drawLine((int) floor(left_size - TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4 + 1, getResources().getDisplayMetrics())), 0, left_size-1, 0, p);
            left_scale_canvas.drawText(Integer.toString(max_scale),(int) floor(left_size - TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4 + 1 + 2, getResources().getDisplayMetrics())),
                    left_axis_r.height(), p);
            last_offset=left_axis_r.height();

            // draw the bottom of the scale
            left_scale_canvas.drawLine((int) floor(left_size - TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4 + 1, getResources().getDisplayMetrics())),
                    getMeasuredHeight()-bottom_size, left_size-1, getMeasuredHeight()-bottom_size, p);
            left_scale_canvas.drawText("0",(int) floor(left_size - TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4 + 1 + 2, getResources().getDisplayMetrics())),
                    getMeasuredHeight()-bottom_size, p);
            first_offset=getMeasuredHeight()-bottom_size-left_axis_r.height();

            // draw some intermediary values
            int int_value= (int) (max_scale-pow(10, scale_magnitude));
            while(int_value >0) {
                left_scale_canvas.drawLine((int) floor(left_size - TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4 + 1, getResources().getDisplayMetrics())),
                        (float) ((1.0-log_scale(int_value)))*(getMeasuredHeight()-bottom_size), left_size-1, (float) ((1-log_scale(int_value))*(getMeasuredHeight()-bottom_size)), p);
                if (((int) ((1-log_scale(int_value))*(getMeasuredHeight()-bottom_size))-left_axis_r.height()/2 > last_offset) &&
                        ((int) ((1-log_scale(int_value))*(getMeasuredHeight()-bottom_size))+left_axis_r.height()/2<first_offset)) {
                    left_scale_canvas.drawText(Integer.toString(int_value),(int) floor(left_size - TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4 + 1 + 2, getResources().getDisplayMetrics())),
                            (int) ((1-log_scale(int_value))*(getMeasuredHeight()-bottom_size))+left_axis_r.height()/2, p);
                    last_offset = (int) ((1-log_scale(int_value))*(getMeasuredHeight()-bottom_size))+left_axis_r.height()/2;
                }
                int_value -= pow(10, scale_magnitude);
            }

            // draw a line along
            left_scale_canvas.drawLine(left_size-1, 0, left_size-1, getMeasuredHeight()-bottom_size, p);
        }

        canvas.drawBitmap(left_scale, 0, 0, p);

        int right_size = (getMeasuredWidth()-left_size)*((int)pow(2, current_zoom));

        if ((graph == null) || (cached_height != getMeasuredHeight()) || (cached_width != getMeasuredWidth()) || (cached_zoom != current_zoom)) {
            if(graph != null) {
                graph.recycle();
                graph=null;
            }
            try {
                graph=Bitmap.createBitmap(right_size, getMeasuredHeight(), Bitmap.Config.ARGB_8888);
            } catch (OutOfMemoryError e) {
                Log.e(TAG, "Not enough memory...");
                current_zoom--;
                max_zoom=current_zoom;
                this.invalidate();
                return;
            }
            Canvas graph_canvas = new Canvas(graph);
            Path graph_path=new Path();

            // draw a line along
            graph_canvas.drawLine(0, getMeasuredHeight()-bottom_size, right_size, getMeasuredHeight()-bottom_size, p);

            // draw the bottom labels
            int last_offset=0, first_offset;
            String date_string= DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(data.get(0).date));
            p.getTextBounds(date_string, 0, date_string.length(), r);
            first_offset=r.width();
            for (int i=data.size()-1; i>=0; i--) {
                graph_canvas.drawLine((right_size)*((float)data.get(i).date-min_date)/(max_date-min_date), getMeasuredHeight()-bottom_size,
                        (right_size)*((float)data.get(i).date-min_date)/(max_date-min_date),
                        getMeasuredHeight()-bottom_size+TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, getResources().getDisplayMetrics()), p);
                if (i==data.size()-1) {
                    p.setTextAlign(Paint.Align.RIGHT);
                    date_string= DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(data.get(i).date));
                    graph_canvas.drawText(date_string, right_size,
                            getMeasuredHeight()-bottom_size+bottom_axis_r.height()+TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4+2, getResources().getDisplayMetrics()), p);
                    p.getTextBounds(date_string, 0, date_string.length(), r);
                    last_offset=right_size-r.width();
                } else if (i>0) {
                    p.setTextAlign(Paint.Align.CENTER);
                    date_string= DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(data.get(i).date));
                    p.getTextBounds(date_string, 0, date_string.length(), r);
                    if ((right_size)*((float)data.get(i).date-min_date)/(max_date-min_date)+r.width()/2 > last_offset)
                        continue;
                    if ((right_size)*((float)data.get(i).date-min_date)/(max_date-min_date)-r.width()/2 <= first_offset)
                        continue;
                    graph_canvas.drawText(date_string, (right_size)*((float)data.get(i).date-min_date)/(max_date-min_date),
                            getMeasuredHeight()-bottom_size+bottom_axis_r.height()+TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4+2, getResources().getDisplayMetrics()), p);
                    last_offset=(int) ((right_size)*((float)data.get(i).date-min_date)/(max_date-min_date)-r.width()/2);
                } else {
                    p.setTextAlign(Paint.Align.LEFT);
                    date_string= DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(data.get(i).date));
                    graph_canvas.drawText(date_string, 0,
                            getMeasuredHeight()-bottom_size+bottom_axis_r.height()+TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4+2, getResources().getDisplayMetrics()), p);
                }
            }

            // draw the graphs
            graph_path.reset();
            graph_path.moveTo(0, getMeasuredHeight()-bottom_size);
            double value[]=new double[data.size()];
            for (int i=0; i<data.size(); i++) {
                value[i] = data.get(i).num_chars_in_box[0]+data.get(i).num_chars_in_box[1]+data.get(i).num_chars_in_box[2]+data.get(i).num_chars_in_box[3]+data.get(i).num_chars_in_box[4];
                float x=(right_size)*((float)data.get(i).date-min_date)/(max_date-min_date);
                float y= (float) ((1.0-log_scale(value[i]))*(getMeasuredHeight()-bottom_size));
                //Log.d(TAG, "Line to "+x+", "+y);
                graph_path.lineTo(x ,y);
            }
            graph_path.lineTo(right_size, getMeasuredHeight()-bottom_size);
            p.setColor(Color.RED);
            p.setStyle(Paint.Style.FILL);
            graph_canvas.drawPath(graph_path, p);

            graph_path.reset();
            graph_path.moveTo(0, getMeasuredHeight()-bottom_size);
            for (int i=0; i<data.size(); i++) {
                value[i] -= data.get(i).num_chars_in_box[0]+data.get(i).num_chars_expired_in_box[1]+data.get(i).num_chars_expired_in_box[2]+data.get(i).num_chars_expired_in_box[3]+data.get(i).num_chars_expired_in_box[4];
                float x=(right_size)*((float)data.get(i).date-min_date)/(max_date-min_date);
                float y= (float) ((1.0-log_scale(value[i]))*(getMeasuredHeight()-bottom_size));
                //Log.d(TAG, "Line to "+x+", "+y);
                graph_path.lineTo(x ,y);
            }
            graph_path.lineTo(right_size, getMeasuredHeight()-bottom_size);
            p.setColor(Color.rgb(255,128,0));
            p.setStyle(Paint.Style.FILL);
            graph_canvas.drawPath(graph_path, p);

            graph_path.reset();
            graph_path.moveTo(0, getMeasuredHeight()-bottom_size);
            for (int i=0; i<data.size(); i++) {
                value[i] -= data.get(i).num_chars_in_box[1]-data.get(i).num_chars_expired_in_box[1];
                float x=(right_size)*((float)data.get(i).date-min_date)/(max_date-min_date);
                float y= (float) ((1.0-log_scale(value[i]))*(getMeasuredHeight()-bottom_size));
                //Log.d(TAG, "Line to "+x+", "+y);
                graph_path.lineTo(x ,y);
            }
            graph_path.lineTo(right_size, getMeasuredHeight()-bottom_size);
            p.setColor(Color.rgb(0,255,0));
            p.setStyle(Paint.Style.FILL);
            graph_canvas.drawPath(graph_path, p);

            graph_path.reset();
            graph_path.moveTo(0, getMeasuredHeight()-bottom_size);
            for (int i=0; i<data.size(); i++) {
                value[i] -= data.get(i).num_chars_in_box[2]-data.get(i).num_chars_expired_in_box[2];
                float x=(right_size)*((float)data.get(i).date-min_date)/(max_date-min_date);
                float y= (float) ((1.0-log_scale(value[i]))*(getMeasuredHeight()-bottom_size));
                //Log.d(TAG, "Line to "+x+", "+y);
                graph_path.lineTo(x ,y);
            }
            graph_path.lineTo(right_size, getMeasuredHeight()-bottom_size);
            p.setColor(Color.rgb(0,170,0));
            p.setStyle(Paint.Style.FILL);
            graph_canvas.drawPath(graph_path, p);

            graph_path.reset();
            graph_path.moveTo(0, getMeasuredHeight()-bottom_size);
            for (int i=0; i<data.size(); i++) {
                value[i] -= data.get(i).num_chars_in_box[3]-data.get(i).num_chars_expired_in_box[3];
                float x=(right_size)*((float)data.get(i).date-min_date)/(max_date-min_date);
                float y= (float) ((1.0-log_scale(value[i]))*(getMeasuredHeight()-bottom_size));
                //Log.d(TAG, "Line to "+x+", "+y);
                graph_path.lineTo(x ,y);
            }
            graph_path.lineTo(right_size, getMeasuredHeight()-bottom_size);
            p.setColor(Color.rgb(0,80,0));
            p.setStyle(Paint.Style.FILL);
            graph_canvas.drawPath(graph_path, p);

            max_offset_x=right_size-getMeasuredWidth()+left_size;
        }

        if (current_zoom == 0)
            canvas.drawBitmap(graph, left_size, 0, p);
        else {
            Rect origin=new Rect(right_size-current_offset_x-getMeasuredWidth()+left_size ,0, right_size-current_offset_x, getMeasuredHeight());
            Rect dest = new Rect(left_size, 0, getMeasuredWidth(), getMeasuredHeight());
            canvas.drawBitmap(graph, origin, dest, p);
        }

        cached_height=getMeasuredHeight();
        cached_width=getMeasuredWidth();
        cached_zoom=current_zoom;
    }
}
