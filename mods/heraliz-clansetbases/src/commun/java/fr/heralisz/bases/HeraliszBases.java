package fr.heralisz.bases;

import com.mojang.logging.LogUtils;
import fr.heraliz.clansetbases.HeralizClansetbases;
import fr.heraliz.clansetbases.pont.Parties;
import fr.heralisz.bases.blindage.BlindageRegistry;
import fr.heralisz.bases.network.BasesNet;
import fr.heralisz.bases.registry.BasesRegistry;
import fr.heralisz.bases.registry.Sons;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

/**
 * HeraliZ Bases : un bunker souterrain par clan dans la dimension {@code heralisz_bases:bunkers}, salles a debloquer avec
 * la banque du clan, permissions de base (Perm BASE_* du mod clans), pillages (paquet {@code pillage}).
 * Point d'entree : registres, reseau, config, evenements serveur. Cahier des charges : docs/specs/BASES_SPEC.md.
 */
@Mod(HeraliszBases.MODID)
public class HeraliszBases {
    public static final String MODID = "heralisz_bases";
    public static final Logger LOG = LogUtils.getLogger();

    public HeraliszBases() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        BasesRegistry.register(modBus);
        Sons.SONS.register(modBus);
        BasesNet.register();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, BasesConfig.SPEC);
        // module de pillage (BASES_SPEC §4, §5), partie commune : sa config, les blocs de blindage, ses paquets
        fr.heralisz.bases.pillage.PillageModule.init(modBus);
        // onglet creatif commun « HeraliZ · Clans et bases » : contenu de l'ancien onglet « HeraliZ Blindage »
        HeralizClansetbases.ONGLET.ajouter("bases", "onglet.heraliz_clansetbases.bases",
                () -> new ItemStack(BlindageRegistry.OBJETS_BLINDAGE.get(BlindageRegistry.NIVEAUX - 1).get()), out -> {
                    for (RegistryObject<net.minecraft.world.item.Item> o : BlindageRegistry.OBJETS_BLINDAGE) out.accept(o.get());
                });
        // regles, store, commandes, teleportations, pillage, blindage : partie serveur (dans l'ordre d'origine) ;
        // ecrans, HUD, plan, touches : partie client (docs/METHODE_CLIENT_SERVEUR.md §4)
        Parties.serveur(MODID, "fr.heralisz.bases.serveur.PartieServeur", modBus);
        Parties.client(MODID, "fr.heralisz.bases.client.PartieClient", modBus);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
