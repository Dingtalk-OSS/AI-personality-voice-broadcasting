package com.dsh.herald.release;

import android.content.Context;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayDeque;
import java.util.Locale;

/** 语音播报：本地高质量语音（晓晓）→ 系统 TTS → 在线合成，一层层退。 */
public class Speaker {
    private static final String TAG = "Herald";
    /** 本机引擎里的 Edge TTS 服务，音色比系统/百度好，带停顿和语调。 */
    private static final String LOCAL = "http://127.0.0.1:18766/tts?text=";
    private static final String ONLINE = "https://fanyi.baidu.com/gettts?lan=zh&spd=5&source=web&text=";

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ArrayDeque<String> queue = new ArrayDeque<>();
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private boolean busy = false;
    private int seq = 0;
    private MediaPlayer player;
    private String lastEngine = "";
    private String diagnosis = "还没念过";
    private long startedAt = 0;
    private String lastText = "";
    private Runnable idleListener;

    public void setIdle(Runnable listener) { idleListener = listener; }

    public Speaker(Context context) {
        this.context = context.getApplicationContext();
        try {
            tts = new TextToSpeech(this.context, status -> {
                if (status != TextToSpeech.SUCCESS) return;
                int available = tts.isLanguageAvailable(Locale.CHINA);
                if (available < TextToSpeech.LANG_AVAILABLE) {
                    Log.i(TAG, "system tts has no Chinese voice");
                    return;
                }
                tts.setLanguage(Locale.CHINA);
                tts.setSpeechRate(1.08f);
                tts.setPitch(1.06f);
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String utteranceId) { }
                    @Override public void onDone(String utteranceId) {
                        long spent = System.currentTimeMillis() - startedAt;
                        if (spent < 600) {
                            diagnosis = "系统语音没出声，改用在线语音";
                            ttsReady = false;
                            speakBaidu(lastText);
                            return;
                        }
                        finish();
                    }
                    @Override public void onError(String utteranceId) {
                        diagnosis = "系统语音报错，改用在线语音";
                        ttsReady = false;
                        speakBaidu(lastText);
                    }
                });
                ttsReady = true;
            });
        } catch (Throwable error) {
            Log.w(TAG, "tts init failed", error);
        }
    }

    public String lastEngine() { return lastEngine; }

    public String diagnosis() { return diagnosis; }

    public void speak(String text) {
        if (text == null || text.trim().isEmpty()) return;
        main.post(() -> {
            queue.add(text.trim());
            pump();
        });
    }

    private void pump() {
        if (busy) return;
        String text = queue.poll();
        if (text == null) return;
        busy = true;
        lastText = text;
        new Thread(() -> {
            File local = BuildInfo.RELEASE ? null : tryDownload(LOCAL + encode(text), 6000);
            if (local != null) {
                lastEngine = "本地高质量语音";
                diagnosis = "本地高质量语音（晓晓）";
                main.post(() -> play(local));
                return;
            }
            if (ttsReady) {
                lastEngine = "系统语音";
                diagnosis = "系统语音";
                main.post(() -> speakSystem(text));
                return;
            }
            speakBaidu(text);
        }, "herald-tts").start();
    }

    private void speakSystem(String text) {
        try {
            seq++;
            startedAt = System.currentTimeMillis();
            int code = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "h" + seq);
            if (code != TextToSpeech.SUCCESS) {
                ttsReady = false;
                speakBaidu(text);
            }
        } catch (Throwable error) {
            ttsReady = false;
            speakBaidu(text);
        }
    }

    private void speakBaidu(String text) {
        lastEngine = "在线语音";
        new Thread(() -> {
            File file = tryDownload(ONLINE + encode(text), 8000);
            if (file == null) {
                diagnosis = "语音合成失败";
                main.post(() -> { busy = false; pump(); });
                return;
            }
            diagnosis = "在线语音（百度）";
            main.post(() -> play(file));
        }, "herald-baidu").start();
    }

    private static String encode(String text) {
        try { return URLEncoder.encode(text, "UTF-8"); } catch (Throwable error) { return text; }
    }

    /** 下载一段音频，失败或超时返回 null。 */
    private File tryDownload(String url, int timeoutMs) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(timeoutMs);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14)");
            int code = connection.getResponseCode();
            if (code != 200) return null;
            String type = String.valueOf(connection.getContentType());
            if (!type.contains("audio") && !type.contains("mpeg") && !type.contains("octet-stream")) return null;
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (InputStream in = connection.getInputStream()) {
                byte[] chunk = new byte[8192];
                int read;
                while ((read = in.read(chunk)) > 0) buffer.write(chunk, 0, read);
            }
            if (buffer.size() < 1024) return null;
            File file = new File(context.getCacheDir(), "tts-" + System.currentTimeMillis() + ".mp3");
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(buffer.toByteArray());
            }
            return file;
        } catch (Throwable error) {
            Log.w(TAG, "download failed " + url.substring(0, Math.min(40, url.length())), error);
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void play(File file) {
        try {
            releasePlayer();
            player = new MediaPlayer();
            player.setDataSource(file.getAbsolutePath());
            player.setOnCompletionListener(mp -> {
                releasePlayer();
                //noinspection ResultOfMethodCallIgnored
                file.delete();
                finish();
            });
            player.setOnErrorListener((mp, what, extra) -> {
                releasePlayer();
                main.post(() -> { busy = false; pump(); });
                return true;
            });
            player.prepare();
            player.start();
        } catch (Throwable error) {
            Log.w(TAG, "playback failed", error);
            releasePlayer();
            main.post(() -> { busy = false; pump(); });
        }
    }

    private void finish() {
        main.post(() -> {
            busy = false;
            pump();
            if (!busy && queue.isEmpty() && idleListener != null) idleListener.run();
        });
    }

    private void releasePlayer() {
        if (player != null) {
            try { player.stop(); } catch (Throwable ignored) { }
            try { player.release(); } catch (Throwable ignored) { }
            player = null;
        }
    }

    public void stopAll() {
        queue.clear();
        if (tts != null) { try { tts.stop(); } catch (Throwable ignored) { } }
        releasePlayer();
        busy = false;
    }

    public void shutdown() {
        stopAll();
        if (tts != null) { try { tts.shutdown(); } catch (Throwable ignored) { } }
        tts = null;
    }
}
