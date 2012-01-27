/******************************
 * Interface for a list adapter where the query 
 * getting the results is typically a separate task that
 * can be cancelled or resumed
 */

package com.matthieu.widget;

import android.widget.ListAdapter;

public interface UpdatableListAdapter extends ListAdapter {
    public abstract void cancel();
    public abstract void resume();
    public abstract boolean is_finished();
}
