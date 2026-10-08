package fr.heralisz.economie.client;

import fr.heralisz.economie.api.EconomieApi;
import fr.heralisz.economie.network.Packets;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.util.Mth;

import java.util.Locale;

/**
 * Etat client : le solde que le serveur m'a envoye, les reglages (noms de l'unite, HUD permanent) et la derniere
 * variation (pour la pastille du HUD). Fil de rendu uniquement. Aucun calcul par image en dehors du HUD.
 */
public final class ClientSolde {
    private ClientSolde() {
    }

    private static long solde;
    private static boolean connu;
    private static String nomSingulier = "héral", nomPluriel = "hérals", symbole = "";
    private static boolean hudPermanent;

    /** Derniere variation : montant, motif brut, instant (ms) ; delta = 0 = rien a montrer. */
    private static long delta;
    private static String motif = "";
    private static long depuis;

    // ------------------------------------------------------------------ reception
    public static void reglages(Packets.ReglagesS2C m) {
        nomSingulier = m.nomSingulier();
        nomPluriel = m.nomPluriel();
        symbole = m.symbole();
        hudPermanent = m.hudPermanent();
    }

    public static void recevoir(Packets.SoldeS2C m) {
        solde = m.solde();
        connu = true;
        if (m.delta() != 0) {
            delta = m.delta();
            motif = m.motif();
            depuis = System.currentTimeMillis();
        }
    }

    public static void vider() {
        solde = 0;
        connu = false;
        delta = 0;
        motif = "";
        depuis = 0;
    }

    // ------------------------------------------------------------------ lecture
    public static long solde() {
        return solde;
    }

    /** Vrai des que le serveur a envoye un solde (sinon le HUD ne montre rien). */
    public static boolean connu() {
        return connu;
    }

    public static boolean hudPermanent() {
        return hudPermanent;
    }

    public static long delta() {
        return delta;
    }

    public static String motif() {
        return motif;
    }

    /** Millisecondes ecoulees depuis la derniere variation (Long.MAX_VALUE si aucune). */
    public static long ageVariation() {
        return depuis == 0 ? Long.MAX_VALUE : System.currentTimeMillis() - depuis;
    }

    /** Pour le banc d'essai / debug : simule une variation. */
    public static void simuler(long nouveauSolde, long d, String m) {
        recevoir(new Packets.SoldeS2C(nouveauSolde, d, m));
    }

    // ------------------------------------------------------------------ formats
    /** « 1 250 hérals » avec les noms recus du serveur. */
    public static String format(long v) {
        return EconomieApi.format(v, nomSingulier, nomPluriel, symbole);
    }

    public static String formatSigne(long v) {
        return (v >= 0 ? "+" : "-") + format(Math.abs(v));
    }

    /** Nom de l'unite au pluriel (capitales espacees du HUD permanent). */
    public static String unite(long v) {
        return !symbole.isEmpty() ? symbole : (EconomieApi.pluriel(v) ? nomPluriel : nomSingulier);
    }

    /**
     * Motif lisible : cle economie.motif.&lt;prefixe&gt; si elle existe (« quete:zombies » -> « Quête »), sinon le
     * motif brut, premiere lettre en capitale, sans son prefixe technique.
     */
    public static String motifLisible(String brut) {
        if (brut == null || brut.isEmpty()) return "";
        int i = brut.indexOf(':');
        String prefixe = (i < 0 ? brut : brut.substring(0, i)).trim().toLowerCase(Locale.ROOT);
        String cle = "economie.motif." + prefixe;
        if (I18n.exists(cle)) return I18n.get(cle);
        String reste = i < 0 ? brut : brut.substring(i + 1);
        reste = reste.replace('_', ' ').trim();
        if (reste.isEmpty()) reste = prefixe;
        return reste.isEmpty() ? "" : Character.toUpperCase(reste.charAt(0)) + reste.substring(1);
    }

    /** Alpha 0..1 de la pastille selon l'age de la variation : 0,2 s d'apparition, 2,3 s pleine, 0,5 s de fondu. */
    public static float alphaPastille(long age) {
        if (age < 0 || age >= 3000) return 0;
        if (age < 200) return age / 200f;
        if (age > 2500) return 1 - (age - 2500) / 500f;
        return 1;
    }

    /** Glissement vertical (px) a l'apparition : -6 -> 0. */
    public static float glissement(long age) {
        if (age >= 200) return 0;
        float t = Mth.clamp(age / 200f, 0, 1);
        t = 1 - (1 - t) * (1 - t);
        return -6 * (1 - t);
    }
}
