package com.matthieu.zi_app;

import android.annotation.SuppressLint;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.matthieu.zi_app.widget.ProgressHandler;

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

        final TextView stage_text = (TextView) res.findViewById(R.id.splash_stage);

        if (db_loader == null) {
            db_loader = new SplashLoader(progress, stage_text);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
                db_loader.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[])null);
            else
                db_loader.execute((Void[])null);
        }
        else {
            db_loader.change_progress_bar(progress, stage_text);
        }

        return res;
    }

    private class update_data {
        Integer value;
        String text;
        public update_data(int v, String t) {this.value=v; this.text=t;}
    }

    private class SplashLoader extends AsyncTask<Void,update_data,Boolean> {
        AndZiApplication application;
        TextView stage;
        ProgressBar progressbar;
        Resources rsc;

        public SplashLoader(ProgressBar bar, TextView text) {
            this.progressbar = bar;
            this.stage = text;
            rsc = getActivity().getResources();
        }

        public void change_progress_bar(ProgressBar bar, TextView text) {
            this.progressbar = bar;
            this.stage = text;
        }

        @Override
        protected void onPreExecute() {
            application = (AndZiApplication) getActivity().getApplication();
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            publishProgress(new update_data(5, rsc.getString(R.string.splash_init)));

            if (!application.initialize_ref_db(new ProgressHandler() {
                @Override
                public void progress(int i) {
                    publishProgress(new update_data(5+i*45/100, rsc.getString(R.string.splash_load_ref)));
                }
            })) {
                return false;
            }

            publishProgress(new update_data(50, rsc.getString(R.string.splash_load_db)));
            HanziDB myDB = application.getMyDB();

            myDB.checkDatabase(true, new ProgressHandler() {
                @Override
                public void progress(int i) {
                    publishProgress(new update_data(55+(i*45)/100, rsc.getString(R.string.splash_check_db)));
                }
            });
            return true;
        }

        @Override
        protected void onProgressUpdate(update_data... i) {
            synchronized (SplashFragment.this) {
                if (getView() == null)
                    return;

                if (progressbar != null)
                    progressbar.setProgress(i[0].value);

                if (stage != null)
                    stage.setText(i[0].text);
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
