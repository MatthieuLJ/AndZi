package com.matthieu;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

import java.util.Timer;
import java.util.TimerTask;

public class ProgressService extends Service {
    private final static int NOTIFICATION_ID=1;
    @Override
    public void onCreate() {
        super.onCreate();
        Timer timer = new Timer();
        final ProgressDB db = new ProgressDB(getApplicationContext(), AndZiApplication.PROGRESS_DB_NAME);
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                ProgressDB.ProgressData current_data = ((AndZiApplication)getApplication()).getCurrentProgress();
                db.add_data(current_data);
                NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (current_data.num_chars_expired_in_box[0] > 10) {
                    Notification notification = new Notification(R.drawable.icon, "More to study", System.currentTimeMillis());
                    notification.flags = Notification.FLAG_AUTO_CANCEL | Notification.FLAG_ONLY_ALERT_ONCE;
                    notification.setLatestEventInfo(ProgressService.this, "More Chinese to study", "You have " + current_data.num_chars_expired_in_box[0] + " to study",
                            PendingIntent.getActivity(ProgressService.this, 0, new Intent(ProgressService.this, Test.class), 0));
                    nm.notify(NOTIFICATION_ID, notification);
                }
                else {
                    nm.cancel(NOTIFICATION_ID);
                }
            }
        };

        timer.scheduleAtFixedRate(task, 0, 2*60*60*1000); // every 2 hours
        //timer.scheduleAtFixedRate(task, 0, 4*60*1000); // every 4 seconds for test...
    }

    public static void clear_notification(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(NOTIFICATION_ID);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
