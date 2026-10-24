package fr.heraliz.corps;

import com.mojang.logging.LogUtils;
import fr.heraliz.corps.registre.Registres;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * HeraliZ Corps : a la vraie mort d'un joueur (hors match), tout ce qu'il portait reste sur un corps allonge au sol, avec
 * son skin. Remplace le mod tiers « corpse » (modId {@code corpse}).
 * <ul>
 *     <li>capture de la mort : {@code mort.CaptureMort}, partie serveur (inventaire, armure, main gauche, Curios, sac equipe
 *     avec son contenu) ;</li>
 *     <li>le corps : {@link fr.heraliz.corps.entite.CorpsEntity} (aucun travail par tick au repos, sauvegarde reduite) ;</li>
 *     <li>la fouille : {@link fr.heraliz.corps.menu.CorpsMenu} (transferts cote serveur seulement) ;</li>
 *     <li>les commandes d'administration {@code /corps liste|tp|vider|supprimer} : {@code commande.CommandeCorps}, partie
 *     serveur.</li>
 * </ul>
 * Liens souples (jamais obligatoires) : Curios, CaraCrea, HeraliZ Clans, Reanimation.
 */
@Mod(HeralizCorps.MODID)
public final class HeralizCorps {
    public static final String MODID = "heraliz_corps";
    public static final Logger LOG = LogUtils.getLogger();

    public HeralizCorps() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        Registres.enregistrer(bus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, CorpsConfig.SERVEUR_SPEC, MODID + "-server.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, CorpsConfig.CLIENT_SPEC, MODID + "-client.toml");
        bus.addListener(CorpsConfig::surChargement);
        bus.addListener(CorpsConfig::surRechargement);
        // parties du jar fusionné HeraliZ Utils (docs/METHODE_CLIENT_SERVEUR.md §4) ; la partie serveur inscrit, à cette
        // même place, la capture de la mort (CaptureMort) et les commandes /corps
        fr.heralisz.utils.pont.Parties.serveur(MODID, "fr.heraliz.corps.serveur.PartieServeur", bus);
        fr.heralisz.utils.pont.Parties.client(MODID, "fr.heraliz.corps.client.PartieClient", bus);
    }

    public static ResourceLocation id(String chemin) {
        return new ResourceLocation(MODID, chemin);
    }
}
