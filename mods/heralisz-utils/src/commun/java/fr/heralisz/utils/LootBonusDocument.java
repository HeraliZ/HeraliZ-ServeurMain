package fr.heralisz.utils;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Document de loot bonus : clic droit pour donner au joueur la permission temporaire PhatLoots
 * « phatloots.bonus.1.2 » pendant 1 h (via LuckPerms, comme l'ancien objet ArkaniaZ).
 * Jar fusionné : l'objet ici ; l'effet du clic dans LootBonusDocumentServeur (HeraliszUtils.DOCUMENT_USINE).
 */
public class LootBonusDocument extends UtilItem {
    public LootBonusDocument(String rarity) {
        super("document", rarity);
    }

    /** Clic droit : la permission, le son et le message sont donnés par LootBonusDocumentServeur (partie serveur). */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
}
