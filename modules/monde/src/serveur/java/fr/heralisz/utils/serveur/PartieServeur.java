package fr.heralisz.utils.serveur;

import fr.heralisz.utils.HeraliszUtils;
import fr.heralisz.utils.LootBonusDocumentServeur;
import fr.heralisz.utils.ascenseur.Ascenseur;
import fr.heralisz.utils.ascenseur.CabineServeur;
import fr.heralisz.utils.ascenseur.MachineBlockEntityServeur;
import fr.heralisz.utils.ascenseur.PalierBlockServeur;
import fr.heralisz.utils.cine.CineReseauServeur;
import fr.heralisz.utils.deco.DecoBlocks;
import fr.heralisz.utils.deco.LiquideRadioactifServeur;
import fr.heralisz.utils.pont.Partie;
import fr.heralisz.utils.train.Train;
import fr.heralisz.utils.train.TrainServeur;
import fr.heralisz.utils.train.VoyageurServeur;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie serveur de heralisz_utils (porteur du jar fusionné HeraliZ Utils) : versions serveur des objets et entités à
 * logique (usines, branchées ici pendant le constructeur @Mod, avant les registres), puis la logique serveur de la
 * cinématique et du train (écouteurs dans l'ordre d'origine : cinématique d'abord, train ensuite). Les gardes de
 * l'ascenseur (AscenseurEvenements) s'inscrivent toutes seules (@Mod.EventBusSubscriber).
 */
public final class PartieServeur implements Partie {
    @Override
    public void init(IEventBus busMod) {
        // usines : version serveur dès que cette partie est dans le jar (-serveur, -complet)
        HeraliszUtils.DOCUMENT_USINE.serveur(LootBonusDocumentServeur::new);
        DecoBlocks.LIQUIDE_USINE.serveur(LiquideRadioactifServeur::new);
        Ascenseur.PALIER_USINE.serveur(PalierBlockServeur::new);
        Ascenseur.MACHINE_USINE.serveur(MachineBlockEntityServeur::new);
        Ascenseur.CABINE_USINE.serveur(CabineServeur::new);
        Train.VOYAGEUR_USINE.serveur(VoyageurServeur::new);
        fr.heralisz.utils.spawn.Depart.BORNE_USINE.serveur(fr.heralisz.utils.spawn.BorneDepartServeur::new);
        // logique serveur (à l'origine : CineReseau.init puis Train.init, dans le constructeur @Mod)
        CineReseauServeur.init();
        fr.heralisz.utils.spawn.DepartServeur.init();               // borne de départ, monde du spawn, /depart
        TrainServeur.init(busMod);
        Suicide.init();
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.SERVER,
                fr.heralisz.utils.zombies.ZombiesConfig.SPEC, "heraliz_zombies-server.toml");   // zombies de la survie
        fr.heralisz.utils.coffres.CoffresPersonnels.init();      // planque et coffre VIP (coffres-forts du mod de meubles)
        fr.heralisz.utils.inspecter.Inspecter.init();            // /inspecter (op) : inventaire, sac, coffres, base
        DistanceVue.init();                                      // distance de vue du serveur (heraliz_distances-server.toml)
        fr.heralisz.utils.cycle.CycleServeur.init();             // cycle jour / nuit de la carte (heraliz_cycle-server.toml, /cycle)
    }
}
