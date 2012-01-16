package com.matthieu;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import com.matthieu.widget.UpdatableListAdapter;

import java.util.ArrayList;

public class Dictionary extends Activity {
    int mode; // 0 for Chinese->English, 1 for Chinese<-English
    Handler updater=null;
    UpdatableListAdapter current_adapter=null;

    private static final String TAG="Dictionary";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.dictionary);
        mode=0;
        TextView dict_mode = (TextView) findViewById(R.id.dict_mode);
        dict_mode.setText(Html.fromHtml("<b>Chinese</b> → English"));
        dict_mode.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                switch (mode) {
                    case 0:
                        mode = 1;
                        ((TextView) view).setText(Html.fromHtml("Chinese ← <b>English</b>"));
                        break;
                    case 1:
                        mode = 0;
                        ((TextView) view).setText(Html.fromHtml("<b>Chinese</b> → English"));
                        break;
                }
                if (current_adapter != null)
                    current_adapter.cancel();
                create_new_adapter("", null);
            }
        });

        updater=new Handler();

        EditText query = (EditText) findViewById(R.id.dict_query);
        query.setText("");
        query.addTextChangedListener(new Dictionary_query_parse());
    }

    private void create_new_adapter(String orig, ArrayList<HanziDB.StringElement> elements) {
        if (current_adapter!= null)
            current_adapter.cancel();

        if (mode == 0) {
            current_adapter=new Details.DetailsWordAdapter(elements, true, Dictionary.this);
        }
        else {
            current_adapter=new EnglishWordAdapter(orig, true, Dictionary.this);
        }
        ListView results = (ListView) findViewById(R.id.dict_results);
        results.setAdapter(current_adapter);
    }

    private void update_list(final String orig, final ArrayList<HanziDB.StringElement> elements) {
        Runnable to_run = new Runnable() {
            public void run() {
                EditText query = (EditText) findViewById(R.id.dict_query);
                if (query == null)
                    return;
                if (!query.getText().toString().equals(orig))
                    return;

                Log.d(TAG, "Starting a new query "+orig);

                create_new_adapter(orig, elements);
            }
        };

        if ((current_adapter==null) || (current_adapter.is_finished())) {
            Log.d(TAG, "Starting right away");
            updater.post(to_run);
        }
        else {
            current_adapter.cancel();
            Log.d(TAG, "Starting a bit later");
            updater.postDelayed(to_run, 1000); // give some time to breath (or change the text further)
        }
    }

    private class Dictionary_query_parse implements TextWatcher {
        public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        }

        public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            if (mode == 0) {
                ArrayList<HanziDB.StringElement> elems = PinyinParser.parse_mixed_string(charSequence.toString());
                if (elems != null)
                    update_list(charSequence.toString(), elems);
            }
            else {
                update_list(charSequence.toString(), null);
            }
        }

        public void afterTextChanged(Editable editable) {
        }
    }

    public static class EnglishWordAdapter extends BaseAdapter implements UpdatableListAdapter {
        ArrayList<Vocabulary> voc_list = new ArrayList<Vocabulary>();
        boolean loading;
        AsyncTask<Void, Void, Void> filler=null;
        boolean use_ref=true;
        Activity context;
        boolean is_cancelled=false;
        String current_english="";

        public EnglishWordAdapter(final String english, final boolean use_ref, Activity context) {
            this.use_ref=use_ref;
            this.context=context;
            current_english=english;
            start_it();
        }

        private void start_it() {
            final HanziDB myDB=((AndZiApplication)context.getApplication()).getMyDB();
            final HanziDB refDB=((AndZiApplication)context.getApplication()).getRefDB();

            if (current_english.length()==0)
                return;

            loading=true;
            is_cancelled=false;

            filler = new AsyncTask<Void, Void, Void>() {
                ArrayList<Vocabulary> voc;

                @Override
                protected Void doInBackground(Void... voids) {
                    voc=new ArrayList<Vocabulary>();
                    publishProgress((Void[])null);

                    voc.addAll(myDB.getCTranslations(current_english,2));
                    if (isCancelled())
                        return null;

                    publishProgress((Void[])null);

                    voc.addAll(myDB.getCTranslations(current_english,1));
                    if (isCancelled())
                        return null;

                    publishProgress((Void[])null);

                    voc.addAll(myDB.getCTranslations(current_english,0));
                    if (isCancelled())
                        return null;

                    if ((use_ref) && (refDB != null)) {
                        publishProgress((Void[])null);

                        HanziDB.merge_vocabulary(voc, refDB.getCTranslations(current_english,2));
                        if (isCancelled())
                            return null;

                        publishProgress((Void[])null);

                        HanziDB.merge_vocabulary(voc, refDB.getCTranslations(current_english,1));
                        if (isCancelled())
                            return null;

                        publishProgress((Void[])null);

                        HanziDB.merge_vocabulary(voc, refDB.getCTranslations(current_english,0));
                        if (isCancelled())
                            return null;
                    }
                    return null;
                }

                @Override
                protected void onCancelled() {
                    super.onCancelled();
                    is_cancelled=true;
                }

                @Override
                protected void onProgressUpdate(Void... values) {
                    super.onProgressUpdate(values);
                    voc_list= (ArrayList<Vocabulary>) voc.clone();
                    EnglishWordAdapter.this.notifyDataSetChanged();
                }

                @Override
                protected void onPostExecute(Void aVoid) {
                    filler = null;
                    voc_list=voc;
                    loading=false;
                    EnglishWordAdapter.this.notifyDataSetChanged();
                }
            };


            filler.execute((Void []) null);
        }

        public void cancel() {
            Log.d(TAG, "Cancelling Dictionary filler");
            if (filler != null)
                new Thread( new Runnable() {
                    public void run() {
                        filler.cancel(true);
                    }
                }).run();
        }

        public void resume() {
            if (is_cancelled)
                start_it();
        }

        public boolean is_finished() {
            return (filler==null);
        }

        public int getCount() {
            return loading==true?voc_list.size()+1:voc_list.size();
        }

        public Object getItem(int i) {
            return i<voc_list.size()?voc_list.get(i):"";
        }

        public long getItemId(int i) {
            return i;
        }

        public View getView(int i, View convertView, ViewGroup viewGroup) {
            if (convertView == null) {
                LayoutInflater li = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = li.inflate(R.layout.details_list_item, viewGroup, false);
            }

            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
            boolean show_pinyin_color = sp.getBoolean("color_pinyin", true);

            if (i==voc_list.size()) {
                TextView details_chinese = (TextView) convertView.findViewById(R.id.details_chinese_word);
                details_chinese.setText("");
                TextView details_pinyin = (TextView) convertView.findViewById(R.id.details_pinyin_word);
                details_pinyin.setText("");
                TextView details_translations = (TextView) convertView.findViewById(R.id.details_translations);
                details_translations.setText("Loading...");
                convertView.setOnClickListener(null);
            }
            else {
                final Vocabulary voc=voc_list.get(i);
                TextView details_chinese = (TextView) convertView.findViewById(R.id.details_chinese_word);
                details_chinese.setText(voc.chinese);
                TextView details_pinyin = (TextView) convertView.findViewById(R.id.details_pinyin_word);
                if (show_pinyin_color)
                    details_pinyin.setText(Html.fromHtml(PinyinParser.convert_to_html_version(voc.pinyin)));
                else
                    details_pinyin.setText(PinyinParser.convert_to_pretty_version(voc.pinyin));
                TextView details_translations = (TextView) convertView.findViewById(R.id.details_translations);
                String trans="";
                for (int j=0; j<voc.english.size();j++) {
                    trans+=voc.english.get(j);
                    if (j<voc.english.size()-1)
                        trans+=", ";
                }
                details_translations.setText(trans);
                convertView.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View view) {
                        Intent intent=new Intent(context, Details.class);
                        intent.putExtra("hanzi", voc.chinese);
                        context.startActivity(intent);
                    }
                });
            }

            return convertView;
        }
    }
}
