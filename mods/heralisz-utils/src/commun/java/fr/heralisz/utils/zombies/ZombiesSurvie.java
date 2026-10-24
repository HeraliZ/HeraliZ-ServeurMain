package fr.heralisz.utils.zombies;

import fr.heralisz.utils.HeraliszUtils;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Zombies de la survie (2.18.0, maquettes validees) : le zombie de Minecraft (memes regles, IA, sons, vitesse), avec un
 * corps de joueur, une machoire 3D qui s'ouvre et une tenue selon l'endroit : ville (5 tenues tirees au hasard),
 * hazmat (zones contaminees), arctique, militaire, prisonnier. Le serveur (serveur/zombies/SpawnZombiesServeur) remplace
 * les zombies de Minecraft qui apparaissent sur la carte par celui de l'endroit. Tenue = {@link ZombieSurvie#VARIANTE}.
 */
public final class ZombiesSurvie {
    private ZombiesSurvie() {
    }

    /** Tenues : 0 a 4 ville, puis les zones. L'ordre ne change jamais (gardé dans les mondes). */
    public enum Tenue {
        VILLE_0, VILLE_1, VILLE_2, VILLE_3, VILLE_4, HAZMAT, ARCTIQUE, MILITAIRE, PRISONNIER;

        public static final int VILLES = 5;
        /** values() recopie le tableau à chaque appel ; de() est lu à chaque image pour chaque zombie dessiné. */
        private static final Tenue[] TOUTES = values();

        public static Tenue de(int i) {
            return TOUTES[Math.floorMod(i, TOUTES.length)];
        }
    }

    public static final DeferredRegister<EntityType<?>> ENTITES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, HeraliszUtils.MODID);
    public static final RegistryObject<EntityType<ZombieSurvie>> ZOMBIE = ENTITES.register("zombie_survie",
            () -> EntityType.Builder.<ZombieSurvie>of(ZombieSurvie::new, MobCategory.MONSTER).sized(0.6f, 1.95f)
                    .clientTrackingRange(8).build("zombie_survie"));

    public static void init(IEventBus modBus) {
        ENTITES.register(modBus);
        modBus.addListener(ZombiesSurvie::attributs);
    }

    private static void attributs(EntityAttributeCreationEvent e) {
        e.put(ZOMBIE.get(), Zombie.createAttributes().build());
    }
}
