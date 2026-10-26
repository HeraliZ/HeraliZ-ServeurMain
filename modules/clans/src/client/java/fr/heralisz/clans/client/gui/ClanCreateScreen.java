package fr.heralisz.clans.client.gui;

import fr.heralisz.clans.ClansConfig;
import fr.heralisz.clans.HeraliszClans;
import fr.heralisz.clans.client.ClientClans;
import fr.heralisz.clans.client.gui.atelier.Modeles;
import fr.heralisz.clans.client.gui.kit.BannerArt;
import fr.heralisz.clans.client.gui.kit.Echelle;
import fr.heralisz.clans.client.gui.kit.HzArea;
import fr.heralisz.clans.client.gui.kit.HzButton;
import fr.heralisz.clans.client.gui.kit.HzField;
import fr.heralisz.clans.client.gui.kit.HzIcon;
import fr.heralisz.clans.client.gui.kit.Icons;
import fr.heralisz.clans.client.gui.kit.Ui;
import fr.heralisz.clans.model.Banniere;
import fr.heralisz.clans.model.Clan;
import fr.heralisz.clans.model.ClanSnapshot;
import fr.heralisz.clans.network.ClansNet;
import fr.heralisz.clans.network.Packets;
import fr.heralisz.clans.registry.Sons;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Fondation d'un clan (panneau 520 x 300, echelle forcee) : a gauche l'apercu vivant de la banniere (fanion 120 x 160,
 * clic = atelier) avec le nom et le tag saisis ; a droite TAG, NOM, DESCRIPTION, puis BANNIERE : une rangee de 8
 * modeles (vignettes cliquables, 2 pages : molette ou points) et « PERSONNALISER » qui ouvre l'{@link AtelierScreen}
 * (« VALIDER » y rend la banniere ici). Validations en ligne (regles de {@link Clan}), cout affiche, bouton PRIMARY
 * « FONDER LE CLAN » -> CreerClanC2S ; les erreurs du serveur arrivent en bandeau discret au-dessus du bouton
 * ({@link #erreurServeur}). La saisie survit a l'aller-retour dans l'atelier (meme instance d'ecran).
 */
public class ClanCreateScreen extends Screen {
    /** Rangee de modeles (decalages depuis le coin du panneau ; colonne de droite a x = 208). */
    public static final int DROITE_X = 208, MODELES_TITRE_Y = 110, MODELES_Y = 122, MODELE_W = 34, MODELE_H = 50, MODELE_PAS = 37,
            MODELES_PAGE = 8, POINTS_Y = 177, PERSONNALISER_Y = 190, PERSONNALISER_W = 150;
    /** Apercu (fanion 120 x 160) : coin depuis le coin du panneau. */
    public static final int APERCU_X = 44, APERCU_Y = 40, APERCU_W = 120, APERCU_H = 160;
    @Nullable
    private final Screen retour;
    private Banniere b;
    /**
     * Derniere banniere composee dans l'atelier quand un clic sur un modele l'a remplacee : le lien « REPRENDRE MA
     * BANNIERE » (a la place de l'aide de PERSONNALISER) la rend, un clic par megarde ne perd plus le travail.
     */
    @Nullable
    private Banniere perso;
    private HzField tag, nom;
    private HzArea desc;
    private HzButton valider, personnaliser, reprendre;
    private String erreur = "";
    private long erreurDepuis;
    private int px, py, pw, ph;
    private String tagV = "", nomV = "", descV = "";
    private int page;
    private String titreBanniere = "";
    private Component apercuAide = Component.empty(), personnaliserAide = Component.empty();
    private final List<Component> infos = new ArrayList<>(1);

    public ClanCreateScreen() {
        this(null, Banniere.DEFAUT);
    }

    /** Fondation ; `retour` = ecran a rouvrir si on annule (l'ecran sans clan, par exemple). */
    public static ClanCreateScreen pourFondation(@Nullable Screen retour) {
        return new ClanCreateScreen(retour instanceof ClanScreen ? retour : null, Banniere.DEFAUT);
    }

    private ClanCreateScreen(@Nullable Screen retour, Banniere initiale) {
        super(Component.translatable("gui.creation.titre"));
        this.retour = retour;
        this.b = initiale;
    }

    /** Ancien mode « banniere seule » (remplace par l'atelier) : toujours faux, garde pour ClientHandlers. */
    public boolean banniereSeule() {
        return false;
    }

    /** Banniere en cours (passee a l'atelier). */
    public Banniere banniere() {
        return b;
    }

    /** Banniere validee dans l'atelier : elle remplace celle de la fondation (l'ecran est rouvert ensuite). */
    public void recevoirBanniere(Banniere n) {
        if (n == null) return;
        b = n;
        perso = null;
    }

    @Override
    protected void init() {
        Echelle.forcer(this);
        pw = Math.min(520, width - 32);
        ph = Math.min(300, height - 24);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        int accent = b.accent();
        int rx = px + DROITE_X, rw = pw - DROITE_X - 16;
        addRenderableWidget(new HzIcon(px + pw - 24, py + 9, 12, Icons.CROIX, Component.translatable("gui.fermer"), this::onClose));
        // TAG et NOM sont cote a cote : leurs erreurs partagent une seule ligne sous la rangee (voir render)
        tag = new HzField(rx, py + 22, 60, 16, Clan.TAG_MAX, HzField.TAG).accent(accent).remplacerAuFocus(false).erreurVisible(false)
                .placeholder(Component.translatable("gui.creation.tag_invite").getString());
        tag.transform(s -> s.toUpperCase(Locale.ROOT));
        tag.setValue(tagV);
        tag.setResponder(v -> {
            tagV = v;
            tag.setErreur(v.isEmpty() ? "" : cle(Clan.validerTag(v)));
        });
        if (!tagV.isEmpty()) tag.setErreur(cle(Clan.validerTag(tagV)));
        addRenderableWidget(tag);
        nom = new HzField(rx + 72, py + 22, rw - 72, 16, Clan.NOM_MAX, HzField.TOUT).accent(accent).remplacerAuFocus(false).erreurVisible(false)
                .placeholder(Component.translatable("gui.creation.nom_invite").getString());
        nom.setValue(nomV);
        nom.setResponder(v -> {
            nomV = v;
            nom.setErreur(v.isEmpty() ? "" : cle(Clan.validerNom(v)));
        });
        if (!nomV.isEmpty()) nom.setErreur(cle(Clan.validerNom(nomV)));
        addRenderableWidget(nom);
        desc = new HzArea(rx, py + 60, rw, 36, Clan.DESC_MAX, Clan.DESC_LIGNES).accent(accent)
                .placeholder(Component.translatable("gui.creation.desc_invite").getString());
        desc.setValue(descV);
        desc.setResponder(v -> {
            descV = v;
            desc.setErreur(cle(Clan.validerDescription(v)));
        });
        addRenderableWidget(desc);
        personnaliser = addRenderableWidget(new HzButton(rx, py + PERSONNALISER_Y, PERSONNALISER_W, 18, Component.translatable("gui.creation.personnaliser"),
                accent, this::ouvrirAtelier));
        reprendre = addRenderableWidget(new HzButton(rx + PERSONNALISER_W + 10, py + PERSONNALISER_Y, rw - PERSONNALISER_W - 10, 18,
                Component.translatable("gui.creation.reprendre"), accent, this::reprendre).look(HzButton.Look.LINK));
        majReprendre();
        valider =new HzButton(rx + rw - 130, py + ph - 16 - 18, 130, 18, Component.translatable("gui.fonder_le_clan"), accent, this::valider)
                .look(HzButton.Look.PRIMARY);
        addRenderableWidget(valider);
        titreBanniere = Component.translatable("gui.creation.banniere_modeles").getString();
        apercuAide = Ui.tr(Ui.SMALL, "gui.creation.apercu_aide");
        personnaliserAide = Ui.tr(Ui.SMALL, "gui.creation.personnaliser_aide");
        page = Math.max(0, Math.min(page, pages() - 1));
    }

    private static String cle(@Nullable String k) {
        return k == null ? "" : Component.translatable(k).getString();
    }

    private int pages() {
        return Math.max(1, (Modeles.tous().size() + MODELES_PAGE - 1) / MODELES_PAGE);
    }

    private void ouvrirAtelier() {
        if (minecraft == null) return;
        Ui.son(Sons.UI_OUVRIR, 0.6f);
        minecraft.setScreen(AtelierScreen.pourCreation(this, b));
    }

    private void choisirModele(Modeles.Modele m) {
        Ui.son(Sons.UI_CLIC, 0.5f);
        // une composition personnelle (ni un modele, ni la banniere par defaut) est gardee pour « REPRENDRE »
        if (perso == null && !b.equals(Banniere.DEFAUT) && !estModele(b)) perso = b;
        changerBanniere(m.banniere());
    }

    /** Rend la composition remplacee par un modele. */
    private void reprendre() {
        if (perso != null) changerBanniere(perso);
    }

    private static boolean estModele(Banniere x) {
        for (Modeles.Modele m : Modeles.tous()) if (m.banniere().equals(x)) return true;
        return false;
    }

    private void changerBanniere(Banniere n) {
        b = n;
        // l'accent suit la couleur A : on le propage sans reconstruire (les champs gardent leur focus)
        int accent = b.accent();
        valider.accent(accent);
        personnaliser.accent(accent);
        reprendre.accent(accent);
        tag.accent(accent);
        nom.accent(accent);
        desc.accent(accent);
        majReprendre();
    }

    /** Le lien n'apparait que si une composition a ete remplacee et n'est pas celle affichee. */
    private void majReprendre() {
        reprendre.visible = perso != null && !perso.equals(b);
        reprendre.active = reprendre.visible;
    }

    @Override
    public void tick() {
        tag.tick();
        nom.tick();
        desc.tick();
    }

    /** Erreur renvoyee par le serveur (bandeau discret, 6 s). */
    public void erreurServeur(String texte) {
        erreur = texte == null ? "" : texte;
        erreurDepuis = Util.getMillis();
    }

    private long cout() {
        try {
            return ClansConfig.COUT_CREATION.get();
        } catch (Exception ex) {
            return 1750;
        }
    }

    /** Solde personnel si on le connait (snapshot, ou mod economie par reflexion), -1 sinon. */
    private long soldePerso() {
        ClanSnapshot s = ClientClans.snapshot();
        if (s != null) return s.monSolde();
        if (!Ui.economiePresente()) return -1;
        try {
            Class<?> c = Class.forName("fr.heralisz.economie.client.ClientSolde");
            Object v = c.getMethod("solde").invoke(null);
            return v instanceof Number n ? n.longValue() : -1;
        } catch (Throwable t) {
            return -1;
        }
    }

    private void valider() {
        String eTag = Clan.validerTag(tagV), eNom = Clan.validerNom(nomV), eDesc = Clan.validerDescription(descV);
        tag.setErreur(cle(eTag));
        nom.setErreur(cle(eNom));
        desc.setErreur(cle(eDesc));
        if (eTag != null || eNom != null || eDesc != null) {
            erreurServeur(Component.translatable("gui.creation.corriger").getString());
            return;
        }
        Banniere v = b.valider();
        if (minecraft != null && minecraft.getConnection() != null) {
            ClansNet.toServer(new Packets.CreerClanC2S(tagV.toUpperCase(Locale.ROOT), Clan.nettoyer(nomV), Clan.nettoyerDescription(descV), v));
        } else {
            HeraliszClans.LOG.info("[clans] fondation (hors ligne) : {} {} {}", tagV, nomV, v.toJson());
        }
    }

    // ------------------------------------------------------------------ rendu
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Ui.fond(g, width, height);
        Ui.glass(g, px, py, pw, ph);
        int accent = b.accent();
        infos.clear();
        // titre
        Ui.spaced(g, font, title.getString(), Ui.BOLD, px + Ui.MARGE, py + 11, Ui.argb(Ui.TEXT, 255), 1.6f);
        // filet sous le titre limite a la colonne de gauche : a droite, les champs TAG / NOM sont a la meme hauteur
        Ui.hairline(g, px + 8, px + DROITE_X - 12, py + 28, 0xFFFFFF, 40, true, true);
        // apercu vivant (texture de travail unique : pas une texture par modele essaye) ; clic = atelier
        int bx = px + APERCU_X, by = py + APERCU_Y;
        boolean survolApercu = dans(mouseX, mouseY, bx, by, APERCU_W, APERCU_H);
        Ui.soft(g, px + 16, py + 36, 176, ph - 52, Ui.argb(0xFFFFFF, survolApercu ? 9 : 5));
        Ui.softOutline(g, px + 17, py + 37, 174, ph - 54, survolApercu ? Ui.argb(accent, 120) : Ui.argb(0xFFFFFF, 10));
        BannerArt.drawVivante(g, b, bx, by, APERCU_W, APERCU_H);
        String n = nomV.isBlank() ? Component.translatable("gui.creation.nom_apercu").getString() : Clan.nettoyer(nomV);
        String t = tagV.isBlank() ? "" : "[" + tagV.toUpperCase(Locale.ROOT) + "]";
        Ui.centered(g, font, Ui.lit(Ui.ellipsis(font, n, Ui.BOLD, 160), Ui.BOLD), px + 104, by + 170, Ui.argb(nomV.isBlank() ? Ui.FAINT : Ui.TEXT, 255));
        if (!t.isEmpty()) Ui.centered(g, font, Ui.lit(t, Ui.FONT), px + 104, by + 182, Ui.argb(Ui.MUTED, 255));
        if (survolApercu) Ui.centered(g, font, apercuAide, px + 104, by + 196, Ui.argb(Ui.mix(accent, 0xFFFFFF, 0.4f), 255));
        // libelles de droite
        int rx = px + DROITE_X, rw = pw - DROITE_X - 16;
        Ui.spaced(g, font, Component.translatable("gui.creation.tag").getString(), Ui.SMALL, rx, py + 14, Ui.argb(Ui.MUTED, 255), 1.0f);
        Ui.spaced(g, font, Component.translatable("gui.creation.nom").getString(), Ui.SMALL, rx + 72, py + 14, Ui.argb(Ui.MUTED, 255), 1.0f);
        Ui.spaced(g, font, Component.translatable("gui.creation.description").getString(), Ui.SMALL, rx, py + 52, Ui.argb(Ui.MUTED, 255), 1.0f);
        // une seule ligne d'erreur sous la rangee TAG · NOM : celle du tag d'abord (les deux filets restent rouges)
        String e = !tag.getErreur().isEmpty() ? tag.getErreur() : nom.getErreur();
        if (!e.isEmpty()) g.drawString(font, Ui.lit(Ui.ellipsis(font, e, Ui.SMALL, rw), Ui.SMALL), rx, py + 40, Ui.argb(Ui.DANGER, 255), false);
        // banniere : rangee de modeles + pagination
        Ui.section(g, font, titreBanniere, rx, py + MODELES_TITRE_Y, rw);
        List<Modeles.Modele> modeles = Modeles.tous();
        int debut = page * MODELES_PAGE;
        for (int k = 0; k < MODELES_PAGE && debut + k < modeles.size(); k++) {
            Modeles.Modele m = modeles.get(debut + k);
            int cx = rx + k * MODELE_PAS, cy = py + MODELES_Y;
            boolean actuel = m.banniere().equals(b), survol = dans(mouseX, mouseY, cx, cy, MODELE_W, MODELE_H);
            if (actuel) Ui.soft(g, cx, cy, MODELE_W, MODELE_H, Ui.argb(accent, 40));
            else Ui.soft(g, cx, cy, MODELE_W, MODELE_H, Ui.argb(0xFFFFFF, survol ? 16 : 6));
            BannerArt.draw(g, m.banniere(), cx + 3, cy + 4, 28, 42);
            if (actuel) Ui.softOutline(g, cx + 1, cy + 1, MODELE_W - 2, MODELE_H - 2, Ui.argb(accent, 230));
            else if (survol) Ui.softOutline(g, cx + 1, cy + 1, MODELE_W - 2, MODELE_H - 2, Ui.argb(0xFFFFFF, 90));
            if (survol) infos.add(m.nom());
        }
        int np = pages();
        if (np > 1) {
            int x0 = rx + (MODELES_PAGE * MODELE_PAS - 3) / 2 - (np * 10 - 5) / 2;
            for (int p = 0; p < np; p++) {
                int dx = x0 + p * 10;
                boolean survol = dans(mouseX, mouseY, dx - 2, py + POINTS_Y - 2, 9, 9);
                Ui.soft(g, dx, py + POINTS_Y, 5, 5, p == page ? Ui.argb(accent, 255) : Ui.argb(0xFFFFFF, survol ? 120 : 45));
            }
        }
        if (!reprendre.visible) g.drawString(font, personnaliserAide, rx + PERSONNALISER_W + 10, py + PERSONNALISER_Y + 5, Ui.argb(Ui.FAINT, 255), false);
        // pied : cout
        int fy = py + ph - 16 - 18;
        long solde = soldePerso();
        Component c = Ui.jetons(cout(), Ui.SMALL);
        g.drawString(font, Ui.in(Component.translatable("gui.cout", c), Ui.SMALL), rx, fy + 2, Ui.argb(Ui.MUTED, 255), false);
        if (solde >= 0) {
            boolean ok = solde >= cout();
            g.drawString(font, Ui.in(Component.translatable("gui.vous_avez", Ui.jetons(solde, Ui.SMALL)), Ui.SMALL), rx, fy + 11,
                    Ui.argb(ok ? Ui.FAINT : Ui.DANGER, 255), false);
        }
        super.render(g, mouseX, mouseY, partialTick);
        // bandeau d'erreur serveur : au-dessus du bouton de validation (la rangee TAG · NOM a deja sa ligne d'erreur)
        if (!erreur.isEmpty()) {
            long age = Util.getMillis() - erreurDepuis;
            if (age > 6000) erreur = "";
            else {
                int a = age > 5000 ? (int) (255 * (6000 - age) / 1000f) : 255;
                int wTxt = font.width(Ui.lit(erreur, Ui.SMALL)) + 16;
                int ex = px + pw - 16 - Math.min(wTxt, rw), ey = fy - 20;
                Ui.soft(g, ex, ey, Math.min(wTxt, rw), 14, Ui.argb(Ui.DANGER, Math.min(a, 40)));
                g.fill(ex, ey, ex + 2, ey + 14, Ui.argb(Ui.DANGER, a));
                g.drawString(font, Ui.lit(Ui.ellipsis(font, erreur, Ui.SMALL, rw - 16), Ui.SMALL), ex + 8, ey + 3, Ui.argb(Ui.DANGER, Math.max(a, 4)), false);
            }
        }
        if (!infos.isEmpty()) Ui.tooltip(g, font, infos, mouseX, mouseY, accent);
    }

    private static boolean dans(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ------------------------------------------------------------------ evenements
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (button != 0) return false;
        int rx = px + DROITE_X;
        // apercu : ouvre l'atelier
        if (dans(mx, my, px + APERCU_X, py + APERCU_Y, APERCU_W, APERCU_H)) {
            setFocused(null);
            ouvrirAtelier();
            return true;
        }
        // vignettes de modeles
        List<Modeles.Modele> modeles = Modeles.tous();
        int debut = page * MODELES_PAGE;
        for (int k = 0; k < MODELES_PAGE && debut + k < modeles.size(); k++) {
            if (dans(mx, my, rx + k * MODELE_PAS, py + MODELES_Y, MODELE_W, MODELE_H)) {
                setFocused(null);
                choisirModele(modeles.get(debut + k));
                return true;
            }
        }
        // points de pagination
        int np = pages();
        int x0 = rx + (MODELES_PAGE * MODELE_PAS - 3) / 2 - (np * 10 - 5) / 2;
        for (int p = 0; p < np; p++) {
            if (dans(mx, my, x0 + p * 10 - 2, py + POINTS_Y - 2, 9, 9)) {
                Ui.son(Sons.UI_CLIC, 0.5f);
                page = p;
                return true;
            }
        }
        setFocused(null);
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (dans(mx, my, px + DROITE_X, py + MODELES_Y, MODELES_PAGE * MODELE_PAS, MODELE_H + 14)) {
            int p = Math.max(0, Math.min(pages() - 1, page + (delta < 0 ? 1 : -1)));
            if (p != page) {
                Ui.son(Sons.UI_CLIC, 0.4f);
                page = p;
            }
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    private boolean saisieEnCours() {
        GuiEventListener f = getFocused();
        return (f instanceof HzField || f instanceof HzArea) && ((AbstractWidget) f).isFocused();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (saisieEnCours()) {
                setFocused(null);
                return true;
            }
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void onClose() {
        Ui.son(Sons.UI_FERMER, 0.6f);
        if (minecraft != null) minecraft.setScreen(retour instanceof ClanScreen ? new ClanScreen(ClanScreen.Onglet.APERCU) : null);
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
