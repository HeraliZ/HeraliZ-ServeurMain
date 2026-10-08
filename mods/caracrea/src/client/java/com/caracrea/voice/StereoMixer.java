package com.caracrea.voice;

/**
 * Stereo mix of the voices for one 20 ms frame (output without OpenAL): each voice is panned left / right and added;
 * {@link #render} writes 16-bit little-endian stereo and clears the mix. A frame where nobody spoke is pure silence —
 * nothing is ever played twice, so a starved output can only go quiet, never loop a sound.
 */
public final class StereoMixer {
    private final float[] left;
    private final float[] right;
    private boolean any;

    public StereoMixer(int frame) {
        left = new float[frame];
        right = new float[frame];
    }

    /** Adds a voice: {@code x, z} its place (x right, z behind), {@code gain} its volume. */
    public void add(short[] pcm, float x, float z, float gain) {
        float horizontal = (float) Math.sqrt(x * x + z * z);
        float pan = horizontal < 0.5F ? 0 : x / horizontal * 0.75F;
        double angle = (pan + 1) * Math.PI / 4;
        float l = (float) (Math.cos(angle) * Math.sqrt(2)) * gain;
        float r = (float) (Math.sin(angle) * Math.sqrt(2)) * gain;
        int n = Math.min(pcm.length, left.length);
        for (int i = 0; i < n; i++) {
            left[i] += pcm[i] * l;
            right[i] += pcm[i] * r;
        }
        any = true;
    }

    /** Whether a voice was added since the last render. */
    public boolean any() {
        return any;
    }

    /** Writes the frame (4 bytes per sample: left, right) and starts a new, silent one. */
    public void render(byte[] out) {
        for (int i = 0; i < left.length; i++) {
            int l = Math.max(-32768, Math.min(32767, Math.round(left[i])));
            int r = Math.max(-32768, Math.min(32767, Math.round(right[i])));
            out[4 * i] = (byte) l;
            out[4 * i + 1] = (byte) (l >> 8);
            out[4 * i + 2] = (byte) r;
            out[4 * i + 3] = (byte) (r >> 8);
            left[i] = 0;
            right[i] = 0;
        }
        any = false;
    }
}
