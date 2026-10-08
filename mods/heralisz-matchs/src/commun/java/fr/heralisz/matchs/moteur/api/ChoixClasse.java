package fr.heralisz.matchs.moteur.api;

import fr.heralisz.matchs.pont.Services;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Ecran de choix de classe (MATCHS_SPEC §1.7, §2.4, §3) : cartes epurees (silhouette de l'arme, 3 jauges portee /
 * cadence / mobilite), limites par classe et par equipe affichees (« 1/1 SNIPER PRIS PAR VORTEX »), compte a rebours ;
 * choix par defaut a l'echeance. Toute regle est verifiee cote serveur (kit propose, limite de la classe dans
 * l'equipe du joueur : {@link Partie#equipeDe}). Fil serveur.
 * <p>Exemple ClashWar : {@code ChoixClasse.ouvrir(partie, partie.joueursEnLigne(), Kits.pourMode("clashwar"),
 * Map.of(Kit.CLASSE_SNIPER, 1, Kit.CLASSE_POMPE, 1), 10, titre, choix -> choix.forEach((u, k) ->
 * Equipement.donner(partie.joueur(u), k, partie.id())))}.
 */
public final class ChoixClasse {
    private ChoixClasse() {
    }

    /** Duree par defaut du choix (s). */
    public static final int SECONDES_DEFAUT = 10;

    /**
     * Ouvre le choix pour ces joueurs (un joueur deja dans un autre choix en est retire). A l'echeance (ou des que tous
     * ont choisi), l'ecran se ferme et {@code quandFini} recoit le kit de chaque joueur encore connecte : son choix, ou
     * le premier kit de la liste dont la classe n'est pas pleine dans son equipe, ou le premier kit.
     *
     * @param limites  nombre maximal par classe ({@code Kit.CLASSE_*}) et par equipe ; classe absente = sans limite
     * @param secondes duree (1 a 60)
     * @param titre    titre de l'ecran (« CLASHWAR · QUART DE FINALE »)
     */
    public static void ouvrir(Partie partie, Collection<ServerPlayer> joueurs, List<Kit> kits, Map<String, Integer> limites,
                              int secondes, Component titre, Consumer<Map<UUID, Kit>> quandFini) {
        Services.Moteur m = Services.MOTEUR;   // moteur/kits/ChoixService (serveur)
        if (m != null) m.choixOuvrir(partie, joueurs, kits, limites, secondes, titre, quandFini);
    }

    /** Vrai si un choix est en cours pour cette partie. */
    public static boolean enCours(Partie partie) {
        Services.Moteur m = Services.MOTEUR;
        return m != null && m.choixEnCours(partie);
    }

    /** Ferme sans appeler {@code quandFini} tous les choix en cours de cette partie. */
    public static void annuler(Partie partie) {
        Services.Moteur m = Services.MOTEUR;
        if (m != null) m.choixAnnuler(partie);
    }

    /** Kit choisi par ce joueur dans le choix en cours, ou null. */
    @Nullable
    public static Kit choix(UUID joueur) {
        Services.Moteur m = Services.MOTEUR;
        return m != null ? m.choixDe(joueur) : null;
    }
}
