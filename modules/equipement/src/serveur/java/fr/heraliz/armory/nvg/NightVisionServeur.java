package fr.heraliz.armory.nvg;

import fr.heraliz.armory.HeralizArmory;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Lunettes de vision nocturne, cote serveur (repris tel quel de NightVision) : bascule demandee par la touche (paquet
 * ToggleNvg) ou /armory nvg, effet Vision nocturne entretenu tant qu'elles sont baissees, retire sinon.
 */
@Mod.EventBusSubscriber(modid = HeralizArmory.MOD_ID)
public final class NightVisionServeur {
    private NightVisionServeur() {}

    private static final String MARK = "heraliz_nvg";
    private static final int DURATION = 420;   // > 200 ticks : pas de clignotement de fin d'effet

    /** Dernier basculement par joueur (temps de jeu) : la touche ne peut pas etre spammee (son et animation). */
    private static final java.util.Map<java.util.UUID, Long> LAST = new java.util.HashMap<>();
    private static final long COOLDOWN = 10;

    public static void toggle(ServerPlayer player) {
        long now = player.level().getGameTime();
        Long last = LAST.get(player.getUUID());
        if (last != null && now - last >= 0 && now - last < COOLDOWN) return;
        LAST.put(player.getUUID(), now);
        if (LAST.size() > 256) LAST.values().removeIf(t -> now - t > COOLDOWN || now < t);
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!NightVision.hasNvg(head)) {
            player.displayClientMessage(Component.translatable("message.heraliz_armory.nvg.none"), true);
            return;
        }
        boolean down = !NightVision.isDown(head);
        head.getOrCreateTag().putBoolean(NightVision.TAG, down);
        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(),
                down ? SoundEvents.SPYGLASS_USE : SoundEvents.SPYGLASS_STOP_USING, SoundSource.PLAYERS, 0.6F, down ? 0.75F : 0.9F);
        update(player);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide || event.player.tickCount % 20 != 0) return;
        update(event.player);
    }

    private static void update(Player player) {
        boolean down = NightVision.isDown(player.getItemBySlot(EquipmentSlot.HEAD));
        var data = player.getPersistentData();
        if (down) {
            MobEffectInstance cur = player.getEffect(MobEffects.NIGHT_VISION);
            if (cur == null || cur.getDuration() < DURATION - 60) {
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, DURATION, 0, true, false, false));
            }
            data.putBoolean(MARK, true);
        } else if (data.getBoolean(MARK)) {
            MobEffectInstance cur = player.getEffect(MobEffects.NIGHT_VISION);
            // on ne retire que notre effet (ambiant, invisible, court), jamais celui d'une potion
            if (cur != null && cur.isAmbient() && !cur.isVisible() && cur.getDuration() <= DURATION) {
                player.removeEffect(MobEffects.NIGHT_VISION);
            }
            data.remove(MARK);
        }
    }
}
