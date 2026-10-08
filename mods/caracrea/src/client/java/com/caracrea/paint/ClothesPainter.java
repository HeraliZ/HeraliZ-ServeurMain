package com.caracrea.paint;

import com.caracrea.character.Appearance;
import com.caracrea.character.BodyType;
import com.caracrea.character.Catalog;
import com.caracrea.character.Trait;
import com.caracrea.paint.SkinLayout.Face;
import com.caracrea.paint.SkinLayout.Part;

import java.util.ArrayList;
import java.util.List;

/** Shirts, trousers and shoes, then dust, dirt or blood over them. */
final class ClothesPainter {
    private ClothesPainter() {
    }

    static void paint(Canvas canvas, Appearance a) {
        new Painter(canvas, a).paintAll();
    }

    private static final class Painter {
        private static final int BELT = Colors.rgb(58, 40, 26);
        private static final int BUCKLE = Colors.rgb(196, 176, 124);
        private static final int LACE = Colors.rgb(228, 226, 220);

        private final Canvas canvas;
        private final Appearance a;
        private final BodyType type;
        private final long seed;
        private final int skin;
        private final int shirt;
        private final int shirt2;
        private final int pants;
        private final int shoes;
        private final int shirtStyle;
        private final int pantsStyle;
        private final int shoesStyle;
        /** Everything covered by clothes, for the wear pass. */
        private final List<Rect> cloth = new ArrayList<>();

        Painter(Canvas canvas, Appearance a) {
            this.canvas = canvas;
            this.a = a;
            this.type = a.bodyType();
            this.seed = a.seed(Trait.SHIRT_STYLE, Trait.PANTS_STYLE, Trait.SHOES_STYLE, Trait.WEAR) + 300;
            this.skin = BodyPainter.skinColor(a);
            this.shirt = Catalog.swatch(a, Trait.SHIRT_COLOR).argb();
            this.shirt2 = Catalog.swatch(a, Trait.SHIRT_COLOR2).argb();
            this.pants = Catalog.swatch(a, Trait.PANTS_COLOR).argb();
            this.shoes = Catalog.swatch(a, Trait.SHOES_COLOR).argb();
            this.shirtStyle = a.get(Trait.SHIRT_STYLE);
            this.pantsStyle = a.get(Trait.PANTS_STYLE);
            this.shoesStyle = a.get(Trait.SHOES_STYLE);
        }

        void paintAll() {
            pants();
            shoes();
            shirt();
            wear();
        }

        // ---------------------------------------------------------- helpers

        private Pen pen(Part part, Face face) {
            return Pen.of(canvas, part, face, type);
        }

        /** Fabric colour with gentle folds and darker edges, so garments do not look flat. */
        private int fabric(int base, Pen pen, int lx, int ly) {
            float folds = 1 + 0.045F * (Noise.fbm(pen.ux() + lx, pen.uy() + ly, 5, seed) - 0.5F) * 2;
            float grain = 1 + 0.012F * Noise.signed(lx, ly, seed + 1);
            float edge = lx == 0 || lx == pen.width() - 1 ? 0.93F : 1.0F;
            return Colors.shade(base, folds * grain * edge);
        }

        /** Four-tone camouflage, continuous across the canvas. */
        private int camo(int base, int alt, Pen pen, int lx, int ly) {
            int x = pen.ux() + lx;
            int y = pen.uy() + ly;
            float n1 = Noise.fbm(x, y, 6.5F, seed + 2);
            float n2 = Noise.smooth(x, y, 3.8F, seed + 3);
            int tone;
            if (n1 < 0.4F) {
                tone = Colors.shade(base, 0.62F);
            } else if (n1 > 0.63F) {
                tone = Colors.lighten(base, 0.22F);
            } else {
                tone = n2 > 0.58F ? alt : base;
            }
            return Colors.shade(tone, 1 + 0.02F * Noise.signed(lx, ly, seed + 4));
        }

        /** Fills rows [y0, y1) of the side faces of a part with a shader. */
        private void sides(Part part, int y0, int y1, FaceShader shader) {
            for (Face face : Face.SIDES) {
                Pen pen = pen(part, face);
                pen.shade(0, y0, pen.width(), y1 - y0, (lx, ly, current) -> shader.shade(face, pen, lx, ly, current));
                cloth.add(pen.region(0, y0, pen.width(), y1 - y0));
            }
        }

        @FunctionalInterface
        private interface FaceShader {
            int shade(Face face, Pen pen, int lx, int ly, int current);
        }

        // ------------------------------------------------------------ shirt

        private void shirt() {
            String id = Catalog.SHIRTS.get(shirtStyle).id();
            int torsoBottom = id.equals("crop_top") ? 30 : 48;
            int sleeves = switch (id) {
                case "tank_top" -> 0;
                case "crop_top" -> 13;
                case "tshirt", "vneck", "striped", "polo", "camo" -> 17;
                case "flannel" -> 32;
                case "sweater", "long_sleeve", "henley", "hoodie", "jacket", "dress_shirt" -> 40;
                default -> 17;
            };
            boolean camo = id.equals("camo");
            boolean jacket = id.equals("jacket");
            int torsoColor = jacket ? shirt2 : shirt;

            // Torso.
            for (Face face : Face.SIDES) {
                Pen pen = pen(Part.BODY, face);
                pen.shade(0, 0, pen.width(), torsoBottom, (lx, ly, current) -> {
                    if (!covered(id, face, pen, lx, ly)) {
                        return current;
                    }
                    int color = camo ? camo(shirt, shirt2, pen, lx, ly) : fabric(torsoColor, pen, lx, ly);
                    color = pattern(id, color, pen, lx, ly);
                    color = torsoShape(color, face, pen, lx, ly);
                    color = neckline(id, color, face, lx, ly, current);
                    if (ly == torsoBottom - 1 || (torsoBottom < 48 && ly == torsoBottom - 2)) {
                        color = Colors.shade(color, 0.85F);
                    }
                    return color;
                });
                cloth.add(pen.region(0, 0, pen.width(), torsoBottom));
            }
            // Shoulders (top of the torso) except the neck hole.
            Pen top = pen(Part.BODY, Face.TOP);
            if (!id.equals("tank_top")) {
                top.shade((lx, ly, current) -> Math.abs(lx + 0.5F - 16) < 4.5F ? current
                        : (camo ? camo(shirt, shirt2, top, lx, ly) : fabric(torsoColor, top, lx, ly)));
            } else {
                top.shade((lx, ly, current) -> Math.abs(Math.abs(lx + 0.5F - 16) - 7) < 2 ? fabric(shirt, top, lx, ly) : current);
            }
            // Bottom of the torso: hidden by the trousers.
            pen(Part.BODY, Face.BOTTOM).shade((lx, ly, current) -> Colors.shade(pants, 0.8F));

            // Sleeves.
            if (sleeves > 0) {
                int sleeveColor = jacket ? shirt : torsoColor;
                for (Part arm : new Part[]{Part.RIGHT_ARM, Part.LEFT_ARM}) {
                    sides(arm, 0, sleeves, (face, pen, lx, ly, current) -> {
                        int color = camo ? camo(shirt, shirt2, pen, lx, ly) : fabric(sleeveColor, pen, lx, ly);
                        color = pattern(id, color, pen, lx, ly);
                        if (ly == 0) {
                            color = Colors.shade(color, 0.86F); // shoulder seam
                        }
                        if (ly >= sleeves - 2) {
                            color = Colors.shade(color, id.equals("sweater") || id.equals("hoodie") ? (lx % 2 == 0 ? 0.88F : 0.98F) : 0.88F);
                        }
                        if (id.equals("flannel") && ly >= sleeves - 4) {
                            color = Colors.shade(color, 0.9F); // rolled cuff
                        }
                        return color;
                    });
                    Pen armTop = pen(arm, Face.TOP);
                    armTop.shade((lx, ly, current) -> camo ? camo(shirt, shirt2, armTop, lx, ly) : fabric(sleeveColor, armTop, lx, ly));
                }
            }
            outerShirt(id, sleeves);
        }

        /** Tank tops and crop tops leave skin uncovered. */
        private boolean covered(String id, Face face, Pen pen, int lx, int ly) {
            if (!id.equals("tank_top")) {
                return true;
            }
            if (face == Face.RIGHT || face == Face.LEFT) {
                return ly >= 11;
            }
            float dx = Math.abs(lx + 0.5F - 16);
            if (ly < 4) {
                return Math.abs(dx - 7) < 2;
            }
            if (ly < 10) {
                return dx < 9.5F + (ly - 4) * 1.3F;
            }
            return true;
        }

        private int pattern(String id, int color, Pen pen, int lx, int ly) {
            int x = pen.ux() + lx;
            int y = pen.uy() + ly;
            return switch (id) {
                case "flannel" -> {
                    boolean v = (x % 9) < 3;
                    boolean h = (y % 9) < 3;
                    if (v && h) {
                        yield Colors.lerp(color, shirt2, 0.85F);
                    }
                    yield v || h ? Colors.lerp(color, shirt2, 0.55F) : color;
                }
                case "striped" -> ((ly / 4) % 2 == 1) ? Colors.lerp(color, shirt2, 0.9F) : color;
                case "sweater" -> Colors.shade(color, 0.965F + 0.07F * ((lx + ly) & 1));
                default -> color;
            };
        }

        /** Chest and waist shading that reads as a body under the fabric. */
        private int torsoShape(int color, Face face, Pen pen, int lx, int ly) {
            if (face != Face.FRONT) {
                return face == Face.BACK && ly >= 6 && ly <= 30 && Math.abs(lx + 0.5F - 16) < 1.2F
                        ? Colors.shade(color, 0.95F) : color;
            }
            float dx = lx + 0.5F - 16;
            if (type == BodyType.FEMALE) {
                float best = 0;
                for (int s = -1; s <= 1; s += 2) {
                    float ex = (dx - s * 5.5F) / 5F;
                    float ey = (ly + 0.5F - 14) / 4.2F;
                    float d = (float) Math.sqrt(ex * ex + ey * ey);
                    best = Math.max(best, 1 - d);
                }
                if (best > 0) {
                    color = Colors.lighten(color, 0.045F * best);
                }
                if (ly >= 18 && ly <= 20 && Math.abs(dx) < 10.5F) {
                    color = Colors.shade(color, 0.95F + 0.015F * (ly - 18));
                }
                if (ly >= 24 && ly < 42 && Math.abs(dx) > 12.5F) {
                    color = Colors.shade(color, 0.93F);
                }
            } else {
                if (ly >= 7 && ly <= 17) {
                    color = Colors.lighten(color, 0.035F * (1 - Math.abs(ly - 12) / 6F) * (1 - Math.abs(dx) / 18F));
                }
            }
            return color;
        }

        /** Neck opening and collar details; returns the skin pixel where the neckline is open. */
        private int neckline(String id, int color, Face face, int lx, int ly, int skinPixel) {
            if (face != Face.FRONT && face != Face.BACK) {
                return color;
            }
            float dx = Math.abs(lx + 0.5F - 16);
            float y = ly + 0.5F;
            boolean back = face == Face.BACK;
            switch (id) {
                case "vneck" -> {
                    if (!back && y < 6.5F - dx * 1.4F) {
                        return skinPixel;
                    }
                    if (!back && y < 7.5F - dx * 1.4F) {
                        return Colors.shade(color, 0.85F);
                    }
                }
                case "polo", "dress_shirt" -> {
                    boolean open = !back && dx < 1.6F && y < 4.5F;
                    if (open) {
                        return skinPixel;
                    }
                    if (y < 5 && dx < 8.5F && (back || dx > 1.2F)) {
                        return Colors.shade(color, y < 1.5F ? 0.85F : 1.08F); // collar
                    }
                    if (!back && dx < 0.8F && y >= 5 && ((int) y - 5) % 4 == 1 && (id.equals("dress_shirt") || y < 13)) {
                        return Colors.shade(color, 0.7F); // buttons
                    }
                    if (!back && id.equals("dress_shirt") && dx < 1.6F && y >= 5) {
                        return Colors.shade(color, 0.94F); // placket
                    }
                    if (!back && id.equals("dress_shirt") && lx >= 20 && lx <= 27 && ly >= 11 && ly <= 18
                            && (lx == 20 || lx == 27 || ly == 11 || ly == 18)) {
                        return Colors.shade(color, 0.88F); // pocket
                    }
                }
                case "henley" -> {
                    if (!back && dx < 1.6F && y >= 1 && y < 13) {
                        if (dx < 0.8F && ((int) y - 1) % 4 == 1) {
                            return Colors.lighten(color, 0.3F);
                        }
                        return Colors.shade(color, 0.92F);
                    }
                    if (y < 2 && dx < 4.5F) {
                        return skinPixel;
                    }
                    if (y < 3 && dx < 5.5F) {
                        return Colors.shade(color, 0.86F);
                    }
                }
                case "tank_top", "crop_top" -> {
                    if (!back && y < 3 && dx < 5) {
                        return skinPixel;
                    }
                }
                default -> {
                    if (y < 2 && dx < 4.5F) {
                        return skinPixel;
                    }
                    if (y < 3 && dx < 5.5F) {
                        return Colors.shade(color, 0.86F); // crew neck rib
                    }
                }
            }
            return color;
        }

        /** Hood, open jacket and kangaroo pocket on the outer layer. */
        private void outerShirt(String id, int sleeves) {
            if (id.equals("hoodie")) {
                Pen back = pen(Part.JACKET, Face.BACK);
                back.shade(0, 0, 32, 12, (lx, ly, current) -> {
                    float dx = Math.abs(lx + 0.5F - 16);
                    float limit = 14.5F - Math.max(0, ly - 6) * 1.8F;
                    if (dx > limit) {
                        return current;
                    }
                    int color = fabric(shirt, back, lx, ly);
                    return dx > limit - 1.5F || ly >= 10 ? Colors.shade(color, 0.82F) : color;
                });
                cloth.add(back.region(0, 0, 32, 12));
                Pen front = pen(Part.JACKET, Face.FRONT);
                front.shade((lx, ly, current) -> {
                    float dx = Math.abs(lx + 0.5F - 16);
                    // Hood edges on the shoulders.
                    if (ly < 5 && dx > 4.5F && dx < 10.5F - ly) {
                        return Colors.shade(fabric(shirt, front, lx, ly), 0.9F);
                    }
                    // Drawstrings.
                    if ((lx == 13 || lx == 18) && ly >= 3 && ly < 15) {
                        return ly >= 13 ? Colors.rgb(90, 90, 90) : Colors.rgb(215, 215, 210);
                    }
                    // Kangaroo pocket.
                    if (lx >= 6 && lx <= 25 && ly >= 30 && ly <= 44) {
                        int color = fabric(shirt, front, lx, ly);
                        boolean edge = ly == 30 || ly == 44 || ((lx == 6 || lx == 25) && ly > 33);
                        boolean opening = (lx == 7 || lx == 24) && ly > 30 && ly < 38;
                        return edge || opening ? Colors.shade(color, 0.8F) : color;
                    }
                    return current;
                });
                cloth.add(front.region(6, 30, 20, 15));
            } else if (id.equals("jacket")) {
                for (Face face : Face.SIDES) {
                    Pen pen = pen(Part.JACKET, face);
                    pen.shade((lx, ly, current) -> {
                        float dx = lx + 0.5F - 16;
                        if (face == Face.FRONT) {
                            float open = 5.5F - Math.min(ly, 10) * 0.25F;
                            if (Math.abs(dx) < open) {
                                return current; // open front shows the shirt underneath
                            }
                            int color = fabric(shirt, pen, lx, ly);
                            if (Math.abs(dx) < open + 1.2F) {
                                return Colors.shade(color, 0.8F); // zip edge
                            }
                            if (ly < 7 && Math.abs(dx) < open + 6 - ly * 0.6F) {
                                return Colors.lighten(color, 0.1F); // lapels
                            }
                            if (ly >= 28 && ly <= 36 && Math.abs(Math.abs(dx) - 9.5F) < 4 && (ly == 28 || Math.abs(Math.abs(dx) - 9.5F) > 3.3F)) {
                                return Colors.shade(color, 0.82F); // pockets
                            }
                            return color;
                        }
                        int color = fabric(shirt, pen, lx, ly);
                        return ly == 47 ? Colors.shade(color, 0.85F) : color;
                    });
                    cloth.add(pen.face());
                }
                Pen top = pen(Part.JACKET, Face.TOP);
                top.shade((lx, ly, current) -> Math.abs(lx + 0.5F - 16) < 5 ? current : fabric(shirt, top, lx, ly));
                for (Part sleeve : new Part[]{Part.RIGHT_SLEEVE, Part.LEFT_SLEEVE}) {
                    sides(sleeve, 0, sleeves, (face, pen, lx, ly, current) -> {
                        int color = fabric(shirt, pen, lx, ly);
                        return ly >= sleeves - 3 || ly == 0 ? Colors.shade(color, 0.86F) : color;
                    });
                }
            }
        }

        // ------------------------------------------------------------ pants

        private void pants() {
            String id = Catalog.PANTS.get(pantsStyle).id();
            int bottom = switch (id) {
                case "shorts" -> 24;
                case "skirt" -> 20;
                case "joggers", "track" -> 46;
                default -> 48;
            };
            boolean belt = id.equals("jeans") || id.equals("cargo") || id.equals("trousers") || id.equals("ripped_jeans") || id.equals("shorts");
            boolean camo = id.equals("camo");
            for (Part leg : new Part[]{Part.RIGHT_LEG, Part.LEFT_LEG}) {
                boolean left = leg.isLeft();
                sides(leg, 0, bottom, (face, pen, lx, ly, current) -> {
                    int color = camo ? camo(pants, Colors.shade(pants, 0.5F), pen, lx, ly) : fabric(pants, pen, lx, ly);
                    boolean outerSide = (left && face == Face.LEFT) || (!left && face == Face.RIGHT);
                    boolean innerSide = (left && face == Face.RIGHT) || (!left && face == Face.LEFT);
                    switch (id) {
                        case "jeans", "ripped_jeans", "cargo" -> {
                            if (outerSide && (lx == 7 || lx == 8)) {
                                color = Colors.shade(color, 1.1F); // outer seam
                            }
                            if (innerSide && lx == 8) {
                                color = Colors.shade(color, 0.9F);
                            }
                            if (face == Face.FRONT) {
                                float knee = 1 - Math.abs(ly - 23) / 5F;
                                if (knee > 0) {
                                    color = Colors.lighten(color, 0.06F * knee);
                                }
                                int inner = left ? 0 : pen.width() - 1;
                                if (Math.abs(lx - inner) <= 1 && ly >= 2 && ly < 11 && !id.equals("cargo")) {
                                    color = Colors.shade(color, 0.88F); // fly
                                }
                            }
                            if (face == Face.BACK && lx >= 3 && lx <= 12 && ly >= 4 && ly <= 11
                                    && (lx == 3 || lx == 12 || ly == 4 || ly == 11)) {
                                color = Colors.shade(color, 0.84F); // back pocket
                            }
                        }
                        case "trousers" -> {
                            if (face == Face.FRONT && (lx == 7 || lx == 8)) {
                                color = Colors.lighten(color, 0.08F); // crease
                            }
                        }
                        case "joggers", "track" -> {
                            if (ly < 3 || ly >= 41) {
                                color = Colors.shade(color, (lx & 1) == 0 ? 0.88F : 0.98F); // ribbing
                            }
                            if (id.equals("track") && outerSide && (lx == 4 || lx == 5 || lx == 10 || lx == 11)) {
                                color = Colors.lerp(color, Colors.rgb(236, 236, 230), 0.9F);
                            }
                        }
                        case "leggings" -> {
                            if (face == Face.FRONT && lx >= 6 && lx <= 8) {
                                color = Colors.lighten(color, 0.06F);
                            }
                            if (ly < 2) {
                                color = Colors.shade(color, 0.85F);
                            }
                        }
                        case "skirt" -> {
                            color = Colors.shade(color, ((pen.ux() + lx) / 4) % 2 == 0 ? 0.94F : 1.0F); // pleats
                            if (ly < 2) {
                                color = Colors.shade(color, 0.8F);
                            }
                        }
                        default -> {
                        }
                    }
                    if (ly == bottom - 1 && bottom < 48) {
                        color = Colors.shade(color, 0.85F);
                    }
                    if (ly == 47 && bottom == 48 && !id.equals("leggings")) {
                        color = Colors.shade(color, 0.88F);
                    }
                    if (belt && ly < 2) {
                        color = BELT;
                    }
                    return color;
                });
            }
            if (belt) {
                for (Face face : Face.SIDES) {
                    Pen pen = pen(Part.BODY, face);
                    pen.shade(0, 46, pen.width(), 2, (lx, ly, current) -> face == Face.FRONT && Math.abs(lx + 0.5F - 16) < 1.6F ? BUCKLE : BELT);
                }
            }
            if (id.equals("ripped_jeans")) {
                ripped();
            }
            if (id.equals("cargo") || id.equals("skirt")) {
                outerPants(id);
            }
        }

        private void ripped() {
            for (Part leg : new Part[]{Part.RIGHT_LEG, Part.LEFT_LEG}) {
                Pen pen = pen(leg, Face.FRONT);
                boolean left = leg.isLeft();
                int x0 = left ? 3 : 4;
                int y0 = left ? 20 : 30;
                int w = left ? 10 : 8;
                int h = left ? 6 : 3;
                pen.shade(x0 - 1, y0 - 1, w + 2, h + 2, (lx, ly, current) -> {
                    boolean inside = lx >= x0 && lx < x0 + w && ly >= y0 && ly < y0 + h
                            && !((lx == x0 || lx == x0 + w - 1) && (ly == y0 || ly == y0 + h - 1));
                    if (inside) {
                        boolean thread = Noise.hash(lx, ly, seed + 5) < 0.18F && (ly == y0 || ly == y0 + h - 1 || Noise.hash(lx, ly, seed + 6) < 0.3F);
                        return thread ? Colors.lighten(pants, 0.45F) : Colors.shade(skin, 0.95F + 0.04F * Noise.signed(lx, ly, seed));
                    }
                    return Noise.hash(lx, ly, seed + 7) < 0.3F ? Colors.lighten(current, 0.35F) : current;
                });
            }
        }

        /** Cargo pockets and the flare of a skirt on the outer leg layer. */
        private void outerPants(String id) {
            for (Part leg : new Part[]{Part.RIGHT_LEG, Part.LEFT_LEG}) {
                Part outer = leg.outerLayer();
                boolean left = leg.isLeft();
                if (id.equals("cargo")) {
                    Face side = left ? Face.LEFT : Face.RIGHT;
                    Pen pen = pen(outer, side);
                    pen.shade(2, 18, 12, 13, (lx, ly, current) -> {
                        int color = fabric(pants, pen, lx, ly);
                        boolean flap = ly <= 21;
                        boolean edge = lx == 2 || lx == 13 || ly == 30;
                        return flap || edge ? Colors.shade(color, 0.82F) : color;
                    });
                    cloth.add(pen.region(2, 18, 12, 13));
                } else {
                    for (Face face : Face.SIDES) {
                        Pen pen = pen(outer, face);
                        pen.shade(0, 0, pen.width(), 22, (lx, ly, current) -> {
                            int color = fabric(pants, pen, lx, ly);
                            color = Colors.shade(color, ((pen.ux() + lx) / 4) % 2 == 0 ? 0.94F : 1.0F);
                            if (ly >= 20 || (ly > 17 && Noise.hash(lx, ly, seed + 8) < 0.3F)) {
                                return ly >= 20 && Noise.hash(lx, ly, seed + 9) < 0.5F ? current : Colors.shade(color, 0.86F);
                            }
                            return ly < 2 ? Colors.shade(color, 0.8F) : color;
                        });
                        cloth.add(pen.region(0, 0, pen.width(), 22));
                    }
                }
            }
        }

        // ------------------------------------------------------------ shoes

        private void shoes() {
            String id = Catalog.SHOES.get(shoesStyle).id();
            int top = switch (id) {
                case "boots" -> 34;
                case "combat_boots" -> 28;
                case "hiking_boots" -> 38;
                case "dress_shoes" -> 42;
                case "rain_boots" -> 22;
                case "sneakers" -> 40;
                default -> 48; // sandals and bare feet keep the skin
            };
            int sole = switch (id) {
                case "sneakers" -> Colors.rgb(222, 220, 214);
                case "hiking_boots" -> Colors.rgb(92, 80, 68);
                case "dress_shoes" -> Colors.rgb(52, 40, 34);
                case "sandals" -> Colors.rgb(150, 120, 82);
                case "barefoot" -> 0;
                default -> Colors.rgb(38, 34, 32);
            };
            int soleTop = id.equals("dress_shoes") ? 46 : 45;
            int upper = id.equals("dress_shoes") ? Colors.shade(shoes, 0.75F) : shoes;
            for (Part leg : new Part[]{Part.RIGHT_LEG, Part.LEFT_LEG}) {
                boolean left = leg.isLeft();
                if (top < 48) {
                    sides(leg, top, 48, (face, pen, lx, ly, current) -> {
                        float dx = Math.abs(lx + 0.5F - 8);
                        if (ly >= soleTop) {
                            int color = Colors.shade(sole, 1 + 0.05F * Noise.signed(lx, ly, seed + 10));
                            return ly == soleTop ? Colors.shade(color, 0.8F) : color;
                        }
                        int color = fabric(upper, pen, lx, ly);
                        switch (id) {
                            case "sneakers" -> {
                                if (face == Face.FRONT && ly < 44 && dx < 3.5F) {
                                    color = ((ly & 1) == 0) == (dx < 1.5F) ? LACE : Colors.shade(color, 0.9F);
                                }
                                if (face == Face.FRONT && ly == 44) {
                                    color = Colors.lighten(color, 0.3F); // toe cap
                                }
                            }
                            case "boots", "hiking_boots" -> {
                                if (face == Face.FRONT && ly < 43 && dx < 4) {
                                    boolean eyelet = dx > 2.5F && (ly & 1) == 0;
                                    boolean lace = dx <= 2.5F && ((ly + (dx < 1.5F ? 0 : 1)) & 1) == 0;
                                    color = eyelet ? Colors.shade(color, 0.6F) : lace ? Colors.lerp(color, LACE, 0.7F) : color;
                                }
                                if (ly <= top + 1) {
                                    color = Colors.shade(color, 0.82F); // collar
                                }
                                if (face == Face.FRONT && ly >= 43) {
                                    color = Colors.shade(color, id.equals("hiking_boots") ? 0.75F : 1.1F);
                                }
                            }
                            case "combat_boots" -> {
                                if (face == Face.FRONT && ly >= 31 && ly < 44 && dx < 4) {
                                    boolean eyelet = dx > 2.5F && (ly & 1) == 1;
                                    boolean lace = dx <= 2.5F && ((ly + (dx < 1.5F ? 0 : 1)) & 1) == 1;
                                    color = eyelet ? Colors.lerp(color, BUCKLE, 0.6F) : lace ? Colors.lerp(color, Colors.shade(upper, 1.5F), 0.5F) : color;
                                }
                                if (ly <= top + 2) {
                                    color = Colors.shade(color, 0.72F); // strap
                                    if (lx == 2 || lx == 3) {
                                        color = BUCKLE;
                                    }
                                }
                                if (face == Face.BACK && ly >= 43) {
                                    color = Colors.shade(color, 0.8F);
                                }
                            }
                            case "dress_shoes" -> {
                                if (face == Face.FRONT && ly == 43 && dx < 2.5F) {
                                    color = Colors.lighten(color, 0.45F);
                                }
                                if (ly == top) {
                                    color = Colors.shade(color, 0.8F);
                                }
                            }
                            case "rain_boots" -> {
                                if (lx == 4 || lx == 5) {
                                    color = Colors.lighten(color, 0.25F);
                                } else if (lx >= 12) {
                                    color = Colors.shade(color, 0.86F);
                                }
                                if (ly <= top + 1) {
                                    color = Colors.shade(color, 0.82F);
                                }
                            }
                            default -> {
                            }
                        }
                        return color;
                    });
                } else if (id.equals("sandals")) {
                    sides(leg, 41, 48, (face, pen, lx, ly, current) -> {
                        float dx = Math.abs(lx + 0.5F - 8);
                        if (ly >= 46) {
                            return Colors.shade(sole, 0.9F + 0.04F * Noise.signed(lx, ly, seed));
                        }
                        if (ly == 44 || ly == 45) {
                            return face == Face.BACK ? Colors.shade(shoes, 0.9F) : shoes;
                        }
                        if (face == Face.FRONT && ly < 43 && dx < 2) {
                            return shoes;
                        }
                        return current;
                    });
                }
                Pen bottom = pen(leg, Face.BOTTOM);
                if (sole != 0) {
                    bottom.shade((lx, ly, current) -> {
                        int color = Colors.shade(sole, 0.85F + 0.06F * Noise.signed(lx, ly, seed + 11));
                        return (ly % 4) < 2 && lx > 1 && lx < 14 ? Colors.shade(color, 0.8F) : color; // tread
                    });
                } else {
                    bottom.shade((lx, ly, current) -> {
                        int color = Colors.shade(current, 0.9F);
                        return ly >= 12 && (lx % 3) == 0 ? Colors.shade(color, 0.86F) : color; // toes
                    });
                }
            }
        }

        // ------------------------------------------------------------- wear

        private void wear() {
            int wear = a.get(Trait.WEAR);
            if (wear == 0) {
                return;
            }
            int dust = Colors.rgb(200, 190, 170);
            int dirt = Colors.rgb(55, 42, 28);
            int blood = Colors.rgb(110, 12, 14);
            for (Rect rect : cloth) {
                canvas.shade(rect, (x, y, current) -> {
                    if (Colors.alpha(current) == 0) {
                        return current;
                    }
                    float down = (y - rect.y()) / (float) Math.max(1, rect.h());
                    int result = current;
                    if (wear == 1) {
                        float n = Colors.clamp01((Noise.fbm(x, y, 7, seed + 20) - 0.45F) / 0.3F);
                        result = Colors.lerp(result, dust, 0.2F * n);
                    } else {
                        float n = Colors.clamp01((Noise.fbm(x, y, 5, seed + 21) - 0.5F) / 0.3F);
                        result = Colors.lerp(result, dirt, 0.38F * n * (0.5F + 0.5F * down));
                        if (Noise.hash(x, y, seed + 22) < 0.02F) {
                            result = Colors.lerp(result, dirt, 0.45F);
                        }
                    }
                    if (wear == 3) {
                        // A few splashes with drips running down from them.
                        float blob = Noise.smooth(x, y, 2.2F, seed + 23);
                        if (blob > 0.84F) {
                            result = Colors.lerp(result, blood, 0.85F);
                        } else if (blob > 0.8F) {
                            result = Colors.lerp(result, blood, 0.45F);
                        }
                        if (Noise.hash(x, 0, seed + 24) < 0.035F) {
                            for (int up = 2; up <= 7; up++) {
                                if (Noise.smooth(x, y - up, 2.2F, seed + 23) > 0.84F) {
                                    result = Colors.lerp(result, Colors.shade(blood, 0.8F), 0.7F);
                                    break;
                                }
                            }
                        }
                    }
                    return result;
                });
            }
        }
    }
}
