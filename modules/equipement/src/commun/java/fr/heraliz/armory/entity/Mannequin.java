package fr.heraliz.armory.entity;

import fr.heraliz.armory.registry.ModItems;
import fr.heraliz.armory.suit.SuitRules;
import net.minecraft.core.Rotations;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;

/**
 * Mannequin d'armurerie : un porte-armure a silhouette humaine qui porte TOUT, y compris les slots Curios (visage, gilet,
 * mains, et tout slot ajoute par datapack dans data/heraliz_armory/curios/entities/mannequin.json, ex. une ghillie).
 * <ul>
 *   <li>clic droit avec une piece d'armure : comme un porte-armure vanilla ;</li>
 *   <li>clic droit avec un objet Curios (gilet, cagoule, gants...) : il est enfile dans son slot (echange si occupe) ;</li>
 *   <li>accroupi + clic droit : menu complet (armure, mains, slots Curios) ;</li>
 *   <li>deux coups : le mannequin tombe et rend son contenu (armure, mains, Curios) et lui-meme.</li>
 * </ul>
 * 0.9.0 : il porte aussi les combinaisons, avec la meme regle que le joueur (une combinaison se porte seule : SuitRules).
 */
public class Mannequin extends ArmorStand {
    public Mannequin(EntityType<? extends Mannequin> type, Level level) {
        super(type, level);
        setShowArms(true);
        // bras legerement ecartes : les gilets et les manches se lisent bien, rien ne traverse le torse
        setLeftArmPose(new Rotations(0.0F, 0.0F, -6.0F));
        setRightArmPose(new Rotations(0.0F, 0.0F, 6.0F));
    }

    @Override
    public InteractionResult interactAt(Player player, Vec3 hit, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (isMarker() || held.is(Items.NAME_TAG) || player.isSpectator()) return super.interactAt(player, hit, hand);
        if (player.isSecondaryUseActive()) {
            if (!level().isClientSide && player instanceof ServerPlayer sp) ouvrirMenu(sp);
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (!held.isEmpty() && isCurio(held)) {
            if (level().isClientSide) return InteractionResult.CONSUME;
            if (!equipCurio(player, hand, held)) {
                // 0.9.0 : slot de vetements bloque par une combinaison portee
                if (SuitRules.wearsSuit(this)) SuitRules.tell(player, true, true);
                return InteractionResult.FAIL;
            }
            return InteractionResult.SUCCESS;
        }
        // 0.9.0 : regle des combinaisons (une combinaison se porte seule) pour les armures posees au clic droit
        if (!held.isEmpty()) {
            EquipmentSlot slot = Mob.getEquipmentSlotForItem(held);
            if (slot.getType() == EquipmentSlot.Type.ARMOR && !SuitRules.mayEquipArmor(this, slot, held)) {
                if (!level().isClientSide) SuitRules.tell(player, !SuitRules.isSuit(held), true);
                return InteractionResult.FAIL;
            }
        }
        return super.interactAt(player, hit, hand);
    }

    /** L'objet a au moins un slot Curios que porte le mannequin. */
    public boolean isCurio(ItemStack stack) {
        return !CuriosApi.getItemStackSlots(stack, this).isEmpty();
    }

    public static boolean canEquip(String id, int index, Mannequin m, ItemStack stack) {
        SlotContext ctx = new SlotContext(id, m, index, false, true);
        return SuitRules.mayEquipCurio(m, id, stack) && CuriosApi.isStackValid(ctx, stack)
                && CuriosApi.getCurio(stack).map(c -> c.canEquip(ctx)).orElse(true);
    }

    /** Accroupi + clic droit (serveur) : menu complet. Neutre ici ; partie serveur : MannequinServeur. */
    void ouvrirMenu(ServerPlayer player) {
    }

    /**
     * Enfile un objet Curios tenu en main (serveur). Neutre ici (rien n'est enfile) ; la partie serveur (MannequinServeur)
     * reprend la methode d'origine.
     */
    boolean equipCurio(Player player, InteractionHand hand, ItemStack held) {
        return false;
    }

    // ------------------------------------------------------------------ casse (meme comportement qu'un porte-armure)
    /**
     * Casse (meme comportement qu'un porte-armure) : logique serveur (MannequinServeur.hurt). Ici, la branche client de
     * l'original : rien (un monde client ne casse jamais le mannequin lui-meme).
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(ModItems.MANNEQUIN.get());
    }
}
