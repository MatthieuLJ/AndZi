/******************************
 * Main application
 * Keeps references to local, reference and progress database
 * Also offers some helper functions for some preferences and move / copy / checking for available space
 */
package com.matthieu;

import android.app.Application;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.StatFs;
import android.preference.PreferenceManager;
import android.util.Log;
import com.matthieu.widget.FileMountPoint;
import com.matthieu.widget.ObjectSerializer;
import org.acra.ACRA;
import org.acra.ReportingInteractionMode;
import org.acra.annotation.ReportsCrashes;

import java.io.*;
import java.util.ArrayList;

@ReportsCrashes(formKey = "", // will not be used
                mailTo = "matthieu.jeanson@gmail.com",
                mode = ReportingInteractionMode.TOAST,
                resToastText = R.string.crash_toast_text)
public class AndZiApplication extends Application {
    private HanziDB myDB=null;
    private HanziDB refDB=null;
    private ProgressDB prog=null;
    private boolean ref_enabled=true;
    private boolean ref_present=false;

    static final String TAG = "AndZiApplication";
    static final String REF_DB_NAME = "cedict_db";
    static final String DEFAULT_DATABASE_NAME = "HANZI_DB";
    static final String PROGRESS_DB_NAME = "progress_db";

    private static final String VERSION="0.2.0-acra";

    public void onCreate() {
        ACRA.init(this);
        initialize_my_db();

        Pinyin.prepare_pinyin_process();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        refDB = new HanziDB(getApplicationContext(), this,REF_DB_NAME);
        if (refDB.getNumChars()==0) {
            refDB.close();
            ContextWrapper cw = new ContextWrapper(getApplicationContext());
            cw.deleteDatabase(REF_DB_NAME);
            refDB=null;
            ref_present=false;
        }
        else {
            ref_present=true;
            ref_enabled=prefs.getBoolean("ref_enabled", true);
        }
        prog = new ProgressDB(getApplicationContext(), PROGRESS_DB_NAME);
        
        Intent startServiceIntent = new Intent(this, ProgressService.class);
        this.startService(startServiceIntent);
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        prog.close();
        myDB.close();
        if (ref_present)
            refDB.close();
    }

    public HanziDB getMyDB() { return myDB; }
    public HanziDB getRefDB() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        ref_enabled=prefs.getBoolean("ref_enabled", true);
        //Log.d(TAG, "Reference db is "+(ref_enabled?"enabled":"disabled"));
        return (ref_enabled&&ref_present)?refDB:null;
    }
    public boolean getRefDBPresent() { return ref_present; }
    public ProgressDB getProgress() { return prog; }
    public ProgressDB.ProgressData getCurrentProgress() {
        ProgressDB.ProgressData new_data = new ProgressDB.ProgressData();
        for (int i=0; i<4; i++) {
            new_data.num_chars_in_box[i]=myDB.getNumCharsInBox(i);
            new_data.num_chars_expired_in_box[i]=myDB.getNumCharsExpiredInBox(i);
        }
        new_data.num_chars_in_box[4]=myDB.getNumCharsInBoxOrAbove(4);
        new_data.num_chars_expired_in_box[4]=myDB.getNumCharsExpiredInBoxOrAbove(4);
        new_data.date = System.currentTimeMillis();
        return new_data;
    }

    public String get_version() {
        return VERSION;
    }
    
    private void initialize_my_db() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        myDB = new HanziDB(getApplicationContext(), this, DEFAULT_DATABASE_NAME);
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
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
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

    public boolean check_exporting_space(String path) {
        return check_exporting_space(path, getApplicationContext().getDatabasePath(DEFAULT_DATABASE_NAME));
    }
    public boolean check_exporting_progress_space(String path) {
        return check_exporting_space(path, getApplicationContext().getDatabasePath(PROGRESS_DB_NAME));
    }
    public boolean check_exporting_space(String path, File source_path) {
        // exporting is a several steps process:
        // create another temporary database with the data to export
        // copy the temporary file to the destination
        // delete the temporary file
        String destination_mp = FileMountPoint.mount_point(path);
        String localDB_mp = FileMountPoint.mount_point(source_path.getPath());

        if (destination_mp.equals(localDB_mp)) {
            StatFs stats = new StatFs(destination_mp);
            int available_space = stats.getAvailableBlocks();

            long dbSize = source_path.length()/stats.getBlockSize();

            if (available_space < 2*dbSize)
                return false;
        }
        else {
            long dbSize = source_path.length();

            StatFs stats = new StatFs(destination_mp);
            int available_space = stats.getAvailableBlocks();

            if (available_space < dbSize/stats.getBlockSize())
                return false;

            stats = new StatFs(localDB_mp);
            available_space = stats.getAvailableBlocks();

            if (available_space < dbSize/stats.getBlockSize())
                return false;
        }

        return true;
    }

    public boolean check_replace_progress(String path) {
        StatFs stats = new StatFs(FileMountPoint.mount_point(getApplicationContext().getDatabasePath(PROGRESS_DB_NAME).getPath()));
        long available_space = stats.getAvailableBlocks();

        File current = new File(getApplicationContext().getDatabasePath(PROGRESS_DB_NAME).getPath());
        long current_size = current.length();

        File f = new File(path);
        long dbSize = f.length();

        return available_space-current_size/stats.getBlockSize() >= dbSize/stats.getBlockSize();
    }


    public boolean check_merging_space(String path) {
        // merging is a several steps process:
        // copy the database into DB path
        // create a backup
        // merge it
        // delete the copy
        // delete the backup

        StatFs stats = new StatFs(FileMountPoint.mount_point(getApplicationContext().getDatabasePath(DEFAULT_DATABASE_NAME).getPath()));
        long available_space = stats.getAvailableBlocks();

        File f = new File(path);
        long dbSize = f.length();

        return available_space >= 3 * dbSize/stats.getBlockSize();
    }

    public boolean check_ref_download_space(String path, int size) {
        // Downloading and installing the reference database means:
        // download the file to the path
        // delete the existing ref db
        // copy the new one in place
        // delete the temporary file (?)

        // first there is enough space to download the file
        String destination_mp = FileMountPoint.mount_point(path);
        StatFs stats = new StatFs(destination_mp);
        int available_space = stats.getAvailableBlocks();
        int dest_blockSize = stats.getBlockSize();

        String localDB_mp = FileMountPoint.mount_point(getApplicationContext().getDatabasePath(REF_DB_NAME).getPath());
        long installed_db_size = getApplicationContext().getDatabasePath(REF_DB_NAME).length();

        if (destination_mp.equals(localDB_mp)) {
            if (available_space + installed_db_size/dest_blockSize < 2*size/dest_blockSize)
                return false;
        }
        else {
            if (available_space < size/dest_blockSize)
                return false;

            stats = new StatFs(localDB_mp);
            available_space = stats.getAvailableBlocks();
            dest_blockSize = stats.getBlockSize();
            if (available_space + installed_db_size/dest_blockSize < size/dest_blockSize)
                return false;

        }

        return true;
    }

    public boolean copyMyDataBase(String destination) {
        return copyFile(getApplicationContext().getDatabasePath(DEFAULT_DATABASE_NAME).getPath(), destination);
    }
    public boolean copyProgressDataBase(String destination) {
            return copyFile(getApplicationContext().getDatabasePath(PROGRESS_DB_NAME).getPath(), destination);
        }

    public boolean copyFile(String source, String destination) {
        InputStream input;
        try {
            input = new FileInputStream(source);
        }
        catch (IOException e) {
            Log.d(TAG, "Could not open source");
            return false;
        }
        if (input == null) {
            Log.d(TAG, "Could not open file at "+source);
            return false;
        }

        OutputStream output;
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
            return false;
        }

        return true;
    }

    public void replaceDataBase(String source) {
        myDB.close();
        myDB=null;
        ContextWrapper cw = new ContextWrapper(getApplicationContext());
        cw.deleteDatabase(DEFAULT_DATABASE_NAME);
        copyFile(source, getApplicationContext().getDatabasePath(DEFAULT_DATABASE_NAME).getPath());
        initialize_my_db();
    }

    public void installRefDB(String path, boolean erase_source) {
        eraseRefDB();
        String dest = getApplicationContext().getDatabasePath(REF_DB_NAME).getPath();
        if (!copyFile(path, dest)) {
            new File(dest).delete();
            return;
        }
        if (erase_source) {
            File to_delete= new File(path);
            to_delete.delete();
        }

        refDB = new HanziDB(getApplicationContext(), this, REF_DB_NAME);
        ref_present=true;
        Log.d(TAG, "Done installing reference db " + dest);
    }

    public void eraseRefDB() {
        if (refDB != null) {
            refDB.close();
            refDB = null;
        }
        ContextWrapper cw = new ContextWrapper(getApplicationContext());
        cw.deleteDatabase(REF_DB_NAME);
        ref_present=false;
    }

    public void replaceProgressDataBase(String source) {
        if (prog != null) {
            prog.close();
            prog = null;
        }
        ContextWrapper cw = new ContextWrapper(getApplicationContext());
        cw.deleteDatabase(PROGRESS_DB_NAME);
        copyFile(source, getApplicationContext().getDatabasePath(PROGRESS_DB_NAME).getPath());
        prog = new ProgressDB(getApplicationContext(), PROGRESS_DB_NAME);
    }
}
