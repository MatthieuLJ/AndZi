package com.matthieu.widget;

import android.widget.ListAdapter;

public interface UpdatableListAdapter extends ListAdapter {
    public abstract void cancel();
    public abstract void resume();
    public abstract boolean is_finished();
}
