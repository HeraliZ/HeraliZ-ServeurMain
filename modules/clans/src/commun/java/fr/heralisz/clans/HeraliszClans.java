package fr.heralisz.clans;

import com.mojang.logging.LogUtils;
import fr.heraliz.clansetbases.HeralizClansetbases;
import fr.heraliz.clansetbases.pont.Parties;
import fr.heralisz.clans.network.ClansNet;
import fr.heralisz.clans.registry.ClansRegistry;
import fr.heralisz.clans.registry.Sons;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * HeraliZ Clans : clans, banniere, banque, niveaux et quetes, territoires a capturer et largages de ravitaillement.
 * Point d'entree du mod : bus, config, reseau, registres, evenements serveur.
 */
@Mod(HeraliszClans.MODID)
public class HeraliszClans {
    public static final String MODID = "heralisz_clans";
    public static final Logger LOG = LogUtils.getLogger();

    public HeraliszClans() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ClansRegistry.register(modBus);
        Sons.SONS.register(modBus);
        ClansNet.register();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ClansConfig.SPEC);
        // onglet creatif commun « HeraliZ · Clans et bases » : drapeau de territoire et caisse de ravitaillement
        HeralizClansetbases.ONGLET.ajouter("clans", "onglet.heraliz_clansetbases.clans", () -> new ItemStack(ClansRegistry.DRAPEAU_ITEM.get()), out -> {
            out.accept(ClansRegistry.DRAPEAU_ITEM.get());
            out.accept(ClansRegistry.CAISSE_ITEM.get());
        });
        // store, commandes, regles de jeu (stats, chat de clan, liste Tab, synchronisation groupee, quetes), territoires
        // et largages : partie serveur ; ecrans, banniere, HUD, touches, rendus des territoires : partie client
        // (docs/METHODE_CLIENT_SERVEUR.md §4)
        Parties.serveur(MODID, "fr.heralisz.clans.serveur.PartieServeur", modBus);
        Parties.client(MODID, "fr.heralisz.clans.client.PartieClient", modBus);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
