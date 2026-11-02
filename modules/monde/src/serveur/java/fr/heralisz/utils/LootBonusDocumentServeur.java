package fr.heralisz.utils;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** « Bonus de loot » côté serveur (HeraliszUtils.DOCUMENT_USINE) : clic droit = bonus de loot pour un temps de jeu. */
public class LootBonusDocumentServeur extends LootBonusDocument {
    public LootBonusDocumentServeur(String rarity) {
        super(rarity);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            long restant = BonusLootTemps.activer(sp);
            level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
            // 02/11 : le temps ne s'use que sur la carte et pas AFK (BonusLootConfig) : le message le dit
            boolean carte = BonusLootConfig.carteSeulement();
            sp.sendSystemMessage(Component.translatableWithFallback(carte ? "heralisz_utils.bonus_loot.active_carte" : "heralisz_utils.bonus_loot.active",
                    carte ? "Bonus de loot +%s %% activé : %s de jeu sur la carte (le temps s'arrête au spawn et quand tu ne bouges plus)."
                            : "Bonus de loot +%s %% activé : %s de jeu.",
                    String.valueOf(BonusLootConfig.pourcent()), BonusLootTemps.duree(restant)).withStyle(ChatFormatting.GOLD));
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
