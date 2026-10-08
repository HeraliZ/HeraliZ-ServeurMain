package fr.heraliz.armory.item;

import fr.heraliz.armory.entity.Mannequin;
import fr.heraliz.armory.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * Partie SERVEUR de {@link MannequinItem} (docs/METHODE_CLIENT_SERVEUR.md §5.3) : la pose du mannequin dans le monde.
 * Creee par l'usine ModItems.MANNEQUIN_USINE, branchee par la partie serveur.
 */
public class MannequinItemServeur extends MannequinItem {
    public MannequinItemServeur(Properties properties) {
        super(properties);
    }

    @Override
    protected boolean poser(UseOnContext ctx, ServerLevel sl, BlockPos pos, ItemStack stack) {
        EntityType<Mannequin> type = ModEntities.MANNEQUIN.get();
        Mannequin m = type.create(sl, stack.getTag(), EntityType.createDefaultStackConfig(sl, stack, ctx.getPlayer()), pos,
                MobSpawnType.SPAWN_EGG, true, true);
        if (m == null) return false;
        float yaw = (float) Mth.floor((Mth.wrapDegrees(ctx.getRotation() - 180.0F) + 22.5F) / 45.0F) * 45.0F;
        m.moveTo(m.getX(), m.getY(), m.getZ(), yaw, 0.0F);
        sl.addFreshEntityWithPassengers(m);
        sl.playSound(null, m.getX(), m.getY(), m.getZ(), SoundEvents.ARMOR_STAND_PLACE, SoundSource.BLOCKS, 0.75F, 0.8F);
        m.gameEvent(GameEvent.ENTITY_PLACE, ctx.getPlayer());
        return true;
    }
}
