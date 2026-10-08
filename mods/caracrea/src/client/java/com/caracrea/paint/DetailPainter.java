package com.caracrea.paint;

import com.caracrea.character.Appearance;
import com.caracrea.character.Catalog;
import com.caracrea.character.Trait;
import com.caracrea.model.Bone;
import com.caracrea.model.BoxSpec;
import com.caracrea.model.CharacterModelSpec;
import com.caracrea.model.CharacterModelSpec.HairShape;
import com.caracrea.model.Material;
import com.caracrea.paint.SkinLayout.Face;

/**
 * Paints the faces of the 3D detail boxes into the detail atlas. Each face is painted from a material shader that
 * knows where the pixel sits on the box (and on the body), so shirts, trousers and hair stay continuous across the
 * boxes that make up a part.
 *
 * <p>Volumes are painted in: every face is shaded like a rounded surface (darker towards its edges, and in the
 * creases where parts meet: under the arms, under the chest, the neck, the crotch, the knee), clothes get their seams,
 * hems, plackets and folds. Hair is painted lock by lock, with crisp tips: calm on top of the head, the locks showing
 * more towards their tips, a sheen where the light catches them.
 */
final class DetailPainter {
    private static final int BUCKLE = Colors.rgb(196, 176, 124);
    private static final int BELT = Colors.rgb(58, 40, 26);
    private static final int LACE = Colors.rgb(228, 226, 220);
    private static final int BUTTON = Colors.rgb(232, 228, 216);
    /** Warmth mixed into shadowed skin, so creases look alive rather than grey. */
    private static final int SKIN_SHADOW = Colors.rgb(150, 70, 60);

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
        final HairShape hairShape;
        final HairLook look;
        final String shirtId;
        final String pantsId;
        final String shoesId;
        final int wear;
        final float shirtBottom;
        final float pantsBottom;
        /** Hair ramp: deep shadow, shadow, base, light, highlight. */
        final int[] ramp;

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
            this.hairId = Catalog.hair(a).id();
            this.hairShape = CharacterModelSpec.hairShape(hairId);
            this.look = HairLook.of(hairId);
            this.shirtId = Catalog.optionId(a, Trait.SHIRT_STYLE);
            this.pantsId = Catalog.optionId(a, Trait.PANTS_STYLE);
            this.shoesId = Catalog.optionId(a, Trait.SHOES_STYLE);
            this.wear = a.get(Trait.WEAR);
            this.shirtBottom = 12.5F;
            this.pantsBottom = switch (pantsId) {
                case "shorts" -> 6.0F;
                case "joggers", "track" -> 11.5F;
                default -> 12.5F;
            };
            this.ramp = hairRamp(hair);
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
            case HAIR -> (fx, fy, lx, ly, lz, px, py) -> hair(ctx, box, face, lx, ly, lz);
            case SHIRT -> (fx, fy, lx, ly, lz, px, py) -> torso(ctx, box, face, lx, ly, lz, px, py);
            case SHIRT_TRIM -> (fx, fy, lx, ly, lz, px, py) -> trim(ctx, box, face, lx, ly, lz, px, py);
            case PANTS -> (fx, fy, lx, ly, lz, px, py) -> pants(ctx, box, face, lx, ly, lz, px, py);
            case SHOE -> (fx, fy, lx, ly, lz, px, py) -> shoe(ctx, box, face, lx, ly, lz, px, py);
            case SOLE -> (fx, fy, lx, ly, lz, px, py) -> sole(ctx, box, face, lx, ly, lz, px, py);
            case BELT -> (fx, fy, lx, ly, lz, px, py) -> belt(ctx, box, face, lx, ly, lz, px, py);
        };
    }

    // ---------------------------------------------------------------- volume

    /** Smooth step from 0 (at {@code a}) to 1 (at {@code b}). */
    private static float step(float a, float b, float x) {
        float t = Colors.clamp01((x - a) / (b - a));
        return t * t * (3 - 2 * t);
    }

    /** Light from the shape of a box face: rounded towards its vertical edges, so boxes read as rounded limbs. */
    private static float round(BoxSpec box, Face face, float lx, float lz, float strength) {
        if (face == Face.TOP || face == Face.BOTTOM) {
            return 1;
        }
        float t = face == Face.FRONT || face == Face.BACK ? lx / box.w() : lz / box.d();
        float c = Math.abs(t * 2 - 1);
        return 1 - strength * c * c * c;
    }

    /** Whether a face of a leg faces the other leg. */
    private static boolean inner(BoxSpec box, Face face) {
        return switch (box.bone()) {
            case RIGHT_LEG -> face == Face.LEFT;
            case LEFT_LEG -> face == Face.RIGHT;
            default -> false;
        };
    }

    /** Light from top to bottom along a part of the body: the shoulders catch more light than the waist. */
    private static float height(BoxSpec box, float ly) {
        float y = box.y() + ly;
        return switch (box.bone()) {
            case RIGHT_LEG, LEFT_LEG -> 1.02F - 0.07F * y / 12;
            case BODY -> 1.03F - 0.06F * y / 12;
            default -> 1;
        };
    }

    /** Shadow where parts of the body meet: under the arms, the neck, under the chest, the crotch, the knee. */
    private static float crease(BoxSpec box, Face face, float lx, float ly) {
        String n = box.name();
        float ao = 1;
        switch (box.bone()) {
            case BODY -> {
                boolean side = face == Face.LEFT || face == Face.RIGHT;
                if (n.equals("chest") && side) {
                    ao *= 1 - 0.2F * (1 - step(0.4F, 3.2F, ly));
                }
                if (n.equals("chest") && (face == Face.FRONT || face == Face.BACK)) {
                    float bx = Math.abs(box.x() + lx);
                    ao *= 1 - 0.1F * (1 - step(0, 0.8F, ly)) * (1 - step(1.6F, 3.0F, bx));
                }
                if ((n.equals("waist") || n.equals("hips")) && face != Face.TOP && face != Face.BOTTOM) {
                    ao *= 1 - 0.1F * (1 - step(0, 0.45F, ly));
                }
            }
            case RIGHT_LEG, LEFT_LEG -> {
                if (inner(box, face)) {
                    ao *= 0.9F;
                }
                if (n.startsWith("leg_lower")) {
                    ao *= 1 - 0.07F * (1 - step(0, 0.4F, ly));
                }
            }
            default -> {
            }
        }
        return ao;
    }

    /** All the volume shading of a body box face (roundness, light from above, creases). */
    private static float volume(BoxSpec box, Face face, float lx, float ly, float lz, float roundness) {
        return round(box, face, lx, lz, roundness) * height(box, ly) * crease(box, face, lx, ly) * ledge(box, face);
    }

    /**
     * The top of a block that sits under a wider one (the waist under the chest, the lower leg...): a thin step that
     * would catch the light like an edge, painted in the shadow of the block above so it reads as a crease.
     */
    private static float ledge(BoxSpec box, Face face) {
        if (face != Face.TOP) {
            return 1;
        }
        String n = box.name();
        return n.equals("waist") || n.equals("hips") || n.startsWith("leg_lower") ? 0.68F : 1;
    }

    // ------------------------------------------------------------------ skin

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
        return Colors.shade(color, 1 + 0.012F * (Noise.fbm(px, py, 6, seed) - 0.5F) * 2 + 0.006F * Noise.signed(px, py, seed + 1));
    }

    /** Shading applied to skin: darker means a little warmer too. */
    private static int lit(int skin, float light) {
        int color = Colors.shade(skin, light);
        return light < 1 ? Colors.tint(color, SKIN_SHADOW, Math.min(0.3F, (1 - light) * 0.6F)) : color;
    }

    /** Bare skin on the body and the legs, with their volume. */
    private static int bareSkin(Context ctx, BoxSpec box, Face face, float lx, float ly, float lz, int px, int py) {
        return lit(skinAt(ctx, box, face, lx, ly, px, py), volume(box, face, lx, ly, lz, 0.16F));
    }

    // ---------------------------------------------------------------- cloth

    private static int fabric(int base, int px, int py, long seed) {
        float folds = 1 + 0.025F * (Noise.fbm(px, py, 6, seed) - 0.5F) * 2;
        float grain = 1 + 0.01F * Noise.signed(px, py, seed + 1);
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

    /** Whether the shirt is knitted with ribs at its ends (collar, hem). */
    private static boolean ribbed(String id) {
        return switch (id) {
            case "sweater", "hoodie", "long_sleeve" -> true;
            default -> false;
        };
    }

    /** Shirt colour at a point of the body, {@code bodyX} across the chest (0 = centre), {@code bodyY} down from the neck. */
    private static int shirtPixel(Context ctx, Face face, float bodyX, float bodyY, int px, int py) {
        String id = ctx.shirtId;
        int color;
        switch (id) {
            case "camo" -> color = camo(ctx.shirt, ctx.shirt2, px, py, ctx.seed);
            case "flannel" -> {
                color = fabric(ctx.shirt, px, py, ctx.seed);
                // Plaid in quarter units, the same as on the skin (ClothesPainter), so the sleeves match the torso.
                int x = (int) Math.floor((bodyX + 8) * 4);
                int y = (int) Math.floor(bodyY * 4);
                color = plaid(color, ctx.shirt2, x, y);
            }
            case "striped" -> {
                color = fabric(ctx.shirt, px, py, ctx.seed);
                if (((int) Math.floor(bodyY * 1.25F) & 1) == 1) {
                    color = Colors.lerp(color, ctx.shirt2, 0.9F);
                }
            }
            case "sweater" -> {
                color = fabric(ctx.shirt, px, py, ctx.seed);
                // Knit: fine vertical ribs.
                float rib = (bodyX * 4) - (float) Math.floor(bodyX * 4);
                color = Colors.shade(color, rib < 0.5F ? 0.965F : 1.02F);
            }
            default -> color = fabric(id.equals("jacket") ? ctx.shirt2 : ctx.shirt, px, py, ctx.seed);
        }
        if (id.equals("jacket")) {
            if (face == Face.FRONT) {
                // Open jacket over a tee: panels on both sides of the opening, a zip along their edges.
                float open = 1.3F - Math.min(bodyY, 10) * 0.05F;
                float ax = Math.abs(bodyX);
                if (ax > open) {
                    color = fabric(ctx.shirt, px, py, ctx.seed);
                    if (ax < open + 0.22F) {
                        float tooth = bodyY * 5 - (float) Math.floor(bodyY * 5);
                        color = tooth < 0.5F ? Colors.rgb(150, 150, 150) : Colors.shade(color, 0.7F);
                    } else if (ax < open + 0.45F) {
                        color = Colors.shade(color, 0.86F);
                    }
                    // Side pockets with their flaps.
                    if (bodyY > 7.3F && bodyY < 7.55F && ax > 1.9F && ax < 3.5F) {
                        color = Colors.shade(color, 0.72F);
                    }
                } else {
                    color = Colors.shade(color, 0.9F);   // the tee, in the jacket's shadow
                }
            } else {
                color = fabric(ctx.shirt, px, py, ctx.seed);
            }
        }
        return color;
    }

    /** Flannel plaid at a point in quarter units: wide bands of the second colour, a thin light line between them. */
    static int plaid(int color, int second, int x, int y) {
        boolean v = Math.floorMod(x, 9) < 3;
        boolean h = Math.floorMod(y, 9) < 3;
        color = v && h ? Colors.lerp(color, second, 0.85F) : v || h ? Colors.lerp(color, second, 0.55F) : color;
        if (Math.floorMod(x, 9) == 6 || Math.floorMod(y, 9) == 6) {
            color = Colors.lerp(color, Colors.rgb(230, 226, 214), 0.22F);
        }
        return color;
    }

    /** Front placket with its buttons, for the shirts that have one (y down from the neck). */
    private static int placket(Context ctx, int color, float bodyX, float bodyY) {
        float end = switch (ctx.shirtId) {
            case "polo" -> 2.8F;
            case "henley" -> 3.2F;
            case "dress_shirt", "flannel" -> 12;
            default -> -1;
        };
        float ax = Math.abs(bodyX);
        if (bodyY < 0.4F || bodyY > end || ax > 0.5F) {
            return color;
        }
        if (ax > 0.38F) {
            return Colors.shade(color, 0.82F);
        }
        float pitch = ctx.shirtId.equals("polo") || ctx.shirtId.equals("henley") ? 0.9F : 1.7F;
        float t = (bodyY - 0.6F) / pitch;
        float frac = t - (float) Math.floor(t);
        if (ax < 0.2F && Math.abs(frac - 0.5F) < 0.12F && bodyY < end - 0.2F) {
            return Colors.lerp(BUTTON, color, 0.25F);
        }
        return Colors.shade(color, 0.97F);
    }

    /** Soft folds of a loose top: from the armpits towards the waist, and gathered above the belt. */
    private static float folds(Context ctx, Face face, float bodyX, float bodyY) {
        if (face != Face.FRONT && face != Face.BACK) {
            return 1;
        }
        if (ctx.shirtId.equals("tank_top") || ctx.shirtId.equals("dress_shirt")) {
            return 1;
        }
        float ax = Math.abs(bodyX);
        float light = 1;
        // Diagonal folds from under the arms.
        float d = (bodyY - 2.6F) - (3.9F - ax) * 0.9F;
        if (ax > 1.6F && bodyY > 1.5F && bodyY < 7.5F) {
            light *= 1 - 0.05F * Math.max(0, 1 - Math.abs(d) / 0.45F) + 0.02F * Math.max(0, 1 - Math.abs(d + 0.5F) / 0.4F);
        }
        // Gathered above the belt.
        if (bodyY > 9.0F && bodyY < 10.6F) {
            float wave = (float) Math.sin(bodyX * 2.3F + 1.1F) * 0.35F;
            float line = Math.abs(bodyY - 9.9F - wave);
            light *= 1 - 0.05F * Math.max(0, 1 - line / 0.25F);
        }
        return light;
    }

    private static int torso(Context ctx, BoxSpec box, Face face, float lx, float ly, float lz, int px, int py) {
        float bodyY = box.y() + ly;
        float bodyX = box.x() + lx;            // -4..4 across the body
        if (face == Face.BACK) {
            bodyX = -bodyX;
        }
        if (bodyY > ctx.shirtBottom) {
            return bareSkin(ctx, box, face, lx, ly, lz, px, py);
        }
        String id = ctx.shirtId;
        float ax = Math.abs(bodyX);
        // Bare skin: the tank top's armholes and straps, and the necklines.
        if (id.equals("tank_top")) {
            if (face == Face.RIGHT || face == Face.LEFT) {
                if (bodyY < 2.9F) {
                    return bareSkin(ctx, box, face, lx, ly, lz, px, py);
                }
            } else if (face == Face.TOP || bodyY < 1.0F) {
                if (Math.abs(ax - 1.9F) > 0.55F || face == Face.TOP) {
                    return face == Face.TOP && Math.abs(ax - 1.9F) <= 0.55F
                            ? Colors.shade(fabric(ctx.shirt, px, py, ctx.seed), 1.02F) : bareSkin(ctx, box, face, lx, ly, lz, px, py);
                }
            } else if (bodyY < 2.6F && ax > 2.4F + (bodyY - 1.0F) * 1.1F) {
                return bareSkin(ctx, box, face, lx, ly, lz, px, py);
            } else if (face == Face.FRONT && bodyY < 0.9F + 0.25F * (1 - ax / 1.4F) && ax < 1.4F) {
                return bareSkin(ctx, box, face, lx, ly, lz, px, py);
            }
        }
        float neck = -1;      // depth of the neckline at this x, for the rib around it
        if (face == Face.FRONT && !id.equals("tank_top")) {
            switch (id) {
                case "vneck" -> neck = 1.9F - ax * 1.3F;
                case "polo", "dress_shirt", "jacket", "henley", "flannel" -> neck = ax < 0.45F ? 0.85F : -1;
                default -> neck = ax < 1.25F ? 0.55F - 0.35F * (ax / 1.25F) * (ax / 1.25F) : -1;
            }
            if (bodyY < neck) {
                return bareSkin(ctx, box, face, lx, ly, lz, px, py);
            }
        } else if (face == Face.BACK && ax < 1.1F && bodyY < 0.25F) {
            return bareSkin(ctx, box, face, lx, ly, lz, px, py);
        }
        int color = shirtPixel(ctx, face, bodyX, bodyY, px, py);
        if (face == Face.FRONT) {
            color = placket(ctx, color, bodyX, bodyY);
            // Neck rib (crew and V necks).
            if (neck > 0 && !id.equals("polo") && !id.equals("dress_shirt") && !id.equals("jacket") && !id.equals("henley")
                    && !id.equals("flannel") && bodyY < neck + 0.35F) {
                color = Colors.shade(color, bodyY < neck + 0.08F ? 0.85F : 1.05F);
            }
            // Hoodie drawstrings.
            if (id.equals("hoodie") && Math.abs(ax - 0.75F) < 0.1F && bodyY > 0.3F && bodyY < 3.1F) {
                color = bodyY > 2.75F ? Colors.rgb(205, 205, 200) : Colors.lighten(ctx.shirt, 0.35F);
            }
        }
        // Side seams and the shoulder seams on top.
        if ((face == Face.LEFT || face == Face.RIGHT) && Math.abs(lz - box.d() / 2) < 0.08F) {
            color = Colors.shade(color, 0.9F);
        }
        if (face == Face.TOP && Math.abs(lz - box.d() / 2) < 0.08F) {
            color = Colors.shade(color, 0.88F);
        }
        // Hem and ribbed waistband.
        if (ribbed(id) && bodyY > 11.2F) {
            float rib = bodyX * 4 - (float) Math.floor(bodyX * 4);
            color = Colors.shade(color, (rib < 0.5F ? 0.9F : 0.98F) * (bodyY < 11.32F ? 0.9F : 1));
        } else if (bodyY > 11.75F) {
            color = Colors.shade(color, 0.85F);
        }
        float light = volume(box, face, lx, ly, lz, 0.12F) * folds(ctx, face, bodyX, bodyY);
        return wear(ctx, Colors.shade(color, light), px, py, bodyY / 12);
    }

    private static int trim(Context ctx, BoxSpec box, Face face, float lx, float ly, float lz, int px, int py) {
        int color = fabric(ctx.shirt, px, py, ctx.seed);
        switch (box.name()) {
            case "hood" -> {
                if (face == Face.TOP && Math.abs(lx - box.w() / 2) < box.w() * 0.32F && lz < box.d() * 0.65F) {
                    color = Colors.shade(color, 0.5F);   // the hollow of the hood
                } else if (face == Face.TOP || face == Face.FRONT) {
                    color = Colors.shade(color, 0.88F);
                }
                if (face == Face.BACK) {
                    // The hood lying flat: a seam down its middle, folds at the bottom.
                    if (Math.abs(lx - box.w() / 2) < 0.08F) {
                        color = Colors.shade(color, 0.86F);
                    }
                    color = Colors.shade(color, 1.03F - 0.12F * ly / box.h());
                }
            }
            case "pocket" -> {
                if (face == Face.FRONT) {
                    boolean opening = (lx < 0.9F || lx > box.w() - 0.9F) && ly > 0.5F;
                    if (opening) {
                        color = Colors.shade(color, 0.86F);
                    }
                    if (Math.abs(lx - 0.9F) < 0.08F || Math.abs(lx - (box.w() - 0.9F)) < 0.08F || ly < 0.1F) {
                        color = Colors.shade(color, 0.78F);   // seams of the pocket
                    }
                }
                if (face == Face.TOP) {
                    color = Colors.shade(color, 0.7F);
                }
            }
            default -> {
                // Collars: lighter, with a dark fold at the bottom.
                color = Colors.lighten(color, 0.06F);
                if (face == Face.BOTTOM || (face == Face.FRONT && ly > box.h() - 0.3F)) {
                    color = Colors.shade(color, 0.78F);
                }
                if (face == Face.FRONT && ly < 0.2F) {
                    color = Colors.shade(color, 0.9F);
                }
            }
        }
        return wear(ctx, color, px, py, 0.2F);
    }

    // ----------------------------------------------------------------- pants

    private static int pants(Context ctx, BoxSpec box, Face face, float lx, float ly, float lz, int px, int py) {
        float legY = box.y() + ly;                 // 0 (hip) .. 11.2 (ankle)
        if (legY > ctx.pantsBottom) {
            return bareSkin(ctx, box, face, lx, ly, lz, px, py);
        }
        String id = ctx.pantsId;
        boolean left = box.bone() == Bone.LEFT_LEG;
        boolean outerSide = left ? face == Face.LEFT : face == Face.RIGHT;
        boolean innerSide = left ? face == Face.RIGHT : face == Face.LEFT;
        int color = id.equals("camo") ? camo(ctx.pants, Colors.shade(ctx.pants, 0.5F), px, py, ctx.seed) : fabric(ctx.pants, px, py, ctx.seed);
        float center = lx - box.w() / 2;
        switch (id) {
            case "jeans", "ripped_jeans", "cargo" -> {
                if (outerSide && Math.abs(lz - box.d() / 2) < 0.22F) {
                    color = Colors.shade(color, Math.abs(lz - box.d() / 2) < 0.07F ? 0.85F : 1.08F);   // outer seam, double stitched
                }
                if (innerSide && Math.abs(lz - box.d() / 2) < 0.1F) {
                    color = Colors.shade(color, 0.88F);
                }
                if (face == Face.FRONT) {
                    // Front pocket opening, a faded thigh and knee.
                    float pocket = (left ? 1 : -1) * center;
                    if (legY < 1.6F && Math.abs(pocket - (1.6F - legY * 0.9F)) < 0.09F) {
                        color = Colors.shade(color, 0.8F);
                    }
                    float fade = 1 - Math.min(1, Math.abs(legY - 4.5F) / 3.5F);
                    color = Colors.lighten(color, 0.05F * fade * (1 - Math.abs(center) / 2));
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
                        return Noise.hash(px, py, ctx.seed + 5) < 0.2F ? Colors.lighten(ctx.pants, 0.45F) : bareSkin(ctx, box, face, lx, ly, lz, px, py);
                    }
                }
            }
            case "trousers" -> {
                if (face == Face.FRONT && Math.abs(center) < 0.2F) {
                    color = Colors.lighten(color, 0.08F);   // the crease
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
        // Folds behind the knee.
        float light = volume(box, face, lx, ly, lz, 0.13F);
        if (face == Face.BACK && legY > 5.4F && legY < 6.8F) {
            float wave = (float) Math.sin(lx * 3.1F) * 0.15F;
            light *= 1 - 0.07F * Math.max(0, 1 - Math.abs(legY - 6.1F - wave) / 0.25F);
        }
        return wear(ctx, Colors.shade(color, light), px, py, legY / 12);
    }

    // ----------------------------------------------------------------- shoes

    private static int shoe(Context ctx, BoxSpec box, Face face, float lx, float ly, float lz, int px, int py) {
        String id = ctx.shoesId;
        boolean boot = box.name().startsWith("boot");
        float center = lx - box.w() / 2;
        switch (id) {
            case "barefoot" -> {
                int color = skinAt(ctx, box, face, lx, ly, px, py);
                if ((face == Face.TOP || face == Face.FRONT) && lz < box.d() * 0.35F) {
                    float t = lx / box.w() * 5;
                    if (Math.abs(t - Math.round(t)) < 0.14F && t > 0.4F && t < 4.6F) {
                        color = Colors.shade(color, 0.84F);   // toes
                    }
                }
                return lit(color, round(box, face, lx, lz, 0.1F));
            }
            case "sandals" -> {
                int color = skinAt(ctx, box, face, lx, ly, px, py);
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
        color = Colors.shade(color, round(box, face, lx, lz, 0.08F));
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

    // ------------------------------------------------------------------ belt

    private static int belt(Context ctx, BoxSpec box, Face face, float lx, float ly, float lz, int px, int py) {
        int color = Colors.shade(BELT, 1 + 0.05F * Noise.signed(px, py, ctx.seed + 12));
        float center = Math.abs(lx - box.w() / 2);
        if (face == Face.TOP) {
            color = Colors.shade(color, 1.12F);
        } else if (face == Face.FRONT && center < 0.7F) {
            // The buckle: a frame with the strap through it.
            boolean frame = center > 0.5F || ly < 0.18F || ly > box.h() - 0.18F;
            color = frame ? BUCKLE : Colors.shade(BELT, 0.8F);
            if (!frame && Math.abs(lx - box.w() / 2 + 0.15F) < 0.06F) {
                color = Colors.shade(BUCKLE, 0.75F);   // the prong
            }
        } else if (face != Face.BOTTOM) {
            if (ly < 0.12F || ly > box.h() - 0.12F) {
                color = Colors.shade(color, 0.78F);
            } else if (Math.abs(ly - 0.24F) < 0.04F || Math.abs(ly - (box.h() - 0.24F)) < 0.04F) {
                color = Colors.shade(color, 1.12F);   // stitching
            }
            // Belt loops of the trousers.
            float across = face == Face.FRONT || face == Face.BACK ? lx : lz;
            if (face != Face.FRONT || center > 1.5F) {
                float t = (across + 0.6F) / 2.2F;
                if (Math.abs(t - Math.round(t)) * 2.2F < 0.18F) {
                    color = Colors.shade(ctx.pants, 0.9F);
                }
            }
        }
        return Colors.shade(color, round(box, face, lx, lz, 0.1F));
    }

    // ------------------------------------------------------------------ hair

    /** How a cut is painted: lock width and tips, how messy, the slant of the fringe, the flow on top, the shine. */
    private record HairLook(float lock, float tip, float messy, float sweep, Flow flow, float sheen, Kind kind, float fringeTip) {
        enum Flow { CROWN, PART, MIDDLE, BACK }

        enum Kind { STRAIGHT, CURLY, DREADS }

        static HairLook of(String id) {
            return switch (id) {
                case "crew" -> new HairLook(0.85F, 0.35F, 0.25F, 0.1F, Flow.CROWN, 0.12F, Kind.STRAIGHT, 0.3F);
                case "short_messy" -> new HairLook(1.15F, 0.85F, 0.7F, 0.25F, Flow.CROWN, 0.12F, Kind.STRAIGHT, 0.85F);
                case "side_part" -> new HairLook(1.35F, 0.5F, 0.15F, 0.75F, Flow.PART, 0.17F, Kind.STRAIGHT, 0.5F);
                case "quiff" -> new HairLook(1.2F, 0.4F, 0.2F, 0, Flow.BACK, 0.17F, Kind.STRAIGHT, 0.3F);
                case "slicked_back" -> new HairLook(1.5F, 0.3F, 0.05F, 0, Flow.BACK, 0.24F, Kind.STRAIGHT, 0.2F);
                case "undercut" -> new HairLook(1.4F, 0.6F, 0.25F, 0.65F, Flow.PART, 0.16F, Kind.STRAIGHT, 0.6F);
                case "fade" -> new HairLook(0.85F, 0.3F, 0.15F, 0, Flow.CROWN, 0.1F, Kind.STRAIGHT, 0.25F);
                case "curly_short", "afro" -> new HairLook(0.9F, 0.35F, 0.3F, 0, Flow.CROWN, 0.1F, Kind.CURLY, 0.35F);
                case "dreads" -> new HairLook(1.1F, 0.4F, 0.3F, 0, Flow.CROWN, 0.08F, Kind.DREADS, 0.4F);
                case "shoulder_length" -> new HairLook(1.4F, 0.8F, 0.3F, 0, Flow.MIDDLE, 0.15F, Kind.STRAIGHT, 0.3F);
                default -> new HairLook(1.0F, 0.4F, 0.2F, 0, Flow.CROWN, 0.12F, Kind.STRAIGHT, 0.4F);
            };
        }
    }

    /** Five tones of a hair colour, from the deep shadow between locks to the highlight. */
    private static int[] hairRamp(int base) {
        float lum = Colors.luminance(base);
        // Dark hair gets a cool, visible sheen; light hair keeps its warmth in the shadows.
        int highlight = lum < 0.25F ? Colors.lerp(base, Colors.rgb(150, 150, 160), 0.38F) : Colors.lighten(base, 0.3F);
        return new int[]{
                Colors.shade(base, 0.42F),
                Colors.shade(base, 0.7F),
                base,
                Colors.lerp(base, highlight, 0.45F),
                highlight};
    }

    /** Colour on the hair ramp for a value 0 (deep shadow) .. 1 (highlight); 0.5 is the base colour. */
    private static int rampAt(int[] ramp, float value) {
        float v = Colors.clamp01(value) * (ramp.length - 1);
        int i = Math.min(ramp.length - 2, (int) Math.floor(v));
        return Colors.lerp(ramp[i], ramp[i + 1], v - i);
    }

    /**
     * A hair pixel. {@code lx, ly, lz} place it in the box (model units). Returns 0 where the slab is cut away
     * (between the tips of the locks), the colour otherwise (a little transparent on the very edge of a tip).
     *
     * <p>Surfaces stay calm and the edges are crisp: on top of the head the hair is one smooth mass with a sheen; down
     * the sides the locks show more and more towards their tips, each lit along one edge and in shadow along the
     * other (as if overlapping the next), and the slabs are cut out along the tips.
     */
    private static int hair(Context ctx, BoxSpec box, Face face, float lx, float ly, float lz) {
        HairLook look = ctx.look;
        String name = box.name();
        long seed = ctx.hairSeed;
        float hx = box.x() + lx;
        float hy = box.y() + ly;
        float hz = box.z() + lz;
        if (face == Face.TOP) {
            return Colors.opaque(hairTop(ctx, box, hx, hz));
        }
        if (face == Face.BOTTOM) {
            return rampAt(ctx.ramp, 0.16F);
        }
        boolean slab = name.startsWith("hair_front") || name.startsWith("hair_right") || name.startsWith("hair_left")
                || name.startsWith("hair_back");
        boolean quiff = name.startsWith("hair_quiff");
        // Locks falling down the sides: "across" runs across them, "down" from the top of the hair.
        float across = face == Face.FRONT || face == Face.BACK ? hx : hz;
        float down = quiff ? hy - box.y() : hy + 8.6F;
        if (name.startsWith("hair_front")) {
            // The fringe: the locks slant towards one side, or part in the middle and open outwards.
            float below = Math.max(0, hy + 8.15F);
            if (look.flow() == HairLook.Flow.MIDDLE) {
                across += Math.signum(hx) * 0.55F * below;
            } else {
                across -= look.sweep() * below;
            }
        }
        // Locks of uneven widths: the position across them is warped by a slow noise.
        float warp = across / look.lock() + 0.45F * (Noise.smooth(across / look.lock() * 0.8F, 0, 1, seed + 1) - 0.5F) * 2;
        int lockIndex = (int) Math.floor(warp);
        float t = warp - lockIndex;                          // 0..1 across the lock
        float lockTone = Noise.signed(lockIndex, 3, seed);
        // Coverage: the bottom edge of a slab follows the tips of its locks.
        float alpha = 1;
        float tipDepth = 99;
        if (slab) {
            float limit = slabLength(ctx, name, face, hx, hz);
            if (limit <= 0) {
                return 0;
            }
            boolean fringe = name.startsWith("hair_front");
            float variation = look.messy() * Noise.signed(lockIndex, 7, seed) * (fringe && look.flow() == HairLook.Flow.MIDDLE ? 0.3F : 0.8F);
            float tip = look.kind() == HairLook.Kind.CURLY
                    ? 0.35F * (float) Math.sqrt(Math.max(0, 1 - (2 * t - 1) * (2 * t - 1)))
                    : (fringe ? look.fringeTip() : look.tip()) * (1 - Math.abs(2 * t - 1));
            tipDepth = limit + variation + tip - (hy + 8);
            if (tipDepth <= 0) {
                return 0;
            }
            alpha = Math.min(1, tipDepth / 0.12F);
        }
        // Between the ropes of dreads: the hair underneath, in shadow.
        boolean between = look.kind() == HairLook.Kind.DREADS && slab && hy + 8 > 1.2F && Math.abs(t * 2 - 1) > 0.8F;
        float value;
        switch (look.kind()) {
            case CURLY -> value = curls(across, down, seed);
            case DREADS -> {
                float rope = 1 - (2 * t - 1) * (2 * t - 1);
                float twist = (down * 2.2F + t * 1.4F) - (float) Math.floor(down * 2.2F + t * 1.4F);
                value = 0.3F + 0.26F * rope + 0.06F * (twist < 0.5F ? 1 : -1) * rope + 0.04F * lockTone;
            }
            default -> {
                // Each lock overlaps the next: lit along one edge, in shadow along the other. Just under the top
                // of the head the locks lie together; they show more and more towards their tips.
                float lit = 1 - step(0.0F, 0.55F, t);
                float shadow = step(0.62F, 1.0F, t);
                float contrast = quiff ? 0.7F : 0.35F + 0.65F * step(0.2F, 1.6F, down);
                value = 0.5F + contrast * (0.1F * lit - 0.2F * shadow + 0.05F * lockTone)
                        + 0.012F * Noise.signed((int) Math.floor(across * 8), 11, seed);
            }
        }
        // Lighter on top, darker towards the tips (the front of the quiff is lit, its underside in shadow).
        value += quiff ? 0.08F - 0.22F * step(box.h() * 0.6F, box.h(), down) : 0.07F - 0.14F * Colors.clamp01(down / 6);
        if (slab) {
            value -= 0.12F * (1 - step(0, 0.25F, ly));     // in the shadow of the ring above
            if (tipDepth < 0.25F) {
                value -= 0.07F;                               // a fine dark line along the tips
            }
        }
        // Sheen: a soft band just under the top of the head, with strokes along the lit locks.
        float band = quiff ? 0 : 1 - Math.min(1, Math.abs(down - 1.0F) / 0.75F);
        if (look.kind() == HairLook.Kind.STRAIGHT) {
            boolean shiny = Noise.hash(lockIndex, 13, seed) < 0.65F;
            float stroke = 1 - Math.min(1, Math.abs(t - 0.3F) / 0.18F);
            value += (shiny ? look.sheen() * 2.0F * band * stroke : 0) + look.sheen() * 0.4F * band;
        } else {
            value += look.sheen() * 0.8F * band;
        }
        // The afro is a big soft mass: lit from above, shaded underneath.
        if (name.startsWith("hair_afro")) {
            value += 0.1F - 0.28F * step(0, box.h(), ly);
        }
        if (between) {
            value = 0.12F;
        }
        int color = rampAt(ctx.ramp, value);
        return alpha >= 1 ? color : Colors.fade(color, alpha);
    }

    /**
     * Value of curly hair: a tangle of small curls (smooth noise at two scales, so no rows or grid show), lit in
     * small spots where a curl catches the light.
     */
    private static float curls(float across, float along, long seed) {
        float x = across * 4;
        float y = along * 4;
        float coarse = Noise.smooth(x, y, 3.1F, seed + 5);
        float fine = Noise.smooth(x + 17, y - 9, 1.7F, seed + 6);
        float value = 0.42F + 0.2F * (coarse - 0.5F) * 2 + 0.1F * (fine - 0.5F) * 2;
        if (fine > 0.78F && coarse > 0.45F) {
            value += 0.08F;   // a curl catching the light
        }
        return value + 0.025F * Noise.signed((int) Math.floor(x), (int) Math.floor(y), seed + 7);
    }

    /**
     * The top of the head: one calm mass, combed in the direction of the cut (fine strokes, low contrast), a sheen
     * where the light catches the curve of the head, darker as it rolls over the edges; the parting for the cuts
     * that have one.
     */
    private static int hairTop(Context ctx, BoxSpec box, float hx, float hz) {
        HairLook look = ctx.look;
        long seed = ctx.hairSeed;
        float across;
        float along;
        switch (look.flow()) {
            case BACK -> {
                across = hx;
                along = hz + 4.5F;
            }
            case PART, MIDDLE -> {
                float part = look.flow() == HairLook.Flow.PART ? 1.6F : 0;
                across = hz + (hx > part ? 20 : 0);
                along = Math.abs(hx - part);
            }
            default -> {
                // Radiating from the crown, at the back of the head, with a little twist.
                float cz = hz - 1.8F;
                float r = (float) Math.sqrt(hx * hx + cz * cz);
                across = ((float) Math.atan2(cz, hx) + 0.7F / (r + 0.8F)) * 2.2F;
                along = r;
            }
        }
        float value;
        if (look.kind() == HairLook.Kind.CURLY) {
            value = 0.52F + 0.6F * (curls(hx * 1.0F, hz * 1.0F, seed) - 0.5F);
        } else {
            float warp = across / look.lock() + 0.45F * (Noise.smooth(across / look.lock() * 0.8F, 0, 1, seed + 1) - 0.5F) * 2;
            float t = warp - (float) Math.floor(warp);
            float strokes = 0.04F * (1 - step(0, 0.55F, t)) - 0.07F * step(0.62F, 1, t);
            if (look.flow() == HairLook.Flow.CROWN) {
                strokes *= step(0.5F, 2.4F, along);   // calm around the crown
            }
            value = 0.54F + strokes + 0.01F * Noise.signed((int) Math.floor(across * 8), (int) Math.floor(along * 2), seed + 11);
        }
        // Sheen across the front half of the top.
        float sheen = 1 - Math.min(1, Math.abs(hz + 1.2F) / 2.2F);
        value += look.sheen() * 1.3F * sheen * sheen;
        // Rolling over the edges.
        float edge = Math.max(Math.abs(hx) / (box.w() / 2), Math.abs(hz - (box.z() + box.d() / 2)) / (box.d() / 2));
        value -= 0.06F * step(0.75F, 1.0F, edge);
        if (look.flow() == HairLook.Flow.PART || look.flow() == HairLook.Flow.MIDDLE) {
            value -= 0.22F * (1 - step(0.05F, 0.22F, along));   // the parting: a thin line where the scalp shows
        }
        if (box.name().startsWith("hair_afro")) {
            value += 0.08F;
        }
        return rampAt(ctx.ramp, value);
    }

    /**
     * How far down a hair slab goes at a point of its face, in model units from the top of the head (the painted
     * locks add their tips below this). 0 where the slab is cut away entirely.
     */
    private static float slabLength(Context ctx, String name, Face face, float hx, float hz) {
        HairShape shape = ctx.hairShape;
        if (shape == null) {
            return 0;
        }
        if (name.startsWith("hair_front")) {
            float ax = Math.abs(hx);
            float length = shape.front();
            // Framing the face: the fringe joins the temples at its ends.
            if (ax > 3.4F) {
                length = Math.max(length, shape.temple() * step(3.4F, 4.4F, ax));
            }
            if (ctx.look.flow() == HairLook.Flow.MIDDLE) {
                length *= 0.55F + 0.45F * step(0, 2.8F, ax);
            }
            return length;
        }
        if (name.startsWith("hair_back")) {
            // The nape: a little rounded.
            float ax = Math.abs(hx) / 4.4F;
            return shape.back() - 0.5F * ax * ax;
        }
        // Sides: shorter by the face (temple), longer behind the ear.
        float front = Colors.clamp01((-hz + 4.4F) / 8.8F);   // 1 at the face, 0 at the back
        float temple = shape.temple();
        float side = shape.side();
        float t = step(0.55F, 0.85F, front);
        return side + (temple - side) * t;
    }
}
