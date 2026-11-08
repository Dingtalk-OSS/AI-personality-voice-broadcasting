package com.dsh.herald.release;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;

/** 播报完之后，发一条带「直接回复」的通知，顺手就能回。 */
public final class ReplyNotifier {
    private static final String CHANNEL = "herald-reply";
    private static final String[] PRESETS = {"好", "收到", "在忙，晚点说"};

    public static void post(Context context, ReplyStore.Target target) {
        try {
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) return;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                if (manager.getNotificationChannel(CHANNEL) == null) {
                    NotificationChannel channel = new NotificationChannel(CHANNEL, "快速回复", NotificationManager.IMPORTANCE_DEFAULT);
                    channel.setShowBadge(false);
                    manager.createNotificationChannel(channel);
                }
            }
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) flags |= PendingIntent.FLAG_MUTABLE;

            RemoteInput remote = new RemoteInput.Builder("reply_text").setLabel("回复 " + target.who).build();
            Intent intent = new Intent(context, ReplyReceiver.class).putExtra("key", target.key);
            PendingIntent reply = PendingIntent.getBroadcast(context, target.key.hashCode(), intent, flags);
            Notification.Action action = new Notification.Action.Builder(
                    Icon.createWithResource(context, android.R.drawable.ic_menu_send), "回复", reply)
                    .addRemoteInput(remote)
                    .build();

            Notification.Builder builder;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                builder = new Notification.Builder(context, CHANNEL);
            } else {
                builder = new Notification.Builder(context);
            }
            builder.setSmallIcon(android.R.drawable.ic_dialog_email)
                    .setContentTitle("回复 " + target.who)
                    .setContentText(target.app + " · 直接在这里回")
                    .setAutoCancel(true)
                    .addAction(action);
            if (!BuildInfo.RELEASE) {
                Intent voice = new Intent(context, VoiceReplyActivity.class)
                        .putExtra("key", target.key)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                PendingIntent voicePending = PendingIntent.getActivity(context, ("v" + target.key).hashCode(), voice, flags);
                builder.addAction(new Notification.Action.Builder(
                        Icon.createWithResource(context, android.R.drawable.ic_btn_speak_now), "语音回复", voicePending).build());
            }
            for (String preset : PRESETS) {
                Intent presetIntent = new Intent(context, ReplyReceiver.class)
                        .putExtra("key", target.key)
                        .putExtra("preset", preset);
                PendingIntent pending = PendingIntent.getBroadcast(context, (target.key + preset).hashCode(), presetIntent, flags);
                builder.addAction(new Notification.Action.Builder(
                        Icon.createWithResource(context, android.R.drawable.ic_menu_edit), preset, pending).build());
            }
            manager.notify(target.key.hashCode(), builder.build());
        } catch (Throwable ignored) { }
    }

    private ReplyNotifier() { }
}
