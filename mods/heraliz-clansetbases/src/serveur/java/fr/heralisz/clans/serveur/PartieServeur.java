package fr.heralisz.clans.serveur;

import fr.heraliz.clansetbases.pont.Partie;
import fr.heralisz.clans.api.ClansApiServeur;
import fr.heralisz.clans.network.Packets;
import fr.heralisz.clans.registry.ClansRegistry;
import fr.heralisz.clans.server.ServerHandlers;
import fr.heralisz.clans.territory.CaisseRavitaillementBlockServeur;
import fr.heralisz.clans.territory.DrapeauBlock;
import fr.heralisz.clans.territory.DrapeauBlockEntity;
import fr.heralisz.clans.territory.Territoires;
import fr.heralisz.clans.territory.supply.CaisseParachuteEntityServeur;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie serveur de HeraliZ Clans : clans derriere l'API, ecouteurs du bus Forge dans l'ordre d'origine (store,
 * commandes, regles de jeu ; Territoires et TerritoireCommands s'inscrivent seuls par @Mod.EventBusSubscriber), versions
 * serveur de la caisse posee et de la caisse sous parachute, actions du drapeau, paquets recus des joueurs.
 */
public final class PartieServeur implements Partie {
    @Override
    public void init(IEventBus busMod) {
        ClansApiServeur.installer();
        MinecraftForge.EVENT_BUS.register(fr.heralisz.clans.server.ClanStore.class);
        MinecraftForge.EVENT_BUS.register(fr.heralisz.clans.server.ClanCommands.class);
        // regles de jeu : stats, chat de clan, liste Tab, synchronisation groupee, quetes, evenements des territoires
        MinecraftForge.EVENT_BUS.register(fr.heralisz.clans.server.ClanEvents.class);
        // saison des clans : points, classements, commandes /saison et /clan ameliorer|don
        MinecraftForge.EVENT_BUS.register(fr.heralisz.clans.saison.Saison.class);
        MinecraftForge.EVENT_BUS.register(fr.heralisz.clans.saison.SaisonCommandes.class);

        ClansRegistry.CAISSE_USINE.serveur(CaisseRavitaillementBlockServeur::new);
        ClansRegistry.CAISSE_PARACHUTE_USINE.serveur(CaisseParachuteEntityServeur::new);
        DrapeauBlock.PANNEAU_SERVEUR = Territoires::ouvrirPanneau;
        DrapeauBlock.RETRAIT_SERVEUR.brancher(pos -> {
            Territoires t = Territoires.get();
            if (t != null) t.drapeauRetire(pos);
        });
        DrapeauBlockEntity.CHARGEMENT_SERVEUR.brancher(be -> {
            Territoires t = Territoires.get();
            if (t != null) t.rafraichirDrapeau(be);
        });

        Packets.RECEPTION_SERVEUR.brancher(r -> {
            Object m = r.paquet();
            var p = r.joueur();
            if (m instanceof Packets.CreerClanC2S x) ServerHandlers.creerClan(p, x);
            else if (m instanceof Packets.InviterC2S x) ServerHandlers.inviter(p, x);
            else if (m instanceof Packets.ReponseInvitationC2S x) ServerHandlers.reponseInvitation(p, x);
            else if (m instanceof Packets.ActionMembreC2S x) ServerHandlers.actionMembre(p, x);
            else if (m instanceof Packets.BanqueC2S x) ServerHandlers.banque(p, x);
            else if (m instanceof Packets.ParametreC2S x) ServerHandlers.parametre(p, x);
            else if (m instanceof Packets.BanniereC2S x) ServerHandlers.banniere(p, x);
            else if (m instanceof Packets.PermsC2S x) ServerHandlers.perms(p, x);
            else if (m instanceof Packets.SimpleC2S x) ServerHandlers.simple(p, x);
            else if (m instanceof Packets.RavitaillementC2S x) ServerHandlers.ravitaillement(p, x);
            else if (m instanceof Packets.PermsJoueurC2S x) ServerHandlers.permsJoueur(p, x);
            else if (m instanceof Packets.CarteDemandeC2S) Territoires.carte(p);
            else if (m instanceof Packets.AmeliorerC2S x) {
                fr.heralisz.clans.model.Arbre.Branche b = fr.heralisz.clans.model.Arbre.Branche.parId(x.branche());
                if (b != null) fr.heralisz.clans.server.ClanService.ameliorer(p, b);
            }
        });
    }
}
