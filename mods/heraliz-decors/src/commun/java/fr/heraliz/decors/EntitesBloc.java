package fr.heraliz.decors;

import fr.heraliz.decors.effet.CascadeBE;
import fr.heraliz.decors.effet.RayonBE;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Entités de bloc des grands effets (sans tick : elles ne servent qu'à porter les mesures et au dessin client). */
public final class EntitesBloc {
    public static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,
            HeralizDecors.MODID);

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<CascadeBE>> CASCADE = TYPES.register("grande_cascade",
            () -> BlockEntityType.Builder.of(CascadeBE::new, Blocs.GRANDE_CASCADE.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<RayonBE>> RAYON = TYPES.register("rayon_soleil",
            () -> BlockEntityType.Builder.of(RayonBE::new, Blocs.RAYON_SOLEIL.get()).build(null));

    private EntitesBloc() {}
}
