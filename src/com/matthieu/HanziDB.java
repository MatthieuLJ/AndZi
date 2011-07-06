package com.matthieu;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * User: matthieu
 * Date: 5/20/11
 */
public class HanziDB extends SQLiteOpenHelper {
    private static final String TAG = "HanziDB";
    private static final String DEFAULT_DATABASE_NAME = "HANZI_DB";
    private static final int DATABASE_VERSION = 1;

    protected Context appContext;
    protected SQLiteDatabase db;

    public HanziDB(Context context) {
        super(context, DEFAULT_DATABASE_NAME, null, DATABASE_VERSION);
        db = this.getWritableDatabase();
        appContext = context;
    }

    public HanziDB(Context context, String db_name) {
        super(context, db_name, null, DATABASE_VERSION);
        db = this.getWritableDatabase();
        appContext = context;
    }

    public String getDatabasePath() {
        return db.getPath();
    }

    ////////////////////////////////////////
    // Database construction and structure
    ////////////////////////////////////////
    @Override
    public void close() {
        db.close();
        super.close();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.v(TAG, "onCreate HanziDB...");

        // basics
        db.execSQL("DROP TABLE IF EXISTS eword;");
        db.execSQL("DROP TABLE IF EXISTS cword_segment;");
        db.execSQL("DROP TABLE IF EXISTS cword;");
        db.execSQL("DROP TABLE IF EXISTS char_pinyin;");

        // lesson stuff
        db.execSQL("DROP TABLE IF EXISTS lesson_word;");
        db.execSQL("DROP TABLE IF EXISTS lessons;");

        // leitner stuff
        db.execSQL("DROP TABLE IF EXISTS leitner;");

        // basics
        db.execSQL("CREATE TABLE char_pinyin (" +
                "char_pinyin_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                "hanzi VARCHAR(2) NOT NULL," +
                "pinyin VARCHAR(10) NOT NULL," +
                "UNIQUE(hanzi, pinyin));");
        db.execSQL("CREATE TABLE cword (" +
                "cword_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                "chinese VARCHAR(50) NOT NULL," +
                "pinyin VARCHAR(150) NOT NULL," +
                "UNIQUE(chinese,pinyin));");
        db.execSQL("CREATE TABLE cword_segment (" +
                "cword_id INTEGER NOT NULL," +
                "char_pinyin_id INTEGER NOT NULL," +
                "pos INTEGER NOT NULL," +
                "FOREIGN KEY (cword_id) REFERENCES cword(cword_id)," +
                "FOREIGN KEY (char_pinyin_id) REFERENCES char_pinyin(char_pinyin_id)," +
                "UNIQUE(cword_id, pos));");
        db.execSQL("CREATE TABLE eword (" +
                "eng_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                "cword_id INTEGER NOT NULL," +
                "translation VARCHAR(250) NOT NULL," +
                "FOREIGN KEY (cword_id) REFERENCES cword(cword_id)," +
                "UNIQUE(cword_id, translation));");

        // lesson stuff
        db.execSQL("CREATE TABLE lessons (" +
                "lesson_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                "name VARCHAR(64) NOT NULL," +
                "UNIQUE(name));");
        db.execSQL("CREATE TABLE lesson_word (" +
                "eng_id INTEGER NOT NULL," +
                "lesson_id INTEGER NOT NULL," +
                "FOREIGN KEY (eng_id) REFERENCES eword(eng_id)," +
                "FOREIGN KEY (lesson_id) REFERENCES lessons(lesson_id)," +
                "UNIQUE(eng_id,lesson_id));");

        // leitner stuff
        db.execSQL("CREATE TABLE leitner (" +
                        "hanzi VARCHAR(2) PRIMARY KEY NOT NULL," +
                        "box INTEGER NOT NULL," +
                        "mod_time INTEGER NOT NULL);");

    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int i, int i1) {
        Log.v(TAG, "onUpgrade ("+i+","+i1+")");
    }

    public boolean checkDatabase() {
        boolean res = true;

        Log.v(TAG, "checkDatabase");

        //
        // Basics
        //

        // check foreign key from cword_segment to cword_id
        Cursor c = db.rawQuery("SELECT DISTINCT cword_id FROM cword_segment WHERE cword_id NOT IN (SELECT cword_id FROM cword);", null);
        if (c.moveToFirst()) {
            Log.w(TAG, "Foreign key cword_id not respected from cword_segment");
            res = false;
            do{
                Log.w(TAG, "bad cword_id " + c.getString(c.getColumnIndex("cword_id")));
            }while(c.moveToNext());
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
        }
        c.close();

        //
        // Lessons
        //

        // check foreign key from lesson_word to lessons
        c = db.rawQuery("SELECT DISTINCT lesson_id FROM lesson_word WHERE lesson_id NOT IN (SELECT lesson_id FROM lessons);", null);
        if (c.moveToFirst()) {
            Log.w(TAG, "Foreign key lesson_id not respected from lesson_word");
            res = false;
            do{
                Log.w(TAG, "bad lesson_id " + c.getString(c.getColumnIndex("lesson_id")));
            }while(c.moveToNext());
        }
        c.close();

        // check foreign key from lesson_word to eword
        c = db.rawQuery("SELECT DISTINCT eng_id FROM lesson_word WHERE eng_id NOT IN (SELECT eng_id FROM eword);", null);
        if (c.moveToFirst()) {
            Log.w(TAG, "Foreign key eng_id not respected from lesson_word");
            res = false;
            do{
                Log.w(TAG, "bad eng_id " + c.getString(c.getColumnIndex("eng_id")));
            }while(c.moveToNext());
        }
        c.close();

        //
        // Leitner
        //

        // check all characters in leitner table are in DB
        c = db.rawQuery("SELECT DISTINCT hanzi FROM leitner WHERE hanzi NOT IN (SELECT hanzi FROM char_pinyin);", null);
        if (c.moveToFirst()) {
            Log.w(TAG, "Character in leitner table is not in database");
            res = false;
            do{
                Log.w(TAG, "bad character " + c.getString(c.getColumnIndex("hanzi")));
            }while(c.moveToNext());
        }
        c.close();

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
            res = matcher.group(1) + matcher.group(2);
            matcher = pattern.matcher(res);
        }
        return res.toLowerCase().trim();
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
                "WHERE translation=?;", new String[] {english});
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

    public static ArrayList<String> combineListOfTranslations(ArrayList<Vocabulary> vocs) {
        ArrayList<String> res=new ArrayList<String>();
        for (Vocabulary voc : vocs) {
            res.addAll(voc.english);
        }
        return res;
    }

    protected ArrayList<Integer> getWordIdWithPinyin(String hanzi) {
        ArrayList<Integer> res = new ArrayList<Integer>();
        Cursor cur = db.rawQuery("SELECT cword.cword_id AS cword_id " +
                "FROM cword " +
                "JOIN cword_segment ON cword.cword_id=cword_segment.cword_id " +
                "JOIN char_pinyin ON char_pinyin.char_pinyin_id=cword_segment.char_pinyin_id " +
                "WHERE char_pinyin.hanzi=?;", new String[] {hanzi});
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
        Cursor cur = db.rawQuery("SELECT cword.cword_id AS cword_id " +
                "FROM cword " +
                "JOIN cword_segment ON cword.cword_id=cword_segment.cword_id " +
                "JOIN char_pinyin ON char_pinyin.char_pinyin_id=cword_segment.char_pinyin_id " +
                "WHERE char_pinyin.hanzi=? AND char_pinyin.pinyin=?;", new String[] {hanzi, pinyin});
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
        Cursor cur = db.rawQuery("SELECT * FROM cword WHERE chinese=?;", new String[] {chinese});
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
        Cursor cur = db.rawQuery("SELECT * FROM cword WHERE chinese=? AND pinyin=?;", new String[] {chinese, pinyin});
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
        Cursor cur = db.rawQuery("SELECT * FROM char_pinyin WHERE hanzi=? AND pinyin=?;", new String[] {hanzi, pinyin});
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
        Cursor cur = db.rawQuery("SELECT * FROM char_pinyin WHERE hanzi=?;", new String[] {hanzi});
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
                "WHERE cword.cword_id=? ORDER BY cword.cword_id ASC;", new String[] {Integer.toString(cword_id)});

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
            db.delete("eword", "cword_id=?", new String[] {Integer.toString(cid)} );
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

        cur = db.rawQuery("SELECT * FROM char_pinyin WHERE hanzi=?;", new String[] {hanzi});
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
        Cursor cur = db.rawQuery("SELECT cword_id FROM cword;", null);
        res= cur.getCount();

        cur.close();
        return res;
    }

    public int getNumChars() {
        int res;
        Cursor cur = db.rawQuery("SELECT hanzi FROM char_pinyin GROUP BY hanzi;", null);
        res= cur.getCount();

        cur.close();
        return res;
    }

    ////////////////////////////////////////
    // Merge Database
    ////////////////////////////////////////
    public void mergeDB(HanziDB other_db) {
        ArrayList<String> lessons = other_db.getLessons();
        lessons.add("");
        ArrayList<Vocabulary> vocabulary = other_db.getVocabularyInLessons(lessons);
        for (Vocabulary v : vocabulary) {
            addVocabulary(v);
        }
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
    }

    public ArrayList<String> getLessons() {
        ArrayList<String> res = new ArrayList<String>();

        Cursor cur = db.rawQuery("SELECT name FROM lessons", null);
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
        Cursor cur = db.rawQuery("SELECT * FROM lessons WHERE name=?;", new String[] {name});
        if (cur.moveToFirst()) {
            res = cur.getInt(cur.getColumnIndex("lesson_id"));
        }
        cur.close();
        return res;

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
        for (String english : voc.english) {
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
        for (String english : voc.english) {
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
        String whereQuery = buildWhereQuery(lessons);

        Cursor cur = db.rawQuery("SELECT e.translation AS translation, " +
                                 "c.chinese AS chinese, " +
                                 "c.pinyin AS pinyin " +
                                 "FROM (SELECT * FROM lesson_word " + whereQuery + ") AS lesson_word " +
                                 " JOIN eword AS e ON lesson_word.eng_id=e.eng_id " +
                                 " JOIN cword AS c ON c.cword_id=e.cword_id "
                                 ,null);

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

    public void setLessonsToFilter(ArrayList<String> lessons) {
        lesson_filter = lessons;
        where_filter = buildWhereQuery(lessons);
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
                "WHERE cword.chinese=?;", new String[] {chinese});
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
                "WHERE cword.chinese=? AND cword.pinyin=?;", new String[] {chinese, pinyin});
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
                "WHERE cword.cword_id=? ORDER BY cword.cword_id ASC;", new String[] {Integer.toString(cword_id)});

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

    private static final long two_hours = (long) 2 * 60 * 60 * 1000;
    private static final long two_days = (long) 2 * 24 * 60 * 60 * 1000;
    private static final long two_weeks = (long) 2 * 7 * 24 * 60 * 60 * 1000;
    private static final long two_months = (long) 2 * 30 * 24 * 60 * 60 * 1000;

    private static final int MAX_BOX=4;

    ////////////////////////////////////////
    // Get information
    ////////////////////////////////////////
    public int getNumCharsInBox(int box) {
        int res;

        if ((box < 0) || (box > MAX_BOX))
            return 0;

        Cursor cur = db.rawQuery("SELECT hanzi FROM leitner WHERE box=?;", new String[] {Integer.toString(box)});
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

        Cursor cur = db.rawQuery("SELECT hanzi FROM leitner WHERE box=? AND mod_time<?;",
                new String[] {Integer.toString(box), Long.toString(System.currentTimeMillis()-add_time)});
        res= cur.getCount();

        cur.close();
        return res;
    }

    public int getBoxForChar(String hanzi) {
        int res;

        Cursor cur = db.rawQuery("SELECT box FROM leitner WHERE hanzi=?;",
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

    private long getExpiryTime (int box) {
        switch (box) {
            case 0 :
                return 0;
            case 1:
                return two_hours;
            case 2:
                return two_days;
            case 3:
                return two_weeks;
            case 4:
                return two_months;
            default:
                return 0;
        }
    }

    ////////////////////////////////////////
    // Update data
    ////////////////////////////////////////
    public void resetTime(String hanzi) {

        Cursor cur = db.rawQuery("SELECT box FROM leitner WHERE hanzi=?;",
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
    }

    public void betterBox(String hanzi) {
        int current_box = getBoxForChar(hanzi);
        current_box ++;
        if (current_box > MAX_BOX) current_box = MAX_BOX;

        ContentValues cv = new ContentValues();
        cv.put("box", current_box);
        cv.put("mod_time", System.currentTimeMillis());
        Cursor cur = db.rawQuery("SELECT box FROM leitner WHERE hanzi=?;",
                new String[] {hanzi});
        if (cur.getCount() == 0) {
            cv.put("hanzi", hanzi);
            db.insert("leitner", null, cv);
        }
        else {
            db.update("leitner", cv, "hanzi=?", new String[] {hanzi});
        }
    }

    public void restartStudy(String hanzi) {
        ContentValues cv = new ContentValues();
        cv.put("box", 0);
        cv.put("mod_time", System.currentTimeMillis());
        Cursor cur = db.rawQuery("SELECT box FROM leitner WHERE hanzi=?;",
                new String[] {hanzi});
        if (cur.getCount() == 0) {
            cv.put("hanzi", hanzi);
            db.insert("leitner", null, cv);
        }
        else {
            db.update("leitner", cv, "hanzi=?", new String[] {hanzi});
        }
    }

    ////////////////////////////////////////
    // Use to find what character to study
    ////////////////////////////////////////
    public String getNextToStudy() {
        String res;
        for (int i = MAX_BOX; i >= 0; i--) {
            res = getNextToStudy(i);
            if (res != null) {
                return res;
            }
        }
        return null;
    }

    public String getNextToStudy(int box) {
        String res = null;
        Cursor cur = db.rawQuery("SELECT hanzi FROM leitner " +
                "WHERE box=? AND mod_time<? " +
                "ORDER BY mod_time ASC " +
                "LIMIT 1;",
                new String[] {Integer.toString(box), Long.toString(System.currentTimeMillis() - getExpiryTime(box))});
        if (cur.moveToFirst()) {
            res = cur.getString(cur.getColumnIndex("hanzi"));
        }
        cur.close();

        if ((box == 0) && (res == null)) {
            cur = db.rawQuery("SELECT hanzi FROM char_pinyin WHERE hanzi NOT IN (SELECT hanzi FROM leitner) LIMIT 1", null);
            if (cur.moveToFirst()) {
                res = cur.getString(cur.getColumnIndex("hanzi"));
            }
        }
        cur.close();

        return res;
    }
}
