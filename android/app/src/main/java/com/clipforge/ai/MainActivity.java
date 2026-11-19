package com.clipforge.ai;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.database.Cursor;
import android.util.Base64;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;
    private static final int FILE_PICKER = 4102;
    private Uri pendingUri;
    private OutputStream pendingStream;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        webView = new WebView(this);
        setContentView(webView);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        webView.addJavascriptInterface(new GalleryBridge(), "ClipforgeNative");
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("video/*");
                startActivityForResult(Intent.createChooser(intent, "Choose a video"), FILE_PICKER);
                return true;
            }
        });
        webView.loadUrl("https://clipforge-ai-beta-two.vercel.app");
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_PICKER && fileCallback != null) {
            Uri[] result = resultCode == RESULT_OK && data != null && data.getData() != null
                ? new Uri[]{data.getData()} : null;
            fileCallback.onReceiveValue(result);
            fileCallback = null;
        }
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    private synchronized void closePending(boolean publish) {
        try { if (pendingStream != null) pendingStream.close(); } catch (Exception ignored) {}
        pendingStream = null;
        if (pendingUri != null && publish) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Video.Media.IS_PENDING, 0);
            getContentResolver().update(pendingUri, values, null, null);
        } else if (pendingUri != null) {
            getContentResolver().delete(pendingUri, null, null);
        }
        pendingUri = null;
    }

    private class GalleryBridge {
        @JavascriptInterface public synchronized boolean beginSave(String name) {
            closePending(false);
            try {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Video.Media.DISPLAY_NAME, name);
                values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
                values.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CLIPFORGE");
                values.put(MediaStore.Video.Media.IS_PENDING, 1);
                pendingUri = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
                if (pendingUri == null) return false;
                pendingStream = getContentResolver().openOutputStream(pendingUri);
                return pendingStream != null;
            } catch (Exception e) { closePending(false); return false; }
        }
        @JavascriptInterface public synchronized boolean appendChunk(String encoded) {
            try {
                if (pendingStream == null) return false;
                pendingStream.write(Base64.decode(encoded, Base64.DEFAULT));
                return true;
            } catch (Exception e) { return false; }
        }
        @JavascriptInterface public synchronized boolean finishSave() {
            if (pendingStream == null || pendingUri == null) return false;
            closePending(true);
            return true;
        }
        @JavascriptInterface public synchronized void cancelSave() { closePending(false); }
    }
}
