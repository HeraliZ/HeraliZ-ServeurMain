package fr.heraliz.armory.suit;

import fr.heraliz.armory.HeralizArmory;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.event.CurioChangeEvent;

/**
 * Garde-fous serveur de la regle des combinaisons (repris tels quels de SuitEvents) : ce qui arrive malgre tout (commande
 * /item, distributeur, autre mod...) est retire au tick suivant et rendu a l'inventaire du joueur (au sol pour le
 * mannequin). Les refus utiles a la prediction du client (Curios, clic droit) restent communs (SuitEvents).
 */
@Mod.EventBusSubscriber(modid = HeralizArmory.MOD_ID)
public final class SuitEventsServeur {
    private SuitEventsServeur() {}


    /** Garde-fou des slots d'armure (joueurs et mannequin). */
    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        LivingEntity entity = event.getEntity();
        EquipmentSlot slot = event.getSlot();
        ItemStack to = event.getTo();
        if (entity.level().isClientSide || slot.getType() != EquipmentSlot.Type.ARMOR || to.isEmpty()) return;
        if (slot == EquipmentSlot.CHEST && SuitRules.isSuit(to)) {
            if (!SuitRules.wearsOtherGear(entity)) return;
            eject(entity, slot, to, false);
        } else if (SuitRules.wearsSuit(entity)) {
            eject(entity, slot, to, true);
        }
    }

    /** Garde-fou des slots Curios de vetements. */
    @SubscribeEvent
    public static void onCurioChange(CurioChangeEvent event) {
        LivingEntity entity = event.getEntity();
        ItemStack to = event.getTo();
        if (entity.level().isClientSide || to.isEmpty() || !SuitRules.blockedCurio(event.getIdentifier()) || !SuitRules.wearsSuit(entity)) return;
        int index = event.getSlotIndex();
        CuriosApi.getCuriosInventory(entity).ifPresent(inv -> inv.getStacksHandler(event.getIdentifier()).ifPresent(h -> {
            if (index < h.getSlots() && h.getStacks().getStackInSlot(index) == to) {
                h.getStacks().setStackInSlot(index, ItemStack.EMPTY);
                // une copie : Curios finit de traiter l'objet d'origine pendant ce tick (bonus retires au tick suivant)
                SuitRules.giveBack(entity, to.copy());
                SuitRules.tell(entity instanceof Player p ? p : null, true);
            }
        }));
    }

    private static void eject(LivingEntity entity, EquipmentSlot slot, ItemStack to, boolean suitInTheWay) {
        if (entity.getItemBySlot(slot) != to) return;
        entity.setItemSlot(slot, ItemStack.EMPTY);
        // une copie : le jeu finit de traiter l'objet d'origine pendant ce tick (bonus retires au tick suivant)
        SuitRules.giveBack(entity, to.copy());
        SuitRules.tell(entity instanceof Player p ? p : null, suitInTheWay);
    }
}
