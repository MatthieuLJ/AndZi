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
import java.util.Arrays;


public class LessonListFragment extends Fragment {
    protected HanziDB myDB=null;
    private Cursor cur=null;

    private static final String TAG="LessonListFragment";
    update_lesson_list_task loader=null;
    private String lesson=null;

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
        lesson = bundle.getString("lesson");
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View res = inflater.inflate(R.layout.review_list, container, false);

        ListView list = (ListView) res.findViewById(R.id.review_list);
        TextView header = (TextView) inflater.inflate(R.layout.review_list_header, list, false);
        if (lesson.equals(""))
            header.setText("Words that are not assigned to any lesson");
        else
            header.setText("Words in lesson "+lesson);

        list.addHeaderView(header, null, false);
        return res;
    }

    @Override
    @SuppressLint({"NewApi"})
    public void onResume() {
        super.onResume();
        update_list();
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
            ProgressBar pb = (ProgressBar) getView().findViewById(R.id.lesson_content_loading);
            pb.setVisibility(View.VISIBLE);

            ListView list = (ListView) getView().findViewById(R.id.review_list);
            list.setAdapter(null);
        }

        @Override
        protected Void doInBackground(Void... voids) {
            do {
                do_it_again=false;
                cur = myDB.getVocabularyCursorInLessons(new ArrayList<String>(Arrays.asList(lesson)), true);
                cur.getCount();
            } while (do_it_again);

            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            synchronized (LessonListFragment.this) {
                ListView list = (ListView) getView().findViewById(R.id.review_list);
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

    public String current_lesson() {
        return lesson;
    }

    @Override
    public void onPause() {
        if (cur != null)
            cur.close();
        super.onPause();
    }

    public void change_menu() {
        ((VocabularyEditorActivity)getActivity()).set_menu(VocabularyEditorActivity.menu_types.INITIAL);
    }
}
