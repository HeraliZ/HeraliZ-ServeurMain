package fr.heraliz.crac.build.geom;

import java.math.BigInteger;
import fr.heraliz.crac.build.model.Dirs;

/**
 * Exact arithmetic in Z[sqrt3] on pairs of longs: {@code (p, q)} is the number {@code p + q * sqrt3}. Lattice
 * predicates (overlap, crossing, sign tests) use it so that they never depend on rounding.
 */
public final class Q3 {
    private static final long SAFE_P = 1L << 31;
    private static final long SAFE_Q = 1L << 30;

    private Q3() {
    }

    /** Rational part of {@code (a + b sqrt3) + (c + d sqrt3)}. */
    public static long addP(long a, long b, long c, long d) {
        return Math.addExact(a, c);
    }

    /** Irrational part of the sum. */
    public static long addQ(long a, long b, long c, long d) {
        return Math.addExact(b, d);
    }

    /** {@code (a + b sqrt3) + (c + d sqrt3)} as {p, q}. */
    public static long[] add(long a, long b, long c, long d) {
        return new long[]{addP(a, b, c, d), addQ(a, b, c, d)};
    }

    /** Rational part of {@code (a + b sqrt3)(c + d sqrt3) = ac + 3bd + (ad + bc) sqrt3}. */
    public static long mulP(long a, long b, long c, long d) {
        return Math.addExact(Math.multiplyExact(a, c), Math.multiplyExact(3L, Math.multiplyExact(b, d)));
    }

    /** Irrational part of the product. */
    public static long mulQ(long a, long b, long c, long d) {
        return Math.addExact(Math.multiplyExact(a, d), Math.multiplyExact(b, c));
    }

    /** {@code (a + b sqrt3)(c + d sqrt3)} as {p, q}. */
    public static long[] mul(long a, long b, long c, long d) {
        return new long[]{mulP(a, b, c, d), mulQ(a, b, c, d)};
    }

    /**
     * Sign of {@code p + q sqrt3}: when p and q agree (or one is 0) it is their sign, otherwise the sign of the one
     * with the larger magnitude, comparing {@code p^2} with {@code 3 q^2} (never equal: sqrt3 is irrational).
     */
    public static int sign(long p, long q) {
        if (p >= 0 && q >= 0) {
            return (p | q) == 0 ? 0 : 1;
        }
        if (p <= 0 && q <= 0) {
            return -1;
        }
        int pBigger;
        if (Math.abs(p) < SAFE_P && Math.abs(q) < SAFE_Q) {
            pBigger = Long.compare(p * p, 3 * q * q);
        } else {
            BigInteger bp = BigInteger.valueOf(p);
            BigInteger bq = BigInteger.valueOf(q);
            pBigger = bp.multiply(bp).compareTo(bq.multiply(bq).multiply(BigInteger.valueOf(3)));
        }
        return pBigger > 0 ? Long.signum(p) : Long.signum(q);
    }

    /** Compares {@code p1 + q1 sqrt3} with {@code p2 + q2 sqrt3}. */
    public static int compare(long p1, long q1, long p2, long q2) {
        return sign(Math.subtractExact(p1, p2), Math.subtractExact(q1, q2));
    }

    public static double toDouble(long p, long q) {
        return p + q * Dirs.SQRT3;
    }
}
