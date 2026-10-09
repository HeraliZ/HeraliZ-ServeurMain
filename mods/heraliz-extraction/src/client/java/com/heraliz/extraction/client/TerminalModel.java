package com.heraliz.extraction.client;

import com.heraliz.extraction.HeralizExtraction;
import com.heraliz.extraction.TerminalGeometry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Modèle 3D du terminal d'extraction : un PC portable renforcé (type "toughbook" militaire),
 * bien abîmé, avec une caisse à munitions vissée sur le côté qui contient le fumigène.
 * <p>
 * Unités : pixels (1/16 de bloc). +X = côté caisse, +Y = haut, +Z = côté utilisateur.
 * La géométrie n'utilise que PoseStack/VertexConsumer pour pouvoir être rendue hors du jeu
 * (outil d'aperçu dans src/preview).
 */
public final class TerminalModel {
    public static final ResourceLocation TEXTURE =
            new ResourceLocation(HeralizExtraction.MODID, "textures/entity/extraction_terminal.png");

    private static final float TEX_SIZE = 128.0F;
    /** Densité de texture pour les matériaux répétés (texels par pixel de modèle). */
    private static final float DENSITY = 2.0F;
    /** Taille d'un "pixel" de police sur l'écran. */
    private static final float TEXT_SCALE = 0.115F;

    /** Zone de la texture. stretch = l'image entière est plaquée sur la face. */
    private record Mat(int u, int v, int w, int h, boolean stretch, boolean emissive) {
        static Mat tile(int col, int row) {
            return new Mat(col * 16, row * 16, 16, 16, false, false);
        }

        static Mat decal(int col, int row) {
            return new Mat(col * 16, row * 16, 16, 16, true, false);
        }

        static Mat glow(int col, int row) {
            return new Mat(col * 16, row * 16, 16, 16, false, true);
        }
    }

    private static final Mat PLASTIC = Mat.tile(0, 0);
    private static final Mat PLASTIC_DARK = Mat.tile(1, 0);
    private static final Mat RUBBER = Mat.tile(2, 0);
    private static final Mat DECK = Mat.tile(3, 0);
    private static final Mat KEY_TOP = Mat.decal(4, 0);
    private static final Mat KEY_SIDE = Mat.tile(5, 0);
    private static final Mat TRACKPAD = Mat.decal(6, 0);
    private static final Mat BEZEL = Mat.tile(7, 0);
    private static final Mat OLIVE = Mat.tile(0, 1);
    private static final Mat OLIVE_DARK = Mat.tile(1, 1);
    private static final Mat STEEL = Mat.tile(2, 1);
    private static final Mat BOX_INNER = Mat.tile(3, 1);
    private static final Mat TAPE = Mat.tile(4, 1);
    private static final Mat WIRE_BLACK = Mat.tile(5, 1);
    private static final Mat WIRE_RED = Mat.tile(6, 1);
    private static final Mat FLARE_RED = Mat.tile(7, 1);
    private static final Mat FLARE_CAP = Mat.tile(0, 2);
    private static final Mat FLARE_TIP = Mat.glow(1, 2);
    private static final Mat LED_GREEN = Mat.glow(2, 2);
    private static final Mat LED_RED = Mat.glow(3, 2);
    private static final Mat VENT = Mat.decal(4, 2);
    private static final Mat PORTS = Mat.decal(5, 2);
    private static final Mat STICKER = Mat.decal(6, 2);
    private static final Mat FLARE_LABEL = Mat.decal(7, 2);
    private static final Mat SCREEN = new Mat(0, 48, 64, 44, true, true);
    private static final Mat LID_BACK = new Mat(64, 48, 64, 44, true, false);
    private static final Mat STENCIL = new Mat(0, 96, 48, 16, true, false);

    /** Hauteur de la base et position de la charnière. */
    private static final float BASE_H = 1.6F;
    private static final float HINGE_Y = BASE_H + 0.45F;
    private static final float HINGE_Z = -6.2F;

    /** Touches arrachées (rangée * 100 + colonne) et touche à moitié décollée. */
    private static final int[] MISSING_KEYS = {6, 102, 209, 304};
    private static final int LOOSE_KEY = 110;

    public record ScreenLine(String text, int color) {
    }

    /** Positions animées du modèle. */
    public record State(float lidDegrees, float boxLid, float deploy, boolean flareLit) {
        public static final State HELD = new State(100.0F, 0.0F, 0.0F, false);
    }

    private TerminalModel() {
    }

    /** Rendu complet en jeu : géométrie + texte de l'écran. */
    public static void render(PoseStack pose, MultiBufferSource buffers, int light, int overlay,
                              State state, List<ScreenLine> lines) {
        Matrix4f screen = renderGeometry(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), light, overlay, state);
        if (!lines.isEmpty() && state.lidDegrees() > 30.0F) {
            Font font = Minecraft.getInstance().font;
            for (int i = 0; i < lines.size(); i++) {
                ScreenLine line = lines.get(i);
                if (!line.text().isEmpty()) {
                    font.drawInBatch(line.text(), 0.0F, i * 9.0F, line.color(), false, screen, buffers,
                            Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
                }
            }
        }
    }

    /**
     * Dessine toute la géométrie dans {@code vc}.
     *
     * @return la matrice du coin haut-gauche de l'écran (unités de police, Y vers le bas)
     */
    public static Matrix4f renderGeometry(PoseStack pose, VertexConsumer vc, int light, int overlay, State state) {
        Builder b = new Builder(pose, vc, light, overlay, state.flareLit());
        renderBase(b);
        Matrix4f screen = renderLid(b, state.lidDegrees());
        renderBox(b, state.boxLid());
        renderFlare(b, state.deploy());
        return screen;
    }

    // ================================================================== PC : base

    private static void renderBase(Builder b) {
        // coque : plaque de fond + corps
        b.cube(-8.6F, 0.0F, -6.1F, 8.6F, 0.45F, 6.1F, PLASTIC_DARK);
        b.cube(-9.0F, 0.45F, -6.5F, 9.0F, BASE_H, 6.5F, PLASTIC);

        // coins renforcés en caoutchouc (deux blocs décalés = coin arrondi)
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                b.cube(sx * 7.8F, -0.1F, sz * 5.0F, sx * 9.25F, BASE_H + 0.15F, sz * 7.0F, RUBBER);
                b.cube(sx * 7.8F, 0.05F, sz * 5.0F, sx * 9.45F, BASE_H, sz * 6.8F, RUBBER);
            }
        }

        // fond du clavier + touches en relief
        b.cube(-8.0F, BASE_H, -5.9F, 8.0F, BASE_H + 0.02F, 0.9F, DECK);
        float keyY0 = BASE_H + 0.02F;
        float keyY1 = BASE_H + 0.38F;
        for (int row = 0; row < 5; row++) {
            float z0 = -5.55F + row * 1.3F;
            float z1 = z0 + 1.0F;
            float stagger = (row == 1 || row == 3) ? 0.3F : 0.0F;
            for (int k = 0; k < 12; k++) {
                if (isMissing(row * 100 + k)) {
                    continue;
                }
                float x0 = -7.75F + k * 1.3F + stagger;
                float x1 = x0 + 1.05F;
                if (row == 4 && k >= 3 && k <= 8) {
                    if (k == 3) { // barre d'espace
                        b.cube(x0, keyY0, z0, -7.75F + 8 * 1.3F + 1.05F, keyY1, z1, KEY_TOP, KEY_SIDE);
                    }
                    continue;
                }
                if (row * 100 + k == LOOSE_KEY) {
                    // touche à moitié arrachée, soulevée vers l'avant
                    b.pose.pushPose();
                    b.pose.translate(x0, keyY0, z0);
                    b.pose.mulPose(Axis.XP.rotationDegrees(-28.0F));
                    b.cube(0.0F, 0.0F, 0.0F, 1.05F, 0.36F, 1.0F, KEY_TOP, KEY_SIDE);
                    b.pose.popPose();
                    continue;
                }
                b.cube(x0, keyY0, z0, x1, keyY1, z1, KEY_TOP, KEY_SIDE);
            }
        }

        // pavé tactile + boutons
        b.cube(-2.4F, BASE_H, 1.9F, 2.4F, BASE_H + 0.02F, 4.5F, TRACKPAD);
        b.cube(-2.4F, BASE_H, 4.75F, -0.1F, BASE_H + 0.12F, 5.5F, KEY_SIDE);
        b.cube(0.1F, BASE_H, 4.75F, 2.4F, BASE_H + 0.12F, 5.5F, KEY_SIDE);

        // voyants à l'avant, ports sur la gauche, aérations à l'arrière
        b.cube(5.6F, 0.8F, 6.5F, 6.1F, 1.15F, 6.6F, LED_GREEN);
        b.cube(6.4F, 0.8F, 6.5F, 6.9F, 1.15F, 6.6F, LED_RED);
        b.cube(-9.06F, 0.65F, -3.5F, -8.98F, 1.3F, 1.5F, PORTS);
        b.cube(-6.0F, 0.6F, -6.56F, 3.0F, 1.35F, -6.48F, VENT);

        // poignée de transport à l'avant
        b.cube(-5.0F, 0.5F, 6.5F, -4.2F, 1.3F, 7.6F, RUBBER);
        b.cube(4.2F, 0.5F, 6.5F, 5.0F, 1.3F, 7.6F, RUBBER);
        b.cube(-5.0F, 0.55F, 7.6F, 5.0F, 1.25F, 8.3F, RUBBER);

        // charnières rondes
        for (int sx = -1; sx <= 1; sx += 2) {
            b.pose.pushPose();
            b.pose.translate(sx * 5.2F, HINGE_Y, HINGE_Z);
            b.pose.mulPose(Axis.YP.rotationDegrees(90.0F));
            b.cylinder(-1.6F, 1.6F, 0.6F, PLASTIC_DARK, PLASTIC_DARK);
            b.pose.popPose();
        }

        // pattes de fixation vissées vers la caisse
        for (int sz = -1; sz <= 1; sz += 2) {
            float za = sz * 2.2F;
            float zb = sz * 3.6F;
            b.cube(8.7F, 0.3F, Math.min(za, zb), 10.1F, 1.3F, Math.max(za, zb), STEEL);
            float zc = sz * 2.9F;
            b.cube(9.0F, 1.3F, zc - 0.25F, 9.5F, 1.5F, zc + 0.25F, STEEL);
        }

        // câbles bricolés du PC vers la caisse, tenus par du scotch
        b.cube(6.0F, 0.7F, -7.6F, 6.35F, 1.05F, -6.5F, WIRE_RED);
        b.cube(6.0F, 0.7F, -7.6F, 11.2F, 1.05F, -7.25F, WIRE_RED);
        b.cube(10.85F, 0.7F, -7.6F, 11.2F, 1.05F, -5.0F, WIRE_RED);
        b.cube(5.4F, 0.3F, -7.95F, 5.75F, 0.65F, -6.5F, WIRE_BLACK);
        b.cube(5.4F, 0.3F, -7.95F, 12.0F, 0.65F, -7.6F, WIRE_BLACK);
        b.cube(11.65F, 0.3F, -7.95F, 12.0F, 0.65F, -5.0F, WIRE_BLACK);
        b.cube(8.0F, 0.2F, -8.1F, 8.9F, 1.2F, -7.1F, TAPE);
    }

    private static boolean isMissing(int key) {
        for (int k : MISSING_KEYS) {
            if (k == key) {
                return true;
            }
        }
        return false;
    }

    // ================================================================== PC : écran

    private static Matrix4f renderLid(Builder b, float lidDegrees) {
        PoseStack pose = b.pose;
        pose.pushPose();
        pose.translate(0.0F, HINGE_Y, HINGE_Z);
        pose.mulPose(Axis.XP.rotationDegrees(90.0F - lidDegrees));

        // coque arrière + cadre + dalle
        b.cube(-9.0F, -0.4F, -0.8F, 9.0F, 12.6F, -0.15F, PLASTIC, PLASTIC, LID_BACK, PLASTIC, PLASTIC, PLASTIC);
        b.cube(-8.7F, -0.2F, -0.15F, 8.7F, 12.4F, 0.0F, BEZEL);
        b.quad(-7.7F, 11.4F, 0.012F, -7.7F, 1.0F, 0.012F, 7.7F, 1.0F, 0.012F, 7.7F, 11.4F, 0.012F,
                0.0F, 0.0F, 1.0F, SCREEN, 15.4F, 10.4F);
        // webcam + voyant
        b.cube(-0.35F, 11.75F, -0.02F, 0.35F, 12.15F, 0.03F, PLASTIC_DARK);
        b.cube(0.6F, 11.85F, -0.02F, 0.85F, 12.05F, 0.03F, LED_GREEN);

        // coins renforcés en haut de l'écran
        for (int sx = -1; sx <= 1; sx += 2) {
            b.cube(sx * 7.8F, 11.3F, -0.95F, sx * 9.3F, 12.95F, 0.12F, RUBBER);
            b.cube(sx * 7.8F, 11.3F, -0.95F, sx * 9.45F, 12.75F, 0.12F, RUBBER);
        }

        // scotch qui tient le coin cassé de l'écran
        pose.pushPose();
        pose.translate(8.3F, 12.0F, -0.33F);
        pose.mulPose(Axis.ZP.rotationDegrees(45.0F));
        b.cube(-0.75F, -2.6F, -0.62F, 0.75F, 2.6F, 0.46F, TAPE);
        pose.popPose();

        // gros X de scotch au dos
        for (int sign = -1; sign <= 1; sign += 2) {
            pose.pushPose();
            pose.translate(0.0F, 6.2F, -0.82F);
            pose.mulPose(Axis.ZP.rotationDegrees(sign * 35.0F));
            b.cube(-0.8F, -7.0F, -0.05F, 0.8F, 7.0F, 0.03F, TAPE);
            pose.popPose();
        }
        b.cube(-8.0F, 7.5F, -0.86F, -5.6F, 10.0F, -0.8F, STICKER);

        // antenne scotchée au dos
        pose.pushPose();
        pose.translate(-7.6F, 0.5F, -1.2F);
        pose.mulPose(Axis.XP.rotationDegrees(-90.0F));
        b.cylinder(0.0F, 15.5F, 0.22F, WIRE_BLACK, WIRE_BLACK);
        pose.popPose();
        b.cube(-7.95F, 16.0F, -1.55F, -7.25F, 16.7F, -0.85F, LED_RED);
        b.cube(-8.0F, 3.0F, -1.65F, -7.2F, 3.8F, -0.75F, TAPE);
        b.cube(-8.0F, 9.0F, -1.65F, -7.2F, 9.8F, -0.75F, TAPE);

        // position du texte : coin haut-gauche de la dalle
        pose.translate(-7.3F, 11.0F, 0.05F);
        pose.scale(TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);
        Matrix4f screen = new Matrix4f(pose.last().pose());
        pose.popPose();
        return screen;
    }

    // ================================================================== caisse à munitions

    private static void renderBox(Builder b, float boxLid) {
        // fond + 4 parois (faces intérieures sombres)
        b.cube(9.8F, 0.0F, -5.0F, 14.8F, 0.5F, 5.0F, BOX_INNER, OLIVE, OLIVE, OLIVE, OLIVE, OLIVE);
        b.cube(9.8F, 0.5F, -5.0F, 10.3F, 4.8F, 5.0F, OLIVE, OLIVE, OLIVE, OLIVE, OLIVE, BOX_INNER);
        b.cube(14.3F, 0.5F, -5.0F, 14.8F, 4.8F, 5.0F, OLIVE, OLIVE, OLIVE, OLIVE, BOX_INNER, STENCIL);
        b.cube(10.3F, 0.5F, -5.0F, 14.3F, 4.8F, -4.5F, OLIVE, OLIVE, OLIVE, BOX_INNER, OLIVE, OLIVE);
        b.cube(10.3F, 0.5F, 4.5F, 14.3F, 4.8F, 5.0F, OLIVE, OLIVE, BOX_INNER, OLIVE, OLIVE, OLIVE);

        // nervures
        b.cube(14.8F, 1.3F, -4.8F, 15.0F, 1.6F, 4.8F, OLIVE_DARK);
        b.cube(14.8F, 3.5F, -4.8F, 15.0F, 3.8F, 4.8F, OLIVE_DARK);
        for (float x : new float[]{11.3F, 13.0F}) {
            b.cube(x, 0.3F, -5.2F, x + 0.3F, 4.6F, -5.0F, OLIVE_DARK);
            b.cube(x, 0.3F, 5.0F, x + 0.3F, 4.6F, 5.2F, OLIVE_DARK);
        }

        // couvercle articulé sur le bord extérieur
        PoseStack pose = b.pose;
        pose.pushPose();
        pose.translate(14.8F, 4.8F, 0.0F);
        pose.mulPose(Axis.ZP.rotationDegrees(-125.0F * boxLid));
        b.cube(-5.0F, 0.0F, -5.15F, 0.1F, 0.55F, 5.15F, OLIVE, BOX_INNER, OLIVE, OLIVE, OLIVE, OLIVE);
        b.cube(-4.1F, 0.55F, -4.7F, -3.7F, 0.75F, 4.7F, OLIVE_DARK);
        b.cube(-1.4F, 0.55F, -4.7F, -1.0F, 0.75F, 4.7F, OLIVE_DARK);
        // poignée
        b.cube(-2.85F, 0.55F, -2.3F, -2.15F, 1.0F, -1.9F, STEEL);
        b.cube(-2.85F, 0.55F, 1.9F, -2.15F, 1.0F, 2.3F, STEEL);
        pose.pushPose();
        pose.translate(-2.5F, 1.05F, 0.0F);
        b.cylinder(-2.3F, 2.3F, 0.22F, STEEL, STEEL);
        pose.popPose();
        // loquet
        b.cube(-5.3F, -0.9F, -0.7F, -4.95F, 0.6F, 0.7F, STEEL);
        pose.popPose();
    }

    // ================================================================== fumigène

    private static void renderFlare(Builder b, float deploy) {
        PoseStack pose = b.pose;
        pose.pushPose();
        pose.translate(TerminalGeometry.FLARE_PIVOT_X,
                TerminalGeometry.FLARE_PIVOT_Y + TerminalGeometry.FLARE_RISE * deploy,
                TerminalGeometry.FLARE_PIVOT_Z);
        pose.mulPose(Axis.XP.rotationDegrees(-90.0F * deploy));
        b.cylinder(0.0F, 0.6F, 0.95F, FLARE_CAP, FLARE_CAP);
        b.cylinder(0.6F, 5.6F, 0.85F, FLARE_RED, FLARE_CAP);
        b.cylinder(2.4F, 3.6F, 0.9F, FLARE_LABEL, FLARE_RED);
        b.cylinder(5.6F, 6.3F, 0.95F, FLARE_CAP, FLARE_CAP);
        b.cylinder(6.3F, TerminalGeometry.FLARE_LENGTH, 0.7F, FLARE_TIP, FLARE_TIP);
        pose.popPose();
    }

    // ================================================================== primitives

    private static final class Builder {
        final PoseStack pose;
        final VertexConsumer vc;
        final int light;
        final int overlay;
        final boolean flareLit;

        Builder(PoseStack pose, VertexConsumer vc, int light, int overlay, boolean flareLit) {
            this.pose = pose;
            this.vc = vc;
            this.light = light;
            this.overlay = overlay;
            this.flareLit = flareLit;
        }

        void cube(float x0, float y0, float z0, float x1, float y1, float z1, Mat all) {
            cube(x0, y0, z0, x1, y1, z1, all, all, all, all, all, all);
        }

        void cube(float x0, float y0, float z0, float x1, float y1, float z1, Mat top, Mat sides) {
            cube(x0, y0, z0, x1, y1, z1, top, sides, sides, sides, sides, sides);
        }

        /** Ordre : dessus, dessous, nord (-Z), sud (+Z), ouest (-X), est (+X). Coordonnées dans n'importe quel ordre. */
        void cube(float ax, float ay, float az, float bx, float by, float bz,
                  Mat top, Mat bottom, Mat north, Mat south, Mat west, Mat east) {
            float x0 = Math.min(ax, bx), x1 = Math.max(ax, bx);
            float y0 = Math.min(ay, by), y1 = Math.max(ay, by);
            float z0 = Math.min(az, bz), z1 = Math.max(az, bz);
            float w = x1 - x0, h = y1 - y0, d = z1 - z0;
            quad(x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, 0, 1, 0, top, w, d);
            quad(x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, 0, -1, 0, bottom, w, d);
            quad(x1, y1, z0, x1, y0, z0, x0, y0, z0, x0, y1, z0, 0, 0, -1, north, w, h);
            quad(x0, y1, z1, x0, y0, z1, x1, y0, z1, x1, y1, z1, 0, 0, 1, south, w, h);
            quad(x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z1, -1, 0, 0, west, d, h);
            quad(x1, y1, z1, x1, y0, z1, x1, y0, z0, x1, y1, z0, 1, 0, 0, east, d, h);
        }

        /** Cylindre octogonal le long de Z local, de z0 à z1. */
        void cylinder(float z0, float z1, float r, Mat side, Mat cap) {
            int seg = 8;
            float circumference = Mth.TWO_PI * r;
            float[] uv = region(side, circumference, z1 - z0, z0 * 3.1F + r * 7.0F);
            for (int i = 0; i < seg; i++) {
                float a0 = Mth.TWO_PI * i / seg;
                float a1 = Mth.TWO_PI * (i + 1) / seg;
                float am = (a0 + a1) * 0.5F;
                float c0 = Mth.cos(a0) * r, s0 = Mth.sin(a0) * r;
                float c1 = Mth.cos(a1) * r, s1 = Mth.sin(a1) * r;
                float u0 = Mth.lerp((float) i / seg, uv[0], uv[2]);
                float u1 = Mth.lerp((float) (i + 1) / seg, uv[0], uv[2]);
                emit(c0, s0, z1, c0, s0, z0, c1, s1, z0, c1, s1, z1, Mth.cos(am), Mth.sin(am), 0,
                        side, u0, uv[1], u1, uv[3]);
            }
            float cu = (cap.u() + cap.w() * 0.5F) / TEX_SIZE;
            float cv = (cap.v() + cap.h() * 0.5F) / TEX_SIZE;
            float rad = Math.min(r * DENSITY, cap.w() * 0.5F) / TEX_SIZE;
            for (int end = 0; end < 2; end++) {
                float z = end == 0 ? z0 : z1;
                float nz = end == 0 ? -1 : 1;
                for (int i = 0; i < seg; i++) {
                    float a0 = Mth.TWO_PI * i / seg;
                    float a1 = Mth.TWO_PI * (i + 1) / seg;
                    vertex(0, 0, z, cu, cv, 0, 0, nz, cap);
                    vertex(Mth.cos(a0) * r, Mth.sin(a0) * r, z, cu + Mth.cos(a0) * rad, cv + Mth.sin(a0) * rad, 0, 0, nz, cap);
                    vertex(Mth.cos(a1) * r, Mth.sin(a1) * r, z, cu + Mth.cos(a1) * rad, cv + Mth.sin(a1) * rad, 0, 0, nz, cap);
                    vertex(Mth.cos(a1) * r, Mth.sin(a1) * r, z, cu + Mth.cos(a1) * rad, cv + Mth.sin(a1) * rad, 0, 0, nz, cap);
                }
            }
        }

        /** Quad : haut-gauche, bas-gauche, bas-droite, haut-droite. w/h = taille de la face en pixels. */
        void quad(float ax, float ay, float az, float bx, float by, float bz,
                  float cx, float cy, float cz, float dx, float dy, float dz,
                  float nx, float ny, float nz, Mat m, float w, float h) {
            float[] uv = region(m, w, h, ax * 1.7F + ay * 3.3F + az * 5.1F);
            emit(ax, ay, az, bx, by, bz, cx, cy, cz, dx, dy, dz, nx, ny, nz, m, uv[0], uv[1], uv[2], uv[3]);
        }

        /** Choisit la portion de texture : toute l'image (stretch) ou un morceau à densité constante. */
        private float[] region(Mat m, float w, float h, float seed) {
            if (m.stretch()) {
                return new float[]{m.u() / TEX_SIZE, m.v() / TEX_SIZE, (m.u() + m.w()) / TEX_SIZE, (m.v() + m.h()) / TEX_SIZE};
            }
            float du = Math.min(Math.max(w * DENSITY, 1.0F), m.w());
            float dv = Math.min(Math.max(h * DENSITY, 1.0F), m.h());
            float hash = Mth.frac(Mth.sin(seed * 12.9898F) * 43758.547F);
            float ou = (m.w() - du) * hash;
            float ov = (m.h() - dv) * Mth.frac(hash * 7.31F);
            float u0 = m.u() + ou;
            float v0 = m.v() + ov;
            return new float[]{u0 / TEX_SIZE, v0 / TEX_SIZE, (u0 + du) / TEX_SIZE, (v0 + dv) / TEX_SIZE};
        }

        private void emit(float ax, float ay, float az, float bx, float by, float bz,
                          float cx, float cy, float cz, float dx, float dy, float dz,
                          float nx, float ny, float nz, Mat m, float u0, float v0, float u1, float v1) {
            vertex(ax, ay, az, u0, v0, nx, ny, nz, m);
            vertex(bx, by, bz, u0, v1, nx, ny, nz, m);
            vertex(cx, cy, cz, u1, v1, nx, ny, nz, m);
            vertex(dx, dy, dz, u1, v0, nx, ny, nz, m);
        }

        private void vertex(float x, float y, float z, float u, float v, float nx, float ny, float nz, Mat m) {
            boolean bright = m.emissive() && (m != FLARE_TIP || this.flareLit);
            PoseStack.Pose last = this.pose.last();
            Matrix4f p = last.pose();
            Matrix3f n = last.normal();
            this.vc.vertex(p, x, y, z).color(255, 255, 255, 255).uv(u, v).overlayCoords(this.overlay)
                    .uv2(bright ? LightTexture.FULL_BRIGHT : this.light).normal(n, nx, ny, nz).endVertex();
        }
    }
}
