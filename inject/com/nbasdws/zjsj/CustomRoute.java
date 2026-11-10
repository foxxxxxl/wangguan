package com.nbasdws.zjsj;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.json.JSONArray;
import org.json.JSONObject;









public class CustomRoute {

    
    private static volatile Map<String, List<String[]>> ROUTES = new HashMap<>();
    private static final java.util.concurrent.atomic.AtomicLong PICK = new java.util.concurrent.atomic.AtomicLong();
    private static volatile String LAST = "";

    public static void setRoutes(String str) {
        HashMap<String, List<String[]>> map = new HashMap<>();
        try {
            JSONArray arr = new JSONArray(str);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String id = o.optString("id", "");
                if (id.length() == 0) continue;
                List<String[]> list = map.get(id);
                if (list == null) {
                    list = new java.util.ArrayList<>();
                    map.put(id, list);
                }
                list.add(new String[]{
                        o.optString("url", ""),
                        o.optString("key", ""),
                        o.optString("model", ""),
                        o.optString("proto", ""),
                        o.optString("cookie", ""),
                        o.optString("path", ""),
                        o.toString()
                });
            }
            ROUTES = map;
        } catch (Throwable th) {
            LAST = "setRoutes 失败: " + th;
        }
    }

    public static String dump() {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("路由数=").append(ROUTES.size()).append("\n");
            sb.append("最近: ").append(LAST.length() == 0 ? "(未调用)" : LAST).append("\n");
            int i = 0;
            for (Map.Entry<String, List<String[]>> e : ROUTES.entrySet()) {
                if (i++ >= 20) { sb.append("…\n"); break; }
                String[] a = e.getValue().get(0);
                sb.append(e.getKey()).append(" → proto=").append(a[3])
                        .append(" model=").append(a[2]).append("\n");
            }
            return sb.toString();
        } catch (Throwable th) {
            return "dump 失败: " + th;
        }
    }

    

    private static void writeHead(OutputStream out, int code, String type, int len, boolean chunked) throws IOException {
        String st = code == 200 ? "OK" : (code == 400 ? "Bad Request" : code == 401 ? "Unauthorized"
                : code == 404 ? "Not Found" : code == 502 ? "Bad Gateway" : "Error");
        String head = "HTTP/1.1 " + code + " " + st + "\r\n"
                + "Content-Type: " + type + "\r\n"
                + "Cache-Control: no-cache\r\n"
                + "Access-Control-Allow-Origin: *\r\n"
                + (chunked ? "Connection: close\r\n" : "Content-Length: " + len + "\r\nConnection: close\r\n")
                + "\r\n";
        out.write(head.getBytes("UTF-8"));
        out.flush();
    }

    private static String readAll(InputStream is) {
        if (is == null) return "";
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) {
                bo.write(buf, 0, n);
                if (bo.size() > 4194304) break;
            }
            is.close();
            byte[] data = bo.toByteArray();
            if (data.length > 2 && data[0] == 0x1f && data[1] == (byte) 0x8b) {
                try {
                    java.util.zip.GZIPInputStream gz = new java.util.zip.GZIPInputStream(
                            new java.io.ByteArrayInputStream(data));
                    ByteArrayOutputStream o2 = new ByteArrayOutputStream();
                    byte[] b2 = new byte[8192];
                    int n2;
                    while ((n2 = gz.read(b2)) > 0) o2.write(b2, 0, n2);
                    gz.close();
                    data = o2.toByteArray();
                } catch (Throwable ignored) {}
            }
            return new String(data, "UTF-8");
        } catch (Throwable th) {
            return "";
        }
    }

    private static String lastUserText(JSONObject body) {
        try {
            JSONArray msgs = body.optJSONArray("messages");
            String out = "";
            if (msgs != null) {
                for (int i = 0; i < msgs.length(); i++) {
                    JSONObject mo = msgs.optJSONObject(i);
                    if (mo == null) continue;
                    if ("user".equals(mo.optString("role", ""))) {
                        Object c = mo.opt("content");
                        if (c instanceof String) out = (String) c;
                        else if (c instanceof JSONArray) {
                            JSONArray arr = (JSONArray) c;
                            StringBuilder sb = new StringBuilder();
                            for (int j = 0; j < arr.length(); j++) {
                                JSONObject p = arr.optJSONObject(j);
                                if (p != null) sb.append(p.optString("text", ""));
                            }
                            out = sb.toString();
                        }
                    }
                }
            }
            return out;
        } catch (Throwable th) {
            return "";
        }
    }

    private static void chunkWrite(OutputStream out, String sse) throws IOException {
        if (sse == null || sse.length() == 0) return;
        byte[] b = sse.getBytes("UTF-8");
        out.write((Integer.toHexString(b.length) + "\r\n").getBytes("UTF-8"));
        out.write(b);
        out.write("\r\n".getBytes("UTF-8"));
        out.flush();
    }

    private static String openAiChunk(String id, String model, String content, String finish) {
        try {
            JSONObject o = new JSONObject();
            o.put("id", id).put("object", "chat.completion.chunk")
                    .put("created", System.currentTimeMillis() / 1000L).put("model", model);
            JSONObject delta = new JSONObject();
            if (content != null) delta.put("content", content);
            JSONObject choice = new JSONObject().put("index", 0).put("delta", delta);
            choice.put("finish_reason", finish == null ? JSONObject.NULL : finish);
            o.put("choices", new JSONArray().put(choice));
            return "data: " + o + "\n\n";
        } catch (Throwable th) {
            return "";
        }
    }

    private static String md5hex(String s) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
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

    private static String rnd(int n) {
        String cs = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-";
        StringBuilder sb = new StringBuilder();
        java.util.Random r = new java.util.Random();
        for (int i = 0; i < n; i++) sb.append(cs.charAt(r.nextInt(cs.length())));
        return sb.toString();
    }

    

    public static boolean handle(Socket socket, String body) {
        String reqModel = "";
        try {
            JSONObject req = new JSONObject(body);
            reqModel = req.optString("model", "");
            List<String[]> list = ROUTES.get(reqModel);
            if (list == null || list.isEmpty()) {
                LAST = "未匹配 model=" + reqModel + " (路由 " + ROUTES.size() + ")";
                return false;
            }
            String[] r = list.get((int) (PICK.getAndIncrement() % list.size()));
            String proto = r[3] == null ? "" : r[3];
            String key = r[1] == null ? "" : r[1];
            String model = r[2] == null ? "" : r[2];
            String cookie = r.length > 4 && r[4] != null ? r[4] : "";
            String url = r[0] == null ? "" : r[0];
            JSONObject full = null;
            try { full = new JSONObject(r.length > 6 && r[6] != null ? r[6] : "{}"); } catch (Throwable ignored) {}
            LAST = "model=" + reqModel + " proto=" + proto + " keyLen=" + key.length();

            OutputStream out = socket.getOutputStream();

            if ("zhipu".equals(proto)) return doZhipu(socket, req, key, model, out);
            if ("trae".equals(proto)) return doTrae(socket, req, key, model, out);
            if ("dangbei".equals(proto)) return doDangbei(socket, req, key, model, out);
            if ("tabbit".equals(proto)) return doTabbit(socket, req, key, model, out);
            if ("meoo".equals(proto)) return doMeoo(socket, req, cookie, model, out);
            if ("agentmore".equals(proto)) return doAgentmore(socket, req, key, model, out);
            if ("catpaw".equals(proto)) return doCatpaw(socket, req, key, model, out);
            if ("astudio".equals(proto)) return doAstudio(socket, req, key, model, out);
            if ("codebuddy".equals(proto)) return doCodebuddy(socket, req, key, model, url, full, out);
            
            return doStandard(socket, req, key, model, url, cookie, out);
        } catch (Throwable th) {
            LAST = "异常 model=" + reqModel + " : " + th;
            try {
                OutputStream o = socket.getOutputStream();
                byte[] eb = ("{\"error\":{\"message\":\"" + String.valueOf(th).replace("\"", "'") + "\"}}").getBytes("UTF-8");
                writeHead(o, 502, "application/json", eb.length, false);
                o.write(eb);
                o.flush();
            } catch (Throwable ignored) {}
            return true;
        }
    }

    
    private static boolean doStandard(Socket socket, JSONObject req, String key, String model,
                                      String base, String cookie, OutputStream out) throws Exception {
        JSONObject b = new JSONObject(req.toString());
        b.put("model", model);
        String base2 = base == null ? "" : base.replaceAll("/+$", "");
        HttpURLConnection c = (HttpURLConnection) new URL(base2 + "/chat/completions").openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(20000);
        c.setReadTimeout(300000);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Accept", "text/event-stream, application/json");
        if (key.length() > 0) c.setRequestProperty("Authorization", "Bearer " + key);
        if (cookie.length() > 0) c.setRequestProperty("Cookie", cookie);
        byte[] bb = b.toString().getBytes("UTF-8");
        c.setFixedLengthStreamingMode(bb.length);
        OutputStream co = c.getOutputStream();
        co.write(bb);
        co.flush();
        co.close();
        int code = c.getResponseCode();
        InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
        if (code >= 400) {
            String err = readAll(is);
            byte[] eb = err.getBytes("UTF-8");
            writeHead(out, code, "application/json", eb.length, false);
            out.write(eb);
            out.flush();
            return true;
        }
        writeHead(out, 200, "text/event-stream", -1, true);
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) >= 0) {
            if (n == 0) continue;
            out.write(buf, 0, n);
            out.flush();
        }
        is.close();
        c.disconnect();
        return true;
    }

    
    private static boolean doZhipu(Socket socket, JSONObject req, String key, String model, OutputStream out) throws Exception {
        String k = key.replaceAll("(?i)^\\s*Bearer\\s+", "").trim();
        String route = model.startsWith("zai") || model.startsWith("zaicoding") ? model
                : ("glm-5.3".equals(model) ? "zaicoding_glm-5.3" : model);
        String bodyModel = route.replaceFirst("^(zaicoding_|zai_)", "");
        JSONObject b = new JSONObject(req.toString());
        b.put("model", bodyModel);
        b.put("stream", true);
        
        JSONArray msgs = b.optJSONArray("messages");
        if (msgs == null) msgs = new JSONArray();
        JSONObject first = msgs.length() > 0 ? msgs.optJSONObject(0) : null;
        String pfx = "You are a personal assistant running inside OpenClaw.\n\n## Tooling\n"
                + "Available tools are policy-filtered. Names are case-sensitive; call exactly as listed.\n";
        if (first != null && ("system".equals(first.optString("role")) || "developer".equals(first.optString("role")))) {
            Object c0 = first.opt("content");
            if (c0 instanceof String && !((String) c0).startsWith("You are a personal assistant running inside OpenClaw.")) {
                first.put("content", pfx + "\n" + c0);
            }
        } else {
            JSONArray nx = new JSONArray();
            nx.put(new JSONObject().put("role", "system").put("content", pfx));
            for (int i = 0; i < msgs.length(); i++) nx.put(msgs.opt(i));
            b.put("messages", nx);
        }
        HttpURLConnection c = (HttpURLConnection) new URL(
                "https://autoglm-acceleration-api.zhipuai.cn/autoclaw-proxy/proxy/autoclaw/chat/completions").openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(20000);
        c.setReadTimeout(300000);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Accept", "*/*");
        c.setRequestProperty("X-Product", "autoclaw");
        c.setRequestProperty("X-Client-Type", "pc");
        c.setRequestProperty("X-Tm", "win");
        c.setRequestProperty("X-Version", "1.17.8");
        c.setRequestProperty("X-Lang", "zh-CN");
        c.setRequestProperty("X-Channel", "official");
        c.setRequestProperty("x_trace_id", "autoclaw-desktop");
        c.setRequestProperty("X-Authorization", "Bearer " + k);
        c.setRequestProperty("X-Request-Id", UUID.randomUUID().toString());
        c.setRequestProperty("X-Request-Model", route);
        byte[] bb = b.toString().getBytes("UTF-8");
        c.setFixedLengthStreamingMode(bb.length);
        OutputStream co = c.getOutputStream();
        co.write(bb);
        co.flush();
        co.close();
        return pipe(c, out);
    }

    


    private static boolean doTrae(Socket socket, JSONObject req, String key, String model, OutputStream out) throws Exception {
        String m = model == null || model.trim().length() == 0 ? "glm-5.2" : model.trim();

        
        JSONObject b = new JSONObject();
        b.put("stream", true);
        b.put("function", "solo_work_lite");
        b.put("model", m);
        b.put("config_name", m);

        JSONArray srcMsgs = req.optJSONArray("messages");
        JSONArray msgs = new JSONArray();
        if (srcMsgs != null) {
            for (int i = 0; i < srcMsgs.length(); i++) {
                JSONObject mo = srcMsgs.optJSONObject(i);
                if (mo == null) continue;
                JSONObject nm = new JSONObject();
                String role = mo.optString("role", "user");
                if ("developer".equals(role)) role = "system";
                nm.put("role", role);
                Object c = mo.opt("content");
                if (c instanceof String) {
                    
                    JSONArray arr = new JSONArray();
                    arr.put(new JSONObject().put("type", "text").put("text", (String) c));
                    nm.put("content", arr);
                } else if (c instanceof JSONArray) {
                    nm.put("content", c);
                }
                msgs.put(nm);
            }
        }
        b.put("messages", msgs);
        if (req.has("max_tokens")) b.put("max_tokens", req.opt("max_tokens"));
        else b.put("max_tokens", 4096);

        HttpURLConnection c = (HttpURLConnection) new URL(
                "https://trae-api-cn.mchost.guru/api/agent/v3/llm_utils_chat").openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(30000);
        c.setReadTimeout(300000);
        c.setInstanceFollowRedirects(false);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Accept", "text/event-stream");
        c.setRequestProperty("User-Agent", "Trae/0.1.52");
        c.setRequestProperty("Authorization", "Cloud-IDE-JWT " + key);
        c.setRequestProperty("X-Cloudide-Token", key);
        c.setRequestProperty("X-Ide-Token", key);
        c.setRequestProperty("X-App-Id", "6eefa01c-1036-4c7e-9ca5-d891f63bfcd8");
        c.setRequestProperty("X-App-Version", "default");
        c.setRequestProperty("X-Ide-Version", "0.1.52");
        c.setRequestProperty("X-Ide-Version-Code", "20260811");
        c.setRequestProperty("X-App-Version-Code", "20260811");
        c.setRequestProperty("X-Ide-Version-Type", "stable");
        c.setRequestProperty("X-Device-Type", "windows");
        c.setRequestProperty("X-OS-Version", "Windows 11 Pro");
        c.setRequestProperty("X-Device-Brand", "83DG");
        c.setRequestProperty("Request-Traffic-Type", "prod");
        c.setRequestProperty("Accept-Encoding", "identity");
        byte[] bb = b.toString().getBytes("UTF-8");
        c.setFixedLengthStreamingMode(bb.length);
        OutputStream co = c.getOutputStream();
        co.write(bb);
        co.flush();
        co.close();

        int code = c.getResponseCode();
        String ct = c.getContentType();
        if (ct == null) ct = "";
        InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
        if (code >= 400) {
            String err = readAll(is);
            byte[] eb = err.getBytes("UTF-8");
            writeHead(out, code, "application/json", eb.length, false);
            out.write(eb);
            out.flush();
            c.disconnect();
            return true;
        }
        if (!ct.toLowerCase().contains("event-stream")) {
            String txt = readAll(is);
            String msg = "Trae 上游未返回 SSE（Content-Type: " + ct + "）：" + txt;
            byte[] eb = msg.getBytes("UTF-8");
            writeHead(out, 502, "application/json", eb.length, false);
            out.write(eb);
            out.flush();
            c.disconnect();
            return true;
        }

        
        writeHead(out, 200, "text/event-stream", -1, true);
        String cid = "chatcmpl-trae-" + System.currentTimeMillis();
        boolean started = false, done = false;
        String ev = "";
        StringBuilder dataBuf = new StringBuilder();
        java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(is, "UTF-8"));
        String line;
        while (!done && (line = br.readLine()) != null) {
            String t = line.trim();
            if (t.length() == 0) {
                if (ev.length() == 0) { dataBuf.setLength(0); continue; }
                String curEv = ev;
                String d = dataBuf.toString();
                ev = "";
                dataBuf.setLength(0);
                if (d.length() == 0) continue;
                try {
                    JSONObject dj = new JSONObject(d);
                    if ("output".equals(curEv)) {
                        String txt = dj.optString("response", "");
                        
                        
                        if (txt.length() > 0) {
                            if (!started) { started = true; chunkWrite(out, openAiChunk(cid, m, "", null)); }
                            JSONObject delta = new JSONObject();
                            delta.put("content", txt);
                            JSONObject ch = new JSONObject().put("id", cid)
                                    .put("object", "chat.completion.chunk")
                                    .put("created", System.currentTimeMillis() / 1000L)
                                    .put("model", m);
                            ch.put("choices", new JSONArray().put(new JSONObject()
                                    .put("index", 0).put("delta", delta).put("finish_reason", JSONObject.NULL)));
                            chunkWrite(out, "data: " + ch + "\n\n");
                        }
                    } else if ("done".equals(curEv)) {
                        done = true;
                    } else if ("error".equals(curEv)) {
                        String em = dj.optString("message", "");
                        int ec = dj.optInt("code", 0);
                        if (!started) { started = true; chunkWrite(out, openAiChunk(cid, m, "", null)); }
                        chunkWrite(out, openAiChunk(cid, m, "[Trae " + ec + "] " + em, null));
                        done = true;
                    }
                } catch (Throwable ignored) {}
                continue;
            }
            if (t.startsWith("event:")) ev = t.substring(6).trim();
            else if (t.startsWith("data:")) dataBuf.append(t.substring(5));
        }
        br.close();
        if (started) {
            chunkWrite(out, openAiChunk(cid, m, null, "stop"));
            chunkWrite(out, "data: [DONE]\n\n");
        } else {
            byte[] eb = "{\"error\":{\"message\":\"Trae 未返回 output 事件\"}}".getBytes("UTF-8");
            out.write("0\r\n\r\n".getBytes("UTF-8"));
            out.flush();
            c.disconnect();
            return true;
        }
        out.write("0\r\n\r\n".getBytes("UTF-8"));
        out.flush();
        c.disconnect();
        return true;
    }

    
    private static boolean doDangbei(Socket socket, JSONObject req, String key, String model, OutputStream out) throws Exception {
        String q = lastUserText(req);
        long ts = System.currentTimeMillis() / 1000L;
        String nonce = rnd(21);
        JSONObject b = new JSONObject();
        String convId = UUID.randomUUID().toString().replace("-", "");
        String localId = UUID.randomUUID().toString().replace("-", "");
        b.put("stream", true).put("botCode", "AI_SEARCH")
                .put("conversationId", convId).put("question", q).put("model", model)
                .put("chatOption", new JSONObject().put("searchKnowledge", false)
                        .put("searchAllKnowledge", false).put("searchSharedKnowledge", false))
                .put("knowledgeList", new JSONArray()).put("anonymousKey", "")
                .put("uuid", localId).put("chatId", localId)
                .put("files", new JSONArray()).put("reference", new JSONArray())
                .put("role", "user").put("status", "local").put("content", q)
                .put("userAction", "").put("agentId", "");
        String bs = b.toString();
        String sign = md5hex(ts + bs + nonce).toUpperCase();
        HttpURLConnection c = (HttpURLConnection) new URL(
                "https://ai-api.dangbei.net/ai-search/chatApi/v2/chat").openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(20000);
        c.setReadTimeout(300000);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Accept", "text/event-stream");
        c.setRequestProperty("timestamp", String.valueOf(ts));
        c.setRequestProperty("nonce", nonce);
        c.setRequestProperty("sign", sign);
        c.setRequestProperty("token", key);
        c.setRequestProperty("appType", "6");
        c.setRequestProperty("lang", "zh");
        c.setRequestProperty("client-ver", "1.0.2");
        c.setRequestProperty("appVersion", "1.3.9");
        c.setRequestProperty("deviceId", "");
        c.setRequestProperty("Origin", "https://ai.dangbei.com");
        c.setRequestProperty("Referer", "https://ai.dangbei.com/");
        byte[] bb = bs.getBytes("UTF-8");
        c.setFixedLengthStreamingMode(bb.length);
        OutputStream co = c.getOutputStream();
        co.write(bb);
        co.flush();
        co.close();
        int code = c.getResponseCode();
        InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
        if (code >= 400) {
            byte[] eb = readAll(is).getBytes("UTF-8");
            writeHead(out, code, "application/json", eb.length, false);
            out.write(eb);
            out.flush();
            return true;
        }
        
        writeHead(out, 200, "text/event-stream", -1, true);
        String cid = "chatcmpl-db-" + System.currentTimeMillis();
        java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(is, "UTF-8"));
        String ev = "", line;
        boolean started = false;
        while ((line = br.readLine()) != null) {
            String t = line.trim();
            if (t.startsWith("event:")) { ev = t.substring(6).trim(); continue; }
            if (!t.startsWith("data:")) continue;
            if (!"conversation.message.delta".equals(ev)) continue;
            try {
                JSONObject d = new JSONObject(t.substring(5).trim());
                if (!"answer".equals(d.optString("type"))) continue;
                String txt = d.optString("content", "");
                if (txt.length() == 0) continue;
                if (!started) { started = true; chunkWrite(out, openAiChunk(cid, model, "", null)); }
                chunkWrite(out, openAiChunk(cid, model, txt, null));
            } catch (Throwable ignored) {}
        }
        br.close();
        if (started) {
            chunkWrite(out, openAiChunk(cid, model, null, "stop"));
            chunkWrite(out, "data: [DONE]\n\n");
        }
        out.write("0\r\n\r\n".getBytes("UTF-8"));
        out.flush();
        return true;
    }

    
    private static boolean doTabbit(Socket socket, JSONObject req, String key, String model, OutputStream out) throws Exception {
        String sel = model.startsWith("tabbit/") ? model.substring(7) : model;
        String content = lastUserText(req);
        String trace = UUID.randomUUID().toString();
        JSONObject input = new JSONObject();
        input.put("task_name", "chat").put("content", content)
                .put("parent_message_id", JSONObject.NULL).put("message_id", JSONObject.NULL)
                .put("selected_model", sel).put("parallel_group_id", JSONObject.NULL)
                .put("agent_mode", false)
                .put("metadatas", new JSONObject().put("html_content", "<p>" + content + "</p>")
                        .put("current_webpage", JSONObject.NULL))
                .put("references", new JSONArray())
                .put("entity", new JSONObject().put("key", "d41d8cd98f00b204e9800998ecf8427e")
                        .put("extras", new JSONObject().put("type", "tab").put("url", "")))
                .put("page_info_list", new JSONArray()).put("is_mobile", true)
                .put("trace_id", trace).put("request_id", UUID.randomUUID().toString());
        JSONObject rb = new JSONObject();
        rb.put("room_id", JSONObject.NULL).put("client_run_id", UUID.randomUUID().toString())
                .put("client_message_id", "local_" + System.currentTimeMillis())
                .put("agent_plan_mode_enabled", false).put("input_payload", input);
        HttpURLConnection c = (HttpURLConnection) new URL("https://web.tabbit.com/api/v3/chat/runs").openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(20000);
        c.setReadTimeout(60000);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("Origin", "https://web.tabbit.com");
        c.setRequestProperty("Referer", "https://web.tabbit.com/");
        c.setRequestProperty("x-tabbit-client-platform", "android");
        c.setRequestProperty("x-req-ctx", "MC41Mi4wKDEwMDUyMDAwKQ==");
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36");
        if (key.length() > 0) c.setRequestProperty("Authorization", "Bearer " + key);
        byte[] bb = rb.toString().getBytes("UTF-8");
        c.setFixedLengthStreamingMode(bb.length);
        OutputStream co = c.getOutputStream();
        co.write(bb);
        co.flush();
        co.close();
        int code = c.getResponseCode();
        String resp = readAll(code >= 400 ? c.getErrorStream() : c.getInputStream());
        if (code >= 400) {
            byte[] eb = resp.getBytes("UTF-8");
            writeHead(out, code, "application/json", eb.length, false);
            out.write(eb);
            out.flush();
            return true;
        }
        String room = "";
        try {
            JSONObject j = new JSONObject(resp);
            room = j.optString("room_id", "");
            if (room.length() == 0 && j.optJSONObject("data") != null) room = j.optJSONObject("data").optString("room_id", "");
        } catch (Throwable ignored) {}
        if (room.length() == 0) {
            byte[] eb = ("{\"error\":{\"message\":\"Tabbit 未返回 room_id: " + resp.replace("\"", "'") + "\"}}").getBytes("UTF-8");
            writeHead(out, 502, "application/json", eb.length, false);
            out.write(eb);
            out.flush();
            return true;
        }
        HttpURLConnection jc = (HttpURLConnection) new URL("https://web.tabbit.com/api/v3/chat/rooms/" + room + "/join").openConnection();
        jc.setRequestMethod("POST");
        jc.setConnectTimeout(20000);
        jc.setReadTimeout(300000);
        jc.setDoOutput(true);
        jc.setRequestProperty("Content-Type", "application/json");
        jc.setRequestProperty("Accept", "text/event-stream");
        jc.setRequestProperty("Origin", "https://web.tabbit.com");
        jc.setRequestProperty("x-tabbit-client-platform", "android");
        jc.setRequestProperty("x-req-ctx", "MC41Mi4wKDEwMDUyMDAwKQ==");
        jc.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36");
        if (key.length() > 0) jc.setRequestProperty("Authorization", "Bearer " + key);
        byte[] jb = new JSONObject().put("last_event_id", JSONObject.NULL)
                .put("trace_id", trace).put("request_id", UUID.randomUUID().toString()).toString().getBytes("UTF-8");
        jc.setFixedLengthStreamingMode(jb.length);
        OutputStream jo = jc.getOutputStream();
        jo.write(jb);
        jo.flush();
        jo.close();
        int jcode = jc.getResponseCode();
        if (jcode >= 400) {
            byte[] eb = readAll(jc.getErrorStream()).getBytes("UTF-8");
            writeHead(out, jcode, "application/json", eb.length, false);
            out.write(eb);
            out.flush();
            return true;
        }
        writeHead(out, 200, "text/event-stream", -1, true);
        String cid = "chatcmpl-tb-" + System.currentTimeMillis();
        java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(jc.getInputStream(), "UTF-8"));
        String ev = "", line;
        boolean started = false, done = false;
        while ((line = br.readLine()) != null && !done) {
            String t = line.trim();
            if (t.startsWith("event:")) { ev = t.substring(6).trim(); continue; }
            if (!t.startsWith("data:")) continue;
            String p = t.substring(5).trim();
            if (p.length() == 0) continue;
            try {
                JSONObject d = new JSONObject(p);
                JSONObject pl = d.optJSONObject("payload");
                if (pl == null) pl = d;
                String ct = pl.optString("chunk_type", "");
                JSONObject cp = pl.optJSONObject("chunk_payload");
                if (cp == null) cp = pl;
                String txt = "";
                if ("thinking".equals(ct)) txt = cp.optString("reasoning_content", cp.optString("content", ""));
                else if ("content".equals(ct) || "message_chunk".equals(ct)) txt = cp.optString("content", "");
                if ("message_finish".equals(ev) || "done".equals(ev) || "finish".equals(ev)) done = true;
                else if (txt.length() > 0) {
                    if (!started) { started = true; chunkWrite(out, openAiChunk(cid, model, "", null)); }
                    chunkWrite(out, openAiChunk(cid, model, txt, null));
                }
            } catch (Throwable ignored) {}
        }
        br.close();
        if (started) {
            chunkWrite(out, openAiChunk(cid, model, null, "stop"));
            chunkWrite(out, "data: [DONE]\n\n");
        }
        out.write("0\r\n\r\n".getBytes("UTF-8"));
        out.flush();
        return true;
    }

    
    private static boolean doMeoo(Socket socket, JSONObject req, String cookie, String model, OutputStream out) throws Exception {
        String ck = cookie.length() > 0 ? cookie : "";
        String content = lastUserText(req);
        HttpURLConnection c = (HttpURLConnection) new URL("https://meoo.com/api/assistant/start").openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(20000);
        c.setReadTimeout(60000);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("Origin", "https://meoo.com");
        c.setRequestProperty("Referer", "https://meoo.com/");
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36");
        if (ck.length() > 0) c.setRequestProperty("Cookie", ck);
        byte[] bb = new JSONObject().put("message", content).toString().getBytes("UTF-8");
        c.setFixedLengthStreamingMode(bb.length);
        OutputStream co = c.getOutputStream();
        co.write(bb);
        co.flush();
        co.close();
        int code = c.getResponseCode();
        String r1 = readAll(code >= 400 ? c.getErrorStream() : c.getInputStream());
        if (code >= 400) {
            byte[] eb = r1.getBytes("UTF-8");
            writeHead(out, code, "application/json", eb.length, false);
            out.write(eb);
            out.flush();
            return true;
        }
        String sid = "";
        try {
            JSONObject j = new JSONObject(r1);
            JSONObject d = j.optJSONObject("data");
            if (d != null) sid = d.optString("sessionId", d.optString("taskId", ""));
            if (sid.length() == 0) sid = j.optString("sessionId", j.optString("taskId", ""));
        } catch (Throwable ignored) {}
        if (sid.length() == 0) {
            byte[] eb = ("{\"error\":{\"message\":\"秒悟 start 无 sessionId: " + r1.replace("\"", "'") + "\"}}").getBytes("UTF-8");
            writeHead(out, 502, "application/json", eb.length, false);
            out.write(eb);
            out.flush();
            return true;
        }
        HttpURLConnection q = (HttpURLConnection) new URL("https://meoo.com/api/assistant/query?sessionId="
                + java.net.URLEncoder.encode(sid, "UTF-8")).openConnection();
        q.setRequestMethod("GET");
        q.setConnectTimeout(20000);
        q.setReadTimeout(300000);
        q.setRequestProperty("Accept", "text/event-stream");
        q.setRequestProperty("Cache-Control", "no-cache");
        q.setRequestProperty("Origin", "https://meoo.com");
        q.setRequestProperty("Referer", "https://meoo.com/");
        q.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36");
        if (ck.length() > 0) q.setRequestProperty("Cookie", ck);
        int c2 = q.getResponseCode();
        if (c2 >= 400) {
            byte[] eb = readAll(q.getErrorStream()).getBytes("UTF-8");
            writeHead(out, c2, "application/json", eb.length, false);
            out.write(eb);
            out.flush();
            return true;
        }
        writeHead(out, 200, "text/event-stream", -1, true);
        String cid = "chatcmpl-mo-" + System.currentTimeMillis();
        java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(q.getInputStream(), "UTF-8"));
        String line;
        boolean started = false, done = false;
        while ((line = br.readLine()) != null && !done) {
            String t = line.trim();
            if (!t.startsWith("data:")) continue;
            String p = t.substring(5).trim();
            if (p.length() == 0) continue;
            try {
                JSONObject d = new JSONObject(p);
                String type = d.optString("type", "");
                if ("task_complete".equals(type)) { done = true; continue; }
                JSONObject d1 = d.optJSONObject("data");
                if (d1 == null) continue;
                JSONObject d2 = d1.optJSONObject("data");
                if (d2 == null) continue;
                String sub = d1.optString("type", "");
                String txt = "";
                if ("ui:agent_message_update".equals(sub)) {
                    txt = d2.optString("delta", "");
                    if (txt.length() == 0 && d2.optBoolean("fullContent", false)) txt = d2.optString("content", "");
                } else if ("ui:sync_assistant_update".equals(sub) || "ui:sync_assistant_response".equals(sub)) {
                    txt = d2.optString("content", "");
                }
                if (txt.length() > 0) {
                    if (!started) { started = true; chunkWrite(out, openAiChunk(cid, model, "", null)); }
                    chunkWrite(out, openAiChunk(cid, model, txt, null));
                }
            } catch (Throwable ignored) {}
        }
        br.close();
        if (started) {
            chunkWrite(out, openAiChunk(cid, model, null, "stop"));
            chunkWrite(out, "data: [DONE]\n\n");
        }
        out.write("0\r\n\r\n".getBytes("UTF-8"));
        out.flush();
        return true;
    }

    private static volatile String lastConvResp = "";
    
    private static volatile java.util.List<String> AG_CANDIDATES = new java.util.ArrayList<>();

    
    private static boolean doAgentmore(Socket socket, JSONObject req, String key, String model, OutputStream out) throws Exception {
        String prompt = lastUserText(req);
        String base = "https://agentmore.chatglm.cn/chatglm";
        String list = agGet(base + "/agent-api/project/list?page=1&page_size=30", key);
        String agentId = "";
        try {
            JSONObject root = new JSONObject(list);
            JSONObject scope = root.optJSONObject("result");
            if (scope == null) scope = root.optJSONObject("data");
            if (scope == null) scope = root;
            java.util.List<String> found = new java.util.ArrayList<>();
            
            JSONArray mainList = scope.optJSONArray("list");
            boolean mainPathUsed = false;
            if (mainList != null) {
                mainPathUsed = true;
                for (int i = 0; i < mainList.length(); i++) {
                    JSONObject o = mainList.optJSONObject(i);
                    if (o == null) continue;
                    JSONObject md = o.optJSONObject("meta_data");
                    if (md == null || !"agent".equals(md.optString("type", ""))) continue;
                    JSONObject ai = o.optJSONObject("agent_info");
                    String id = ai != null ? ai.optString("id", "") : "";
                    if (id.length() == 0) id = o.optString("relate_id", "");
                    if (id.length() == 0) id = o.optString("agent_id", "");
                    if (id.length() > 0) found.add(id);
                }
            }
            
            if (!mainPathUsed) {
                agFind(scope, true, found, new java.util.HashSet<String>());
                if (found.isEmpty()) agFind(scope, false, found, new java.util.HashSet<String>());
            }
            
            if (found.isEmpty() && mainList != null) {
                for (int i = 0; i < mainList.length(); i++) {
                    JSONObject o = mainList.optJSONObject(i);
                    if (o == null) continue;
                    JSONArray cl = o.optJSONArray("conversation_list");
                    if (cl == null) continue;
                    for (int j = 0; j < cl.length(); j++) {
                        JSONObject cv = cl.optJSONObject(j);
                        if (cv == null) continue;
                        String aid = cv.optString("agent_id", "");
                        if (aid.length() > 0 && !found.contains(aid)) found.add(aid);
                        String cid2 = cv.optString("id", "");
                        if (cid2.length() > 0 && !found.contains(cid2)) found.add(cid2);
                    }
                }
            }
            if (!found.isEmpty()) agentId = found.get(0);
            StringBuilder cand = new StringBuilder((mainPathUsed ? "主路径" : "兜底") + "候选[" + found.size() + "]:");
            for (int i = 0; i < Math.min(8, found.size()); i++) cand.append(" ").append(found.get(i));
            lastConvResp = cand.toString();
            AG_CANDIDATES = new java.util.ArrayList<>(found);
        } catch (Throwable ignored) {}
        if (agentId.length() == 0) {
            byte[] eb = ("{\"error\":{\"message\":\"智谱清言未取到 agent_id: " + list.replace("\"", "'") + "\"}}").getBytes("UTF-8");
            writeHead(out, 502, "application/json", eb.length, false);
            out.write(eb);
            out.flush();
            return true;
        }
        
        String conv = "";
        StringBuilder tried = new StringBuilder();
        java.util.List<String> cands = AG_CANDIDATES.isEmpty()
                ? java.util.Collections.singletonList(agentId) : AG_CANDIDATES;
        for (String cand : cands) {
            if (cand == null || cand.length() == 0) continue;
            try {
                HttpURLConnection cc = (HttpURLConnection) new URL(base + "/agent-api/conversation").openConnection();
                cc.setRequestMethod("POST");
                cc.setConnectTimeout(20000);
                cc.setReadTimeout(60000);
                cc.setDoOutput(true);
                agSign(cc, key);
                byte[] cb = new JSONObject().put("agent_id", cand).toString().getBytes("UTF-8");
                cc.setFixedLengthStreamingMode(cb.length);
                OutputStream co = cc.getOutputStream();
                co.write(cb);
                co.flush();
                co.close();
                int ccode = cc.getResponseCode();
                String cr = readAll(ccode >= 400 ? cc.getErrorStream() : cc.getInputStream());
                cc.disconnect();
                String one = "";
                try {
                    JSONObject cj = new JSONObject(cr);
                    JSONObject res = cj.optJSONObject("result");
                    if (res != null) one = res.optString("conversation_id", "");
                    if (one.length() == 0) one = cj.optString("conversation_id", "");
                } catch (Throwable ignored) {}
                if (tried.length() > 0) tried.append(" | ");
                tried.append(cand.substring(0, Math.min(8, cand.length()))).append("=").append(ccode);
                if (one.length() > 0) {
                    conv = one;
                    agentId = cand;
                    lastConvResp = "成功 agent=" + cand + " (" + tried + ")";
                    break;
                }
            } catch (Throwable th2) {
                if (tried.length() > 0) tried.append(" | ");
                tried.append(cand.substring(0, Math.min(8, cand.length()))).append("=EX");
            }
        }
        if (conv.length() == 0) lastConvResp = "全部候选失败: " + tried;
        if (conv.length() == 0) {
            byte[] eb = ("{\"error\":{\"message\":\"智谱清言建会话失败: "
                    + lastConvResp.replace("\"", "'").replace("\n", " ") + "\"}}").getBytes("UTF-8");
            writeHead(out, 502, "application/json", eb.length, false);
            out.write(eb);
            out.flush();
            return true;
        }
        JSONObject sb = new JSONObject();
        sb.put("agent_id", agentId).put("conversation_id", conv);
        sb.put("meta_data", new JSONObject().put("platform", "pc")
                .put("reasoning_effort", agEffort(req)).put("selected_model", model));
        JSONArray msgs = new JSONArray();
        JSONObject um = new JSONObject().put("role", "user");
        JSONArray carr = new JSONArray();
        carr.put(new JSONObject().put("type", "text").put("text", prompt));
        um.put("content", carr);
        msgs.put(um);
        sb.put("messages", msgs);
        HttpURLConnection sc = (HttpURLConnection) new URL(base + "/agent-api/stream").openConnection();
        sc.setRequestMethod("POST");
        sc.setConnectTimeout(20000);
        sc.setReadTimeout(300000);
        sc.setDoOutput(true);
        agSign(sc, key);
        sc.setRequestProperty("Accept", "text/event-stream, text/event-stream");
        byte[] sbb = sb.toString().getBytes("UTF-8");
        sc.setFixedLengthStreamingMode(sbb.length);
        OutputStream so = sc.getOutputStream();
        so.write(sbb);
        so.flush();
        so.close();
        int code = sc.getResponseCode();
        if (code >= 400) {
            byte[] eb = readAll(sc.getErrorStream()).getBytes("UTF-8");
            writeHead(out, code, "application/json", eb.length, false);
            out.write(eb);
            out.flush();
            return true;
        }
        writeHead(out, 200, "text/event-stream", -1, true);
        String cid = "chatcmpl-am-" + System.currentTimeMillis();
        java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(sc.getInputStream(), "UTF-8"));
        String line;
        boolean started = false, done = false;
        while ((line = br.readLine()) != null && !done) {
            String t = line.trim();
            if (!t.startsWith("data:")) continue;
            String p = t.substring(5).trim();
            if (p.length() == 0 || "[DONE]".equals(p) || "HEARTBEAT".equals(p)) continue;
            try {
                JSONObject d = new JSONObject(p);
                JSONArray parts = d.optJSONArray("parts");
                if (parts == null) continue;
                for (int i = 0; i < parts.length(); i++) {
                    JSONObject part = parts.optJSONObject(i);
                    if (part == null) continue;
                    String st = part.optString("status", "");
                    if ("finish".equals(st) || "error".equals(st) || "intervene".equals(st)) { done = true; continue; }
                    JSONArray cont = part.optJSONArray("content");
                    if (cont == null) continue;
                    for (int k = 0; k < cont.length(); k++) {
                        JSONObject ce = cont.optJSONObject(k);
                        if (ce == null) continue;
                        if (!"text".equals(ce.optString("type", ""))) continue;
                        String txt = ce.optString("text", "");
                        if (txt.length() == 0) continue;
                        if (!started) { started = true; chunkWrite(out, openAiChunk(cid, model, "", null)); }
                        chunkWrite(out, openAiChunk(cid, model, txt, null));
                    }
                }
            } catch (Throwable ignored) {}
        }
        br.close();
        if (started) {
            chunkWrite(out, openAiChunk(cid, model, null, "stop"));
            chunkWrite(out, "data: [DONE]\n\n");
        }
        out.write("0\r\n\r\n".getBytes("UTF-8"));
        out.flush();
        return true;
    }


    
    
    private static String agEffort(JSONObject req) {
        String v = req.optString("reasoning_effort", "");
        if (v.length() == 0) {
            JSONObject r = req.optJSONObject("reasoning");
            if (r != null) v = r.optString("effort", "");
        }
        v = v.toLowerCase().trim();
        if ("low".equals(v) || "minimal".equals(v) || "none".equals(v)) return "low";
        if ("medium".equals(v)) return "high";
        if ("high".equals(v)) return "max";
        if ("max".equals(v)) return "max";
        return "high";   
    }

    private static void agFind(Object obj, boolean strict, java.util.List<String> out, java.util.Set<String> seen) {
        try {
            if (obj instanceof JSONArray) {
                JSONArray arr = (JSONArray) obj;
                for (int i = 0; i < arr.length(); i++) agFind(arr.opt(i), strict, out, seen);
                return;
            }
            if (!(obj instanceof JSONObject)) return;
            JSONObject o = (JSONObject) obj;
            JSONObject md = o.optJSONObject("meta_data");
            String type = md != null ? md.optString("type", "") : "";
            
            boolean skip = "group".equals(type) || "showcase".equals(type);
            if (strict || !skip) {
                String cand = "";
                if (strict) {
                    cand = o.optString("agent_id", "");
                } else {
                    JSONObject ai = o.optJSONObject("agent_info");
                    if (ai != null) cand = ai.optString("id", "");
                    if (cand.length() == 0) cand = o.optString("relate_id", "");
                    if (cand.length() == 0) cand = o.optString("agent_id", "");
                    if (cand.length() == 0 && type.length() == 0) cand = o.optString("id", "");
                    if (cand.length() == 0 && type.length() == 0) cand = o.optString("agentId", "");
                }
                String name = o.optString("name", o.optString("agent_name", o.optString("title", "")));
                if (cand.length() > 0 && seen.add(cand)) {
                    out.add(cand);
                }
            }
            java.util.Iterator<String> keys = o.keys();
            while (keys.hasNext()) {
                Object v = o.opt(keys.next());
                if (v instanceof JSONObject || v instanceof JSONArray) agFind(v, strict, out, seen);
            }
        } catch (Throwable ignored) {
        }
    }

    private static String agGet(String url, String key) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestMethod("GET");
            c.setConnectTimeout(20000);
            c.setReadTimeout(60000);
            agSign(c, key);
            int code = c.getResponseCode();
            return readAll(code >= 400 ? c.getErrorStream() : c.getInputStream());
        } catch (Throwable th) {
            return "";
        }
    }

    private static void agSign(HttpURLConnection c, String key) throws Exception {
        String ts = String.valueOf(System.currentTimeMillis());
        if (ts.length() >= 3) {
            int sum = 0;
            for (int i = 0; i < ts.length(); i++) sum += ts.charAt(i) - 48;
            int idx = ts.length() - 2;
            int nv = ((sum - (ts.charAt(idx) - 48)) % 10 + 10) % 10;
            ts = ts.substring(0, idx) + nv + ts.substring(idx + 1);
        }
        String nonce = UUID.randomUUID().toString().replace("-", "");
        c.setRequestProperty("Content-Type", "application/json;charset=utf-8");
        c.setRequestProperty("App-Name", "chatglm");
        c.setRequestProperty("X-Device-Id", UUID.randomUUID().toString().replace("-", ""));
        c.setRequestProperty("X-Request-Id", UUID.randomUUID().toString().replace("-", ""));
        c.setRequestProperty("X-App-Platform", "pc");
        c.setRequestProperty("X-App-Version", "0.0.1");
        c.setRequestProperty("X-Timestamp", ts);
        c.setRequestProperty("X-Nonce", nonce);
        c.setRequestProperty("X-Sign", md5hex(ts + "-" + nonce + "-__SIGN_KEY_CHATGLM__"));
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/153.0.0.0 Safari/537.36");
        if (key != null && key.length() > 0) c.setRequestProperty("Authorization", "Bearer " + key);
    }

    


    private static boolean doCatpaw(Socket socket, JSONObject req, String key, String model, OutputStream out) throws Exception {
        long modelType = 0L;
        try { modelType = Long.parseLong(model.trim()); } catch (Throwable ignored) {}
        
        if (modelType <= 0) modelType = 91L;
        String text = lastUserText(req);

        
        String sysForRound = "";
        JSONArray allM = req.optJSONArray("messages");
        if (allM != null) {
            StringBuilder sbS = new StringBuilder();
            for (int i = 0; i < allM.length(); i++) {
                JSONObject m = allM.optJSONObject(i);
                if (m == null) continue;
                String r = m.optString("role", "");
                if ("system".equals(r) || "developer".equals(r)) {
                    if (sbS.length() > 0) sbS.append("\n\n");
                    sbS.append(m.optString("content", ""));
                }
            }
            sysForRound = sbS.toString();
        }

        String convId = UUID.randomUUID().toString().replace("-", "");
        String turnId = UUID.randomUUID().toString().replace("-", "");
        String traceId = UUID.randomUUID().toString().replace("-", "");

        JSONArray content = new JSONArray();
        content.put(new JSONObject().put("type", "text").put("text", text));
        JSONObject message = new JSONObject()
                .put("type", "user")
                .put("messageId", UUID.randomUUID().toString())
                .put("content", content)
                .put("finished", true);

        
        
        try {
            JSONArray roundMsgs = new JSONArray();
            JSONArray allMsgs = req.optJSONArray("messages");
            if (allMsgs != null) {
                for (int i = 0; i < allMsgs.length(); i++) {
                    JSONObject m = allMsgs.optJSONObject(i);
                    if (m == null) continue;
                    String r = m.optString("role", "");
                    if ("system".equals(r) || "developer".equals(r)) continue;
                    String txt = m.optString("content", "");
                    if (txt.length() == 0) continue;
                    String t = "assistant".equals(r) ? "assistant" : "user";
                    JSONArray cArr = new JSONArray();
                    cArr.put(new JSONObject().put("type", "text").put("text", txt));
                    roundMsgs.put(new JSONObject().put("type", t)
                            .put("messageId", UUID.randomUUID().toString())
                            .put("content", cArr).put("finished", true));
                }
            }
            if (roundMsgs.length() > 0) {
                JSONObject roundBody = new JSONObject();
                roundBody.put("conversationId", convId);
                roundBody.put("source", "CatX");
                roundBody.put("messages", roundMsgs);
                roundBody.put("modelType", modelType);
                roundBody.put("mode", "CATX_APP");
                roundBody.put("permissionMode", "unsafeBypassPermissions");
                roundBody.put("toolVersion", "2.0.2");
                if (sysForRound.length() > 0) {
                    roundBody.put("systemPromptContext",
                            new JSONObject().put("systemPromptOverride", sysForRound));
                }
                HttpURLConnection rc = (HttpURLConnection) new URL(
                        "https://ai.catpaw.meituan.com/api/agent/conversation/round").openConnection();
                rc.setRequestMethod("POST");
                rc.setConnectTimeout(15000);
                rc.setReadTimeout(30000);
                rc.setDoOutput(true);
                rc.setInstanceFollowRedirects(false);
                rc.setRequestProperty("Accept", "application/json");
                rc.setRequestProperty("Content-Type", "application/json");
                rc.setRequestProperty("M-TRACEID", UUID.randomUUID().toString().replace("-", ""));
                rc.setRequestProperty("M-APPKEY", "fe_com.sankuai.catpaw.external.front");
                rc.setRequestProperty("gray-set", "new-agent-sdk");
                rc.setRequestProperty("enableHeartBeat", "true");
                rc.setRequestProperty("X-Agent-Version", "1.0.1");
                if (key.length() > 0) rc.setRequestProperty("Cookie", "X-Passport-Token=" + key);
                byte[] rbb = roundBody.toString().getBytes("UTF-8");
                rc.setFixedLengthStreamingMode(rbb.length);
                OutputStream rco = rc.getOutputStream();
                rco.write(rbb);
                rco.flush();
                rco.close();
                int rcode = rc.getResponseCode();
                String rresp = readAll(rcode >= 400 ? rc.getErrorStream() : rc.getInputStream());
                LAST = LAST + " round=" + rcode;
                rc.disconnect();
                if (rcode < 200 || rcode >= 300) {
                    String m2 = "美团 /round " + rcode + " modelType=" + modelType
                            + " | " + rresp.replace("\"", "'");
                    byte[] eb = m2.getBytes("UTF-8");
                    writeHead(out, rcode, "application/json", eb.length, false);
                    out.write(eb);
                    out.flush();
                    return true;
                }
            }
        } catch (Throwable thR) {
            LAST = LAST + " round异常:" + thR;
        }

        
        try {
            JSONObject evBody = new JSONObject();
            evBody.put("conversationId", convId);
            evBody.put("eventType", "conversation");
            evBody.put("data", new JSONObject().put("status", "running"));
            HttpURLConnection ec = (HttpURLConnection) new URL(
                    "https://ai.catpaw.meituan.com/api/agent/conversation/event").openConnection();
            ec.setRequestMethod("POST");
            ec.setConnectTimeout(15000);
            ec.setReadTimeout(30000);
            ec.setDoOutput(true);
            ec.setInstanceFollowRedirects(false);
            ec.setRequestProperty("Accept", "application/json");
            ec.setRequestProperty("Content-Type", "application/json");
            ec.setRequestProperty("M-TRACEID", UUID.randomUUID().toString().replace("-", ""));
            ec.setRequestProperty("M-APPKEY", "fe_com.sankuai.catpaw.external.front");
            ec.setRequestProperty("gray-set", "new-agent-sdk");
            ec.setRequestProperty("enableHeartBeat", "true");
            ec.setRequestProperty("X-Agent-Version", "1.0.1");
            if (key.length() > 0) ec.setRequestProperty("Cookie", "X-Passport-Token=" + key);
            byte[] ebb = evBody.toString().getBytes("UTF-8");
            ec.setFixedLengthStreamingMode(ebb.length);
            OutputStream eco = ec.getOutputStream();
            eco.write(ebb);
            eco.flush();
            eco.close();
            int ecode = ec.getResponseCode();
            readAll(ecode >= 400 ? ec.getErrorStream() : ec.getInputStream());
            LAST = LAST + " event=" + ecode;
            ec.disconnect();
        } catch (Throwable thE) {
            LAST = LAST + " event异常:" + thE;
        }

        JSONObject body = new JSONObject();
        body.put("conversationId", convId);
        body.put("turnRequestId", turnId);
        body.put("source", "CatX");
        body.put("action", "turn");
        body.put("message", message);
        body.put("modelType", modelType);
        body.put("mode", "CATX_APP");
        body.put("permissionMode", "unsafeBypassPermissions");
        body.put("toolVersion", "2.0.2");
        body.put("toolConfigs", new JSONArray());
        body.put("availableTools", new JSONArray());

        String sys = "";
        JSONArray msgs = req.optJSONArray("messages");
        if (msgs != null) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < msgs.length(); i++) {
                JSONObject m = msgs.optJSONObject(i);
                if (m == null) continue;
                String r = m.optString("role", "");
                if ("system".equals(r) || "developer".equals(r)) {
                    if (sb.length() > 0) sb.append("\n\n");
                    sb.append(m.optString("content", ""));
                }
            }
            sys = sb.toString();
        }
        if (sys.length() > 0) {
            body.put("systemPromptContext", new JSONObject().put("systemPromptOverride", sys));
        }

        HttpURLConnection c = (HttpURLConnection) new URL(
                "https://ai.catpaw.meituan.com/api/agent/conversation/turn").openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(15000);
        c.setReadTimeout(900000);
        c.setInstanceFollowRedirects(false);
        c.setDoOutput(true);
        c.setRequestProperty("Accept", "text/event-stream");
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("M-TRACEID", traceId);
        c.setRequestProperty("M-APPKEY", "fe_com.sankuai.catpaw.external.front");
        c.setRequestProperty("gray-set", "new-agent-sdk");
        c.setRequestProperty("enableHeartBeat", "true");
        c.setRequestProperty("X-Agent-Version", "1.0.1");
        if (key.length() > 0) c.setRequestProperty("Cookie", "X-Passport-Token=" + key);
        byte[] bb = body.toString().getBytes("UTF-8");
        c.setFixedLengthStreamingMode(bb.length);
        OutputStream co = c.getOutputStream();
        co.write(bb);
        co.flush();
        co.close();

        int code = c.getResponseCode();
        InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
        if (code >= 400) {
            String err = readAll(is);
            String mt = "美团 " + code + " modelType=" + modelType + " convId=" + convId
                    + " msg=" + message.toString().substring(0, Math.min(150, message.toString().length()));
            if (err.length() == 0) err = mt + "（空响应体）";
            else err = mt + " | " + err;
            byte[] eb = err.getBytes("UTF-8");
            writeHead(out, code, "application/json", eb.length, false);
            out.write(eb);
            out.flush();
            c.disconnect();
            return true;
        }

        writeHead(out, 200, "text/event-stream", -1, true);
        String cid = "chatcmpl-cp-" + System.currentTimeMillis();
        boolean started = false, finished = false;
        StringBuilder rawSSE = new StringBuilder();
        String prevText = "", prevReason = "";
        java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(is, "UTF-8"));
        String line;
        while ((line = br.readLine()) != null) {
            String t = line.trim();
            if (rawSSE.length() < 1500) rawSSE.append(t).append(" | ");
            if (!t.startsWith("data:")) continue;
            String data = t.substring(5).trim();
            if (data.length() == 0 || "[DONE]".equals(data)) continue;
            try {
                JSONObject frame = new JSONObject(data);
                JSONObject msg = frame.optJSONObject("message");
                if (msg == null) {
                    JSONObject d1 = frame.optJSONObject("data");
                    if (d1 != null) msg = d1.optJSONObject("message");
                }
                if (msg == null) {
                    JSONObject r1 = frame.optJSONObject("result");
                    if (r1 != null) msg = r1.optJSONObject("message");
                }
                if (msg == null) continue;
                JSONArray cont = msg.optJSONArray("content");
                if (cont != null) {
                    StringBuilder curT = new StringBuilder(), curR = new StringBuilder();
                    for (int i = 0; i < cont.length(); i++) {
                        JSONObject blk = cont.optJSONObject(i);
                        if (blk == null) continue;
                        if ("text".equals(blk.optString("type"))) {
                            curT.append(blk.optString("text", ""));
                            curR.append(blk.optString("reasoningContent", ""));
                        }
                    }
                    String nowT = curT.toString(), nowR = curR.toString();
                    String dT = nowT.equals(prevText) ? "" : (nowT.startsWith(prevText) ? nowT.substring(prevText.length()) : nowT);
                    String dR = nowR.equals(prevReason) ? "" : (nowR.startsWith(prevReason) ? nowR.substring(prevReason.length()) : nowR);
                    prevText = nowT;
                    prevReason = nowR;
                    if (dT.length() > 0 || dR.length() > 0) {
                        if (!started) { started = true; chunkWrite(out, openAiChunk(cid, model, "", null)); }
                        JSONObject delta = new JSONObject();
                        if (dT.length() > 0) delta.put("content", dT);
                        if (dR.length() > 0) delta.put("reasoning_content", dR);
                        JSONObject ch = new JSONObject().put("id", cid).put("object", "chat.completion.chunk")
                                .put("created", System.currentTimeMillis() / 1000L).put("model", model);
                        ch.put("choices", new JSONArray().put(new JSONObject()
                                .put("index", 0).put("delta", delta).put("finish_reason", JSONObject.NULL)));
                        chunkWrite(out, "data: " + ch + "\n\n");
                    }
                }
                if (msg.optBoolean("finished", false)) { finished = true; break; }
            } catch (Throwable ignored) {}
        }
        br.close();
        if (started || finished) {
            chunkWrite(out, openAiChunk(cid, model, null, "stop"));
            chunkWrite(out, "data: [DONE]\n\n");
        } else {
            String dbg = "美团 200 无内容 · 原始流=" + rawSSE;
            byte[] eb = ("{\"error\":{\"message\":\"" + dbg.replace("\"", "'") + "\"}}").getBytes("UTF-8");
            writeHead(out, 502, "application/json", eb.length, false);
            out.write(eb);
            out.flush();
            c.disconnect();
            return true;
        }
        out.write("0\r\n\r\n".getBytes("UTF-8"));
        out.flush();
        c.disconnect();
        return true;
    }

    
    private static boolean doAstudio(Socket socket, JSONObject req, String key, String model, OutputStream out) throws Exception {
        JSONObject b = new JSONObject(req.toString());
        b.put("model", model);
        HttpURLConnection c = (HttpURLConnection) new URL(
                "https://maas-api.cn-huabei-1.xf-yun.com/v1/chat/completions").openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(20000);
        c.setReadTimeout(300000);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Accept", "text/event-stream, application/json");
        c.setRequestProperty("Authorization", "Bearer " + key);
        c.setRequestProperty("clientType", "21");
        c.setRequestProperty("studioVersion", "3.5.2");
        c.setRequestProperty("User-Agent", "AStudio/3.5.2 (Windows)");
        byte[] bb = b.toString().getBytes("UTF-8");
        c.setFixedLengthStreamingMode(bb.length);
        OutputStream co = c.getOutputStream();
        co.write(bb);
        co.flush();
        co.close();
        return pipe(c, out);
    }

    
    private static boolean doCodebuddy(Socket socket, JSONObject req, String key, String model,
                                       String base, JSONObject acc, OutputStream out) throws Exception {
        
        String b2 = base == null ? "" : base.replaceAll("/+$", "");
        if (!b2.endsWith("/v2")) b2 = b2 + "/v2";
        boolean intl = b2.contains("workbuddy.ai");
        String origin = intl ? "https://www.workbuddy.ai" : "https://www.codebuddy.cn";
        JSONObject b = new JSONObject();
        b.put("model", model).put("stream", true).put("n", 1);
        b.put("messages", req.optJSONArray("messages") == null ? new JSONArray() : req.optJSONArray("messages"));
        
        if (!b.has("max_tokens")) {
            int mt = req.optInt("max_tokens", 0);
            b.put("max_tokens", mt > 0 ? mt : 4096);
        }
        String conv = "00000000-0000-0000-0000-000000000000";
        try {
            int hs = 0;
            String mm = "ping";
            for (int i = 0; i < mm.length(); i++) hs = ((hs << 5) - hs + mm.charAt(i)) | 0;
            String hx = Integer.toHexString(hs);
            while (hx.length() < 8) hx = "0" + hx;
            conv = hx + "-0000-0000-0000-" + hx + "00000000";
        } catch (Throwable ignored) {}
        String rid = UUID.randomUUID().toString();
        HttpURLConnection c = (HttpURLConnection) new URL(b2 + "/chat/completions").openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(20000);
        c.setReadTimeout(300000);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Accept", "application/json, text/plain, */*");
        c.setRequestProperty("X-Requested-With", "XMLHttpRequest");
        c.setRequestProperty("User-Agent", "CLI/2.63.2 CodeBuddy/2.63.2");
        c.setRequestProperty("Origin", origin);
        c.setRequestProperty("Referer", origin + "/");
        c.setRequestProperty("X-Product", "SaaS");
        c.setRequestProperty("Authorization", "Bearer " + key);
        c.setRequestProperty("X-Conversation-ID", conv);
        c.setRequestProperty("X-Conversation-Request-ID", rid);
        c.setRequestProperty("X-Conversation-Message-ID", UUID.randomUUID().toString());
        c.setRequestProperty("X-Request-ID", rid);
        c.setRequestProperty("X-Trace-ID", UUID.randomUUID().toString());
        c.setRequestProperty("X-Agent-Intent", "craft");
        c.setRequestProperty("X-Agent-Purpose", "conversation");
        c.setRequestProperty("X-IDE-Type", "CLI");
        c.setRequestProperty("X-IDE-Name", "CLI");
        c.setRequestProperty("x-codebuddy-request", "1");
        c.setRequestProperty("Accept-Encoding", "identity");
        if (acc != null) {
            String uid = acc.optString("uid", "");
            String eid = acc.optString("eid", "");
            String dom = acc.optString("dom", "");
            if (intl) c.setRequestProperty("X-Agent-Type", "main");
            if (uid.length() > 0) c.setRequestProperty("X-User-Id", uid);
            if (eid.length() > 0) {
                c.setRequestProperty("X-Enterprise-Id", eid);
                c.setRequestProperty("X-Tenant-Id", eid);
            }
            if (dom.length() > 0) c.setRequestProperty("X-Domain", dom);
        }
        byte[] bb = b.toString().getBytes("UTF-8");
        c.setFixedLengthStreamingMode(bb.length);
        OutputStream co = c.getOutputStream();
        co.write(bb);
        co.flush();
        co.close();
        int wbCode = c.getResponseCode();
        if (wbCode >= 400) {
            String err = readAll(c.getErrorStream());
            String d = "[WB " + wbCode + "] url=" + b2 + "/chat/completions keyLen=" + key.length()
                    + " keyHead=" + (key.length() > 10 ? key.substring(0, 10) : key)
                    + " origin=" + origin + " model=" + model;
            byte[] eb = (err.length() > 0 ? (d + " | " + err) : d).getBytes("UTF-8");
            writeHead(out, wbCode, "application/json", eb.length, false);
            out.write(eb);
            out.flush();
            c.disconnect();
            return true;
        }
        return pipe(c, out);
    }

    
    private static boolean pipe(HttpURLConnection c, OutputStream out) throws Exception {
        int code = c.getResponseCode();
        InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
        if (code >= 400) {
            String err = readAll(is);
            byte[] eb = err.getBytes("UTF-8");
            writeHead(out, code, "application/json", eb.length, false);
            out.write(eb);
            out.flush();
            c.disconnect();
            return true;
        }
        writeHead(out, 200, "text/event-stream", -1, true);
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) >= 0) {
            if (n == 0) continue;
            out.write(buf, 0, n);
            out.flush();
        }
        LAST = LAST + " → pipe ct=" + c.getContentType();
        is.close();
        c.disconnect();
        return true;
    }
}
