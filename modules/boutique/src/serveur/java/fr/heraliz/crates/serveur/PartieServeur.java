package fr.heraliz.crates.serveur;

import fr.heraliz.crates.block.CrateBlock;
import fr.heraliz.crates.block.CrateBlockEntityServeur;
import fr.heraliz.crates.event.CommonEvents;
import fr.heraliz.crates.menu.PreviewMenu;
import fr.heraliz.crates.network.Packets;
import fr.heraliz.crates.opening.CaseOpening;
import fr.heraliz.crates.opening.LootEditing;
import fr.heraliz.crates.pont.Partie;
import fr.heraliz.crates.registry.ModRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie serveur de Heraliz Crates (docs/METHODE_CLIENT_SERVEUR.md §4) : butin et tirages, clés, séquences d'ouverture,
 * interface façon CS:GO (décisions et récompenses), éditeur de butin, aperçu, récompenses en attente, commandes.
 * Les écouteurs (event/ServerEvents) s'inscrivent seuls.
 */
public final class PartieServeur implements Partie {
    @Override
    public void init(IEventBus busMod) {
        ModRegistry.CRATE_USINE.serveur(CrateBlockEntityServeur::new);
        CrateBlock.APERCU = PreviewMenu::open;
        CommonEvents.EDITEUR = LootEditing::open;
        Packets.RECEPTION_SERVEUR.brancher(PartieServeur::recevoir);
    }

    /** Corps d'origine des gestionnaires des paquets client -> serveur (fil principal). */
    private static void recevoir(Packets.Recu recu) {
        ServerPlayer p = recu.joueur();
        Object paquet = recu.paquet();
        if (paquet instanceof Packets.CaseOpen o) {
            CaseOpening.handleOpen(p, o);
        } else if (paquet instanceof Packets.CaseSkip) {
            CaseOpening.flush(p);
        } else if (paquet instanceof Packets.CaseClose c) {
            CaseOpening.close(p, c.session());
        } else if (paquet instanceof Packets.EditorSave e) {
            LootEditing.save(p, e);
        }
    }
}
