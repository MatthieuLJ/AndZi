package com.matthieu;

import android.annotation.SuppressLint;
import android.app.Activity;
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
import com.matthieu.widget.VocabularyCursorAdapter;

import java.util.ArrayList;
import java.util.Arrays;


public class LessonListFragment extends Fragment {
    protected HanziDB myDB=null;
    private Cursor cur=null;

    private static final String TAG="LessonListFragment";
    AsyncTask<Void, Void,Void> loader=null;
    private String lesson=null;

    public LessonListFragment(String lesson) {
        this.lesson = lesson;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        myDB = ((AndZiApplication) activity.getApplication()).getMyDB();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View res = inflater.inflate(R.layout.review_list, container, false);
        return res;
    }

    @Override
    @SuppressLint({"NewApi"})
    public void onResume() {
        super.onResume();
         loader = new AsyncTask<Void, Void, Void>() {

            @Override
            protected void onPreExecute() {
                ProgressBar pb = (ProgressBar) getView().findViewById(R.id.lesson_content_loading);
                pb.setVisibility(View.VISIBLE);

                ListView list = (ListView) getView().findViewById(R.id.review_list);
                list.setAdapter(null);
            }

            @Override
            protected Void doInBackground(Void... voids) {
                cur = myDB.getVocabularyCursorInLessons(new ArrayList<String>(Arrays.asList(lesson)), true);
                cur.getCount();
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                synchronized (LessonListFragment.this) {
                    ListView list = (ListView) getView().findViewById(R.id.review_list);
                    list.setAdapter(new VocabularyCursorAdapter(getActivity(), cur, null));

                    ProgressBar pb = (ProgressBar) getView().findViewById(R.id.lesson_content_loading);
                    pb.setVisibility(View.GONE);
                    loader=null;
                }
            }
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
            loader.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[])null);
        else
            loader.execute((Void[])null);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (cur != null)
            cur.close();
    }

    public void change_menu() {
        ((VocabularyEditorActivity)getActivity()).set_menu(VocabularyEditorActivity.menu_types.INITIAL);
    }
}
