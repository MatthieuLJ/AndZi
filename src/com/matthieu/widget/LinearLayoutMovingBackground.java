package com.matthieu.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.LinearLayout;

import java.io.IOException;
import java.io.InputStream;

public class LinearLayoutMovingBackground extends LinearLayout {
    int background_id=-1;
    int background_saved_id=-1;
    int saved_width=-1, saved_height=-1;

    private final static String TAG="LinearLayoutMovingBackground";

    public LinearLayoutMovingBackground(Context context) {
        super(context);
    }

    public LinearLayoutMovingBackground(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @SuppressLint({"NewApi"})
    public LinearLayoutMovingBackground(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @SuppressLint({"NewApi"})
    private void set_new_background() {
        if (background_id == -1)
            return;

        if (getWidth()==0 || getHeight()==0)
            return;

        if ((saved_height == getHeight()) && (saved_width == getWidth()) && (background_id==background_saved_id))
            return;

        InputStream is;

        try {
            is = getContext().getResources().openRawResource(background_id);

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(is, null, options);
            int imageHeight = options.outHeight;
            int imageWidth = options.outWidth;

            float zoom_level = ((float)imageHeight) / getHeight(); // we are always in 'fitY' mode
            int random_offset_max = (int) (imageWidth - (getWidth() * zoom_level));
            int offset = (int) (Math.random() * random_offset_max);
            is.reset();
            BitmapDrawable new_background;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD_MR1) {
                BitmapRegionDecoder brd = BitmapRegionDecoder.newInstance(is, true);
                options.inJustDecodeBounds = false;

                new_background = new BitmapDrawable(getResources(), brd.decodeRegion(new Rect(offset, 0, (int) (offset + (getWidth() * zoom_level)), imageHeight), options));
                brd.recycle();
            } else {
                Bitmap image = Bitmap.createBitmap(BitmapFactory.decodeStream(is), offset, 0, (int) (getWidth() * zoom_level), imageHeight) ;
                new_background = new BitmapDrawable(image);
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
                this.setBackground(new_background);
            else
                this.setBackgroundDrawable(new_background);

            is.close();
        } catch (IOException e) {
            Log.e(TAG, "Cannot decode: "+e.getMessage());
            background_id = -1;
            return;
        }

        saved_height = getHeight();
        saved_width = getWidth();
        background_saved_id = background_id;
    }

    public void setBackgroundAsset(int res_id) {
        background_id = res_id;
        set_new_background();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        set_new_background();
    }
}
