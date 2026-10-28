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
            case "malle_chantier" -> new Mesures(new double[]{0.0, 1.75, 16.0, 15.9}, new double[]{0.1, 0.0, 1.75, 16.0, 12.05, 15.9},
                new double[]{0.0, 0.0, 1.75, 15.9, 12.05, 15.9}, 8, new Mobile[]{
                    new Mobile("couvercle", null, 16.0F, 10.0F, 15.6F, 'x', new float[]{0F, 1F}, new float[]{0F, 82F})});
            case "casier_metallique" -> new Mesures(new double[]{0.5, 1.75, 15.5, 15.75}, new double[]{0.5, 0.0, 1.75, 15.5, 16.0, 15.75},
                new double[]{0.5, 0.0, 1.75, 15.5, 15.4, 15.75}, 8, new Mobile[]{
                    new Mobile("porte", null, 14.95F, 16.0F, 3.0F, 'y', new float[]{0F, 1F}, new float[]{0F, -108F})});
            case "caisse_armes" -> new Mesures(new double[]{0.0, 1.55, 16.0, 15.9}, new double[]{0.25, 0.0, 1.55, 16.0, 13.45, 15.9},
                new double[]{0.0, 0.0, 1.55, 15.75, 13.45, 15.9}, 10, new Mobile[]{
                    new Mobile("couvercle", null, 16.0F, 10.4F, 15.6F, 'x', new float[]{0F, 0.15F, 1F}, new float[]{0F, 4F, 78F})});
            case "coffre_fort" -> new Mesures(new double[]{0.5, 1.0, 16.0, 15.75}, new double[]{0.5, 0.0, 1.0, 16.0, 16.0, 15.75},
                new double[]{0.5, 0.0, 1.0, 16.0, 15.5, 15.75}, 18, new Mobile[]{
                    new Mobile("porte", null, 15.15F, 16.0F, 3.0F, 'y', new float[]{0F, 0.3F, 1F}, new float[]{0F, 0F, -100F}),
                    new Mobile("volant", "porte", 8.0F, 12.6F, 1.2F, 'z', new float[]{0F, 0.3F, 1F}, new float[]{0F, -180F, -180F})});
            default -> throw new IllegalArgumentException(nom);
        };
    }
}
