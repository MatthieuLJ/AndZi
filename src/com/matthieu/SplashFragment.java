package com.matthieu;

import android.annotation.SuppressLint;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.matthieu.widget.DBCheckProgressHandler;

public class SplashFragment extends Fragment {
    private SplashLoader db_loader=null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    @Override
    @SuppressLint({"NewApi"})
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        final View res = inflater.inflate(R.layout.main_menu_splash, container, false);
        TextView tversion = (TextView) res.findViewById(R.id.splash_version);
        tversion.setText(((AndZiApplication) getActivity().getApplication()).get_version());

        final ProgressBar progress = (ProgressBar) res.findViewById(R.id.splash_progress);
        progress.setIndeterminate(false);
        progress.setMax(100);

        if (db_loader == null) {
            db_loader = new SplashLoader();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
                db_loader.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[])null);
            else
                db_loader.execute((Void[])null);
        }

        return res;
    }

    private class SplashLoader extends AsyncTask<Void,Integer,Boolean> {
        AndZiApplication application;

        @Override
        protected void onPreExecute() {
            application = (AndZiApplication) getActivity().getApplication();
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            publishProgress(5);

            if (!application.initialize_ref_db()) {
                return false;
            }

            publishProgress(10);
            HanziDB myDB = application.getMyDB();

            publishProgress(15);
            myDB.checkDatabase(true, new DBCheckProgressHandler() {
                @Override
                public void progress(int i) {
                    publishProgress(15+(i*85)/100);
                }
            });
            return true;
        }

        @Override
        protected void onProgressUpdate(Integer... i) {
            synchronized (SplashFragment.this) {
                if (getView() == null)
                    return;

                ProgressBar progress = (ProgressBar) getView().findViewById(R.id.splash_progress);
                if (progress != null)
                    progress.setProgress(i[0]);
            }
        }

        @Override
        protected void onPostExecute(Boolean aBool) {
            synchronized (SplashFragment.this) {
                db_loader=null;
                if (getActivity()!=null) {
                    if (aBool)
                        ((MainMenuActivity)getActivity()).start_it();
                    else
                        ((MainMenuActivity)getActivity()).fail_it();
                }
            }
        }
    }
}
