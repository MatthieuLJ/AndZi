/******************************
 * Dictionary activity to either use Details for Chinese to English or have its own functions for English translations
 */

package com.matthieu;

import android.database.Cursor;
import android.os.*;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.*;
import android.widget.*;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.matthieu.widget.MessageCursor;
import com.matthieu.widget.VocabularyCursorAdapter;
import com.matthieu.widget.VocabularyCursorClickHandle;

import java.util.ArrayList;

public class DictionaryFragment extends Fragment implements VocabularyCursorClickHandle {
    int mode; // 0 for Chinese->English, 1 for Chinese<-English
    Cursor current_cursor =null;
    static update_delayer update_task =null;
    boolean use_ref=true;
    private static final String TAG="Dictionary";

    static final int CHINESE_TO_ENGLISH_MODE=0;
    static final int ENGLISH_TO_CHINESE_MODE=1;

    String last_query="";
    int last_mode=0;
    boolean last_use_ref=true;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        mode=CHINESE_TO_ENGLISH_MODE;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View res = inflater.inflate(R.layout.dictionary, container, false);
        ((SherlockFragmentActivity)getActivity()).setSupportProgressBarIndeterminateVisibility(false);

        final ListView results = (ListView) res.findViewById(R.id.dict_results);
        results.setAdapter(new VocabularyCursorAdapter(getActivity(), null, this));

        EditText query = (EditText) res.findViewById(R.id.dict_query);
        query.setText("");
        query.addTextChangedListener(new Dictionary_query_parse());

        use_ref = (((AndZiApplication)getActivity().getApplication()).getRefDB() != null);

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
        int msg_mode;
        boolean msg_ref;

        boolean is_cancelled = false;
        HanziDB currentDB=null;

        public update_delayer(String query, int mode, boolean ref) {
            msg_query = query;
            msg_mode = mode;
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
            if ((currentDB == null) || (!msg_ref)) {
                currentDB = ((AndZiApplication)getActivity().getApplication()).getMyDB();
            }
        }

        @Override
        protected Cursor doInBackground(Void... voids) {
            Cursor result_cursor;
            Log.d(TAG, "Starting a new query for "+msg_query);

            if (currentDB == null)
                return null;

            if ((msg_query.equals(last_query)) && (msg_mode == last_mode) && (msg_ref == last_use_ref) &&
                    (current_cursor != null)) {
                Log.d(TAG, "Already had the results");
                // just to make sure it's loaded....
                current_cursor.getCount();
                current_cursor.moveToFirst();
                return current_cursor;
            }

            last_query = msg_query;
            last_mode = msg_mode;
            last_use_ref = msg_ref;
            publishProgress(null);


            if (msg_query.length()==0) {
                result_cursor =null;
            }
            else {
                if (msg_mode == CHINESE_TO_ENGLISH_MODE) {
                    ArrayList<HanziDB.StringElement> elems = PinyinParser.parse_mixed_string(msg_query);

                    if (elems == null) {
                        return new MessageCursor("Cannot understand this query");
                    }

                    boolean all_chinese=true;
                    if (elems != null)
                        for (HanziDB.StringElement e : elems) {
                            if (e.type != HanziDB.StringElement.type_t.CHINESE)
                                all_chinese=false;
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
                else if (msg_mode == ENGLISH_TO_CHINESE_MODE) {
                    if (msg_query.length()<2)
                        result_cursor=null;
                    else if (msg_query.length()<3)
                        result_cursor = currentDB.getStartingCTranslations(msg_query);
                    else
                        result_cursor = currentDB.getCTranslations(msg_query);
                }
                else
                    result_cursor =null;
            }

            if (is_cancelled)
                return null;

            // Just to make sure the query actually happens in this thread (and not the UI thread)
            if ((result_cursor != null) && (!result_cursor.isClosed())) {
                result_cursor.getCount();
                result_cursor.moveToFirst();
            }

            if (isCancelled() && result_cursor != null)
                result_cursor.close();

            return result_cursor;
        }

        @Override
        protected void onProgressUpdate(Cursor... values) {
            publish_cursor(values==null?null:values[0]);
        }

        @Override
        protected void onPostExecute(Cursor cursor) {
            publish_cursor(cursor);
            if (getActivity()!=null)
                ((SherlockFragmentActivity)getActivity()).setSupportProgressBarIndeterminateVisibility(false);
        }

        private void publish_cursor(Cursor cur) {
            if (getActivity()==null)
                return;

            if (getView()==null)
                return;
            EditText query = (EditText) getView().findViewById(R.id.dict_query);
            if (query == null)
                return;

            ((SherlockFragmentActivity)getActivity()).setSupportProgressBarIndeterminateVisibility(cur==null && !query.getText().toString().equals(""));

            final ListView results = (ListView) getView().findViewById(R.id.dict_results);

            if ((results == null) && (cur!=null)) {
                cur.close();
                return;
            }
            ((VocabularyCursorAdapter)results.getAdapter()).changeCursor(cur);
            current_cursor = cur;
        }


    }

    private void start_new_query(int mode, String query) {
        if (update_task != null) {
            update_task.cancel();
        }

        update_task = new update_delayer(query, mode, use_ref);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
            update_task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[])null);
        else
            update_task.execute((Void[])null);
    }

    private class Dictionary_query_parse implements TextWatcher {
        public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        }

        public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            start_new_query(mode, charSequence.toString());
        }

        public void afterTextChanged(Editable editable) {
        }
    }

    public void change_direction() {
        switch (mode) {
            case CHINESE_TO_ENGLISH_MODE:
                mode = ENGLISH_TO_CHINESE_MODE;
                break;
            case ENGLISH_TO_CHINESE_MODE:
                mode = CHINESE_TO_ENGLISH_MODE;
                break;
        }
        View v = getView();
        if (v == null)
            return;
        EditText query= (EditText) v.findViewById(R.id.dict_query);
        if (query!=null) {
            start_new_query(mode, query.getText().toString());
        }
    }

    public void change_source() {
        use_ref = !use_ref;
        View v = getView();
        if (v == null)
            return;
        EditText query = (EditText) v.findViewById(R.id.dict_query);
        if (query!=null) {
            start_new_query(mode, query.getText().toString());
        }
    }

    public boolean is_dictionary_search() {
        return use_ref;
    }

    public boolean is_chinese_to_english() {
        return mode == CHINESE_TO_ENGLISH_MODE;
    }
}
