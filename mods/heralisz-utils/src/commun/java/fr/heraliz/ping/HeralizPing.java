package fr.heraliz.ping;

import com.mojang.logging.LogUtils;
import fr.heraliz.ping.net.PingNet;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * HeraliZ Ping (remplace Ping Wheel) : appui bref sur la touche = marquer ce qu'on vise, maintien = roue des 6 types
 * ({@link PingType}). Le client propose, le serveur verifie (cadence, portee, ligne de vue) et relaie le ping au seul
 * cercle du joueur ({@code fr.heraliz.ping.server.Cercles} : equipe en match, sinon clan, sinon groupe, sinon lui
 * seul) ; jamais a tout le serveur. Ce fichier branche les sons, le reseau et la config ; les evenements serveur sont
 * inscrits par la partie serveur ({@code serveur.PartieServeur}, a la meme place) ; le client est dans
 * {@code fr.heraliz.ping.client} (abonnements {@code Dist.CLIENT}).
 */
@Mod(HeralizPing.MODID)
public final class HeralizPing {
    public static final String MODID = "heraliz_ping";
    public static final Logger LOG = LogUtils.getLogger();

    public HeralizPing() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        PingSons.SONS.register(modBus);
        PingNet.enregistrer();
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, PingConfig.SERVEUR_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, PingConfig.CLIENT_SPEC);
        // parties du jar fusionné HeraliZ Utils (docs/METHODE_CLIENT_SERVEUR.md §4) ; la partie serveur inscrit, à cette
        // même place, les écouteurs serveur de l'ancien @Mod (commandes, tick, connexion, déconnexion, arrêt)
        fr.heralisz.utils.pont.Parties.serveur(MODID, "fr.heraliz.ping.serveur.PartieServeur", modBus);
        fr.heralisz.utils.pont.Parties.client(MODID, "fr.heraliz.ping.client.PartieClient", modBus);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
