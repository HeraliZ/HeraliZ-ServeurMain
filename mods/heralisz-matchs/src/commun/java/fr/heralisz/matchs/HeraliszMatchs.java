package fr.heralisz.matchs;

import com.mojang.logging.LogUtils;
import fr.heralisz.matchs.reseau.MatchsNet;
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
 * HeraliZ Matchs : moteur commun (coffre, arenes, groupes, kits, HUD), ClashWar, Duels et Zombies.
 * <p>Point d'entree : les sous-paquets ne sont pas cites ici en dur ; ils sont branches par {@link Modules} (classes
 * d'entree a nom fixe, appelees par reflexion tolerante : un module absent est simplement ignore). Ainsi chaque agent
 * ajoute son module sans toucher a ce fichier.
 */
@Mod(HeraliszMatchs.MODID)
public class HeraliszMatchs {
    public static final String MODID = "heralisz_matchs";
    /** Marque de propriété HeraliZ (anti-vol) : embarquée dans le jar, elle dit à qui l'ouvre à qui ce mod appartient. */
    public static final String PROPRIETE_HERALIZ = "HeraliZ - propriété de HeraliZ (serveur play.heraliz.fr). Mod réservé au serveur HeraliZ ; reproduction ou utilisation ailleurs interdite.";
    public static final Logger LOG = LogUtils.getLogger();

    /** Dimension des arenes ClashWar et Duels (jour fixe, vide, sans monstres). */
    public static final ResourceKey<Level> ARENES = ResourceKey.create(Registries.DIMENSION, id("arenes"));
    /** Dimension des cartes Zombies (nuit fixe, vide). */
    public static final ResourceKey<Level> ZOMBIES = ResourceKey.create(Registries.DIMENSION, id("zombies"));

    public HeraliszMatchs() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        // registres (DeferredRegister) de chaque module, puis reseau (ordre des paquets = ordre des modules)
        Modules.construire(modBus);
        fr.heralisz.matchs.zombies.contenu.MateriauxGiant.enregistrer(modBus);   // materiaux de The Giant (0.2.3)
        MatchsNet.init();
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> Modules.commandes(e.getDispatcher(), e.getBuildContext()));
        // parties coupées (docs/METHODE_CLIENT_SERVEUR.md) : règles et déroulé des matchs (serveur) ; HUD, écrans, rendus (client)
        fr.heralisz.matchs.pont.Parties.serveur(MODID, "fr.heralisz.matchs.serveur.PartieServeur", modBus);
        fr.heralisz.matchs.pont.Parties.client(MODID, "fr.heralisz.matchs.client.PartieClient", modBus);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
