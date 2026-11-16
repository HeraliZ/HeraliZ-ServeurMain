package fr.heraliz.decors.bloc;

/* Repris du jar Décors 1.30.3 de la nouvelle carte (décompilé le 08/11, sources d'origine absentes du dépôt). */

import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public class LiquideToxique extends Deco {
    private static final Vec3 ENGLUE = new Vec3(0.55, 0.7, 0.55);

    public LiquideToxique(Properties p, Function<BlockState, VoxelShape> forme) {
        super(p, forme, true, Effet.AUCUN);
    }

    @Override
    public void entityInside(BlockState s, Level l, BlockPos p, Entity e) {
        e.makeStuckInBlock(s, ENGLUE);
        if (!l.isClientSide && e instanceof LivingEntity v && !v.hasEffect(MobEffects.POISON) && l.random.nextInt(20) == 0) {
            v.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0));
        }
    }
}
