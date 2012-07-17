package com.matthieu;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import com.matthieu.widget.CharacterCursorAdapter;
import com.matthieu.widget.VocabularyCursorAdapter;

import java.util.ArrayList;
import java.util.Arrays;

public class ReviewList extends ListActivity {
    protected HanziDB myDB=null;
    private Cursor cur=null;

    private static final String TAG="ReviewList";

    private String lesson=null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        myDB = ((AndZiApplication) getApplication()).getMyDB();

        lesson=getIntent().getStringExtra("lesson");
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
                mProgressDialog = new ProgressDialog(ReviewList.this);
                mProgressDialog.setMessage(getResources().getString(R.string.loading));
                mProgressDialog.setIndeterminate(true);
                mProgressDialog.setCancelable(false);
                mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                mProgressDialog.show();
            }

            @Override
            protected Void doInBackground(Void... voids) {
                if (lesson == null) {
                    cur = myDB.getCursorFilteredToStudy();
                    expired_filtered = cur.getCount();
                }
                else {
                    cur = myDB.getVocabularyCursorInLessons(new ArrayList<String>(Arrays.asList(lesson)), true);
                    cur.getCount();
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                getListView().setAdapter(null);
                if (lesson == null) {
                    if (expired_filtered == 0) {
                        AlertDialog.Builder no_words_dialog = new AlertDialog.Builder(ReviewList.this);
                        no_words_dialog.setCancelable(false);
                        no_words_dialog.setMessage(getResources().getString(R.string.review_list_nothing_to_study));
                        no_words_dialog.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int i) {
                                dialog.dismiss();
                                finish();
                            }
                        });
                        no_words_dialog.create();
                        no_words_dialog.show();
                    }


                    int expired = myDB.getNumCharsExpiredInBoxOrAbove(0);
                    String header_text;
                    if (expired != expired_filtered) {
                        header_text = getString(R.string.review_list_num_to_study_filtered, expired_filtered, expired);
                    }
                    else {
                        header_text = getString(R.string.review_list_num_to_study, expired);
                    }

                    TextView old_header = (TextView) getListView().getTag(R.id.header_view);
                    if (old_header != null) {
                        old_header.setText(header_text);
                    } else {
                        LayoutInflater inflater = getLayoutInflater();
                        TextView header = (TextView) inflater.inflate(R.layout.review_list_header, getListView(), false);
                        header.setText(header_text);
                        getListView().addHeaderView(header, null, false);
                        getListView().setTag(R.id.header_view ,header);
                    }



                    setListAdapter(new CharacterCursorAdapter(ReviewList.this, cur, myDB, true));
                } else {
                    setListAdapter(new VocabularyCursorAdapter(ReviewList.this, cur));
                }
                mProgressDialog.dismiss();
            }
        };

        loader.execute();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cur != null)
            cur.close();
    }
}
