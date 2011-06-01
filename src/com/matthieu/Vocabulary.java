package com.matthieu;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * User: matthieu
 * Date: 5/31/11
 * Time: 2:01 PM
 */
public class Vocabulary {
    public String chinese;
    public String pinyin;
    public ArrayList<String> english;

    public Vocabulary(String ch, String pi, String en) {
        chinese = ch;
        pinyin = pi;
        english = new ArrayList<String>(Arrays.asList(en));
    }
    public Vocabulary(String ch, String pi, ArrayList<String> en) {
        chinese = ch;
        pinyin = pi;
        english = en;
    }
}
