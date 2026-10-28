package fr.heraliz.crac.client.render;

import fr.heraliz.crac.block.Adosse;
import fr.heraliz.crac.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Modèle d'un coffre ou de la caisse en métal descendu sur un plancher à mi-bloc ({@link Adosse#BAS}, HeraliZ, 28/10) :
 * le modèle du même état non descendu (mêmes fichiers, écrits par les outils), abaissé d'un demi-bloc, exactement comme sa
 * forme. Les faces ne sont jamais cachées par une case voisine (elles ne sont plus au bord de leur case). Calculé une fois
 * par couche de rendu, rien à chaque image.
 */
@Mod.EventBusSubscriber(modid = "crac", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModeleDescendu extends BakedModelWrapper<BakedModel> {
    private static final Direction[] DIRS = Direction.values();
    private static final Object SANS_COUCHE = new Object();
    private final BlockState original;
    private final Map<Object, List<BakedQuad>> quads = new ConcurrentHashMap<>();

    private ModeleDescendu(BakedModel modele, BlockState original) {
        super(modele);
        this.original = original;
    }

    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        Map<ResourceLocation, BakedModel> modeles = event.getModels();
        for (Block b : new Block[]{ModBlocks.METAL_CRATE.get(), ModBlocks.MALLE_CHANTIER.get(), ModBlocks.CASIER_METALLIQUE.get(),
            ModBlocks.CAISSE_ARMES.get(), ModBlocks.COFFRE_FORT.get()}) {
            for (BlockState s : b.getStateDefinition().getPossibleStates()) {
                if (!Adosse.bas(s)) continue;
                BlockState haut = s.setValue(Adosse.BAS, false);
                ModelResourceLocation ici = BlockModelShaper.stateToModelLocation(s);
                BakedModel m = modeles.get(BlockModelShaper.stateToModelLocation(haut));
                if (m != null) {
                    modeles.put(ici, new ModeleDescendu(m, haut));
                }
            }
        }
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource rand,
                                             @NotNull ModelData data, @Nullable RenderType renderType) {
        if (side != null) {
            return List.of();
        }
        return this.quads.computeIfAbsent(renderType == null ? SANS_COUCHE : renderType, k -> this.descendre(rand, data, renderType));
    }

    private List<BakedQuad> descendre(RandomSource rand, ModelData data, @Nullable RenderType renderType) {
        List<BakedQuad> out = new ArrayList<>();
        float dy = (float) (Adosse.DEMI / 16.0);
        for (int i = -1; i < DIRS.length; i++) {
            for (BakedQuad q : this.originalModel.getQuads(this.original, i < 0 ? null : DIRS[i], rand, data, renderType)) {
                int[] v = q.getVertices().clone();
                int pas = v.length / 4;
                for (int k = 0; k < 4; k++) {
                    v[k * pas + 1] = Float.floatToRawIntBits(Float.intBitsToFloat(v[k * pas + 1]) - dy);
                }
                out.add(new BakedQuad(v, q.getTintIndex(), q.getDirection(), q.getSprite(), q.isShade(), q.hasAmbientOcclusion()));
            }
        }
        return List.copyOf(out);
    }
}
