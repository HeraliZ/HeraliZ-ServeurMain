package com.caracrea.voice;

import com.caracrea.character.FaceState;

/**
 * Reads the shape of the mouth from one frame of voice (Simple Voice Chat
 * sends 20 ms of 16-bit mono audio at 48 kHz).
 *
 * <p>Vowels are told apart by the two lowest resonances of the mouth and
 * throat, the formants F1 and F2: F1 rises as the jaw opens (A), F2 rises as
 * the tongue moves forward (E, I) and drops with rounded lips (O, OU). They
 * are found as the peaks of the spectral envelope given by linear prediction
 * (LPC), the classic speech technique, on the voice brought down to 12 kHz.
 * Hissing sounds (S, F, CH) have most of their energy at high frequencies
 * and show teeth. Silence, M, B and P close the mouth.
 *
 * <p>About 20 microseconds per frame; the working arrays are kept per thread,
 * so nothing is allocated frame after frame. Safe to call from several threads
 * at once.
 */
public final class SpeechAnalyzer {
    /** Rate the envelope is computed at. */
    private static final int RATE = 12_000;
    private static final int ORDER = 12;
    /** Frequencies at which the envelope is read: 150 Hz to 3.5 kHz. */
    private static final float F_MIN = 150;
    private static final float F_STEP = 20;
    private static final int BINS = 169;
    private static final float[][] COS = new float[BINS][ORDER + 1];
    private static final float[][] SIN = new float[BINS][ORDER + 1];

    static {
        for (int b = 0; b < BINS; b++) {
            double w = 2 * Math.PI * (F_MIN + b * F_STEP) / RATE;
            for (int k = 0; k <= ORDER; k++) {
                COS[b][k] = (float) Math.cos(w * k);
                SIN[b][k] = (float) Math.sin(w * k);
            }
        }
    }

    /** Working arrays of one thread. */
    private static final class Scratch {
        double[] x = new double[256];
        final double[] r = new double[ORDER + 1];
        final double[] a = new double[ORDER + 1];
        final double[] tmp = new double[ORDER + 1];
        final float[] power = new float[BINS];
        double[] pitch = new double[256];
    }

    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    private SpeechAnalyzer() {
    }

    /**
     * Loudness (0 silent, 1 shouting) and mouth shape ({@code FaceState.MOUTH_*}) of a frame.
     * {@code f1} / {@code f2} are the formants found, in Hz (0 when none).
     */
    public record Result(float level, int mouth, float f1, float f2) {
        static final Result SILENT = new Result(0, FaceState.MOUTH_REST, 0, 0);
    }

    /** Loudness of a frame, 0..1: speech sits roughly between -42 dB (quiet) and -12 dB (loud). */
    public static float level(short[] pcm) {
        double sum = 0;
        for (short sample : pcm) {
            sum += (double) sample * sample;
        }
        double rms = Math.sqrt(sum / pcm.length) / 32768.0;
        if (rms <= 0) {
            return 0;
        }
        float db = (float) (20 * Math.log10(rms));
        return Math.max(0, Math.min(1, (db + 42) / 30));
    }

    public static Result analyse(short[] pcm, int sampleRate) {
        if (pcm == null || pcm.length < 64) {
            return Result.SILENT;
        }
        float level = level(pcm);
        if (level < 0.1F) {
            return new Result(level, FaceState.MOUTH_REST, 0, 0);
        }

        // Hiss: share of the energy in fast changes. A tone at f gives 2 (1 - cos(2 pi f / rate)), so
        // anything above ~0.8 of the value at 2.5 kHz is mostly high-pitched noise, not a vowel.
        double energy = 0;
        double change = 0;
        for (int i = 1; i < pcm.length; i++) {
            double x = pcm[i];
            double d = x - pcm[i - 1];
            energy += x * x;
            change += d * d;
        }
        double hissAt = 2 * (1 - Math.cos(2 * Math.PI * 2500 / sampleRate));
        if (energy > 0 && change / energy > 0.8 * hissAt) {
            return new Result(level, FaceState.MOUTH_TEETH, 0, 0);
        }

        // Down to ~12 kHz (block average), pre-emphasis, Hamming window.
        int factor = Math.max(1, Math.round(sampleRate / (float) RATE));
        int n = pcm.length / factor;
        Scratch scratch = SCRATCH.get();
        if (scratch.x.length < n) {
            scratch.x = new double[n];
        }
        double[] x = scratch.x;
        for (int i = 0; i < n; i++) {
            double sum = 0;
            for (int j = 0; j < factor; j++) {
                sum += pcm[i * factor + j];
            }
            x[i] = sum / factor;
        }
        // Formants are higher in higher voices (women, children): compare them to a man's.
        float scale = voiceScale(x, n, scratch);
        double previous = x[0];
        for (int i = 1; i < n; i++) {
            double current = x[i];
            x[i] = current - 0.94 * previous;
            previous = current;
        }
        x[0] = 0;
        for (int i = 0; i < n; i++) {
            x[i] *= 0.54 - 0.46 * Math.cos(2 * Math.PI * i / (n - 1));
        }

        // Linear prediction (autocorrelation, Levinson-Durbin).
        double[] r = scratch.r;
        for (int lag = 0; lag <= ORDER; lag++) {
            double sum = 0;
            for (int i = lag; i < n; i++) {
                sum += x[i] * x[i - lag];
            }
            r[lag] = sum;
        }
        if (r[0] <= 0) {
            return new Result(level, FaceState.MOUTH_SMALL, 0, 0);
        }
        r[0] *= 1.0001;
        double[] a = scratch.a;
        double[] tmp = scratch.tmp;
        java.util.Arrays.fill(a, 0);
        a[0] = 1;
        double error = r[0];
        for (int i = 1; i <= ORDER; i++) {
            double acc = r[i];
            for (int j = 1; j < i; j++) {
                acc += a[j] * r[i - j];
            }
            double k = -acc / error;
            System.arraycopy(a, 0, tmp, 0, i);
            for (int j = 1; j < i; j++) {
                a[j] = tmp[j] + k * tmp[i - j];
            }
            a[i] = k;
            error *= 1 - k * k;
            if (error <= 0) {
                return new Result(level, FaceState.MOUTH_SMALL, 0, 0);
            }
        }

        // Spectral envelope 1 / |A(f)|^2 and its peaks.
        float[] power = scratch.power;
        for (int b = 0; b < BINS; b++) {
            double re = 0;
            double im = 0;
            for (int k = 0; k <= ORDER; k++) {
                re += a[k] * COS[b][k];
                im -= a[k] * SIN[b][k];
            }
            power[b] = (float) (1 / Math.max(1e-12, re * re + im * im));
        }
        float f1 = 0;
        float p1 = 0;
        float f2 = 0;
        float p2 = 0;
        for (int b = 1; b < BINS - 1; b++) {
            if (power[b] > power[b - 1] && power[b] >= power[b + 1]) {
                float f = F_MIN + b * F_STEP;
                if (f1 == 0) {
                    if (f >= 220 && f <= 1100) {
                        f1 = f;
                        p1 = power[b];
                    }
                } else if (f >= Math.max(650, f1 + 200) && f <= 3000) {
                    f2 = f;
                    p2 = power[b];
                    break;
                }
            }
        }
        return new Result(level, classify(level, f1 / scale, p1, f2 / scale, p2), f1, f2);
    }

    /**
     * How much higher than a man's this voice's formants are, from its pitch: 1 at 120 Hz,
     * about 1.17 at 210 Hz (a woman's voice), at most 1.2.
     */
    private static float voiceScale(double[] x, int n, Scratch scratch) {
        int minLag = RATE / 400;
        int maxLag = Math.min(n / 2, RATE / 70);
        double r0 = 0;
        for (int i = 0; i < n; i++) {
            r0 += x[i] * x[i];
        }
        if (r0 <= 0 || maxLag <= minLag) {
            return 1;
        }
        if (scratch.pitch.length < maxLag + 1) {
            scratch.pitch = new double[maxLag + 1];
        }
        double[] r = scratch.pitch;
        r[minLag - 1] = 0;
        double best = 0;
        for (int lag = minLag; lag <= maxLag; lag++) {
            double sum = 0;
            for (int i = lag; i < n; i++) {
                sum += x[i] * x[i - lag];
            }
            r[lag] = sum / r0;
            best = Math.max(best, r[lag]);
        }
        if (best < 0.3) {
            return 1; // not clearly voiced
        }
        // The shortest period close to the best one, so a multiple of the period is not taken for it.
        for (int lag = minLag; lag <= maxLag; lag++) {
            if (r[lag] >= best * 0.85 && r[lag] >= r[lag - 1] && (lag == maxLag || r[lag] >= r[lag + 1])) {
                float pitch = RATE / (float) lag;
                return Math.max(1, Math.min(1.2F, 1 + (pitch - 120) * 0.0019F));
            }
        }
        return 1;
    }

    /** Mouth shape from the formants (thresholds cover men's and women's voices, French and English vowels). */
    static int classify(float level, float f1, float p1, float f2, float p2) {
        if (f1 == 0) {
            return FaceState.MOUTH_SMALL;
        }
        // Rounded back vowels have almost nothing above 1.5 kHz: a faint "F2" up there is
        // really F3, with F1 and F2 merged into one low peak.
        boolean back = f2 == 0 || f2 < 1150 || (f2 > 1500 && p2 < p1 * 0.003F);
        if (back) {
            return f1 < 430 ? FaceState.MOUTH_U : FaceState.MOUTH_O;
        }
        if (f2 >= 1700) {
            return FaceState.MOUTH_E;
        }
        if (f1 >= 600) {
            return level > 0.62F ? FaceState.MOUTH_WIDE : FaceState.MOUTH_OPEN;
        }
        return f1 >= 470 ? FaceState.MOUTH_OPEN : FaceState.MOUTH_SMALL;
    }
}
