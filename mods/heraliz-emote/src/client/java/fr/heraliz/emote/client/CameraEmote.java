package fr.heraliz.emote.client;

import io.github.kosmx.emotes.executor.emotePlayer.IEmotePlayerEntity;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Vue pendant une emote : le joueur passe à la 3e personne (de dos) pour se voir danser, F5 change la vue tant que
 * l'emote dure, puis il revient à la 1re personne en survie. HeraliZ Interface (1re personne forcée en survie) lit
 * {@link #actif()} par réflexion pour laisser F5 marcher : ne pas renommer. Les armes, elles, restent hors des mains
 * pendant l'emote et 10 s après (décidé par le serveur, {@code serveur.SansArme}).
 */
public final class CameraEmote {
    private static boolean enEmote;
    private static boolean changee;

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
        boolean joue = p instanceof IEmotePlayerEntity<?> h && h.isPlayingEmote();
        if (joue && !enEmote) {
            enEmote = true;
            changee = mc.options.getCameraType() == CameraType.FIRST_PERSON;
            if (changee) mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        } else if (!joue && enEmote) {
            enEmote = false;
            if (changee && p != null && !p.isCreative() && !p.isSpectator()) mc.options.setCameraType(CameraType.FIRST_PERSON);
            changee = false;
        }
    }
}
