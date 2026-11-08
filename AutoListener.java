package com.dsh.herald.release;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** 播报结束后自动开麦听一句：安静 1.2 秒或满 8 秒就停，识别后直接回。 */
public final class AutoListener {
    private static final String TAG = "Herald";
    private static final int RATE = 16000;
    private static final String ASR = "http://127.0.0.1:18767/asr";
    private static boolean listening = false;

    public static void listen(final Context context, final String key) {
        if (listening) return;
        listening = true;
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            AudioRecord record = null;
            try {
                int min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                record = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE,
                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(min * 2, 8192));
                record.startRecording();
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[1600];
                long start = System.currentTimeMillis();
                long lastVoice = 0;
                double threshold = 500;
                while (System.currentTimeMillis() - start < 8000) {
                    int read = record.read(buffer, 0, buffer.length);
                    if (read <= 0) continue;
                    out.write(buffer, 0, read);
                    long sum = 0;
                    for (int i = 0; i + 1 < read; i += 2) {
                        int sample = (short) ((buffer[i] & 0xFF) | (buffer[i + 1] << 8));
                        sum += (long) sample * sample;
                    }
                    double rms = Math.sqrt(sum / (double) Math.max(1, read / 2));
                    long now = System.currentTimeMillis();
                    if (rms > threshold) lastVoice = now;
                    if (now - start > 1200 && (lastVoice == 0 || now - lastVoice > 1200)) break;
                }
                byte[] pcm = out.toByteArray();
                if (pcm.length < RATE / 2) return;
                byte[] wav = wav(pcm);
                HttpURLConnection connection = (HttpURLConnection) new URL(ASR).openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(2000);
                connection.setReadTimeout(12000);
                connection.setDoOutput(true);
                connection.setRequestProperty("content-type", "audio/wav");
                try (OutputStream os = connection.getOutputStream()) { os.write(wav); }
                if (connection.getResponseCode() != 200) return;
                String body = new String(connection.getInputStream().readAllBytes(), "UTF-8");
                String text = body.replaceAll(".*\"text\"\\s*:\\s*\"", "").replaceAll("\"\\s*}.*", "").trim();
                if (text.isEmpty() || text.length() < 2) return;
                main.post(() -> VoiceReply.send(context, key, text, true));
            } catch (Throwable error) {
                Log.w(TAG, "auto listen failed", error);
            } finally {
                try { if (record != null) { record.stop(); record.release(); } } catch (Throwable ignored) { }
                listening = false;
            }
        }, "herald-listen").start();
    }

    public static boolean isListening() { return listening; }

    static byte[] wav(byte[] pcm) {
        byte[] header = new byte[44];
        int length = pcm.length;
        header[0] = 'R'; header[1] = 'I'; header[2] = 'F'; header[3] = 'F';
        header[4] = (byte) (36 + length); header[5] = (byte) ((36 + length) >> 8); header[6] = (byte) ((36 + length) >> 16); header[7] = (byte) ((36 + length) >> 24);
        header[8] = 'W'; header[9] = 'A'; header[10] = 'V'; header[11] = 'E';
        header[12] = 'f'; header[13] = 'm'; header[14] = 't'; header[15] = ' ';
        header[16] = 16;
        header[20] = 1;
        header[22] = 1;
        header[24] = (byte) 0x80; header[25] = 0x3E;
        header[28] = (byte) 0x00; header[29] = 0x7D;
        header[32] = 2;
        header[34] = 16;
        header[36] = 'd'; header[37] = 'a'; header[38] = 't'; header[39] = 'a';
        header[40] = (byte) length; header[41] = (byte) (length >> 8); header[42] = (byte) (length >> 16); header[43] = (byte) (length >> 24);
        byte[] out = new byte[44 + length];
        System.arraycopy(header, 0, out, 0, 44);
        System.arraycopy(pcm, 0, out, 44, length);
        return out;
    }

    private AutoListener() { }
}
