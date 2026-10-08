package fr.heralisz.matchs.zombies.client.rendu;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.zombies.contenu.bloc.BlocObjetQuete;
import fr.heralisz.matchs.zombies.contenu.bloc.ObjetQueteBE;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Quaternionf;

import javax.annotation.Nullable;

/**
 * Objets de quete de The Giant ({@link ObjetQueteBE}) : fusible dans sa cachette (modele valide {@code fusible.json}),
 * boitier du Fly Trap (interrupteur des easter eggs), singe et nounours ({@code peluche.json}, le singe en brun sombre en
 * attendant son modele), Annihilator du fourneau (la vraie arme TaCZ), carte d'acces de Maxis. Au repos : un petit eclat
 * dore tourne vers la camera ; actif (a tirer, a prendre) : il flotte en tournant et brille ; pris : il monte et se
 * dissout en 0,5 s. Rien au-dela de 32 blocs.
 */
public class ObjetsQueteRendu implements BlockEntityRenderer<ObjetQueteBE> {
    private static final RenderType HALO = ZbTypesRendu.lueur(HeraliszMatchs.id("textures/entity/zombies/halo.png"));
    private static final float[] PLEIN = {0, 0, 1, 1};
    private static final Quaternionf Q = new Quaternionf();

    public ObjetsQueteRendu(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(ObjetQueteBE be, float partiel, PoseStack ps, MultiBufferSource buf, int lumiere, int overlay) {
        int etat = be.etat();
        if (etat == ObjetQueteBE.CACHE || be.getLevel() == null) return;
        float t = be.ecoule(partiel);
        if (etat == ObjetQueteBE.PRIS && t > 10) return;
        var cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        if (cam.getPosition().distanceToSqr(be.getBlockPos().getX() + 0.5, be.getBlockPos().getY() + 0.5, be.getBlockPos().getZ() + 0.5) > 32 * 32) return;
        int genre = be.genre();
        Direction f = be.getBlockState().hasProperty(BlocObjetQuete.FACING) ? be.getBlockState().getValue(BlocObjetQuete.FACING) : Direction.SOUTH;
        float age = (be.getLevel().getGameTime() % 24000L) + partiel;
        boolean actif = etat == ObjetQueteBE.ACTIF;
        float pris = etat == ObjetQueteBE.PRIS ? Mth.clamp(t / 10F, 0, 1) : 0;
        float echelle = 1 - pris;
        ps.pushPose();
        ps.translate(0.5F + be.ox(), be.oy(), 0.5F + be.oz());
        ps.mulPose(Q.rotationY(-f.toYRot() * Mth.DEG_TO_RAD));
        float monte = pris * 0.6F + (actif ? 0.25F + 0.06F * Mth.sin(age * 0.12F) : 0);
        ps.translate(0, monte, 0);
        switch (genre) {
            case BlocObjetQuete.FUSIBLE -> {
                ModeleZb m = ModelesZb.get("fusible");
                if (m != null) {
                    ModelesZb.Tex tx = ModelesZb.tex(m.texture);
                    ps.pushPose();
                    // debout dans les pinces de la cachette (decor zb_deco_cachette_fusible : centre (0, 8.3, -5.25) px,
                    // fusible de 4,92 px de haut, origine en bas)
                    ps.translate(0, (8.3F - 2.46F) / 16F, -5.25F / 16F);
                    ps.scale(echelle, echelle, echelle);
                    ModeleZb.dessiner(m.groupe("fusible"), ModeleZb.BASE, ps, buf.getBuffer(tx.base()), lumiere, overlay, 0xFFFFFFFF);
                    ModeleZb.dessiner(m.groupe("fil"), ModeleZb.BASE, ps, buf.getBuffer(tx.base()), lumiere, overlay, 0xFFFFFFFF);
                    ModeleZb.dessiner(m.groupe("fil"), ModeleZb.LUEUR, ps, buf.getBuffer(tx.lueur()), 0xF000F0, OverlayTexture.NO_OVERLAY,
                            Geo.additif(0.35F + 0.25F * Mth.sin(age * 0.2F), 0xFFFFFF));
                    ps.popPose();
                }
                eclat(ps, buf, f, 0, 0.52F, -0.24F, 0.2F * echelle, 0.35F + 0.65F * Math.max(0, Mth.sin(age * 0.09F)) * Math.max(0, Mth.sin(age * 0.23F)),
                        0xFFE2A0);
            }
            case BlocObjetQuete.BOITIER -> {
                ModeleZb m = ModelesZb.get("easter_interrupteur");
                if (m != null) {
                    ModelesZb.Tex tx = ModelesZb.tex(m.texture);
                    VertexConsumer vb = buf.getBuffer(tx.base());
                    for (ModeleZb.Groupe g : m.groupes()) {
                        ps.pushPose();
                        if ("levier".equals(g.nom) && actif) ModeleZb.tourner(ps, g, com.mojang.math.Axis.XP, 150);
                        ModeleZb.dessiner(g, ModeleZb.BASE, ps, vb, lumiere, overlay, 0xFFFFFFFF);
                        ps.popPose();
                    }
                    VertexConsumer vl = buf.getBuffer(tx.lueur());
                    ModeleZb.dessiner(m.groupe(actif ? "voyant_vert" : "voyant_rouge"), ModeleZb.LUEUR, ps, vl, 0xF000F0, OverlayTexture.NO_OVERLAY,
                            Geo.additif(actif ? 0.9F : 0.35F + 0.3F * Math.max(0, Mth.sin(age * 0.15F)), 0xFFFFFF));
                }
                if (actif) eclat(ps, buf, f, 0, 0.5F, -0.2F, 0.6F, 0.5F + 0.2F * Mth.sin(age * 0.3F), 0xD08CFF);
            }
            case BlocObjetQuete.SINGE, BlocObjetQuete.NOUNOURS -> {
                ModeleZb m = ModelesZb.get("peluche");
                if (m != null) {
                    ModelesZb.Tex tx = ModelesZb.tex(m.texture);
                    boolean singe = genre == BlocObjetQuete.SINGE;
                    int teinte = singe ? 0xFF6E5236 : 0xFFFFFFFF;
                    ps.pushPose();
                    if (actif) {
                        // les objets « s'envolent en cercle » (Fly Trap) : petit cercle en l'air
                        ps.translate(0.18F * Mth.cos(age * 0.15F), 0, 0.18F * Mth.sin(age * 0.15F));
                        ps.mulPose(Q.rotationY(age * 0.15F));
                    }
                    float s = (singe ? 0.85F : 1F) * echelle;
                    ps.scale(s, s, s);
                    VertexConsumer vb = buf.getBuffer(tx.base());
                    for (ModeleZb.Groupe g : m.groupes()) ModeleZb.dessiner(g, ModeleZb.BASE, ps, vb, lumiere, overlay, teinte);
                    ps.popPose();
                }
                if (actif) eclat(ps, buf, f, 0, 0.35F, 0, 0.55F, 0.45F + 0.2F * Mth.sin(age * 0.25F), 0x8FFFD8);
                else if (pris > 0) eclat(ps, buf, f, 0, 0.35F, 0, 0.9F * pris + 0.3F, 1 - pris, 0xFFFFFF);
            }
            case BlocObjetQuete.ARME -> {
                ItemStack s = pile(be);
                if (!s.isEmpty() && actif) {
                    ps.pushPose();
                    ps.translate(0, 0.55F, 0);
                    ps.mulPose(Q.rotationY(age * 0.05F));
                    ps.scale(0.9F, 0.9F, 0.9F);
                    Minecraft.getInstance().getItemRenderer().renderStatic(s, ItemDisplayContext.FIXED, 0xF000F0, OverlayTexture.NO_OVERLAY, ps, buf,
                            be.getLevel(), 0);
                    ps.popPose();
                }
                if (actif) eclat(ps, buf, f, 0, 0.55F, 0, 1.1F, 0.55F + 0.2F * Mth.sin(age * 0.2F), 0xFFC860);
            }
            case BlocObjetQuete.CARTE -> {
                // carte d'acces : petite plaque doree posee a plat, qui brille
                ps.pushPose();
                ps.translate(0, 0.03F, 0);
                ps.mulPose(Q.rotationX(-Mth.HALF_PI));
                Geo.plan(ps, buf.getBuffer(HALO), -0.14F * echelle, -0.09F * echelle, 0.14F * echelle, 0.09F * echelle, PLEIN, 0xF000F0, OverlayTexture.NO_OVERLAY,
                        Geo.additif(0.5F, 0xFFD27A));
                ps.popPose();
                eclat(ps, buf, f, 0, 0.12F, 0, 0.25F * echelle, 0.4F + 0.5F * Math.max(0, Mth.sin(age * 0.11F)), 0xFFE2A0);
            }
            default -> {
            }
        }
        ps.popPose();
    }

    /** Eclat (halo additif) tourne vers la camera, centre en (x, y, z) du repere de l'objet. */
    private static void eclat(PoseStack ps, MultiBufferSource buf, Direction f, float x, float y, float z, float taille, float k, int rgb) {
        if (k <= 0.01F || taille <= 0.01F) return;
        ps.pushPose();
        ps.translate(x, y, z);
        ps.mulPose(Q.rotationY(f.toYRot() * Mth.DEG_TO_RAD));
        ps.mulPose(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
        Geo.plan(ps, buf.getBuffer(HALO), -taille, -taille, taille, taille, PLEIN, 0xF000F0, OverlayTexture.NO_OVERLAY, Geo.additif(k, rgb));
        ps.popPose();
    }

    /** Pile TaCZ de l'arme du fourneau ({@code tacz:modern_kinetic_gun} + {@code GunId}), construite une fois. */
    private static ItemStack pile(ObjetQueteBE be) {
        if (be.pileClient != null) return be.pileClient;
        ItemStack s = ItemStack.EMPTY;
        String id = be.donnee();
        if (!id.isEmpty()) {
            Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation("tacz", "modern_kinetic_gun"));
            if (it != null && it != net.minecraft.world.item.Items.AIR) {
                s = new ItemStack(it);
                CompoundTag tag = s.getOrCreateTag();
                tag.putString("GunId", id);
            }
        }
        be.pileClient = s;
        return s;
    }

    @Override
    public boolean shouldRenderOffScreen(ObjetQueteBE be) {
        return false;
    }

    @Override
    public int getViewDistance() {
        return 32;
    }

}
