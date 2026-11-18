package fr.heralisz.economie.client;

import fr.heralisz.economie.api.EconomieApi;
import fr.heralisz.economie.network.Packets;
import net.minecraft.client.resources.language.I18n;

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

    /** Derniere variation : montant, motif brut ; delta = 0 = rien a montrer. */
    private static long delta;
    private static String motif = "";

    // ------------------------------------------------------------------ reception
    public static void reglages(Packets.ReglagesS2C m) {
        nomSingulier = m.nomSingulier();
        nomPluriel = m.nomPluriel();
        symbole = m.symbole();
        hudPermanent = m.hudPermanent();
        // l'API (format des montants) prend aussi les noms du serveur ; le plafond n'est pas envoye (serveur seulement)
        EconomieApi.reglages(m.nomSingulier(), m.nomPluriel(), m.symbole(), EconomieApi.maxSolde());
    }

    public static void recevoir(Packets.SoldeS2C m) {
        solde = m.solde();
        connu = true;
        if (m.delta() != 0) {
            delta = m.delta();
            motif = m.motif();
            // 18/11 : la variation s'affiche dans un bandeau commun à droite de l'écran (maquette validée), au lieu d'une
            // pastille à part : « +250 hérals » puis « Quête · solde 1 250 hérals »
            String raison = motifLisible(motif), total = I18n.get("economie.hud.solde", format(solde));
            fr.heraliz.noyau.client.Bandeaux.ajouter(formatSigne(delta), raison.isEmpty() ? total : raison + " · " + total,
                    delta >= 0 ? 0x5FD08A : 0xE3201B, 3);
        }
    }

    public static void vider() {
        solde = 0;
        connu = false;
        delta = 0;
        motif = "";
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
}
