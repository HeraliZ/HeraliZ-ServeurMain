package fr.heralisz.clans.client.gui;

import fr.heralisz.clans.HeraliszClans;
import fr.heralisz.clans.client.ClientClans;
import fr.heralisz.clans.client.gui.atelier.Atelier;
import fr.heralisz.clans.client.gui.atelier.Historique;
import fr.heralisz.clans.client.gui.atelier.ListeCalques;
import fr.heralisz.clans.client.gui.atelier.Modeles;
import fr.heralisz.clans.client.gui.atelier.Noms;
import fr.heralisz.clans.client.gui.atelier.PanneauCalque;
import fr.heralisz.clans.client.gui.atelier.PanneauFond;
import fr.heralisz.clans.client.gui.atelier.Pictos;
import fr.heralisz.clans.client.gui.atelier.Selecteur;
import fr.heralisz.clans.client.gui.atelier.Toile;
import fr.heralisz.clans.client.gui.kit.Echelle;
import fr.heralisz.clans.client.gui.kit.HzButton;
import fr.heralisz.clans.client.gui.kit.HzField;
import fr.heralisz.clans.client.gui.kit.HzIcon;
import fr.heralisz.clans.client.gui.kit.Icons;
import fr.heralisz.clans.client.gui.kit.Ui;
import fr.heralisz.clans.model.Banniere;
import fr.heralisz.clans.model.Calque;
import fr.heralisz.clans.model.ClanSnapshot;
import fr.heralisz.clans.model.Formes;
import fr.heralisz.clans.model.Perm;
import fr.heralisz.clans.network.ClansNet;
import fr.heralisz.clans.network.Packets;
import fr.heralisz.clans.registry.Sons;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Atelier de banniere (cahier des charges BANNIERE_V2_SPEC §5) : ecran plein panneau a echelle forcee (canevas
 * 640 x 360), HUD masque. Ouvert depuis la fondation (aperçu ou PERSONNALISER : « VALIDER » rend la banniere a l'ecran
 * de fondation) ou depuis Parametres › Banniere › MODIFIER (« ENREGISTRER » envoie BanniereC2S ; delai et permission
 * affiches, le serveur revalide tout).
 * <p>Disposition (decalages depuis le coin du panneau 600 x 340, voir docs/UI.md) : a gauche la {@link Toile} et la
 * rangee d'outils (grille, annuler, retablir, miroir H, miroir V, dupliquer, supprimer) ; au milieu la
 * {@link ListeCalques} « CALQUES n/12 » et « + AJOUTER » ; a droite le {@link Selecteur} (ajout), le
 * {@link PanneauCalque} (calque selectionne) ou le {@link PanneauFond} (rien de selectionne) ; en pied ANNULER et le
 * bouton principal.
 * <p>Clavier : Ctrl+Z / Ctrl+Y (ou Ctrl+Maj+Z) sur 50 etats, Suppr, Ctrl+D, fleches (1 unite, 1 case avec la grille,
 * Maj x4), Q / E rotation ±15° (Maj : ±1°), + / − taille (± 2 unites, Ctrl : ± 8), G grille, Tab calque suivant (Maj : precedent),
 * Page haut / bas = monter / descendre dans la pile, Echap = ferme le selecteur → deselectionne → quitte (si modifie :
 * bandeau « Echap encore pour abandonner », jamais de fenetre modale).
 */
public class AtelierScreen extends Screen implements Atelier {
    public enum Mode { CREATION, PARAMETRES }

    // ------------------------------------------------------------------ mise en page (decalages depuis le coin du panneau)
    public static final int PANNEAU_L = 600, PANNEAU_H = 340;
    /** Zone de la toile (recoit les clics, poignees comprises) et coin de la banniere 144 x 216. */
    public static final int TOILE_ZX = 16, TOILE_ZY = 30, TOILE_ZW = 176, TOILE_ZH = 230, TOILE_X = 32, TOILE_Y = 38;
    /** Rangee d'outils : 7 cases de 19 x 17 au pas de 22. */
    public static final int OUTILS_X = 28, OUTILS_Y = 262, OUTIL_W = 19, OUTIL_H = 17, OUTIL_PAS = 22;
    /** Colonne des calques. */
    public static final int LISTE_X = 204, LISTE_W = 140, LISTE_TITRE_Y = 36, LISTE_Y = 48, AJOUTER_Y = 270, AJOUTER_H = 16;
    /** Colonne de droite (largeur = panneau - 372). */
    public static final int DROITE_X = 356, DROITE_Y = 36, SELECTEUR_H = 254;
    /** Pied : bouton principal 130 x 18 a droite, lien ANNULER a sa gauche. */
    public static final int PIED_Y_DEPUIS_BAS = 34, BOUTON_W = 130, BOUTON_H = 18, ANNULER_W = 60;
    /** Hexa : 56 x 14. */
    public static final int HEXA_W = 56, HEXA_H = 14;
    private static final long ABANDON_MS = 3000;
    /** Attente de la reponse du serveur apres ENREGISTRER (au-dela : bouton rendu et message), duree du bandeau d'erreur. */
    private static final long ATTENTE_MS = 10_000, ERREUR_MS = 6000;
    private static final int[] OUTILS = {Pictos.GRILLE, Pictos.ANNULER, Pictos.RETABLIR, Pictos.MIROIR_H, Pictos.MIROIR_V, Pictos.DUPLIQUER,
            Pictos.SUPPRIMER};
    private static final String[] OUTILS_CLES = {"grille", "annuler", "retablir", "miroir_h", "miroir_v", "dupliquer", "supprimer"};
    /** Grille gardee d'une ouverture a l'autre (session). */
    private static boolean grilleMemo = true;

    private final Mode mode;
    @Nullable
    private final Screen retour;
    private final Banniere initiale;
    private Banniere b;
    private int sel = -1, cible;
    private boolean selecteurOuvert;
    private final Historique hist = new Historique();
    private int gesteSuivant = Historique.GESTES;
    private final Toile toile = new Toile(this);
    private final ListeCalques liste = new ListeCalques(this);
    private final Selecteur selecteur = new Selecteur(this);
    private final PanneauCalque panneauCalque = new PanneauCalque(this);
    private final PanneauFond panneauFond = new PanneauFond(this);
    private HzField hexa;
    private HzButton ajouter, principal, annuler;
    private int px, py, pw, ph;
    private long abandonDepuis = -1;
    private int ticks;
    /**
     * ENREGISTRER (Parametres) envoye, reponse attendue : l'atelier reste ouvert (bouton « ENREGISTREMENT… ») jusqu'a un
     * snapshot qui porte cette banniere (succes : on quitte) ou une notice ERREUR (on reste, bandeau) : un refus
     * (delai, permission retiree, cadence) ne perd plus la composition.
     */
    @Nullable
    private Banniere envoyee;
    @Nullable
    private ClanSnapshot snapEnvoi;
    private long envoiDepuis, erreurDepuis = -1;
    private Component erreur = Component.empty(), libellePrincipal = Component.empty(), libelleAttente = Component.empty();
    // textes calcules hors du rendu
    private String titre = "", titreCalques = "";
    private int compteCalques = -1;
    private final Component[] outilsTitres = new Component[OUTILS.length], outilsTouches = new Component[OUTILS.length];
    private Component pied = Component.empty(), abandon = Component.empty();
    private int piedCouleur = Ui.FAINT;
    // infobulle de l'image courante
    @Nullable
    private Component infoTitre, infoSous;
    private final List<Component> infos = new ArrayList<>(2);

    private AtelierScreen(Mode mode, @Nullable Screen retour, Banniere initiale) {
        super(Component.translatable("gui.atelier.titre"));
        this.mode = mode;
        this.retour = retour;
        this.initiale = initiale == null ? Banniere.DEFAUT : initiale;
        this.b = this.initiale;
    }

    /** Depuis l'ecran de fondation : « VALIDER » lui rend la banniere, ANNULER y revient sans rien changer. */
    public static AtelierScreen pourCreation(ClanCreateScreen retour, Banniere b) {
        return new AtelierScreen(Mode.CREATION, retour, b);
    }

    /** Depuis Parametres › Banniere : « ENREGISTRER » envoie BanniereC2S puis revient a l'onglet Parametres. */
    public static AtelierScreen pourParametres(@Nullable Screen retour) {
        ClanSnapshot s = ClientClans.snapshot();
        return new AtelierScreen(Mode.PARAMETRES, retour, s != null ? s.banniere() : Banniere.DEFAUT);
    }

    public Mode mode() {
        return mode;
    }

    // ------------------------------------------------------------------ construction
    @Override
    protected void init() {
        Echelle.forcer(this);
        Noms.vider();
        pw = Math.min(PANNEAU_L, width - 16);
        ph = Math.min(PANNEAU_H, height - 12);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        int acc = accent();
        toile.placer(px + TOILE_X, py + TOILE_Y, px + TOILE_ZX, py + TOILE_ZY, TOILE_ZW, TOILE_ZH);
        liste.placer(px + LISTE_X, py + LISTE_Y, LISTE_W);
        int dx = px + DROITE_X, dw = pw - DROITE_X - 16;
        selecteur.placer(dx, py + DROITE_Y, dw, SELECTEUR_H);
        panneauCalque.placer(dx, py + DROITE_Y, dw);
        panneauFond.placer(dx, py + DROITE_Y, dw);

        addRenderableWidget(new HzIcon(px + pw - 24, py + 9, 12, Icons.CROIX, Component.translatable("gui.fermer"), this::abandonner));
        hexa = new HzField(panneauFond.hexaX(), panneauFond.hexaY(), HEXA_W, HEXA_H, 6, HzField.HEXA).accent(acc).remplacerAuFocus(true)
                .placeholder("RRGGBB");
        hexa.transform(v -> v.toUpperCase(Locale.ROOT));
        hexa.setValue(hex(couleurCible()));
        hexa.setResponder(v -> {
            if (v.length() == 6) {
                try {
                    appliquerCouleur(Integer.parseInt(v, 16), Historique.HEXA);
                } catch (NumberFormatException ignore) {
                    // filtre HEXA : impossible
                }
            }
        });
        addRenderableWidget(hexa);
        ajouter = addRenderableWidget(new HzButton(px + LISTE_X, py + AJOUTER_Y, LISTE_W, AJOUTER_H, Component.translatable("gui.atelier.ajouter"), acc,
                this::basculerSelecteur));
        int fy = py + ph - PIED_Y_DEPUIS_BAS;
        libellePrincipal = Component.translatable(mode == Mode.CREATION ? "gui.atelier.valider" : "gui.action.enregistrer");
        libelleAttente = Component.translatable("gui.atelier.enregistrement");
        principal = addRenderableWidget(new HzButton(px + pw - 16 - BOUTON_W, fy, BOUTON_W, BOUTON_H, libellePrincipal, acc, this::enregistrer)
                .look(HzButton.Look.PRIMARY));
        annuler = addRenderableWidget(new HzButton(px + pw - 16 - BOUTON_W - 16 - ANNULER_W, fy, ANNULER_W, BOUTON_H,
                Component.translatable("gui.atelier.annuler"), acc, this::abandonner).look(HzButton.Look.LINK));

        titre = title.getString();
        for (int k = 0; k < OUTILS.length; k++) {
            outilsTitres[k] = Ui.tr(Ui.FONT, "gui.atelier.outil." + OUTILS_CLES[k]);
            outilsTouches[k] = Ui.tr(Ui.SMALL, "gui.atelier.outil." + OUTILS_CLES[k] + ".touche");
        }
        abandon = Ui.tr(Ui.SMALL, "gui.atelier.abandon");
        compteCalques = -1;
        majPied();
        synchroniser();
    }

    private static String hex(int rgb) {
        return String.format(Locale.ROOT, "%06X", rgb & 0xFFFFFF);
    }

    /** Remet les widgets et textes en accord avec l'etat (appele apres chaque changement, jamais a l'image). */
    private void synchroniser() {
        if (sel >= b.nombreCalques()) sel = b.nombreCalques() - 1;
        if (hexa == null) return;
        int acc = accent();
        hexa.accent(acc);
        hexa.visible = !selecteurOuvert;
        if (!hexa.isFocused()) hexa.setValue(hex(couleurCible()));
        hexa.setX(selecteurOuvert || sel < 0 ? panneauFond.hexaX() : panneauCalque.hexaX());
        hexa.setY(selecteurOuvert || sel < 0 ? panneauFond.hexaY() : panneauCalque.hexaY());
        ajouter.accent(acc).look(selecteurOuvert ? HzButton.Look.PRIMARY : HzButton.Look.OUTLINE);
        ajouter.active = !b.pleine() || selecteurOuvert;
        principal.accent(acc);
        annuler.accent(acc);
        principal.active = peutEnregistrer();
        principal.setMessage(envoyee != null ? libelleAttente : libellePrincipal);
        if (b.nombreCalques() != compteCalques) {
            compteCalques = b.nombreCalques();
            titreCalques = Component.translatable("gui.atelier.calques", compteCalques, Banniere.MAX_CALQUES).getString();
        }
    }

    /** Texte du pied (delai restant, permission) : recalcule a l'ouverture puis une fois par seconde. */
    private void majPied() {
        if (mode == Mode.CREATION) {
            pied = Ui.tr(Ui.SMALL, "gui.atelier.pied_creation");
            piedCouleur = Ui.FAINT;
            return;
        }
        ClanSnapshot s = ClientClans.snapshot();
        if (s == null) {
            pied = Ui.tr(Ui.SMALL, "gui.atelier.sans_clan");
            piedCouleur = Ui.DANGER;
        } else if (!s.jePeux(Perm.MODIFIER_BANNIERE)) {
            pied = Ui.tr(Ui.SMALL, "gui.parametres.sans_permission");
            piedCouleur = Ui.FAINT;
        } else if (ClientClans.reste(s.cdBanniereMs()) > 0) {
            // delai decompte depuis la reception du snapshot (l'atelier n'en recoit pas forcement d'autre)
            pied = Ui.tr(Ui.SMALL, "gui.parametres.prochain", Ui.duree(ClientClans.reste(s.cdBanniereMs())));
            piedCouleur = Ui.AMBRE;
        } else {
            pied = Ui.tr(Ui.SMALL, "gui.atelier.pied_parametres");
            piedCouleur = Ui.FAINT;
        }
    }

    private boolean modifie() {
        return !b.equals(initiale);
    }

    private boolean peutEnregistrer() {
        if (mode == Mode.CREATION) return true;
        if (envoyee != null) return false;
        ClanSnapshot s = ClientClans.snapshot();
        return s != null && s.jePeux(Perm.MODIFIER_BANNIERE) && ClientClans.reste(s.cdBanniereMs()) <= 0 && modifie();
    }

    /** Vrai pendant l'attente de la reponse a ENREGISTRER (ClientHandlers y dirige alors les erreurs du serveur). */
    public boolean enAttente() {
        return envoyee != null;
    }

    /** Refus du serveur pendant l'attente : on reste dans l'atelier, composition intacte, bandeau d'erreur au pied. */
    public void erreurServeur(String texte) {
        envoyee = null;
        snapEnvoi = null;
        String t = texte == null ? "" : texte;
        // le bandeau tient a gauche des boutons du pied
        if (font != null) t = Ui.ellipsis(font, t, Ui.SMALL, pw - BOUTON_W - ANNULER_W - 80);
        erreur = Ui.lit(t, Ui.SMALL);
        erreurDepuis = Util.getMillis();
        majPied();
        synchroniser();
    }

    /** Texte d'un bandeau du pied (police SMALL), coupe avec « … » s'il depasse (fenetre redimensionnee, langue). */
    private Component bandeauTexte(Component c, int max) {
        return font.width(c) <= max ? c : Ui.lit(Ui.ellipsis(font, c.getString(), Ui.SMALL, max), Ui.SMALL);
    }

    /** Suivi de l'envoi (a chaque tick) : snapshot portant la banniere envoyee = succes ; trop long = on rend la main. */
    private void suivreEnvoi() {
        if (envoyee == null) return;
        ClanSnapshot s = ClientClans.snapshot();
        if (s != null && s != snapEnvoi && s.banniere().equals(envoyee)) {
            envoyee = null;
            quitter(false);
            return;
        }
        if (Util.getMillis() - envoiDepuis > ATTENTE_MS) erreurServeur(Component.translatable("gui.atelier.sans_reponse").getString());
    }

    // ------------------------------------------------------------------ Atelier (etat partage avec les morceaux)
    @Override
    public Banniere banniere() {
        return b;
    }

    @Override
    public int selection() {
        return sel;
    }

    @Override
    public void selectionner(int i) {
        int n = i >= 0 && i < b.nombreCalques() ? i : -1;
        if (n != sel) cible = 0;
        sel = n;
        if (n >= 0) selecteurOuvert = false;
        hist.couper();
        synchroniser();
    }

    @Override
    public void changer(Banniere n, int groupe) {
        if (n == null || n.equals(b)) return;
        hist.noter(b, groupe);
        b = n;
        abandonDepuis = -1;
        synchroniser();
    }

    @Override
    public void deplacer(int de, int vers) {
        Banniere n = b.avecCalqueDeplace(de, vers);
        if (n == b) return;
        changer(n, Historique.AUCUN);
        sel = Math.max(0, Math.min(b.nombreCalques() - 1, vers));
        synchroniser();
    }

    @Override
    public int nouveauGeste() {
        return gesteSuivant++;
    }

    @Override
    public boolean grille() {
        return grilleMemo;
    }

    @Override
    public int accent() {
        return b.accent();
    }

    @Override
    public Font police() {
        return font;
    }

    @Override
    public void infobulle(Component t, @Nullable Component sous) {
        infoTitre = t;
        infoSous = sous;
    }

    @Override
    public void ajouterForme(int forme) {
        if (b.pleine() || !Formes.existe(forme)) return;
        Calque c = Calque.de(forme, Banniere.MIGRATION_CX, Banniere.MIGRATION_CY, fr.heralisz.clans.client.gui.atelier.Dessin.contraste(b));
        Banniere n = b.avecCalqueAjoute(c);
        if (n == b) return;
        hist.couper();
        changer(n, Historique.AUCUN);
        sel = b.nombreCalques() - 1;
        cible = 0;
        selecteurOuvert = false;
        synchroniser();
    }

    @Override
    public void appliquerModele(Modeles.Modele m) {
        if (m == null) return;
        hist.couper();
        changer(m.banniere(), Historique.AUCUN);
        sel = -1;
        cible = 0;
        synchroniser();
    }

    @Override
    public int cible() {
        return cible;
    }

    @Override
    public void cible(int c) {
        cible = c == 1 ? 1 : 0;
        hist.couper();
        if (hexa != null) hexa.setFocused(false);
        synchroniser();
    }

    private int couleurCible() {
        Calque c = b.calque(sel);
        if (c != null) return cible == 0 ? c.couleur() : c.couleurContour();
        return cible == 0 || b.degrade == Banniere.Degrade.UNI ? b.couleurA : b.couleurB;
    }

    @Override
    public void appliquerCouleur(int rgb, int groupe) {
        Calque c = b.calque(sel);
        Banniere n;
        if (c != null) {
            if (cible == 0) n = b.avecCalque(sel, c.avecCouleur(rgb));
            // une couleur de contour choisie sur un calque sans contour : on pose un contour de 2 pour qu'elle se voie
            else n = b.avecCalque(sel, c.contour() == 0 ? c.avecCouleurContour(rgb).avecContour(2) : c.avecCouleurContour(rgb));
        } else {
            n = cible == 0 || b.degrade == Banniere.Degrade.UNI ? b.avecCouleurA(rgb) : b.avecCouleurB(rgb);
        }
        changer(n, groupe);
    }

    // ------------------------------------------------------------------ actions
    private void basculerSelecteur() {
        selecteurOuvert = !selecteurOuvert;
        if (hexa.isFocused()) setFocused(null);
        synchroniser();
    }

    private void annulerEtat() {
        Banniere p = hist.annuler(b);
        if (p == null) return;
        b = p;
        Ui.son(Sons.UI_CLIC, 0.4f);
        synchroniser();
    }

    private void retablirEtat() {
        Banniere p = hist.retablir(b);
        if (p == null) return;
        b = p;
        Ui.son(Sons.UI_CLIC, 0.4f);
        synchroniser();
    }

    private void supprimer() {
        if (sel < 0) return;
        int i = sel;
        changer(b.avecCalqueRetire(i), Historique.AUCUN);
        sel = b.nombreCalques() == 0 ? -1 : Math.max(0, i - 1);
        synchroniser();
    }

    private void dupliquer() {
        if (sel < 0 || b.pleine()) return;
        int i = sel;
        changer(b.avecCalqueDuplique(i), Historique.AUCUN);
        sel = i + 1;
        synchroniser();
    }

    private void miroir(boolean horizontal) {
        Calque c = b.calque(sel);
        if (c == null) return;
        changer(b.avecCalque(sel, horizontal ? c.avecMiroirX(!c.miroirX()) : c.avecMiroirY(!c.miroirY())), Historique.AUCUN);
    }

    private void basculerGrille() {
        grilleMemo = !grilleMemo;
    }

    private void tourner(int delta) {
        Calque c = b.calque(sel);
        if (c != null) changer(b.avecCalque(sel, c.avecRotation(c.rot() + delta)), Historique.CLAVIER_TOURNER);
    }

    private void decaler(int dx, int dy, boolean x4) {
        Calque c = b.calque(sel);
        if (c == null) return;
        int pas = (grilleMemo ? Banniere.GRILLE : 1) * (x4 ? 4 : 1);
        changer(b.avecCalque(sel, c.avecDecalage(dx * pas, dy * pas)), Historique.CLAVIER_DEPLACER);
    }

    /** + / − : grandit ou retrecit le calque de 2 unites (8 avec Ctrl) sur son plus grand cote, proportions gardees. */
    private void redimensionner(int signe, boolean x4) {
        Calque c = b.calque(sel);
        if (c == null) return;
        int m = Math.max(c.w(), c.h());
        int nm = Math.max(Calque.TAILLE_MIN, Math.min(Calque.TAILLE_MAX, m + signe * (x4 ? 8 : 2)));
        if (nm == m) return;
        float k = nm / (float) m;
        int nw = Math.max(Calque.TAILLE_MIN, Math.round(c.w() * k)), nh = Math.max(Calque.TAILLE_MIN, Math.round(c.h() * k));
        changer(b.avecCalque(sel, c.avecTaille(nw, nh)), Historique.CLAVIER_TAILLE);
    }

    private void suivant(boolean arriere) {
        int n = b.nombreCalques();
        if (n == 0) return;
        if (sel < 0) selectionner(arriere ? n - 1 : 0);
        else selectionner(Math.floorMod(sel + (arriere ? -1 : 1), n));
    }

    private void outil(int k) {
        Ui.son(Sons.UI_CLIC, 0.5f);
        switch (OUTILS[k]) {
            case Pictos.GRILLE -> basculerGrille();
            case Pictos.ANNULER -> annulerEtat();
            case Pictos.RETABLIR -> retablirEtat();
            case Pictos.MIROIR_H -> miroir(true);
            case Pictos.MIROIR_V -> miroir(false);
            case Pictos.DUPLIQUER -> dupliquer();
            case Pictos.SUPPRIMER -> supprimer();
            default -> {
            }
        }
    }

    private boolean outilActif(int k) {
        return switch (OUTILS[k]) {
            case Pictos.GRILLE -> true;
            case Pictos.ANNULER -> hist.peutAnnuler();
            case Pictos.RETABLIR -> hist.peutRetablir();
            case Pictos.DUPLIQUER -> sel >= 0 && !b.pleine();
            default -> sel >= 0;
        };
    }

    private int outilSous(double mx, double my) {
        int ox = px + OUTILS_X, oy = py + OUTILS_Y;
        if (my < oy || my >= oy + OUTIL_H || mx < ox) return -1;
        int k = (int) ((mx - ox) / OUTIL_PAS);
        if (k < 0 || k >= OUTILS.length || mx - ox - k * OUTIL_PAS >= OUTIL_W) return -1;
        return k;
    }

    private void enregistrer() {
        if (mode == Mode.CREATION) {
            quitter(true);
            return;
        }
        if (!peutEnregistrer()) return;
        Banniere v = b.valider();
        if (minecraft != null && minecraft.getConnection() != null) {
            // on attend la reponse sans fermer (voir envoyee) : un refus laisse la composition et l'historique intacts
            ClansNet.toServer(new Packets.BanniereC2S(v));
            envoyee = v;
            snapEnvoi = ClientClans.snapshot();
            envoiDepuis = Util.getMillis();
            erreurDepuis = -1;
            synchroniser();
            return;
        }
        HeraliszClans.LOG.info("[clans] banniere (hors ligne) : {}", v.toJson());
        quitter(false);
    }

    /** ANNULER, croix, Echap au repos : quitte, ou demande une seconde fois si la banniere a change. */
    private void abandonner() {
        long t = Util.getMillis();
        if (modifie() && (abandonDepuis < 0 || t - abandonDepuis > ABANDON_MS)) {
            abandonDepuis = t;
            Ui.son(Sons.UI_CLIC, 0.5f);
            return;
        }
        quitter(false);
    }

    private void quitter(boolean valider) {
        Ui.son(Sons.UI_FERMER, 0.6f);
        if (minecraft == null) return;
        if (mode == Mode.CREATION) {
            if (retour instanceof ClanCreateScreen cs) {
                if (valider) cs.recevoirBanniere(b.valider());
                minecraft.setScreen(cs);
            } else {
                minecraft.setScreen(null);
            }
        } else {
            minecraft.setScreen(retour instanceof ClanScreen ? new ClanScreen(ClanScreen.Onglet.PARAMETRES) : null);
        }
    }

    private void echap() {
        if (hexa.isFocused()) {
            setFocused(null);
            return;
        }
        if (selecteurOuvert) {
            selecteurOuvert = false;
            synchroniser();
            return;
        }
        if (sel >= 0) {
            selectionner(-1);
            return;
        }
        abandonner();
    }

    // ------------------------------------------------------------------ rendu
    @Override
    public void tick() {
        hexa.tick();
        if (++ticks % 20 == 0) majPied();
        if (abandonDepuis >= 0 && Util.getMillis() - abandonDepuis > ABANDON_MS) abandonDepuis = -1;
        if (erreurDepuis >= 0 && Util.getMillis() - erreurDepuis > ERREUR_MS) erreurDepuis = -1;
        suivreEnvoi();
        if (mode == Mode.PARAMETRES && ticks % 20 == 0) principal.active = peutEnregistrer();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        infoTitre = null;
        infoSous = null;
        int acc = accent();
        Ui.fond(g, width, height);
        Ui.glass(g, px, py, pw, ph);
        Ui.spaced(g, font, titre, Ui.BOLD, px + Ui.MARGE, py + 11, Ui.argb(Ui.TEXT, 255), 1.6f);
        Ui.hairline(g, px + 8, px + pw - 8, py + 28, 0xFFFFFF, 40, true, true);
        // separateurs de colonnes
        g.fill(px + LISTE_X - 8, py + 36, px + LISTE_X - 7, py + 296, Ui.argb(0xFFFFFF, 14));
        g.fill(px + DROITE_X - 6, py + 36, px + DROITE_X - 5, py + 296, Ui.argb(0xFFFFFF, 14));
        // gauche : toile + outils
        toile.render(g, mouseX, mouseY);
        int ox = px + OUTILS_X, oy = py + OUTILS_Y, survolOutil = outilSous(mouseX, mouseY);
        for (int k = 0; k < OUTILS.length; k++) {
            int x = ox + k * OUTIL_PAS;
            boolean actif = outilActif(k), survol = actif && survolOutil == k, allume = OUTILS[k] == Pictos.GRILLE && grilleMemo;
            if (allume) Ui.soft(g, x, oy, OUTIL_W, OUTIL_H, Ui.argb(acc, survol ? 70 : 45));
            else if (survol) Ui.soft(g, x, oy, OUTIL_W, OUTIL_H, Ui.argb(0xFFFFFF, 18));
            int c = !actif ? Ui.argb(Ui.FAINT, 150) : allume || survol ? 0xFFFFFFFF : Ui.argb(Ui.MUTED, 255);
            Pictos.dessiner(g, OUTILS[k], x + 4, oy + 3, c);
            if (survolOutil == k) infobulle(outilsTitres[k], outilsTouches[k]);
        }
        // milieu : calques
        Ui.section(g, font, titreCalques, px + LISTE_X, py + LISTE_TITRE_Y, LISTE_W);
        liste.render(g, mouseX, mouseY);
        // droite : selecteur, calque ou fond
        if (selecteurOuvert) selecteur.render(g, mouseX, mouseY, partialTick);
        else if (sel >= 0) panneauCalque.render(g, mouseX, mouseY);
        else panneauFond.render(g, mouseX, mouseY);
        // pied : delai / permission, ou bandeau d'abandon
        int fy = py + ph - PIED_Y_DEPUIS_BAS;
        Ui.hairline(g, px + 8, px + pw - 8, fy - 8, 0xFFFFFF, 26, true, true);
        // bandeaux d'abandon et d'erreur : tenus a gauche des boutons du pied, texte coupe a la largeur du bandeau
        int bandeauMax = pw - BOUTON_W - ANNULER_W - 64;
        if (abandonDepuis >= 0) {
            Component t = bandeauTexte(abandon, bandeauMax - 16);
            int w = Math.min(font.width(t) + 16, bandeauMax);
            Ui.soft(g, px + Ui.MARGE, fy, w, BOUTON_H, Ui.argb(Ui.DANGER, 36));
            g.fill(px + Ui.MARGE, fy, px + Ui.MARGE + 2, fy + BOUTON_H, Ui.argb(Ui.DANGER, 255));
            g.drawString(font, t, px + Ui.MARGE + 8, fy + 6, Ui.argb(Ui.DANGER, 255), false);
        } else if (erreurDepuis >= 0) {
            // refus du serveur apres ENREGISTRER : meme bandeau discret que l'abandon
            Component t = bandeauTexte(erreur, bandeauMax - 16);
            int w = Math.min(font.width(t) + 16, bandeauMax);
            Ui.soft(g, px + Ui.MARGE, fy, w, BOUTON_H, Ui.argb(Ui.DANGER, 36));
            g.fill(px + Ui.MARGE, fy, px + Ui.MARGE + 2, fy + BOUTON_H, Ui.argb(Ui.DANGER, 255));
            g.drawString(font, t, px + Ui.MARGE + 8, fy + 6, Ui.argb(Ui.DANGER, 255), false);
        } else {
            g.drawString(font, pied, px + Ui.MARGE, fy + 6, Ui.argb(piedCouleur, 255), false);
        }
        super.render(g, mouseX, mouseY, partialTick);
        if (infoTitre != null) {
            infos.clear();
            infos.add(infoTitre);
            if (infoSous != null) infos.add(infoSous);
            Ui.tooltip(g, font, infos, mouseX, mouseY, acc);
        }
    }

    // ------------------------------------------------------------------ souris
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) {
            // seul l'hexa garde le focus : un bouton focalise (ANNULER, croix, AJOUTER) reagirait sinon a Entree / Espace
            if (getFocused() != hexa) setFocused(null);
            return true;
        }
        if (getFocused() != null) setFocused(null);
        if (button != 0) return false;
        int k = outilSous(mx, my);
        if (k >= 0) {
            if (outilActif(k)) outil(k);
            return true;
        }
        if (toile.clic(mx, my, button)) return true;
        if (liste.clic(mx, my, button)) return true;
        if (selecteurOuvert) return selecteur.clic(mx, my, button);
        if (sel >= 0) return panneauCalque.clic(mx, my, button);
        return panneauFond.clic(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button == 0) {
            if (toile.glisser(mx, my)) return true;
            if (liste.glisser(mx, my)) return true;
            if (!selecteurOuvert && sel >= 0 && panneauCalque.glisser(mx)) return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        toile.relacher();
        liste.relacher();
        panneauCalque.relacher();
        setDragging(false);
        boolean pris = false;
        for (GuiEventListener c : children()) pris |= c.mouseReleased(mx, my, button);
        return pris;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (selecteurOuvert && selecteur.molette(mx, my, delta)) return true;
        return super.mouseScrolled(mx, my, delta);
    }

    // ------------------------------------------------------------------ clavier
    /** Lettre de la touche selon la disposition du clavier (Ctrl+Z reste Ctrl+Z en AZERTY), 0 si ce n'en est pas une. */
    private static char lettre(int key, int scan) {
        String n = GLFW.glfwGetKeyName(key, scan);
        if (n != null && n.length() == 1) return Character.toLowerCase(n.charAt(0));
        if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) return (char) ('a' + key - GLFW.GLFW_KEY_A);
        return 0;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        boolean ctrl = (mods & GLFW.GLFW_MOD_CONTROL) != 0, shift = (mods & GLFW.GLFW_MOD_SHIFT) != 0;
        if (hexa.isFocused()) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                setFocused(null);
                return true;
            }
            return super.keyPressed(key, scan, mods);
        }
        boolean fait = ctrl ? raccourciCtrl(lettre(key, scan), shift) : raccourciTouche(key, shift);
        if (fait) {
            interrompreGestes();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    /**
     * Un raccourci vient de changer l'etat pendant un geste souris (bouton encore enfonce) : le geste s'arrete (la
     * toile et les glissieres le verifient aussi), le glisser de la liste est abandonne sans deplacer.
     */
    private void interrompreGestes() {
        toile.relacher();
        liste.abandonner();
        panneauCalque.relacher();
    }

    /** Touches sans lettre (Echap, Suppr, fleches, Tab, Page haut / bas). */
    private boolean raccourciTouche(int key, boolean shift) {
        switch (key) {
            case GLFW.GLFW_KEY_ESCAPE -> echap();
            case GLFW.GLFW_KEY_DELETE, GLFW.GLFW_KEY_BACKSPACE -> supprimer();
            case GLFW.GLFW_KEY_LEFT -> decaler(-1, 0, shift);
            case GLFW.GLFW_KEY_RIGHT -> decaler(1, 0, shift);
            case GLFW.GLFW_KEY_UP -> decaler(0, -1, shift);
            case GLFW.GLFW_KEY_DOWN -> decaler(0, 1, shift);
            case GLFW.GLFW_KEY_TAB -> suivant(shift);
            case GLFW.GLFW_KEY_PAGE_UP -> {
                if (sel >= 0) deplacer(sel, sel + 1);
            }
            case GLFW.GLFW_KEY_PAGE_DOWN -> {
                if (sel >= 0) deplacer(sel, sel - 1);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    /** Ctrl+Z / Ctrl+Maj+Z / Ctrl+Y / Ctrl+D. */
    private boolean raccourciCtrl(char l, boolean shift) {
        switch (l) {
            case 'z' -> {
                if (shift) retablirEtat();
                else annulerEtat();
            }
            case 'y' -> retablirEtat();
            case 'd' -> dupliquer();
            // taille au grand pas : Ctrl + « + » (touche « = » ou pave numerique) / Ctrl + « − »
            case '+', '=' -> redimensionner(1, true);
            case '-' -> redimensionner(-1, true);
            default -> {
                return false;
            }
        }
        return true;
    }

    /**
     * Lettres et signes (selon la disposition, via charTyped) : Q / E rotation, G grille, + / − taille au petit pas.
     * Maj ne compte pas pour la taille : « + » s'obtient avec Maj sur le clavier principal (QWERTY comme AZERTY), et
     * Maj + « − » ne donne pas « − » ; « = » vaut « + » (meme touche). Le grand pas est sur Ctrl (raccourciCtrl).
     */
    private boolean raccourciCaractere(char c, boolean shift) {
        switch (c) {
            case 'q' -> tourner(shift ? -1 : -15);
            case 'Q' -> tourner(-1);
            case 'e' -> tourner(shift ? 1 : 15);
            case 'E' -> tourner(1);
            case 'g', 'G' -> basculerGrille();
            case '+', '=' -> redimensionner(1, false);
            case '-' -> redimensionner(-1, false);
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (hexa.isFocused()) return super.charTyped(c, mods);
        if (raccourciCaractere(c, hasShiftDown())) {
            interrompreGestes();
            return true;
        }
        return super.charTyped(c, mods);
    }

    @Override
    public void onClose() {
        abandonner();
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

    // ------------------------------------------------------------------ banc d'essai (dev/ClansHarness)
    /** Applique un modele par identifiant (etape "atelierModele"). */
    public boolean appliquerModele(String id) {
        Modeles.Modele m = Modeles.parId(id);
        if (m == null) return false;
        appliquerModele(m);
        return true;
    }

    /** Remplace le calque selectionne (etape "atelierCalque"). */
    public boolean remplacerSelection(Calque c) {
        if (sel < 0 || c == null) return false;
        changer(b.avecCalque(sel, c), Historique.AUCUN);
        return true;
    }

    /**
     * Raccourci clavier par nom (etape "atelierTouche") : "ctrl+z", "ctrl+shift+z", "ctrl+y", "ctrl+d", "delete",
     * "escape", "tab", "shift+tab", "left|right|up|down" (avec "shift+"), "pageup", "pagedown", "q", "e", "g", "+", "-",
     * "ctrl++", "ctrl+-" (grand pas).
     * Les lettres sont prises telles quelles (independamment de la disposition du clavier de la machine).
     */
    public boolean touche(String nom) {
        String s = nom.trim().toLowerCase(Locale.ROOT);
        if (s.equals("+") || s.equals("-")) return raccourciCaractere(s.charAt(0), false);
        boolean ctrl = false, shift = false;
        String t = s;
        while (true) {
            if (t.startsWith("ctrl+")) {
                ctrl = true;
                t = t.substring(5);
            } else if (t.startsWith("shift+")) {
                shift = true;
                t = t.substring(6);
            } else break;
        }
        if (hexa.isFocused() && !t.equals("escape")) setFocused(null);
        if (t.length() == 1) return ctrl ? raccourciCtrl(t.charAt(0), shift) : raccourciCaractere(t.charAt(0), shift);
        int key = switch (t) {
            case "escape" -> GLFW.GLFW_KEY_ESCAPE;
            case "delete", "suppr" -> GLFW.GLFW_KEY_DELETE;
            case "backspace" -> GLFW.GLFW_KEY_BACKSPACE;
            case "left" -> GLFW.GLFW_KEY_LEFT;
            case "right" -> GLFW.GLFW_KEY_RIGHT;
            case "up" -> GLFW.GLFW_KEY_UP;
            case "down" -> GLFW.GLFW_KEY_DOWN;
            case "tab" -> GLFW.GLFW_KEY_TAB;
            case "pageup" -> GLFW.GLFW_KEY_PAGE_UP;
            case "pagedown" -> GLFW.GLFW_KEY_PAGE_DOWN;
            default -> GLFW.GLFW_KEY_UNKNOWN;
        };
        if (key == GLFW.GLFW_KEY_UNKNOWN) return false;
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            echap();
            return true;
        }
        return raccourciTouche(key, shift);
    }

    /** Coordonnees d'interface d'un point du canevas 64 x 96 (repere "toile" du banc). */
    public double toileX(double u) {
        return toile.ecranX(u);
    }

    public double toileY(double v) {
        return toile.ecranY(v);
    }

    /** Rectangles de la mise en page (coordonnees d'interface), pour corriger vite un scenario du banc. */
    public String miseEnPage() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "atelier %dx%d panneau (%d,%d %dx%d)", width, height, px, py, pw, ph));
        sb.append(String.format(Locale.ROOT, " | banniere (%d,%d %dx%d, %.2f px/unite) zone toile (%d,%d %dx%d)", toile.x(), toile.y(), Toile.L, Toile.H,
                Toile.E, px + TOILE_ZX, py + TOILE_ZY, TOILE_ZW, TOILE_ZH));
        sb.append(String.format(Locale.ROOT, " | outils x=%d+%d*k y=%d (%dx%d) %s", px + OUTILS_X, OUTIL_PAS, py + OUTILS_Y, OUTIL_W, OUTIL_H,
                String.join(",", OUTILS_CLES)));
        sb.append(String.format(Locale.ROOT, " | calques (%d,%d %dx%d, ligne %d, le dessus en haut) ajouter (%d,%d %dx%d)", px + LISTE_X, py + LISTE_Y,
                LISTE_W, ListeCalques.HAUTEUR, ListeCalques.LIGNE, px + LISTE_X, py + AJOUTER_Y, LISTE_W, AJOUTER_H));
        int dx = px + DROITE_X, dw = pw - DROITE_X - 16;
        sb.append(String.format(Locale.ROOT, " | droite (%d,%d %dx%d) onglets x=%d/%d/%d y=%d grille (%d,%d) case %d (modeles %dx%d)", dx, py + DROITE_Y, dw,
                SELECTEUR_H, selecteur.ongletX(Selecteur.Onglet.FORMES), selecteur.ongletX(Selecteur.Onglet.EMBLEMES),
                selecteur.ongletX(Selecteur.Onglet.MODELES), py + DROITE_Y, selecteur.caseX(0), selecteur.caseY(0), Selecteur.CASE, Selecteur.CASE,
                Selecteur.MODELE_H));
        sb.append(String.format(Locale.ROOT, " | hexa (%d,%d %dx%d) palette y=%d pas %d contour y=%d pas %d styles y=%d pas %d", panneauFond.hexaX(),
                panneauFond.hexaY(), HEXA_W, HEXA_H, py + DROITE_Y + PanneauFond.Y_PALETTE, 14, py + DROITE_Y + PanneauCalque.Y_EPAISSEURS,
                PanneauCalque.EPAISSEUR_PAS, py + DROITE_Y + PanneauFond.Y_PASTILLES, PanneauFond.STYLE_PAS));
        int fy = py + ph - PIED_Y_DEPUIS_BAS;
        sb.append(String.format(Locale.ROOT, " | principal (%d,%d %dx%d) annuler (%d,%d %dx%d)", px + pw - 16 - BOUTON_W, fy, BOUTON_W, BOUTON_H,
                px + pw - 16 - BOUTON_W - 16 - ANNULER_W, fy, ANNULER_W, BOUTON_H));
        sb.append(" | etat : ").append(b.nombreCalques()).append(" calques, selection ").append(sel).append(selecteurOuvert ? ", selecteur ouvert" : "")
                .append(grilleMemo ? ", grille" : "").append(", historique ").append(hist.taille());
        return sb.toString();
    }

    /** Etat lisible pour le journal du banc (banniere en JSON compact). */
    public String etat() {
        return b.toJson().toString();
    }
}
