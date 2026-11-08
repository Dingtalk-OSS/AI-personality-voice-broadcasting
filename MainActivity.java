package com.dsh.herald.release;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 主界面：底部四个一级页签（首页 / 人设 / 记忆 / 设置）。 */
public class MainActivity extends Activity {
    private static final int INK = 0xFF111827;
    private static final int MUTED = 0xFF6B7280;
    private static final int ACCENT = 0xFF4F46E5;
    private static final int SURFACE = 0xFFF5F6FA;

    private static final String[] TAB_NAMES = {"首页", "关注度", "人设", "记忆", "设置"};
    private static final String[] TAB_ICONS = {"⌂", "◔", "◉", "◈", "⚙"};

    private final View[] pages = new View[5];
    private final TextView[] tabLabels = new TextView[5];
    private final TextView[] tabIcons = new TextView[5];
    private FrameLayout pageHost;
    private int tab = 0;

    private TextView heroState;
    private TextView heroSub;
    private TextView heroToggle;
    private TextView statusChip;
    private Speaker preview;

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density); }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radiusDp));
        return shape;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preview = new Speaker(this);
        try {
            if (android.os.Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 7);
            }
        } catch (Throwable ignored) { }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(SURFACE);

        pageHost = new FrameLayout(this);
        FrameLayout.LayoutParams hostLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        hostLp.bottomMargin = dp(64);
        root.addView(pageHost, hostLp);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(Color.WHITE);
        bar.setElevation(dp(10));
        FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64));
        barLp.gravity = Gravity.BOTTOM;
        root.addView(bar, barLp);

        for (int i = 0; i < TAB_NAMES.length; i++) {
            final int index = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setOnClickListener(v -> selectTab(index));
            LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            bar.addView(item, itemLp);

            TextView icon = new TextView(this);
            icon.setText(TAB_ICONS[i]);
            icon.setTextSize(18);
            icon.setGravity(Gravity.CENTER);
            icon.setTextColor(MUTED);
            item.addView(icon);
            tabIcons[i] = icon;

            TextView label = new TextView(this);
            label.setText(TAB_NAMES[i]);
            label.setTextSize(11);
            label.setGravity(Gravity.CENTER);
            label.setTextColor(MUTED);
            item.addView(label);
            tabLabels[i] = label;
        }

        if (Prefs.enabled(this)) KeepAlive.start(this);
        setContentView(root);
        selectTab(0);
    }

    private void selectTab(int index) {
        tab = index;
        if (index == 0) pages[0] = buildPage(0);
        if (pages[index] == null) pages[index] = buildPage(index);
        pageHost.removeAllViews();
        pageHost.addView(pages[index], new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        for (int i = 0; i < TAB_NAMES.length; i++) {
            int color = i == index ? ACCENT : MUTED;
            tabIcons[i].setTextColor(color);
            tabLabels[i].setTextColor(color);
            tabLabels[i].setTypeface(Typeface.create("sans-serif", i == index ? Typeface.BOLD : Typeface.NORMAL));
        }
        if (index == 0) refreshHero();
    }

    private View buildPage(int index) {
        switch (index) {
            case 0: return page(homeContent());
            case 1: return page(attentionContent());
            case 2: return page(personaContent());
            case 3: return page(memoryContent());
            default: return page(settingsContent());
        }
    }

    private ScrollView page(LinearLayout content) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        FrameLayout frame = new FrameLayout(this);
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int width = Math.min(metrics.widthPixels - dp(24), dp(760));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        frame.addView(content, lp);
        content.setPadding(dp(4), dp(28), dp(4), dp(24));
        scroll.addView(frame, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    // ---------- 首页 ----------
    private LinearLayout homeContent() {
        LinearLayout root = column();

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable gradient = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF4F46E5, 0xFF06B6D4});
        gradient.setCornerRadius(dp(28));
        head.setBackground(gradient);
        head.setPadding(dp(22), dp(20), dp(22), dp(20));
        head.setElevation(dp(6));
        LinearLayout.LayoutParams headLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headLp.bottomMargin = dp(14);
        head.setLayoutParams(headLp);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("消息播报");
        title.setTextSize(22);
        title.setTextColor(Color.WHITE);
        title.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        titleRow.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        statusChip = new TextView(this);
        statusChip.setTextSize(11);
        statusChip.setPadding(dp(10), dp(5), dp(10), dp(5));
        titleRow.addView(statusChip);
        head.addView(titleRow);

        heroState = new TextView(this);
        heroState.setTextSize(34);
        heroState.setTextColor(Color.WHITE);
        heroState.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        heroState.setPadding(0, dp(10), 0, 0);
        head.addView(heroState);

        heroSub = new TextView(this);
        heroSub.setTextSize(13);
        heroSub.setTextColor(0xCCFFFFFF);
        heroSub.setPadding(0, dp(2), 0, dp(14));
        head.addView(heroSub);

        heroToggle = new TextView(this);
        heroToggle.setTextSize(16);
        heroToggle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        heroToggle.setGravity(Gravity.CENTER);
        heroToggle.setPadding(dp(16), dp(14), dp(16), dp(14));
        heroToggle.setBackground(round(Color.WHITE, 18));
        heroToggle.setOnClickListener(v -> {
            boolean next = !Prefs.enabled(this);
            Prefs.setEnabled(this, next);
            if (next) KeepAlive.start(this); else KeepAlive.stop(this);
            refreshHero();
        });
        head.addView(heroToggle);
        root.addView(head);

        LinearLayout quick = card();
        quick.addView(caption("最近播报"));
        quick.addView(hint("它说过的话都在这里，空的就是还没念过"));
        quick.addView(recentLog());
        TextView refresh = primary("刷新记录", 0xFFF3F4F6, INK, v -> {
            pages[0] = null;
            selectTab(0);
        });
        LinearLayout.LayoutParams refreshLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        refreshLp.topMargin = dp(8);
        quick.addView(refresh, refreshLp);

        TextView debug = new TextView(this);
        debug.setTextSize(11);
        debug.setTextColor(0xFF9CA3AF);
        debug.setText("总共收到 " + Prefs.receivedCount(this) + " 条通知\n最近一条：" + Prefs.lastNotification(this));
        debug.setPadding(0, dp(8), 0, dp(4));
        quick.addView(debug);

        TextView simulate = primary("模拟一条消息（测试模型和发音）", 0xFFEEF2FF, ACCENT, v -> {
            final String[] items = {"微信	何进进	晚上一起吃饭吗"};
            new Thread(() -> {
                java.util.List<String[]> list = new java.util.ArrayList<>();
                list.add(new String[]{"微信", "何进进", "晚上一起吃饭吗", "com.tencent.mm"});
                java.util.Map<String, String> memory = new java.util.HashMap<>();
                memory.put("何进进", Memory.recall(this, "何进进"));
                java.util.Map<String, Integer> attention = new java.util.HashMap<>();
                attention.put("com.tencent.mm", Prefs.attention(this, "com.tencent.mm"));
                String narration = Llm.narrate(this, list, memory, attention);
                final String shown = narration == null ? "模型没返回（检查设置里的 token 和网络）" : narration;
                if (narration != null) {
                    preview.speak(narration);
                    Prefs.recordNarration(this, narration);
                }

            }).start();
        });
        LinearLayout.LayoutParams simLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        simLp.topMargin = dp(8);
        quick.addView(simulate, simLp);
        TextView test = primary("试听一段", ACCENT, Color.WHITE, v -> {
            preview.speak("主人，微信来消息了。小王说：晚上一起吃个饭吧。");
            preview.speak("哎哟这何进进，大半夜问你吃饭，真不把自己当外人。");
            new Thread(() -> {
                String quip = Llm.quip(getApplicationContext(), "微信", "小王", "晚上一起吃个饭吧");
                if (quip != null) preview.speak(quip);
            }).start();
            Toast.makeText(this, "正在播放：" + preview.diagnosis(), Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams testLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        testLp.topMargin = dp(10);
        quick.addView(test, testLp);
        TextView stop = primary("停止播放", 0xFFF3F4F6, INK, v -> {
            preview.stopAll();
            Toast.makeText(this, "已经停了", Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams stopLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stopLp.topMargin = dp(8);
        quick.addView(stop, stopLp);
        root.addView(quick);

        final TextView connInfo = new TextView(this);
        LinearLayout conn = card();
        conn.addView(caption("连接状态"));
        connInfo.setTextSize(12);
        connInfo.setTextColor(MUTED);
        connInfo.setText("通知使用权：" + (isListenerEnabled() ? "已授权 ✓" : "未授权")
                + "\n模型凭据：" + (modelReady() ? "已填写 ✓" : "未填写")
                + (BuildInfo.RELEASE ? "" : "\n本机语音服务：检测中…"));
        connInfo.setPadding(0, dp(2), 0, dp(8));
        conn.addView(connInfo);
        TextView grant = primary("授权通知使用权", 0xFFEEF2FF, ACCENT,
                v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        conn.addView(grant);
        TextView check = primary("检测连接", 0xFFECFDF5, 0xFF047857, v -> {
            connInfo.setText("通知使用权：" + (isListenerEnabled() ? "已授权 ✓" : "未授权")
                    + "\n模型凭据：" + (modelReady() ? "已填写 ✓" : "未填写")
                    + (BuildInfo.RELEASE ? "" : "\n本机语音服务：检测中…"));
            new Thread(() -> {
                boolean tts = ping("http://127.0.0.1:18766/health");
                boolean asr = ping("http://127.0.0.1:18767/health");
                runOnUiThread(() -> connInfo.setText("通知使用权：" + (isListenerEnabled() ? "已授权 ✓" : "未授权")
                        + "\n模型凭据：" + (modelReady() ? "已填写 ✓" : "未填写")
                        + (BuildInfo.RELEASE ? "" : "\n本机语音服务：" + (tts ? "已连接（高质量发音）" : "未连接（用在线语音）")
                        + "\n语音识别：" + (asr ? "已连接" : "未连接（语音回复不可用）"))));
            }).start();
        });
        LinearLayout.LayoutParams checkLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        checkLp.topMargin = dp(8);
        conn.addView(check, checkLp);
        root.addView(conn);

        if (!Prefs.onboarded(this)) {
            LinearLayout guide = card();
            guide.addView(caption("第一次使用"));
            guide.addView(hint("三步就能用起来，之后可以在各个页签里慢慢调"));
            guide.addView(hint("1. 点上面「授权通知使用权」\n2. 在设置里确认模型凭据\n3. 给个悬浮窗权限，语音回复才能弹卡片"));
            TextView done = primary("开始使用", ACCENT, Color.WHITE, v -> {
                Prefs.setOnboarded(this, true);
                pages[0] = null;
                selectTab(0);
            });
            guide.addView(done);
            root.addView(guide);
        }
        return root;
    }

    private View recentLog() {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        JSONArray log;
        try { log = new JSONArray(Prefs.narrations(this)); } catch (Throwable error) { log = new JSONArray(); }
        if (log.length() == 0) {
            TextView empty = new TextView(this);
            empty.setText("还没有记录");
            empty.setTextSize(13);
            empty.setTextColor(0xFF9CA3AF);
            empty.setPadding(0, dp(6), 0, dp(6));
            list.addView(empty);
            return list;
        }
        SimpleDateFormat format = new SimpleDateFormat("HH:mm", Locale.CHINA);
        for (int i = log.length() - 1; i >= 0; i--) {
            JSONObject item = log.optJSONObject(i);
            if (item == null) continue;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, dp(7), 0, dp(7));
            TextView time = new TextView(this);
            time.setText(format.format(new Date(item.optLong("t", 0))));
            time.setTextSize(11);
            time.setTextColor(0xFF9CA3AF);
            row.addView(time);
            TextView text = new TextView(this);
            text.setText(item.optString("text", ""));
            text.setTextSize(13);
            text.setTextColor(INK);
            row.addView(text);
            list.addView(row);
        }
        return list;
    }

    private void refreshHero() {
        if (heroState == null) return;
        boolean enabled = Prefs.enabled(this);
        boolean granted = isListenerEnabled();
        heroState.setText(enabled ? "运行中" : "已暂停");
        if (!granted) heroSub.setText("还差一步：给「通知使用权」授权，不然收不到通知");
        else if (enabled) heroSub.setText("正在监听通知，来消息就念给你听");
        else heroSub.setText("暂停中，消息照收但不念");
        heroToggle.setText(enabled ? "暂停播报" : "开始播报");
        heroToggle.setTextColor(enabled ? 0xFFB91C1C : 0xFF4F46E5);
        statusChip.setText(granted ? "授权正常" : "未授权");
        statusChip.setTextColor(granted ? 0xFF166534 : 0xFF7C2D12);
        statusChip.setBackground(round(granted ? 0xE6FFFFFF : 0xFFFFEDD5, 20));
    }

    // ---------- 关注度 ----------
    private LinearLayout attentionContent() {
        LinearLayout root = column();

        LinearLayout addCard = card();
        addCard.addView(caption("添加应用"));
        addCard.addView(hint("输入应用名（如 微信）或包名（如 com.tencent.mm）；收到过通知的 App 会自动出现在下面"));
        EditText input = field("", "应用名或包名", 1);
        input.setSingleLine(true);
        input.setMinLines(1);
        addCard.addView(input);
        TextView addButton = primary("加入关注度列表", ACCENT, Color.WHITE, v -> {
            String text = input.getText().toString().trim();
            if (text.isEmpty()) {
                Toast.makeText(this, "先写个应用名", Toast.LENGTH_SHORT).show();
                return;
            }
            String[] resolved = resolveApp(text);
            Prefs.learnApp(this, resolved[1], resolved[0]);
            if (!Prefs.attentionMap(this).has(resolved[1])) Prefs.setAttention(this, resolved[1], 5);
            pages[1] = null;
            selectTab(1);
        });
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        addLp.topMargin = dp(10);
        addCard.addView(addButton, addLp);
        root.addView(addCard);

        LinearLayout card = card();
        card.addView(caption("关注度"));
        card.addView(hint("1-3 只提一句「不重要」，4-6 简报，7-10 详细念"));
        java.util.LinkedHashMap<String, String> apps = launcherApps();
        org.json.JSONObject learned = Prefs.learnedApps(this);
        java.util.Iterator<String> learnedKeys = learned.keys();
        while (learnedKeys.hasNext()) {
            String pkg = learnedKeys.next();
            if (!apps.containsKey(pkg)) {
                String label = learned.optString(pkg, pkg);
                apps.put(pkg, label.startsWith("label:") ? label.substring(6) : label);
            }
        }
        java.util.Iterator<String> attentionKeys = Prefs.attentionMap(this).keys();
        while (attentionKeys.hasNext()) {
            String pkg = attentionKeys.next();
            if (!apps.containsKey(pkg)) apps.put(pkg, pkg.startsWith("label:") ? pkg.substring(6) : pkg);
        }
        for (String pkg : apps.keySet()) {
            final String packageName = pkg;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, dp(8), 0, dp(4));

            LinearLayout titleRow = new LinearLayout(this);
            titleRow.setOrientation(LinearLayout.HORIZONTAL);
            titleRow.setGravity(Gravity.CENTER_VERTICAL);
            TextView name = new TextView(this);
            name.setText(apps.get(pkg));
            name.setTextSize(13);
            name.setTextColor(INK);
            titleRow.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            final TextView value = new TextView(this);
            value.setTextSize(12);
            value.setTextColor(ACCENT);
            value.setText(String.valueOf(Prefs.attention(this, pkg)));
            titleRow.addView(value);
            row.addView(titleRow);

            android.widget.SeekBar bar = new android.widget.SeekBar(this);
            bar.setMax(9);
            bar.setProgress(Math.max(0, Math.min(9, Prefs.attention(this, pkg) - 1)));
            bar.setPadding(dp(2), dp(4), dp(2), dp(4));
            bar.setProgressTintList(ColorStateList.valueOf(ACCENT));
            bar.setThumbTintList(ColorStateList.valueOf(ACCENT));
            bar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                    int score = progress + 1;
                    value.setText(String.valueOf(score));
                    if (fromUser) Prefs.setAttention(MainActivity.this, packageName, score);
                }
                @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) { }
                @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) { }
            });
            row.addView(bar);
            card.addView(row);
        }
        root.addView(card);
        return root;
    }

    /** 把用户输入的应用名或包名解析成 {label, package}；认不出来就按名字记，照样生效。 */
    private String[] resolveApp(String text) {
        String want = text.trim();
        String compact = want.replace(" ", "").toLowerCase(java.util.Locale.ROOT);
        try {
            android.content.pm.PackageManager manager = getPackageManager();
            if (want.contains(".")) {
                String label = want;
                try {
                    label = String.valueOf(manager.getApplicationLabel(manager.getApplicationInfo(want, 0)));
                } catch (Throwable ignored) { }
                return new String[]{label, want};
            }
            // 先精确匹配，再模糊匹配已安装应用的名字
            java.util.List<android.content.pm.ResolveInfo> launchers = new java.util.ArrayList<>();
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            launchers.addAll(manager.queryIntentActivities(intent, 0));
            String fuzzyLabel = null;
            String fuzzyPkg = null;
            for (android.content.pm.ResolveInfo info : launchers) {
                String label = String.valueOf(info.loadLabel(manager));
                String key = label.replace(" ", "").toLowerCase(java.util.Locale.ROOT);
                if (key.equals(compact)) return new String[]{label, info.activityInfo.packageName};
                if (fuzzyLabel == null && (key.contains(compact) || compact.contains(key))) {
                    fuzzyLabel = label;
                    fuzzyPkg = info.activityInfo.packageName;
                }
            }
            if (fuzzyPkg != null) return new String[]{fuzzyLabel, fuzzyPkg};
            // 再看已见过的应用
            org.json.JSONObject learned = Prefs.learnedApps(this);
            java.util.Iterator<String> keys = learned.keys();
            while (keys.hasNext()) {
                String pkg = keys.next();
                String label = learned.optString(pkg, pkg);
                String key = label.replace(" ", "").toLowerCase(java.util.Locale.ROOT);
                if (key.equals(compact) || key.contains(compact) || compact.contains(key)) return new String[]{label, pkg};
            }
        } catch (Throwable ignored) { }
        // 认不出来也照收：按名字记一份关注度，收到同名的通知时就会用上
        return new String[]{want, "label:" + want};
    }

    private java.util.LinkedHashMap<String, String> launcherApps() {
        java.util.LinkedHashMap<String, String> map = new java.util.LinkedHashMap<>();
        try {
            android.content.pm.PackageManager manager = getPackageManager();
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            java.util.List<android.content.pm.ResolveInfo> list = manager.queryIntentActivities(intent, 0);
            java.util.List<String[]> rows = new java.util.ArrayList<>();
            for (android.content.pm.ResolveInfo info : list) {
                String pkg = info.activityInfo.packageName;
                if (pkg.equals(getPackageName())) continue;
                String label = String.valueOf(info.loadLabel(manager));
                rows.add(new String[]{label, pkg});
            }
            rows.sort((a, b) -> a[0].compareTo(b[0]));
            for (String[] row : rows) map.put(row[1], row[0]);
        } catch (Throwable ignored) { }
        if (map.isEmpty()) {
            map.put("com.tencent.mm", "微信");
            map.put("com.tencent.mobileqq", "QQ");
        }
        return map;
    }

    // ---------- 人设 ----------
    private LinearLayout personaContent() {
        LinearLayout root = column();
        LinearLayout card = card();
        card.addView(caption("我是谁"));
        card.addView(hint("性格、生活、最近在忙什么，它念消息时会参考"));
        EditText userField = field(Prefs.userProfile(this), "例：25 岁做设计，最近赶项目，讨厌被打扰", 6);
        userField.addTextChangedListener(saver(value -> Prefs.setUserProfile(MainActivity.this, value)));
        card.addView(userField);
        root.addView(card);

        LinearLayout templates = card();
        templates.addView(caption("性格模板"));
        templates.addView(hint("点一个直接用，下面还能自己改"));
        for (String[] preset : Personas.ALL) {
            final String name = preset[0];
            final String persona = preset[1];
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, dp(8), 0, dp(8));
            row.setOnClickListener(v -> {
                Prefs.setAiPersona(MainActivity.this, persona);
                pages[2] = null;
                selectTab(2);
                Toast.makeText(this, "换成「" + name + "」了", Toast.LENGTH_SHORT).show();
            });
            boolean active = Prefs.aiPersona(this).equals(persona);
            LinearLayout nameRow = new LinearLayout(this);
            nameRow.setOrientation(LinearLayout.HORIZONTAL);
            nameRow.setGravity(Gravity.CENTER_VERTICAL);
            TextView title = new TextView(this);
            title.setText(name);
            title.setTextSize(14);
            title.setTextColor(active ? ACCENT : INK);
            title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            nameRow.addView(title);
            if (active) {
                TextView badge = new TextView(this);
                badge.setText("✓ 使用中");
                badge.setTextSize(10);
                badge.setTextColor(ACCENT);
                badge.setPadding(dp(8), dp(1), dp(8), dp(1));
                badge.setBackground(round(0xFFEEF2FF, 10));
                LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                badgeLp.leftMargin = dp(8);
                nameRow.addView(badge, badgeLp);
            }
            row.addView(nameRow);
            TextView sample = new TextView(this);
            sample.setText("「" + preset[2] + "」");
            sample.setTextSize(11);
            sample.setTextColor(MUTED);
            sample.setPadding(0, dp(2), 0, 0);
            row.addView(sample);
            templates.addView(row);
        }
        root.addView(templates);

        LinearLayout card2 = card();
        card2.addView(caption("它是什么性格（可自定义）"));
        card2.addView(hint("决定它说话的味道，随时改"));
        EditText personaField = field(Prefs.aiPersona(this), "例：贱兮兮、嘴欠但护主", 5);
        personaField.addTextChangedListener(saver(value -> Prefs.setAiPersona(MainActivity.this, value)));
        card2.addView(personaField);
        root.addView(card2);
        return root;
    }

    // ---------- 记忆 ----------
    private LinearLayout memoryContent() {
        LinearLayout root = column();
        LinearLayout add = card();
        add.addView(caption("手动添加"));
        add.addView(hint("自己加人或者加群，下次播报就会带上这些设定"));
        EditText nameField = field("", "名字（联系人 / 群名 / App）", 1);
        nameField.setSingleLine(true);
        nameField.setMinLines(1);
        add.addView(nameField);
        TextView gap = new TextView(this);
        gap.setPadding(0, dp(6), 0, 0);
        add.addView(gap);
        EditText memoField = field("", "印象或设定，例：大学同学，老约饭，上回放我鸽子", 3);
        add.addView(memoField);
        TextView addButton = primary("添加 / 更新", ACCENT, Color.WHITE, v -> {
            String name = nameField.getText().toString().trim();
            String memo = memoField.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, "先写个名字", Toast.LENGTH_SHORT).show();
                return;
            }
            Memory.put(this, name, memo);
            pages[3] = null;
            selectTab(3);
        });
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        addLp.topMargin = dp(10);
        add.addView(addButton, addLp);
        root.addView(add);

        LinearLayout card = card();
        card.addView(caption("记忆库"));
        List<String[]> rows = Memory.list(this);
        card.addView(hint(rows.isEmpty() ? "还没记住任何人，来消息后会自动开始记" : "已记住 " + rows.size() + " 个人，只存在本机"));
        for (String[] row : rows) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(0, dp(8), 0, dp(8));
            TextView name = new TextView(this);
            name.setText(row[0] + "  ·  " + row[2] + " 条");
            name.setTextSize(14);
            name.setTextColor(INK);
            name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            item.addView(name);
            TextView summary = new TextView(this);
            summary.setText(row[1]);
            summary.setTextSize(12);
            summary.setTextColor(MUTED);
            summary.setPadding(0, dp(2), 0, 0);
            item.addView(summary);
            final String who = row[0];
            final String memo = row[1];
            TextView edit = new TextView(this);
            edit.setText("编辑");
            edit.setTextSize(11);
            edit.setTextColor(ACCENT);
            edit.setPadding(0, dp(4), dp(12), 0);
            edit.setOnClickListener(v -> {
                nameField.setText(who);
                memoField.setText(memo);
                nameField.requestFocus();
                Toast.makeText(this, "已经填到上面的框里，改完点「添加 / 更新」", Toast.LENGTH_SHORT).show();
            });
            LinearLayout ops = new LinearLayout(this);
            ops.setOrientation(LinearLayout.HORIZONTAL);
            ops.addView(edit);
            TextView delete = new TextView(this);
            delete.setText("删除");
            delete.setTextSize(11);
            delete.setTextColor(0xFFDC2626);
            delete.setPadding(0, dp(4), 0, 0);
            delete.setOnClickListener(v -> {
                Memory.remove(MainActivity.this, who);
                pages[3] = null;
                selectTab(3);
            });
            ops.addView(delete);
            item.addView(ops);
            card.addView(item);
        }
        TextView clear = primary("清空记忆库", 0xFFFEE2E2, 0xFFDC2626, v -> {
            Memory.clear(this);
            pages[3] = null;
            selectTab(3);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        card.addView(clear, lp);
        root.addView(card);
        return root;
    }

    // ---------- 设置 ----------
    private LinearLayout settingsContent() {
        LinearLayout root = column();

        LinearLayout card = card();
        card.addView(caption("播报方式"));
        card.addView(toggle("智能点评（调用大模型现编）", Prefs.smart(this),
                (v, checked) -> Prefs.setSmart(MainActivity.this, checked)));
        card.addView(toggle("模型不可用时用固定吐槽", Prefs.quip(this),
                (v, checked) -> Prefs.setQuip(MainActivity.this, checked)));
        card.addView(toggle("播报时弹出卡片问我要不要语音回复", Prefs.promptOverlay(this),
                (v, checked) -> Prefs.setPromptOverlay(MainActivity.this, checked)));
        card.addView(toggle("播报完自动开麦听我回（不弹卡片时）", Prefs.autoReply(this),
                (v, checked) -> Prefs.setAutoReply(MainActivity.this, checked)));
        card.addView(toggle("回复时让模型润色成助理口气", Prefs.replyPolish(this),
                (v, checked) -> Prefs.setReplyPolish(MainActivity.this, checked)));
        EditText prefix = field(Prefs.replyPrefix(this), "回复前缀，例：【助理回复】", 1);
        prefix.setSingleLine(true);
        prefix.setMinLines(1);
        prefix.addTextChangedListener(saver(value -> Prefs.setReplyPrefix(MainActivity.this, value)));
        card.addView(prefix);
        TextView overlayPermission = primary("打开悬浮窗权限（弹卡片要用）", 0xFFEEF2FF, ACCENT, v -> {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:" + getPackageName())));
            } catch (Throwable ignored) { }
        });
        LinearLayout.LayoutParams overlayLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        overlayLp.topMargin = dp(10);
        card.addView(overlayPermission, overlayLp);
        root.addView(card);

        LinearLayout quiet = card();
        quiet.addView(caption("免打扰"));
        quiet.addView(hint("这段时间不念，消息照收"));
        quiet.addView(toggle("启用免打扰时段", Prefs.quietEnabled(this),
                (v, checked) -> Prefs.setQuietEnabled(MainActivity.this, checked)));
        LinearLayout hours = new LinearLayout(this);
        hours.setOrientation(LinearLayout.HORIZONTAL);
        EditText start = hourField(Prefs.quietStart(this), "开始");
        start.addTextChangedListener(saver(value -> Prefs.setQuietStart(MainActivity.this, parseHour(value, Prefs.quietStart(MainActivity.this)))));
        EditText end = hourField(Prefs.quietEnd(this), "结束");
        end.addTextChangedListener(saver(value -> Prefs.setQuietEnd(MainActivity.this, parseHour(value, Prefs.quietEnd(MainActivity.this)))));
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        half.rightMargin = dp(8);
        hours.addView(start, half);
        hours.addView(end, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        quiet.addView(hours);
        root.addView(quiet);

        LinearLayout apiCard = card();
        apiCard.addView(caption("模型与接口"));
        apiCard.addView(hint("填写接口地址、API Key 和模型名"));
        EditText base = field(Prefs.apiBase(this), "接口地址，例：https://api.deepseek.com", 1);
        base.setSingleLine(true);
        base.setMinLines(1);
        base.addTextChangedListener(saver(value -> Prefs.setApiBase(MainActivity.this, value)));
        apiCard.addView(base);
        EditText key = field(Prefs.apiKey(this), "API Key", 1);
        key.setSingleLine(true);
        key.setMinLines(1);
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.addTextChangedListener(saver(value -> Prefs.setApiKey(MainActivity.this, value)));
        apiCard.addView(key);
        EditText model = field(Prefs.model(this), "模型名，例：deepseek-chat", 1);
        model.setSingleLine(true);
        model.setMinLines(1);
        model.addTextChangedListener(saver(value -> Prefs.setModel(MainActivity.this, value)));
        apiCard.addView(model);

        final LinearLayout modelBox = new LinearLayout(this);
        modelBox.setOrientation(LinearLayout.VERTICAL);
        apiCard.addView(modelBox);

        TextView fetch = primary("获取模型列表", 0xFFEEF2FF, ACCENT, v -> {
            modelBox.removeAllViews();
            TextView loading = new TextView(this);
            loading.setText("正在拉取…");
            loading.setTextSize(12);
            loading.setTextColor(MUTED);
            modelBox.addView(loading);
            new Thread(() -> {
                java.util.List<String> models = Llm.listModels(this);
                runOnUiThread(() -> {
                    modelBox.removeAllViews();
                    if (models.isEmpty()) {
                        TextView empty = new TextView(this);
                        empty.setText("没拉到模型，检查接口地址和密钥");
                        empty.setTextSize(12);
                        empty.setTextColor(0xFFDC2626);
                        modelBox.addView(empty);
                        return;
                    }
                    for (String name : models) {
                        TextView item = new TextView(this);
                        item.setText((name.equals(Prefs.model(this)) ? "✓ " : "") + name);
                        item.setTextSize(13);
                        item.setTextColor(ACCENT);
                        item.setPadding(0, dp(6), 0, dp(6));
                        item.setOnClickListener(row -> {
                            Prefs.setModel(this, name);
                            model.setText(name);
                            Toast.makeText(this, "模型换成 " + name, Toast.LENGTH_SHORT).show();
                        });
                        modelBox.addView(item);
                    }
                });
            }).start();
        });
        LinearLayout.LayoutParams fetchLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        fetchLp.topMargin = dp(8);
        apiCard.addView(fetch, fetchLp);
        TextView testModel = primary("测试模型", 0xFFECFDF5, 0xFF047857, v -> {
            new Thread(() -> {
                String answer = Llm.quip(this, "测试", "系统", "你好");
                runOnUiThread(() -> Toast.makeText(this,
                        answer == null ? "没通，检查接口/密钥" : "模型正常：" + answer, Toast.LENGTH_LONG).show());
            }).start();
        });
        LinearLayout.LayoutParams testModelLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        testModelLp.topMargin = dp(8);
        apiCard.addView(testModel, testModelLp);
        root.addView(apiCard);



        LinearLayout power = card();
        power.addView(caption("后台保活"));
        power.addView(hint("小米/澎湃系统会杀后台，建议把省电策略设为「无限制」并允许自启动"));
        TextView details = primary("打开应用详情（省电策略 / 自启动）", 0xFFEEF2FF, ACCENT, v -> {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(android.net.Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        });
        power.addView(details);
        TextView battery = primary("加入电池优化白名单", 0xFFECFDF5, 0xFF047857, v -> {
            try {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Throwable ignored) { }
        });
        LinearLayout.LayoutParams batteryLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        batteryLp.topMargin = dp(8);
        power.addView(battery, batteryLp);
        root.addView(power);

        LinearLayout voice = card();
        voice.addView(caption("语音状态"));
        voice.addView(hint("系统没有中文语音时会自动改用在线合成"));
        TextView voiceInfo = new TextView(this);
        voiceInfo.setTextSize(12);
        voiceInfo.setTextColor(MUTED);
        voiceInfo.setText(preview == null ? "—" : preview.diagnosis());
        voiceInfo.setPadding(0, dp(2), 0, dp(6));
        voice.addView(voiceInfo);
        TextView testVoice = primary("说一句话试试", 0xFFEEF2FF, ACCENT, v -> {
            preview.speak("测试一下，主人，我现在能出声了。");
            voiceInfo.setText(preview.diagnosis());
            Toast.makeText(this, preview.diagnosis(), Toast.LENGTH_SHORT).show();
        });
        voice.addView(testVoice);
        root.addView(voice);

        LinearLayout about = card();
        about.addView(caption("关于"));
        about.addView(hint("消息播报 · 自己写的本地小工具\n通知只在本机处理，只有播报内容会发给模型"));
        root.addView(about);
        return root;
    }

    // ---------- 组件 ----------
    private LinearLayout column() {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        return column;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(round(Color.WHITE, 24));
        card.setElevation(dp(2));
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(14);
        card.setLayoutParams(lp);
        return card;
    }

    private TextView caption(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(16);
        view.setTextColor(INK);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return view;
    }

    private TextView hint(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(12);
        view.setTextColor(MUTED);
        view.setPadding(0, dp(2), 0, dp(8));
        return view;
    }

    private Switch toggle(String text, boolean checked, android.widget.CompoundButton.OnCheckedChangeListener listener) {
        Switch toggle = new Switch(this);
        toggle.setText(text);
        toggle.setTextSize(14);
        toggle.setTextColor(INK);
        toggle.setChecked(checked);
        toggle.setPadding(0, dp(6), 0, dp(6));
        int[][] states = new int[][]{{android.R.attr.state_checked}, {-android.R.attr.state_checked}};
        toggle.setThumbTintList(new ColorStateList(states, new int[]{ACCENT, 0xFFD1D5DB}));
        toggle.setTrackTintList(new ColorStateList(states, new int[]{0x664F46E5, 0xFFE5E7EB}));
        toggle.setOnCheckedChangeListener(listener);
        return toggle;
    }

    private EditText field(String value, String hintText, int lines) {
        EditText field = new EditText(this);
        field.setText(value);
        field.setHint(hintText);
        field.setTextSize(13);
        field.setHintTextColor(0xFF9CA3AF);
        field.setTextColor(INK);
        field.setGravity(Gravity.TOP | Gravity.START);
        field.setMinLines(lines);
        field.setSingleLine(false);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        field.setBackground(round(0xFFF3F4F6, 16));
        field.setPadding(dp(12), dp(10), dp(12), dp(10));
        field.setLineSpacing(dp(3), 1f);
        return field;
    }

    private EditText hourField(int value, String hint) {
        EditText field = new EditText(this);
        field.setText(String.valueOf(value));
        field.setHint(hint);
        field.setTextSize(14);
        field.setInputType(InputType.TYPE_CLASS_NUMBER);
        field.setGravity(Gravity.CENTER);
        field.setTextColor(INK);
        field.setHintTextColor(0xFF9CA3AF);
        field.setBackground(round(0xFFF3F4F6, 14));
        field.setPadding(dp(10), dp(10), dp(10), dp(10));
        return field;
    }

    private TextView primary(String text, int bg, int fg, View.OnClickListener listener) {
        TextView button = new TextView(this);
        button.setText(text);
        button.setTextSize(14);
        button.setTextColor(fg);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(16), dp(12), dp(16), dp(12));
        button.setBackground(round(bg, 16));
        button.setOnClickListener(listener);
        return button;
    }

    private TextWatcher saver(java.util.function.Consumer<String> sink) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { sink.accept(s.toString()); }
        };
    }

    private static int parseHour(String text, int fallback) {
        try {
            int value = Integer.parseInt(text.trim());
            return value >= 0 && value <= 23 ? value : fallback;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (tab == 0) refreshHero();
    }

    private boolean modelReady() {
        return !Prefs.token(this).isEmpty() || !Prefs.apiKey(this).isEmpty();
    }

    private boolean ping(String url) {
        java.net.HttpURLConnection connection = null;
        try {
            connection = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            connection.setConnectTimeout(700);
            connection.setReadTimeout(700);
            return connection.getResponseCode() == 200;
        } catch (Throwable ignored) {
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private boolean isListenerEnabled() {
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (TextUtils.isEmpty(flat)) return false;
        String me = getPackageName();
        for (String part : flat.split(":")) {
            if (part != null && part.startsWith(me)) return true;
        }
        return false;
    }

    @Override
    protected void onDestroy() {
        if (preview != null) preview.shutdown();
        super.onDestroy();
    }
}
