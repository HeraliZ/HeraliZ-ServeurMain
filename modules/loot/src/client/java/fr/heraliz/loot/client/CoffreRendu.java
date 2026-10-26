package fr.heraliz.loot.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heraliz.loot.CoffreBlock;
import fr.heraliz.loot.CoffreBlockEntity;
import fr.heraliz.loot.HeralizLoot;
import fr.heraliz.loot.client.rendu.GlowFilter;
import fr.heraliz.loot.client.rendu.MeshRender;
import fr.heraliz.loot.client.rendu.RenduTypes;
import fr.heraliz.loot.client.rendu.coffres.AtlasT7;
import fr.heraliz.loot.client.rendu.coffres.DesignCoffre;
import fr.heraliz.loot.client.rendu.coffres.Designs;
import fr.heraliz.loot.client.rendu.mesh.Mesh;
import fr.heraliz.loot.client.rendu.scene.DrawList;
import fr.heraliz.loot.client.rendu.scene.Pose;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Rendu en jeu des coffres T7 : le modèle (client/rendu/coffres) remplit une liste de dessin selon le moment de
 * l'animation, puis on dessine les maillages opaques (éclairés par le monde), le verre, et le calque lumineux (voyants,
 * bandeaux, noyaux) en pleine lumière. Moteur de rendu repris du casino.
 */
public class CoffreRendu implements BlockEntityRenderer<CoffreBlockEntity> {
    public static final ResourceLocation TEXTURE = new ResourceLocation(HeralizLoot.MODID, "textures/coffre/" + AtlasT7.TEXTURE + ".png");
    public static final ResourceLocation GLOW = new ResourceLocation(HeralizLoot.MODID, "textures/coffre/" + AtlasT7.TEXTURE + "_glow.png");
    private static final java.util.Set<String> ECHECS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private final DrawList liste = new DrawList();
    private final Pose pose = new Pose();

    public CoffreRendu(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public boolean shouldRenderOffScreen(CoffreBlockEntity be) {
        return false;                                               // la boîte de rendu de l'entité couvre déjà le faisceau
    }

    @Override
    public int getViewDistance() {
        return 48;
    }

    @Override
    public void render(CoffreBlockEntity be, float partiel, PoseStack ps, MultiBufferSource buf, int lum, int overlay) {
        if (be.getLevel() == null) return;
        DesignCoffre d = Designs.get(be.type().nom);
        if (d == null) return;
        long now = be.getLevel().getGameTime();
        float t = be.tempsAnimation(now, partiel);
        float yRot = -be.getBlockState().getValue(CoffreBlock.FACING).toYRot();
        liste.clear();
        try {
            int etat = be.etat();
            if (etat == CoffreBlockEntity.APPARITION || etat == CoffreBlockEntity.DISPARITION) {
                d.dessinerApparition(be.avancement(now, partiel), now + partiel, etat == CoffreBlockEntity.DISPARITION, pose.reset(), liste);
            } else {
                d.dessiner(t, now + partiel, etat == CoffreBlockEntity.FERME, pose.reset(), liste);
            }
        } catch (RuntimeException e) {
            if (ECHECS.add(d.nom)) HeralizLoot.LOG.error("Rendu du coffre {} impossible", d.nom, e);
            return;
        }
        Matrix4f modele = new Matrix4f().translation(0.5f, 0, 0.5f).rotateY((float) Math.toRadians(yRot)).translate(-0.5f, 0, -0.5f).scale(1f / 16f);
        dessiner(liste, ps, buf, modele, lum, overlay);
    }

    /** Dessine une liste (repère du modèle en pixels, passé dans la pose par "modele"). */
    public static void dessiner(DrawList liste, PoseStack ps, MultiBufferSource buf, Matrix4f modele, int lum, int overlay) {
        Matrix4f m4 = new Matrix4f();
        Matrix3f n3 = new Matrix3f(), tmp = new Matrix3f();
        VertexConsumer vc = null;
        for (int i = 0; i < liste.size(); i++) {
            DrawList.Op o = liste.get(i);
            if (o.type != DrawList.SOLID) continue;
            if (vc == null) vc = buf.getBuffer(RenduTypes.solid(TEXTURE));
            matrices(o, ps, modele, m4, n3, tmp);
            MeshRender.render(o.mesh, m4, n3, vc, o.r, o.g, o.b, 1f, lum, overlay, true);
        }
        vc = null;
        for (int i = 0; i < liste.size(); i++) {
            DrawList.Op o = liste.get(i);
            if (o.type != DrawList.TRANSLUCENT) continue;
            if (vc == null) vc = buf.getBuffer(RenduTypes.translucent(TEXTURE));
            matrices(o, ps, modele, m4, n3, tmp);
            MeshRender.render(o.mesh, m4, n3, vc, o.r, o.g, o.b, o.a, lum, overlay, true);
        }
        vc = null;
        for (int i = 0; i < liste.size(); i++) {
            DrawList.Op o = liste.get(i);
            if (o.type == DrawList.SOLID && o.glow > 0.003f) {
                Mesh lit = GlowFilter.glowOnly(o.mesh, GLOW);
                if (lit.isEmpty()) continue;
                if (vc == null) vc = buf.getBuffer(RenduTypes.glow(GLOW));
                float k = Math.min(1f, o.glow);
                matrices(o, ps, modele, m4, n3, tmp);
                MeshRender.render(lit, m4, n3, vc, k, k, k, 1f, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, false);
            } else if (o.type == DrawList.ADDITIVE) {
                if (vc == null) vc = buf.getBuffer(RenduTypes.glow(GLOW));
                matrices(o, ps, modele, m4, n3, tmp);
                MeshRender.render(o.mesh, m4, n3, vc, o.r, o.g, o.b, o.a, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, true);
            }
        }
    }

    private static void matrices(DrawList.Op o, PoseStack ps, Matrix4f modele, Matrix4f m4, Matrix3f n3, Matrix3f tmp) {
        m4.set(ps.last().pose()).mul(modele).mul(o.m);
        n3.set(ps.last().normal()).mul(modele.normal(tmp));
        n3.mul(o.m.normal(tmp));
    }
}
