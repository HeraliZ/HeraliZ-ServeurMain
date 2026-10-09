package com.heraliz.extraction.serveur;

import com.heraliz.extraction.ExtractionConfig;
import com.heraliz.extraction.ModRegistry;
import com.heraliz.extraction.TerminalGeometry;
import com.heraliz.extraction.entity.ExtractionTerminalEntity;
import com.heraliz.extraction.item.ExtractionTerminalItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Partie serveur du terminal d'extraction : bruit de clavier, puis pose du terminal au bout de la saisie. */
public class ExtractionTerminalItemServeur extends ExtractionTerminalItem {
    public ExtractionTerminalItemServeur(Properties properties) {
        super(properties);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
        int used = getUseDuration(stack) - remainingUseDuration;
        // bruit de clavier
        if (!level.isClientSide && used % 3 == 0) {
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.WOODEN_BUTTON_CLICK_ON,
                    SoundSource.PLAYERS, 0.35F, 1.7F + level.random.nextFloat() * 0.4F);
        }
        // séquence terminée : le serveur pose le terminal et arrête la saisie (le client suit via la synchro).
        // En cas d'erreur, rien : le joueur continue de taper et l'écran enchaîne les refus.
        if (!level.isClientSide && entity instanceof ServerPlayer player
                && used >= TerminalGeometry.TYPING_TICKS && launchError(level, player) == null) {
            placeTerminal(stack, player);
            player.stopUsingItem();
        }
    }

    private void placeTerminal(ItemStack stack, ServerPlayer player) {
        ServerLevel serverLevel = player.serverLevel();
        Vec3 pos = findPlacement(serverLevel, player);
        ExtractionTerminalEntityServeur terminal = (ExtractionTerminalEntityServeur) ModRegistry.EXTRACTION_TERMINAL_ENTITY.get().create(serverLevel);
        terminal.setup(player, ExtractionConfig.durationTicks(), ExtractionConfig.zoneLifetimeTicks(),
                ExtractionConfig.RADIUS.get().floatValue());
        terminal.moveTo(pos.x, pos.y, pos.z, player.getYRot(), 0.0F);
        serverLevel.addFreshEntity(terminal);

        serverLevel.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ARMOR_EQUIP_IRON, SoundSource.PLAYERS, 1.0F, 0.7F);

        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        player.getCooldowns().addCooldown(this, 40);
    }

    /** Le terminal est posé un peu devant le joueur, sinon à ses pieds s'il y a un obstacle. */
    private static Vec3 findPlacement(ServerLevel level, ServerPlayer player) {
        Vec3 feet = player.position();
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0D, look.z);
        if (flat.lengthSqr() < 1.0E-4D) {
            return feet;
        }
        Vec3 target = feet.add(flat.normalize().scale(1.2D));
        BlockPos at = BlockPos.containing(target);
        boolean blocked = !level.getBlockState(at).getCollisionShape(level, at).isEmpty();
        boolean noFloor = level.getBlockState(at.below()).getCollisionShape(level, at.below()).isEmpty();
        Vec3 from = feet.add(0.0D, 0.5D, 0.0D);
        boolean hidden = level.clip(new ClipContext(from, target.add(0.0D, 0.5D, 0.0D),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() != HitResult.Type.MISS;
        if (blocked || noFloor || hidden || !ExtractionTerminalEntity.isUnderOpenSky(level, at)) {
            return feet;
        }
        return new Vec3(target.x, feet.y, target.z);
    }
}
