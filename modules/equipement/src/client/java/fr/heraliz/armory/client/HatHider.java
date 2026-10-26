package fr.heraliz.armory.client;

import fr.heraliz.armory.HeralizArmory;
import fr.heraliz.armory.armor.PieceDef;
import fr.heraliz.armory.item.PieceItem;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Casques et cagoules ajustes : le calque « chapeau » de la peau (cheveux, accessoires dessines sur le calque
 * exterieur de la tete) est masque pendant le rendu de l'entite qui porte une piece marquee hide_hat. Sans cela, ce
 * calque (gonfle d'un demi-pixel) traverserait les coques qui epousent la tete. Restaure apres le rendu.
 */
@Mod.EventBusSubscriber(modid = HeralizArmory.MOD_ID, value = Dist.CLIENT)
public final class HatHider {
    /** Modeles dont le calque a ete masque pour ce rendu, et sa visibilite d'origine. */
    private static final Map<HumanoidModel<?>, Boolean> HIDDEN = new IdentityHashMap<>();

    private HatHider() {}

    @SubscribeEvent
    public static void onPre(RenderLivingEvent.Pre<?, ?> event) {
        if (!(event.getRenderer().getModel() instanceof HumanoidModel<?> model) || !hidesHat(event.getEntity())) return;
        HIDDEN.putIfAbsent(model, model.hat.visible);
        model.hat.visible = false;
    }

    @SubscribeEvent
    public static void onPost(RenderLivingEvent.Post<?, ?> event) {
        if (!(event.getRenderer().getModel() instanceof HumanoidModel<?> model)) return;
        Boolean before = HIDDEN.remove(model);
        if (before != null) model.hat.visible = before;
    }

    /** Vrai si l'entite porte (casque, ou piece Curios visible) une piece qui masque le calque chapeau. */
    public static boolean hidesHat(LivingEntity entity) {
        ItemStack head = entity.getItemBySlot(EquipmentSlot.HEAD);
        if (head.getItem() instanceof PieceItem p && hides(p.def())) return true;
        // combinaisons (0.9.0) : portees au torse, elles couvrent aussi la tete
        if (entity.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof PieceItem p && p.def().isSuit() && hides(p.def())) return true;
        // nos slots Curios n'existent que sur les joueurs et le mannequin : pas de recherche pour les zombies et autres mobs
        if (!(entity instanceof net.minecraft.world.entity.player.Player) && !(entity instanceof fr.heraliz.armory.entity.Mannequin)) return false;
        // meme test que findCurios(...).anyMatch(visible), sans la liste de resultats creee a chaque image par joueur
        var inv = CuriosApi.getCuriosInventory(entity).resolve();
        if (inv.isEmpty()) return false;
        for (ICurioStacksHandler handler : inv.get().getCurios().values()) {
            var stacks = handler.getStacks();
            var renders = handler.getRenders();
            for (int i = 0; i < stacks.getSlots(); i++) {
                ItemStack stack = stacks.getStackInSlot(i);
                if (!stack.isEmpty() && stack.getItem() instanceof PieceItem p && hides(p.def()) && renders.size() > i && renders.get(i)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hides(PieceDef def) {
        ArmorModels.Variants v = ArmorModels.INSTANCE.get(def);
        return v != null && v.hideHat();
    }
}
