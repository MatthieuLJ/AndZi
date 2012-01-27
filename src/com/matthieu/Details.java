/******************************
 * Details activity to show everything we know of a word or character
 * Also offers static function to populate a ViewGroup with those details
 */

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
import com.matthieu.widget.DBVocabularyResultsHandler;
import com.matthieu.widget.UpdatableListAdapter;
import com.matthieu.widget.UpdatableListView;

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

        if (current_adapter != null)
            current_adapter.cancel();
        FrameLayout frame = new FrameLayout(this);
        current_adapter=populate_details_view(this, frame, hanzi, use_ref);
        setContentView(frame);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (((keyCode == KeyEvent.KEYCODE_BACK)) && (!old_hanzi.equals(""))) {
            current_hanzi=old_hanzi;
            old_hanzi="";
            if (current_adapter != null)
                current_adapter.cancel();
            FrameLayout frame = new FrameLayout(this);
            populate_details_view(this, frame, current_hanzi, current_use_ref);
            setContentView(frame);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    public static class DetailsWordAdapter extends BaseAdapter implements UpdatableListAdapter {
        ArrayList<Vocabulary> voc_list = new ArrayList<Vocabulary>();
        boolean loading;
        AsyncTask<Void, ArrayList<Vocabulary>, Void> filler=null;
        boolean use_ref;
        boolean only_exact;
        Activity context;
        ListView list;
        boolean is_cancelled=false;
        ArrayList<HanziDB.StringElement> current_elements;

        public DetailsWordAdapter(final String hanzi, final boolean use_ref, final boolean only_exact, Activity context, ListView list) {
            ArrayList<HanziDB.StringElement> elements = new ArrayList<HanziDB.StringElement>();
            for (int i=0; i<hanzi.length(); ) {
                elements.add(new HanziDB.StringElement(HanziDB.StringElement.CHINESE,
                        hanzi.substring(i, i+Character.charCount(hanzi.codePointAt(i)))));
                i+=Character.charCount(hanzi.codePointAt(i));
            }
            this.use_ref=use_ref;
            this.context=context;
            this.list=list;
            this.only_exact=only_exact;
            current_elements=elements;
            start_it();
        }

        public DetailsWordAdapter(final String hanzi, final boolean use_ref, Activity context, ListView list) {
            this(hanzi,use_ref,false,context, list);
        }

        public DetailsWordAdapter(final ArrayList<HanziDB.StringElement> elements, final boolean use_ref, final boolean only_exact, Activity context, ListView list) {
            this.use_ref=use_ref;
            this.context=context;
            this.list=list;
            current_elements=elements;
            this.only_exact=only_exact;
            start_it();
        }

        public DetailsWordAdapter(final ArrayList<HanziDB.StringElement> elements, final boolean use_ref, Activity context, ListView list) {
            this(elements,use_ref,false,context, list);
        }

        private void finished(ArrayList<Vocabulary> voc) {
            loading=false;
            filler=null;
            voc_list= voc;
            DetailsWordAdapter.this.notifyDataSetChanged();
            if (voc.size()<=1) {
                list.setDividerHeight(0);
            }
            else {
                list.setDividerHeight(1);
            }

        }

        private void start_it() {
            final HanziDB myDB=((AndZiApplication)context.getApplication()).getMyDB();
            final HanziDB refDB=((AndZiApplication)context.getApplication()).getRefDB();

            loading=true;
            is_cancelled=false;

            final ArrayList<Vocabulary> voc=new ArrayList<Vocabulary>();
            if (current_elements==null) {
                finished(voc);
                return;
            }

            final DBVocabularyResultsHandler ref_handler = new DBVocabularyResultsHandler() {
                int step=0;

                @Override
                public void handle_more_results(ArrayList<Vocabulary> data) {
                    if (is_cancelled)
                        return;
                    Log.d(TAG, "Received "+data.size()+" items from ref");
                    HanziDB.merge_vocabulary(voc, data);
                    voc_list= (ArrayList<Vocabulary>) voc.clone();
                    DetailsWordAdapter.this.notifyDataSetChanged();
                }

                @Override
                public void handle_end_of_results() {
                    if (is_cancelled)
                        return;
                    switch(step) {
                        case 0:
                            if (only_exact) {
                                finished(voc);
                            }
                            else {
                                filler = refDB.getWordsWithSequence(current_elements, 1, this);
                            }
                            break;
                        case 1:
                            filler = refDB.getWordsWithSequence(current_elements, 0, this);
                            break;
                        case 2:
                            finished(voc);
                            break;
                    }
                    step++;
                }
            };

            final DBVocabularyResultsHandler my_handler = new DBVocabularyResultsHandler() {
                int step=0;

                @Override
                public void handle_more_results(ArrayList<Vocabulary> data) {
                    if (is_cancelled)
                        return;
                    Log.d(TAG, "Received "+data.size()+" items from my");
                    HanziDB.merge_vocabulary(voc, data);
                    voc_list= (ArrayList<Vocabulary>) voc.clone();
                    DetailsWordAdapter.this.notifyDataSetChanged();
                }

                @Override
                public void handle_end_of_results() {
                    if (is_cancelled)
                        return;
                    switch(step) {
                        case 0:
                            if (only_exact) {
                                if ((use_ref) && (refDB != null)) {
                                    Log.d(TAG, "Switching to REF");
                                    filler = refDB.getWordsWithSequence(current_elements, 2, ref_handler);
                                }
                                else {
                                    finished(voc);
                                }
                            }
                            else {
                                if (use_ref)
                                    filler = myDB.getWordsWithSequence(current_elements, 1, this);
                                else
                                    filler = myDB.getFilteredWordsWithSequence(current_elements, 1, this);
                            }
                            break;
                        case 1:
                            if (use_ref)
                                filler = myDB.getWordsWithSequence(current_elements, 0, this);
                            else
                                filler = myDB.getFilteredWordsWithSequence(current_elements, 0, this);
                            break;
                        case 2:
                            if ((use_ref) && (refDB != null)) {
                                Log.d(TAG, "Switching to REF");
                                filler = refDB.getWordsWithSequence(current_elements, 2, ref_handler);
                            }
                            else {
                                finished(voc);
                            }
                            break;
                    }
                    step++;
                }
            };

            if (use_ref)
                filler = myDB.getWordsWithSequence(current_elements, 2, my_handler);
            else
                filler = myDB.getFilteredWordsWithSequence(current_elements, 2, my_handler);
        }

        public void cancel() {
            if (filler != null) {
                Log.d(TAG, "Cancelling Details filler");
                filler.cancel(true);
                is_cancelled=true;
                loading=false;
                filler=null;
            }
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
                        DetailsWordAdapter.this.cancel();
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
        return populate_details_view(context,section,hanzi,include_ref,false);
    }

    static DetailsWordAdapter populate_details_view(final Activity context, ViewGroup section, final String hanzi, boolean include_ref, boolean only_exact) {
        HanziDB myDB = ((AndZiApplication) context.getApplication()).getMyDB();
        HanziDB refDB = ((AndZiApplication) context.getApplication()).getRefDB();
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        boolean show_pinyin_color = sp.getBoolean("color_pinyin", true);

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

                if (show_pinyin_color)
                    info_pinyins+=PinyinParser.convert_to_html_version(pin);
                else
                    info_pinyins+=PinyinParser.convert_to_pretty_version(pin);
                if (i < pinyins.size()-1)
                    info_pinyins+=", ";
            }
            info_pinyins+=")";
            if (show_pinyin_color)
                info.setText(Html.fromHtml(info_pinyins));
            else
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

        UpdatableListView list= (UpdatableListView) new_view.findViewById(R.id.details_list);
        DetailsWordAdapter res = new DetailsWordAdapter(hanzi, include_ref, only_exact, context, list);
        if (list != null)
            list.setAdapter(res);

        return res;
    }
}
