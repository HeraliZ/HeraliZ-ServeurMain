package fr.heraliz.recolte.entites;

import fr.heraliz.recolte.HeralizRecolte;
import fr.heraliz.recolte.pont.UsineEntite;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Entités de HeraliZ Récolte. Identifiants FIGÉS (posées sur la carte) : {@code heraliz_recolte:gisement},
 * {@code heraliz_recolte:arbre}. Usines (docs/METHODE_CLIENT_SERVEUR.md §4) : la partie serveur branche
 * {@code GisementServeur} et {@code ArbreServeur} ; le joueur reçoit toujours la version commune (affichage).
 * Jamais d'invocation à la main ({@code /summon}) : une entité sans place connue du serveur est retirée ; le staff
 * passe par {@code /recolte placer}.
 */
public final class EntitesRecolte {
    private EntitesRecolte() {
    }

    public static final DeferredRegister<EntityType<?>> ENTITES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, HeralizRecolte.MODID);

    public static final UsineEntite<Gisement> GISEMENT_USINE = new UsineEntite<>(Gisement::new);
    public static final UsineEntite<Arbre> ARBRE_USINE = new UsineEntite<>(Arbre::new);

    /** Suivi par les joueurs à 64 blocs (4 tronçons) ; immobile : position renvoyée rarement, état dès qu'il change. */
    public static final RegistryObject<EntityType<Gisement>> GISEMENT = ENTITES.register("gisement",
            () -> EntityType.Builder.of(GISEMENT_USINE, MobCategory.MISC).sized(2.1f, 1.7f).fireImmune().noSummon()
                    .clientTrackingRange(4).updateInterval(20).build("gisement"));
    /** Suivi par les joueurs à 96 blocs (6 tronçons). */
    public static final RegistryObject<EntityType<Arbre>> ARBRE = ENTITES.register("arbre",
            () -> EntityType.Builder.of(ARBRE_USINE, MobCategory.MISC).sized(0.65f, 12.0f).fireImmune().noSummon()
                    .clientTrackingRange(6).updateInterval(20).build("arbre"));

    public static void enregistrer(IEventBus bus) {
        ENTITES.register(bus);
    }
}
