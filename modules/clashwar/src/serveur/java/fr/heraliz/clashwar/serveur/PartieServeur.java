package fr.heraliz.clashwar.serveur;

import fr.heraliz.clashwar.tournoi.net.ReceptionCwPaquetsServeur;
import fr.heraliz.clashwar.moteur.api.Coffre;
import fr.heraliz.clashwar.moteur.api.Instance;
import fr.heraliz.clashwar.moteur.api.Kit;
import fr.heraliz.clashwar.moteur.api.Partie;
import fr.heraliz.clashwar.moteur.arenes.ArenesService;
import fr.heraliz.clashwar.moteur.coffre.Coffres;
import fr.heraliz.clashwar.moteur.combat.RestrictionsMatch;
import fr.heraliz.clashwar.moteur.compat.Compat;
import fr.heraliz.clashwar.moteur.kits.ChoixService;
import fr.heraliz.clashwar.moteur.kits.Distribution;
import fr.heraliz.clashwar.moteur.net.ReceptionPaquetsMoteurServeur;
import fr.heraliz.clashwar.moteur.spectateur.Spectateurs;
import fr.heraliz.clashwar.pont.Services;
import fr.heraliz.clashwar.reseau.MatchsNet;
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
 * Partie serveur de HeraliZ ClashWar (docs/METHODE_CLIENT_SERVEUR.md §4) : moteur des matchs (arènes, coffre, kits,
 * spectateurs) et tournoi. Les modules s'inscrivent par Modules.SERVEURS (écouteurs, commandes) ; ici : services des
 * API communes et paquets client -> serveur.
 */
public final class PartieServeur implements fr.heraliz.clashwar.pont.Partie {
    @Override
    public void init(IEventBus busMod) {
        Services.MOTEUR = new Moteur();
        MatchsNet.RECEPTION_SERVEUR.brancher(PartieServeur::recevoir);
    }

    private static void recevoir(MatchsNet.Recu r) {
        if (ReceptionPaquetsMoteurServeur.recevoir(r)) return;
        if (ReceptionCwPaquetsServeur.recevoir(r)) return;
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
    }
}
