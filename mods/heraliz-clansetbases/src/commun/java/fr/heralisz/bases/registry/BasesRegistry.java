package fr.heralisz.bases.registry;

import fr.heralisz.bases.HeraliszBases;
import fr.heralisz.bases.bloc.CoqueBlock;
import fr.heralisz.bases.bloc.SortieBlock;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registres du coeur : la coque (obsidienne renforcee, incassable), le bloc de sortie des sas, la particule de
 * poussiere de coque. Les blocs de blindage (§5) sont enregistres par le module pillage dans ses propres registres.
 */
public final class BasesRegistry {
    private BasesRegistry() {
    }

    public static final DeferredRegister<Block> BLOCS = DeferredRegister.create(ForgeRegistries.BLOCKS, HeraliszBases.MODID);
    public static final DeferredRegister<Item> OBJETS = DeferredRegister.create(ForgeRegistries.ITEMS, HeraliszBases.MODID);
    public static final DeferredRegister<ParticleType<?>> PARTICULES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, HeraliszBases.MODID);

    /** Proprietes communes de la coque et de la sortie : incassable, insensible aux explosions et aux pistons. */
    private static BlockBehaviour.Properties incassable() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(-1.0f, 3_600_000.0f).noLootTable()
                .pushReaction(PushReaction.BLOCK).isValidSpawn((s, l, p, t) -> false).sound(Sons.COQUE);
    }

    public static final RegistryObject<Block> COQUE = BLOCS.register("coque", () -> new CoqueBlock(incassable()));
    public static final RegistryObject<Block> SORTIE = BLOCS.register("sortie", () -> new SortieBlock(incassable().lightLevel(s -> 7)));

    public static final RegistryObject<Item> COQUE_OBJET = OBJETS.register("coque",
            () -> new BlockItem(COQUE.get(), new Item.Properties().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> SORTIE_OBJET = OBJETS.register("sortie",
            () -> new BlockItem(SORTIE.get(), new Item.Properties().rarity(Rarity.EPIC)));

    /** Poussiere sombre de la cloison qui se desagrege (texture maison, tools/bases/gen_textures.py). */
    public static final RegistryObject<SimpleParticleType> POUSSIERE_COQUE = PARTICULES.register("poussiere_coque",
            () -> new SimpleParticleType(false));

    public static void register(IEventBus modBus) {
        BLOCS.register(modBus);
        OBJETS.register(modBus);
        PARTICULES.register(modBus);
    }
}
