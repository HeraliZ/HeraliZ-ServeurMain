package fr.heraliz.crac.block;

/**
 * Mesures des coffres par rareté (px, coffre tourné vers le nord) : boîte d'adossement, formes des deux cases, durée et
 * pièces qui bougent à l'ouverture. ÉCRIT par modules/bases/outils/coffres_modeles.py --installer : jamais à la main.
 */
public final class CoffresDonnees {
    /**
     * Une pièce qui tourne à l'ouverture autour de sa charnière (px), sur un axe ; images clés : t de 0 à 1, angle en
     * degrés, lissé entre deux (comme angle_cles du script). parent : pièce qui la porte (le volant suit la porte).
     */
    public record Mobile(String os, String parent, float px, float py, float pz, char axe, float[] t, float[] angle) {
    }

    /** boite : x0, z0, x1, z1 ; formes : x0, y0, z0, x1, y1, z1 (case principale, puis l'autre case) ; durée en ticks. */
    public record Mesures(double[] boite, double[] formePrincipale, double[] formeAnnexe, int dureeTicks, Mobile[] mobiles) {
    }

    private CoffresDonnees() {
    }

    public static Mesures de(String nom) {
        return switch (nom) {
            case "malle_chantier" -> new Mesures(new double[]{0.0, 1.75, 16.0, 15.5}, new double[]{0.5, 0.0, 1.75, 16.0, 11.75, 15.5},
                new double[]{0.0, 0.0, 1.75, 15.5, 11.75, 15.5}, 8, new Mobile[]{
                    new Mobile("couvercle", null, 16.0F, 9.5F, 15.5F, 'x', new float[]{0F, 1F}, new float[]{0F, 82F})});
            case "casier_metallique" -> new Mesures(new double[]{0.5, 1.75, 15.5, 15.5}, new double[]{0.5, 0.0, 1.75, 15.5, 16.0, 15.5},
                new double[]{0.5, 0.0, 1.75, 15.5, 15.5, 15.5}, 8, new Mobile[]{
                    new Mobile("porte", null, 15.5F, 16.0F, 2.5F, 'y', new float[]{0F, 1F}, new float[]{0F, -108F})});
            case "caisse_armes" -> new Mesures(new double[]{0.0, 1.75, 16.0, 15.5}, new double[]{0.5, 0.0, 1.75, 16.0, 13.0, 15.5},
                new double[]{0.0, 0.0, 1.75, 15.5, 13.0, 15.5}, 10, new Mobile[]{
                    new Mobile("couvercle", null, 16.0F, 10.0F, 15.5F, 'x', new float[]{0F, 0.15F, 1F}, new float[]{0F, 4F, 78F})});
            case "coffre_fort" -> new Mesures(new double[]{0.5, 1.75, 16.0, 15.5}, new double[]{0.5, 0.0, 1.75, 16.0, 16.0, 15.5},
                new double[]{0.5, 0.0, 1.75, 16.0, 15.5, 15.5}, 18, new Mobile[]{
                    new Mobile("porte", null, 15.5F, 16.0F, 3.25F, 'y', new float[]{0F, 0.3F, 1F}, new float[]{0F, 0F, -100F}),
                    new Mobile("poignee", "porte", 8.0F, 12.0F, 2.25F, 'z', new float[]{0F, 0.3F, 1F}, new float[]{0F, -90F, -90F})});
            default -> throw new IllegalArgumentException(nom);
        };
    }
}
