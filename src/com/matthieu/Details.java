package com.matthieu;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Html;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import java.util.ArrayList;

public class Details extends Activity {
    protected HanziDB myDB=null;
    protected HanziDB refDB=null;

    private static final String TAG="Details";

    private String current_hanzi;
    private boolean current_use_ref;

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
        populate_details_view(this, frame, hanzi, use_ref);
        setContentView(frame);
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

        FrameLayout frame = new FrameLayout(this);
        populate_details_view(this, frame, hanzi, use_ref);
        setContentView(frame);
    }

    private static class DetailsWordAdapter extends BaseAdapter {
        ArrayList<Vocabulary> voc_list;
        String seed;
        int loading;
        Activity act;

        public DetailsWordAdapter(final String hanzi, final boolean use_ref, Activity context) {
            final HanziDB myDB=((AndZiApplication)context.getApplication()).getMyDB();
            final HanziDB refDB=((AndZiApplication)context.getApplication()).getRefDB();

            act=context;
            voc_list = new ArrayList<Vocabulary>();
            seed=hanzi;

            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
            final boolean local_db_first = sp.getBoolean("local_db_first", true);
            loading=1;

            //noinspection unchecked
            new AsyncTask<Void, Void, Void>() {
                ArrayList<Vocabulary> voc;

                @Override
                protected Void doInBackground(Void... voids) {
                    ArrayList<HanziDB.StringElement> elements = new ArrayList<HanziDB.StringElement>();
                    for (int i=0; i<hanzi.length(); ) {
                        elements.add(new HanziDB.StringElement(HanziDB.StringElement.CHINESE,
                                hanzi.substring(i, i+Character.charCount(hanzi.codePointAt(i)))));
                        i+=Character.charCount(hanzi.codePointAt(i));
                    }

                    voc=sort_vocabulary_list(myDB.getWordsWithSequence(elements), hanzi);

                    publishProgress();

                    if ((use_ref) && (refDB != null)) {
                        voc = HanziDB.merge_vocabulary(voc, sort_vocabulary_list(refDB.getWordsWithSequence(elements), hanzi));
                    }

                    publishProgress();

                    if (! local_db_first) {
                        voc=sort_vocabulary_list(voc, hanzi);
                    }
                    return null;
                }

                @Override
                protected void onProgressUpdate(Void... values) {
                    super.onProgressUpdate(values);
                    voc_list= (ArrayList<Vocabulary>) voc.clone();
                    DetailsWordAdapter.this.notifyDataSetChanged();
                }

                @Override
                protected void onPostExecute(Void aVoid) {
                    voc_list=voc;
                    loading=0;
                    DetailsWordAdapter.this.notifyDataSetChanged();
                }
            }.execute();
        }

        private ArrayList<Vocabulary> sort_vocabulary_list(ArrayList<Vocabulary> to_sort, String hanzi) {
            int got_match=0;

            for (int index=0; index<to_sort.size();index++) {
                if ((index > 0) && (to_sort.get(index).chinese.length()==hanzi.length())) {
                    Vocabulary tmp = to_sort.remove(index);
                    to_sort.add(0, tmp);
                    got_match++;
                    continue;
                }
                if ((index > got_match) && (to_sort.get(index).chinese.startsWith(hanzi))) {
                    Vocabulary tmp = to_sort.remove(index);
                    to_sort.add(got_match, tmp);
                    continue;
                }
            }
            return to_sort;
        }

        public int getCount() {
            return loading==1?voc_list.size()+1:voc_list.size();
        }

        public Object getItem(int i) {
            return i<voc_list.size()?voc_list.get(i):"";
        }

        public long getItemId(int i) {
            return i;
        }

        public View getView(int i, View convertView, ViewGroup viewGroup) {
            if (convertView == null) {
                LayoutInflater li = (LayoutInflater)act.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = li.inflate(R.layout.details_list_item, viewGroup, false);
            }

            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(act);
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
                details_chinese.setText(Html.fromHtml(voc.chinese.replace(seed, "<b>"+seed+"</b>")));
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
                        Intent intent=new Intent(act, Details.class);
                        intent.putExtra("hanzi", voc.chinese);
                        act.startActivity(intent);
                    }
                });
            }

            return convertView;
        }
    }

    static void populate_details_view(final Activity context, ViewGroup section, final String hanzi, boolean include_ref) {
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
        list.setAdapter(new DetailsWordAdapter(hanzi, include_ref, context));

        //section.addView(new_view);
    }
}
