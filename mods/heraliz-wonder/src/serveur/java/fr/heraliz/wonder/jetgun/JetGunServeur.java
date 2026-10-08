package fr.heraliz.wonder.jetgun;

import fr.heraliz.wonder.HeralizWonder;
import fr.heraliz.wonder.jetgun.serveur.Aspiration;
import fr.heraliz.wonder.jetgun.serveur.JetCommandes;
import net.minecraftforge.eventbus.api.IEventBus;

/** Partie serveur du Jet Gun (reprise telle quelle de JetGun.init) : aspiration, commandes, TaCZ. */
public final class JetGunServeur {
    private JetGunServeur() {
    }

    public static void brancher(IEventBus mod, IEventBus forge) {
        boolean tacz = JetGun.tacz;
        forge.addListener(Aspiration::tick);
        forge.addListener(Aspiration::dechargement);
        forge.addListener(JetCommandes::enregistrer);
        forge.addListener(JetCommandes::stressTick);
        if (tacz) fr.heraliz.wonder.jetgun.serveur.JetTacz.brancher(forge);
        HeralizWonder.LOG.info("[wonder] Jet Gun branche (aspiration, chaleur, effets){}", tacz ? "" : " - TaCZ absent : inactif");
    }
}
