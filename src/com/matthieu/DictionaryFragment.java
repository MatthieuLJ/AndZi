/******************************
 * Dictionary activity to either use Details for Chinese to English or have its own functions for English translations
 */

package com.matthieu;

import android.annotation.SuppressLint;
import android.app.Activity;
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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public class DictionaryFragment extends Fragment implements VocabularyCursorClickHandle {
    int mode; // 0 for Chinese->English, 1 for Chinese<-English
    Cursor current_cursor =null;
    static update_delayer update_thread=null;
    boolean use_ref=true;
    private static final String TAG="Dictionary";

    static final int CHINESE_TO_ENGLISH_MODE=0;
    static final int ENGLISH_TO_CHINESE_MODE=1;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View res = inflater.inflate(R.layout.dictionary, container, false);
        ((SherlockFragmentActivity)getActivity()).setSupportProgressBarIndeterminateVisibility(false);
        mode=CHINESE_TO_ENGLISH_MODE;

        final ListView results = (ListView) res.findViewById(R.id.dict_results);
        results.setAdapter(new VocabularyCursorAdapter(getActivity(), null, this));

        Button dict_mode = (Button) res.findViewById(R.id.dict_mode);
        dict_mode.setText(R.string.dictionary_chinese_to_eng);
        dict_mode.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                switch (mode) {
                    case CHINESE_TO_ENGLISH_MODE:
                        mode = ENGLISH_TO_CHINESE_MODE;
                        ((TextView) view).setText(R.string.dictionary_eng_to_chinese);
                        break;
                    case ENGLISH_TO_CHINESE_MODE:
                        mode = CHINESE_TO_ENGLISH_MODE;
                        ((TextView) view).setText(R.string.dictionary_chinese_to_eng);
                        break;
                }
                EditText edit_query= (EditText) res.findViewById(R.id.dict_query);
                start_new_query(mode, edit_query.getText().toString());
            }
        });

        EditText query = (EditText) res.findViewById(R.id.dict_query);
        query.setText("");
        query.addTextChangedListener(new Dictionary_query_parse());

        Button change_dict = (Button) res.findViewById(R.id.dict_source);
        if (((AndZiApplication)getActivity().getApplication()).getRefDB() != null) {
            use_ref = true;
            change_dict.setText(R.string.details_dictionary);
            change_dict.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    use_ref = !use_ref;
                    ((Button)view).setText(use_ref?R.string.details_dictionary:R.string.details_voc_list);
                    EditText query = (EditText) res.findViewById(R.id.dict_query);
                    if (query!=null) {
                        start_new_query(mode, query.getText().toString());
                    }
                }
            });
        }
        else {
            change_dict.setEnabled(false);
        }

        return res;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        if (update_thread != null) {
            update_thread.cancel();
            update_thread = null;
        }
        synchronized (DictionaryFragment.this) {
            if (current_cursor != null)
                current_cursor.close();
        }
    }

    @Override
    public void click(String item_clicked) {
        ((ReferenceActivity)getActivity()).new_details_request(item_clicked, use_ref);
    }

    private static class update_delayer extends Thread {
        String msg_query;
        int msg_mode;
        boolean msg_ref;
        Cursor result_cursor;
        DictionaryFragment parent;
        boolean is_cancelled = false;

        public update_delayer(String query, int mode, boolean ref, DictionaryFragment dict) {
            msg_query = query;
            msg_mode = mode;
            msg_ref = ref;
            parent = dict;
        }

        @Override
        public void run() {
            update_list();
        }

        public void cancel() {
            is_cancelled=true;
        }

        private void update_list() {
            Log.d(TAG, "Starting a new query for "+msg_query);

            HanziDB currentDB=null;
            if (msg_ref) {
                currentDB = ((AndZiApplication)parent.getActivity().getApplication()).getRefDB();
            }
            if ((currentDB == null) || (!msg_ref)) {
                currentDB = ((AndZiApplication)parent.getActivity().getApplication()).getMyDB();
            }

            publish_cursor(null);

            if (msg_query.length()==0) {
                result_cursor =null;
            }
            else {
                if (msg_mode == CHINESE_TO_ENGLISH_MODE) {
                    ArrayList<HanziDB.StringElement> elems = PinyinParser.parse_mixed_string(msg_query);
                    boolean all_chinese=true;
                    if (elems != null)
                        for (HanziDB.StringElement e : elems) {
                            if (e.type != HanziDB.StringElement.type_t.CHINESE)
                                all_chinese=false;
                        }

                    if (elems == null)
                        result_cursor = new MessageCursor("Cannot understand this query");
                    else if (elems.size()==1) {
                        if (elems.get(0).type == HanziDB.StringElement.type_t.INCOMPLETE_PINYIN)
                            result_cursor = null;
                        else
                            result_cursor = currentDB.getWordsStartingWithSequence(elems, true, true);
                    }
                    else if ((all_chinese) && (elems.size() >= 3) && (currentDB.getTranslations(msg_query).size()==0))
                        result_cursor = currentDB.getDecomposedParts(elems, 4, true, true);
                    else
                        result_cursor = currentDB.getWordsWithSequence(elems, true, true);
                }
                else if (msg_mode == ENGLISH_TO_CHINESE_MODE) {
                    if (msg_query.length()<2)
                        result_cursor=null;
                    else if (msg_query.length()<3)
                        result_cursor = currentDB.getFilteredStartingCTranslations(msg_query);
                    else
                        result_cursor = currentDB.getFilteredCTranslations(msg_query);
                }
                else
                    result_cursor =null;
            }

            if (is_cancelled)
                return;

            // Just to make sure the query actually happens in this thread (and not the UI thread)
            if ((result_cursor != null) && (!result_cursor.isClosed())) {
                result_cursor.getCount();
                result_cursor.moveToFirst();
            }

            if (!is_cancelled) {
                publish_cursor(result_cursor);
            } else if (result_cursor != null) {
                result_cursor.close();
            }
        }

        private void publish_cursor(@Nullable final Cursor cur) {
            parent.getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    synchronized (parent) {
                        EditText query = (EditText) parent.getView().findViewById(R.id.dict_query);
                        if (query == null)
                            return;

                        ((SherlockFragmentActivity)parent.getActivity()).setSupportProgressBarIndeterminateVisibility(cur==null && !query.getText().toString().equals(""));

                        final ListView results = (ListView) parent.getView().findViewById(R.id.dict_results);

                        if ((results == null) && (cur!=null)) {
                            cur.close();
                            return;
                        }
                        ((VocabularyCursorAdapter)results.getAdapter()).changeCursor(cur);
                    }
                }
            });
        }
    }

    private void start_new_query(int mode, String query) {
        if (update_thread != null) {
            update_thread.cancel();
        }

        update_thread = new update_delayer(query, mode, use_ref, this);
        update_thread.start();
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
}
