package fr.heraliz.airstrike.strike;

import fr.heraliz.airstrike.AirstrikeConfig;
import fr.heraliz.airstrike.item.DesignatorItem;
import fr.heraliz.airstrike.item.PayloadItem;
import fr.heraliz.airstrike.registry.ModItems;
import fr.heraliz.airstrike.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Server validation of a designation request coming from the binoculars. */
public final class DesignatorLogic {
    private DesignatorLogic() {
    }

    private static void deny(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args).withStyle(ChatFormatting.RED), true);
        player.playNotifySound(ModSounds.RADIO_DENIED.get(), SoundSource.PLAYERS, 0.8f, 1.0f);
    }

    /** Counts the payload items of a type carried by the player. */
    public static int countPayload(Inventory inv, StrikeType type) {
        return PayloadItem.countPayload(inv, type);
    }

    private static boolean consumePayload(Inventory inv, StrikeType type) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.getItem() instanceof PayloadItem p && p.getStrikeType() == type) {
                s.shrink(1);
                return true;
            }
        }
        return false;
    }

    private static ItemStack heldDesignator(ServerPlayer player) {
        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (!(held.getItem() instanceof DesignatorItem)) held = player.getItemInHand(InteractionHand.OFF_HAND);
        return held.getItem() instanceof DesignatorItem ? held : ItemStack.EMPTY;
    }

    /**
     * Loads a cartridge into the held binoculars: one payload item leaves the inventory, the cartridge that was in
     * the bay (unused) goes back to it. Plays the reload animation for everybody.
     */
    public static void loadCartridge(ServerPlayer player, StrikeType type) {
        ItemStack held = heldDesignator(player);
        if (held.isEmpty()) return;
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        if (DesignatorItem.isReloading(held, now)) return;
        StrikeType current = DesignatorItem.cartridge(held);
        if (type == current) return;
        boolean creative = player.getAbilities().instabuild;
        Inventory inv = player.getInventory();
        if (!creative) {
            if (AirstrikeConfig.CONSUME_PAYLOAD.get()) {
                if (!consumePayload(inv, type)) {
                    deny(player, "msg.airstrike.no_payload", Component.translatable(type.translationKey()));
                    return;
                }
                if (current != null) {
                    ItemStack back = new ItemStack(ModItems.payload(current));
                    if (!inv.add(back)) player.drop(back, false);
                }
            } else if (countPayload(inv, type) <= 0) {
                deny(player, "msg.airstrike.no_payload", Component.translatable(type.translationKey()));
                return;
            }
        }
        if (player.isUsingItem()) player.stopUsingItem();
        DesignatorItem.load(held, type, current, now);
        level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), ModSounds.DESIGNATOR_RELOAD.get(),
                SoundSource.PLAYERS, 0.9f, 1.0f);
        player.displayClientMessage(Component.translatable("msg.airstrike.loaded",
                Component.translatable(type.translationKey()).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(type.uiColor)))), true);
    }

    public static void designate(ServerPlayer player, Vec3 target) {
        ItemStack held = heldDesignator(player);
        if (held.isEmpty()) return;
        StrikeType type = DesignatorItem.cartridge(held);
        if (type == null) {
            deny(player, "msg.airstrike.no_cartridge");
            return;
        }
        if (DesignatorItem.isReloading(held, player.serverLevel().getGameTime())) return;
        if (player.getCooldowns().isOnCooldown(held.getItem())) {
            deny(player, "msg.airstrike.cooldown");
            return;
        }
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        double dist = eye.distanceTo(target);
        int maxRange = AirstrikeConfig.MAX_RANGE.get();
        if (dist > maxRange + 4 || !level.isLoaded(BlockPos.containing(target))) {
            deny(player, "msg.airstrike.out_of_range", maxRange);
            return;
        }
        Vec3 dir = target.subtract(eye).normalize();
        if (player.getViewVector(1.0f).dot(dir) < 0.9) return;
        BlockHitResult hit = level.clip(new ClipContext(eye, target.add(dir.scale(0.75)), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.ANY, player));
        if (hit.getType() == HitResult.Type.BLOCK && hit.getLocation().distanceTo(target) > 4.0) {
            deny(player, "msg.airstrike.no_line_of_sight");
            return;
        }
        if (StrikeManager.countActive(player.getUUID()) >= AirstrikeConfig.MAX_ACTIVE_PER_PLAYER.get()) {
            deny(player, "msg.airstrike.already_active");
            return;
        }
        if (StrikeManager.countActive(null) >= AirstrikeConfig.MAX_ACTIVE_TOTAL.get()) {
            deny(player, "msg.airstrike.airspace_busy");
            return;
        }
        boolean creative = player.getAbilities().instabuild;
        // the cartridge in the bay is used up by the call
        if (!creative && AirstrikeConfig.CONSUME_PAYLOAD.get()) DesignatorItem.spend(held);
        Strike strike = StrikeManager.launch(level, type, target, player.position(), player);
        int cooldown = AirstrikeConfig.COOLDOWN_SECONDS.get() * 20;
        if (!creative && cooldown > 0) player.getCooldowns().addCooldown(held.getItem(), cooldown);

        long eta = Math.max(1, Math.round((strike.startTick + strike.firstImpactTime() - level.getGameTime()) / 20.0));
        player.displayClientMessage(Component.translatable("msg.airstrike.confirmed",
                Component.translatable(type.translationKey()).withStyle(ChatFormatting.WHITE), eta).withStyle(ChatFormatting.GREEN), false);
        player.playNotifySound(ModSounds.RADIO_CONFIRM.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
    }
}
