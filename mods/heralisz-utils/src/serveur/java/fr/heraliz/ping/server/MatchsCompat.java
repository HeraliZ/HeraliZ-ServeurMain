package fr.heraliz.ping.server;

import fr.heralisz.matchs.moteur.api.Equipe;
import fr.heralisz.matchs.moteur.api.Groupe;
import fr.heralisz.matchs.moteur.api.Groupes;
import fr.heralisz.matchs.moteur.api.MatchApi;
import fr.heralisz.matchs.moteur.api.Partie;
import fr.heralisz.matchs.moteur.api.Spectateur;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

import java.util.List;
import java.util.UUID;

/**
 * Lien avec HeraliZ Matchs (charge seulement si le mod est present : voir {@link Compat}). En match (duels, ClashWar,
 * zombies), le cercle d'un joueur est son equipe ; hors match, un joueur sans clan peut pinguer son groupe. Fil serveur.
 */
final class MatchsCompat {
    private MatchsCompat() {
    }

    /** Resultats de {@link #equipe} autres qu'une couleur. */
    static final int HORS_MATCH = -2, REFUS = -1;

    /** Vrai si le joueur est dans une partie (inscrit, vivant ou spectateur). */
    static boolean enMatch(ServerPlayer p) {
        try {
            return MatchApi.enMatch(p);
        } catch (LinkageError ex) {
            Compat.couperMatchs(ex);
            return false;
        }
    }

    /**
     * Joueur en match : remplit {@code out} avec les coequipiers connectes dans le meme monde (lui compris ; les
     * spectateurs seulement si {@code spectateursVoient}) et rend la couleur de l'equipe. {@link #REFUS} si le joueur
     * est spectateur de sa partie (un spectateur ne pingue pas), {@link #HORS_MATCH} s'il n'est pas en match.
     */
    static int equipe(ServerPlayer p, List<ServerPlayer> out, boolean spectateursVoient) {
        try {
            Partie partie = MatchApi.partieDe(p);
            if (partie == null) return HORS_MATCH;
            UUID u = p.getUUID();
            if (!Regles.peutPinguer(p.isAlive(), p.isSpectator() || partie.spectateurs().contains(u) || Spectateur.est(u))) return REFUS;
            Equipe e = partie.equipeDe(u);
            if (e == null) {
                out.add(p);
                return Cercles.NEUTRE;
            }
            PlayerList liste = p.server.getPlayerList();
            for (UUID m : e.joueurs()) {
                ServerPlayer d = liste.getPlayer(m);
                if (d == null || d == p) continue;
                boolean spectateur = d.isSpectator() || partie.spectateurs().contains(m) || Spectateur.est(m);
                if (Regles.recoitEnMatch(false, d.hasDisconnected(), d.level() == p.level(), MatchApi.partieDe(d) == partie, spectateur,
                        spectateursVoient)) out.add(d);
            }
            out.add(p);
            return e.couleur() & 0xFFFFFF;
        } catch (LinkageError ex) {
            Compat.couperMatchs(ex);
            return HORS_MATCH;
        }
    }

    /** Hors match : ajoute les membres connectes du groupe du joueur ; faux s'il n'a pas de groupe. */
    static boolean groupe(ServerPlayer p, List<ServerPlayer> out) {
        try {
            Groupe g = Groupes.de(p.getUUID());
            if (g == null) return false;
            PlayerList liste = p.server.getPlayerList();
            for (UUID m : g.membres()) {
                ServerPlayer d = liste.getPlayer(m);
                if (d != null) out.add(d);
            }
            return true;
        } catch (LinkageError ex) {
            Compat.couperMatchs(ex);
            return false;
        }
    }
}
