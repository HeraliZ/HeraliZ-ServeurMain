package com.heraliz.extraction.client;

import com.heraliz.extraction.TerminalGeometry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Poses et placements du terminal tenu en main, partagés entre le jeu et l'outil d'aperçu.
 * La main droite porte le PC par en dessous, la main gauche tape sur le clavier.
 * Pendant la saisie, le joueur est à genoux sur les deux genoux.
 */
public final class TerminalPoses {
    /** Taille du PC tenu en 3e personne. */
    public static final float HELD_SCALE = 0.55F;
    /** Taille des bras en 1re personne pendant la saisie (proportionnés au PC). */
    private static final float FP_ARM_SCALE = 0.55F;
    /** Hauteur du dessus des touches (pixels du modèle). */
    private static final float KEY_TOP = 1.98F;

    private TerminalPoses() {
    }

    // ================================================================== 3e personne

    /**
     * Bras droit sous le PC, bras gauche sur le clavier (qui tape si {@code typing}).
     * Angles calculés pour que les mains tombent sur le PC placé par {@link #applyHeldTransform}.
     */
    public static void applyArms(ModelPart rightArm, ModelPart leftArm, float headXRot, float time, boolean typing) {
        rightArm.xRot = -0.42F;
        rightArm.yRot = -0.1F;
        rightArm.zRot = 0.0F;

        float tap = typing ? Mth.sin(time * 1.7F) * 0.08F : 0.0F;
        float slide = typing ? Mth.sin(time * 0.37F) * 0.07F : 0.0F;
        leftArm.xRot = -0.84F + tap;
        leftArm.yRot = 0.49F + slide;
        leftArm.zRot = 0.0F;
    }

    /**
     * À genoux : les jambes d'origine sont cachées et remplacées par {@link KneelLegs} (pliées au genou).
     * On les laisse droites ici pour que l'armure éventuelle (jambières, bottes) suive les cuisses.
     */
    public static void applyKneelLegs(ModelPart rightLeg, ModelPart leftLeg) {
        rightLeg.xRot = 0.05F;
        rightLeg.yRot = 0.0F;
        rightLeg.zRot = 0.05F;
        leftLeg.xRot = 0.05F;
        leftLeg.yRot = 0.0F;
        leftLeg.zRot = -0.05F;
        rightLeg.visible = false;
        leftLeg.visible = false;
    }

    /**
     * Place le PC devant le torse, posé sur la main droite. Appelé après les transformations
     * du joueur (espace du modèle : Y vers le bas, -Z = devant).
     */
    public static void applyHeldTransform(PoseStack pose, ModelPart body) {
        body.translateAndRotate(pose);
        pose.translate(-1.5F / 16.0F, 10.2F / 16.0F, -6.4F / 16.0F);
        pose.mulPose(Axis.ZP.rotationDegrees(180.0F)); // remet le haut en haut
        pose.mulPose(Axis.XP.rotationDegrees(12.0F));  // clavier incliné vers le joueur
        float s = HELD_SCALE / 16.0F;
        pose.scale(s, s, s);
    }

    // ================================================================== 1re personne

    /** Le PC ouvert devant soi, en bas de l'écran. */
    public static void applyFirstPersonTransform(PoseStack pose, float lower) {
        pose.translate(0.0F, -0.29F - lower * 0.4F, -0.72F);
        pose.mulPose(Axis.XP.rotationDegrees(34.0F));
        float s = 0.025F;
        pose.scale(s, s, s);
        pose.translate(-TerminalGeometry.MODEL_CENTER_X + 1.5F, 0.0F, 0.0F);
    }

    /**
     * Oriente un bras (partie de modèle à la position 0, sans rotation) pour que le bout du poing
     * arrive exactement sur un point du PC : sous la base pour la main droite (immobile),
     * sur les touches pour la main gauche (qui passe de touche en touche).
     */
    public static void applyFirstPersonHand(PoseStack pose, boolean right, float time, float lower) {
        // main droite : tient le coin avant droit du PC, contre la caisse (immobile)
        Vector3f target = right ? new Vector3f(8.6F, 0.4F, 6.6F) : typingPoint(time);
        PoseStack laptop = new PoseStack();
        applyFirstPersonTransform(laptop, lower);
        Vector3f tip = laptop.last().pose().transformPosition(target);

        Vector3f shoulder = right ? new Vector3f(0.55F, -0.95F, -0.2F) : new Vector3f(-0.62F, -0.98F, -0.12F);
        Vector3f dir = new Vector3f(tip).sub(shoulder).normalize();

        pose.translate(tip.x, tip.y, tip.z);
        pose.mulPose(new Quaternionf().rotationTo(0.0F, 1.0F, 0.0F, dir.x, dir.y, dir.z));
        pose.mulPose(Axis.YP.rotationDegrees(right ? 90.0F : -90.0F)); // dos de la main vers le haut
        pose.scale(FP_ARM_SCALE, FP_ARM_SCALE, FP_ARM_SCALE);
        // la boîte du bras va de y=-2 à y=10 : on ramène son extrémité (le poing) sur le point visé
        pose.translate((right ? 1.0F : -1.0F) / 16.0F, -10.0F / 16.0F, 0.0F);
    }

    /** Position du poing gauche : il saute de touche en touche et appuie à chaque fois. */
    private static Vector3f typingPoint(float time) {
        float t = time / 3.5F;
        int i = Mth.floor(t);
        float f = t - i;
        Vector3f a = keyPos(i);
        Vector3f b = keyPos(i + 1);
        float e = f * f * (3.0F - 2.0F * f);
        float lift = Mth.sin(f * Mth.PI) * 3.0F;
        return new Vector3f(Mth.lerp(e, a.x, b.x), KEY_TOP + 1.6F + lift, Mth.lerp(e, a.z, b.z));
    }

    /** Touche "au hasard" (déterministe) dans la partie gauche/centre du clavier. */
    private static Vector3f keyPos(int i) {
        int h = i * 0x9E3779B1;
        h ^= h >>> 15;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        int col = Math.floorMod(h, 6);
        int row = Math.floorMod(h >>> 8, 4);
        return new Vector3f(-4.6F + col * 1.4F, 0.0F, -4.6F + row * 1.4F);
    }
}
