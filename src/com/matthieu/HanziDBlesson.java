package com.matthieu;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.Html;
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
                "FOREIGN KEY (eword_id) REFERENCES eword(eng_id)," +
                "FOREIGN KEY (lesson_id) REFERENCES lessons(lesson_id)," +
                "UNIQUE(eword_id,lesson_id));");
    }

    public void addLesson(String name) {
        ContentValues cv = new ContentValues();
        cv.put("name", name);
        db.insertWithOnConflict("lessons", null, cv, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public void delLesson(String name) {
        int lesson_id;

        lesson_id = getLessonId(name);
        if (lesson_id == -1)
            return;

        db.delete("lesson_word", "lesson_id=?", new String[] {Integer.toString(lesson_id)});
        db.delete("lessons", "lesson_id=?", new String[] {Integer.toString(lesson_id)});
    }

    protected int getLessonId(String name) {
        int res = -1;
        Cursor cur = db.rawQuery("SELECT * FROM lessons WHERE name=?;", new String[] {name});
        if (cur.moveToFirst()) {
            res = cur.getInt(cur.getColumnIndex("name"));
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
        db.insertWithOnConflict("lesson_word", null, cv, SQLiteDatabase.CONFLICT_IGNORE);
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

    /*
    public ArrayList<Vocabulary> getVocabularyInLessons(ArrayList<String> lessons) {
        
    }

    public HanziDB createFilterDB() {

    }
    */
}
