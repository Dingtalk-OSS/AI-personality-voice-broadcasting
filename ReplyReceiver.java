package com.dsh.herald.release;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

/** 收到通知栏里的回复文字，转给对应的 App。 */
public class ReplyReceiver extends BroadcastReceiver {
    private static final String TAG = "Herald";

    @Override
    public void onReceive(Context context, Intent intent) {
        String key = intent.getStringExtra("key");
        String preset = intent.getStringExtra("preset");
        Bundle results = RemoteInput.getResultsFromIntent(intent);
        String text = preset == null ? "" : preset;
        if (results != null) {
            for (String resultKey : results.keySet()) {
                CharSequence value = results.getCharSequence(resultKey);
                if (value != null && value.length() > 0) text = value.toString();
            }
        }
        text = text.trim();
        ReplyStore.Target target = key == null ? null : ReplyStore.get(key);
        if (target == null) target = ReplyStore.latest();
        if (target == null) {
            Toast.makeText(context, "这条通知已经失效，去应用里回吧", Toast.LENGTH_SHORT).show();
            return;
        }
        if (text.isEmpty()) return;
        try {
            Intent fill = new Intent();
            Bundle payload = new Bundle();
            for (RemoteInput input : target.inputs) payload.putCharSequence(input.getResultKey(), text);
            RemoteInput.addResultsToIntent(target.inputs, fill, payload);
            target.intent.send(context, 0, fill);
            Toast.makeText(context, "已回复 " + target.who, Toast.LENGTH_SHORT).show();
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.cancel(target.key.hashCode());
        } catch (Throwable error) {
            Log.w(TAG, "reply failed", error);
            Toast.makeText(context, "回复没发出去，去应用里回吧", Toast.LENGTH_SHORT).show();
        }
    }
}
