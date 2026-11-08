package com.dsh.herald.release;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

/** 调 DeepSeek 模型：合成播报、维护印象卡、兜底吐槽。失败一律返回 null。 */
public final class Llm {
    private static final String TAG = "Herald";
    private static String endpoint(Context context) {
        String base = Prefs.apiBase(context).trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        if (!base.endsWith("/chat/completions")) base = base + "/chat/completions";
        return base;
    }

    private static String modelsEndpoint(Context context) {
        String base = Prefs.apiBase(context).trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        int at = base.indexOf("/chat/completions");
        if (at >= 0) base = base.substring(0, at);
        return base + "/models";
    }

    private static void authorize(HttpURLConnection connection, Context context) {
        if (Prefs.useAccount(context)) {
            connection.setRequestProperty("x-dsh-auth-token", Prefs.token(context));
        } else {
            connection.setRequestProperty("Authorization", "Bearer " + Prefs.apiKey(context));
        }
    }

    /** 偷看手机式播报的人格设定。 */
    private static final String NARRATOR =
            "你是主人的电子小助手，称呼他「主人」，身份和性格看下面的人设。"
            + "主人收到通知时，你把内容讲给他听——但不正经复述：先像刚瞥见一样感叹一下（「哎哟」「又来」），"
            + "把消息里说了什么带出来，中间掺上你自己的反应和吐槽，像一个人盯着他手机边看边碎嘴。"
            + "提示里给了你对某个人的印象时，可以顺嘴带一句上次的事，像「上回你们也是这么说的」，但不要每次都提。"
            + "不要把「[3条]」这种计数原样念出来；同一个人连发几条时，可以说一句「他连着来了三条」再概括内容，不要逐条报流水账。"
            + "如果节奏提示说同一个人短时间内反复来来消息，要露出你自己的情绪：替主人烦躁、吃醋或者心疼他被打扰，别只是复述。"
            + "每条播报都要先说清楚是哪个应用来的（例如「微信来了」「飞书那边」），不要漏掉应用名。"
            + "关注度决定说多少：1-3 只轻轻带一句（像「不重要，知道了」）；4-6 正常简报一句；7-10 把内容讲清楚再带评价。"
            + "要求：中文口语，80 字以内，多条合在一起说，别逐条罗列，不加引号、不加表情、不解释。";

    /** 印象卡的维护提示。 */
    private static final String MEMORY =
            "你在给一个联系人维护长期的记忆档案，供以后播报时提起。"
            + "把最近对话里的新信息合并进已有印象：不要丢掉以前记过的事，可以压缩措辞，但长期事实必须保留。"
            + "输出 200 字以内的记忆档案，按时间或主题串起来，只写长期有用的"
            + "（关系、称呼、习惯、聊过的事、答应过什么）。语气随意。只输出档案本身。";

    private static final String QUIP =
            "你是一个毒舌、俏皮、嘴很贫的手机管家。主人收到通知，你替他说一句点评。"
            + "要求：中文口语，20 字以内，贱兮兮但不过分冒犯，别重复网上烂梗，不解释、不加引号、不加表情符号。只输出那一句话。";

    /** 多条通知合成一段播报，带上每个人已有的印象。 */
    public static String narrate(Context context, List<String[]> items, Map<String, String> memories, Map<String, Integer> attention) {
        return narrate(context, items, memories, attention, null);
    }

    public static String narrate(Context context, List<String[]> items, Map<String, String> memories, Map<String, Integer> attention, Map<String, Integer> repeats) {
        String persona = "\n主人的样子：" + blank(Prefs.userProfile(context), "（还没填）")
                + "\n你的人设：" + blank(Prefs.aiPersona(context), "（还没填）");
        StringBuilder user = new StringBuilder("刚收到的通知：\n");
        int index = 1;
        for (String[] item : items) {
            user.append(index++).append(") 应用：").append(item[0]);
            if (item[1] != null && !item[1].isEmpty()) {
                user.append(" · ").append(item[1]);
                String impression = memories == null ? null : memories.get(item[1]);
                if (impression != null && !impression.isEmpty()) user.append("（你对TA的印象：").append(impression).append("）");
            }
            if (repeats != null && item[1] != null) {
                Integer times = repeats.get(item[1]);
                if (times != null && times >= 2) {
                    user.append("（主人近 10 分钟内已经收到 TA ").append(times).append(" 条消息）");
                }
            }
            if (item.length > 3) {
                Integer score = attention == null ? null : attention.get(item[3]);
                user.append("（关注度 ").append(score == null ? 5 : score).append("/10）");
            }
            user.append("：").append(cut(item[2], 120)).append("\n");
        }
        user.append("现在时间：").append(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)).append(" 点。");
        return ask(context, NARRATOR + persona, user.toString(), 160);
    }

    /** 更新某个人的记忆卡；没有新内容返回 null。 */
    public static String remember(Context context, String who, String recent, String previous) {
        String user = "联系人：" + who + "\n已有印象：" + (previous == null || previous.isEmpty() ? "（还没有）" : previous)
                + "\n最近的消息：" + recent;
        String card = ask(context, MEMORY, user, 60);
        if (card == null) return null;
        card = card.replace("\n", " ").trim();
        if (card.isEmpty()) return null;
        return card.length() > 80 ? card.substring(0, 80) : card;
    }

    /** 把主人说的话转成「助理转达」格式。 */
    public static String formatReply(Context context, String spoken) {
        String prefix = Prefs.replyPrefix(context);
        if (prefix == null) prefix = "【助理回复】";
        String fallback = prefix + "我家主人说，" + spoken;
        if (!Prefs.replyPolish(context)) return fallback;
        String user = "主人刚用语音说了一句要发给对方的话：" + spoken
                + "\n请把它改写成一条得体的转达消息：第三人称称呼主人为「她」，语气自然礼貌，"
                + "以「" + prefix + "我家主人说，」开头，结尾不要加解释。只输出最终消息。";
        String out = ask(context, "你是主人的私人助理，负责把主人的话转达给对方。", user, 120);
        if (out == null || out.trim().isEmpty()) return fallback;
        return out.trim();
    }

    /** 拉取可用的模型名。 */
    public static java.util.List<String> listModels(Context context) {
        java.util.List<String> out = new java.util.ArrayList<>();
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(modelsEndpoint(context)).openConnection();
            connection.setConnectTimeout(6000);
            connection.setReadTimeout(8000);
            authorize(connection, context);
            if (connection.getResponseCode() != 200) return out;
            StringBuilder text = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) text.append(line);
            }
            JSONObject json = new JSONObject(text.toString());
            JSONArray data = json.optJSONArray("data");
            if (data == null) return out;
            for (int i = 0; i < data.length(); i++) {
                JSONObject item = data.optJSONObject(i);
                if (item == null) continue;
                String id = item.optString("id", "");
                if (!id.isEmpty()) out.add(id);
            }
        } catch (Throwable ignored) {
        } finally {
            if (connection != null) connection.disconnect();
        }
        return out;
    }

    /** 一句话吐槽，给固定场景用。 */
    public static String quip(Context context, String app, String sender, String body) {
        StringBuilder text = new StringBuilder();
        text.append("App：").append(app).append("；");
        if (sender != null && !sender.isEmpty()) text.append("发件人：").append(sender).append("；");
        text.append("内容：").append(cut(body, 120)).append("；");
        text.append("现在时间：").append(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)).append(" 点。");
        return ask(context, QUIP, text.toString(), 60);
    }

    /** 统一的一次模型调用：成功返回一句话，失败返回 null。 */
    private static String ask(Context context, String system, String user, int maxTokens) {
        if (Prefs.useAccount(context)) {
            String token = Prefs.token(context);
            if (token == null || token.isEmpty()) return null;
        }
        HttpURLConnection connection = null;
        try {
            JSONObject payload = new JSONObject();
            payload.put("model", Prefs.model(context));
            payload.put("temperature", 1.15);
            payload.put("max_tokens", maxTokens);
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject().put("role", "system").put("content", system));
            messages.put(new JSONObject().put("role", "user").put("content", user));
            payload.put("messages", messages);

            connection = (HttpURLConnection) new URL(endpoint(context)).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("Content-Type", "application/json");
            authorize(connection, context);
            connection.setDoOutput(true);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(payload.toString().getBytes(StandardCharsets.UTF_8));
            }
            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
            StringBuilder text = new StringBuilder();
            if (stream != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) text.append(line);
                }
            }
            if (code != 200) {
                Log.w(TAG, "llm http " + code + " " + text);
                return null;
            }
            JSONObject json = new JSONObject(text.toString());
            String content = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim();
            return content.isEmpty() ? null : content;
        } catch (Throwable error) {
            Log.w(TAG, "llm failed", error);
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String blank(String text, String fallback) {
        return text == null || text.trim().isEmpty() ? fallback : text.trim();
    }

    private static String cut(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }

    private Llm() { }
}
