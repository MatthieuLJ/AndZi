package com.matthieu;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.SparseArray;
import android.view.*;
import android.widget.*;
import com.matthieu.widget.AndZiActivity;
import com.matthieu.widget.CharacterCursorAdapter;
import com.matthieu.widget.VocabularyCursorAdapter;

import java.lang.ref.SoftReference;
import java.util.Arrays;

public class ReviewList extends AndZiActivity {
    protected HanziDB myDB=null;
    private CursorAdapter adapters[]=null;
    int mode=3; // 3 (Default), both panes, 1=only chars, 2=only words, 0=nothing...

    private static final String TAG="ReviewList";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        myDB = ((AndZiApplication) getApplication()).getMyDB();

        mode=3;
        if (myDB.getNumCharsExpiredInBoxOrAbove(0) == 0) {
            mode &= 0x2;
        }
        if (myDB.getNumWordsExpiredInBoxOrAbove(0) == 0) {
            mode &= 0x1;
        }

        if (mode == 0) {
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
        else {
            adapters = new CursorAdapter[2];
            Arrays.fill(adapters, null);

            setContentView(R.layout.review_list_pages);
            ViewPager pager = (ViewPager) findViewById(R.id.reviews);
            pager.setAdapter(new ReviewPageAdapter());
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        ViewPager pager = (ViewPager) findViewById(R.id.reviews);
        PagerAdapter adapter = pager.getAdapter();

        setContentView(R.layout.review_list_pages);
        pager = (ViewPager) findViewById(R.id.reviews);
        pager.setAdapter(adapter);
    }

    private class review_loader extends AsyncTask<Void, Void, Void> {
        int expired_filtered;
        ProgressDialog mProgressDialog;
        ListView list;
        Cursor cur;
        int list_id; // 0 for words to review, 1 for characters

        public review_loader(ListView l, int id) {
            list = l;
            list_id = id;
        }

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
            switch (list_id) {
                case 0:
                    cur = myDB.getCursorFilteredWordsToStudy();
                    break;
                case 1:
                    cur = myDB.getCursorFilteredCharsToStudy();
                    break;
                default:
                    return null;
            }
            expired_filtered = cur.getCount();

            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            if (mProgressDialog.isShowing())
                mProgressDialog.dismiss();

            if (cur == null)
                return;

            int expired;
            String header_text="";
            switch(list_id) {
                case 0:
                    expired = myDB.getNumWordsExpiredInBoxOrAbove(0);

                    if (expired != expired_filtered) {
                        header_text = getString(R.string.review_list_num_words_to_study_filtered, expired_filtered, expired);
                    }
                    else {
                        header_text = getString(R.string.review_list_num_words_to_study, expired);
                    }
                    break;
                case 1:
                    expired = myDB.getNumCharsExpiredInBoxOrAbove(0);

                    if (expired != expired_filtered) {
                        header_text = getString(R.string.review_list_num_chars_to_study_filtered, expired_filtered, expired);
                    }
                    else {
                        header_text = getString(R.string.review_list_num_chars_to_study, expired);
                    }
                    break;
            }

            TextView old_header = (TextView) list.getTag(R.id.header_view);
            if (old_header != null) {
                old_header.setText(header_text);
            } else {
                LayoutInflater inflater = getLayoutInflater();
                TextView header = (TextView) inflater.inflate(R.layout.review_list_header, list, false);
                header.setText(header_text);
                list.addHeaderView(header, null, false);
                list.setTag(R.id.header_view, header);
            }

            CursorAdapter new_adapter=null;
            switch (list_id) {
                case 0:
                    new_adapter = new VocabularyCursorAdapter(ReviewList.this, cur);
                    break;
                case 1:
                    new_adapter = new CharacterCursorAdapter(ReviewList.this, cur, myDB, true);
                    break;
            }
            adapters[list_id] = new_adapter;
            list.setAdapter(new_adapter);

        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        for (int i=0; i<2; i++) {
            if (adapters[i] != null)
                adapters[i].changeCursor(null);
        }
    }

    private class ReviewPageAdapter extends PagerAdapter {
        SparseArray<SoftReference<View>> view_storage = new SparseArray<SoftReference<View>>();

        @Override
        public int getCount() {
            return (mode==3)?2:1;
        }

        @Override
        public boolean isViewFromObject(View view, Object o) {
            ListView list = (ListView) view.findViewById(R.id.review_list);
            if (list==null)
                return false;

            return list.getTag(R.id.review_list_content)==o;
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            View new_view=null;
            int list_to_display=-1;
            switch(mode) {
                case 1:
                    list_to_display=1;
                    break;
                case 2:
                    list_to_display=0;
                    break;
                case 3:
                    list_to_display=position;
                    break;
            }

            if (view_storage.get(position) != null) {
                new_view = view_storage.get(position).get();
                ((ViewGroup)new_view.getParent()).removeView(new_view);
            }

            if (new_view == null) {
                LayoutInflater inflater = getLayoutInflater();
                new_view = inflater.inflate(R.layout.review_list, container, false);
                ListView list = (ListView) new_view.findViewById(R.id.review_list);

                if (adapters[list_to_display] != null) {
                    list.setAdapter(adapters[list_to_display]);
                } else {
                    review_loader loader = new review_loader(list, list_to_display);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
                        loader.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[])null);
                    else
                        loader.execute((Void[])null);
                }
                list.setTag(R.id.review_list_content, list_to_display);
                view_storage.put(position, new SoftReference<View>(new_view));
            }
            container.addView(new_view);
            return list_to_display;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            super.destroyItem(container, position, object);
        }
    }
}
