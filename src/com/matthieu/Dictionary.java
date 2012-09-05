/******************************
 * Dictionary activity to either use Details for Chinese to English or have its own functions for English translations
 */

package com.matthieu;

import android.content.res.Configuration;
import android.database.Cursor;
import android.os.*;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.util.Log;
import android.view.*;
import android.widget.*;
import com.matthieu.widget.AndZiActivity;
import com.matthieu.widget.MessageCursor;
import com.matthieu.widget.VocabularyCursorAdapter;

import java.util.ArrayList;

public class Dictionary extends AndZiActivity {
    int mode; // 0 for Chinese->English, 1 for Chinese<-English
    Cursor current_cursor =null;
    Handler update_handler=null;
    update_delayer update_thread=null;
    HanziDB refDB, myDB, currentDB;
    private static final String TAG="Dictionary";

    static final int CHINESE_TO_ENGLISH_MODE=0;
    static final int ENGLISH_TO_CHINESE_MODE=1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        refDB = ((AndZiApplication) getApplication()).getRefDB();
        myDB = ((AndZiApplication) getApplication()).getMyDB();

        if (((AndZiApplication) getApplication()).getRefDBPresent())
            currentDB=refDB;
        else
            currentDB=myDB;

        if (update_thread == null) {
            update_thread = new update_delayer();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
                update_thread.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[])null);
            else
                update_thread.execute((Void[])null);
        }

        setContentView(R.layout.dictionary);
        setProgressBarIndeterminateVisibility(false);
        mode=CHINESE_TO_ENGLISH_MODE;

        final ListView results = (ListView) findViewById(R.id.dict_results);
        results.setAdapter(new VocabularyCursorAdapter(Dictionary.this, null));

        TextView dict_mode = (TextView) findViewById(R.id.dict_mode);
        dict_mode.setText(Html.fromHtml("<b>Chinese</b> → English"));
        dict_mode.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                switch (mode) {
                    case CHINESE_TO_ENGLISH_MODE:
                        mode = ENGLISH_TO_CHINESE_MODE;
                        ((TextView) view).setText(Html.fromHtml("Chinese ← <b>English</b>"));
                        break;
                    case ENGLISH_TO_CHINESE_MODE:
                        mode = CHINESE_TO_ENGLISH_MODE;
                        ((TextView) view).setText(Html.fromHtml("<b>Chinese</b> → English"));
                        break;
                }
                EditText edit_query= (EditText) findViewById(R.id.dict_query);
                start_new_query(mode, edit_query.getText().toString());
            }
        });

        EditText query = (EditText) findViewById(R.id.dict_query);
        query.setText("");
        query.addTextChangedListener(new Dictionary_query_parse());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (update_handler != null) {
            update_handler.getLooper().quit();
            update_thread = null;
            update_handler = null;
        }
        synchronized (Dictionary.this) {
            if (current_cursor != null)
                current_cursor.close();
        }
    }

    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        synchronized (Dictionary.this) {
            EditText edit_query= (EditText) findViewById(R.id.dict_query);
            String current_search = edit_query.getText().toString();
            ListView results = (ListView) findViewById(R.id.dict_results);
            VocabularyCursorAdapter current_adapter = (VocabularyCursorAdapter) results.getAdapter();

            setContentView(R.layout.dictionary);

            TextView dict_mode = (TextView) findViewById(R.id.dict_mode);
            dict_mode.setText(Html.fromHtml("<b>Chinese</b> → English"));
            dict_mode.setOnClickListener(new View.OnClickListener() {
                public void onClick(View view) {
                    switch (mode) {
                        case CHINESE_TO_ENGLISH_MODE:
                            mode = ENGLISH_TO_CHINESE_MODE;
                            ((TextView) view).setText(Html.fromHtml("Chinese ← <b>English</b>"));
                            break;
                        case ENGLISH_TO_CHINESE_MODE:
                            mode = CHINESE_TO_ENGLISH_MODE;
                            ((TextView) view).setText(Html.fromHtml("<b>Chinese</b> → English"));
                            break;
                    }
                    EditText edit_query= (EditText) findViewById(R.id.dict_query);
                    start_new_query(mode, edit_query.getText().toString());
                }
            });
            switch (mode) {
                case CHINESE_TO_ENGLISH_MODE:
                    dict_mode.setText(Html.fromHtml("<b>Chinese</b> → English"));
                    break;
                case ENGLISH_TO_CHINESE_MODE:
                    dict_mode.setText(Html.fromHtml("Chinese ← <b>English</b>"));
                    break;
            }

            EditText query = (EditText) findViewById(R.id.dict_query);
            query.setText(current_search);
            query.addTextChangedListener(new Dictionary_query_parse());

            results = (ListView) findViewById(R.id.dict_results);
            results.setAdapter(current_adapter);
        }
    }

    private class update_delayer extends AsyncTask<Void,Cursor, Void> {
        String msg_query;
        int msg_mode;

        @Override
        protected Void doInBackground(Void... voids) {
            Looper.prepare();

            update_handler = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    msg_query = (String) msg.obj;
                    msg_mode = msg.arg1;

                    update_list();
                }
            };

            Looper.loop();
            return null;
        }

        private void update_list() {
            EditText query = (EditText) findViewById(R.id.dict_query);
            if (query == null)
                return;

            if ((msg_mode != mode) || (!msg_query.equals(query.getText().toString())))
                return;

            Log.d(TAG, "Starting a new query for "+msg_query);

            publishProgress((Cursor[])null);

            if (msg_query.length()==0) {
                current_cursor=null;
            }
            else {
                if (msg_mode == CHINESE_TO_ENGLISH_MODE) {
                    ArrayList<HanziDB.StringElement> elems = PinyinParser.parse_mixed_string(msg_query);
                    boolean all_chinese=true;
                    if (elems != null)
                        for (HanziDB.StringElement e : elems) {
                            if (e.type != HanziDB.StringElement.CHINESE)
                                all_chinese=false;
                        }

                    if (elems == null)
                        current_cursor = new MessageCursor("Cannot understand this query");
                    else if (elems.size()==1) {
                        if (elems.get(0).type == HanziDB.StringElement.INCOMPLETE_PINYIN)
                            current_cursor = null;
                        else
                            current_cursor = currentDB.getWordsStartingWithSequence(elems, true, true);
                    }
                    else if ((all_chinese) && (elems.size() >= 3) && (currentDB.getTranslations(msg_query).size()==0))
                        current_cursor = currentDB.getDecomposedParts(elems, 4, true, true);
                    else
                        current_cursor = currentDB.getWordsWithSequence(elems, true, true);
                }
                else if (msg_mode == ENGLISH_TO_CHINESE_MODE) {
                    if (msg_query.length()<2)
                        current_cursor=null;
                    else if (msg_query.length()<3)
                        current_cursor = currentDB.getFilteredStartingCTranslations(msg_query);
                    else
                        current_cursor = currentDB.getFilteredCTranslations(msg_query);
                }
                else
                    current_cursor =null;
            }

            // Just to make sure the query actually happens in this thread (and not the UI thread)
            if ((msg_mode == mode) && (msg_query.equals(query.getText().toString())) && (current_cursor != null) && (!current_cursor.isClosed())) {
                current_cursor.getCount();
                current_cursor.moveToFirst();
            }

            if ((msg_mode == mode) && (msg_query.equals(query.getText().toString()))) {
                publishProgress(current_cursor);
            }

        }

        @Override
        protected void onProgressUpdate(Cursor... values) {
            synchronized (Dictionary.this) {
                setProgressBarIndeterminateVisibility(values==null);

                final ListView results = (ListView) findViewById(R.id.dict_results);

                if ((results == null) && (values!=null)) {
                    values[0].close();
                    return;
                }
                ((VocabularyCursorAdapter)results.getAdapter()).changeCursor(values==null?null:values[0]);
            }
        }
    }

    private void start_new_query(int mode, String query) {
        Message toSend = new Message();
        toSend.obj = query;
        toSend.arg1 = mode;
        if (update_handler!=null)
            update_handler.sendMessage(toSend);
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (((AndZiApplication)getApplication()).getRefDBPresent()) {
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.dictionary_menu, menu);

            return true;
        }
        else {
            return false;
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem add_word = menu.findItem(R.id.dictionary_switch_dic);
        if (currentDB == myDB) {
            add_word.setTitle("Use the dictionary");
        }
        else {
            add_word.setTitle("Use my vocabulary list");
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.dictionary_switch_dic:
                EditText query = (EditText) findViewById(R.id.dict_query);
                if (query == null)
                    return false;
                currentDB= (currentDB == refDB) ? myDB : refDB;
                start_new_query(mode, query.getText().toString());
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }
}
