package fr.heraliz.emote.client;

import fr.heraliz.emote.HeralizEmote;
import io.github.kosmx.emotes.executor.emotePlayer.IEmotePlayerEntity;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.event.TickEvent;

/**
 * Vue pendant une emote : toujours à la 3e personne (de dos), le joueur se voit danser ; à la fin, retour à la
 * 1re personne en survie. Les armes restent hors des mains pendant l'emote et 10 s après (serveur, {@code SansArme}).
 * <p>Ce qui bloquait : geckolib222 (GeckoLib modifié avec le contenu ArkaniaZ, jar d'un autre auteur, pas touché) remet
 * la vue à la 1re personne à CHAQUE tick hors du créatif ({@code software.bernie.example.client.handler
 * .PlayerShaderHandler.onClientTick} et {@code BleedingEventHandler.onPlayerTick}). Les ticks passent tous avant le
 * dessin de l'image : la vue est donc remise à la 3e personne au début de chaque image ({@link #image}), après eux.
 * HeraliZ Interface lit {@link #actif()} par réflexion (F5 laissé libre pendant l'emote) : ne pas renommer.
 */
public final class CameraEmote {
    private static boolean enEmote;
    private static boolean journal;

    private CameraEmote() {
    }

    /** Vrai tant que le joueur local joue une emote (lu par HeraliZ Interface). */
    public static boolean actif() {
        return enEmote;
    }

    /** Fin de chaque tick client : début et fin de l'emote. */
    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        boolean joue = p instanceof IEmotePlayerEntity<?> h && h.isPlayingEmote();
        if (joue && !enEmote) {
            enEmote = true;
            journal = false;
        } else if (!joue && enEmote) {
            enEmote = false;
            if (p != null && !p.isCreative() && !p.isSpectator()) mc.options.setCameraType(CameraType.FIRST_PERSON);
        }
        if (enEmote) forcer(mc);
    }

    /** Début de chaque image : 3e personne pendant l'emote (après tout ce qui a remis la 1re pendant les ticks). */
    public static void image(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.START || !enEmote) return;
        forcer(Minecraft.getInstance());
    }

    private static void forcer(Minecraft mc) {
        if (mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK) return;
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        if (!journal) {
            journal = true;
            HeralizEmote.LOGGER.info("[emote] emote en cours : vue passée à la 3e personne");
        }
    }
}
