package fr.heraliz.clashwar.moteur.api;

import fr.heraliz.clashwar.moteur.net.PaquetsMoteur;
import fr.heraliz.clashwar.reseau.MatchsNet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Bandeau de match (MATCHS_SPEC §1.8) : temps, equipes en pastilles colorees (vivants / total), score. Le client le
 * dessine en haut au centre (DM Sans, verre, aucun vanilla) jusqu'au prochain envoi ou {@link #effacer}. A renvoyer
 * a chaque changement (pas a chaque tick : au plus 1 fois par seconde pour le temps, le client decompte seul entre
 * deux envois). Fil serveur.
 */
public final class Hud {
    private Hud() {
    }

    /** Pastille d'equipe : nom court, couleur RGB, vivants, total. */
    public record Pastille(String nom, int couleur, int vivants, int total) {
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(nom, 48);
            buf.writeInt(couleur);
            buf.writeVarInt(vivants);
            buf.writeVarInt(total);
        }

        public static Pastille read(FriendlyByteBuf buf) {
            return new Pastille(buf.readUtf(48), buf.readInt(), buf.readVarInt(), buf.readVarInt());
        }
    }

    /**
     * Contenu du bandeau.
     *
     * @param titre    ligne du haut (« CLASHWAR · QUART DE FINALE », « MANCHE 3 »...)
     * @param secondes temps restant affiche (decompte local), -1 = pas de temps
     * @param equipes  pastilles d'equipes (au plus 8)
     * @param score    texte de score (« 2 — 1 », « 1 250 pts »), vide = aucun
     */
    public record Bandeau(Component titre, int secondes, List<Pastille> equipes, Component score) {
        public Bandeau {
            equipes = List.copyOf(equipes);
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeComponent(titre);
            buf.writeVarInt(secondes + 1);
            int n = Math.min(8, equipes.size());
            buf.writeVarInt(n);
            for (int i = 0; i < n; i++) equipes.get(i).write(buf);
            buf.writeComponent(score);
        }

        public static Bandeau read(FriendlyByteBuf buf) {
            Component titre = buf.readComponent();
            int secondes = buf.readVarInt() - 1;
            int n = Math.min(8, buf.readVarInt());
            List<Pastille> l = new ArrayList<>(n);
            for (int i = 0; i < n; i++) l.add(Pastille.read(buf));
            return new Bandeau(titre, secondes, l, buf.readComponent());
        }
    }

    /** Envoie le bandeau a un joueur. */
    public static void envoyer(ServerPlayer joueur, Bandeau bandeau) {
        MatchsNet.versJoueur(joueur, new PaquetsMoteur.HudS2C(bandeau));
    }

    /** Envoie le bandeau a plusieurs joueurs. */
    public static void envoyer(Collection<ServerPlayer> joueurs, Bandeau bandeau) {
        MatchsNet.versJoueurs(joueurs, new PaquetsMoteur.HudS2C(bandeau));
    }

    /** Bandeau standard d'une partie (pastilles de ses equipes) envoye a ses joueurs connectes. */
    public static void envoyer(Partie partie, Component titre, int secondes, Component score) {
        envoyer(partie.joueursEnLigne(), bandeau(partie, titre, secondes, score));
    }

    /** Construit le bandeau standard d'une partie. */
    public static Bandeau bandeau(Partie partie, Component titre, int secondes, Component score) {
        List<Pastille> l = new ArrayList<>(partie.equipes().size());
        for (Equipe e : partie.equipes()) l.add(new Pastille(e.nom(), e.couleur(), e.nbVivants(), e.taille()));
        return new Bandeau(titre, secondes, l, score);
    }

    /** Retire le bandeau (fin de match, retour au Coffre). */
    public static void effacer(ServerPlayer joueur) {
        MatchsNet.versJoueur(joueur, new PaquetsMoteur.HudS2C(null));
    }

}
