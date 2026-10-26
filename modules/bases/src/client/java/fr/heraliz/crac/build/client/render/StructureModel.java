package fr.heraliz.crac.build.client.render;

import java.util.ArrayList;
import java.util.List;
import fr.heraliz.crac.build.model.PieceKind;
import fr.heraliz.crac.build.model.Tier;
import fr.heraliz.crac.build.world.Occupant;
import fr.heraliz.crac.build.world.StructureBlockEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.IDynamicBakedModel;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Model of every {@code crac:structure} block state: the quads of each piece passing through the block, read from the
 * block entity's occupants. Rendered in the chunk mesh (solid, cutout mipped, translucent for glass).
 */
@OnlyIn(Dist.CLIENT)
public final class StructureModel implements IDynamicBakedModel {
    private static final RenderType[] LAYERS = {RenderType.solid(), RenderType.cutoutMipped(), RenderType.translucent()};
    private static final ChunkRenderTypeSet[] SETS = new ChunkRenderTypeSet[1 << LAYERS.length];

    static {
        for (int mask = 0; mask < SETS.length; mask++) {
            List<RenderType> types = new ArrayList<>();
            for (int i = 0; i < LAYERS.length; i++) {
                if ((mask & (1 << i)) != 0) types.add(LAYERS[i]);
            }
            SETS[mask] = ChunkRenderTypeSet.of(types);
        }
    }

    /** Each occupant's quads for this block, resolved once per chunk build (see {@link #getModelData}). */
    public static final ModelProperty<PieceBakery.BlockQuads[]> RESOLVED = new ModelProperty<>();

    /**
     * The quads of every occupant in the block, from the occupant keys carried by the model data (render threads never
     * read the client mirror): {@code bake(phase).get(pos - anchorBlock)}.
     */
    private static PieceBakery.BlockQuads[] resolve(List<Occupant> occ, BlockPos pos) {
        PieceBakery.BlockQuads[] out = new PieceBakery.BlockQuads[occ.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = PieceBakery.quads(occ.get(i), pos);
        }
        return out;
    }

    /** The resolved quads of the model data, or null when the block holds nothing. */
    @Nullable
    private static PieceBakery.BlockQuads[] quads(ModelData data) {
        PieceBakery.BlockQuads[] q = data.get(RESOLVED);
        if (q != null) {
            return q;
        }
        List<Occupant> occ = data.get(StructureBlockEntity.OCCUPANTS);
        BlockPos pos = data.get(StructureBlockEntity.POS);
        if (occ == null || occ.isEmpty() || pos == null) {
            return null;
        }
        return resolve(occ, pos);
    }

    @Override
    public @NotNull ModelData getModelData(@NotNull BlockAndTintGetter level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ModelData data) {
        List<Occupant> occ = data.get(StructureBlockEntity.OCCUPANTS);
        if (occ == null || occ.isEmpty() || data.get(RESOLVED) != null) {
            return data;
        }
        BlockPos at = data.get(StructureBlockEntity.POS);
        return data.derive().with(RESOLVED, resolve(occ, at != null ? at : pos)).build();
    }

    private static int layerOf(@Nullable RenderType type) {
        for (int i = 0; i < LAYERS.length; i++) {
            if (LAYERS[i] == type) return i;
        }
        return -1;
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource rand, @NotNull ModelData data, @Nullable RenderType renderType) {
        PieceBakery.BlockQuads[] all = quads(data);
        if (all == null) {
            return List.of();
        }
        int s = side == null ? PieceBakery.NO_SIDE : side.get3DDataValue();
        int layer = layerOf(renderType);
        if (layer < 0 && renderType != null) {
            return List.of();
        }
        if (all.length == 1 && layer >= 0) {
            return all[0].get(layer, s);
        }
        List<BakedQuad> out = new ArrayList<>();
        for (PieceBakery.BlockQuads q : all) {
            if (layer >= 0) {
                out.addAll(q.get(layer, s));
            } else {
                for (int l = 0; l < LAYERS.length; l++) {
                    out.addAll(q.get(l, s));
                }
            }
        }
        return out;
    }

    @Override
    public @NotNull ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state, @NotNull RandomSource rand, @NotNull ModelData data) {
        PieceBakery.BlockQuads[] all = quads(data);
        if (all == null) {
            return SETS[0];
        }
        int mask = 0;
        for (PieceBakery.BlockQuads q : all) {
            mask |= q.layerMask;
        }
        return SETS[mask];
    }

    @Override
    public TextureAtlasSprite getParticleIcon(@NotNull ModelData data) {
        List<Occupant> occ = data.get(StructureBlockEntity.OCCUPANTS);
        if (occ == null || occ.isEmpty()) {
            return this.getParticleIcon();
        }
        Occupant o = occ.get(0);
        return PieceBakery.particle(o.kind(), o.tier());
    }

    @Override
    public boolean useAmbientOcclusion() {
        return true;
    }

    @Override
    public boolean isGui3d() {
        return false;
    }

    @Override
    public boolean usesBlockLight() {
        return true;
    }

    @Override
    public boolean isCustomRenderer() {
        return false;
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return PieceBakery.particle(PieceKind.WALL, Tier.WOOD);
    }

    @Override
    public ItemOverrides getOverrides() {
        return ItemOverrides.EMPTY;
    }
}
