package fr.heralisz.bases.client.gui;

import fr.heralisz.bases.client.BasesClient;
import fr.heralisz.bases.client.BasesKeys;
import fr.heralisz.bases.client.ClientBases;
import fr.heralisz.bases.client.gui.hud.BasesHud;
import fr.heralisz.bases.client.gui.kit.Echelle;
import fr.heralisz.bases.client.gui.kit.Hold;
import fr.heralisz.bases.client.gui.kit.HzButton;
import fr.heralisz.bases.client.gui.kit.HzIcon;
import fr.heralisz.bases.client.gui.kit.HzScroll;
import fr.heralisz.bases.client.gui.kit.Icons;
import fr.heralisz.bases.client.gui.kit.Sons;
import fr.heralisz.bases.client.gui.kit.Ui;
import fr.heralisz.bases.model.FenetreVue;
import fr.heralisz.bases.model.PillableVue;
import fr.heralisz.bases.network.Packets;
import fr.heralisz.clans.client.gui.kit.BannerArt;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Ecran PILLER (BASES_SPEC §6 ; {@code /base piller} sans argument), echelle forcee, panneau 580 x 324 : en-tete
 * (titre, regles en une ligne, compte a rebours de la fenetre « FERMETURE DANS 02:14:08 » / « OUVERTURE DANS … »),
 * liste defilante des bases pillables (cartes de 40 px : banniere, nom + [TAG], membres et en ligne, pillards presents,
 * « deja pillee par vous aujourd'hui »), PILLER en maintenant 1 s quand c'est possible, sinon la raison du serveur.
 * Pied : lien retour BASE DU CLAN et nombre de bases.
 * <p>Se reconstruit a chaque nouvelle liste ({@link ClientBases#versionPillables()}) ; le compte a rebours est recalcule
 * une fois par seconde ; a zero, la liste est redemandee une fois (la fenetre vient de s'ouvrir ou de se fermer).
 */
public class PillerScreen extends Screen {
    private static final int ENTETE = 52, CARTE = 40, PAS = 44;
    @Nullable
    private final Screen retour;
    private int px, py, pw, ph;
    private int vListe = -1;
    private long ouvertLe;
    private long derniereSeconde;
    private long redemandeLe;
    @Nullable
    private Liste liste;

    private String titre = "", regles = "", chronoLibelle = "", chrono = "", vide = "";
    private int chronoCouleur;
    private Component nombre = Component.empty();

    public PillerScreen(@Nullable Screen retour) {
        super(Component.translatable("gui.heralisz_bases.piller.titre"));
        this.retour = retour;
    }

    @Override
    protected void init() {
        Echelle.forcer(this);
        if (ouvertLe == 0) {
            ouvertLe = System.currentTimeMillis();
            Sons.jouer(Sons.OUVRIR, 0.7f);
        }
        vListe = ClientBases.versionPillables();
        pw = Math.min(580, width - 24);
        ph = Math.min(324, height - 16);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        int accent = Ui.accent();
        titre = Ui.t("gui.heralisz_bases.piller.titre");
        regles = Ui.t("gui.heralisz_bases.piller.regles");
        vide = Ui.t("gui.heralisz_bases.piller.vide");
        List<PillableVue> l = ClientBases.pillables();
        nombre = Ui.tr(Ui.SMALL, l.size() <= 1 ? "gui.heralisz_bases.piller.nombre_un" : "gui.heralisz_bases.piller.nombre", l.size());
        addRenderableWidget(new HzIcon(px + pw - 24, py + 10, 12, Icons.CROIX, Component.translatable("gui.heralisz_bases.fermer"), this::onClose));
        int ly = py + ENTETE + 10;
        liste = new Liste(px + Ui.MARGE, ly, pw - 2 * Ui.MARGE, py + ph - 40 - ly, l);
        liste.accent(accent);
        addRenderableWidget(liste);
        addRenderableWidget(new HzButton(px + Ui.MARGE, py + ph - 26, 120, 14, Component.translatable("gui.heralisz_bases.lien_base"), accent,
                () -> BasesClient.ouvrir(retour)).look(HzButton.Look.LINK).icone(Icons.BUNKER));
        derniereSeconde = 0;
        chaqueSeconde();
    }

    /** Compte a rebours de la fenetre (une fois par seconde). */
    private void chaqueSeconde() {
        FenetreVue f = ClientBases.fenetre();
        if (f == null) {
            chronoLibelle = "";
            chrono = "";
            return;
        }
        if (f.msAvantChangement() < 0) {
            chronoLibelle = Ui.t(f.ouverte() ? "gui.heralisz_bases.fenetre.forcee_ouverte" : "gui.heralisz_bases.fenetre.forcee_fermee");
            chrono = "";
        } else {
            long reste = Math.max(0, f.msAvantChangement() - (System.currentTimeMillis() - ClientBases.pillablesRecuMs()));
            chronoLibelle = Ui.t(f.ouverte() ? "gui.heralisz_bases.piller.fermeture_dans" : "gui.heralisz_bases.piller.ouverture_dans");
            chrono = Ui.chrono(reste);
            // la fenetre vient de changer : la liste est redemandee (une fois toutes les 5 s au plus)
            long now = System.currentTimeMillis();
            if (reste == 0 && now - redemandeLe > 5000 && !ClientBases.estFaux()) {
                redemandeLe = now;
                BasesClient.demander(Packets.ActionC2S.Genre.PILLABLES, "");
            }
        }
        chronoCouleur = f.ouverte() ? Ui.DANGER : Ui.MUTED;
    }

    @Override
    public void tick() {
        if (ClientBases.versionPillables() != vListe) {
            rebuildWidgets();
            return;
        }
        long s = System.currentTimeMillis() / 1000;
        if (s != derniereSeconde) {
            derniereSeconde = s;
            chaqueSeconde();
        }
    }

    /** Banc d'essai : fige la barre PILLER de la carte {@code index} a {@code fraction}. */
    public void figerMaintien(int index, float fraction) {
        if (liste != null && index >= 0 && index < liste.holds.size() && liste.holds.get(index) != null) liste.holds.get(index).figer(fraction);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (BasesKeys.OUVRIR.matches(key, scan) && System.currentTimeMillis() - ouvertLe > 300) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        // relachement transmis a la liste meme hors d'elle (fin d'un maintien)
        if (liste != null) liste.mouseReleased(mx, my, button);
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Sons.jouer(Sons.FERMER, 0.6f);
        BasesClient.demander(Packets.ActionC2S.Genre.FERMER, "");
        if (minecraft != null) minecraft.setScreen(retour);
    }

    @Override
    public void removed() {
        Echelle.restaurer();
        super.removed();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Ui.fond(g, width, height);
        Ui.glass(g, px, py, pw, ph);
        Icons.draw(g, Icons.CIBLE, px + Ui.MARGE, py + 11, 14, Ui.DANGER, 255);
        Ui.spaced(g, font, titre, Ui.BOLD, px + Ui.MARGE + 22, py + 12, Ui.argb(Ui.TEXT, 255), 1.6f);
        Ui.spaced(g, font, regles, Ui.SMALL, px + Ui.MARGE, py + 32, Ui.argb(Ui.MUTED, 255), 0.6f);
        if (!chronoLibelle.isEmpty()) {
            int xr = px + pw - 44;
            Ui.spacedRight(g, font, chronoLibelle, Ui.SMALL, xr, py + 11, Ui.argb(chronoCouleur == Ui.MUTED ? Ui.MUTED
                    : Ui.mix(chronoCouleur, 0xFFFFFF, 0.3f), 255), 1.0f);
            if (!chrono.isEmpty()) Ui.spacedRight(g, font, chrono, Ui.BOLD, xr, py + 24, Ui.argb(chronoCouleur == Ui.MUTED ? Ui.TEXT : chronoCouleur, 255), 1.6f);
        }
        Ui.hairline(g, px + 8, px + pw - 8, py + ENTETE, 0xFFFFFF, 40, true, true);
        if (ClientBases.pillables().isEmpty()) {
            Icons.draw(g, Icons.BUNKER, px + pw / 2 - 12, py + ph / 2 - 30, 24, Ui.FAINT, 200);
            Ui.spacedCentered(g, font, vide, Ui.SMALL, px + pw / 2f, py + ph / 2f + 2, Ui.argb(Ui.MUTED, 255), 1.0f);
        }
        Ui.hairline(g, px + 8, px + pw - 8, py + ph - 34, 0xFFFFFF, 30, true, true);
        Ui.right(g, font, nombre, px + pw - Ui.MARGE, py + ph - 23, Ui.argb(Ui.FAINT, 255));
        super.render(g, mouseX, mouseY, partialTick);
        BasesHud.toastsEcran(g, font, width);
    }

    /**
     * Liste defilante des bases : cartes et boutons PILLER dessines ensemble en coordonnees de contenu. Textes prepares
     * a la construction (une liste = un etat recu).
     */
    private final class Liste extends HzScroll {
        private final List<PillableVue> bases;
        final List<Hold> holds = new ArrayList<>();
        private final List<Component> noms = new ArrayList<>(), tags = new ArrayList<>(), membres = new ArrayList<>(),
                raisons = new ArrayList<>(), pillards = new ArrayList<>();
        private final List<Integer> largeursNom = new ArrayList<>();
        private final String deja;

        Liste(int x, int y, int w, int h, List<PillableVue> bases) {
            super(x, y, w, h);
            this.bases = bases;
            this.deja = Ui.t("gui.heralisz_bases.piller.deja");
            int cy = y;
            for (PillableVue b : bases) {
                Component nom = Ui.lit(b.nom(), Ui.BOLD);
                noms.add(nom);
                largeursNom.add(font.width(nom));
                tags.add(Ui.lit("[" + b.tag() + "]", Ui.FONT));
                membres.add(Ui.tr(Ui.SMALL, b.membres() == 1 ? "gui.heralisz_bases.piller.membre" : "gui.heralisz_bases.piller.membres",
                        b.membres(), b.enLigne()));
                pillards.add(b.pillards() <= 0 ? Component.empty() : Ui.tr(Ui.SMALL, b.pillards() == 1 ? "gui.heralisz_bases.piller.pillard"
                        : "gui.heralisz_bases.piller.pillards", b.pillards()));
                raisons.add(b.pillable() || b.raison().isEmpty() ? Component.empty() : Ui.tr(Ui.SMALL, b.raison()));
                if (b.pillable()) {
                    String tag = b.tag();
                    holds.add(add(new Hold(x + w - 2 - 104, cy + 10, 96, 20, Component.translatable("gui.heralisz_bases.piller.action"), 1000,
                            () -> BasesClient.demander(Packets.ActionC2S.Genre.PILLER, tag)).couleur(0xE0533D)));
                } else {
                    holds.add(null);
                }
                cy += PAS;
            }
            setContentHeight(Math.max(0, bases.size() * PAS - 4));
        }

        @Override
        protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int cw = getWidth() - 4, haut = getY() + getScroll(), bas = haut + getHeight();
            for (int k = 0; k < bases.size(); k++) {
                int y = getY() + k * PAS;
                if (y + CARTE < haut || y > bas) continue;
                PillableVue b = bases.get(k);
                boolean survol = mouseX >= getX() && mouseX < getX() + cw && mouseY >= y && mouseY < y + CARTE;
                Ui.soft(g, getX(), y, cw, CARTE, Ui.argb(0xFFFFFF, b.pillable() ? (survol ? 14 : 9) : 4));
                Ui.softOutline(g, getX() + 1, y + 1, cw - 2, CARTE - 2, Ui.argb(0xFFFFFF, survol ? 26 : 14));
                BannerArt.draw(g, b.banniere(), getX() + 8, y + 4, 24, 32, b.pillable() ? 1f : 0.55f);
                int tx = getX() + 42;
                int texte = b.pillable() ? Ui.TEXT : Ui.MUTED;
                g.drawString(font, noms.get(k), tx, y + 7, Ui.argb(texte, 255), false);
                int tagX = tx + largeursNom.get(k) + 6;
                g.drawString(font, tags.get(k), tagX, y + 7, Ui.argb(b.banniere().accent(), 255), false);
                int ty = y + 22;
                Ui.point(g, tx, ty + 2, b.enLigne() > 0 ? Ui.OK : Ui.FAINT, 255);
                g.drawString(font, membres.get(k), tx + 9, ty, Ui.argb(Ui.MUTED, 255), false);
                int mx = tx + 9 + font.width(membres.get(k)) + 10;
                if (b.pillards() > 0) {
                    Icons.draw(g, Icons.CIBLE, mx, ty, 8, Ui.AMBRE, 230);
                    g.drawString(font, pillards.get(k), mx + 11, ty, Ui.argb(Ui.AMBRE, 230), false);
                    mx += 11 + font.width(pillards.get(k)) + 10;
                }
                if (b.dejaPilleeParVous()) Ui.chip(g, font, deja, mx, ty - 2, Ui.AMBRE);
                if (!b.pillable()) {
                    Component r = raisons.get(k);
                    Ui.right(g, font, r, getX() + cw - 12, y + 16, Ui.argb(Ui.FAINT, 255));
                }
            }
            super.renderContent(g, mouseX, mouseY, partialTick);
        }
    }
}
