package fr.heralisz.economie;

import com.mojang.logging.LogUtils;
import fr.heraliz.clansetbases.pont.Parties;
import fr.heralisz.economie.network.EconomieNet;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * HeraliZ Économie : la monnaie du serveur (les « hérals »). Comptes des joueurs, paiements, classement, journal,
 * HUD discret, API statique pour les autres mods ({@link fr.heralisz.economie.api.EconomieApi}). Point d'entree : reseau
 * et parties (la config est declaree par la partie serveur).
 */
@Mod(HeraliszEconomie.MODID)
public class HeraliszEconomie {
    public static final String MODID = "heralisz_economie";
    public static final Logger LOG = LogUtils.getLogger();

    public HeraliszEconomie() {
        // heralisz_economie-common.toml : declare par la partie serveur seulement (audit du 02/11, point 11)
        IEventBus mod = FMLJavaModLoadingContext.get().getModEventBus();
        EconomieNet.register();
        // comptes, commandes, evenements serveur et pont Vault : partie serveur (jars -serveur et -complet) ;
        // HUD du solde : partie client (docs/METHODE_CLIENT_SERVEUR.md §4)
        Parties.serveur(MODID, "fr.heralisz.economie.serveur.PartieServeur", mod);
        Parties.client(MODID, "fr.heralisz.economie.client.PartieClient", mod);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
