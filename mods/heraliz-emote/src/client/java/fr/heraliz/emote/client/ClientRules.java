package fr.heraliz.emote.client;

import fr.heraliz.emote.EmoteRules;
import io.github.kosmx.emotes.executor.emotePlayer.IEmotePlayerEntity;
import io.github.kosmx.emotes.main.network.ClientEmotePlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.lang.reflect.Method;

/**
 * {@link EmoteRules} on the client, with what the client knows (works even when the server does not have this mod):
 * <ul>
 *     <li>downed: Reanimation syncs the downed state to the clients ({@code Downed.isDowned});</li>
 *     <li>in a match: HeraliZ Matchs tells the client ({@code moteur.client.MoteurClient.enMatch()}); zombies games
 *     (their HUD is on, {@code zombies.jeu.api.HudZombies.actif()}) are allowed;</li>
 *     <li>combat-tagged: the "EN COMBAT 12 s" countdown of HeraliZ Matchs ({@code MoteurClient.combatRestantMs()}).</li>
 * </ul>
 * Every local emote start goes through {@code mixin.ClientEmotePlayMixin}; {@link #tick()} stops an emote that is
 * already playing when one of these becomes true (shot while dancing, pulled into a duel...).
 */
public final class ClientRules {
    private ClientRules() {
    }

    private static boolean looked;
    @Nullable
    private static Method enMatch, combatMs, zombiesHud;

    /** Why the local player may not play an emote right now, or null if they may. */
    @Nullable
    public static Component refusal() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || p.isCreative() || p.isSpectator()) return null;
        if (EmoteRules.isDowned(p)) return EmoteRules.downed();
        look();
        if (call(enMatch) instanceof Boolean en && en && !(call(zombiesHud) instanceof Boolean z && z)) return EmoteRules.match();
        if (call(combatMs) instanceof Long left && left > 0) return EmoteRules.combat(left);
        return null;
    }

    /** Shows the refusal above the hotbar (in the chat while downed: Reanimation hides the action bar then). */
    public static void tell(Component why) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null) p.displayClientMessage(why, !EmoteRules.isDowned(p));
    }

    /** End of each client tick: an emote that is playing stops as soon as it is no longer allowed. */
    static void tick() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (!(p instanceof IEmotePlayerEntity<?> holder) || !holder.isPlayingEmote() || holder.isForcedEmote()) return;
        Component why = refusal();
        if (why != null && ClientEmotePlay.clientStopLocalEmote()) tell(why);
    }

    private static void look() {
        if (looked) return;
        looked = true;
        try {
            Class<?> c = Class.forName("fr.heralisz.matchs.moteur.client.MoteurClient");
            enMatch = c.getMethod("enMatch");
            combatMs = c.getMethod("combatRestantMs");
        } catch (ReflectiveOperationException | LinkageError e) {
            enMatch = combatMs = null;
        }
        try {
            zombiesHud = Class.forName("fr.heralisz.matchs.zombies.jeu.api.HudZombies").getMethod("actif");
        } catch (ReflectiveOperationException | LinkageError e) {
            zombiesHud = null;
        }
    }

    @Nullable
    private static Object call(@Nullable Method m) {
        if (m == null) return null;
        try {
            return m.invoke(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
