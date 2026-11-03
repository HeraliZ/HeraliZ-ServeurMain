package fr.heraliz.armory.item;

import fr.heraliz.armory.cible.CibleEssai;
import fr.heraliz.armory.cible.CibleEssaiServeur;
import fr.heraliz.armory.cible.CiblesServeur;
import fr.heraliz.armory.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * Partie SERVEUR de {@link CibleItem} (docs/METHODE_CLIENT_SERVEUR.md §5.3) : pose de la cible d'essai, si elle est
 * permise (CiblesServeur.posePermise : staff et créatif seulement, où ils veulent). Créée par l'usine
 * ModItems.CIBLE_USINE, branchée par la partie serveur.
 */
public class CibleItemServeur extends CibleItem {
    public CibleItemServeur(Properties properties) {
        super(properties);
    }

    @Override
    protected boolean poser(UseOnContext ctx, ServerLevel sl, BlockPos pos, ItemStack stack) {
        if (!(ctx.getPlayer() instanceof ServerPlayer joueur) || !CiblesServeur.posePermise(joueur, sl, pos)) return false;
        EntityType<CibleEssai> type = ModEntities.CIBLE.get();
        CibleEssai c = type.create(sl, stack.getTag(), EntityType.createDefaultStackConfig(sl, stack, joueur), pos,
                MobSpawnType.SPAWN_EGG, true, true);
        if (!(c instanceof CibleEssaiServeur cible)) return false;
        float lacet = (float) Mth.floor((Mth.wrapDegrees(ctx.getRotation() - 180.0F) + 22.5F) / 45.0F) * 45.0F;
        cible.moveTo(cible.getX(), cible.getY(), cible.getZ(), lacet, 0.0F);
        cible.setYBodyRot(lacet);
        cible.poseePar(joueur.getUUID());
        sl.addFreshEntity(cible);
        sl.playSound(null, cible.getX(), cible.getY(), cible.getZ(), SoundEvents.ARMOR_STAND_PLACE, SoundSource.BLOCKS, 0.75F, 0.8F);
        cible.gameEvent(GameEvent.ENTITY_PLACE, joueur);
        return true;
    }
}
