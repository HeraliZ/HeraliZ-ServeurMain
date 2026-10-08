package com.caracrea.voice;

/**
 * Cleans up the microphone before it is sent, one 20 ms frame at a time, in plain Java (no native library):
 *
 * <ol>
 *   <li><b>High-pass</b> at 90 Hz (2nd-order Butterworth): desk thumps, rumble, DC offset.</li>
 *   <li><b>Noise suppression</b>: OM-LSA (Cohen &amp; Berdugo, 2001) — the optimally-modified log-spectral amplitude
 *   estimator of Ephraim &amp; Malah weighted by the probability that speech is present in each frequency band — with
 *   the noise followed by MCRA (minima-controlled recursive averaging, Cohen 2002) and the a priori SNR estimated
 *   "decision-directed". 1024-point FFT, 20 ms windows overlapping by half (square-root Hann), so 10 ms per step.
 *   Steady noise (fan, computer, air conditioning, road, hiss) goes away while the voice stays natural; the strength
 *   sets how far the noise is pushed down (−12, −18 or −26 dB).</li>
 *   <li><b>Automatic gain</b>: a quiet microphone is brought up and a loud one down, towards the same speech level for
 *   everyone, with a limiter so it never clips.</li>
 *   <li><b>Voice detection</b>: energy above the background the microphone hears, or the suppressor's speech
 *   probability in the voice band, for automatic voice activation.</li>
 * </ol>
 *
 * About 0.1 ms per frame, nothing allocated. Not thread-safe: one per microphone.
 */
public final class VoiceProcessor {
    private static final int FRAME = VoiceCodec.FRAME;
    private static final int HOP = FRAME / 2;
    private static final int FFT = 1024;
    private static final int BINS = FFT / 2 + 1;
    private static final float[] WINDOW = new float[FRAME];
    /** Voice band used by the detection (300 to 3400 Hz). */
    private static final int VOICE_LOW = 300 * FFT / VoiceCodec.SAMPLE_RATE;
    private static final int VOICE_HIGH = 3400 * FFT / VoiceCodec.SAMPLE_RATE;
    /** Strength 1 to 3: lowest gain applied to the noise. */
    private static final float[] FLOOR = {1F, 0.25F, 0.126F, 0.05F};

    // MCRA (noise tracking).
    private static final float ALPHA_S = 0.8F;
    private static final float ALPHA_P = 0.2F;
    private static final float ALPHA_D = 0.95F;
    private static final float DELTA = 5F;
    private static final int MINIMA_WINDOW = 100;
    // OM-LSA.
    private static final float ALPHA_DD = 0.95F;
    private static final float XI_MIN = 0.003162F; // -25 dB
    private static final float BETA_Z = 0.7F;
    private static final float ZETA_MIN = 0.1F; // -10 dB
    private static final float ZETA_MAX = 0.3162F; // -5 dB
    private static final float Q_MAX = 0.95F;
    private static final int GLOBAL_HALF = 15;

    static {
        // Square root of a periodic Hann window, for analysis and synthesis: overlapping halves add up to 1.
        for (int i = 0; i < FRAME; i++) {
            WINDOW[i] = (float) Math.sqrt(0.5 - 0.5 * Math.cos(2 * Math.PI * i / FRAME));
        }
    }

    // High-pass biquad (transposed direct form II).
    private static final float HP_B0;
    private static final float HP_B1;
    private static final float HP_B2;
    private static final float HP_A1;
    private static final float HP_A2;

    static {
        double w = 2 * Math.PI * 90 / VoiceCodec.SAMPLE_RATE;
        double alpha = Math.sin(w) / (2 * Math.sqrt(0.5));
        double cos = Math.cos(w);
        double a0 = 1 + alpha;
        HP_B0 = (float) ((1 + cos) / 2 / a0);
        HP_B1 = (float) (-(1 + cos) / a0);
        HP_B2 = (float) ((1 + cos) / 2 / a0);
        HP_A1 = (float) (-2 * cos / a0);
        HP_A2 = (float) ((1 - alpha) / a0);
    }

    private float hp1;
    private float hp2;

    // Spectral state.
    private final float[] input = new float[FRAME];
    private final float[] overlap = new float[HOP];
    private final float[] re = new float[FFT];
    private final float[] im = new float[FFT];
    private final float[] output = new float[FRAME];
    private final float[] samples = new float[FRAME];
    private final float[] power = new float[BINS];
    private final float[] noise = new float[BINS];
    private final float[] smooth = new float[BINS];
    private final float[] minimum = new float[BINS];
    private final float[] minimumTemp = new float[BINS];
    private final float[] presence = new float[BINS];
    private final float[] lastClean = new float[BINS];
    private final float[] zeta = new float[BINS];
    private final float[] xiNow = new float[BINS];
    private final float[] gain = new float[BINS];
    private int hops;
    private float zetaPeak = 1;
    private float lastZetaFrame;
    private float speechProbability;

    // Automatic gain and voice detection state.
    private float agcDb;
    private float floorDb = -60;
    private int floorFrames;
    private boolean voice;
    private float levelDb = -100;

    /** Processes one frame in place, with the default strength (normal). */
    public void process(short[] pcm, boolean denoise, boolean automaticGain) {
        process(pcm, denoise ? 2 : 0, automaticGain);
    }

    /**
     * Processes one frame in place.
     *
     * @param strength      noise suppression: 0 off, 1 light, 2 normal, 3 strong
     * @param automaticGain bring the voice to a steady level
     */
    public void process(short[] pcm, int strength, boolean automaticGain) {
        for (int i = 0; i < FRAME; i++) {
            float x = pcm[i];
            float y = HP_B0 * x + hp1;
            hp1 = HP_B1 * x - HP_A1 * y + hp2;
            hp2 = HP_B2 * x - HP_A2 * y;
            samples[i] = y;
        }
        if (strength > 0) {
            float floor = FLOOR[Math.min(3, strength)];
            for (int half = 0; half < 2; half++) {
                System.arraycopy(input, HOP, input, 0, HOP);
                System.arraycopy(samples, half * HOP, input, HOP, HOP);
                hop(floor);
                System.arraycopy(output, 0, samples, half * HOP, HOP);
            }
        } else {
            hops = 0; // start over when turned back on
            speechProbability = 0;
        }
        for (int i = 0; i < FRAME; i++) {
            pcm[i] = (short) Math.max(-32768, Math.min(32767, Math.round(samples[i])));
        }
        float db = levelDb(pcm);
        detectVoice(db, strength > 0);
        if (automaticGain) {
            automaticGain(pcm, db);
        }
        levelDb = levelDb(pcm);
    }

    /** The same 90 Hz high-pass on a whole signal (to build a fair reference when measuring the suppressor). */
    public static float[] highPass(float[] in) {
        float[] out = new float[in.length];
        float z1 = 0;
        float z2 = 0;
        for (int i = 0; i < in.length; i++) {
            float x = in[i];
            float y = HP_B0 * x + z1;
            z1 = HP_B1 * x - HP_A1 * y + z2;
            z2 = HP_B2 * x - HP_A2 * y;
            out[i] = y;
        }
        return out;
    }

    public boolean voice() {
        return voice;
    }

    /** Loudness of the latest frame in dBFS, after processing. */
    public float levelDb() {
        return levelDb;
    }

    /** The background level the microphone hears, in dBFS. */
    public float floorDb() {
        return floorDb;
    }

    /** Probability (0..1) that the latest frame holds speech, from the noise suppressor (0 when it is off). */
    public float speechProbability() {
        return speechProbability;
    }

    // ------------------------------------------------------------ noise suppression

    private void hop(float floor) {
        for (int i = 0; i < FFT; i++) {
            re[i] = i < FRAME ? input[i] * WINDOW[i] : 0;
            im[i] = 0;
        }
        Fft.transform(re, im, false);
        hops++;
        for (int k = 0; k < BINS; k++) {
            power[k] = Math.max(1e-3F, re[k] * re[k] + im[k] * im[k]);
        }
        if (hops <= 10) {
            // The first 100 ms: starting estimate of the background.
            for (int k = 0; k < BINS; k++) {
                noise[k] = hops == 1 ? power[k] : noise[k] + (power[k] - noise[k]) / hops;
                smooth[k] = power[k];
                minimum[k] = power[k];
                minimumTemp[k] = power[k];
                presence[k] = 0;
                lastClean[k] = power[k] * FLOOR[2] * FLOOR[2];
                zeta[k] = 0;
            }
        } else {
            trackNoise();
        }
        estimateGains(floor);
        for (int k = 0; k < BINS; k++) {
            re[k] *= gain[k];
            im[k] *= gain[k];
            if (k > 0 && k < FFT / 2) {
                re[FFT - k] = re[k];
                im[FFT - k] = -im[k];
            }
        }
        Fft.transform(re, im, true);
        // Synthesis window and overlap-add: 10 ms out per 10 ms in.
        for (int i = 0; i < HOP; i++) {
            output[i] = overlap[i] + re[i] * WINDOW[i];
            overlap[i] = re[HOP + i] * WINDOW[HOP + i];
        }
    }

    /** MCRA: smoothed power, its running minimum, speech presence per band, and the noise updated where speech is absent. */
    private void trackNoise() {
        boolean renew = hops % MINIMA_WINDOW == 0;
        for (int k = 0; k < BINS; k++) {
            float left = k > 0 ? power[k - 1] : power[k];
            float right = k < BINS - 1 ? power[k + 1] : power[k];
            float local = 0.25F * left + 0.5F * power[k] + 0.25F * right;
            float s = ALPHA_S * smooth[k] + (1 - ALPHA_S) * local;
            smooth[k] = s;
            if (renew) {
                minimum[k] = Math.min(minimumTemp[k], s);
                minimumTemp[k] = s;
            } else {
                minimum[k] = Math.min(minimum[k], s);
                minimumTemp[k] = Math.min(minimumTemp[k], s);
            }
            float speech = s > DELTA * minimum[k] ? 1 : 0;
            presence[k] = ALPHA_P * presence[k] + (1 - ALPHA_P) * speech;
            float alpha = ALPHA_D + (1 - ALPHA_D) * presence[k];
            noise[k] = Math.max(1e-3F, alpha * noise[k] + (1 - alpha) * power[k]);
        }
    }

    /** OM-LSA gains: LSA gain under speech presence, weighted by its probability, towards {@code floor} in its absence. */
    private void estimateGains(float floor) {
        double lnFloor = Math.log(floor);
        float globalSum = 0;
        // A priori SNR (decision-directed) and its recursive average for the speech absence probability.
        float zetaFrame = 0;
        for (int k = 0; k < BINS; k++) {
            float gamma = power[k] / noise[k];
            float xi = ALPHA_DD * lastClean[k] / noise[k] + (1 - ALPHA_DD) * Math.max(gamma - 1, 0);
            xi = Math.max(XI_MIN, xi);
            xiNow[k] = xi;
            zeta[k] = BETA_Z * zeta[k] + (1 - BETA_Z) * xi;
            if (k >= VOICE_LOW && k <= VOICE_HIGH) {
                zetaFrame += zeta[k];
            }
        }
        zetaFrame /= VOICE_HIGH - VOICE_LOW + 1;
        float frameProbability;
        if (zetaFrame > ZETA_MIN) {
            if (zetaFrame > lastZetaFrame) {
                zetaPeak = Math.max(1, Math.min(10, zetaFrame));
                frameProbability = 1;
            } else {
                frameProbability = mapProbability(zetaFrame, zetaPeak * ZETA_MIN, zetaPeak * ZETA_MAX);
            }
        } else {
            frameProbability = 0;
        }
        lastZetaFrame = zetaFrame;
        float voiceSum = 0;
        for (int k = 0; k < BINS; k++) {
            float local = 0.25F * zeta[Math.max(0, k - 1)] + 0.5F * zeta[k] + 0.25F * zeta[Math.min(BINS - 1, k + 1)];
            // Wide average (31 bands) as a running sum.
            int in = Math.min(BINS - 1, k + GLOBAL_HALF);
            int out = k - GLOBAL_HALF - 1;
            if (k == 0) {
                globalSum = 0;
                for (int j = -GLOBAL_HALF; j <= GLOBAL_HALF; j++) {
                    globalSum += zeta[Math.max(0, Math.min(BINS - 1, j))];
                }
            } else {
                globalSum += zeta[in] - zeta[Math.max(0, out)];
            }
            float global = globalSum / (2 * GLOBAL_HALF + 1);
            float q = 1 - mapProbability(local, ZETA_MIN, ZETA_MAX) * mapProbability(global, ZETA_MIN, ZETA_MAX) * frameProbability;
            q = Math.min(Q_MAX, q);
            float xi = xiNow[k];
            float gamma = power[k] / noise[k];
            float v = gamma * xi / (1 + xi);
            float expMinusV = (float) Math.exp(-v);
            // Conditional probability that speech is present in this band.
            float p = q <= 0 ? 1 : 1 / (1 + q / (1 - q) * (1 + xi) * expMinusV);
            double lnLsa = Math.min(0, Math.log(xi / (1 + xi)) + 0.5 * expIntegral(v, expMinusV));
            // G = G_lsa^p x G_min^(1-p), computed in logarithms.
            gain[k] = (float) Math.exp(p * lnLsa + (1 - p) * lnFloor);
            lastClean[k] = (float) Math.exp(2 * lnLsa) * power[k];
            if (k >= VOICE_LOW && k <= VOICE_HIGH) {
                voiceSum += p;
            }
        }
        speechProbability = voiceSum / (VOICE_HIGH - VOICE_LOW + 1);
    }

    private static float mapProbability(float value, float low, float high) {
        if (value <= low) {
            return 0;
        }
        if (value >= high) {
            return 1;
        }
        return (float) (Math.log(value / low) / Math.log(high / low));
    }

    /** Exponential integral E1(v) (Abramowitz &amp; Stegun 5.1.11 and 5.1.56), {@code expMinusV} = e^-v. */
    static double expIntegral(double v, double expMinusV) {
        if (v < 1e-6) {
            v = 1e-6;
        }
        if (v < 1) {
            return -Math.log(v) - 0.5772156649 + v - v * v / 4 + v * v * v / 18 - v * v * v * v / 96 + v * v * v * v * v / 600;
        }
        return expMinusV / v * (v * v + 2.334733 * v + 0.250621) / (v * v + 3.330657 * v + 1.681534);
    }

    // ------------------------------------------------------------ level

    private static float levelDb(short[] pcm) {
        double sum = 0;
        for (short s : pcm) {
            sum += (double) s * s;
        }
        double rms = Math.sqrt(sum / pcm.length) / 32768.0;
        return rms <= 1e-6 ? -100 : (float) (20 * Math.log10(rms));
    }

    private void detectVoice(float db, boolean probability) {
        // Background level: follows the quiet moments down at once, and rises slowly (1 dB a second).
        if (floorFrames++ == 0 || db < floorDb) {
            floorDb = db;
        } else {
            floorDb += 0.02F;
        }
        floorDb = Math.max(-90, Math.min(-20, floorDb));
        boolean loud = db > Math.max(floorDb + 9, -55);
        boolean speech = probability && speechProbability > 0.55F && db > Math.max(floorDb + 3, -62);
        voice = loud || speech;
    }

    private void automaticGain(short[] pcm, float db) {
        if (voice) {
            // Towards -20 dBFS of speech; quick to come down, slower to go up.
            float error = -20 - (db + agcDb);
            agcDb += Math.max(-1.5F, Math.min(0.3F, error * 0.08F));
            agcDb = Math.max(-12, Math.min(24, agcDb));
        }
        float factor = (float) Math.pow(10, agcDb / 20);
        int peak = 0;
        for (short s : pcm) {
            peak = Math.max(peak, Math.abs((int) s));
        }
        // Limiter: never past 90 % of full scale.
        if (peak * factor > 29_500) {
            factor = 29_500F / Math.max(1, peak);
        }
        for (int i = 0; i < pcm.length; i++) {
            pcm[i] = (short) Math.max(-32768, Math.min(32767, Math.round(pcm[i] * factor)));
        }
    }

    /** In-place radix-2 FFT of size 1024. */
    static final class Fft {
        private static final float[] COS = new float[FFT / 2];
        private static final float[] SIN = new float[FFT / 2];
        private static final int[] REVERSE = new int[FFT];

        static {
            for (int i = 0; i < FFT / 2; i++) {
                COS[i] = (float) Math.cos(2 * Math.PI * i / FFT);
                SIN[i] = (float) Math.sin(2 * Math.PI * i / FFT);
            }
            int bits = Integer.numberOfTrailingZeros(FFT);
            for (int i = 0; i < FFT; i++) {
                REVERSE[i] = Integer.reverse(i) >>> (32 - bits);
            }
        }

        private Fft() {
        }

        static void transform(float[] re, float[] im, boolean inverse) {
            for (int i = 0; i < FFT; i++) {
                int j = REVERSE[i];
                if (j > i) {
                    float t = re[i];
                    re[i] = re[j];
                    re[j] = t;
                    t = im[i];
                    im[i] = im[j];
                    im[j] = t;
                }
            }
            for (int size = 2; size <= FFT; size <<= 1) {
                int half = size >> 1;
                int step = FFT / size;
                for (int start = 0; start < FFT; start += size) {
                    for (int k = 0; k < half; k++) {
                        float c = COS[k * step];
                        float s = inverse ? SIN[k * step] : -SIN[k * step];
                        int a = start + k;
                        int b = a + half;
                        float tr = re[b] * c - im[b] * s;
                        float ti = re[b] * s + im[b] * c;
                        re[b] = re[a] - tr;
                        im[b] = im[a] - ti;
                        re[a] += tr;
                        im[a] += ti;
                    }
                }
            }
            if (inverse) {
                float scale = 1F / FFT;
                for (int i = 0; i < FFT; i++) {
                    re[i] *= scale;
                    im[i] *= scale;
                }
            }
        }
    }
}
