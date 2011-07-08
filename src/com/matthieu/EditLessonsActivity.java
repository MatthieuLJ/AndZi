package com.matthieu;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import java.util.ArrayList;
import java.util.Arrays;

public class EditLessonsActivity extends Activity {

    protected HanziDB myDB=null;

    protected ArrayList<Lesson> current_lessons;
    protected String edited_lesson;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        myDB = ((AndZiApplication) getApplication()).myDB;

        super.onCreate(savedInstanceState);

        current_lessons = new ArrayList<Lesson>();
        edited_lesson="";

        setContentView(R.layout.edit_lessons);
        ListView list = (ListView) findViewById(R.id.lesson_list);
        list.setAdapter(new LessonArrayAdapter());

        new InitLessons().execute();
    }

    private class InitLessons extends AsyncTask<Void, Void, Void> {
        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);

            ListView lv = (ListView) findViewById(R.id.lesson_list);
            if (lv != null) {
                ((LessonArrayAdapter)lv.getAdapter()).notifyDataSetChanged();
            }
        }

        @Override
        protected Void doInBackground(Void... voids) {
            ArrayList<String> existing_lessons = myDB.getLessons();

            for (String lesson : existing_lessons) {
                int num = myDB.getVocabularyInLessons(new ArrayList<String>(Arrays.asList(new String[] {lesson}))).size();
                current_lessons.add(new Lesson(lesson, num));
            }

            return null;
        }
    }

    private class Lesson {
        public String current_label;
        public String previous_label;
        public boolean deleted;
        public int num_items;

        public Lesson(String label) {
            current_label = label;
            previous_label="";
            deleted = false;
            num_items = 0;
        }

        public Lesson(String label, int num) {
            current_label = label;
            previous_label="";
            deleted = false;
            num_items = num;
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
                more_info = more_info + "has " + le.num_items + "words";
            }

            if (more_info.equals("")) {
                row.findViewById(R.id.more_info_lesson_label).setVisibility(View.GONE);
            }
            else {
                TextView more_tview = (TextView) row.findViewById(R.id.more_info_lesson_label);
                more_tview.setText(more_info);
                more_tview.setVisibility(View.VISIBLE);
            }

            TextView label = (TextView) row.findViewById(R.id.lesson_label);
            label.setText(le.current_label);
            label.setTag(R.id.lesson_item);
            return row;
        }

        @Override
        public int getCount() {
            return current_lessons.size();
        }
    }

    public void handle_add_lesson(View v) {
        int i;
        String new_lesson= ((EditText) findViewById(R.id.edit_new_lesson)).getText().toString();
        if (new_lesson.equals("")) {
            return;
        }
        for (i=current_lessons.size()-1; i>=0; i--) {
            if (current_lessons.get(i).current_label.equals(new_lesson)) {
                if (current_lessons.get(i).deleted) {
                    current_lessons.get(i).deleted=false;
                }
                edited_lesson = "";
                ((TextView) findViewById(R.id.new_lesson_label)).setText("New word:");
                ((EditText) findViewById(R.id.edit_new_lesson)).setText("");
                return; // do not do anything if it is already in the list...
            }
        }

        if (edited_lesson.equals("")) {
            current_lessons.add(new Lesson(new_lesson));
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
        ((TextView) findViewById(R.id.new_lesson_label)).setText("New word:");
        ListView lv = (ListView) findViewById(R.id.lesson_list);
        if (lv != null) {
            ((LessonArrayAdapter)lv.getAdapter()).notifyDataSetChanged();
        }
        ((EditText) findViewById(R.id.edit_new_lesson)).setText("");
    }

    public void handle_lesson_click(View v) {
        TextView tview = (TextView) v;
        Lesson le = (Lesson) tview.getTag(R.id.lesson_item);
        EditText edit = (EditText) findViewById(R.id.edit_new_lesson);

        edit.setText(tview.getText());

        edited_lesson=tview.getText().toString();
        ((TextView) findViewById(R.id.new_lesson_label)).setText("Edit word:");

        edit.requestFocus();
    }
}
