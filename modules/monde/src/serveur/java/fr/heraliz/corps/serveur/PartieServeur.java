package fr.heraliz.corps.serveur;

import fr.heraliz.corps.commande.CommandeCorps;
import fr.heraliz.corps.entite.CorpsEntityServeur;
import fr.heraliz.corps.mort.CaptureMort;
import fr.heraliz.corps.registre.Registres;
import fr.heralisz.utils.pont.Partie;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie serveur de heraliz_corps (jar fusionne HeraliZ Utils, docs/METHODE_CLIENT_SERVEUR.md §4) : versions serveur du
 * corps (contenu, minuteur, chute, fouille, sauvegarde), puis les ecouteurs de
 * l'ancien constructeur, dans le meme ordre : capture de la mort (CaptureMort), commandes /corps.
 */
public final class PartieServeur implements Partie {
    @Override
    public void init(IEventBus busMod) {
        Registres.CORPS_USINE.serveur(CorpsEntityServeur::new);
        CaptureMort.enregistrer();
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> CommandeCorps.enregistrer(e.getDispatcher()));
    }
}
