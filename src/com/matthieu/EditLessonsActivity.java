package com.matthieu;

import android.app.Activity;
import android.gesture.Gesture;
import android.gesture.GestureOverlayView;
import android.graphics.Paint;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.matthieu.widget.TouchListView;

import java.util.ArrayList;
import java.util.Arrays;

public class EditLessonsActivity extends Activity {
    protected HanziDB myDB=null;

    protected ArrayList<Lesson> current_lessons;
    protected String edited_lesson;

    private static final String TAG = "EditLessonsActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        myDB = ((AndZiApplication) getApplication()).myDB;

        super.onCreate(savedInstanceState);

        current_lessons = new ArrayList<Lesson>();
        edited_lesson="";

        setContentView(R.layout.edit_lessons);
        TouchListView list = (TouchListView) findViewById(R.id.lesson_list);
        list.setAdapter(new LessonArrayAdapter());
        list.setRemoveListener(new TouchListView.RemoveListener() {
            public void remove(int which) {
                current_lessons.get(which).deleted=true;
            }
        });
        list.setDropListener(new TouchListView.DropListener() {
            public void drop(int from, int to) {
                Lesson le = current_lessons.get(from);
                Log.d(TAG, "Moving from "+from+" to "+to);
                current_lessons.remove(from);
                current_lessons.add(to, le);
                TouchListView lv = (TouchListView) findViewById(R.id.lesson_list);
                ((LessonArrayAdapter)lv.getAdapter()).notifyDataSetChanged();
            }
        });

        ((Button) findViewById(R.id.edit_lesson_delete)).setEnabled(false);

        new InitLessons().execute();
    }

    private class InitLessons extends AsyncTask<Void, Void, Void> {
        ArrayList<Lesson> tmp_lessons;
        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);

            current_lessons = tmp_lessons;
            TouchListView lv = (TouchListView) findViewById(R.id.lesson_list);
            if (lv != null) {
                ((LessonArrayAdapter)lv.getAdapter()).notifyDataSetChanged();
            }
        }

        @Override
        protected Void doInBackground(Void... voids) {
            tmp_lessons = new ArrayList<Lesson>();
            ArrayList<String> existing_lessons = myDB.getLessons();

            for (String lesson : existing_lessons) {
                int num = myDB.getVocabularyInLessons(new ArrayList<String>(Arrays.asList(new String[] {lesson}))).size();
                tmp_lessons.add(new Lesson(lesson, Lesson.EXISTING, num));
            }

            return null;
        }
    }

    private class Lesson {
        public String current_label;
        public String previous_label;
        public boolean deleted;
        public int num_items;
        public int origin; // 1 is pre-existing, 2 is new

        public static final int EXISTING=1;
        public static final int NEW=2;

        public Lesson(String label, int from) {
            current_label = label;
            previous_label="";
            deleted = false;
            num_items = 0;
            origin = from;
        }

        public Lesson(String label, int from, int num) {
            current_label = label;
            previous_label="";
            deleted = false;
            num_items = num;
            origin = from;
        }
    }

    private class LessonArrayAdapter extends ArrayAdapter<Object> {
        public LessonArrayAdapter() {
            super(EditLessonsActivity.this, R.layout.edit_lesson_list_item, current_lessons.toArray());
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row;
            if (convertView == null) {
                LayoutInflater inflater = getLayoutInflater();
                row = inflater.inflate(R.layout.edit_lesson_list_item, parent, false);
            }
            else {
                row = convertView;
            }

            Lesson le = current_lessons.get(position);

            String more_info = "";

            if (!le.previous_label.equals("")) {
                more_info = "was : " + le.previous_label;
            }

            if (le.num_items != 0) {
                if (!more_info.equals(""))
                    more_info = more_info + ", ";
                more_info = more_info + "has " + le.num_items + " chinese word";
                if (le.num_items > 1)
                    more_info += "s";
            }

            if (more_info.equals("")) {
                row.findViewById(R.id.lesson_more_info_label).setVisibility(View.GONE);
            }
            else {
                TextView more_tview = (TextView) row.findViewById(R.id.lesson_more_info_label);
                more_tview.setText(more_info);
                more_tview.setVisibility(View.VISIBLE);
            }

            TextView label = (TextView) row.findViewById(R.id.lesson_label);
            label.setText(le.current_label);
            if (le.deleted) {
                label.setPaintFlags(label.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            }
            else {
                label.setPaintFlags(label.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            }
            label.setTag(R.id.lesson_item, le);

            return row;
        }

        private class StrikeGesture implements GestureOverlayView.OnGesturePerformedListener {

            public void onGesturePerformed(GestureOverlayView gestureOverlayView, Gesture gesture) {
                Log.d(TAG, "Strike !");
                Lesson le = (Lesson) gestureOverlayView.getTag(R.id.lesson_item);
                if (le.origin == Lesson.NEW) {
                    current_lessons.remove(le);
                }
                else if (!le.deleted) {
                    le.deleted=true;
                }
                else {
                    return;
                }
                edited_lesson = "";
                ((TextView) findViewById(R.id.new_lesson_label)).setText("New lesson:");
                ((EditText) findViewById(R.id.edit_new_lesson)).setText("");
                TouchListView lv = (TouchListView) findViewById(R.id.lesson_list);
                ((LessonArrayAdapter)lv.getAdapter()).notifyDataSetChanged();
                Button delete_button = (Button) findViewById(R.id.edit_lesson_delete);
                delete_button.setText("Delete");
                delete_button.setEnabled(false);
            }
        }

        @Override
        public int getCount() {
            return current_lessons.size();
        }
    }

    public void handle_delete_lesson(View v) {
        if (edited_lesson.equals("")) {
            // that's a problem !
            Button delete_button = (Button) findViewById(R.id.edit_lesson_delete);
            delete_button.setText("Delete");
            delete_button.setEnabled(false);
        }
        for (int i=current_lessons.size()-1; i>=0; i--) {
            if (current_lessons.get(i).current_label.equals(edited_lesson)) {
                current_lessons.get(i).deleted = !current_lessons.get(i).deleted;
            }
        }
        edited_lesson = "";
        ((TextView) findViewById(R.id.new_lesson_label)).setText("New lesson:");
        ((EditText) findViewById(R.id.edit_new_lesson)).setText("");
        TouchListView lv = (TouchListView) findViewById(R.id.lesson_list);
        ((LessonArrayAdapter)lv.getAdapter()).notifyDataSetChanged();
        Button delete_button = (Button) findViewById(R.id.edit_lesson_delete);
        delete_button.setText("Delete");
        delete_button.setEnabled(false);
    }

    public void handle_add_lesson(View v) {
        int i;
        String new_lesson= ((EditText) findViewById(R.id.edit_new_lesson)).getText().toString();
        if (new_lesson.equals("")) {
            return;
        }
        for (i=current_lessons.size()-1; i>=0; i--) {
            if (current_lessons.get(i).current_label.equals(new_lesson)) {
                /*
                if (current_lessons.get(i).deleted) {
                    current_lessons.get(i).deleted=false;
                }
                */
                edited_lesson = "";
                ((TextView) findViewById(R.id.new_lesson_label)).setText("New lesson:");
                ((EditText) findViewById(R.id.edit_new_lesson)).setText("");
                return; // do not do anything if it is already in the list...
            }
        }

        if (edited_lesson.equals("")) {
            current_lessons.add(new Lesson(new_lesson, Lesson.NEW));
        } else {
            for (i=current_lessons.size()-1; i>=0; i--) {
                if (current_lessons.get(i).current_label.equals(edited_lesson)) {
                    if (current_lessons.get(i).previous_label.equals(new_lesson)) {
                        current_lessons.get(i).previous_label="";
                    } else if (current_lessons.get(i).previous_label.equals("")) {
                        current_lessons.get(i).previous_label = current_lessons.get(i).current_label;
                    }
                    current_lessons.get(i).current_label = new_lesson;
                }
            }
        }
        edited_lesson = "";
        Button delete_button = (Button) findViewById(R.id.edit_lesson_delete);
        delete_button.setText("Delete");
        delete_button.setEnabled(false);
        ((TextView) findViewById(R.id.new_lesson_label)).setText("New lesson:");

        TouchListView lv = (TouchListView) findViewById(R.id.lesson_list);
        ((LessonArrayAdapter)lv.getAdapter()).notifyDataSetChanged();

        ((EditText) findViewById(R.id.edit_new_lesson)).setText("");
    }

    public void handle_lesson_click(View v) {
        TextView tview = (TextView) v;
        Lesson le = (Lesson) tview.getTag(R.id.lesson_item);
        EditText edit = (EditText) findViewById(R.id.edit_new_lesson);

        edit.setText(tview.getText());

        edited_lesson=tview.getText().toString();
        ((TextView) findViewById(R.id.new_lesson_label)).setText("Edit lesson:");

        Button delete_button = (Button) findViewById(R.id.edit_lesson_delete);
        if (le.deleted) {
            delete_button.setText("Undelete");
        }
        else {
            delete_button.setText("Delete");
        }
        delete_button.setEnabled(true);

        edit.requestFocus();
    }

    public void handle_cancel(View v) {
        finish();
    }

    public void handle_ok(View v) {
        ArrayList<String> lesson_order=new ArrayList<String>();

        for(Lesson le : current_lessons) {
            if (le.origin == Lesson.NEW) {
                if (le.deleted) {
                    continue;
                }
                myDB.addLesson(le.current_label);
            }
            if (!le.previous_label.equals("")) {
                if (le.deleted) {
                    myDB.delLesson(le.previous_label);
                }
                else {
                    myDB.renameLesson(le.previous_label, le.current_label);
                }
            }
            else {
                if (le.deleted) {
                    myDB.delLesson(le.current_label);
                }
            }
            if (!le.deleted) {
                lesson_order.add(le.current_label);
            }
        }
        myDB.sortLessons(lesson_order);
        finish();
    }
}
