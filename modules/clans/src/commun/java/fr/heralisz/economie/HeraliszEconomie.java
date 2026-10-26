package fr.heralisz.economie;

import com.mojang.logging.LogUtils;
import fr.heraliz.clansetbases.pont.Parties;
import fr.heralisz.economie.network.EconomieNet;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * HeraliZ Économie : la monnaie du serveur (les « hérals »). Comptes des joueurs, paiements, classement, journal,
 * HUD discret, API statique pour les autres mods ({@link fr.heralisz.economie.api.EconomieApi}) et pont Vault pour
 * les plugins Bukkit. Point d'entree : config, reseau, evenements serveur.
 */
@Mod(HeraliszEconomie.MODID)
public class HeraliszEconomie {
    public static final String MODID = "heralisz_economie";
    public static final Logger LOG = LogUtils.getLogger();

    public HeraliszEconomie() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, EconomieConfig.SPEC);
        IEventBus mod = FMLJavaModLoadingContext.get().getModEventBus();
        mod.addListener((ModConfigEvent.Loading e) -> {
            if (e.getConfig().getSpec() == EconomieConfig.SPEC) EconomieConfig.convertirAncienNom();
        });
        mod.addListener((ModConfigEvent.Reloading e) -> {
            if (e.getConfig().getSpec() == EconomieConfig.SPEC) EconomieConfig.convertirAncienNom();
        });
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
