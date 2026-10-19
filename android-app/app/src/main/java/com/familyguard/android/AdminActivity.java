package com.familyguard.android;

import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.webkit.*;
import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;

public class AdminActivity extends ComponentActivity {
  WebView web;

  protected void onCreate(Bundle b) {
    super.onCreate(b);
    getOnBackPressedDispatcher()
        .addCallback(
            this,
            new OnBackPressedCallback(true) {
              @Override
              public void handleOnBackPressed() {
                if (web != null && web.canGoBack()) web.goBack();
                else finish();
              }
            });
    web = new WebView(this);
    setContentView(web);
    WebSettings settings = web.getSettings();
    settings.setJavaScriptEnabled(true);
    settings.setDomStorageEnabled(true);
    settings.setAllowFileAccess(false);
    settings.setAllowContentAccess(false);
    settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
    WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);
    CookieManager.getInstance().setAcceptCookie(true);
    CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
    web.setWebViewClient(
        new WebViewClient() {
          public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
            Uri uri = r.getUrl();
            return !"https".equals(uri.getScheme())
                || !Uri.parse(BuildConfig.WEB_URL).getHost().equals(uri.getHost());
          }

          public void onReceivedSslError(WebView v, SslErrorHandler h, SslError error) {
            h.cancel();
          }

          public void onReceivedError(
              WebView v, WebResourceRequest request, WebResourceError error) {
            if (request.isForMainFrame())
              runOnUiThread(
                  () -> {
                    Ui.page(
                        AdminActivity.this,
                        "Connection unavailable",
                        "The secure dashboard could not load. Render may be waking up, or the"
                            + " internet is unavailable.");
                    Ui.button(
                        AdminActivity.this,
                        "Try again",
                        () -> {
                          setContentView(web);
                          web.loadUrl(BuildConfig.WEB_URL);
                        });
                  });
          }
        });
    web.loadUrl(BuildConfig.WEB_URL);
  }

  protected void onDestroy() {
    if (web != null) {
      web.stopLoading();
      web.destroy();
    }
    super.onDestroy();
  }
}
