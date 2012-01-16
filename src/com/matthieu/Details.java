package com.matthieu;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Html;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.matthieu.widget.UpdatableListAdapter;
import java.util.ArrayList;

public class Details extends Activity {
    protected HanziDB myDB=null;
    protected HanziDB refDB=null;

    private static final String TAG="Details";

    private String current_hanzi;
    private boolean current_use_ref;
    private DetailsWordAdapter current_adapter=null;
    private String old_hanzi="";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        myDB = ((AndZiApplication) getApplication()).getMyDB();
        refDB = ((AndZiApplication) getApplication()).getRefDB();

        String hanzi=getIntent().getStringExtra("hanzi");
        if ((hanzi==null) || (hanzi.equals("")))
            finish();

        boolean use_ref = getIntent().getBooleanExtra("use_ref", true);

        current_hanzi = hanzi;
        current_use_ref = use_ref;

        FrameLayout frame = new FrameLayout(this);
        current_adapter=populate_details_view(this, frame, hanzi, use_ref);
        setContentView(frame);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (current_adapter != null)
            current_adapter.cancel();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (current_adapter != null)
            current_adapter.resume();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String hanzi=intent.getStringExtra("hanzi");
        if ((hanzi==null) || (hanzi.equals("")))
            finish();

        boolean use_ref = intent.getBooleanExtra("use_ref", true);

        if ((current_use_ref==use_ref) && (current_hanzi.equals(hanzi)))
            return;

        if (!current_hanzi.equals(hanzi))
            old_hanzi=current_hanzi;

        current_hanzi=hanzi;

        Log.d(TAG, "Starting new intent for " + hanzi);

        FrameLayout frame = new FrameLayout(this);
        current_adapter=populate_details_view(this, frame, hanzi, use_ref);
        setContentView(frame);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (((keyCode == KeyEvent.KEYCODE_BACK)) && (!old_hanzi.equals(""))) {
            current_hanzi=old_hanzi;
            old_hanzi="";
            FrameLayout frame = new FrameLayout(this);
            populate_details_view(this, frame, current_hanzi, current_use_ref, current_adapter);
            setContentView(frame);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    public static class DetailsWordAdapter extends BaseAdapter implements UpdatableListAdapter {
        ArrayList<Vocabulary> voc_list = new ArrayList<Vocabulary>();
        boolean loading;
        AsyncTask<Void, Void, Void> filler=null;
        boolean use_ref=true;
        Activity context;
        boolean is_cancelled=false;
        ArrayList<HanziDB.StringElement> current_elements;

        public DetailsWordAdapter(final String hanzi, final boolean use_ref, Activity context) {
            ArrayList<HanziDB.StringElement> elements = new ArrayList<HanziDB.StringElement>();
            for (int i=0; i<hanzi.length(); ) {
                elements.add(new HanziDB.StringElement(HanziDB.StringElement.CHINESE,
                        hanzi.substring(i, i+Character.charCount(hanzi.codePointAt(i)))));
                i+=Character.charCount(hanzi.codePointAt(i));
            }
            this.use_ref=use_ref;
            this.context=context;
            current_elements=elements;
            start_it();
        }

        public DetailsWordAdapter(final ArrayList<HanziDB.StringElement> elements, final boolean use_ref, Activity context) {
            this.use_ref=use_ref;
            this.context=context;
            current_elements=elements;
            start_it();
        }

        public void update_content(ArrayList<HanziDB.StringElement> elements) {
            cancel();
            current_elements=elements;
            start_it();
        }

        public void update_content(String element) {
            ArrayList<HanziDB.StringElement> elements = new ArrayList<HanziDB.StringElement>();
            for (int i=0; i<element.length(); ) {
                elements.add(new HanziDB.StringElement(HanziDB.StringElement.CHINESE,
                        element.substring(i, i+Character.charCount(element.codePointAt(i)))));
                i+=Character.charCount(element.codePointAt(i));
            }
            cancel();
            current_elements=elements;
            start_it();
        }

        private void start_it() {
            final HanziDB myDB=((AndZiApplication)context.getApplication()).getMyDB();
            final HanziDB refDB=((AndZiApplication)context.getApplication()).getRefDB();

            if (current_elements==null)
                return;

            loading=true;
            is_cancelled=false;

            filler = new AsyncTask<Void, Void, Void>() {
                ArrayList<Vocabulary> voc;

                @Override
                protected Void doInBackground(Void... voids) {
                    voc=new ArrayList<Vocabulary>();
                    publishProgress((Void[])null);
                    
                    if (use_ref)
                        voc=myDB.getWordsWithSequence(current_elements,2);
                    else
                        voc=myDB.getFilteredWordsWithSequence(current_elements,2);
                    if (isCancelled()) {
                        Log.d(TAG, "Details filling is cancelled (1)");
                        return null;
                    }

                    publishProgress((Void[])null);

                    if (use_ref)
                        voc.addAll(myDB.getWordsWithSequence(current_elements,1));
                    else
                        voc.addAll(myDB.getFilteredWordsWithSequence(current_elements,1));
                    if (isCancelled()) {
                        Log.d(TAG, "Details filling is cancelled (2)");
                        return null;
                    }

                    publishProgress((Void[])null);

                    if (use_ref)
                        voc.addAll(myDB.getWordsWithSequence(current_elements,0));
                    else
                        voc.addAll(myDB.getFilteredWordsWithSequence(current_elements,0));
                    if (isCancelled()) {
                        Log.d(TAG, "Details filling is cancelled (3)");
                        return null;
                    }

                    if ((use_ref) && (refDB != null)) {
                        publishProgress((Void[])null);

                        voc = HanziDB.merge_vocabulary(voc, refDB.getWordsWithSequence(current_elements,2));
                        if (isCancelled()) {
                            Log.d(TAG, "Details filling is cancelled (4)");
                            return null;
                        }

                        publishProgress((Void[])null);

                        voc = HanziDB.merge_vocabulary(voc, refDB.getWordsWithSequence(current_elements,1));
                        if (isCancelled()) {
                            Log.d(TAG, "Details filling is cancelled  (5)");
                            return null;
                        }

                        publishProgress((Void[])null);

                        voc = HanziDB.merge_vocabulary(voc, refDB.getWordsWithSequence(current_elements,0));
                        if (isCancelled()) {
                            Log.d(TAG, "Details filling is cancelled (6)");
                            return null;
                        }
                    }
                    return null;
                }

                @Override
                protected void onCancelled() {
                    super.onCancelled();
                    Log.d(TAG, "Details filling is being cancelled");
                    is_cancelled=true;
                }

                @Override
                protected void onProgressUpdate(Void... values) {
                    super.onProgressUpdate(values);
                    voc_list= (ArrayList<Vocabulary>) voc.clone();
                    DetailsWordAdapter.this.notifyDataSetChanged();
                }

                @Override
                protected void onPostExecute(Void aVoid) {
                    filler = null;
                    voc_list=voc;
                    loading=false;
                    DetailsWordAdapter.this.notifyDataSetChanged();
                }
            };

            filler.execute((Void []) null);
        }

        public void cancel() {
            Log.d(TAG, "Cancelling Details filler");
            if (filler != null) {
                new Thread( new Runnable() {
                    public void run() {
                        filler.cancel(true);
                    }
                }).run();
                is_cancelled=true;
            }
            Log.d(TAG, "Continuing");
        }

        public void resume() {
            if (is_cancelled)
                start_it();
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

    static DetailsWordAdapter populate_details_view(final Activity context, ViewGroup section, final String hanzi, boolean include_ref) {
        HanziDB myDB = ((AndZiApplication) context.getApplication()).getMyDB();
        HanziDB refDB = ((AndZiApplication) context.getApplication()).getRefDB();

        View new_view = View.inflate(context, R.layout.details, section);

        if (hanzi.codePointCount(0, hanzi.length()) == 1) {
            TextView info = (TextView) new_view.findViewById(R.id.details_info);
            ArrayList<String> pinyins = myDB.getPinyinForChar(hanzi);
            if ((include_ref) && (refDB != null)) {
                for (String pin:refDB.getPinyinForChar(hanzi)) {
                    if (!pinyins.contains(pin))
                        pinyins.add(pin);
                }
            }
            String info_pinyins="(";
            for (int i=0; i<pinyins.size(); i++) {
                String pin=pinyins.get(i);
                info_pinyins+=PinyinParser.convert_to_pretty_version(pin);
                if (i < pinyins.size()-1)
                    info_pinyins+=", ";
            }
            info_pinyins+=")";
            info.setText(info_pinyins);
        }

        // first write the word at the top
        LinearLayout top_word = (LinearLayout) new_view.findViewById(R.id.details_title);
        for (int i=0; i<hanzi.length(); ) {
            TextView character = new TextView(context);
            final String character_label = hanzi.substring(i, i + Character.charCount(hanzi.codePointAt(i)));
            character.setTextAppearance(context, android.R.style.TextAppearance_Large_Inverse);
            character.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40);
            character.setText(character_label);
            character.setOnClickListener(new View.OnClickListener() {
                public void onClick(View view) {
                    Intent intent=new Intent(context, Details.class);
                    intent.putExtra("hanzi", character_label);
                    context.startActivity(intent);
                }
            });
            top_word.addView(character);
            i+=Character.charCount(hanzi.codePointAt(i));
        }

        DetailsWordAdapter res = new DetailsWordAdapter(hanzi, include_ref, context);
        ListView list= (ListView) new_view.findViewById(R.id.details_list);
        if (list != null)
            list.setAdapter(res);

        return res;
    }

    static DetailsWordAdapter populate_details_view(final Activity context, ViewGroup section, final String hanzi, boolean include_ref, DetailsWordAdapter adapter) {
        HanziDB myDB = ((AndZiApplication) context.getApplication()).getMyDB();
        HanziDB refDB = ((AndZiApplication) context.getApplication()).getRefDB();

        View new_view = View.inflate(context, R.layout.details, section);

        if (hanzi.codePointCount(0, hanzi.length()) == 1) {
            TextView info = (TextView) new_view.findViewById(R.id.details_info);
            ArrayList<String> pinyins = myDB.getPinyinForChar(hanzi);
            if ((include_ref) && (refDB != null)) {
                for (String pin:refDB.getPinyinForChar(hanzi)) {
                    if (!pinyins.contains(pin))
                        pinyins.add(pin);
                }
            }
            String info_pinyins="(";
            for (int i=0; i<pinyins.size(); i++) {
                String pin=pinyins.get(i);
                info_pinyins+=PinyinParser.convert_to_pretty_version(pin);
                if (i < pinyins.size()-1)
                    info_pinyins+=", ";
            }
            info_pinyins+=")";
            info.setText(info_pinyins);
        }

        // first write the word at the top
        LinearLayout top_word = (LinearLayout) new_view.findViewById(R.id.details_title);
        for (int i=0; i<hanzi.length(); ) {
            TextView character = new TextView(context);
            final String character_label = hanzi.substring(i, i + Character.charCount(hanzi.codePointAt(i)));
            character.setTextAppearance(context, android.R.style.TextAppearance_Large_Inverse);
            character.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40);
            character.setText(character_label);
            character.setOnClickListener(new View.OnClickListener() {
                public void onClick(View view) {
                    Intent intent=new Intent(context, Details.class);
                    intent.putExtra("hanzi", character_label);
                    context.startActivity(intent);
                }
            });
            top_word.addView(character);
            i+=Character.charCount(hanzi.codePointAt(i));
        }

        ListView list= (ListView) new_view.findViewById(R.id.details_list);
        if (list != null) {
            adapter.update_content(hanzi);
            list.setAdapter(adapter);
        }

        return adapter;
    }
}
