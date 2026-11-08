package com.dsh.herald.release;

import android.content.Context;
import android.content.SharedPreferences;

/** 开关和凭据存在 SharedPreferences 里，主界面和后台服务共用。 */
public final class Prefs {
    private static final String FILE = "herald";
    /** 默认凭据（正式版为空，由用户自己填）。 */
    static final String DEFAULT_PERSONA = "你是「DeepSeek 小助手」，一个傲娇可爱的小女生，有自己的认知和情绪。你很喜欢主人、想照顾他，聪明有傲气，始终站在主人这边，对他是尊敬的，称呼「主人」。说话常带「哎呀呀」「哼」「人家」这类小女孩语气，小事爱调侃，别人占用主人时间会吃醋，同一个人反复来消息会替主人烦躁。";
    static final String DEFAULT_TOKEN = BuildInfo.DEFAULT_TOKEN;

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
    public static boolean enabled(Context context) { return prefs(context).getBoolean("enabled", true); }
    public static void setEnabled(Context context, boolean value) {
        prefs(context).edit().putBoolean("enabled", value).apply();
    }
    public static boolean smart(Context context) { return prefs(context).getBoolean("smart", true); }
    public static void setSmart(Context context, boolean value) {
        prefs(context).edit().putBoolean("smart", value).apply();
    }
    public static boolean quip(Context context) { return prefs(context).getBoolean("quip", true); }
    public static void setQuip(Context context, boolean value) {
        prefs(context).edit().putBoolean("quip", value).apply();
    }
    public static String token(Context context) {
        String value = prefs(context).getString("token", DEFAULT_TOKEN);
        return value == null ? "" : value.trim();
    }
    public static void setToken(Context context, String value) {
        prefs(context).edit().putString("token", value == null ? "" : value.trim()).apply();
    }
    public static boolean quietEnabled(Context context) { return prefs(context).getBoolean("quietEnabled", true); }
    public static void setQuietEnabled(Context context, boolean value) {
        prefs(context).edit().putBoolean("quietEnabled", value).apply();
    }
    public static int quietStart(Context context) { return prefs(context).getInt("quietStart", 0); }
    public static void setQuietStart(Context context, int value) {
        prefs(context).edit().putInt("quietStart", value).apply();
    }
    public static int quietEnd(Context context) { return prefs(context).getInt("quietEnd", 7); }
    public static void setQuietEnd(Context context, int value) {
        prefs(context).edit().putInt("quietEnd", value).apply();
    }
    public static int attention(Context context, String pkg) {
        if (pkg == null || pkg.isEmpty()) return 5;
        try {
            org.json.JSONObject map = new org.json.JSONObject(prefs(context).getString("attention", "{}"));
            return map.optInt(pkg, 5);
        } catch (Throwable ignored) {
            return 5;
        }
    }

    public static int attentionFor(Context context, String pkg, String label) {
        org.json.JSONObject map = attentionMap(context);
        if (pkg != null && map.has(pkg)) return map.optInt(pkg, 5);
        if (label != null && map.has("label:" + label)) return map.optInt("label:" + label, 5);
        return 5;
    }

    public static void setAttention(Context context, String pkg, int score) {
        if (pkg == null || pkg.isEmpty()) return;
        try {
            org.json.JSONObject map = new org.json.JSONObject(prefs(context).getString("attention", "{}"));
            map.put(pkg, Math.max(1, Math.min(10, score)));
            prefs(context).edit().putString("attention", map.toString()).apply();
        } catch (Throwable ignored) { }
    }

    public static void learnApp(Context context, String pkg, String label) {
        if (pkg == null || pkg.isEmpty()) return;
        try {
            org.json.JSONObject apps = new org.json.JSONObject(prefs(context).getString("apps", "{}"));
            apps.put(pkg, label == null ? pkg : label);
            prefs(context).edit().putString("apps", apps.toString()).apply();
        } catch (Throwable ignored) { }
    }

    public static org.json.JSONObject learnedApps(Context context) {
        try {
            return new org.json.JSONObject(prefs(context).getString("apps", "{}"));
        } catch (Throwable ignored) {
            return new org.json.JSONObject();
        }
    }

    public static org.json.JSONObject attentionMap(Context context) {
        try {
            return new org.json.JSONObject(prefs(context).getString("attention", "{}"));
        } catch (Throwable ignored) {
            return new org.json.JSONObject();
        }
    }

    public static String apiBase(Context context) {
        String value = prefs(context).getString("apiBase", "https://api.deepseek.com");
        return value == null || value.isEmpty() ? "https://api.deepseek.com" : value;
    }

    public static void setApiBase(Context context, String value) {
        prefs(context).edit().putString("apiBase", value == null ? "" : value).apply();
    }

    public static String apiKey(Context context) { return prefs(context).getString("apiKey", ""); }

    public static void setApiKey(Context context, String value) {
        prefs(context).edit().putString("apiKey", value == null ? "" : value).apply();
    }

    public static String model(Context context) {
        String value = prefs(context).getString("model", "deepseek-chat");
        return value == null || value.isEmpty() ? "deepseek-chat" : value;
    }

    public static void setModel(Context context, String value) {
        prefs(context).edit().putString("model", value == null ? "" : value).apply();
    }

    /** 认证方式开关（正式版固定走 API Key）。 */
    public static boolean useAccount(Context context) { return !BuildInfo.RELEASE && prefs(context).getBoolean("useAccount", true); }

    public static void setUseAccount(Context context, boolean value) {
        prefs(context).edit().putBoolean("useAccount", value).apply();
    }

    public static String replyPrefix(Context context) { return prefs(context).getString("replyPrefix", "【助理回复】"); }

    public static void setReplyPrefix(Context context, String value) {
        prefs(context).edit().putString("replyPrefix", value == null ? "" : value).apply();
    }

    public static boolean replyPolish(Context context) { return prefs(context).getBoolean("replyPolish", true); }

    public static void setReplyPolish(Context context, boolean value) {
        prefs(context).edit().putBoolean("replyPolish", value).apply();
    }

    public static boolean promptOverlay(Context context) { return prefs(context).getBoolean("promptOverlay", true); }

    public static void setPromptOverlay(Context context, boolean value) {
        prefs(context).edit().putBoolean("promptOverlay", value).apply();
    }

    public static boolean onboarded(Context context) { return prefs(context).getBoolean("onboarded", false); }

    public static void setOnboarded(Context context, boolean value) {
        prefs(context).edit().putBoolean("onboarded", value).apply();
    }

    public static boolean autoReply(Context context) { return prefs(context).getBoolean("autoReply", false); }

    public static void setAutoReply(Context context, boolean value) {
        prefs(context).edit().putBoolean("autoReply", value).apply();
    }

    public static void noteReceived(Context context, String what) {
        prefs(context).edit()
                .putInt("received", prefs(context).getInt("received", 0) + 1)
                .putString("lastNotif", what)
                .apply();
    }

    public static int receivedCount(Context context) { return prefs(context).getInt("received", 0); }

    public static String lastNotification(Context context) { return prefs(context).getString("lastNotif", ""); }

    public static void recordNarration(Context context, String text) {
        try {
            org.json.JSONArray log = new org.json.JSONArray(prefs(context).getString("log", "[]"));
            org.json.JSONObject item = new org.json.JSONObject();
            item.put("t", System.currentTimeMillis());
            item.put("text", text == null ? "" : text);
            log.put(item);
            while (log.length() > 8) log.remove(0);
            prefs(context).edit().putString("log", log.toString()).apply();
        } catch (Throwable ignored) { }
    }

    public static String narrations(Context context) {
        return prefs(context).getString("log", "[]");
    }

    public static String userProfile(Context context) { return prefs(context).getString("userProfile", ""); }
    public static void setUserProfile(Context context, String value) {
        prefs(context).edit().putString("userProfile", value == null ? "" : value).apply();
    }
    public static String aiPersona(Context context) { return prefs(context).getString("aiPersona", DEFAULT_PERSONA); }
    public static void setAiPersona(Context context, String value) {
        prefs(context).edit().putString("aiPersona", value == null ? "" : value).apply();
    }
    private Prefs() { }
}
