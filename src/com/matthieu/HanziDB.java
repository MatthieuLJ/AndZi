/******************************
 * Chinese vocabulary database
 */
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
import com.matthieu.widget.DBCheckProgressHandler;
import com.matthieu.widget.DBVocabularyResultsHandler;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class HanziDB extends SQLiteOpenHelper {
    private static final String TAG = "HanziDB";
    private static final int DATABASE_VERSION = 3;

    protected Context appContext;
    protected AndZiApplication app;
    private SQLiteDatabase db;

    public HanziDB(Context context, AndZiApplication application, String db_name) {
        super(context, db_name, null, DATABASE_VERSION);
        appContext = context;
        app = application;
        db = this.getWritableDatabase();
        db.execSQL("PRAGMA synchronous=OFF");
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
                "score INTEGER DEFAULT 0," +
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
                "hanzi VARCHAR(2) PRIMARY KEY NOT NULL," +
                "box INTEGER NOT NULL," +
                "exp_time INTEGER NOT NULL)");
        database.execSQL("CREATE INDEX leitner_hanzi ON leitner(hanzi)");

        cached_num_chars=0;
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int i, int i1) {
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
            db.execSQL("ALTER TABLE cword ADD COLUMN score INTEGER DEFAULT 0");
        }
    }

    @Override
    public synchronized void close() {
        super.close();
        db.releaseReference();
        db.close();
    }

    public AsyncTask<Void, Integer, Boolean> checkDatabase(final boolean fixme, final DBCheckProgressHandler handler) {
        AsyncTask<Void, Integer, Boolean> res = new AsyncTask<Void, Integer, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... voids) {
                boolean res = true;

                final int num_steps=13;
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
                    publishProgress((int)(current_step*100/num_steps));

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
                    publishProgress((int)(current_step*100/num_steps));

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
                    publishProgress((int)(current_step*100/num_steps));

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
                    publishProgress((int)(current_step*100/num_steps));

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
                    publishProgress((int)(current_step*100/num_steps));

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
                    publishProgress((int)(current_step*100/num_steps));

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
                    publishProgress((int)(current_step*100/num_steps));

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
                    publishProgress((int)(current_step*100/num_steps));

                    // check cword length is correct
                    c= db.rawQuery("SELECT cword_id FROM cword WHERE length(chinese) != length", null);
                    if (c.moveToFirst()) {
                        Log.w(TAG, "Found some words with the wrong size");
                        db.execSQL("UPDATE cword SET length = length(chinese)");
                    }
                    c.close();
                    current_step++;
                    publishProgress((int)(current_step*100/num_steps));

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
                    publishProgress((int)(current_step*100/num_steps));

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
                    publishProgress((int)(current_step*100/num_steps));

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
                    publishProgress((int)(current_step*100/num_steps));

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
                    current_step++;
                    publishProgress((int)(current_step*100/num_steps));

                }
                catch (SQLiteException exp) {
                    Log.d(TAG, "Ran into a problem while opening database");
                    Log.d(TAG, exp.toString());
                    res=false;
                }
                return res;
            }

            @Override
            protected void onProgressUpdate(Integer... values) {
                handler.progress(values[0]);
            }

            @Override
            protected void onPostExecute(Boolean aBoolean) {
                handler.finish(aBoolean);
            }
        };

        res.execute((Void[]) null);

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

    public ArrayList<Vocabulary> getCTranslations(String english) {
        return getCTranslations(english, SEQUENCE_EXACT);
    }

    public ArrayList<Vocabulary> getCTranslations(String english, int start) {
        // start is 2 for exactly the same sequence
        //          1 for starting with this sequence (but not the same)
        //          0 for not starting with this sequence
        //         -1 for don't care
        ArrayList<Vocabulary> res = new ArrayList<Vocabulary>();
        String query;
        ArrayList<String> query_arg=new ArrayList<String>();

        switch (start) {
            case SEQUENCE_EXACT:
                query="SELECT c.chinese AS chinese,c.pinyin AS pinyin, eword.translation AS translation " +
                        "FROM eword " +
                        "JOIN cword AS c ON c.cword_id=eword.cword_id " +
                        "WHERE translation=?";
                query_arg.add(english);
                break;
            case SEQUENCE_START:
                query="SELECT c.chinese AS chinese,c.pinyin AS pinyin, eword.translation AS translation " +
                        "FROM eword " +
                        "JOIN cword AS c ON c.cword_id=eword.cword_id " +
                        "WHERE translation LIKE ? AND translation!=?";
                query_arg.add(english+"%");
                query_arg.add(english);
                break;
            case SEQUENCE_NO_START:
                query="SELECT c.chinese AS chinese,c.pinyin AS pinyin, eword.translation AS translation " +
                        "FROM eword " +
                        "JOIN cword AS c ON c.cword_id=eword.cword_id " +
                        "WHERE translation LIKE ? AND translation NOT LIKE ?";
                query_arg.add("%"+english+"%");
                query_arg.add(english+"%");
                break;
            case SEQUENCE_ALL:
                query="SELECT c.chinese AS chinese,c.pinyin AS pinyin, eword.translation AS translation " +
                        "FROM eword " +
                        "JOIN cword AS c ON c.cword_id=eword.cword_id " +
                        "WHERE translation LIKE ?";
                query_arg.add("%"+english+"%");
                break;
            default:
                return res;
        }
        Cursor cur = db.rawQuery(query, query_arg.toArray(new String[query_arg.size()]));
        if (cur.moveToFirst()) {
            res.ensureCapacity(cur.getCount());
            do{
                res.add(new Vocabulary(cur.getString(cur.getColumnIndex("chinese")),
                        cur.getString(cur.getColumnIndex("pinyin")),
                        cur.getString(cur.getColumnIndex("translation"))));
            }while(cur.moveToNext());
        }

        cur.close();
        return res;
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

    public ArrayList<Vocabulary> getWordsWithPinyinForTesting(String hanzi) {
            ArrayList<Vocabulary> res = new ArrayList<Vocabulary>();
            Vocabulary to_add=null;
            Cursor cur = db.rawQuery("SELECT * " +
                    "FROM cword_segment " +
                    "JOIN char_pinyin ON char_pinyin.char_pinyin_id=cword_segment.char_pinyin_id " +
                    "JOIN cword ON cword.cword_id=cword_segment.cword_id " +
                    "JOIN eword ON eword.cword_id=cword.cword_id " +
                    "WHERE char_pinyin.hanzi=? GROUP BY eword.eng_id " +
                    "ORDER BY cword.score ASC", new String[] {hanzi});
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

    protected ArrayList<Integer> getWordIdWithPinyin(String hanzi) {
        ArrayList<Integer> res = new ArrayList<Integer>();
        Cursor cur = db.rawQuery("SELECT DISTINCT cword_segment.cword_id AS cword_id " +
                "FROM cword_segment " +
                "JOIN char_pinyin ON char_pinyin.char_pinyin_id=cword_segment.char_pinyin_id " +
                "WHERE char_pinyin.hanzi=?", new String[] {hanzi});
        if (cur.moveToFirst()) {
            res.ensureCapacity(cur.getCount());
            do{
                res.add(cur.getInt(cur.getColumnIndex("cword_id")));
            }while(cur.moveToNext());
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
            res.ensureCapacity(cur.getCount());
            do{
                res.add(cur.getInt(cur.getColumnIndex("cword_id")));
            }while(cur.moveToNext());
        }
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

    protected ArrayList<Integer> getEWordId(int cword_id) {
        ArrayList<Integer> res=new ArrayList<Integer>();
        Cursor cur = db.rawQuery("SELECT eng_id " +
                "FROM eword " +
                "WHERE cword_id=?", new String[] {Integer.toString(cword_id)});
        if (cur.moveToFirst()) {
            res.ensureCapacity(cur.getCount());
            do{
                res.add(cur.getInt(cur.getColumnIndex("eng_id")));
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

    public ArrayList<String> getAllChar() {
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

    protected ArrayList<Vocabulary> getWordsWithSequence(ArrayList<StringElement> elements) {
        return getWordsWithSequence(elements, SEQUENCE_ALL, false);
    }

    protected ArrayList<Vocabulary> getWordsWithSequence(ArrayList<StringElement> elements, int start, boolean filtered) {
        final ArrayList<Vocabulary> res=new ArrayList<Vocabulary>();
        final CountDownLatch signal=new CountDownLatch(1);

        getWordsWithSequence(elements, start, filtered, new DBVocabularyResultsHandler() {
            @Override
            public void handle_more_results(ArrayList<Vocabulary> data) {
                res.addAll(data);
            }

            @Override
            public void handle_end_of_results() {
                signal.countDown();
            }
        });
        try {
            signal.await();
        } catch (InterruptedException ignored) { }
        return res;
    }

    public AsyncTask<Void,ArrayList<Vocabulary>,Void> getWordsWithSequence(final ArrayList<StringElement> elements, final int start, final DBVocabularyResultsHandler handler) {
        return getWordsWithSequence(elements, start, false, handler);
    }

    public static final int SEQUENCE_START=1;
    public static final int SEQUENCE_NO_START=0;
    public static final int SEQUENCE_EXACT=2;
    public static final int SEQUENCE_ALL=-1;

    protected AsyncTask<Void,ArrayList<Vocabulary>,Void> getWordsWithSequence(final ArrayList<StringElement> elements, final int start, boolean filtered, final DBVocabularyResultsHandler handler) {
        // start is 2 for exactly the same sequence
        //          1 for starting with this sequence (but not the same)
        //          0 for not starting with this sequence
        //         -1 for don't care

        final boolean lesson_filtered = filtered && ((lesson_filter.size() > 0) && (lesson_filter_enabled));

        AsyncTask<Void,ArrayList<Vocabulary>,Void> res = new AsyncTask<Void,ArrayList<Vocabulary>,Void>() {
            ArrayList<Vocabulary> query_results=new ArrayList<Vocabulary>();

            @Override
            protected Void doInBackground(Void... voids) {
                String query="";
                ArrayList<String> query_args=new ArrayList<String>();

                if (elements.size()==0)
                    return null;

                if (elements.size()==1) {
                    switch (elements.get(0).type) {
                        case StringElement.CHINESE:
                            if (start == SEQUENCE_EXACT) {
                                query="SELECT DISTINCT cword.cword_id AS cword_id, cword.chinese AS chinese, cword.pinyin AS pinyin, eword.translation AS english " +
                                        "FROM eword " +
                                        "JOIN cword ON cword.cword_id=eword.cword_id ";
                                if (lesson_filtered) {
                                    query += "JOIN lesson_word ON eword.eng_id=lesson_word.eng_id " + where_filter + " AND ";
                                }
                                else {
                                    query += "WHERE ";
                                }
                                query += "chinese=? ORDER BY cword.pinyin, cword.cword_id";
                                query_args.add(elements.get(0).content);
                                break;
                            }
                            query="SELECT DISTINCT cword.cword_id AS cword_id, cword.chinese AS chinese, cword.pinyin AS pinyin, eword.translation AS english " +
                                    "FROM eword " +
                                    "JOIN cword ON cword.cword_id=eword.cword_id " +
                                    "JOIN cword_segment ON cword.cword_id=cword_segment.cword_id " +
                                    "JOIN char_pinyin ON char_pinyin.char_pinyin_id=cword_segment.char_pinyin_id ";
                            if (lesson_filtered) {
                                query += "JOIN lesson_word ON eword.eng_id=lesson_word.eng_id " + where_filter + " AND ";
                            }
                            else {
                                query += "WHERE ";
                            }
                            query += "char_pinyin.hanzi=? ";
                            query_args.add(elements.get(0).content);
                            if (start == SEQUENCE_START) {
                                query+="AND cword_segment.pos=0 AND cword.chinese!=? ";
                                query_args.add(elements.get(0).content);
                            }
                            else if (start == SEQUENCE_NO_START) {
                                query+="AND cword.chinese NOT LIKE ? ";
                                query_args.add(elements.get(0).content+"%");
                            }
                            query+="ORDER BY cword.pinyin, cword.cword_id";
                            break;
                        case StringElement.PINYIN_WITH_TONE:
                            query="SELECT DISTINCT cword.cword_id AS cword_id, cword.chinese AS chinese, cword.pinyin AS pinyin, eword.translation AS english " +
                                    "FROM eword " +
                                    "JOIN cword ON cword.cword_id=eword.cword_id " +
                                    "JOIN cword_segment ON cword.cword_id=cword_segment.cword_id " +
                                    "JOIN char_pinyin ON char_pinyin.char_pinyin_id=cword_segment.char_pinyin_id ";
                            if (lesson_filtered) {
                                query += "JOIN lesson_word ON eword.eng_id=lesson_word.eng_id " + where_filter + " AND ";
                            }
                            else {
                                query += "WHERE ";
                            }
                            query += "char_pinyin.pinyin=? ";
                            query_args.add(elements.get(0).content);
                            if (start == SEQUENCE_START) {
                                query+="AND cword_segment.pos=0 AND cword.pinyin!=? ";
                                query_args.add(elements.get(0).content);
                            }
                            else if (start == SEQUENCE_NO_START) {
                                query+="AND cword.pinyin NOT LIKE ? ";
                                query_args.add(elements.get(0).content+"%");
                            }
                            else if (start == SEQUENCE_EXACT) {
                                query+="AND cword.pinyin=? ";
                                query_args.add(elements.get(0).content);
                            }
                            query+="ORDER BY cword.pinyin, cword.cword_id";
                            break;
                        case StringElement.PINYIN_WITHOUT_TONE:
                            query="SELECT DISTINCT cword.cword_id AS cword_id, cword.chinese AS chinese, cword.pinyin AS pinyin, eword.translation AS english " +
                                    "FROM eword " +
                                    "JOIN cword ON cword.cword_id=eword.cword_id " +
                                    "JOIN cword_segment ON cword.cword_id=cword_segment.cword_id " +
                                    "JOIN char_pinyin ON char_pinyin.char_pinyin_id=cword_segment.char_pinyin_id ";
                            if (lesson_filtered) {
                                query += "JOIN lesson_word ON eword.eng_id=lesson_word.eng_id " + where_filter + " AND ";
                            }
                            else {
                                query += "WHERE ";
                            }
                            query += "char_pinyin.pinyin LIKE ? ";
                            query_args.add(elements.get(0).content+"_");
                            if (start == SEQUENCE_START) {
                                query+="AND cword_segment.pos=0 AND cword.pinyin NOT LIKE ? ";
                                query_args.add(elements.get(0).content+"_");
                            }
                            else if (start == SEQUENCE_NO_START) {
                                query+="AND cword.pinyin NOT LIKE ? ";
                                query_args.add(elements.get(0).content+"%");
                            }
                            else if (start == SEQUENCE_EXACT) {
                                query+="AND cword.pinyin LIKE ? ";
                                query_args.add(elements.get(0).content+"_");
                            }
                            query+="ORDER BY cword.pinyin, cword.cword_id";
                            break;
                        case StringElement.INCOMPLETE_PINYIN:
                            query="SELECT DISTINCT cword.cword_id AS cword_id, cword.chinese AS chinese, cword.pinyin AS pinyin, eword.translation AS english " +
                                    "FROM eword " +
                                    "JOIN cword ON cword.cword_id=eword.cword_id " +
                                    "JOIN cword_segment ON cword.cword_id=cword_segment.cword_id " +
                                    "JOIN char_pinyin ON char_pinyin.char_pinyin_id=cword_segment.char_pinyin_id ";
                            if (lesson_filtered) {
                                query += "JOIN lesson_word ON eword.eng_id=lesson_word.eng_id " + where_filter + " AND ";
                            }
                            else {
                                query += "WHERE ";
                            }
                            query += "char_pinyin.pinyin LIKE ? ";
                            query_args.add(elements.get(0).content+"%");
                            if (start == SEQUENCE_START)
                                query+="AND cword_segment.pos=0 AND length(cword.chinese)!=1 ";
                            else if (start == SEQUENCE_NO_START) {
                                query+="AND cword.pinyin NOT LIKE ? ";
                                query_args.add(elements.get(0).content+"%");
                            }
                            else if (start == SEQUENCE_EXACT) {
                                query+="AND length(cword.chinese)=1 ";
                            }
                            query+="ORDER BY cword.pinyin, cword.cword_id";
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
                        if (start == SEQUENCE_START) {
                            query="SELECT DISTINCT cword.cword_id AS cword_id, cword.chinese AS chinese, cword.pinyin AS pinyin, eword.translation AS english " +
                                    "FROM eword " +
                                    "JOIN cword ON cword.cword_id=eword.cword_id ";
                            if (lesson_filtered) {
                                query += "JOIN lesson_word ON eword.eng_id=lesson_word.eng_id " + where_filter + " AND ";
                            }
                            else {
                                query += "WHERE ";
                            }
                            query += "chinese LIKE ? AND chinese!=? " +
                                    "ORDER BY cword.pinyin, cword.cword_id";
                            query_args.add(concat+"%");
                            query_args.add(concat);
                        } else if (start == SEQUENCE_NO_START) {
                            query="SELECT DISTINCT cword.cword_id AS cword_id, cword.chinese AS chinese, cword.pinyin AS pinyin, eword.translation AS english " +
                                    "FROM eword " +
                                    "JOIN cword ON cword.cword_id=eword.cword_id ";
                            if (lesson_filtered) {
                                query += "JOIN lesson_word ON eword.eng_id=lesson_word.eng_id " + where_filter + " AND ";
                            }
                            else {
                                query += "WHERE ";
                            }
                            query += "chinese LIKE ? AND chinese NOT LIKE ? " +
                                    "ORDER BY cword.pinyin, cword.cword_id";
                            query_args.add("%"+concat+"%");
                            query_args.add(concat+"%");
                        } else if (start == SEQUENCE_EXACT) {
                            query="SELECT DISTINCT cword.cword_id AS cword_id, cword.chinese AS chinese, cword.pinyin AS pinyin, eword.translation AS english " +
                                    "FROM eword " +
                                    "JOIN cword ON cword.cword_id=eword.cword_id ";
                            if (lesson_filtered) {
                                query += "JOIN lesson_word ON eword.eng_id=lesson_word.eng_id " + where_filter + " AND ";
                            }
                            else {
                                query += "WHERE ";
                            }
                            query += "chinese=?" +
                                    "ORDER BY cword.pinyin, cword.cword_id";
                            query_args.add(concat);
                        } else {
                            query="SELECT DISTINCT cword.cword_id AS cword_id, cword.chinese AS chinese, cword.pinyin AS pinyin, eword.translation AS english " +
                                    "FROM eword " +
                                    "JOIN cword ON cword.cword_id=eword.cword_id ";
                            if (lesson_filtered) {
                                query += "JOIN lesson_word ON eword.eng_id=lesson_word.eng_id " + where_filter + " AND ";
                            }
                            else {
                                query += "WHERE ";
                            }
                            query += "chinese LIKE ? " +
                                    "ORDER BY cword.pinyin, cword.cword_id";
                            query_args.add("%"+concat+"%");
                        }

                    } else if ((i==elements.size()) && (type == StringElement.PINYIN_WITH_TONE)) {
                        // only pinyins with tones....
                        if (start == SEQUENCE_START) {
                            query="SELECT DISTINCT cword.cword_id AS cword_id, cword.chinese AS chinese, cword.pinyin AS pinyin, eword.translation AS english " +
                                    "FROM eword " +
                                    "JOIN cword ON cword.cword_id=eword.cword_id ";
                            if (lesson_filtered) {
                                query += "JOIN lesson_word ON eword.eng_id=lesson_word.eng_id " + where_filter + " AND ";
                            }
                            else {
                                query += "WHERE ";
                            }
                            query += "pinyin LIKE ? AND pinyin!=? " +
                                    "ORDER BY cword.pinyin, cword.cword_id";
                            query_args.add(concat+"%");
                            query_args.add(concat);
                        } else if (start == SEQUENCE_NO_START) {
                            query="SELECT DISTINCT cword.cword_id AS cword_id, cword.chinese AS chinese, cword.pinyin AS pinyin, eword.translation AS english " +
                                    "FROM eword " +
                                    "JOIN cword ON cword.cword_id=eword.cword_id ";
                            if (lesson_filtered) {
                                query += "JOIN lesson_word ON eword.eng_id=lesson_word.eng_id " + where_filter + " AND ";
                            }
                            else {
                                query += "WHERE ";
                            }
                            query += "pinyin LIKE ? AND pinyin NOT LIKE ? " +
                                    "ORDER BY cword.pinyin, cword.cword_id";
                            query_args.add("%"+concat+"%");
                            query_args.add(concat+"%");
                        } else if (start == SEQUENCE_EXACT) {
                            query="SELECT DISTINCT cword.cword_id AS cword_id, cword.chinese AS chinese, cword.pinyin AS pinyin, eword.translation AS english " +
                                    "FROM eword " +
                                    "JOIN cword ON cword.cword_id=eword.cword_id ";
                            if (lesson_filtered) {
                                query += "JOIN lesson_word ON eword.eng_id=lesson_word.eng_id " + where_filter + " AND ";
                            }
                            else {
                                query += "WHERE ";
                            }
                            query += "pinyin=? " +
                                    "ORDER BY cword.pinyin, cword.cword_id";
                            query_args.add(concat);
                        } else {
                            query="SELECT DISTINCT cword.cword_id AS cword_id, cword.chinese AS chinese, cword.pinyin AS pinyin, eword.translation AS english " +
                                    "FROM eword " +
                                    "JOIN cword ON cword.cword_id=eword.cword_id ";
                            if (lesson_filtered) {
                                query += "JOIN lesson_word ON eword.eng_id=lesson_word.eng_id " + where_filter + " AND ";
                            }
                            else {
                                query += "WHERE ";
                            }
                            query += "pinyin LIKE ? " +
                                    "ORDER BY cword.pinyin, cword.cword_id";
                            query_args.add("%"+concat+"%");
                        }

                    } else {
                        // mixed up
                        query="SELECT DISTINCT cword.cword_id AS cword_id, cword.chinese AS chinese, cword.pinyin AS pinyin, eword.translation AS english FROM (SELECT * FROM cword ";
                        if (start == SEQUENCE_EXACT) {
                            query += " WHERE length(chinese)="+elements.size();
                        } else if (start == SEQUENCE_START) {
                            query += " WHERE length(chinese)!="+elements.size();
                        }
                        query += ") AS cword JOIN eword ON cword.cword_id=eword.cword_id ";
                        for (i=0; i<elements.size(); i++) {
                            query+=" JOIN (SELECT * FROM char_pinyin JOIN cword_segment ON char_pinyin.char_pinyin_id=cword_segment.char_pinyin_id WHERE ";
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
                                if (start == SEQUENCE_START)
                                    query+=" AND cword_segment.pos=0";
                                else if (start == SEQUENCE_NO_START)
                                    query+=" AND cword_segment.pos>0";
                            }
                            query+=") AS elem"+i;
                            if (i>0) {
                                query+=" ON (elem"+i+".pos=elem"+(i-1)+".pos+1) AND (elem"+i+".cword_id=elem"+(i-1)+".cword_id)";
                            }
                            else {
                                query+=" ON (elem"+i+".cword_id=cword.cword_id)";
                            }
                        }
                        if (lesson_filtered) {
                            query += "JOIN lesson_word ON eword.eng_id=lesson_word.eng_id " + where_filter;
                        }
                        query += " ORDER BY cword.pinyin, cword.cword_id";
                    }
                }

                Log.d(TAG, "Query for incomplete search is ["+query+"] with args "+query_args.toString());

                int current_offset=0, limit_size=20, num_records;

                do {
                    if (isCancelled()) {
                        Log.d(TAG, "getWordIdWithSequence cancelled");
                        return null;
                    }

                    query_results.clear();
                    Vocabulary current_voc=null;
                    int current_cword_id=-1;

                    num_records=0;
                    String tmp_query=query+" LIMIT "+limit_size+" OFFSET "+current_offset;
                    Cursor cur = db.rawQuery(tmp_query, query_args.toArray(new String[query_args.size()]));

                    if (cur.moveToFirst()) {
                        query_results.ensureCapacity(cur.getCount());
                        do{
                            int cword_id = cur.getInt(cur.getColumnIndex("cword_id"));
                            String english=cur.getString(cur.getColumnIndex("english"));
                            if (current_cword_id==cword_id) {
                                current_voc.english.add(english);
                            }
                            else {
                                String chinese=cur.getString(cur.getColumnIndex("chinese"));
                                String pinyin =cur.getString(cur.getColumnIndex("pinyin"));
                                if (current_voc!=null) {
                                    query_results.add(current_voc);
                                }
                                current_voc = new Vocabulary(chinese, pinyin);
                                current_cword_id=cword_id;
                                current_voc.english.add(english);
                            }

                            num_records++;
                        }while(cur.moveToNext());
                    }

                    cur.close();
                    if (current_voc!=null) {
                        query_results.add(current_voc);
                    }

                    if (isCancelled()) {
                        Log.d(TAG, "getWordIdWithSequence cancelled");
                        return null;
                    }

                    current_offset+=limit_size;
                    publishProgress(query_results);
                } while (num_records==limit_size);

                return (Void) null;
            }

            @Override
            protected void onProgressUpdate(ArrayList<Vocabulary>... values) {
                if ((query_results.size()>0) && (!isCancelled()))
                    handler.handle_more_results((ArrayList<Vocabulary>) query_results.clone());
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                handler.handle_end_of_results();
            }
        };

        res.execute((Void[]) null);

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
        if (pinyin == null)
            return;

        addVocabulary(voc.english,chinese,pinyin);
    }

    protected int addCword(ArrayList<String> chinese, ArrayList<String> pinyin) {
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
        cv.put("length", chinese_str.codePointCount(0, chinese_str.length()));
        db.insert("cword", null, cv);
        //db.insertWithOnConflict("cword", null, cv, SQLiteDatabase.CONFLICT_IGNORE);

        cword_id = getWordId(chinese_str, pinyin_str);
        if (cword_id == -1) {
            Log.w(TAG, "Add a cword id, but got no ID");
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
        Integer cword_id;

        for (i = 0; i < chinese.size(); i++)
        {
            chinese_str += chinese.get(i);
            pinyin_str += pinyin.get(i);
        }

        cword_id = getWordId(chinese_str, pinyin_str);
        if (cword_id == -1) {
            cword_id = addCword(chinese, pinyin);
        }

        if (english != null) {
            addVocabulary(english, cword_id);
        }

        cached_num_chars=-1;

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
        int cword_id;
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

        if (cword_id != -1) {
            ArrayList<Integer> eng_ids = getEWordId(cword_id);
            for (int eng_id:eng_ids)
                delEword(eng_id);
            checkIsolatedCword(cword_id);
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
        if (pinyin == null)
            return;

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
        ArrayList<Integer> char_pinyin_id = new ArrayList<Integer>();

        Cursor cur = db.rawQuery("SELECT char_pinyin_id FROM cword_segment WHERE cword_id=?", new String[] {Integer.toString(cword_id)});
        if (cur.moveToFirst()) {
            do{
                char_pinyin_id.add(cur.getInt(cur.getColumnIndex("char_pinyin_id")));
            }while(cur.moveToNext());
        }
        cur.close();
        db.delete("cword", "cword_id=?", new String[] {Integer.toString(cword_id)});
        db.delete("cword_segment", "cword_id=?", new String[] {Integer.toString(cword_id)});

        for (int cpid : char_pinyin_id) {
            if (getCwordWithChar(cpid).size() == 0) {
                delCharPinyin(cpid);
            }
        }
    }

    protected void checkIsolatedCword(int cword_id) {
        if (getTranslations(cword_id) == null) {
            delCword(cword_id);
            cached_num_chars=-1;
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
                            setExpirationTimeForChar(c, other_db.getExpirationTimeForChar(c));
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

        res.execute((Void[])null);

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
                    else {
                        ArrayList<String> all_lessons=getLessons();
                        all_lessons.add("");
                        vocabulary=getVocabularyInLessons(all_lessons);
                    }

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
                                long t = getExpirationTimeForChar(c);
                                new_db.setBoxForChar(c, b);
                                new_db.setExpirationTimeForChar(c, t);
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
    protected String where_filter; // = "WHERE lesson_word.lesson_id IN ( ... )"

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
            Vocabulary to_add=null;
            Cursor cur = db.rawQuery("SELECT DISTINCT cword.cword_id AS cword_id, cword.pinyin AS pinyin, eword.translation AS translation " +
                    "FROM (SELECT eng_id FROM lesson_word " + where_filter + ") AS lesson_word " +
                    "JOIN eword ON eword.eng_id=lesson_word.eng_id " +
                    "JOIN cword ON cword.cword_id=eword.cword_id " +
                    "WHERE cword.chinese=? ORDER BY cword.cword_id", new String[] {chinese});
            int id_column_index      = cur.getColumnIndex("cword_id");
            int pinyin_column_index  = cur.getColumnIndex("pinyin");
            int english_column_index = cur.getColumnIndex("translation");
            int current_id = -1;
            if (cur.moveToFirst()) {
                do{
                    int new_id = cur.getInt(id_column_index);
                    String pinyin =  cur.getString(pinyin_column_index);
                    String english = cur.getString(english_column_index);

                    if (new_id != current_id) {
                        if (to_add != null)
                            res.add(to_add);
                        to_add = new Vocabulary(chinese,pinyin, new ArrayList<String>());
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
        else
            return getTranslations(chinese);
    }

    public Vocabulary getFilteredTranslations(String chinese, String pinyin) {
        if ((lesson_filter.size() > 0) && (lesson_filter_enabled)) {
            Vocabulary res = null;
            Cursor cur = db.rawQuery("SELECT DISTINCT cword.cword_id AS cword_id, cword.pinyin AS pinyin, eword.translation AS translation " +
                    "FROM (SELECT eng_id FROM lesson_word " + where_filter + ") AS lesson_word " +
                    "JOIN eword ON eword.eng_id=lesson_word.eng_id " +
                    "JOIN cword ON cword.cword_id=eword.cword_id " +
                    "WHERE cword.chinese=? AND cword.pinyin=?", new String[]{chinese, pinyin});
            int english_column_index = cur.getColumnIndex("translation");
            if (cur.moveToFirst()) {
                res = new Vocabulary(chinese,pinyin);
                do{
                    String english = cur.getString(english_column_index);
                    res.english.add(english);
                }while(cur.moveToNext());
            }
            cur.close();
            return res;
        }
        else
            return getTranslations(chinese, pinyin);
    }

    public ArrayList<Vocabulary> getFilteredCTranslations(String english) {
        return getFilteredCTranslations(english, SEQUENCE_EXACT);
    }

    public ArrayList<Vocabulary> getFilteredCTranslations(String english, int start) {
        if ((lesson_filter.size() > 0) && (lesson_filter_enabled)) {
            ArrayList<Vocabulary> res = new ArrayList<Vocabulary>();
            Cursor cur = db.rawQuery("SELECT DISTINCT cword.chinese AS chinese,cword.pinyin AS pinyin " +
                    "FROM (SELECT eng_id FROM lesson_word " + where_filter + ") AS lesson_word " +
                    "JOIN (SELECT * FROM eword WHERE translation=?) AS eword ON eword.eng_id=lesson_word.eng_id " +
                    "JOIN cword ON cword.cword_id=eword.cword_id "
                    , new String[] {english});
            if (cur.moveToFirst()) {
                res.ensureCapacity(cur.getCount());
                do{
                    res.add(new Vocabulary
                            (cur.getString(cur.getColumnIndex("chinese")), cur.getString(cur.getColumnIndex("pinyin")),english));
                }while(cur.moveToNext());
            }

            cur.close();
            return res;
        }
        else
            return getCTranslations(english, start);
    }

    public ArrayList<Vocabulary> getFilteredWordsWithPinyin(String hanzi) {
        if ((lesson_filter.size() > 0) && (lesson_filter_enabled)) {
            ArrayList<Vocabulary> res = new ArrayList<Vocabulary>();
            Vocabulary to_add=null;
            Cursor cur = db.rawQuery("SELECT DISTINCT cword_segment.cword_id AS cword_id, cword.chinese AS chinese, " +
                    "cword.pinyin AS pinyin, eword.translation AS translation " +
                    "FROM (SELECT eng_id FROM lesson_word " + where_filter + ") AS lesson_word " +
                    "JOIN eword ON eword.eng_id=lesson_word.eng_id " +
                    "JOIN cword ON cword.cword_id=eword.cword_id " +
                    "JOIN cword_segment ON cword_segment.cword_id=cword.cword_id " +
                    "JOIN char_pinyin ON char_pinyin.char_pinyin_id=cword_segment.char_pinyin_id " +
                    "WHERE char_pinyin.hanzi=?", new String[] {hanzi});
            int id_column_index      = cur.getColumnIndex("cword_id");
            int chinese_column_index = cur.getColumnIndex("chinese");
            int pinyin_column_index  = cur.getColumnIndex("pinyin");
            int english_column_index = cur.getColumnIndex("translation");
            int current_id = -1;
            if (cur.moveToFirst()) {
                do{
                    int new_id = cur.getInt(id_column_index);
                    String chinese = cur.getString(chinese_column_index);
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
        else {
            return getWordsWithPinyin(hanzi);
        }
    }

    public ArrayList<Vocabulary> getFilteredWordsWithPinyinForTesting(String hanzi) {
            if ((lesson_filter.size() > 0) && (lesson_filter_enabled)) {
                ArrayList<Vocabulary> res = new ArrayList<Vocabulary>();
                Vocabulary to_add=null;
                Cursor cur = db.rawQuery("SELECT DISTINCT cword_segment.cword_id AS cword_id, cword.chinese AS chinese, " +
                        "cword.pinyin AS pinyin, eword.translation AS translation " +
                        "FROM (SELECT eng_id FROM lesson_word " + where_filter + ") AS lesson_word " +
                        "JOIN eword ON eword.eng_id=lesson_word.eng_id " +
                        "JOIN cword ON cword.cword_id=eword.cword_id " +
                        "JOIN cword_segment ON cword_segment.cword_id=cword.cword_id " +
                        "JOIN char_pinyin ON char_pinyin.char_pinyin_id=cword_segment.char_pinyin_id " +
                        "WHERE char_pinyin.hanzi=? ORDER BY cword.score ASC", new String[] {hanzi});
                int id_column_index      = cur.getColumnIndex("cword_id");
                int chinese_column_index = cur.getColumnIndex("chinese");
                int pinyin_column_index  = cur.getColumnIndex("pinyin");
                int english_column_index = cur.getColumnIndex("translation");
                int current_id = -1;
                if (cur.moveToFirst()) {
                    do{
                        int new_id = cur.getInt(id_column_index);
                        String chinese = cur.getString(chinese_column_index);
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
            else {
                return getWordsWithPinyinForTesting(hanzi);
            }
        }

    public ArrayList<Vocabulary> getFilteredWordsWithPinyin(String hanzi, String pinyin) {
        if ((lesson_filter.size() > 0) && (lesson_filter_enabled)) {
            ArrayList<Vocabulary> res = new ArrayList<Vocabulary>();
            Vocabulary to_add=null;
            Cursor cur = db.rawQuery("SELECT DISTINCT cword_segment.cword_id AS cword_id, cword.chinese AS chinese, " +
                    "cword.pinyin AS pinyin, eword.translation AS translation " +
                    "FROM (SELECT eng_id FROM lesson_word " + where_filter + ") AS lesson_word " +
                    "JOIN eword ON eword.eng_id=lesson_word.eng_id " +
                    "JOIN cword ON cword.cword_id=eword.cword_id " +
                    "JOIN cword_segment ON cword_segment.cword_id=cword.cword_id " +
                    "JOIN char_pinyin ON char_pinyin.char_pinyin_id=cword_segment.char_pinyin_id " +
                    "WHERE char_pinyin.hanzi=? AND char_pinyin.pinyin=?", new String[] {hanzi, pinyin});
            int id_column_index      = cur.getColumnIndex("cword_id");
            int chinese_column_index = cur.getColumnIndex("chinese");
            int pinyin_column_index  = cur.getColumnIndex("pinyin");
            int english_column_index = cur.getColumnIndex("translation");
            int current_id = -1;
            if (cur.moveToFirst()) {
                do{
                    int new_id = cur.getInt(id_column_index);
                    String chinese = cur.getString(chinese_column_index);
                    String pinyin_w =  cur.getString(pinyin_column_index);
                    String english = cur.getString(english_column_index);

                    if (new_id != current_id) {
                        if (to_add != null)
                            res.add(to_add);
                        to_add = new Vocabulary(chinese,pinyin_w, new ArrayList<String>());
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
        else {
            return getWordsWithPinyin(hanzi, pinyin);
        }
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
            cur = db.rawQuery("SELECT COUNT(char_pinyin.hanzi) AS count FROM char_pinyin " +
                    "LEFT OUTER JOIN leitner ON leitner.hanzi=char_pinyin.hanzi " +
                    "WHERE (box IS NULL) OR (box=0)", null);


        else
            cur = db.rawQuery("SELECT COUNT(hanzi) AS count FROM leitner WHERE box=?", new String[] {Integer.toString(box)});

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

        Cursor cur = db.rawQuery("SELECT COUNT(hanzi) AS count FROM leitner WHERE box>=?", new String[] {Integer.toString(box)});
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
            Cursor cur = db.rawQuery("SELECT COUNT(hanzi) AS count FROM leitner WHERE box=? AND exp_time<?",
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
            cur = db.rawQuery("SELECT COUNT(char_pinyin.hanzi) AS count FROM char_pinyin " +
                    "LEFT OUTER JOIN leitner ON leitner.hanzi=char_pinyin.hanzi " +
                    "WHERE (exp_time<?) OR (box IS NULL)",
                    new String[] {Long.toString(System.currentTimeMillis())});
        else
            cur = db.rawQuery("SELECT COUNT(hanzi) AS count FROM leitner WHERE box>=? AND exp_time<?",
                    new String[] {Integer.toString(box), Long.toString(System.currentTimeMillis())});

        cur.moveToFirst();
        res= cur.getInt(cur.getColumnIndex("count"));

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

    protected long getExpirationTimeForChar(String hanzi) {
        long res;

        Cursor cur = db.rawQuery("SELECT exp_time FROM leitner WHERE hanzi=?",
                new String[] {hanzi});
        if (cur.moveToFirst()) {
            do{
                res = cur.getLong(cur.getColumnIndex("exp_time"));
            }while(cur.moveToNext());
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

        double multiplicator = 1;//Math.max(Math.log10(getNumChars()), 1);
        // at the minimum this will give a delay of 12 hours and will double per box and per power of 10
        return (long) (box_delay * Math.pow(2, box + multiplicator));

        //System.out.println("With "+getNumChars()+" characters, mult is "+multiplicator+", so delay is "+(res/(1000*60*60)+" hours for box "+box));
    }

    protected ArrayList<String> getMostAdvancedExpiring(int num) {
        Cursor cur;
        ArrayList<String> res=new ArrayList<String>();

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
        if (getBoxForChar(hanzi)==0)
            betterBox(hanzi);

        Cursor cur = db.rawQuery("SELECT box FROM leitner WHERE hanzi=?",
                new String[] {hanzi});
        ContentValues cv = new ContentValues();
        cv.put("exp_time", System.currentTimeMillis()+getExpiryTime(getBoxForChar(hanzi)));

        if (cur.getCount() == 0) {
            cv.put("hanzi", hanzi);
            cv.put("box", 0);
            db.insert("leitner", null, cv);
        }
        else {
            cv.put("exp_time", System.currentTimeMillis()+getExpiryTime(getBoxForChar(hanzi)));
            db.update("leitner", cv, "hanzi=?", new String[] {hanzi});
        }
        cur.close();
    }

    public void betterBox(String hanzi) {
        int current_box = getBoxForChar(hanzi);
        current_box ++;

        ContentValues cv = new ContentValues();
        cv.put("box", current_box);
        cv.put("exp_time", System.currentTimeMillis()+getExpiryTime(current_box));
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
        cv.put("exp_time", System.currentTimeMillis());
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

    protected void setExpirationTimeForChar(String hanzi, long time) {
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

    protected void setBoxForChar(String hanzi, int box) {
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

    protected void setExpired(String hanzi) {
        setExpirationTimeForChar(hanzi, System.currentTimeMillis());
    }

    public void guessedWord(boolean correct, String hanzi, String pinyin) {
        String update_score = correct ? "score=score+1" : "score=score-1";
        db.execSQL("UPDATE cword SET "+update_score+" WHERE chinese=? AND pinyin=?", new String[] {hanzi, pinyin});
    }

    public void guessedWord(boolean correct, String hanzi) {
        String update_score = correct ? "score=score+1" : "score=score-1";
        db.execSQL("UPDATE cword SET "+update_score+" WHERE chinese=?", new String[] {hanzi});

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

        cur = db.rawQuery("SELECT char_pinyin.hanzi AS hanzi, (CASE leitner.exp_time WHEN NULL THEN 0 ELSE leitner.exp_time END) AS exp_time " +
                "FROM char_pinyin " +
                "OUTER LEFT JOIN leitner ON leitner.hanzi=char_pinyin.hanzi " +
                "WHERE (box IS NULL) OR (box=0) OR (exp_time<?) " +
                "ORDER BY exp_time ASC",
                new String[] {Long.toString(System.currentTimeMillis())});
        if (cur.moveToFirst()) {
            res.ensureCapacity(cur.getCount());
            do {
                res.add(cur.getString(cur.getColumnIndex("hanzi")));
            } while(cur.moveToNext());
        }
        cur.close();

        return res;

    }

    public void getMoreStudy(int how_many) {
        ContentValues cv = new ContentValues();
        cv.put("exp_time", System.currentTimeMillis());
        db.update("leitner", cv, "hanzi IN (SELECT hanzi FROM leitner WHERE exp_time>? ORDER BY exp_time DESC LIMIT ?)",
                new String[] {Long.toString(System.currentTimeMillis()), Integer.toString(how_many)});
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

        if ((lesson_filter.size() > 0) && (lesson_filter_enabled)) {
            Cursor cur = db.rawQuery("SELECT DISTINCT char_pinyin.hanzi AS hanzi, " +
                    "(CASE leitner.box WHEN NULL THEN 0 ELSE leitner.exp_time END) AS exp_time " +
                    "FROM (SELECT eng_id FROM lesson_word " + where_filter + ") AS lesson_word " +
                    "JOIN eword ON eword.eng_id=lesson_word.eng_id " +
                    "JOIN cword ON eword.cword_id=cword.cword_id " +
                    "JOIN cword_segment ON cword_segment.cword_id=cword.cword_id " +
                    "JOIN char_pinyin ON char_pinyin.char_pinyin_id=cword_segment.char_pinyin_id " +
                    "OUTER LEFT JOIN leitner ON leitner.hanzi=char_pinyin.hanzi " +
                    "WHERE (leitner.box IS NULL) or (leitner.exp_time<?) ORDER BY exp_time DESC",
                    new String[] {Long.toString(System.currentTimeMillis())});
            if (cur.moveToFirst()) {
                res.ensureCapacity(cur.getCount());
                do {
                    res.add(cur.getString(cur.getColumnIndex("hanzi")));
                } while(cur.moveToNext());
            }
            cur.close();
            return res;
        }
        else {
            return getAllToStudy();
        }
    }
}
