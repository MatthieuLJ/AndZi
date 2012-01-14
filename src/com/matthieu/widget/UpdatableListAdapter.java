package com.matthieu.widget;

import android.widget.ListAdapter;
import com.matthieu.HanziDB;

import java.util.ArrayList;

public interface UpdatableListAdapter extends ListAdapter {
    public abstract void cancel();
    public abstract void update_content(ArrayList<HanziDB.StringElement> elements);
    public abstract void update_content(String element);
}
