package fr.heralisz.utils.deco;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Liquide radioactif côté serveur (DecoBlocks.LIQUIDE_USINE) : le poison d'origine, une fois par seconde. */
public class LiquideRadioactifServeur extends LiquideRadioactif {
    public LiquideRadioactifServeur(BlockBehaviour.Properties props) {
        super(props);
    }

    @Override
    void empoisonner(Level level, Entity e) {
        if (!level.isClientSide && e instanceof LivingEntity le && level.getGameTime() % 20 == 0) {
            le.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 1));
        }
    }
}
