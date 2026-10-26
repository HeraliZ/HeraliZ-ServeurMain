package fr.heraliz.emote.client;

import fr.heraliz.emote.EmoteRules;
import fr.heraliz.emote.reseau.ReseauEmotes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Le délai sans tir après une emote, côté joueur : le serveur décide (son {@code SansArme}), le jeu du joueur suit pour
 * ne pas montrer un tir que le serveur refusera (« tir fantôme »). Il part de la fin de l'emote vue ici, et du temps
 * restant envoyé par le serveur ({@link ReseauEmotes.SansArme} : fin de l'emote vue par le serveur, reconnexion pendant
 * le délai) ; on garde le plus tard des deux. Créatif et spectateur non concernés. Thread du jeu.
 */
public final class SansArmeClient {
    private static long jusqua;
    private static long dernierMessage;

    private SansArmeClient() {
    }

    /** Fin de l'emote du joueur local ({@link CameraEmote}). */
    static void finEmote() {
        jusqua = Math.max(jusqua, System.currentTimeMillis() + EmoteRules.SANS_ARME_MS);
    }

    static void recevoir(ReseauEmotes.SansArme m) {
        jusqua = Math.max(jusqua, System.currentTimeMillis() + m.restantMs());
    }

    /** Fin de partie : le prochain serveur renverra le sien. */
    static void oublier() {
        jusqua = 0;
    }

    /** Temps restant sans tir (ms), 0 si le joueur local peut tirer. */
    static long restant() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || p.isCreative() || p.isSpectator()) return 0;
        if (CameraEmote.actif()) return EmoteRules.SANS_ARME_MS;
        return Math.max(0, jusqua - System.currentTimeMillis());
    }

    /** Le message « pas d'arme » au-dessus de la barre, au plus une fois par seconde. */
    static void prevenir(long restantMs) {
        LocalPlayer p = Minecraft.getInstance().player;
        long now = System.currentTimeMillis();
        if (p == null || now - dernierMessage <= 1000) return;
        dernierMessage = now;
        p.displayClientMessage(EmoteRules.sansArme(restantMs), true);
    }
}
