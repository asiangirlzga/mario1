package com.blockhero.tv;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.SoundPool;

import java.io.File;

/** Tiny chiptune sound effects + music, synthesised at start-up (no audio files, ~200 KB RAM). */
final class Sfx {
    static final int JUMP = 0, COIN = 1, STOMP = 2, HURT = 3, WIN = 4, COUNT = 5;
    private static final int RATE = 11025;

    private final AudioTrack[] fx = new AudioTrack[COUNT];
    private AudioTrack music;
    private boolean musicPlaying;
    private SoundPool pool;
    private final int[] poolIds = new int[COUNT];
    private MediaPlayer mp;

    Sfx() {
        try {
            fx[JUMP] = track(tone(300, 720, 0.17f, 0.25f));
            fx[COIN] = track(cat(tone(988, 988, 0.07f, 0.22f), tone(1319, 1319, 0.22f, 0.22f)));
            fx[STOMP] = track(tone(520, 140, 0.13f, 0.3f));
            fx[HURT] = track(tone(620, 90, 0.45f, 0.3f));
            fx[WIN] = track(cat(tone(523, 523, 0.12f, 0.22f), tone(659, 659, 0.12f, 0.22f),
                    tone(784, 784, 0.12f, 0.22f), tone(1047, 1047, 0.45f, 0.22f)));
            music = makeMusic();
        } catch (Throwable ignored) {
            // no audio on this device: the game still works silently
        }
    }

    synchronized void play(int id) {
        try {
            if (pool != null && poolIds[id] > 0) {
                pool.play(poolIds[id], 1f, 1f, 1, 0, 1f);
                return;
            }
            AudioTrack a = fx[id];
            if (a == null) return;
            a.stop();
            a.reloadStaticData();
            a.play();
        } catch (Throwable ignored) {
        }
    }

    synchronized void music(boolean on) {
        if (on == musicPlaying) return;
        musicPlaying = on;
        try {
            if (mp != null) {
                if (on) mp.start(); else mp.pause();
            } else if (music != null) {
                if (on) music.play(); else music.pause();
            }
        } catch (Throwable ignored) {
        }
    }

    /** Use downloaded files (if present in dir) instead of the built-in synthesised sounds. */
    synchronized void loadRemote(File dir) {
        try {
            File m = new File(dir, Assets.MUSIC);
            if (mp == null && m.exists()) {
                MediaPlayer p = new MediaPlayer();
                p.setDataSource(m.getPath());
                p.setLooping(true);
                p.setVolume(0.6f, 0.6f);
                p.prepare();
                mp = p;
                if (musicPlaying) {
                    if (music != null) music.pause();
                    mp.start();
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            for (int i = 0; i < COUNT; i++) {
                File f = new File(dir, Assets.FX[i]);
                if (poolIds[i] == 0 && f.exists()) {
                    if (pool == null) pool = new SoundPool(4, AudioManager.STREAM_MUSIC, 0);
                    poolIds[i] = pool.load(f.getPath(), 1);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    synchronized void release() {
        try {
            if (mp != null) mp.release();
            if (pool != null) pool.release();
            for (AudioTrack a : fx) if (a != null) a.release();
            if (music != null) music.release();
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------- synthesis

    private static AudioTrack track(short[] d) {
        AudioTrack a = new AudioTrack(AudioManager.STREAM_MUSIC, RATE, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT, d.length * 2, AudioTrack.MODE_STATIC);
        a.write(d, 0, d.length);
        return a;
    }

    private static short[] tone(float f0, float f1, float dur, float vol) {
        int n = (int) (RATE * dur);
        short[] o = new short[n];
        float ph = 0;
        for (int i = 0; i < n; i++) {
            float t = i / (float) n;
            ph += (f0 + (f1 - f0) * t) / RATE;
            float env = (1f - t * 0.75f) * Math.min(1f, i / 60f);
            o[i] = (short) ((ph % 1f < 0.5f ? 1 : -1) * vol * env * 32000);
        }
        return o;
    }

    private static short[] cat(short[]... parts) {
        int n = 0;
        for (short[] p : parts) n += p.length;
        short[] o = new short[n];
        int k = 0;
        for (short[] p : parts) {
            System.arraycopy(p, 0, o, k, p.length);
            k += p.length;
        }
        return o;
    }

    private static float hz(int semitonesFromC4) {
        return 261.63f * (float) Math.pow(2.0, semitonesFromC4 / 12.0);
    }

    private AudioTrack makeMusic() {
        int[] mel = {12, 16, 19, 16, 21, 19, 16, 12, 14, 17, 21, 17, 19, 16, 14, 12,
                12, 16, 19, 24, 21, 19, 17, 16, 14, 17, 19, 17, 16, 14, 12, -99};
        int[] bass = {-12, -15, -19, -17, -12, -19, -17, -12};
        int per = (int) (RATE * 0.2f);
        short[] o = new short[mel.length * per];
        float pm = 0, pb = 0;
        for (int n = 0; n < mel.length; n++) {
            float fm = mel[n] > -90 ? hz(mel[n]) : 0f;
            float fb = hz(bass[n / 4]);
            for (int i = 0; i < per; i++) {
                float t = i / (float) per;
                float s = 0;
                if (fm > 0) {
                    pm += fm / RATE;
                    s += (pm % 1f < 0.5f ? 1 : -1) * 0.09f * (1f - t * 0.6f);
                }
                pb += fb / RATE;
                s += (pb % 1f < 0.5f ? 1 : -1) * 0.06f * (n % 4 == 0 ? 1f : 0.7f);
                o[n * per + i] = (short) (s * 32000);
            }
        }
        AudioTrack a = track(o);
        a.setLoopPoints(0, o.length, -1);
        return a;
    }
}
