package fr.heraliz.usure.regles;

/**
 * Valeurs d'une arme precise, calculees une fois (regles + surcharges + donnees TaCZ de l'arme) puis gardees en cache
 * par identifiant d'arme : rien n'est recalcule ni alloue a chaque tir.
 */
public final class Profil {
    /** Profil des armes inconnues ou exclues : aucune usure. */
    public static final Profil EXCLUE = new Profil(Categorie.MELEE, true, 0, 0, 0, 0, 1000);

    public final Categorie categorie;
    /** Arme exclue par une surcharge (couteaux, armes du mode zombies...). */
    public final boolean exclue;
    /** Vrai si l'arme s'use : infobulle, HUD et blocage de l'arme ruinee ne concernent que ces armes. */
    public final boolean suivie;
    /** Usure par tir en % d'etat (affichage) et en milliemes (calcul). */
    public final float usurePct, usureMilliemes;
    /** Multiplicateurs d'usure : sous la pluie, dans l'eau, avec un silencieux (1 = aucun surplus). */
    public final float multPluie, multEau, multSilencieux;
    /** Durabilite maximale que Gun Durability donnait a cette arme (conversion des anciennes armes). */
    public final int maxGundb;

    public Profil(Categorie categorie, boolean exclue, float usurePct, float pluie, float eau, float silencieux, int maxGundb) {
        this.categorie = categorie;
        this.exclue = exclue;
        this.usurePct = exclue ? 0 : Math.max(0, usurePct);
        this.usureMilliemes = this.usurePct * 10f;
        this.multPluie = 1f + Math.max(0, pluie) / 100f;
        this.multEau = 1f + Math.max(0, eau) / 100f;
        this.multSilencieux = 1f + Math.max(0, silencieux) / 100f;
        this.suivie = !exclue && this.usurePct > 0;
        this.maxGundb = Math.max(1, maxGundb);
    }

    /** Nombre de tirs (sans pluie ni accessoire) pour passer de 100 % a 0 %, ou -1 si l'arme ne s'use pas. */
    public int tirsPourRuiner() {
        return usureMilliemes <= 0 ? -1 : Math.round(1000f / usureMilliemes);
    }
}
