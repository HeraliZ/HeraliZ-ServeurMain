package fr.heraliz.ping.server;

import fr.heraliz.ping.PingConfig;
import fr.heraliz.ping.server.Regles.Origine;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Qui voit le ping d'un joueur : jamais tout le serveur (en PvP, ce serait donner sa position et celle de la cible).
 * <ol>
 *     <li>En match (HeraliZ Matchs) : son equipe, et elle seule. Un spectateur de la partie ne pingue pas ; les
 *     spectateurs ne recoivent rien sauf si {@code spectateurs_voient}.</li>
 *     <li>Hors match : les membres connectes de son clan (HeraliZ Clans), couleur = accent du clan.</li>
 *     <li>Sans clan : son groupe (/groupe de HeraliZ Matchs) s'il en a un et si {@code groupe_hors_match}.</li>
 *     <li>Sinon : lui seul.</li>
 * </ol>
 * Les decisions sont dans {@link Regles} (testees hors du jeu) ; ici on rassemble les faits. Fil serveur, aucune
 * allocation hors la liste fournie (reutilisee).
 */
public final class Cercles {
    private Cercles() {
    }

    /** Couleur d'un auteur sans clan ni equipe (accent neutre du kit d'interface). */
    public static final int NEUTRE = 0x6FA8FF;
    /** Resultat de {@link #destinataires} : l'auteur ne peut pas pinguer (spectateur de sa partie). */
    public static final int REFUS = -1;

    private static Origine derniere = Origine.SEUL;

    /**
     * Remplit {@code out} (vide d'abord) avec les joueurs qui doivent recevoir le ping de {@code auteur}, lui compris,
     * et rend la couleur de l'auteur (RGB), ou {@link #REFUS}.
     */
    public static int destinataires(ServerPlayer auteur, List<ServerPlayer> out) {
        out.clear();
        boolean specVoient = PingConfig.SPECTATEURS_VOIENT.get();
        boolean matchs = Compat.matchs();
        if (matchs) {
            int r = MatchsCompat.equipe(auteur, out, specVoient);
            if (r != MatchsCompat.HORS_MATCH) {
                derniere = Regles.origine(true, false, false, false);
                return r == MatchsCompat.REFUS ? REFUS : r;
            }
        }
        int accentClan = Compat.clans() ? ClansCompat.membres(auteur, out) : -1;
        boolean groupe = false;
        if (accentClan < 0 && matchs && PingConfig.GROUPE_HORS_MATCH.get()) {
            out.clear();
            groupe = MatchsCompat.groupe(auteur, out);
        }
        derniere = Regles.origine(false, accentClan >= 0, groupe, PingConfig.GROUPE_HORS_MATCH.get());
        for (int i = out.size() - 1; i >= 0; i--) {
            ServerPlayer d = out.get(i);
            boolean garder = d != auteur && Regles.recoitHorsMatch(false, d.hasDisconnected(), d.level() == auteur.level(), d.isSpectator(),
                    matchs && MatchsCompat.enMatch(d), specVoient);
            if (!garder) out.remove(i);
        }
        out.add(auteur);
        return accentClan >= 0 ? accentClan : NEUTRE;
    }

    /** Origine du dernier cercle calcule. */
    public static Origine derniere() {
        return derniere;
    }
}
