package fr.heraliz.noyau.api;

import fr.heraliz.noyau.net.NoyauNet;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;

/**
 * Messages à l'écran d'un joueur dans le style commun (18/11, maquette validée {@code maquettes/notifications/}) : un
 * bandeau à droite de l'écran, titre en gras, texte dessous, trait de couleur à gauche (empilés, le plus récent en haut).
 * À appeler côté serveur (fil du serveur) à la place d'un message de chat ou d'un titre pour une information du moment :
 * quête, vente, extraction, clan... Les textes sont des composants : clés traduites chez le joueur, avec leur texte de
 * secours ({@code Component.translatableWithFallback}). Classe lue par d'autres mods : ne pas la renommer.
 */
public final class Notifs {
    private Notifs() {
    }

    /** Couleurs du trait : réussite, hérals, information, attention, danger, neutre. */
    public static final int OK = 0x5FD08A, OR = 0xE8C36A, INFO = 0x6FA8FF, ALERTE = 0xE8B34A, DANGER = 0xE3201B, NEUTRE = 0x8F95A0;

    /** Bandeau de 4,5 s. */
    public static void envoyer(ServerPlayer joueur, Component titre, @Nullable Component texte, int couleur) {
        envoyer(joueur, titre, texte, couleur, 0);
    }

    /** Bandeau de {@code secondes} s (0 : 4,5 s ; 60 au plus). */
    public static void envoyer(ServerPlayer joueur, Component titre, @Nullable Component texte, int couleur, int secondes) {
        if (joueur == null || joueur.connection == null) return;
        NoyauNet.versJoueur(joueur, new NoyauNet.BandeauS2C(titre, texte, couleur, secondes));
    }
}
