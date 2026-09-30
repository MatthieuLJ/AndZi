/******************************
 * Main application
 * Keeps references to local, reference and progress database
 * Also offers some helper functions for some preferences and move / copy / checking for available space
 */
package com.matthieu.zi_app;

import android.app.ActivityManager;
import android.app.Application;
import android.content.*;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.StatFs;
import android.preference.PreferenceManager;
import android.util.Log;
import com.matthieu.zi_app.widget.FileMountPoint;
import com.matthieu.zi_app.R;
import com.matthieu.zi_app.widget.ProgressHandler;

import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class AndZiApplication extends Application {
    private HanziDB myDB=null;
    private HanziDB refDB=null;
    private ProgressDB prog=null;
    private ScreenBroadcastReceiver screenReceiver;
    private boolean ref_initialized=false;

    private static final boolean DEBUG = false;
    private static final String TAG = "AndZiApplication";
    private static final String REF_DB_NAME = "cedict_db";
    private static final String DEFAULT_DATABASE_NAME = "HANZI_DB";
    private static final String PROGRESS_DB_NAME = "progress_db";

    private com.matthieu.zi_app.data.AppContainer appContainer = null;

    public com.matthieu.zi_app.data.AppContainer getAppContainer() {
        if (appContainer == null) {
            appContainer = new com.matthieu.zi_app.data.DefaultAppContainer(this);
        }
        return appContainer;
    }

    private static final int this_ref_version = 1;

    @Override
    public void onCreate() {
        super.onCreate();
        prog = new ProgressDB(getApplicationContext(), PROGRESS_DB_NAME);
        
        Intent startServiceIntent = new Intent(this, ProgressService.class);
        this.startService(startServiceIntent);

        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        screenReceiver = new ScreenBroadcastReceiver();
        registerReceiver(screenReceiver, filter);

        Pinyin.prepare_pinyin_process();
    }

    private void update_dbs_in_details_populator() {
        DetailsFragment.populate_dbs(myDB, refDB);
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        prog.close();
        myDB.close();
        refDB.close();
    }

    public HanziDB getMyDB() {
        synchronized(this) {
            if (myDB==null) {
                if (!initialize_my_db())
                    return null;

            }
        }
        return myDB;
    }
    public HanziDB getRefDB() {
        synchronized(this) {
        if (!ref_initialized) {
            if (!initialize_ref_db(null))
                return null;
        }
        }

        return refDB;
    }

    public ProgressDB getProgress() {
        return prog;
    }

    public ProgressDB.ProgressData getCurrentProgress() {
        synchronized(this) {
            if (myDB == null) {
                if (!initialize_my_db())
                    return null;
            }
        }

        return myDB.getProgress();
    }

    public String get_version() {
        PackageManager manager = this.getPackageManager();
        PackageInfo info = null;
        try {
            info = manager.getPackageInfo(this.getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
        return info.versionName;
    }

    public boolean initialize_my_db() {
        synchronized(this) {
            if (myDB != null)
                myDB.close();
            try {
                myDB = new HanziDB(this, DEFAULT_DATABASE_NAME);
            } catch (Exception e) {
                return false;
            }
            update_dbs_in_details_populator();
            return true;
        }
    }
    
    public boolean initialize_ref_db(ProgressHandler progress) {
        synchronized(this) {
            if (ref_initialized)
                return true;

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            int ref_version = prefs.getInt("cedict_version", 0);
            int num_chars;

            if (ref_version == this_ref_version)
                try {
                    refDB = new HanziDB(this, REF_DB_NAME);
                    num_chars = refDB.getNumChars();
                }
                catch (Exception e) {
                    num_chars=0;
                }
            else
                num_chars=0;

            if (progress != null)
                progress.progress(10);

            if (num_chars==0)  {
                if (DEBUG) Log.d(TAG, "Install new dictionary");
                if (refDB!= null)
                    refDB.close();

                if (progress != null)
                    progress.progress(15);

                // Installing ref DB
                ContextWrapper cw = new ContextWrapper(getApplicationContext());
                cw.deleteDatabase(REF_DB_NAME);
                String dest = getApplicationContext().getDatabasePath(REF_DB_NAME).getPath();
                InputStream is = getResources().openRawResource(R.raw.cedict_db);

                if (!unpackZip(is, dest, progress)) {
                    new File(dest).delete();
                    return false;
                }

                if (DEBUG) Log.d(TAG, "Done installing reference db " + dest);

                if (progress != null)
                    progress.progress(85);

                refDB = new HanziDB(this, REF_DB_NAME);
                if ((!refDB.getReadableDatabase().isOpen()) || (refDB.getNumChars()==0))
                    return false;
            }
            ref_initialized=true;
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt("cedict_version", this_ref_version);
            editor.commit();

            if (progress != null)
                progress.progress(90);

            update_dbs_in_details_populator();

            return true;
        }
    }

    public boolean check_exporting_space(String path) {
        return check_exporting_space(path, getApplicationContext().getDatabasePath(DEFAULT_DATABASE_NAME));
    }
    public boolean check_exporting_progress_space(String path) {
        return check_exporting_space(path, getApplicationContext().getDatabasePath(PROGRESS_DB_NAME));
    }
    boolean check_exporting_space(String path, File source_path) {
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

    public boolean copyProgressDataBase(String destination) {
        return copyFile(getApplicationContext().getDatabasePath(PROGRESS_DB_NAME).getPath(), destination);
    }

    public boolean copyFile(String source, String destination) {
        InputStream input;
        try {
            input = new FileInputStream(source);
        }
        catch (IOException e) {
            if (DEBUG) Log.d(TAG, "Could not open source");
            return false;
        }
        return copyFile(input, destination);
    }

    public boolean copyFile(InputStream input, String destination) {
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
            if (DEBUG) Log.d(TAG, "Failed to copy database");
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

    private ActivityManager mActivityManager;
    public boolean isApplicationForeground() {
        ActivityManager.RunningAppProcessInfo info;

        if(mActivityManager==null)
            mActivityManager = (ActivityManager)this.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> l = mActivityManager.getRunningAppProcesses();
        for (ActivityManager.RunningAppProcessInfo aL : l) {
            info = aL;
            if ((info.processName.equals(this.getPackageName())) &&
                    (info.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND)) {
//                Log.d(TAG, "We're in foreground!");
                return true;
            }
        }
//        Log.d(TAG, "We're in the back!");
        return false;
    }

    private boolean show_splash=true;
    public void showSplash(boolean set) {
        show_splash=set;
    }
    public boolean getShowSplash() {
        return show_splash;
    }

    public boolean is_screen_on() {
        return screenReceiver.wasScreenOn;
    }

    Typeface chinese_font = null;
    public Typeface get_chinese_font() {
        if (chinese_font == null)
            chinese_font = Typeface.createFromAsset(getAssets(), "fonts/HDZB_simp.png");
        return chinese_font;
    }

    private boolean unpackZip(InputStream input, String destination, ProgressHandler progress)
    {
        ZipInputStream zis;
        try {
            zis = new ZipInputStream(new BufferedInputStream(input));
            byte[] buffer = new byte[1024];
            int count;
            int loop_count=0; // that goes up to 50000+

            while (zis.getNextEntry() != null) {
                // only one file in the zip....
                FileOutputStream fout = new FileOutputStream(destination);

                // cteni zipu a zapis
                while ((count = zis.read(buffer)) != -1) {
                    fout.write(buffer, 0, count);
                    loop_count++;
                    if ((progress != null) && (loop_count % 1000 == 0) && (loop_count<= 50000))
                        progress.progress(20+60*(loop_count/1000)/50); // send from 20 to 80
                }

                fout.close();
                zis.closeEntry();
            }

            zis.close();
        } catch(IOException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }
}
