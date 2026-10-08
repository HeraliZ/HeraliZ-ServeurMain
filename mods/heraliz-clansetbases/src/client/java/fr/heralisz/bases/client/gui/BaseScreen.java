package fr.heralisz.bases.client.gui;

import fr.heralisz.bases.HeraliszBases;
import fr.heralisz.bases.client.BasesClient;
import fr.heralisz.bases.client.BasesKeys;
import fr.heralisz.bases.client.ClientBases;
import fr.heralisz.bases.client.gui.hud.BasesHud;
import fr.heralisz.bases.client.gui.kit.Echelle;
import fr.heralisz.bases.client.gui.kit.Hold;
import fr.heralisz.bases.client.gui.kit.HzButton;
import fr.heralisz.bases.client.gui.kit.HzIcon;
import fr.heralisz.bases.client.gui.kit.Icons;
import fr.heralisz.bases.client.gui.kit.Sons;
import fr.heralisz.bases.client.gui.kit.Ui;
import fr.heralisz.bases.model.Base;
import fr.heralisz.bases.model.BaseVue;
import fr.heralisz.bases.model.Boite;
import fr.heralisz.bases.model.FenetreVue;
import fr.heralisz.bases.model.Pillage;
import fr.heralisz.bases.model.Plan;
import fr.heralisz.bases.network.Packets;
import fr.heralisz.clans.client.gui.kit.BannerArt;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Ecran BASE (BASES_SPEC §6), echelle forcee (canevas 640 x 360) :
 * <ul>
 * <li>sans clan (panneau 380 x 180) : « les bases appartiennent aux clans » + OUVRIR LE CLAN ;</li>
 * <li>sans base (520 x 262) : apercu du plan, presentation en trois arguments, cout, CREER LA BASE (maintenir 1,2 s,
 * permission BASE_GERER et banque suffisante, sinon grise avec la raison) ;</li>
 * <li>avec base (580 x 324) : plan vu de dessus a gauche ({@link PlanBunker}, clic = choisir une salle) ; a droite
 * ACCES (ENTRER NORD / SUD, ou SORTIR si l'on est dedans), SALLE choisie (etat, dimensions, prix, DEBLOQUER en
 * maintenant 1 s), PILLAGE (fenetre, attaque en cours, 3 derniers pillages) et le lien PILLER UNE BASE ; pendant la
 * pose ou l'effacement : plan grise + barre de progression (l'etat est redemande chaque seconde).</li>
 * </ul>
 * Onglets dans l'en-tete (seulement pour qui a BASE_GERER) : BUNKER (cet ecran) et PERMISSIONS, qui ouvre l'editeur des
 * permissions par joueur du mod clans filtre sur les permissions BASE_* ({@code ClansUi.ouvrirPermissionsJoueurs(true)}).
 * Ne se reconstruit que si l'etat ou le plan change ({@link ClientBases#versionEtat()}, {@link ClientBases#versionPlan()}) ;
 * les textes qui dependent de l'heure sont recalcules une fois par seconde. Tous les composants affiches sont prepares a
 * la construction : le rendu n'en cree aucun.
 */
public class BaseScreen extends Screen {
    private static final int ENTETE = 44;
    @Nullable
    private final Screen retour;
    private int px, py, pw, ph;
    private int vEtat = -1, vPlan = -1;
    private long ouvertLe;
    private long derniereSeconde;
    private int ticks;
    @Nullable
    private PlanBunker plan;
    @Nullable
    private String selection;
    private int rx, rw, ySalle, yPillage;
    @Nullable
    private Hold maintien;
    private final List<Component> infobulle = new ArrayList<>();
    private boolean infobulleVisible;
    @Nullable
    private String survolInfobulle;

    // ---- textes prepares (reconstruction / chaque seconde)
    private String titre = "", sousTag = "", sousNom = "", banqueLibelle = "", accesTitre = "", salleTitre = "", salleEtat = "",
            pillageTitre = "", fenetreChip = "", attaque = "", poseTitre = "", posePct = "", presentation = "", coutLibelle = "";
    private int salleEtatCouleur, fenetreCouleur;
    private Component banque = Component.empty(), cout = Component.empty(), dedans = Component.empty(), accesAide = Component.empty(),
            salleNom = Component.empty(), salleDims = Component.empty(), salleAide = Component.empty(), plage = Component.empty(),
            aucunPillage = Component.empty(), aideCreer = Component.empty(), sansClan1 = Component.empty(), sansClan2 = Component.empty(),
            planAbsent = Component.empty();
    @Nullable
    private Component sallePrix;
    private boolean aideCreerAlerte, salleAideAlerte;
    private final List<Component[]> lignesPillage = new ArrayList<>();
    private final List<String> issuesPillage = new ArrayList<>();
    private final List<Integer> couleursPillage = new ArrayList<>();
    private final List<Component> arguments = new ArrayList<>();
    private final List<Integer> iconesArguments = new ArrayList<>();
    // ---- onglets de l'en-tete (BASE_GERER) : BUNKER (actif) · PERMISSIONS
    private boolean onglets;
    private String ongletBunker = "", ongletPermissions = "";
    private int ongletY, ongletBunkerX, ongletBunkerW, ongletPermX, ongletPermW;

    public BaseScreen(@Nullable Screen retour) {
        super(Component.translatable("gui.heralisz_bases.base.titre"));
        this.retour = retour;
    }

    // ------------------------------------------------------------------ construction
    @Override
    protected void init() {
        Echelle.forcer(this);
        if (ouvertLe == 0) {
            ouvertLe = System.currentTimeMillis();
            Sons.jouer(Sons.OUVRIR, 0.7f);
        }
        vEtat = ClientBases.versionEtat();
        vPlan = ClientBases.versionPlan();
        maintien = null;
        survolInfobulle = null;
        BaseVue v = ClientBases.etat();
        int accent = Ui.accent();
        titre = Ui.t("gui.heralisz_bases.base.titre");
        if (v == null || v.clan() == null) {
            pw = Math.min(380, width - 32);
            ph = Math.min(180, height - 24);
            px = (width - pw) / 2;
            py = (height - ph) / 2;
            croix();
            sansClan1 = Ui.tr(Ui.BOLD, "gui.heralisz_bases.sans_clan");
            sansClan2 = Ui.tr(Ui.FONT, "gui.heralisz_bases.sans_clan_sous");
            addRenderableWidget(new HzButton(px + pw / 2 - 70, py + ph - 42, 140, 20, Component.translatable("gui.heralisz_bases.ouvrir_clan"),
                    accent, BaseScreen::ouvrirClan));
            return;
        }
        sousTag = "[" + v.tag() + "]";
        sousNom = v.nom();
        banqueLibelle = Ui.t("gui.heralisz_bases.banque");
        banque = Ui.jetons(v.banque(), Ui.BOLD);
        Plan p = ClientBases.plan();
        if (!v.existe()) {
            construireSansBase(v, p, accent);
            preparerOnglets(v);
            return;
        }
        pw = Math.min(580, width - 24);
        ph = Math.min(324, height - 16);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        croix();
        preparerOnglets(v);
        // plan a gauche
        int zoneH = ph - ENTETE - 10 - 12;
        int planL = 220;
        if (p != null) {
            if (plan == null || plan.plan() != p) plan = new PlanBunker(p);
            planL = plan.placer(font, px + Ui.MARGE, py + ENTETE + 8, 236, zoneH, v);
        } else {
            plan = null;
        }
        rx = px + Ui.MARGE + Math.max(planL, 200) + 22;
        rw = px + pw - Ui.MARGE - rx;
        if (selection == null || (p != null && p.salle(selection) == null)) selection = selectionParDefaut(v, p);
        // ---- ACCES
        int y = py + ENTETE + 10;
        accesTitre = Ui.t("gui.heralisz_bases.section.acces");
        boolean prete = v.etat() == Base.Etat.PRETE;
        boolean peutEntrer = v.peut(BaseVue.Droit.ENTRER);
        if (v.dansLaBase()) {
            addRenderableWidget(new HzButton(rx, y + 14, 150, 20, Component.translatable("gui.heralisz_bases.sortir"), accent,
                    () -> BasesClient.demander(Packets.ActionC2S.Genre.SORTIR, "")).look(HzButton.Look.PRIMARY).icone(Icons.SORTIR));
            String zone = ClientBases.hud().mode() != Packets.HudS2C.Mode.AUCUN ? PlanBunker.nomZone(p, ClientBases.hud().zone()) : "";
            dedans = zone.isEmpty() ? Ui.tr(Ui.FONT, "gui.heralisz_bases.dedans") : Ui.tr(Ui.FONT, "gui.heralisz_bases.dedans_zone", zone);
            accesAide = Ui.tr(Ui.SMALL, "gui.heralisz_bases.aide_sortir");
        } else {
            dedans = Component.empty();
            int bw = (rw - 8) / 2;
            addRenderableWidget(new HzButton(rx, y + 14, bw, 20, Component.translatable("gui.heralisz_bases.entrer_nord"), accent,
                    () -> BasesClient.demander(Packets.ActionC2S.Genre.ENTRER, "nord")).look(HzButton.Look.PRIMARY).icone(Icons.ENTRER)
                    .actif(prete && peutEntrer));
            addRenderableWidget(new HzButton(rx + bw + 8, y + 14, bw, 20, Component.translatable("gui.heralisz_bases.entrer_sud"), accent,
                    () -> BasesClient.demander(Packets.ActionC2S.Genre.ENTRER, "sud")).icone(Icons.ENTRER).actif(prete && peutEntrer));
            accesAide = Ui.tr(Ui.SMALL, !prete ? "gui.heralisz_bases.aide_travaux" : peutEntrer ? "gui.heralisz_bases.aide_entrer"
                    : "gui.heralisz_bases.aide_sans_droit_entrer");
        }
        // ---- SALLE choisie
        ySalle = y + 58;
        construireSalle(v, p, accent);
        // ---- PILLAGE
        yPillage = ySalle + 84;
        pillageTitre = Ui.t("gui.heralisz_bases.section.pillage");
        aucunPillage = Ui.tr(Ui.SMALL, "gui.heralisz_bases.aucun_pillage");
        planAbsent = Ui.tr(Ui.SMALL, "gui.heralisz_bases.plan_absent");
        addRenderableWidget(new HzButton(rx, py + ph - 26, 130, 14, Component.translatable("gui.heralisz_bases.lien_piller"), accent,
                () -> BasesClient.ouvrirPiller(retour)).look(HzButton.Look.LINK).icone(Icons.CIBLE));
        poseTitre = Ui.t(v.etat() == Base.Etat.EFFACEMENT ? "gui.heralisz_bases.effacement" : "gui.heralisz_bases.pose");
        derniereSeconde = 0;
        chaqueSeconde();
    }

    private void croix() {
        addRenderableWidget(new HzIcon(px + pw - 24, py + 10, 12, Icons.CROIX, Component.translatable("gui.heralisz_bases.fermer"), this::onClose));
    }

    /** Onglets BUNKER · PERMISSIONS au milieu de l'en-tete, seulement pour qui gere la base (BASE_GERER). */
    private void preparerOnglets(BaseVue v) {
        onglets = v.peut(BaseVue.Droit.GERER);
        if (!onglets) return;
        ongletBunker = Ui.t("gui.heralisz_bases.onglet.bunker");
        ongletPermissions = Ui.t("gui.heralisz_bases.onglet.permissions");
        ongletBunkerW = (int) Ui.spacedWidth(font, ongletBunker, Ui.SMALL, 1.0f);
        ongletPermW = (int) Ui.spacedWidth(font, ongletPermissions, Ui.SMALL, 1.0f) + 12;
        int total = ongletBunkerW + 18 + ongletPermW;
        ongletBunkerX = px + pw / 2 - total / 2 + 20;
        ongletPermX = ongletBunkerX + ongletBunkerW + 18;
        ongletY = py + 18;
    }

    /** Banc d'essai : clic sur l'onglet PERMISSIONS (faux s'il n'est pas affiche : pas de BASE_GERER). */
    public boolean cliquerOngletPermissions() {
        if (!onglets) return false;
        return mouseClicked(ongletPermX + 4, ongletY + 3, 0);
    }

    /** Onglets visibles (BASE_GERER). */
    public boolean ongletsVisibles() {
        return onglets;
    }

    private boolean surOngletPermissions(double mx, double my) {
        return onglets && mx >= ongletPermX - 6 && mx < ongletPermX + ongletPermW + 6 && my >= ongletY - 6 && my < ongletY + 14;
    }

    /**
     * Onglet PERMISSIONS : editeur des permissions par joueur du mod clans, filtre sur BASE_* (le serveur des clans
     * reverifie tout). Jar des clans trop ancien ou clan pas encore recu : message propre, rien ne plante.
     */
    private void ouvrirPermissions() {
        boolean ok;
        try {
            ok = fr.heralisz.clans.client.ClansUi.ouvrirPermissionsJoueurs(true);
        } catch (LinkageError ex) {
            HeraliszBases.LOG.warn("[bases] editeur des permissions du mod clans indisponible : {}", ex.toString());
            ok = false;
        }
        if (!ok) {
            BasesHud.notice(new Packets.NoticeS2C(Packets.NoticeS2C.Genre.ERREUR, "gui.heralisz_bases.permissions_indispo", List.of(), ""));
        }
    }

    private void construireSansBase(BaseVue v, @Nullable Plan p, int accent) {
        pw = Math.min(520, width - 24);
        ph = Math.min(262, height - 16);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        croix();
        int zoneH = ph - ENTETE - 10 - 12;
        int planL = 150;
        if (p != null) {
            if (plan == null || plan.plan() != p) plan = new PlanBunker(p);
            planL = plan.placer(font, px + Ui.MARGE, py + ENTETE + 8, 170, zoneH, null);
        } else {
            plan = null;
        }
        rx = px + Ui.MARGE + Math.max(planL, 150) + 24;
        rw = px + pw - Ui.MARGE - rx;
        arguments.clear();
        iconesArguments.clear();
        int salles = p == null ? 6 : p.salles.size();
        int aVendre = p == null ? 5 : p.sallesAVendre().size();
        FenetreVue f = v.fenetre();
        String[] args = {
                Ui.t("gui.heralisz_bases.argument.bunker", salles, aVendre),
                Ui.t("gui.heralisz_bases.argument.construire"),
                Ui.t("gui.heralisz_bases.argument.pillage", f.debutH(), f.finH())};
        int[] icones = {Icons.BUNKER, Icons.BOUCLIER, Icons.CIBLE};
        for (int i = 0; i < args.length; i++) {
            List<String> l = Ui.lignes(font, args[i], Ui.FONT, rw - 16);
            for (int k = 0; k < l.size(); k++) {
                arguments.add(Ui.lit(l.get(k), Ui.FONT));
                iconesArguments.add(k == 0 ? icones[i] : -1);
            }
        }
        presentation = Ui.t("gui.heralisz_bases.presentation");
        coutLibelle = Ui.t("gui.heralisz_bases.cout");
        cout = Ui.jetons(v.coutCreation(), Ui.BOLD);
        boolean gerer = v.peut(BaseVue.Droit.GERER);
        boolean fonds = v.banque() >= v.coutCreation();
        aideCreerAlerte = !gerer || !fonds;
        aideCreer = !gerer ? Ui.tr(Ui.SMALL, "gui.heralisz_bases.aide_sans_droit_gerer")
                : !fonds ? Ui.tr(Ui.SMALL, "gui.heralisz_bases.aide_fonds", Ui.jetonsTexte(v.coutCreation() - v.banque()))
                : Ui.tr(Ui.SMALL, "gui.heralisz_bases.aide_maintenir");
        maintien = new Hold(rx, py + ph - 46, 170, 20, Component.translatable("gui.heralisz_bases.creer"), 1200,
                () -> BasesClient.demander(Packets.ActionC2S.Genre.CREER, "")).couleur(accent).actif(gerer && fonds);
        addRenderableWidget(maintien);
    }

    private void construireSalle(BaseVue v, @Nullable Plan p, int accent) {
        sallePrix = null;
        salleAide = Component.empty();
        salleAideAlerte = false;
        salleEtat = "";
        salleDims = Component.empty();
        salleTitre = Ui.t("gui.heralisz_bases.section.salle");
        Plan.Salle sa = p == null || selection == null ? null : p.salle(selection);
        BaseVue.SalleVue sv = null;
        for (BaseVue.SalleVue x : v.salles()) if (selection != null && x.id().equalsIgnoreCase(selection)) sv = x;
        if (sa == null && sv == null) {
            salleNom = Ui.tr(Ui.BOLD, "gui.heralisz_bases.choisir_salle");
            return;
        }
        salleNom = Ui.lit(sa != null ? sa.nom(Ui.french() ? "fr" : "en") : selection, Ui.BOLD);
        boolean depart = sv != null ? sv.depart() : sa.depart();
        boolean ouverte = depart || (sv != null && sv.ouverte());
        long prix = sv != null ? sv.cout() : sa.cout();
        salleEtat = Ui.t(depart ? "gui.heralisz_bases.etat.depart" : ouverte ? "gui.heralisz_bases.etat.ouverte" : "gui.heralisz_bases.etat.verrouillee");
        salleEtatCouleur = ouverte ? Ui.OK : Ui.MUTED;
        Boite grande = null;
        if (sa != null) for (Boite b : sa.interieur()) if (grande == null || b.dx() * b.dz() > grande.dx() * grande.dz()) grande = b;
        if (grande != null) salleDims = Ui.tr(Ui.SMALL, "gui.heralisz_bases.dimensions", grande.dx(), grande.dz(), grande.dy());
        if (ouverte) {
            salleAide = Ui.tr(Ui.SMALL, "gui.heralisz_bases.aide_salle_ouverte");
            return;
        }
        sallePrix = Ui.jetons(prix, Ui.BOLD);
        boolean gerer = v.peut(BaseVue.Droit.GERER);
        boolean fonds = v.banque() >= prix;
        boolean prete = v.etat() == Base.Etat.PRETE;
        salleAideAlerte = !gerer || !fonds || !prete;
        salleAide = !gerer ? Ui.tr(Ui.SMALL, "gui.heralisz_bases.aide_sans_droit_gerer")
                : !prete ? Ui.tr(Ui.SMALL, "gui.heralisz_bases.aide_travaux")
                : !fonds ? Ui.tr(Ui.SMALL, "gui.heralisz_bases.aide_fonds", Ui.jetonsTexte(prix - v.banque()))
                : Ui.tr(Ui.SMALL, "gui.heralisz_bases.aide_maintenir");
        String id = sa != null ? sa.id() : selection;
        maintien = new Hold(rx + rw - 132, ySalle + 40, 132, 20, Component.translatable("gui.heralisz_bases.debloquer"), 1000,
                () -> BasesClient.demander(Packets.ActionC2S.Genre.DEBLOQUER, id)).couleur(accent).actif(gerer && fonds && prete);
        addRenderableWidget(maintien);
    }

    @Nullable
    private static String selectionParDefaut(BaseVue v, @Nullable Plan p) {
        // la salle ou l'on se trouve, sinon la prochaine a debloquer (la moins chere), sinon la premiere
        Packets.HudS2C h = ClientBases.hud();
        if (v.dansLaBase() && p != null && p.salle(h.zone()) != null) return p.salle(h.zone()).id();
        BaseVue.SalleVue moinsChere = null;
        for (BaseVue.SalleVue s : v.salles()) {
            if (!s.ouverte() && !s.depart() && (moinsChere == null || s.cout() < moinsChere.cout())) moinsChere = s;
        }
        if (moinsChere != null) return moinsChere.id();
        if (!v.salles().isEmpty()) return v.salles().get(0).id();
        return p == null || p.salles.isEmpty() ? null : p.salles.get(0).id();
    }

    /** Textes qui dependent de l'heure (fenetre de pillage, « il y a … »), recalcules une fois par seconde. */
    private void chaqueSeconde() {
        BaseVue v = ClientBases.etat();
        if (v == null || v.clan() == null || !v.existe()) return;
        long now = System.currentTimeMillis();
        FenetreVue f = v.fenetre();
        plage = Ui.tr(Ui.FONT, "gui.heralisz_bases.plage", f.debutH(), f.finH());
        long reste = f.msAvantChangement() < 0 ? -1 : Math.max(0, f.msAvantChangement() - (now - ClientBases.etatRecuMs()));
        if (reste < 0) {
            fenetreChip = Ui.t(f.ouverte() ? "gui.heralisz_bases.fenetre.forcee_ouverte" : "gui.heralisz_bases.fenetre.forcee_fermee");
        } else {
            fenetreChip = Ui.t(f.ouverte() ? "gui.heralisz_bases.fenetre.ouverte" : "gui.heralisz_bases.fenetre.fermee", Ui.duree(reste));
        }
        fenetreCouleur = f.ouverte() ? Ui.DANGER : Ui.MUTED;
        attaque = v.attaquants().isEmpty() ? "" : Ui.t("gui.heralisz_bases.attaquee_par", "[" + String.join("] [", v.attaquants()) + "]", v.pillards());
        lignesPillage.clear();
        issuesPillage.clear();
        couleursPillage.clear();
        for (int i = 0; i < Math.min(3, v.pillages().size()); i++) {
            Pillage pi = v.pillages().get(i);
            lignesPillage.add(new Component[]{Ui.lit(Ui.ilYA(Math.max(0, now - pi.debut)), Ui.SMALL),
                    Ui.tr(Ui.SMALL, "gui.heralisz_bases.pillage_ligne", pi.tagAttaquant, pi.joueurs)});
            issuesPillage.add(Ui.t("gui.heralisz_bases.issue." + pi.issue.name().toLowerCase(Locale.ROOT)));
            couleursPillage.add(switch (pi.issue) {
                case EN_COURS -> Ui.AMBRE;
                case PILLEE -> Ui.DANGER;
                case DEFENDUE -> Ui.OK;
            });
        }
        posePct = Math.round(v.progression() * 100) + " %";
    }

    // ------------------------------------------------------------------ actions
    private static void ouvrirClan() {
        try {
            fr.heralisz.clans.client.ClansClient.ouvrirDepuisTouche();
        } catch (LinkageError ex) {
            HeraliszBases.LOG.warn("[bases] ecran du clan indisponible", ex);
        }
    }

    /** Choisit une salle du plan (clic, banc d'essai). */
    public void choisir(@Nullable String id) {
        if (id == null || id.equalsIgnoreCase(selection == null ? "" : selection)) return;
        selection = id;
        rebuildWidgets();
    }

    /** Banc d'essai : fige la barre de DEBLOQUER / CREER a {@code fraction} (l'action ne part pas). */
    public void figerMaintien(float fraction) {
        if (maintien != null) maintien.figer(fraction);
    }

    @Nullable
    public PlanBunker planBunker() {
        return plan;
    }

    @Override
    public void tick() {
        if (ClientBases.versionEtat() != vEtat || ClientBases.versionPlan() != vPlan) {
            rebuildWidgets();
            return;
        }
        ticks++;
        BaseVue v = ClientBases.etat();
        // pose / effacement en cours : l'etat est redemande chaque seconde (lecture, hors cadence du serveur)
        if (v != null && v.existe() && v.etat() != Base.Etat.PRETE && ticks % 20 == 0 && !ClientBases.estFaux()) BasesClient.demander(Packets.ActionC2S.Genre.ETAT, "");
        long s = System.currentTimeMillis() / 1000;
        if (s != derniereSeconde) {
            derniereSeconde = s;
            chaqueSeconde();
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && surOngletPermissions(mx, my)) {
            Sons.jouer(Sons.CLIC, 0.5f);
            ouvrirPermissions();
            return true;
        }
        if (super.mouseClicked(mx, my, button)) return true;
        BaseVue v = ClientBases.etat();
        if (button == 0 && plan != null && v != null && v.existe()) {
            String id = plan.salleSous(mx, my);
            if (id != null) {
                Sons.jouer(Sons.CLIC, 0.5f);
                choisir(id);
                return true;
            }
        }
        return false;
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

    // ------------------------------------------------------------------ rendu
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        infobulleVisible = false;
        Ui.fond(g, width, height);
        Ui.glass(g, px, py, pw, ph);
        BaseVue v = ClientBases.etat();
        int accent = Ui.accent();
        if (v == null || v.clan() == null) {
            sansClan(g);
        } else {
            entete(g, v, accent);
            dessinerOnglets(g, accent, mouseX, mouseY);
            if (!v.existe()) sansBase(g, accent, mouseX, mouseY);
            else avecBase(g, v, accent, mouseX, mouseY);
        }
        super.render(g, mouseX, mouseY, partialTick);
        if (infobulleVisible) Ui.tooltip(g, font, infobulle, mouseX, mouseY, accent);
        BasesHud.toastsEcran(g, font, width);
    }

    private void entete(GuiGraphics g, BaseVue v, int accent) {
        BannerArt.draw(g, v.banniere(), px + Ui.MARGE, py + 6, 24, 32);
        int tx = px + Ui.MARGE + 34;
        Ui.spaced(g, font, titre, Ui.BOLD, tx, py + 10, Ui.argb(Ui.TEXT, 255), 1.6f);
        Ui.spaced(g, font, sousTag, Ui.SMALL, tx, py + 26, Ui.argb(accent, 255), 0.6f);
        float tw = Ui.spacedWidth(font, sousTag, Ui.SMALL, 0.6f);
        Ui.spaced(g, font, sousNom, Ui.SMALL, tx + tw + 6, py + 26, Ui.argb(Ui.MUTED, 255), 0.6f);
        Ui.spacedRight(g, font, banqueLibelle, Ui.SMALL, px + pw - 40, py + 11, Ui.argb(Ui.MUTED, 255), 1.0f);
        Ui.right(g, font, banque, px + pw - 40, py + 22, Ui.argb(Ui.TEXT, 255));
        Ui.hairline(g, px + 8, px + pw - 8, py + ENTETE, 0xFFFFFF, 40, true, true);
    }

    /** Onglets de l'en-tete (style de la barre du clan) : BUNKER souligne a l'accent, PERMISSIONS au survol. */
    private void dessinerOnglets(GuiGraphics g, int accent, int mouseX, int mouseY) {
        if (!onglets) return;
        Ui.spaced(g, font, ongletBunker, Ui.SMALL, ongletBunkerX, ongletY, Ui.argb(Ui.TEXT, 255), 1.0f);
        g.fill(ongletBunkerX, ongletY + 10, ongletBunkerX + ongletBunkerW, ongletY + 11, Ui.argb(accent, 230));
        boolean hover = surOngletPermissions(mouseX, mouseY);
        int c = Ui.argb(hover ? Ui.mix(Ui.MUTED, Ui.TEXT, 0.6f) : Ui.MUTED, 255);
        Icons.draw(g, Icons.MEMBRES, ongletPermX, ongletY - 1, 9, c & 0xFFFFFF, 255);
        Ui.spaced(g, font, ongletPermissions, Ui.SMALL, ongletPermX + 12, ongletY, c, 1.0f);
        if (hover) g.fill(ongletPermX, ongletY + 10, ongletPermX + ongletPermW, ongletY + 11, Ui.argb(0xFFFFFF, 50));
    }

    private void sansClan(GuiGraphics g) {
        Ui.spaced(g, font, titre, Ui.BOLD, px + Ui.MARGE, py + 11, Ui.argb(Ui.TEXT, 255), 1.6f);
        Ui.hairline(g, px + 8, px + pw - 8, py + 28, 0xFFFFFF, 40, true, true);
        Icons.draw(g, Icons.BUNKER, px + pw / 2 - 12, py + 40, 24, Ui.MUTED, 200);
        Ui.centered(g, font, sansClan1, px + pw / 2f, py + 74, Ui.argb(Ui.TEXT, 255));
        Ui.centered(g, font, sansClan2, px + pw / 2f, py + 90, Ui.argb(Ui.MUTED, 255));
    }

    private void sansBase(GuiGraphics g, int accent, int mouseX, int mouseY) {
        if (plan != null) plan.render(g, font, mouseX, mouseY, accent, 0.85f, null, null, true);
        int y = py + ENTETE + 14;
        Ui.spaced(g, font, presentation, Ui.BOLD, rx, y, Ui.argb(Ui.TEXT, 255), 1.4f);
        y += 20;
        for (int i = 0; i < arguments.size(); i++) {
            int ic = iconesArguments.get(i);
            if (ic >= 0) {
                if (i > 0) y += 6;
                Icons.draw(g, ic, rx, y, 10, accent, 230);
            }
            Ui.at(g, font, arguments.get(i), rx + 16, y + 1, Ui.argb(Ui.TEXT, 230));
            y += 11;
        }
        int yc = py + ph - 86;
        Ui.hairline(g, rx, px + pw - Ui.MARGE, yc - 8, 0xFFFFFF, 30, false, true);
        Ui.spaced(g, font, coutLibelle, Ui.SMALL, rx, yc, Ui.argb(Ui.MUTED, 255), 1.0f);
        Ui.gros(g, font, cout, rx, yc + 10, 1.5f, Ui.argb(Ui.TEXT, 255));
        Ui.at(g, font, aideCreer, rx, py + ph - 22, Ui.argb(aideCreerAlerte ? Ui.AMBRE : Ui.FAINT, 255));
    }

    private void avecBase(GuiGraphics g, BaseVue v, int accent, int mouseX, int mouseY) {
        boolean travaux = v.etat() != Base.Etat.PRETE;
        String ici = v.dansLaBase() && ClientBases.hud().mode() != Packets.HudS2C.Mode.AUCUN ? ClientBases.hud().zone() : null;
        if (plan != null) {
            plan.render(g, font, mouseX, mouseY, accent, travaux ? 0.35f : 1f, selection, ici, false);
            if (travaux) {
                int cx = plan.x() + plan.largeur() / 2, cy = plan.y() + plan.hauteur() / 2;
                Ui.glass(g, cx - 80, cy - 24, 160, 48, 230);
                Ui.spacedCentered(g, font, poseTitre, Ui.SMALL, cx, cy - 14, Ui.argb(Ui.TEXT, 255), 1.0f);
                Ui.bar(g, cx - 60, cy, 120, 3, v.progression(), accent);
                Ui.spacedCentered(g, font, posePct, Ui.SMALL, cx, cy + 9, Ui.argb(Ui.MUTED, 255), 0.6f);
            } else if (plan.dans(mouseX, mouseY)) {
                String id = plan.salleSous(mouseX, mouseY);
                if (id != null && !id.equalsIgnoreCase(selection)) {
                    // infobulle reconstruite seulement quand la salle survolee change
                    if (!id.equals(survolInfobulle)) {
                        survolInfobulle = id;
                        infobulle.clear();
                        infobulle.add(Ui.lit(PlanBunker.nomZone(ClientBases.plan(), id), Ui.BOLD));
                        infobulle.add(Ui.tr(Ui.SMALL, "gui.heralisz_bases.cliquer_salle"));
                    }
                    infobulleVisible = true;
                }
            }
        } else {
            Ui.centered(g, font, planAbsent, px + Ui.MARGE + 110, py + ph / 2f, Ui.argb(Ui.FAINT, 255));
        }
        // ---- ACCES
        int y = py + ENTETE + 10;
        Ui.section(g, font, accesTitre, rx, y, rw);
        if (v.dansLaBase()) {
            Ui.point(g, rx + 160, y + 22, Ui.OK, 255);
            Ui.at(g, font, dedans, rx + 168, y + 20, Ui.argb(Ui.TEXT, 255));
        }
        Ui.at(g, font, accesAide, rx, y + 39, Ui.argb(Ui.FAINT, 255));
        // ---- SALLE
        Ui.section(g, font, salleTitre, rx, ySalle, rw);
        Ui.at(g, font, salleNom, rx, ySalle + 15, Ui.argb(Ui.TEXT, 255));
        if (!salleEtat.isEmpty()) Ui.chip(g, font, salleEtat, rx + font.width(salleNom) + 8, ySalle + 14, salleEtatCouleur);
        Ui.at(g, font, salleDims, rx, ySalle + 28, Ui.argb(Ui.MUTED, 255));
        if (sallePrix != null) Ui.gros(g, font, sallePrix, rx, ySalle + 44, 1.4f, Ui.argb(Ui.AMBRE, 255));
        Ui.at(g, font, salleAide, rx, ySalle + 66, Ui.argb(salleAideAlerte ? Ui.AMBRE : Ui.FAINT, 255));
        // ---- PILLAGE
        int yp = yPillage;
        Ui.section(g, font, pillageTitre, rx, yp, rw);
        Icons.draw(g, Icons.HORLOGE, rx, yp + 14, 9, Ui.MUTED, 255);
        Ui.at(g, font, plage, rx + 13, yp + 14, Ui.argb(Ui.TEXT, 230));
        int cw = Ui.chipWidth(font, fenetreChip);
        Ui.chip(g, font, fenetreChip, rx + rw - cw, yp + 13, fenetreCouleur);
        int yl = yp + 28;
        if (!attaque.isEmpty()) {
            float p = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 200.0));
            Ui.soft(g, rx, yl, rw, 15, Ui.argb(Ui.DANGER, Math.round(28 + 26 * p)));
            g.fill(rx, yl + 1, rx + 2, yl + 14, Ui.argb(Ui.DANGER, 230));
            Icons.draw(g, Icons.ALERTE, rx + 6, yl + 3, 9, Ui.DANGER, 255);
            Ui.spaced(g, font, attaque, Ui.SMALL, rx + 19, yl + 5, Ui.argb(Ui.mix(Ui.DANGER, 0xFFFFFF, 0.35f), 255), 0.8f);
            yl += 20;
        }
        if (lignesPillage.isEmpty()) {
            Ui.at(g, font, aucunPillage, rx, yl + 1, Ui.argb(Ui.FAINT, 255));
        } else {
            for (int i = 0; i < lignesPillage.size(); i++) {
                Component[] c = lignesPillage.get(i);
                int ly = yl + i * 12;
                Ui.at(g, font, c[0], rx, ly, Ui.argb(Ui.FAINT, 255));
                Ui.at(g, font, c[1], rx + 74, ly, Ui.argb(Ui.TEXT, 220));
                Ui.spacedRight(g, font, issuesPillage.get(i), Ui.SMALL, rx + rw, ly + 1, Ui.argb(couleursPillage.get(i), 255), 0.8f);
            }
        }
        Ui.hairline(g, rx, px + pw - Ui.MARGE, py + ph - 34, 0xFFFFFF, 26, false, true);
    }
}
