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
 *   <li>{@code CoffresVehicules} (27/11) : le coffre HeraliZ des véhicules (Maj + clic droit ; moto 27 cases, buggy 72,
 *   fourgon 90, camion 144), gardé avec le véhicule, posé au sol quand il est détruit ; menu
 *   {@link fr.heraliz.vehicules.menu.CoffreVehiculeMenu}, écran {@code client.EcranCoffreVehicule} ;</li>
 *   <li>{@link PointsDeVie} : PV max divisés par 5 (réglage pv_pourcent), véhicules déjà posés compris ;</li>
 *   <li>{@code Passagers} (11/11) : un joueur dans ou sur un véhicule prend les dégâts comme à pied (réglages
 *   passagers_proteges et balles_traversent) ;</li>
 *   <li>{@code SortieSure} (14/11) : on ne sort jamais d'un véhicule dans un bloc ni de l'autre côté d'un mur (réglage
 *   sortie_sure ; vérifiée juste après la sortie) ;</li>
 *   <li>{@code SortieVitesse} (14/11) : pas de sortie volontaire d'un véhicule lancé (réglage sortie_vitesse_max_kmh,
 *   15 km/h ; mixin serveur VehiculeMessageMixin) ;</li>
 *   <li>{@code ArretSansConducteur} (14/11) : sans conducteur, le véhicule freine jusqu'à l'arrêt et coupe son moteur
 *   (réglage arret_sans_conducteur ; événement VehicleMoveEvent du moteur, sans mixin) ;</li>
 *   <li>{@code client.SortieAccroupi} (14/11) : on sort d'un véhicule avec la touche S'accroupir du joueur, par le paquet
 *   de sortie du moteur ;</li>
 *   <li>{@code client.IndicateurDegats} : plus de panneau des dégâts quand on touche un véhicule.</li>
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
        // 27/11 : menu du coffre des véhicules (heraliz_vehicules:coffre), chez les joueurs et sur le serveur
        fr.heraliz.vehicules.menu.MenusVehicules.enregistrer(modBus);
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
