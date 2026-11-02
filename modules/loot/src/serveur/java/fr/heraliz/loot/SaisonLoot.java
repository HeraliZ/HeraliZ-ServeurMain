package fr.heraliz.loot;

import java.lang.reflect.Method;
import java.util.Locale;
import net.minecraft.server.level.ServerPlayer;

/**
 * Points de saison des clans (HeraliZ Clans 3.6.0, {@code fr.heralisz.clans.saison.SaisonApi.pve}, par réflexion ;
 * absent : rien). Le barème est chez Clans : on n'envoie qu'une source. Donnés une seule fois par remplissage, au premier
 * qui ouvre (le contenu est tiré à ce moment-là) : rouvrir ne rapporte rien.
 */
final class SaisonLoot {
    private SaisonLoot() {
    }

    private static boolean cherche;
    private static Method pve;

    static void pve(ServerPlayer p, String source) {
        if (!cherche) {
            cherche = true;
            try {
                pve = Class.forName("fr.heralisz.clans.saison.SaisonApi").getMethod("pve", ServerPlayer.class, String.class, double.class);
            } catch (ReflectiveOperationException | LinkageError e) {
                pve = null;
            }
        }
        if (pve == null || p.isCreative() || p.isSpectator()) return;
        try {
            pve.invoke(null, p, source, 1.0);
        } catch (ReflectiveOperationException | RuntimeException e) {
            HeralizLoot.LOG.warn("[loot] saison des clans : {}", e.toString());
        }
    }

    /** Conteneur de palier 1 à 7 ouvert pour la première fois depuis son remplissage. */
    static void conteneur(ServerPlayer p, int palier) {
        if (palier >= 1 && palier <= 7) pve(p, "conteneur_t" + palier);
    }

    /** Couleur d'une salle à carte donnée par Bunker : « verte », « bleue », « rouge » (autre chose : null). */
    @javax.annotation.Nullable
    static String couleur(@javax.annotation.Nullable String c) {
        if (c == null) return null;
        String l = c.trim().toLowerCase(Locale.ROOT);
        return l.equals("verte") || l.equals("bleue") || l.equals("rouge") ? l : null;
    }

    /**
     * Couleur d'une salle déclarée avant le 02/11, lue dans son nom affiché (« … (rouge) ») : verte, bleue, rouge ou null.
     * Seulement pour les anciennes données et un Bunker plus ancien : depuis, Bunker donne la couleur à part (audit
     * mods-2 §18 : changer ce texte coupait en silence les points de saison des salles et les quêtes du jour « salle »).
     */
    @javax.annotation.Nullable
    static String couleurDuLibelle(String libelle) {
        String l = libelle.toLowerCase(Locale.ROOT);
        if (l.endsWith("(rouge)")) return "rouge";
        if (l.endsWith("(bleue)")) return "bleue";
        if (l.endsWith("(verte)")) return "verte";
        return null;
    }
}
