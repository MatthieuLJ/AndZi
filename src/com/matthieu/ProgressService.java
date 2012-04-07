package com.matthieu;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import java.util.Timer;
import java.util.TimerTask;

public class ProgressService extends Service  {
    @Override
    public void onCreate() {
        super.onCreate();
        Timer timer = new Timer();
        final ProgressDB db = new ProgressDB(getApplicationContext(), AndZiApplication.PROGRESS_DB_NAME);
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                db.add_data(((AndZiApplication)getApplication()).getCurrentProgress());
            }
        };
        timer.scheduleAtFixedRate(task, 0, 4*60*60*1000); // every 2 hours
        //timer.scheduleAtFixedRate(task, 0, 4*1000); // every 4 seconds for test...
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
