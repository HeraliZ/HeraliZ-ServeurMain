package fr.heraliz.armory.item;

import fr.heraliz.armory.armor.Equiper;
import fr.heraliz.armory.armor.PieceDef;
import fr.heraliz.armory.armor.SetDef;
import fr.heraliz.armory.registry.ModItems;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Partie SERVEUR de {@link SetKitItem} (docs/METHODE_CLIENT_SERVEUR.md §5.3) : la tenue s'enfile (code d'origine, tel quel).
 * Creee par l'usine ModItems.KIT_USINE, branchee par la partie serveur.
 */
public class SetKitItemServeur extends SetKitItem {
    public SetKitItemServeur(SetDef set) {
        super(set);
    }

    @Override
    protected InteractionResultHolder<ItemStack> enfiler(Level level, Player player, InteractionHand hand, ItemStack kit,
                                                        List<PieceDef> pieces, boolean intoInventory) {
        List<ItemStack> leftovers = new ArrayList<>();   // pieces a ranger (accroupi, slot indisponible) et objets retires
        for (PieceDef def : pieces) {
            ItemStack piece = new ItemStack(ModItems.get(def));
            if (intoInventory || !wear(player, def, piece, leftovers)) leftovers.add(piece);
        }
        if (!player.getAbilities().instabuild) kit.shrink(1);
        ItemStack result = kit;
        for (ItemStack stack : leftovers) {
            if (result.isEmpty()) {
                // la main liberee par la derniere tenue recoit le premier objet, avant de ranger les autres : sinon l'un
                // d'eux pourrait y etre range puis efface par le jeu, qui remplace ensuite la main par le resultat
                result = stack;
                player.setItemInHand(hand, result);
            } else {
                player.getInventory().placeItemBackInInventory(stack);   // inventaire, ou au sol s'il est plein
            }
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_GENERIC, player.getSoundSource(),
                1.0F, 1.0F);
        player.gameEvent(GameEvent.EQUIP);
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.success(result);
    }

    /**
     * Enfile la piece dans son slot (meme methode que /armory equip) ; l'objet qu'elle remplace part dans leftovers.
     * Faux si le slot est indisponible (slot Curios absent, objet maudit du lien) : la piece est alors rangee.
     */
    private static boolean wear(Player player, PieceDef def, ItemStack piece, List<ItemStack> leftovers) {
        ItemStack old;
        if (def.curioSlot() != null) {
            Optional<ICurioStacksHandler> handler = CuriosApi.getCuriosInventory(player).resolve()
                    .flatMap(inv -> inv.getStacksHandler(def.curioSlot()));
            if (handler.isEmpty() || handler.get().getSlots() < 1) return false;
            old = handler.get().getStacks().getStackInSlot(0);
        } else {
            old = player.getItemBySlot(def.equipmentSlot());
        }
        if (!old.isEmpty() && EnchantmentHelper.hasBindingCurse(old) && !player.isCreative()) return false;
        old = old.copy();
        if (!Equiper.piece(player, def, piece)) return false;
        if (!old.isEmpty()) leftovers.add(old);
        return true;
    }
}
