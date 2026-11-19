package fr.heraliz.loot.client;

import fr.heraliz.loot.HeralizLoot;
import fr.heraliz.noyau.client.kit.EcranConteneurVerre;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;

/**
 * Fouille d'un conteneur de loot T1-T6 (et des salles à carte) : en haut, seulement le badge de son palier
 * ({@link BadgePalier}), sans le nom du conteneur (19/11, demande du propriétaire ; avant : le nom puis le badge). Depuis le 25/10, aussi tous les autres coffres de base (planque, coffre VIP, coffre du clan,
 * inspection…), palier 0 : sans badge. Ouvert à la place de l'écran de coffre vanilla par
 * {@link ClientLoot.Jeu#titreConteneur}.
 * <p>14/11 (aperçu validé par le propriétaire) : en verre comme l'inventaire ({@link EcranConteneurVerre} du noyau) :
 * un panneau, le nom et le badge en haut, le contenu en grille, « Sur toi » et « Accès rapide » en bas. Même menu
 * (ChestMenu) : côté serveur rien ne change. Erreur de dessin : l'écran de coffre de Minecraft pour la session.
 */
public class EcranConteneur extends EcranConteneurVerre<ChestMenu> {
    /** Vrai après une erreur de dessin : l'écran de coffre de Minecraft pour le reste de la session. */
    static boolean enPanne;
    private final int palier;

    public EcranConteneur(ChestMenu menu, Inventory inv, Component titre, int palier) {
        super(menu, inv, titre, 9, menu.getRowCount());
        this.palier = palier;
    }

    /** Conteneur de loot (palier 1 à 7) : pas de nom, seulement son palier (19/11, demande du propriétaire). */
    private boolean aPalier() {
        return palier >= 1 && palier <= 7;
    }

    /** Un conteneur de loot n'écrit pas son nom ; un coffre de base (palier 0 : planque, coffre VIP, du clan…) le garde. */
    @Override
    protected String nom() {
        return aPalier() ? "" : super.nom();
    }

    /**
     * Badge du palier à la place du nom, au bord gauche du panneau (x arrive après un nom vide et l'écart de 6 points qui
     * le suit) ; rien pour un coffre de base.
     */
    @Override
    protected void apresNom(GuiGraphics g, float x, float cy) {
        if (aPalier()) BadgePalier.dessiner(g, font, palier, Math.round(x - 6f), Math.round(cy - 4f));
    }

    @Override
    protected void secours() {
        enPanne = true;
        HeralizLoot.LOG.warn("[loot] fouille en verre en panne : écran de coffre de Minecraft pour cette session");
        minecraft.setScreen(new ContainerScreen(menu, minecraft.player.getInventory(), title));
    }
}
