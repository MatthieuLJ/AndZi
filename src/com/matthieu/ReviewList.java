package com.matthieu;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.util.SparseArray;
import android.view.*;
import android.widget.*;
import com.matthieu.widget.CharacterCursorAdapter;
import com.matthieu.widget.VocabularyCursorAdapter;
import com.matthieu.widget.VocabularyCursorClickHandle;

import java.lang.ref.SoftReference;
import java.util.Arrays;

public class ReviewList extends Activity  implements VocabularyCursorClickHandle {
    protected HanziDB myDB=null;
    private Cursor cursors[]=null;
    int mode=3; // 3 (Default), both panes, 1=only chars, 2=only words, 0=nothing...

    private static final String TAG="ReviewList";
    SparseArray<review_loader> loaders = new SparseArray<review_loader>();

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

        Object retained = getLastNonConfigurationInstance();
        if ( retained instanceof processing_to_save ) {
            loaders = ((processing_to_save) retained).loaders;
            cursors = ((processing_to_save) retained).cursors;
        } else {
            cursors = new Cursor[2];
            Arrays.fill(cursors, null);
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
            setContentView(R.layout.review_list_pages);
            ViewPager pager = (ViewPager) findViewById(R.id.reviews);
            pager.setAdapter(new ReviewPageAdapter());
        }
    }

    private class processing_to_save {
        Cursor cursors[];
        SparseArray<review_loader> loaders;
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        processing_to_save to_save = new processing_to_save();
        to_save.cursors = cursors;
        to_save.loaders = loaders;
        return to_save;
    }

    private class review_loader extends AsyncTask<Void, Void, Void> {
        View parent;
        Cursor cur;
        int list_id; // 0 for words to review, 1 for characters

        public review_loader(View v, int id) {
            parent = v;
            list_id = id;
        }

        public void set_parent_view(View v) {
            parent = v;
        }

        @Override
        protected void onPreExecute() {
            ProgressBar pb = (ProgressBar) parent.findViewById(R.id.lesson_content_loading);
            pb.setVisibility(View.VISIBLE);
        }

        @Override
        protected Void doInBackground(Void... voids) {
            switch (list_id) {
                case 0:
                    cur = myDB.getCursorWordsToStudy();
                    break;
                case 1:
                    cur = myDB.getCursorCharsToStudy();
                    break;
                default:
                    return null;
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            synchronized (ReviewList.this) {
                populate_list(parent, cur, list_id);
                cursors[list_id] = cur;
            }
        }
    }

    private void populate_list(View parent, Cursor cur, int list_id) {
        ProgressBar pb = (ProgressBar) parent.findViewById(R.id.lesson_content_loading);
        pb.setVisibility(View.GONE);
        ListView list = (ListView) parent.findViewById(R.id.review_list);

        int expired = cur.getCount();

        if (cur == null)
            return;

        String header_text="";
        switch(list_id) {
            case 0:
                header_text = getString(R.string.review_list_num_words_to_study, expired);
                break;
            case 1:
                header_text = getString(R.string.review_list_num_chars_to_study, expired);
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
                new_adapter = new VocabularyCursorAdapter(ReviewList.this, cur, ReviewList.this);
                break;
            case 1:
                new_adapter = new CharacterCursorAdapter(ReviewList.this, cur, myDB);
                break;
        }
        list.setAdapter(new_adapter);
    }

    @Override
    public void click(String item_clicked) {
        Intent i=new Intent(this, ReferenceActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);
        i.putExtra("Hanzi", item_clicked);
        i.putExtra("use_ref", false);
        startActivity(i);
    }

    /*@Override
    protected void onDestroy() {
        super.onDestroy();
        for (int i=0; i<2; i++) {
            if (cursors[i] != null)
                cursors[i].close();
        }
    }*/

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
        @SuppressLint({"NewApi"})
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
                list.setTag(R.id.review_list_content, list_to_display);

                if (cursors[list_to_display] != null) {
                    populate_list(new_view, cursors[list_to_display], list_to_display);
                } else {
                    synchronized (ReviewList.this) {
                        if ((loaders.get(position) == null) || (loaders.get(position).getStatus() == AsyncTask.Status.FINISHED)) {
                            loaders.put(position,new review_loader(new_view, list_to_display));
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
                                loaders.get(position).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[])null);
                            else
                                loaders.get(position).execute((Void[])null);
                        } else {
                            loaders.get(position).set_parent_view(new_view);
                        }
                    }
                }
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
