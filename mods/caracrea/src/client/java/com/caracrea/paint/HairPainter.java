package com.caracrea.paint;

import com.caracrea.character.Appearance;
import com.caracrea.character.BodyType;
import com.caracrea.character.Catalog;
import com.caracrea.character.HairStyle;
import com.caracrea.character.HairStyle.Extra;
import com.caracrea.character.HairStyle.Fringe;
import com.caracrea.character.HairStyle.Texture;
import com.caracrea.character.Trait;
import com.caracrea.paint.SkinLayout.Face;
import com.caracrea.paint.SkinLayout.Part;

/**
 * Procedural hairstyles: a coverage function per face of the head (and of
 * the back of the torso for long hair), drawn on the inner layer and again
 * on the outer layer with feathered edges for volume, coloured strand by
 * strand.
 */
final class HairPainter {
    private HairPainter() {
    }

    static void paint(Canvas canvas, Appearance a) {
        HairStyle style = Catalog.hair(a);
        if (style.bald()) {
            return;
        }
        int base = Catalog.swatch(a, Trait.HAIR_COLOR).argb();
        int skin = BodyPainter.skinColor(a);
        long seed = a.seed(Trait.HAIR_STYLE);
        BodyType type = a.bodyType();
        Painter inner = new Painter(canvas, style, base, skin, seed, type, false);
        inner.head();
        inner.torso();
        if (style.volume()) {
            Painter outer = new Painter(canvas, style, base, skin, seed, type, true);
            outer.head();
            outer.torso();
        }
    }

    /** Hair of one layer (inner or outer). */
    private static final class Painter {
        private final Canvas canvas;
        private final HairStyle style;
        private final int base;
        private final int skin;
        private final long seed;
        private final BodyType type;
        private final boolean outer;
        /** Lengths shrink a little on the outer layer so the inner layer shows at the edges. */
        private final float trim;

        Painter(Canvas canvas, HairStyle style, int base, int skin, long seed, BodyType type, boolean outer) {
            this.canvas = canvas;
            this.style = style;
            this.base = base;
            this.skin = skin;
            this.seed = seed ^ (outer ? 0x7A7A7A7AL : 0);
            this.type = type;
            this.outer = outer;
            this.trim = outer ? (style.extra() == Extra.AFRO ? -4 : 1) : 0;
        }

        private Part headPart() {
            return outer ? Part.HAT : Part.HEAD;
        }

        // ------------------------------------------------------------ head

        void head() {
            for (Face face : Face.VALUES) {
                if (face == Face.BOTTOM) {
                    continue;
                }
                Pen pen = Pen.of(canvas, headPart(), face, type);
                pen.shade((lx, ly, current) -> {
                    float cover = coverHead(face, lx, ly);
                    float shaved = outer ? 0 : shavedHead(face, lx, ly);
                    int result = current;
                    if (shaved > 0) {
                        result = Colors.over(result, Colors.fade(Colors.shade(base, 0.9F), shaved * (0.75F + 0.25F * Noise.hash(lx, ly, seed + 9))));
                    }
                    if (cover <= 0) {
                        return result;
                    }
                    int color = strand(face, lx, ly, false);
                    if (outer) {
                        return Colors.alpha(color) == 0 ? result : Colors.over(result, Colors.fade(color, cover));
                    }
                    return Colors.over(result, Colors.fade(color, cover));
                });
            }
        }

        /** 1 where this layer has hair on a head face, 0 elsewhere (fractional at feathered edges). */
        private float coverHead(Face face, int lx, int ly) {
            float limit = switch (face) {
                case TOP -> coverTop(lx, ly) ? Float.MAX_VALUE : 0;
                case FRONT -> fringeHeight(lx);
                case RIGHT, LEFT -> sideLength(face, lx);
                case BACK -> backLength(lx);
                default -> 0;
            };
            if (limit <= 0) {
                return 0;
            }
            if (limit == Float.MAX_VALUE) {
                return holes(lx, ly, face) ? 0 : 1;
            }
            float edge = limit - trim - ly;
            if (edge <= 0) {
                return 0;
            }
            if (holes(lx, ly, face)) {
                return 0;
            }
            // Feathered tips: random strands drop out within the last two pixels.
            if (outer && edge < 2 && Noise.hash(lx, ly, seed + 3) < 0.45F) {
                return 0;
            }
            if (!outer && edge < 1 && Noise.hash(lx, ly, seed + 4) < 0.3F) {
                return 0;
            }
            return 1;
        }

        /** Texture gaps on the outer layer that let the inner layer show through. */
        private boolean holes(int lx, int ly, Face face) {
            if (!outer) {
                return false;
            }
            float h = Noise.hash(lx, ly, seed + 5);
            return switch (style.texture()) {
                case CURLY -> h < 0.2F;
                case WAVY -> h < 0.09F;
                case DREADS -> dreadGap(face, lx) > 0.72F;
                default -> h < 0.06F;
            };
        }

        /** 0 at the centre of a dread, 1 between two dreads. */
        private float dreadGap(Face face, int lx) {
            float period = 3.6F;
            float pos = (lx + (face == Face.BACK ? 1.3F : 0)) / period;
            float frac = pos - (float) Math.floor(pos);
            return Math.abs(frac - 0.5F) * 2;
        }

        private boolean coverTop(int lx, int ly) {
            if (!style.top()) {
                return false;
            }
            return switch (style.extra()) {
                case MOHAWK -> Math.abs(lx + 0.5F - 16) <= 3.5F + 0.6F * Noise.signed(0, ly, seed + 6);
                case SIDE_SHAVE -> lx >= 9 + Noise.hash(0, ly, seed + 7) * 2;
                default -> true;
            };
        }

        /** Height of the fringe (sub-pixels from the top of the forehead) at a column of the front face. */
        private float fringeHeight(int lx) {
            if (style.extra() == Extra.MOHAWK) {
                return Math.abs(lx + 0.5F - 16) <= 3.5F ? 4 : 0;
            }
            float f = style.fringe();
            if (f <= 0) {
                return 0;
            }
            float dx = lx + 0.5F - 16;
            float jitter = Noise.hash(lx, 0, seed + 8) < 0.35F ? 1 : 0;
            float h = switch (style.fringeShape()) {
                case NONE -> 0;
                case STRAIGHT -> f;
                case SIDE_SWEPT -> f * (0.3F + 0.7F * (lx / 31F));
                case MIDDLE_PART -> f * Colors.clamp01(Math.abs(dx) / 7F) + 1.5F;
                case MESSY -> f + 2.5F * (Noise.smooth(lx, 0, 3.5F, seed + 10) - 0.5F) * 2;
                case CURTAIN -> curtain(f, dx);
                case SPIKY -> f * (0.45F + 0.55F * Math.abs(((lx * 0.37F) % 1F) * 2 - 1));
            };
            if (style.extra() == Extra.SIDE_SHAVE && lx < 7) {
                h = 0;
            }
            // Hair framing the face: the outer columns follow the temple length.
            float edge = Math.min(lx, 31 - lx);
            float temple = style.templeLength();
            if (temple > h && edge < 3 && !(style.extra() == Extra.SIDE_SHAVE && lx < 7)) {
                float frame = edge == 0 ? temple : edge == 1 ? temple * 0.75F : temple * 0.4F;
                h = Math.max(h, Math.min(frame, 20 + (temple > 20 ? (temple - 20) : 0)));
            }
            return Math.max(0, h + jitter);
        }

        /** Parted in the middle with long pieces framing the face. */
        private static float curtain(float f, float dx) {
            float ax = Math.abs(dx);
            if (ax > 12) {
                return 18 + (ax - 12) * 1.5F;
            }
            if (ax > 8) {
                return f + (ax - 8) / 4 * (18 - f);
            }
            return f * 0.3F + f * 0.7F * (float) Math.sqrt(ax / 8);
        }

        /** How far down a side face the hair goes at a column; x grows towards the front on the RIGHT face. */
        private float sideLength(Face face, int lx) {
            float front = face == Face.RIGHT ? lx : 31 - lx;   // 31 next to the face
            Extra extra = style.extra();
            if (extra == Extra.MOHAWK) {
                return 0;
            }
            if (extra == Extra.SIDE_SHAVE && face == Face.RIGHT) {
                return 0;
            }
            float side = style.sideLength();
            float temple = style.templeLength();
            if (extra == Extra.AFRO) {
                side += 2.5F * (Noise.smooth(lx, 0, 4, seed + 11) - 0.5F) * 2;
                temple = side;
            }
            float t = Colors.clamp01((front - 20) / 8F);
            float length = side + (temple - side) * t;
            if (extra == Extra.UNDERCUT) {
                length = Math.min(length, 4);
            }
            float wave = style.texture() == Texture.WAVY ? 1.5F * (float) Math.sin(lx * 0.8F) : 0;
            return length + wave + jitterColumn(lx, face.ordinal());
        }

        private float backLength(int lx) {
            float dx = lx + 0.5F - 16;
            float back = style.backLength();
            switch (style.extra()) {
                case BUN -> {
                    // The knot: a disc below the hairline, slightly larger on the outer layer.
                    float radius = 6.5F + (outer ? 1.5F : 0) + 0.7F * Noise.signed(lx, 2, seed + 12);
                    float dy = (float) Math.sqrt(Math.max(0, radius * radius - dx * dx));
                    return Math.max(back, Math.abs(dx) <= radius ? 9 + dy : 0) + trim;
                }
                case MOHAWK -> {
                    return Math.abs(dx) <= 3.5F + 0.6F * Noise.signed(lx, 1, seed + 12) ? back : 0;
                }
                case SIDE_SHAVE -> {
                    // On the back face, larger x is the player's right (the shaved side).
                    if (lx > 22) {
                        return 0;
                    }
                    back = lx > 18 ? back * (22 - lx) / 4F : back;
                }
                case AFRO -> back += 2.5F * (Noise.smooth(lx, 1, 4, seed + 13) - 0.5F) * 2;
                default -> {
                }
            }
            float wave = style.texture() == Texture.WAVY ? 1.2F * (float) Math.sin(lx * 0.7F + 1) : 0;
            return back + wave + jitterColumn(lx, 7);
        }

        private float jitterColumn(int lx, int salt) {
            return Noise.hash(lx, salt, seed + 14) < 0.35F ? 1 : 0;
        }

        /** Shaved parts of the head (inner layer only): short stubble in the hair colour over the skin. */
        private float shavedHead(Face face, int lx, int ly) {
            Extra extra = style.extra();
            if (extra != Extra.MOHAWK && extra != Extra.UNDERCUT && extra != Extra.SIDE_SHAVE) {
                return 0;
            }
            switch (face) {
                case TOP -> {
                    return extra == Extra.MOHAWK || extra == Extra.SIDE_SHAVE ? (coverTop(lx, ly) ? 0 : 0.32F) : 0;
                }
                case RIGHT, LEFT -> {
                    if (extra == Extra.SIDE_SHAVE && face == Face.LEFT) {
                        return 0;
                    }
                    if (extra == Extra.UNDERCUT) {
                        return ly < 4 ? 0 : 0.3F * (1 - (ly - 4) / 40F);
                    }
                    return ly < 14 ? 0.3F : 0;
                }
                case BACK -> {
                    if (extra == Extra.MOHAWK) {
                        return Math.abs(lx + 0.5F - 16) > 3.5F ? 0.3F : 0;
                    }
                    if (extra == Extra.SIDE_SHAVE) {
                        return lx > 20 && ly < 28 ? 0.3F : 0;
                    }
                    return ly >= 8 && ly < 16 ? 0.3F * (1 - (ly - 8) / 10F) : 0;
                }
                case FRONT -> {
                    return extra == Extra.MOHAWK && ly < 3 ? 0.25F : 0;
                }
                default -> {
                    return 0;
                }
            }
        }

        // ----------------------------------------------------------- torso

        void torso() {
            if (style.torsoLength() <= 0) {
                return;
            }
            Part part = outer ? Part.JACKET : Part.BODY;
            Pen back = Pen.of(canvas, part, Face.BACK, type);
            back.shade((lx, ly, current) -> {
                float cover = coverTorsoBack(lx, ly);
                if (cover <= 0) {
                    return current;
                }
                return Colors.over(current, Colors.fade(strandTorso(lx, ly), cover));
            });
            // Hair resting on the back half of the shoulders.
            if (style.torsoLength() >= 8 && style.extra() != Extra.PONYTAIL && style.extra() != Extra.BRAID) {
                Pen top = Pen.of(canvas, part, Face.TOP, type);
                top.shade((lx, ly, current) -> ly < 7 - trim && !(outer && Noise.hash(lx, ly, seed + 15) < 0.2F)
                        ? Colors.over(current, strandTorso(lx, ly)) : current);
                if (style.torsoLength() >= 16) {
                    for (Part arm : new Part[]{Part.RIGHT_ARM, Part.LEFT_ARM}) {
                        Part armPart = outer ? arm.outerLayer() : arm;
                        Pen armTop = Pen.of(canvas, armPart, Face.TOP, type);
                        boolean leftArm = arm.isLeft();
                        armTop.shade((lx, ly, current) -> {
                            // Only the half of the shoulder next to the neck, and the back of it.
                            float inner = leftArm ? lx : armTop.width() - 1 - lx;
                            if (ly >= 7 - trim || inner > armTop.width() * 0.75F) {
                                return current;
                            }
                            if (outer && Noise.hash(lx, ly, seed + 16) < 0.25F) {
                                return current;
                            }
                            return Colors.over(current, strandTorso(lx + 40, ly));
                        });
                    }
                }
            }
            if (style.extra() == Extra.FRONT_STRANDS) {
                Pen front = Pen.of(canvas, part, Face.FRONT, type);
                float length = Math.min(14, style.torsoLength() * 0.6F);
                front.shade((lx, ly, current) -> {
                    float edge = Math.min(lx, 31 - lx);
                    float width = 3.5F + 1.5F * Noise.smooth(ly, 0, 4, seed + 17) - ly * 0.1F;
                    if (edge > width || ly > length - trim - (edge * 1.5F)) {
                        return current;
                    }
                    if (outer && Noise.hash(lx, ly, seed + 18) < 0.25F) {
                        return current;
                    }
                    return Colors.over(current, strandTorso(lx + 80, ly));
                });
            }
        }

        private float coverTorsoBack(int lx, int ly) {
            float dx = lx + 0.5F - 16;
            float length = style.torsoLength() - trim;
            switch (style.extra()) {
                case PONYTAIL -> {
                    float width = ly < 3 ? 4.5F : 3.5F - 1.0F * (ly / length);
                    return Math.abs(dx) <= width && ly < length + jitterColumn(lx, 20) ? 1 : 0;
                }
                case BRAID -> {
                    return Math.abs(dx) <= 3.5F && ly < length ? 1 : 0;
                }
                case SIDE_SHAVE -> {
                    // Hair falls on the player's left only (small x on the back face).
                    float limit = 18 - ly * 0.25F;
                    return lx < limit && ly < length + jitterColumn(lx, 21) ? feather(lx, ly, limit - lx) : 0;
                }
                default -> {
                    float half = 16 - ly * 0.22F;
                    if (style.texture() == Texture.WAVY) {
                        half += 1.5F * (float) Math.sin(ly * 0.5F + lx * 0.3F);
                    }
                    if (style.texture() == Texture.DREADS && dreadGap(Face.BACK, lx) > (outer ? 0.72F : 0.9F)) {
                        return 0;
                    }
                    float edge = Math.min(half - Math.abs(dx), length + jitterColumn(lx, 22) - ly);
                    return edge <= 0 ? 0 : feather(lx, ly, edge);
                }
            }
        }

        private float feather(int lx, int ly, float edge) {
            if (edge < 2 && Noise.hash(lx, ly, seed + 23) < (outer ? 0.45F : 0.25F)) {
                return 0;
            }
            return 1;
        }

        // ---------------------------------------------------------- colour

        /** Colour of a hair pixel on the head. */
        private int strand(Face face, int lx, int ly, boolean torso) {
            if (style.extra() == Extra.BUN && face == Face.BACK) {
                float dx = lx + 0.5F - 16;
                float dy = ly + 0.5F - 9;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d > 4.5F && d < 7.5F) {
                    return Colors.shade(base, 0.78F + 0.08F * Noise.signed(lx, ly, seed + 40));
                }
                if (d <= 4.5F) {
                    return Colors.shade(base, 1.0F + 0.08F * (float) Math.sin(Math.atan2(dy, dx) * 5) + 0.05F * Noise.signed(lx, ly, seed + 41));
                }
            }
            if (style.texture() == Texture.BUZZ) {
                float grain = 1 + 0.05F * Noise.signed(lx, ly, seed + 30);
                return Colors.shade(Colors.lerp(skin, base, 0.74F), grain);
            }
            float light = strandLight(lx, ly, face == Face.TOP ? ly : lx);
            // Highlights: a band across the top of the head and along the top of the fringe.
            float shine = switch (face) {
                case TOP -> 0.14F * Math.max(0, 1 - Math.abs(ly - 11) / 7F);
                case FRONT -> ly < 3 ? 0.08F : 0;
                default -> 0;
            };
            int color = Colors.shade(base, light);
            if (outer) {
                color = Colors.shade(color, 1.04F);
            }
            if (shine > 0) {
                color = Colors.lighten(color, shine);
            }
            // Tips are a touch darker, so the layered edges read as depth.
            return color;
        }

        private int strandTorso(int lx, int ly) {
            float light = strandLight(lx, ly, lx);
            int color = Colors.shade(base, light * (outer ? 1.03F : 0.97F));
            if (style.extra() == Extra.BRAID) {
                int segment = (int) Math.floor(ly / 3.2F) + (lx + 0.5F > 16 ? 1 : 0);
                color = Colors.shade(color, segment % 2 == 0 ? 0.82F : 1.1F);
                if (ly >= style.torsoLength() - 3 - trim) {
                    color = Colors.shade(color, 0.6F); // the tie at the end
                }
            }
            if (style.extra() == Extra.PONYTAIL && ly >= 1 && ly < 3) {
                color = Colors.shade(color, 0.55F); // hair tie
            }
            return color;
        }

        /** Per-strand lightness: vertical streaks, waves or curls depending on the texture. */
        private float strandLight(int lx, int ly, int along) {
            switch (style.texture()) {
                case WAVY -> {
                    int column = Math.round(lx + 2 * (float) Math.sin(ly * 0.45F + Noise.hash(0, 0, seed) * 6));
                    return 1 + 0.11F * Noise.signed(column, 0, seed + 31) + 0.05F * (Noise.smooth(lx, ly, 3, seed + 32) - 0.5F) * 2;
                }
                case CURLY -> {
                    return 1 + 0.2F * (Noise.smooth(lx, ly, 2.4F, seed + 33) - 0.5F) * 2 + 0.05F * Noise.signed(lx, ly, seed + 34);
                }
                case DREADS -> {
                    float period = 3.6F;
                    int strip = (int) Math.floor(lx / period);
                    float gap = dreadGap(Face.FRONT, lx);
                    float within = 1.05F - 0.35F * gap * gap;
                    return within * (1 + 0.1F * Noise.signed(strip, 0, seed + 35)) + 0.04F * Noise.signed(lx, ly, seed + 36);
                }
                default -> {
                    return 1 + 0.1F * Noise.signed(along, 0, seed + 37) + 0.05F * (Noise.smooth(lx, ly, 3, seed + 38) - 0.5F) * 2
                            + 0.02F * Noise.signed(lx, ly, seed + 39);
                }
            }
        }
    }
}
