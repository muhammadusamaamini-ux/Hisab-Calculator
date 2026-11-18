package com.usama.hisab;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.widget.Toast;

/** Gets the result of an in-app update and shows Android's "Update this app?" screen. */
public class InstallReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        int status = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    c.startActivity(confirm);
                } catch (Exception e) {
                    toast(c, "Open Hisab Calculator and tap Check for updates again.");
                }
            }
        } else if (status == PackageInstaller.STATUS_SUCCESS) {
            toast(c, "Hisab Calculator is updated.");
        } else if (status != PackageInstaller.STATUS_FAILURE_ABORTED) {
            String msg = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            toast(c, "The update didn't install" + (msg == null ? "" : " (" + msg + ")")
                    + ". If Play Protect blocked it, pause Play Protect and try again.");
        }
    }

    private static void toast(Context c, String s) {
        Toast.makeText(c.getApplicationContext(), s, Toast.LENGTH_LONG).show();
    }
}
