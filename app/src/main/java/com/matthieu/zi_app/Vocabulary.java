/******************************
 * Basic structure for Vocabulary with Chinese, Pinyin and translations
 */
package com.matthieu.zi_app;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Arrays;

public class Vocabulary implements Parcelable {
    public String chinese;
    public String pinyin;
    public ArrayList<String> english;

    public Vocabulary(String ch, String pi) {
        chinese = ch;
        pinyin = pi;
        english = new ArrayList<String>();
    }
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
    public Vocabulary(Parcel p) {
        chinese = p.readString();
        pinyin = p.readString();
        p.readStringList(english);
    }

    public boolean same_word(Vocabulary other) {
        return (other.chinese.equals(chinese)) && (other.pinyin.equals(pinyin));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(chinese);
        parcel.writeString(pinyin);
        parcel.writeStringList(english);
    }

    public static final Parcelable.Creator<Vocabulary> CREATOR
            = new Parcelable.Creator<Vocabulary>() {
        public Vocabulary createFromParcel(Parcel in) {
            return new Vocabulary(in);
        }

        public Vocabulary[] newArray(int size) {
            return new Vocabulary[size];
        }
    };
}
