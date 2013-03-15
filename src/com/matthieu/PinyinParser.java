/******************************
 * Class to parse a string into a list of Pinyins
 */
package com.matthieu;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Locale;
import java.util.WeakHashMap;

public class PinyinParser {
    private static final boolean DEBUG = false;
    private final static String TAG = "PinyinParser";
    private final static String pinyin_chars = "[bpmfdtnlgkhzcsrjqxaoeiuvwyāōēīūǖáóéíúǘǎǒěǐǔǚàòèìùǜ\'0-5]*";

    static public ArrayList<String> parse_string(String pinyin_string)
    {
        int split_index;
        ArrayList<String> result;
        ArrayList<String> temp;

        // Try to find strophe to split the string
        split_index = pinyin_string.indexOf("'");
        if (split_index != -1)
        {
            if (split_index == 0)
            {
                return null;
            }
            if (split_index == pinyin_string.length())
            {
                return parse_string(pinyin_string.substring(0, pinyin_string.length() - 1));
            }
            temp = parse_string(pinyin_string.substring(0, split_index - 1));
            if (temp == null)
            {
                return null;
            }
            result = temp;

            temp = parse_string(pinyin_string.substring(split_index + 1, pinyin_string.length()));
            if (temp == null)
            {
                return null;
            }
            result.addAll(temp);

            return result;
        }

        // digits are tones at the end of pinyins... can split there too
        int i=0;
        while (i<6) {
            int tmp_split_index = pinyin_string.indexOf(Integer.toString(i));
            if ((tmp_split_index != -1) && ((split_index == -1) || (tmp_split_index < split_index)))
                split_index = tmp_split_index;
            i++;
        }
        if ((split_index != -1) && (split_index != pinyin_string.length()-1))
        {
            if (split_index == 0)
            {
                return parse_string(pinyin_string.substring(1, pinyin_string.length()));
            }

            temp = parse_string(pinyin_string.substring(0, split_index+1));
            if (temp == null)
            {
                return null;
            }
            result = temp;

            temp = parse_string(pinyin_string.substring(split_index + 1, pinyin_string.length()));
            if (temp == null)
            {
                return null;
            }
            result.addAll(temp);

            return result;
        }

        for (i = Math.min(7, pinyin_string.length()); i >= 0; i--) // the maximum size with tone is 7 characters with the tone...
        // start with the maximum size to avoid breaking down unnecessary (that would have a "'")
        {
            Pinyin try_pinyin = new Pinyin(pinyin_string.substring(0, i));
            if (!try_pinyin.get_lame().equals(""))
            {
                if (i < pinyin_string.length())
                {
                    String next_step=pinyin_string.substring(i, pinyin_string.length());
                    temp = parse_string(next_step);
                    if (temp == null)
                    {
                        continue;
                    }
                    result = temp;
                }
                else {
                    result = new ArrayList<String>();
                }

                result.add(0, try_pinyin.get_lame());
                return result;
            }
        }

        return null;
    }

    static public ArrayList<Pinyin> parse_string_to_pinyin(String pinyin_string)
    {
        int split_index;
        ArrayList<Pinyin> result;
        ArrayList<Pinyin> temp;
        int i=0;

        // Try to find strophe to split the string
        split_index = pinyin_string.indexOf("'");
        if (split_index != -1)
        {
            if (split_index == 0)
            {
                return null;
            }
            if (split_index == pinyin_string.length())
            {
                return parse_string_to_pinyin(pinyin_string.substring(0, pinyin_string.length() - 1));
            }
            temp = parse_string_to_pinyin(pinyin_string.substring(0, split_index - 1));
            if (temp == null)
            {
                return null;
            }
            result = temp;

            temp = parse_string_to_pinyin(pinyin_string.substring(split_index + 1, pinyin_string.length()));
            if (temp == null)
            {
                return null;
            }
            result.addAll(temp);

            return result;
        }

        // digits are tones at the end of pinyins... can split there too
        while (i<6) {
            int tmp_split_index = pinyin_string.indexOf(Integer.toString(i));
            if ((tmp_split_index != -1) && ((split_index == -1) || (tmp_split_index < split_index)))
                split_index = tmp_split_index;
            i++;
        }
        if ((split_index != -1) && (split_index != pinyin_string.length()-1))
        {
            if (split_index == 0)
            {
                return null;
            }

            temp = parse_string_to_pinyin(pinyin_string.substring(0, split_index + 1));
            if (temp == null)
            {
                return null;
            }
            result = temp;

            temp = parse_string_to_pinyin(pinyin_string.substring(split_index + 1, pinyin_string.length()));
            if (temp == null)
            {
                return null;
            }
            result.addAll(temp);

            return result;
        }

        for (i = Math.min(7, pinyin_string.length()); i >= 0; i--) // the maximum size with tone is 7 characters with the tone...
        // start with the maximum size to avoid breaking down unnecessary (that would have a "'")
        {
            String try_pinyin = pinyin_string.substring(0, i);
            if (Pinyin.verify_pinyin(try_pinyin))
            {
                if (i < pinyin_string.length())
                {
                    String next_step=pinyin_string.substring(i, pinyin_string.length());
                    temp = parse_string_to_pinyin(next_step);
                    if (temp == null)
                    {
                        continue;
                    }
                    result = temp;
                }
                else {
                    result = new ArrayList<Pinyin>();
                }

                result.add(0, new Pinyin(try_pinyin));
                return result;
            }
        }

        return null;
    }

    static WeakHashMap<String, ArrayList<HanziDB.StringElement>> cache = new WeakHashMap<String, ArrayList<HanziDB.StringElement>>();
    static private void add_to_cache(String entry, ArrayList<HanziDB.StringElement> result) {
        synchronized(cache) {
            if (!cache.containsKey(entry)) {
                ArrayList<HanziDB.StringElement> to_save = new ArrayList<HanziDB.StringElement>(result);
                cache.put(entry, to_save);
                if (DEBUG) Log.d(TAG, "Save to cache for ["+entry+"] with "+result.size()+" items");
            }
        }
    }
    static private ArrayList<HanziDB.StringElement> query_cache(String entry) {
        synchronized(cache) {
            if (cache.containsKey(entry)) {
                if (DEBUG) Log.d(TAG, "Found entry for "+entry+" in cache");
                return cache.get(entry);
            }
            else
                return null;
        }
    }

    static public ArrayList<HanziDB.StringElement> parse_string_to_elements(String pinyin_string) {
        int split_index;
        ArrayList<HanziDB.StringElement> result;
        ArrayList<HanziDB.StringElement> temp;
        int i=0;

        result = query_cache(pinyin_string);
        if (result != null)
            return result;

        if (!pinyin_string.matches(pinyin_chars))
            return null;

        // Try to find strophe to split the string
        split_index = pinyin_string.indexOf("'");
        if (split_index != -1)
        {
            if (split_index == 0)
            {
                return null;
            }
            if (split_index == pinyin_string.length())
            {
                return parse_string_to_elements(pinyin_string.substring(0, pinyin_string.length() - 1));
            }
            temp = parse_string_to_elements(pinyin_string.substring(0, split_index));
            if ((temp == null) || (temp.get(temp.size()-1).type==HanziDB.StringElement.type_t.INCOMPLETE_PINYIN))
            {
                return null;
            }
            result = temp;

            temp = parse_string_to_elements(pinyin_string.substring(split_index+1, pinyin_string.length()));
            if (temp == null)
            {
                return null;
            }
            result.addAll(temp);

            return result;
        }

        // digits are tones at the end of pinyins... can split there too
        while (i<6) {
            int tmp_split_index = pinyin_string.indexOf(Integer.toString(i));
            if ((tmp_split_index != -1) && ((split_index == -1) || (tmp_split_index < split_index)))
                split_index = tmp_split_index;
            i++;
        }
        if ((split_index != -1) && (split_index != pinyin_string.length()-1))
        {
            if (split_index == 0)
            {
                return null;
            }

            temp = parse_string_to_elements(pinyin_string.substring(0, split_index + 1));
            if (temp == null)
            {
                return null;
            }
            result = temp;

            temp = parse_string_to_elements(pinyin_string.substring(split_index + 1, pinyin_string.length()));
            if (temp == null)
            {
                return null;
            }
            result.addAll(temp);

            add_to_cache(pinyin_string, result);
            return result;
        }

        for (i = Math.min(7, pinyin_string.length()); i > 0; i--) // the maximum size with tone is 7 characters with the tone...
        // start with the maximum size to avoid breaking down unnecessary (that would have a "'")
        {
            Pinyin try_pinyin = new Pinyin(pinyin_string.substring(0, i));
            if (!try_pinyin.get_lame().equals(""))
            {
                if (i < pinyin_string.length())
                {
                    String next_step=pinyin_string.substring(i, pinyin_string.length());
                    temp = parse_string_to_elements(next_step);
                    if (temp == null)
                    {
                        continue;
                    }
                    result = temp;
                }
                else {
                    result = new ArrayList<HanziDB.StringElement>();
                }

                if (try_pinyin.tone_is_given()) {
                    result.add(0, new HanziDB.StringElement(HanziDB.StringElement.type_t.PINYIN_WITH_TONE, try_pinyin.get_lame()));
                }
                else {
                    result.add(0, new HanziDB.StringElement(HanziDB.StringElement.type_t.PINYIN_WITHOUT_TONE, try_pinyin.get_radical()));
                }
                add_to_cache(pinyin_string, result);
                return result;
            }
        }
        if ((i == 0) && (pinyin_string.length()<7)) {
            Pinyin incomplete_pinyin=new Pinyin(pinyin_string,true);
            if (!incomplete_pinyin.get_lame().equals("")) {
                result = new ArrayList<HanziDB.StringElement>();
                result.add(new HanziDB.StringElement(HanziDB.StringElement.type_t.INCOMPLETE_PINYIN, incomplete_pinyin.get_lame()));

                add_to_cache(pinyin_string, result);
                return result;
            }
        }

        return null;
    }

    @Nullable
    static public ArrayList<HanziDB.StringElement> parse_mixed_string(String input) {
        ArrayList<HanziDB.StringElement> res = query_cache(input);
        if (res == null)
            res = new ArrayList<HanziDB.StringElement>();
        else
            return res;

        for (int i=0; i<input.length(); ) {
            int entry=input.codePointAt(i);
            if (((entry >= 0x4E00) && (entry <= 0x9fff)) // main CJK
                    || ((entry >= 0x2e00) && (entry <= 0x31ff)) // additional punctuation
                    || ((entry >= 0x3400) && (entry <= 0x4dbf)) // extension A
                    ) {
                res.add(new HanziDB.StringElement(HanziDB.StringElement.type_t.CHINESE, input.substring(i, i+Character.charCount(entry))));
                i+=Character.charCount(entry);
            }
            else {
                if (!input.substring(i, i+1).toLowerCase(Locale.ENGLISH).matches(pinyin_chars)) {
                    res.clear();
                    return res;
                }
                int j=i+1;
                while ((j<=input.length()) && (input.substring(i, j).toLowerCase(Locale.ENGLISH).matches(pinyin_chars))) {
                    j++;
                }
                String pinyins=input.substring(i, j-1);
                ArrayList<HanziDB.StringElement> more = parse_string_to_elements(pinyins);
                if (more == null) {
                    res = null;
                    break;
                }
                res.addAll(more);
                i=j-1;
            }
        }
        if (res == null)
            if (DEBUG) Log.d(TAG, "Failed to parse string ["+input+"]");
        else {
                if (DEBUG) Log.d(TAG, "Parsed string ["+input+"], got "+res.size()+" items");
            add_to_cache(input, res);
        }

        return res;
    }

    public static String convert_to_pretty_version(String input) {
        int i;
        StringBuilder result = new StringBuilder();
        if (input.equals(""))
            return result.toString();
        ArrayList<Pinyin> parsed = parse_string_to_pinyin(input);
        for (i = 0; i < parsed.size(); i++) {
            result.append(parsed.get(i).get_pretty());
        }
        return result.toString();
    }

    public static String convert_to_html_version(String input) {
        int i;
        StringBuilder result = new StringBuilder();
        if (input.equals(""))
            return result.toString();
        ArrayList<Pinyin> parsed = parse_string_to_pinyin(input);
        if (parsed == null)
            return input;
        for (i = 0; i < parsed.size(); i++) {
            result.append(parsed.get(i).get_color());
        }
        return result.toString();
    }

    public static SpannableStringBuilder convert_to_spannable_version(String input, Context context) {
        int i;
        if (input.equals(""))
            return new SpannableStringBuilder("");

        ArrayList<Pinyin> parsed = parse_string_to_pinyin(input);
        if (parsed == null)
            return new SpannableStringBuilder("");

        StringBuilder result_builder = new StringBuilder();
        for (i = 0; i < parsed.size(); i++) {
            result_builder.append(parsed.get(i).get_pretty());
        }
        SpannableStringBuilder result = new SpannableStringBuilder(result_builder.toString());
        int offset=0;
        for (i = 0; i < parsed.size(); i++) {
            int size=parsed.get(i).get_radical().length();
            ForegroundColorSpan color;
            switch (parsed.get(i).get_tone()) {
                case 1:
                    color = new ForegroundColorSpan(context.getResources().getColor(R.color.first_tone));
                    break;
                case 2:
                    color = new ForegroundColorSpan(context.getResources().getColor(R.color.second_tone));
                    break;
                case 3:
                    color = new ForegroundColorSpan(context.getResources().getColor(R.color.third_tone));
                    break;
                case 4:
                    color = new ForegroundColorSpan(context.getResources().getColor(R.color.fourth_tone));
                    break;
                case 0:
                case 5:
                default:
                    offset += size;
                    continue;
            }

            result.setSpan(color, offset, offset+size, 0);
            offset += size;
        }
        return result;
    }

    public static String convert_to_lame_version(String input)
    {
        if (input.equals(""))
            return "";
        StringBuilder result = new StringBuilder();
        ArrayList<Pinyin> parsed = parse_string_to_pinyin(input);
        if (parsed == null)
            return input;
        for (Pinyin aParsed : parsed) {
            result.append(aParsed.get_lame());
        }
        return result.toString();
    }

    public static String convert_chinese_to_html_version(String input, ArrayList<Pinyin> pinyins) {
        String res="";
        int index=0;
        for (int i=0; i<input.length();) {
            String hanzi = input.substring(i, i+Character.charCount(input.codePointAt(i)));
            if (pinyins.size() < index+1)
                return "";
            switch(pinyins.get(index).get_tone()) {
                case 1:
                    res+="<font color=\"red\">"+hanzi+"</font>";
                    break;
                case 2:
                    res+="<font color=\"#ff8000\">"+hanzi+"</font>";
                    break;
                case 3:
                    res+="<font color=\"green\">"+hanzi+"</font>";
                    break;
                case 4:
                    res+="<font color=\"blue\">"+hanzi+"</font>";
                    break;
                case 5:
                    res+=hanzi;
                    break;
            }
            i+=Character.charCount(input.codePointAt(i));
        }
        return res;
    }
}
