package com.matthieu;

import android.app.Activity;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.res.Configuration;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import com.matthieu.widget.VocabularyCursorAdapter;

import java.util.ArrayList;
import java.util.Arrays;


public class LessonList extends Activity {
    protected HanziDB myDB=null;
    private Cursor cur=null;

    private static final String TAG="LessonList";
    AsyncTask<Void, Void,Void> loader=null;
    private String lesson=null;
    private ProgressDialog mProgressDialog=null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        myDB = ((AndZiApplication) getApplication()).getMyDB();

        lesson=getIntent().getStringExtra("lesson");
        if (lesson == null) {
            finish();
        }
        setContentView(R.layout.review_list);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ((AndZiApplication)getApplication()).activityPaused();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        synchronized (LessonList.this) {
            ListAdapter adapter=null;
            if (loader==null) {
                ListView list = (ListView) findViewById(R.id.review_list);
                adapter = list.getAdapter();
            }
            else {
                mProgressDialog.dismiss();
            }
            setContentView(R.layout.review_list);

            if (loader==null) {
                ListView list = (ListView) findViewById(R.id.review_list);
                list.setAdapter(adapter);
            }
            else {
                mProgressDialog = new ProgressDialog(LessonList.this);
                mProgressDialog.setMessage(getResources().getString(R.string.loading));
                mProgressDialog.setIndeterminate(true);
                mProgressDialog.setCancelable(false);
                mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                mProgressDialog.show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        ((AndZiApplication)getApplication()).activityResumed();

         loader = new AsyncTask<Void, Void, Void>() {

            @Override
            protected void onPreExecute() {
                mProgressDialog = new ProgressDialog(LessonList.this);
                mProgressDialog.setMessage(getResources().getString(R.string.loading));
                mProgressDialog.setIndeterminate(true);
                mProgressDialog.setCancelable(false);
                mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                mProgressDialog.show();

                ListView list = (ListView) findViewById(R.id.review_list);
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
                synchronized (LessonList.this) {
                    ListView list = (ListView) findViewById(R.id.review_list);
                    list.setAdapter(new VocabularyCursorAdapter(LessonList.this, cur, null));

                    mProgressDialog.dismiss();
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
    protected void onDestroy() {
        super.onDestroy();
        if (cur != null)
            cur.close();
    }

}
