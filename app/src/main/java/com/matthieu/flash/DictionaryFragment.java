/******************************
 * Dictionary activity to either use Details for Chinese to English or have its own functions for English translations
 */

package com.matthieu.flash;

import android.annotation.SuppressLint;
import android.database.Cursor;
import android.database.MergeCursor;
import android.os.*;
import androidx.fragment.app.Fragment;

import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.*;
import android.widget.*;
import com.matthieu.flash.widget.MessageCursor;
import com.matthieu.flash.widget.VocabularyCursorAdapter;
import com.matthieu.flash.widget.VocabularyCursorClickHandle;
import com.matthieu.flash.R;

import java.util.ArrayList;

public class DictionaryFragment extends Fragment implements VocabularyCursorClickHandle {
    Cursor current_cursor =null;
    static update_delayer update_task =null;
    boolean use_ref=true;

    private static final boolean DEBUG = false;
    private static final String TAG="Dictionary";

    static final int CHINESE_TO_ENGLISH_MODE=0;
    static final int ENGLISH_TO_CHINESE_MODE=1;

    String last_query="";
    int last_mode=-1;
    boolean last_use_ref=true;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View res = inflater.inflate(R.layout.dictionary, container, false);
        // TODO
        //  ((FragmentActivity)getActivity()).setSupportProgressBarIndeterminateVisibility(false);

        final ListView results = res.findViewById(R.id.dict_results);
        results.setAdapter(new VocabularyCursorAdapter(getActivity(), null, this));

        EditText query = res.findViewById(R.id.dict_query);
        query.setText("");
        query.addTextChangedListener(new Dictionary_query_parse());

        final Button source_button = res.findViewById(R.id.details_source_button);
        if (source_button != null) {
            if (is_dictionary_search())
                source_button.setText(R.string.details_dictionary);
            else
                source_button.setText(R.string.details_voc_list);
            source_button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    change_source();
                    if (is_dictionary_search())
                        source_button.setText(R.string.details_dictionary);
                    else
                        source_button.setText(R.string.details_voc_list);

                }
            });

        }

        return res;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (update_task != null) {
            update_task.cancel();
            update_task = null;
        }
        synchronized (DictionaryFragment.this) {
            if (current_cursor != null) {
                current_cursor.close();
                current_cursor = null;
            }
        }
    }

    @Override
    public void click(String item_clicked) {
        ((ReferenceActivity)getActivity()).new_details_request(item_clicked, use_ref);
    }

    private class update_delayer extends AsyncTask<Void, Cursor, Cursor> {
        String msg_query;
        boolean msg_ref;
        String cannot_understand_query;

        boolean is_cancelled = false;
        HanziDB currentDB=null;

        public update_delayer(String query, boolean ref) {
            msg_query = query;
            msg_ref = ref;
        }

        public void cancel() {
            is_cancelled=true;
        }

        @Override
        protected void onPreExecute() {
            if (getActivity()== null)
                return;

            if (msg_ref) {
                currentDB = ((AndZiApplication)getActivity().getApplication()).getRefDB();
            }
            else {
                currentDB = ((AndZiApplication)getActivity().getApplication()).getMyDB();
            }
            cannot_understand_query = getResources().getString(R.string.reference_cannot_understand);
        }

        @Override
        protected Cursor doInBackground(Void... voids) {
            Cursor result_cursor=null;
            boolean potential_english=true;
            if (DEBUG) Log.d(TAG, "Starting a new query for "+msg_query);

            long start_time = System.currentTimeMillis();

            if (currentDB == null)
                return null;

            int mode = detect_mode(msg_query);

            if (DEBUG) Log.d(TAG, "Detected mode "+mode);
            if (mode == -1) {
                current_cursor = new MessageCursor(cannot_understand_query);
                return current_cursor;
            }

            synchronized (DictionaryFragment.this) {
                if ((mode == last_mode) && (msg_query.trim().equals(last_query.trim())) && (msg_ref == last_use_ref) &&
                        (current_cursor != null)) {
                    if (DEBUG) Log.d(TAG, "Already had the results");
                    // just to make sure it's loaded....
                    current_cursor.getCount();
                    current_cursor.moveToFirst();
                    return current_cursor;
                }

                View v = getView();
                if (v == null)
                    return null;
                EditText query = v.findViewById(R.id.dict_query);
                if (query != null) {
                    if ((!msg_query.trim().equals(query.getText().toString().trim())) || (msg_ref != use_ref)) {
                        return null;
                    }
                }

                last_query = msg_query;
                last_mode = mode;
                last_use_ref = msg_ref;
                publishProgress((Cursor[])null);

                if (msg_query.length()==0) {
                    result_cursor =null;
                }
                else {
                    if (mode == CHINESE_TO_ENGLISH_MODE) {
                        ArrayList<HanziDB.StringElement> elems = PinyinParser.parse_mixed_string(msg_query);

                        if (elems.size() > 1) {
                            for (HanziDB.StringElement elem : elems) {
                                if ((elem.type != HanziDB.StringElement.type_t.PINYIN_WITHOUT_TONE) &&
                                        (elem.type != HanziDB.StringElement.type_t.INCOMPLETE_PINYIN)) {
                                    potential_english = false;
                                    break;
                                }
                            }
                        }

                        boolean all_chinese=true;
                        for (HanziDB.StringElement e : elems) {
                            if (e.type != HanziDB.StringElement.type_t.CHINESE) {
                                all_chinese = false;
                                break;
                            }
                        }

                        if (elems.size()==1) {
                            switch(elems.get(0).type) {
                                case CHINESE:
                                    result_cursor = currentDB.getWordsStartingWithSequence(elems, true);
                                    break;
                                case PINYIN_WITH_TONE:
                                case PINYIN_WITHOUT_TONE:
                                    result_cursor = currentDB.getWordsAsSequence(elems, true);
                                    break;
                                case INCOMPLETE_PINYIN:
                                default:
                                    result_cursor = null;
                                    break;
                            }
                        } else if (elems.size()==2) {
                            switch(elems.get(0).type) {
                                case CHINESE:
                                case PINYIN_WITH_TONE:
                                    switch (elems.get(1).type) {
                                        case CHINESE:
                                        case PINYIN_WITH_TONE:
                                            result_cursor = currentDB.getWordsWithSequence(elems, true);
                                            break;
                                        case PINYIN_WITHOUT_TONE:
                                        case INCOMPLETE_PINYIN:
                                            result_cursor = currentDB.getWordsStartingWithSequence(elems, true);
                                            break;
                                        default:
                                            result_cursor=null;
                                    }
                                    break;
                                case PINYIN_WITHOUT_TONE:
                                    if (elems.get(1).type == HanziDB.StringElement.type_t.INCOMPLETE_PINYIN)
                                        result_cursor = currentDB.getWordsStartingWithSequence(elems, true);
                                    else
                                        result_cursor = currentDB.getWordsAsSequence(elems, true);
                                    break;
                                default:
                                    result_cursor=null;
                                    break;
                            }
                        } else if ((all_chinese) && (elems.size() >= 3) && (currentDB.getTranslations(msg_query).size()==0))
                            result_cursor = currentDB.getDecomposedParts(elems, 4, true);
                        else
                            result_cursor = currentDB.getWordsWithSequence(elems, true);
                    }
                    if ((mode == ENGLISH_TO_CHINESE_MODE) || (potential_english && (result_cursor==null || result_cursor.getCount()==0))) {
                        if (result_cursor != null)
                            result_cursor.close();

                        if (!msg_query.contains(" ") && !msg_query.contains(",")) {
                            if (msg_query.trim().length()<2)
                                result_cursor=null;
                            else if (msg_query.trim().length()<3)
                                result_cursor = currentDB.getStartingCTranslations(msg_query.trim());
                            else
                                result_cursor = currentDB.getCTranslations(msg_query.trim());
                        } else { // some work to do to handle several words
                            Cursor result1_cursor=null, result2_cursor=null, result3_cursor=null;
                            if (msg_query.contains(",")) {
                                String[] items = msg_query.split(",");
                                ArrayList<String> prepared_items = new ArrayList<String>();
                                for (String i : items) {
                                    i = i.trim();
                                    if (i.length() > 1)
                                        prepared_items.add(i);
                                }
                                if (prepared_items.size() > 0)
                                    result1_cursor = currentDB.getCTranslations(prepared_items, true);
                                else
                                    result1_cursor = null;
                            }
                            if (msg_query.contains(" ") && ((result1_cursor==null) || (result1_cursor.getCount()<2))) {
                                String[] items = msg_query.split("[, ]");
                                ArrayList<String> prepared_items = new ArrayList<String>();
                                for (String i : items) {
                                    i = i.trim();
                                    if (i.length() > 2)
                                        prepared_items.add(i);
                                }
                                if (prepared_items.size() > 0)
                                    result2_cursor = currentDB.getCTranslations(prepared_items, true);
                                else
                                    result2_cursor = null;
                            }
                            if (((result1_cursor==null)?0:result1_cursor.getCount()) + ((result2_cursor==null)?0:result2_cursor.getCount()) < 5) {
                                String[] items = msg_query.split("[, ]");
                                ArrayList<String> prepared_items = new ArrayList<String>();
                                for (String i : items) {
                                    i = i.trim();
                                    if (i.length() > 2)
                                        prepared_items.add(i);
                                }
                                if (prepared_items.size() > 0)
                                    result3_cursor = currentDB.getCTranslations(prepared_items, false);
                                else
                                    result3_cursor = null;
                            }
                            ArrayList<Cursor> all_cursors = new ArrayList<Cursor>();
                            if (result1_cursor != null)
                                all_cursors.add(result1_cursor);
                            if (result2_cursor != null)
                                all_cursors.add(result2_cursor);
                            if (result3_cursor != null)
                                all_cursors.add(result3_cursor);

                            if (all_cursors.size() != 0)
                                result_cursor = new MergeCursor(all_cursors.toArray(new Cursor[3]));
                            else
                                result_cursor = null;
                        }
                    }
                }

                // Just to make sure the query actually happens in this thread (and not the UI thread)
                if ((result_cursor != null) && (!result_cursor.isClosed())) {
                    result_cursor.getCount();
                    result_cursor.moveToFirst();
                }

                if (isCancelled() && result_cursor != null)
                    result_cursor.close();

                current_cursor = result_cursor;
                long end_time = System.currentTimeMillis();
                if (DEBUG) Log.d(TAG, "Dictionary query done in "+((end_time-start_time)/1000)+"s");
                return result_cursor;
            }
        }

        @Override
        protected void onProgressUpdate(Cursor... values) {
            if ((getActivity()!=null) && (getView() != null)) {
                final ListView results = getView().findViewById(R.id.dict_results);
                Cursor current_cursor = ((VocabularyCursorAdapter)results.getAdapter()).getCursor();
                if (current_cursor instanceof MessageCursor)
                    ((VocabularyCursorAdapter)results.getAdapter()).changeCursor(null);
                // TODO
                //  ((SherlockFragmentActivity)getActivity()).setSupportProgressBarIndeterminateVisibility(true);
            }
        }

        @Override
        protected void onPostExecute(Cursor cursor) {
            if ((getActivity()!=null) && (getView() != null)) {
                EditText query = getView().findViewById(R.id.dict_query);
                if (msg_query.trim().equals(query.getText().toString().trim()) && (msg_ref == use_ref)) {
                    publish_cursor(cursor);
                    // TODO
                    //  ((SherlockFragmentActivity)getActivity()).setSupportProgressBarIndeterminateVisibility(false);
                }
                else {
                    if (cursor!=null)
                        cursor.close();
                }
            }
        }

        @SuppressLint({"NewApi"})
        private void publish_cursor(Cursor cur) {
            if (getActivity()==null)
                return;

            if (getView()==null)
                return;
            EditText query = getView().findViewById(R.id.dict_query);
            if (query == null)
                return;

            if ((!msg_query.trim().equals(query.getText().toString().trim())) || (msg_ref != use_ref))
                return;

            if (DEBUG) Log.d(TAG, "msg_query is "+msg_query+", and current search is "+query.getText());

            final ListView results = getView().findViewById(R.id.dict_results);

            if (results == null) {
                if (cur!=null)
                    cur.close();
                return;
            }
            ((VocabularyCursorAdapter)results.getAdapter()).changeCursor(cur);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO)
                results.smoothScrollToPosition(0);
        }
    }

    @SuppressLint({"NewApi"})
    private void start_new_query(String query) {
        if (update_task != null) {
            update_task.cancel();
        }

        update_task = new update_delayer(query, use_ref);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
            update_task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[])null);
        else
            update_task.execute((Void[])null);
    }

    private class Dictionary_query_parse implements TextWatcher {
        public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        }

        public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            start_new_query(charSequence.toString());
        }

        public void afterTextChanged(Editable editable) {
        }
    }

    public void change_source() {
        use_ref = !use_ref;
        View v = getView();
        if (v == null)
            return;
        EditText query = v.findViewById(R.id.dict_query);
        if (query!=null) {
            start_new_query(query.getText().toString());
        }
    }

    public boolean has_search() {
        View v = getView();
        if (v == null)
            return false;
        EditText query = v.findViewById(R.id.dict_query);
        return query != null && !query.getText().toString().equals("");
    }

    public boolean is_dictionary_search() {
        return use_ref;
    }

    private int detect_mode(String entry) {
        int len = entry.length();
        int res=-1;

        for (int i=0; i<len;) {
            int character=entry.codePointAt(i);
            if (((character >= 0x4E00) && (character <= 0x9fff)) // main CJK
                    || ((character >= 0x2e00) && (character <= 0x31ff)) // additional punctuation
                    || ((character >= 0x3400) && (character <= 0x4dbf)) // extension A
                    )
            {
                if (res == ENGLISH_TO_CHINESE_MODE)
                    return -1;
                res = CHINESE_TO_ENGLISH_MODE;
            }
            if (entry.substring(i, i+1).matches("[, ]"))
            {
                if (res == CHINESE_TO_ENGLISH_MODE)
                    return -1;
                res= ENGLISH_TO_CHINESE_MODE;
            }
            i+=Character.charCount(character);
        }
        if (res != -1)
            return res;

        ArrayList<HanziDB.StringElement> elems = PinyinParser.parse_mixed_string(entry);
        if (elems == null)
            return ENGLISH_TO_CHINESE_MODE;
        else
            return CHINESE_TO_ENGLISH_MODE;
    }
}
