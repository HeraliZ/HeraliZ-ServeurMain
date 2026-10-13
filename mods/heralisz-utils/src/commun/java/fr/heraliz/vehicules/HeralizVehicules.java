package fr.heraliz.vehicules;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * HeraliZ Véhicules : les véhicules repris de Fuse (buggy MRZR4, moto, camion benne, fourgon Hiace), leur essence
 * (jerrican), le chalumeau de réparation et le parachute dorsal. Le moteur est Limitless Vehicle (mod « ywzj_vehicle »,
 * GPL-3.0), installé à côté dans la version réduite par Fuse ; ce mod-ci le branche sur le serveur :
 * <ul>
 *   <li>{@link Contenu} : onglet créatif, liste des véhicules gardés ;</li>
 *   <li>{@link ContenuRetire} : objets du moteur qu'on ne garde pas (drones, munitions, boîte à figurines…) cachés et effacés ;</li>
 *   <li>{@code Regles} : pas de véhicule dans les matchs ni les bunkers, pas de véhicule sorti en plein combat, aucun bloc
 *   cassé par les explosions de véhicules, descente du véhicule avant tout changement de dimension ;</li>
 *   <li>{@code Commandes} : {@code /vehicules liste | donner} (réponses en message système : retours de commande coupés
 *   sur le serveur) ;</li>
 *   <li>{@code CoffreRetire}, {@code SuiviVehicules} : plus de coffre dans les véhicules (contenu déjà rangé posé au
 *   sol) ;</li>
 *   <li>{@link PointsDeVie} : PV max divisés par 5 (réglage pv_pourcent), véhicules déjà posés compris.</li>
 * </ul>
 * Jar fusionné HeraliZ Utils (docs/METHODE_CLIENT_SERVEUR.md) : ici le commun (liste des véhicules, onglet, réglages,
 * filtre des onglets du moteur) ; {@code Regles}, {@code Commandes} et l'effacement des objets retirés
 * ({@code ContenuRetireServeur}) sont la partie serveur, branchée par {@code serveur.PartieServeur} ; les touches, la
 * partie client ({@code client.PartieClient}).
 */
@Mod(HeralizVehicules.MODID)
public final class HeralizVehicules {
    public static final String MODID = "heraliz_vehicules";
    public static final Logger LOG = LogUtils.getLogger();

    public HeralizVehicules() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, VehiculesConfig.SPEC);
        // (ancien onglet heraliz_vehicules:vehicules : groupe « Véhicules » de l'onglet commun HeraliZ · Utils, déclaré dans
        // l'ordre par le porteur, HeraliszUtils, avec Contenu.VEHICULES et Contenu.outils())
        modBus.addListener(ContenuRetire::onBuildContents);
        // logique serveur (Regles::onConfig, objets retirés effacés, règles, commandes) : partie serveur
        // (HeralizVehiculesServeur.init, appelé par serveur.PartieServeur ci-dessous), dans l'ordre d'origine
        // parties du jar fusionné HeraliZ Utils (docs/METHODE_CLIENT_SERVEUR.md §4)
        fr.heralisz.utils.pont.Parties.serveur(MODID, "fr.heraliz.vehicules.serveur.PartieServeur", modBus);
        fr.heralisz.utils.pont.Parties.client(MODID, "fr.heraliz.vehicules.client.PartieClient", modBus);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
