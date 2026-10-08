package fr.heraliz.wonder.serveur;

import fr.heraliz.wonder.HeralizWonder;
import fr.heraliz.wonder.blundergat.BlundergatServeur;
import fr.heraliz.wonder.jetgun.JetGunServeur;
import fr.heraliz.wonder.paralyzer.ParalyseurServeur;
import fr.heraliz.wonder.pont.Partie;
import fr.heraliz.wonder.sliquifier.SliquifierServeur;
import fr.heraliz.wonder.wintershowl.WintersHowlServeur;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;

/**
 * Partie SERVEUR de HeraliZ Wonder (docs/METHODE_CLIENT_SERVEUR.md §4), chargee par HeralizWonder si elle est dans le jar
 * (-serveur, -complet) : tous les ecouteurs serveur de l'ancien constructeur, dans le meme ordre (tirs, ondes, degats,
 * commandes, Pack-a-Punch, branchements TaCZ cote serveur).
 */
public final class PartieServeur implements Partie {
    @Override
    public void init(IEventBus mod) {
        IEventBus forge = MinecraftForge.EVENT_BUS;
        // Blundergat et Acid Gat
        BlundergatServeur.brancher(mod, forge);
        forge.addListener(Commandes::enregistrer);
        forge.addListener(Foudre::tick);
        forge.addListener(Tonnerre::tick);
        forge.addListener(Degats::recul);
        forge.addListener(Perf::tick);
        forge.addListener(Commandes::stressTick);
        forge.addListener(NomsPap::tick);
        // Jet Gun
        JetGunServeur.brancher(mod, forge);
        if (ModList.get().isLoaded("tacz")) {
            TaczServeur.brancher(forge);
            mod.addListener(TaczServeur::config);
            HeralizWonder.LOG.info("[wonder] TaCZ present : tirs des Wonder Weapons branches");
        } else {
            HeralizWonder.LOG.warn("[wonder] TaCZ absent : rien a faire");
        }
        // Winter's Howl, Sliquifier, Paralyzer
        WintersHowlServeur.brancher(mod, forge);
        SliquifierServeur.brancher(mod, forge);
        ParalyseurServeur.brancher(mod, forge);
    }
}
