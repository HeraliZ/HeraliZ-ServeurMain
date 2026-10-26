package fr.heraliz.crac.event;

import java.lang.reflect.Field;
import fr.heraliz.crac.Config;
import fr.heraliz.crac.Crac;
import fr.heraliz.crac.claim.ClaimManager;
import fr.heraliz.crac.heraliz.Heraliz;
import fr.heraliz.crac.heraliz.HeralizC4;
import fr.heraliz.crac.raid.BlastDamage;
import fr.heraliz.crac.raid.RaidBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.level.ExplosionEvent.Detonate;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Vanilla explosions (TNT, creepers, crystals, beds...) and those of other mods (HeraliZ Tactical grenades). The
 * building pieces and placables a blast can see in range take damage scaled by its power, once each; nothing behind a
 * wall is hit. World blocks: inside claims only what a raid may break, for blasts no mob caused, never natural terrain
 * (see {@link RaidBlocks}); outside claims the blast keeps its block list, minus CRac's own blocks.
 * <p>HeraliZ: the C4 of the server ({@code heralisz_grenade:c4}, {@link HeralizC4}) is a CRac C4: the piece it is stuck
 * to takes {@code c4.damage} (Rust: 1 for wood, 2 stone, 4 sheet metal, 7 armored) and, stuck to a plain block in a
 * claim, it breaches like the CRac C4 instead of carving its 7.5 block crater. Nothing is done in the dimensions where
 * CRac is off ({@code heraliz.blockedDimensions}: matches, clan bunkers), which keep their own explosion rules.
 */
@EventBusSubscriber(
    modid = "crac"
)
public final class GenericExplosionHandler {
    /** Power of a TNT explosion: genericDamage and genericRadius are given for it. */
    static final float TNT_POWER = 4.0F;
    /** Blasts weaker or stronger than this many TNT are scaled as if they were this strong. */
    private static final float MIN_SCALE = 0.25F;
    private static final float MAX_SCALE = 2.0F;

    private GenericExplosionHandler() {
    }

    @SubscribeEvent
    public static void onDetonate(Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level) || Heraliz.blocked(level)) {
            return;
        }
        Explosion explosion = event.getExplosion();
        // 26/10 (règle du propriétaire) : une frappe aérienne ne fait AUCUN dégât aux bases, quel que soit le réglage
        // onlyC4Damages ; HeraliZ Airstrike vide déjà sa liste de blocs, ceci protège aussi les pièces et objets posés
        if (explosion.getDamageSource().typeHolder().unwrapKey().map(k -> k.location().toString().equals("airstrike:bomb")).orElse(false)) {
            RaidBlocks.keepBreakable(level, event.getAffectedBlocks(), false);
            return;
        }
        Vec3 origin = explosion.getPosition();
        boolean byMob = explosion.getIndirectSourceEntity() instanceof Mob;
        Entity c4 = HeralizC4.c4Of(explosion);
        if (c4 != null) {
            heralizC4(level, event, c4, origin);
            return;
        }
        if (Heraliz.onlyC4()) {
            // HeraliZ: grenades, TNT, creepers... do nothing to pieces or placables, and break nothing inside claims
            RaidBlocks.keepBreakable(level, event.getAffectedBlocks(), false);
            return;
        }
        float power = power(explosion);
        int dmg = scaled(Config.genericExplosionDamage.get(), power);
        if (dmg > 0) {
            // pieces first: the sight lines are traced while every block still stands
            int radius = scaled(Config.genericExplosionRadius.get(), power);
            // a creeper or a ghast fireball is mob damage: it hurts the pieces but does not raid block their owners
            boolean raid = Config.raidBlockMobExplosions.get() || !byMob;
            BlastDamage.splash(level, origin, BlastDamage.sphere(BlockPos.containing(origin), radius), dmg, Config.genericExplosionMaxTargets.get(), raid);
        }
        RaidBlocks.keepBreakable(level, event.getAffectedBlocks(), !byMob);
    }

    /**
     * The HeraliZ C4 goes off: the piece (or placable) it is stuck to takes {@code c4.damage}; stuck to a plain block of
     * a claim it breaks what the CRac C4 would ({@code c4.blockRadius}). Inside claims its own block list is dropped;
     * outside them it is left to the other mods. A C4 stuck to nothing CRac knows (an entity, the ground beside a wall)
     * counts as a generic blast of its power.
     */
    private static void heralizC4(ServerLevel level, Detonate event, Entity c4, Vec3 origin) {
        BlockPos stuck = HeralizC4.stuckBlock(c4);
        fr.heraliz.crac.heraliz.Purge.prevenirExplosifInutile(level, stuck != null ? stuck : BlockPos.containing(origin),
            event.getExplosion().getIndirectSourceEntity());
        boolean hit = stuck != null && BlastDamage.stuck(level, stuck, origin, Config.c4Damage.get());
        if (!hit) {
            float power = power(event.getExplosion());
            int dmg = scaled(Config.genericExplosionDamage.get(), power);
            if (dmg > 0) {
                int radius = scaled(Config.genericExplosionRadius.get(), power);
                BlastDamage.splash(level, origin, BlastDamage.sphere(BlockPos.containing(origin), radius), dmg, Config.genericExplosionMaxTargets.get(), true);
            }
        }
        event.getAffectedBlocks().removeIf(p -> ClaimManager.getAt(level, p) != null);
        RaidBlocks.keepBreakable(level, event.getAffectedBlocks(), true);
        if (!hit && stuck != null && ClaimManager.getAt(level, stuck) != null) {
            RaidBlocks.breakAround(level, stuck, Config.c4BlockRadius.get());
        }
        Crac.LOGGER.debug("[CRac] HeraliZ C4 at {} stuck to {}: {}", BlockPos.containing(origin), stuck, hit ? "piece hit" : "no piece");
    }

    /** A damage or a radius given for a TNT blast, for a blast of {@code power}; 0 stays 0, anything else is at least 1. */
    static int scaled(int tntValue, float power) {
        if (tntValue <= 0) {
            return 0;
        }
        float scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, power / TNT_POWER));
        return Math.max(1, Math.round(tntValue * scale));
    }

    /** Explosion power (TNT 4, creeper 3, bed 5, end crystal 6...); TNT's when it cannot be read. */
    static float power(Explosion explosion) {
        Field radius = RadiusField.FIELD;
        if (radius != null) {
            try {
                return radius.getFloat(explosion);
            } catch (IllegalAccessException | IllegalArgumentException e) {
                return TNT_POWER;
            }
        }
        return TNT_POWER;
    }

    /** {@code Explosion.radius}, private and without a getter in 1.20.1. */
    private static final class RadiusField {
        @Nullable
        static final Field FIELD = find();

        @Nullable
        private static Field find() {
            try {
                return ObfuscationReflectionHelper.findField(Explosion.class, "f_46017_");
            } catch (RuntimeException e) {
                Crac.LOGGER.warn("Cannot read the explosion power, every generic blast counts as TNT", e);
                return null;
            }
        }
    }
}
