package com.matthieu.widget;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.Html;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.matthieu.*;
import java.util.ArrayList;

public class PinyinButtons extends LinearLayout {
    ArrayList<PinyinButton> pinyin_choices = new ArrayList<PinyinButton>();
    private Handler process_handler = new Handler();
    protected HanziDB myDB=null;
    protected HanziDB refDB=null;


    String current_chinese_word;
    AddVocabularyFragment current_fragment=null;

    public PinyinButtons(Context context) {
        super(context);
        refDB = ((AndZiApplication) context.getApplicationContext()).getRefDB();
        myDB = ((AndZiApplication) context.getApplicationContext()).getMyDB();
    }

    public PinyinButtons(Context context, AttributeSet attrs) {
        super(context, attrs);
        refDB = ((AndZiApplication) context.getApplicationContext()).getRefDB();
        myDB = ((AndZiApplication) context.getApplicationContext()).getMyDB();
    }

    @SuppressLint({"NewApi"})
    public PinyinButtons(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        refDB = ((AndZiApplication) context.getApplicationContext()).getRefDB();
        myDB = ((AndZiApplication) context.getApplicationContext()).getMyDB();
    }

    public void set_containing_fragment(AddVocabularyFragment fr) {
        current_fragment = fr;
    }

    public void clearPinyinButtons() {
        pinyin_choices.clear();
        this.removeAllViews();
        current_chinese_word = "";
    }

    public void setPinyinButtons(String chinese_word) {
        pinyin_choices.clear();
        this.removeAllViews();
        current_chinese_word = chinese_word;
        for (int j=0; j<chinese_word.length(); j++) {
            Button b = new Button(getContext());
            b.setBackgroundColor(0x00000000);
            b.setTextColor(0xff000000);
            b.setBackgroundResource(0);
            PinyinButton tmp = new PinyinButton(chinese_word.substring(j, j+1), b);
            process_handler.post(new PinyinButtonFiller(tmp));
            pinyin_choices.add(j, tmp);
            this.addView(b, j,
                    new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        // populate the default values for the pinyin buttons
        process_handler.post(new UpdateDefaultPinyin());
    }

    public void setPinyinButtons(String charSequence, int i, int i1, int i2, String chinese_word) {
        current_chinese_word = chinese_word;

        if (i1 > 0) {
            // remove the ones that were changed
            for (int j=0; j<i1; j++) {
                pinyin_choices.remove(i);
                this.removeViewAt(i);
            }
        }
        if (i2 > 0) {
            for (int j=0; j<i2; j++) {
                Button b = new Button(getContext());
                b.setBackgroundColor(0x00000000);
                b.setTextColor(0xff000000);
                b.setBackgroundResource(0);
                PinyinButton tmp = new PinyinButton(charSequence.toString().substring(i+j, i+j+1), b);
                process_handler.post(new PinyinButtonFiller(tmp));
                pinyin_choices.add(i + j, tmp);
                this.addView(b, i + j,
                        new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
        }

        // populate the default values for the pinyin buttons
        process_handler.post(new UpdateDefaultPinyin());
    }

    private class PinyinButtonFiller implements Runnable {
        PinyinButton button;
        public PinyinButtonFiller(PinyinButton b) {
            button=b;
        }

        public void run() {
            String hanzi=button.get_hanzi();
            ArrayList<String> res=new ArrayList<String>();
            if (myDB != null)
                res.addAll(myDB.getPinyinForChar(hanzi));

            if (refDB != null) {
                for (String pin : refDB.getPinyinForChar(hanzi)) {
                    if (!res.contains(pin))
                        res.add(pin);
                }
            }
            button.set_db_pinyins(res);
        }
    }

    private class PinyinButton {
        private String hanzi;
        private ArrayList<String> db_pinyins;

        private String new_pinyin="";
        private String default_choice="";
        private String current_choice="";

        //private final static String TAG = "PinyinButton";
        private Button button;

        public PinyinButton(String h, Button b) {
            button = b;
            hanzi = h;
            db_pinyins=new ArrayList<String>();
        }

        public String get_pinyin(int index) {
            if (new_pinyin.equals("")) {
                if (index < db_pinyins.size())
                    return db_pinyins.get(index);
            }
            else {
                if (index == 0) return new_pinyin;
                if (index <= db_pinyins.size())
                    return db_pinyins.get(index-1);
            }
            return "";
        }

        public int get_number_pinyins() {
            int res = db_pinyins.size();
            if (new_pinyin.equals("")) {
                return res;
            } else {
                return res+1;
            }
        }

        public void set_default_choice(String s) {
            default_choice = s;
            update_button();
        }

        public void set_current_choice(String s) {
            //Log.d(TAG, "pinyin button set current choice"+s);
            current_choice = s;
            if (!db_pinyins.contains(s))
                new_pinyin=s;
            update_button();
        }

        public String get_current_choice() {
            String res;

            if (current_choice.equals("")) {
                if ((!default_choice.equals("")) && (db_pinyins.contains(default_choice))) {
                    res=default_choice;
                }
                else if (db_pinyins.size()!= 0) {
                    res = db_pinyins.get(0);
                }
                else {
                    res = "";
                }
            }
            else {
                res=current_choice;
            }
            return res;
        }

        public String get_hanzi() {
            return hanzi;
        }

        public void set_db_pinyins(ArrayList<String> pinyins) {
            db_pinyins=pinyins;
            update_button();
        }

        private void update_button() {
            String to_display=get_current_choice();
            Pinyin pinyin = new Pinyin(to_display);
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
            boolean show_color = sp.getBoolean("color_pinyin", true);

            if (to_display.equals("")) {
                button.setText("-?-");
            }
            else {
                if (show_color) {
                    button.setText(Html.fromHtml(pinyin.get_color()));
                }
                else {
                    button.setText(pinyin.get_pretty());
                }
            }
            button.invalidate();
            button.setOnClickListener(new PinyinButtonClickListener());

            if (current_fragment != null)
                current_fragment.update_vocabulary();
        }


        private class PinyinButtonClickListener implements View.OnClickListener {
            public void onClick(View view) {
                Dialog dialog = new Dialog(getContext());
                dialog.setTitle(getResources().getString(R.string.add_voc_pick_pinyin)+PinyinButton.this.get_hanzi());
                fill_dialog(dialog);
                dialog.show();
            }

            private void fill_dialog(final Dialog dial) {
                dial.setContentView(R.layout.add_vocabulary_pinyin_dialog);

                final ListView list = (ListView) dial.findViewById(R.id.pinyin_dialog_choices);
                String choices[] = new String[PinyinButton.this.get_number_pinyins()];
                for (int i=0; i<PinyinButton.this.get_number_pinyins(); i++) {
                    choices[i] = new Pinyin(PinyinButton.this.get_pinyin(i)).get_pretty();
                    //Log.d(TAG, "Add pinyin " + choices[i] +" to the list");
                }

                list.setAdapter(new ArrayAdapter<String>(dial.getContext(), R.layout.add_vocabulary_pinyin_dialog_item, choices));
                list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                        change_pinyin(new Pinyin(PinyinButton.this.get_pinyin(i)).get_lame());
                        dial.dismiss();
                    }
                });

                Button OK_button = (Button) dial.findViewById(R.id.edit_new_pinyin_ok);
                OK_button.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View view) {
                        EditText new_pinyin = (EditText) dial.findViewById(R.id.edit_new_pinyin);
                        change_pinyin(new Pinyin(new_pinyin.getText().toString()).get_lame());
                        dial.dismiss();
                    }
                });
            }

            private void change_pinyin(String new_pinyin) {
                PinyinButton.this.set_current_choice(new_pinyin);
            }
        }
    }

    private class UpdateDefaultPinyin implements Runnable {
        public void run() {
            int i;
            ArrayList<String> default_pinyins;

            // figure out the default pinyin buttons
            ArrayList<Vocabulary> vocs = myDB.getTranslations(current_chinese_word);
            if ((refDB != null) && (vocs.size() == 0)){
                vocs= refDB.getTranslations(current_chinese_word);
            }
            if (vocs.size() == 0)
                return;

            int max_transl=0, best_index=0;
            for (i=0; i<vocs.size(); i++) {
                if (vocs.get(i).english.size() > max_transl) {
                    best_index=i;
                    max_transl = vocs.get(i).english.size();
                }
            }
            //Log.d(TAG, "Setting default pinyins to "+vocs.get(best_index).pinyin);

            default_pinyins = PinyinParser.parse_string(vocs.get(best_index).pinyin);
            if (default_pinyins == null)
                return;

            for (i=0; i<default_pinyins.size(); i++) {
                if (i >= pinyin_choices.size()) break;
                pinyin_choices.get(i).set_default_choice(default_pinyins.get(i));
            }
        }
    }

    public String get_current_pinyin() {
        StringBuilder pinyin=new StringBuilder();
        for (PinyinButton pc : pinyin_choices) {
            if (pc.get_current_choice().equals(""))
                return "";
            pinyin = pinyin.append(pc.get_current_choice());
        }
        return pinyin.toString();
    }

}
