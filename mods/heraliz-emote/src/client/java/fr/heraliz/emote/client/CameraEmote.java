package fr.heraliz.emote.client;

import fr.heraliz.emote.HeralizEmote;
import io.github.kosmx.emotes.executor.EmoteInstance;
import io.github.kosmx.emotes.executor.emotePlayer.IEmotePlayerEntity;
import io.github.kosmx.emotes.main.config.ClientConfig;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Vue pendant une emote : le joueur passe à la 3e personne (de dos) pour se voir danser, F5 change la vue tant que
 * l'emote dure, puis il revient à la 1re personne en survie. HeraliZ Interface (1re personne forcée en survie) lit
 * {@link #actif()} par réflexion pour laisser F5 marcher : ne pas renommer. Les armes, elles, restent hors des mains
 * pendant l'emote et 10 s après (décidé par le serveur, {@code serveur.SansArme}).
 * <p>Deux mécanismes : l'option « Play Emotes in 3rd Person » d'Emotecraft, mise d'office (il passe à la 3e personne au
 * début et remet la 1re à la fin) ; et, en secours, chaque tick de l'emote, la vue remise à la 3e personne si elle est en
 * 1re, tant que le joueur n'a pas changé de vue lui-même (F5) pendant cette emote.
 */
public final class CameraEmote {
    private static boolean enEmote;
    /** La vue a été changée par nous pendant cette emote (à remettre en 1re personne à la fin). */
    private static boolean forcee;
    /** Le joueur a changé de vue lui-même pendant cette emote : on ne force plus. */
    private static boolean choixJoueur;
    private static CameraType derniere;
    private static boolean optionPosee;

    private CameraEmote() {
    }

    /** Vrai tant que le joueur local joue une emote (lu par HeraliZ Interface). */
    public static boolean actif() {
        return enEmote;
    }

    /** Fin de chaque tick client. */
    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        optionEmotecraft();
        boolean joue = p instanceof IEmotePlayerEntity<?> h && h.isPlayingEmote();
        CameraType vue = mc.options.getCameraType();
        if (joue) {
            if (!enEmote) {
                enEmote = true;
                HeralizEmote.LOGGER.info("[emote] début d'emote (vue : {})", vue);
                forcee = false;
                choixJoueur = false;
            } else if (derniere != null && vue != derniere) {
                choixJoueur = true;
            }
            if (!choixJoueur && vue == CameraType.FIRST_PERSON) {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                vue = CameraType.THIRD_PERSON_BACK;
                if (!forcee) HeralizEmote.LOGGER.info("[emote] emote en cours : vue passée à la 3e personne");
                forcee = true;
            }
            derniere = vue;
        } else if (enEmote) {
            enEmote = false;
            derniere = null;
            if (p != null && !p.isCreative() && !p.isSpectator() && (forcee || choixJoueur)) {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
            }
            forcee = choixJoueur = false;
        }
    }

    /** Option « Play Emotes in 3rd Person » d'Emotecraft : toujours mise (sa config peut être lue après nous). */
    private static void optionEmotecraft() {
        try {
            if (EmoteInstance.config instanceof ClientConfig c && !Boolean.TRUE.equals(c.enablePerspective.get())) {
                c.enablePerspective.set(true);
                if (!optionPosee) HeralizEmote.LOGGER.info("[emote] option 3e personne d'Emotecraft activée");
                optionPosee = true;
            }
        } catch (RuntimeException | LinkageError e) {
            // Emotecraft d'une autre version : le secours du tick suffit
        }
    }
}
