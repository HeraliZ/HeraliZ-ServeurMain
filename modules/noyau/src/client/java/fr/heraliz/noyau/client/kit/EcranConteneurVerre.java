package fr.heraliz.noyau.client.kit;

import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heraliz.noyau.HeralizNoyau;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.joml.Matrix4f;

/**
 * Écran d'un conteneur en verre (sac à dos, fouille du loot, coffres ; aperçu validé le 14/11) : un seul panneau, en haut
 * le nom du conteneur (et ce que l'écran ajoute après lui : badge du palier, rareté du sac…), dessous son contenu en
 * grille ({@code colonnes x lignes}, centrée), en bas « ton inventaire » comme dans l'inventaire ({@link InventaireVerre}).
 * Monde flouté derrière ({@link FondVerre}), infobulles aux couleurs du verre ({@link EcranVerre}).
 * <ul>
 *     <li>Côté serveur, rien ne change : même menu, mêmes cases aux mêmes numéros, mêmes règles ; seules les places des
 *     cases changent chez le joueur ({@link CasesEcran}). Les cases du conteneur sont rangées par leur numéro dans le
 *     conteneur (colonne = numéro % colonnes) ; celles du joueur par leur numéro dans son inventaire.</li>
 *     <li>Échelle d'interface choisie pour que le panneau tienne dans la fenêtre ({@link Echelle#forcer(net.minecraft.client.gui.screens.Screen, int, int)}),
 *     rendue à la fermeture.</li>
 *     <li>Rien n'est alloué à chaque image : textes gardés, boucles sur les cases par numéro.</li>
 * </ul>
 */
public abstract class EcranConteneurVerre<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> implements EcranVerre {
    /** Marges du panneau, haut de la grille du contenu, milieu du titre, bas du trait sous le titre. */
    protected static final int MARGE = 12, Y_CONTENU = 30, SEPARATION = 22;
    protected static final float TITRE_CY = 11.5f;
    private static final int CADRE = InventaireVerre.CADRE, PAS = InventaireVerre.PAS, PAD = InventaireVerre.PAD;

    protected final int colonnes, lignes;
    private final int xContenu;
    protected final InventaireVerre inventaire = new InventaireVerre();
    private final TexteHud nom = new TexteHud(PoliceHud.TEXTE_GRAS);
    private long signature;
    private boolean secoursFait;

    protected EcranConteneurVerre(T menu, Inventory inv, Component titre, int colonnes, int lignes) {
        super(menu, inv, titre);
        this.colonnes = Math.max(1, colonnes);
        this.lignes = Math.max(1, lignes);
        int largeurContenu = this.colonnes * PAS - (PAS - CADRE);
        int interieur = Math.max(largeurContenu, InventaireVerre.LARGEUR);
        imageWidth = interieur + 2 * MARGE;
        xContenu = MARGE + (interieur - largeurContenu) / 2;
        int yGrille = Y_CONTENU + this.lignes * PAS - (PAS - CADRE) + 15 + InventaireVerre.ETIQUETTE;
        int yBarre = yGrille + InventaireVerre.HAUTEUR + 15 + InventaireVerre.ETIQUETTE;
        imageHeight = yBarre + CADRE + MARGE;
        inventaire.disposer(MARGE, yGrille, yBarre);
    }

    /** Nom écrit en haut du panneau (le titre de l'écran par défaut). */
    protected String nom() {
        return title.getString();
    }

    /**
     * Ce que l'écran dessine juste après le nom (badge, rareté…), x = juste après le nom, y = milieu du titre (absolus).
     * Rien par défaut.
     */
    protected void apresNom(GuiGraphics g, float x, float cy) {
    }

    @Override
    protected void init() {
        Echelle.forcer(this, imageWidth, imageHeight);
        super.init();
        nom.texte(nom());
        inventaire.textes();
        disposer();
    }

    @Override
    public void removed() {
        Echelle.restaurer();
        super.removed();
    }

    /** Places des cases : contenu en grille, inventaire du joueur en bas ; toute autre case est cachée. */
    protected void disposer() {
        Inventory inv = minecraft.player.getInventory();
        int n = colonnes * lignes;
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            if (CasesEcran.cachee(s) || inventaire.placer(s, inv)) continue;
            int k = s.getContainerSlot();
            if (s.container != inv && k >= 0 && k < n) {
                CasesEcran.placer(s, xContenu + PAD + (k % colonnes) * PAS, Y_CONTENU + PAD + (k / colonnes) * PAS);
            } else {
                CasesEcran.cacher(menu, i);
            }
        }
        signature = CasesEcran.signature(menu);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        try {
            if (CasesEcran.signature(menu) != signature) disposer();
            FondVerre.dessiner(g, width, height);
            super.render(g, mx, my, pt);
            renderTooltip(g, mx, my);
        } catch (RuntimeException ex) {
            // filet : jamais de plantage du jeu à cause d'un écran en verre
            if (!secoursFait) {
                secoursFait = true;
                HeralizNoyau.LOG.error("[noyau] écran en verre en panne : {}", getClass().getSimpleName(), ex);
                secours();
            }
        }
    }

    /** Après une erreur de dessin (déjà notée au journal) : par défaut l'écran se ferme ; un écran peut passer la main. */
    protected void secours() {
        onClose();
    }

    /** Toute la case (son cadre) est sensible, pas seulement l'objet. */
    @Override
    protected boolean isHovering(int x, int y, int w, int h, double mx, double my) {
        if (w == 16 && h == 16) {
            double rx = mx - leftPos, ry = my - topPos;
            return rx >= x - PAD && rx < x + 16 + PAD && ry >= y - PAD && ry < y + 16 + PAD;
        }
        return super.isHovering(x, y, w, h, mx, my);
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = leftPos, y = topPos;
        Ui.glass(g, x, y, imageWidth, imageHeight, 205);
        VertexConsumer vc = Formes.tampon(g);
        Matrix4f m = g.pose().last().pose();
        Formes.rect(vc, m, x + 10, y + SEPARATION, x + imageWidth - 10, y + SEPARATION + Formes.pixel(), Palette.argb(0xFFFFFF, 23));
        inventaire.filet(vc, m, x + 10, x + imageWidth - 10, y);
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            if (!s.isActive()) continue;
            CaseVerre.dessiner(vc, m, x + s.x - PAD, y + s.y - PAD, CADRE, isHovering(s.x, s.y, 16, 16, mx, my));
        }
        g.flush();
        nom.ajouter(g, x + MARGE, y + TITRE_CY, Palette.argb(Palette.TEXTE, 255), TexteHud.GAUCHE, false);
        inventaire.etiquettes(g, x, y);
        g.flush();
        apresNom(g, x + MARGE + nom.largeur() + 6f, y + TITRE_CY);
    }

    /** Tout est dessiné par {@link #renderBg} (pas de titres de Minecraft). */
    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
    }
}
