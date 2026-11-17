package fr.heraliz.recolte.client.enmain;

import org.joml.Matrix3f;
import org.joml.Quaternionf;

/**
 * Poses de l'outil à la première personne, dans le repère de la caméra (x à droite, y en haut, -z devant ; mètres ;
 * champ de vision des mains de Minecraft : 70°). Ce sont les poses des images validées {@code maquettes/recolte/
 * vue_joueur_*.png}, reprises de {@code modules/recolte/outils/recolte_modeles.py} ({@code POSES_HACHE},
 * {@code POSES_PIOCHE} ; prises : {@link TenueOutil}) et {@code recolte_cubes.py} ({@code POINTES}, {@code POINTES_PIC}) :
 * à régler en jeu ici, les mêmes nombres que là-bas.
 * <p>Une pose : base du manche B (bas du manche, y = 0 du modèle), sens du manche U (+y du modèle), côté qui frappe F
 * (le tranchant, +x du modèle ; pour la pioche et le côté pic de la hache-pioche, le pic, -x du modèle). À l'impact, B
 * est calculé pour que le bout qui frappe touche le point visé de l'image.</p>
 */
final class PosesPremierePersonne {
    /** Bx By Bz, Ux Uy Uz, Fx Fy Fz : au repos, élan, frappe, impact (B de l'impact : calculé). */
    private static final float[][] HACHE = {
            {0.20F, -0.48F, -0.50F, -0.45F, 0.85F, -0.25F, -0.90F, 0.10F, -0.30F},
            {0.25F, -0.50F, -0.60F, 0.45F, 0.80F, 0.20F, -0.60F, 0.25F, -0.75F},
            {0.22F, -0.42F, -0.58F, -0.60F, 0.45F, -0.66F, -0.70F, -0.20F, 0.60F},
            {Float.NaN, Float.NaN, Float.NaN, -0.10F, 0.20F, -1.00F, -1.00F, -0.05F, -0.20F}};
    private static final float[][] PIOCHE = {
            {0.22F, -0.50F, -0.52F, -0.35F, 0.92F, -0.15F, -0.90F, -0.30F, -0.30F},
            {0.20F, -0.48F, -0.62F, 0.15F, 0.85F, 0.35F, -0.20F, 0.30F, -1.00F},
            {0.16F, -0.36F, -0.60F, -0.05F, 0.62F, -0.78F, 0.00F, -0.80F, -0.60F},
            {Float.NaN, Float.NaN, Float.NaN, 0.02F, 0.15F, -1.00F, 0.00F, -1.00F, -0.15F}};
    /** Point touché à l'impact (repère de la caméra) : le tronc, la pierre des scènes des images. */
    private static final float[] IMPACT_BOIS = {-0.0212F, -0.2496F, -1.8384F}, IMPACT_PIERRE = {-0.0714F, -0.1444F, -1.6749F};
    /** Bout qui frappe, repère de l'outil (m). */
    private static final float[] POINTE_HACHE = {0.143F, 0.667F, 0.0F}, POINTE_PIOCHE = {-0.28F, 0.767F, 0.0F},
            POINTE_LEGENDAIRE = {0.118F, 0.74F, 0.0F}, POINTE_LEGENDAIRE_PIC = {-0.235F, 0.669F, 0.0F};

    /** D'où viennent les bras (repère de la caméra) : épaule droite, épaule gauche. */
    static final float[] EPAULE_DROITE = {0.7F, -1.0F, -0.25F}, EPAULE_GAUCHE = {-0.6F, -1.0F, -0.25F};

    /** Une pose prête : base B et rotation (repère de l'outil vers celui de la caméra). */
    static final class Pose {
        final float x, y, z;
        final Quaternionf q;

        Pose(float x, float y, float z, Quaternionf q) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.q = q;
        }
    }

    /** Les quatre poses de chaque coup : hache, pioche, hache-pioche côté hache, hache-pioche côté pic. */
    static final Pose[] COUP_HACHE = poses(HACHE, false, POINTE_HACHE, IMPACT_BOIS);
    static final Pose[] COUP_PIOCHE = poses(PIOCHE, true, POINTE_PIOCHE, IMPACT_PIERRE);
    static final Pose[] COUP_LEGENDAIRE_BOIS = poses(HACHE, false, POINTE_LEGENDAIRE, IMPACT_BOIS);
    static final Pose[] COUP_LEGENDAIRE_PIERRE = poses(PIOCHE, true, POINTE_LEGENDAIRE_PIC, IMPACT_PIERRE);

    static {
        // la hache-pioche se tient au repos comme une hache, quel que soit le côté du coup
        COUP_LEGENDAIRE_PIERRE[Geste.REPOS] = COUP_LEGENDAIRE_BOIS[Geste.REPOS];
    }

    private PosesPremierePersonne() {
    }

    private static Pose[] poses(float[][] p, boolean pic, float[] pointe, float[] impact) {
        Pose[] out = new Pose[p.length];
        for (int i = 0; i < p.length; i++) {
            float[] l = p[i];
            Matrix3f r = Repere.outil(l[3], l[4], l[5], pic ? -l[6] : l[6], pic ? -l[7] : l[7], pic ? -l[8] : l[8]);
            float bx = l[0], by = l[1], bz = l[2];
            if (Float.isNaN(bx)) {
                // B = point touché - R · pointe
                bx = impact[0] - (r.m00 * pointe[0] + r.m10 * pointe[1] + r.m20 * pointe[2]);
                by = impact[1] - (r.m01 * pointe[0] + r.m11 * pointe[1] + r.m21 * pointe[2]);
                bz = impact[2] - (r.m02 * pointe[0] + r.m12 * pointe[1] + r.m22 * pointe[2]);
            }
            out[i] = new Pose(bx, by, bz, new Quaternionf().setFromNormalized(r));
        }
        return out;
    }
}
