package fr.heraliz.ping.server;

import fr.heraliz.ping.PingConfig;
import fr.heraliz.ping.server.Regles.Origine;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Qui voit le ping d'un joueur : jamais tout le serveur (en PvP, ce serait donner sa position et celle de la cible).
 * <ol>
 *     <li>En match (ClashWar) : son equipe, et elle seule. Un spectateur de la partie ne pingue pas ; les
 *     spectateurs ne recoivent rien sauf si {@code spectateurs_voient}.</li>
 *     <li>Hors match, sur un canal libre ({@link Canaux}, {@code /ping canal <nom>}) : les membres connectes du canal.</li>
 *     <li>Hors match, sinon : les membres connectes de son clan (HeraliZ Clans) restes sur le canal du clan, couleur =
 *     accent du clan.</li>
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
        // 25/10 : canal libre (/ping canal <nom>) avant le clan ; sur le canal du clan, les membres partis sur un canal
        // libre ne recoivent plus rien (un ping ne va qu'aux joueurs du meme canal)
        String canal = Canaux.de(auteur.getUUID());
        int couleur;
        if (canal != null) {
            Canaux.enLigne(canal, auteur.server, out);
            derniere = Origine.CANAL;
            couleur = Canaux.couleur(canal);
        } else {
            int accentClan = Compat.clans() ? ClansCompat.membres(auteur, out) : -1;
            derniere = Regles.origine(false, accentClan >= 0, false, false);
            couleur = accentClan >= 0 ? accentClan : NEUTRE;
        }
        for (int i = out.size() - 1; i >= 0; i--) {
            ServerPlayer d = out.get(i);
            boolean garder = d != auteur && (canal != null || Canaux.de(d.getUUID()) == null)
                    && Regles.recoitHorsMatch(false, d.hasDisconnected(), d.level() == auteur.level(), d.isSpectator(),
                    matchs && MatchsCompat.enMatch(d), specVoient);
            if (!garder) out.remove(i);
        }
        out.add(auteur);
        return couleur;
    }

    /** Origine du dernier cercle calcule. */
    public static Origine derniere() {
        return derniere;
    }
}
