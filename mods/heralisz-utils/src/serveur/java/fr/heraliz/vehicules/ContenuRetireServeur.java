package fr.heraliz.vehicules;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Partie SERVEUR de {@link ContenuRetire} (docs/METHODE_CLIENT_SERVEUR.md §5) : les objets retirés sont effacés des
 * inventaires et ne peuvent pas apparaître au sol. Code d'origine déplacé tel quel ; inscrit par la partie serveur
 * (HeralizVehiculesServeur.init, appelé par serveur.PartieServeur), à sa place d'origine dans l'ordre des écouteurs.
 */
public final class ContenuRetireServeur {
    /** Vérification des inventaires : une fois par seconde et par joueur. */
    private static final int INTERVALLE = 20;

    private ContenuRetireServeur() {
    }

    static void enregistrer(IEventBus bus) {
        bus.addListener(ContenuRetireServeur::onPlayerTick);
        bus.addListener(ContenuRetireServeur::onEntityJoin);
    }

    private static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide() || event.player.tickCount % INTERVALLE != 0) return;
        Inventory inv = event.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (ContenuRetire.estRetire(s)) {
                HeralizVehicules.LOG.info("[vehicules] objet retire efface chez {} : {} {}", event.player.getGameProfile().getName(),
                        ForgeRegistries.ITEMS.getKey(s.getItem()), s.getTag() == null ? "" : s.getTag());
                inv.setItem(i, ItemStack.EMPTY);
            }
        }
    }

    private static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof ItemEntity item && ContenuRetire.estRetire(item.getItem())) {
            event.setCanceled(true);
        }
    }
}
