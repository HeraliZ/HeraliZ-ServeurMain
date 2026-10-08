package fr.heralisz.utils;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Document de loot bonus côté serveur (HeraliszUtils.DOCUMENT_USINE) : clic droit d'origine (LuckPerms, son, message). */
public class LootBonusDocumentServeur extends LootBonusDocument {
    public LootBonusDocumentServeur(String rarity) {
        super(rarity);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        MinecraftServer server = level.getServer();
        if (!level.isClientSide && server != null) {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
                    "lp user " + player.getGameProfile().getName() + " permission settemp phatloots.bonus.1.2 true 1h");
            level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
            player.displayClientMessage(Component.translatable("message.heralisz_utils.lootbonus").withStyle(ChatFormatting.GREEN), true);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
