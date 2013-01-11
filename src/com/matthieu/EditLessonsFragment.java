/******************************
 * Activity to edit lessons and lesson filter
 */
package com.matthieu;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Paint;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import com.matthieu.widget.ExtendingLinearLayout;
import com.matthieu.widget.TouchListView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class EditLessonsFragment extends Fragment {
    protected HanziDB myDB=null;

    protected ArrayList<Lesson> current_lessons= new ArrayList<Lesson>();
    protected Boolean loaded = false;
    private InitLessons init_lessons_thread=null;
    private UpdateLessons update_lessons_thread=null;

    private static final String TAG = "EditLessonsFragment";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        myDB = ((AndZiApplication) activity.getApplication()).getMyDB();
    }

    @Override
    @SuppressLint({"NewApi"})
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View res = inflater.inflate(R.layout.edit_lessons, container, false);
        TouchListView list = (TouchListView) res.findViewById(R.id.lesson_list);
        list.setDropListener(new TouchListView.DropListener() {
            public void drop(int from, int to) {
                if (from == 0)
                    return;
                if (init_lessons_thread != null)
                    return;
                if (to == 0)
                    to = 1;
                Lesson le = current_lessons.get(from - 1);
                Log.d(TAG, "Moving from " + from + " to " + to);
                current_lessons.remove(from - 1);
                current_lessons.add(to - 1, le);
                final ArrayList<String> lesson_order = new ArrayList<String>();
                for (Lesson l : current_lessons)
                    lesson_order.add(l.label);
                TouchListView lv = (TouchListView) res.findViewById(R.id.lesson_list);
                ((LessonArrayAdapter) lv.getAdapter()).notifyDataSetChanged();
                new Thread(new Runnable() {
                    public void run() {
                        myDB.sortLessons(lesson_order);
                    }
                }).start();
            }
        });

        ProgressBar pb = (ProgressBar) res.findViewById(R.id.lessons_loading);

        Log.d(TAG, "Finishing onCreateView");
        synchronized (this) {
            if (init_lessons_thread == null) {
                if (!loaded) {
                    Log.d(TAG, "Finishing onCreateView");
                    init_lessons_thread = new InitLessons();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
                        init_lessons_thread.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[])null);
                    else
                        init_lessons_thread.execute((Void[])null);

                    pb.setVisibility(View.VISIBLE);
                } else {
                    list.setAdapter(new LessonArrayAdapter());
                    pb.setVisibility(View.GONE);
                }
            }
        }

        return res;
    }

    private class InitLessons extends AsyncTask<Void, Void, Void> {
        ArrayList<Lesson> tmp_lessons;

        @Override
        protected Void doInBackground(Void... voids) {
            tmp_lessons = new ArrayList<Lesson>();
            Log.d(TAG, "Loading lesson list");
            ArrayList<String> existing_lessons = myDB.getLessons();

            for (int i=0; i<existing_lessons.size(); i++) {
                if (isCancelled())
                    return null;

                String lesson = existing_lessons.get(i);
                int num = myDB.getVocabularyInLessons(new ArrayList<String>(Arrays.asList(lesson))).size();
                Lesson new_lesson = new Lesson(lesson);
                new_lesson.state = Lesson.EXISTING;
                new_lesson.num_items=num;
                tmp_lessons.add(new_lesson);
            }

            Log.d(TAG, "Done with lesson list, will populate");
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            synchronized (EditLessonsFragment.this) {
                current_lessons = tmp_lessons;
                loaded = true;

                TouchListView lv = (TouchListView) getView().findViewById(R.id.lesson_list);
                if (lv != null) {
                    lv.setAdapter(new LessonArrayAdapter());
                }

                ProgressBar pb = (ProgressBar) getView().findViewById(R.id.lessons_loading);
                pb.setVisibility(View.GONE);

                init_lessons_thread=null;
            }
        }
    }

    private class UpdateLessons extends AsyncTask<Void, Void, Void> {
        boolean start_over;
        protected Void doInBackground(Void... voids) {
            do {
                start_over=false;
                for (Lesson l : current_lessons) {
                    int num = myDB.getVocabularyInLessons(new ArrayList<String>(Arrays.asList(l.label))).size();
                    l.num_items=num;
                }
            } while (start_over);
            return null;
        }

        public void redo_update() {
            start_over=true;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);

            TouchListView lv = (TouchListView) getView().findViewById(R.id.lesson_list);
            if (lv != null) {
                ((LessonArrayAdapter)lv.getAdapter()).notifyDataSetChanged();
            }

            synchronized (EditLessonsFragment.this) {
                update_lessons_thread = null;
            }
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

        public Lesson(String l) {
            label = l;
            num_items = 0;
            state = 0;
        }
    }

    @SuppressLint({"NewApi"})
    public void update_lesson_list() {
        if (current_lessons.size() == 0)
            return;

        synchronized (this) {
            if (update_lessons_thread== null) {
                update_lessons_thread = new UpdateLessons();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
                    update_lessons_thread.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[])null);
                else
                    update_lessons_thread.execute((Void[])null);
            }
            else {
                update_lessons_thread.redo_update();
            }
        }
    }

    private class LessonArrayAdapter extends BaseAdapter {
        boolean new_collapsed = true;
        ExtendingLinearLayout first_row = null;
        public LessonArrayAdapter() {
            super();
        }

        public void expand_new_field(boolean expand) {
            if (first_row != null && expand && new_collapsed) {
                new_collapsed = !expand;
                first_row.expand();
                EditText field = (EditText) first_row.findViewById(R.id.edit_new_lesson);
                field.requestFocus();
            } else if (first_row == null && expand && new_collapsed) {
                new_collapsed = !expand;
            }
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            View row;

            if (position == 0) {
                if ((convertView != null) && ((Integer)convertView.getTag(R.id.lesson_list_view_inflated)==R.layout.edit_lesson_list_new_item)) {
                    row = convertView;
                }
                else {
                    LayoutInflater inflater = getActivity().getLayoutInflater();
                    row = inflater.inflate(R.layout.edit_lesson_list_new_item, parent, false);
                    row.setTag(R.id.lesson_list_view_inflated, R.layout.edit_lesson_list_new_item);
                }
                if (getCount()==1)
                    new_collapsed = false;

                ExtendingLinearLayout elv = (ExtendingLinearLayout) row.findViewById(R.id.edit_lesson_new_container);
                if (new_collapsed)
                    elv.collapse_immediate();
                else
                    elv.expand_immediate();
                first_row = elv;

                Button new_lesson_ok = (Button) row.findViewById(R.id.edit_new_lesson_ok);
                EditText new_lesson_field = (EditText) row.findViewById(R.id.edit_new_lesson);
                new_lesson_ok.setTag(R.id.edit_new_lesson, new_lesson_field);
                new_lesson_ok.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View view) {
                        if (init_lessons_thread!=null)
                            return;
                        EditText new_lesson_field = (EditText)view.getTag(R.id.edit_new_lesson);
                        final String new_lesson_label = new_lesson_field.getText().toString();
                        new_lesson_field.setText("");
                        if (new_lesson_label.equals(""))
                            return;
                        if (new_lesson_label.equals("(none)"))
                            return;
                        if (lesson_exists(new_lesson_label)) {
                            Toast.makeText(getActivity(), "The lesson [" + new_lesson_label + "] already exists", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        Lesson new_lesson = new Lesson(new_lesson_label);
                        new_lesson.state = Lesson.NEW;
                        new_lesson.num_items=0;
                        current_lessons.add(0,new_lesson);
                        final ArrayList<String> lesson_order=new ArrayList<String>();
                        for (Lesson l:current_lessons)
                            lesson_order.add(l.label);
                        new_collapsed = true;
                        InputMethodManager imm = (InputMethodManager)EditLessonsFragment.this.getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.hideSoftInputFromWindow(new_lesson_field.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
                        redraw_list();
                        new Thread(new Runnable() {
                            public void run() {
                                myDB.addLesson(new_lesson_label);
                                myDB.sortLessons(lesson_order);
                            }
                        }).start();
                    }
                });
            }
            else {
                if ((convertView != null) && ((Integer)convertView.getTag(R.id.lesson_list_view_inflated)==R.layout.edit_lesson_list_item)) {
                    row = convertView;
                }
                else {
                    LayoutInflater inflater = getActivity().getLayoutInflater();
                    row = inflater.inflate(R.layout.edit_lesson_list_item, parent, false);
                    row.setTag(R.id.lesson_list_view_inflated, R.layout.edit_lesson_list_item);
                }

                Lesson le = current_lessons.get(position-1);

                VocabularyEditorActivity.activity_state state = ((VocabularyEditorActivity)getActivity()).get_state();

                String more_info = "";
                if ((le.state & Lesson.RENAMED) != 0) {
                    more_info = "was : " + le.old_label;
                }
                if ((le.num_items != 0) && (state != VocabularyEditorActivity.activity_state.STATE_EDIT_WORD_VOC_SELECTED_LESSON_CHANGED)) {
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


                if (state == VocabularyEditorActivity.activity_state.STATE_INIT) {
                    row.setBackgroundColor(0x00000000);
                } else if ((state == VocabularyEditorActivity.activity_state.STATE_EDIT_WORD_VOC_SELECTED_LESSON_CHANGED) ||
                        (state == VocabularyEditorActivity.activity_state.STATE_EDIT_WORD_VOC_SELECTED_MULTIPLE) ||
                        (state == VocabularyEditorActivity.activity_state.STATE_EDIT_WORD_VOC_SELECTED_SINGLE)) {
                    if (!current_lesson_assignment.containsKey(le.label)) {
                        row.setBackgroundColor(0x00000000);
                    } else {
                        VocabularyEditorActivity.LessonAssignment this_lesson = current_lesson_assignment.get(le.label);
                        switch (this_lesson.state) {
                            case 0:
                                row.setBackgroundColor(0x00000000);
                                break;
                            case 1:
                                row.setBackgroundColor(0xFF777777);
                                break;
                            case 2:
                                row.setBackgroundColor(0xFFAAAAAA);
                                break;
                        }
                    }
                } else {
                    if (current_lesson_selection.contains(le)) {
                        row.setBackgroundColor(0xFF777777);
                    } else {
                        row.setBackgroundColor(0x00000000);
                    }
                }

                row.setTag(R.id.lesson_item, le);
                row.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        handle_lesson_click(view);
                    }
                });
            }

            return row;
        }

        public int getCount() {
            return current_lessons.size()+1;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public int getItemViewType(int position) {
            if (position == 0)
                return 0;
            else
                return 1;
        }

        public Object getItem(int position) {
            if (position == 0)
                return "";
            else
                return current_lessons.get(position-1);
        }

        public long getItemId(int position) {
            return getItem(position).hashCode();
        }
    }

    private boolean lesson_exists(String lesson) {
        for (Lesson le: current_lessons) {
            if (le.label.equals(lesson))
                return true;
        }
        return false;
    }

    private ArrayList<Lesson> current_lesson_selection=new ArrayList<Lesson>();

    public ArrayList<String> get_selection() {
        ArrayList<String> res = new ArrayList<String>();
        for (Lesson l : current_lesson_selection)
            res.add(l.label);
        return res;
    }

    protected void update_state() {
        if (getActivity() == null)
            return;

        VocabularyEditorActivity.activity_state state = ((VocabularyEditorActivity)getActivity()).get_state();
        VocabularyEditorActivity.activity_state new_state;

        if ((state == VocabularyEditorActivity.activity_state.STATE_INIT) ||
                (state == VocabularyEditorActivity.activity_state.STATE_LESSON_SELECTED_MULTIPLE) ||
                (state == VocabularyEditorActivity.activity_state.STATE_LESSON_SELECTED_SINGLE)) {

            switch (current_lesson_selection.size()) {
                case 0:
                    new_state = VocabularyEditorActivity.activity_state.STATE_INIT;
                    break;
                case 1:
                    new_state = VocabularyEditorActivity.activity_state.STATE_LESSON_SELECTED_SINGLE;
                    break;
                default:
                    new_state = VocabularyEditorActivity.activity_state.STATE_LESSON_SELECTED_MULTIPLE;
                    break;
            }
            if (current_lesson_selection.size()>0)
                list_selection();
            else
                ((VocabularyEditorActivity)getActivity()).close_lesson_list();

            ((VocabularyEditorActivity)getActivity()).set_state(new_state);
        }
    }

    public void handle_lesson_click(View v) {
        final Lesson le = (Lesson) v.getTag(R.id.lesson_item);
        if (le == null)
            return;

        VocabularyEditorActivity.activity_state state = ((VocabularyEditorActivity)getActivity()).get_state();
        if ((state == VocabularyEditorActivity.activity_state.STATE_EDIT_WORD_DIC_SELECTED) ||
                (state == VocabularyEditorActivity.activity_state.STATE_EDIT_WORD))
            return;

        if (state == VocabularyEditorActivity.activity_state.STATE_LESSON_SELECTION) {
            if (!current_lesson_selection.contains(le)) {
                current_lesson_selection.add(le);
                v.setBackgroundColor(0xFF777777);
            }
            else {
                current_lesson_selection.remove(le);
                v.setBackgroundColor(0x00000000);
            }

            /*TouchListView lv = (TouchListView) getView().findViewById(R.id.lesson_list);
            ((LessonArrayAdapter)lv.getAdapter()).notifyDataSetChanged();*/
        } else if ((state == VocabularyEditorActivity.activity_state.STATE_EDIT_WORD_VOC_SELECTED_LESSON_CHANGED) ||
                (state == VocabularyEditorActivity.activity_state.STATE_EDIT_WORD_VOC_SELECTED_MULTIPLE) ||
                (state == VocabularyEditorActivity.activity_state.STATE_EDIT_WORD_VOC_SELECTED_SINGLE)) {
            if (!current_lesson_assignment.containsKey(le.label)) {
                VocabularyEditorActivity.LessonAssignment assign = new VocabularyEditorActivity.LessonAssignment();
                assign.partiable = false;
                assign.state = 1;
                current_lesson_assignment.put(le.label, assign);
                v.setBackgroundColor(0xFF777777);
            } else {
                VocabularyEditorActivity.LessonAssignment this_lesson = current_lesson_assignment.get(le.label);
                switch (this_lesson.state) {
                    case 0:
                        if (this_lesson.partiable) {
                            this_lesson.state = 2;
                            v.setBackgroundColor(0xFFAAAAAA);
                        } else {
                            this_lesson.state = 1;
                            v.setBackgroundColor(0xFF777777);
                        }
                        break;
                    case 1:
                        this_lesson.state = 0;
                        v.setBackgroundColor(0x00000000);
                        break;
                    case 2:
                        this_lesson.state = 1;
                        v.setBackgroundColor(0xFF777777);
                        break;
                }
            }
            ((VocabularyEditorActivity)getActivity()).set_state(VocabularyEditorActivity.activity_state.STATE_EDIT_WORD_VOC_SELECTED_LESSON_CHANGED);
        } else {
            if (current_lesson_selection.contains(le)) {
                current_lesson_selection.remove(le);
                v.setBackgroundColor(0x00000000);
            } else {
                current_lesson_selection.add(le);
                v.setBackgroundColor(0xFF777777);
            }
        }

        v.invalidate();
        update_state();
    }

    @SuppressLint({"NewApi"})
    public void add_new() {
        TouchListView lv = (TouchListView) getView().findViewById(R.id.lesson_list);
        if (lv == null)
            return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO)
            lv.smoothScrollToPosition(0);
        ((LessonArrayAdapter)lv.getAdapter()).expand_new_field(true);
    }

    public void clone_selection() {
        if (current_lesson_selection.size() != 1) {
            update_state();
            return;
        }
        final Lesson le = current_lesson_selection.get(0);
        String new_name = le.label + " - copy";
        StringBuilder other_new_name = new StringBuilder(new_name);
        int index = 1;
        while (lesson_exists(other_new_name.toString())) {
            other_new_name = new StringBuilder(new_name).append(" (").append(index).append(")");
            index++;
        }
        new_name = other_new_name.toString();

        Lesson new_lesson=new Lesson(new_name);
        new_lesson.state = Lesson.NEW;
        new_lesson.num_items = le.num_items;
        current_lessons.add(0, new_lesson);

        final String final_new_name = new_name;
        new Thread(new Runnable() {
            public void run() {
                myDB.addLesson(final_new_name);
                for (Vocabulary voc:myDB.getVocabularyInLessons(new ArrayList<String>(Arrays.asList(le.label))))
                    myDB.assignWordLesson(final_new_name, voc);
                ArrayList<String> lesson_order=new ArrayList<String>();
                for (Lesson l:current_lessons)
                    lesson_order.add(l.label);
                myDB.sortLessons(lesson_order);
            }
        }).start();
        current_lesson_selection.clear();
        redraw_list();
        update_state();
    }

    HashMap<String, VocabularyEditorActivity.LessonAssignment> current_lesson_assignment = new HashMap<String, VocabularyEditorActivity.LessonAssignment>();

    public void start_assigning_lesson(HashMap<String, VocabularyEditorActivity.LessonAssignment> current_assignment) {
        current_lesson_assignment = current_assignment;
        redraw_list();
    }

    public void clear_selection() {
        current_lesson_assignment.clear();
        current_lesson_selection.clear();
        redraw_list();
    }

    public HashMap<String, VocabularyEditorActivity.LessonAssignment> get_assignment() {
        return current_lesson_assignment;
    }

    public void del_selection() {
        if (current_lesson_selection.size() == 0) {
            update_state();
            return;
        }
        final ArrayList<Lesson> to_delete = new ArrayList<Lesson>(current_lesson_selection);
        current_lessons.removeAll(current_lesson_selection);
        new Thread(new Runnable() {
            public void run() {
                for (Lesson le : to_delete)
                    myDB.delLesson(le.label);
            }
        }).start();
        current_lesson_selection.clear();
        redraw_list();
        update_state();
    }

    public void rename_selection() {
        if (current_lesson_selection.size() != 1) {
            update_state();
            return;
        }
        final Lesson le = current_lesson_selection.get(0);

        if (le.old_label.equals(""))
            le.old_label=le.label;

        AlertDialog.Builder alert = new AlertDialog.Builder(getActivity());
        alert.setTitle("Rename lesson: "+le.label);
        alert.setMessage("Enter new name: ");

        final EditText input = new EditText(getActivity());
        input.setSingleLine(false);
        input.setMaxLines(3);
        input.setText(le.label);
        alert.setView(input);

        alert.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                final String new_label = input.getText().toString();
                if (new_label.equals(""))
                    return;
                if (new_label.equals("(none)"))
                    return;
                if (new_label.equals(le.label))
                    return;
                if (lesson_exists(new_label)) {
                    Toast.makeText(getActivity(), "The lesson [" + new_label + "] already exists", Toast.LENGTH_SHORT).show();
                    return;
                }

                final String old_name = le.label;
                le.label = new_label;
                if (le.old_label.equals(new_label)) {
                    le.old_label = "";
                    le.state &= ~Lesson.RENAMED;
                } else
                    le.state |= Lesson.RENAMED;


                new Thread(new Runnable() {
                    public void run() {
                        myDB.renameLesson(old_name, new_label);
                    }
                }).start();

                current_lesson_selection.clear();
                redraw_list();
                update_state();

                dialog.dismiss();
            }
        });

        alert.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                dialog.dismiss();
            }
        });
        alert.show();
    }

    public void merge_selection() {
        if (current_lesson_selection.size() < 1) {
            update_state();
            return;
        }

        String new_name = "Combined lesson";
        StringBuilder other_new_name = new StringBuilder(new_name);
        int index = 1;
        while (lesson_exists(other_new_name.toString())) {
            other_new_name = new StringBuilder(new_name).append(" (").append(index).append(")");
            index++;
        }
        new_name = other_new_name.toString();

        final Lesson new_lesson=new Lesson(new_name);
        new_lesson.state = Lesson.NEW;
        new_lesson.label = new_name;
        current_lessons.add(0, new_lesson);

        final ArrayList<Lesson> to_combine = new ArrayList<Lesson>(current_lesson_selection);

        new Thread(new Runnable() {
            public void run() {
                for (Lesson le : to_combine) {
                    ArrayList<Vocabulary> voc = myDB.getVocabularyInLessons(new ArrayList<String>(Arrays.asList(le.label)));
                    for (Vocabulary v:voc) {
                        myDB.assignWordLesson(new_lesson.label, v);
                    }
                }
                new_lesson.num_items=myDB.getVocabularyInLessons(new ArrayList<String>(Arrays.asList(new_lesson.label))).size();
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        TouchListView lv = (TouchListView) getView().findViewById(R.id.lesson_list);
                        if (lv == null)
                            return;
                        ((LessonArrayAdapter)lv.getAdapter()).notifyDataSetChanged();
                    }
                });
            }
        }).start();

        current_lesson_selection.clear();
        redraw_list();
        update_state();
    }

    public void list_selection() {
        if (current_lesson_selection.size() == 0) {
            update_state();
            return;
        }
        ArrayList<String> to_list=new ArrayList<String>();
        for (Lesson l : current_lesson_selection)
            to_list.add(l.label);

        ((VocabularyEditorActivity)getActivity()).open_lesson_list(to_list);
    }

    public void set_select_all() {
        current_lesson_selection.clear();
        for (Lesson le : current_lessons)
            current_lesson_selection.add(le);
        redraw_list();
    }

    public void set_select_none() {
        current_lesson_selection.clear();
        redraw_list();
    }

    public void redraw_list() {
        if (getView() == null)
            return;
        TouchListView lv = (TouchListView) getView().findViewById(R.id.lesson_list);
        ((LessonArrayAdapter)lv.getAdapter()).notifyDataSetChanged();
    }
}
