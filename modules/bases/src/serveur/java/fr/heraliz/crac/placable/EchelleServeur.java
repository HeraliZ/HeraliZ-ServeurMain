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
 * TaCZ). À 0 : l'échelle part, avec le bruit et les éclats du bois, sans rien lâcher ; celle qu'elle portait au-dessus
 * tombe avec elle (EchelleBlock.canSurvive) et le mur reprend sa case. N'importe qui peut la casser (le défenseur
 * comme le pilleur). Rien par tick : seulement à un coup ou à une balle qui touche une échelle.
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

    /** Retire des PV à l'échelle ; à 0, elle part sans rien lâcher. */
    static void abimer(ServerLevel level, BlockPos pos, float degats) {
        if (!(level.getBlockEntity(pos) instanceof EchelleBlockEntity be)) {
            return;
        }
        float reste = be.abimer(degats);
        Vec3 c = Vec3.atCenterOf(pos);
        BlockParticleOption bois = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_PLANKS.defaultBlockState());
        if (reste <= 0F) {
            level.sendParticles(bois, c.x, c.y, c.z, 30, 0.3, 0.4, 0.3, 0.12);
            level.playSound(null, pos, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 1.0F, 0.8F);
            level.removeBlock(pos, false);
        } else {
            level.sendParticles(bois, c.x, c.y, c.z, 5, 0.25, 0.3, 0.25, 0.05);
            level.playSound(null, pos, SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 0.8F, 0.8F + level.getRandom().nextFloat() * 0.3F);
        }
    }
}
