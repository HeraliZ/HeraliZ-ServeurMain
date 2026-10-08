package com.caracrea.paint;

import com.caracrea.character.Appearance;
import com.caracrea.character.BodyType;
import com.caracrea.character.Catalog;
import com.caracrea.character.HairStyle;
import com.caracrea.character.Trait;
import com.caracrea.model.Bone;
import com.caracrea.model.BoxSpec;
import com.caracrea.model.CharacterModelSpec;
import com.caracrea.model.Material;
import com.caracrea.paint.SkinLayout.Face;

/**
 * Paints the faces of the 3D detail boxes into the detail atlas. Each
 * face is painted from a material shader that knows where the pixel sits
 * on the box (and on the body), so shirts, trousers and hair stay
 * continuous across the boxes that make up a limb.
 */
final class DetailPainter {
    private static final int BUCKLE = Colors.rgb(196, 176, 124);
    private static final int BELT = Colors.rgb(58, 40, 26);
    private static final int LACE = Colors.rgb(228, 226, 220);

    private DetailPainter() {
    }

    static void paint(Canvas canvas, Appearance a) {
        Context ctx = new Context(a);
        for (BoxSpec box : CharacterModelSpec.visible(a)) {
            if (box.material() == Material.HEAD) {
                continue; // the head is painted in high definition by SkinPainter
            }
            for (Face face : Face.VALUES) {
                paintFace(canvas, ctx, box, face);
            }
        }
    }

    /** Everything the shaders need about the character. */
    private static final class Context {
        final Appearance a;
        final long seed;
        final long hairSeed;
        final long skinSeed;
        final java.util.List<ScarPainter.Line> scars;
        final int skin;
        final int hair;
        final int shirt;
        final int shirt2;
        final int pants;
        final int shoes;
        final String hairId;
        final HairStyle hairStyle;
        final String shirtId;
        final String pantsId;
        final String shoesId;
        final int wear;
        final float sleeveLength;
        final float shirtBottom;
        final float pantsBottom;

        Context(Appearance a) {
            this.a = a;
            this.seed = a.seed(Trait.SHIRT_STYLE, Trait.PANTS_STYLE, Trait.SHOES_STYLE, Trait.WEAR) + 500;
            this.hairSeed = a.seed(Trait.HAIR_STYLE) + 510;
            this.skinSeed = a.seed(Trait.BODY_TYPE) + 520;
            this.scars = ScarPainter.lines(a);
            this.skin = BodyPainter.skinColor(a);
            this.hair = Catalog.swatch(a, Trait.HAIR_COLOR).argb();
            this.shirt = Catalog.swatch(a, Trait.SHIRT_COLOR).argb();
            this.shirt2 = Catalog.swatch(a, Trait.SHIRT_COLOR2).argb();
            this.pants = Catalog.swatch(a, Trait.PANTS_COLOR).argb();
            this.shoes = Catalog.swatch(a, Trait.SHOES_COLOR).argb();
            this.hairStyle = Catalog.hair(a);
            this.hairId = hairStyle.id();
            this.shirtId = Catalog.optionId(a, Trait.SHIRT_STYLE);
            this.pantsId = Catalog.optionId(a, Trait.PANTS_STYLE);
            this.shoesId = Catalog.optionId(a, Trait.SHOES_STYLE);
            this.wear = a.get(Trait.WEAR);
            this.sleeveLength = switch (shirtId) {
                case "tank_top" -> -3;
                case "crop_top" -> 3.25F;
                case "tshirt", "vneck", "striped", "polo", "camo" -> 4.25F;
                case "flannel" -> 8.0F;
                default -> 10.0F;
            };
            this.shirtBottom = shirtId.equals("crop_top") ? 7.5F : 12.5F;
            this.pantsBottom = switch (pantsId) {
                case "shorts" -> 6.0F;
                case "skirt" -> -1;
                case "joggers", "track" -> 11.5F;
                default -> 12.5F;
            };
        }
    }

    /** A pixel of a face: position on the face and inside the box, in model units. */
    @FunctionalInterface
    private interface FaceShader {
        int shade(float fx, float fy, float lx, float ly, float lz, int px, int py);
    }

    // ------------------------------------------------------------- geometry

    /** Texel rectangle of a face of an unfolded box, in texels (floats). */
    private static float[] faceBounds(BoxSpec box, Face face) {
        float u = box.u();
        float v = box.v();
        float w = box.texW();
        float h = box.texH();
        float d = box.texD();
        return switch (face) {
            case RIGHT -> new float[]{u, v + d, u + d, v + d + h};
            case FRONT -> new float[]{u + d, v + d, u + d + w, v + d + h};
            case LEFT -> new float[]{u + d + w, v + d, u + 2 * d + w, v + d + h};
            case BACK -> new float[]{u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h};
            case TOP -> new float[]{u + d, v, u + d + w, v + d};
            case BOTTOM -> new float[]{u + d + w, v, u + d + 2 * w, v + d};
        };
    }

    private static void paintFace(Canvas canvas, Context ctx, BoxSpec box, Face face) {
        float[] b = faceBounds(box, face);
        int x0 = (int) Math.floor(b[0] * Canvas.SCALE);
        int y0 = (int) Math.floor(b[1] * Canvas.SCALE);
        int x1 = (int) Math.ceil(b[2] * Canvas.SCALE);
        int y1 = (int) Math.ceil(b[3] * Canvas.SCALE);
        if (x1 <= x0 || y1 <= y0) {
            return;
        }
        float faceW = b[2] - b[0];
        float faceH = b[3] - b[1];
        // Texture units to real units on this face (the texture is rounded to whole pixels).
        float kx = box.w() / box.texW();
        float ky = box.h() / box.texH();
        float kz = box.d() / box.texD();
        float kh = face == Face.RIGHT || face == Face.LEFT ? kz : kx;
        float kv = face == Face.TOP || face == Face.BOTTOM ? kz : ky;
        FaceShader shader = shader(ctx, box, face);
        float light = switch (face) {
            case TOP -> 1.03F;
            case BOTTOM -> 0.86F;
            default -> 1.0F;
        };
        Rect rect = new Rect(x0, y0, x1 - x0, y1 - y0);
        canvas.shade(rect, (px, py, current) -> {
            float fx = Math.min(faceW, Math.max(0, (px + 0.5F) / Canvas.SCALE - b[0])) * kh;
            float fy = Math.min(faceH, Math.max(0, (py + 0.5F) / Canvas.SCALE - b[1])) * kv;
            // Where this pixel sits inside the box.
            float lx;
            float ly;
            float lz;
            switch (face) {
                case FRONT -> { lx = fx; ly = fy; lz = 0; }
                case BACK -> { lx = box.w() - fx; ly = fy; lz = box.d(); }
                case RIGHT -> { lx = 0; ly = fy; lz = box.d() - fx; }
                case LEFT -> { lx = box.w(); ly = fy; lz = fx; }
                case TOP -> { lx = fx; ly = 0; lz = box.d() - fy; }
                default -> { lx = fx; ly = box.h(); lz = box.d() - fy; }
            }
            int color = shader.shade(fx, fy, lx, ly, lz, px, py);
            return Colors.alpha(color) == 0 ? 0 : Colors.shade(color, light);
        });
    }

    // ------------------------------------------------------------- materials

    private static FaceShader shader(Context ctx, BoxSpec box, Face face) {
        return switch (box.material()) {
            case HEAD -> (fx, fy, lx, ly, lz, px, py) -> 0;
            case SKIN -> (fx, fy, lx, ly, lz, px, py) -> skin(ctx, box, face, lx, ly, lz, px, py);
            case HAIR -> (fx, fy, lx, ly, lz, px, py) -> hair(ctx, box, face, fx, fy, lx, ly, lz, px, py);
            case SHIRT -> (fx, fy, lx, ly, lz, px, py) -> shirt(ctx, box, face, lx, ly, lz, px, py);
            case SHIRT_TRIM -> (fx, fy, lx, ly, lz, px, py) -> trim(ctx, box, face, lx, ly, lz, px, py);
            case PANTS -> (fx, fy, lx, ly, lz, px, py) -> pants(ctx, box, face, lx, ly, lz, px, py);
            case SHOE -> (fx, fy, lx, ly, lz, px, py) -> shoe(ctx, box, face, lx, ly, lz, px, py);
            case SOLE -> (fx, fy, lx, ly, lz, px, py) -> sole(ctx, box, face, lx, ly, lz, px, py);
            case BELT -> (fx, fy, lx, ly, lz, px, py) -> belt(ctx, box, face, lx, ly, lz, px, py);
            case SKIRT -> (fx, fy, lx, ly, lz, px, py) -> skirt(ctx, box, face, lx, ly, lz, px, py);
        };
    }

    /** Bare skin at a pixel of a box face, with the scars that cross it (front and back faces). */
    private static int skinAt(Context ctx, BoxSpec box, Face face, float lx, float ly, int px, int py) {
        int color = grain(ctx.skin, px, py, ctx.skinSeed);
        if (ctx.scars.isEmpty() || (face != Face.FRONT && face != Face.BACK)) {
            return color;
        }
        com.caracrea.character.Scar.Part part = com.caracrea.character.Scar.Part.valueOf(box.bone().name());
        return ScarPainter.apply(color, ctx.skin, ctx.scars, part, face == Face.BACK, box.x() + lx, box.y() + ly);
    }

    private static int grain(int color, int px, int py, long seed) {
        return Colors.shade(color, 1 + 0.016F * (Noise.fbm(px, py, 6, seed) - 0.5F) * 2 + 0.01F * Noise.signed(px, py, seed + 1));
    }

    // ------------------------------------------------------------------ skin

    private static int skin(Context ctx, BoxSpec box, Face face, float lx, float ly, float lz, int px, int py) {
        int color = skinAt(ctx, box, face, lx, ly, px, py);
        if (box.name().startsWith("hand")) {
            // Faint finger lines on the back of the hand (its outer side), towards the fingertips.
            boolean outer = box.name().equals("hand_right") ? face == Face.RIGHT : face == Face.LEFT;
            if (outer && ly > box.h() * 0.45F) {
                float t = lz / box.d() * 4;
                float frac = t - (float) Math.floor(t);
                if (Math.abs(frac - 0.5F) > 0.44F && t > 0.6F && t < 3.4F) {
                    color = Colors.shade(color, 0.93F);
                }
            }
            if (face == Face.BOTTOM) {
                color = Colors.shade(color, 0.94F);
            }
        }
        return color;
    }

    // ------------------------------------------------------------------ hair

    private static int hair(Context ctx, BoxSpec box, Face face, float fx, float fy, float lx, float ly, float lz, int px, int py) {
        String name = box.name();
        long seed = ctx.hairSeed;
        HairStyle.Texture texture = ctx.hairStyle.texture();
        // Tie of the ponytail: a dark band.
        if (name.equals("hair_tie")) {
            return Colors.shade(ctx.hair, 0.55F + 0.06F * Noise.signed(px, py, seed));
        }
        // Coverage: 1 inside the hair, 0 where the box is cut away (parting, feathered tips, dread gaps).
        float cover = hairCover(ctx, box, face, fx, fy, lx, ly, lz, seed);
        if (cover <= 0) {
            return 0;
        }
        // Strand lightness: streaks along the direction the hair falls. Coordinates are taken
        // in head space (centred across the face, absolute height), so the streaks run on
        // without a seam where the cap meets the fringe or the sides.
        float across = face == Face.FRONT || face == Face.BACK ? box.x() + lx : box.z() + lz;
        float along = across * 4;
        float down = (face == Face.TOP || face == Face.BOTTOM ? box.x() + lx : box.y() + ly) * 4;
        int gx = (int) Math.floor(along);
        int gy = (int) Math.floor(down);
        // Seen from above, combed hair shows softer lines than on the sides.
        float streak = face == Face.TOP ? 0.5F : 1;
        float light;
        switch (texture) {
            case WAVY -> {
                int column = Math.round(along + 2 * (float) Math.sin(down * 0.45F + Noise.hash(0, 0, seed) * 6));
                light = 1 + 0.11F * streak * Noise.signed(column, 0, seed + 31) + 0.05F * (Noise.smooth(gx, gy, 3, seed + 32) - 0.5F) * 2;
            }
            case CURLY -> light = 1 + 0.2F * (Noise.smooth(gx, gy, 2.4F, seed + 33) - 0.5F) * 2 + 0.05F * Noise.signed(gx, gy, seed + 34);
            case DREADS -> {
                float gap = dreadGap(along);
                light = (1.05F - 0.4F * gap * gap) * (1 + 0.1F * Noise.signed((int) Math.floor(along / 3.6F), 0, seed + 35));
            }
            default -> light = 1 + 0.09F * streak * Noise.signed(Math.round(along), 0, seed + 37)
                    + 0.05F * (Noise.smooth(gx, gy, 3, seed + 38) - 0.5F) * 2 + 0.02F * Noise.signed(gx, gy, seed + 39);
        }
        int color = Colors.shade(ctx.hair, light);
        // Highlights: a ring around the crown of the cap; the tops of the thin slabs stay dark
        // (where they show, under the cap's edge, a bright line would read as a seam).
        boolean cap = name.startsWith("hair_cap") || name.equals("hair_afro") || name.equals("hair_bun") || name.equals("hair_mohawk");
        if (face == Face.TOP) {
            if (cap) {
                // A soft sheen across the front half of the crown, as light catches combed hair.
                float t = lz / Math.max(0.1F, box.d());          // 0 at the back, 1 at the front
                float band = 1 - Math.abs(t - 0.62F) / 0.3F;
                if (band > 0) {
                    color = Colors.lighten(color, 0.09F * band * band);
                }
            } else {
                color = Colors.shade(color, 0.78F);
            }
        } else if (ly < 0.6F && face != Face.BOTTOM && cap) {
            color = Colors.lighten(color, 0.06F);
        }
        if (name.equals("hair_braid")) {
            int segment = (int) Math.floor(ly / 0.8F) + (lx > box.w() / 2 ? 1 : 0);
            color = Colors.shade(color, segment % 2 == 0 ? 0.82F : 1.1F);
            if (ly > box.h() - 0.8F) {
                color = Colors.shade(color, 0.6F);
            }
        }
        if (name.equals("hair_bun")) {
            // A coiled bun: a spiral of strands, lit in the middle and shaded at its rim.
            float cx = (lx - box.w() / 2) / (box.w() / 2);
            float cy = (ly - box.h() / 2) / (box.h() / 2);
            float r = (float) Math.sqrt(cx * cx + cy * cy);
            float angle = (float) Math.atan2(cy, cx);
            float spiral = (float) Math.sin(angle * 2 + r * 9);
            color = Colors.shade(color, (1.12F - 0.38F * r * r) * (1 + 0.1F * spiral));
        }
        if ((name.startsWith("hair_long") || name.equals("hair_tail_body") || name.equals("hair_braid")) && ly < 1.2F) {
            color = Colors.shade(color, 0.72F + 0.28F * ly / 1.2F);
        }
        // Tips are darker.
        float tip = tipDepth(box, face, ly);
        if (tip > 0) {
            color = Colors.shade(color, 1 - 0.12F * tip);
        }
        return cover >= 1 ? color : Colors.fade(color, cover);
    }

    /** How far into the feathered tip region a pixel is (0 outside, 1 at the very end). */
    private static float tipDepth(BoxSpec box, Face face, float ly) {
        if (face == Face.TOP || face == Face.BOTTOM || box.name().startsWith("hair_cap") || box.name().equals("hair_afro")) {
            return 0;
        }
        float tipLength = Math.min(1.4F, box.h() * 0.35F);
        float fromEnd = box.h() - ly;
        return fromEnd >= tipLength ? 0 : 1 - fromEnd / tipLength;
    }

    private static float dreadGap(float along) {
        float pos = along / 3.6F;
        float frac = pos - (float) Math.floor(pos);
        return Math.abs(frac - 0.5F) * 2;
    }

    private static float hairCover(Context ctx, BoxSpec box, Face face, float fx, float fy, float lx, float ly, float lz, long seed) {
        String name = box.name();
        String id = ctx.hairId;
        HairStyle.Texture texture = ctx.hairStyle.texture();
        int column = Math.round((face == Face.TOP || face == Face.BOTTOM ? lx : (face == Face.FRONT || face == Face.BACK ? lx : lz)) * 4);
        float h = box.h();
        // The slabs around the head start under the cap; their lengths are measured from the
        // top of the head as if they started there.
        if (name.startsWith("hair_front") || name.startsWith("hair_right") || name.startsWith("hair_left") || name.startsWith("hair_back")) {
            float hidden = box.y() + 8.0F;
            h += hidden;
            ly += hidden;
        }
        // The length of this column (model units from the top of the box), shaped by the style.
        float limit = h;
        if (name.startsWith("hair_front")) {
            float t = lx / box.w();          // 0 at the player's right, 1 at the left
            float center = Math.abs(t - 0.5F) * 2;
            limit = switch (ctx.hairStyle.fringeShape()) {
                case SIDE_SWEPT -> h * (0.3F + 0.7F * t);
                case MIDDLE_PART -> h * Math.max(0.2F, Math.min(1, center * 1.6F));
                case CURTAIN -> center > 0.6F ? h : h * (0.3F + 0.7F * (float) Math.sqrt(center / 0.6F));
                case MESSY -> h * (0.65F + 0.35F * Noise.smooth(column, 0, 3.5F, seed + 10));
                case SPIKY -> h * (0.55F + 0.45F * Math.abs(((column * 0.37F) % 1F) * 2 - 1));
                default -> h;
            };
            if (id.equals("side_shave")) {
                limit = h * (0.4F + 0.6F * t);
            }
        } else if (name.startsWith("hair_right") || name.startsWith("hair_left")) {
            // Sides: a little shorter next to the face (the temple) for short styles.
            float front = name.startsWith("hair_right") ? 1 - lz / box.d() : lz / box.d();
            if (h < 4.5F && front > 0.75F) {
                limit = h * 0.8F;
            }
            if (ctx.hairStyle.texture() == HairStyle.Texture.WAVY) {
                limit += 0.4F * (float) Math.sin(column * 0.8F);
            }
        } else if (name.startsWith("hair_back") || name.startsWith("hair_long")) {
            if (ctx.hairStyle.texture() == HairStyle.Texture.WAVY) {
                limit += 0.4F * (float) Math.sin(column * 0.7F + 1);
            }
            if (name.startsWith("hair_long")) {
                // Hair falling down the back: narrower towards the tips, ending in a soft V.
                float t = ly / h;
                float edge = Math.min(lx, box.w() - lx);
                if (edge < t * 0.9F) {
                    return 0;
                }
                float center = Math.abs(lx - box.w() / 2) / (box.w() / 2);
                limit = h * (1 - 0.32F * center * center);
            }
        } else if (name.equals("hair_tail_body") || name.equals("hair_braid")) {
            // A rounded tip.
            float center = Math.abs(lx - box.w() / 2) / (box.w() / 2);
            limit = h - 0.9F * center * center;
        }
        // Dread gaps (transparent on the thin slabs, dark on the cap).
        if (texture == HairStyle.Texture.DREADS && !name.startsWith("hair_cap")) {
            float along = face == Face.FRONT || face == Face.BACK ? lx * 4 : lz * 4;
            if (dreadGap(along) > 0.78F) {
                return 0;
            }
        }
        // The cap is solid: the hair goes on below it (slabs, or hair painted on the head), so
        // feathered tips at its lower edge would only show as a see-through line.
        if (name.startsWith("hair_cap") && face != Face.TOP) {
            return 1;
        }
        if (face == Face.TOP) {
            if (texture == HairStyle.Texture.CURLY && Noise.hash(px(fx), px(fy), seed + 40) < 0.1F) {
                return 0.6F;
            }
            return 1;
        }
        if (face == Face.BOTTOM) {
            // The underside of a slab is its tips: mostly see-through.
            return name.startsWith("hair_cap") || name.equals("hair_afro") || name.equals("hair_bun") || name.equals("hair_tie") ? 1 : 0.35F;
        }
        float jitter = Noise.hash(column, 1, seed + 41) < 0.35F ? 0.25F : 0;
        float edge = limit + jitter - ly;
        if (edge <= 0) {
            return 0;
        }
        // Feathered tips over the last pixel and a half.
        if (edge < 0.4F) {
            return 0.45F + (Noise.hash(column, 2, seed + 42) < 0.5F ? 0.3F : 0);
        }
        if (texture == HairStyle.Texture.CURLY && Noise.hash(px(fx), px(fy), seed + 43) < 0.12F) {
            return 0.7F;
        }
        return 1;
    }

    private static int px(float units) {
        return Math.round(units * 4);
    }

    // ---------------------------------------------------------------- cloth

    private static int fabric(int base, int px, int py, long seed) {
        float folds = 1 + 0.045F * (Noise.fbm(px, py, 5, seed) - 0.5F) * 2;
        float grain = 1 + 0.012F * Noise.signed(px, py, seed + 1);
        return Colors.shade(base, folds * grain);
    }

    private static int camo(int base, int alt, int px, int py, long seed) {
        float n1 = Noise.fbm(px, py, 6.5F, seed + 2);
        float n2 = Noise.smooth(px, py, 3.8F, seed + 3);
        int tone;
        if (n1 < 0.4F) {
            tone = Colors.shade(base, 0.62F);
        } else if (n1 > 0.63F) {
            tone = Colors.lighten(base, 0.22F);
        } else {
            tone = n2 > 0.58F ? alt : base;
        }
        return Colors.shade(tone, 1 + 0.02F * Noise.signed(px, py, seed + 4));
    }

    /** Dust, dirt or blood over a cloth pixel. */
    private static int wear(Context ctx, int color, int px, int py, float down) {
        if (ctx.wear == 0) {
            return color;
        }
        long seed = ctx.seed + 20;
        int result = color;
        if (ctx.wear == 1) {
            float n = Colors.clamp01((Noise.fbm(px, py, 7, seed) - 0.45F) / 0.3F);
            result = Colors.lerp(result, Colors.rgb(200, 190, 170), 0.2F * n);
        } else {
            float n = Colors.clamp01((Noise.fbm(px, py, 5, seed + 1) - 0.5F) / 0.3F);
            result = Colors.lerp(result, Colors.rgb(55, 42, 28), 0.38F * n * (0.5F + 0.5F * down));
            if (Noise.hash(px, py, seed + 2) < 0.02F) {
                result = Colors.lerp(result, Colors.rgb(55, 42, 28), 0.45F);
            }
        }
        if (ctx.wear == 3) {
            float blob = Noise.smooth(px, py, 2.2F, seed + 3);
            if (blob > 0.84F) {
                result = Colors.lerp(result, Colors.rgb(110, 12, 14), 0.85F);
            } else if (blob > 0.8F) {
                result = Colors.lerp(result, Colors.rgb(110, 12, 14), 0.45F);
            }
        }
        return result;
    }

    /** Shirt colour at a point of the body, {@code bodyX} across the chest (0 = centre), {@code bodyY} down from the neck. */
    private static int shirtPixel(Context ctx, Face face, float bodyX, float bodyY, int px, int py, boolean sleeve) {
        String id = ctx.shirtId;
        int base = id.equals("jacket") && !sleeve ? ctx.shirt2 : ctx.shirt;
        int color;
        switch (id) {
            case "camo" -> color = camo(ctx.shirt, ctx.shirt2, px, py, ctx.seed);
            case "flannel" -> {
                color = fabric(ctx.shirt, px, py, ctx.seed);
                boolean v = (px / 4 % 9) < 3;
                boolean h = (py / 4 % 9) < 3;
                color = v && h ? Colors.lerp(color, ctx.shirt2, 0.85F) : v || h ? Colors.lerp(color, ctx.shirt2, 0.55F) : color;
            }
            case "striped" -> {
                color = fabric(ctx.shirt, px, py, ctx.seed);
                if (((int) Math.floor(bodyY) & 1) == 1) {
                    color = Colors.lerp(color, ctx.shirt2, 0.9F);
                }
            }
            case "sweater" -> color = Colors.shade(fabric(ctx.shirt, px, py, ctx.seed), 0.965F + 0.07F * ((px + py) & 1));
            default -> color = fabric(base, px, py, ctx.seed);
        }
        if (id.equals("jacket") && !sleeve && face == Face.FRONT) {
            // Open jacket over a tee: panels on both sides, a dark zip edge.
            float open = 1.4F - Math.min(bodyY, 10) * 0.06F;
            if (Math.abs(bodyX) > open) {
                color = fabric(ctx.shirt, px, py, ctx.seed);
                if (Math.abs(bodyX) < open + 0.3F) {
                    color = Colors.shade(color, 0.8F);
                }
            }
        } else if (id.equals("jacket") && !sleeve) {
            color = fabric(ctx.shirt, px, py, ctx.seed);
        }
        return color;
    }

    private static int shirt(Context ctx, BoxSpec box, Face face, float lx, float ly, float lz, int px, int py) {
        boolean torso = box.bone() == Bone.BODY;
        int skin = skinAt(ctx, box, face, lx, ly, px, py);
        if (torso) {
            float bodyY = box.y() + ly;
            float bodyX = box.x() + lx;            // -4..4 across the body
            if (face == Face.BACK) {
                bodyX = -bodyX;
            }
            if (bodyY > ctx.shirtBottom) {
                return skin;
            }
            String id = ctx.shirtId;
            if (id.equals("tank_top")) {
                if (face == Face.RIGHT || face == Face.LEFT) {
                    if (bodyY < 2.75F) {
                        return skin;      // armholes
                    }
                } else if (bodyY < 1.0F) {
                    if (Math.abs(Math.abs(bodyX) - 1.9F) > 0.55F) {
                        return skin;      // straps only
                    }
                } else if (bodyY < 2.5F && Math.abs(bodyX) > 2.4F + (bodyY - 1.0F) * 1.1F) {
                    return skin;
                } else if (face == Face.FRONT && bodyY < 0.8F) {
                    return skin;
                }
            } else if (face == Face.FRONT) {
                // Necklines.
                switch (id) {
                    case "vneck" -> {
                        if (bodyY < 1.7F - Math.abs(bodyX) * 1.3F) {
                            return skin;
                        }
                    }
                    case "polo", "dress_shirt", "jacket", "henley" -> {
                        if (bodyY < 0.9F && Math.abs(bodyX) < 0.45F) {
                            return skin;
                        }
                    }
                    case "crop_top" -> {
                        if (bodyY < 0.6F && Math.abs(bodyX) < 1.2F) {
                            return skin;
                        }
                    }
                    default -> {
                        if (bodyY < 0.5F && Math.abs(bodyX) < 1.1F) {
                            return skin;
                        }
                    }
                }
            }
            int color = shirtPixel(ctx, face, bodyX, bodyY, px, py, false);
            if (box.name().equals("bust")) {
                // The wedge faces slightly upward and catches more light than the chest: tone it down,
                // and shade its underside like the fold of the fabric.
                color = Colors.shade(color, face == Face.BOTTOM ? 0.72F : face == Face.FRONT ? 0.9F : 0.94F);
            }
            if (face == Face.FRONT && (id.equals("henley") || id.equals("dress_shirt")) && Math.abs(bodyX) < 0.4F && bodyY > 0.9F
                    && (id.equals("dress_shirt") || bodyY < 3.2F)) {
                color = ((int) Math.floor(bodyY) % 1 == 0 && Math.abs(bodyY - Math.floor(bodyY) - 0.5F) < 0.15F) ? Colors.shade(color, 0.7F) : Colors.shade(color, 0.94F);
            }
            if (bodyY > ctx.shirtBottom - 0.3F) {
                color = Colors.shade(color, 0.85F);   // hem
            }
            if (face != Face.TOP && face != Face.BOTTOM && (lx < 0.25F || lx > box.w() - 0.25F)) {
                color = Colors.shade(color, 0.94F);
            }
            return wear(ctx, color, px, py, bodyY / 12);
        }
        // Arms: sleeves down to the sleeve length, skin below.
        float armY = box.y() + ly;                 // -2 (shoulder) .. 10 (hand)
        if (armY > ctx.sleeveLength) {
            return skin;
        }
        boolean left = box.bone() == Bone.LEFT_ARM;
        float bodyX = (left ? 1 : -1) * (5 + lx - box.w() / 2);
        int color = shirtPixel(ctx, face, bodyX, armY + 2, px, py, true);
        if (armY < -1.75F) {
            color = Colors.shade(color, 0.88F);    // shoulder seam
        }
        if (armY > ctx.sleeveLength - 0.5F) {
            color = Colors.shade(color, ctx.shirtId.equals("sweater") || ctx.shirtId.equals("hoodie") ? (px % 2 == 0 ? 0.88F : 0.98F) : 0.88F);
        }
        if (ctx.shirtId.equals("flannel") && armY > ctx.sleeveLength - 1.0F) {
            color = Colors.shade(color, 0.9F);
        }
        return wear(ctx, color, px, py, (armY + 2) / 12);
    }

    private static int trim(Context ctx, BoxSpec box, Face face, float lx, float ly, float lz, int px, int py) {
        int color = fabric(ctx.shirt, px, py, ctx.seed);
        switch (box.name()) {
            case "hood" -> {
                if (face == Face.TOP && Math.abs(lx - box.w() / 2) < box.w() * 0.32F && lz < box.d() * 0.65F) {
                    color = Colors.shade(color, 0.5F);   // the hollow of the hood
                } else if (face == Face.TOP || face == Face.FRONT) {
                    color = Colors.shade(color, 0.9F);
                }
                if (face == Face.BACK && ly > box.h() - 0.4F) {
                    color = Colors.shade(color, 0.85F);
                }
            }
            case "pocket" -> {
                if (face == Face.FRONT && (ly < 0.3F || lx < 0.3F || lx > box.w() - 0.3F)) {
                    color = Colors.shade(color, 0.8F);
                }
                if (face == Face.TOP) {
                    color = Colors.shade(color, 0.7F);
                }
            }
            default -> {
                // Collars: lighter, with a dark fold at the bottom.
                color = Colors.lighten(color, 0.08F);
                if (face == Face.BOTTOM || (face == Face.FRONT && ly > box.h() - 0.3F)) {
                    color = Colors.shade(color, 0.8F);
                }
            }
        }
        return wear(ctx, color, px, py, 0.2F);
    }

    // ----------------------------------------------------------------- pants

    private static int pants(Context ctx, BoxSpec box, Face face, float lx, float ly, float lz, int px, int py) {
        float legY = box.y() + ly;                 // 0 (hip) .. 11.2 (ankle)
        int skin = skinAt(ctx, box, face, lx, ly, px, py);
        if (legY > ctx.pantsBottom) {
            return skin;
        }
        String id = ctx.pantsId;
        boolean left = box.bone() == Bone.LEFT_LEG;
        boolean outerSide = left ? face == Face.LEFT : face == Face.RIGHT;
        boolean innerSide = left ? face == Face.RIGHT : face == Face.LEFT;
        int color = id.equals("camo") ? camo(ctx.pants, Colors.shade(ctx.pants, 0.5F), px, py, ctx.seed) : fabric(ctx.pants, px, py, ctx.seed);
        float center = lx - box.w() / 2;
        switch (id) {
            case "jeans", "ripped_jeans", "cargo" -> {
                if (outerSide && Math.abs(lz - box.d() / 2) < 0.25F) {
                    color = Colors.shade(color, 1.1F);
                }
                if (innerSide && Math.abs(lz - box.d() / 2) < 0.2F) {
                    color = Colors.shade(color, 0.9F);
                }
                if (face == Face.FRONT) {
                    float knee = 1 - Math.abs(legY - 6) / 1.5F;
                    if (knee > 0) {
                        color = Colors.lighten(color, 0.06F * knee);
                    }
                }
                if (face == Face.BACK && legY > 0.8F && legY < 2.8F && Math.abs(center) < 1.3F
                        && (legY < 1.1F || legY > 2.5F || Math.abs(center) > 1.0F)) {
                    color = Colors.shade(color, 0.84F);   // back pocket
                }
                if (id.equals("cargo") && outerSide && legY > 3.2F && legY < 5.8F && Math.abs(lz - box.d() / 2) < 1.4F) {
                    boolean edge = legY < 3.6F || legY > 5.5F || Math.abs(lz - box.d() / 2) > 1.1F;
                    color = Colors.shade(color, edge ? 0.8F : 1.02F);
                }
                if (id.equals("ripped_jeans") && face == Face.FRONT) {
                    boolean hole = left ? (legY > 5.0F && legY < 6.4F && Math.abs(center) < 1.3F) : (legY > 7.6F && legY < 8.4F && Math.abs(center) < 1.0F);
                    if (hole) {
                        return Noise.hash(px, py, ctx.seed + 5) < 0.2F ? Colors.lighten(ctx.pants, 0.45F) : skin;
                    }
                }
            }
            case "trousers" -> {
                if (face == Face.FRONT && Math.abs(center) < 0.25F) {
                    color = Colors.lighten(color, 0.08F);
                }
            }
            case "joggers", "track" -> {
                if (legY < 0.7F || legY > 10.3F) {
                    color = Colors.shade(color, (px & 1) == 0 ? 0.88F : 0.98F);
                }
                if (id.equals("track") && outerSide && (Math.abs(lz - box.d() / 2 - 0.6F) < 0.25F || Math.abs(lz - box.d() / 2 + 0.6F) < 0.25F)) {
                    color = Colors.lerp(color, Colors.rgb(236, 236, 230), 0.9F);
                }
            }
            case "leggings" -> {
                if (face == Face.FRONT && Math.abs(center) < 0.5F) {
                    color = Colors.lighten(color, 0.06F);
                }
            }
            default -> {
            }
        }
        if (legY > ctx.pantsBottom - 0.3F && !id.equals("leggings")) {
            color = Colors.shade(color, 0.86F);   // hem
        }
        return wear(ctx, color, px, py, legY / 12);
    }

    // ----------------------------------------------------------------- shoes

    private static int shoe(Context ctx, BoxSpec box, Face face, float lx, float ly, float lz, int px, int py) {
        String id = ctx.shoesId;
        int skin = skinAt(ctx, box, face, lx, ly, px, py);
        boolean boot = box.name().startsWith("boot");
        float center = lx - box.w() / 2;
        switch (id) {
            case "barefoot" -> {
                int color = skin;
                if ((face == Face.TOP || face == Face.FRONT) && lz < box.d() * 0.35F) {
                    float t = lx / box.w() * 5;
                    if (Math.abs(t - Math.round(t)) < 0.14F && t > 0.4F && t < 4.6F) {
                        color = Colors.shade(color, 0.84F);   // toes
                    }
                }
                return color;
            }
            case "sandals" -> {
                int color = skin;
                if (face == Face.TOP && Math.abs(lz - box.d() * 0.42F) < 0.35F) {
                    return fabric(ctx.shoes, px, py, ctx.seed);   // strap across the foot
                }
                if ((face == Face.TOP || face == Face.FRONT) && Math.abs(center) < 0.3F && lz < box.d() * 0.45F) {
                    return fabric(ctx.shoes, px, py, ctx.seed);   // toe strap
                }
                if (face == Face.RIGHT || face == Face.LEFT || face == Face.BACK) {
                    if (Math.abs(ly - box.h() * 0.45F) < 0.3F) {
                        return fabric(ctx.shoes, px, py, ctx.seed);
                    }
                }
                return color;
            }
            default -> {
            }
        }
        int upper = id.equals("dress_shoes") ? Colors.shade(ctx.shoes, 0.75F) : ctx.shoes;
        int color = fabric(upper, px, py, ctx.seed + 7);
        switch (id) {
            case "sneakers" -> {
                if (face == Face.TOP && Math.abs(center) < 0.9F && lz > box.d() * 0.3F) {
                    boolean lace = ((int) Math.floor(lz * 2) & 1) == 0;
                    color = lace ? LACE : Colors.shade(color, 0.9F);
                }
                if ((face == Face.TOP || face == Face.FRONT) && lz < box.d() * 0.25F) {
                    color = Colors.lighten(color, 0.3F);   // toe cap
                }
            }
            case "boots", "hiking_boots" -> {
                if (boot && face == Face.FRONT && Math.abs(center) < 1.0F) {
                    boolean eyelet = Math.abs(center) > 0.6F && ((int) Math.floor(ly * 2) & 1) == 0;
                    boolean lace = Math.abs(center) <= 0.6F && (((int) Math.floor(ly * 2) + (center < 0 ? 0 : 1)) & 1) == 0;
                    color = eyelet ? Colors.shade(color, 0.6F) : lace ? Colors.lerp(color, LACE, 0.7F) : color;
                }
                if (boot && ly < 0.4F) {
                    color = Colors.shade(color, 0.82F);   // collar
                }
                if (!boot && (face == Face.TOP || face == Face.FRONT) && lz < box.d() * 0.3F) {
                    color = Colors.shade(color, id.equals("hiking_boots") ? 0.72F : 1.1F);
                }
                if (!boot && face == Face.TOP && Math.abs(center) < 0.8F && lz > box.d() * 0.3F) {
                    color = Colors.lerp(color, LACE, ((int) Math.floor(lz * 2) & 1) == 0 ? 0.6F : 0);
                }
            }
            case "combat_boots" -> {
                if (boot && face == Face.FRONT && Math.abs(center) < 1.0F && ly > 0.8F) {
                    boolean eyelet = Math.abs(center) > 0.6F && ((int) Math.floor(ly * 2) & 1) == 1;
                    boolean lace = Math.abs(center) <= 0.6F && (((int) Math.floor(ly * 2) + (center < 0 ? 0 : 1)) & 1) == 1;
                    color = eyelet ? Colors.lerp(color, BUCKLE, 0.6F) : lace ? Colors.shade(color, 1.5F) : color;
                }
                if (boot && ly < 0.7F) {
                    color = Colors.shade(color, 0.72F);
                    if (face == Face.FRONT && Math.abs(center + 1.2F) < 0.3F) {
                        color = BUCKLE;
                    }
                }
            }
            case "dress_shoes" -> {
                if (face == Face.TOP && Math.abs(center) < 0.6F && lz < box.d() * 0.5F) {
                    color = Colors.lighten(color, 0.45F);
                }
            }
            case "rain_boots" -> {
                if (face != Face.TOP && face != Face.BOTTOM) {
                    float across = face == Face.FRONT || face == Face.BACK ? lx / box.w() : lz / box.d();
                    if (across > 0.15F && across < 0.35F) {
                        color = Colors.lighten(color, 0.25F);
                    } else if (across > 0.75F) {
                        color = Colors.shade(color, 0.86F);
                    }
                }
                if (boot && ly < 0.5F) {
                    color = Colors.shade(color, 0.82F);
                }
            }
            default -> {
            }
        }
        return ctx.wear >= 2 ? wear(ctx, color, px, py, 1) : color;
    }

    private static int sole(Context ctx, BoxSpec box, Face face, float lx, float ly, float lz, int px, int py) {
        int sole = switch (ctx.shoesId) {
            case "sneakers" -> Colors.rgb(222, 220, 214);
            case "hiking_boots" -> Colors.rgb(92, 80, 68);
            case "dress_shoes" -> Colors.rgb(52, 40, 34);
            case "sandals" -> Colors.rgb(150, 120, 82);
            default -> Colors.rgb(38, 34, 32);
        };
        int color = Colors.shade(sole, 1 + 0.05F * Noise.signed(px, py, ctx.seed + 11));
        if (face == Face.BOTTOM) {
            color = Colors.shade(color, (((int) Math.floor(lz * 2)) & 1) == 0 && lx > 0.3F && lx < box.w() - 0.3F ? 0.78F : 0.92F);
        } else if (face != Face.TOP && ly < 0.15F) {
            color = Colors.shade(color, 0.8F);
        }
        return color;
    }

    // ------------------------------------------------------------ belt, skirt

    private static int belt(Context ctx, BoxSpec box, Face face, float lx, float ly, float lz, int px, int py) {
        int color = Colors.shade(BELT, 1 + 0.06F * Noise.signed(px, py, ctx.seed + 12));
        if (face == Face.TOP) {
            color = Colors.shade(color, 1.15F);
        } else if (face == Face.FRONT && Math.abs(lx - box.w() / 2) < 0.65F) {
            color = Math.abs(lx - box.w() / 2) < 0.45F && ly > 0.2F && ly < box.h() - 0.2F ? Colors.shade(BUCKLE, 0.8F) : BUCKLE;
        } else if (face != Face.BOTTOM && (ly < 0.15F || ly > box.h() - 0.15F)) {
            color = Colors.shade(color, 0.8F);
        }
        return color;
    }

    private static int skirt(Context ctx, BoxSpec box, Face face, float lx, float ly, float lz, int px, int py) {
        int color = fabric(ctx.pants, px, py, ctx.seed);
        if (face == Face.BOTTOM) {
            return Colors.shade(color, 0.6F);
        }
        float across = face == Face.FRONT || face == Face.BACK ? lx : lz;
        if (face != Face.TOP) {
            color = Colors.shade(color, ((int) Math.floor(across) & 1) == 0 ? 0.94F : 1.0F);   // pleats
            if (ly < 0.5F) {
                color = Colors.shade(color, 0.8F);   // waistband
            } else if (ly > box.h() - 0.35F) {
                color = Colors.shade(color, 0.85F);  // hem
            }
        }
        return wear(ctx, color, px, py, ly / box.h());
    }
}
