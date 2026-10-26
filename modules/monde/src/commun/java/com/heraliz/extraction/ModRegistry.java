package com.heraliz.extraction;

import com.heraliz.extraction.entity.ExtractionTerminalEntity;
import com.heraliz.extraction.item.ExtractionTerminalItem;
import com.heraliz.extraction.pont.Usine;
import com.heraliz.extraction.pont.UsineEntite;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModRegistry {
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, HeralizExtraction.MODID);
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, HeralizExtraction.MODID);
    private static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, HeralizExtraction.MODID);

    /** Objet coupé en deux : la pose du terminal (fin de la saisie) est dans ExtractionTerminalItemServeur. */
    public static final Usine<Item.Properties, ExtractionTerminalItem> TERMINAL_ITEM_USINE = new Usine<>(ExtractionTerminalItem::new);
    /** Entité coupée en deux : chronos, barres et téléportation dans ExtractionTerminalEntityServeur. */
    public static final UsineEntite<ExtractionTerminalEntity> TERMINAL_USINE = new UsineEntite<>(ExtractionTerminalEntity::new);

    public static final RegistryObject<Item> EXTRACTION_TERMINAL = ITEMS.register("extraction_terminal",
            () -> TERMINAL_ITEM_USINE.apply(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    // clientTrackingRange(16) = 256 blocs : le terminal est envoyé aux clients de très loin
    // (limité en pratique par la view-distance du serveur).
    public static final RegistryObject<EntityType<ExtractionTerminalEntity>> EXTRACTION_TERMINAL_ENTITY =
            ENTITIES.register("extraction_terminal", () -> EntityType.Builder
                    .<ExtractionTerminalEntity>of(TERMINAL_USINE, MobCategory.MISC)
                    .sized(1.0F, 0.6F)
                    .clientTrackingRange(16)
                    .updateInterval(20)
                    .fireImmune()
                    .noSummon()
                    .build("extraction_terminal"));

    // "true" = ignore la limite de distance des particules (32 blocs) : visibles de loin.
    public static final RegistryObject<SimpleParticleType> EXTRACTION_SMOKE =
            PARTICLES.register("extraction_smoke", () -> new SimpleParticleType(true));
    public static final RegistryObject<SimpleParticleType> EXTRACTION_SPARK =
            PARTICLES.register("extraction_spark", () -> new SimpleParticleType(true));
    public static final RegistryObject<SimpleParticleType> EXTRACTION_RING =
            PARTICLES.register("extraction_ring", () -> new SimpleParticleType(false));

    private ModRegistry() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        PARTICLES.register(modBus);
    }
}
