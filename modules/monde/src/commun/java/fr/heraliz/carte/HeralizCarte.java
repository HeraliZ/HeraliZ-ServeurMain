package fr.heraliz.carte;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * HeraliZ Carte (jar fusionné HeraliZ Utils) : la carte du monde, ouverte par une touche (M par défaut). Vue de dessus de
 * tout le monde (satellite ou relief, images refaites par outils/carte_monde.py), position et direction du joueur,
 * membres de son clan, territoires des clans, extractions ouvertes, lieux nommés, pings du clan.
 * <p>Ce qui décide reste au serveur : les territoires et les alliés viennent du mod des clans (CarteApi, ce que le
 * joueur a le droit de voir), les extractions du leur (ExtractionApi), les pings posés depuis la carte passent par le
 * serveur des pings (heraliz_ping). Ici : le canal des lieux nommés ({@link CarteNet}) et les réglages du joueur.
 * Les commandes et les lieux gardés avec le monde sont dans la partie serveur ; l'écran dans la partie client.
 */
@Mod(HeralizCarte.MODID)
public final class HeralizCarte {
    public static final String MODID = "heraliz_carte";
    public static final Logger LOG = LogUtils.getLogger();

    public HeralizCarte() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        CarteNet.enregistrer();
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, CarteConfig.CLIENT_SPEC);
        fr.heralisz.utils.pont.Parties.serveur(MODID, "fr.heraliz.carte.serveur.PartieServeur", modBus);
        fr.heralisz.utils.pont.Parties.client(MODID, "fr.heraliz.carte.client.PartieClient", modBus);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
