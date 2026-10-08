package com.dayzhotbar.client;

/**
 * The chalk drawings of the hotbar, one cell each: the frame and the smudged
 * background of every slot (each drawn differently, as by hand), the frame of
 * the selected slot and the blood on it (see {@link Blood}). The slant is drawn into the cells, so
 * they are blitted straight, one texel per screen pixel.
 */
final class SlotArt {
    static final int SLOT = 20;
    /** Room around the slot for the overshooting chalk strokes and the blood droplets. */
    static final int MARGIN = 4;
    /** Frame and background of slot i (0 to 8, 9 is the offhand). */
    static final int FRAMES = 0;
    static final int BACKGROUNDS = 10;
    static final int SELECTED = 20;
    static final int BLOOD = 21;
    static final int BLOOD_RIM = 22;
    static final int BLOOD_SHINE = 23;
    static final int CELLS = 24;

    final float scale;
    final float k;
    /** Where the slot's unslanted rectangle starts in a cell, in GUI pixels. */
    final int slotX;
    final int cellWidth;
    final int cellHeight;

    SlotArt(float scale, float k) {
        this.scale = scale;
        this.k = k;
        this.slotX = MARGIN + (int) Math.ceil(k * SLOT / 2);
        this.cellWidth = slotX * 2 + SLOT;
        this.cellHeight = MARGIN * 2 + SLOT;
    }

    int cellPixelsX() {
        return Math.round(cellWidth * scale);
    }

    int cellPixelsY() {
        return Math.round(cellHeight * scale);
    }

    Chalk paint(int cell) {
        Chalk chalk = new Chalk(cellPixelsX(), cellPixelsY(), scale);
        // Corners of the slanted slot: top left, top right, bottom right, bottom left.
        float lean = k * SLOT / 2;
        float[] xs = {slotX + lean, slotX + SLOT + lean, slotX + SLOT - lean, slotX - lean};
        float[] ys = {MARGIN, MARGIN, MARGIN + SLOT, MARGIN + SLOT};
        if (cell < BACKGROUNDS) {
            int seed = 1000 + cell * 31;
            chalk.line(xs[0], ys[0], xs[1], ys[1], 1.4f, 0.8f, seed);
            chalk.line(xs[3], ys[3], xs[2], ys[2], 1.4f, 0.8f, seed + 7);
            chalk.line(xs[3], ys[3], xs[0], ys[0], 1.4f, 0.8f, seed + 13);
            chalk.line(xs[2], ys[2], xs[1], ys[1], 1.4f, 0.8f, seed + 19);
        } else if (cell < SELECTED) {
            float in = 0.4f;
            chalk.fill(new float[]{xs[0] + in, xs[1] - in, xs[2] - in, xs[3] + in},
                    new float[]{ys[0] + in, ys[1] + in, ys[2] - in, ys[3] - in}, 1, 2000 + cell * 17);
        } else if (cell == SELECTED) {
            chalk.line(xs[0], ys[0], xs[1], ys[1], 2f, 1.05f, 3001);
            chalk.line(xs[3], ys[3], xs[2], ys[2], 2f, 1.05f, 3004);
            chalk.line(xs[3], ys[3], xs[0], ys[0], 2f, 1.05f, 3002);
            chalk.line(xs[2], ys[2], xs[1], ys[1], 2f, 1.05f, 3003);
        } else {
            Blood blood = new Blood(xs, ys);
            if (cell == BLOOD) {
                blood.body(chalk);
            } else if (cell == BLOOD_RIM) {
                blood.rim(chalk);
            } else {
                blood.shine(chalk);
            }
        }
        return chalk;
    }
}
