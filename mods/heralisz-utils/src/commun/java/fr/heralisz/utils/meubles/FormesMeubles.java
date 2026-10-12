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
 */
public final class FormesMeubles {
    /** Meuble cfm -> forme selon le côté où il regarde (propriété facing, comme son modèle). */
    private static final Map<ResourceLocation, Function<Direction, VoxelShape>> FORMES = Map.of(
            // petit escalier en grille de métal : on y monte sans sauter, comme un escalier de Minecraft
            new ResourceLocation("cfm", "model_239"), FormesMeubles::escalier);

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

    /** Escalier de Minecraft : moitié basse pleine, moitié haute du côté où il monte (celui où il regarde). */
    static VoxelShape escalier(Direction haut) {
        VoxelShape marche = switch (haut) {
            case SOUTH -> Block.box(0, 8, 8, 16, 16, 16);
            case EAST -> Block.box(8, 8, 0, 16, 16, 16);
            case WEST -> Block.box(0, 8, 0, 8, 16, 16);
            default -> Block.box(0, 8, 0, 16, 16, 8);
        };
        return Shapes.or(Block.box(0, 0, 0, 16, 8, 16), marche);
    }
}
