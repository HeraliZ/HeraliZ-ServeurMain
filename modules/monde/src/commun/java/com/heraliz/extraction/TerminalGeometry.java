package com.heraliz.extraction;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Valeurs partagées entre le serveur, les particules et le modèle 3D du terminal.
 * Les coordonnées du modèle sont en "pixels" (1/16 de bloc avant mise à l'échelle) :
 * +X = côté de la caisse à fumigène, +Y = haut, +Z = côté de l'utilisateur (devant le clavier).
 */
public final class TerminalGeometry {
    /** Taille du terminal posé au sol. */
    public static final float GROUND_SCALE = 0.75F;

    /** Centre horizontal du modèle complet (PC + caisse), pour le recentrer. */
    public static final float MODEL_CENTER_X = 2.8F;
    /** Largeur totale du modèle (PC + caisse). */
    public static final float MODEL_WIDTH = 24.4F;

    /** Durée de la saisie au clavier avant de poser le terminal (ticks). */
    public static final int TYPING_TICKS = 60;

    /** Animation au sol : ouverture de la caisse puis déploiement du fumigène (ticks). */
    public static final float LID_OPEN_START = 4.0F;
    public static final float LID_OPEN_END = 16.0F;
    public static final float DEPLOY_START = 14.0F;
    public static final float DEPLOY_END = 32.0F;

    /** Pivot (au fond de la caisse) et dimensions du fumigène. */
    public static final float FLARE_PIVOT_X = 12.3F;
    public static final float FLARE_PIVOT_Y = 1.45F;
    public static final float FLARE_PIVOT_Z = -3.5F;
    public static final float FLARE_RISE = 2.0F;
    public static final float FLARE_LENGTH = 7.0F;

    private TerminalGeometry() {
    }

    public static float lidOpen(float ticks) {
        return easeOut(Mth.clamp((ticks - LID_OPEN_START) / (LID_OPEN_END - LID_OPEN_START), 0.0F, 1.0F));
    }

    public static float deploy(float ticks) {
        return easeOut(Mth.clamp((ticks - DEPLOY_START) / (DEPLOY_END - DEPLOY_START), 0.0F, 1.0F));
    }

    private static float easeOut(float t) {
        float inv = 1.0F - t;
        return 1.0F - inv * inv * inv;
    }

    /** Position (relative au terminal, en blocs) de la tête du fumigène une fois déployé. */
    public static Vec3 flareTipOffset(float yawDegrees) {
        float y = FLARE_PIVOT_Y + FLARE_RISE + FLARE_LENGTH + 0.3F;
        return modelToWorld(FLARE_PIVOT_X, y, FLARE_PIVOT_Z, yawDegrees);
    }

    /** Convertit un point du modèle (pixels) en décalage monde, avec la même rotation que le renderer. */
    public static Vec3 modelToWorld(float x, float y, float z, float yawDegrees) {
        double c = Math.toRadians(180.0F - yawDegrees);
        double cos = Math.cos(c);
        double sin = Math.sin(c);
        double s = GROUND_SCALE / 16.0D;
        double wx = x * cos + z * sin;
        double wz = -x * sin + z * cos;
        return new Vec3(wx * s, y * s, wz * s);
    }
}
