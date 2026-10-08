package fr.heralisz.bases.server;

import fr.heralisz.bases.HeraliszBases;
import fr.heralisz.bases.api.BasesApi;
import fr.heralisz.bases.api.PillageHooks;
import fr.heralisz.bases.model.Base;
import fr.heralisz.bases.monde.Grille;
import fr.heralisz.bases.registry.BasesRegistry;
import fr.heralisz.clans.model.Perm;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ChorusFruitItem;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EndGatewayBlock;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Regles de la dimension des bases (BASES_SPEC §3), toutes cote serveur :
 * <ul>
 *   <li>construire / casser / poser un conteneur : seulement dans le volume constructible de la base (salles ouvertes,
 *   hall, couloirs), jamais la coque, et selon les permissions BASE_* du clan (membres) ou le module pillage
 *   (visiteurs) ;</li>
 *   <li>clic droit : conteneurs = BASE_COFFRES, portes / trappes / portillons / leviers / boutons = BASE_PORTES ;</li>
 *   <li>interdits : lits, ancres, portails, perles d'ender, chorus, elytres ; explosions sans effet sur la coque et hors
 *   du volume constructible ;</li>
 *   <li>admins (op en creatif / spectateur) : aucune restriction.</li>
 * </ul>
 * Les refus sont propres (evenement annule) avec un message discret (1 par seconde).
 */
public final class Regles {
    private Regles() {
    }

    private static boolean ici(Level level) {
        return !level.isClientSide && Grille.estBunkers(level);
    }

    /** Bloc interdit a la pose dans la dimension. */
    private static boolean interdit(BlockState s) {
        Block b = s.getBlock();
        return b instanceof BedBlock || b instanceof RespawnAnchorBlock || b instanceof NetherPortalBlock || b instanceof EndPortalBlock
                || b instanceof EndPortalFrameBlock || b instanceof EndGatewayBlock;
    }

    private static boolean coque(BlockState s) {
        return s.is(BasesRegistry.COQUE.get()) || s.is(BasesRegistry.SORTIE.get());
    }

    /** Conteneur : BlockEntity avec inventaire (Container ou capacite d'objets), ou coffre de l'Ender. */
    public static boolean estConteneur(Level level, BlockPos pos, BlockState s) {
        if (s.getBlock() instanceof EnderChestBlock) return true;
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return false;
        return be instanceof Container || be.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent();
    }

    /** Porte au sens large : portes, trappes, portillons, leviers, boutons. */
    public static boolean estPorte(BlockState s) {
        Block b = s.getBlock();
        return b instanceof DoorBlock || b instanceof TrapDoorBlock || b instanceof FenceGateBlock || b instanceof LeverBlock
                || b instanceof ButtonBlock;
    }

    // ------------------------------------------------------------------ casser
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBreak(BlockEvent.BreakEvent e) {
        if (!(e.getLevel() instanceof Level level) || !ici(level) || !(e.getPlayer() instanceof ServerPlayer p)) return;
        if (BaseService.estAdmin(p)) return;
        BlockPos pos = e.getPos();
        Base b = BaseService.baseA(level, pos);
        if (b == null || !b.prete() || coque(e.getState()) || !BaseService.constructible(b, pos)) {
            e.setCanceled(true);
            BaseService.refus(p, b == null ? "refus.heralisz_bases.coque" : BaseService.refusHorsVolume(b, pos, "refus.heralisz_bases.coque"));
            return;
        }
        if (BaseService.estMembre(p, b)) {
            if (!BaseService.membreAvec(p, b, Perm.BASE_CASSER)) {
                e.setCanceled(true);
                BaseService.refus(p, "refus.heralisz_bases.casser");
            }
            return;
        }
        if (!hook(() -> BasesApi.pillage().peutCasser(p, b, pos, e.getState()))) {
            e.setCanceled(true);
            BaseService.refus(p, "refus.heralisz_bases.visiteur");
        }
    }

    // ------------------------------------------------------------------ poser
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getLevel() instanceof Level level) || !ici(level)) return;
        Entity ent = e.getEntity();
        ServerPlayer p = ent instanceof ServerPlayer sp ? sp : null;
        if (p != null && BaseService.estAdmin(p)) return;
        // pose multiple (lit, porte) : chaque position est controlee
        if (e instanceof BlockEvent.EntityMultiPlaceEvent multi) {
            for (BlockSnapshot snap : multi.getReplacedBlockSnapshots()) {
                if (!posePermise(level, p, snap.getPos(), level.getBlockState(snap.getPos()))) {
                    e.setCanceled(true);
                    return;
                }
            }
            return;
        }
        if (!posePermise(level, p, e.getPos(), e.getPlacedBlock())) e.setCanceled(true);
    }

    private static boolean posePermise(Level level, ServerPlayer p, BlockPos pos, BlockState s) {
        if (interdit(s)) {
            if (p != null) BaseService.refus(p, "refus.heralisz_bases.interdit");
            return false;
        }
        Base b = BaseService.baseA(level, pos);
        if (b == null || !b.prete() || !BaseService.constructible(b, pos)) {
            if (p != null) BaseService.refus(p, b == null ? "refus.heralisz_bases.hors_volume"
                    : BaseService.refusHorsVolume(b, pos, "refus.heralisz_bases.hors_volume"));
            return false;
        }
        // pose sans joueur (dispensaire, golem de neige, mod) : seulement dans le volume constructible
        if (p == null) return true;
        if (BaseService.estMembre(p, b)) {
            boolean conteneur = s.getBlock() instanceof BaseEntityBlock && estConteneur(level, pos, s);
            Perm perm = conteneur ? Perm.BASE_POSER_COFFRES : Perm.BASE_CONSTRUIRE;
            if (!BaseService.membreAvec(p, b, perm)) {
                BaseService.refus(p, conteneur ? "refus.heralisz_bases.poser_coffres" : "refus.heralisz_bases.construire");
                return false;
            }
            return true;
        }
        if (!hook(() -> BasesApi.pillage().peutPoser(p, b, pos, s))) {
            BaseService.refus(p, "refus.heralisz_bases.visiteur");
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ clic droit
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClick(PlayerInteractEvent.RightClickBlock e) {
        if (!ici(e.getLevel()) || !(e.getEntity() instanceof ServerPlayer p) || BaseService.estAdmin(p)) return;
        Level level = e.getLevel();
        BlockPos pos = e.getPos();
        BlockState s = level.getBlockState(pos);
        if (s.is(BasesRegistry.SORTIE.get())) return; // la sortie est pour tout le monde
        Base b = BaseService.baseA(level, pos);
        if (b == null || !b.prete()) {
            e.setUseBlock(Event.Result.DENY);
            e.setUseItem(Event.Result.DENY);
            return;
        }
        PillageHooks.Usage usage = estConteneur(level, pos, s) ? PillageHooks.Usage.CONTENEUR
                : estPorte(s) ? PillageHooks.Usage.PORTE : PillageHooks.Usage.AUTRE;
        if (BaseService.estMembre(p, b)) {
            Perm perm = usage == PillageHooks.Usage.CONTENEUR ? Perm.BASE_COFFRES : usage == PillageHooks.Usage.PORTE ? Perm.BASE_PORTES : null;
            if (perm != null && !BaseService.membreAvec(p, b, perm)) {
                e.setUseBlock(Event.Result.DENY);
                BaseService.refus(p, usage == PillageHooks.Usage.CONTENEUR ? "refus.heralisz_bases.coffres" : "refus.heralisz_bases.portes");
            }
            return;
        }
        if (!hook(() -> BasesApi.pillage().peutUtiliser(p, b, pos, s, usage))) {
            e.setUseBlock(Event.Result.DENY);
            if (usage != PillageHooks.Usage.AUTRE) BaseService.refus(p, "refus.heralisz_bases.visiteur");
        }
    }

    /** Outils qui transforment un bloc (houe, hache, pelle) : comme casser. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onOutil(BlockEvent.BlockToolModificationEvent e) {
        if (!(e.getLevel() instanceof Level level) || !ici(level) || e.isSimulated()) return;
        if (!(e.getPlayer() instanceof ServerPlayer p) || BaseService.estAdmin(p)) return;
        Base b = BaseService.baseA(level, e.getPos());
        boolean ok = b != null && b.prete() && !coque(e.getState()) && BaseService.constructible(b, e.getPos())
                && (BaseService.estMembre(p, b) ? BaseService.membreAvec(p, b, Perm.BASE_CONSTRUIRE)
                : hook(() -> BasesApi.pillage().peutCasser(p, b, e.getPos(), e.getState())));
        if (!ok) {
            e.setCanceled(true);
            BaseService.refus(p, "refus.heralisz_bases.construire");
        }
    }

    /** Seaux : vider ou remplir seulement dans le volume constructible, avec BASE_CONSTRUIRE. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onSeau(FillBucketEvent e) {
        if (!ici(e.getLevel()) || !(e.getEntity() instanceof ServerPlayer p) || BaseService.estAdmin(p)) return;
        if (!(e.getTarget() instanceof net.minecraft.world.phys.BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        // seau vide : la source visee ; seau plein : le bloc vise (s'il prend l'eau) ou celui devant la face
        BlockPos vise = hit.getBlockPos();
        boolean plein = !e.getEmptyBucket().isEmpty() && e.getEmptyBucket().getItem() != net.minecraft.world.item.Items.BUCKET;
        Base b0 = BaseService.baseA(e.getLevel(), vise);
        BlockPos pos = plein && (b0 == null || !BaseService.constructible(b0, vise)) ? vise.relative(hit.getDirection()) : vise;
        Base b = BaseService.baseA(e.getLevel(), pos);
        boolean ok = b != null && b.prete() && BaseService.constructible(b, pos)
                && (BaseService.estMembre(p, b) ? BaseService.membreAvec(p, b, Perm.BASE_CONSTRUIRE)
                : hook(() -> BasesApi.pillage().peutPoser(p, b, pos, e.getLevel().getBlockState(pos))));
        if (!ok) {
            e.setCanceled(true);
            BaseService.refus(p, "refus.heralisz_bases.construire");
        }
    }

    // ------------------------------------------------------------------ explosions
    /**
     * Les explosions ne touchent jamais la coque ni ce qui est hors du volume constructible (lumieres encastrees, sas...).
     * Priorite HIGH : le module pillage (blindage, C4) voit une liste deja nettoyee.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onExplosion(ExplosionEvent.Detonate e) {
        Level level = e.getLevel();
        if (!ici(level)) return;
        e.getAffectedBlocks().removeIf(pos -> {
            BlockState s = level.getBlockState(pos);
            if (s.isAir()) return false;
            if (coque(s)) return true;
            Base b = BaseService.baseA(level, pos);
            return b == null || !BaseService.constructible(b, pos);
        });
    }

    @SubscribeEvent
    public static void onPortail(BlockEvent.PortalSpawnEvent e) {
        if (e.getLevel() instanceof Level level && ici(level)) e.setCanceled(true);
    }

    // ------------------------------------------------------------------ objets interdits
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onUtiliserObjet(PlayerInteractEvent.RightClickItem e) {
        if (!ici(e.getLevel()) || !(e.getEntity() instanceof ServerPlayer p) || BaseService.estAdmin(p)) return;
        ItemStack st = e.getItemStack();
        if (st.getItem() instanceof EnderpearlItem || st.getItem() instanceof ChorusFruitItem) {
            e.setCanceled(true);
            BaseService.refus(p, "refus.heralisz_bases.interdit");
            p.inventoryMenu.sendAllDataToRemote();
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onManger(LivingEntityUseItemEvent.Start e) {
        if (e.getEntity() instanceof ServerPlayer p && ici(p.level()) && e.getItem().getItem() instanceof ChorusFruitItem && !BaseService.estAdmin(p)) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPerle(EntityTeleportEvent.EnderPearl e) {
        if (ici(e.getEntity().level())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onChorus(EntityTeleportEvent.ChorusFruit e) {
        if (ici(e.getEntity().level())) e.setCanceled(true);
    }

    /** Elytres : le vol plane est coupe dans la dimension. */
    @SubscribeEvent
    public static void onTickJoueur(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.player.level().isClientSide) return;
        Player pl = e.player;
        if (pl.isFallFlying() && Grille.estBunkers(pl.level()) && !(pl instanceof ServerPlayer sp && BaseService.estAdmin(sp))) {
            pl.stopFallFlying();
        }
    }

    // ------------------------------------------------------------------ outil
    private static boolean hook(java.util.function.BooleanSupplier f) {
        try {
            return f.getAsBoolean();
        } catch (RuntimeException ex) {
            HeraliszBases.LOG.error("[bases] module pillage en erreur (refus par defaut)", ex);
            return false;
        }
    }
}
