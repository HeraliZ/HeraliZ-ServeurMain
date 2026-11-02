package fr.heraliz.clashwar.moteur;

import fr.heraliz.clashwar.moteur.net.PaquetsMoteur;
import fr.heraliz.clashwar.reseau.MatchsNet;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie commune (registres, paquets) du moteur des matchs (voir {@code Modules}) : sons et paquets ; la logique est
 * dans {@code MoteurLogique} (src/serveur). Le HUD client est branche par
 * {@code moteur.client.HudMoteur}. Depuis le 02/11 (audit), les parties, arènes, cartes, kits, coffre, équipes et
 * spectateurs de {@code moteur.api} sont aussi côté serveur seulement ; restent partagés ici les paquets et ce que le
 * HUD affiche ({@code Hud}, {@code Annonces}, {@code KillFeed}, {@code Kit}).
 */
public final class Moteur {
    private Moteur() {
    }

    public static void construire(IEventBus modBus) {
        MoteurSons.enregistrer(modBus);
        // écouteurs, commandes et logique : MoteurLogique (src/serveur), appelé par Modules juste après
    }

    /** Appele par MatchsNet.init. */
    public static void paquets(MatchsNet.Registre r) {
        PaquetsMoteur.enregistrer(r);
    }
}
