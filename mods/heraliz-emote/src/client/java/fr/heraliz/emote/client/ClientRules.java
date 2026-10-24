package fr.heraliz.emote.client;

import fr.heraliz.emote.EmoteRules;
import io.github.kosmx.emotes.executor.emotePlayer.IEmotePlayerEntity;
import io.github.kosmx.emotes.main.network.ClientEmotePlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * {@link EmoteRules} on the client, with what the client knows (works even when the server does not have this mod):
 * <ul>
 *     <li>downed: Reanimation syncs the downed state to the clients ({@code Downed.isDowned});</li>
 *     <li>combat-tagged: the "EN COMBAT 12 s" countdown of HeraliZ Utils
 *     ({@code fr.heralisz.utils.combat.client.CombatClient.combatRestantMs()}, by reflection; HeraliZ Matchs, which had
 *     it before, was removed from the server on 24/10);</li>
 *     <li>not unlocked (emote crate): {@link DeblocagesClient}, sent by the server.</li>
 * </ul>
 * Every local emote start goes through {@code mixin.ClientEmotePlayMixin}; {@link #tick()} stops an emote that is
 * already playing when one of these becomes true (shot while dancing...).
 */
public final class ClientRules {
    private ClientRules() {
    }

    private static boolean looked;
    @Nullable
    private static Method combatMs;

    /** Why the local player may not play an emote right now, or null if they may. */
    @Nullable
    public static Component refusal() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || p.isCreative() || p.isSpectator()) return null;
        if (EmoteRules.isDowned(p)) return EmoteRules.downed();
        look();
        if (call(combatMs) instanceof Long left && left > 0) return EmoteRules.combat(left);
        return null;
    }

    /** Same, for this emote: also refused while it is not unlocked. */
    @Nullable
    public static Component refusal(@Nullable UUID emote) {
        Component why = refusal();
        if (why == null && emote != null && !DeblocagesClient.debloquee(emote)) return EmoteRules.verrouillee();
        return why;
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
            combatMs = Class.forName("fr.heralisz.utils.combat.client.CombatClient").getMethod("combatRestantMs");
        } catch (ReflectiveOperationException | LinkageError e) {
            combatMs = null;
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
