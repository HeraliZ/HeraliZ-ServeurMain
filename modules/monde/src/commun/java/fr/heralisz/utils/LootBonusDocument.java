package fr.heralisz.utils;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Objet « Bonus de loot » (vendu sur la boutique) : clic droit = +10 % de loot (objets plus rares, quantités) à
 * l'ouverture des conteneurs de HeraliZ Loot, pendant 2 h de jeu (réglages : BonusLootConfig). Un deuxième objet ajoute
 * sa durée. Jar fusionné : l'objet ici ; l'effet du clic dans LootBonusDocumentServeur (HeraliszUtils.DOCUMENT_USINE).
 */
public class LootBonusDocument extends UtilItem {
    public LootBonusDocument(String rarity) {
        super("document", rarity);
    }

    /** Clic droit : le bonus, le son et le message sont donnés par LootBonusDocumentServeur (partie serveur). */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
}
