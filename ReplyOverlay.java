package com.dsh.herald.release;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/** 播报时浮在屏幕上的小卡片：问要不要语音回复，默认打开。 */
public final class ReplyOverlay {
    private static final String TAG = "Herald";
    private static View view;
    private static WindowManager manager;
    private static boolean voiceWanted = true;
    private static boolean shown = false;

    public static boolean canShow(Context context) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                return Settings.canDrawOverlays(context);
            }
            return true;
        } catch (Throwable error) {
            return false;
        }
    }

    public static void show(final Context context, String title, String text) {
        if (!canShow(context) || shown) return;
        try {
            manager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if (manager == null) return;
            float density = context.getResources().getDisplayMetrics().density;
            int dp = (int) density;

            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xF2111827);
            bg.setCornerRadius(20 * dp);
            card.setBackground(bg);
            card.setPadding(18 * dp, 14 * dp, 18 * dp, 12 * dp);

            TextView head = new TextView(context);
            head.setText(title);
            head.setTextSize(13);
            head.setTextColor(0xFF9CA3AF);
            card.addView(head);

            TextView body = new TextView(context);
            body.setText(text == null ? "" : text);
            body.setTextSize(15);
            body.setTextColor(Color.WHITE);
            body.setPadding(0, 4 * dp, 0, 8 * dp);
            card.addView(body);

            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            voiceWanted = true;
            Switch voice = new Switch(context);
            voice.setText("语音回复");
            voice.setTextSize(14);
            voice.setTextColor(Color.WHITE);
            voice.setChecked(true);
            voice.setOnCheckedChangeListener((v, checked) -> voiceWanted = checked);
            row.addView(voice, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView ok = new TextView(context);
            ok.setText("不用");
            ok.setTextSize(14);
            ok.setTextColor(0xFFF87171);
            ok.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            ok.setPadding(12 * dp, 8 * dp, 4 * dp, 8 * dp);
            ok.setOnClickListener(v -> {
                voiceWanted = false;
                hide();
            });
            row.addView(ok);
            card.addView(row);

            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    android.graphics.PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP;
            lp.y = 40 * dp;
            lp.x = 24 * dp;
            lp.width = Math.min(context.getResources().getDisplayMetrics().widthPixels - 48 * dp, 560 * dp);
            manager.addView(card, lp);
            view = card;
            shown = true;
            new Handler(Looper.getMainLooper()).postDelayed(ReplyOverlay::hide, 12000);
        } catch (Throwable error) {
            Log.w(TAG, "overlay failed", error);
        }
    }

    public static boolean wantsVoice() { return voiceWanted; }

    public static void hide() {
        shown = false;
        if (view != null && manager != null) {
            try { manager.removeView(view); } catch (Throwable ignored) { }
        }
        view = null;
        manager = null;
    }

    private ReplyOverlay() { }
}
