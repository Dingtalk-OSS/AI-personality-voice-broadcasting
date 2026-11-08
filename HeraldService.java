package com.dsh.herald.release;

import android.app.Notification;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.app.RemoteInput;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 监听所有通知：攒一小会儿再念，内容交给模型边念边吐槽。 */
public class HeraldService extends NotificationListenerService {
    private static final String TAG = "Herald";
    private static final long BATCH_DELAY_MS = 2500;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<String[]> pending = new ArrayList<>();
    private Speaker speaker;
    private Runnable flushTask;
    private final Map<String, List<Long>> recentBySender = new HashMap<>();
    private String lastBody = "";
    private long lastAt = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        speaker = new Speaker(this);
        speaker.setIdle(() -> {
            if (BuildInfo.RELEASE || !Prefs.autoReply(this)) return;
            ReplyStore.Target target = ReplyStore.latest();
            if (target == null) return;
            if (System.currentTimeMillis() - target.at > 60000L) return;
            if (ReplyOverlay.wantsVoice()) AutoListener.listen(getApplicationContext(), target.key);
            ReplyOverlay.hide();
        });
    }

    @Override
    public void onDestroy() {
        if (flushTask != null) handler.removeCallbacks(flushTask);
        if (speaker != null) speaker.shutdown();
        super.onDestroy();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try { handle(sbn); } catch (Throwable error) { Log.w(TAG, "handle failed", error); }
    }

    private void handle(StatusBarNotification sbn) {
        if (sbn == null) return;
        String pkg = sbn.getPackageName();
        if (pkg == null || pkg.equals(getPackageName())) return;
        if (sbn.isOngoing()) return;
        Notification notification = sbn.getNotification();
        if (notification == null) return;
        Bundle extras = notification.extras;
        if (extras == null) return;

        CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        if (text == null || text.length() == 0) text = extras.getCharSequence(Notification.EXTRA_TEXT);
        CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        String body = text != null ? text.toString().trim() : "";
        if (body.isEmpty() && lines != null && lines.length > 0) {
            StringBuilder joined = new StringBuilder();
            for (CharSequence line : lines) {
                if (line == null) continue;
                if (joined.length() > 0) joined.append("，");
                joined.append(line.toString().trim());
            }
            body = joined.toString();
        }
        body = cleanBody(body);
        if (body.isEmpty()) return;
        if (!Prefs.enabled(this)) return;

        long now = System.currentTimeMillis();
        if (body.equals(lastBody) && now - lastAt < 20000) return;
        lastBody = body;
        lastAt = now;

        captureReply(pkg, title, notification);

        String app = appLabel(pkg);
        String who = title != null ? title.toString().trim() : "";
        Prefs.noteReceived(this, app + " | " + who + " | " + trim(body, 60));
        Prefs.learnApp(this, pkg, app);
        if (!who.isEmpty()) Memory.record(this, who, body);
        if (!who.isEmpty()) {
            List<Long> times = recentBySender.get(who);
            if (times == null) { times = new java.util.ArrayList<>(); recentBySender.put(who, times); }
            times.add(now);
            while (!times.isEmpty() && now - times.get(0) > 600000L) times.remove(0);
        }
        enqueue(app, who, body, pkg);
    }

    /** 记住这条通知里的「直接回复」动作，播报完就能回。 */
    private void captureReply(String pkg, CharSequence title, Notification notification) {
        try {
            if (notification.actions == null) return;
            for (Notification.Action action : notification.actions) {
                RemoteInput[] inputs = action.getRemoteInputs();
                if (inputs == null || inputs.length == 0) continue;
                String who = title == null ? "" : title.toString().trim();
                ReplyStore.put(pkg + "|" + who, appLabel(pkg), who, action.actionIntent, inputs);
                return;
            }
        } catch (Throwable ignored) { }
    }

    /** 去掉「[3条]」这类计数和「[表情]」占位，只留真正的内容。 */
    static String cleanBody(String text) {
        String body = String.valueOf(text);
        body = body.replaceAll("^\\s*\\[\\s*\\d+\\s*条\\s*\\]", "");
        body = body.replaceAll("^\\s*\\(\\s*\\d+\\s*条\\s*\\)", "");
        body = body.replaceAll("\\[(表情|动画表情|图片|语音|视频|文件|链接|红包|位置|名片|音乐)\\]", "");
        body = body.replaceAll("\\s+", " ").trim();
        return body;
    }

    private void enqueue(String app, String who, String body, String pkg) {
        pending.add(new String[]{app, who, body, pkg});
        if (pending.size() > 8) pending.remove(0);
        if (flushTask != null) handler.removeCallbacks(flushTask);
        flushTask = this::flushQueue;
        handler.postDelayed(flushTask, BATCH_DELAY_MS);
    }

    private void flushQueue() {
        List<String[]> items = new ArrayList<>(pending);
        pending.clear();
        if (items.isEmpty()) return;
        if (inQuietHours()) {
            Log.i(TAG, "quiet hours, skipped " + items.size());
            return;
        }
        final Context context = getApplicationContext();
        if (Prefs.smart(this)) {
            final Map<String, Integer> attention = new HashMap<>();
            for (String[] item : items) {
                if (item.length > 3) attention.put(item[3], Prefs.attentionFor(this, item[3], item[0]));
            }
            final Map<String, Integer> repeats = new HashMap<>();
            for (String[] item : items) {
                if (item.length > 1 && item[1] != null) {
                    List<Long> times = recentBySender.get(item[1]);
                    if (times != null) repeats.put(item[1], times.size());
                }
            }
            final Map<String, String> memories = new HashMap<>();
            for (String[] item : items) {
                if (item[1] != null && !item[1].isEmpty() && !memories.containsKey(item[1])) {
                    memories.put(item[1], Memory.recall(this, item[1]));
                }
            }
            new Thread(() -> {
                String narration = Llm.narrate(context, items, memories, attention, repeats);
                ReplyStore.Target target = null;
                for (String[] item : items) {
                    if (item.length < 4) continue;
                    target = ReplyStore.get(item[3] + "|" + item[1]);
                    if (target != null) break;
                }
                if (target != null && !BuildInfo.RELEASE && Prefs.promptOverlay(HeraldService.this) && ReplyOverlay.canShow(HeraldService.this)) {
                    ReplyOverlay.show(getApplicationContext(), target.app + " · " + target.who,
                            narration != null && !narration.isEmpty() ? narration : "来了新消息，要语音回复吗？");
                }
                if (narration != null && !narration.isEmpty()) {
                    speaker.speak(narration);
                    Prefs.recordNarration(context, narration);
                } else {
                    speakRaw(items);
                }
                updateMemories(context, items);
            }, "herald-llm").start();
        } else {
            speakRaw(items);
        }
        postReplyCards(items);
    }

    /** 播报完后给能回的会话发一张「快速回复」通知。 */
    private void postReplyCards(List<String[]> items) {
        for (String[] item : items) {
            if (item.length < 4) continue;
            ReplyStore.Target target = ReplyStore.get(item[3] + "|" + item[1]);
            if (target != null) ReplyNotifier.post(this, target);
        }
    }

    /** 顺手更新每个人的记忆卡：同一个人最少隔 10 分钟才更新一次，一批最多两张。 */
    private void updateMemories(Context context, List<String[]> items) {
        Set<String> senders = new LinkedHashSet<>();
        for (String[] item : items) {
            if (item[1] != null && !item[1].isEmpty()) senders.add(item[1]);
        }
        int updated = 0;
        for (String who : senders) {
            if (updated >= 2) break;
            if (Memory.updateAgeMinutes(context, who) < 10) continue;
            String recent = Memory.recent(context, who);
            if (recent.isEmpty()) continue;
            String card = Llm.remember(context, who, recent, Memory.recall(context, who));
            if (card != null) {
                Memory.setSummary(context, who, card);
                updated++;
            }
        }
    }

    /** 模型不可用时退回：逐条念 + 一句存货吐槽。 */
    private void speakRaw(List<String[]> items) {
        for (String[] item : items) {
            StringBuilder say = new StringBuilder();
            say.append("主人，").append(item[0]).append("来消息了。");
            if (item[1] != null && !item[1].isEmpty()) say.append(item[1]).append("说：");
            say.append(trim(item[2], 120)).append("。");
            speaker.speak(say.toString());
        }
        if (Prefs.quip(this)) speaker.speak(quip());
        Prefs.recordNarration(this, "（兜底）" + items.size() + " 条通知");
    }

    private boolean inQuietHours() {
        if (!Prefs.quietEnabled(this)) return false;
        int start = Prefs.quietStart(this);
        int end = Prefs.quietEnd(this);
        if (start == end) return false;
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return start < end ? (hour >= start && hour < end) : (hour >= start || hour < end);
    }

    private static String trim(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "……";
    }

    private String appLabel(String pkg) {
        try {
            return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0)).toString();
        } catch (Throwable ignored) {
            return pkg;
        }
    }

    /** 兜底用的固定吐槽。 */
    private static String quip() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        boolean late = hour >= 23 || hour < 6;
        String[] pool = late
                ? new String[]{"这都几点了还发，真不把自己当外人。", "哟，夜猫子又上线了，是不睡觉了吗。", "半夜发消息，他倒是挺有精神。"}
                : new String[]{"哟，他这么闲的吗。", "知道了知道了，别催。", "这位挺惦记你啊。", "行吧，我先替你记着了。", "又来活了，你看着办。"};
        return pool[(int) (Math.random() * pool.length)];
    }
}
