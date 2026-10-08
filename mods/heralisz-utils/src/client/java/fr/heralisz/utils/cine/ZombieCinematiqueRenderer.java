package fr.heralisz.utils.cine;

import fr.heralisz.utils.HeraliszUtils;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Rendu du zombie de la cinématique : modèle du zombie, texture balafrée et ensanglantée. */
@OnlyIn(Dist.CLIENT)
public class ZombieCinematiqueRenderer extends ZombieRenderer {
    private static final ResourceLocation TEX = new ResourceLocation(HeraliszUtils.MODID, "textures/entity/zombie_cinematique.png");

    public ZombieCinematiqueRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public ResourceLocation getTextureLocation(Zombie z) {
        return TEX;
    }

    /** Sur le plateau de la cinématique (en plein ciel), la lumière est celle de la scène, pas celle du monde. */
    @Override
    protected int getBlockLightLevel(Zombie z, net.minecraft.core.BlockPos p) {
        int l = Cinematique.lumiereActeurs;
        return l >= 0 ? l : super.getBlockLightLevel(z, p);
    }

    @Override
    protected int getSkyLightLevel(Zombie z, net.minecraft.core.BlockPos p) {
        return Cinematique.lumiereActeurs >= 0 ? 0 : super.getSkyLightLevel(z, p);
    }
}
