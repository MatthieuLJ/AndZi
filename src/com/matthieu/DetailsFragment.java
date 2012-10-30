/******************************
 * Details activity to show everything we know of a word or character
 * Also offers static function to populate a ViewGroup with those details
 */

package com.matthieu;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
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
import com.matthieu.widget.handle_details_request;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class DetailsFragment extends Fragment {
    private static final String TAG="Details";
    private boolean use_ref=false;

    private String current_hanzi;
    private boolean pinned=false;

    static Object locker = new Object();

    public DetailsFragment(String hanzi, boolean use_ref) {
        current_hanzi = hanzi;
        this.use_ref = use_ref;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
    }

    public String get_hanzi() {
        return current_hanzi;
    }

    public boolean get_use_ref() {
        return use_ref;
    }

    public boolean isPinned() {
        return pinned;
    }

    public boolean is_same_query(DetailsFragment other) {
        return other.get_hanzi().equals(get_hanzi()) && (other.get_use_ref() == get_use_ref());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View res = inflater.inflate(R.layout.details_fragment, container, false);
        FrameLayout frame = (FrameLayout) res.findViewById(R.id.top_level_detail_view);

        if (!current_hanzi.equals(""))
            populate_details_view(this, frame, current_hanzi, use_ref, (ReferenceActivity) getActivity());

        Button pin_button = (Button) res.findViewById(R.id.details_pin);
        pin_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                DetailsFragment.this.pinned = !DetailsFragment.this.pinned;
                if (DetailsFragment.this.pinned)
                    ((Button) view).setText(R.string.details_unpin);
                else
                    ((Button) view).setText(R.string.details_pin);
            }
        });

        Button source = (Button) res.findViewById(R.id.details_dict);
        source.setText(use_ref?R.string.details_dictionary:R.string.details_voc_list);
        source.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ((ReferenceActivity) getActivity()).new_details_request(current_hanzi, !use_ref);
            }
        });

        Button add_edit = (Button) res.findViewById(R.id.details_add_edit);
        HanziDB myDB = ((AndZiApplication) getActivity().getApplication()).getMyDB();
        if (myDB.getTranslations(current_hanzi).size()!=0) {
            add_edit.setText(R.string.details_edit);
        } else {
            add_edit.setText(R.string.details_add);
        }
        add_edit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent i=new Intent(getActivity(), VocabularyEditorActivity.class);
                i.putExtra("Hanzi", current_hanzi);
                startActivity(i);
            }
        });

        return res;
    }

    @Override
    public void onDestroyView() {
        kill_detail_view((ViewGroup) getView());
        super.onDestroyView();
    }

    static void populate_details_view(final Fragment fragment, ViewGroup section,
                                      final String hanzi, final boolean use_ref, final handle_details_request new_request) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(fragment.getActivity());
        boolean show_pinyin_color = sp.getBoolean("color_pinyin", true);

        HanziDB db=null;
        if (use_ref) {
            db = ((AndZiApplication)fragment.getActivity().getApplication()).getRefDB();
        }
        if ((db == null) || (!use_ref)) {
            db = ((AndZiApplication)fragment.getActivity().getApplication()).getMyDB();
        }

        Log.d(TAG, "populate_details_view: populate view for "+hanzi);

        // if the view already had a detail list...
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
        final View new_view = View.inflate(fragment.getActivity(), R.layout.details, section);

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
                        new_request.new_details_request(character_label, use_ref);
                    }
                });
            top_word.addView(character);
            i+=Character.charCount(hanzi.codePointAt(i));
        }

        // write the pinyin(s) if applicable
        final TextView info = (TextView) new_view.findViewById(R.id.details_info);
        if (old_pinyins != null) {
            info.setText(old_pinyins);
            info.setVisibility(View.VISIBLE);
        } else if (hanzi.codePointCount(0, hanzi.length()) == 1) {
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

            info.setVisibility(View.VISIBLE);
        }
        else { // TODO: handle the case where the word is in the DB and has only one pronounciation
            info.setVisibility(View.GONE);
        }

        // populate the list
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

        populator populate = new populator(fragment, hanzi, db, section);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
            populate.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[])null);
        else
            populate.execute((Void[])null);

        list.setTag(R.id.detail_populator, new WeakReference<populator>(populate));
        section.setTag(R.id.detail_query, hanzi);
    }

    static private class populator extends AsyncTask<Void,Void,Void> {
        Cursor cursor;
        Fragment fragment;
        ArrayList<HanziDB.StringElement> elements;
        HanziDB db;
        WeakReference<ViewGroup> container;
        public String hanzi;

        public populator(Fragment f, String h, HanziDB d, ViewGroup v) {
            fragment = f;
            hanzi = h;
            db = d;
            container = new WeakReference<ViewGroup>(v);
            elements = new ArrayList<HanziDB.StringElement>();
            for (int i=0; i<hanzi.length(); ) {
                elements.add(new HanziDB.StringElement(HanziDB.StringElement.CHINESE,
                        hanzi.substring(i, i+Character.charCount(hanzi.codePointAt(i)))));
                i+=Character.charCount(hanzi.codePointAt(i));
            }
        }

        public void changeList(ViewGroup v) {
            container = new WeakReference<ViewGroup>(v);
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            ListView list = (ListView) container.get().findViewById(R.id.details_list);
            list.setVisibility(View.GONE);
            ProgressBar loading_wheel = (ProgressBar) container.get().findViewById(R.id.details_loading);
            loading_wheel.setVisibility(View.VISIBLE);
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
            if (cursor!=null)
                cursor.close();
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            synchronized (locker) {
                if (container.get()!=null) {
                    ListView list = (ListView) container.get().findViewById(R.id.details_list);
                    VocabularyCursorAdapter adapter = (VocabularyCursorAdapter) list.getAdapter();
                    if (adapter == null)
                        list.setAdapter(new VocabularyCursorAdapter(fragment.getActivity(), cursor, null));
                    else
                        adapter.changeCursor(cursor);
                    list.setVisibility(View.VISIBLE);
                    ProgressBar loading_wheel = (ProgressBar) container.get().findViewById(R.id.details_loading);
                    loading_wheel.setVisibility(View.GONE);
                }
                else {
                    cursor.close();
                }
            }
        }
    }

    static public void kill_detail_view(ViewGroup section) {
        if (section == null)
            return;
        ListView old_list= (ListView) section.findViewById(R.id.details_list);
        if (old_list == null)
            return;
        synchronized (locker) {
            WeakReference<populator> old_populate_task = (WeakReference<populator>) old_list.getTag(R.id.detail_populator);
            if ((old_populate_task!=null) && (old_populate_task.get()!=null))
                old_populate_task.get().cancel(false);

            VocabularyCursorAdapter old_adapter = (VocabularyCursorAdapter) old_list.getAdapter();
            if (old_adapter != null) {
                old_adapter.changeCursor(null);
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
