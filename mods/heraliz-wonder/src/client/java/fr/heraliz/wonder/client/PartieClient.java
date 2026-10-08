package fr.heraliz.wonder.client;

import fr.heraliz.wonder.pont.Partie;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie CLIENT de HeraliZ Wonder (docs/METHODE_CLIENT_SERVEUR.md §4), chargee par HeralizWonder sur un client physique si
 * elle est dans le jar (-client, -complet) : effets, sons, rendus et branchements TaCZ cote client de chaque arme, dans
 * l'ordre d'origine, puis la reception des paquets d'effets (crochets RECEPTION_CLIENT du code commun).
 */
public final class PartieClient implements Partie {
    @Override
    public void init(IEventBus mod) {
        IEventBus forge = MinecraftForge.EVENT_BUS;
        fr.heraliz.wonder.blundergat.client.BlunderClient.init(mod);
        fr.heraliz.wonder.jetgun.client.JetClient.init(mod, forge);
        ClientWonder.init(mod);
        fr.heraliz.wonder.wintershowl.client.ClientGivre.init(mod, forge);
        fr.heraliz.wonder.sliquifier.client.SliqClient.init(mod);
        fr.heraliz.wonder.paralyzer.client.ClientParalyseur.init(mod);
        // paquets d'effets recus du serveur
        fr.heraliz.wonder.blundergat.net.Acide.RECEPTION_CLIENT.brancher(m -> fr.heraliz.wonder.blundergat.client.FxBlunder.acide(m));
        fr.heraliz.wonder.blundergat.net.Eclats.RECEPTION_CLIENT.brancher(m -> fr.heraliz.wonder.blundergat.client.FxBlunder.eclats(m));
        fr.heraliz.wonder.jetgun.net.Broyage.RECEPTION_CLIENT.brancher(m -> fr.heraliz.wonder.jetgun.client.JetFx.broyage(m));
        fr.heraliz.wonder.net.Chaine.RECEPTION_CLIENT.brancher(m -> fr.heraliz.wonder.client.fx.Fx.chaine(m));
        fr.heraliz.wonder.net.Impact.RECEPTION_CLIENT.brancher(m -> fr.heraliz.wonder.client.fx.Fx.impact(m));
        fr.heraliz.wonder.net.Souffle.RECEPTION_CLIENT.brancher(m -> fr.heraliz.wonder.client.fx.FxVent.souffle(m));
        fr.heraliz.wonder.paralyzer.ReseauParalyseur.Etat.RECEPTION_CLIENT.brancher(m -> fr.heraliz.wonder.paralyzer.client.FxParalyseur.etat(m));
        fr.heraliz.wonder.paralyzer.ReseauParalyseur.Flotte.RECEPTION_CLIENT.brancher(m -> fr.heraliz.wonder.paralyzer.client.FxParalyseur.flotte(m.hauteur()));
        fr.heraliz.wonder.sliquifier.net.PaquetFlaque.RECEPTION_CLIENT.brancher(m -> fr.heraliz.wonder.sliquifier.client.FxSliq.flaque(m));
        fr.heraliz.wonder.sliquifier.net.PaquetSync.RECEPTION_CLIENT.brancher(m -> fr.heraliz.wonder.sliquifier.client.FxSliq.sync(m));
        fr.heraliz.wonder.sliquifier.net.PaquetTir.RECEPTION_CLIENT.brancher(m -> fr.heraliz.wonder.sliquifier.client.FxSliq.tir(m));
        fr.heraliz.wonder.sliquifier.net.PaquetZombie.RECEPTION_CLIENT.brancher(m -> fr.heraliz.wonder.sliquifier.client.FxSliq.zombie(m));
        fr.heraliz.wonder.wintershowl.ReseauGivre.Souffle.RECEPTION_CLIENT.brancher(m -> fr.heraliz.wonder.wintershowl.client.FxGivre.souffle(m));
        fr.heraliz.wonder.wintershowl.ReseauGivre.Eclat.RECEPTION_CLIENT.brancher(m -> fr.heraliz.wonder.wintershowl.client.FxGivre.eclat(m));
        fr.heraliz.wonder.wintershowl.ReseauGivre.Degel.RECEPTION_CLIENT.brancher(m -> fr.heraliz.wonder.wintershowl.client.FxGivre.degel(m));
    }
}
