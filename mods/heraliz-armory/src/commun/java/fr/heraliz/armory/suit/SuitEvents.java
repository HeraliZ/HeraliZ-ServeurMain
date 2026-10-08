package fr.heraliz.armory.suit;

import fr.heraliz.armory.HeralizArmory;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.event.CurioEquipEvent;

/**
 * Application cote serveur (et client pour la prediction) de la regle des combinaisons (SuitRules) :
 * <ul>
 *   <li>Curios : un slot de vetements refuse tout objet tant qu'une combinaison est portee (CurioEquipEvent, utilise par
 *   le panneau Curios, le clic droit, le shift-clic et le mannequin) ;</li>
 *   <li>clic droit avec une armure (de n'importe quel mod) pendant qu'une combinaison est portee : refuse ;</li>
 *   <li>garde-fou : ce qui arrive malgre tout (commande /item, distributeur, autre mod...) est retire au tick suivant et
 *   rendu a l'inventaire du joueur (au sol pour le mannequin) : une combinaison enfilee alors qu'autre chose est porte,
 *   ou un objet mis dans un slot d'armure ou de vetements pendant qu'une combinaison est portee.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = HeralizArmory.MOD_ID)
public final class SuitEvents {
    private SuitEvents() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onCurioEquip(CurioEquipEvent event) {
        var ctx = event.getSlotContext();
        if (!SuitRules.mayEquipCurio(ctx.entity(), ctx.identifier(), event.getStack())) {
            event.setResult(Event.Result.DENY);
            SuitRules.tell(ctx.entity() instanceof Player p ? p : null, true);
        }
    }

    /** Clic droit avec une piece d'armure (Equipable : armures, citrouille, tetes...) alors qu'une combinaison est portee. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClick(PlayerInteractEvent.RightClickItem event) {
        ItemStack stack = event.getItemStack();
        Player player = event.getEntity();
        if (SuitRules.isSuit(stack) || Equipable.get(stack) == null || !SuitRules.wearsSuit(player)) return;
        if (Mob.getEquipmentSlotForItem(stack).getType() != EquipmentSlot.Type.ARMOR) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        SuitRules.tell(player, true);
    }
}
