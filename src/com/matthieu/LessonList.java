package com.matthieu;

import android.app.ListActivity;
import android.app.ProgressDialog;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ListView;
import com.matthieu.widget.VocabularyCursorAdapter;

import java.util.ArrayList;
import java.util.Arrays;


public class LessonList extends ListActivity {
    protected HanziDB myDB=null;
    private Cursor cur=null;

    private static final String TAG="LessonList";

    private String lesson=null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        myDB = ((AndZiApplication) getApplication()).getMyDB();

        lesson=getIntent().getStringExtra("lesson");
        if (lesson == null) {
            finish();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        ((AndZiApplication)getApplication()).activityPaused();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ((AndZiApplication)getApplication()).activityResumed();

        AsyncTask<Void, Void,Void> loader = new AsyncTask<Void, Void, Void>() {
            int expired_filtered;
            ProgressDialog mProgressDialog;

            @Override
            protected void onPreExecute() {
                mProgressDialog = new ProgressDialog(LessonList.this);
                mProgressDialog.setMessage(getResources().getString(R.string.loading));
                mProgressDialog.setIndeterminate(true);
                mProgressDialog.setCancelable(false);
                mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                mProgressDialog.show();

                getListView().setAdapter(null);

            }

            @Override
            protected Void doInBackground(Void... voids) {
                cur = myDB.getVocabularyCursorInLessons(new ArrayList<String>(Arrays.asList(lesson)), true);
                cur.getCount();
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                getListView().setAdapter(new VocabularyCursorAdapter(LessonList.this, cur));

                mProgressDialog.dismiss();
            }
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
            loader.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[])null);
        else
            loader.execute((Void[])null);

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cur != null)
            cur.close();
    }

}
