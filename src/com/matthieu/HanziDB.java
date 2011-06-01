package com.matthieu;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.io.StringWriter;
import java.lang.ref.Reference;
import java.nio.DoubleBuffer;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * User: matthieu
 * Date: 5/20/11
 */
public class HanziDB extends SQLiteOpenHelper {
    private static final String TAG = "HanziDB";
    private static final String DEFAULT_DATABASE_NAME = "HANZI_DB";
    private static final int DATABASE_VERSION = 1;

    protected SQLiteDatabase db;

    public HanziDB(Context context) {
        super(context, DEFAULT_DATABASE_NAME, null, DATABASE_VERSION);
        db = this.getWritableDatabase();
    }

    public HanziDB(Context context, String db_name) {
        super(context, db_name, null, DATABASE_VERSION);
        db = this.getWritableDatabase();
    }

    @Override
    public void close() {
        db.close();
        super.close();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.v(TAG, "onCreate HanziDB...");
        db.execSQL("DROP TABLE IF EXISTS eword;");
        db.execSQL("DROP TABLE IF EXISTS cword_segment;");
        db.execSQL("DROP TABLE IF EXISTS cword;");
        db.execSQL("DROP TABLE IF EXISTS char_pinyin;");

        db.execSQL("CREATE TABLE char_pinyin (" +
                "char_pinyin_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                "hanzi VARCHAR(2)," +
                "pinyin VARCHAR(10)," +
                "UNIQUE(hanzi, pinyin));");
        db.execSQL("CREATE TABLE cword (" +
                "cword_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                "chinese VARCHAR(50)," +
                "pinyin VARCHAR(150)," +
                "UNIQUE(chinese,pinyin));");
        db.execSQL("CREATE TABLE cword_segment (" +
                "cword_id INTEGER," +
                "char_pinyin_id INTEGER," +
                "pos INTEGER," +
                "FOREIGN KEY (cword_id) REFERENCES cword(cword_id)," +
                "FOREIGN KEY (char_pinyin_id) REFERENCES char_pinyin(char_pinyin_id)," +
                "UNIQUE(cword_id, pos));");
        db.execSQL("CREATE TABLE eword (" +
                "eng_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                "cword_id INTEGER," +
                "translation VARCHAR(250)," +
                "FOREIGN KEY (cword_id) REFERENCES cword(cword_id)," +
                "UNIQUE(cword_id, translation));");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int i, int i1) {
        Log.v(TAG, "onUpgrade ("+i+","+i1+")");
    }

    public boolean checkDatabase() {
        boolean res = true;

        // check foreign key from cword_segment to cword_id
        Cursor c = db.rawQuery("SELECT DISTINCT cword_id FROM cword_segment WHERE cword_id NOT IN (SELECT cword_id FROM cword);", null);
        if (c.moveToFirst()) {
            Log.w(TAG, "Foreign key cword_id not respected from cword_segment");
            res = false;
            do{
                Log.w(TAG, "bad cword_id " + c.getString(c.getColumnIndex("cword_id")));
            }while(c.moveToNext());
            // TODO : try to fix cword_id foreign key
        }
        c.close();

        // check foreign key from cword_segment to char_pinyin_id
        c = db.rawQuery("SELECT DISTINCT char_pinyin_id FROM cword_segment WHERE char_pinyin_id NOT IN (SELECT char_pinyin_id FROM char_pinyin);", null);
        if (c.moveToFirst()) {
            res = false;
            Log.w(TAG, "Foreign key char_pinyin_id not respected from cword_segment");
            do{
                Log.w(TAG, "bad char_pinyin_id " + c.getString(c.getColumnIndex("char_pinyin_id")));
            }while(c.moveToNext());
            // TODO : try to fix char_pinyin_id foreign key
        }
        c.close();

        // check foreign key from eword to cword_id
        c = db.rawQuery("SELECT DISTINCT cword_id FROM eword WHERE cword_id NOT IN (SELECT cword_id FROM cword);", null);
        if (c.moveToFirst()) {
            res = false;
            Log.w(TAG, "Foreign key cword_id not respected from eword");
            do{
                Log.w(TAG, "bad cword_id " + c.getString(c.getColumnIndex("cword_id")));
            }while(c.moveToNext());
            // TODO : try to fix eword foreign key
        }
        c.close();

        // check chinese and pinyin fields in cword
        c= db.rawQuery("SELECT cword_id, chinese, pinyin, GROUP_CONCAT(t1_chinese,'') AS t_chinese, GROUP_CONCAT(t1_pinyin,'') AS t_pinyin FROM " +
                            "(SELECT cw.cword_id AS cword_id,  cw.chinese AS chinese, cw.pinyin AS pinyin, " +
                                "cp.hanzi AS t1_chinese, cp.pinyin AS t1_pinyin "+
                            "FROM cword_segment AS cs "+
                                "JOIN cword AS cw ON cw.cword_id=cs.cword_id "+
                                "JOIN char_pinyin AS cp ON cp.char_pinyin_id=cs.char_pinyin_id "+
                            "ORDER BY cs.pos ASC) "+
                        "GROUP BY cword_id;", null);
        if (c.moveToFirst()) {
            do{
                int cword_id = c.getInt(c.getColumnIndex("cword_id"));
                String pinyin = c.getString(c.getColumnIndex("pinyin"));
                String t_pinyin = c.getString(c.getColumnIndex("t_pinyin"));
                if (!pinyin.equals(t_pinyin)) {
                    res = false;
                    Log.w(TAG, "Wrong pinyin formwation with cword_id " + cword_id+ " : " + pinyin + " != " + t_pinyin);
                }
                String chinese = c.getString(c.getColumnIndex("chinese"));
                String t_chinese = c.getString(c.getColumnIndex("t_chinese"));
                if (!chinese.equals(t_chinese)) {
                    res = false;
                    Log.w(TAG, "Wrong pinyin formwation with cword_id " + cword_id + " : " + chinese + " != " + t_chinese);
                }
            }while(c.moveToNext());
            // TODO : try to fix chinese and pinyin content in cword
        }
        c.close();

        // check for unique char/pinyin pairs in char_pinyin
        c = db.rawQuery("SELECT COUNT(char_pinyin_id) AS count FROM char_pinyin GROUP BY hanzi, pinyin " +
                        "ORDER BY count DESC;", null);
        if (c.moveToFirst()) {
            do{
                if (c.getInt(c.getColumnIndex("count")) != 1) {
                    res = false;
                    Log.w(TAG, "hanzi, pinyin not unique in char_pinyin table");
                }
            }while(c.moveToNext());
            // TODO : try to fix duplicate char_pinyin
        }
        c.close();

        // check for unique chinese/pinyin in cword
        c = db.rawQuery("SELECT COUNT(cword_id) AS count FROM cword GROUP BY chinese, pinyin " +
                        "ORDER BY count DESC;", null);
        if (c.moveToFirst()) {
            do{
                if (c.getInt(c.getColumnIndex("count")) != 1) {
                    res = false;
                    Log.w(TAG, "chinese, pinyin not unique in cword table");
                }
            }while(c.moveToNext());
            // TODO : try to fix duplicate cword
        }
        c.close();

        // check for unique cword/translation in eword
        c = db.rawQuery("SELECT COUNT(cword_id) AS count FROM eword GROUP BY cword_id, translation " +
                        "ORDER BY count DESC;", null);
        if (c.moveToFirst()) {
            do{
                if (c.getInt(c.getColumnIndex("count")) != 1) {
                    res = false;
                    Log.w(TAG, "cword_id, translation not unique in eword table");
                }
            }while(c.moveToNext());
            // TODO : try to fix duplicate eword
        }
        c.close();

        return res;
    }

    protected ArrayList<Integer> getWordId(String chinese) {
        ArrayList<Integer> res = new ArrayList<Integer>();
        Cursor cur = db.rawQuery("SELECT * FROM cword WHERE chinese=?;", new String[] {chinese});
        if (cur.moveToFirst()) {
            do{
                //Log.d(TAG, "got record " + cur.getInt(cur.getColumnIndex("cword_id")) + " for word " + chinese);
                res.add(cur.getInt(cur.getColumnIndex("cword_id")));
            }while(cur.moveToNext());
        }
        else {
            Log.d(TAG, "no record found for word "+chinese);
        }
        cur.close();
        return res;
    }

    protected ArrayList<Integer> getWordId(String chinese, String pinyin) {
        ArrayList<Integer> res = new ArrayList<Integer>();
        Cursor cur = db.rawQuery("SELECT * FROM cword WHERE chinese=? AND pinyin=?;", new String[] {chinese, pinyin});
        if (cur.moveToFirst()) {
            do{
                //Log.d(TAG, "got record " + cur.getInt(cur.getColumnIndex("cword_id"))
                //        + " for word " + chinese + "/" + pinyin);
                res.add(cur.getInt(cur.getColumnIndex("cword_id")));
            }while(cur.moveToNext());
        }
        else {
            Log.d(TAG, "no record found for word "+chinese+"/"+pinyin);
        }

        if (res.size() > 1) {
            Log.w(TAG, "Warning, getWordId("+chinese+","+pinyin+") returns more than one id");
        }

        cur.close();
        return res;
    }

    protected Integer addCword(ArrayList<String> chinese, ArrayList<String> pinyin) {
        ArrayList<Integer> cword_id_lst, char_pinyin_id_lst;
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
        db.insertWithOnConflict("cword", null, cv, SQLiteDatabase.CONFLICT_IGNORE);
        
        cword_id_lst = getWordId(chinese_str, pinyin_str);
        cword_id = cword_id_lst.get(0);
        if (cword_id_lst.size() > 1) {
            Log.w(TAG, "Add a cword id, but got " + cword_id_lst.size() + " items");
        }

        for (i = 0; i<chinese.size(); i++) {
            han = chinese.get(i);
            pin = pinyin.get(i);
            char_pinyin_id_lst = getCharPinyinId(han, pin);
            if (char_pinyin_id_lst.size() > 0) {
                char_pinyin_id = char_pinyin_id_lst.get(0);
            }
            else {
                char_pinyin_id = addCharPinyin(han, pin);
            }
            cv.clear();
            cv.put("cword_id", cword_id);
            cv.put("char_pinyin_id", char_pinyin_id);
            cv.put("pos", i);
            db.insertWithOnConflict("cword_segment", null, cv, SQLiteDatabase.CONFLICT_IGNORE);
        }

        return cword_id;
    }

    protected ArrayList<Integer> getCharPinyinId(String hanzi, String pinyin) {
        ArrayList<Integer> res = new ArrayList<Integer>();
        Cursor cur = db.rawQuery("SELECT * FROM char_pinyin WHERE hanzi=? AND pinyin=?;", new String[] {hanzi, pinyin});
        if (cur.moveToFirst()) {
            do{
                //Log.d(TAG, "got record "+cur.getInt(cur.getColumnIndex("char_pinyin_id")) +
                //        " for character " + hanzi + "/" + pinyin);
                res.add(cur.getInt(cur.getColumnIndex("char_pinyin_id")));
            }while(cur.moveToNext());
        }
        else {
            Log.d(TAG, "no record found for character "+hanzi+"/"+pinyin);
        }

        if (res.size() > 1) {
            Log.w(TAG, "Warning, getCharPinyinId("+hanzi+","+pinyin+") returns more than one id");
        }

        cur.close();
        return res;
    }

    protected ArrayList<Integer> getCharPinyinId(String hanzi) {
        ArrayList<Integer> res = new ArrayList<Integer>();
        Cursor cur = db.rawQuery("SELECT * FROM char_pinyin WHERE hanzi=?;", new String[] {hanzi});
        if (cur.moveToFirst()) {
            do{
                //Log.d(TAG, "got record " + cur.getInt(cur.getColumnIndex("char_pinyin_id")) + " for character " + hanzi);
                res.add(cur.getInt(cur.getColumnIndex("char_pinyin_id")));
            }while(cur.moveToNext());
        }
        else {
            Log.d(TAG, "no record found for character "+hanzi);
        }

        cur.close();
        return res;
    }

    protected Integer addCharPinyin(String hanzi, String pinyin) {
        ArrayList<Integer> res;

        ContentValues cv = new ContentValues();
        cv.put("hanzi", hanzi);
        cv.put("pinyin", pinyin);
        db.insertWithOnConflict("char_pinyin", null, cv, SQLiteDatabase.CONFLICT_IGNORE);

        res = getCharPinyinId(hanzi, pinyin);
        if (res.size() != 1) {
            Log.w(TAG, "Add a char_pinyin id, but got " + res.size() + " items");
        }
        return res.get(0);
    }

    protected ArrayList<String> getTranslations(int cword_id) {
        ArrayList<String> res = new ArrayList<String>();
        Cursor cur = db.rawQuery("SELECT * FROM eword WHERE cword_id=?;", new String[] {Integer.toString(cword_id)});
        if (cur.moveToFirst()) {
            do{
                res.add(cur.getString(cur.getColumnIndex("translation")));
                //Log.d(TAG, "got translation " + cur.getString(cur.getColumnIndex("translation")));
            }while(cur.moveToNext());
        }

        cur.close();
        return res;
    }

    public ArrayList<String> getTranslations(String chinese) {
        ArrayList<String> res = new ArrayList<String>();
        ArrayList<Integer> cword_ids = getWordId(chinese);
        for (int id : cword_ids) {
            res.addAll(getTranslations(id));
        }
        return res;
    }

    public ArrayList<String> getTranslations(String chinese, String pinyin) {
        ArrayList<String> res = new ArrayList<String>();
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
                "WHERE translation=?;", new String[] {english});
        if (cur.moveToFirst()) {
            do{
                res.add(new Vocabulary
                        (cur.getString(cur.getColumnIndex("chinese")), cur.getString(cur.getColumnIndex("pinyin")),english));
            }while(cur.moveToNext());
        }
        else {
            Log.d(TAG, "no record found for word "+english);
        }

        cur.close();
        return res;
    }

    protected ArrayList<Integer> getCwordWithChar(int char_pinyin_id) {
        ArrayList<Integer> res = new ArrayList<Integer>();
        Cursor cur = db.rawQuery("SELECT cword_id " +
                "FROM cword_segment " +
                "WHERE char_pinyin_id=?;", new String[] {Integer.toString(char_pinyin_id)});
        if (cur.moveToFirst()) {
            do{
                res.add(cur.getInt(cur.getColumnIndex("cword_id")));
            }while(cur.moveToNext());
        }
        cur.close();
        return res;
    }

    protected void delete_char_pinyin(int char_pinyin_id) {
        db.delete("char_pinyin", "char_pinyin=?",new String[] {Integer.toString(char_pinyin_id)});
        //db.execSQL("DELETE FROM char_pinyin WHERE char_pinyin_id=?", new String[] {Integer.toString(char_pinyin_id)});
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
            ArrayList<String> trans = getTranslations(cword_id);
            if (!trans.contains(english))
                addVocabulary(english, cword_id);
        }

        return cword_id;
    }

    protected void addVocabulary(String english, int cword_id) {
        ContentValues cv = new ContentValues();
        cv.put("cword_id", cword_id);
        cv.put("translation", english);
        db.insertWithOnConflict("eword", null, cv, SQLiteDatabase.CONFLICT_IGNORE);
    }

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

    public void deleteCword(int cword_id) {
        ArrayList<Integer> char_pinyin_id = new ArrayList<Integer>();

        Cursor cur = db.rawQuery("SELECT char_pinyin_id FROM cword_segment WHERE cword_id=?", new String[] {Integer.toString(cword_id)});
        if (cur.moveToFirst()) {
            do{
                char_pinyin_id.add(cur.getInt(cur.getColumnIndex("char_pinyin_id")));
            }while(cur.moveToNext());
        }
        db.delete("cword", "cword_id=?", new String[] {Integer.toString(cword_id)});
        //db.execSQL("DELETE FROM cword WHERE cword_id=?", new String[] {Integer.toString(cword_id)});
        db.delete("cword_segment", "cword_id=?", new String[] {Integer.toString(cword_id)});
        //db.execSQL("DELETE FROM cword_segment WHERE cword_id=?", new String[] {Integer.toString(cword_id)});

        for (int cpid : char_pinyin_id) {
            if (getCwordWithChar(cpid).size() == 0) {
                delete_char_pinyin(cpid);
            }

        }

        cur.close();
    }

    public void checkIsolatedCword(int cword_id) {
        if (getTranslations(cword_id).isEmpty()) {
            deleteCword(cword_id);
        }
    }

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
            db.delete("eword", "cword_id=?", new String[] {Integer.toString(cid)} );
            //db.execSQL("DELETE FROM eword WHERE cword_id=?", new String[] {Integer.toString(cid)} );
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

            db.delete("eword", "eng_id=?", new String[] {Integer.toString(eng_id)} );
            //db.execSQL("DELETE FROM eword WHERE eng_id=?", new String[] {Integer.toString(eng_id)} );
            checkIsolatedCword(cword_id);
        }
        cur.close();
    }

}
