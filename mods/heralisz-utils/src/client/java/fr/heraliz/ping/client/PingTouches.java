package fr.heraliz.ping.client;

import com.mojang.blaze3d.platform.InputConstants;
import fr.heraliz.ping.client.hud.Roue;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * La touche des pings, par defaut le bouton lateral avant de la souris (souris 5) : celui de Ping Wheel, libre sur
 * HeraliZ (clic molette = choisir le bloc et coup de crosse TaCZ ; souris 4 = camera libre des vehicules). Appui bref
 * (moins de 180 ms) = ping rapide sur la cible (type choisi par le serveur ; sur un de ses propres pings = le retirer) ;
 * maintien = roue des types, relacher pour valider. Changeable dans Options > Commandes > HeraliZ Ping (clavier ou
 * souris). Une seconde touche, sans touche par defaut, ouvre les reglages.
 */
public final class PingTouches {
    private PingTouches() {
    }

    public static final String CATEGORIE = "key.categories.heraliz_ping";
    public static final KeyMapping PING = new KeyMapping("key.heraliz_ping.ping", KeyConflictContext.IN_GAME, InputConstants.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_5, CATEGORIE);
    public static final KeyMapping REGLAGES = new KeyMapping("key.heraliz_ping.reglages", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORIE);

    /** Seuil entre appui bref et maintien (ms). */
    public static final long SEUIL_MS = 180;
    private static long appuyeDepuis = -1;
    /** Banc d'essai : la fenetre du client de dev n'a pas forcement la main, on n'en tient pas compte. */
    public static boolean banc;

    /** Fin de tick client. */
    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            appuyeDepuis = -1;
            Roue.fermer();
            vider();
            return;
        }
        while (REGLAGES.consumeClick()) {
            if (mc.screen == null) mc.setScreen(new EcranReglages(null));
        }
        if (mc.screen != null || !(mc.isWindowActive() || banc)) {
            // un ecran s'ouvre (Echap, inventaire) ou la fenetre perd la main : la roue se ferme sans pinguer
            if (Roue.ouverte()) Roue.fermer();
            appuyeDepuis = -1;
            vider();
            return;
        }
        boolean bas = PING.isDown();
        long maintenant = Util.getMillis();
        if (bas) {
            vider();
            if (appuyeDepuis < 0) appuyeDepuis = maintenant;
            else if (!Roue.ouverte() && maintenant - appuyeDepuis >= SEUIL_MS) Roue.ouvrir();
        } else if (appuyeDepuis >= 0) {
            long duree = maintenant - appuyeDepuis;
            appuyeDepuis = -1;
            vider();
            if (Roue.ouverte()) valider();
            else if (duree < SEUIL_MS) PingClient.pingRapide();
        } else if (PING.consumeClick()) {
            // appui-relachement entier entre deux ticks
            vider();
            PingClient.pingRapide();
        }
    }

    /** Relacher (ou clic gauche) : le type survole est envoye ; au centre, rien. */
    public static void valider() {
        int choix = Roue.choix();
        Roue.fermer();
        appuyeDepuis = -1;
        if (choix >= 0) PingClient.ping(choix);
    }

    /** Clic droit : la roue se ferme sans ping. */
    public static void annuler() {
        Roue.fermer();
        appuyeDepuis = -1;
    }

    private static void vider() {
        while (PING.consumeClick()) {
            // rien : on consomme
        }
    }

    /** Vrai si le bouton de souris est celui de la touche des pings. */
    public static boolean estBoutonPing(int bouton) {
        InputConstants.Key k = PING.getKey();
        return k.getType() == InputConstants.Type.MOUSE && k.getValue() == bouton;
    }
}
