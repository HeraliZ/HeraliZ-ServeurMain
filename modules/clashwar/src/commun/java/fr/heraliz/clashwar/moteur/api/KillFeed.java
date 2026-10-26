package fr.heraliz.clashwar.moteur.api;

import fr.heraliz.clashwar.moteur.net.PaquetsMoteur;
import fr.heraliz.clashwar.reseau.MatchsNet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

/**
 * Fil des eliminations en haut a droite (« Vortex ▸ Dziga · AK-47 », MATCHS_SPEC §1.8) : 5 lignes au plus, 5 s
 * chacune, fondu. Fil serveur.
 */
public final class KillFeed {
    private KillFeed() {
    }

    /**
     * Ligne du fil.
     *
     * @param tueur          nom du tueur, vide = environnement (chute, zone, vide)
     * @param couleurTueur   couleur RGB de son equipe
     * @param victime        nom de la victime
     * @param couleurVictime couleur RGB de son equipe
     * @param arme           nom affiche de l'arme (« AK-47 », « Couteau »), vide = aucun
     * @param tete           tir a la tete (icone)
     */
    public record Ligne(String tueur, int couleurTueur, String victime, int couleurVictime, String arme, boolean tete) {
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(tueur, 48);
            buf.writeInt(couleurTueur);
            buf.writeUtf(victime, 48);
            buf.writeInt(couleurVictime);
            buf.writeUtf(arme, 64);
            buf.writeBoolean(tete);
        }

        public static Ligne read(FriendlyByteBuf buf) {
            return new Ligne(buf.readUtf(48), buf.readInt(), buf.readUtf(48), buf.readInt(), buf.readUtf(64), buf.readBoolean());
        }
    }

    /** Ajoute la ligne chez ces joueurs. */
    public static void ajouter(Collection<ServerPlayer> joueurs, Ligne ligne) {
        MatchsNet.versJoueurs(joueurs, new PaquetsMoteur.KillFeedS2C(ligne));
    }

    /** Ajoute la ligne chez tous les joueurs connectes de la partie. */
    public static void ajouter(Partie partie, Ligne ligne) {
        ajouter(partie.joueursEnLigne(), ligne);
    }
}
