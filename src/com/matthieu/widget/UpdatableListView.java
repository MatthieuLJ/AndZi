package com.matthieu.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ListView;

public class UpdatableListView extends ListView {
    private static final String TAG="UpdatableListView";

    public UpdatableListView(Context context) {
        super(context);
    }

    public UpdatableListView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public UpdatableListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        Log.d(TAG, "Cancelling search !");
        ((UpdatableListAdapter) getAdapter()).cancel();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if ((visibility==GONE) || (visibility==INVISIBLE)) {
            Log.d(TAG, "Cancelling search (2)!");
            ((UpdatableListAdapter) getAdapter()).cancel();
        }

    }
}
