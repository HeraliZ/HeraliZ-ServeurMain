package fr.heraliz.corps.registre;

import fr.heraliz.corps.HeralizCorps;
import fr.heraliz.corps.entite.AncienCorps;
import fr.heraliz.corps.entite.CorpsEntity;
import fr.heraliz.corps.menu.CorpsMenu;
import fr.heralisz.utils.pont.UsineEntite;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Registres du mod : l'entite « corps » et le menu de fouille. Ni objet ni recette (le butin et l'artisanat viendront plus tard). */
public final class Registres {
    private Registres() {
    }

    public static final DeferredRegister<EntityType<?>> ENTITES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, HeralizCorps.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, HeralizCorps.MODID);

    /**
     * Usines des entites coupees en deux (docs/METHODE_CLIENT_SERVEUR.md §4) : version neutre ici, version serveur
     * (CorpsEntityServeur, AncienCorpsServeur) branchee par serveur.PartieServeur.
     */
    public static final UsineEntite<CorpsEntity> CORPS_USINE = new UsineEntite<>(CorpsEntity::new);
    public static final UsineEntite<AncienCorps> ANCIEN_USINE = new UsineEntite<>(AncienCorps::new);

    /**
     * {@code heraliz_corps:corpse}. Boite qui suit le corps allonge (1 x 1,9 x 0,45, tournee comme lui : voir
     * CorpsEntity.makeBoundingBox ; la largeur declaree ici ne sert qu'avant le premier placement) ; suivi par les clients
     * a 4 chunks (64 blocs) au plus ;
     * position renvoyee toutes les 100 ticks seulement (un corps ne bouge pas ; pendant une chute, a chaque tick) ;
     * insensible au feu ; pas de /summon (un corps vide n'a pas de sens).
     */
    public static final RegistryObject<EntityType<CorpsEntity>> CORPS = ENTITES.register("corpse",
            () -> EntityType.Builder.<CorpsEntity>of(CORPS_USINE, MobCategory.MISC)
                    .sized(1.0F, 0.45F)
                    .clientTrackingRange(4)
                    .updateInterval(100)
                    .setShouldReceiveVelocityUpdates(false)
                    .fireImmune()
                    .noSummon()
                    .setCustomClientFactory((spawn, level) -> CORPS_USINE.create(Registres.CORPS.get(), level))
                    .build(HeralizCorps.MODID + ":corpse"));

    /**
     * Ancien corps du mod « corpse » ({@code corpse:corpse}), converti en corps HeraliZ a son chargement. Enregistre
     * seulement si l'ancien mod n'est plus la (voir {@link #enregistrer}).
     */
    public static final DeferredRegister<EntityType<?>> ANCIENS = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, "corpse");
    public static final RegistryObject<EntityType<AncienCorps>> ANCIEN = ANCIENS.register("corpse",
            () -> EntityType.Builder.<AncienCorps>of(ANCIEN_USINE, MobCategory.MISC)
                    .sized(2.0F, 0.5F)
                    .clientTrackingRange(0)
                    .noSummon()
                    .build("corpse:corpse"));

    /** Vrai si le convertisseur des anciens corps est actif (ancien mod absent). */
    public static boolean convertisseur;

    public static void enregistrer(net.minecraftforge.eventbus.api.IEventBus bus) {
        ENTITES.register(bus);
        MENUS.register(bus);
        convertisseur = !net.minecraftforge.fml.ModList.get().isLoaded("corpse");
        if (convertisseur) ANCIENS.register(bus);
    }

    public static final RegistryObject<MenuType<CorpsMenu>> MENU = MENUS.register("fouille", () -> IForgeMenuType.create(CorpsMenu::client));
}
