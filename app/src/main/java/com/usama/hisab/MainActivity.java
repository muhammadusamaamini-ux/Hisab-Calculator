package com.usama.hisab;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.core.content.FileProvider;
import androidx.webkit.WebViewAssetLoader;

/**
 * Hisab Calculator: the whole calculator runs from files inside the APK, with no internet.
 * Internet is used only to check GitHub for app updates.
 */
public class MainActivity extends Activity {
    private static final String HOME = "https://appassets.androidplatform.net/assets/index.html";
    private static final int REQ_NOTIFY = 5;
    private static final int REQ_SAVE = 7;

    private byte[] pendingBytes;
    private String pendingMime;

    private WebView web;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setTextZoom(100);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if ("appassets.androidplatform.net".equals(u.getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }
                return true;
            }
        });
        web.addJavascriptInterface(new Bridge(), "HisabAndroid");

        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl(HOME);

        // Ask once for permission to show the "update available" notification (Android 13+).
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            SharedPreferences sp = getSharedPreferences("app", MODE_PRIVATE);
            if (!sp.getBoolean("askedNotify", false)) {
                sp.edit().putBoolean("askedNotify", true).apply();
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFY);
            }
        }

        Updater.schedule(this);
        if (getIntent() != null && getIntent().getBooleanExtra("update", false)) {
            Updater.check(this);
        } else {
            final Context app = getApplicationContext();
            new Thread(() -> {
                try { Updater.checkAndNotify(app); } catch (Exception ignored) { }
            }).start();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.getBooleanExtra("update", false)) Updater.check(this);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (web != null) web.saveState(outState);
    }

    /** Back button: close an open panel first; leave the app only from the main screen. */
    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (web == null) { super.onBackPressed(); return; }
        web.evaluateJavascript("(window.hisabBack&&hisabBack())?'1':'0'", v -> {
            if (!"\"1\"".equals(v)) finish();
        });
    }

    /** The bill export picked a place to save: write the file there, then open it. */
    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_SAVE) return;
        byte[] bytes = pendingBytes;
        String mime = pendingMime;
        pendingBytes = null;
        if (resultCode != RESULT_OK || data == null || data.getData() == null || bytes == null) return;
        Uri uri = data.getData();
        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
            if (out == null) throw new Exception("no stream");
            out.write(bytes);
        } catch (Exception e) {
            Toast.makeText(this, "Could not save the file", Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
        Intent view = new Intent(Intent.ACTION_VIEW);
        view.setDataAndType(uri, mime);
        view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivity(view); } catch (Exception e) {
            Toast.makeText(this, "Saved. No app on this phone opens this file type.", Toast.LENGTH_LONG).show();
        }
    }

    /** Small helpers the calculator page calls as window.HisabAndroid.*. */
    private class Bridge {
        @JavascriptInterface
        public String version() {
            return "build " + Updater.installedBuild(MainActivity.this);
        }

        @JavascriptInterface
        public void checkUpdates() {
            runOnUiThread(() -> Updater.check(MainActivity.this));
        }

        @JavascriptInterface
        public void copyText(String text) {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Hisab Calculator", text));
        }

        /** Save an exported bill (Excel, PDF or CSV): Android asks where, Downloads by default. */
        @JavascriptInterface
        public void saveFile(String name, String mime, String base64) {
            final byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            runOnUiThread(() -> {
                pendingBytes = bytes;
                pendingMime = mime;
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType(mime);
                i.putExtra(Intent.EXTRA_TITLE, name);
                try { startActivityForResult(i, REQ_SAVE); } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Could not open the save screen", Toast.LENGTH_LONG).show();
                }
            });
        }

        /** Share an exported bill to WhatsApp, email and so on. */
        @JavascriptInterface
        public void shareFile(String name, String mime, String base64) {
            final byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            runOnUiThread(() -> {
                try {
                    File dir = new File(getCacheDir(), "exports");
                    if (!dir.exists()) dir.mkdirs();
                    File f = new File(dir, name.replaceAll("[\\\\/:*?\"<>|]", "_"));
                    try (FileOutputStream out = new FileOutputStream(f)) { out.write(bytes); }
                    Uri uri = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".files", f);
                    Intent s = new Intent(Intent.ACTION_SEND);
                    s.setType(mime);
                    s.putExtra(Intent.EXTRA_STREAM, uri);
                    s.putExtra(Intent.EXTRA_SUBJECT, name);
                    s.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(s, "Share bill of quantities"));
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Could not share the file", Toast.LENGTH_LONG).show();
                }
            });
        }
    }
}
