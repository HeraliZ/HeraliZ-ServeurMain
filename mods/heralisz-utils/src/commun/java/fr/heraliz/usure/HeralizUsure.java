package fr.heraliz.usure;

import com.mojang.logging.LogUtils;
import fr.heraliz.usure.regles.Regles;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

import java.util.function.Supplier;

/**
 * HeraliZ Usure des armes : remplace Gun Durability (gundb) pour les armes TaCZ du serveur.
 * <ul>
 *   <li>{@link Tirs} : usure a chaque tir (categorie, pluie, eau, silencieux, accessoires), sans enrayage ni malus,
 *   arme ruinee qui ne tire plus — le serveur seul decide ;</li>
 *   <li>{@link KitReparation} / {@link Reparation} : kits par niveau, reparation partielle, etat maximal qui baisse ;</li>
 *   <li>{@link Usure} : l'etat dans le NBT de l'arme, conversion des armes marquees par gundb ;</li>
 *   <li>{@code UsureConfig} / {@code ReglesServeur} : reglages serveur (fichier {@code serverconfig/heraliz_usure-server.toml}
 *   relu tout seul, ecran {@code /usure config}) ;</li>
 *   <li>{@code Commandes} : {@code /usure voir | reparer | definir | config} ;</li>
 *   <li>client ({@code client.ClientUsure}) : infobulle, petit indicateur de l'arme en main en bas a gauche de l'ecran
 *   (alerte « ruinee ») ; rien sur l'icone de l'arme.</li>
 * </ul>
 * Aucune usure en match (drapeau {@code heralisz:en_match} de HeraliZ Matchs, armes pretees {@code HeraliszMatch}),
 * dans les dimensions des matchs et des zombies ; couteaux et armes des zombies exclus. Camos et Pack-a-Punch intacts.
 * <p>Jar fusionne HeraliZ Utils (docs/METHODE_CLIENT_SERVEUR.md) : ici le commun (objets, reseau, etat des armes,
 * regles lues des deux cotes) ; la logique serveur ({@code TirsServeur}, {@code ReparationServeur}, {@code ReglesServeur},
 * {@code UsureConfig}, {@code Commandes}, conversion Gun Durability de {@code HeralizUsureServeur}) est branchee par
 * {@code serveur.PartieServeur}, l'affichage par {@code client.PartieClient}.
 */
@Mod(HeralizUsure.MODID)
public final class HeralizUsure {
    public static final String MODID = "heraliz_usure";
    public static final Logger LOG = LogUtils.getLogger();
    /** Drapeau booleen du persistentData pose par HeraliZ Matchs pendant un match (aucune dependance de compilation). */
    public static final String DRAPEAU_EN_MATCH = "heralisz:en_match";

    /**
     * Regles vues par le client (remplacees par celles du client a son initialisation). A l'origine {@code ReglesServeur::get}
     * par defaut : la partie serveur le branche (serveur.PartieServeur, AVANT la partie client qui le remplace ensuite) ;
     * sans aucune partie, les regles par defaut.
     */
    private static Supplier<Regles> reglesClient = Regles::defauts;

    public HeralizUsure() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        Contenu.enregistrer(modBus);
        Reseau.enregistrer();
        // ecouteurs serveur (tirs, reglages, conversion Gun Durability, commandes) : partie serveur
        // (HeralizUsureServeur.init, appele par serveur.PartieServeur ci-dessous), dans l'ordre d'origine
        // (ancien onglet heraliz_usure:usure : groupe « Usure des armes » de l'onglet commun HeraliZ · Utils, déclaré dans
        // l'ordre par le porteur, HeraliszUtils, avec Contenu.KITS)
        // parties du jar fusionné HeraliZ Utils (docs/METHODE_CLIENT_SERVEUR.md §4)
        fr.heralisz.utils.pont.Parties.serveur(MODID, "fr.heraliz.usure.serveur.PartieServeur", modBus);
        fr.heralisz.utils.pont.Parties.client(MODID, "fr.heraliz.usure.client.PartieClient", modBus);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }

    public static Regles reglesClient() {
        return reglesClient.get();
    }

    public static void reglesClient(Supplier<Regles> s) {
        reglesClient = s;
    }
}
