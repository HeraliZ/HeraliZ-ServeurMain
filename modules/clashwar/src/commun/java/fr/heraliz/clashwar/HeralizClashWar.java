package fr.heraliz.clashwar;

import com.mojang.logging.LogUtils;
import fr.heraliz.clashwar.reseau.MatchsNet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * HeraliZ ClashWar : le tournoi des clans (élimination, au meilleur des 5 manches) et son petit moteur de matchs
 * (arènes, coffre, kits, HUD, spectateurs). Ne coûte rien hors tournoi : arènes chargées seulement pendant un tournoi.
 * <p>Point d'entrée : les sous-paquets sont branchés par {@link Modules} (classes d'entrée à nom fixe).
 */
@Mod(HeralizClashWar.MODID)
public class HeralizClashWar {
    public static final String MODID = "heraliz_clashwar";
    /** Marque de propriété HeraliZ (anti-vol) : embarquée dans le jar, elle dit à qui l'ouvre à qui ce mod appartient. */
    public static final String PROPRIETE_HERALIZ = "HeraliZ - propriété de HeraliZ (serveur play.heraliz.fr). Mod réservé au serveur HeraliZ ; reproduction ou utilisation ailleurs interdite.";
    public static final Logger LOG = LogUtils.getLogger();

    /** Dimension des arènes du ClashWar (jour fixe, vide, sans monstres). */
    public static final ResourceKey<Level> ARENES = ResourceKey.create(Registries.DIMENSION, id("arenes"));

    public HeralizClashWar() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        // registres (DeferredRegister) de chaque module, puis reseau (ordre des paquets = ordre des modules)
        Modules.construire(modBus);
        MatchsNet.init();
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> Modules.commandes(e.getDispatcher(), e.getBuildContext()));
        // parties coupées (docs/METHODE_CLIENT_SERVEUR.md) : règles et déroulé des matchs (serveur) ; HUD, écrans (client)
        fr.heraliz.clashwar.pont.Parties.serveur(MODID, "fr.heraliz.clashwar.serveur.PartieServeur", modBus);
        fr.heraliz.clashwar.pont.Parties.client(MODID, "fr.heraliz.clashwar.client.PartieClient", modBus);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
