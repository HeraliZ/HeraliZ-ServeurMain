package fr.heralisz.bases.serveur;

import fr.heraliz.clansetbases.pont.Partie;
import fr.heralisz.bases.api.BasesApi;
import fr.heralisz.bases.blindage.Blindage;
import fr.heralisz.bases.blindage.C4Garde;
import fr.heralisz.bases.bloc.SortieBlock;
import fr.heralisz.bases.network.Packets;
import fr.heralisz.bases.pillage.PillageEvents;
import fr.heralisz.bases.pillage.ReglesPillage;
import fr.heralisz.bases.server.BaseService;
import fr.heralisz.bases.server.ServerHandlers;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie serveur de HeraliZ Bases : ecouteurs du coeur (store, cycle de vie, regles de la dimension, commandes, tag de
 * combat, descente des vehicules) puis ceux du module pillage (regles des pillards, fenetre, blindage, C4), dans l'ordre
 * d'origine ; bloc de sortie et paquets recus des joueurs.
 */
public final class PartieServeur implements Partie {
    @Override
    public void init(IEventBus busMod) {
        // ordre important : le store est charge avant les regles et les commandes qui le lisent
        MinecraftForge.EVENT_BUS.register(fr.heralisz.bases.server.BasesStore.class);
        MinecraftForge.EVENT_BUS.register(fr.heralisz.bases.server.BaseEvents.class);
        MinecraftForge.EVENT_BUS.register(fr.heralisz.bases.server.Regles.class);
        MinecraftForge.EVENT_BUS.register(fr.heralisz.bases.server.BaseCommands.class);
        MinecraftForge.EVENT_BUS.register(fr.heralisz.bases.combat.CombatLocal.class);
        // descente forcee des vehicules avant nos teleportations (un mod qui refuse la descente est passe outre)
        MinecraftForge.EVENT_BUS.register(fr.heralisz.bases.server.Descente.class);
        // banc d'essai seulement (propriete heralisz.bases.harness) : vehicule « collant » qui imite le train
        if (System.getProperty("heralisz.bases.harness") != null) MinecraftForge.EVENT_BUS.register(fr.heralisz.bases.dev.VehiculeCollant.class);
        pillage();
        SortieBlock.SORTIE_SERVEUR.brancher(BaseService::sortir);
        Packets.RECEPTION_SERVEUR.brancher(r -> {
            if (r.paquet() instanceof Packets.ActionC2S m) ServerHandlers.action(r.joueur(), m);
        });
    }

    /** Partie serveur du module pillage (ex-PillageModule.init) : regles branchees sur le coeur, puis ses ecouteurs. */
    private static void pillage() {
        BasesApi.pillage(new ReglesPillage());
        MinecraftForge.EVENT_BUS.register(PillageEvents.class);
        MinecraftForge.EVENT_BUS.register(Blindage.class);
        MinecraftForge.EVENT_BUS.register(C4Garde.class);
    }
}
