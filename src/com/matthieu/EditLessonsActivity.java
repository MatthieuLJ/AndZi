package com.matthieu;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Paint;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.*;
import android.widget.*;
import com.matthieu.widget.TouchListView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.StringTokenizer;

public class EditLessonsActivity extends Activity {
    protected HanziDB myDB=null;

    protected ArrayList<Lesson> current_lessons;
    protected ArrayList<String> current_lesson_filter;
    protected boolean filter_on;

    private static final String TAG = "EditLessonsActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        myDB = ((AndZiApplication) getApplication()).getMyDB();

        super.onCreate(savedInstanceState);

        current_lessons = new ArrayList<Lesson>();
        filter_on = myDB.isFilterEnabled();

        setContentView(R.layout.edit_lessons);
        TouchListView list = (TouchListView) findViewById(R.id.lesson_list);
        list.setAdapter(new LessonArrayAdapter());
        list.setDropListener(new TouchListView.DropListener() {
            public void drop(int from, int to) {
                if (from == 0)
                    return;
                if (to==0)
                    to=1;
                Lesson le = current_lessons.get(from-1);
                Log.d(TAG, "Moving from " + from + " to " + to);
                current_lessons.remove(from-1);
                current_lessons.add(to-1, le);
                ArrayList<String> lesson_order=new ArrayList<String>();
                for (Lesson l:current_lessons)
                    lesson_order.add(l.label);
                myDB.sortLessons(lesson_order);
                TouchListView lv = (TouchListView) findViewById(R.id.lesson_list);
                ((LessonArrayAdapter) lv.getAdapter()).notifyDataSetChanged();
            }
        });
        registerForContextMenu(list);

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
            current_lesson_filter = myDB.getLessonsToFilter();
            tmp_lessons = new ArrayList<Lesson>();
            ArrayList<String> existing_lessons = myDB.getLessons();

            for (String lesson : existing_lessons) {
                int num = myDB.getVocabularyInLessons(new ArrayList<String>(Arrays.asList(lesson))).size();
                Lesson new_lesson = new Lesson(lesson);
                new_lesson.state = Lesson.EXISTING;
                if (current_lesson_filter.contains(lesson))
                    new_lesson.state|=Lesson.FILTERED_ENABLED;
                new_lesson.num_items=num;
                tmp_lessons.add(new_lesson);
            }

            return null;
        }
    }

    private class Lesson {
        public String label;
        public int num_items;
        public int state;

        public String old_label="";

        public static final int EXISTING=1;
        public static final int NEW=2;
        public static final int RENAMED=4;
        public static final int FILTERED_ENABLED=8;

        public Lesson(String l) {
            label = l;
            num_items = 0;
            state = 0;
        }
    }

    private class LessonArrayAdapter extends ArrayAdapter<Object> {
        public LessonArrayAdapter() {
            super(EditLessonsActivity.this, R.layout.edit_lesson_list_item, current_lessons.toArray());
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row;

            if (position == 0) {
                if ((convertView != null) && ((Integer)convertView.getTag(R.id.lesson_list_view_inflated)==R.layout.edit_lesson_list_new_item)) {
                    row = convertView;
                }
                else {
                    LayoutInflater inflater = getLayoutInflater();
                    row = inflater.inflate(R.layout.edit_lesson_list_new_item, parent, false);
                    row.setTag(R.id.lesson_list_view_inflated, R.layout.edit_lesson_list_new_item);
                }

                Button new_lesson_ok = (Button) row.findViewById(R.id.edit_new_lesson_ok);
                EditText new_lesson_field = (EditText) row.findViewById(R.id.edit_new_lesson);
                new_lesson_ok.setTag(R.id.edit_new_lesson, new_lesson_field);
                new_lesson_ok.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View view) {
                        EditText new_lesson_field = (EditText)view.getTag(R.id.edit_new_lesson);
                        String new_lesson_label = new_lesson_field.getText().toString();
                        new_lesson_field.setText("");
                        if (new_lesson_label.equals(""))
                            return;
                        if (lesson_exists(new_lesson_label)) {
                            Toast.makeText(EditLessonsActivity.this, "The lesson [" + new_lesson_label + "] already exists", 5).show();
                            return;
                        }
                        Lesson new_lesson = new Lesson(new_lesson_label);
                        new_lesson.state = Lesson.NEW;
                        new_lesson.num_items=0;
                        myDB.addLesson(new_lesson_label);
                        current_lessons.add(0,new_lesson);
                        ArrayList<String> lesson_order=new ArrayList<String>();
                        for (Lesson l:current_lessons)
                            lesson_order.add(l.label);
                        myDB.sortLessons(lesson_order);
                        TouchListView lv = (TouchListView) findViewById(R.id.lesson_list);
                        ((LessonArrayAdapter)lv.getAdapter()).notifyDataSetChanged();
                    }
                });



            }
            else {
                if ((convertView != null) && ((Integer)convertView.getTag(R.id.lesson_list_view_inflated)==R.layout.edit_lesson_list_item)) {
                    row = convertView;
                }
                else {
                    LayoutInflater inflater = getLayoutInflater();
                    row = inflater.inflate(R.layout.edit_lesson_list_item, parent, false);
                    row.setTag(R.id.lesson_list_view_inflated, R.layout.edit_lesson_list_item);
                }

                Lesson le = current_lessons.get(position-1);

                String more_info = "";
                if ((le.state & Lesson.RENAMED) != 0) {
                    more_info = "was : " + le.old_label;
                }
                if (le.num_items != 0) {
                    if (!more_info.equals(""))
                        more_info = more_info + ", ";
                    more_info = more_info + "has " + le.num_items + " chinese word";
                    if (le.num_items > 1)
                        more_info += "s";
                }
                TextView more_tview = (TextView) row.findViewById(R.id.lesson_more_info_label);
                if (more_info.equals("")) {
                    more_tview.setVisibility(View.GONE);
                }
                else {
                    more_tview.setText(more_info);
                    more_tview.setVisibility(View.VISIBLE);
                }

                TextView label = (TextView) row.findViewById(R.id.lesson_label);
                label.setText(le.label);
                if ((le.state & Lesson.NEW) != 0) {
                    label.setPaintFlags(label.getPaintFlags() | Paint.FAKE_BOLD_TEXT_FLAG);
                }
                else {
                    label.setPaintFlags(label.getPaintFlags() & ~Paint.FAKE_BOLD_TEXT_FLAG);
                }
                label.setTag(R.id.lesson_item, le);

                CheckBox cb = (CheckBox) row.findViewById(R.id.lesson_filter_on);
                cb.setTag(R.id.lesson_item, le);
                cb.setChecked((le.state & Lesson.FILTERED_ENABLED) != 0);
                if (filter_on) {
                    cb.setVisibility(View.VISIBLE);
                }
                else {
                    cb.setVisibility(View.GONE);
                }
                cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                        Lesson lesson = (Lesson) compoundButton.getTag(R.id.lesson_item);
                        if (b) {
                            lesson.state |= Lesson.FILTERED_ENABLED;
                            current_lesson_filter.add(lesson.label);
                        }
                        else {
                            lesson.state &= ~Lesson.FILTERED_ENABLED;
                            current_lesson_filter.remove(lesson.label);
                        }
                        myDB.setLessonsToFilter(current_lesson_filter);
                        ((AndZiApplication) getApplication()).save_database_filter(filter_on, current_lesson_filter);
                        TouchListView lv = (TouchListView) findViewById(R.id.lesson_list);
                        ((LessonArrayAdapter)lv.getAdapter()).notifyDataSetChanged();
                    }
                });
            }

            return row;
        }


        @Override
        public int getCount() {
            return current_lessons.size()+1;
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater=getMenuInflater();
        inflater.inflate(R.menu.lesson_item_menu, menu);
    }

    private boolean lesson_exists(String lesson) {
        for (Lesson le: current_lessons) {
            if (le.label.equals(lesson))
                return true;
        }
        return false;
    }

    private interface String_action{
        void run(String param);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        ContextMenu.ContextMenuInfo menuInfo=item.getMenuInfo();
        final Lesson le = current_lessons.get(((AdapterView.AdapterContextMenuInfo)menuInfo).position-1);

        switch(item.getItemId()) {
            case R.id.delete_lesson:
                myDB.delLesson(le.label);
                current_lessons.remove(le);
                TouchListView lv = (TouchListView) findViewById(R.id.lesson_list);
                ((LessonArrayAdapter)lv.getAdapter()).notifyDataSetChanged();
                break;
            case R.id.rename_lesson:
                if (le.old_label.equals(""))
                    le.old_label=le.label;

                prompt_user_input("Rename lesson "+le.label, "Enter new name:", new String_action() {
                    public void run(String param) {
                        if (param.equals(""))
                            return;
                        if (lesson_exists(param)) {
                            Toast.makeText(EditLessonsActivity.this, "The lesson [" + param + "] already exists", 5).show();
                            return;
                        }
                        if (param.equals(le.label))
                            return;
                        myDB.renameLesson(le.label, param);
                        le.label=param;
                        if (le.old_label.equals(param)) {
                            le.old_label="";
                            le.state &= ~Lesson.RENAMED;
                        }
                        else
                            le.state |= Lesson.RENAMED;
                        TouchListView lv = (TouchListView) findViewById(R.id.lesson_list);
                        ((LessonArrayAdapter)lv.getAdapter()).notifyDataSetChanged();

                    }
                });
                break;
            case R.id.duplicate_lesson:
                prompt_user_input("Duplicate lesson "+le.label, "Enter new name:", new String_action() {
                    public void run(String param) {
                        if (param.equals(""))
                            return;
                        if (lesson_exists(param)) {
                            Toast.makeText(EditLessonsActivity.this, "The lesson [" + param + "] already exists", 5).show();
                            return;
                        }
                        Lesson new_lesson=new Lesson(param);
                        new_lesson.state = Lesson.NEW;
                        new_lesson.num_items = le.num_items;
                        myDB.addLesson(param);
                        for (Vocabulary voc:myDB.getVocabularyInLessons(new ArrayList<String>(Arrays.asList(le.label))))
                            myDB.assignWordLesson(param, voc);
                        current_lessons.add(0, new_lesson);
                        ArrayList<String> lesson_order=new ArrayList<String>();
                        for (Lesson l:current_lessons)
                            lesson_order.add(l.label);
                        myDB.sortLessons(lesson_order);
                        TouchListView lv = (TouchListView) findViewById(R.id.lesson_list);
                        ((LessonArrayAdapter)lv.getAdapter()).notifyDataSetChanged();
                    }
                });
                break;
            case R.id.combine_lesson:
                AlertDialog.Builder alert = new AlertDialog.Builder(this);
                alert.setTitle("Select lessons to combine with "+le.label);
                final String[] lessons = new String[current_lessons.size()];
                final boolean[] lesson_checked = new boolean[current_lessons.size()];
                for (int i=0; i<current_lessons.size(); i++) {
                    lessons[i] = current_lessons.get(i).label;
                    lesson_checked[i] = false;
                }
                alert.setMultiChoiceItems(lessons, lesson_checked, new DialogInterface.OnMultiChoiceClickListener() {
                    public void onClick(DialogInterface dialog, int i, boolean b) {
                        lesson_checked[i]=b;
                    }
                });
                alert.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int n) {
                        ArrayList<String> lessons_selected = new ArrayList<String>();
                        for (int i=0; i<current_lessons.size(); i++) {
                            if (lesson_checked[i]) {
                                lessons_selected.add(lessons[i]);
                            }
                        }
                        ArrayList<Vocabulary> voc = myDB.getVocabularyInLessons(lessons_selected);
                        for (Vocabulary v:voc) {
                            myDB.assignWordLesson(le.label, v);
                        }
                        le.num_items=myDB.getVocabularyInLessons(new ArrayList<String>(Arrays.asList(le.label))).size();
                        dialog.dismiss();
                        TouchListView lv = (TouchListView) findViewById(R.id.lesson_list);
                        ((LessonArrayAdapter)lv.getAdapter()).notifyDataSetChanged();
                    }
                });
                alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int n) {
                        dialog.dismiss();
                    }
                });
                alert.show();



                break;
            default:
                return super.onContextItemSelected(item);
        }

        return true;
    }


    private void prompt_user_input(String title, String message, final String_action action) {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);

        alert.setTitle(title);
        alert.setMessage(message);

        final EditText input = new EditText(this);
        alert.setView(input);

        alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                String value = input.getText().toString();
                action.run(value);
            }
        });

        alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                dialog.dismiss();
            }
        });

        alert.show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.edit_lesson_menu, menu);

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean res = super.onPrepareOptionsMenu(menu);
        MenuItem enable = menu.findItem(R.id.lesson_enable_filter);
        if (filter_on) {
            enable.setTitle("Disable Lesson Filter");
        }
        else {
            enable.setTitle("Enable Lesson Filter");
        }

        return res;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        TouchListView lv = (TouchListView) findViewById(R.id.lesson_list);
        switch(item.getItemId()) {
            case R.id.lesson_enable_filter:
                filter_on = !filter_on;
                myDB.enableFilter(filter_on);
                ((AndZiApplication) getApplication()).save_database_filter(filter_on, current_lesson_filter);
                ((LessonArrayAdapter)lv.getAdapter()).notifyDataSetChanged();
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

}
