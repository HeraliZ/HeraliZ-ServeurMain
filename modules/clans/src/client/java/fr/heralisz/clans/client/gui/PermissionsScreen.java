package fr.heralisz.clans.client.gui;

import fr.heralisz.clans.client.ClientClans;
import fr.heralisz.clans.client.gui.kit.BannerArt;
import fr.heralisz.clans.client.gui.kit.Echelle;
import fr.heralisz.clans.client.gui.kit.Hote;
import fr.heralisz.clans.client.gui.kit.HzIcon;
import fr.heralisz.clans.client.gui.kit.HzScroll;
import fr.heralisz.clans.client.gui.kit.Icons;
import fr.heralisz.clans.client.gui.kit.Ui;
import fr.heralisz.clans.client.gui.perms.EditeurJoueurs;
import fr.heralisz.clans.model.ClanSnapshot;
import fr.heralisz.clans.registry.Sons;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Ecran autonome des permissions PAR JOUEUR (ouvert par {@code ClansUi.ouvrirPermissionsJoueurs}, par exemple depuis
 * l'onglet PERMISSIONS de l'ecran de la base) : meme panneau que l'ecran du clan (echelle forcee, verre sur fond en
 * degrade), en-tete banniere + « PERMISSIONS DES MEMBRES » (ou « DE LA BASE » filtre sur Perm.BASE_*), puis
 * {@link EditeurJoueurs}. Echap ou la croix ramenent a l'ecran d'origine. Se reconstruit a chaque nouveau snapshot
 * (defilements gardes) et se ferme si le joueur n'a plus de clan.
 */
public class PermissionsScreen extends Screen implements Hote {
    private static final int ENTETE = 44;
    @Nullable
    private final Screen retour;
    private final boolean seulementBase;
    private int px, py, pw, ph;
    private int vSnapshot = -1;
    @Nullable
    private EditeurJoueurs editeur;
    @Nullable
    private List<Component> infobulle;

    public PermissionsScreen(@Nullable Screen retour, boolean seulementBase) {
        super(Component.translatable(seulementBase ? "gui.permissions.titre_base" : "gui.permissions.titre"));
        this.retour = retour;
        this.seulementBase = seulementBase;
    }

    public boolean seulementBase() {
        return seulementBase;
    }

    @Nullable
    public EditeurJoueurs editeur() {
        return editeur;
    }

    @Override
    public <T extends AbstractWidget> T ajouter(T w) {
        return addRenderableWidget(w);
    }

    @Override
    public void infobulle(List<Component> lignes) {
        infobulle = lignes;
    }

    @Override
    protected void init() {
        Echelle.forcer(this);
        pw = Math.min(560, width - 32);
        ph = Math.min(320, height - 24);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        vSnapshot = ClientClans.versionSnapshot();
        addRenderableWidget(new HzIcon(px + pw - 24, py + 10, 12, Icons.CROIX, Component.translatable("gui.fermer"), this::onClose));
        ClanSnapshot s = ClientClans.snapshot();
        editeur = s == null ? null : new EditeurJoueurs(this, s, px + Ui.MARGE, py + ENTETE + 24, pw - 2 * Ui.MARGE, ph - ENTETE - 24 - 14, seulementBase);
    }

    /** Nouveau snapshot : reconstruction en gardant le defilement des deux listes. */
    @Override
    protected void rebuildWidgets() {
        List<Integer> defilements = new ArrayList<>();
        for (GuiEventListener c : children()) if (c instanceof HzScroll sc) defilements.add(sc.getScroll());
        super.rebuildWidgets();
        int k = 0;
        for (GuiEventListener c : children()) {
            if (c instanceof HzScroll sc && k < defilements.size()) sc.setScroll(defilements.get(k++));
        }
    }

    @Override
    public void tick() {
        if (ClientClans.snapshot() == null) {
            onClose();
            return;
        }
        if (ClientClans.versionSnapshot() != vSnapshot) rebuildWidgets();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        infobulle = null;
        Ui.fond(g, width, height);
        Ui.glass(g, px, py, pw, ph);
        ClanSnapshot s = ClientClans.snapshot();
        if (s != null) {
            // en-tete : banniere, titre, clan
            BannerArt.draw(g, s.banniere(), px + Ui.MARGE, py + 6, 24, 32);
            int tx = px + Ui.MARGE + 34;
            Ui.spaced(g, font, getTitle().getString(), Ui.BOLD, tx, py + 11, Ui.argb(Ui.TEXT, 255), 1.2f);
            g.drawString(font, Ui.lit("[" + s.tag() + "] " + s.nom(), Ui.SMALL), tx, py + 25, Ui.argb(Ui.MUTED, 255), false);
            Ui.hairline(g, px + 8, px + pw - 8, py + ENTETE, 0xFFFFFF, 40, true, true);
            // indication, puis l'editeur
            g.drawString(font, Ui.tr(Ui.SMALL, "gui.permissions.astuce_joueur"), px + Ui.MARGE, py + ENTETE + 9, Ui.argb(Ui.FAINT, 255), false);
            if (editeur != null) editeur.render(g, mouseX, mouseY);
        }
        super.render(g, mouseX, mouseY, partialTick);
        if (infobulle != null) Ui.tooltip(g, font, infobulle, mouseX, mouseY, Ui.accent(), px + pw);
    }

    /** Le relachement va a tous les enfants (comme l'ecran du clan). */
    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        setDragging(false);
        boolean pris = false;
        for (GuiEventListener c : children()) pris |= c.mouseReleased(mx, my, button);
        return pris;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    /** Retour a l'ecran d'origine (l'ecran de la base par exemple), ou au jeu. */
    @Override
    public void onClose() {
        Ui.son(Sons.UI_FERMER, 0.6f);
        if (minecraft != null) minecraft.setScreen(retour);
    }

    @Override
    public void removed() {
        Echelle.restaurer();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
