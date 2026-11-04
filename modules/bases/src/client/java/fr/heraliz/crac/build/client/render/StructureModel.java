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
        BlockPos p = at != null ? at : pos;
        return data.derive().with(RESOLVED, avecMoitiesLaissees(level, occ, p, resolve(occ, p))).build();
    }

    /**
     * Trou dans un mur posé après une armoire ou une caisse en métal (HeraliZ, 27/10, capture du propriétaire) : le mur
     * laisse à l'objet la moitié de case où il se tient (règle d'enfouissement de PlacementRules : la case reste celle de
     * l'objet, le mur la reprend quand il part). Personne ne dessinait cette moitié : au-dessus et autour de l'objet, le mur
     * était creux. La case du mur voisine la dessine maintenant, décalée d'un bloc vers l'objet ; refait à chaque
     * reconstruction du tronçon (objet posé ou retiré). Seulement les murs, seulement contre un objet posé ({@link #laisseMoitie}).
     * <p>HeraliZ (31/10) : une seule case la dessine ({@link #dessine}). Avant, chaque case voisine qui tenait ce mur la
     * dessinait : jusqu'à trois fois les mêmes faces (la case de l'autre côté du mur et les deux le long du mur, pour une
     * caisse posée au milieu d'un mur), éclairées chacune depuis sa case : des faces confondues qui pouvaient scintiller.
     */
    private static PieceBakery.BlockQuads[] avecMoitiesLaissees(BlockAndTintGetter level, List<Occupant> occ, BlockPos pos,
                                                                  PieceBakery.BlockQuads[] base) {
        List<PieceBakery.BlockQuads> plus = null;
        for (Occupant o : occ) {
            if (!o.kind().isWall()) continue;
            List<BlockPos> blocs = null;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos n = pos.relative(d);
                if (!laisseMoitie(level.getBlockState(n).getBlock())) continue;
                if (blocs == null) {
                    blocs = fr.heraliz.crac.build.geom.PieceSolids.collisionBlocks(o.key());
                }
                if (!blocs.contains(n) || !dessine(level, n, o, pos)) continue;
                if (plus == null) {
                    plus = new ArrayList<>();
                }
                plus.add(PieceBakery.deplacees(PieceBakery.quads(o, n), d.getStepX(), 0, d.getStepZ()));
            }
        }
        if (plus == null) {
            return base;
        }
        PieceBakery.BlockQuads[] out = java.util.Arrays.copyOf(base, base.length + plus.size());
        for (int i = 0; i < plus.size(); i++) {
            out[base.length + i] = plus.get(i);
        }
        return out;
    }

    /**
     * Un objet de ce bloc peut se tenir dans la moitié intérieure d'une case de mur (le mur la lui laisse) : armoire,
     * caisses et coffres de CRac ({@code Adosse.Adossable}) et, HeraliZ (03/11, photo du propriétaire : mur invisible ou
     * trop fin derrière un objet collé), tout objet posé d'une base (placables : table d'armurier de TaCZ posée dans la
     * rangée d'un mur, four, établi, enclume… posés avant le mur). Avant, seuls les objets de CRac faisaient redessiner la
     * moitié laissée : derrière les autres, il ne restait que la moitié extérieure du mur (2 px), et sa face tournée vers
     * l'objet était cachée par Minecraft quand l'objet est un bloc plein : on voyait au travers du mur. Jamais le terrain
     * (un mur enterré n'est pas redessiné dans la terre).
     */
    private static boolean laisseMoitie(net.minecraft.world.level.block.Block b) {
        // 04/11 (photo du propriétaire : « l'échelle coupe une partie du mur et enlève la texture ») : l'échelle de raid prend
        // aussi la moitié intérieure de trois cases de mur (StructureService.ceder) ; elle n'était ni un objet de CRac
        // « adossable » ni dans la liste des objets posables : derrière elle, le mur restait creux sur toute sa hauteur
        return b instanceof fr.heraliz.crac.block.Adosse.Adossable || b instanceof fr.heraliz.crac.block.EchelleBlock
            || fr.heraliz.crac.placable.PlacableSet.canPlaceClient(b);
    }

    /** Les voisines d'une case dans l'ordre où l'une d'elles dessine la moitié laissée d'un mur le long de x (z constant). */
    private static final Direction[] ORDRE_MUR_X = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
    /** Même chose pour un mur le long de z (x constant). */
    private static final Direction[] ORDRE_MUR_Z = {Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH};

    /**
     * La case {@code ici} est celle qui dessine la moitié du mur {@code o} laissée à l'objet de la case {@code n} : la
     * première voisine de {@code n} qui tient ce mur, d'abord de l'autre côté du mur (sa moitié extérieure, toujours là sauf
     * enterrée), puis le long du mur. La même réponse depuis chaque voisine : une seule la dessine.
     */
    private static boolean dessine(BlockAndTintGetter level, BlockPos n, Occupant o, BlockPos ici) {
        Direction[] ordre = o.key().orient() % 6 == 3 ? ORDRE_MUR_Z : ORDRE_MUR_X;
        for (Direction d : ordre) {
            BlockPos m = n.relative(d);
            if (m.equals(ici)) {
                return true;
            }
            if (level.getBlockEntity(m) instanceof StructureBlockEntity be) {
                for (Occupant x : be.occupants()) {
                    if (x.pieceId() == o.pieceId()) {
                        return false;
                    }
                }
            }
        }
        return false;
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
