package com.matthieu;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.matthieu.widget.VocabularyCursorAdapter;
import com.matthieu.widget.VocabularyCursorClickHandle;

import java.util.ArrayList;


public class LessonListFragment extends Fragment {
    protected HanziDB myDB=null;
    private Cursor cur=null;

    private static final String TAG="LessonListFragment";
    update_lesson_list_task loader=null;
    private ArrayList<String> lesson=null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        myDB = ((AndZiApplication) activity.getApplication()).getMyDB();
        Bundle bundle = getArguments();
        lesson = bundle.getStringArrayList("lesson");
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.lesson_list, container, false);
    }

    private void update_header_view(ListView list) {
        TextView header = (TextView) list.getTag(R.id.header_view);
        if (header == null) {
            header = (TextView) getLayoutInflater(null).inflate(R.layout.lesson_list_header, list, false);
            list.setTag(R.id.header_view, header);
            list.addHeaderView(header, null, false);
        }

        if (lesson.size() == 0)
            header.setText("Words that are not assigned to any lesson");
        else {
            StringBuilder lessons = new StringBuilder();
            for (int i=0; i<lesson.size(); i++) {
                lessons.append(lesson.get(i));
                if (i<lesson.size()-1)
                    lessons.append(", ");
            }
            header.setText("Words in lesson "+lessons.toString());
        }
    }

    @Override
    @SuppressLint({"NewApi"})
    public void onResume() {
        super.onResume();
        if (cur != null) {
            ListView list = (ListView) getView().findViewById(R.id.review_list);
            update_header_view(list);
            list.setAdapter(new VocabularyCursorAdapter(getActivity(), cur, new VocabularyCursorClickHandle() {
                @Override
                public void click(String item_clicked) {
                    Intent i=new Intent(getActivity(), ReferenceActivity.class);
                    i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    i.putExtra("Hanzi", item_clicked);
                    i.putExtra("use_ref", false);
                    startActivity(i);
                }
            }));

            ProgressBar pb = (ProgressBar) getView().findViewById(R.id.lesson_content_loading);
            pb.setVisibility(View.GONE);
            loader=null;
        } else {
            update_list();
        }
    }

    private class update_lesson_list_task extends AsyncTask<Void,Void,Void> {
        private boolean do_it_again=false;
        public void update_again() {
            synchronized (LessonListFragment.this) {
                do_it_again = true;
            }
        }

        @Override
        protected void onPreExecute() {
            if (getView() == null) {
                this.cancel(true);
                return;
            }
            ProgressBar pb = (ProgressBar) getView().findViewById(R.id.lesson_content_loading);
            pb.setVisibility(View.VISIBLE);

            ListView list = (ListView) getView().findViewById(R.id.review_list);
            list.setAdapter(null);
        }

        @Override
        protected Void doInBackground(Void... voids) {
            do {
                do_it_again=false;
                cur = myDB.getVocabularyCursorInLessons(lesson, true);
                cur.getCount();
            } while (do_it_again);

            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            if (getView() == null)
                return;
            synchronized (LessonListFragment.this) {
                ListView list = (ListView) getView().findViewById(R.id.review_list);
                if (list != null) {
                    update_header_view(list);
                    list.setAdapter(new VocabularyCursorAdapter(getActivity(), cur, new VocabularyCursorClickHandle() {
                        @Override
                        public void click(String item_clicked) {
                            Intent i=new Intent(getActivity(), ReferenceActivity.class);
                            i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                            i.putExtra("Hanzi", item_clicked);
                            i.putExtra("use_ref", false);
                            startActivity(i);
                        }
                    }));
                }

                ProgressBar pb = (ProgressBar) getView().findViewById(R.id.lesson_content_loading);
                if (pb != null)
                    pb.setVisibility(View.GONE);
                loader=null;
            }
        }
    }

    @SuppressLint({"NewApi"})
    public synchronized void update_list() {
        if (loader != null) {
            loader.update_again();
            return;
        }

        loader = new update_lesson_list_task();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
            loader.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[])null);
        else
            loader.execute((Void[])null);
    }

    public ArrayList<String> current_lessons() {
        return lesson;
    }

    public void change_lessons(ArrayList<String> new_lessons) {
        synchronized (LessonListFragment.this) {
            lesson = new_lessons;
            update_list();
        }
    }

    @Override
    public void onDestroy() {
        if (cur != null)
            cur.close();
        super.onDestroy();
    }
}
