package com.the.adventurer;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.ContentValues;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.URLUtil;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private WebView web;
    private ValueCallback<Uri[]> fileCb;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(), "AndroidSave");
        web.setDownloadListener((url, ua, cd, mime, len) -> handleDownload(url, cd, mime));
        if (Build.VERSION.SDK_INT >= 23 && Build.VERSION.SDK_INT < 29)
            requestPermissions(new String[]{"android.permission.WRITE_EXTERNAL_STORAGE"}, 2);
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                try {
                    startActivityForResult(p.createIntent(), 1);
                } catch (Exception e) {
                    fileCb = null;
                    return false;
                }
                return true;
            }
        });
        web.loadUrl("file:///android_asset/index.html");
    }

    public class Bridge {
        @JavascriptInterface
        public void save(String name, String mime, String b64) {
            saveFile(name, mime, Base64.decode(b64, Base64.DEFAULT));
        }
    }

    private void toast(String m) {
        runOnUiThread(() -> Toast.makeText(this, m, Toast.LENGTH_LONG).show());
    }

    private void saveFile(String name, String mime, byte[] data) {
        try {
            OutputStream os;
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
                cv.put(MediaStore.Downloads.MIME_TYPE, mime);
                cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri u = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                os = getContentResolver().openOutputStream(u);
            } else {
                os = new FileOutputStream(new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), name));
            }
            os.write(data);
            os.close();
            toast("Enregistré dans Téléchargements : " + name);
        } catch (Exception e) {
            toast("Erreur : " + e.getMessage());
        }
    }

    private void handleDownload(String url, String cd, String mime) {
        if (mime == null || mime.isEmpty()) mime = "application/octet-stream";
        String name = URLUtil.guessFileName(url, cd, mime);
        if (url.startsWith("blob:")) {
            String js = "(function(){var x=new XMLHttpRequest();x.open('GET','" + url + "',true);x.responseType='blob';"
                + "x.onload=function(){var r=new FileReader();r.onloadend=function(){AndroidSave.save('"
                + name.replace("'", "") + "','" + mime + "',r.result.split(',')[1]);};r.readAsDataURL(x.response);};x.send();})();";
            web.evaluateJavascript(js, null);
        } else if (url.startsWith("data:")) {
            try {
                int c = url.indexOf(',');
                String head = url.substring(5, c), body = url.substring(c + 1);
                byte[] d = head.contains(";base64") ? Base64.decode(body, Base64.DEFAULT)
                    : URLDecoder.decode(body, "UTF-8").getBytes("UTF-8");
                saveFile(name, mime, d);
            } catch (Exception e) {
                toast("Erreur : " + e.getMessage());
            }
        } else {
            DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
            r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
            ((DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE)).enqueue(r);
            toast("Téléchargement lancé : " + name);
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == 1 && fileCb != null) {
            fileCb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data));
            fileCb = null;
        }
        super.onActivityResult(req, res, data);
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack(); else super.onBackPressed();
    }
}
