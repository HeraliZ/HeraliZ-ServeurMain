package fr.heraliz.crac.client.render;

import fr.heraliz.crac.block.Adosse;
import fr.heraliz.crac.block.ClaimBlock;
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
 * Modèle de la caisse en métal (et, HeraliZ 31/10, des coffres par rareté) posée dans la case d'un mur ({@link Adosse#DANS_MUR}, HeraliZ, 31/10) : le modèle du même
 * état sans poussée (mêmes fichiers, écrits par les outils), décalé contre la face du mur exactement comme sa forme
 * ({@link Adosse#decalageDansMur}, moins d'un pixel). Aucun nouveau fichier de modèle ni d'état : les fichiers d'états ne
 * nomment pas {@code dans_mur}, chaque état reçoit d'abord le modèle sans décalage, remplacé ici. Faces jamais cachées par
 * une case voisine (elles ne sont plus au bord de leur case). Calculé une fois par couche de rendu, rien à chaque image.
 * Même principe que {@link ModeleDescendu}. Coffres par rareté : chaque case (principale, l'autre case d'un coffre de 2 de
 * large) a son modèle, décalé de la même façon ; la case du haut d'un coffre de 2 de haut n'est pas dessinée.
 */
@Mod.EventBusSubscriber(modid = "crac", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModeleDansMur extends BakedModelWrapper<BakedModel> {
    private static final Direction[] DIRS = Direction.values();
    private static final Object SANS_COUCHE = new Object();
    private final BlockState original;
    private final float dx, dz;
    private final Map<Object, List<BakedQuad>> quads = new ConcurrentHashMap<>();

    private ModeleDansMur(BakedModel modele, BlockState original, double dx, double dz) {
        super(modele);
        this.original = original;
        this.dx = (float) (dx / 16.0);
        this.dz = (float) (dz / 16.0);
    }

    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        Map<ResourceLocation, BakedModel> modeles = event.getModels();
        for (Block b : new Block[]{ModBlocks.METAL_CRATE.get(), ModBlocks.MALLE_CHANTIER.get(), ModBlocks.CASIER_METALLIQUE.get(),
            ModBlocks.CAISSE_ARMES.get(), ModBlocks.COFFRE_FORT.get()}) {
            if (b instanceof Adosse.Adossable a && b.defaultBlockState().hasProperty(Adosse.DANS_MUR)) {
                remplacer(modeles, b, a);
            }
        }
    }

    private static void remplacer(Map<ResourceLocation, BakedModel> modeles, Block b, Adosse.Adossable a) {
        for (BlockState s : b.getStateDefinition().getPossibleStates()) {
            int mur = s.getValue(Adosse.DANS_MUR);
            // jamais descendue ni poussée vers une case voisine en même temps (Adosse.dansMur) : ces états ne servent pas
            if (mur == Adosse.AUCUN || Adosse.bas(s) || s.getValue(Adosse.ADOSSE) != Adosse.AUCUN) continue;
            BlockState sansMur = s.setValue(Adosse.DANS_MUR, Adosse.AUCUN);
            BakedModel m = modeles.get(BlockModelShaper.stateToModelLocation(sansMur));
            if (m == null) continue;
            double[] d = Adosse.decalageDansMur(Adosse.tourner(a.boiteNord(), s.getValue(ClaimBlock.FACING)), mur);
            ModelResourceLocation ici = BlockModelShaper.stateToModelLocation(s);
            modeles.put(ici, new ModeleDansMur(m, sansMur, d[0], d[1]));
        }
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource rand,
                                             @NotNull ModelData data, @Nullable RenderType renderType) {
        if (side != null) {
            return List.of();
        }
        return this.quads.computeIfAbsent(renderType == null ? SANS_COUCHE : renderType, k -> this.decaler(rand, data, renderType));
    }

    private List<BakedQuad> decaler(RandomSource rand, ModelData data, @Nullable RenderType renderType) {
        List<BakedQuad> out = new ArrayList<>();
        for (int i = -1; i < DIRS.length; i++) {
            for (BakedQuad q : this.originalModel.getQuads(this.original, i < 0 ? null : DIRS[i], rand, data, renderType)) {
                int[] v = q.getVertices().clone();
                int pas = v.length / 4;
                for (int k = 0; k < 4; k++) {
                    v[k * pas] = Float.floatToRawIntBits(Float.intBitsToFloat(v[k * pas]) + this.dx);
                    v[k * pas + 2] = Float.floatToRawIntBits(Float.intBitsToFloat(v[k * pas + 2]) + this.dz);
                }
                out.add(new BakedQuad(v, q.getTintIndex(), q.getDirection(), q.getSprite(), q.isShade(), q.hasAmbientOcclusion()));
            }
        }
        return List.copyOf(out);
    }
}
