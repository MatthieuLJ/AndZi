package com.matthieu;

import android.util.Log;

import java.text.BreakIterator;
import java.util.ArrayList;

/**
 * User: matthieu
 * Date: 6/1/11
 * Time: 3:54 PM
 */
public class PinyinParser {
    private final static String TAG = "PinyinParser";

    public PinyinParser()
    {
    }

    static public ArrayList<Pinyin> parse_string(String pinyin_string)
    {
        int split_index;
        ArrayList<Pinyin> result = new ArrayList<Pinyin>();
        ArrayList<Pinyin> temp;
        int i=0;


        // Try to find strophe to split the string
        split_index = pinyin_string.indexOf("'");
        if (split_index != -1)
        {
            if (split_index == 0)
            {
                return parse_string(pinyin_string.substring(1, pinyin_string.length()));
            }
            if (split_index == pinyin_string.length())
            {
                return parse_string(pinyin_string.substring(0, pinyin_string.length() - 1));
            }
            temp = parse_string(pinyin_string.substring(0, split_index - 1));
            if (temp.size() == 0)
            {
                return result;
            }
            result = temp;

            temp = parse_string(pinyin_string.substring(split_index + 1, pinyin_string.length()));
            if (temp.size() == 0)
            {
                return result;
            }
            result.addAll(temp);

            return result;
        }

        // digits are tones at the end of pinyins... can split there too
        while ((split_index == -1) && (i<5)) {
            split_index = pinyin_string.indexOf(Integer.toString(i));
            i++;
        }
        if ((split_index != -1) && (split_index != pinyin_string.length()-1))
        {
            if (split_index == 0)
            {
                return parse_string(pinyin_string.substring(1, pinyin_string.length()));
            }

            temp = parse_string(pinyin_string.substring(0, split_index+1));
            if (temp.size() == 0)
            {
                return result;
            }
            result = temp;

            temp = parse_string(pinyin_string.substring(split_index + 1, pinyin_string.length()));
            if (temp.size() == 0)
            {
                return result;
            }
            result.addAll(temp);

            return result;
        }


        for (i = Math.min(6, pinyin_string.length()); i >= 0; i--) // the maximum size with tone is 6 characters...
        // start with the maximum size to avoid breaking down unnecessary (that would have a "'")
        {
            String try_pinyin = pinyin_string.substring(0, i);
            if (Pinyin.verify_pinyin(try_pinyin))
            {
                if (i < pinyin_string.length())
                {
                    String next_step=pinyin_string.substring(i, pinyin_string.length());
                    temp = parse_string(next_step);
                    if (temp.size() == 0)
                    {
                        continue;
                    }
                    result = temp;
                }

                result.add(0, new Pinyin(try_pinyin));
                return result;
            }
        }

        return result;
    }


    /**
     *
     * @param input String of pinyins with tones as numbers
     * @return String with accents correctly marked
     */
    /*
    public static String convert_to_printed_version(String input)
    {
        int i;
        String result = "";
        if (input == "")
            return result;
        ArrayList<Pinyin> parsed = parse_string(input);
        for (i = 0; i < parsed.size(); i++)
        {
            result += parsed.get(i).get_print_version();
        }
        return result;
    }
    */
}
