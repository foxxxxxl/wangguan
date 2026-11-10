package com.nbasdws.zjsj;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Iterator;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;






public final class WebLogin {
    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static Dialog sDialog;
    private static WebView sWv;
    private static WebView sApp;
    private static String sTag = "";
    private static String sLastTag = "";
    private static WebView sLastApp = null;
    private static String sPrefix = "";
    private static String sPrefix2 = "";
    private static String sLastCookie = "";
    private static TextView sStatusBar;
    private static JSONArray sHosts;
    private static String sProbe = "";
    private static long sDeadline;
    private static Runnable sTask;

    private WebLogin() {
    }

    public static boolean open(WebView app, String config, String cbTag) {
        
        final WebView fApp = app;
        final String fCfg = config;
        final String fTag = cbTag;
        UI.post(new Runnable() {
            @Override
            public void run() {
                try {
                    WebLogin.openInternal(fApp, fCfg, fTag);
                } catch (Throwable th) {
                    WebLogin.closeInternal();
                    JSONObject o = new JSONObject();
                    try {
                        o.put("type", "error");
                        o.put("message", String.valueOf(th));
                    } catch (Throwable th2) {
                    }
                    WebLogin.deliver(o.toString());
                }
            }
        });
        return true;
    }

    private static void openInternal(WebView app, String config, String cbTag) {
        closeInternal();
        try {
            JSONObject cfg = new JSONObject(config);
            Context ctx = app.getContext();
            Activity act = null;
            Context c = ctx;
            while (c != null && act == null) {
                if (c instanceof Activity) {
                    act = (Activity) c;
                    break;
                }
                if (c instanceof android.content.ContextWrapper) {
                    c = ((android.content.ContextWrapper) c).getBaseContext();
                } else {
                    break;
                }
            }
            if (act == null) {
                throw new IllegalStateException("登录弹窗需要 Activity 环境");
            }
            ctx = act;
            sApp = app;
            sTag = cbTag;
            sLastTag = cbTag;
            sLastApp = app;
            sPrefix = cfg.optString("urlMatch", "");
            sPrefix2 = cfg.optString("urlMatch2", "");
            sHosts = cfg.optJSONArray("cookieHosts");
            sProbe = cfg.optString("lsProbe", "");
            sDeadline = System.currentTimeMillis() + cfg.optLong("maxMs", 240000L);

            final Dialog d = new Dialog(ctx, android.R.style.Theme_DeviceDefault_Light_NoActionBar);
            LinearLayout root = new LinearLayout(ctx);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackgroundColor(0xFFF4F7FD);
            LinearLayout head = new LinearLayout(ctx);
            head.setOrientation(LinearLayout.VERTICAL);
            head.setPadding(dp(ctx, 18), dp(ctx, 12), dp(ctx, 18), dp(ctx, 8));
            TextView title = new TextView(ctx);
            title.setText(cfg.optString("title", "登录"));
            title.setTextSize(19.0f);
            title.setTypeface(null, Typeface.BOLD);
            title.setTextColor(0xFF0F172A);
            TextView note = new TextView(ctx);
            note.setText(cfg.optString("note", "在页面里完成登录，凭证会自动识别并保存到本机。"));
            note.setTextSize(12.0f);
            note.setTextColor(0xFF64748B);
            note.setPadding(0, dp(ctx, 4), 0, 0);
            head.addView(title);
            head.addView(note);
            final WebView wv = new WebView(ctx);
            WebSettings st = wv.getSettings();
            st.setJavaScriptEnabled(true);
            st.setDomStorageEnabled(true);
            st.setJavaScriptCanOpenWindowsAutomatically(true);
            try {
                st.setDatabaseEnabled(true);
            } catch (Throwable th) {
            }
            String uaCfg = cfg.optString("ua", "");
            
            if (uaCfg.length() == 0 || "desktop".equalsIgnoreCase(uaCfg)) {
                uaCfg = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
            } else if ("mobile".equalsIgnoreCase(uaCfg)) {
                uaCfg = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36";
            }
            st.setUserAgentString(uaCfg);
            
            try {
                CookieManager cm = CookieManager.getInstance();
                cm.setAcceptCookie(true);
                cm.setAcceptThirdPartyCookies(wv, true);
            } catch (Throwable th) {
            }
            try {
                st.setLoadsImagesAutomatically(true);
                st.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
                st.setAllowFileAccess(false);
                st.setCacheMode(WebSettings.LOAD_DEFAULT);
                st.setUseWideViewPort(true);
                st.setLoadWithOverviewMode(true);
                st.setSupportZoom(true);
                st.setBuiltInZoomControls(true);
                st.setDisplayZoomControls(false);
                st.setSupportMultipleWindows(false);
            } catch (Throwable th) {
            }
            wv.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                    try {
                        String u = req.getUrl().toString();
                        if (WebLogin.matchesCallback(u)) {
                            v.stopLoading();
                            WebLogin.onCallbackUrl(u);
                            return true;
                        }
                    } catch (Throwable th) {
                    }
                    return false;
                }

                
                @Override
                public boolean shouldOverrideUrlLoading(WebView v, String u) {
                    try {
                        if (WebLogin.matchesCallback(u)) {
                            v.stopLoading();
                            WebLogin.onCallbackUrl(u);
                            return true;
                        }
                    } catch (Throwable th) {
                    }
                    return false;
                }

                
                @Override
                public void onPageStarted(WebView v, String u, Bitmap b) {
                    super.onPageStarted(v, u, b);
                    try {
                        if (WebLogin.matchesCallback(u)) {
                            v.stopLoading();
                            WebLogin.onCallbackUrl(u);
                        }
                    } catch (Throwable th) {
                    }
                }

                @Override
                public void onReceivedError(WebView v, int errorCode, String description, String failingUrl) {
                    super.onReceivedError(v, errorCode, description, failingUrl);
                    
                    WebLogin.updateStatus("页面加载失败(" + errorCode + ")：" + description);
                }

                @Override
                public void onReceivedHttpError(WebView v, WebResourceRequest req, WebResourceResponse res) {
                    super.onReceivedHttpError(v, req, res);
                    try {
                        if (req != null && req.isForMainFrame() && res != null) {
                            WebLogin.updateStatus("页面 HTTP " + res.getStatusCode() + "：" + req.getUrl());
                        }
                    } catch (Throwable th) {
                    }
                }
            });
            Button close = new Button(ctx);
            close.setText("关闭登录页");
            close.setAllCaps(false);
            close.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    WebLogin.closeInternal();
                    WebLogin.deliver("{\"type\":\"close\"}");
                }
            });
            root.addView(head);
            root.addView(wv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));
            
            final TextView status = new TextView(ctx);
            status.setText("等待登录… 已读到 Cookie: 无");
            status.setTextSize(11.0f);
            status.setTextColor(0xFF64748B);
            status.setBackgroundColor(0xFFEEF2FA);
            status.setPadding(dp(ctx, 14), dp(ctx, 8), dp(ctx, 14), dp(ctx, 8));
            sStatusBar = status;
            root.addView(status, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            root.addView(close, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 46)));
            try {
                d.requestWindowFeature(Window.FEATURE_NO_TITLE);
            } catch (Throwable th) {
            }
            d.setContentView(root);
            d.setCancelable(true);
            Window win = d.getWindow();
            if (win != null) {
                win.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            }
            sDialog = d;
            sWv = wv;
            d.show();
            wv.loadUrl(cfg.optString("url", ""));
            sTask = new Runnable() {
                @Override
                public void run() {
                    WebLogin.poll();
                }
            };
            UI.postDelayed(sTask, 1200L);
        } catch (Throwable th) {
            closeInternal();
            JSONObject o = new JSONObject();
            try {
                o.put("type", "error");
                o.put("message", String.valueOf(th));
            } catch (Throwable th2) {
            }
            deliver(o.toString());
        }
    }

    static void poll() {
        boolean rescheduled = false;
        try {
            if (sDialog == null || sWv == null || sApp == null) {
                return;
            }
            if (System.currentTimeMillis() > sDeadline) {
                deliver("{\"type\":\"timeout\"}");
                return;
            }
            
            
            if (sWv != null) {
                try {
                    String cur = sWv.getUrl();
                    if (cur != null && matchesCallback(cur)) {
                        sWv.stopLoading();
                        onCallbackUrl(cur);
                        return;
                    }
                } catch (Throwable th) {
                }
            }
            if (sHosts != null) {
                
                CookieManager cm = CookieManager.getInstance();
                try {
                    cm.flush();
                } catch (Throwable th) {
                }
                StringBuilder merged = new StringBuilder();
                java.util.HashSet<String> seenKeys = new java.util.HashSet<String>();
                String hitKey = "";
                String hitHost = "";
                for (int i = 0; i < sHosts.length(); i++) {
                    JSONObject h = sHosts.optJSONObject(i);
                    if (h == null) {
                        continue;
                    }
                    String host = h.optString("host", "");
                    if (host.length() == 0) {
                        continue;
                    }
                    String cookie = cm.getCookie(host);
                    if (cookie == null || cookie.length() == 0) {
                        continue;
                    }
                    String[] parts = cookie.split(";");
                    for (int pi = 0; pi < parts.length; pi++) {
                        String kv = parts[pi].trim();
                        int eq = kv.indexOf('=');
                        if (eq <= 0) {
                            continue;
                        }
                        String name = kv.substring(0, eq).trim();
                        String value = kv.substring(eq + 1).trim();
                        if (value.length() == 0) {
                            continue;
                        }
                        if (!seenKeys.add(name)) {
                            continue;
                        }
                        if (merged.length() > 0) {
                            merged.append("; ");
                        }
                        merged.append(name).append('=').append(value);
                    }
                    JSONArray keys = h.optJSONArray("keys");
                    if (keys != null && hitKey.length() == 0) {
                        for (int k = 0; k < keys.length(); k++) {
                            String want = keys.optString(k, "").toLowerCase();
                            if (want.length() == 0) {
                                continue;
                            }
                            
                            String[] nm = cookie.split(";");
                            for (int ni = 0; ni < nm.length; ni++) {
                                String kv2 = nm[ni].trim();
                                int eq2 = kv2.indexOf('=');
                                if (eq2 <= 0) {
                                    continue;
                                }
                                String nmRaw = kv2.substring(0, eq2).trim().toLowerCase();
                                if (nmRaw.startsWith("__secure-")) {
                                    nmRaw = nmRaw.substring(9);
                                } else if (nmRaw.startsWith("__host-")) {
                                    nmRaw = nmRaw.substring(7);
                                }
                                if (nmRaw.equals(want) && kv2.substring(eq2 + 1).trim().length() > 0) {
                                    hitKey = want;
                                    hitHost = host;
                                    break;
                                }
                            }
                            if (hitKey.length() > 0) {
                                break;
                            }
                        }
                    }
                }
                
                try {
                    if (sStatusBar != null) {
                        if (merged.length() > 0) {
                            java.util.HashSet<String> names = new java.util.HashSet<String>();
                            for (String kv : merged.toString().split(";")) {
                                int eq = kv.indexOf('=');
                                if (eq > 0) {
                                    names.add(kv.substring(0, eq).trim());
                                }
                            }
                            StringBuilder sb2 = new StringBuilder();
                            for (String nm : names) {
                                if (sb2.length() > 0) {
                                    sb2.append(", ");
                                }
                                sb2.append(nm);
                            }
                            sStatusBar.setText("已读到 " + names.size() + " 个 Cookie: " + sb2);
                        } else {
                            sStatusBar.setText("等待登录… 已读到 Cookie: 无（请在下方页面完成登录）");
                        }
                    }
                } catch (Throwable th) {
                }
                
                if (merged.length() > 0 && sLastCookie.length() == 0) {
                    try {
                        JSONObject dbg = new JSONObject();
                        dbg.put("type", "debug");
                        dbg.put("cookie", merged.toString());
                        deliver(dbg.toString());
                    } catch (Throwable th) {
                    }
                }
                
                if (merged.length() > 0) {
                    String cookieStr = merged.toString();
                    if (!cookieStr.equals(sLastCookie)) {
                        sLastCookie = cookieStr;
                        JSONObject o = new JSONObject();
                        o.put("type", "cookie");
                        o.put("host", hitHost);
                        o.put("hit", hitKey);
                        o.put("cookie", cookieStr);
                        deliver(o.toString());
                    }
                    rescheduled = true;
                }
            }
            
            if (!rescheduled && sWv != null) {
                try {
                    String cur = sWv.getUrl();
                    if (cur != null && matchesCallback(cur)) {
                        sWv.stopLoading();
                        onCallbackUrl(cur);
                        rescheduled = true;
                    }
                } catch (Throwable th) {
                }
            }
            if (!rescheduled && sProbe.length() > 0 && sWv != null) {
                final String probe = sProbe;
                sWv.evaluateJavascript(probe, new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String value) {
                        try {
                            if (value != null) {
                                String v = value.trim();
                                if (v.length() > 0 && !"null".equals(v) && !"\"\"".equals(v) && !"\"null\"".equals(v)) {
                                    Object parsed = new JSONTokener(v).nextValue();
                                    String str = parsed instanceof String ? (String) parsed : String.valueOf(parsed);
                                    if (str.length() > 0) {
                                        JSONObject o = new JSONObject();
                                        o.put("type", "ls");
                                        o.put("value", str);
                                        WebLogin.deliver(o.toString());
                                    }
                                }
                            }
                        } catch (Throwable th) {
                        }
                    }
                });
            }
            rescheduled = true;
        } catch (Throwable th) {
        }
        if (rescheduled && sTask != null) {
            UI.postDelayed(sTask, 1200L);
        }
    }

    static boolean matchesCallback(String u) {
        if (u == null) {
            return false;
        }
        if (sPrefix.length() > 0 && u.startsWith(sPrefix)) {
            return true;
        }
        if (sPrefix2.length() > 0 && u.indexOf(sPrefix2) >= 0) {
            return true;
        }
        return false;
    }

    static void onCallbackUrl(String url) {
        try {
            JSONObject o = new JSONObject();
            o.put("type", "url");
            o.put("url", url);
            deliver(o.toString());
        } catch (Throwable th) {
        }
    }

    static void deliver(String json) {
        try {
            
            
            final WebView w = sApp != null ? sApp : sLastApp;
            final String tg = (sTag != null && sTag.length() > 0) ? sTag : sLastTag;
            final String payload = json;
            if (w == null || tg == null || tg.length() == 0) {
                return;
            }
            UI.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        w.evaluateJavascript("window.__ofmCallback(" + JSONObject.quote(tg) + "," + payload + ")", null);
                    } catch (Throwable th) {
                    }
                }
            });
        } catch (Throwable th) {
        }
    }

    
    public static void updateStatus(final String msg) {
        UI.post(new Runnable() {
            @Override
            public void run() {
                try {
                    if (sStatusBar != null) {
                        sStatusBar.setText(msg == null ? "" : msg);
                    }
                } catch (Throwable th) {
                }
            }
        });
    }

    
    public static void showCookieNames(final String names) {
        UI.post(new Runnable() {
            @Override
            public void run() {
                try {
                    if (sStatusBar != null) {
                        sStatusBar.setText("Cookie: " + (names == null ? "" : names));
                    }
                } catch (Throwable th) {
                }
            }
        });
    }

    public static boolean isOpen() {
        return sDialog != null;
    }

    public static void close() {
        UI.post(new Runnable() {
            @Override
            public void run() {
                WebLogin.closeInternal();
            }
        });
    }

    private static void closeInternal() {
        try {
            if (sTask != null) {
                UI.removeCallbacks(sTask);
            }
        } catch (Throwable th) {
        }
        sTask = null;
        sHosts = null;
        sProbe = "";
        sPrefix = "";
        sPrefix2 = "";
        sLastCookie = "";
        try {
            if (sWv != null) {
                sWv.stopLoading();
                sWv.destroy();
            }
        } catch (Throwable th) {
        }
        sWv = null;
        try {
            if (sDialog != null) {
                sDialog.dismiss();
            }
        } catch (Throwable th) {
        }
        sDialog = null;
        
        final WebView keepApp = sApp;
        final String keepTag = sTag;
        sApp = null;
        sTag = "";
        sStatusBar = null;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(8000L);
                } catch (Throwable th) {
                }
                if (sApp == null && keepApp != null && sLastTag.equals(keepTag)) {
                    sLastTag = "";
                }
            }
        }).start();
    }

    
    public static void qoderModels(final WebView app, final String config, final String cbTag) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                int code = 0;
                String body;
                try {
                    JSONObject cfg = new JSONObject(config);
                    String url = cfg.optString("url", "");
                    String token = cfg.optString("token", "");
                    String refreshToken = cfg.optString("refreshToken", "");
                    String uid = cfg.optString("uid", "");
                    String nickname = cfg.optString("nickname", "");

                    String uuidHex = UUID.randomUUID().toString().replace("-", "");
                    byte[] keyBytes = uuidHex.substring(0, 16).getBytes("UTF-8");

                    byte[] pubDer = Base64.decode("MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDA8iMH5c02LilrsERw9t6Pv5Nc4k6Pz1EaDicBMpdpxKduSZu5OANqUq8er4GM95omAGIOPOh+Nx0spthYA2BqGz+l6HRkPJ7S236FZz73In/KVuLnwI8JJ2CbuJap8kvheCCZpmAWpb/cPx/3Vr/J6I17XcW+ML9FoCI6AOvOzwIDAQAB", 0);
                    PublicKey pub = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(pubDer));
                    Cipher rsa = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                    rsa.init(Cipher.ENCRYPT_MODE, pub);
                    String cosyKey = Base64.encodeToString(rsa.doFinal(keyBytes), 0);

                    String infoJson = "{" + "\"aid\":\"" + uid + "\","
                            + "\"name\":\"" + nickname + "\","
                            + "\"organization_id\":\"\","
                            + "\"organization_name\":\"\","
                            + "\"refresh_token\":\"" + refreshToken + "\","
                            + "\"security_oauth_token\":\"" + token + "\","
                            + "\"uid\":\"" + uid + "\","
                            + "\"user_type\":\"personal_professional_trial\","
                            + "\"yx_uid\":\"\"" + "}";
                    Cipher aes = Cipher.getInstance("AES/CBC/PKCS5Padding");
                    aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, "AES"), new IvParameterSpec(keyBytes));
                    String info = Base64.encodeToString(aes.doFinal(infoJson.getBytes("UTF-8")), 0);

                    String reqBody = "{\"cosyVersion\":\"1.1.64\",\"ideVersion\":\"\",\"info\":\"" + info
                            + "\",\"requestId\":\"" + UUID.randomUUID().toString() + "\",\"version\":\"v1\"}";
                    String b64Body = Base64.encodeToString(reqBody.getBytes("UTF-8"), 0);

                    String machineId = md5hex("machine:" + uid);
                    String machinetype = md5hex("machinetype:" + uid);
                    if (machinetype.length() > 18) machinetype = machinetype.substring(0, 18);
                    MessageDigest sha512 = MessageDigest.getInstance("SHA-512");
                    String machinetoken = Base64.encodeToString(sha512.digest(("machinetoken:" + uid).getBytes("UTF-8")), 2).replace("+", "-").replace("/", "_");
                    if (machinetoken.length() > 43) machinetoken = machinetoken.substring(0, 43);

                    String date = String.valueOf(System.currentTimeMillis() / 1000L);
                    String path = new URI(url).getPath();
                    if (path == null) path = "/";
                    else if (path.startsWith("/algo")) path = path.substring(5);
                    
                    
                    String obfBody = obfuscateBody("{}");
                    String signInput = b64Body + "\n" + cosyKey + "\n" + date + "\n" + obfBody + "\n" + path;
                    String bearer = "Bearer COSY." + b64Body + "." + md5hex(signInput);

                    HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(20000);
                    conn.setReadTimeout(25000);
                    conn.setDoOutput(true);
                    conn.setRequestProperty("cosy-data-policy", "AGREE");
                    conn.setRequestProperty("content-type", "application/json");
                    conn.setRequestProperty("cosy-machinetype", machinetype);
                    conn.setRequestProperty("cosy-clienttype", "5");
                    conn.setRequestProperty("cosy-date", date);
                    conn.setRequestProperty("cosy-user", uid);
                    conn.setRequestProperty("cosy-key", cosyKey);
                    conn.setRequestProperty("accept", "application/json");
                    conn.setRequestProperty("cosy-clientip", "169.254.198.161");
                    conn.setRequestProperty("authorization", bearer);
                    conn.setRequestProperty("accept-encoding", "identity");
                    conn.setRequestProperty("cosy-version", "1.1.64");
                    conn.setRequestProperty("cosy-machineid", machineId);
                    conn.setRequestProperty("cosy-machinetoken", machinetoken);
                    conn.setRequestProperty("login-version", "v2");
                    conn.setRequestProperty("user-agent", "Go-http-client/2.0");
                    
                    byte[] bytes = obfBody.getBytes("UTF-8");
                    conn.setFixedLengthStreamingMode(bytes.length);
                    OutputStream os = conn.getOutputStream();
                    os.write(bytes);
                    os.flush();
                    os.close();
                    code = conn.getResponseCode();
                    InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
                    body = readAll(is);
                } catch (Throwable th) {
                    body = String.valueOf(th);
                    if (code == 0) code = -1;
                }
                try {
                    JSONObject o = new JSONObject();
                    o.put("code", code);
                    o.put("body", body == null ? "" : body);
                    final JSONObject fo = o;
                    app.post(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                app.evaluateJavascript("window.__ofmCallback(" + JSONObject.quote(cbTag) + "," + fo + ")", null);
                            } catch (Throwable th) {
                            }
                        }
                    });
                } catch (Throwable th) {
                }
            }
        }).start();
    }

    
    public static void raw(final WebView app, final String config, final String cbTag) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                int code = 0;
                String body;
                try {
                    JSONObject cfg = new JSONObject(config);
                    HttpURLConnection conn = (HttpURLConnection) new URL(cfg.optString("url", "")).openConnection();
                    conn.setRequestMethod(cfg.optString("method", "GET"));
                    conn.setConnectTimeout(20000);
                    conn.setReadTimeout(25000);
                    
                    
                    JSONObject headers = cfg.optJSONObject("headers");
                    boolean hasAE = false;
                    if (headers != null) {
                        Iterator<String> it0 = headers.keys();
                        while (it0.hasNext()) {
                            if ("accept-encoding".equalsIgnoreCase(it0.next())) {
                                hasAE = true;
                                break;
                            }
                        }
                    }
                    if (!hasAE) {
                        conn.setRequestProperty("Accept-Encoding", "identity");
                    }
                    if (headers != null) {
                        Iterator<String> it = headers.keys();
                        while (it.hasNext()) {
                            String k = it.next();
                            conn.setRequestProperty(k, headers.optString(k, ""));
                        }
                    }
                    String bodyStr = cfg.has("body") ? cfg.optString("body", "") : null;
                    String method = cfg.optString("method", "GET");
                    if (bodyStr != null && ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method))) {
                        conn.setDoOutput(true);
                        byte[] bytes = bodyStr.getBytes("UTF-8");
                        conn.setFixedLengthStreamingMode(bytes.length);
                        OutputStream os = conn.getOutputStream();
                        os.write(bytes);
                        os.flush();
                        os.close();
                    }
                    code = conn.getResponseCode();
                    InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
                    body = readAll(is);
                } catch (Throwable th) {
                    body = String.valueOf(th);
                    if (code == 0) {
                        code = -1;
                    }
                }
                try {
                    JSONObject o = new JSONObject();
                    o.put("code", code);
                    o.put("body", body == null ? "" : body);
                    final JSONObject fo = o;
                    app.post(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                app.evaluateJavascript("window.__ofmCallback(" + JSONObject.quote(cbTag) + "," + fo + ")", null);
                            } catch (Throwable th) {
                            }
                        }
                    });
                } catch (Throwable th) {
                }
            }
        }).start();
    }

    

    static String obfuscateBody(String plain) {
        final String STD = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
        final String TBL = "_doRTgHZBKcGVjlvpC,@aFSx#DPuNJme&i*MzLOEn)sUrthbf%Y^w.(kIQyXqWA!";
        try {
            String b64 = Base64.encodeToString(plain.getBytes("UTF-8"), 0);
            int len = b64.length();
            int a = len / 3;
            int b = len - a;
            String tail = b64.substring(b);
            String mid = b64.substring(a, b);
            String head = b64.substring(0, a);
            String rot = tail + mid + head;
            StringBuilder sb = new StringBuilder(rot.length());
            for (int i = 0; i < rot.length(); i++) {
                char c = rot.charAt(i);
                int idx = STD.indexOf(c);
                if (c == '=') {
                    sb.append('$');
                } else if (idx >= 0) {
                    sb.append(TBL.charAt(idx));
                } else {
                    sb.append('$');
                }
            }
            return sb.toString();
        } catch (Throwable th) {
            return "";
        }
    }

    static String md5hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] d = md.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(Character.forDigit((b & 240) >> 4, 16));
                sb.append(Character.forDigit(b & 15, 16));
            }
            return sb.toString();
        } catch (Throwable th) {
            return "";
        }
    }

    private static String readAll(InputStream is) {
        if (is == null) {
            return "";
        }
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int total = 0;
            int tailChecked = 0;
            while (true) {
                int n = is.read(buf);
                if (n <= 0) {
                    break;
                }
                bo.write(buf, 0, n);
                total += n;
                
                if (n > 0 && (total - tailChecked) > 0) {
                    String chunk = new String(buf, 0, n, "UTF-8");
                    tailChecked = total;
                    if (chunk.indexOf("[DONE]") >= 0) {
                        break;
                    }
                }
                
                if (total > 8388608) {
                    break;
                }
            }
            try {
                is.close();
            } catch (Throwable th0) {
            }
            byte[] data = bo.toByteArray();
            
            if (data.length > 2 && data[0] == 0x1f && data[1] == (byte) 0x8b) {
                try {
                    java.util.zip.GZIPInputStream gz = new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(data));
                    ByteArrayOutputStream out2 = new ByteArrayOutputStream();
                    byte[] b2 = new byte[8192];
                    while (true) {
                        int n2 = gz.read(b2);
                        if (n2 <= 0) {
                            break;
                        }
                        out2.write(b2, 0, n2);
                    }
                    gz.close();
                    data = out2.toByteArray();
                } catch (Throwable th2) {
                }
            }
            return new String(data, "UTF-8");
        } catch (Throwable th) {
            return "";
        }
    }

    private static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }
}
