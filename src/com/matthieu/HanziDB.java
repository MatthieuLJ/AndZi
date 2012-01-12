package com.matthieu;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.database.sqlite.*;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.lang.reflect.Array;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class HanziDB extends SQLiteOpenHelper {
    private static final String TAG = "HanziDB";
    private static final int DATABASE_VERSION = 1;

    protected Context appContext;
    protected AndZiApplication app;
    private SQLiteDatabase db;

    public HanziDB(Context context, AndZiApplication application, String db_name) {
        super(context, db_name, null, DATABASE_VERSION);
        appContext = context;
        app = application;
        db = this.getWritableDatabase();
        db.execSQL("PRAGMA synchronous=OFF");
        db.acquireReference();
    }

    ////////////////////////////////////////
    // Database construction and structure
    ////////////////////////////////////////

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.v(TAG, "onCreate HanziDB...");

        // basics
        db.execSQL("DROP TABLE IF EXISTS eword");
        db.execSQL("DROP TABLE IF EXISTS cword_segment");
        db.execSQL("DROP TABLE IF EXISTS cword");
        db.execSQL("DROP TABLE IF EXISTS char_pinyin");

        // lesson stuff
        db.execSQL("DROP TABLE IF EXISTS lesson_word");
        db.execSQL("DROP TABLE IF EXISTS lessons");

        // leitner stuff
        db.execSQL("DROP TABLE IF EXISTS leitner");

        // basics
        db.execSQL("CREATE TABLE char_pinyin (" +
                "char_pinyin_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                "hanzi VARCHAR(2) NOT NULL," +
                "pinyin VARCHAR(10) NOT NULL," +
                "UNIQUE(hanzi, pinyin))");
        db.execSQL("CREATE TABLE cword (" +
                "cword_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                "chinese VARCHAR(50) NOT NULL," +
                "pinyin VARCHAR(150) NOT NULL," +
                "UNIQUE(chinese,pinyin))");
        db.execSQL("CREATE TABLE cword_segment (" +
                "cword_id INTEGER NOT NULL," +
                "char_pinyin_id INTEGER NOT NULL," +
                "pos INTEGER NOT NULL," +
                "FOREIGN KEY (cword_id) REFERENCES cword(cword_id)," +
                "FOREIGN KEY (char_pinyin_id) REFERENCES char_pinyin(char_pinyin_id)," +
                "UNIQUE(cword_id, pos))");
        db.execSQL("CREATE TABLE eword (" +
                "eng_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                "cword_id INTEGER NOT NULL," +
                "translation VARCHAR(250) NOT NULL," +
                "FOREIGN KEY (cword_id) REFERENCES cword(cword_id)," +
                "UNIQUE(cword_id, translation))");

        // lesson stuff
        db.execSQL("CREATE TABLE lessons (" +
                "lesson_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                "list_order INTEGER DEFAULT 0," +
                "name VARCHAR(64) NOT NULL UNIQUE)");
        db.execSQL("CREATE TABLE lesson_word (" +
                "eng_id INTEGER NOT NULL," +
                "lesson_id INTEGER NOT NULL," +
                "FOREIGN KEY (eng_id) REFERENCES eword(eng_id)," +
                "FOREIGN KEY (lesson_id) REFERENCES lessons(lesson_id)," +
                "UNIQUE(eng_id,lesson_id))");

        // leitner stuff
        db.execSQL("CREATE TABLE leitner (" +
                        "hanzi VARCHAR(2) PRIMARY KEY NOT NULL," +
                        "box INTEGER NOT NULL," +
                        "mod_time INTEGER NOT NULL)");

    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int i, int i1) {
        Log.v(TAG, "onUpgrade ("+i+","+i1+")");
    }

    public boolean checkDatabase() {
        return checkDatabase(false);
    }

    public boolean checkDatabase(boolean fixme) {
        boolean res = true;

        Log.v(TAG, "checkDatabase");

        try {

            //
            // Basics
            //

            // check chinese and pinyin fields in cword
            Cursor c= db.rawQuery("SELECT cword_id, chinese, pinyin, GROUP_CONCAT(t1_chinese,'') AS t_chinese, GROUP_CONCAT(t1_pinyin,'') AS t_pinyin FROM " +
                    "(SELECT cw.cword_id AS cword_id,  cw.chinese AS chinese, cw.pinyin AS pinyin, " +
                    "cp.hanzi AS t1_chinese, cp.pinyin AS t1_pinyin "+
                    "FROM cword_segment AS cs "+
                    "JOIN cword AS cw ON cw.cword_id=cs.cword_id "+
                    "JOIN char_pinyin AS cp ON cp.char_pinyin_id=cs.char_pinyin_id "+
                    "ORDER BY cs.pos ASC) "+
                    "GROUP BY cword_id", null);
            if (c.moveToFirst()) {
                do{
                    int cword_id = c.getInt(c.getColumnIndex("cword_id"));
                    String pinyin = c.getString(c.getColumnIndex("pinyin"));
                    String t_pinyin = c.getString(c.getColumnIndex("t_pinyin"));
                    if (!pinyin.equals(t_pinyin)) {
                        res = false;
                        Log.w(TAG, "Wrong pinyin formwation with cword_id " + cword_id+ " : " + pinyin + " != " + t_pinyin);
                        if (fixme) {
                            db.delete("cword", "cword_id=?", new String[]{Integer.toString(cword_id)});
                        }
                    }
                    String chinese = c.getString(c.getColumnIndex("chinese"));
                    String t_chinese = c.getString(c.getColumnIndex("t_chinese"));
                    if (!chinese.equals(t_chinese)) {
                        res = false;
                        Log.w(TAG, "Wrong pinyin formwation with cword_id " + cword_id + " : " + chinese + " != " + t_chinese);
                        if (fixme) {
                            db.delete("cword", "cword_id=?", new String[]{Integer.toString(cword_id)});
                        }
                    }
                }while(c.moveToNext());
            }
            c.close();

            // check every cword has at least one translation
            c = db.rawQuery("SELECT cword_id FROM cword WHERE cword_id NOT IN (SELECT cword_id FROM eword)", null);
            if (c.moveToFirst()) {
                do{
                    res = false;
                    Log.w(TAG, "cword_id "+c.getInt(c.getColumnIndex("cword_id"))+", does not have translation");
                    if (fixme) {
                        int id=c.getInt(c.getColumnIndex("cword_id"));
                        db.delete("cword", "cword_id=?", new String[]{Integer.toString(id)});
                    }
                }while(c.moveToNext());
            }
            c.close();

            // check foreign key from cword_segment to cword_id
            c = db.rawQuery("SELECT DISTINCT cword_id FROM cword_segment WHERE cword_id NOT IN (SELECT cword_id FROM cword)", null);
            if (c.moveToFirst()) {
                Log.w(TAG, "Foreign key cword_id not respected from cword_segment");
                res = false;
                do{
                    if (fixme) {
                        int id=c.getInt(c.getColumnIndex("cword_id"));
                        db.delete("cword_segment", "cword_id=?", new String[]{Integer.toString(id)});
                    }
                    Log.w(TAG, "bad cword_id " + c.getString(c.getColumnIndex("cword_id")));
                }while(c.moveToNext());
            }
            c.close();

            // check foreign key from cword_segment to char_pinyin_id
            c = db.rawQuery("SELECT DISTINCT char_pinyin_id FROM cword_segment WHERE char_pinyin_id NOT IN (SELECT char_pinyin_id FROM char_pinyin)", null);
            if (c.moveToFirst()) {
                res = false;
                Log.w(TAG, "Foreign key char_pinyin_id not respected from cword_segment");
                do{
                    if (fixme) {
                        int id=c.getInt(c.getColumnIndex("char_pinyin_id"));
                        db.delete("cword_segment", "char_pinyin_id=?", new String[]{Integer.toString(id)});
                    }
                    Log.w(TAG, "bad char_pinyin_id " + c.getString(c.getColumnIndex("char_pinyin_id")));
                }while(c.moveToNext());
            }
            c.close();

            // check all char_pinyins are used
            c=db.rawQuery("SELECT char_pinyin_id FROM char_pinyin WHERE char_pinyin_id NOT IN (SELECT DISTINCT char_pinyin_id FROM cword_segment)", null);
            if (c.moveToFirst()) {
                res = false;
                do{
                    Log.w(TAG, "char_pinyin_id " + c.getInt(c.getColumnIndex("char_pinyin_id")) + "not used in cword_segment");
                    if (fixme) {
                        int id=c.getInt(c.getColumnIndex("char_pinyin_id"));
                        db.delete("char_pinyin", "char_pinyin_id=?", new String[]{Integer.toString(id)});
                    }
                }while(c.moveToNext());
            }
            c.close();

            // check foreign key from eword to cword_id
            c = db.rawQuery("SELECT DISTINCT cword_id FROM eword WHERE cword_id NOT IN (SELECT cword_id FROM cword)", null);
            if (c.moveToFirst()) {
                res = false;
                Log.w(TAG, "Foreign key cword_id not respected from eword");
                do{
                    Log.w(TAG, "bad cword_id " + c.getString(c.getColumnIndex("cword_id")));
                    if (fixme) {
                        int id=c.getInt(c.getColumnIndex("cword_id"));
                        db.delete("eword", "cword_id=?", new String[]{Integer.toString(id)});
                    }
                }while(c.moveToNext());
            }
            c.close();

            // check for unique char/pinyin pairs in char_pinyin
            c = db.rawQuery("SELECT COUNT(char_pinyin_id) AS count, hanzi, pinyin FROM char_pinyin GROUP BY hanzi, pinyin " +
                    "ORDER BY count DESC", null);
            if (c.moveToFirst()) {
                do{
                    if (c.getInt(c.getColumnIndex("count")) != 1) {
                        res = false;
                        Log.w(TAG, "hanzi, pinyin not unique in char_pinyin table");

                        if (fixme) {
                            // pick one id
                            String pinyin = c.getString(c.getColumnIndex("pinyin"));
                            String hanzi = c.getString(c.getColumnIndex("hanzi"));
                            c = db.rawQuery("SELECT char_pinyin_id FROM char_pinyin WHERE hanzi=? AND pinyin=?",
                                    new String[] {hanzi, pinyin});
                            c.moveToFirst();
                            int id=c.getInt(c.getColumnIndex("char_pinyin_id"));

                            // replace in the other tables
                            ContentValues cv = new ContentValues();
                            cv.put("char_pinyin_id", id);
                            db.update("cword_segment", cv, "char_pinyin_id IN (SELECT char_pinyin_id FROM char_pinyin WHERE char_pinyin_id!=? AND hanzi=? AND pinyin=?)",
                                    new String[]{Integer.toString(id), hanzi, pinyin});

                            // remove the others
                            db.delete("char_pinyin", "char_pinyin_id!=? AND hanzi=? AND pinyin=?", new String[]{Integer.toString(id), hanzi, pinyin});
                        }
                    }
                }while(c.moveToNext());
            }
            c.close();

            // check for unique chinese/pinyin in cword
            c = db.rawQuery("SELECT COUNT(cword_id) AS count, chinese, pinyin FROM cword GROUP BY chinese, pinyin " +
                    "ORDER BY count DESC", null);
            if (c.moveToFirst()) {
                do{
                    if (c.getInt(c.getColumnIndex("count")) != 1) {
                        res = false;
                        Log.w(TAG, "chinese, pinyin not unique in cword table");

                        if (fixme) {
                            // pick one id
                            String pinyin = c.getString(c.getColumnIndex("pinyin"));
                            String chinese = c.getString(c.getColumnIndex("chinese"));
                            c = db.rawQuery("SELECT cword_id FROM cword WHERE chinese=? AND pinyin=?",
                                    new String[] {chinese, pinyin});
                            c.moveToFirst();
                            int id=c.getInt(c.getColumnIndex("cword_id"));

                            // replace in the other tables
                            ContentValues cv = new ContentValues();
                            cv.put("cword_id", id);
                            db.update("cword_segment", cv, "cword_id IN (SELECT cword_id FROM cword WHERE cword_id!=? AND chinese=? AND pinyin=?)",
                                    new String[]{Integer.toString(id), chinese, pinyin});
                            db.update("eword", cv, "cword_id IN (SELECT cword_id FROM cword WHERE cword_id!=? AND chinese=? AND pinyin=?)",
                                    new String[]{Integer.toString(id), chinese, pinyin});

                            // remove the others
                            db.delete("cword", "cword_id!=? AND chinese=? AND pinyin=?", new String[]{Integer.toString(id), chinese, pinyin});
                        }
                    }
                }while(c.moveToNext());
            }
            c.close();

            // check for unique cword/translation in eword
            c = db.rawQuery("SELECT COUNT(cword_id) AS count, cword_id, translation FROM eword GROUP BY cword_id, translation " +
                    "ORDER BY count DESC", null);
            if (c.moveToFirst()) {
                do{
                    if (c.getInt(c.getColumnIndex("count")) != 1) {
                        res = false;
                        Log.w(TAG, "cword_id, translation not unique in eword table");

                        if (fixme) {
                            // pick one id
                            String translation = c.getString(c.getColumnIndex("translation"));
                            int cword_id = c.getInt(c.getColumnIndex("cword_id"));
                            c = db.rawQuery("SELECT eng_id FROM eword WHERE cword_id=? AND translation=?",
                                    new String[] {Integer.toString(cword_id), translation});
                            c.moveToFirst();
                            int id=c.getInt(c.getColumnIndex("eng_id"));

                            // replace in the other tables
                            ContentValues cv = new ContentValues();
                            cv.put("eng_id", id);
                            db.update("lesson_word", cv, "eng_id IN (SELECT eng_id FROM eword WHERE eng_id!=? AND cword_id=? AND translation=?)",
                                    new String[]{Integer.toString(id), Integer.toString(cword_id), translation});

                            // remove the others
                            db.delete("eword", "eng_id!=? AND cword_id=? AND translation=?",
                                    new String[]{Integer.toString(id), Integer.toString(cword_id), translation});
                        }
                    }
                }while(c.moveToNext());
            }
            c.close();

            //
            // Lessons
            //

            // check foreign key from lesson_word to lessons
            c = db.rawQuery("SELECT DISTINCT lesson_id FROM lesson_word WHERE lesson_id NOT IN (SELECT lesson_id FROM lessons)", null);
            if (c.moveToFirst()) {
                Log.w(TAG, "Foreign key lesson_id not respected from lesson_word");
                res = false;
                do{
                    Log.w(TAG, "bad lesson_id " + c.getString(c.getColumnIndex("lesson_id")));
                    if (fixme) {
                        int id=c.getInt(c.getColumnIndex("lesson_id"));
                        db.delete("lesson_word", "lesson_id=?", new String[]{Integer.toString(id)});
                    }
                }while(c.moveToNext());
            }
            c.close();

            // check foreign key from lesson_word to eword
            c = db.rawQuery("SELECT DISTINCT eng_id FROM lesson_word WHERE eng_id NOT IN (SELECT eng_id FROM eword)", null);
            if (c.moveToFirst()) {
                Log.w(TAG, "Foreign key eng_id not respected from lesson_word");
                res = false;
                do{
                    Log.w(TAG, "bad eng_id " + c.getString(c.getColumnIndex("eng_id")));
                    if (fixme) {
                        int id=c.getInt(c.getColumnIndex("eng_id"));
                        db.delete("lesson_word", "eng_id=?", new String[]{Integer.toString(id)});
                    }
                }while(c.moveToNext());
            }
            c.close();

            //
            // Leitner
            //

            // check all characters in leitner table are in DB
            c = db.rawQuery("SELECT DISTINCT hanzi FROM leitner WHERE hanzi NOT IN (SELECT hanzi FROM char_pinyin)", null);
            if (c.moveToFirst()) {
                Log.w(TAG, "Character in leitner table is not in database");
                res = false;
                do{
                    Log.w(TAG, "bad character " + c.getString(c.getColumnIndex("hanzi")));
                    if (fixme) {
                        String hanzi=c.getString(c.getColumnIndex("hanzi"));
                        db.delete("leitner", "hanzi=?", new String[]{hanzi});
                    }
                }while(c.moveToNext());
            }
            c.close();

        }
        catch (SQLiteException exp) {
            Log.d(TAG, "Ran into a problem while opening database");
            Log.d(TAG, exp.toString());
            res=false;
        }

        return res;
    }

    /*
     *
     *  Some utility functions
     *
     *
     */
    public static String bare_translation(String input) {
        Pattern pattern = Pattern.compile("(.*)\\(.*\\)(.*)");
        Matcher matcher = pattern.matcher(input);
        String res=input;

        while(matcher.matches()) {
            res = matcher.group(1) + " " + matcher.group(2);
            matcher = pattern.matcher(res);
        }
        return res.toLowerCase().trim();
    }

    public static boolean compare_translations(String ref, String guess) {
        return ref.equals(guess) ||
                ref.equals(HanziDB.bare_translation(guess)) ||
                ((!HanziDB.bare_translation(ref).equals("")) &&
                        (HanziDB.bare_translation(ref).equals(guess) ||
                        HanziDB.bare_translation(ref).equals(HanziDB.bare_translation(guess)))
                );
    }

    public static ArrayList<Vocabulary> merge_vocabulary(ArrayList<Vocabulary> itemA, ArrayList<Vocabulary> itemB) {
        if (itemA == null)
            return itemB;
        if (itemB == null)
            return itemA;

        boolean a_smaller = itemA.size() < itemB.size();
        ArrayList<Vocabulary> smaller = a_smaller ? itemA : itemB;
        ArrayList<Vocabulary> larger =  a_smaller ? itemB : itemA;

        ArrayList<Vocabulary> res=new ArrayList<Vocabulary>(larger);
        for (int j=a_smaller?smaller.size()-1:0;a_smaller?(j>=0):(j<smaller.size());j=a_smaller?(j-1):(j+1)) {
            Vocabulary voc = smaller.get(j);
            boolean found = false;
            for (int i=0; i<res.size(); i++) {
                if (res.get(i).same_word(voc)) {
                    for (String en:voc.english)
                        if (!res.get(i).english.contains(en))
                            res.get(i).english.add(en);
                    found=true;
                    if (a_smaller) {
                        Vocabulary tmp=res.remove(i);
                        res.add(0, tmp);
                    }
                    break;
                }
            }
            if (!found) {
                if (a_smaller)
                    res.add(0, voc);
                else
                    res.add(voc);
            }
        }

        return res;
    }

    /*
     *
     *  Basic database management
     *
     *
     */

    ////////////////////////////////////////
    // Querying for data
    ////////////////////////////////////////
    public ArrayList<Vocabulary> getTranslations(String chinese) {
        ArrayList<Vocabulary> res = new ArrayList<Vocabulary>();
        ArrayList<Integer> cword_ids = getWordId(chinese);
        for (int id : cword_ids) {
            res.addAll(getTranslations(id));
        }
        return res;
    }

    public ArrayList<Vocabulary> getTranslations(String chinese, String pinyin) {
        ArrayList<Vocabulary> res = new ArrayList<Vocabulary>();
        ArrayList<Integer> cword_ids = getWordId(chinese, pinyin);
        for (int id : cword_ids) {
            res.addAll(getTranslations(id));
        }
        return res;
    }

    public ArrayList<Vocabulary> getCTranslations(String english) {
        ArrayList<Vocabulary> res = new ArrayList<Vocabulary>();
        Cursor cur = db.rawQuery("SELECT c.chinese AS chinese,c.pinyin AS pinyin " +
                "FROM eword " +
                "JOIN cword AS c ON c.cword_id=eword.cword_id " +
                "WHERE translation=?", new String[] {english});
        if (cur.moveToFirst()) {
            do{
                res.add(new Vocabulary
                        (cur.getString(cur.getColumnIndex("chinese")), cur.getString(cur.getColumnIndex("pinyin")),english));
            }while(cur.moveToNext());
        }
        else {
            //Log.d(TAG, "no record found for word "+english);
        }

        cur.close();
        return res;
    }

    public ArrayList<Vocabulary> getWordsWithPinyin(String hanzi) {
        ArrayList<Vocabulary> res=new ArrayList<Vocabulary>();
        ArrayList<Integer> cword_ids = getWordIdWithPinyin(hanzi);
        for (int id : cword_ids) {
            res.addAll(getTranslations(id));
        }
        return res;
    }

    public ArrayList<Vocabulary> getWordsWithPinyin(String hanzi, String pinyin) {
        ArrayList<Vocabulary> res=new ArrayList<Vocabulary>();
        ArrayList<Integer> cword_ids = getWordIdWithPinyin(hanzi, pinyin);
        for (int id : cword_ids) {
            res.addAll(getTranslations(id));
        }
        return res;
    }

    protected ArrayList<Integer> getWordIdWithPinyin(String hanzi) {
        ArrayList<Integer> res = new ArrayList<Integer>();
        Cursor cur = db.rawQuery("SELECT DISTINCT cword_segment.cword_id AS cword_id " +
                "FROM cword_segment " +
                "JOIN char_pinyin ON char_pinyin.char_pinyin_id=cword_segment.char_pinyin_id " +
                "WHERE char_pinyin.hanzi=?", new String[] {hanzi});
        if (cur.moveToFirst()) {
            do{
                //Log.d(TAG, "got record " + cur.getInt(cur.getColumnIndex("cword_id")) + " for word " + chinese);
                res.add(cur.getInt(cur.getColumnIndex("cword_id")));
            }while(cur.moveToNext());
        }
        else {
            //Log.d(TAG, "no record found for word with "+hanzi);
        }
        cur.close();
        return res;
    }

    protected ArrayList<Integer> getWordIdWithPinyin(String hanzi, String pinyin) {
        ArrayList<Integer> res = new ArrayList<Integer>();
        Cursor cur = db.rawQuery("SELECT DISTINCT cword_segment.cword_id AS cword_id " +
                "FROM cword_segment " +
                "JOIN char_pinyin ON char_pinyin.char_pinyin_id=cword_segment.char_pinyin_id " +
                "WHERE char_pinyin.hanzi=? AND char_pinyin.pinyin=?", new String[] {hanzi, pinyin});
        if (cur.moveToFirst()) {
            do{
                //Log.d(TAG, "got record " + cur.getInt(cur.getColumnIndex("cword_id")) + " for word " + chinese);
                res.add(cur.getInt(cur.getColumnIndex("cword_id")));
            }while(cur.moveToNext());
        }
        else {
            //Log.d(TAG, "no record found for word with "+hanzi+"/"+pinyin);
        }
        cur.close();
        return res;
    }

    protected ArrayList<Integer> getWordId(String chinese) {
        ArrayList<Integer> res = new ArrayList<Integer>();
        Cursor cur = db.rawQuery("SELECT * FROM cword WHERE chinese=?", new String[] {chinese});
        if (cur.moveToFirst()) {
            do{
                //Log.d(TAG, "got record " + cur.getInt(cur.getColumnIndex("cword_id")) + " for word " + chinese);
                res.add(cur.getInt(cur.getColumnIndex("cword_id")));
            }while(cur.moveToNext());
        }
        else {
            //Log.d(TAG, "no record found for word "+chinese);
        }
        cur.close();
        return res;
    }

    protected ArrayList<Integer> getWordId(String chinese, String pinyin) {
        ArrayList<Integer> res = new ArrayList<Integer>();
        Cursor cur = db.rawQuery("SELECT * FROM cword WHERE chinese=? AND pinyin=?", new String[] {chinese, pinyin});
        if (cur.moveToFirst()) {
            do{
                //Log.d(TAG, "got record " + cur.getInt(cur.getColumnIndex("cword_id"))
                //        + " for word " + chinese + "/" + pinyin);
                res.add(cur.getInt(cur.getColumnIndex("cword_id")));
            }while(cur.moveToNext());
        }
        else {
            //Log.d(TAG, "no record found for word "+chinese+"/"+pinyin);
        }

        if (res.size() > 1) {
            Log.w(TAG, "Warning, getWordId("+chinese+","+pinyin+") returns more than one id");
        }

        cur.close();
        return res;
    }

    protected int getCharPinyinId(String hanzi, String pinyin) {
        ArrayList<Integer> res = new ArrayList<Integer>();
        Cursor cur = db.rawQuery("SELECT * FROM char_pinyin WHERE hanzi=? AND pinyin=?", new String[] {hanzi, pinyin});
        if (cur.moveToFirst()) {
            do{
                //Log.d(TAG, "got record "+cur.getInt(cur.getColumnIndex("char_pinyin_id")) +
                //        " for character " + hanzi + "/" + pinyin);
                res.add(cur.getInt(cur.getColumnIndex("char_pinyin_id")));
            }while(cur.moveToNext());
        }
        else {
            //Log.d(TAG, "no record found for character "+hanzi+"/"+pinyin);
            res.add(-1);
        }

        if (res.size() > 1) {
            Log.w(TAG, "Warning, getCharPinyinId("+hanzi+","+pinyin+") returns more than one id");
        }

        cur.close();
        return res.get(0);
    }

    public ArrayList<String> getPinyinForChar(String hanzi) {
        ArrayList<String> res = new ArrayList<String>();
        Cursor cur = db.rawQuery("SELECT * FROM char_pinyin WHERE hanzi=?", new String[] {hanzi});
        if (cur.moveToFirst()) {
            do{
                res.add(cur.getString(cur.getColumnIndex("pinyin")));
            }while(cur.moveToNext());
        }
        else {
            //Log.d(TAG, "no record found for character "+hanzi);
        }

        cur.close();
        return res;
    }

    protected ArrayList<Vocabulary> getTranslations(int cword_id) {
        ArrayList<Vocabulary> res = new ArrayList<Vocabulary>();
        Vocabulary tmp=null;

        Cursor cur = db.rawQuery("SELECT * " +
                "FROM eword " +
                "JOIN cword ON eword.cword_id=cword.cword_id " +
                "WHERE cword.cword_id=? ORDER BY cword.cword_id ASC", new String[] {Integer.toString(cword_id)});

        if (cur.moveToFirst()) {
            do{
                String chinese = cur.getString(cur.getColumnIndex("chinese"));
                String pinyin =  cur.getString(cur.getColumnIndex("pinyin"));
                if (tmp == null)
                    tmp = new Vocabulary(
                            chinese, pinyin,
                            cur.getString(cur.getColumnIndex("translation")));
                else {
                    tmp.english.add(cur.getString(cur.getColumnIndex("translation")));
                }
            }while(cur.moveToNext());
            res.add(tmp);
        }

        cur.close();
        return res;
    }

    protected ArrayList<Integer> getEWordId(int cword_id) {
        ArrayList<Integer> res=new ArrayList<Integer>();
        Cursor cur = db.rawQuery("SELECT eng_id " +
                "FROM eword " +
                "WHERE cword_id=?", new String[] {Integer.toString(cword_id)});
        if (cur.moveToFirst()) {
            do{
                res.add(cur.getInt(cur.getColumnIndex("eng_id")));
            }while(cur.moveToNext());
        }
        return res;
    }

    protected ArrayList<Integer> getCwordWithChar(int char_pinyin_id) {
        ArrayList<Integer> res = new ArrayList<Integer>();
        Cursor cur = db.rawQuery("SELECT cword_id " +
                "FROM cword_segment " +
                "WHERE char_pinyin_id=?", new String[] {Integer.toString(char_pinyin_id)});
        if (cur.moveToFirst()) {
            do{
                res.add(cur.getInt(cur.getColumnIndex("cword_id")));
            }while(cur.moveToNext());
        }
        cur.close();
        return res;
    }

    public ArrayList<String> getAllChar() {
        ArrayList<String> res = new ArrayList<String>();

        Cursor cur = db.rawQuery("SELECT DISTINCT hanzi " +
                "FROM char_pinyin",null);

        if (cur.moveToFirst()) {
            do{
                res.add(cur.getString(cur.getColumnIndex("hanzi")));
            }while(cur.moveToNext());
        }

        cur.close();
        return res;
    }

    ////////////////////////////////////////
    // Query for mixed or incomplete data
    ////////////////////////////////////////
    public static class StringElement {
        public static final int CHINESE=0;
        public static final int PINYIN_WITH_TONE=1;
        public static final int PINYIN_WITHOUT_TONE=2;
        public static final int INCOMPLETE_PINYIN=3;

        protected String content;
        protected int type;
        public StringElement(int t, String c) {
            type=t;
            content=c;
        }
    }

    public ArrayList<Vocabulary> getWordsWithSequence(ArrayList<StringElement> elements) {
        return getWordsWithSequence(elements, -1);
    }

    public ArrayList<Vocabulary> getWordsWithSequence(ArrayList<StringElement> elements, int start) {
        ArrayList<Vocabulary> res=new ArrayList<Vocabulary>();
        ArrayList<Integer> cword_ids = getWordIdWithSequence(elements, start);
        for (int id : cword_ids) {
            res.addAll(getTranslations(id));
        }
        return res;
    }

    protected ArrayList<Integer> getWordIdWithSequence(ArrayList<StringElement> elements) {
        return getWordIdWithSequence(elements, -1);
    }

    protected ArrayList<Integer> getWordIdWithSequence(ArrayList<StringElement> elements, int start) {
        // start is 1 for starting with this sequence
        //          0 for not starting with this sequence
        //         -1 for don't care
        ArrayList<Integer> res=new ArrayList<Integer>();
        String query="";
        ArrayList<String> query_args=new ArrayList<String>();

        if (elements.size()==0)
            return res;

        if (elements.size()==1) {
            switch (elements.get(0).type) {
                case StringElement.CHINESE:
                    query= "SELECT DISTINCT cword.cword_id AS cword_id " +
                            "FROM cword " +
                            "JOIN cword_segment ON cword.cword_id=cword_segment.cword_id " +
                            "JOIN char_pinyin ON char_pinyin.char_pinyin_id=cword_segment.char_pinyin_id " +
                            "WHERE char_pinyin.hanzi=?";
                    query_args.add(elements.get(0).content);
                    if (start == 1)
                        query+=" AND cword_segment.pos=0";
                    else if (start == 0) {
                        query+=" AND cword.chinese NOT LIKE ?";
                        query_args.add(elements.get(0).content+"%");
                    }
                    query+=" ORDER BY cword.pinyin";
                    break;
                case StringElement.PINYIN_WITH_TONE:
                    query="SELECT DISTINCT cword.cword_id AS cword_id " +
                            "FROM cword " +
                            "JOIN cword_segment ON cword.cword_id=cword_segment.cword_id " +
                            "JOIN char_pinyin ON char_pinyin.char_pinyin_id=cword_segment.char_pinyin_id " +
                            "WHERE char_pinyin.pinyin=?";
                    query_args.add(elements.get(0).content);
                    if (start == 1)
                        query+=" AND cword_segment.pos=0";
                    else if (start == 0) {
                        query+=" AND cword.pinyin NOT LIKE ?";
                        query_args.add(elements.get(0).content+"%");
                    }
                    query+=" ORDER BY cword.pinyin";
                    break;
                case StringElement.PINYIN_WITHOUT_TONE:
                    query="SELECT DISTINCT cword.cword_id AS cword_id " +
                            "FROM cword " +
                            "JOIN cword_segment ON cword.cword_id=cword_segment.cword_id " +
                            "JOIN char_pinyin ON char_pinyin.char_pinyin_id=cword_segment.char_pinyin_id " +
                            "WHERE char_pinyin.pinyin LIKE ?";
                    query_args.add(elements.get(0).content+"_");
                    if (start == 1)
                        query+=" AND cword_segment.pos=0";
                    else if (start == 0) {
                        query+=" AND cword.pinyin NOT LIKE ?";
                        query_args.add(elements.get(0).content+"%");
                    }
                    query+=" ORDER BY cword.pinyin";
                    break;
                case StringElement.INCOMPLETE_PINYIN:
                    query="SELECT DISTINCT cword.cword_id AS cword_id " +
                            "FROM cword " +
                            "JOIN cword_segment ON cword.cword_id=cword_segment.cword_id " +
                            "JOIN char_pinyin ON char_pinyin.char_pinyin_id=cword_segment.char_pinyin_id " +
                            "WHERE char_pinyin.pinyin LIKE ?";
                    query_args.add(elements.get(0).content+"%");
                    if (start == 1)
                        query+=" AND cword_segment.pos=0";
                    else if (start == 0) {
                        query+=" AND cword.pinyin NOT LIKE ?";
                        query_args.add(elements.get(0).content+"%");
                    }
                    query+=" ORDER BY cword.pinyin";
                    break;
            }
        }
        else {
            int type = elements.get(0).type;
            int i=0;
            String concat="";
            if (type == StringElement.CHINESE) {
                for (i=0; i<elements.size(); i++) {
                    concat+=elements.get(i).content;
                    if (elements.get(i).type != StringElement.CHINESE)
                        break;
                }
            }
            else if (type == StringElement.PINYIN_WITH_TONE) {
                for (i=0; i<elements.size(); i++) {
                    concat+=elements.get(i).content;
                    if (elements.get(i).type != StringElement.PINYIN_WITH_TONE)
                        break;
                }
            }

            if ((i==elements.size()) && (type == StringElement.CHINESE)) {
                // only Chinese...
                if (start == 1) {
                    query="SELECT * FROM cword WHERE chinese LIKE ? ORDER BY cword.pinyin";
                    query_args.add(concat+"%");
                } else if (start == 0) {
                    query="SELECT * FROM cword WHERE chinese LIKE ? AND chinese NOT LIKE ? ORDER BY cword.pinyin";
                    query_args.add("%"+concat+"%");
                    query_args.add(concat+"%");
                } else {
                    query="SELECT * FROM cword WHERE chinese LIKE ? ORDER BY cword.pinyin";
                    query_args.add("%"+concat+"%");
                }

            } else if ((i==elements.size()) && (type == StringElement.CHINESE)) {
                // only pinyins with tones....
                if (start == 1) {
                    query="SELECT * FROM cword WHERE pinyin LIKE ? ORDER BY cword.pinyin";
                    query_args.add(concat+"%");
                } else if (start == 0) {
                    query="SELECT * FROM cword WHERE pinyin LIKE ? AND pinyin NOT LIKE ? ORDER BY cword.pinyin";
                    query_args.add("%"+concat+"%");
                    query_args.add(concat+"%");
                } else {
                    query="SELECT * FROM cword WHERE pinyin LIKE ? ORDER BY cword.pinyin";
                    query_args.add("%"+concat+"%");
                }

            } else {
                // mixed up
                query="SELECT * FROM ";
                for (i=0; i<elements.size(); i++) {
                    if (i>0) {
                        query+= " JOIN ";
                    }
                    query+="(SELECT * FROM char_pinyin JOIN cword_segment ON char_pinyin.char_pinyin_id=cword_segment.char_pinyin_id WHERE ";
                    switch (elements.get(i).type) {
                        case StringElement.CHINESE:
                            query+="hanzi=?";
                            query_args.add(elements.get(i).content);
                            break;
                        case StringElement.PINYIN_WITH_TONE:
                            query+="pinyin=?";
                            query_args.add(elements.get(i).content);
                            break;
                        case StringElement.PINYIN_WITHOUT_TONE:
                            query+="pinyin LIKE ?";
                            query_args.add(elements.get(i).content+"_");
                            break;
                        case StringElement.INCOMPLETE_PINYIN:
                            query+="pinyin LIKE ?";
                            query_args.add(elements.get(i).content+"%");
                            break;
                    }
                    if (i==0) {
                        if (start == 1)
                            query+=" AND cword_segment.pos=0";
                        else if (start == 0)
                            query+=" AND cword_segment.pos>0";
                    }
                    query+=") AS elem"+i;
                    if (i>0) {
                        query+=" ON (elem"+i+".pos=elem"+(i-1)+".pos+1) AND (elem"+i+".cword_id=elem"+(i-1)+".cword_id)";
                    }
                }
                //query += " JOIN cword ON elem0.cword_id=cword.cword_id ORDER BY cword.pinyin";
            }
        }

        Log.d(TAG, "Query for incomplete search is ["+query+"] with args "+query_args.toString());

        Cursor cur = db.rawQuery(query, query_args.toArray(new String[0]));
        if (cur.moveToFirst()) {
            do{
                //Log.d(TAG, "got record " + cur.getInt(cur.getColumnIndex("cword_id")) + " for word " + chinese);
                res.add(cur.getInt(cur.getColumnIndex("cword_id")));
            }while(cur.moveToNext());
        }
        cur.close();
        return res;
    }

    ////////////////////////////////////////
    // Adding data
    ////////////////////////////////////////
    public void addVocabulary(String english, ArrayList<String> chinese, ArrayList<String> pinyin) {
        if (pinyin.size() != chinese.size()) {
            Log.e(TAG, "addVocabulary : Pinyins and Hanzi tables have different sizes");
            return;
        }

        addVocabularyReturnId(english,chinese,pinyin);
    }

    public void addVocabulary(ArrayList<String> english, ArrayList<String> chinese, ArrayList<String> pinyin) {
        int cword_id;

        if (pinyin.size() != chinese.size()) {
            Log.e(TAG, "addVocabulary : Pinyins and Hanzi tables have different sizes");
            return;
        }

        cword_id=addVocabularyReturnId(null,chinese,pinyin);
        for (String eng : english) {
            addVocabulary(eng, cword_id);
        }
    }

    public void addVocabulary(Vocabulary voc) {
        ArrayList<String> chinese = new ArrayList<String>();
        ArrayList<String> pinyin;
        int i, len;

        if (voc.english == null) {
            Log.w(TAG, "Cannot add vocabulary without translations");
            return;
        }

        len = voc.chinese.codePointCount(0, voc.chinese.length());
        for (i = 0; i< len; i++) {
            int from = voc.chinese.offsetByCodePoints(0, i);
            int to = voc.chinese.offsetByCodePoints(0, i + 1);

            chinese.add(voc.chinese.substring(from, to));
        }
        pinyin = PinyinParser.parse_string(voc.pinyin);

        addVocabulary(voc.english,chinese,pinyin);
    }

    protected int addCword(ArrayList<String> chinese, ArrayList<String> pinyin) {
        ArrayList<Integer> cword_id_lst;
        int cword_id, char_pinyin_id;
        int i;
        String chinese_str = "", pinyin_str = "";
        String han, pin;

        for (i = 0; i < chinese.size(); i++) {
            chinese_str += chinese.get(i);
            pinyin_str += pinyin.get(i);
        }

        ContentValues cv = new ContentValues();
        cv.put("chinese", chinese_str);
        cv.put("pinyin", pinyin_str);
        db.insert("cword", null, cv);
        //db.insertWithOnConflict("cword", null, cv, SQLiteDatabase.CONFLICT_IGNORE);

        cword_id_lst = getWordId(chinese_str, pinyin_str);
        cword_id = cword_id_lst.get(0);
        if (cword_id_lst.size() > 1) {
            Log.w(TAG, "Add a cword id, but got " + cword_id_lst.size() + " items");
        }

        for (i = 0; i<chinese.size(); i++) {
            han = chinese.get(i);
            pin = pinyin.get(i);
            char_pinyin_id = getCharPinyinId(han, pin);
            if (char_pinyin_id == -1) {
                char_pinyin_id = addCharPinyin(han, pin);
            }
            cv.clear();
            cv.put("cword_id", cword_id);
            cv.put("char_pinyin_id", char_pinyin_id);
            cv.put("pos", i);
            db.insert("cword_segment", null, cv);
            //db.insertWithOnConflict("cword_segment", null, cv, SQLiteDatabase.CONFLICT_IGNORE);
        }
        return cword_id;
    }

    protected int addCharPinyin(String hanzi, String pinyin) {
        int res;

        ContentValues cv = new ContentValues();
        cv.put("hanzi", hanzi);
        cv.put("pinyin", pinyin);
        db.insert("char_pinyin", null, cv);
        //db.insertWithOnConflict("char_pinyin", null, cv, SQLiteDatabase.CONFLICT_IGNORE);

        res = getCharPinyinId(hanzi, pinyin);
        if (res == -1) {
            Log.w(TAG, "Add a char_pinyin id, but could not find it");
        }

        return res;
    }

    protected int addVocabularyReturnId(String english, ArrayList<String> chinese, ArrayList<String> pinyin) {
        int i;
        String chinese_str = "", pinyin_str = "";
        ArrayList<Integer> cword_id_lst;
        Integer cword_id;

        for (i = 0; i < chinese.size(); i++)
        {
            chinese_str += chinese.get(i);
            pinyin_str += pinyin.get(i);
        }

        cword_id_lst = getWordId(chinese_str, pinyin_str);
        if (cword_id_lst.size() > 0) {
            cword_id = cword_id_lst.get(0);
        }
        else {
            cword_id = addCword(chinese, pinyin);
        }

        if (english != null) {
            addVocabulary(english, cword_id);
        }

        return cword_id;
    }

    protected void addVocabulary(String english, int cword_id) {
        ContentValues cv = new ContentValues();
        cv.put("cword_id", cword_id);
        cv.put("translation", english);
        db.insert("eword", null, cv);
        //db.insertWithOnConflict("eword", null, cv, SQLiteDatabase.CONFLICT_IGNORE);
    }

    ////////////////////////////////////////
    // Deleting data
    ////////////////////////////////////////
    public void delVocabulary(ArrayList<String> chinese, ArrayList<String> pinyin) {
        int i;
        ArrayList<Integer> cword_id;
        String chinese_str="", pinyin_str="";

        if (pinyin.size() != chinese.size()) {
            Log.e(TAG, "delVocabulary : Pinyins and Hanzi tables have different sizes");
            return;
        }

        for (i = 0; i < chinese.size(); i++)
        {
            chinese_str += chinese.get(i);
            pinyin_str += pinyin.get(i);
        }
        cword_id = getWordId(chinese_str,pinyin_str);

        for (int cid : cword_id) {
            ArrayList<Integer> eng_ids = getEWordId(cid);
            for (int eng_id:eng_ids)
                delEword(eng_id);
            checkIsolatedCword(cid);
        }
    }

    public void delVocabulary(String english, ArrayList<String> chinese, ArrayList<String> pinyin) {
        int eng_id, cword_id, i;
        String chinese_str="", pinyin_str="";

        if (pinyin.size() != chinese.size()) {
            Log.e(TAG, "delVocabulary : Pinyins and Hanzi tables have different sizes");
            return;
        }

        for (i = 0; i < chinese.size(); i++)
        {
            chinese_str += chinese.get(i);
            pinyin_str += pinyin.get(i);
        }

        Cursor cur = db.rawQuery("SELECT eng_id, eword.cword_id AS cword_id " +
                                 "FROM eword " +
                                 "  JOIN cword ON cword.cword_id=eword.cword_id " +
                                 "WHERE eword.translation=? AND cword.chinese=? AND cword.pinyin=?",
                new String[] {english,chinese_str,pinyin_str});
        if (cur.moveToFirst()) {
            eng_id = cur.getInt(cur.getColumnIndex("eng_id"));
            cword_id = cur.getInt(cur.getColumnIndex("cword_id"));

            delEword(eng_id);
            checkIsolatedCword(cword_id);
        }
        cur.close();
    }

    public void delVocabulary(ArrayList<String> english, ArrayList<String> chinese, ArrayList<String> pinyin) {
        if (english == null) {
            delVocabulary(chinese,pinyin);
        }
        else {
            for(String en:english) {
                delVocabulary(en, chinese, pinyin);
            }
        }
    }

    public void delVocabulary(Vocabulary voc) {
        ArrayList<String> chinese = new ArrayList<String>();
        ArrayList<String> pinyin;
        int i, len;

        len = voc.chinese.codePointCount(0, voc.chinese.length());
        for (i = 0; i< len; i++) {
            int from = voc.chinese.offsetByCodePoints(0, i);
            int to = voc.chinese.offsetByCodePoints(0, i + 1);

            chinese.add(voc.chinese.substring(from, to));
        }
        pinyin = PinyinParser.parse_string(voc.pinyin);

        if (voc.english == null) {
            delVocabulary(chinese, pinyin);
        }
        else {
            delVocabulary(voc.english,chinese,pinyin);
        }
    }

    protected void delEword(int eng_id) {
        db.delete("eword", "eng_id=?", new String[] {Integer.toString(eng_id)} );

        db.delete("lesson_word", "eng_id=?", new String[] {Integer.toString(eng_id)});
    }

    protected void delCharPinyin(int char_pinyin_id) {
        String hanzi;
        Cursor cur = db.rawQuery("SELECT hanzi  " +
                "FROM char_pinyin " +
                "WHERE char_pinyin_id=?;", new String[] {Integer.toString(char_pinyin_id)});
        if (cur.moveToFirst()) {
            hanzi = cur.getString(cur.getColumnIndex("hanzi"));
        }
        else {
            cur.close();
            return;
        }

        db.delete("char_pinyin", "char_pinyin_id=?",new String[] {Integer.toString(char_pinyin_id)});

        cur = db.rawQuery("SELECT * FROM char_pinyin WHERE hanzi=?", new String[] {hanzi});
        if (cur.getCount() == 0) {
            db.delete("leitner", "hanzi=?",new String[] {hanzi});
        }
        cur.close();
    }

    protected void delCword(int cword_id) {
        ArrayList<Integer> char_pinyin_id = new ArrayList<Integer>();

        Cursor cur = db.rawQuery("SELECT char_pinyin_id FROM cword_segment WHERE cword_id=?", new String[] {Integer.toString(cword_id)});
        if (cur.moveToFirst()) {
            do{
                char_pinyin_id.add(cur.getInt(cur.getColumnIndex("char_pinyin_id")));
            }while(cur.moveToNext());
        }
        db.delete("cword", "cword_id=?", new String[] {Integer.toString(cword_id)});
        db.delete("cword_segment", "cword_id=?", new String[] {Integer.toString(cword_id)});

        for (int cpid : char_pinyin_id) {
            if (getCwordWithChar(cpid).size() == 0) {
                delCharPinyin(cpid);
            }

        }

        cur.close();
    }

    protected void checkIsolatedCword(int cword_id) {
        if (getTranslations(cword_id).isEmpty()) {
            delCword(cword_id);
        }
    }

    ////////////////////////////////////////
    // Get information
    ////////////////////////////////////////
    public int getNumWords() {
        int res;
        Cursor cur = db.rawQuery("SELECT cword_id FROM cword", null);
        res= cur.getCount();

        cur.close();
        return res;
    }

    public int getNumChars() {
        int res;
        Cursor cur = db.rawQuery("SELECT hanzi FROM char_pinyin GROUP BY hanzi", null);
        res= cur.getCount();

        cur.close();
        return res;
    }

    ////////////////////////////////////////
    // Merge Database
    ////////////////////////////////////////

    public AsyncTask create_merge_task(final String path, final boolean with_dialog, final Activity context, final Runnable finishing) {
        AsyncTask<Void,Integer,Void> res = new AsyncTask<Void,Integer,Void>() {
            ProgressDialog mProgressDialog;
            boolean backup_ready=false;
            final String new_db_name = "tmp_db", backup_db_name="backup_db";

            @Override
            protected Void doInBackground(Void... voids) {
                if (getAllChar().size()==0) {
                    // just need to copy
                    app.replaceDataBase(path);
                }
                else {
                    // copy to the DB directory
                    if (!app.copyDataBase(path, appContext.getApplicationContext().getDatabasePath(new_db_name).getPath())) {
                        clean_up_on_cancel(true);
                        return null;
                    }
                    publishProgress(5);

                    if (isCancelled()) {
                        clean_up_on_cancel(false);
                        return null;
                    }

                    // copy a backup
                    if (!app.copyDataBase(db.getPath(), appContext.getApplicationContext().getDatabasePath(backup_db_name).getPath())) {
                        clean_up_on_cancel(true);
                        return null;
                    }
                    publishProgress(10);
                    if (isCancelled()) {
                        clean_up_on_cancel(false);
                        return null;
                    }
                    backup_ready=true;

                    // merge it
                    HanziDB other_db = new HanziDB(appContext, app, new_db_name);
                    ArrayList<String> chars = other_db.getAllChar();
                    for (int i=0; i<chars.size(); i++) {
                        publishProgress(10+(i*50)/chars.size());
                        if (isCancelled()) {
                            clean_up_on_cancel(false);
                            return null;
                        }
                        String c=chars.get(i);
                        ArrayList<Vocabulary> voc = other_db.getWordsWithPinyin(c);
                        for(Vocabulary v:voc)
                            addVocabulary(v);
                        if (other_db.getBoxForChar(c) != 0) {
                            setBoxForChar(c, other_db.getBoxForChar(c));
                            setUpdateTimeForChar(c, other_db.getUpdateTimeForChar(c));
                        }

                    }

                    ArrayList<String> original_lessons = getLessons();
                    ArrayList<String> lessons = other_db.getLessons();
                    for (int i=0; i<lessons.size(); i++) {
                        publishProgress(60+(i*30)/lessons.size());
                        if (isCancelled()) {
                            clean_up_on_cancel(false);
                            return null;
                        }
                        String l = lessons.get(i);
                        if (!original_lessons.contains(l))
                            addLesson(l);
                        ArrayList<Vocabulary> voc = other_db.getVocabularyInLessons(new ArrayList<String>(Arrays.asList(l)));
                        for (Vocabulary v:voc) {
                            assignWordLesson(l,v);
                        }
                    }

                    ArrayList<String> all_lessons=new ArrayList<String>();
                    all_lessons.addAll(lessons);
                    all_lessons.addAll(0,original_lessons);
                    // remove duplicate from the end
                    for (int i=all_lessons.size()-1; i>=0; i--) {
                        if (all_lessons.indexOf(all_lessons.get(i))<i)
                            all_lessons.remove(i);
                    }
                    sortLessons(all_lessons);
                    
                    other_db.close();

                    backup_ready=false;
                    // Erase the temporary database
                    new File(appContext.getApplicationContext().getDatabasePath(new_db_name).getPath()).delete();
                    publishProgress(95);

                    // Erase backup
                    new File(appContext.getApplicationContext().getDatabasePath(backup_db_name).getPath()).delete();
                    publishProgress(100);

                    if (isCancelled()) { // everything went through anyway...
                        onPostExecute(null);
                    }
                }

                return null;
            }

            @Override
            protected void onPreExecute() {
                super.onPreExecute();

                // create the progress dialog if needed
                if (with_dialog) {
                    if (getAllChar().size()==0) {
                        mProgressDialog = ProgressDialog.show(context, "Importing Database", "Copying...", false);
                        mProgressDialog.setIndeterminate(true);
                    }
                    else {
                        mProgressDialog = new ProgressDialog(context);
                        mProgressDialog.setMessage("Merging database");
                        mProgressDialog.setIndeterminate(false);
                        mProgressDialog.setMax(100);
                        mProgressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                            public void onCancel(DialogInterface dialogInterface) {
                                cancel(false);
                            }
                        });
                        mProgressDialog.setCancelable(true);
                        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                        mProgressDialog.show();
                    }
                }
            }

            @Override
            protected void onProgressUpdate(Integer... values) {
                super.onProgressUpdate(values);
                if (with_dialog)
                    mProgressDialog.setProgress(values[0]);
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);

                // Dismiss progress dialog
                if (with_dialog)
                    mProgressDialog.dismiss();

                if (finishing != null)
                    finishing.run();
            }

            private void clean_up_on_cancel(final boolean error) {
                // Erase the temporary database
                new File(appContext.getApplicationContext().getDatabasePath(new_db_name).getPath()).delete();

                // restore the backup if it is ready
                if (backup_ready) {
                    String db_path = db.getPath();
                    new File(db_path).delete();
                    new File(appContext.getApplicationContext().getDatabasePath(backup_db_name).getPath()).renameTo(new File(db_path));
                }
                else {
                    // delete the possible backup
                    new File(appContext.getApplicationContext().getDatabasePath(backup_db_name).getPath()).delete();
                }

                if (with_dialog || error)
                    context.runOnUiThread(new Runnable() {
                        public void run() {
                            if (with_dialog)
                                mProgressDialog.dismiss();

                            if (error) {
                                Toast toast = Toast.makeText(context, "An error occured while importing the database", 5);
                                toast.show();
                            }
                        }
                    });
            }
        };

        res.execute();

        return res;
    }

    ////////////////////////////////////////
    // Export Database
    ////////////////////////////////////////
    public AsyncTask create_export_task(final String path, final boolean with_scores, final boolean filtered_lessons, final boolean with_dialog, final Activity context, final Runnable finishing) {
        AsyncTask<Void,Integer,Void> res = new AsyncTask<Void,Integer,Void>() {
            ProgressDialog mProgressDialog;
            final String new_db_name = "tmp_db";
            HanziDB new_db=null;

            @Override
            protected Void doInBackground(Void... voids) {
                if ((with_scores) && (!filtered_lessons)) {
                    if (!app.copyDataBase(db.getPath(), path)) {
                        clean_up_on_cancel(true);
                        return null;
                    }
                }
                else {
                    new_db = new HanziDB(appContext, app, new_db_name);

                    ArrayList<Vocabulary> vocabulary;
                    if (filtered_lessons)
                        vocabulary=getVocabularyInLessons(lesson_filter);
                    else
                        vocabulary=getVocabularyInLessons(new ArrayList<String>(Arrays.asList("")));

                    for (int i=0; i<vocabulary.size(); i++) {
                        Vocabulary v=vocabulary.get(i);
                        publishProgress((i*50)/vocabulary.size());
                        if (isCancelled()) {
                            Log.d(TAG, "getting out");
                            clean_up_on_cancel(false);
                            return null;
                        }

                        new_db.addVocabulary(v);
                        Log.d(TAG, "exporting vocabulary "+v.chinese);
                    }

                    ArrayList<String> lessons;
                    if (filtered_lessons)
                        lessons=lesson_filter;
                    else
                        lessons=getLessons();

                    for (int i=0; i<lessons.size(); i++) {
                        String lesson = lessons.get(i);
                        if (isCancelled()) {
                            clean_up_on_cancel(false);
                            return null;
                        }
                        publishProgress(50+(i*30)/lessons.size());
                        Log.d(TAG, "exporting lesson "+lesson);
                        if (lesson.equals(""))
                            continue;
                        new_db.addLesson(lesson);

                        ArrayList<Vocabulary> lesson_voc = getVocabularyInLessons(new ArrayList<String>(Arrays.asList(lesson)));
                        for (Vocabulary v : lesson_voc) {
                            new_db.assignWordLesson(lesson, v);
                        }
                    }
                    new_db.sortLessons(lessons);

                    if (with_scores) {
                        ArrayList<String> chars = new_db.getAllChar();
                        for (int i=0; i<chars.size(); i++) {
                            String c = chars.get(i);
                            if (isCancelled()) {
                                clean_up_on_cancel(false);
                                return null;
                            }
                            publishProgress(80+(i*10)/chars.size());
                            int b = getBoxForChar(c);
                            if (b!=0) {
                                long t = getUpdateTimeForChar(c);
                                new_db.setBoxForChar(c, b);
                                new_db.setUpdateTimeForChar(c, t);
                            }
                        }
                    }

                    if (!app.copyDataBase(new_db.getWritableDatabase().getPath(), path)) {
                        clean_up_on_cancel(true);
                        return null;
                    }

                    new_db.close();

                    publishProgress(90);

                    appContext.deleteDatabase(new_db_name);

                    if (isCancelled()) { // everything went through anyway...
                        onPostExecute(null);
                    }
                }
                return null;
            }

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                // create the progress dialog if needed
                if (with_dialog) {
                    if ((with_scores) && (!filtered_lessons)) {
                        mProgressDialog = ProgressDialog.show(context, "Exporting Database", "Copying...", false);
                        mProgressDialog.setIndeterminate(true);
                    }
                    else {
                        mProgressDialog = new ProgressDialog(context);
                        mProgressDialog.setMessage("Exporting database");
                        mProgressDialog.setIndeterminate(false);
                        mProgressDialog.setMax(100);
                        mProgressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                            public void onCancel(DialogInterface dialogInterface) {
                                cancel(false);
                            }
                        });
                        mProgressDialog.setCancelable(true);
                        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                        mProgressDialog.show();
                    }
                }
            }

            @Override
            protected void onProgressUpdate(Integer... values) {
                super.onProgressUpdate(values);
                if (with_dialog)
                    mProgressDialog.setProgress(values[0]);
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);

                if (with_dialog)
                    mProgressDialog.dismiss();

                if (finishing!= null)
                    finishing.run();
            }

            private void clean_up_on_cancel(final boolean error) {
                Log.d(TAG, "Cancelling export !");
                if(new_db!=null)
                    new_db.close();

                appContext.deleteDatabase(new_db_name);
                new File(path).delete();

                if (with_dialog || error)
                    context.runOnUiThread(new Runnable() {
                        public void run() {
                            if (with_dialog)
                                mProgressDialog.dismiss();

                            if (error) {
                                Toast toast = Toast.makeText(context, "An error occured while exporting the database", 5);
                                toast.show();
                            }
                        }
                    });

            }
        };

        res.execute();

        return res;
    }

    ////////////////////////////////////////
    // Clear Database
    ////////////////////////////////////////
    public void clearDB() {
        onCreate(db);
    }

    /*
     *
     *  Lesson database management
     *
     *
     */

    ////////////////////////////////////////
    // Manage Lessons
    ////////////////////////////////////////
    public void addLesson(String name) {
        ContentValues cv = new ContentValues();
        cv.put("name", name);
        db.insert("lessons", null, cv);
        //db.insertWithOnConflict("lessons", null, cv, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public void delLesson(String name) {
        int lesson_id;

        lesson_id = getLessonId(name);
        if (lesson_id == -1)
            return;

        db.delete("lesson_word", "lesson_id=?", new String[] {Integer.toString(lesson_id)});
        db.delete("lessons", "lesson_id=?", new String[] {Integer.toString(lesson_id)});

        if (lesson_filter.contains(name)) {
            lesson_filter.remove(name);
        }
    }

    public void renameLesson(String old_name, String new_name) {
        int lesson_id;

        lesson_id = getLessonId(old_name);
        if (lesson_id == -1)
            return;

        ContentValues cv = new ContentValues();
        cv.put("name", new_name);

        db.update("lessons", cv, "lesson_id=?", new String[] {Integer.toString(lesson_id)});

        if (lesson_filter.contains(old_name)) {
            lesson_filter.remove(old_name);
            lesson_filter.add(new_name);
        }
    }

    public ArrayList<String> getLessons() {
        ArrayList<String> res = new ArrayList<String>();

        Cursor cur = db.rawQuery("SELECT name FROM lessons ORDER BY list_order ASC", null);
        if (cur.moveToFirst()) {
            do{
                res.add(cur.getString(cur.getColumnIndex("name")));
            }while(cur.moveToNext());
        }
        cur.close();
        return res;
    }

    protected int getLessonId(String name) {
        int res = -1;
        Cursor cur = db.rawQuery("SELECT * FROM lessons WHERE name=?", new String[] {name});
        if (cur.moveToFirst()) {
            res = cur.getInt(cur.getColumnIndex("lesson_id"));
        }
        cur.close();
        return res;

    }

    protected void sortLessons(ArrayList<String> lessons) {
        int i = 1;
        ContentValues cv = new ContentValues();
        for (String le : lessons) {
            cv.clear();
            cv.put("list_order", i);

            db.update("lessons", cv, "name=?", new String[] {le});
            i++;
        }

    }

    ////////////////////////////////////////
    // Manage Words in lessons
    ////////////////////////////////////////
    public void assignWordLesson(String lesson, String english, String chinese, String pinyin) {
        int eng_id=-1;
        int lesson_id;

        lesson_id = getLessonId(lesson);
        if (lesson_id == -1) {
            Log.w(TAG, "Unknown lesson " + lesson);
            return;
        }

        Cursor cur = db.rawQuery("SELECT eng_id " +
                                 "FROM eword " +
                                 "  JOIN cword ON cword.cword_id=eword.cword_id " +
                                 "WHERE eword.translation=? AND cword.chinese=? AND cword.pinyin=?",
                new String[] {english,chinese,pinyin});
        if (cur.moveToFirst()) {
            eng_id = cur.getInt(cur.getColumnIndex("eng_id"));
        }
        cur.close();
        if (eng_id == -1) {
            Log.w(TAG, "Cannot find word ("+english+","+chinese+","+pinyin);
            return;
        }

        ContentValues cv = new ContentValues();
        cv.put("lesson_id", lesson_id);
        cv.put("eng_id", Integer.toString(eng_id));
        db.insert("lesson_word", null, cv);
        //db.insertWithOnConflict("lesson_word", null, cv, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public void assignWordLesson(String lesson, Vocabulary voc) {
        Vocabulary new_voc;
        if (voc.english == null) {
            new_voc = getTranslations(voc.chinese, voc.pinyin).get(0);
        }
        else {
            new_voc = voc;
        }
        for (String english : new_voc.english) {
            assignWordLesson(lesson, english, voc.chinese, voc.pinyin);
        }
    }

    public void removeWordLesson(String lesson, String english, String chinese, String pinyin) {
        int eng_id=-1;
        int lesson_id;

        lesson_id = getLessonId(lesson);
        if (lesson_id == -1) {
            Log.w(TAG, "Unknown lesson " + lesson);
            return;
        }

        Cursor cur = db.rawQuery("SELECT eng_id " +
                                 "FROM eword " +
                                 "  JOIN cword ON cword.cword_id=eword.cword_id " +
                                 "WHERE eword.translation=? AND cword.chinese=? AND cword.pinyin=?",
                new String[] {english,chinese,pinyin});
        if (cur.moveToFirst()) {
            eng_id = cur.getInt(cur.getColumnIndex("eng_id"));
        }
        cur.close();
        if (eng_id == -1) {
            Log.w(TAG, "Cannot find word ("+english+","+chinese+","+pinyin);
            return;
        }

        db.delete("lesson_word", "eng_id=? AND lesson_id=?", new String[] {Integer.toString(eng_id), Integer.toString(lesson_id)});
    }

    public void removeWordLesson(String lesson, Vocabulary voc) {
        Vocabulary new_voc;
        if (voc.english == null) {
            new_voc = getTranslations(voc.chinese, voc.pinyin).get(0);
        }
        else {
            new_voc = voc;
        }
        for (String english : new_voc.english) {
            removeWordLesson(lesson, english, voc.chinese, voc.pinyin);
        }
    }

    public ArrayList<String> getLessonForWord(String chinese, String pinyin, String english) {
        ArrayList<String> res=new ArrayList<String>();
        ArrayList<Integer> cword_id = getWordId(chinese, pinyin);

        if (cword_id.size() == 0)
            return res;

        Cursor cur = db.rawQuery("SELECT l.name AS name " +
                "FROM lessons AS l " +
                " JOIN lesson_word AS lw ON lw.lesson_id=l.lesson_id " +
                " JOIN (SELECT cword_id, eng_id FROM eword WHERE translation=?) AS ew ON lw.eng_id=ew.eng_id" +
                " JOIN (SELECT cword_id FROM cword WHERE chinese=? AND pinyin=?) AS cw ON ew.cword_id=cw.cword_id"
                , new String[] {english, chinese, pinyin});

        if (cur.moveToFirst()) {
            do{
                //Log.d(TAG, "got record " + cur.getInt(cur.getColumnIndex("cword_id")) + " for word " + chinese);
                res.add(cur.getString(cur.getColumnIndex("name")));
            }while(cur.moveToNext());
        }

        cur.close();
        return res;
    }

    public ArrayList<Vocabulary> getVocabularyInLessons(ArrayList<String> lessons) {
        ArrayList<Vocabulary> res = new ArrayList<Vocabulary>();
        Vocabulary tmp=null;
        Cursor cur;

        if (lessons.contains("")) {
            cur = db.rawQuery("SELECT e.translation AS translation, " +
                    "c.chinese AS chinese, " +
                    "c.pinyin AS pinyin " +
                    "FROM  eword AS e " +
                    " JOIN cword AS c ON c.cword_id=e.cword_id "
                    ,null);
        }
        else {
            String whereQuery = buildWhereQuery(lessons);

            cur = db.rawQuery("SELECT e.translation AS translation, " +
                    "c.chinese AS chinese, " +
                    "c.pinyin AS pinyin " +
                    "FROM (SELECT * FROM lesson_word " + whereQuery + ") AS lesson_word " +
                    " JOIN eword AS e ON lesson_word.eng_id=e.eng_id " +
                    " JOIN cword AS c ON c.cword_id=e.cword_id "
                    ,null);
        }

        if (cur.moveToFirst()) {
            do{
                String chinese = cur.getString(cur.getColumnIndex("chinese"));
                String pinyin = cur.getString(cur.getColumnIndex("pinyin"));
                if (tmp == null)
                    tmp = new Vocabulary(chinese, pinyin,
                            cur.getString(cur.getColumnIndex("translation")));
                else {
                    if (tmp.chinese.equals(chinese) && tmp.pinyin.equals(pinyin))
                        tmp.english.add(cur.getString(cur.getColumnIndex("translation")));
                    else {
                        res.add(tmp);
                        tmp = new Vocabulary(chinese, pinyin,
                                cur.getString(cur.getColumnIndex("translation")));
                    }
                }
            }while(cur.moveToNext());
            res.add(tmp);
        }

        cur.close();
        return res;
    }

    protected String buildWhereQuery(ArrayList<String> lessons) {
        String whereQuery = "WHERE lesson_word.lesson_id IN (";
        int size = lessons.size();
        if (size == 0)
            return "";

        for (String lesson : lessons) {
            int id = getLessonId(lesson);
            if (id == -1) {
                size --;
                continue;
            }
            if (--size == 0)
                whereQuery += "'" + Integer.toString(id) + "'";
            else
                whereQuery += "'" + Integer.toString(id) + "',";
        }
        whereQuery += ")";
        return whereQuery;
    }

    ////////////////////////////////////////
    // Manage lesson filter
    ////////////////////////////////////////
    protected boolean lesson_filter_enabled =false;
    protected ArrayList<String> lesson_filter=new ArrayList<String>();
    protected String where_filter;

    public void enableFilter(boolean enable) {
        lesson_filter_enabled = enable;
    }
    
    public boolean isFilterEnabled() {
        return lesson_filter_enabled;
    }

    public void setLessonsToFilter(ArrayList<String> lessons) {
        lesson_filter.clear();
        for (String lesson: getLessons()) {
            if (lessons.contains(lesson))
                lesson_filter.add(lesson);
        }
        where_filter = buildWhereQuery(lessons);
    }

    public ArrayList<String> getLessonsToFilter() {
        return (ArrayList<String>) lesson_filter.clone();
    }

    ////////////////////////////////////////
    // Use lesson filter
    ////////////////////////////////////////
    public ArrayList<Vocabulary> getFilteredTranslations(String chinese) {
        if ((lesson_filter.size() > 0) && (lesson_filter_enabled)) {
            ArrayList<Vocabulary> res = new ArrayList<Vocabulary>();
            ArrayList<Integer> cword_ids = getFilteredWordId(chinese);
            for (int id : cword_ids) {
                res.addAll(getFilteredTranslations(id));
            }
            return res;
        }
        else
            return getTranslations(chinese);
    }

    public ArrayList<Vocabulary> getFilteredTranslations(String chinese, String pinyin) {
        if ((lesson_filter.size() > 0) && (lesson_filter_enabled)) {
            ArrayList<Vocabulary> res = new ArrayList<Vocabulary>();
            ArrayList<Integer> cword_ids = getFilteredWordId(chinese, pinyin);
            for (int id : cword_ids) {
                res.addAll(getFilteredTranslations(id));
            }
            return res;
        }
        else
            return getTranslations(chinese, pinyin);
    }

    public ArrayList<Vocabulary> getFilteredCTranslations(String english) {
        if ((lesson_filter.size() > 0) && (lesson_filter_enabled)) {
            ArrayList<Vocabulary> res = new ArrayList<Vocabulary>();
            Cursor cur = db.rawQuery("SELECT cword.chinese AS chinese,cword.pinyin AS pinyin " +
                    "FROM (SELECT eng_id FROM lesson_word " + where_filter + ") AS lesson_word " +
                    "JOIN (SELECT * FROM eword WHERE translation=?) AS eword ON eword.eng_id=lesson_word.eng_id " +
                    "JOIN cword ON cword.cword_id=eword.cword_id "
                    , new String[] {english});
            if (cur.moveToFirst()) {
                do{
                    res.add(new Vocabulary
                            (cur.getString(cur.getColumnIndex("chinese")), cur.getString(cur.getColumnIndex("pinyin")),english));
                }while(cur.moveToNext());
            }
            else {
                //Log.d(TAG, "no record found for word "+english);
            }

            cur.close();
            return res;
        }
        else
            return getCTranslations(english);
    }

    protected ArrayList<Integer> getFilteredWordId(String chinese) {
        ArrayList<Integer> res = new ArrayList<Integer>();
        Cursor cur = db.rawQuery("SELECT cword.cword_id AS cword_id " +
                "FROM (SELECT eng_id FROM lesson_word " + where_filter + ") AS lesson_word " +
                "JOIN eword ON eword.eng_id=lesson_word.eng_id " +
                "JOIN cword ON cword.cword_id=eword.cword_id " +
                "WHERE cword.chinese=?", new String[] {chinese});
        if (cur.moveToFirst()) {
            do{
                //Log.d(TAG, "got record " + cur.getInt(cur.getColumnIndex("cword_id")) + " for word " + chinese);
                res.add(cur.getInt(cur.getColumnIndex("cword_id")));
            }while(cur.moveToNext());
        }
        else {
            //Log.d(TAG, "no record found for word "+chinese+" in filtered db");
        }
        cur.close();
        return res;
    }

    protected ArrayList<Integer> getFilteredWordId(String chinese, String pinyin) {
        ArrayList<Integer> res = new ArrayList<Integer>();
        Cursor cur = db.rawQuery("SELECT * " +
                "FROM (SELECT eng_id FROM lesson_word " + where_filter + ") AS lesson_word " +
                "JOIN eword ON eword.eng_id=lesson_word.eng_id " +
                "JOIN cword ON cword.cword_id=eword.cword_id " +
                "WHERE cword.chinese=? AND cword.pinyin=?", new String[] {chinese, pinyin});
        if (cur.moveToFirst()) {
            do{
                //Log.d(TAG, "got record " + cur.getInt(cur.getColumnIndex("cword_id")) + " for word " + chinese);
                res.add(cur.getInt(cur.getColumnIndex("cword_id")));
            }while(cur.moveToNext());
        }
        else {
            //Log.d(TAG, "no record found for word "+chinese+" in filtered db");
        }
        cur.close();
        return res;
    }

    protected ArrayList<Vocabulary> getFilteredTranslations(int cword_id) {
        ArrayList<Vocabulary> res = new ArrayList<Vocabulary>();
        Vocabulary tmp=null;

        Cursor cur = db.rawQuery("SELECT * " +
                "FROM (SELECT eng_id FROM lesson_word " + where_filter + ") AS lesson_word " +
                "JOIN eword ON eword.eng_id=lesson_word.eng_id " +
                "JOIN cword ON eword.cword_id=cword.cword_id " +
                "WHERE cword.cword_id=? ORDER BY cword.cword_id ASC", new String[] {Integer.toString(cword_id)});

        if (cur.moveToFirst()) {
            do{
                String chinese = cur.getString(cur.getColumnIndex("chinese"));
                String pinyin =  cur.getString(cur.getColumnIndex("pinyin"));
                if (tmp == null)
                    tmp = new Vocabulary(
                            chinese, pinyin,
                            cur.getString(cur.getColumnIndex("translation")));
                else {
                    if (tmp.chinese.equals(chinese) && tmp.pinyin.equals(pinyin))
                        tmp.english.add(cur.getString(cur.getColumnIndex("translation")));
                    else {
                        res.add(tmp);
                        tmp = new Vocabulary(chinese, pinyin,
                                cur.getString(cur.getColumnIndex("translation")));
                    }
                }
            }while(cur.moveToNext());
            res.add(tmp);
        }

        cur.close();
        return res;
    }

    /*
     *
     *  Leitner learning database management
     *
     *
     */

    protected long box1_delay = (long) 2 * 60 * 60 * 1000; // 2 hours
    protected long box2_delay = (long) 2 * 24 * 60 * 60 * 1000; // 2 days
    protected long box3_delay = (long) 1 * 7 * 24 * 60 * 60 * 1000; // 1 week
    protected long box4_delay = (long) 3 * 7 * 24 * 60 * 60 * 1000; // 3 weeks

    private static final int MAX_BOX=4;

    ////////////////////////////////////////
    // Get information
    ////////////////////////////////////////
    public int getNumCharsInBox(int box) {
        int res;

        if ((box < 0) || (box > MAX_BOX))
            return 0;

        Cursor cur = db.rawQuery("SELECT hanzi FROM leitner WHERE box=?", new String[] {Integer.toString(box)});
        res= cur.getCount();

        cur.close();

        if (box == 0) {
            // add the characters that do not have any box...
            cur = db.rawQuery("SELECT hanzi FROM char_pinyin WHERE hanzi NOT IN (SELECT hanzi FROM leitner)", null);
            res += cur.getCount();
            cur.close();
        }
        return res;
    }

    public int getNumCharsExpiredInBox(int box) {
        int res;

        if ((box < 0) || (box > MAX_BOX))
            return 0;

        if (box == 0)
            return getNumCharsInBox(0);
        
        long add_time = getExpiryTime(box);

        Cursor cur = db.rawQuery("SELECT hanzi FROM leitner WHERE box=? AND mod_time<?",
                new String[] {Integer.toString(box), Long.toString(System.currentTimeMillis()-add_time)});
        res= cur.getCount();

        cur.close();
        return res;
    }

    public int getBoxForChar(String hanzi) {
        int res;

        Cursor cur = db.rawQuery("SELECT box FROM leitner WHERE hanzi=?",
                new String[] {hanzi});
        if (cur.moveToFirst()) {
            do{
                //Log.d(TAG, "got record " + cur.getInt(cur.getColumnIndex("cword_id")) + " for word " + chinese);
                res = cur.getInt(cur.getColumnIndex("box"));
            }while(cur.moveToNext());
        }
        else {
            res = 0;
        }

        cur.close();
        return res;
    }

    protected long getUpdateTimeForChar(String hanzi) {
        long res;

        Cursor cur = db.rawQuery("SELECT mod_time FROM leitner WHERE hanzi=?",
                new String[] {hanzi});
        if (cur.moveToFirst()) {
            do{
                //Log.d(TAG, "got record " + cur.getInt(cur.getColumnIndex("cword_id")) + " for word " + chinese);
                res = cur.getLong(cur.getColumnIndex("mod_time"));
            }while(cur.moveToNext());
        }
        else {
            res = 0;
        }

        cur.close();
        return res;
    }

    protected long getExpiryTime (int box) {
        switch (box) {
            case 0 :
                return 0;
            case 1:
                return box1_delay;
            case 2:
                return box2_delay;
            case 3:
                return box3_delay;
            case 4:
                return box4_delay;
            default:
                return 0;
        }
    }

    protected ArrayList<String> getMostAdvancedExpiring(int num) {
        Cursor cur;
        ArrayList<String> res=new ArrayList<String>();

        cur = db.rawQuery("SELECT hanzi, CASE box WHEN 1 THEN (?-mod_time)*? WHEN 2 THEN (?-mod_time)*? WHEN 3 THEN (?-mod_time)*? WHEN 4 THEN (?-mod_time)*? ELSE mod_time END AS delay " +
                "FROM leitner WHERE (box=1 AND mod_time>?) OR (box=2 AND mod_time>?) OR (box=3 AND mod_time>?) OR (box=4 AND mod_time>?) ORDER BY delay DESC",
                new String[] {
                        Long.toString(System.currentTimeMillis()), Long.toString(getExpiryTime(4)/getExpiryTime(1)),
                        Long.toString(System.currentTimeMillis()), Long.toString(getExpiryTime(4)/getExpiryTime(2)),
                        Long.toString(System.currentTimeMillis()), Long.toString(getExpiryTime(4)/getExpiryTime(3)),
                        Long.toString(System.currentTimeMillis()), Long.toString(1),
                        Long.toString(System.currentTimeMillis()-getExpiryTime(1)), Long.toString(System.currentTimeMillis()-getExpiryTime(2)),
                        Long.toString(System.currentTimeMillis()-getExpiryTime(3)), Long.toString(System.currentTimeMillis()-getExpiryTime(4))});
        if (cur.moveToFirst()) {
            do {
                res.add(cur.getString(cur.getColumnIndex("hanzi")));
            } while ((--num>0) && (cur.moveToNext()));
        }
        cur.close();
        return res;
    }

    ////////////////////////////////////////
    // Update data
    ////////////////////////////////////////
    public void resetTime(String hanzi) {

        Cursor cur = db.rawQuery("SELECT box FROM leitner WHERE hanzi=?",
                new String[] {hanzi});
        ContentValues cv = new ContentValues();
        cv.put("mod_time", System.currentTimeMillis());

        if (cur.getCount() == 0) {
            cv.put("hanzi", hanzi);
            cv.put("box", 0);
            db.insert("leitner", null, cv);
        }
        else {
            cv.put("mod_time", System.currentTimeMillis());
            db.update("leitner", cv, "hanzi=?", new String[] {hanzi});
        }
        cur.close();
    }

    public void betterBox(String hanzi) {
        int current_box = getBoxForChar(hanzi);
        current_box ++;
        if (current_box > MAX_BOX) current_box = MAX_BOX;

        ContentValues cv = new ContentValues();
        cv.put("box", current_box);
        cv.put("mod_time", System.currentTimeMillis());
        Cursor cur = db.rawQuery("SELECT box FROM leitner WHERE hanzi=?",
                new String[] {hanzi});
        if (cur.getCount() == 0) {
            cv.put("hanzi", hanzi);
            db.insert("leitner", null, cv);
        }
        else {
            db.update("leitner", cv, "hanzi=?", new String[] {hanzi});
        }
        cur.close();
    }

    public void restartStudy(String hanzi) {
        ContentValues cv = new ContentValues();
        cv.put("box", 0);
        cv.put("mod_time", System.currentTimeMillis());
        Cursor cur = db.rawQuery("SELECT box FROM leitner WHERE hanzi=?",
                new String[] {hanzi});
        if (cur.getCount() == 0) {
            cv.put("hanzi", hanzi);
            db.insert("leitner", null, cv);
        }
        else {
            db.update("leitner", cv, "hanzi=?", new String[] {hanzi});
        }
        cur.close();
    }

    protected void setUpdateTimeForChar(String hanzi, long time) {
        ContentValues cv = new ContentValues();
        cv.put("mod_time", time);
        Cursor cur = db.rawQuery("SELECT box FROM leitner WHERE hanzi=?",
                new String[] {hanzi});
        if (cur.getCount() == 0) {
            cv.put("hanzi", hanzi);
            cv.put("box", 0);
            db.insert("leitner", null, cv);
        }
        else {
            db.update("leitner", cv, "hanzi=?", new String[] {hanzi});
        }
        cur.close();
    }

    protected void setBoxForChar(String hanzi, int box) {
        ContentValues cv = new ContentValues();
        cv.put("box", box);
        cv.put("mod_time", System.currentTimeMillis());
        Cursor cur = db.rawQuery("SELECT box FROM leitner WHERE hanzi=?",
                new String[] {hanzi});
        if (cur.getCount() == 0) {
            cv.put("hanzi", hanzi);
            db.insert("leitner", null, cv);
        }
        else {
            db.update("leitner", cv, "hanzi=?", new String[] {hanzi});
        }
        cur.close();
    }

    protected void setExpired(String hanzi) {
        setUpdateTimeForChar(hanzi, System.currentTimeMillis() - getExpiryTime(getBoxForChar(hanzi)));
    }

    ////////////////////////////////////////
    // Use to find what character to study
    ////////////////////////////////////////
    public String getNextToStudy() {
        ArrayList<String> all = getAllToStudy();
        if (all.size() > 0)
            return all.get((int) (all.size() * Math.pow(Math.random(),2)));
        return null;
    }

    protected ArrayList<String> getAllToStudy() {
        ArrayList<String> res=new ArrayList<String>();
        Cursor cur;

        cur = db.rawQuery("SELECT hanzi, 0 FROM char_pinyin WHERE hanzi NOT IN (SELECT hanzi FROM leitner) " +
                "UNION " +
                "SELECT hanzi, " +
                "CASE box WHEN 0 THEN mod_time WHEN 1 THEN mod_time+? WHEN 2 THEN mod_time+? WHEN 3 THEN mod_time+? WHEN 4 THEN mod_time+? END AS expiring_time " +
                "FROM leitner WHERE (box=0) OR (box=1 AND mod_time<?) OR (box=2 AND mod_time<?) " +
                "OR (box=3 AND mod_time<?) OR (box=4 AND mod_time<?) ORDER BY expiring_time ASC",
                new String[] {Long.toString(getExpiryTime(1)), Long.toString(getExpiryTime(2)), Long.toString(getExpiryTime(3)), Long.toString(getExpiryTime(4)),
                        Long.toString(System.currentTimeMillis() - getExpiryTime(1)),
                        Long.toString(System.currentTimeMillis() - getExpiryTime(2)),
                        Long.toString(System.currentTimeMillis() - getExpiryTime(3)),
                        Long.toString(System.currentTimeMillis() - getExpiryTime(4))});
        if (cur.moveToFirst()) {
            for (int i=0; i<cur.getCount(); i++) {
                res.add(cur.getString(cur.getColumnIndex("hanzi")));
                cur.moveToNext();
            }
        }
        cur.close();
        
        return res;

    }

    public void getMoreStudy(int how_many) {
        ArrayList<String> chars = getMostAdvancedExpiring(how_many);
        for (String ch:chars)
            setExpired(ch);
    }
    /*
     *
     *  Combining lesson database management with Leitner system
     *
     *
     */
    public String getNextFilteredToStudy() {
        ArrayList<String> all = getAllFilteredToStudy();
        if (all.size() > 0)
            return all.get((int) (all.size() * Math.pow(Math.random(),2)));
        return null;
    }

    public ArrayList<String> getAllFilteredToStudy() {
        ArrayList<String> res=new ArrayList<String>();
        Cursor cur;

        if ((lesson_filter.size() > 0) && (lesson_filter_enabled)) {
            cur = db.rawQuery("SELECT char_pinyin.hanzi AS hanzi, 0 " +
                    "FROM (SELECT eng_id FROM lesson_word " + where_filter + ") AS lesson_word " +
                    "JOIN eword ON eword.eng_id=lesson_word.eng_id " +
                    "JOIN cword ON eword.cword_id=cword.cword_id " +
                    "JOIN cword_segment ON cword_segment.cword_id=cword.cword_id " +
                    "JOIN char_pinyin ON char_pinyin.char_pinyin_id=cword_segment.char_pinyin_id " +
                    "WHERE hanzi NOT IN (SELECT hanzi FROM leitner) " +
                    "UNION " +
                    "SELECT char_pinyin.hanzi AS hanzi, " +
                    "CASE leitner.box WHEN 0 THEN leitner.mod_time WHEN 1 THEN leitner.mod_time+? WHEN 2 THEN leitner.mod_time+? " +
                    "WHEN 3 THEN leitner.mod_time+? WHEN 4 THEN leitner.mod_time+? END AS expiring_time " +
                    "FROM (SELECT eng_id FROM lesson_word " + where_filter + ") AS lesson_word " +
                    "JOIN eword ON eword.eng_id=lesson_word.eng_id " +
                    "JOIN cword ON eword.cword_id=cword.cword_id " +
                    "JOIN cword_segment ON cword_segment.cword_id=cword.cword_id " +
                    "JOIN char_pinyin ON char_pinyin.char_pinyin_id=cword_segment.char_pinyin_id " +
                    "JOIN leitner ON leitner.hanzi=char_pinyin.hanzi " +
                    "WHERE leitner.box=0 OR (leitner.box=1 AND leitner.mod_time<?) " +
                    "OR (leitner.box=2 AND leitner.mod_time<?) OR (leitner.box=3 AND leitner.mod_time<?) " +
                    "OR (leitner.box=4 AND leitner.mod_time<?)  ORDER BY expiring_time DESC",
                    new String[] {Long.toString(getExpiryTime(1)), Long.toString(getExpiryTime(2)), Long.toString(getExpiryTime(3)), Long.toString(getExpiryTime(4)),
                            Long.toString(System.currentTimeMillis() - getExpiryTime(1)),
                            Long.toString(System.currentTimeMillis() - getExpiryTime(2)),
                            Long.toString(System.currentTimeMillis() - getExpiryTime(3)),
                            Long.toString(System.currentTimeMillis() - getExpiryTime(4))});
            if (cur.moveToFirst()) {
                for (int i=0; i<cur.getCount(); i++) {
                    res.add(cur.getString(cur.getColumnIndex("hanzi")));
                    cur.moveToNext();
                }
            }
            cur.close();
            return res;
        }
        else {
            return getAllToStudy();
        }
    }
}
