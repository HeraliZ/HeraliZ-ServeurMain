package fr.heraliz.recolte.client.enmain;

import org.joml.Matrix3f;

/** Repère d'un outil (comme {@code _repere} de recolte_modeles.py), sans dépendance au jeu. */
final class Repere {
    private Repere() {
    }

    /**
     * Rotation qui envoie +y de l'outil sur U (le manche), +x sur F (le tranchant, redressé perpendiculaire à U) et +z sur
     * F × U. Colonnes : F, U, F × U.
     */
    static Matrix3f outil(float ux, float uy, float uz, float fx, float fy, float fz) {
        float l = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        ux /= l;
        uy /= l;
        uz /= l;
        float d = fx * ux + fy * uy + fz * uz;
        fx -= d * ux;
        fy -= d * uy;
        fz -= d * uz;
        l = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
        fx /= l;
        fy /= l;
        fz /= l;
        float zx = fy * uz - fz * uy, zy = fz * ux - fx * uz, zz = fx * uy - fy * ux;
        return new Matrix3f(fx, fy, fz, ux, uy, uz, zx, zy, zz);
    }
}
