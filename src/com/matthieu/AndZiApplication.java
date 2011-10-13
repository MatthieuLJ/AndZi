package com.matthieu;

import android.app.Application;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.*;
import java.util.ArrayList;

public class AndZiApplication extends Application {
    public HanziDB myDB=null;
    public HanziDB refDB=null;

    private final static String TAG = "AndZiApplication";
    private final static String PREFERENCES_NAME="AndziPrefs";
    private final static String REF_DB_NAME = "cedict_db";
    private static final String DEFAULT_DATABASE_NAME = "HANZI_DB";

    public void onCreate() {
        SharedPreferences prefs = getSharedPreferences(PREFERENCES_NAME, 0);

        myDB = new HanziDB(getApplicationContext(), this, DEFAULT_DATABASE_NAME);

        refDB = new HanziDB(getApplicationContext(), this,REF_DB_NAME);
        if (refDB.getNumChars()==0) {
            refDB.close();
            ContextWrapper cw = new ContextWrapper(getApplicationContext());
            cw.deleteDatabase(REF_DB_NAME);
            refDB=null;
        }

        boolean filter_on = prefs.getBoolean("Lesson_Filter_On", false);
        ArrayList<String> lesson=new ArrayList<String>();
        try {
            lesson = (ArrayList<String>) ObjectSerializer.deserialize(prefs.getString("Lesson_Filter", (String) ObjectSerializer.serialize(new ArrayList<String>())));
        } catch (IOException e) {
            e.printStackTrace();
            filter_on = false;
        }
        myDB.enableFilter(filter_on);
        myDB.setLessonsToFilter(lesson);
    }

    public void save_database_filter(boolean on, ArrayList<String> list) {
        SharedPreferences prefs = getSharedPreferences(PREFERENCES_NAME, 0);
        SharedPreferences.Editor edit = prefs.edit();
        edit.putBoolean("Lesson_Filter_On", on);
        try {
            edit.putString("Lesson_Filter", (String) ObjectSerializer.serialize(list));
        } catch (IOException e) {
            edit.putBoolean("Lesson_Filter_On", false);
            e.printStackTrace();
        }
        edit.commit();
    }

    public boolean checkFileExists(String path) {
        return new File(path).exists();
    }

    public void installRefDB(String path) {
        eraseRefDB();
        String dest = getApplicationContext().getDatabasePath(REF_DB_NAME).getPath();
        copyDataBase(path, dest);

        refDB = new HanziDB(getApplicationContext(), this, REF_DB_NAME);

        Log.d(TAG, "Done installing reference db " + dest);
    }

    public void eraseRefDB() {

        if (refDB != null) {
            refDB.close();
            refDB = null;
        }
        ContextWrapper cw = new ContextWrapper(getApplicationContext());
        cw.deleteDatabase(REF_DB_NAME);

    }

    public void copyDataBase(String source, String destination) {
        InputStream input;
        try {
            input = new FileInputStream(source);
        }
        catch (IOException e) {
            Log.d(TAG, "no ref DB in assets");
            return;
        }
        if (input == null) {
            Log.d(TAG, "could not open file at "+source);
            return;
        }

        OutputStream output=null;
        try {
            output = new FileOutputStream(destination);

            //transfer bytes
            byte[] buffer = new byte[1024];
            int length;
            while ((length = input.read(buffer))>0){
                output.write(buffer, 0, length);
            }

            //Close the streams
            output.flush();
            output.close();
            input.close();
        }
        catch (IOException e) {
            Log.d(TAG, "Failed to copy database");
            return;
        }

    }
}
