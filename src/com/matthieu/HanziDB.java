/******************************
 * Chinese vocabulary database
 */
package com.matthieu;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.*;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;
import com.matthieu.widget.DBCheckProgressHandler;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class HanziDB extends SQLiteOpenHelper {
    private static final String TAG = "HanziDB";
    private static final int DATABASE_VERSION = 5;

    protected AndZiApplication app;
    private SQLiteDatabase db;

    public HanziDB(AndZiApplication application, String db_name) {
        super(application.getApplicationContext(), db_name, null, DATABASE_VERSION);
        app = application;
        db = this.getWritableDatabase();
        db.execSQL("PRAGMA case_sensitive_like=ON");
        db.acquireReference();
    }

    ////////////////////////////////////////
    // Database construction and structure
    ////////////////////////////////////////

    @Override
    public void onCreate(SQLiteDatabase database) {
        Log.v(TAG, "onCreate HanziDB...");

        // basics
        database.execSQL("DROP TABLE IF EXISTS eword");
        database.execSQL("DROP TABLE IF EXISTS cword_segment");
        database.execSQL("DROP TABLE IF EXISTS cword");
        database.execSQL("DROP TABLE IF EXISTS char_pinyin");

        // lesson stuff
        database.execSQL("DROP TABLE IF EXISTS lesson_word");
        database.execSQL("DROP TABLE IF EXISTS lessons");

        // leitner stuff
        database.execSQL("DROP TABLE IF EXISTS leitner");

        // basics
        database.execSQL("CREATE TABLE char_pinyin (" +
                "char_pinyin_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                "hanzi VARCHAR(2) NOT NULL," +
                "pinyin VARCHAR(10) NOT NULL," +
                "UNIQUE(hanzi, pinyin))");
        database.execSQL("CREATE INDEX char_pinyin_id ON char_pinyin(char_pinyin_id)");
        database.execSQL("CREATE INDEX char_pinyin_hanzi ON char_pinyin(hanzi)");
        database.execSQL("CREATE INDEX char_pinyin_pinyin ON char_pinyin(pinyin)");
        database.execSQL("CREATE TABLE cword (" +
                "cword_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                "chinese VARCHAR(50) NOT NULL," +
                "pinyin VARCHAR(150) NOT NULL," +
                "length INTEGER DEFAULT 0," +
                "UNIQUE(chinese,pinyin))");
        database.execSQL("CREATE INDEX cword_id ON cword(cword_id)");
        database.execSQL("CREATE INDEX cword_chinese ON cword(chinese)");
        database.execSQL("CREATE INDEX cword_pinyin ON cword(pinyin)");
        database.execSQL("CREATE TABLE cword_segment (" +
                "cword_id INTEGER NOT NULL," +
                "char_pinyin_id INTEGER NOT NULL," +
                "pos INTEGER NOT NULL," +
                "FOREIGN KEY (cword_id) REFERENCES cword(cword_id)," +
                "FOREIGN KEY (char_pinyin_id) REFERENCES char_pinyin(char_pinyin_id)," +
                "UNIQUE(cword_id, pos))");
        database.execSQL("CREATE INDEX cword_segment_cword_id ON cword_segment(cword_id)");
        database.execSQL("CREATE INDEX cword_segment_char_pinyin_id ON cword_segment(char_pinyin_id)");
        database.execSQL("CREATE INDEX cword_segment_pos ON cword_segment(pos)");
        database.execSQL("CREATE TABLE eword (" +
                "eng_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                "cword_id INTEGER NOT NULL," +
                "translation VARCHAR(250) NOT NULL," +
                "FOREIGN KEY (cword_id) REFERENCES cword(cword_id)," +
                "UNIQUE(cword_id, translation))");
        database.execSQL("CREATE INDEX eword_translation ON eword(translation)");

        // lesson stuff
        database.execSQL("CREATE TABLE lessons (" +
                "lesson_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                "list_order INTEGER DEFAULT 0," +
                "name VARCHAR(64) NOT NULL UNIQUE)");
        database.execSQL("CREATE INDEX lessons_name ON lessons(name)");
        database.execSQL("CREATE TABLE lesson_word (" +
                "eng_id INTEGER NOT NULL," +
                "lesson_id INTEGER NOT NULL," +
                "FOREIGN KEY (eng_id) REFERENCES eword(eng_id)," +
                "FOREIGN KEY (lesson_id) REFERENCES lessons(lesson_id)," +
                "UNIQUE(eng_id,lesson_id))");
        database.execSQL("CREATE INDEX lesson_word_eng_id ON lesson_word(eng_id)");
        database.execSQL("CREATE INDEX lesson_word_lesson_id ON lesson_word(lesson_id)");

        // leitner stuff
        database.execSQL("CREATE TABLE leitner (" +
                "hanzi VARCHAR(50) PRIMARY KEY NOT NULL," +
                "box INTEGER NOT NULL," +
                "exp_time INTEGER NOT NULL," +
                "num_tested INTEGER DEFAULT 0," +
                "num_wrong INTEGER DEFAULT 0," +
                "streak INTEGER DEFAULT 0," +
                "date_added INTEGER)");
        database.execSQL("CREATE INDEX leitner_hanzi ON leitner(hanzi)");

        cached_num_chars=0;
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, final int i, final int i1) {
        Log.v(TAG, "onUpgrade ("+i+","+i1+")");

        if (i<2) {
            // first have to manually calculate expiration time
            Cursor cur = db.rawQuery("SELECT hanzi FROM char_pinyin GROUP BY hanzi", null);
            int num_chars= cur.getCount();
            cur.close();

            long box_delay = (long) 6 * 60 * 60 * 1000;

            int multiplicator = Math.max((int) Math.log10(num_chars), 1);
            long expiry1 = (long) (box_delay * Math.pow(2, multiplicator));
            long expiry2 = (long) (box_delay * Math.pow(4, multiplicator));
            long expiry3 = (long) (box_delay * Math.pow(8, multiplicator));
            long expiry4 = (long) (box_delay * Math.pow(16, multiplicator));

            db.execSQL("ALTER TABLE cword ADD COLUMN length INTEGER DEFAULT 0");
            db.execSQL("UPDATE cword SET length=length(cword.chinese)");

            db.execSQL("DROP INDEX IF EXISTS leitner_hanzi");
            db.execSQL("ALTER TABLE leitner RENAME TO old_leitner");
            db.execSQL("CREATE TABLE leitner (" +
                    "hanzi VARCHAR(2) PRIMARY KEY NOT NULL," +
                    "box INTEGER NOT NULL," +
                    "exp_time INTEGER NOT NULL)");
            db.execSQL("CREATE INDEX leitner_hanzi ON leitner(hanzi)");
            db.execSQL("INSERT INTO leitner SELECT hanzi,box,mod_time FROM old_leitner");
            db.execSQL("UPDATE leitner SET exp_time=exp_time + (CASE box WHEN 1 THEN ? WHEN 2 THEN ? WHEN 3 THEN ? WHEN 4 THEN ? ELSE 0 END)",
                    new String[] {
                            Long.toString(expiry1),
                            Long.toString(expiry2),
                            Long.toString(expiry3),
                            Long.toString(expiry4)});
            db.execSQL("DROP TABLE old_leitner");
        }

        if (i<3) {
            //db.execSQL("ALTER TABLE cword ADD COLUMN score INTEGER DEFAULT 0");
        }

        if (i<4) {
            db.execSQL("ALTER TABLE leitner ADD COLUMN num_tested INTEGER DEFAULT 0");
            db.execSQL("ALTER TABLE leitner ADD COLUMN num_wrong INTEGER DEFAULT 0");
            db.execSQL("ALTER TABLE leitner ADD COLUMN streak INTEGER DEFAULT 0");
            db.execSQL("ALTER TABLE leitner ADD COLUMN date_added INTEGER");
        }
        if (i<5) {
            db.execSQL("DROP INDEX IF EXISTS leitner_hanzi");
            db.execSQL("ALTER TABLE leitner RENAME TO old_leitner");
            db.execSQL("CREATE TABLE leitner (" +
                    "hanzi VARCHAR(50) PRIMARY KEY NOT NULL," +
                    "box INTEGER NOT NULL," +
                    "exp_time INTEGER NOT NULL," +
                    "num_tested INTEGER DEFAULT 0," +
                    "num_wrong INTEGER DEFAULT 0," +
                    "streak INTEGER DEFAULT 0," +
                    "date_added INTEGER)");
            db.execSQL("CREATE INDEX leitner_hanzi ON leitner(hanzi)");
            db.execSQL("INSERT INTO leitner SELECT hanzi,box,exp_time,num_tested,num_wrong, streak, date_added FROM old_leitner");
            db.execSQL("DROP TABLE old_leitner");

            if (i>=3) {
                Cursor cur = db.rawQuery("SELECT chinese, score FROM cword WHERE (score>0) AND (length(chinese)>1)", null);
                if (cur.moveToFirst()) {
                    int score_column_index = cur.getColumnIndex("score");
                    int hanzi_column_index = cur.getColumnIndex("chinese");
                    do {
                        String hanzi=cur.getString(hanzi_column_index);
                        int score = Math.min(5,cur.getInt(score_column_index));
                        ContentValues cv = new ContentValues();
                        cv.put("box", score);
                        cv.put("exp_time", System.currentTimeMillis()+getExpiryTime(score));
                        cv.put("hanzi", hanzi);
                        db.insert("leitner", null, cv);
                    } while(cur.moveToNext());
                }
                cur.close();

                cur = db.rawQuery("SELECT COUNT(cword_id) AS count FROM cword", null);
                cur.moveToFirst();
                int num_words= cur.getInt(cur.getColumnIndex("count"));
                cur.close();
                if (num_words < 3000) { // forget it otherwise... it would do some ANR
                    db.beginTransaction();
                    db.execSQL("DROP INDEX IF EXISTS cword_id");
                    db.execSQL("DROP INDEX IF EXISTS cword_chinese");
                    db.execSQL("DROP INDEX IF EXISTS cword_pinyin");
                    db.execSQL("ALTER TABLE cword RENAME TO old_cword");
                    db.execSQL("CREATE TABLE cword (" +
                            "cword_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                            "chinese VARCHAR(50) NOT NULL," +
                            "pinyin VARCHAR(150) NOT NULL," +
                            "length INTEGER DEFAULT 0," +
                            "UNIQUE(chinese,pinyin))");
                    db.execSQL("INSERT INTO cword SELECT cword_id, chinese, pinyin, length FROM old_cword");
                    db.execSQL("CREATE INDEX cword_id ON cword(cword_id)");
                    db.execSQL("CREATE INDEX cword_chinese ON cword(chinese)");
                    db.execSQL("CREATE INDEX cword_pinyin ON cword(pinyin)");
                    db.execSQL("DROP TABLE old_cword");
                    db.endTransaction();
                }
            }
        }
    }

    @Override
    public synchronized void close() {
        db.releaseReference();
        db.close();
        super.close();
    }

    public boolean checkDatabase(final boolean fixme, final DBCheckProgressHandler handler) {
        boolean res = true;

        final int num_steps=15;
        int current_step=0;

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
            current_step++;
            if (handler != null)
                handler.progress((int)(current_step*100/num_steps));

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
            current_step++;
            if (handler != null)
                handler.progress((int)(current_step*100/num_steps));

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
            current_step++;
            if (handler != null)
                handler.progress((int)(current_step*100/num_steps));

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
            current_step++;
            if (handler != null)
                handler.progress((int)(current_step*100/num_steps));

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
            current_step++;
            if (handler != null)
                handler.progress((int)(current_step*100/num_steps));

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
            current_step++;
            if (handler != null)
                handler.progress((int)(current_step*100/num_steps));

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
            current_step++;
            if (handler != null)
                handler.progress((int)(current_step*100/num_steps));

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
            current_step++;
            if (handler != null)
                handler.progress((int)(current_step*100/num_steps));

            // check cword length is correct
            c= db.rawQuery("SELECT cword_id FROM cword WHERE length(chinese) != length", null);
            if (c.moveToFirst()) {
                Log.w(TAG, "Found some words with the wrong size");
                db.execSQL("UPDATE cword SET length = length(chinese)");
            }
            c.close();
            current_step++;
            if (handler != null)
                handler.progress((int)(current_step*100/num_steps));

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
            current_step++;
            if (handler != null)
                handler.progress((int)(current_step*100/num_steps));

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
            current_step++;
            if (handler != null)
                handler.progress((int)(current_step*100/num_steps));

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
            current_step++;
            if (handler != null)
                handler.progress((int)(current_step*100/num_steps));

            //
            // Leitner
            //

            // check all characters in leitner table are in DB
            c = db.rawQuery("SELECT DISTINCT hanzi FROM leitner WHERE length(hanzi)==1 AND hanzi NOT IN (SELECT hanzi FROM char_pinyin)", null);
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
            current_step++;
            if (handler != null)
                handler.progress((int)(current_step*100/num_steps));

            // check all characters in leitner table are in DB
            c = db.rawQuery("SELECT DISTINCT hanzi FROM leitner WHERE length(hanzi)>1 AND hanzi NOT IN (SELECT chinese FROM cword)", null);
            if (c.moveToFirst()) {
                Log.w(TAG, "Word in leitner table is not in database");
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
            current_step++;
            if (handler != null)
                handler.progress((int)(current_step*100/num_steps));

            c = db.rawQuery("SELECT DISTINCT hanzi FROM leitner WHERE num_wrong > num_tested", null);
            if (c.moveToFirst()) {
                Log.w(TAG, "Character in has been more wrong than tested");
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
            current_step++;
            if (handler != null)
                handler.progress((int)(current_step*100/num_steps));
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

    public static void merge_vocabulary(ArrayList<Vocabulary> itemA, ArrayList<Vocabulary> itemB) {
        if ((itemA == null) || (itemB == null))
            return;

        itemA.ensureCapacity(itemA.size()+itemB.size());
        ArrayList<Vocabulary> original_A = (ArrayList<Vocabulary>) itemA.clone();
        for (Vocabulary voc : itemB) {
            boolean found = false;
            for (Vocabulary orig_voc : original_A) {
                if (orig_voc.same_word(voc)) {
                    for (Vocabulary anItemA : itemA) {
                        if (anItemA.same_word(voc)) {
                            for (String en : voc.english)
                                if (!anItemA.english.contains(en))
                                    anItemA.english.add(en);
                            break;
                        }
                    }
                    found=true;
                    break;
                }
            }
            if (!found) {
                itemA.add(voc);
            }
        }
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
    int cached_num_chars=-1;

    public ArrayList<Vocabulary> getTranslations(String chinese) {
        ArrayList<Vocabulary> res = new ArrayList<Vocabulary>();
        Vocabulary to_add=null;

        Cursor cur = db.rawQuery("SELECT * " +
                "FROM eword " +
                "JOIN cword ON eword.cword_id=cword.cword_id " +
                "WHERE cword.chinese=? ORDER BY cword.cword_id", new String[] {chinese});

        int current_id=-1;

        int id_column_index      = cur.getColumnIndex("cword_id");
        int pinyin_column_index  = cur.getColumnIndex("pinyin");
        int english_column_index = cur.getColumnIndex("translation");
        if (cur.moveToFirst()) {
            do{
                int new_id = cur.getInt(id_column_index);
                String pinyin =  cur.getString(pinyin_column_index);
                String english = cur.getString(english_column_index);

                if (new_id != current_id) {
                    if (to_add != null)
                        res.add(to_add);
                    to_add = new Vocabulary(chinese,pinyin, new ArrayList<String>());
                    current_id = new_id;
                }
                assert to_add != null;
                to_add.english.add(english);
            }while(cur.moveToNext());
        }
        if (to_add != null)
            res.add(to_add);

        cur.close();

        return res;
    }

    public Vocabulary getTranslations(String chinese, String pinyin) {
        Vocabulary res = null;

        Cursor cur = db.rawQuery("SELECT * " +
                "FROM eword " +
                "JOIN cword ON eword.cword_id=cword.cword_id " +
                "WHERE cword.chinese=? AND cword.pinyin=? ORDER BY cword.cword_id", new String[] {chinese, pinyin});

        int english_column_index = cur.getColumnIndex("translation");
        if (cur.moveToFirst()) {
            res = new Vocabulary(chinese, pinyin, new ArrayList<String>());
            do{
                String english = cur.getString(english_column_index);

                res.english.add(english);
            }while(cur.moveToNext());
        }

        cur.close();

        return res;
    }

    public Cursor getCTranslations(String english) {
        ArrayList<String> query_arg=new ArrayList<String>();
        String query="SELECT selection.cword_id AS _id, c.chinese AS chinese,c.pinyin AS pinyin, MIN(selection.sort), GROUP_CONCAT(eword.translation,', ') AS english " +
                "FROM ( " +
                "    SELECT DISTINCT cword.cword_id AS cword_id, " +
                "    CASE WHEN eword.translation=? THEN 0 WHEN eword.translation LIKE ? THEN 1 " +
                "    WHEN eword.translation LIKE ? THEN 2 WHEN eword.translation LIKE ? THEN 3 ELSE 4 END AS sort " +
                "    FROM (SELECT * FROM eword WHERE eword.translation LIKE ?) AS eword " +
                "    JOIN cword ON cword.cword_id=eword.cword_id ) AS selection " +
                "JOIN cword AS c ON c.cword_id=selection.cword_id " +
                "JOIN eword ON eword.cword_id=c.cword_id GROUP BY _id ORDER BY selection.sort, c.length, eword.translation";
        query_arg.add(english);
        query_arg.add(english+" %");
        query_arg.add("% "+english);
        query_arg.add("% "+english+" %");
        query_arg.add("%"+english+"%");

        return db.rawQuery(query, query_arg.toArray(new String[query_arg.size()]));
    }

    public Cursor getStartingCTranslations(String english) {
        ArrayList<String> query_arg=new ArrayList<String>();
        String query="SELECT selection.cword_id AS _id, c.chinese AS chinese,c.pinyin AS pinyin, MIN(selection.sort), GROUP_CONCAT(eword.translation,', ') AS english " +
                "FROM ( " +
                "    SELECT DISTINCT cword.cword_id AS cword_id, " +
                "    CASE WHEN eword.translation=? THEN 0 WHEN eword.translation LIKE ? THEN 1 " +
                "    ELSE 2 END AS sort " +
                "    FROM (SELECT * FROM eword WHERE eword.translation LIKE ?) AS eword " +
                "    JOIN cword ON cword.cword_id=eword.cword_id ) AS selection " +
                "JOIN cword AS c ON c.cword_id=selection.cword_id " +
                "JOIN eword ON eword.cword_id=c.cword_id GROUP BY _id ORDER BY selection.sort, c.length, eword.translation";
        query_arg.add(english);
        query_arg.add(english+" %");
        query_arg.add(english+"%");

        return db.rawQuery(query, query_arg.toArray(new String[query_arg.size()]));
    }

    public ArrayList<Vocabulary> getWordsWithPinyin(String hanzi) {
        ArrayList<Vocabulary> res = new ArrayList<Vocabulary>();
        Vocabulary to_add=null;
        Cursor cur = db.rawQuery("SELECT * " +
                "FROM cword_segment " +
                "JOIN char_pinyin ON char_pinyin.char_pinyin_id=cword_segment.char_pinyin_id " +
                "JOIN cword ON cword.cword_id=cword_segment.cword_id " +
                "JOIN eword ON eword.cword_id=cword.cword_id " +
                "WHERE char_pinyin.hanzi=? GROUP BY eword.eng_id ORDER BY cword.cword_id", new String[] {hanzi});
        int current_id=-1;

        int id_column_index      = cur.getColumnIndex("cword_id");
        int chinese_column_index = cur.getColumnIndex("chinese");
        int pinyin_column_index  = cur.getColumnIndex("pinyin");
        int english_column_index = cur.getColumnIndex("translation");
        if (cur.moveToFirst()) {
            do{
                int new_id = cur.getInt(id_column_index);
                String pinyin =  cur.getString(pinyin_column_index);
                String english = cur.getString(english_column_index);
                String chinese = cur.getString(chinese_column_index);

                if (new_id != current_id) {
                    if (to_add != null)
                        res.add(to_add);
                    to_add = new Vocabulary(chinese,pinyin, new ArrayList<String>());
                    current_id = new_id;
                }
                assert to_add != null;
                to_add.english.add(english);
            }while(cur.moveToNext());
        }
        if (to_add != null)
            res.add(to_add);

        cur.close();
        return res;
    }

    public ArrayList<Vocabulary> getWordsWithPinyin(String hanzi, String pinyin) {
        ArrayList<Vocabulary> res = new ArrayList<Vocabulary>();
        Vocabulary to_add=null;
        Cursor cur = db.rawQuery("SELECT * " +
                "FROM cword_segment " +
                "JOIN char_pinyin ON char_pinyin.char_pinyin_id=cword_segment.char_pinyin_id " +
                "JOIN cword ON cword.cword_id=cword_segment.cword_id " +
                "JOIN eword ON eword.cword_id=cword.cword_id " +
                "WHERE char_pinyin.hanzi=? AND char_pinyin.pinyin=? GROUP BY eword.eng_id ORDER BY cword.cword_id", new String[] {hanzi, pinyin});
        int current_id=-1;

        int id_column_index      = cur.getColumnIndex("cword_id");
        int chinese_column_index = cur.getColumnIndex("chinese");
        int pinyin_column_index  = cur.getColumnIndex("pinyin");
        int english_column_index = cur.getColumnIndex("translation");
        if (cur.moveToFirst()) {
            do{
                int new_id = cur.getInt(id_column_index);
                String english = cur.getString(english_column_index);

                if (new_id != current_id) {
                    String w_pinyin=  cur.getString(pinyin_column_index);
                    String chinese = cur.getString(chinese_column_index);
                    if (to_add != null)
                        res.add(to_add);
                    to_add = new Vocabulary(chinese,w_pinyin, new ArrayList<String>());
                    current_id=new_id;
                }
                assert to_add != null;
                to_add.english.add(english);
            }while(cur.moveToNext());
        }
        if (to_add != null)
            res.add(to_add);

        cur.close();
        return res;
    }

    protected int getWordId(String chinese, String pinyin) {
        int res=-1;
        Cursor cur = db.rawQuery("SELECT * FROM cword WHERE chinese=? AND pinyin=?", new String[] {chinese, pinyin});
        if (cur.moveToFirst()) {
            res=cur.getInt(cur.getColumnIndex("cword_id"));
        }

        if (cur.moveToNext()) {
            Log.w(TAG, "Warning, getWordId("+chinese+","+pinyin+") returns more than one id");
        }

        cur.close();
        return res;
    }

    protected int getCharPinyinId(String hanzi, String pinyin) {
        int res;
        Cursor cur = db.rawQuery("SELECT * FROM char_pinyin WHERE hanzi=? AND pinyin=?", new String[] {hanzi, pinyin});
        if (cur.moveToFirst()) {
            //Log.d(TAG, "got record "+cur.getInt(cur.getColumnIndex("char_pinyin_id")) +
            //        " for character " + hanzi + "/" + pinyin);
            res=cur.getInt(cur.getColumnIndex("char_pinyin_id"));
        }
        else {
            //Log.d(TAG, "no record found for character "+hanzi+"/"+pinyin);
            res=-1;
        }

        if (cur.getCount() > 1) {
            Log.w(TAG, "Warning, getCharPinyinId("+hanzi+","+pinyin+") returns more than one id");
        }

        cur.close();
        return res;
    }

    public boolean is_char_toneless_pair(String hanzi) {
        boolean res;
        Cursor cur = db.rawQuery("SELECT hanzi FROM char_pinyin WHERE hanzi=? AND " +
                "(pinyin LIKE '%5' AND hanzi IN (SELECT hanzi FROM char_pinyin GROUP BY hanzi HAVING COUNT(*)=2))",
                new String[] {hanzi});
        res = (cur.getCount() == 1);
        cur.close();
        return res;
    }

    public String get_toned_pinyin_in_pair(String hanzi) {
        String res=null;
        Cursor cur = db.rawQuery("SELECT pinyin FROM char_pinyin WHERE hanzi=? AND " +
                "pinyin NOT LIKE '%5'", new String[] {hanzi});
        if (cur.moveToFirst())
            res = cur.getString(cur.getColumnIndex("pinyin"));
        cur.close();
        return res;
    }

    public ArrayList<String> getPinyinForChar(String hanzi) {
        ArrayList<String> res = new ArrayList<String>();
        Cursor cur = db.rawQuery("SELECT * FROM char_pinyin WHERE hanzi=?", new String[] {hanzi});
        if (cur.moveToFirst()) {
            res.ensureCapacity(cur.getCount());
            do{
                res.add(cur.getString(cur.getColumnIndex("pinyin")));
            }while(cur.moveToNext());
        }

        cur.close();
        return res;
    }

    protected Vocabulary getTranslations(int cword_id) {
        Vocabulary res=null;

        Cursor cur = db.rawQuery("SELECT * " +
                "FROM eword " +
                "JOIN cword ON eword.cword_id=cword.cword_id " +
                "WHERE cword.cword_id=?", new String[] {Integer.toString(cword_id)});

        if (cur.moveToFirst()) {
            String chinese = cur.getString(cur.getColumnIndex("chinese"));
            String pinyin =  cur.getString(cur.getColumnIndex("pinyin"));

            res = new Vocabulary(chinese, pinyin);
            res.english.ensureCapacity(cur.getCount());
            do{
                res.english.add(cur.getString(cur.getColumnIndex("translation")));
            }while(cur.moveToNext());
        }
        cur.close();
        return res;
    }

    protected ArrayList<Integer> getCwordWithChar(int char_pinyin_id) {
        ArrayList<Integer> res = new ArrayList<Integer>();
        Cursor cur = db.rawQuery("SELECT cword_id " +
                "FROM cword_segment " +
                "WHERE char_pinyin_id=?", new String[] {Integer.toString(char_pinyin_id)});
        if (cur.moveToFirst()) {
            res.ensureCapacity(cur.getCount());
            do{
                res.add(cur.getInt(cur.getColumnIndex("cword_id")));
            }while(cur.moveToNext());
        }
        cur.close();
        return res;
    }

    public ArrayList<String> getAllChars() {
        ArrayList<String> res = new ArrayList<String>();

        Cursor cur = db.rawQuery("SELECT DISTINCT hanzi " +
                "FROM char_pinyin",null);

        if (cur.moveToFirst()) {
            res.ensureCapacity(cur.getCount());
            do{
                res.add(cur.getString(cur.getColumnIndex("hanzi")));
            }while(cur.moveToNext());
        }

        cur.close();
        return res;
    }

    public ArrayList<String> getAllWords() {
        ArrayList<String> res = new ArrayList<String>();

        Cursor cur = db.rawQuery("SELECT DISTINCT chinese " +
                "FROM cword", null);

        if (cur.moveToFirst()) {
            res.ensureCapacity(cur.getCount());
            do{
                res.add(cur.getString(cur.getColumnIndex("chinese")));
            }while(cur.moveToNext());
        }

        cur.close();
        return res;
    }

    ////////////////////////////////////////
    // Query for mixed or incomplete data
    ////////////////////////////////////////
    public static class StringElement {
        public static enum type_t {
            CHINESE,
            PINYIN_WITH_TONE,
            PINYIN_WITHOUT_TONE,
            INCOMPLETE_PINYIN,
            CHINESE_WITH_PINYIN,
            CHINESE_WITH_PINYIN_NO_TONE
        };

        protected String content;
        protected type_t type;
        public StringElement(type_t t, String c) {
            type=t;
            content=c;
        }
        public String chinese_part() {
            return content.substring(0, Character.charCount(content.codePointAt(0)));
        }
        public String pinyin_part() {
            return content.substring(Character.charCount(content.codePointAt(0)));
        }
        public String toString() { return content; }
    }

    public Cursor getWordsStartingWithSequence(final ArrayList<StringElement> elements, final boolean group) {
        StringBuilder query = new StringBuilder();
        ArrayList<String> query_args=new ArrayList<String>();

        if (elements.size()==0)
            return null;

        if (group) {
            query.append("SELECT _id, chinese, pinyin, GROUP_CONCAT(translation,', ') AS english FROM (" +
                    "SELECT DISTINCT cword.cword_id AS _id, cword.chinese AS chinese, cword.pinyin AS pinyin, eword.translation AS translation, cword.length AS length, ");
        } else {
            query.append("SELECT _id, chinese, pinyin, translation AS english FROM (" +
                    "SELECT DISTINCT cword.cword_id AS _id, cword.chinese AS chinese, cword.pinyin AS pinyin, eword.translation AS translation, cword.length AS length, ");
        }

        if (elements.size()==1) {
            switch (elements.get(0).type) {
                case CHINESE:
                    query.append("CASE WHEN cword.length=1 THEN 0 ELSE 1 END AS sort " +
                            "FROM (SELECT * FROM char_pinyin WHERE char_pinyin.hanzi=?) AS char_pinyin " +
                            "JOIN (SELECT * FROM cword_segment WHERE cword_segment.pos=0) AS cword_segment ON char_pinyin.char_pinyin_id=cword_segment.char_pinyin_id " +
                            "JOIN cword ON cword.cword_id=cword_segment.cword_id " +
                            "JOIN eword ON cword.cword_id=eword.cword_id ");
                    query_args.add(elements.get(0).content);
                    break;
                case PINYIN_WITH_TONE:
                    query.append("CASE WHEN cword.length=1 THEN 0 ELSE 1 END AS sort " +
                            "FROM (SELECT * FROM char_pinyin WHERE char_pinyin.pinyin=?) AS char_pinyin " +
                            "JOIN (SELECT * FROM cword_segment WHERE cword_segment.pos=0) AS cword_segment ON char_pinyin.char_pinyin_id=cword_segment.char_pinyin_id " +
                            "JOIN cword ON cword.cword_id=cword_segment.cword_id " +
                            "JOIN eword ON cword.cword_id=eword.cword_id ");
                    query_args.add(elements.get(0).content);
                    break;
                case PINYIN_WITHOUT_TONE:
                    query.append("CASE WHEN cword.length=1 THEN 0 ELSE 1 END AS sort " +
                            "FROM (SELECT * FROM char_pinyin WHERE char_pinyin.pinyin LIKE ?) AS char_pinyin " +
                            "JOIN (SELECT * FROM cword_segment WHERE cword_segment.pos=0) AS cword_segment ON char_pinyin.char_pinyin_id=cword_segment.char_pinyin_id " +
                            "JOIN cword ON cword.cword_id=cword_segment.cword_id " +
                            "JOIN eword ON cword.cword_id=eword.cword_id ");
                    query_args.add(elements.get(0).content+"_");
                    break;
                case INCOMPLETE_PINYIN:
                    query.append("CASE WHEN cword.length=1 THEN 0 ELSE 1 END AS sort " +
                            "FROM (SELECT * FROM char_pinyin WHERE char_pinyin.pinyin LIKE ?) AS char_pinyin " +
                            "JOIN (SELECT * FROM cword_segment WHERE cword_segment.pos=0) AS cword_segment ON char_pinyin.char_pinyin_id=cword_segment.char_pinyin_id " +
                            "JOIN cword ON cword.cword_id=cword_segment.cword_id " +
                            "JOIN eword ON cword.cword_id=eword.cword_id ");
                    query_args.add(elements.get(0).content+"%");
                    break;
                case CHINESE_WITH_PINYIN:
                    query.append("CASE WHEN cword.length=1 THEN 0 ELSE 1 END AS sort " +
                            "FROM (SELECT * FROM char_pinyin WHERE char_pinyin.hanzi=? AND char_pinyin.pinyin=?) AS char_pinyin " +
                            "JOIN (SELECT * FROM cword_segment WHERE cword_segment.pos=0) AS cword_segment ON char_pinyin.char_pinyin_id=cword_segment.char_pinyin_id " +
                            "JOIN cword ON cword.cword_id=cword_segment.cword_id " +
                            "JOIN eword ON cword.cword_id=eword.cword_id ");
                    query_args.add(elements.get(0).chinese_part());
                    query_args.add(elements.get(0).pinyin_part());
                    break;
                case CHINESE_WITH_PINYIN_NO_TONE:
                    query.append("CASE WHEN cword.length=1 THEN 0 ELSE 1 END AS sort " +
                            "FROM (SELECT * FROM char_pinyin WHERE char_pinyin.hanzi=? AND char_pinyin.pinyin LIKE ?) AS char_pinyin " +
                            "JOIN (SELECT * FROM cword_segment WHERE cword_segment.pos=0) AS cword_segment ON char_pinyin.char_pinyin_id=cword_segment.char_pinyin_id " +
                            "JOIN cword ON cword.cword_id=cword_segment.cword_id " +
                            "JOIN eword ON cword.cword_id=eword.cword_id ");
                    query_args.add(elements.get(0).chinese_part());
                    query_args.add(elements.get(0).pinyin_part()+"_");
                    break;
            }
        }
        else {
            StringElement.type_t type = elements.get(0).type;
            int i=0;
            String concat="";
            if (type == StringElement.type_t.CHINESE) {
                for (i=0; i<elements.size(); i++) {
                    concat+=elements.get(i).content;
                    if (elements.get(i).type != StringElement.type_t.CHINESE)
                        break;
                }
            } else if (type == StringElement.type_t.PINYIN_WITH_TONE) {
                for (i=0; i<elements.size(); i++) {
                    concat+=elements.get(i).content;
                    if (elements.get(i).type != StringElement.type_t.PINYIN_WITH_TONE)
                        break;
                }
            } else if (type == StringElement.type_t.CHINESE_WITH_PINYIN) {
                for (i=0; i<elements.size(); i++) {
                    concat+=elements.get(i).content;
                    if (elements.get(i).type != StringElement.type_t.CHINESE_WITH_PINYIN)
                        break;
                }
            } else if (type == StringElement.type_t.CHINESE_WITH_PINYIN_NO_TONE) {
                for (i=0; i<elements.size(); i++) {
                    concat+=elements.get(i).content;
                    if (elements.get(i).type != StringElement.type_t.CHINESE_WITH_PINYIN_NO_TONE)
                        break;
                }
            }

            if ((i==elements.size()) && (type == StringElement.type_t.CHINESE)) {
                query.append("CASE WHEN cword.length=? THEN 0 ELSE 1 END AS sort " +
                        "FROM (SELECT * FROM cword WHERE cword.chinese LIKE ?) AS cword " +
                        "JOIN eword ON cword.cword_id=eword.cword_id ");
                query_args.add(Integer.toString(elements.size()));
                query_args.add(concat+"%");
            } else if ((i==elements.size()) && (type == StringElement.type_t.PINYIN_WITH_TONE)) {
                query.append("CASE WHEN cword.length=? THEN 0 ELSE 1 END AS sort " +
                        "FROM (SELECT * FROM cword WHERE cword.pinyin LIKE ?) AS cword " +
                        "JOIN eword ON cword.cword_id=eword.cword_id ");
                query_args.add(Integer.toString(elements.size()));
                query_args.add(concat+"%");
            } else if ((i==elements.size()) && (type == StringElement.type_t.CHINESE_WITH_PINYIN)) {
                StringBuilder chinese = new StringBuilder();
                StringBuilder pinyin = new StringBuilder();
                for (StringElement e : elements) {
                    chinese.append(e.chinese_part());
                    pinyin.append(e.pinyin_part());
                }
                query.append("CASE WHEN cword.length=? THEN 0 ELSE 1 END AS sort " +
                        "FROM (SELECT * FROM cword WHERE cword.chinese LIKE ? AND cword.pinyin LIKE ?) AS cword " +
                        "JOIN eword ON cword.cword_id=eword.cword_id ");
                query_args.add(Integer.toString(elements.size()));
                chinese.append("%");
                pinyin.append("%");
                query_args.add(chinese.toString());
                query_args.add(pinyin.toString());
            } else if ((i==elements.size()) && (type == StringElement.type_t.CHINESE_WITH_PINYIN_NO_TONE)) {
                StringBuilder chinese = new StringBuilder();
                StringBuilder pinyin = new StringBuilder();
                for (StringElement e : elements) {
                    chinese.append(e.chinese_part());
                    pinyin.append(e.pinyin_part());
                    pinyin.append("_");
                }
                query.append("CASE WHEN cword.length=? THEN 0 ELSE 1 END AS sort " +
                        "FROM (SELECT * FROM cword WHERE cword.chinese LIKE ? AND cword.pinyin LIKE ?) AS cword " +
                        "JOIN eword ON cword.cword_id=eword.cword_id ");
                query_args.add(Integer.toString(elements.size()));
                chinese.append("%");
                pinyin.append("%");
                query_args.add(chinese.toString());
                query_args.add(pinyin.toString());
            } else {
                // mixed up
                query.append("CASE WHEN cword.length=? THEN 0 ELSE 1 END AS sort FROM (SELECT * FROM cword) AS cword JOIN eword ON cword.cword_id=eword.cword_id ");
                query_args.add(Integer.toString(elements.size()));
                for (i=0; i<elements.size(); i++) {
                    query.append(" JOIN (SELECT * FROM char_pinyin JOIN cword_segment ON char_pinyin.char_pinyin_id=cword_segment.char_pinyin_id WHERE ");
                    switch (elements.get(i).type) {
                        case CHINESE:
                            query.append("hanzi=?");
                            query_args.add(elements.get(i).content);
                            break;
                        case PINYIN_WITH_TONE:
                            query.append("pinyin=?");
                            query_args.add(elements.get(i).content);
                            break;
                        case PINYIN_WITHOUT_TONE:
                            query.append("pinyin LIKE ?");
                            query_args.add(elements.get(i).content+"_");
                            break;
                        case INCOMPLETE_PINYIN:
                            query.append("pinyin LIKE ?");
                            query_args.add(elements.get(i).content+"%");
                            break;
                        case CHINESE_WITH_PINYIN:
                            query.append("hanzi=? AND pinyin=?");
                            query_args.add(elements.get(i).chinese_part());
                            query_args.add(elements.get(i).pinyin_part());
                            break;
                        case CHINESE_WITH_PINYIN_NO_TONE:
                            query.append("hanzi=? AND pinyin LIKE ?");
                            query_args.add(elements.get(i).chinese_part());
                            query_args.add(elements.get(i).pinyin_part()+"_");
                            break;
                    }
                    query.append(" AND cword_segment.pos=?");
                    query_args.add(Integer.toString(i));
                    query.append(") AS elem").append(i);
                    query.append(" ON (elem").append(i).append(".cword_id=cword.cword_id)");
                }
            }
        }
        if (group) {
            query.append(") GROUP BY _id ");
        } else {
            query.append(") ");
        }
        query.append("ORDER BY sort, length, pinyin, _id;");
        Log.d(TAG, "Query is ["+query+"] with args "+query_args.toString());

        return db.rawQuery(query.toString(), query_args.toArray(new String[query_args.size()]));
    }

    public Cursor getWordsWithSequence(final ArrayList<StringElement> elements, final boolean group) {
        StringBuilder query = new StringBuilder();
        ArrayList<String> query_args=new ArrayList<String>();

        if (elements.size()==0)
            return null;

        if (group) {
            query.append("SELECT _id, chinese, pinyin, GROUP_CONCAT(translation,', ') AS english FROM (" +
                    "SELECT DISTINCT cword.cword_id AS _id, cword.chinese AS chinese, cword.pinyin AS pinyin, eword.translation AS translation, cword.length AS length, ");
        } else {
            query.append("SELECT _id, chinese, pinyin, translation AS english FROM (" +
                    "SELECT DISTINCT cword.cword_id AS _id, cword.chinese AS chinese, cword.pinyin AS pinyin, eword.translation AS translation, cword.length AS length, ");
        }

        if (elements.size()==1) {
            switch (elements.get(0).type) {
                case CHINESE:
                    query.append("CASE WHEN cword.length=1 THEN 0 WHEN cword.chinese LIKE ? THEN 1 ELSE 2 END AS sort " +
                            "FROM (SELECT * FROM char_pinyin WHERE char_pinyin.hanzi=?) AS char_pinyin " +
                            "JOIN cword_segment ON char_pinyin.char_pinyin_id=cword_segment.char_pinyin_id " +
                            "JOIN cword ON cword.cword_id=cword_segment.cword_id " +
                            "JOIN eword ON cword.cword_id=eword.cword_id ");
                    query_args.add(elements.get(0).content+"%");
                    query_args.add(elements.get(0).content);
                    break;
                case PINYIN_WITH_TONE:
                    query.append("CASE WHEN cword.length=1 THEN 0 WHEN cword.pinyin LIKE ? THEN 1 ELSE 2 END AS sort " +
                            "FROM (SELECT * FROM char_pinyin WHERE char_pinyin.pinyin=?) AS char_pinyin " +
                            "JOIN cword_segment ON char_pinyin.char_pinyin_id=cword_segment.char_pinyin_id " +
                            "JOIN cword ON cword.cword_id=cword_segment.cword_id " +
                            "JOIN eword ON cword.cword_id=eword.cword_id ");
                    query_args.add(elements.get(0).content+"%");
                    query_args.add(elements.get(0).content);
                    break;
                case PINYIN_WITHOUT_TONE:
                    query.append("CASE WHEN cword.length=1 THEN 0 WHEN cword.pinyin LIKE ? OR cword.pinyin LIKE ? OR cword.pinyin LIKE ? OR cword.pinyin LIKE ? OR " +
                            "cword.pinyin LIKE ? THEN 1 ELSE 2 END AS sort " +
                            "FROM (SELECT * FROM char_pinyin WHERE char_pinyin.pinyin LIKE ?) AS char_pinyin " +
                            "JOIN cword_segment ON char_pinyin.char_pinyin_id=cword_segment.char_pinyin_id " +
                            "JOIN cword ON cword.cword_id=cword_segment.cword_id " +
                            "JOIN eword ON cword.cword_id=eword.cword_id ");
                    query_args.add(elements.get(0).content+"1%");
                    query_args.add(elements.get(0).content+"2%");
                    query_args.add(elements.get(0).content+"3%");
                    query_args.add(elements.get(0).content+"4%");
                    query_args.add(elements.get(0).content+"5%");
                    query_args.add(elements.get(0).content+"_");
                    break;
                case INCOMPLETE_PINYIN:
                    query.append("CASE WHEN cword.length=1 THEN 0 WHEN cword.pinyin LIKE ? THEN 1 ELSE 2 END AS sort " +
                            "FROM (SELECT * FROM char_pinyin WHERE char_pinyin.pinyin LIKE ?) AS char_pinyin " +
                            "JOIN cword_segment ON char_pinyin.char_pinyin_id=cword_segment.char_pinyin_id " +
                            "JOIN cword ON cword.cword_id=cword_segment.cword_id " +
                            "JOIN eword ON cword.cword_id=eword.cword_id ");
                    query_args.add(elements.get(0).content+"%");
                    query_args.add(elements.get(0).content+"%");
                    break;
                case CHINESE_WITH_PINYIN:
                    query.append("CASE WHEN cword.length=1 THEN 0 WHEN cword.chinese LIKE ? THEN 1 ELSE 2 END AS sort " +
                            "FROM (SELECT * FROM char_pinyin WHERE char_pinyin.hanzi=? AND char_pinyin.pinyin=?) AS char_pinyin " +
                            "JOIN cword_segment ON char_pinyin.char_pinyin_id=cword_segment.char_pinyin_id " +
                            "JOIN cword ON cword.cword_id=cword_segment.cword_id " +
                            "JOIN eword ON cword.cword_id=eword.cword_id ");
                    query_args.add(elements.get(0).chinese_part()+"%");
                    query_args.add(elements.get(0).chinese_part());
                    query_args.add(elements.get(0).pinyin_part());
                    break;
                case CHINESE_WITH_PINYIN_NO_TONE:
                    query.append("CASE WHEN cword.length=1 THEN 0 WHEN cword.chinese LIKE ? THEN 1 ELSE 2 END AS sort " +
                            "FROM (SELECT * FROM char_pinyin WHERE char_pinyin.hanzi=? AND char_pinyin.pinyin LIKE ?) AS char_pinyin " +
                            "JOIN cword_segment ON char_pinyin.char_pinyin_id=cword_segment.char_pinyin_id " +
                            "JOIN cword ON cword.cword_id=cword_segment.cword_id " +
                            "JOIN eword ON cword.cword_id=eword.cword_id ");
                    query_args.add(elements.get(0).chinese_part()+"%");
                    query_args.add(elements.get(0).chinese_part());
                    query_args.add(elements.get(0).pinyin_part()+"_");
                    break;
            }
        }
        else {
            StringElement.type_t type = elements.get(0).type;
            int i=0;
            String concat="";
            if (type == StringElement.type_t.CHINESE) {
                for (i=0; i<elements.size(); i++) {
                    concat+=elements.get(i).content;
                    if (elements.get(i).type != StringElement.type_t.CHINESE)
                        break;
                }
            } else if (type == StringElement.type_t.PINYIN_WITH_TONE) {
                for (i=0; i<elements.size(); i++) {
                    concat+=elements.get(i).content;
                    if (elements.get(i).type != StringElement.type_t.PINYIN_WITH_TONE)
                        break;
                }
            }

            if ((i==elements.size()) && (type == StringElement.type_t.CHINESE)) {
                query.append("CASE WHEN cword.length=? THEN 0 WHEN cword.chinese LIKE ? THEN 1 ELSE 2 END AS sort " +
                        "FROM (SELECT * FROM cword WHERE cword.chinese LIKE ?) AS cword " +
                        "JOIN eword ON cword.cword_id=eword.cword_id ");
                query_args.add(Integer.toString(elements.size()));
                query_args.add(concat+"%");
                query_args.add("%"+concat+"%");
            } else if ((i==elements.size()) && (type == StringElement.type_t.PINYIN_WITH_TONE)) {
                query.append("CASE WHEN cword.length=? THEN 0 WHEN cword.pinyin LIKE ? THEN 1 ELSE 2 END AS sort " +
                        "FROM (SELECT * FROM cword WHERE cword.pinyin LIKE ? OR cword.pinyin LIKE ? OR cword.pinyin LIKE ? OR cword.pinyin LIKE ? OR cword.pinyin LIKE ? OR cword.pinyin LIKE ?) AS cword " +
                        "JOIN eword ON cword.cword_id=eword.cword_id ");
                query_args.add(Integer.toString(elements.size()));
                query_args.add(concat+"%");
                query_args.add(concat+"%");
                query_args.add("%1"+concat+"%");
                query_args.add("%2"+concat+"%");
                query_args.add("%3"+concat+"%");
                query_args.add("%4"+concat+"%");
                query_args.add("%5"+concat+"%");
            } else {
                // mixed up
                query.append("CASE WHEN cword.length=? THEN 0 WHEN elem0.pos=0 THEN 1 ELSE 2 END AS sort FROM (SELECT * FROM cword ");
                query_args.add(Integer.toString(elements.size()));
                query.append(") AS cword JOIN eword ON cword.cword_id=eword.cword_id ");
                for (i=0; i<elements.size(); i++) {
                    query.append(" JOIN (SELECT * FROM char_pinyin JOIN cword_segment ON char_pinyin.char_pinyin_id=cword_segment.char_pinyin_id WHERE ");
                    switch (elements.get(i).type) {
                        case CHINESE:
                            query.append("hanzi=?");
                            query_args.add(elements.get(i).content);
                            break;
                        case PINYIN_WITH_TONE:
                            query.append("pinyin=?");
                            query_args.add(elements.get(i).content);
                            break;
                        case PINYIN_WITHOUT_TONE:
                            query.append("pinyin LIKE ?");
                            query_args.add(elements.get(i).content+"_");
                            break;
                        case INCOMPLETE_PINYIN:
                            query.append("pinyin LIKE ?");
                            query_args.add(elements.get(i).content+"%");
                            break;
                        case CHINESE_WITH_PINYIN:
                            query.append("hanzi=? AND pinyin=?");
                            query_args.add(elements.get(i).chinese_part());
                            query_args.add(elements.get(i).pinyin_part());
                            break;
                        case CHINESE_WITH_PINYIN_NO_TONE:
                            query.append("hanzi=? AND pinyin LIKE ?");
                            query_args.add(elements.get(i).chinese_part());
                            query_args.add(elements.get(i).pinyin_part()+"_");
                            break;
                    }
                    query.append(") AS elem").append(i);
                    if (i>0) {
                        query.append(" ON (elem").append(i).append(".pos=elem").append(i - 1).append(".pos+1) AND (elem").append(i).append(".cword_id=elem").append(i - 1).append(".cword_id)");
                    }
                    else {
                        query.append(" ON (elem").append(i).append(".cword_id=cword.cword_id)");
                    }
                }
            }
        }
        if (group) {
            query.append(") GROUP BY _id ");
        } else {
            query.append(") ");
        }
        query.append("ORDER BY sort, length, pinyin, _id;");
        Log.d(TAG, "Query is ["+query+"] with args "+query_args.toString());

        return db.rawQuery(query.toString(), query_args.toArray(new String[query_args.size()]));
    }

    public Cursor getDecomposedParts(final ArrayList<StringElement> elements, final int max_size, final boolean group) {
        // Assuming all elements are Chinese...
        StringBuilder query = new StringBuilder();
        ArrayList<String> query_args=new ArrayList<String>();
        for (int i=0; i<elements.size(); i++) {
            StringBuilder part_query= new StringBuilder(elements.get(i).content);
            for (int j=0; j<Math.min(max_size, elements.size()-i); j++) {
                query.append("SELECT cword.cword_id AS _id, cword.chinese AS chinese, cword.pinyin AS pinyin, ");
                if (group)
                    query.append("GROUP_CONCAT(eword.translation,', ') AS english, ");
                else
                    query.append("eword.translation AS english, ");
                query.append((i*max_size) + (max_size-j)).append(" AS sort ");
                query.append("FROM (SELECT * FROM cword WHERE cword.chinese=?) AS cword " +
                        "JOIN eword ON cword.cword_id=eword.cword_id ");
                query_args.add(part_query.toString());
                if (group)
                    query.append("GROUP BY cword.cword_id ");

                if (j < Math.min(max_size, elements.size()-i) - 1) {
                    query.append(" UNION ");
                    part_query.append(elements.get(i+j+1).content);
                }
            }
            if (i < elements.size() - 1)
                query.append(" UNION ");
        }
        query.append("ORDER BY sort");
        Log.d(TAG, "Query is ["+query+"] with args "+query_args.toString());

        return db.rawQuery(query.toString(), query_args.toArray(new String[query_args.size()]));
    }

    ////////////////////////////////////////
    // Adding data
    ////////////////////////////////////////
    public void addVocabulary(Vocabulary voc) {
        addVocabulary(voc.english,voc.chinese,voc.pinyin);
    }

    public void addVocabulary(ArrayList<String> english, String chinese, String pinyin) {
        int cword_id;
        cword_id=addVocabulary((String)null,chinese,pinyin);
        addVocabulary(english, cword_id);
    }

    protected int addVocabulary(String english, String chinese, String pinyin) {
        String pinyin_to_insert = PinyinParser.convert_to_lame_version(pinyin);

        ArrayList<String> ch_array = new ArrayList<String>();
        int len = chinese.codePointCount(0, chinese.length());
        for (int i = 0; i< len; i++) {
            int from = chinese.offsetByCodePoints(0, i);
            int to = chinese.offsetByCodePoints(0, i + 1);

            ch_array.add(chinese.substring(from, to));
        }
        ArrayList<String> pin_array = PinyinParser.parse_string(pinyin_to_insert);
        if ((pin_array == null) || (pin_array.size() != ch_array.size()))
            return -1;

        int cword_id, char_pinyin_id;
        String han, pin;

        ContentValues cv = new ContentValues();
        cv.put("chinese", chinese);
        cv.put("pinyin", pinyin_to_insert);
        cv.put("length", chinese.codePointCount(0, chinese.length()));
        db.insert("cword", null, cv);

        cached_num_chars=-1;

        cword_id = getWordId(chinese, pinyin_to_insert);
        if (cword_id == -1) {
            Log.w(TAG, "Added a cword id, but got no ID");
        }
        else {
            for (int i = 0; i<ch_array.size(); i++) {
                han = ch_array.get(i);
                pin = pin_array.get(i);

                // add the character/pinyin pair
                cv.clear();
                cv.put("hanzi", han);
                cv.put("pinyin", pin);
                db.insert("char_pinyin", null, cv);
                char_pinyin_id = getCharPinyinId(han, pin);

                // add the link between the work and the char_pinyin pair
                cv.clear();
                cv.put("cword_id", cword_id);
                cv.put("char_pinyin_id", char_pinyin_id);
                cv.put("pos", i);
                db.insert("cword_segment", null, cv);
            }
        }

        if ((cword_id != -1) && (english != null))
            addVocabulary(english, cword_id);

        return cword_id;
    }

    @SuppressLint({"NewApi"})
    protected void addVocabulary(String english, int cword_id) {
        ContentValues cv = new ContentValues();
        cv.put("cword_id", cword_id);
        cv.put("translation", english);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO)
            db.insertWithOnConflict("eword", null, cv, SQLiteDatabase.CONFLICT_IGNORE);
        else
            db.insert("eword", null, cv);
    }

    protected void addVocabulary(ArrayList<Vocabulary> voc) {
        class bridge_input {
            public int cword_id;
            public int char_pinyin_id;
            public int position;
            public String word_chinese;
            public String word_pinyin;
            public String chinese;
            public String pinyin;
            public ArrayList<String> english;
            public bridge_input(String word_chinese, String word_pinyin, String chinese, String pinyin, int position) {
                this.word_chinese=word_chinese;
                this.word_pinyin=word_pinyin;
                this.char_pinyin_id=-1;
                this.position=position;
                this.chinese =chinese;
                this.pinyin=pinyin;
            }
            public bridge_input(String word_chinese, String word_pinyin, String chinese, String pinyin, int position, ArrayList<String> english) {
                this.word_chinese=word_chinese;
                this.word_pinyin=word_pinyin;
                this.char_pinyin_id=-1;
                this.position=position;
                this.chinese =chinese;
                this.pinyin=pinyin;
                this.english=english;
            }
        }
        ArrayList<bridge_input> to_add = new ArrayList<bridge_input>();

        DatabaseUtils.InsertHelper cword_ih = new DatabaseUtils.InsertHelper(db, "cword");
        final int cword_chinese_column = cword_ih.getColumnIndex("chinese");
        final int cword_pinyin_column = cword_ih.getColumnIndex("pinyin");
        final int cword_length_column = cword_ih.getColumnIndex("length");
        String pinyin_to_insert;
        ArrayList<String> pin_array;
        for (Vocabulary v:voc) {
            pinyin_to_insert = PinyinParser.convert_to_lame_version(v.pinyin);
            pin_array  = PinyinParser.parse_string(pinyin_to_insert);
            int pin_count=0;
            int len = v.chinese.codePointCount(0, v.chinese.length());
            for (int i = 0; i< len; i++) {
                int from = v.chinese.offsetByCodePoints(0, i);
                int to = v.chinese.offsetByCodePoints(0, i + 1);

                if (pin_count==0) {
                    to_add.add(new bridge_input(v.chinese, pinyin_to_insert, v.chinese.substring(from, to), pin_array.get(pin_count), pin_count, v.english));
                } else {
                    to_add.add(new bridge_input(v.chinese, pinyin_to_insert, v.chinese.substring(from, to), pin_array.get(pin_count), pin_count));
                }
                pin_count++;
            }
            cword_ih.prepareForInsert();
            cword_ih.bind(cword_chinese_column, v.chinese);
            cword_ih.bind(cword_pinyin_column, pinyin_to_insert);
            cword_ih.bind(cword_length_column, len);
            cword_ih.execute();
        }
        cword_ih.close();

        DatabaseUtils.InsertHelper char_pinyin_ih = new DatabaseUtils.InsertHelper(db, "char_pinyin");
        final int char_pinyin_hanzi_column = char_pinyin_ih.getColumnIndex("hanzi");
        final int char_pinyin_pinyin_column = char_pinyin_ih.getColumnIndex("pinyin");
        String cached_chinese=null;
        String cached_pinyin=null;
        int cached_cword_id=-1;
        for (bridge_input br:to_add) {
            if ((cached_cword_id==-1) || (!cached_chinese.equals(br.word_chinese) || (!cached_pinyin.equals(br.word_pinyin)))) {
                cached_chinese=br.word_chinese;
                cached_pinyin=br.word_pinyin;
                cached_cword_id = getWordId(cached_chinese, PinyinParser.convert_to_lame_version(cached_pinyin));
            }
            br.cword_id = cached_cword_id;
            char_pinyin_ih.prepareForInsert();
            char_pinyin_ih.bind(char_pinyin_hanzi_column, br.chinese);
            char_pinyin_ih.bind(char_pinyin_pinyin_column, br.pinyin);
            char_pinyin_ih.execute();
        }
        char_pinyin_ih.close();

        DatabaseUtils.InsertHelper cword_segment_ih = new DatabaseUtils.InsertHelper(db, "cword_segment");
        final int cword_segment_cword_id_column = cword_segment_ih.getColumnIndex("cword_id");
        final int cword_segment_char_pinyin_id_column = cword_segment_ih.getColumnIndex("char_pinyin_id");
        final int cword_segment_pos_column = cword_segment_ih.getColumnIndex("pos");
        for (bridge_input br:to_add) {
            br.char_pinyin_id=getCharPinyinId(br.chinese, br.pinyin);
            cword_segment_ih.prepareForInsert();
            cword_segment_ih.bind(cword_segment_cword_id_column, br.cword_id);
            cword_segment_ih.bind(cword_segment_char_pinyin_id_column, br.char_pinyin_id);
            cword_segment_ih.bind(cword_segment_pos_column, br.position);
            cword_segment_ih.execute();
        }
        cword_segment_ih.close();

        DatabaseUtils.InsertHelper eword_ih = new DatabaseUtils.InsertHelper(db, "eword");
        final int eword_cword_id_column = eword_ih.getColumnIndex("cword_id");
        final int eword_translation_column = eword_ih.getColumnIndex("translation");
        for (bridge_input br:to_add) {
            if (br.position==0) {
                for (String en:br.english) {
                    eword_ih.prepareForInsert();
                    eword_ih.bind(eword_cword_id_column, br.cword_id);
                    eword_ih.bind(eword_translation_column, en);
                    eword_ih.execute();
                }
            }
        }
        eword_ih.close();
    }

    protected void addVocabulary(ArrayList<String> english, int cword_id) {
        DatabaseUtils.InsertHelper ih = new DatabaseUtils.InsertHelper(db, "eword");
        final int cword_id_column = ih.getColumnIndex("cword_id");
        final int translation_column = ih.getColumnIndex("translation");
        for (String en:english) {
            ih.prepareForInsert();
            ih.bind(cword_id_column, cword_id);
            ih.bind(translation_column, en);
            ih.execute();
        }
        ih.close();
    }

    ////////////////////////////////////////
    // Deleting data
    ////////////////////////////////////////
    public void delVocabulary(String chinese, String pinyin) {
        int cword_id;

        cword_id = getWordId(chinese,pinyin);

        if (cword_id != -1) {
            delCwordTranslations(cword_id);
            checkIsolatedCword(cword_id);
        }
    }

    public void delVocabulary(String english, String chinese, String pinyin) {
        int eng_id, cword_id;

        Cursor cur = db.rawQuery("SELECT eng_id, eword.cword_id AS cword_id " +
                "FROM eword " +
                "  JOIN cword ON cword.cword_id=eword.cword_id " +
                "WHERE eword.translation=? AND cword.chinese=? AND cword.pinyin=?",
                new String[] {english,chinese,pinyin});
        if (cur.moveToFirst()) {
            eng_id = cur.getInt(cur.getColumnIndex("eng_id"));
            cword_id = cur.getInt(cur.getColumnIndex("cword_id"));

            delEword(eng_id);
            checkIsolatedCword(cword_id);
        }
        cur.close();
    }

    public void delVocabulary(ArrayList<String> english, String chinese, String pinyin) {
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
        if (voc.english == null) {
            delVocabulary(voc.chinese, voc.pinyin);
        }
        else {
            delVocabulary(voc.english,voc.chinese,voc.pinyin);
        }
    }

    protected void delEword(int eng_id) {
        db.delete("lesson_word", "eng_id=?", new String[] {Integer.toString(eng_id)});
        db.delete("eword", "eng_id=?", new String[] {Integer.toString(eng_id)} );
    }

    protected void delCwordTranslations(int cword_id) {
        db.delete("lesson_word", "eng_id IN (SELECT eng_id FROM eword WHERE cword_id=?)", new String[] {Integer.toString(cword_id)});
        db.delete("eword", "cword_id=?", new String[] {Integer.toString(cword_id)} );
    }

    protected void delCharPinyin(int char_pinyin_id) {
        String hanzi;
        Cursor cur = db.rawQuery("SELECT hanzi  " +
                "FROM char_pinyin " +
                "WHERE char_pinyin_id=?;", new String[] {Integer.toString(char_pinyin_id)});
        if (cur.moveToFirst()) {
            hanzi = cur.getString(cur.getColumnIndex("hanzi"));
            cur.close();
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
        String hanzi;
        Cursor cur = db.rawQuery("SELECT chinese  " +
                "FROM cword " +
                "WHERE cword_id=?;", new String[] {Integer.toString(cword_id)});
        if (cur.moveToFirst()) {
            hanzi = cur.getString(cur.getColumnIndex("chinese"));
            cur.close();
        }
        else {
            cur.close();
            return;
        }

        ArrayList<Integer> char_pinyin_id = new ArrayList<Integer>();

        cur = db.rawQuery("SELECT char_pinyin_id FROM cword_segment WHERE cword_id=?", new String[] {Integer.toString(cword_id)});
        if (cur.moveToFirst()) {
            do{
                char_pinyin_id.add(cur.getInt(cur.getColumnIndex("char_pinyin_id")));
            }while(cur.moveToNext());
        }
        cur.close();
        db.delete("cword", "cword_id=?", new String[] {Integer.toString(cword_id)});
        db.delete("cword_segment", "cword_id=?", new String[] {Integer.toString(cword_id)});

        cached_num_chars=-1;

        for (int cpid : char_pinyin_id) {
            if (getCwordWithChar(cpid).size() == 0) {
                delCharPinyin(cpid);
            }
        }
        cur = db.rawQuery("SELECT * FROM cword WHERE chinese=?", new String[] {hanzi});
        if (cur.getCount() == 0) {
            db.delete("leitner", "hanzi=?",new String[] {hanzi});
        }
        cur.close();
    }

    protected void checkIsolatedCword(int cword_id) {
        if (getTranslations(cword_id) == null) {
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
        if (cached_num_chars != -1)
            return cached_num_chars;
        Cursor cur = db.rawQuery("SELECT hanzi FROM char_pinyin GROUP BY hanzi", null);
        res= cur.getCount();
        cached_num_chars=res;

        cur.close();
        return res;
    }

    ////////////////////////////////////////
    // Merge Database
    ////////////////////////////////////////
    public class merge_task extends AsyncTask<Void,Integer,Void> {
        final String to_merge_name = "tmp_db", new_db_name ="merged_db";
        HanziDB other_db=null;
        HanziDB merge_from, merge_to;
        String path;
        ProgressDialog dialog;
        Activity context;
        Runnable finishing;
        boolean copying;
        int last_progress;

        public merge_task(String path, ProgressDialog dialog, Activity context,  Runnable finishing) {
            this.path = path;
            this.dialog = dialog;
            this.context = context;
            this.finishing = finishing;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            if (getAllChars().size()==0) {
                // just need to copy
                app.replaceDataBase(path);
            }
            else {
                // copy to the DB directory
                if (!app.copyFile(path, app.getApplicationContext().getDatabasePath(to_merge_name).getPath())) {
                    clean_up_on_cancel(true);
                    return null;
                }
                publishProgress(5);

                if (isCancelled()) {
                    clean_up_on_cancel(false);
                    return null;
                }

                // copy a backup
                other_db = new HanziDB(app, to_merge_name);
                publishProgress(10);

                if (other_db.getNumChars() < getNumChars()) {
                    merge_from = other_db;
                    if (!app.copyFile(db.getPath(), app.getApplicationContext().getDatabasePath(new_db_name).getPath())) {
                        clean_up_on_cancel(true);
                        return null;
                    }

                } else {
                    merge_from = HanziDB.this;
                    if (!app.copyFile(app.getDatabasePath(to_merge_name).getPath(), app.getApplicationContext().getDatabasePath(new_db_name).getPath())) {
                        clean_up_on_cancel(true);
                        return null;
                    }
                }
                merge_to = new HanziDB(app, new_db_name);

                // merge it
                ArrayList<String> original_lessons = merge_to.getLessons();
                ArrayList<String> lessons = merge_from.getLessons();
                for (int i=0; i<lessons.size(); i++) {
                    publishProgress(10+(i*50)/(lessons.size()+1));
                    if (isCancelled()) {
                        clean_up_on_cancel(false);
                        return null;
                    }
                    String l = lessons.get(i);
                    if (!original_lessons.contains(l))
                        merge_to.addLesson(l);

                    ArrayList<Vocabulary> voc = merge_from.getVocabularyInLessons(new ArrayList<String>(Arrays.asList(l)));
                    merge_to.addVocabulary(voc);
                    for (Vocabulary v:voc) {
                        merge_to.assignWordLesson(l,v);
                    }
                }

                // add the vocabulary that's not in any lesson
                ArrayList<Vocabulary> voc = merge_from.getVocabularyInLessons(new ArrayList<String>(Arrays.asList("")));
                merge_to.addVocabulary(voc);

                ArrayList<String> chars = merge_from.getAllChars();
                for (int i=0; i<chars.size(); i++) {
                    publishProgress(60+(i*15)/chars.size());
                    if (isCancelled()) {
                        clean_up_on_cancel(false);
                        return null;
                    }
                    String c=chars.get(i);
                    if (merge_from.getBoxForChar(c) != 0) {
                        merge_to.setBoxForChar(c, merge_from.getBoxForChar(c));
                        merge_to.setExpirationTimeForChar(c, merge_from.getExpirationTimeForChar(c));
                    }
                }

                ArrayList<String> words = merge_from.getAllWords();
                for (int i=0; i<words.size(); i++) {
                    publishProgress(75+(i*15)/words.size());
                    if (isCancelled()) {
                        clean_up_on_cancel(false);
                        return null;
                    }
                    String c=words.get(i);
                    if (merge_from.getBoxForChar(c) != 0) {
                        merge_to.setBoxForChar(c, merge_from.getBoxForChar(c));
                        merge_to.setExpirationTimeForChar(c, merge_from.getExpirationTimeForChar(c));
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
                merge_to.sortLessons(all_lessons);

                other_db.close();
                HanziDB.this.close();

                publishProgress(95);
                app.replaceDataBase(app.getDatabasePath(new_db_name).getPath());

                publishProgress(100);

                if (isCancelled()) { // everything went through anyway...
                    onPostExecute((Void) null);
                }
            }

            return null;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            // create the progress dialog if needed
            if (dialog!=null) {
                if (getAllChars().size()==0) {
                    copying = true;
                    dialog.setTitle("Importing Database");
                    dialog.setMessage("Copying...");
                    dialog.setIndeterminate(true);
                    dialog.show();
                }
                else {
                    copying = false;
                    dialog.setMessage("Merging database");
                    dialog.setIndeterminate(false);
                    dialog.setMax(100);
                    dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                        public void onCancel(DialogInterface dialogInterface) {
                            cancel(false);
                        }
                    });
                    dialog.setCancelable(true);
                    dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                    dialog.show();
                }
            }
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            last_progress = values[0];
            if (dialog!=null)
                dialog.setProgress(values[0]);
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);

            // Dismiss progress dialog
            if (dialog!=null)
                dialog.dismiss();

            if (finishing != null)
                finishing.run();
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();

            if (dialog!=null)
                dialog.dismiss();
        }

        private void clean_up_on_cancel(final boolean error) {
            // Erase the temporary database
            app.getDatabasePath(to_merge_name).delete();
            app.getDatabasePath(new_db_name).delete();

            if (other_db != null)
                other_db.close();

            if (error)
                context.runOnUiThread(new Runnable() {
                    public void run() {
                        Toast.makeText(context, "An error occured while importing the database", Toast.LENGTH_SHORT).show();
                    }
                });
        }

        public void change_progress_dialog(ProgressDialog new_progress) {
            this.dialog = new_progress;
            if (copying) {
                dialog.setTitle("Importing Database");
                dialog.setMessage("Copying...");
                dialog.setIndeterminate(true);
                dialog.show();
            }
            else {
                dialog.setMessage("Merging database");
                dialog.setIndeterminate(false);
                dialog.setMax(100);
                dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    public void onCancel(DialogInterface dialogInterface) {
                        cancel(false);
                    }
                });
                dialog.setCancelable(true);
                dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                dialog.show();
                dialog.setProgress(last_progress);
            }
        }

        public void change_context(Activity context) {
            this.context = context;
        }
    }

    @SuppressLint({"NewApi"})
    public merge_task create_merge_task(final String path, final ProgressDialog mProgressDialog, final Activity context, final Runnable finishing) {
        merge_task res = new merge_task(path, mProgressDialog, context, finishing);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
            res.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[])null);
        else
            res.execute((Void[])null);

        return res;
    }

    ////////////////////////////////////////
    // Export Database
    ////////////////////////////////////////
    public class export_task extends AsyncTask<Void,Integer,Void> {
        ProgressDialog mProgressDialog;
        final String new_db_name = "tmp_db";
        HanziDB new_db=null;
        boolean with_scores;
        ArrayList<String> selected_lessons;
        String path;
        Activity context;
        Runnable finishing;
        int last_progress=0;

        public export_task(String path, ArrayList<String> selected_lessons, ProgressDialog dialog, boolean with_scores, Activity context, Runnable finishing) {
            this.path = path;
            this.selected_lessons = selected_lessons;
            this.with_scores = with_scores;
            this.context = context;
            this.finishing = finishing;
            mProgressDialog = dialog;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            if ((with_scores) && (selected_lessons == null)) {
                if (!app.copyFile(db.getPath(), path)) {
                    clean_up_on_cancel(true);
                    return null;
                }
            } else if (selected_lessons == null) {
                if (!app.copyFile(db.getPath(), HanziDB.this.app.getDatabasePath(new_db_name).getPath())) {
                    clean_up_on_cancel(true);
                    return null;
                }
                new_db = new HanziDB(app, new_db_name);
                new_db.resetAllScores();
                new_db.close();

                if (!app.copyFile(HanziDB.this.app.getDatabasePath(new_db_name).getPath(), path)) {
                    clean_up_on_cancel(true);
                    return null;
                }

                HanziDB.this.app.deleteDatabase(new_db_name);
            } else {
                new_db = new HanziDB(app, new_db_name);
                publishProgress(10);

                for (int i=0; i<selected_lessons.size(); i++) {
                    String lesson = selected_lessons.get(i);
                    if (isCancelled()) {
                        clean_up_on_cancel(false);
                        return null;
                    }
                    publishProgress(10+(i*70)/selected_lessons.size());
                    Log.d(TAG, "exporting lesson "+lesson);
                    if (lesson.equals(""))
                        continue;
                    new_db.addLesson(lesson);

                    ArrayList<Vocabulary> lesson_voc = getVocabularyInLessons(new ArrayList<String>(Arrays.asList(lesson)));
                    new_db.addVocabulary(lesson_voc);
                    for (Vocabulary v : lesson_voc) {
                        new_db.assignWordLesson(lesson, v);
                    }
                }
                new_db.sortLessons(selected_lessons);

                if (with_scores) {
                    ArrayList<String> chars = new_db.getAllChars();
                    for (int i=0; i<chars.size(); i++) {
                        String c = chars.get(i);
                        if (isCancelled()) {
                            clean_up_on_cancel(false);
                            return null;
                        }
                        publishProgress(80+(i*5)/chars.size());
                        int b = getBoxForChar(c);
                        if (b!=0) {
                            long t = getExpirationTimeForChar(c);
                            new_db.setBoxForChar(c, b);
                            new_db.setExpirationTimeForChar(c, t);
                        }
                    }
                    ArrayList<String> words = new_db.getAllWords();
                    for (int i=0; i<words.size(); i++) {
                        String c = words.get(i);
                        if (isCancelled()) {
                            clean_up_on_cancel(false);
                            return null;
                        }
                        publishProgress(85+(i*5)/words.size());
                        int b = getBoxForChar(c);
                        if (b!=0) {
                            long t = getExpirationTimeForChar(c);
                            new_db.setBoxForChar(c, b);
                            new_db.setExpirationTimeForChar(c, t);
                        }
                    }
                }


                if (!app.copyFile(new_db.getWritableDatabase().getPath(), path)) {
                    clean_up_on_cancel(true);
                    return null;
                }
                new_db.close();

                publishProgress(95);

                HanziDB.this.app.deleteDatabase(new_db_name);

                if (isCancelled()) { // everything went through anyway...
                    onPostExecute((Void) null);
                }
            }
            return null;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            // create the progress dialog if needed
            if (mProgressDialog != null) {
                if (selected_lessons == null) {
                    mProgressDialog.setTitle("Exporting Database");
                    mProgressDialog.setMessage("Copying....");
                    mProgressDialog.setIndeterminate(true);
                    mProgressDialog.show();
                }
                else {
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
            last_progress = values[0];
            if (mProgressDialog != null)
                mProgressDialog.setProgress(values[0]);
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);

            if (mProgressDialog != null)
                mProgressDialog.dismiss();

            if (finishing!= null)
                finishing.run();
        }

        private void clean_up_on_cancel(final boolean error) {
            Log.d(TAG, "Cancelling export !");
            if(new_db!=null)
                new_db.close();

            HanziDB.this.app.deleteDatabase(new_db_name);
            new File(path).delete();

            if (mProgressDialog != null || error) {
                context.runOnUiThread(new Runnable() {
                    public void run() {
                        if (mProgressDialog != null)
                            mProgressDialog.dismiss();

                        if (error) {
                            Toast.makeText(context, "An error occured while exporting the database", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        }

        public void change_context(Activity context) {
            this.context = context;
        }

        public void change_dialog(ProgressDialog dialog) {
            mProgressDialog = dialog;
            if (selected_lessons == null) {
                mProgressDialog.setTitle("Exporting Database");
                mProgressDialog.setMessage("Copying....");
                mProgressDialog.setIndeterminate(true);
                mProgressDialog.show();
            }
            else {
                mProgressDialog.setMessage("Exporting database");
                mProgressDialog.setIndeterminate(false);
                mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                mProgressDialog.setMax(100);
                mProgressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    public void onCancel(DialogInterface dialogInterface) {
                        cancel(false);
                    }
                });
                mProgressDialog.setCancelable(true);
                mProgressDialog.show();
                mProgressDialog.setProgress(last_progress);
            }
        }
    }

    @SuppressLint({"NewApi"})
    public export_task create_export_task(final String path, final boolean with_scores, final ArrayList<String> selected_lessons, ProgressDialog dialog, final Activity context, final Runnable finishing) {
        export_task res = new export_task(path, selected_lessons, dialog, with_scores, context, finishing);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
            res.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[])null);
        else
            res.execute((Void[])null);

        return res;
    }

    ////////////////////////////////////////
    // Clear Database
    ////////////////////////////////////////
    public class clear_task extends AsyncTask<Void,Integer,Void> {
        ProgressDialog mProgressDialog;
        Runnable finishing;

        public clear_task(ProgressDialog dialog, Runnable finishing) {
            mProgressDialog = dialog;
            this.finishing = finishing;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            HanziDB.this.onCreate(db);
            return null;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            // create the progress dialog if needed
            if (mProgressDialog != null) {
                mProgressDialog.setMessage("Clearing database");
                mProgressDialog.setIndeterminate(true);
                mProgressDialog.setCancelable(false);
                mProgressDialog.show();
            }
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);

            if (mProgressDialog != null)
                mProgressDialog.dismiss();

            if (finishing!= null)
                finishing.run();
        }

        public void change_dialog(ProgressDialog dialog) {
            mProgressDialog = dialog;
            mProgressDialog.setMessage("Clearing database");
            mProgressDialog.setIndeterminate(true);
            mProgressDialog.setCancelable(false);
            mProgressDialog.show();
        }

    }

    @SuppressLint({"NewApi"})
    public clear_task create_clear_task(ProgressDialog dialog, final Runnable finishing) {
        clear_task res = new clear_task(dialog, finishing);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
            res.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[])null);
        else
            res.execute((Void[])null);

        return res;
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
    @SuppressLint({"NewApi"})
    public void addLesson(String name) {
        ContentValues cv = new ContentValues();
        cv.put("name", name);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO)
            db.insertWithOnConflict("lessons", null, cv, SQLiteDatabase.CONFLICT_IGNORE);
        else
            db.insert("lessons", null, cv);
    }

    public void delLesson(String name) {
        int lesson_id;

        lesson_id = getLessonId(name);
        if (lesson_id == -1)
            return;

        db.delete("lesson_word", "lesson_id=?", new String[] {Integer.toString(lesson_id)});
        db.delete("lessons", "lesson_id=?", new String[] {Integer.toString(lesson_id)});
    }

    public void renameLesson(String old_name, String new_name) {
        int lesson_id;

        lesson_id = getLessonId(old_name);
        if (lesson_id == -1)
            return;

        ContentValues cv = new ContentValues();
        cv.put("name", new_name);

        db.update("lessons", cv, "lesson_id=?", new String[] {Integer.toString(lesson_id)});
    }

    public ArrayList<String> getLessons() {
        ArrayList<String> res = new ArrayList<String>();

        Cursor cur = db.rawQuery("SELECT name FROM lessons ORDER BY list_order ASC", null);
        if (cur.moveToFirst()) {
            res.ensureCapacity(cur.getCount());
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
    @SuppressLint({"NewApi"})
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO)
            db.insertWithOnConflict("lesson_word", null, cv, SQLiteDatabase.CONFLICT_IGNORE);
        else
            db.insert("lesson_word", null, cv);
    }

    public void assignWordLesson(String lesson, Vocabulary voc) {
        Vocabulary new_voc;
        if (voc.english == null) {
            new_voc = getTranslations(voc.chinese, voc.pinyin);
        }
        else {
            new_voc = voc;
        }
        if (new_voc != null)
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

    public ArrayList<String> getLessonForWord(String chinese, String pinyin, String english) {
        ArrayList<String> res=new ArrayList<String>();
        int cword_id = getWordId(chinese, pinyin);

        if (cword_id == -1)
            return res;

        Cursor cur = db.rawQuery("SELECT l.name AS name " +
                "FROM lessons AS l " +
                " JOIN lesson_word AS lw ON lw.lesson_id=l.lesson_id " +
                " JOIN (SELECT cword_id, eng_id FROM eword WHERE translation=?) AS ew ON lw.eng_id=ew.eng_id" +
                " JOIN (SELECT cword_id FROM cword WHERE chinese=? AND pinyin=?) AS cw ON ew.cword_id=cw.cword_id"
                , new String[] {english, chinese, pinyin});

        if (cur.moveToFirst()) {
            res.ensureCapacity(cur.getCount());
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

        if ((lessons.size() == 1) && (lessons.contains(""))) {
            // only words not in any lesson
            cur = db.rawQuery("SELECT e.translation AS translation, " +
                    "c.chinese AS chinese, " +
                    "c.pinyin AS pinyin " +
                    "FROM eword AS e " +
                    " JOIN cword AS c ON c.cword_id=e.cword_id " +
                    " WHERE e.eng_id NOT IN (SELECT eng_id FROM lesson_word) ORDER BY c.cword_id"
                    ,null);
        } else if (!lessons.contains("")) {
            // words that have some lessons associated with them
            String whereQuery = buildWhereQuery(lessons);

            cur = db.rawQuery("SELECT e.translation AS translation, " +
                    "c.chinese AS chinese, " +
                    "c.pinyin AS pinyin " +
                    "FROM (SELECT * FROM lesson_word " + whereQuery + ") AS lesson_word " +
                    " JOIN eword AS e ON lesson_word.eng_id=e.eng_id " +
                    " JOIN cword AS c ON c.cword_id=e.cword_id ORDER BY c.cword_id"
                    ,null);


        } else {
            // a little bit of both
            lessons.remove("");
            String whereQuery = buildWhereQuery(lessons);
            cur = db.rawQuery("SELECT e.translation AS translation, " +
                    "c.chinese AS chinese, " +
                    "c.pinyin AS pinyin, " +
                    "c.cword_id AS cword_id " +
                    "FROM eword AS e " +
                    " JOIN cword AS c ON c.cword_id=e.cword_id " +
                    " WHERE e.eng_id NOT IN (SELECT eng_id FROM lesson_word) " +
                    "UNION " +
                    "SELECT e.translation AS translation, " +
                    "c.chinese AS chinese, " +
                    "c.pinyin AS pinyin, " +
                    "c.cword_id AS cword_id " +
                    "FROM (SELECT * FROM lesson_word " + whereQuery + ") AS lesson_word " +
                    " JOIN eword AS e ON lesson_word.eng_id=e.eng_id " +
                    " JOIN cword AS c ON c.cword_id=e.cword_id " +
                    "ORDER BY cword_id"
                    ,null);
        }
        if (cur.moveToFirst()) {
            res.ensureCapacity(cur.getCount());
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

    public Cursor getVocabularyCursorInLessons(ArrayList<String> lessons, boolean group) {
        StringBuilder query=new StringBuilder();

        if ((lessons.size() == 1) && (lessons.contains(""))) {
            // only words not in any lesson
            query.append("SELECT c.cword_id AS _id, c.chinese AS chinese, c.pinyin AS pinyin, ");
            if (group)
                query.append("GROUP_CONCAT(e.translation,', ') AS english ");
            else
                query.append("e.translation AS english ");
            query.append("FROM eword AS e " +
                    " JOIN cword AS c ON c.cword_id=e.cword_id " +
                    " WHERE e.eng_id NOT IN (SELECT eng_id FROM lesson_word) ");
            if (group)
                query.append("GROUP BY c.cword_id ");
        } else if (!lessons.contains("")) {
            // words that have some lessons associated with them
            String whereQuery = buildWhereQuery(lessons);

            query.append("SELECT c.cword_id AS _id, c.chinese AS chinese, c.pinyin AS pinyin, ");
            if (group)
                query.append("GROUP_CONCAT(e.translation,', ') AS english ");
            else
                query.append("e.translation AS english ");

            query.append("FROM (SELECT * FROM lesson_word ").append(whereQuery).append(") AS lesson_word " +
                    " JOIN eword AS e ON lesson_word.eng_id=e.eng_id " +
                    " JOIN cword AS c ON c.cword_id=e.cword_id ");
            if (group)
                query.append("GROUP BY c.cword_id ");
        } else {
            // a little bit of both
            lessons.remove("");
            String whereQuery = buildWhereQuery(lessons);
            query.append("SELECT c.cword_id AS _id, c.chinese AS chinese, c.pinyin AS pinyin, ");
            if (group)
                query.append("GROUP_CONCAT(e.translation,', ') AS english ");
            else
                query.append("e.translation AS english ");
            query.append("FROM eword AS e " +
                    " JOIN cword AS c ON c.cword_id=e.cword_id " +
                    " WHERE (e.eng_id NOT IN (SELECT eng_id FROM lesson_word)) " +
                    " OR (e.eng_id IN " +
                    "   (SELECT eword.eng_id FROM (SELECT * FROM lesson_word ").append(whereQuery).append(") AS lessons" +
                    "   JOIN eword ON lessons.eng_id=eword.eng_id))");
            if (group)
                query.append("GROUP BY c.cword_id ");
        }
        return db.rawQuery(query.toString(), null);
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

    /*
     *
     *  Leitner learning database management
     *
     *
     */

    ////////////////////////////////////////
    // Get information
    ////////////////////////////////////////
    public int getNumCharsInBox(int box) {
        int res;

        if (box < 0)
            return 0;

        Cursor cur;
        if (box == 0)
            // add the characters that do not have any box...
            cur = db.rawQuery("SELECT COUNT(chars.hanzi) AS count FROM " +
                    "(SELECT * FROM char_pinyin GROUP BY hanzi) AS chars " +
                    "LEFT OUTER JOIN leitner ON leitner.hanzi=chars.hanzi " +
                    "WHERE (box IS NULL) OR (box=0)", null);


        else
            cur = db.rawQuery("SELECT COUNT(hanzi) AS count FROM leitner " +
                    "WHERE (length(hanzi)==1) AND (box=?)", new String[] {Integer.toString(box)});

        cur.moveToFirst();
        res= cur.getInt(cur.getColumnIndex("count"));
        cur.close();

        return res;
    }

    public int getNumCharsInBoxOrAbove(int box) {
        int res;

        if (box < 0)
            return 0;

        if (box == 0)
            return getNumChars();

        Cursor cur = db.rawQuery("SELECT COUNT(hanzi) AS count FROM leitner " +
                "WHERE (length(hanzi)==1) AND (box>=?)", new String[] {Integer.toString(box)});
        cur.moveToFirst();
        res= cur.getInt(cur.getColumnIndex("count"));

        cur.close();

        return res;
    }

    public int getNumCharsExpiredInBox(int box) {
        int res;

        if (box < 0)
            return 0;

        if (box == 0)
            return getNumCharsInBox(0);
        else {
            Cursor cur = db.rawQuery("SELECT COUNT(hanzi) AS count FROM leitner " +
                    "WHERE length(hanzi)==1 AND box=? AND exp_time<?",
                    new String[] {Integer.toString(box), Long.toString(System.currentTimeMillis())});
            cur.moveToFirst();
            res= cur.getInt(cur.getColumnIndex("count"));

            cur.close();
        }
        return res;
    }

    public int getNumCharsExpiredInBoxOrAbove(int box) {
        int res;

        if (box < 0)
            return 0;

        Cursor cur;

        if (box == 0)
            cur = db.rawQuery("SELECT COUNT(chars.hanzi) AS count FROM " +
                    "(SELECT * FROM char_pinyin GROUP BY hanzi) AS chars " +
                    "LEFT OUTER JOIN leitner ON leitner.hanzi=chars.hanzi " +
                    "WHERE (exp_time<?) OR (box IS NULL) OR (box=0)",
                    new String[] {Long.toString(System.currentTimeMillis())});
        else
            cur = db.rawQuery("SELECT COUNT(hanzi) AS count FROM leitner " +
                    "WHERE length(hanzi)==1 AND box>=? AND exp_time<?",
                    new String[] {Integer.toString(box), Long.toString(System.currentTimeMillis())});

        cur.moveToFirst();
        res= cur.getInt(cur.getColumnIndex("count"));

        cur.close();
        return res;
    }

    public int getNumWordsExpiredInBoxOrAbove(int box) {
        int res;

        if (box < 0)
            return 0;

        Cursor cur;

        if (box == 0)
            cur = db.rawQuery("SELECT COUNT(cword.chinese) AS count FROM cword " +
                    "LEFT OUTER JOIN leitner ON leitner.hanzi=cword.chinese " +
                    "WHERE (exp_time<?) OR (box IS NULL) OR (box=0)",
                    new String[] {Long.toString(System.currentTimeMillis())});
        else
            cur = db.rawQuery("SELECT COUNT(hanzi) AS count FROM leitner " +
                    "WHERE length(hanzi)>1 AND box>=? AND exp_time<?",
                    new String[] {Integer.toString(box), Long.toString(System.currentTimeMillis())});

        cur.moveToFirst();
        res= cur.getInt(cur.getColumnIndex("count"));

        cur.close();
        return res;
    }

    public int getNumExpired() {
        int res;

        Cursor cur = db.rawQuery("SELECT chinese FROM (" +
                "SELECT cword.chinese AS chinese FROM cword " +
                "LEFT OUTER JOIN leitner ON leitner.hanzi=cword.chinese " +
                "WHERE (exp_time<?) OR (box IS NULL) OR (box=0) " +
                "UNION " +
                "SELECT chars.hanzi AS chinese FROM " +
                "(SELECT * FROM char_pinyin GROUP BY hanzi) AS chars " +
                "LEFT OUTER JOIN leitner ON leitner.hanzi=chars.hanzi " +
                "WHERE (exp_time<?) OR (box IS NULL) OR (box=0)" +
                ") GROUP BY chinese",
                new String[] {Long.toString(System.currentTimeMillis()), Long.toString(System.currentTimeMillis())});

        res=cur.getCount();

        cur.close();
        return res;
    }
    public int getBoxForChar(String hanzi) {
        int res;

        Cursor cur = db.rawQuery("SELECT box FROM leitner WHERE hanzi=?",
                new String[] {hanzi});
        if (cur.moveToFirst()) {
            //Log.d(TAG, "got record " + cur.getInt(cur.getColumnIndex("cword_id")) + " for word " + chinese);
            res = cur.getInt(cur.getColumnIndex("box"));
        }
        else {
            res = 0;
        }

        cur.close();
        return res;
    }

    protected long getExpirationTimeForChar(String hanzi) {
        long res;

        Cursor cur = db.rawQuery("SELECT exp_time FROM leitner WHERE hanzi=?",
                new String[] {hanzi});
        if (cur.moveToFirst()) {
            res = cur.getLong(cur.getColumnIndex("exp_time"));
        }
        else {
            res = 0;
        }

        cur.close();
        return res;
    }

    protected long getExpiryTime (int box) {
        if (box == 0)
            return 0;

        long box_delay = (long) 6 * 60 * 60 * 1000; // 6 hours is basic

        // at the minimum this will give a delay of 12 hours and will double per box and per power of 10
        return (long) (box_delay * Math.pow(2, box + 1));

        //System.out.println("With "+getNumChars()+" characters, mult is "+multiplicator+", so delay is "+(res/(1000*60*60)+" hours for box "+box));
    }

    protected long getExpiryTime (int box, int num_tested, int num_wrong, long date_started, long last_expiration) {
        if (box==0)
            return 0;

        long box_delay = (long) 6 * 60 * 60 * 1000; // 6 hours is basic
        long extra_delay = 0;
        if ((last_expiration != 0) && (last_expiration < System.currentTimeMillis()))
            extra_delay = (System.currentTimeMillis() - last_expiration)/2;

        double multiplicand=2;
        if (num_tested > 3) {
            multiplicand += (0.5-(num_wrong/num_tested))*8/5; // multiplicand becomes between 1.2 and 2.8 depending on results so far
        }
        // at the minimum this will give a delay of 12 hours and will double per box and per power of 10
        return (long) (box_delay * Math.pow(multiplicand, box + 1) + extra_delay);
    }

    protected ArrayList<String> getMostAdvancedExpiring(int num, boolean only_chars) {
        Cursor cur;
        ArrayList<String> res=new ArrayList<String>();

        if (only_chars)
            cur = db.rawQuery("SELECT hanzi FROM leitner " +
                    "WHERE length(hanzi)==1 AND exp_time>? ORDER BY exp_time ASC LIMIT ?",
                    new String[] {Long.toString(System.currentTimeMillis()), Integer.toString(num)});
        else
            cur = db.rawQuery("SELECT hanzi FROM leitner WHERE exp_time>? ORDER BY exp_time ASC LIMIT ?",
                    new String[] {Long.toString(System.currentTimeMillis()), Integer.toString(num)});

        if (cur.moveToFirst()) {
            res.ensureCapacity(cur.getCount());
            do {
                res.add(cur.getString(cur.getColumnIndex("hanzi")));
            } while (cur.moveToNext());
        }
        cur.close();
        return res;
    }

    ////////////////////////////////////////
    // Update data
    ////////////////////////////////////////
    public void resetAllScores() {
        db.delete("leitner", null, null);
    }

    public void resetTime(String hanzi) {
        if (getBoxForChar(hanzi)==0) {
            betterBox(hanzi);
            return;
        }

        Cursor cur = db.rawQuery("SELECT * FROM leitner WHERE hanzi=?",
                new String[] {hanzi});
        cur.moveToFirst();

        ContentValues cv = new ContentValues();
        int num_tested = cur.getInt(cur.getColumnIndex("num_tested")) + 1;
        cv.put("num_tested", num_tested);
        cur.close();

        cv.put("exp_time", System.currentTimeMillis()+getExpiryTime(getBoxForChar(hanzi)));
        db.update("leitner", cv, "hanzi=?", new String[] {hanzi});
    }

    public void betterBox(String hanzi) {
        Cursor cur = db.rawQuery("SELECT * FROM leitner WHERE hanzi=?",
                new String[] {hanzi});

        ContentValues cv = new ContentValues();

        if (cur.getCount() == 0) {
            cv.put("hanzi", hanzi);
            cv.put("box", 1);
            cv.put("exp_time", System.currentTimeMillis()+getExpiryTime(1));
            cv.put("num_tested",1);
            cv.put("num_wrong",0);
            cv.put("streak",1);
            cv.put("date_added", System.currentTimeMillis());
            db.insert("leitner", null, cv);
        }
        else {
            cur.moveToFirst();
            int current_box = 1 + cur.getInt(cur.getColumnIndex("box"));
            cv.put("box", current_box);
            int streak = cur.getInt(cur.getColumnIndex("streak"));
            if (streak>3) {
                int wrong = cur.getInt(cur.getColumnIndex("num_wrong"));
                cv.put("num_wrong", wrong-1);
            }
            cv.put("streak", streak+1);
            int num_tested = cur.getInt(cur.getColumnIndex("num_tested")) + 1;
            cv.put("num_tested", num_tested);
            cv.put("exp_time", System.currentTimeMillis()+getExpiryTime(current_box, num_tested, cur.getInt(cur.getColumnIndex("num_wrong")),cur.getLong(cur.getColumnIndex("date_added")), cur.getLong(cur.getColumnIndex("exp_time"))));
            db.update("leitner", cv, "hanzi=?", new String[] {hanzi});
        }
        cur.close();
    }

    public void worseBox(String hanzi) {
        ContentValues cv = new ContentValues();

        Cursor cur = db.rawQuery("SELECT * FROM leitner WHERE hanzi=?",
                new String[] {hanzi});

        if (cur.getCount() == 0) {
            cv.put("hanzi", hanzi);
            cv.put("box", 0);
            cv.put("exp_time", System.currentTimeMillis());
            cv.put("num_tested",1);
            cv.put("num_wrong", 1);
            cv.put("date_added", System.currentTimeMillis());
            db.insert("leitner", null, cv);
        }
        else {
            cur.moveToFirst();
            int num_tested = cur.getInt(cur.getColumnIndex("num_tested")) + 1;
            cv.put("num_tested", num_tested);
            int wrong = cur.getInt(cur.getColumnIndex("num_wrong"));
            cv.put("num_wrong", wrong+1);
            int current_box = cur.getInt(cur.getColumnIndex("box"));
            if (current_box > 2)
                current_box-=3;
            else
                current_box=0;
            cv.put("box", current_box);
            cv.put("streak", 0);
            cv.put("exp_time", System.currentTimeMillis());
            db.update("leitner", cv, "hanzi=?", new String[] {hanzi});
        }
        cur.close();
    }

    public void restartStudy(String hanzi) {
        ContentValues cv = new ContentValues();
        cv.put("box", 0);
        cv.put("exp_time", System.currentTimeMillis());
        Cursor cur = db.rawQuery("SELECT * FROM leitner WHERE hanzi=?",
                new String[] {hanzi});

        if (cur.getCount() == 0) {
            cv.put("hanzi", hanzi);
            cv.put("exp_time", System.currentTimeMillis());
            cv.put("num_tested",1);
            cv.put("num_wrong", 1);
            cv.put("date_added", System.currentTimeMillis());
            db.insert("leitner", null, cv);
        }
        else {
            cur.moveToFirst();
            int num_tested = cur.getInt(cur.getColumnIndex("num_tested")) + 1;
            cv.put("num_tested", num_tested);
            int wrong = cur.getInt(cur.getColumnIndex("num_wrong"));
            cv.put("num_wrong", wrong+1);
            cv.put("streak", 0);
            db.update("leitner", cv, "hanzi=?", new String[] {hanzi});
        }
        cur.close();
    }

    public void setExpirationTimeForChar(String hanzi, long time) {
        ContentValues cv = new ContentValues();
        cv.put("exp_time", time);
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

    public void setBoxForChar(String hanzi, int box) {
        ContentValues cv = new ContentValues();
        cv.put("box", box);
        cv.put("exp_time", System.currentTimeMillis()+getExpiryTime(box));
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

    ////////////////////////////////////////
    // Use to find what character to study
    ////////////////////////////////////////

    // TestActivity
    public ArrayList<String> getAllToStudy(int limit) {
        ArrayList<String> res=new ArrayList<String>();

        Cursor cur = getCursorToStudy(limit);
        if (cur.moveToFirst()) {
            res.ensureCapacity(cur.getCount());
            do {
                res.add(cur.getString(cur.getColumnIndex("chinese")));
            } while(cur.moveToNext());
        }
        cur.close();
        return res;
    }

    // getCursorFilteredCharsToStudy <- ReviewList
    public Cursor getCursorCharsToStudy() {
        return db.rawQuery("SELECT char_pinyin.char_pinyin_id AS _id, char_pinyin.hanzi AS chinese," +
                "char_pinyin.pinyin AS pinyin, (CASE leitner.exp_time WHEN NULL THEN 0 ELSE leitner.exp_time END) AS exp_time, " +
                "(CASE leitner.box WHEN NULL THEN 0 ELSE leitner.box END) AS forced_box " +
                "FROM char_pinyin " +
                "OUTER LEFT JOIN leitner ON leitner.hanzi=char_pinyin.hanzi " +
                "WHERE ((box IS NULL) OR (box=0) OR (exp_time<?)) " +
                "AND ((char_pinyin.pinyin NOT LIKE '%5') OR (char_pinyin.hanzi IN (SELECT hanzi FROM char_pinyin GROUP BY hanzi HAVING COUNT(*) <> 2))) " +
                "ORDER BY forced_box DESC, exp_time ASC",
                new String[] {Long.toString(System.currentTimeMillis())});
    }

    public Cursor getCursorWordsToStudy() {
        return db.rawQuery("SELECT cword.cword_id AS _id, cword.chinese AS chinese, " +
                "cword.pinyin AS pinyin, " +
                "(CASE leitner.exp_time WHEN NULL THEN 0 ELSE leitner.exp_time END) AS exp_time, " +
                "(CASE leitner.box WHEN NULL THEN 0 ELSE leitner.box END) AS forced_box, " +
                "GROUP_CONCAT(eword.translation,', ') AS english " +
                "FROM cword " +
                "JOIN eword ON eword.cword_id=cword.cword_id " +
                "OUTER LEFT JOIN leitner ON leitner.hanzi=cword.chinese " +
                "WHERE (box IS NULL) OR (box=0) OR (exp_time<?) " +
                "GROUP BY chinese, eword.cword_id ORDER BY forced_box DESC, exp_time ASC",
                new String[] {Long.toString(System.currentTimeMillis())});
    }

    public Cursor getCursorToStudy() {
        return db.rawQuery("SELECT _id, chinese, exp_time, forced_box FROM (" +
                "SELECT 2*char_pinyin.char_pinyin_id AS _id, char_pinyin.hanzi AS chinese, " +
                "(CASE leitner.exp_time WHEN NULL THEN 0 ELSE leitner.exp_time END) AS exp_time, " +
                "(CASE leitner.box WHEN NULL THEN 0 ELSE leitner.box END) AS forced_box " +
                "FROM char_pinyin " +
                "OUTER LEFT JOIN leitner ON leitner.hanzi=char_pinyin.hanzi " +
                "WHERE (box IS NULL) OR (box=0) OR (exp_time<?) " +
                "UNION " +
                "SELECT 1+2*cword.cword_id AS _id, cword.chinese AS chinese," +
                "(CASE leitner.exp_time WHEN NULL THEN 0 ELSE leitner.exp_time END) AS exp_time, " +
                "(CASE leitner.box WHEN NULL THEN 0 ELSE leitner.box END) AS forced_box " +
                "FROM cword " +
                "OUTER LEFT JOIN leitner ON leitner.hanzi=cword.chinese " +
                "WHERE (box IS NULL) OR (box=0) OR (exp_time<?)) " +
                "GROUP BY chinese ORDER BY forced_box DESC, exp_time ASC",
                new String[] {Long.toString(System.currentTimeMillis()), Long.toString(System.currentTimeMillis())});
    }

    public Cursor getCursorToStudy(int limit) {
        return db.rawQuery("SELECT _id, chinese, exp_time, forced_box FROM (" +
                "SELECT 2*char_pinyin.char_pinyin_id AS _id, char_pinyin.hanzi AS chinese, " +
                "(CASE leitner.exp_time WHEN NULL THEN 0 ELSE leitner.exp_time END) AS exp_time, " +
                "(CASE leitner.box WHEN NULL THEN 0 ELSE leitner.box END) AS forced_box " +
                "FROM char_pinyin " +
                "OUTER LEFT JOIN leitner ON leitner.hanzi=char_pinyin.hanzi " +
                "WHERE (box IS NULL) OR (box=0) OR (exp_time<?) " +
                "UNION " +
                "SELECT 1+2*cword.cword_id AS _id, cword.chinese AS chinese," +
                "(CASE leitner.exp_time WHEN NULL THEN 0 ELSE leitner.exp_time END) AS exp_time, " +
                "(CASE leitner.box WHEN NULL THEN 0 ELSE leitner.box END) AS forced_box " +
                "FROM cword " +
                "OUTER LEFT JOIN leitner ON leitner.hanzi=cword.chinese " +
                "WHERE (box IS NULL) OR (box=0) OR (exp_time<?)) " +
                "GROUP BY chinese ORDER BY forced_box DESC, exp_time ASC LIMIT ?",
                new String[] {Long.toString(System.currentTimeMillis()), Long.toString(System.currentTimeMillis()), Integer.toString(limit)});
    }
}
