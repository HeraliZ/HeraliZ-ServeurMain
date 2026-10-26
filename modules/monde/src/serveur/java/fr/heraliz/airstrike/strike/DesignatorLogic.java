package fr.heraliz.airstrike.strike;

import fr.heraliz.airstrike.AirstrikeConfig;
import fr.heraliz.airstrike.item.CartridgeItem;
import fr.heraliz.airstrike.item.DesignatorItem;
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

/**
 * Server validation of a designation request coming from the binoculars.
 * <p>26/10 (demande du propriétaire) : une cartouche donne {@link CartridgeItem#UTILISATIONS} frappes ; la recharge entre
 * deux frappes d'un joueur est gardée dans ses données ({@link #CLE_RECHARGE}, heure en ms) : ni une déconnexion ni un
 * redémarrage ne l'effacent ; frappes refusées hors de la carte (spawn, monde du tutoriel, arènes) et dans les zones VIP.
 * Les frappes ne font aucun dégât aux bases ({@link AbriFrappes}).
 */
public final class DesignatorLogic {
    private DesignatorLogic() {
    }

    /** Heure (ms) avant laquelle le joueur ne peut pas appeler de nouvelle frappe (données persistantes du joueur). */
    public static final String CLE_RECHARGE = "airstrike_prochaine_frappe";

    private static net.minecraft.nbt.CompoundTag persiste(ServerPlayer p) {
        net.minecraft.nbt.CompoundTag pd = p.getPersistentData();
        if (!pd.contains(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG)) {
            pd.put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, new net.minecraft.nbt.CompoundTag());
        }
        return pd.getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
    }

    /** Temps (ms) avant la prochaine frappe de ce joueur ; jamais plus que la recharge réglée (horloge reculée). */
    public static long rechargeRestante(ServerPlayer p) {
        long max = AirstrikeConfig.COOLDOWN_SECONDS.get() * 1000L;
        return Math.max(0, Math.min(persiste(p).getLong(CLE_RECHARGE) - System.currentTimeMillis(), max));
    }

    /** À la connexion : la recharge vanilla des jumelles (affichage chez le joueur) suit la recharge gardée. */
    public static void connexion(ServerPlayer p) {
        long reste = rechargeRestante(p);
        if (reste > 0) {
            p.getCooldowns().addCooldown(fr.heraliz.airstrike.registry.ModItems.DESIGNATOR.get(), (int) Math.max(1, reste / 50));
        }
    }

    /** Raison qui interdit une frappe ici (clé de langue) ou null : seulement sur la carte, jamais dans une zone VIP. */
    @org.jetbrains.annotations.Nullable
    private static String zoneInterdite(ServerPlayer player, Vec3 target) {
        if (player.level().dimension() != net.minecraft.world.level.Level.OVERWORLD) return "msg.airstrike.zone_carte";
        if (player.getPersistentData().getBoolean("heralisz:en_match")) return "msg.airstrike.zone_carte";
        fr.heraliz.airstrike.helico.PointCrash.lireVip(player.server);
        if (fr.heraliz.airstrike.helico.PointCrash.dansZoneVip(target.x, target.z)
                || fr.heraliz.airstrike.helico.PointCrash.dansZoneVip(player.getX(), player.getZ())) return "msg.airstrike.zone_vip";
        return null;
    }

    private static void deny(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args).withStyle(ChatFormatting.RED), true);
        player.playNotifySound(ModSounds.RADIO_DENIED.get(), SoundSource.PLAYERS, 0.8f, 1.0f);
    }

    /** Counts the cartridges of the level of a strike carried by the player. */
    public static int countPayload(Inventory inv, StrikeType type) {
        return type.tier == null ? 0 : CartridgeItem.count(inv, type.tier);
    }

    private static ItemStack heldDesignator(ServerPlayer player) {
        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (!(held.getItem() instanceof DesignatorItem)) held = player.getItemInHand(InteractionHand.OFF_HAND);
        return held.getItem() instanceof DesignatorItem ? held : ItemStack.EMPTY;
    }

    /**
     * Loads a strike into the held binoculars. Same level as the cartridge in the bay: the cartridge is only
     * reprogrammed (nothing taken, short screen sequence). Otherwise one cartridge of the level leaves the inventory,
     * the one that was in the bay (unused) goes back to it, and everybody sees the reload animation.
     */
    public static void loadCartridge(ServerPlayer player, StrikeType type) {
        if (!type.selectable()) return;
        ItemStack held = heldDesignator(player);
        if (held.isEmpty()) return;
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        if (DesignatorItem.isReloading(held, now)) return;
        StrikeType current = DesignatorItem.cartridge(held);
        if (type == current) return;
        Component name = Component.translatable(type.translationKey()).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(type.uiColor)));
        if (current != null && current.tier == type.tier) {
            DesignatorItem.reprogram(held, type, now);
            level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), ModSounds.DESIGNATOR_BEEP.get(),
                    SoundSource.PLAYERS, 0.7f, 1.3f);
            player.displayClientMessage(Component.translatable("msg.airstrike.reprogrammed", name), true);
            return;
        }
        boolean creative = player.getAbilities().instabuild;
        Inventory inv = player.getInventory();
        Component tierName = Component.translatable(type.tier.translationKey());
        int coups = CartridgeItem.UTILISATIONS;
        if (!creative) {
            if (AirstrikeConfig.CONSUME_PAYLOAD.get()) {
                coups = CartridgeItem.consume(inv, type.tier);
                if (coups <= 0) {
                    deny(player, "msg.airstrike.no_cartridge_tier", tierName);
                    return;
                }
                if (current != null) {
                    // la cartouche qui sort de la baie garde ses frappes restantes (entamée : 1)
                    int reste = DesignatorItem.coups(held);
                    if (reste > 0) {
                        ItemStack back = CartridgeItem.avecReste(new ItemStack(ModItems.cartridgeFor(current)), reste);
                        if (!inv.add(back)) player.drop(back, false);
                    }
                }
            } else if (CartridgeItem.count(inv, type.tier) <= 0) {
                deny(player, "msg.airstrike.no_cartridge_tier", tierName);
                return;
            }
        }
        if (player.isUsingItem()) player.stopUsingItem();
        DesignatorItem.load(held, type, current, now, coups);
        level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), ModSounds.DESIGNATOR_RELOAD.get(),
                SoundSource.PLAYERS, 0.9f, 1.0f);
        player.displayClientMessage(Component.translatable("msg.airstrike.loaded", name), true);
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
        boolean creatif = player.getAbilities().instabuild;
        if (!creatif && (player.getCooldowns().isOnCooldown(held.getItem()) || rechargeRestante(player) > 0)) {
            deny(player, "msg.airstrike.cooldown");
            return;
        }
        String zone = zoneInterdite(player, target);
        if (zone != null && !creatif) {
            deny(player, zone);
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
        if (!creative && cooldown > 0) {
            player.getCooldowns().addCooldown(held.getItem(), cooldown);
            persiste(player).putLong(CLE_RECHARGE, System.currentTimeMillis() + cooldown * 50L);
        }

        long eta = Math.max(1, Math.round((strike.startTick + strike.firstImpactTime() - level.getGameTime()) / 20.0));
        player.displayClientMessage(Component.translatable("msg.airstrike.confirmed",
                Component.translatable(type.translationKey()).withStyle(ChatFormatting.WHITE), eta).withStyle(ChatFormatting.GREEN), false);
        player.playNotifySound(ModSounds.RADIO_CONFIRM.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
    }
}
