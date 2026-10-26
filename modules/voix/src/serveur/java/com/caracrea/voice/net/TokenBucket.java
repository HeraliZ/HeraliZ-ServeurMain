package com.caracrea.voice.net;

/** A rate limit: {@code rate} events per second on average, with bursts up to {@code burst}. Thread-safe. */
public final class TokenBucket {
    private final double rate;
    private final double burst;
    private double tokens;
    private long last = System.nanoTime();

    public TokenBucket(double rate, double burst) {
        this.rate = rate;
        this.burst = burst;
        this.tokens = burst;
    }

    public synchronized boolean take() {
        return take(System.nanoTime());
    }

    public synchronized boolean take(long now) {
        tokens = Math.min(burst, tokens + (now - last) / 1e9 * rate);
        last = now;
        if (tokens < 1) {
            return false;
        }
        tokens--;
        return true;
    }
}
