package com.usama.hisab;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Updates from the newest GitHub release: "Check for updates" (MODE → App),
 * plus a quiet check every few hours that shows an "Update available" notification with what's new.
 */
final class Updater {
    private Updater() {}

    private static final String LATEST =
            "https://api.github.com/repos/muhammadusamaamini-ux/Hisab-Calculator/releases/latest";
    private static final String CHANNEL = "updates";
    private static final int NOTIFY_ID = 7;
    private static final int JOB_ID = 77;

    static final class Latest {
        int build;
        String apkUrl;
        String error;
        String notes = "";
    }

    /** Release text without the GitHub/Claude bookkeeping lines. */
    static String cleanNotes(String body) {
        if (body == null) return "";
        StringBuilder sb = new StringBuilder();
        for (String line : body.split("\n")) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("Co-Authored-By") || t.startsWith("Claude-Session")) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(t.startsWith("- ") ? "• " + t.substring(2) : t);
        }
        return sb.toString();
    }

    static int installedBuild(Context c) {
        try {
            PackageInfo p = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28 ? (int) p.getLongVersionCode() : p.versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    private static int buildNumber(String tag) {
        if (tag == null) return 0;
        String digits = tag.replaceAll("^.*?(\\d+)$", "$1");
        try { return Integer.parseInt(digits); } catch (Exception e) { return 0; }
    }

    /** Asks GitHub for the newest build. Call from a background thread. */
    static Latest fetch() {
        Latest l = new Latest();
        try {
            HttpURLConnection con = open(LATEST);
            con.setRequestProperty("Accept", "application/vnd.github+json");
            int code = con.getResponseCode();
            if (code == 404) {
                l.error = "Couldn't find the updates on GitHub. Make sure the Hisab-Calculator repository is Public.";
            } else if (code != 200) {
                l.error = "GitHub answered with error " + code + ". Please try again later.";
            } else {
                JSONObject j = new JSONObject(readAll(con.getInputStream()));
                l.build = buildNumber(j.optString("tag_name"));
                l.notes = cleanNotes(j.optString("body", ""));
                JSONArray assets = j.optJSONArray("assets");
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject as = assets.getJSONObject(i);
                        if (as.optString("name").endsWith(".apk")) l.apkUrl = as.optString("browser_download_url");
                    }
                }
            }
        } catch (IOException e) {
            l.error = "Couldn't reach GitHub. Check your internet connection and try again.";
        } catch (Exception e) {
            l.error = "Couldn't read the update information.";
        }
        return l;
    }

    // ---------- automatic checks + notification ----------

    /** Checks about every 6 hours when there is internet, even if the app isn't open. */
    static void schedule(Context c) {
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null || js.getPendingJob(JOB_ID) != null) return;
        JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(c, UpdateJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(6 * 60 * 60 * 1000L)
                .setPersisted(true)
                .build();
        try { js.schedule(job); } catch (Exception ignored) { }
    }

    /** Quiet check: shows a notification if there's a new build. Call from a background thread. */
    static boolean checkAndNotify(Context c) {
        Latest l = fetch();
        if (l.error != null || l.apkUrl == null || l.build <= installedBuild(c)) return false;
        SharedPreferences sp = c.getSharedPreferences("updates", Context.MODE_PRIVATE);
        if (sp.getInt("notified", 0) == l.build) return true; // already told about this build
        sp.edit().putInt("notified", l.build).apply();
        notifyUpdate(c, l.build, l.notes);
        return true;
    }

    private static void notifyUpdate(Context c, int build, String notes) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= 33
                && c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "App updates",
                    NotificationManager.IMPORTANCE_DEFAULT));
            b = new Notification.Builder(c, CHANNEL);
        } else {
            b = new Notification.Builder(c);
        }
        Intent open = new Intent(c, MainActivity.class)
                .putExtra("update", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 5, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        b.setSmallIcon(R.drawable.ic_notif)
                .setContentTitle("Hisab Calculator update available")
                .setContentText(notes.isEmpty() ? "Build " + build + " is ready. Tap to install it."
                        : "What's new: " + notes.replace('\n', ' '))
                .setStyle(new Notification.BigTextStyle().bigText("Build " + build + " is ready. Tap to install it."
                        + (notes.isEmpty() ? "" : "\n\nWhat's new:\n" + notes)))
                .setContentIntent(pi)
                .setAutoCancel(true);
        nm.notify(NOTIFY_ID, b.build());
    }

    static void clearNotification(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(NOTIFY_ID);
    }

    // ---------- "Check for updates" ----------

    static void check(Activity a) {
        AlertDialog wait = new AlertDialog.Builder(a)
                .setMessage("Checking for updates…").setCancelable(false).show();
        new Thread(() -> {
            final Latest l = fetch();
            a.runOnUiThread(() -> {
                wait.dismiss();
                if (a.isFinishing()) return;
                int have = installedBuild(a);
                if (l.error != null) {
                    message(a, "Update check failed", l.error);
                } else if (l.build <= have) {
                    message(a, "You're up to date", "You have the newest version (build " + have + ").");
                } else if (l.apkUrl == null || l.apkUrl.isEmpty()) {
                    message(a, "Update not ready", "Build " + l.build + " is still being prepared. Try again in a few minutes.");
                } else {
                    offer(a, l, have);
                }
            });
        }).start();
    }

    private static void offer(Activity a, Latest l, int have) {
        new AlertDialog.Builder(a)
                .setTitle("Update available")
                .setMessage("Build " + l.build + " is ready (you have build " + have + ")."
                        + (l.notes.isEmpty() ? "" : "\n\nWhat's new:\n" + l.notes)
                        + "\n\nDownload and install it now? Your history, memory and settings are kept.")
                .setPositiveButton("Update", (d, w) -> download(a, l.apkUrl, l.build))
                .setNegativeButton("Later", null)
                .show();
    }

    private static void download(Activity a, String url, int build) {
        if (Build.VERSION.SDK_INT >= 26 && !a.getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(a)
                    .setTitle("One-time permission")
                    .setMessage("Allow Hisab Calculator to install its own updates: on the next screen, turn on "
                            + "\"Allow from this source\". Then come back and tap Check for updates again.")
                    .setPositiveButton("Open settings", (d, w) -> a.startActivity(
                            new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:" + a.getPackageName()))))
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }
        clearNotification(a);
        AlertDialog progress = new AlertDialog.Builder(a)
                .setTitle("Downloading build " + build)
                .setMessage("Please wait…")
                .setCancelable(false)
                .show();

        new Thread(() -> {
            String error = null;
            PackageInstaller.Session session = null;
            try {
                PackageInstaller installer = a.getPackageManager().getPackageInstaller();
                PackageInstaller.SessionParams params =
                        new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                int id = installer.createSession(params);
                session = installer.openSession(id);

                HttpURLConnection con = open(url);
                if (con.getResponseCode() != 200) throw new IOException("HTTP " + con.getResponseCode());
                try (InputStream in = con.getInputStream();
                     OutputStream out = session.openWrite("HisabCalculator.apk", 0, -1)) {
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    session.fsync(out);
                }

                Intent done = new Intent(a, InstallReceiver.class);
                int flags = PendingIntent.FLAG_UPDATE_CURRENT
                        | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
                PendingIntent pi = PendingIntent.getBroadcast(a, id, done, flags);
                session.commit(pi.getIntentSender());
                session.close();
                session = null;
            } catch (Exception e) {
                error = "The download didn't finish. Check your internet connection and try again.";
                if (session != null) {
                    try { session.abandon(); } catch (Exception ignored) { }
                }
            }
            final String err = error;
            a.runOnUiThread(() -> {
                progress.dismiss();
                if (err != null && !a.isFinishing()) message(a, "Update failed", err);
            });
        }).start();
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(15_000);
        con.setReadTimeout(30_000);
        con.setInstanceFollowRedirects(true);
        con.setRequestProperty("User-Agent", "HisabCalculator-Android");
        return con;
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        in.close();
        return bo.toString("UTF-8");
    }

    private static void message(Activity a, String title, String msg) {
        new AlertDialog.Builder(a).setTitle(title).setMessage(msg).setPositiveButton("OK", null).show();
    }
}
