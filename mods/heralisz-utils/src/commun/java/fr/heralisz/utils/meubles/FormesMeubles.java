package fr.heralisz.utils.meubles;

import com.google.common.collect.ImmutableMap;
import fr.heralisz.utils.HeraliszUtils;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Formes de collision de quelques meubles de cfm (Furniture Mod de MrCrayfish, jar tiers jamais retouché) : ses
 * meubles « Standard_Block » sont tous un cube de 15 px, quel que soit leur modèle. Sans toucher au jar, la forme des
 * meubles listés ici est remplacée au démarrage (champ public SHAPES du bloc, puis caches des états recalculés).
 * Code commun : le joueur et le serveur doivent avoir la même forme, sinon ils ne sont pas d'accord sur les collisions.
 * Meuble absent ou cfm changé (plus de champ SHAPES) : forme d'origine gardée, avertissement dans le journal.
 * <p>Ces meubles cachent les faces des blocs voisins que leur forme recouvre en entier (le jeu ne dessine pas une face
 * collée à un bloc plein) ; or ce sont des grilles, on voit à travers : une forme qui touche toute une face du bloc
 * faisait disparaître la face du voisin (on voyait le vide sous l'escalier, 2.7.0). Les formes s'arrêtent donc à un
 * centième de pixel des côtés du bloc ({@link #RETRAIT}) : rien de visible ni de sensible en jeu, aucune face cachée.
 */
public final class FormesMeubles {
    /** Écart laissé entre la forme et les côtés du bloc, en pixels (voir plus haut). */
    static final double RETRAIT = 0.01;

    /**
     * Meuble cfm -> forme selon le côté où il regarde (propriété facing, comme son modèle). Boîtes en pixels, données
     * pour un meuble tourné vers le nord (modèle sans rotation), relevées dans les modèles de cfm.
     */
    private static final Map<ResourceLocation, Function<Direction, VoxelShape>> FORMES = Map.of(
            // petit escalier en grille de métal : on y monte sans sauter, comme un escalier de Minecraft (haut au nord)
            new ResourceLocation("cfm", "model_239"), tournee(
                    new double[] {0, 0, 0, 16, 8, 16},
                    new double[] {0, 8, 0, 16, 16, 8}),
            // garde-corps : deux poteaux et trois lisses, dessinés juste devant le bord nord du bloc (z de -1 à 0) ; les
            // vides entre les lisses restent ouverts (balles), trop petits pour qu'un joueur passe
            new ResourceLocation("cfm", "model_240"), tournee(
                    new double[] {0, 0, -1, 0.5, 16, 0},
                    new double[] {15.5, 0, -1, 16, 16, 0},
                    new double[] {0.5, 15, -1, 15.5, 16, 0},
                    new double[] {0.5, 12, -1, 15.5, 12.8, 0},
                    new double[] {0.5, 5, -1, 15.5, 6, 0}),
            // plancher en caillebotis, posé sur le dessus du bloc (y de 16 à 17) : on marche dessus, rien en dessous
            new ResourceLocation("cfm", "model_241"), tournee(
                    new double[] {0, 16, 0, 16, 17, 16}));

    private FormesMeubles() {
    }

    /** Branché sur le bus du mod : formes remplacées sur le fil principal, une fois tous les blocs enregistrés. */
    public static void installer(FMLCommonSetupEvent event) {
        event.enqueueWork(FormesMeubles::appliquer);
    }

    static void appliquer() {
        FORMES.forEach((id, forme) -> {
            if (!ForgeRegistries.BLOCKS.containsKey(id)) {
                return;
            }
            Block bloc = ForgeRegistries.BLOCKS.getValue(id);
            try {
                Field champ = champFormes(bloc.getClass());
                ImmutableMap.Builder<BlockState, VoxelShape> formes = ImmutableMap.builder();
                for (BlockState etat : bloc.getStateDefinition().getPossibleStates()) {
                    Direction cote = etat.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                            ? etat.getValue(BlockStateProperties.HORIZONTAL_FACING) : Direction.NORTH;
                    formes.put(etat, forme.apply(cote));
                }
                champ.setAccessible(true);
                champ.set(bloc, formes.build());
                // collisions gardées en cache par chaque état (calculées à l'enregistrement, avec l'ancienne forme)
                bloc.getStateDefinition().getPossibleStates().forEach(BlockState::initCache);
                HeraliszUtils.LOG.info("[utils] forme du meuble {} remplacée", id);
            } catch (ReflectiveOperationException | RuntimeException e) {
                HeraliszUtils.LOG.warn("[utils] forme du meuble {} non remplacée (cfm a changé ?) : forme d'origine gardée", id, e);
            }
        });
    }

    private static Field champFormes(Class<?> classe) throws NoSuchFieldException {
        for (Class<?> c = classe; c != null && c != Block.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if ("SHAPES".equals(f.getName()) && Map.class.isAssignableFrom(f.getType())) {
                    return f;
                }
            }
        }
        throw new NoSuchFieldException("SHAPES dans " + classe.getName());
    }

    /** Forme faite des boîtes données (meuble tourné vers le nord), tournée comme le modèle dans son blockstate. */
    static Function<Direction, VoxelShape> tournee(double[]... boites) {
        return cote -> {
            VoxelShape forme = Shapes.empty();
            for (double[] b : boites) {
                forme = Shapes.or(forme, tourner(b, cote));
            }
            return forme;
        };
    }

    /** Une boîte {x1, y1, z1, x2, y2, z2} du nord vers ce côté (rotation y du blockstate : est 90°, sud 180°, ouest 270°). */
    static VoxelShape tourner(double[] b, Direction cote) {
        double x1 = b[0], z1 = b[2], x2 = b[3], z2 = b[5];
        double[] xz = switch (cote) {
            case EAST -> new double[] {16 - z2, x1, 16 - z1, x2};
            case SOUTH -> new double[] {16 - x2, 16 - z2, 16 - x1, 16 - z1};
            case WEST -> new double[] {z1, 16 - x2, z2, 16 - x1};
            default -> new double[] {x1, z1, x2, z2};
        };
        return Block.box(retrait(xz[0]), b[1], retrait(xz[1]), retrait(xz[2]), b[4], retrait(xz[3]));
    }

    /** Un bord de boîte posé sur un côté du bloc (0 ou 16) en est écarté de {@link #RETRAIT}. */
    private static double retrait(double v) {
        return v == 0 ? RETRAIT : v == 16 ? 16 - RETRAIT : v;
    }
}
