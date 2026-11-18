package com.usama.hisab;

import android.app.job.JobParameters;
import android.app.job.JobService;

/** Background update check, run by Android every few hours when there is internet. */
public class UpdateJob extends JobService {
    @Override
    public boolean onStartJob(JobParameters params) {
        new Thread(() -> {
            try { Updater.checkAndNotify(getApplicationContext()); } catch (Exception ignored) { }
            jobFinished(params, false);
        }).start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
