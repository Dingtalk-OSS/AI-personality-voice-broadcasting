package com.dsh.herald.release;

import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

/** 把一句话发到对应的会话（借通知里的直接回复动作）。 */
public final class VoiceReply {
    private static final String TAG = "Herald";

    public static boolean send(Context context, String key, String text) {
        return send(context, key, text, false);
    }

    public static boolean send(final Context context, String key, String text, boolean format) {
        ReplyStore.Target target = key == null ? null : ReplyStore.get(key);
        if (target == null) target = ReplyStore.latest();
        if (target == null) {
            Toast.makeText(context, "这条通知已经失效，去应用里回吧", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (text == null || text.trim().isEmpty()) return false;
        if (format) text = Llm.formatReply(context, text.trim());
        try {
            Intent fill = new Intent();
            Bundle payload = new Bundle();
            for (RemoteInput input : target.inputs) payload.putCharSequence(input.getResultKey(), text.trim());
            RemoteInput.addResultsToIntent(target.inputs, fill, payload);
            PendingIntent intent = target.intent;
            intent.send(context, 0, fill);
            Toast.makeText(context, "已回复 " + target.who + "：" + text.trim(), Toast.LENGTH_LONG).show();
            return true;
        } catch (Throwable error) {
            Log.w(TAG, "voice reply failed", error);
            Toast.makeText(context, "没发出去，去应用里回吧", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    private VoiceReply() { }
}
