package com.worm.io;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Random;

public class SoundManager {
    private static SoundPool soundPool;
    private static int soundDoublePopId = -1;
    private static boolean ready = false;
    private static final Random rnd = new Random();
    private static long lastPlayTime = 0;

    public static void init(Context ctx) {
        if (ready) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            soundPool = new SoundPool.Builder()
                    .setMaxStreams(6)
                    .setAudioAttributes(attrs)
                    .build();
        } else {
            soundPool = new SoundPool(6, AudioManager.STREAM_MUSIC, 0);
        }

        soundPool.setOnLoadCompleteListener((sp, sampleId, status) -> {
            if (status == 0) ready = true;
        });

        new Thread(() -> {
            try {
                File soundFile = new File(ctx.getCacheDir(), "double_pop.wav");
                generateDoublePopWav(soundFile);
                soundDoublePopId = soundPool.load(soundFile.getAbsolutePath(), 1);
            } catch (Exception ignored) {}
        }).start();
    }

    public static void playDoublePop() {
        if (!ready || soundPool == null || soundDoublePopId == -1) return;

        long now = System.currentTimeMillis();
        if (now - lastPlayTime < 45) return;
        lastPlayTime = now;

        float rate = 0.95f + rnd.nextFloat() * 0.17f;
        soundPool.play(soundDoublePopId, 0.75f, 0.75f, 1, 0, rate);
    }

    public static void release() {
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
            ready = false;
        }
    }

    private static void generateDoublePopWav(File outFile) throws Exception {
        int sampleRate = 44100;
        float duration = 0.11f;
        int totalSamples = (int) (sampleRate * duration);
        byte[] pcmData = new byte[totalSamples * 2];

        for (int i = 0; i < totalSamples; i++) {
            float t = i / (float) sampleRate;
            float sample = 0f;

            if (t < 0.038f) {
                float pt = t / 0.038f;
                float freq = 420f + 430f * pt;
                float decay = (float) Math.exp(-pt * 6.5f);
                sample += (float) Math.sin(2.0 * Math.PI * freq * t) * decay * 0.75f;
            } else if (t >= 0.056f && t < 0.105f) {
                float t2 = t - 0.056f;
                float pt2 = t2 / 0.049f;
                float freq = 620f + 630f * pt2;
                float decay = (float) Math.exp(-pt2 * 5.8f);
                sample += (float) Math.sin(2.0 * Math.PI * freq * t2) * decay * 0.90f;
            }

            short val = (short) (Math.max(-1f, Math.min(1f, sample)) * 32767);
            pcmData[2 * i] = (byte) (val & 0xFF);
            pcmData[2 * i + 1] = (byte) ((val >> 8) & 0xFF);
        }

        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            int subChunk2Size = pcmData.length;
            int chunkSize = 36 + subChunk2Size;

            byte[] header = new byte[44];
            header[0] = 'R'; header[1] = 'I'; header[2] = 'F'; header[3] = 'F';
            header[4] = (byte) (chunkSize & 0xff);
            header[5] = (byte) ((chunkSize >> 8) & 0xff);
            header[6] = (byte) ((chunkSize >> 16) & 0xff);
            header[7] = (byte) ((chunkSize >> 24) & 0xff);
            header[8] = 'W'; header[9] = 'A'; header[10] = 'V'; header[11] = 'E';
            header[12] = 'f'; header[13] = 'm'; header[14] = 't'; header[15] = ' ';
            header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0;
            header[20] = 1; header[21] = 0;
            header[22] = 1; header[23] = 0;
            header[24] = (byte) (sampleRate & 0xff);
            header[25] = (byte) ((sampleRate >> 8) & 0xff);
            header[26] = (byte) ((sampleRate >> 16) & 0xff);
            header[27] = (byte) ((sampleRate >> 24) & 0xff);
            int byteRate = sampleRate * 2;
            header[28] = (byte) (byteRate & 0xff);
            header[29] = (byte) ((byteRate >> 8) & 0xff);
            header[30] = (byte) ((byteRate >> 16) & 0xff);
            header[31] = (byte) ((byteRate >> 24) & 0xff);
            header[32] = 2; header[33] = 0;
            header[34] = 16; header[35] = 0;
            header[36] = 'd'; header[37] = 'a'; header[38] = 't'; header[39] = 'a';
            header[40] = (byte) (subChunk2Size & 0xff);
            header[41] = (byte) ((subChunk2Size >> 8) & 0xff);
            header[42] = (byte) ((subChunk2Size >> 16) & 0xff);
            header[43] = (byte) ((subChunk2Size >> 24) & 0xff);

            fos.write(header);
            fos.write(pcmData);
        }
    }
}