package fr.heraliz.vehicules;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.ywzj.vehicle.all.AllConfigs;
import org.ywzj.vehicle.entity.misc.VehiclePart;
import org.ywzj.vehicle.entity.vehicle.AbstractVehicle;
import org.ywzj.vehicle.item.VehicleSpawnItem;

import java.util.List;

/**
 * Les véhicules et les systèmes du serveur.
 * <ul>
 *   <li><b>Matchs et bunkers</b> ({@link VehiculesConfig#DIMENSIONS_INTERDITES}) : impossible d'y poser un véhicule ; un
 *   véhicule qui y arriverait (commande, vieux monde) est retiré et rendu en objet à sa place, avec le contenu de son
 *   coffre ; aucun véhicule ne peut y changer de dimension.</li>
 *   <li><b>Tag de combat</b> (clé {@code heralisz:combat_jusqua} de HeraliZ Matchs, epoch ms) : pas de véhicule sorti de la
 *   poche en plein combat. Les dégâts d'un véhicule conduit comptent déjà comme venant du conducteur (source de Limitless
 *   Vehicle : entité causale = conducteur), donc ils taguent normalement.</li>
 *   <li><b>Téléportations</b> : nos téléportations (bases, Coffre des matchs, train) font descendre le joueur par
 *   {@code stopRiding}, que Limitless Vehicle accepte (il ne bloque que la touche « s'accroupir ») ; le moteur fait aussi
 *   descendre à chaque {@code EntityTeleportEvent} et la téléportation vanilla vers une autre dimension descend d'elle-même.
 *   Filet en plus ici : descente avant tout changement de dimension d'un passager.</li>
 *   <li><b>Blocs</b> : les explosions de véhicules ne cassent rien (bases, bunkers) : réglages du moteur forcés.</li>
 * </ul>
 * Messages au joueur : {@code sendSystemMessage} (les retours de commande sont coupés sur le serveur).
 */
public final class Regles {
    private Regles() {
    }

    /** Clé du tag de combat (contrat de HeraliZ Matchs, sans en dépendre). */
    public static final String CLE_COMBAT = "heralisz:combat_jusqua";

    static void enregistrer(IEventBus bus) {
        bus.addListener(EventPriority.HIGH, Regles::onPose);
        bus.addListener(Regles::onArrivee);
        bus.addListener(Regles::onVoyage);
        bus.addListener(Regles::onDemarrage);
    }

    public static boolean dimensionInterdite(ResourceKey<Level> dim) {
        List<? extends String> l;
        try {
            l = VehiculesConfig.DIMENSIONS_INTERDITES.get();
        } catch (IllegalStateException pasEncoreCharge) {
            l = VehiculesConfig.DIMENSIONS_INTERDITES.getDefault();
        }
        return l.contains(dim.location().toString());
    }

    public static boolean enCombat(ServerPlayer p) {
        return p.getPersistentData().getLong(CLE_COMBAT) > System.currentTimeMillis();
    }

    private static boolean refusEnCombat() {
        try {
            return VehiculesConfig.REFUS_EN_COMBAT.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    /** Pose d'un véhicule (clic droit d'un objet véhicule sur un bloc). */
    private static void onPose(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getItemStack().getItem() instanceof VehicleSpawnItem) || !(e.getEntity() instanceof ServerPlayer p)) return;
        String refus = null;
        if (dimensionInterdite(p.level().dimension())) refus = "heraliz_vehicules.refus.dimension";
        else if (refusEnCombat() && enCombat(p)) refus = "heraliz_vehicules.refus.combat";
        else if (ContenuRetire.estRetire(e.getItemStack())) refus = "heraliz_vehicules.refus.retire";
        if (refus != null) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.FAIL);
            p.sendSystemMessage(Component.translatable(refus).withStyle(ChatFormatting.RED));
            HeralizVehicules.LOG.info("[vehicules] pose refusee pour {} en {} ({})", p.getGameProfile().getName(),
                    p.level().dimension().location(), refus);
        }
    }

    /**
     * Véhicule qui apparaît dans une dimension interdite : retiré, rendu en objet sur place (tick suivant), avec ce que
     * contenait son coffre (sinon perdu avec lui).
     */
    private static void onArrivee(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof AbstractVehicle v) || !dimensionInterdite(e.getLevel().dimension())) return;
        e.setCanceled(true);
        ResourceLocation id = v.getVehicleId();
        ServerLevel level = (ServerLevel) e.getLevel();
        double x = v.getX(), y = v.getY(), z = v.getZ();
        HeralizVehicules.LOG.warn("[vehicules] vehicule {} retire de {} ({} {} {}) : dimension sans vehicules", id,
                level.dimension().location(), (int) x, (int) y, (int) z);
        List<ItemStack> contenu = CoffreRetire.sortir(v);
        // un débris d'épave (VehiclePart) porte l'id du véhicule détruit : pas de véhicule neuf rendu pour lui
        boolean rendu = !(v instanceof VehiclePart) && id != null && Contenu.VEHICULES.contains(id.toString());
        if (rendu || !contenu.isEmpty()) {
            level.getServer().execute(() -> {
                if (rendu) level.addFreshEntity(new ItemEntity(level, x, y + 0.5, z, Contenu.vehicule(id.toString())));
                CoffreRetire.poser(level, new Vec3(x, y + 0.5, z), contenu, id);
            });
        }
    }

    /** Changement de dimension : un véhicule ne va pas dans une dimension interdite ; un passager descend d'abord. */
    private static void onVoyage(EntityTravelToDimensionEvent e) {
        if (e.getEntity() instanceof AbstractVehicle && dimensionInterdite(e.getDimension())) {
            e.setCanceled(true);
            return;
        }
        if (e.getEntity() instanceof ServerPlayer p && p.getVehicle() instanceof AbstractVehicle) {
            p.stopRiding();
        }
    }

    private static void onDemarrage(ServerStartedEvent e) {
        appliquerProtection();
    }

    static void onConfig(ModConfigEvent e) {
        if (e.getConfig().getSpec() == VehiculesConfig.SPEC && ServerLifecycleHooks.getCurrentServer() != null) {
            appliquerProtection();
        }
    }

    /** Réglages de Limitless Vehicle forcés : explosions sans casse de blocs (bases, bunkers, pillages). */
    static void appliquerProtection() {
        boolean proteger;
        try {
            proteger = VehiculesConfig.PROTEGER_BLOCS.get();
        } catch (IllegalStateException e) {
            proteger = true;
        }
        if (!proteger) return;
        try {
            AllConfigs.CommonConfig c = AllConfigs.common;
            boolean change = false;
            if (c.canDestroyBlock.get()) {
                c.canDestroyBlock.set(false);
                change = true;
            }
            if (c.explosionDropBlock.get()) {
                c.explosionDropBlock.set(false);
                change = true;
            }
            if (c.infiniteFuel.get()) {
                c.infiniteFuel.set(false);
                change = true;
            }
            if (change) {
                c.canDestroyBlock.save();
                HeralizVehicules.LOG.info("[vehicules] Limitless Vehicle : explosions sans casse de blocs, essence obligatoire");
            }
        } catch (RuntimeException ex) {
            HeralizVehicules.LOG.error("[vehicules] impossible de regler Limitless Vehicle (canDestroyBlock)", ex);
        }
    }
}
