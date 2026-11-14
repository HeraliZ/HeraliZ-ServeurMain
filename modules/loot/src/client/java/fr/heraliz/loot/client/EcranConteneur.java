package fr.heraliz.loot.client;

import fr.heraliz.loot.HeralizLoot;
import fr.heraliz.noyau.client.kit.EcranConteneurVerre;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;

/**
 * Fouille d'un conteneur de loot T1-T6 (et des salles à carte), avec le badge du palier juste après le nom
 * ({@link BadgePalier}). Depuis le 25/10, aussi tous les autres coffres de base (planque, coffre VIP, coffre du clan,
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

    /** Badge du palier juste après le nom (rien pour un coffre de base : palier 0). */
    @Override
    protected void apresNom(GuiGraphics g, float x, float cy) {
        if (palier >= 1 && palier <= 7) BadgePalier.dessiner(g, font, palier, Math.round(x), Math.round(cy - 4f));
    }

    @Override
    protected void secours() {
        enPanne = true;
        HeralizLoot.LOG.warn("[loot] fouille en verre en panne : écran de coffre de Minecraft pour cette session");
        minecraft.setScreen(new ContainerScreen(menu, minecraft.player.getInventory(), title));
    }
}
