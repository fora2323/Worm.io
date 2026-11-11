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
    private static int soundBoostId = -1;
    private static int soundCrashId = -1;
    private static int soundLobbyBgmId = -1;

    private static int boostStreamId = 0;
    private static int lobbyBgmStreamId = 0;
    private static boolean ready = false;
    private static final Random rnd = new Random();
    private static long lastPopTime = 0;

    public static void init(Context ctx) {
        if (soundPool != null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            soundPool = new SoundPool.Builder()
                    .setMaxStreams(10)
                    .setAudioAttributes(attrs)
                    .build();
        } else {
            soundPool = new SoundPool(10, AudioManager.STREAM_MUSIC, 0);
        }

        soundPool.setOnLoadCompleteListener((sp, sampleId, status) -> {
            if (status == 0) ready = true;
        });

        new Thread(() -> {
            try {
                File popFile = new File(ctx.getCacheDir(), "double_pop.wav");
                generateDoublePopWav(popFile);
                soundDoublePopId = soundPool.load(popFile.getAbsolutePath(), 1);

                File boostFile = new File(ctx.getCacheDir(), "turbo_boost.wav");
                generateTurboBoostWav(boostFile);
                soundBoostId = soundPool.load(boostFile.getAbsolutePath(), 1);

                File crashFile = new File(ctx.getCacheDir(), "crash_hit.wav");
                generateCrashWav(crashFile);
                soundCrashId = soundPool.load(crashFile.getAbsolutePath(), 1);

                File bgmFile = new File(ctx.getCacheDir(), "lobby_bgm.wav");
                generateLobbyBgmWav(bgmFile);
                soundLobbyBgmId = soundPool.load(bgmFile.getAbsolutePath(), 1);
            } catch (Exception ignored) {}
        }).start();
    }

    public static void startLobbyMusic() {
        if (soundPool == null || soundLobbyBgmId == -1) return;
        stopLobbyMusic();
        lobbyBgmStreamId = soundPool.play(soundLobbyBgmId, 0.45f, 0.45f, 1, -1, 1.0f);
    }

    public static void stopLobbyMusic() {
        if (soundPool != null && lobbyBgmStreamId != 0) {
            soundPool.stop(lobbyBgmStreamId);
            lobbyBgmStreamId = 0;
        }
    }

    public static void playDoublePop() {
        if (!ready || soundPool == null || soundDoublePopId == -1) return;
        long now = System.currentTimeMillis();
        if (now - lastPopTime < 45) return;
        lastPopTime = now;

        float rate = 0.95f + rnd.nextFloat() * 0.17f;
        soundPool.play(soundDoublePopId, 0.70f, 0.70f, 2, 0, rate);
    }

    public static void startBoost() {
        if (!ready || soundPool == null || soundBoostId == -1) return;
        stopBoost();
        boostStreamId = soundPool.play(soundBoostId, 0.85f, 0.85f, 3, -1, 1.05f);
    }

    public static void stopBoost() {
        if (soundPool != null && boostStreamId != 0) {
            soundPool.stop(boostStreamId);
            boostStreamId = 0;
        }
    }

    public static void playCrash() {
        if (!ready || soundPool == null || soundCrashId == -1) return;
        soundPool.play(soundCrashId, 0.95f, 0.95f, 4, 0, 1.0f);
    }

    public static void release() {
        stopBoost();
        stopLobbyMusic();
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
                float freq = 440f + 420f * pt;
                float decay = (float) Math.exp(-pt * 6.5f);
                sample += (float) Math.sin(2.0 * Math.PI * freq * t) * decay * 0.75f;
            } else if (t >= 0.056f && t < 0.105f) {
                float t2 = t - 0.056f;
                float pt2 = t2 / 0.049f;
                float freq = 650f + 650f * pt2;
                float decay = (float) Math.exp(-pt2 * 5.8f);
                sample += (float) Math.sin(2.0 * Math.PI * freq * t2) * decay * 0.90f;
            }
            short val = (short) (Math.max(-1f, Math.min(1f, sample)) * 32767);
            pcmData[2 * i] = (byte) (val & 0xFF);
            pcmData[2 * i + 1] = (byte) ((val >> 8) & 0xFF);
        }
        writeWav(outFile, pcmData, sampleRate);
    }

    /** Suara Turbo Plasma Jet yang halus dan bertenaga */
    private static void generateTurboBoostWav(File outFile) throws Exception {
        int sampleRate = 44100;
        float duration = 0.40f;
        int totalSamples = (int) (sampleRate * duration);
        byte[] pcmData = new byte[totalSamples * 2];

        for (int i = 0; i < totalSamples; i++) {
            float t = i / (float) sampleRate;
            float freq1 = 280f + 25f * (float) Math.sin(2.0 * Math.PI * 8.0 * t);
            float freq2 = 560f + 40f * (float) Math.sin(2.0 * Math.PI * 12.0 * t);
            float tone = (float) (Math.sin(2.0 * Math.PI * freq1 * t) * 0.55 + Math.sin(2.0 * Math.PI * freq2 * t) * 0.35);
            float noise = ((i % 17) / 17.0f - 0.5f) * 0.15f;
            float sample = (tone + noise) * 0.75f;

            short val = (short) (Math.max(-1f, Math.min(1f, sample)) * 32767);
            pcmData[2 * i] = (byte) (val & 0xFF);
            pcmData[2 * i + 1] = (byte) ((val >> 8) & 0xFF);
        }
        writeWav(outFile, pcmData, sampleRate);
    }

    /** Suara Tabrakan / Crash berat saat cacing menabrak */
    private static void generateCrashWav(File outFile) throws Exception {
        int sampleRate = 44100;
        float duration = 0.38f;
        int totalSamples = (int) (sampleRate * duration);
        byte[] pcmData = new byte[totalSamples * 2];
        Random crashRnd = new Random(99);

        for (int i = 0; i < totalSamples; i++) {
            float t = i / (float) sampleRate;
            float env = (float) Math.exp(-t * 9.5f);
            float bass = (float) Math.sin(2.0 * Math.PI * (120f - 80f * (t / duration)) * t) * 0.65f;
            float noise = (crashRnd.nextFloat() * 2f - 1f) * 0.55f;
            float sample = (bass + noise) * env;

            short val = (short) (Math.max(-1f, Math.min(1f, sample)) * 32767);
            pcmData[2 * i] = (byte) (val & 0xFF);
            pcmData[2 * i + 1] = (byte) ((val >> 8) & 0xFF);
        }
        writeWav(outFile, pcmData, sampleRate);
    }

    /** BGM Melodi Chiptune/Synth santai di Lobby (Loopable) */
    private static void generateLobbyBgmWav(File outFile) throws Exception {
        int sampleRate = 22050;
        float duration = 3.2f;
        int totalSamples = (int) (sampleRate * duration);
        byte[] pcmData = new byte[totalSamples * 2];
        float[] notes = {261.63f, 329.63f, 392.00f, 523.25f, 440.00f, 392.00f, 329.63f, 293.66f};
        float noteDur = duration / notes.length;

        for (int i = 0; i < totalSamples; i++) {
            float t = i / (float) sampleRate;
            int nIdx = Math.min(notes.length - 1, (int) (t / noteDur));
            float freq = notes[nIdx];
            float noteT = t - nIdx * noteDur;
            float env = (float) (Math.sin(Math.PI * (noteT / noteDur)) * 0.85f);
            float lead = (float) Math.sin(2.0 * Math.PI * freq * t) * env * 0.40f;
            float bass = (float) Math.sin(2.0 * Math.PI * (freq * 0.5f) * t) * 0.25f;
            float sample = lead + bass;

            short val = (short) (Math.max(-1f, Math.min(1f, sample)) * 32767);
            pcmData[2 * i] = (byte) (val & 0xFF);
            pcmData[2 * i + 1] = (byte) ((val >> 8) & 0xFF);
        }
        writeWav(outFile, pcmData, sampleRate);
    }

    private static void writeWav(File outFile, byte[] pcmData, int sampleRate) throws Exception {
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