package fr.heraliz.crac.bases;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;

/**
 * Textes du monde des bases envoyés par le serveur : clé de assets/crac/lang (fr_fr, en_us, ru_ru) avec le français en
 * secours (la console le lit en français). Paramètres : %s seulement, nombres déjà formatés.
 */
final class T {
    private T() {
    }

    static MutableComponent t(String cle, String fr, Object... args) {
        return Component.translatableWithFallback(cle, fr, args);
    }

    static void chat(ServerPlayer p, MutableComponent c, ChatFormatting couleur) {
        p.sendSystemMessage(c.withStyle(couleur));
    }

    static void barre(ServerPlayer p, MutableComponent c, ChatFormatting couleur) {
        p.displayClientMessage(c.withStyle(couleur), true);
    }

    /** Grand titre et sous-titre au milieu de l'écran ({@code ticks} affichés, fondus de 10 et 20 ticks). */
    static void titre(ServerPlayer p, Component titre, Component sousTitre, int ticks) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(10, ticks, 20));
        p.connection.send(new ClientboundSetSubtitleTextPacket(sousTitre));
        p.connection.send(new ClientboundSetTitleTextPacket(titre));
    }

    /** Durée lisible : « 3 j 12 h », « 5 h 20 min », « 42 min », « 15 s ». */
    static MutableComponent duree(long ms) {
        long s = Math.max(0L, ms / 1000L);
        long j = s / 86_400L;
        long h = s % 86_400L / 3600L;
        long m = s % 3600L / 60L;
        if (j > 0) {
            return t("crac.duree.jh", "%s j %s h", String.valueOf(j), String.valueOf(h));
        }
        if (h > 0) {
            return t("crac.duree.hm", "%s h %s min", String.valueOf(h), String.valueOf(m));
        }
        if (m > 0) {
            return t("crac.duree.m", "%s min", String.valueOf(m));
        }
        return t("crac.duree.s", "%s s", String.valueOf(s));
    }
}
