package com.dsh.herald.release;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;

/** 说一句话 → 本地离线识别 → 直接回给对方。 */
public class VoiceReplyActivity extends Activity {
    private static final int RATE = 16000;
    private static final String ASR = "http://127.0.0.1:18767/asr";

    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean recording = new AtomicBoolean(false);
    private TextView status;
    private AudioRecord record;
    private String targetKey = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        targetKey = getIntent().getStringExtra("key");
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.argb(235, 17, 24, 39));
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("正在听你说…");
        title.setTextSize(22);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        status = new TextView(this);
        status.setText("直接说你想回的话，说完点下面");
        status.setTextSize(13);
        status.setTextColor(0xFF9CA3AF);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, pad / 2, 0, pad);
        root.addView(status);

        TextView stop = new TextView(this);
        stop.setText("说完了");
        stop.setTextSize(18);
        stop.setTextColor(Color.WHITE);
        stop.setGravity(Gravity.CENTER);
        stop.setPadding(pad, pad / 2, pad, pad / 2);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xFF4F46E5);
        bg.setCornerRadius(pad);
        stop.setBackground(bg);
        stop.setOnClickListener(v -> finishRecording());
        root.addView(stop, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 9);
        } else {
            startRecording();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == 9 && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            startRecording();
        } else {
            Toast.makeText(this, "没给录音权限，回不去语音", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void startRecording() {
        try {
            int min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            record = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(min * 2, 8192));
            record.startRecording();
            recording.set(true);
            new Thread(this::readLoop, "voice-read").start();
            main.postDelayed(() -> { if (recording.get()) finishRecording(); }, 20000);
        } catch (Throwable error) {
            Toast.makeText(this, "麦克风打不开", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void readLoop() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[3200];
        while (recording.get()) {
            int read = record.read(buffer, 0, buffer.length);
            if (read > 0) out.write(buffer, 0, read);
        }
        try { record.stop(); record.release(); } catch (Throwable ignored) { }
        byte[] pcm = out.toByteArray();
        if (pcm.length < RATE / 2) {  // 不到 0.5 秒当没说话
            main.post(() -> { Toast.makeText(this, "没听清，再说一次", Toast.LENGTH_SHORT).show(); finish(); });
            return;
        }
        main.post(() -> status.setText("正在识别…"));
        try {
            byte[] wav = wav(pcm);
            HttpURLConnection connection = (HttpURLConnection) new URL(ASR).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(15000);
            connection.setDoOutput(true);
            connection.setRequestProperty("content-type", "audio/wav");
            try (OutputStream os = connection.getOutputStream()) { os.write(wav); }
            int code = connection.getResponseCode();
            String body = new String(connection.getInputStream().readAllBytes(), "UTF-8");
            if (code != 200) throw new Exception(body);
            String text = body.replaceAll(".*\"text\"\\s*:\\s*\"", "").replaceAll("\"\\s*}.*", "");
            text = text.replace("\\n", " ").trim();
            if (text.isEmpty()) {
                main.post(() -> { Toast.makeText(this, "没听清，再说一次", Toast.LENGTH_SHORT).show(); finish(); });
                return;
            }
            final String reply = text;
            main.post(() -> {
                status.setText("识别结果：" + reply);
                VoiceReply.send(this, targetKey, reply, true);
                finish();
            });
        } catch (Throwable error) {
            main.post(() -> {
                Toast.makeText(this, "语音识别没连上（引擎没开？）", Toast.LENGTH_LONG).show();
                finish();
            });
        }
    }

    private void finishRecording() {
        if (recording.getAndSet(false)) status.setText("正在识别…");
    }

    private static byte[] wav(byte[] pcm) {
        byte[] header = new byte[44];
        int length = pcm.length;
        header[0] = 'R'; header[1] = 'I'; header[2] = 'F'; header[3] = 'F';
        header[4] = (byte) (36 + length); header[5] = (byte) ((36 + length) >> 8); header[6] = (byte) ((36 + length) >> 16); header[7] = (byte) ((36 + length) >> 24);
        header[8] = 'W'; header[9] = 'A'; header[10] = 'V'; header[11] = 'E';
        header[12] = 'f'; header[13] = 'm'; header[14] = 't'; header[15] = ' ';
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0;
        header[20] = 1; header[21] = 0;
        header[22] = 1; header[23] = 0;
        header[24] = (byte) 0x80; header[25] = 0x3E; header[26] = 0; header[27] = 0;
        header[28] = (byte) 0x00; header[29] = 0x7D; header[30] = 0; header[31] = 0;
        header[32] = 2; header[33] = 0;
        header[34] = 16; header[35] = 0;
        header[36] = 'd'; header[37] = 'a'; header[38] = 't'; header[39] = 'a';
        header[40] = (byte) length; header[41] = (byte) (length >> 8); header[42] = (byte) (length >> 16); header[43] = (byte) (length >> 24);
        byte[] out = new byte[44 + length];
        System.arraycopy(header, 0, out, 0, 44);
        System.arraycopy(pcm, 0, out, 44, length);
        return out;
    }

    @Override
    protected void onDestroy() {
        recording.set(false);
        super.onDestroy();
    }
}
