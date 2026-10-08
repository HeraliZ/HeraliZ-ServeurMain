package fr.heralisz.matchs.zombies.client.rendu;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.zombies.contenu.bloc.AtoutBE;
import fr.heralisz.matchs.zombies.contenu.bloc.BlocAtout;
import fr.heralisz.matchs.zombies.jeu.api.TypeAtout;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Machine d'atout facon CoD (modele {@code machine_atout} genere par {@code tools/matchs/zombies/gen_atouts.py}, une
 * texture par atout {@code machine_atout_<id>}) : caisse coloree, disque au logo en haut, enseigne au nom, vitrine de
 * bouteilles, distributeur. Courant coupe : machine terne, aucune lueur. Mise sous tension : les neons crepitent
 * 1,5 s (clignotements irreguliers) puis tiennent, le logo s'allume ensuite, la vitrine s'eclaire ; en regime : neons
 * qui respirent, petit gresillement de temps en temps, halo de la couleur de l'atout derriere le logo et au sol.
 * Achat : eclat bref de toute la machine. Repere : origine au centre bas de la case, +z vers la salle.
 */
public class AtoutRendu implements BlockEntityRenderer<AtoutBE> {
    private static final RenderType HALO = ZbTypesRendu.lueur(HeraliszMatchs.id("textures/entity/zombies/halo.png"));
    private static final float[] PLEIN = {0, 0, 1, 1};
    private static final ModelesZb.Tex[] TEX = new ModelesZb.Tex[TypeAtout.values().length];

    public AtoutRendu(BlockEntityRendererProvider.Context ctx) {
    }

    private static ModelesZb.Tex tex(TypeAtout t) {
        ModelesZb.Tex x = TEX[t.ordinal()];
        if (x == null) {
            x = ModelesZb.tex(new ResourceLocation(HeraliszMatchs.MODID, "textures/entity/zombies/v2/machine_atout_" + t.id() + ".png"));
            TEX[t.ordinal()] = x;
        }
        return x;
    }

    @Override
    public void render(AtoutBE be, float partiel, PoseStack ps, MultiBufferSource buf, int lumiere, int overlay) {
        ModeleZb m = ModelesZb.get("machine_atout");
        if (m == null) return;
        Direction f = be.getBlockState().hasProperty(BlocAtout.FACING) ? be.getBlockState().getValue(BlocAtout.FACING) : Direction.NORTH;
        TypeAtout type = be.type();
        ModelesZb.Tex tx = tex(type);
        float age = (be.getLevel() == null ? 0 : be.getLevel().getGameTime() % 24000L) + partiel;
        float t = be.ecoule(partiel);
        // intensites : neons (et lettres de l'enseigne), logo, vitrine
        float neon = 0, logo = 0, vitrine = 0;
        if (be.alimente()) {
            if (t < 30) {
                // crepitement d'allumage : creneaux irreguliers de plus en plus longs
                float s = Mth.sin(t * 1.9F) * Mth.sin(t * 0.71F + 1.3F) + t / 26F;
                neon = s > 0.3F ? Math.min(1, 0.45F + t / 30F) : 0.06F;
                logo = t > 14 ? (s > 0.15F ? Math.min(1, (t - 14) / 10F) : 0.2F) : 0;
                vitrine = Mth.clamp((t - 8) / 18F, 0, 1);
            } else {
                // regime : respiration lente + gresillement bref (environ toutes les 9 s, propre a chaque machine)
                long graine = be.getBlockPos().asLong();
                int cycle = (int) ((age + (graine & 255)) % 180);
                float gresille = cycle < 6 && (cycle % 2 == 0) ? 0.45F : 0;
                neon = 0.86F + 0.14F * Mth.sin(age * 0.07F) - gresille;
                logo = 0.92F + 0.08F * Mth.sin(age * 0.045F);
                vitrine = 0.88F;
            }
            float ds = be.depuisServi(partiel);
            if (ds >= 0 && ds < 24) {
                float k = 1 - ds / 24F;
                neon = Math.min(1, neon + 0.6F * k);
                logo = Math.min(1, logo + 0.5F * k);
                vitrine = Math.min(1, vitrine + 0.5F * k);
            }
        }
        ps.pushPose();
        ps.translate(0.5F, 0, 0.5F);
        ps.mulPose(Axis.YP.rotationDegrees(-f.toYRot()));
        int teinte = be.alimente() ? 0xFFFFFFFF : 0xFFB4B4B4;
        VertexConsumer vb = buf.getBuffer(tx.base());
        for (ModeleZb.Groupe g : m.groupes()) ModeleZb.dessiner(g, ModeleZb.BASE, ps, vb, lumiere, overlay, teinte);
        ModeleZb.dessiner(m.groupe("verre"), ModeleZb.VERRE, ps, buf.getBuffer(tx.verre()), lumiere, overlay, 0xFFFFFFFF);
        if (neon > 0.01F || logo > 0.01F || vitrine > 0.01F) {
            VertexConsumer vl = buf.getBuffer(tx.lueur());
            int plein = 0xF000F0;
            ModeleZb.dessiner(m.groupe("neons"), ModeleZb.LUEUR, ps, vl, plein, OverlayTexture.NO_OVERLAY, Geo.additif(neon, 0xFFFFFF));
            ModeleZb.dessiner(m.groupe("corps"), ModeleZb.LUEUR, ps, vl, plein, OverlayTexture.NO_OVERLAY, Geo.additif(neon, 0xFFFFFF));
            ModeleZb.dessiner(m.groupe("logo"), ModeleZb.LUEUR, ps, vl, plein, OverlayTexture.NO_OVERLAY, Geo.additif(logo, 0xFFFFFF));
            ModeleZb.dessiner(m.groupe("vitrine"), ModeleZb.LUEUR, ps, vl, plein, OverlayTexture.NO_OVERLAY, Geo.additif(vitrine, 0xFFFFFF));
            ModeleZb.dessiner(m.groupe("verre"), ModeleZb.LUEUR, ps, vl, plein, OverlayTexture.NO_OVERLAY, Geo.additif(vitrine, 0xFFFFFF));
            VertexConsumer vh = buf.getBuffer(HALO);
            // aureole derriere le disque du logo (lisible de loin) et flaque de lumiere au sol devant la machine
            ps.pushPose();
            ps.translate(0, 31.5F / 16F, 6.04F / 16F);
            Geo.plan(ps, vh, -0.78F, -0.78F, 0.78F, 0.78F, PLEIN, plein, OverlayTexture.NO_OVERLAY, Geo.additif(0.55F * logo, type.couleur));
            ps.popPose();
            ps.pushPose();
            ps.translate(0, 0.02F, 0.78F);
            ps.mulPose(Axis.XP.rotationDegrees(-90));
            Geo.plan(ps, vh, -0.95F, -0.6F, 0.95F, 0.6F, PLEIN, plein, OverlayTexture.NO_OVERLAY, Geo.additif(0.32F * Math.max(neon, vitrine), type.couleur));
            ps.popPose();
        }
        ps.popPose();
    }

    @Override
    public int getViewDistance() {
        return 80;
    }
}
