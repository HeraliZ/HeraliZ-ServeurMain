package fr.heraliz.quetes.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heraliz.quetes.HeralizQuetes;
import fr.heraliz.quetes.entite.Pnj;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Repères des PNJ au spawn : une petite pastille ronde au-dessus de chaque PNJ (Commandant Varga, marchands…), vue à
 * travers les murs, discrète (à demi transparente, de taille presque constante à l'écran, plus pâle de loin, effacée tout
 * près où le nom du PNJ suffit) ; son nom dessous quand on la regarde ou qu'on est à moins de 20 blocs. Seulement dans le
 * monde du spawn ({@code heralisz:spawn}), interface affichée. Pastilles écrites par outils/reperes.py (même ordre que
 * {@link #indice}). Rien ne vient du serveur : les PNJ suivis par le jeu du joueur (jusqu'à 160 blocs) suffisent.
 */
final class Reperes {
    private Reperes() {
    }

    private static final ResourceLocation TEXTURE = new ResourceLocation(HeralizQuetes.MODID, "textures/gui/reperes.png");
    private static final int NOMBRE = 7;
    private static final ResourceLocation SPAWN = new ResourceLocation("heralisz", "spawn");
    private static final int PLEINE_LUMIERE = 0xF000F0;

    /** Pastille d'un PNJ d'après son identifiant ({@code /pnj creer <id>}) : 0 = pastille commune. */
    private static int indice(String id) {
        return switch (id == null ? "" : id) {
            case "varga" -> 1;
            case "jacqueline" -> 2;
            case "mila" -> 3;
            case "doyen" -> 4;
            case "jack" -> 5;
            case "batisseur" -> 6;
            default -> 0;
        };
    }

    static void rendu(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui || !SPAWN.equals(mc.level.dimension().location())) return;
        Camera cam = e.getCamera();
        Vec3 oeil = cam.getPosition();
        Vec3 regard = new Vec3(cam.getLookVector());
        float partiel = e.getPartialTick();
        PoseStack pose = e.getPoseStack();
        MultiBufferSource.BufferSource tampon = mc.renderBuffers().bufferSource();
        Font police = mc.font;
        boolean dessine = false;
        boolean pres = Presentation.actif();
        for (Entity ent : mc.level.entitiesForRendering()) {
            if (!(ent instanceof Pnj pnj) || ent.isInvisible()) continue;
            Vec3 p = ent.getPosition(partiel).add(0, ent.getBbHeight() + 0.95, 0);
            Vec3 vers = p.subtract(oeil);
            double d = vers.length();
            // le PNJ vers qui le joueur doit aller (lueur envoyée par le serveur au-dessus de lui) : toujours bien visible
            boolean cible = Lueurs.cible(ent.blockPosition());
            if (d > 160 || d < (pres ? 1.2 : cible ? 2 : 3.5)) continue;
            float alpha = (float) (0.62 * Mth.clamp((d - 3.5) / 3.5, 0, 1) * (d > 60 ? Math.max(0.55, 1 - (d - 60) / 200) : 1));
            if (pres) alpha = 0.9f;                                   // 20/10 : pendant la visite, la caméra est tout près
            if (cible) alpha = (float) (0.8 + 0.2 * Math.sin(mc.player.tickCount * 0.25));
            if (alpha <= 0.02f) continue;
            boolean vise = vers.scale(1 / d).dot(regard) > 0.994;     // ~6° autour du viseur
            float taille = (float) Mth.clamp(d * 0.034, pres ? 0.22 : 0.32, 2.6);   // presque constante à l'écran
            if (cible) taille *= 1.45f;
            pose.pushPose();
            pose.translate(vers.x, vers.y, vers.z);
            pose.mulPose(cam.rotation());
            pose.scale(-taille / 16f, -taille / 16f, taille / 16f);      // comme les noms : +x à droite, +y vers le bas
            Matrix4f m = pose.last().pose();
            int i = indice(pnj.pnjId());
            float u0 = i / (float) NOMBRE, u1 = (i + 1) / (float) NOMBRE;
            VertexConsumer vc = tampon.getBuffer(RenderType.textSeeThrough(TEXTURE));
            int a = Math.round(alpha * 255);
            vc.vertex(m, -8, -8, 0).color(255, 255, 255, a).uv(u0, 0).uv2(PLEINE_LUMIERE).endVertex();
            vc.vertex(m, -8, 8, 0).color(255, 255, 255, a).uv(u0, 1).uv2(PLEINE_LUMIERE).endVertex();
            vc.vertex(m, 8, 8, 0).color(255, 255, 255, a).uv(u1, 1).uv2(PLEINE_LUMIERE).endVertex();
            vc.vertex(m, 8, -8, 0).color(255, 255, 255, a).uv(u1, 0).uv2(PLEINE_LUMIERE).endVertex();
            vc.vertex(m, 8, -8, 0).color(255, 255, 255, a).uv(u1, 0).uv2(PLEINE_LUMIERE).endVertex();   // et l'autre face
            vc.vertex(m, 8, 8, 0).color(255, 255, 255, a).uv(u1, 1).uv2(PLEINE_LUMIERE).endVertex();
            vc.vertex(m, -8, 8, 0).color(255, 255, 255, a).uv(u0, 1).uv2(PLEINE_LUMIERE).endVertex();
            vc.vertex(m, -8, -8, 0).color(255, 255, 255, a).uv(u0, 0).uv2(PLEINE_LUMIERE).endVertex();
            if (vise || d < 20 || pres || cible) {
                Component nom = cible && d >= 6 ? pnj.getName().copy().append(" · " + (int) d + " m") : pnj.getName();
                float l = police.width(nom) * 0.5f;
                pose.scale(0.55f, 0.55f, 0.55f);
                int coul = (Math.min(255, Math.round(alpha * 1.4f * 255)) << 24) | 0xF2F4F6;
                police.drawInBatch(nom, -l, 17, coul, false, pose.last().pose(), tampon, Font.DisplayMode.SEE_THROUGH, 0, PLEINE_LUMIERE);
            }
            pose.popPose();
            dessine = true;
        }
        if (dessine) tampon.endBatch();
    }
}
