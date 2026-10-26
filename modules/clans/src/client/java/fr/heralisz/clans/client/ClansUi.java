package fr.heralisz.clans.client;

import fr.heralisz.clans.client.gui.PermissionsScreen;
import fr.heralisz.clans.client.gui.kit.Ui;
import fr.heralisz.clans.network.ClansNet;
import fr.heralisz.clans.network.Packets;
import fr.heralisz.clans.registry.Sons;
import net.minecraft.client.Minecraft;

/**
 * API client publique des interfaces du clan pour les autres mods HeraliZ (ecran de la base...). <b>Client
 * seulement</b> (fil de rendu) : ne jamais charger cette classe sur un serveur dedie.
 */
public final class ClansUi {
    private ClansUi() {
    }

    /**
     * Ouvre l'editeur des permissions PAR JOUEUR (membres a gauche avec tete, nom et grade ; permissions du membre
     * choisi a droite, par groupes, trois etats : comme son grade / autorise / refuse), filtre sur les permissions de
     * la base ({@code Perm.BASE_*}) si {@code seulementBase}, sinon toutes. Echap ou la croix ramenent a l'ecran
     * ouvert au moment de l'appel. Redemande au serveur un etat frais du clan. Ce que le joueur ne peut pas regler est
     * grise (le serveur revérifie tout) ; un joueur sans GERER_PERMISSIONS ne voit que ses propres permissions. Faux
     * (rien n'est ouvert) si le joueur n'a pas de clan connu du client.
     */
    public static boolean ouvrirPermissionsJoueurs(boolean seulementBase) {
        Minecraft mc = Minecraft.getInstance();
        if (ClientClans.snapshot() == null) return false;
        if (mc.getConnection() != null && !ClientClans.estFaux()) {
            ClansNet.toServer(new Packets.SimpleC2S(Packets.SimpleC2S.Genre.OUVRIR, "permissions"));
        }
        Ui.son(Sons.UI_OUVRIR, 0.7f);
        mc.setScreen(new PermissionsScreen(mc.screen, seulementBase));
        return true;
    }
}
