package fr.heralisz.matchs.client;

import fr.heralisz.matchs.pont.Partie;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie client de HeraliZ Matchs (docs/METHODE_CLIENT_SERVEUR.md §4) : démarrage des HUD et écrans de chaque module
 * (même ordre que Modules.COMMUNS), réception des paquets serveur -> client, rendu des objets de quête.
 */
public final class PartieClient implements Partie {
    @Override
    public void init(IEventBus busMod) {
        fr.heralisz.matchs.moteur.client.HudMoteur.enregistrer(busMod);
        fr.heralisz.matchs.clashwar.client.ClashWarClient.initMod(busMod);
        fr.heralisz.matchs.duels.client.DuelsClient.initClient(busMod);
        fr.heralisz.matchs.zombies.jeu.client.ZombiesJeuClient.init();
        fr.heralisz.matchs.clashwar.net.CwPaquets.RECEPTION_CLIENT.brancher(fr.heralisz.matchs.clashwar.net.ReceptionCwPaquets::recevoir);
        fr.heralisz.matchs.duels.killcam.PaquetsKillcam.RECEPTION_CLIENT.brancher(fr.heralisz.matchs.duels.killcam.ReceptionPaquetsKillcam::recevoir);
        fr.heralisz.matchs.duels.net.PaquetsDuels.RECEPTION_CLIENT.brancher(fr.heralisz.matchs.duels.net.ReceptionPaquetsDuels::recevoir);
        fr.heralisz.matchs.moteur.net.PaquetsMoteur.RECEPTION_CLIENT.brancher(fr.heralisz.matchs.moteur.net.ReceptionPaquetsMoteur::recevoir);
        fr.heralisz.matchs.zombies.jeu.net.PaquetsZombies.RECEPTION_CLIENT.brancher(fr.heralisz.matchs.zombies.jeu.net.ReceptionPaquetsZombies::recevoir);
        fr.heralisz.matchs.zombies.contenu.objet.ObjetQuete.RENDU_CLIENT.brancher(c -> c.accept(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return fr.heralisz.matchs.zombies.client.rendu.ObjetsRendu.instance();
            }
        }));
    }
}
