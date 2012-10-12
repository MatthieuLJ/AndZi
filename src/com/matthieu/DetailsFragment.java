/******************************
 * Details activity to show everything we know of a word or character
 * Also offers static function to populate a ViewGroup with those details
 */

package com.matthieu;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.text.Html;
import android.util.Log;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import com.matthieu.widget.VocabularyCursorAdapter;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class DetailsFragment extends Fragment {
    private static final String TAG="Details";
    private boolean use_ref=false;

    private String current_hanzi;
    private Activity parent_activity;

    static Object locker = new Object();

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        parent_activity = activity;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        FrameLayout frame = new FrameLayout(parent_activity);
        frame.setId(R.id.top_level_detail_view);
        HanziDB db=null;
        if (use_ref) {
            db = ((AndZiApplication)parent_activity.getApplication()).getRefDB();
        }
        if ((db == null) || (!use_ref)) {
            db = ((AndZiApplication)parent_activity.getApplication()).getMyDB();
        }

        if (!current_hanzi.equals(""))
            populate_details_view(this, frame, current_hanzi, db);
        return frame;
    }

    public void switch_to_reference(boolean use_ref) {
        this.use_ref = use_ref;
    }

    public void change_chinese(String hanzi) {
        current_hanzi = hanzi;
        //TOOD : if parent_activity is not null, update it...
    }

    @Override
    public void onDetach() {
        parent_activity = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        View master_view = getView();
        if (master_view != null) {
            final ListView list= (ListView) master_view.findViewById(R.id.details_list);
            if (list != null) {
                synchronized (locker) {
                    WeakReference<populator> old_populate_task = (WeakReference<populator>) list.getTag(R.id.detail_populator);
                    if ((old_populate_task!=null) && (old_populate_task.get()!=null))
                        old_populate_task.get().cancel(false);
                    VocabularyCursorAdapter adapter = (VocabularyCursorAdapter) list.getAdapter();
                    if (adapter != null) {
                        adapter.changeCursor(null);
                    }
                }
            }
        }
    }

    /*
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        FrameLayout frame = (FrameLayout) getView().findViewById(R.id.top_level_detail_view);
        if (frame == null) {
            frame = new FrameLayout(this);
            frame.setId(R.id.top_level_detail_view);
        }
        populate_details_view(this, frame, current_hanzi, db);
        setContentView(frame);
    }
    */

    void populate_details_view(final Fragment fragment, ViewGroup section, final String hanzi, final HanziDB db) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(fragment.getActivity());
        boolean show_pinyin_color = sp.getBoolean("color_pinyin", true);

        Log.d(TAG, "populate_details_view: populate view for "+hanzi);

        ListView old_list= (ListView) section.findViewById(R.id.details_list);
        VocabularyCursorAdapter old_adapter=null;
        WeakReference<populator> old_populate_task = null;
        if (old_list != null) {
            old_adapter = (VocabularyCursorAdapter) old_list.getAdapter();
            old_populate_task = (WeakReference<populator>) old_list.getTag(R.id.detail_populator);
        }
        String old_query = (String) section.getTag(R.id.detail_query);
        CharSequence old_pinyins=null;
        if ((old_query != null) && (hanzi.equals(old_query)) && (hanzi.codePointCount(0, hanzi.length()) == 1)) {
            TextView old_info = (TextView) section.findViewById(R.id.details_info);
            if (old_info!=null) {
                old_pinyins = old_info.getText();
            }
        }

        section.removeAllViews();
        View new_view = View.inflate(fragment.getActivity(), R.layout.details, section);

        TextView info = (TextView) new_view.findViewById(R.id.details_info);
        if (hanzi.codePointCount(0, hanzi.length()) == 1) {
            if (old_pinyins != null)
                info.setText(old_pinyins);
            else {
                ArrayList<String> pinyins = db.getPinyinForChar(hanzi);
                String info_pinyins="(";
                for (int i=0; i<pinyins.size(); i++) {
                    String pin=pinyins.get(i);

                    if (show_pinyin_color)
                        info_pinyins+=PinyinParser.convert_to_html_version(pin);
                    else
                        info_pinyins+=PinyinParser.convert_to_pretty_version(pin);
                    if (i < pinyins.size()-1)
                        info_pinyins+=", ";
                }
                info_pinyins+=")";

                info.setText(Html.fromHtml(info_pinyins));
            }
            info.setVisibility(View.VISIBLE);
        }
        else {
            info.setVisibility(View.GONE);
        }

        // first write the word at the top
        LinearLayout top_word = (LinearLayout) new_view.findViewById(R.id.details_title);
        top_word.removeAllViews();
        for (int i=0; i<hanzi.length(); ) {
            TextView character = new TextView(fragment.getActivity());
            final String character_label = hanzi.substring(i, i + Character.charCount(hanzi.codePointAt(i)));
            character.setTextAppearance(fragment.getActivity(), android.R.style.TextAppearance_Large_Inverse);
            character.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40);
            character.setText(character_label);
            if (hanzi.codePointCount(0, hanzi.length())!= 1)
                character.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View view) {
                        DetailsFragment.this.change_chinese(character_label);
                    }
                });
            top_word.addView(character);
            i+=Character.charCount(hanzi.codePointAt(i));
        }

        final ListView list= (ListView) new_view.findViewById(R.id.details_list);
        if (old_list != null) {
            synchronized (locker) {
                if ((old_populate_task!=null) && (old_populate_task.get() != null) && (old_populate_task.get().getStatus() != AsyncTask.Status.FINISHED)) {
                    if (hanzi.equals(old_populate_task.get().hanzi)) {
                        old_populate_task.get().changeList(list);
                        list.setTag(R.id.detail_populator, old_populate_task);
                        if (old_adapter != null)
                            list.setAdapter(old_adapter);
                        return;
                    } else {
                        old_populate_task.get().cancel(false);
                    }
                }
                else {
                    if ((old_adapter != null) && (old_query!= null) && (old_query.equals(hanzi))) {
                        list.setAdapter(old_adapter);
                        return;
                    }
                    else if (old_adapter != null) {
                        old_adapter.changeCursor(null);
                    }
                }
            }
        }

        populator populate = new populator(fragment, hanzi, db, list);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
            populate.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[])null);
        else
            populate.execute((Void[])null);

        list.setTag(R.id.detail_populator, new WeakReference<populator>(populate));
        section.setTag(R.id.detail_query, hanzi);
    }

    private class populator extends AsyncTask<Void,Void,Void> {
        Cursor cursor;
        ProgressDialog mProgressDialog;
        Timer show_progress_timer = new Timer();
        Fragment fragment;
        ArrayList<HanziDB.StringElement> elements;
        HanziDB db;
        WeakReference<ListView> list;
        public String hanzi;

        public populator(Fragment f, String h, HanziDB d, ListView l) {
            fragment = f;
            hanzi = h;
            db = d;
            list = new WeakReference<ListView>(l);
            elements = new ArrayList<HanziDB.StringElement>();
            for (int i=0; i<hanzi.length(); ) {
                elements.add(new HanziDB.StringElement(HanziDB.StringElement.CHINESE,
                        hanzi.substring(i, i+Character.charCount(hanzi.codePointAt(i)))));
                i+=Character.charCount(hanzi.codePointAt(i));
            }
        }

        public void changeList(ListView l) {
            list = new WeakReference<ListView>(l);
        }
        @Override
        protected void onPreExecute() {
            mProgressDialog = new ProgressDialog(fragment.getActivity());
            mProgressDialog.setMessage(fragment.getActivity().getResources().getString(R.string.loading));
            mProgressDialog.setIndeterminate(true);
            mProgressDialog.setCancelable(true);
            mProgressDialog.setCanceledOnTouchOutside(true);
            mProgressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                public void onCancel(DialogInterface dialogInterface) {
                    populator.this.cancel(false);
                    dialogInterface.dismiss();
                    if (fragment.getClass().equals(DetailsFragment.class)) {
                        /*
                        if(!((DetailsFragment)fragment).go_back_one())
                            fragment.getActivity().finish();
                            */
                    }
                }
            });
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            TimerTask show_progress = new TimerTask() {
                @Override
                public void run() {
                    fragment.getActivity().runOnUiThread(new Runnable() {
                        public void run() {
                            synchronized (mProgressDialog) {
                                if (list.get()!=null) {
                                    VocabularyCursorAdapter adapter = (VocabularyCursorAdapter) list.get().getAdapter();
                                    if(adapter==null)
                                        mProgressDialog.show();
                                }
                            }
                        }
                    });
                }
            };
            show_progress_timer.schedule(show_progress,500);

        }
        @Override
        protected Void doInBackground(Void... voids) {
            cursor = db.getWordsWithSequence(elements, true, true);
            if (!isCancelled())
                cursor.getCount();  // force the "calculation"
            return (Void) null;
        }

        @Override
        protected void onCancelled(Void aVoid) {
            show_progress_timer.cancel();
            mProgressDialog.dismiss();
            if (cursor!=null)
                cursor.close();
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            synchronized (mProgressDialog) {
                show_progress_timer.cancel();
                mProgressDialog.dismiss();

                synchronized (locker) {
                    if (list.get()!=null) {
                        VocabularyCursorAdapter adapter = (VocabularyCursorAdapter) list.get().getAdapter();
                        if (adapter == null)
                            list.get().setAdapter(new VocabularyCursorAdapter(fragment.getActivity(), cursor, null));
                        else
                            adapter.changeCursor(cursor);
                    }
                    else {
                        cursor.close();
                    }
                }
            }
        }
    }

/*
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.details_menu, menu);

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem add_word = menu.findItem(R.id.details_add_word);
        HanziDB myDB = ((AndZiApplication) getApplication()).getMyDB();
        if (myDB.getTranslations(current_hanzi).size()>0) {
            add_word.setTitle("Edit word in vocabulary list");
        }
        else {
            add_word.setTitle("Add word to vocabulary list");
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.details_add_word:
                Intent i=new Intent(this, AddVocabularyActivity.class);
                i.putExtra("Hanzi", current_hanzi);
                startActivity(i);
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }
    */
}
