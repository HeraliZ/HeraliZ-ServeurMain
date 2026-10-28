package fr.heraliz.airstrike.item;

import fr.heraliz.airstrike.pont.Crochet;
import fr.heraliz.airstrike.registry.ModSounds;
import fr.heraliz.airstrike.strike.StrikeType;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
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
 * Laser designator binoculars. Hold right click to look through them, left click to mark the target.
 * A strike cartridge is loaded into the bay on top of the laser module (key R, held: wheel of the strikes): the loaded
 * strike and the reload animation live in the stack NBT so that every player sees the hatch open. Choosing another
 * strike of the same level only reprograms the cartridge already in the bay. The zoom, HUD and designation input live
 * in {@code client.DesignatorClient}.
 */
public class DesignatorItem extends Item {
    /** Length of the reload animation (hatch opens, cartridge swapped, hatch closes). */
    public static final int RELOAD_TICKS = 46;
    private static final String TAG_CARTRIDGE = "Cartridge";
    private static final String TAG_EJECTED = "Ejected";
    private static final String TAG_RELOAD = "ReloadAt";
    private static final String TAG_REPROGRAM = "ReprogAt";
    /** Frappes qui restent à la cartouche dans la baie (26/10 ; absent sur une cartouche chargée avant : 1). */
    private static final String TAG_COUPS = "Coups";
    /** Length of the screen sequence when the cartridge in the bay is reprogrammed for another strike of its level. */
    public static final int REPROGRAM_TICKS = 12;

    /** Client only: blocks raising the binoculars until the server confirms a reload that was just requested. */
    public static long clientReloadBlock = Long.MIN_VALUE;

    public DesignatorItem(Properties properties) {
        super(properties);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Cartridge state

    @Nullable
    public static StrikeType cartridge(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null || !tag.contains(TAG_CARTRIDGE) ? null : StrikeType.byName(tag.getString(TAG_CARTRIDGE));
    }

    /** Cartridge that was taken out by the last reload (animation only). */
    @Nullable
    public static StrikeType ejected(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null || !tag.contains(TAG_EJECTED) ? null : StrikeType.byName(tag.getString(TAG_EJECTED));
    }

    /** 0..1 progress of the reload animation, 1 when idle. */
    public static float reloadProgress(ItemStack stack, double gameTime) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_RELOAD)) return 1f;
        double t = (gameTime - tag.getLong(TAG_RELOAD)) / RELOAD_TICKS;
        return t < 0 || t > 1 ? 1f : (float) t;
    }

    public static boolean isReloading(ItemStack stack, long gameTime) {
        return reloadProgress(stack, gameTime) < 1f;
    }

    /**
     * Heure (temps du monde du serveur) où la dernière recharge a commencé, ou {@link Long#MIN_VALUE} : repère de
     * l'horloge de l'animation chez le joueur ({@code client.render.HorlogeJumelles}).
     */
    public static long reloadStart(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null || !tag.contains(TAG_RELOAD) ? Long.MIN_VALUE : tag.getLong(TAG_RELOAD);
    }

    /** Heure (temps du monde du serveur) de la dernière reprogrammation, ou {@link Long#MIN_VALUE}. */
    public static long reprogramStart(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null || !tag.contains(TAG_REPROGRAM) ? Long.MIN_VALUE : tag.getLong(TAG_REPROGRAM);
    }

    /** Frappes qui restent à la cartouche de la baie (0 : vide). */
    public static int coups(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_CARTRIDGE)) return 0;
        return tag.contains(TAG_COUPS) ? Math.max(0, tag.getInt(TAG_COUPS)) : 1;
    }

    /** Puts a cartridge in (the previous one, if any, is animated out) ; {@code coups} : frappes qu'elle donne. */
    public static void load(ItemStack stack, StrikeType type, @Nullable StrikeType previous, long gameTime, int coups) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(TAG_CARTRIDGE, type.id);
        tag.putInt(TAG_COUPS, Math.max(1, coups));
        if (previous != null) tag.putString(TAG_EJECTED, previous.id);
        else tag.remove(TAG_EJECTED);
        tag.putLong(TAG_RELOAD, gameTime);
        tag.remove(TAG_REPROGRAM);
    }

    /** The cartridge in the bay is set to another strike of its level (no reload, a short screen sequence). */
    public static void reprogram(ItemStack stack, StrikeType type, long gameTime) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(TAG_CARTRIDGE, type.id);
        tag.putLong(TAG_REPROGRAM, gameTime);
    }

    /** 0..1 progress of the reprogramming screen sequence, 1 when idle. */
    public static float reprogramProgress(ItemStack stack, double gameTime) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_REPROGRAM)) return 1f;
        double t = (gameTime - tag.getLong(TAG_REPROGRAM)) / REPROGRAM_TICKS;
        return t < 0 || t > 1 ? 1f : (float) t;
    }

    /** Une frappe est partie : la cartouche de la baie perd une utilisation ; vide, elle sort de la baie. */
    public static void spend(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return;
        int reste = coups(stack) - 1;
        if (reste > 0) {
            tag.putInt(TAG_COUPS, reste);
            return;
        }
        tag.remove(TAG_CARTRIDGE);
        tag.remove(TAG_EJECTED);
        tag.remove(TAG_COUPS);
    }

    // ------------------------------------------------------------------------------------------------------------

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        long now = level.getGameTime();
        if (isReloading(stack, now) || (level.isClientSide && now < clientReloadBlock)) return InteractionResultHolder.fail(stack);
        player.startUsingItem(hand);
        level.playSound(player, player.getX(), player.getY(), player.getZ(), ModSounds.DESIGNATOR_RAISE.get(),
                SoundSource.PLAYERS, 0.6f, 1.0f);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        StrikeType loaded = cartridge(stack);
        if (loaded != null) {
            tooltip.add(Component.translatable("tooltip.airstrike.designator.loaded",
                    Component.translatable(loaded.translationKey()).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(loaded.uiColor)))));
            tooltip.add(Component.translatable("tooltip.airstrike.designator.uses", coups(stack)).withStyle(ChatFormatting.GOLD));
            if (loaded.tier != null) {
                tooltip.add(Component.translatable("tooltip.airstrike.designator.tier",
                        Component.translatable(loaded.tier.translationKey()).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(loaded.tier.color)))));
            }
        } else {
            tooltip.add(Component.translatable("tooltip.airstrike.designator.empty").withStyle(ChatFormatting.GOLD));
        }
        tooltip.add(Component.translatable("tooltip.airstrike.designator.line1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.airstrike.designator.line2").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.airstrike.designator.line3").withStyle(ChatFormatting.GRAY));
    }

    /** First / third person rendering of the binoculars (client.DesignatorItemExtensions), plugged in by PartieClient. */
    public static final Crochet<Consumer<IClientItemExtensions>> RENDU_CLIENT = new Crochet<>();

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        RENDU_CLIENT.accept(consumer);
    }
}
