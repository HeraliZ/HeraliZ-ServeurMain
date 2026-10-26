package fr.heraliz.armory.suit;

import fr.heraliz.armory.armor.ArmorConfig;
import fr.heraliz.armory.item.SuitItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Regle des combinaisons (0.9.0), demandee par le proprietaire : « on peut equiper la tenue uniquement avec RIEN d'autre ;
 * si on a un autre truc on ne peut l'equiper, et si on essaie pendant qu'on a une de ces tenues de mettre un autre truc on
 * ne peut pas, il faudra d'abord desequiper la tenue ».
 * <ul>
 *   <li>une combinaison ne s'enfile que si les 4 slots d'armure et les slots Curios de vetements (config
 *   combinaisons.slots_bloques : mask, belt, hands, knees, body, bracelet, head, ear_protection...) sont vides ;
 *   seule exception : une combinaison peut en remplacer une autre ;</li>
 *   <li>tant qu'une combinaison est portee, plus rien n'entre dans ces slots ;</li>
 *   <li>le sac a dos (slot Curios « back ») reste autorise ; les mains aussi.</li>
 * </ul>
 * Valable pour les joueurs et le mannequin. Appliquee par : SuitItem (canEquip, clic droit), mixins des slots d'armure
 * de l'inventaire et de l'ecran de Curios (tous les objets, ceux des autres mods compris, {@link #refusesArmorSlot}),
 * CurioEquipEvent (Curios), menu et clic droit du mannequin, commandes et tenues completes ; garde-fou serveur dans
 * SuitEvents (objet rendu a l'inventaire).
 */
public final class SuitRules {
    private SuitRules() {}

    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    /** Dernier message d'explication par joueur (une fois par seconde au plus : mayPlace est appele souvent). */
    private static final Map<Player, Long> LAST_MESSAGE = new WeakHashMap<>();

    public static boolean isSuit(ItemStack stack) {
        return stack.getItem() instanceof SuitItem;
    }

    /** L'entite porte une combinaison (au torse). */
    public static boolean wearsSuit(@Nullable LivingEntity entity) {
        return entity != null && isSuit(entity.getItemBySlot(EquipmentSlot.CHEST));
    }

    /** Slot Curios de vetements (bloque par une combinaison). */
    public static boolean blockedCurio(String id) {
        return ArmorConfig.SUIT_BLOCKED_SLOTS.get().contains(id);
    }

    /** L'entite porte autre chose qu'une combinaison : slots d'armure ou slots Curios de vetements (normaux ou cosmetiques). */
    public static boolean wearsOtherGear(LivingEntity entity) {
        for (EquipmentSlot slot : ARMOR) {
            ItemStack s = entity.getItemBySlot(slot);
            if (!s.isEmpty() && !(slot == EquipmentSlot.CHEST && isSuit(s))) return true;
        }
        return CuriosApi.getCuriosInventory(entity).resolve().map(inv -> {
            for (var e : inv.getCurios().entrySet()) {
                if (!blockedCurio(e.getKey())) continue;
                if (notEmpty(e.getValue().getStacks()) || notEmpty(e.getValue().getCosmeticStacks())) return true;
            }
            return false;
        }).orElse(false);
    }

    private static boolean notEmpty(IDynamicStackHandler stacks) {
        for (int i = 0; i < stacks.getSlots(); i++) if (!stacks.getStackInSlot(i).isEmpty()) return true;
        return false;
    }

    /** Une combinaison peut etre enfilee : rien d'autre n'est porte (une combinaison deja portee sera remplacee). */
    public static boolean canWearSuit(@Nullable LivingEntity entity) {
        return entity == null || !wearsOtherGear(entity);
    }

    /**
     * Cet objet peut-il entrer dans ce slot d'armure de cette entite ? Combinaison : seulement au torse et sur une entite
     * qui ne porte rien d'autre. Autre objet : seulement si l'entite ne porte pas de combinaison.
     */
    public static boolean mayEquipArmor(@Nullable LivingEntity entity, EquipmentSlot slot, ItemStack stack) {
        if (entity == null || stack.isEmpty()) return true;
        if (isSuit(stack)) return slot == EquipmentSlot.CHEST && canWearSuit(entity);
        return !wearsSuit(entity);
    }

    /**
     * Slot d'armure d'un menu du joueur (inventaire de base InventoryMenu$1, ecran de Curios CuriosContainerV2$1 et
     * CuriosContainer$1 : mixins InventoryArmorSlotMixin et CuriosArmorSlotMixin) : vrai si la regle refuse cet objet,
     * explication au joueur. Tous les objets, ceux des autres mods compris (clic, Maj + clic, touches numeriques).
     */
    public static boolean refusesArmorSlot(Slot self, ItemStack stack) {
        if (stack.isEmpty() || !(self.container instanceof Inventory inv)) return false;
        // slots 36 a 39 de l'inventaire : pieds, jambes, torse, tete
        EquipmentSlot slot = switch (self.getContainerSlot()) {
            case 36 -> EquipmentSlot.FEET;
            case 37 -> EquipmentSlot.LEGS;
            case 38 -> EquipmentSlot.CHEST;
            case 39 -> EquipmentSlot.HEAD;
            default -> null;
        };
        if (slot == null) return false;
        if (isSuit(stack)) {
            // une combinaison ailleurs qu'au torse : le jeu la refuse deja, sans message
            if (slot != EquipmentSlot.CHEST || canWearSuit(inv.player)) return false;
            tell(inv.player, false);
            return true;
        }
        if (wearsSuit(inv.player)) {
            tell(inv.player, true);
            return true;
        }
        return false;
    }

    /** Cet objet peut-il entrer dans ce slot Curios ? Rien dans un slot de vetements tant qu'une combinaison est portee. */
    public static boolean mayEquipCurio(@Nullable LivingEntity entity, String slot, ItemStack stack) {
        return entity == null || stack.isEmpty() || !blockedCurio(slot) || !wearsSuit(entity);
    }

    /**
     * Explique le refus au joueur (barre d'action, cote serveur, une fois par seconde au plus). suitInTheWay : vrai si
     * c'est la combinaison portee qui bloque, faux si c'est le reste de la tenue qui empeche d'enfiler la combinaison.
     */
    public static void tell(@Nullable Player player, boolean suitInTheWay) {
        tell(player, suitInTheWay, false);
    }

    /** Meme message, pour la tenue d'un mannequin (onMannequin) ou celle du joueur. */
    public static void tell(@Nullable Player player, boolean suitInTheWay, boolean onMannequin) {
        if (!(player instanceof ServerPlayer sp)) return;
        long now = sp.level().getGameTime();
        Long last = LAST_MESSAGE.get(sp);
        if (last != null && now - last < 20 && now >= last) return;
        LAST_MESSAGE.put(sp, now);
        String key = "message.heraliz_armory.suit." + (suitInTheWay ? "worn" : "alone") + (onMannequin ? "_mannequin" : "");
        sp.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED), true);
    }

    /** Rend un objet retire : a l'inventaire du joueur (ou au sol s'il est plein), au pied d'une autre entite. */
    public static void giveBack(LivingEntity entity, ItemStack stack) {
        if (stack.isEmpty()) return;
        if (entity instanceof Player p) p.getInventory().placeItemBackInInventory(stack);
        else Block.popResource(entity.level(), entity.blockPosition().above(), stack);
    }
}
