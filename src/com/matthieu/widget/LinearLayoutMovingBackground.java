package com.matthieu.widget;

import android.annotation.SuppressLint;
import android.content.Context;
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
    String background_path;
    String background_saved_path;
    int saved_width=-1, saved_height=-1;

    private final static String TAG="LinearLayoutMovingBackground";

    public LinearLayoutMovingBackground(Context context) {
        super(context);
    }

    public LinearLayoutMovingBackground(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public LinearLayoutMovingBackground(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @SuppressLint({"NewApi"})
    private void set_new_background() {
        if (background_path == null)
            return;

        if (getWidth()==0 || getHeight()==0)
            return;

        if ((saved_height == getHeight()) && (saved_width == getWidth()) && (background_saved_path != null) && (background_path.equals(background_saved_path)))
            return;

        InputStream is;

        try {
            is = getContext().getAssets().open(background_path);

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(is, null, options);
            int imageHeight = options.outHeight;
            int imageWidth = options.outWidth;

            float zoom_level = ((float)imageHeight) / getHeight(); // we are always in 'fitY' mode
            int random_offset_max = (int) (imageWidth - (getWidth() * zoom_level));
            int offset = (int) (Math.random() * random_offset_max);
            is.reset();
            BitmapRegionDecoder brd = BitmapRegionDecoder.newInstance(is, true);

            options.inJustDecodeBounds = false;
            BitmapDrawable new_background = new BitmapDrawable(getResources(), brd.decodeRegion(new Rect(offset, 0, (int) (offset + (getWidth() * zoom_level)), imageHeight), options));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
                this.setBackground(new_background);
            else
                this.setBackgroundDrawable(new_background);

            brd.recycle();
            is.close();
        } catch (IOException e) {
            Log.e(TAG, "Cannot decode: "+e.getMessage());
            background_path = null;
            return;
        }

        saved_height = getHeight();
        saved_width = getWidth();
        background_saved_path = background_path;
    }

    public void setBackgroundAsset(String asset_path) {
        background_path = asset_path;
        set_new_background();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        set_new_background();
    }
}
