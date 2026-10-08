package fr.heralisz.matchs.zombies.client.rendu;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.heralisz.matchs.zombies.contenu.objet.ObjetQuete;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Rendu des objets de quete ({@link ObjetQuete} : fusible, noyau de 115) par leur modele « zombies v3 » valide : base,
 * lueur (fil du fusible, coeur vert du noyau) et verre. Sert d'icone (HUD, inventaire) et en main (banc d'essai).
 */
public final class ObjetsRendu extends BlockEntityWithoutLevelRenderer {
    @Nullable
    private static ObjetsRendu instance;

    private ObjetsRendu() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    public static ObjetsRendu instance() {
        if (instance == null) instance = new ObjetsRendu();
        return instance;
    }

    @Override
    public void renderByItem(ItemStack s, ItemDisplayContext ctx, PoseStack ps, MultiBufferSource buf, int lumiere, int overlay) {
        if (!(s.getItem() instanceof ObjetQuete o)) return;
        ModeleZb m = ModelesZb.get(o.modele);
        if (m == null) return;
        ModelesZb.Tex tx = ModelesZb.tex(m.texture);
        ps.pushPose();
        // modele de 5 a 8 px de haut, centre bas : agrandi pour remplir l'icone
        ps.translate(0.5F, 0.12F, 0.5F);
        float e = "fusible".equals(o.modele) ? 2.6F : 1.8F;
        ps.scale(e, e, e);
        for (ModeleZb.Groupe g : m.groupes()) ModeleZb.dessiner(g, ModeleZb.BASE, ps, buf.getBuffer(tx.base()), lumiere, overlay, 0xFFFFFFFF);
        for (ModeleZb.Groupe g : m.groupes()) {
            if (g.a(ModeleZb.LUEUR)) ModeleZb.dessiner(g, ModeleZb.LUEUR, ps, buf.getBuffer(tx.lueur()), 0xF000F0, OverlayTexture.NO_OVERLAY, Geo.additif(0.85F, 0xFFFFFF));
        }
        for (ModeleZb.Groupe g : m.groupes()) {
            if (g.a(ModeleZb.VERRE)) ModeleZb.dessiner(g, ModeleZb.VERRE, ps, buf.getBuffer(tx.verre()), lumiere, overlay, 0xFFFFFFFF);
        }
        ps.popPose();
    }
}
