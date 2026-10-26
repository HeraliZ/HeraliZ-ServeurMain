package com.caracrea.paint;

/** Axis-aligned rectangle in canvas pixels; {@code x1}/{@code y1} are exclusive. */
public record Rect(int x, int y, int w, int h) {
    public static final Rect EMPTY = new Rect(0, 0, 0, 0);

    public int x1() {
        return x + w;
    }

    public int y1() {
        return y + h;
    }

    public boolean isEmpty() {
        return w <= 0 || h <= 0;
    }

    public boolean contains(int px, int py) {
        return px >= x && py >= y && px < x + w && py < y + h;
    }

    public Rect intersect(Rect other) {
        int nx = Math.max(x, other.x);
        int ny = Math.max(y, other.y);
        int nx1 = Math.min(x1(), other.x1());
        int ny1 = Math.min(y1(), other.y1());
        return nx1 <= nx || ny1 <= ny ? EMPTY : new Rect(nx, ny, nx1 - nx, ny1 - ny);
    }

    public Rect union(Rect other) {
        if (isEmpty()) {
            return other;
        }
        if (other.isEmpty()) {
            return this;
        }
        int nx = Math.min(x, other.x);
        int ny = Math.min(y, other.y);
        return new Rect(nx, ny, Math.max(x1(), other.x1()) - nx, Math.max(y1(), other.y1()) - ny);
    }

    /** Sub-rectangle given in local coordinates (clamped to this rectangle). */
    public Rect sub(int lx, int ly, int lw, int lh) {
        return intersect(new Rect(x + lx, y + ly, lw, lh));
    }
}
