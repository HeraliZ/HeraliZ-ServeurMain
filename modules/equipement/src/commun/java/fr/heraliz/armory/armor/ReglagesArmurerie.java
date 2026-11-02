package fr.heraliz.armory.armor;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Réglages de l'Armurerie lus par le code commun (audit du 02/11, point 11). Le fichier (ArmorConfig,
 * config/heraliz_armory-common.toml) est un réglage du serveur seulement, déclaré par la partie serveur : jamais écrit sur
 * le PC des joueurs. Le serveur recopie ici les valeurs à chaque chargement du fichier.
 * <ul>
 *    <li>envoyés aux joueurs (ArmoryNetwork.ReglagesJoueur : à la connexion et quand le fichier change) : bonus de nage des
 *    palmes, bouteille d'air et sa recharge (infobulle, attributs, carte « Oxygène »), slots Curios bloqués par une
 *    combinaison (le jeu du joueur refuse le slot comme le serveur) ; avant, le jeu du joueur lisait sa propre copie ;</li>
 *    <li>serveur seulement, jamais envoyé : plafond des protections (les autres réglages des protections restent dans
 *    ArmorConfig, côté serveur).</li>
 * </ul>
 * Valeurs d'origine tant que rien n'est reçu.
 */
public final class ReglagesArmurerie {
    private ReglagesArmurerie() {}

    /** Slots Curios de vêtements bloqués par une combinaison, à l'origine (le sac à dos « back » n'y est pas). */
    public static final List<String> SLOTS_BLOQUES_DEFAUT = List.of("head", "mask", "face", "belt", "hands", "knees", "body",
            "bracelet", "ear_protection");
    /** Au plus ce nombre de slots, de 64 lettres chacun (paquet). */
    public static final int MAX_SLOTS = 64, MAX_LETTRES = 64;

    private static volatile double nage = 0.8;
    private static volatile int bouteilleSecondes = 250, rechargeSecondes = 5;
    private static volatile Set<String> slotsBloques = Set.copyOf(SLOTS_BLOQUES_DEFAUT);
    private static volatile float plafond = Protection.CAP_DEFAUT;

    /** Bonus de vitesse de nage des palmes (0.8 = +80 %). */
    public static double nage() {
        return nage;
    }

    /** Réserve d'air de la bouteille (secondes la tête sous l'eau). */
    public static int bouteilleSecondes() {
        return bouteilleSecondes;
    }

    /** Recharge complète de la bouteille hors de l'eau (secondes, 0 = immédiate). */
    public static int rechargeSecondes() {
        return rechargeSecondes;
    }

    /** Slot Curios de vêtements bloqué par une combinaison. */
    public static boolean slotBloque(String id) {
        return slotsBloques.contains(id);
    }

    public static Set<String> slotsBloques() {
        return slotsBloques;
    }

    /** Plafond commun des protections (%) : serveur seulement. */
    public static float plafond() {
        return plafond;
    }

    /** Valeurs envoyées aux joueurs (serveur : chargement du fichier ; joueur : paquet reçu). */
    public static void definirJoueur(double nouveauNage, int bouteille, int recharge, Collection<? extends String> slots) {
        nage = Math.max(0.0, Math.min(3.0, nouveauNage));
        bouteilleSecondes = Math.max(10, Math.min(3600, bouteille));
        rechargeSecondes = Math.max(0, Math.min(600, recharge));
        slotsBloques = Set.copyOf(slots);
    }

    /** Plafond des protections : serveur seulement. */
    public static void definirPlafond(double nouveau) {
        plafond = (float) Math.max(0.0, Math.min(100.0, nouveau));
    }
}
