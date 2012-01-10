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
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.util.ArrayList;


public class DetailsCharacter extends Activity {
    protected HanziDB myDB=null;
    protected HanziDB refDB=null;

    private static final String TAG="DetailsCharacter";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        myDB = ((AndZiApplication) getApplication()).getMyDB();
        refDB = ((AndZiApplication) getApplication()).getRefDB();

        setContentView(R.layout.details);
        String hanzi=getIntent().getStringExtra("character");
        if (hanzi.equals(""))
            finish();

        String pinyin=getIntent().getStringExtra("pinyin");
        boolean use_ref = getIntent().getBooleanExtra("use_ref", true);

        TextView title = (TextView) findViewById(R.id.details_title);
        title.setText(hanzi);
        TextView info = (TextView) findViewById(R.id.details_info);
        ArrayList<String> pinyins = refDB.getPinyinForChar(hanzi);
        String info_pinyins="(";
        for (int i=0; i<pinyins.size(); i++) {
            String pin=pinyins.get(i);
            info_pinyins+=PinyinParser.convert_to_pretty_version(pin);
            if (i < pinyins.size()-1)
                info_pinyins+=", ";
        }
        info_pinyins+=")";
        info.setText(info_pinyins);
        
        ((ListView)findViewById(R.id.details_list)).setAdapter(new DetailsCharacterAdapter(hanzi, pinyin, use_ref, (AndZiApplication) getApplication()));
    }

    public static class DetailsCharacterAdapter extends BaseAdapter {
        ArrayList<Vocabulary> voc_list;
        int loading;
        AndZiApplication application;

        public DetailsCharacterAdapter(final String hanzi, final String pinyin, final boolean use_ref, AndZiApplication app) {
            final HanziDB myDB=app.getMyDB();
            final HanziDB refDB=app.getRefDB();

            application=app;
            voc_list = new ArrayList<Vocabulary>();

            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(app);
            final boolean local_db_first = sp.getBoolean("local_db_first", true);
            loading=1;

            new AsyncTask<Void, Void, Void>() {
                ArrayList<Vocabulary> voc;

                @Override
                protected Void doInBackground(Void... voids) {

                    if ((pinyin==null) || (pinyin.equals(""))) {
                        voc=sort_vocabulary_list(myDB.getWordsWithPinyin(hanzi), hanzi);
                    }
                    else {
                        voc=sort_vocabulary_list(myDB.getWordsWithPinyin(hanzi, pinyin), hanzi);
                    }

                    publishProgress();

                    if ((use_ref) && (refDB != null)) {
                        if ((pinyin==null) || (pinyin.equals(""))) {
                            voc = HanziDB.merge_vocabulary(voc, sort_vocabulary_list(refDB.getWordsWithPinyin(hanzi), hanzi));
                        }
                        else {
                            voc = HanziDB.merge_vocabulary(voc, sort_vocabulary_list(refDB.getWordsWithPinyin(hanzi, pinyin), hanzi));
                        }
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
                    DetailsCharacterAdapter.this.notifyDataSetChanged();
                }

                @Override
                protected void onPostExecute(Void aVoid) {
                    voc_list=voc;
                    loading=0;
                    DetailsCharacterAdapter.this.notifyDataSetChanged();
                }
            }.execute();
        }

        private ArrayList<Vocabulary> sort_vocabulary_list(ArrayList<Vocabulary> to_sort, String hanzi) {
            int got_one_sized=0;

            for (int index=0; index<to_sort.size();) {
                if ((index > 0) && (to_sort.get(index).chinese.codePointCount(0, to_sort.get(index).chinese.length())==1)) {
                    Vocabulary tmp = to_sort.remove(index);
                    to_sort.add(0, tmp);
                    got_one_sized++;
                    continue;
                }
                if ((index > got_one_sized) && (to_sort.get(index).chinese.substring(0, Character.charCount(to_sort.get(index).chinese.codePointAt(0))).equals(hanzi))) {
                    Vocabulary tmp = to_sort.remove(index);
                    to_sort.add(got_one_sized, tmp);
                    continue;
                }

                index++;
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
            LayoutInflater li = (LayoutInflater)application.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            if (convertView == null) {
                convertView = li.inflate(R.layout.details_list_item, viewGroup, false);
            }

            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(application);
            boolean show_pinyin_color = sp.getBoolean("color_pinyin", true);

            if (i==voc_list.size()) {
                TextView details_chinese = (TextView) convertView.findViewById(R.id.details_chinese_word);
                details_chinese.setText("");
                TextView details_pinyin = (TextView) convertView.findViewById(R.id.details_pinyin_word);
                details_pinyin.setText("");
                TextView details_translations = (TextView) convertView.findViewById(R.id.details_translations);
                details_translations.setText("Loading...");
            }
            else {
                Vocabulary voc=voc_list.get(i);
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
            }

            return convertView;
        }
    }

    static public void populate_character_details_view(Activity context, ViewGroup section, String hanzi, boolean include_ref) {
        populate_character_details_view(context, section, hanzi, null, include_ref);
    }

    static public void populate_character_details_view(final Activity context, ViewGroup section, final String hanzi, final ArrayList<String> pinyin, boolean include_ref) {
        // first write the character at the top
        TextView character = new TextView(context);
        character.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        final String character_label = hanzi;
        character.setTextAppearance(context, android.R.style.TextAppearance_Large_Inverse);
        character.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40);
        character.setGravity(Gravity.CENTER_HORIZONTAL);
        character.setText(character_label);
        character.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                Intent intent=new Intent(context, DetailsCharacter.class);
                intent.putExtra("character", hanzi);
                context.startActivity(intent);
            }
        });
        section.addView(character);

        ArrayList<String> new_pinyin;
        
        if (pinyin == null) {
            HanziDB myDB = ((AndZiApplication) context.getApplication()).getMyDB();
            HanziDB refDB = ((AndZiApplication) context.getApplication()).getRefDB();

            // get all possible pinyins
            new_pinyin=myDB.getPinyinForChar(hanzi);
            if ((include_ref) && (refDB != null)) {
                for (String p:refDB.getPinyinForChar(hanzi))
                    if (!new_pinyin.contains(p))
                        new_pinyin.add(p);
            }
        }
        else {
            new_pinyin=pinyin;
        }

        for (String pin:new_pinyin)
            add_one_character_pinyin(context, section, hanzi, pin, include_ref);

    }

    static void add_one_character_pinyin(Activity context, ViewGroup section, String hanzi, String pinyin, boolean include_ref) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        boolean show_pinyin_color = sp.getBoolean("color_pinyin", true);
        HanziDB myDB = ((AndZiApplication) context.getApplication()).getMyDB();
        HanziDB refDB = ((AndZiApplication) context.getApplication()).getRefDB();

        TextView tv_pinyin = new TextView(context);
        if (show_pinyin_color)
            tv_pinyin.setText(Html.fromHtml(PinyinParser.convert_to_html_version(pinyin)));
        else
            tv_pinyin.setText(PinyinParser.convert_to_pretty_version(pinyin));
        section.addView(tv_pinyin, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ArrayList<Vocabulary> trans = myDB.getWordsWithPinyin(hanzi, pinyin);
        if ((include_ref) && (refDB != null))
            trans = HanziDB.merge_vocabulary(trans, refDB.getWordsWithPinyin(hanzi, pinyin));

        for (Vocabulary tran : trans) {
            String str_trans = "";
            TextView tv_translations = new TextView(context);
            str_trans += tran.chinese + " (" + PinyinParser.convert_to_html_version(tran.pinyin) + "): ";
            for (int j = 0; j < tran.english.size(); j++) {
                str_trans += tran.english.get(j);
                if (j < tran.english.size() - 1)
                    str_trans += ", ";
            }
            tv_translations.setText(Html.fromHtml(str_trans));
            tv_translations.setTextAppearance(context, android.R.attr.textAppearanceMedium);
            tv_translations.setTextColor(0xff000000);
            tv_translations.setTag(tran.chinese);
            tv_translations.setOnClickListener(new View.OnClickListener() {
                public void onClick(View view) {

                }
            });
            section.addView(tv_translations, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }
}
