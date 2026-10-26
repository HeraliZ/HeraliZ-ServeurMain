package fr.heraliz.casino.serveur;

import fr.heraliz.casino.block.MachinesServeur;
import fr.heraliz.casino.block.entity.BlackjackBlockEntityServeur;
import fr.heraliz.casino.block.entity.CashierBlockEntityServeur;
import fr.heraliz.casino.block.entity.CrashBlockEntityServeur;
import fr.heraliz.casino.block.entity.DuelsServeur;
import fr.heraliz.casino.block.entity.HorseRaceBlockEntityServeur;
import fr.heraliz.casino.block.entity.LeaderboardBlockEntityServeur;
import fr.heraliz.casino.block.entity.MinesBlockEntityServeur;
import fr.heraliz.casino.block.entity.NavalBlockEntityServeur;
import fr.heraliz.casino.block.entity.PlinkoBlockEntityServeur;
import fr.heraliz.casino.block.entity.RouletteBlockEntityServeur;
import fr.heraliz.casino.block.entity.RoundsServeur;
import fr.heraliz.casino.block.entity.RpsBlockEntityServeur;
import fr.heraliz.casino.block.entity.SlotBlockEntityServeur;
import fr.heraliz.casino.command.ZoneCommand;
import fr.heraliz.casino.economy.EconomieServeurPont;
import fr.heraliz.casino.entity.GuardEntityServeur;
import fr.heraliz.casino.item.DirectorTabletItem;
import fr.heraliz.casino.machine.MachineType;
import fr.heraliz.casino.net.CasinoNet;
import fr.heraliz.casino.net.ReceptionServeur;
import fr.heraliz.casino.pont.Partie;
import fr.heraliz.casino.registry.ModRegistry;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie serveur de Heraliz Casino (docs/METHODE_CLIENT_SERVEUR.md) : tirages, mises, gains, guichet, regles des
 * machines (versions serveur des entites de bloc), videurs, zone du casino, commandes et reglages des operateurs.
 * Commandes, chargement des reglages, connexion des joueurs et zone : CommonEventsServeur et ZoneManager s'inscrivent
 * seuls (@Mod.EventBusSubscriber, presents seulement dans ce jar).
 */
public final class PartieServeur implements Partie {
    @Override
    public void init(IEventBus busMod) {
        // logique commune des machines, des jeux en tours et des duels ; pont vers HeraliZ Economie (herals)
        MachinesServeur.installer();
        RoundsServeur.installer();
        DuelsServeur.installer();
        EconomieServeurPont.installer();

        // versions serveur des machines (avant les evenements d'enregistrement)
        MachineType.CAISSE.usine.serveur(CashierBlockEntityServeur::new);
        MachineType.ROTTEN.usine.serveur(SlotBlockEntityServeur::new);
        MachineType.OLYMPE.usine.serveur(SlotBlockEntityServeur::new);
        MachineType.LOUP.usine.serveur(SlotBlockEntityServeur::new);
        MachineType.NICHE.usine.serveur(SlotBlockEntityServeur::new);
        MachineType.PLINKO.usine.serveur(PlinkoBlockEntityServeur::new);
        MachineType.BLACKJACK.usine.serveur(BlackjackBlockEntityServeur::new);
        MachineType.ROULETTE.usine.serveur(RouletteBlockEntityServeur::new);
        MachineType.CRASH.usine.serveur(CrashBlockEntityServeur::new);
        MachineType.MINES.usine.serveur(MinesBlockEntityServeur::new);
        MachineType.COURSE.usine.serveur(HorseRaceBlockEntityServeur::new);
        MachineType.PIERRE_FEUILLE_CISEAUX.usine.serveur(RpsBlockEntityServeur::new);
        MachineType.BATAILLE_NAVALE.usine.serveur(NavalBlockEntityServeur::new);
        MachineType.TABLEAU.usine.serveur(LeaderboardBlockEntityServeur::new);
        MachineType.TABLEAU_PERDANTS.usine.serveur(LeaderboardBlockEntityServeur::new);
        MachineType.TABLEAU_PARIEURS.usine.serveur(LeaderboardBlockEntityServeur::new);
        for (MachineType t : MachineType.values()) {
            if (!t.usine.serveurBranche()) throw new IllegalStateException("[heralizcasino] machine sans version serveur : " + t.id);
        }

        // videurs (IA, poste, dialogue, retrait par un operateur)
        ModRegistry.GUARD_USINE.serveur(GuardEntityServeur::new);
        // Tablette du Directeur : coins de la zone du casino
        DirectorTabletItem.COIN_SERVEUR = ZoneCommand::corner;
        // paquets recus des joueurs (reglages, statistiques, entree payee)
        CasinoNet.RECEPTION_SERVEUR.brancher(ReceptionServeur::recevoir);
    }
}
