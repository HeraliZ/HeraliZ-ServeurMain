package fr.heraliz.crac.registry;

import fr.heraliz.crac.block.entity.ClaimBlockEntity;
import fr.heraliz.crac.block.entity.CoeurClanBlockEntity;
import fr.heraliz.crac.block.entity.MetalCrateBlockEntity;
import fr.heraliz.crac.build.world.StructureBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityType.Builder;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, "crac");
    public static final RegistryObject<BlockEntityType<StructureBlockEntity>> STRUCTURE = BLOCK_ENTITIES.register(
        "structure", () -> Builder.of(StructureBlockEntity::new, ModBlocks.STRUCTURE.get()).build(null)
    );
    public static final RegistryObject<BlockEntityType<MetalCrateBlockEntity>> METAL_CRATE = BLOCK_ENTITIES.register(
        "metal_crate", () -> Builder.of(MetalCrateBlockEntity::new, ModBlocks.METAL_CRATE.get()).build(null)
    );
    public static final RegistryObject<BlockEntityType<ClaimBlockEntity>> CLAIM_BLOCK = BLOCK_ENTITIES.register(
        "claim_block", () -> Builder.of(ClaimBlockEntity::new, ModBlocks.CLAIM_BLOCK.get()).build(null)
    );

    public static final RegistryObject<BlockEntityType<CoeurClanBlockEntity>> COEUR_CLAN = BLOCK_ENTITIES.register(
        "coeur_clan", () -> Builder.of(CoeurClanBlockEntity::new, ModBlocks.COEUR_CLAN.get()).build(null)
    );

    private ModBlockEntities() {
    }
}
