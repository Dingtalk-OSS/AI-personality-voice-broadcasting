package com.dsh.herald.release;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

/** 常驻通知保住进程：小米之类的系统更容易放过带前台服务的 App。 */
public class KeepAlive extends Service {
    private static final String CHANNEL = "herald-alive";
    private static final int ID = 41;

    public static void start(Context context) {
        try { context.startForegroundService(new Intent(context, KeepAlive.class)); }
        catch (Throwable error) { try { context.startService(new Intent(context, KeepAlive.class)); } catch (Throwable ignored) { } }
    }

    public static void stop(Context context) {
        try { context.stopService(new Intent(context, KeepAlive.class)); } catch (Throwable ignored) { }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try { startForeground(ID, build()); } catch (Throwable ignored) { }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private Notification build() {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null && manager.getNotificationChannel(CHANNEL) == null) {
                NotificationChannel channel = new NotificationChannel(CHANNEL, "播报运行中", NotificationManager.IMPORTANCE_MIN);
                channel.setShowBadge(false);
                manager.createNotificationChannel(channel);
            }
            builder = new Notification.Builder(this, CHANNEL);
        } else {
            builder = new Notification.Builder(this);
        }
        return builder
                .setContentTitle("消息播报运行中")
                .setContentText("正在监听通知，来消息就念给你听")
                .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
                .setOngoing(true)
                .build();
    }
}
