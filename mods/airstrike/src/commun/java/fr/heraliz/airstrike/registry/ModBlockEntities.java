package fr.heraliz.airstrike.registry;

import fr.heraliz.airstrike.pont.UsineBlocEntite;
import fr.heraliz.airstrike.AirstrikeMod;
import fr.heraliz.airstrike.block.SupplyCrateBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, AirstrikeMod.MODID);

    /** Factory (docs/METHODE_CLIENT_SERVEUR.md §5.2): server version plugged in by PartieServeur. */
    public static final UsineBlocEntite<SupplyCrateBlockEntity> SUPPLY_CRATE_USINE = new UsineBlocEntite<>(SupplyCrateBlockEntity::new);

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<SupplyCrateBlockEntity>> SUPPLY_CRATE = BLOCK_ENTITIES.register("supply_crate",
            () -> BlockEntityType.Builder.of(SUPPLY_CRATE_USINE, ModBlocks.SUPPLY_CRATE.get()).build(null));

    private ModBlockEntities() {
    }
}
