package fr.heraliz.crac.heraliz;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import fr.heraliz.crac.Config;
import fr.heraliz.crac.bases.BasesReglages;
import fr.heraliz.crac.bases.MondeBases;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * HeraliZ server rules CRac follows (section {@code [heraliz]} of crac-server.toml):
 * <ul>
 *   <li>dimensions where CRac is off: the match maps of HeraliZ Matchs and the clan bunkers of HeraliZ Bases (no piece,
 *   Claim Block or sleeping bag is placed there, and CRac leaves their explosions alone);</li>
 *   <li>the server's combat tag: {@code persistentData} long {@value #COMBAT_KEY} (epoch ms, set by HeraliZ Matchs), read
 *   without depending on that mod. A tagged player does not build.</li>
 * </ul>
 */
public final class Heraliz {
    /** Combat tag of HeraliZ Matchs: end of the tag in epoch ms, absent or 0 = free. */
    public static final String COMBAT_KEY = "heralisz:combat_jusqua";

    private static List<? extends String> cachedList;
    private static Set<ResourceLocation> cachedSet = Set.of();
    private static List<? extends String> cachedProtectedList;
    private static Set<ResourceLocation> cachedProtectedSet = Set.of();

    private Heraliz() {
    }

    /** Whether CRac is off in this level's dimension. False while the server config is not loaded. */
    public static boolean blocked(@Nullable Level level) {
        if (level == null || !Config.SPEC.isLoaded()) {
            return false;
        }
        return blockedSet().contains(level.dimension().location());
    }

    private static synchronized Set<ResourceLocation> blockedSet() {
        List<? extends String> list = Config.blockedDimensions.get();
        if (list != cachedList) {
            cachedSet = toSet(list);
            cachedList = list;
        }
        return cachedSet;
    }

    /** Whether no explosion may break a block in this level's dimension ({@code heraliz.protectedDimensions}). */
    public static boolean protectedMap(@Nullable Level level) {
        if (level == null || !Config.SPEC.isLoaded()) {
            return false;
        }
        return protectedSet().contains(level.dimension().location());
    }

    private static synchronized Set<ResourceLocation> protectedSet() {
        List<? extends String> list = Config.protectedDimensions.get();
        if (list != cachedProtectedList) {
            cachedProtectedSet = toSet(list);
            cachedProtectedList = list;
        }
        return cachedProtectedSet;
    }

    private static Set<ResourceLocation> toSet(List<? extends String> list) {
        Set<ResourceLocation> set = new HashSet<>();
        for (String s : list) {
            ResourceLocation rl = ResourceLocation.tryParse(s.trim());
            if (rl != null) {
                set.add(rl);
            }
        }
        return Set.copyOf(set);
    }

    /** Refusal shown when someone tries to build in a dimension where CRac is off. */
    public static net.minecraft.network.chat.MutableComponent blockedMessage() {
        return Component.translatable("crac.heraliz.dimension_blocked");
    }

    /** Seconds left of the player's combat tag (rounded up), 0 when free. */
    public static int combatSecondsLeft(ServerPlayer player) {
        long until = player.getPersistentData().getLong(COMBAT_KEY);
        long left = until - System.currentTimeMillis();
        return left <= 0 ? 0 : (int) ((left + 999) / 1000);
    }

    /**
     * Why a combat-tagged player may not build right now ({@code combatBlocksBuilding}), or null. Creative players
     * are never stopped.
     */
    @Nullable
    public static Component combatProblem(ServerPlayer player) {
        if (player.getAbilities().instabuild || !Config.combatBlocksBuilding.get()) {
            return null;
        }
        int s = combatSecondsLeft(player);
        return s <= 0 ? null : Component.translatable("crac.heraliz.combat", s);
    }

    /** Set on the GameTest server only (build/test/GameTestRules): the tests were written for CRac's original rules. */
    public static volatile boolean gameTestOriginalRules;

    /**
     * Whether CRac's original rules apply outside claims ({@code heraliz.cracRulesOutsideClaims}): no world block broken
     * by blasts there, placables with HP and adventure pickup everywhere. Off on HeraliZ.
     */
    public static boolean cracRulesOutsideClaims() {
        return gameTestOriginalRules || Config.SPEC.isLoaded() && Config.cracRulesOutsideClaims.get();
    }

    /**
     * Whether only C4 damage pieces and placables ({@code heraliz.onlyC4Damages}, on for HeraliZ): the HeraliZ C4, the
     * CRac C4 and Explosive Charge. The GameTests keep CRac's original rules.
     */
    public static boolean onlyC4() {
        return !gameTestOriginalRules && (!Config.SPEC.isLoaded() || Config.onlyC4Damages.get());
    }

    /** Whether hostile mobs chew on pieces ({@code zombies.attackBuilds}, off for HeraliZ, and never with {@link #onlyC4}). */
    public static boolean mobsDamagePieces() {
        return gameTestOriginalRules || Config.zombiesAttackBuilds.get() && !onlyC4();
    }

    /** Whether a base whose group stays away decays even with its upkeep paid ({@code decay.inactivity}, off for HeraliZ). */
    public static boolean inactivityDecay() {
        return gameTestOriginalRules || Config.decayInactivity.get();
    }

    /** The first refusal among the dimension, the bases world rule and the combat tag, or null. */
    @Nullable
    public static Component buildProblem(ServerPlayer player) {
        if (blocked(player.level())) {
            return blockedMessage();
        }
        Component hors = horsMondeBases(player);
        if (hors != null) {
            return hors;
        }
        return combatProblem(player);
    }

    /**
     * Monde des bases (1.5.0, {@code [bases] construction_ici_seulement} de crac-bases.toml) : en survie, les pièces et le
     * Claim Block ne se posent que dans le monde des bases (la carte principale depuis 1.16.0, pas le monde du spawn). Null quand c'est permis (créatif, réglage coupé, GameTests).
     */
    @Nullable
    public static Component horsMondeBases(@Nullable net.minecraft.world.entity.player.Player player) {
        if (gameTestOriginalRules || player == null || player.getAbilities().instabuild
            || !BasesReglages.b(BasesReglages.CONSTRUCTION_ICI_SEULEMENT, false) || MondeBases.estBases(player.level())
            || MondeBases.estTuto(player.level())) {
            return null;
        }
        return Component.translatableWithFallback("crac.bases.construire_ailleurs",
            "On ne construit que sur la carte, pas au spawn.");
    }
}
