package fr.heralisz.clans.territory.client;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.heralisz.clans.territory.CaisseRavitaillementBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Rendu de la caisse posee : maillage caisse_sol (caisse + parachute degonfle etale) pendant 30 s apres
 * l'atterrissage, puis caisse_seule. Origine du maillage = centre-bas de la caisse.
 */
@OnlyIn(Dist.CLIENT)
public class CaisseRenderer implements BlockEntityRenderer<CaisseRavitaillementBlockEntity> {
    public CaisseRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(CaisseRavitaillementBlockEntity be, float partialTick, PoseStack ps, MultiBufferSource buf, int packedLight, int packedOverlay) {
        Level level = be.getLevel();
        float t = level == null ? partialTick : (float) (level.getGameTime() % 200000L) + partialTick;
        ps.pushPose();
        ps.translate(0.5, 0, 0.5);
        Maillage.get(be.parachuteAuSol() ? "caisse_sol" : "caisse_seule").dessiner(ps, buf, packedLight, t, 0);
        ps.popPose();
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
