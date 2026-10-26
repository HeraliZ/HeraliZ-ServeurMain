package fr.heralisz.utils.ascenseur;

import fr.heralisz.utils.HeraliszUtils;
import fr.heralisz.utils.deco.DecoBlocks;
import fr.heralisz.utils.pont.Usine;
import fr.heralisz.utils.pont.UsineBlocEntite;
import fr.heralisz.utils.pont.UsineEntite;
import java.util.function.Supplier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Le grand ascenseur en bois de la faille des nains. Trois pièces :
 * <ul>
 * <li>la machine ({@link MachineBlock}), posée au sommet du puits : elle repère d'elle-même les paliers du puits, fait
 * exister la cabine et dessine le chevalement, la grande roue qui tourne avec la cabine, les cordes et le contrepoids ;</li>
 * <li>les panneaux d'appel ({@link PalierBlock}) à chaque palier : moitié haute = monter, moitié basse = descendre ; si
 * la cabine est ailleurs, elle vient ;</li>
 * <li>la cabine ({@link Cabine}), une entité que les joueurs debout sur le plancher « montent » au départ : c'est le
 * serveur qui la déplace (aucun risque d'être pris pour un tricheur qui vole), en douceur, et dépose tout le monde
 * sur le plancher à l'arrivée.</li>
 * </ul>
 */
public final class Ascenseur {
    public static final DeferredRegister<Block> BLOCS = DeferredRegister.create(ForgeRegistries.BLOCKS, HeraliszUtils.MODID);
    public static final DeferredRegister<BlockEntityType<?>> ENTITES_BLOC =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, HeraliszUtils.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, HeraliszUtils.MODID);

    public static final RegistryObject<Block> MACHINE = bloc("ascenseur_machine", () -> new MachineBlock(proprietes()));
    /** Usines (jar fusionné) : versions serveur branchées par la partie serveur (serveur.PartieServeur). */
    public static final Usine<BlockBehaviour.Properties, PalierBlock> PALIER_USINE = new Usine<>(PalierBlock::new);
    public static final UsineBlocEntite<MachineBlockEntity> MACHINE_USINE = new UsineBlocEntite<>(MachineBlockEntity::new);
    public static final UsineEntite<Cabine> CABINE_USINE = new UsineEntite<>(Cabine::new);
    public static final RegistryObject<Block> PALIER = bloc("ascenseur_palier", () -> PALIER_USINE.apply(proprietes()));
    public static final RegistryObject<BlockEntityType<MachineBlockEntity>> MACHINE_BE = ENTITES_BLOC.register("ascenseur",
            () -> BlockEntityType.Builder.of(MACHINE_USINE, MACHINE.get()).build(null));
    public static final RegistryObject<EntityType<Cabine>> CABINE = ENTITES.register("cabine",
            () -> EntityType.Builder.<Cabine>of(CABINE_USINE, MobCategory.MISC).sized(4.6f, Cabine.PONT)
                    .clientTrackingRange(10).updateInterval(1).fireImmune().build("cabine"));

    private Ascenseur() {}

    public static void register(IEventBus bus) {
        BLOCS.register(bus);
        ENTITES_BLOC.register(bus);
        ENTITES.register(bus);
    }

    private static BlockBehaviour.Properties proprietes() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(-1.0f, 3600000.0f).noLootTable().sound(SoundType.WOOD)
                .noOcclusion().pushReaction(PushReaction.BLOCK).isValidSpawn((s, l, p, e) -> false)
                .isRedstoneConductor((s, l, p) -> false).isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false);
    }

    private static RegistryObject<Block> bloc(String id, Supplier<Block> b) {
        RegistryObject<Block> r = BLOCS.register(id, b);
        RegistryObject<Item> it = HeraliszUtils.ITEMS.register(id, () -> new BlockItem(r.get(), new Item.Properties()));
        DecoBlocks.ITEMS.add(it);
        return r;
    }
}
