package fr.heralisz.utils.spawn;

import fr.heralisz.utils.HeraliszUtils;
import fr.heralisz.utils.api.SpawnApi;
import fr.heralisz.utils.cine.CineReseauServeur;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.food.FoodData;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Protection du monde du spawn ({@code heralisz:spawn}), réglage {@code [protection] actif} de
 * {@code heraliz_spawn-server.toml}. Pour tout joueur qui s'y trouve :
 * <ul>
 * <li>aucun dégât, quelle que soit la source (attaque, feu, lave, chute, noyade, suffocation, faim, gel, cactus,
 * explosions, autres joueurs...) : évènements de dégâts annulés en priorité la plus haute ; seul {@code /kill}
 * (dégâts « generic_kill ») passe ;</li>
 * <li>chaque tick : feu éteint, distance de chute remise à zéro, gel retiré, air plein, faim pleine et saturation
 * gardée ; sous le bas du monde : retour au point de réveil par une vraie téléportation (suivie par Spartan) ;</li>
 * <li>aucun effet négatif : refusé à l'application, et ceux déjà là (venus de la carte) retirés ;</li>
 * </ul>
 * Dans le monde : aucune créature (toute entité {@link Mob} : monstres, animaux, PNJ ; apparitions naturelles, cages,
 * œufs, mods de zombies), sauf {@code entites_permises} ; celles déjà là sont retirées chaque seconde ; les explosions
 * ne cassent aucun bloc et ne touchent aucune entité. Ne touche à aucune règle de jeu (gamerule) : elles sont communes à
 * tous les mondes. Fil serveur seulement.
 */
public final class ProtectionSpawn {
    private ProtectionSpawn() {}

    public static void init() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ProtectionSpawn::attaque);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ProtectionSpawn::blessure);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ProtectionSpawn::degats);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ProtectionSpawn::effet);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ProtectionSpawn::arrivee);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ProtectionSpawn::explosion);
        MinecraftForge.EVENT_BUS.addListener(ProtectionSpawn::tick);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, ProtectionSpawn::ouvrants);
    }

    /** La protection est-elle active (réglage) ? */
    public static boolean active() {
        return DepartConfig.lire(DepartConfig.PROTEGER);
    }

    /** Ce joueur est-il protégé (protection active, dans le monde du spawn) ? */
    public static boolean protege(Entity e) {
        return e instanceof ServerPlayer sp && SpawnApi.estAuSpawn(sp.level()) && active();
    }

    /** Dégâts qui passent malgré tout : /kill (et ce qui l'imite). */
    private static boolean permis(DamageSource s) {
        return s.is(DamageTypes.GENERIC_KILL);
    }

    // ---------------------------------------------------------------------------------------------- dégâts
    private static void attaque(LivingAttackEvent e) {
        if (protege(e.getEntity()) && !permis(e.getSource())) {
            e.setCanceled(true);
        }
    }

    private static void blessure(LivingHurtEvent e) {
        if (protege(e.getEntity()) && !permis(e.getSource())) {
            e.setCanceled(true);
        }
    }

    private static void degats(LivingDamageEvent e) {
        if (protege(e.getEntity()) && !permis(e.getSource())) {
            e.setCanceled(true);
        }
    }

    // ---------------------------------------------------------------------------------------------- effets
    private static boolean negatif(MobEffectInstance i) {
        return i != null && i.getEffect().getCategory() == MobEffectCategory.HARMFUL;
    }

    private static void effet(MobEffectEvent.Applicable e) {
        if (protege(e.getEntity()) && negatif(e.getEffectInstance())) {
            e.setResult(Event.Result.DENY);
        }
    }

    private static void retirerEffetsNegatifs(ServerPlayer sp) {
        List<MobEffect> negatifs = null;
        for (MobEffectInstance i : sp.getActiveEffects()) {
            if (negatif(i)) {
                if (negatifs == null) {
                    negatifs = new ArrayList<>();
                }
                negatifs.add(i.getEffect());
            }
        }
        if (negatifs != null) {
            negatifs.forEach(sp::removeEffect);
        }
    }

    // ---------------------------------------------------------------------------------------------- créatures
    /**
     * Personnages posés par le staff au spawn, toujours gardés (en plus de {@code entites_permises}) : vigiles du casino
     * et PNJ des quêtes. Sans eux, ils étaient retirés dans la seconde (et refusés à la pose).
     */
    private static final java.util.Set<String> TOUJOURS_PERMISES = java.util.Set.of("heralizcasino:videur", "heraliz_quetes:pnj");

    private static boolean creatureInterdite(Entity e) {
        if (!(e instanceof Mob)) {
            return false;
        }
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        if (id != null && TOUJOURS_PERMISES.contains(id.toString())) {
            return false;
        }
        List<? extends String> permises = DepartConfig.lire(DepartConfig.ENTITES_PERMISES);
        return id == null || !permises.contains(id.toString());
    }

    /** Aucune créature n'entre dans le monde du spawn (apparition, cage, œuf, mod, chargement d'un tronçon). */
    private static void arrivee(EntityJoinLevelEvent e) {
        if (!e.getLevel().isClientSide() && SpawnApi.estAuSpawn(e.getLevel()) && active() && creatureInterdite(e.getEntity())) {
            e.setCanceled(true);
        }
    }

    private static void retirerCreatures(ServerLevel l) {
        List<Entity> a = null;
        for (Entity e : l.getAllEntities()) {
            if (e != null && e.isAlive() && creatureInterdite(e)) {
                if (a == null) {
                    a = new ArrayList<>();
                }
                a.add(e);
            }
        }
        if (a != null) {
            for (Entity e : a) {
                e.discard();
            }
            HeraliszUtils.LOG.info("[spawn] {} créature(s) retirée(s) du monde du spawn", a.size());
        }
    }

    // ---------------------------------------------------------------------------------------------- explosions
    private static void explosion(ExplosionEvent.Detonate e) {
        if (!e.getLevel().isClientSide() && SpawnApi.estAuSpawn(e.getLevel()) && active()) {
            e.getAffectedBlocks().clear();
            e.getAffectedEntities().clear();
        }
    }

    // ---------------------------------------------------------------------------------------------- chaque tick
    private static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.getServer() == null) {
            return;
        }
        MinecraftServer server = e.getServer();
        ServerLevel l = server.getLevel(SpawnApi.SPAWN);
        if (l == null || !active()) {
            return;
        }
        if (server.getTickCount() % 20 == 0) {
            retirerCreatures(l);
        }
        if (l.players().isEmpty()) {
            return;
        }
        for (ServerPlayer sp : List.copyOf(l.players())) {
            if (sp.isSpectator() || !sp.isAlive()) {
                continue;
            }
            if (sp.getRemainingFireTicks() > 0 || sp.isOnFire()) {
                sp.clearFire();
            }
            sp.resetFallDistance();
            if (sp.getTicksFrozen() > 0) {
                sp.setTicksFrozen(0);
            }
            if (sp.getAirSupply() < sp.getMaxAirSupply()) {
                sp.setAirSupply(sp.getMaxAirSupply());
            }
            FoodData f = sp.getFoodData();
            if (f.getFoodLevel() < 20) {
                f.setFoodLevel(20);
            }
            if (f.getSaturationLevel() < 5.0f) {
                f.setSaturation(5.0f);
            }
            retirerEffetsNegatifs(sp);
            if (sp.getY() < l.getMinBuildHeight() - 8) {
                sortirDuVide(sp);
            }
        }
    }

    /** Tombé sous le monde : retour au point de réveil (vraie téléportation), avant que le vide ne fasse mal. */
    private static void sortirDuVide(ServerPlayer sp) {
        if (!CineReseauServeur.auSpawn(sp)) {
            sp.stopRiding();
            CineReseauServeur.allerAuReveil(sp);                    // point de réveil même s'il paraît encombré
            sp.resetFallDistance();
        }
        sp.setDeltaMovement(0, 0, 0);
        sp.hurtMarked = true;
        HeraliszUtils.LOG.info("[spawn] {} tombé sous le monde du spawn : ramené au point de réveil", sp.getGameProfile().getName());
    }

    // ---------------------------------------------------------------------------------------------- ouvrants (2.22.0)
    /**
     * Au spawn, en survie et en aventure, trappes, portes, portillons, leviers et boutons ne s'utilisent plus (choix du
     * propriétaire) ; staff (permission 2), créatif et spectateur libres. Le reste (PNJ, borne, coffres, casino) n'est
     * pas touché : seul le clic droit sur ces blocs est refusé.
     */
    private static void ouvrants(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || !SpawnApi.estAuSpawn(sp.level())) {
            return;
        }
        if (!sp.gameMode.isSurvival() || sp.hasPermissions(2)) {
            return;
        }
        net.minecraft.world.level.block.Block b = sp.level().getBlockState(e.getPos()).getBlock();
        if (b instanceof net.minecraft.world.level.block.TrapDoorBlock || b instanceof net.minecraft.world.level.block.DoorBlock
                || b instanceof net.minecraft.world.level.block.FenceGateBlock || b instanceof net.minecraft.world.level.block.LeverBlock
                || b instanceof net.minecraft.world.level.block.ButtonBlock) {
            e.setUseBlock(Event.Result.DENY);
            e.setCanceled(true);
        }
    }
}
