package fr.heralisz.camo.dev;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;

/**
 * Banc d'essai uniquement (étape « bench » du harness) : coût CPU du rendu d'un équipement Curios, mesuré en appelant son
 * rendu N fois vers un tampon qui compte les sommets. Donne des µs par joueur rendu, hors envoi à la carte graphique.
 */
final class CamoBench {
    private static final Logger LOG = LogUtils.getLogger();

    private CamoBench() {
    }

    /** Tampon factice : compte les sommets et garde une somme pour que la JVM n'élimine pas le travail. */
    private static final class Sink implements VertexConsumer {
        long vertices;
        double sum;

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            sum += x + y + z;
            return this;
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            sum += u;
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            sum += x;
            return this;
        }

        @Override
        public void endVertex() {
            vertices++;
        }

        @Override
        public void defaultColor(int r, int g, int b, int a) {
        }

        @Override
        public void unsetDefaultColor() {
        }
    }

    /**
     * Coût du rendu d'une arme (celle en main, copiée) en 3e personne : skin donné (ou aucun), personnalisation gardée ou non.
     * Passe par le vrai rendu d'objet de TaCZ, donc par tous les ajouts du mod (camo, effets 3D, stickers, pendentif).
     */
    static void gun(Minecraft mc, String label, String camo, boolean decor, int n) {
        ItemStack stack = mc.player.getMainHandItem().copy();
        var tag = stack.getOrCreateTag();
        if (camo.isEmpty()) {
            tag.remove(fr.heralisz.camo.CamoData.KEY);
        } else {
            tag.putString(fr.heralisz.camo.CamoData.KEY, camo);
        }
        if (!decor) {
            tag.remove(fr.heralisz.camo.decor.DecorData.KEY);
        }
        var buffers = mc.renderBuffers().bufferSource();
        PoseStack ps = new PoseStack();
        Runnable one = () -> {
            fr.heralisz.camo.client.CamoFx.resetBudget();
            mc.getItemRenderer().renderStatic(mc.player, stack, net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, false, ps, buffers,
                    mc.level, 0xF000F0, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, 0);
        };
        for (int i = 0; i < 20; i++) {
            one.run();
            buffers.endBatch();
        }
        fr.heralisz.camo.client.CamoFx.profile();   // remise à zéro après la chauffe
        double best = Double.MAX_VALUE;
        for (int trial = 0; trial < 6; trial++) {
            long t0 = System.nanoTime();
            for (int i = 0; i < n; i++) {
                one.run();
                if (i % 10 == 9) {
                    buffers.endBatch();
                }
            }
            buffers.endBatch();
            best = Math.min(best, (System.nanoTime() - t0) / 1000.0 / n);
        }
        LOG.info("[gunbench] {} : {} µs par rendu | {}", label, String.format("%.1f", best), fr.heralisz.camo.client.CamoFx.profile());
    }

    /** Appui réel sur la touche du sac : touche enfoncée + événement clavier du mod (comme un joueur qui presse B). */
    static void pressBackpackKey(Minecraft mc) {
        for (net.minecraft.client.KeyMapping k : mc.options.keyMappings) {
            if (k.getName().equals("key.backpack")) {
                k.setDown(true);
                net.minecraft.client.KeyMapping.click(k.getKey());
            }
        }
        try {
            Class.forName("com.mrcrayfish.backpacked.client.ClientEvents").getMethod("onKeyInput", int.class, int.class, int.class, int.class)
                    .invoke(null, 66, 0, 1, 0);
        } catch (ReflectiveOperationException e) {
            LOG.warn("[etat] touche du sac introuvable", e);
        }
    }

    /** Ce que le joueur a sur le dos (Curios) et l'écran ouvert : preuve écrite pour le banc. */
    static void report(Minecraft mc, String label) {
        String back = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(mc.player).resolve()
                .flatMap(inv -> inv.getStacksHandler("back"))
                .map(h -> h.getStacks().getStackInSlot(0))
                .map(st -> st.isEmpty() ? "vide" : ForgeRegistries.ITEMS.getKey(st.getItem()).toString())
                .orElse("?");
        String screen = mc.screen == null ? "aucun" : mc.screen.getClass().getSimpleName() + " « " + mc.screen.getTitle().getString() + " »";
        String slots = mc.player.containerMenu == null ? "?" : String.valueOf(mc.player.containerMenu.slots.size());
        LOG.info("[etat] {} : dos = {}, écran = {}, cases du menu = {}, main = {}", label, back, screen, slots,
                ForgeRegistries.ITEMS.getKey(mc.player.getMainHandItem().getItem()));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static void run(Minecraft mc, String itemId, String slot, int n, double dist) {
        ItemStack stack = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemId)));
        var renderer = CuriosRendererRegistry.getRenderer(stack.getItem()).orElse(null);
        if (renderer == null || mc.player == null) {
            LOG.warn("[bench] pas de rendu pour {}", itemId);
            return;
        }
        RenderLayerParent parent = (RenderLayerParent) mc.getEntityRenderDispatcher().getRenderer(mc.player);
        // dist > 0 : porteur fictif (support d'armure hors du monde) placé à cette distance de la caméra, pour mesurer le niveau de détail
        net.minecraft.world.entity.LivingEntity wearer = mc.player;
        if (dist > 0) {
            var cam = mc.gameRenderer.getMainCamera();
            var at = cam.getPosition().add(new net.minecraft.world.phys.Vec3(cam.getLookVector()).scale(dist));
            wearer = new net.minecraft.world.entity.decoration.ArmorStand(mc.level, at.x, at.y, at.z);
        }
        SlotContext ctx = new SlotContext(slot, wearer, 0, false, true);
        Sink sink = new Sink();
        MultiBufferSource buffers = type -> sink;
        PoseStack ps = new PoseStack();
        for (int i = 0; i < 30; i++) {
            renderer.render(stack, ctx, ps, parent, buffers, 15728880, 0, 0, 0, 0, 0, 0);
            mc.renderBuffers().bufferSource().endBatch();
        }
        // plusieurs essais, on garde le meilleur (le moins perturbé par le reste de la machine) ; tampon envoyé toutes les 10 fois
        double best = Double.MAX_VALUE;
        for (int trial = 0; trial < 6; trial++) {
            long t0 = System.nanoTime();
            for (int i = 0; i < n; i++) {
                renderer.render(stack, ctx, ps, parent, buffers, 15728880, 0, 0, 0, 0, 0, 0);
                if (i % 10 == 9) {
                    mc.renderBuffers().bufferSource().endBatch();
                }
            }
            mc.renderBuffers().bufferSource().endBatch();
            best = Math.min(best, (System.nanoTime() - t0) / 1000.0 / n);
        }
        double us = best;
        LOG.info("[bench] {} à {} blocs : {} µs par rendu", itemId, (int) dist, String.format("%.1f", us));
    }
}
