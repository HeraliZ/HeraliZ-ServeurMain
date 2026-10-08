package fr.heraliz.casino.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heraliz.casino.HeralizCasino;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Optimisation : pendant le rendu des blocs-entites, toutes les machines et tous les decors ecrivent dans des tampons
 * regroupes par type de rendu, dessines en une fois a la fin de la passe (un appel de dessin par texture au lieu de
 * plusieurs par machine). Le tampon commun de Minecraft, lui, vide son contenu a chaque changement de type.
 * Les sommets sont deja exprimes par rapport a la camera : les dessiner un peu plus tard dans la meme image est
 * equivalent. Desactive avec un pack de shaders (qui gere lui-meme ses tampons).
 */
@Mod.EventBusSubscriber(modid = HeralizCasino.MODID, value = Dist.CLIENT)
public final class CasinoBuffers implements MultiBufferSource {
    private static final CasinoBuffers INSTANCE = new CasinoBuffers();

    private final Map<RenderType, BufferBuilder> builders = new HashMap<>();
    private final List<RenderType> used = new ArrayList<>();
    private boolean active;

    private CasinoBuffers() {
    }

    /** Tampons regroupes pendant la passe des blocs-entites, sinon ceux fournis par le jeu. */
    public static MultiBufferSource wrap(MultiBufferSource fallback) {
        return INSTANCE.active ? INSTANCE : fallback;
    }

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        BufferBuilder b = builders.get(type);
        if (b == null) {
            b = new BufferBuilder(Math.max(2048, type.bufferSize()));
            builders.put(type, b);
        }
        if (!b.building()) {
            b.begin(type.mode(), type.format());
            used.add(type);
        }
        return b;
    }

    /** Dessine tout : opaque, puis translucide, puis lumineux, puis les textes (carrosseries figees en tete de chaque groupe). */
    private void flush() {
        if (used.isEmpty() && !StaticModel.hasDeferred()) return;
        long t0 = RenderProfile.on ? System.nanoTime() : 0;
        try {
            for (int cat = CasinoRenderTypes.CAT_SOLID; cat <= CasinoRenderTypes.CAT_OTHER; cat++) {
                StaticModel.flushDeferred(cat);
                for (RenderType t : used) {
                    if (CasinoRenderTypes.category(t) != cat) continue;
                    BufferBuilder b = builders.get(t);
                    if (b != null && b.building()) t.end(b, RenderSystem.getVertexSorting());
                }
            }
        } finally {
            // un tampon laisse ouvert (erreur) est vide pour l'image suivante
            for (RenderType t : used) {
                BufferBuilder b = builders.get(t);
                if (b != null && b.building()) b.end().release();
            }
            used.clear();
            StaticModel.endDeferred();
            if (RenderProfile.on) {
                long dt = System.nanoTime() - t0;
                RenderProfile.renderNanos += dt;
                RenderProfile.section[8] += dt;
            }
        }
    }

    @SubscribeEvent
    public static void onStage(RenderLevelStageEvent e) {
        RenderLevelStageEvent.Stage st = e.getStage();
        if (st == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            INSTANCE.active = !StaticModel.ShaderPacks.inUse();
            StaticModel.defer = INSTANCE.active;
        } else if (st == RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES || st == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            INSTANCE.active = false;
            StaticModel.defer = false;
            INSTANCE.flush();
            if (st == RenderLevelStageEvent.Stage.AFTER_LEVEL) StaticModel.endFrame();
        }
    }
}
