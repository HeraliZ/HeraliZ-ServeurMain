package fr.heraliz.crac.placable;

import fr.heraliz.crac.Crac;
import fr.heraliz.crac.block.EchelleBlock;
import fr.heraliz.crac.block.entity.EchelleBlockEntity;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Casse de l'échelle de raid (03/11), comme la barricade de Bunker (mêmes nombres, réécrits ici : le code de Bunker est
 * sous une autre licence) : 150 PV ; un coup à la main ou d'arme de mêlée en retire au moins 15 (plus si l'arme frappe plus
 * fort), selon la recharge du coup (un coup donné trop tôt compte moins, comme contre une créature) ; une balle de TaCZ
 * en retire ses propres dégâts au point touché ({@code AmmoHitBlockEvent}, lu par son nom : CRac ne dépend pas de
 * TaCZ). Un coup sur n'importe quelle partie compte pour toute l'échelle (PV gardés par la partie du bas). À 0 : l'échelle
 * part (toute), avec le bruit et les éclats du bois, sans rien lâcher, et le mur reprend ses cases. Le C4 et la charge
 * explosive la détruisent d'un coup ({@link #souffle}) ; les autres explosions ne lui font rien. N'importe qui peut la
 * casser (le défenseur comme le pilleur). Rien par tick : seulement à un coup, une balle ou un explosif.
 */
@EventBusSubscriber(modid = "crac")
public final class EchelleServeur {
    /** Dégâts au moins, par coup à la main ou de mêlée (barricade : environ 10 coups). */
    public static final float COUP_MIN = 15.0F;
    /** Dernier coup compté par joueur (ticks du jeu) : un clic par coup, pas plus de 4 par seconde. */
    private static final Map<UUID, Long> DERNIER = new HashMap<>();
    private static final int ECART_MIN = 5;
    private static Method degatsBalle;

    private EchelleServeur() {
    }

    /** Branché par PartieServeur : balles de TaCZ (si TaCZ est là). */
    public static void brancher() {
        try {
            @SuppressWarnings("unchecked")
            Class<Event> c = (Class<Event>) Class.forName("com.tacz.guns.api.event.server.AmmoHitBlockEvent");
            Method level = c.getMethod("getLevel");
            Method hit = c.getMethod("getHitResult");
            Method ammo = c.getMethod("getAmmo");
            MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, c, e -> balle(e, level, hit, ammo));
        } catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
            Crac.LOGGER.info("[CRac] échelles : TaCZ absent, elles ne se cassent qu'à la main ({})", e.toString());
        }
    }

    private static void balle(Event e, Method mLevel, Method mHit, Method mAmmo) {
        try {
            if (!(mLevel.invoke(e) instanceof ServerLevel level) || !(mHit.invoke(e) instanceof BlockHitResult hit)) {
                return;
            }
            BlockPos pos = hit.getBlockPos();
            if (!(level.getBlockState(pos).getBlock() instanceof EchelleBlock)) {
                return;
            }
            Object balle = mAmmo.invoke(e);
            float degats = 6.0F;   // sans les dégâts de la balle : une balle moyenne
            if (balle != null) {
                if (degatsBalle == null) {
                    degatsBalle = balle.getClass().getMethod("getDamage", Vec3.class);
                }
                if (degatsBalle.invoke(balle, hit.getLocation()) instanceof Float f) {
                    degats = f;
                }
            }
            abimer(level, pos, degats);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            Crac.LOGGER.debug("[CRac] échelle touchée par une balle : {}", ex.toString());
        }
    }

    /** Coup à la main (ou d'arme de mêlée) sur une échelle : des dégâts au lieu de la casse de Minecraft. */
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void coup(PlayerInteractEvent.LeftClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !(p.level() instanceof ServerLevel level)) {
            return;
        }
        BlockState s = level.getBlockState(e.getPos());
        if (!(s.getBlock() instanceof EchelleBlock)) {
            return;
        }
        if (p.isCreative()) {
            return;   // le staff en créatif la casse d'un clic, comme tout bloc
        }
        e.setCanceled(true);
        if (e.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START || p.isSpectator()) {
            return;
        }
        long t = level.getGameTime();
        Long avant = DERNIER.get(p.getUUID());
        if (avant != null && t - avant < ECART_MIN) {
            return;
        }
        DERNIER.put(p.getUUID(), t);
        float force = p.getAttackStrengthScale(0.5F);
        float degats = Math.max(COUP_MIN, (float) p.getAttributeValue(Attributes.ATTACK_DAMAGE)) * (0.2F + 0.8F * force * force);
        p.resetAttackStrengthTicker();
        abimer(level, e.getPos(), degats);
    }

    @SubscribeEvent
    public static void deconnexion(PlayerEvent.PlayerLoggedOutEvent e) {
        DERNIER.remove(e.getEntity().getUUID());
    }

    /** Retire des PV à l'échelle dont une partie est en {@code pos} (gardés par la partie du bas) ; à 0, elle part. */
    static void abimer(ServerLevel level, BlockPos pos, float degats) {
        BlockState s = level.getBlockState(pos);
        if (!(s.getBlock() instanceof EchelleBlock)) {
            return;
        }
        BlockPos bas = EchelleBlock.bas(s, pos);
        if (!(level.getBlockEntity(bas) instanceof EchelleBlockEntity be)) {
            return;
        }
        if (be.abimer(degats) <= 0F) {
            detruire(level, bas);
            return;
        }
        Vec3 c = Vec3.atCenterOf(pos);
        level.sendParticles(bois(), c.x, c.y, c.z, 5, 0.25, 0.3, 0.25, 0.05);
        level.playSound(null, pos, SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 0.8F, 0.8F + level.getRandom().nextFloat() * 0.3F);
    }

    /** Rayon (blocs, depuis le point d'explosion) dans lequel le C4 et la charge explosive détruisent les échelles. */
    public static final double RAYON_EXPLOSIF = 2.5;

    /**
     * C4 ou charge explosive (demande du propriétaire du 03/11) : chaque échelle dont une partie est à moins de
     * {@link #RAYON_EXPLOSIF} du point d'explosion et que le souffle voit (rien de plein entre les deux : une échelle de
     * l'autre côté du mur ne bouge pas, comme les pièces, {@code BlastDamage.inSight}) est détruite d'un coup, sans rien
     * lâcher. Appelé avant les dégâts aux pièces : l'échelle ne protège pas le mur derrière elle. Seulement à l'explosion
     * (quelques dizaines de cases lues).
     */
    public static void souffle(ServerLevel level, Vec3 origine) {
        int r = (int) Math.ceil(RAYON_EXPLOSIF);
        double r2 = RAYON_EXPLOSIF * RAYON_EXPLOSIF;
        BlockPos centre = BlockPos.containing(origine);
        java.util.Set<BlockPos> echelles = new java.util.LinkedHashSet<>();
        for (BlockPos p : BlockPos.betweenClosed(centre.offset(-r, -r, -r), centre.offset(r, r, r))) {
            if (!level.isLoaded(p) || Vec3.atCenterOf(p).distanceToSqr(origine) > r2) {
                continue;
            }
            BlockState s = level.getBlockState(p);
            if (!(s.getBlock() instanceof EchelleBlock)) {
                continue;
            }
            BlockPos bas = EchelleBlock.bas(s, p);
            if (!echelles.contains(bas) && fr.heraliz.crac.raid.BlastDamage.inSight(level, origine,
                new fr.heraliz.crac.raid.BlastDamage.Target(null, p.immutable(), 0.0), java.util.Set.of())) {
                echelles.add(bas);
            }
        }
        for (BlockPos bas : echelles) {
            detruire(level, bas);
        }
    }

    /** L'échelle (partie du bas en {@code bas}) part toute, avec le bruit et les éclats du bois, sans rien lâcher. */
    private static void detruire(ServerLevel level, BlockPos bas) {
        if (!(level.getBlockState(bas).getBlock() instanceof EchelleBlock)) {
            return;
        }
        Vec3 c = Vec3.atCenterOf(bas.above(EchelleBlock.HAUTEUR / 2));
        level.sendParticles(bois(), c.x, c.y, c.z, 60, 0.3, 1.2, 0.3, 0.12);
        level.playSound(null, bas.above(), SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 1.0F, 0.8F);
        level.removeBlock(bas, false);   // les autres parties suivent (EchelleBlock.onRemove)
    }

    private static BlockParticleOption bois() {
        return new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_PLANKS.defaultBlockState());
    }
}
