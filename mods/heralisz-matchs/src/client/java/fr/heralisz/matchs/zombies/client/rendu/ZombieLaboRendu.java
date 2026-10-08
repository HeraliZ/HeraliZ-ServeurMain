package fr.heralisz.matchs.zombies.client.rendu;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.zombies.client.modele.ZombieLaboModele;
import fr.heralisz.matchs.zombies.contenu.entite.ZombieLabo;
import fr.heralisz.matchs.zombies.jeu.api.Allure;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Rendu du zombie « labo » : 4 textures (blouse, hazmat, militaire, technicien), yeux lumineux maison dont l'eclat
 * monte avec la manche (et plus vif chez les sprinteurs), chute en avant a la mort, et remontee depuis le sol pendant
 * la sortie de terre.
 */
public class ZombieLaboRendu extends MobRenderer<ZombieLabo, ZombieLaboModele> {
    private static final String[] NOMS = {"blouse", "hazmat", "militaire", "technicien"};
    private static final ResourceLocation[] PEAUX = new ResourceLocation[NOMS.length];
    private static final RenderType[] YEUX = new RenderType[NOMS.length];

    static {
        for (int i = 0; i < NOMS.length; i++) {
            PEAUX[i] = HeraliszMatchs.id("textures/entity/zombies/labo_" + NOMS[i] + ".png");
            YEUX[i] = RenderType.eyes(HeraliszMatchs.id("textures/entity/zombies/labo_" + NOMS[i] + "_yeux.png"));
        }
    }

    public ZombieLaboRendu(EntityRendererProvider.Context ctx) {
        super(ctx, new ZombieLaboModele(ctx.bakeLayer(ZombieLaboModele.COUCHE)), 0.5F);
        this.addLayer(new Yeux(this));
    }

    @Override
    public ResourceLocation getTextureLocation(ZombieLabo z) {
        return PEAUX[z.variante()];
    }

    @Override
    public void render(ZombieLabo z, float lacet, float partiel, PoseStack ps, MultiBufferSource buf, int lumiere) {
        float co = z.conduit(partiel);
        if (co < 1) {
            // sortie d'une bouche d'aeration : recule dans le mur au depart, rampe vers la salle puis se releve
            float av = co < 0.6F ? -1.6F + 0.8F * (co / 0.6F) : -0.8F * (1 - doux((co - 0.6F) / 0.4F));
            float r = z.yBodyRot * Mth.DEG_TO_RAD;
            ps.pushPose();
            ps.translate(-Mth.sin(r) * av, 0, Mth.cos(r) * av);
            super.render(z, lacet, partiel, ps, buf, lumiere);
            ps.popPose();
            return;
        }
        float eg = z.egout(partiel);
        if (eg < 1) {
            // sortie d'une bouche d'egout : part de 1,9 bloc sous la plaque, se hisse par a-coups
            float k = 1 - doux(eg);
            ps.pushPose();
            ps.translate(0, -1.9F * k + 0.04F * Mth.sin(eg * 30F) * k, 0);
            super.render(z, lacet, partiel, ps, buf, lumiere);
            ps.popPose();
            return;
        }
        float so = z.sortie(partiel);
        if (so < 1) {
            ps.pushPose();
            // remonte du sol avec un leger roulis, comme s'il s'extirpait de la terre
            float k = 1 - so;
            ps.translate(0, -1.75F * k * k, 0);
            ps.mulPose(Axis.ZP.rotationDegrees(Mth.sin(so * 18F) * 6F * k));
            super.render(z, lacet, partiel, ps, buf, lumiere);
            ps.popPose();
            return;
        }
        super.render(z, lacet, partiel, ps, buf, lumiere);
    }

    @Override
    protected void setupRotations(ZombieLabo z, PoseStack ps, float age, float lacetCorps, float partiel) {
        if (z.deathTime > 0) {
            // chute en avant (et non sur le cote comme vanilla)
            ps.mulPose(Axis.YP.rotationDegrees(180.0F - lacetCorps));
            float f = ((float) z.deathTime + partiel - 1.0F) / 16.0F;
            f = Mth.sqrt(Math.min(f, 1.0F));
            ps.mulPose(Axis.XP.rotationDegrees(-f * 88.0F));
            return;
        }
        super.setupRotations(z, ps, age, lacetCorps, partiel);
        float co = z.conduit(partiel);
        if (co < 1) {
            // a plat ventre (pivot aux pieds) pendant la reptation, puis se redresse
            float couche = co < 0.6F ? 1 : 1 - doux((co - 0.6F) / 0.4F);
            ps.translate(0, 0.18F * couche, 0);
            ps.mulPose(Axis.XP.rotationDegrees(-84F * couche + (co < 0.6F ? Mth.sin(age * 0.9F) * 2.5F : 0)));
        }
    }

    private static float doux(float u) {
        u = Mth.clamp(u, 0, 1);
        return u * u * (3 - 2 * u);
    }

    @Override
    protected float getFlipDegrees(ZombieLabo z) {
        return 0.0F;
    }

    /** Yeux lumineux (texture d'emission propre a chaque variante). */
    private static final class Yeux extends RenderLayer<ZombieLabo, ZombieLaboModele> {
        Yeux(RenderLayerParent<ZombieLabo, ZombieLaboModele> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack ps, MultiBufferSource buf, int lumiere, ZombieLabo z, float marche, float ampl, float partiel,
                           float age, float lacetTete, float tangage) {
            if (z.deathTime > 6) return;
            float eclat = Mth.clamp(0.45F + z.manche() * 0.035F, 0.45F, 1.0F);
            if (z.allure() == Allure.SPRINTEUR) eclat = 1.0F;
            // leger scintillement
            eclat *= 0.85F + 0.15F * Mth.sin((age + z.phase * 20) * 0.3F);
            this.getParentModel().renderToBuffer(ps, buf.getBuffer(YEUX[z.variante()]), 0xF00000, OverlayTexture.NO_OVERLAY, eclat, eclat, eclat, 1.0F);
        }
    }
}
