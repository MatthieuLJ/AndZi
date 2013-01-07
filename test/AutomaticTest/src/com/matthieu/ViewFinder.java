package com.matthieu;

import android.view.View;
import android.widget.TextView;

import java.util.ArrayList;

public class ViewFinder {
    public static View find_view(ArrayList<View> views, int result_id, int string_view_id, String contains) {
        for (View v:views) {
            if ((result_id==0) || (v.getId()==result_id)) {
                TextView string_view = (TextView) v.findViewById(string_view_id);
                if (string_view==null)
                    continue;
                String content = (String) string_view.getText();
                if ((contains!=null) && (contains.equals(content)))
                    return v;
            }
        }
        return null;
    }
}
