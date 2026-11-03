package com.limpaamigos.app;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** Abre a página de login oficial do Roblox. A senha nunca passa pelo app. */
public class LoginActivity extends Activity {
    private WebView web;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean finished = false;

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            check();
            if (!finished) handler.postDelayed(this, 1000);
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.parseColor("#0f1115"));
        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView v, String url) { check(); }
        });
        web.loadUrl("https://www.roblox.com/login");
        handler.postDelayed(poll, 1000);
    }

    private void check() {
        if (finished) return;
        String c = CookieManager.getInstance().getCookie("https://www.roblox.com");
        if (c != null && c.contains(".ROBLOSECURITY=")) {
            finished = true;
            CookieManager.getInstance().flush();
            setResult(RESULT_OK);
            finish();
        }
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
