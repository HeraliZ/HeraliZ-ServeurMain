package com.heraliz.extraction.item;

import com.heraliz.extraction.entity.ExtractionTerminalEntity;
import com.heraliz.extraction.pont.Crochet;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * Terminal d'extraction : un PC portable bricolé avec une caisse à fumigène fixée sur le côté.
 * Maintenir clic droit pour taper la séquence de lancement, puis le terminal est posé au sol.
 * <p>
 * Les erreurs (toit au-dessus de la tête, pas au sol) ne s'affichent que sur l'écran du PC :
 * tant que le joueur garde clic droit, il continue de taper et l'écran enchaîne les refus.
 * <p>
 * Partie commune : saisie, erreurs de lancement, infobulle. La pose du terminal au bout de la saisie est dans
 * ExtractionTerminalItemServeur (src/serveur).
 */
public class ExtractionTerminalItem extends Item {
    /** Rendu en main et pose des bras : branchés par la partie client (TerminalClientExtensions). */
    public static final Crochet<Consumer<IClientItemExtensions>> RENDU_CLIENT = new Crochet<>();

    /** Durée max d'utilisation : en pratique la saisie s'arrête d'elle-même dès que le lancement réussit. */
    private static final int MAX_USE = 72000;

    /** Raison d'un échec de lancement, affichée sur l'écran du PC. */
    public enum LaunchError {
        ROOF("toit détecté"),
        AIR("pas au sol");

        public final String label;

        LaunchError(String label) {
            this.label = label;
        }
    }

    public ExtractionTerminalItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    /** Toit ou pas au sol : calculé pareil côté client (écran) et serveur (pose). */
    @Nullable
    public static LaunchError launchError(Level level, LivingEntity entity) {
        if (!ExtractionTerminalEntity.isUnderOpenSky(level, entity.blockPosition())) {
            return LaunchError.ROOF;
        }
        if (!entity.onGround()) {
            return LaunchError.AIR;
        }
        return null;
    }

    public static String formatTime(int seconds) {
        return (seconds / 60) + ":" + String.format("%02d", seconds % 60);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return MAX_USE;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        RENDU_CLIENT.accept(consumer);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("PC bricolé relié à une caisse à fumigène.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Maintiens clic droit à ciel ouvert pour lancer l'extraction.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("La zone reste ouverte 5 min pour tous les joueurs.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("⚠ La fumée est visible de très loin...").withStyle(ChatFormatting.RED));
    }
}
