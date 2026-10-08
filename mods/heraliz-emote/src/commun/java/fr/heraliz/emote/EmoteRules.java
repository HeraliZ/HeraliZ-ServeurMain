package fr.heraliz.emote;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.lang.reflect.Method;

/**
 * When a player may not play an emote on the HeraliZ server. Soft checks only: every other mod is read by reflection or
 * through the player's {@code persistentData}, so nothing breaks when one of them is missing.
 * <ul>
 *     <li>downed (Reanimation, {@code fr.heraliz.reanimation.core.Downed.isDowned(Entity)}, works on both sides);</li>
 *     <li>in a match ({@code persistentData} flag {@code heralisz:en_match} of HeraliZ Matchs): refused in duels and
 *     ClashWar; the zombies mode (co-op against mobs) is allowed;</li>
 *     <li>combat-tagged ({@code persistentData} long {@code heralisz:combat_jusqua} = end of the tag, epoch ms).</li>
 * </ul>
 * This class holds the server side (used by {@code mixin.ServerEmotePlayMixin} when the mod is also on the server);
 * {@code client.ClientRules} does the same with what the client knows.
 */
public final class EmoteRules {
    private EmoteRules() {
    }

    public static final String EN_MATCH = "heralisz:en_match";
    public static final String COMBAT_JUSQUA = "heralisz:combat_jusqua";
    /** Mode id of HeraliZ Matchs' zombies games ({@code Partie.mode()}). */
    public static final String ZOMBIES = "zombies";

    private static boolean downedLooked;
    @Nullable
    private static Method isDowned;
    private static boolean matchLooked;
    @Nullable
    private static Method partieDe, mode;

    // ------------------------------------------------------------------ messages (shared with the client)
    public static Component downed() {
        return Component.translatableWithFallback("heraliz_emote.refused.downed", "Pas d'emote quand on est à terre");
    }

    public static Component match() {
        return Component.translatableWithFallback("heraliz_emote.refused.match", "Pas d'emote pendant un match");
    }

    public static Component combat(long remainingMs) {
        long s = Math.max(1, (remainingMs + 999) / 1000);
        return Component.translatableWithFallback("heraliz_emote.refused.combat", "Pas d'emote en combat (encore %s s)", s);
    }

    // ------------------------------------------------------------------ Reanimation
    /** True if Reanimation says this entity is downed (false without Reanimation). Client and server. */
    public static boolean isDowned(@Nullable Entity e) {
        if (e == null) return false;
        if (!downedLooked) {
            downedLooked = true;
            try {
                isDowned = Class.forName("fr.heraliz.reanimation.core.Downed").getMethod("isDowned", Entity.class);
            } catch (ReflectiveOperationException | LinkageError ex) {
                isDowned = null;
            }
        }
        if (isDowned == null) return false;
        try {
            return Boolean.TRUE.equals(isDowned.invoke(null, e));
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return false;
        }
    }

    // ------------------------------------------------------------------ server side
    /** Why this player may not play an emote right now, or null if they may. Server thread. */
    @Nullable
    public static Component refusal(ServerPlayer p) {
        if (p.isCreative() || p.isSpectator()) return null;
        if (isDowned(p)) return downed();
        CompoundTag data = p.getPersistentData();
        if (data.getBoolean(EN_MATCH) && !inZombies(p)) return match();
        long left = data.getLong(COMBAT_JUSQUA) - System.currentTimeMillis();
        if (left > 0) return combat(left);
        return null;
    }

    /** True if HeraliZ Matchs says the player's game is a zombies game ({@code MatchApi.partieDe(p).mode()}). */
    private static boolean inZombies(ServerPlayer p) {
        if (!matchLooked) {
            matchLooked = true;
            try {
                partieDe = Class.forName("fr.heralisz.matchs.moteur.api.MatchApi").getMethod("partieDe", ServerPlayer.class);
                mode = Class.forName("fr.heralisz.matchs.moteur.api.Partie").getMethod("mode");
            } catch (ReflectiveOperationException | LinkageError ex) {
                partieDe = mode = null;
            }
        }
        if (partieDe == null || mode == null) return false;
        try {
            Object partie = partieDe.invoke(null, p);
            return partie != null && ZOMBIES.equals(mode.invoke(partie));
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return false;
        }
    }
}
