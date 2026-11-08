package com.dsh.herald.release;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** 每个联系人一份小记忆：最近说过的话 + 一张 40 字的印象卡。全存在 App 私有目录。 */
public final class Memory {
    private static final String FILE = "memory.json";
    private static final int KEEP_MESSAGES = 300;
    private static final int MAX_PEOPLE = 60;

    private static File file(Context context) { return new File(context.getFilesDir(), FILE); }

    private static synchronized JSONObject load(Context context) {
        File target = file(context);
        if (!target.exists()) return new JSONObject();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(target), StandardCharsets.UTF_8))) {
            StringBuilder text = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) text.append(line);
            return new JSONObject(text.toString());
        } catch (Throwable ignored) {
            return new JSONObject();
        }
    }

    private static synchronized void save(Context context, JSONObject data) {
        try (FileOutputStream out = new FileOutputStream(file(context))) {
            out.write(data.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Throwable ignored) { }
    }

    private static JSONObject person(JSONObject data, String who) {
        JSONObject person = data.optJSONObject(who);
        if (person == null) {
            person = new JSONObject();
            try { data.put(who, person); } catch (Throwable ignored) { }
        }
        return person;
    }

    /** 记一条新消息：保留最近几条，累计次数。 */
    public static synchronized void record(Context context, String who, String body) {
        if (who == null || who.isEmpty()) return;
        JSONObject data = load(context);
        JSONObject person = person(data, who);
        try {
            JSONArray messages = person.optJSONArray("messages");
            if (messages == null) messages = new JSONArray();
            messages.put(trim(body, 120));
            while (messages.length() > KEEP_MESSAGES) messages.remove(0);
            person.put("messages", messages);
            person.put("count", person.optInt("count", 0) + 1);
            person.put("lastAt", System.currentTimeMillis());
            if (data.length() > MAX_PEOPLE) prune(data);
            save(context, data);
        } catch (Throwable ignored) { }
    }

    /** 读这个人的印象卡，没有就空串。 */
    public static synchronized String recall(Context context, String who) {
        if (who == null || who.isEmpty()) return "";
        JSONObject person = load(context).optJSONObject(who);
        return person == null ? "" : person.optString("summary", "");
    }

    /** 把最近几条消息拼成一段，交给模型更新印象卡。 */
    public static synchronized String recent(Context context, String who) {
        JSONObject person = load(context).optJSONObject(who);
        if (person == null) return "";
        JSONArray messages = person.optJSONArray("messages");
        if (messages == null) return "";
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < messages.length(); i++) {
            if (text.length() > 0) text.append(" / ");
            text.append(messages.optString(i, ""));
        }
        return text.toString();
    }

    public static synchronized void setSummary(Context context, String who, String summary) {
        if (who == null || who.isEmpty() || summary == null) return;
        JSONObject data = load(context);
        JSONObject person = person(data, who);
        try {
            person.put("summary", trim(summary, 240));
            person.put("updatedAt", System.currentTimeMillis());
            save(context, data);
        } catch (Throwable ignored) { }
    }

    public static synchronized int updateAgeMinutes(Context context, String who) {
        JSONObject person = load(context).optJSONObject(who);
        if (person == null) return Integer.MAX_VALUE;
        long last = person.optLong("updatedAt", 0);
        if (last == 0) return Integer.MAX_VALUE;
        return (int) ((System.currentTimeMillis() - last) / 60000L);
    }

    /** 列出所有人的名字、印象卡和消息数，按最近互动排序。 */
    public static synchronized java.util.List<String[]> list(Context context) {
        JSONObject data = load(context);
        java.util.List<String[]> rows = new java.util.ArrayList<>();
        java.util.List<String> keys = new java.util.ArrayList<>();
        java.util.Iterator<String> it = data.keys();
        while (it.hasNext()) keys.add(it.next());
        keys.sort((a, b) -> Long.compare(data.optJSONObject(b) == null ? 0 : data.optJSONObject(b).optLong("lastAt", 0),
                data.optJSONObject(a) == null ? 0 : data.optJSONObject(a).optLong("lastAt", 0)));
        for (String key : keys) {
            JSONObject person = data.optJSONObject(key);
            if (person == null) continue;
            rows.add(new String[]{key, person.optString("summary", "（还没形成印象）"), String.valueOf(person.optInt("count", 0))});
        }
        return rows;
    }

    /** 手动添加或修改一个人的记忆，不影响已有消息记录。 */
    public static synchronized void put(Context context, String who, String summary) {
        if (who == null || who.trim().isEmpty()) return;
        JSONObject data = load(context);
        JSONObject person = person(data, who.trim());
        try {
            if (summary != null && !summary.trim().isEmpty()) person.put("summary", trim(summary.trim(), 240));
            if (!person.has("count")) person.put("count", 0);
            if (!person.has("lastAt")) person.put("lastAt", System.currentTimeMillis());
            save(context, data);
        } catch (Throwable ignored) { }
    }

    /** 删除一个人的记忆。 */
    public static synchronized void remove(Context context, String who) {
        if (who == null) return;
        JSONObject data = load(context);
        data.remove(who);
        save(context, data);
    }

    public static synchronized void clear(Context context) {
        File target = file(context);
        if (target.exists()) target.delete();
    }

    public static synchronized int knownPeople(Context context) {
        return load(context).length();
    }

    private static void prune(JSONObject data) {
        // 超过上限时删掉最久没互动的几个人
        java.util.List<String> keys = new java.util.ArrayList<>();
        java.util.Iterator<String> it = data.keys();
        while (it.hasNext()) keys.add(it.next());
        keys.sort((a, b) -> Long.compare(data.optJSONObject(a) == null ? 0 : data.optJSONObject(a).optLong("lastAt", 0),
                data.optJSONObject(b) == null ? 0 : data.optJSONObject(b).optLong("lastAt", 0)));
        while (keys.size() > MAX_PEOPLE) data.remove(keys.remove(0));
    }

    private static String trim(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }

    private Memory() { }
}
