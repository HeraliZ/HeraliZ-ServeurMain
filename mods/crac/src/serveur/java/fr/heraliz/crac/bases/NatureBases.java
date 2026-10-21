package fr.heraliz.crac.bases;

import fr.heraliz.crac.Crac;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * L'ANCIEN monde des bases ({@code crac:bases}) est vide de vie et d'eau (demande du propriétaire, CRac 1.6.0) ; depuis
 * CRac 1.16.0, les bases sont sur la carte principale, où rien de tout cela ne s'applique (zombies et seaux comme ailleurs) :
 * <ul>
 *   <li>aucune créature : toute entité {@link Mob} (animaux, monstres, villageois, marchands ambulants, golems, PNJ et
 *   zombies des mods qui les font apparaître autour des joueurs) est refusée à l'entrée dans le monde (apparition, œuf,
 *   cage, commande, chargement d'un tronçon), sauf {@code [bases] creatures_permises} ; celles qui y sont quand même
 *   sont retirées chaque seconde. Les autres entités restent (textes flottants, objets posés, véhicules, cadavres,
 *   porte-armures). Le terrain lui-même n'en fait naître aucune (biomes sans liste d'apparition, voir
 *   outils/monde_bases.py) ;</li>
 *   <li>aucun seau ne se vide dans le monde des bases, même dans une base ({@code [bases] seaux}) : le terrain n'a pas
 *   d'eau, les joueurs n'en ajoutent pas. Hors des bases, HeraliZ Loot refuse déjà les seaux.</li>
 * </ul>
 * Libres : créatif et spectateur (staff) pour les seaux. Réglage {@code [bases] creatures = true} : comme avant.
 */
@EventBusSubscriber(modid = "crac")
public final class NatureBases {
    private static int ticks;

    private NatureBases() {
    }

    // ---------------------------------------------------------------------------------------------- créatures

    static boolean creatureInterdite(Entity e) {
        if (!(e instanceof Mob) || BasesReglages.b(BasesReglages.CREATURES, false)) {
            return false;
        }
        List<? extends String> permises = BasesReglages.SPEC.isLoaded() ? BasesReglages.CREATURES_PERMISES.get() : List.of();
        if (permises.isEmpty()) {
            return true;
        }
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        return id == null || !permises.contains(id.toString());
    }

    /** Aucune créature n'entre dans le monde des bases. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void arrivee(EntityJoinLevelEvent e) {
        if (!e.getLevel().isClientSide() && MondeBases.estAncien(e.getLevel()) && creatureInterdite(e.getEntity())) {
            e.setCanceled(true);
        }
    }

    /** Apparitions naturelles, cages, œufs, renforts, patrouilles : refusées avant même de préparer la créature. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void apparition(MobSpawnEvent.FinalizeSpawn e) {
        if (MondeBases.estAncien(e.getLevel().getLevel()) && creatureInterdite(e.getEntity())) {
            e.setSpawnCancelled(true);
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.getServer() == null || ++ticks % 20 != 0) {
            return;
        }
        ServerLevel l = e.getServer().getLevel(MondeBases.ANCIEN_CLE);
        if (l != null) {
            try {
                retirer(l);
            } catch (RuntimeException ex) {
                Crac.LOGGER.error("[CRac] monde des bases : retrait des créatures en erreur", ex);
            }
        }
    }

    private static void retirer(ServerLevel l) {
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
            Crac.LOGGER.info("[CRac] {} créature(s) retirée(s) du monde des bases", a.size());
        }
    }

    // ---------------------------------------------------------------------------------------------- seaux

    /** Seau plein vidé (eau, lave, poisson) dans le monde des bases : refusé, même dans une base. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void seau(FillBucketEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || !MondeBases.estAncien(e.getLevel())
            || BasesReglages.b(BasesReglages.SEAUX, false) || sp.isCreative() || sp.isSpectator()) {
            return;
        }
        ItemStack s = e.getEmptyBucket();
        if (!(s.getItem() instanceof BucketItem b) || b.getFluid() == Fluids.EMPTY) {
            return;
        }
        e.setCanceled(true);
        T.barre(sp, T.t("crac.bases.seau", "Pas d'eau dans le monde des bases."), ChatFormatting.RED);
        // le jeu du joueur a pu poser le liquide de lui-même : on lui renvoie les vrais blocs et son inventaire
        HitResult h = e.getTarget();
        if (h instanceof BlockHitResult bh && h.getType() == HitResult.Type.BLOCK) {
            BlockPos p = bh.getBlockPos();
            sp.connection.send(new ClientboundBlockUpdatePacket(e.getLevel(), p));
            sp.connection.send(new ClientboundBlockUpdatePacket(e.getLevel(), p.relative(bh.getDirection())));
        }
        sp.inventoryMenu.sendAllDataToRemote();
    }
}
