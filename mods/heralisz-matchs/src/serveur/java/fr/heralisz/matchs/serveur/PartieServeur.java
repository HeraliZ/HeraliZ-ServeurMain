package fr.heralisz.matchs.serveur;

import fr.heralisz.matchs.clashwar.net.ReceptionCwPaquetsServeur;
import fr.heralisz.matchs.duels.CibleEntrainementServeur;
import fr.heralisz.matchs.duels.Registres;
import fr.heralisz.matchs.duels.killcam.ReceptionPaquetsKillcamServeur;
import fr.heralisz.matchs.duels.net.ReceptionPaquetsDuelsServeur;
import fr.heralisz.matchs.moteur.api.Coffre;
import fr.heralisz.matchs.moteur.api.Instance;
import fr.heralisz.matchs.moteur.api.Kit;
import fr.heralisz.matchs.moteur.api.Partie;
import fr.heralisz.matchs.moteur.arenes.ArenesService;
import fr.heralisz.matchs.moteur.coffre.Coffres;
import fr.heralisz.matchs.moteur.combat.CombatService;
import fr.heralisz.matchs.moteur.combat.RestrictionsMatch;
import fr.heralisz.matchs.moteur.compat.Compat;
import fr.heralisz.matchs.moteur.kits.ChoixService;
import fr.heralisz.matchs.moteur.kits.Distribution;
import fr.heralisz.matchs.moteur.net.ReceptionPaquetsMoteurServeur;
import fr.heralisz.matchs.moteur.spectateur.Spectateurs;
import fr.heralisz.matchs.pont.Services;
import fr.heralisz.matchs.reseau.MatchsNet;
import fr.heralisz.matchs.zombies.jeu.PartiesZombies;
import fr.heralisz.matchs.zombies.jeu.net.ReceptionPaquetsZombiesServeur;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie serveur de HeraliZ Matchs (docs/METHODE_CLIENT_SERVEUR.md §4) : moteur des matchs (arènes, coffre, combat,
 * kits, groupes, spectateurs), ClashWar, duels et killcam, zombies (parties, manches, boîte, Pack-a-Punch, quêtes).
 * Les modules s'inscrivent par Modules.SERVEURS (écouteurs, commandes) ; ici : services des API communes, paquets
 * client -> serveur, cible d'entraînement.
 */
public final class PartieServeur implements fr.heralisz.matchs.pont.Partie {
    @Override
    public void init(IEventBus busMod) {
        Services.MOTEUR = new Moteur();
        Registres.CIBLE_USINE.serveur(CibleEntrainementServeur::new);
        MatchsNet.RECEPTION_SERVEUR.brancher(PartieServeur::recevoir);
    }

    private static void recevoir(MatchsNet.Recu r) {
        if (ReceptionPaquetsMoteurServeur.recevoir(r)) return;
        if (ReceptionCwPaquetsServeur.recevoir(r)) return;
        if (ReceptionPaquetsDuelsServeur.recevoir(r)) return;
        if (ReceptionPaquetsKillcamServeur.recevoir(r)) return;
        if (ReceptionPaquetsZombiesServeur.recevoir(r)) return;
        r.ctx().get().setPacketHandled(true);
    }

    /** Corps d'origine des API communes (moteur/api, zombies/jeu/api). */
    private static final class Moteur implements Services.Moteur {
        @Override
        public Instance arenesReserver(MinecraftServer s, String carte) {
            return ArenesService.reserver(s, carte);
        }

        @Override
        public void arenesLiberer(Instance instance) {
            ArenesService.liberer(instance);
        }

        @Override
        public void arenesQuandLiberee(Consumer<Instance> ecouteur) {
            ArenesService.quandLiberee(ecouteur);
        }

        @Override
        public int arenesNettoyer(Instance instance) {
            return ArenesService.nettoyer(instance);
        }

        @Override
        public void choixOuvrir(Partie partie, Collection<ServerPlayer> joueurs, List<Kit> kits, Map<String, Integer> limites, int secondes,
                                Component titre, Consumer<Map<UUID, Kit>> quandFini) {
            ChoixService.ouvrir(partie, joueurs, kits, limites, secondes, titre, quandFini);
        }

        @Override
        public boolean choixEnCours(Partie partie) {
            return ChoixService.enCours(partie);
        }

        @Override
        public void choixAnnuler(Partie partie) {
            ChoixService.annuler(partie);
        }

        @Override
        public Kit choixDe(UUID joueur) {
            return ChoixService.choixDe(joueur);
        }

        @Override
        public Coffre.Refus coffreRefus(ServerPlayer joueur) {
            return Coffres.refus(joueur);
        }

        @Override
        public boolean coffreDeposer(ServerPlayer joueur, String raison) {
            return Coffres.deposer(joueur, raison);
        }

        @Override
        public boolean coffreRendre(ServerPlayer joueur) {
            return Coffres.rendre(joueur);
        }

        @Override
        public void combatApresMarquage(ServerPlayer joueur) {
            CombatService.apresMarquage(joueur);
        }

        @Override
        public void combatApresEffacement(ServerPlayer joueur) {
            CombatService.apresEffacement(joueur);
        }

        @Override
        public void combatCommandesBloquees(List<? extends String> commandes) {
            CombatService.commandesBloquees(commandes);
        }

        @Override
        public boolean equipementDonner(ServerPlayer joueur, Kit kit, String matchId) {
            return Distribution.donner(joueur, kit, matchId);
        }

        @Override
        public List<ItemStack> equipementPiles(Kit kit) {
            return Distribution.piles(kit);
        }

        @Override
        public int equipementPurgerCurios(ServerPlayer joueur, Predicate<ItemStack> filtre) {
            return Compat.purgerCurios(joueur, filtre);
        }

        @Override
        public void restrictionsSynchroniser(ServerPlayer joueur) {
            RestrictionsMatch.synchroniser(joueur);
        }

        @Override
        public void spectateurSuivre(ServerPlayer joueur, Partie partie) {
            Spectateurs.suivre(joueur, partie);
        }

        @Override
        public void spectateurArreter(ServerPlayer joueur, boolean restaurerMode) {
            Spectateurs.arreter(joueur, restaurerMode);
        }

        @Override
        public boolean spectateurEst(UUID joueur) {
            return Spectateurs.estSpectateur(joueur);
        }

        @Override
        public Partie zombiesPartieDe(Entity e) {
            return PartiesZombies.partieDe(e);
        }

        @Override
        public Partie zombiesPartieA(Level niveau, BlockPos pos) {
            return PartiesZombies.partieA(niveau, pos);
        }

        @Override
        public int zombiesPlanches(Level niveau, BlockPos pos) {
            return PartiesZombies.planches(niveau, pos);
        }

        @Override
        public boolean zombiesInteragir(ServerPlayer joueur) {
            return PartiesZombies.interagir(joueur);
        }

        @Override
        public void zombiesInvaliderTables() {
            PartiesZombies.invaliderTables();
        }
    }
}
