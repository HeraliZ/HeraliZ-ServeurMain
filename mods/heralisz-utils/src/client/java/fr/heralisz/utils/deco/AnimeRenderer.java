package fr.heralisz.utils.deco;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Rendu des décors animés : le maillage du genre (version allégée au-delà de Genre.lod), tourné selon FACING, animé
 * par le temps du monde (aucun état, aucun tick) avec un décalage propre à chaque exemplaire pour qu'ils ne tournent
 * pas tous en même temps. Éclairé par le bloc devant lui (le moyeu du moulin est dans le toit).
 */
@OnlyIn(Dist.CLIENT)
public class AnimeRenderer implements BlockEntityRenderer<AnimeBlockEntity> {
    public AnimeRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(AnimeBlockEntity be, float partial, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) {
            return;
        }
        Genre g = be.genre();
        BlockPos p = be.getBlockPos();
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double d2 = cam.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
        if (d2 > (double) g.vue * g.vue) {
            return;
        }
        Maillage m = Maillage.get(d2 > (double) g.lod * g.lod ? g.maillage + "_loin" : g.maillage);
        Direction f = be.getBlockState().getValue(AnimeBlock.FACING);
        int lum = LevelRenderer.getLightColor(level, p.relative(f));
        float t = (level.getGameTime() % 72000L) + partial;
        float phase = (float) (Mth.getSeed(p) & 0xFFFFL) / 65536f * 360f;
        float vent = 0f;                                             // brise et rafales de la faille
        if (Vent.dansZone(p.getX(), p.getY(), p.getZ())) {
            double tv = level.getGameTime() + partial;
            vent = Math.min(1f, Vent.brise(tv) + Vent.rafale(p.getX(), p.getY(), p.getZ(), tv));
        }
        ps.pushPose();
        ps.translate(0.5, 0.5, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(180f - f.toYRot()));
        float nuit = level instanceof net.minecraft.client.multiplayer.ClientLevel cl
                ? Mth.clamp((1f - cl.getSkyDarken(partial)) / 0.8f, 0f, 1f) : 1f;
        m.dessiner(ps, buf, lum, t, phase, vent, nuit);
        ps.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(AnimeBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
}
