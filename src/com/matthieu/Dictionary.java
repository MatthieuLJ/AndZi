/******************************
 * Dictionary activity to either use Details for Chinese to English or have its own functions for English translations
 */

package com.matthieu;

import android.app.Activity;
import android.database.Cursor;
import android.os.*;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.*;
import com.matthieu.widget.MessageCursor;
import com.matthieu.widget.VocabularyCursorAdapter;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class Dictionary extends Activity {
    int mode; // 0 for Chinese->English, 1 for Chinese<-English
    Cursor current_cursor =null;
    Handler update_handler=null;
    private static final String TAG="Dictionary";

    static final int CHINESE_TO_ENGLISH_MODE=0;
    static final int ENGLISH_TO_CHINESE_MODE=1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        new update_delayer().start();

        setContentView(R.layout.dictionary);
        mode=0;

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

//        Debug.startMethodTracing("dictionary");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        update_handler.getLooper().quit();
        if (current_cursor != null)
            current_cursor.close();
    }

    private class update_delayer extends Thread {
        private Timer next_update=null;
        private int last_mode=-1;
        private String last_query;
        private MessageCursor loading_cursor = new MessageCursor("Loading...");
        @Override
        public void run() {
            Looper.prepare();
            setPriority(MIN_PRIORITY);

            update_handler = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    if (next_update != null)
                        next_update.cancel();
                    next_update = new Timer();
                    final String query = (String) msg.obj;
                    final int mode = msg.arg1;

                    last_mode=mode;
                    last_query=query;

                    TimerTask next_update_task = new TimerTask() {
                        @Override
                        public void run() {
                            next_update=null;
                            update_list(mode, query);
                        }
                    };
                    next_update.schedule(next_update_task, 500);
                }
            };

            Looper.loop();
        }

        private void update_list(final int mode, final String orig) {
            EditText query = (EditText) findViewById(R.id.dict_query);
            if (query == null)
                return;
            if (!query.getText().toString().equals(orig))
                return;

            Log.d(TAG, "Starting a new query for "+orig);
            final ListView results = (ListView) findViewById(R.id.dict_results);
            runOnUiThread(new Runnable() {
                public void run() {
                    Cursor old_cursor = ((VocabularyCursorAdapter)results.getAdapter()).getCursor();
                    ((VocabularyCursorAdapter)results.getAdapter()).changeCursor(loading_cursor);
                    if (old_cursor != null)
                        old_cursor.close();
                }
            });

            if (orig.length()==0) {
                current_cursor=null;
            }
            else {
                HanziDB refDB = ((AndZiApplication) getApplication()).getRefDB();
                if (mode == 0) {
                    ArrayList<HanziDB.StringElement> elems = PinyinParser.parse_mixed_string(orig);
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
                            current_cursor = refDB.getWordsStartingWithSequence(elems, true, true);
                    }
                    else if ((all_chinese) && (elems.size() >= 3) && (refDB.getTranslations(orig).size()==0))
                        current_cursor = refDB.getDecomposedParts(elems, 4, true, true);
                    else
                        current_cursor = refDB.getWordsWithSequence(elems, true, true);
                }
                else if (mode == 1) {
                    if (orig.length()<2)
                        current_cursor=null;
                    else if (orig.length()<3)
                        current_cursor = refDB.getFilteredStartingCTranslations(orig);
                    else
                        current_cursor = refDB.getFilteredCTranslations(orig);
                }
                else
                    current_cursor =null;
            }

            // Just to make sure the query actually happens in this thread (and not the UI thread)
            if ((mode == last_mode) && (orig.equals(last_query)) && (current_cursor != null)) {
                Log.d(TAG, "Start query! "+System.currentTimeMillis());
                current_cursor.getCount();
                Log.d(TAG, "Finish query! "+System.currentTimeMillis());
            }

            if ((mode == last_mode) && (orig.equals(last_query)))
                runOnUiThread(new Runnable() {
                    public void run() {
                        Cursor old_cursor = ((VocabularyCursorAdapter)results.getAdapter()).getCursor();
                        ((VocabularyCursorAdapter)results.getAdapter()).changeCursor(current_cursor);
                        if (old_cursor != null)
                            old_cursor.close();
                    }
                });
        }
    }

    private void start_new_query(int mode, String query) {
        Message toSend = new Message();
        toSend.obj = query;
        toSend.arg1 = mode;
        if (update_handler!=null)
            update_handler.dispatchMessage(toSend);
    }

    private class Dictionary_query_parse implements TextWatcher {
        public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        }

        public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
//            if (charSequence.toString().equals(" "))
//                Debug.stopMethodTracing();
            start_new_query(mode, charSequence.toString());
        }

        public void afterTextChanged(Editable editable) {
        }
    }
}
