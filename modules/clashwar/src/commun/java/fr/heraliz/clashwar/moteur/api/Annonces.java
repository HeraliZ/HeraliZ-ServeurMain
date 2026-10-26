package fr.heraliz.clashwar.moteur.api;

import fr.heraliz.clashwar.HeralizClashWar;
import fr.heraliz.clashwar.moteur.net.PaquetsMoteur;
import fr.heraliz.clashwar.reseau.MatchsNet;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

/**
 * Grandes annonces centrales animees (« ROUND 1 », « VICTOIRE », « FINALE : [NOVA] contre [RVN] »,
 * « [NOVA] REMPORTE LE CLASHWAR »), compte a rebours de preparation et messages de chat stylises (MATCHS_SPEC §1.8).
 * Sons maison uniquement (id de SoundEvent du mod, generes par script). Fil serveur.
 */
public final class Annonces {
    private Annonces() {
    }

    /** Sons maison du moteur (a passer dans {@link Annonce#son}). */
    public static final String SON_ANNONCE = "heraliz_clashwar:moteur_annonce", SON_VICTOIRE = "heraliz_clashwar:moteur_victoire",
            SON_DEFAITE = "heraliz_clashwar:moteur_defaite", SON_FINALE = "heraliz_clashwar:moteur_finale",
            SON_MANCHE = "heraliz_clashwar:moteur_manche", SON_FIN = "heraliz_clashwar:moteur_fin";

    /** Mise en forme de l'annonce cote client. */
    public enum Style {
        /** Titre blanc, sous-titre attenue. */
        NORMALE,
        /** Or, eclat. */
        VICTOIRE,
        /** Rouge desature. */
        DEFAITE,
        /** Finale / vainqueur du tournoi : plus grand, plus long, bannieres. */
        FINALE,
        /** Numero de manche (style craie rouge des zombies). */
        MANCHE
    }

    /**
     * Annonce centrale.
     *
     * @param titre      gros texte (capitales espacees)
     * @param sousTitre  ligne sous le titre (vide = aucune)
     * @param couleur    couleur d'accent RGB
     * @param son        id du son a jouer ("heraliz_clashwar:annonce_victoire"), vide = aucun
     * @param dureeTicks duree d'affichage (fondus compris), 20 a 200
     * @param style      mise en forme
     */
    public record Annonce(Component titre, Component sousTitre, int couleur, String son, int dureeTicks, Style style) {
        public Annonce {
            dureeTicks = Math.max(20, Math.min(200, dureeTicks));
            son = son == null ? "" : son;
        }

        /** Annonce NORMALE de 3 s, sans son. */
        public static Annonce simple(Component titre, Component sousTitre, int couleur) {
            return new Annonce(titre, sousTitre, couleur, "", 60, Style.NORMALE);
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeComponent(titre);
            buf.writeComponent(sousTitre);
            buf.writeInt(couleur);
            buf.writeUtf(son, 128);
            buf.writeVarInt(dureeTicks);
            buf.writeEnum(style);
        }

        public static Annonce read(FriendlyByteBuf buf) {
            return new Annonce(buf.readComponent(), buf.readComponent(), buf.readInt(), buf.readUtf(128), buf.readVarInt(), buf.readEnum(Style.class));
        }
    }

    /** Grande annonce a un joueur. */
    public static void grande(ServerPlayer joueur, Annonce annonce) {
        MatchsNet.versJoueur(joueur, new PaquetsMoteur.AnnonceS2C(annonce));
    }

    /** Grande annonce a plusieurs joueurs. */
    public static void grande(Collection<ServerPlayer> joueurs, Annonce annonce) {
        MatchsNet.versJoueurs(joueurs, new PaquetsMoteur.AnnonceS2C(annonce));
    }

    /** Grande annonce a tout le serveur (finale, vainqueur du ClashWar). */
    public static void grandeATous(Annonce annonce) {
        MatchsNet.versTous(new PaquetsMoteur.AnnonceS2C(annonce));
    }

    /** Compte a rebours central (« 10 », « 9 »... puis « COMBAT ! ») decompte par le client, avec un titre. */
    public static void compteARebours(Collection<ServerPlayer> joueurs, int secondes, Component titre) {
        MatchsNet.versJoueurs(joueurs, new PaquetsMoteur.CompteS2C(Math.max(0, secondes), titre));
    }

    /** Message de chat stylise a tout le serveur (prefixe HeraliZ Matchs, couleurs de la charte). */
    public static void chat(MinecraftServer serveur, Component message) {
        serveur.getPlayerList().broadcastSystemMessage(PaquetsMoteur.prefixe().append(message), false);
    }

    /** Message de chat stylise a un joueur. */
    public static void chat(ServerPlayer joueur, Component message) {
        joueur.sendSystemMessage(PaquetsMoteur.prefixe().append(message));
    }

    /**
     * Reponse a l'auteur d'une commande (ajout, 01/10), toujours visible : la regle du monde {@code sendCommandFeedback}
     * (fausse sur le serveur HeraliZ, reprise de l'ancien serveur) masque {@code CommandSourceStack#sendSuccess}. Un
     * joueur la recoit directement dans son chat (prefixe HeraliZ Matchs) et elle est ecrite dans le journal du serveur ;
     * la console ou un bloc de commande la recoit par {@code sendSuccess}. Les erreurs restent en {@code sendFailure}
     * (jamais masque).
     */
    public static void retour(CommandSourceStack source, Component message) {
        if (source.getEntity() instanceof ServerPlayer p) {
            chat(p, message);
            HeralizClashWar.LOG.info("[matchs] commande de {} : {}", p.getGameProfile().getName(), message.getString());
        } else {
            source.sendSuccess(() -> message, false);
        }
    }
}
