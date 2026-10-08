package com.caracrea.voice.net;

/**
 * Anti-replay window over the packet counters of one session and direction: a counter is accepted once, and only if
 * it is newer than the latest one or among the 127 before it that have not been seen yet (packets may arrive out of
 * order). {@link #check} before decrypting (cheap), {@link #accept} once the packet is authenticated. Not thread-safe:
 * one receiving thread.
 */
public final class ReplayWindow {
    private long max;
    /** Bit i: counter {@code max - i} seen (i in 0..63, then 64..127 in {@code high}). */
    private long low;
    private long high;

    public boolean check(long counter) {
        if (counter <= 0) {
            return false;
        }
        if (counter > max) {
            return true;
        }
        long behind = max - counter;
        if (behind >= 128) {
            return false;
        }
        return behind < 64 ? (low & (1L << behind)) == 0 : (high & (1L << (behind - 64))) == 0;
    }

    public void accept(long counter) {
        if (counter > max) {
            long shift = counter - max;
            if (shift >= 128) {
                low = 0;
                high = 0;
            } else if (shift >= 64) {
                high = low << (shift - 64);
                low = 0;
            } else {
                high = (high << shift) | (low >>> (64 - shift));
                low <<= shift;
            }
            low |= 1;
            max = counter;
        } else {
            long behind = max - counter;
            if (behind < 64) {
                low |= 1L << behind;
            } else if (behind < 128) {
                high |= 1L << (behind - 64);
            }
        }
    }

    public long latest() {
        return max;
    }
}
