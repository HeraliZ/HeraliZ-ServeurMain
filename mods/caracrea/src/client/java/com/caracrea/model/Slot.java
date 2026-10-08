package com.caracrea.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Texture space for the detail boxes: the detail atlas is {@link #TEXTURE}
 * texels square, one texel per model unit, painted at 4 pixels per texel.
 * Boxes that are never shown together (the hairstyles, the shoe types...)
 * share a slot, sized for the largest.
 */
public enum Slot {
    /**
     * The head, at twice the texel density of the other slots (texture scale
     * 0.5): its faces get 8 pixels per unit, so the face is painted in high
     * definition. Always at the top-left corner of the atlas.
     */
    HEAD(8.0F, 8.0F, 8.0F, 0.5F),
    HAIR_TOP(7.6F, 0.85F, 7.6F),
    HAIR_RING(8.6F, 0.5F, 8.6F),
    HAIR_FRONT(8.8F, 2.85F, 0.45F),
    HAIR_RIGHT(0.45F, 8.05F, 8.8F),
    HAIR_LEFT(0.45F, 8.05F, 8.8F),
    HAIR_BACK(8.8F, 8.25F, 0.45F),
    HAIR_EXTRA(6.6F, 0.65F, 3.4F),
    HAIR_EXTRA_BACK(6.0F, 0.5F, 3.2F),
    AFRO_CROWN(7.2F, 0.5F, 8.0F),
    AFRO_DOME(9.2F, 0.5F, 9.3F),
    AFRO_TOP(10.4F, 2.5F, 9.9F),
    AFRO_SIDE_RIGHT(1.0F, 3.4F, 8.9F),
    AFRO_SIDE_LEFT(1.0F, 3.4F, 8.9F),
    AFRO_BACK(8.0F, 4.4F, 1.3F),
    CHEST(8.0F, 5.6F, 4.4F),
    WAIST(7.4F, 3.8F, 4.0F),
    HIPS(7.6F, 2.6F, 4.1F),
    LEG_UPPER_RIGHT(4.0F, 6.0F, 4.0F),
    LEG_UPPER_LEFT(4.0F, 6.0F, 4.0F),
    LEG_LOWER_RIGHT(3.6F, 5.2F, 3.6F),
    LEG_LOWER_LEFT(3.6F, 5.2F, 3.6F),
    FOOT_RIGHT(4.3F, 1.2F, 4.5F),
    FOOT_LEFT(4.3F, 1.2F, 4.5F),
    SOLE_RIGHT(4.4F, 0.4F, 4.45F),
    SOLE_LEFT(4.4F, 0.4F, 4.45F),
    BOOT_RIGHT(4.0F, 6.6F, 4.0F),
    BOOT_LEFT(4.0F, 6.6F, 4.0F),
    COLLAR_RIGHT(3.0F, 1.3F, 0.25F),
    COLLAR_LEFT(3.0F, 1.3F, 0.25F),
    HOOD(7.6F, 3.6F, 0.5F),
    POCKET(5.6F, 3.0F, 0.2F),
    BELT(7.9F, 1.15F, 4.4F);

    /** Width of the detail atlas in texels. */
    public static final int TEXTURE = 128;

    public final float maxW;
    public final float maxH;
    public final float maxD;
    /** Minecraft's texture scale for the cubes of this slot: below 1, more pixels per unit. */
    public final float texScale;
    private int u;
    private int v;

    Slot(float maxW, float maxH, float maxD) {
        this(maxW, maxH, maxD, 1.0F);
    }

    Slot(float maxW, float maxH, float maxD, float texScale) {
        this.maxW = maxW;
        this.maxH = maxH;
        this.maxD = maxD;
        this.texScale = texScale;
    }

    /** Width of the unfolded box in atlas texels: two widths and two depths. */
    int footprintW() {
        return (int) Math.ceil((2 * (maxW + maxD)) / texScale) + (texScale < 1 ? 0 : 1);
    }

    int footprintH() {
        return (int) Math.ceil((maxH + maxD) / texScale) + (texScale < 1 ? 0 : 1);
    }

    /** Texel column of this slot in the atlas. */
    public int u() {
        return u;
    }

    public int v() {
        return v;
    }

    /** The texture offset Minecraft expects for cubes of this slot (atlas texels times the texture scale). */
    public int texOffsU() {
        return Math.round(u * texScale);
    }

    public int texOffsV() {
        return Math.round(v * texScale);
    }

    static {
        pack();
    }

    /**
     * The head takes the top-left corner; the other slots are shelf-packed,
     * tallest first, into the strip to its right and then the rest of the
     * atlas. Deterministic, so every client agrees on where each slot lives.
     */
    private static void pack() {
        HEAD.u = 0;
        HEAD.v = 0;
        int[][] regions = {
                {HEAD.footprintW(), 0, TEXTURE - HEAD.footprintW(), HEAD.footprintH()},
                {0, HEAD.footprintH(), TEXTURE, TEXTURE - HEAD.footprintH()}};
        List<Slot> order = new ArrayList<>(List.of(values()));
        order.remove(HEAD);
        order.sort(Comparator.comparingInt(Slot::footprintH).reversed().thenComparing(Slot::footprintW, Comparator.reverseOrder()));
        List<Slot> pending = new ArrayList<>(order);
        for (int[] region : regions) {
            int shelfY = region[1];
            int shelfH = 0;
            int x = region[0];
            for (java.util.Iterator<Slot> it = pending.iterator(); it.hasNext(); ) {
                Slot slot = it.next();
                int w = slot.footprintW();
                int h = slot.footprintH();
                if (x + w > region[0] + region[2]) {
                    shelfY += shelfH;
                    shelfH = 0;
                    x = region[0];
                }
                if (shelfY + h > region[1] + region[3] || w > region[2]) {
                    continue; // try the next region
                }
                slot.u = x;
                slot.v = shelfY;
                x += w;
                shelfH = Math.max(shelfH, h);
                it.remove();
            }
        }
        if (!pending.isEmpty()) {
            throw new IllegalStateException("Detail atlas too small for the slots " + pending);
        }
    }

    /** Debug listing of the packed positions. */
    public static Map<Slot, int[]> positions() {
        Map<Slot, int[]> map = new EnumMap<>(Slot.class);
        for (Slot slot : values()) {
            map.put(slot, new int[]{slot.u, slot.v, slot.footprintW(), slot.footprintH()});
        }
        return map;
    }
}
