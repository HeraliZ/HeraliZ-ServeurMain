package fr.heraliz.emote.client;

import fr.heraliz.emote.reseau.ReseauEmotes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Les emotes débloquées du joueur, envoyées par le serveur ({@link ReseauEmotes.Deblocages}). Sans message du serveur
 * (serveur sans HeraliZ Emote, solo), rien n'est verrouillé ; le serveur refuse de toute façon une emote verrouillée.
 */
public final class DeblocagesClient {
    private static volatile boolean connu;
    private static volatile boolean tout;
    private static volatile Set<UUID> emotes = Set.of();

    private DeblocagesClient() {
    }

    static void recevoir(ReseauEmotes.Deblocages m) {
        tout = m.tout();
        emotes = Set.copyOf(new HashSet<>(m.emotes()));
        connu = true;
    }

    /** Fin de partie : le prochain serveur renverra les siens. */
    static void oublier() {
        connu = false;
        tout = false;
        emotes = Set.of();
    }

    public static boolean debloquee(UUID emote) {
        if (!connu || tout) return true;
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null && (p.isCreative() || p.isSpectator())) return true;
        return emotes.contains(emote);
    }
}
