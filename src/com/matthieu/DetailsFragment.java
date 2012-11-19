/******************************
 * Details activity to show everything we know of a word or character
 * Also offers static function to populate a ViewGroup with those details
 */

package com.matthieu;

import android.annotation.SuppressLint;
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
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.matthieu.widget.VocabularyCursorAdapter;
import com.matthieu.widget.VocabularyCursorClickHandle;
import com.matthieu.widget.handle_details_request;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class DetailsFragment extends Fragment {
    private static final String TAG="Details";
    private boolean use_ref=false;
    private boolean exists_in_db=true;

    private String current_hanzi;
    private String current_pinyin="";
    private boolean pinned=false;

    static Object locker = new Object();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    public String get_hanzi() {
        return current_hanzi;
    }

    public String get_pinyin() {
        return current_pinyin;
    }

    public boolean is_dictionary_search() {
        return use_ref;
    }

    public boolean exists_in_db() {
        return exists_in_db;
    }

    public boolean isPinned() {
        return pinned;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Bundle bundle = getArguments();
        if (bundle != null) {
            current_hanzi = bundle.getString("hanzi");
            current_pinyin = bundle.getString("pinyin");
            use_ref = bundle.getBoolean("use_ref");
        }
        final View res = inflater.inflate(R.layout.details_fragment, container, false);
        FrameLayout frame = (FrameLayout) res.findViewById(R.id.top_level_detail_view);

        if (!current_hanzi.equals(""))
            populate_details_view(this, frame, current_hanzi, use_ref, (ReferenceActivity) getActivity());

        if (use_ref)
            check_if_exists_in_db();

        return res;
    }

    public void check_if_exists_in_db() {
        AsyncTask<Void,Void,Void> checker = new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                Activity activity= DetailsFragment.this.getActivity();
                if (activity == null)
                    return null;
                AndZiApplication app = (AndZiApplication) activity.getApplication();
                if (app == null)
                    return null;
                HanziDB db = app.getMyDB();
                if (db == null)
                    return null;
                exists_in_db = db.getTranslations(DetailsFragment.this.current_hanzi).size() > 0;

                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                SherlockFragmentActivity parent = (SherlockFragmentActivity) getActivity();
                if (parent != null)
                    parent.invalidateOptionsMenu();
            }
        }.execute((Void[])null);
    }

    @Override
    public void onDestroyView() {
        kill_detail_view((ViewGroup) getView());
        super.onDestroyView();
    }

    static void populate_details_view(final Fragment fragment, ViewGroup section,
                                      final String hanzi, final boolean ref_query, final handle_details_request new_request) {
        populate_details_view(fragment, section, hanzi, "", ref_query, new_request);
    }

    @SuppressLint({"NewApi"})
    static void populate_details_view(final Fragment fragment, ViewGroup section,
                                      final String hanzi, final String pinyin,
                                      final boolean ref_query, final handle_details_request new_request) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(fragment.getActivity());
        boolean show_pinyin_color = sp.getBoolean("color_pinyin", true);

        HanziDB db=null;
        if (ref_query) {
            db = ((AndZiApplication)fragment.getActivity().getApplication()).getRefDB();
        }
        if ((db == null) || (!ref_query)) {
            db = ((AndZiApplication)fragment.getActivity().getApplication()).getMyDB();
        }

        //Log.d(TAG, "populate_details_view: populate view for "+hanzi);

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
                        if (new_request != null)
                            new_request.new_details_request(character_label, ref_query);
                    }
                });
            top_word.addView(character);
            i+=Character.charCount(hanzi.codePointAt(i));
        }

        // write the pinyin(s) if applicable
        String info_pinyins="";
        final TextView info = (TextView) new_view.findViewById(R.id.details_info);
        if (old_pinyins != null) {
            info_pinyins = old_pinyins.toString();
        } else if (!pinyin.equals("")) {
            info_pinyins="(";
            if (show_pinyin_color)
                info_pinyins+=PinyinParser.convert_to_html_version(pinyin);
            else
                info_pinyins+=PinyinParser.convert_to_pretty_version(pinyin);
            info_pinyins+=")";
        } else if (hanzi.codePointCount(0, hanzi.length()) == 1) {
            ArrayList<String> pinyins = db.getPinyinForChar(hanzi);
            info_pinyins="(";
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
        }
        else { // TODO: handle the case where the word is in the DB and has only one pronounciation
            ArrayList<Vocabulary> voc = db.getTranslations(hanzi);
            if (voc.size() == 1) {
                info_pinyins="(";
                if (show_pinyin_color)
                    info_pinyins+=PinyinParser.convert_to_html_version(voc.get(0).pinyin);
                else
                    info_pinyins+=PinyinParser.convert_to_pretty_version(voc.get(0).pinyin);
                info_pinyins+=")";
            }
        }

        if (info_pinyins.equals("")) {
            info.setVisibility(View.GONE);
        } else {
            info.setText(Html.fromHtml(info_pinyins));
            info.setVisibility(View.VISIBLE);
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

        populator populate = new populator(fragment, hanzi, pinyin, db, section, new VocabularyCursorClickHandle() {
            @Override
            public void click(String item_clicked) {
                if (new_request != null)
                    new_request.new_details_request(item_clicked, ref_query);
            }
        });
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
        public String pinyin="";
        VocabularyCursorClickHandle clicker;

        public populator(Fragment f, String h, String p, HanziDB d, ViewGroup v, VocabularyCursorClickHandle c) {
            fragment = f;
            hanzi = h;
            pinyin = p;
            db = d;
            container = new WeakReference<ViewGroup>(v);
            elements = new ArrayList<HanziDB.StringElement>();
            clicker = c;

            ArrayList<Pinyin> pinyin_table = PinyinParser.parse_string_to_pinyin(pinyin);
            if ((!pinyin.equals("")) &&
                    ((pinyin_table==null) || ((pinyin_table.size() != hanzi.codePointCount(0, hanzi.length()))))) {
                Log.e(TAG, "Cannot match string "+h+" with pinyins "+p);
                pinyin = "";
            }

            int offset=0;
            for (int i=0; i<hanzi.length(); ) {
                if (pinyin.equals(""))
                    elements.add(new HanziDB.StringElement(HanziDB.StringElement.type_t.CHINESE,
                            hanzi.substring(i, i+Character.charCount(hanzi.codePointAt(i)))));
                else
                    elements.add(new HanziDB.StringElement(HanziDB.StringElement.type_t.CHINESE_WITH_PINYIN,
                            hanzi.substring(i, i+Character.charCount(hanzi.codePointAt(i)))+pinyin_table.get(offset).get_lame()));

                i+=Character.charCount(hanzi.codePointAt(i));
                offset++;
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
            if (loading_wheel != null)
                loading_wheel.setVisibility(View.VISIBLE);
        }

        @Override
        protected Void doInBackground(Void... voids) {
            cursor = db.getWordsWithSequence(elements, true, true);
            if (!isCancelled())
                cursor.getCount();  // force the "calculation"
            return null;
        }

        @Override
        protected void onCancelled(Void aVoid) {
            if (cursor!=null)
                cursor.close();
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            synchronized (locker) {
                if ((container.get()!=null) && (fragment.getActivity()!=null)) {
                    ListView list = (ListView) container.get().findViewById(R.id.details_list);
                    VocabularyCursorAdapter adapter = (VocabularyCursorAdapter) list.getAdapter();
                    if (adapter == null)
                        list.setAdapter(new VocabularyCursorAdapter(fragment.getActivity(), cursor, clicker));
                    else
                        adapter.changeCursor(cursor);
                    list.setVisibility(View.VISIBLE);
                    ProgressBar loading_wheel = (ProgressBar) container.get().findViewById(R.id.details_loading);
                    if (loading_wheel != null)
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

    public void pin_unpin() {
        pinned = !pinned;
    }

    public void change_source() {
        ((ReferenceActivity) getActivity()).new_details_request(current_hanzi, !use_ref);
    }

    public void edit_add_word() {
        Intent i=new Intent(getActivity(), VocabularyEditorActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        i.putExtra("Hanzi", current_hanzi);
        startActivity(i);
    }
}
