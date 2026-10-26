package fr.heraliz.ping.server;

import fr.heraliz.noyau.api.Matchs;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

/**
 * Lien avec les matchs (ClashWar), lus dans le noyau HeraliZ (chargé seulement si le noyau est présent : voir
 * {@link Compat}). En match, le cercle d'un joueur est son équipe. Fil serveur.
 */
final class MatchsCompat {
    private MatchsCompat() {
    }

    /** Résultats de {@link #equipe} autres qu'une couleur. */
    static final int HORS_MATCH = -2, REFUS = -1;

    /** Vrai si le joueur est dans une partie (inscrit, vivant ou spectateur). */
    static boolean enMatch(ServerPlayer p) {
        try {
            return Matchs.enMatch(p);
        } catch (LinkageError ex) {
            Compat.couperMatchs(ex);
            return false;
        }
    }

    /**
     * Joueur en match : remplit {@code out} avec les coéquipiers connectés dans le même monde (lui compris ; les
     * spectateurs seulement si {@code spectateursVoient}) et rend la couleur de l'équipe. {@link #REFUS} si le joueur
     * est spectateur de sa partie (un spectateur ne pingue pas), {@link #HORS_MATCH} s'il n'est pas en match.
     */
    static int equipe(ServerPlayer p, List<ServerPlayer> out, boolean spectateursVoient) {
        try {
            UUID u = p.getUUID();
            String partie = Matchs.partie(u);
            if (partie == null) return HORS_MATCH;
            if (!Regles.peutPinguer(p.isAlive(), p.isSpectator() || Matchs.spectateur(u))) return REFUS;
            String equipe = Matchs.equipe(u);
            if (equipe == null) {
                out.add(p);
                return Cercles.NEUTRE;
            }
            for (ServerPlayer d : p.server.getPlayerList().getPlayers()) {
                if (d == p) continue;
                UUID m = d.getUUID();
                if (!equipe.equals(Matchs.equipe(m))) continue;
                boolean spectateur = d.isSpectator() || Matchs.spectateur(m);
                if (Regles.recoitEnMatch(false, d.hasDisconnected(), d.level() == p.level(), partie.equals(Matchs.partie(m)), spectateur,
                        spectateursVoient)) out.add(d);
            }
            out.add(p);
            int c = Matchs.couleurEquipe(u);
            return c < 0 ? Cercles.NEUTRE : c & 0xFFFFFF;
        } catch (LinkageError ex) {
            Compat.couperMatchs(ex);
            return HORS_MATCH;
        }
    }
}
