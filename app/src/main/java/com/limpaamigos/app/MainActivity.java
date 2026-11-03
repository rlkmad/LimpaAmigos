package com.limpaamigos.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQ_LOGIN = 1;
    private static final Set<String> ALLOWED = new HashSet<>(Arrays.asList(
            "users.roblox.com", "friends.roblox.com", "thumbnails.roblox.com"));
    private static final String UA = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

    private WebView web;
    private final ExecutorService pool = Executors.newFixedThreadPool(3);
    private volatile String csrf = "";

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.parseColor("#0f1115"));
        getWindow().setNavigationBarColor(Color.parseColor("#0f1115"));
        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#0f1115"));
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowContentAccess(false);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                return !r.getUrl().toString().startsWith("file:///android_asset/");
            }
        });
        web.addJavascriptInterface(new Bridge(), "Native");
        web.loadUrl("file:///android_asset/index.html");
    }

    /** Só o cookie .ROBLOSECURITY, lido do CookieManager (nunca exposto ao JS). */
    private String sessionCookie() {
        String all = CookieManager.getInstance().getCookie("https://www.roblox.com");
        if (all == null) return null;
        for (String part : all.split(";")) {
            String p = part.trim();
            if (p.startsWith(".ROBLOSECURITY=")) return p;
        }
        return null;
    }

    private void sendToJs(String id, int status, String text) {
        final String js = "window.__cb(" + JSONObject.quote(id) + "," + status + ","
                + JSONObject.quote(text) + ")";
        runOnUiThread(() -> web.evaluateJavascript(js, null));
    }

    private static String read(InputStream in) throws IOException {
        if (in == null) return "";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        return out.toString("UTF-8");
    }

    private void doRequest(String id, String method, String url, String body) {
        int status = 0;
        String text = "";
        try {
            Uri u = Uri.parse(url);
            String host = u.getHost();
            if (!"https".equals(u.getScheme()) || host == null || !ALLOWED.contains(host))
                throw new IOException("URL bloqueada");
            String ck = sessionCookie();
            for (int attempt = 0; attempt < 2; attempt++) {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setRequestMethod(method);
                c.setConnectTimeout(15000);
                c.setReadTimeout(20000);
                c.setRequestProperty("Accept", "application/json");
                c.setRequestProperty("User-Agent", UA);
                if (ck != null) c.setRequestProperty("Cookie", ck);
                if (!"GET".equals(method)) {
                    c.setRequestProperty("x-csrf-token", csrf);
                    c.setRequestProperty("Content-Type", "application/json");
                    c.setDoOutput(true);
                    c.getOutputStream().write((body == null || body.isEmpty() ? "{}" : body).getBytes("UTF-8"));
                }
                status = c.getResponseCode();
                String tok = c.getHeaderField("x-csrf-token");
                if (tok != null) csrf = tok;
                if (status == 403 && tok != null && attempt == 0) { c.disconnect(); continue; }
                text = read(status >= 400 ? c.getErrorStream() : c.getInputStream());
                c.disconnect();
                break;
            }
        } catch (Exception e) {
            status = 0;
            text = String.valueOf(e.getMessage());
        }
        sendToJs(id, status, text);
    }

    private class Bridge {
        @JavascriptInterface public boolean hasSession() { return sessionCookie() != null; }

        @JavascriptInterface public void login() {
            runOnUiThread(() -> startActivityForResult(
                    new Intent(MainActivity.this, LoginActivity.class), REQ_LOGIN));
        }

        @JavascriptInterface public void logout() {
            runOnUiThread(() -> {
                CookieManager.getInstance().removeAllCookies(null);
                CookieManager.getInstance().flush();
                csrf = "";
            });
        }

        @JavascriptInterface public void request(final String id, final String method,
                                                 final String url, final String body) {
            pool.execute(() -> doRequest(id, method, url, body));
        }
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_LOGIN && res == RESULT_OK)
            web.evaluateJavascript("window.onLoggedIn && window.onLoggedIn()", null);
    }

    @Override protected void onDestroy() {
        pool.shutdown();
        super.onDestroy();
    }
}
