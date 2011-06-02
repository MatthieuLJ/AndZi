package com.matthieu;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import java.util.ArrayList;

/**
 * User: matthieu
 * Date: 5/31/11
 * Time: 3:19 PM
 */
public class HanziDBlesson extends HanziDB {
    private static final String TAG = "HanziDBlesson";

    public HanziDBlesson(Context context) {
        super(context);
    }

    public HanziDBlesson(Context context, String db_name) {
        super(context, db_name);
    }

    public void onCreate(SQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS lesson_word;");
        db.execSQL("DROP TABLE IF EXISTS lessons;");

        super.onCreate(db);

        db.execSQL("CREATE TABLE lessons (" +
                "lesson_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                "name VARCHAR(64)," +
                "UNIQUE(name));");
        db.execSQL("CREATE TABLE lesson_word (" +
                "eng_id INTEGER," +
                "lesson_id INTEGER," +
                "FOREIGN KEY (eng_id) REFERENCES eword(eng_id)," +
                "FOREIGN KEY (lesson_id) REFERENCES lessons(lesson_id)," +
                "UNIQUE(eng_id,lesson_id));");
    }

    public boolean checkDatabase() {
        boolean res = super.checkDatabase();

        // check foreign key from lesson_word to lessons
        Cursor c = db.rawQuery("SELECT DISTINCT lesson_id FROM lesson_word WHERE lesson_id NOT IN (SELECT lesson_id FROM lessons);", null);
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

        return res;
    }

    public void addLesson(String name) {
        ContentValues cv = new ContentValues();
        cv.put("name", name);
        db.insert("lessons", null, cv);
        //db.insertWithOnConflict("lessons", null, cv, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public void delLesson(String name) {
        int lesson_id, res;

        lesson_id = getLessonId(name);
        if (lesson_id == -1)
            return;

        res = db.delete("lesson_word", "lesson_id=?", new String[] {Integer.toString(lesson_id)});
        res = db.delete("lessons", "lesson_id=?", new String[] {Integer.toString(lesson_id)});
    }

    public ArrayList<String> getLessons() {
        ArrayList<String> res = new ArrayList<String>();

        Cursor cur = db.rawQuery("SELECT name FROM lessons", null);
        if (cur.moveToFirst()) {
            do{
                res.add(cur.getString(cur.getColumnIndex("name")));
            }while(cur.moveToNext());
        }
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

    public ArrayList<Vocabulary> getVocabularyInLessons(ArrayList<String> lessons) {
        String whereQuery = "IN (";
        ArrayList<Vocabulary> res = new ArrayList<Vocabulary>();
        Vocabulary tmp=null;

        int size = lessons.size();
        if (size == 0)
            return res;

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
        
        Cursor cur = db.rawQuery("SELECT e.translation, c.chinese, c.pinyin " +
                                 "FROM eword AS e " +
                                 " JOIN cword AS c ON c.cword_id=e.cword_id " +
                                 " JOIN lesson_word AS lw ON lw.eng_id=e.eng_id " +
                                 "WHERE lw.lesson_id " + whereQuery,null);

        if (cur.moveToFirst()) {
            do{
                String chinese = cur.getString(cur.getColumnIndex("c.chinese"));
                String pinyin = cur.getString(cur.getColumnIndex("c.pinyin"));
                if (tmp == null)
                    tmp = new Vocabulary(chinese, pinyin,
                            cur.getString(cur.getColumnIndex("e.translation")));
                else {
                    if (tmp.chinese.equals(chinese) && tmp.pinyin.equals(pinyin))
                        tmp.english.add(cur.getString(cur.getColumnIndex("e.translation")));
                    else {
                        res.add(tmp);
                        tmp = new Vocabulary(chinese, pinyin,
                                cur.getString(cur.getColumnIndex("e.translation")));
                    }
                }
            }while(cur.moveToNext());
            res.add(tmp);
        }

        cur.close();
        return res;
    }
}
