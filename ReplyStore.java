package com.dsh.herald.release;

import android.app.PendingIntent;
import android.app.RemoteInput;

import java.util.LinkedHashMap;
import java.util.Map;

/** 记住最近几条通知里的「直接回复」动作，用来替主人回消息。 */
public final class ReplyStore {
    public static final class Target {
        public final String key;
        public final String app;
        public final String who;
        public final PendingIntent intent;
        public final RemoteInput[] inputs;
        public final long at;

        Target(String key, String app, String who, PendingIntent intent, RemoteInput[] inputs) {
            this.key = key;
            this.app = app;
            this.who = who;
            this.intent = intent;
            this.inputs = inputs;
            this.at = System.currentTimeMillis();
        }
    }

    private static final Map<String, Target> TARGETS = new LinkedHashMap<>();

    public static synchronized void put(String key, String app, String who, PendingIntent intent, RemoteInput[] inputs) {
        TARGETS.put(key, new Target(key, app, who, intent, inputs));
        while (TARGETS.size() > 10) {
            String first = TARGETS.keySet().iterator().next();
            TARGETS.remove(first);
        }
    }

    public static synchronized Target get(String key) {
        Target target = TARGETS.get(key);
        if (target == null) return null;
        if (System.currentTimeMillis() - target.at > 30 * 60 * 1000L) {
            TARGETS.remove(key);
            return null;
        }
        return target;
    }

    public static synchronized Target latest() {
        Target last = null;
        for (Target target : TARGETS.values()) last = target;
        return last;
    }

    private ReplyStore() { }
}
