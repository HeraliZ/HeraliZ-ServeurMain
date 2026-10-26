package fr.heralisz.clans.client.gui.perms;

import fr.heralisz.clans.client.PermsLocales;
import fr.heralisz.clans.client.gui.kit.Hote;
import fr.heralisz.clans.client.gui.kit.HzButton;
import fr.heralisz.clans.client.gui.kit.HzScroll;
import fr.heralisz.clans.client.gui.kit.Icons;
import fr.heralisz.clans.client.gui.kit.Ui;
import fr.heralisz.clans.model.ClanSnapshot;
import fr.heralisz.clans.model.Grade;
import fr.heralisz.clans.model.Perm;
import fr.heralisz.clans.model.ReglagesPerms;
import fr.heralisz.clans.registry.Sons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Editeur des permissions PAR JOUEUR, dans le rectangle (x, y, w, h) d'un {@link Hote} (onglet Permissions du clan ou
 * ecran autonome {@code PermissionsScreen}) :
 * <ul>
 * <li>a gauche, « MEMBRES » : liste defilante (lignes de 24 : tete, nom, grade colore ; a droite couronne du chef,
 * cadenas si le membre n'est pas reglable, ou nombre de reglages personnels) ; clic = membre choisi (garde d'une
 * reconstruction a l'autre) ;</li>
 * <li>a droite, le membre choisi (nom, grade) et ses permissions par groupes (CLAN, BASE, CLASHWAR, ou BASE seule),
 * liste defilante ; trois colonnes HERITE (pastille grise = valeur du grade), AUTORISE (verte), REFUSE (rouge) ; fond
 * de ligne teinte quand un reglage personnel s'applique, libelle clair si le membre a la permission ; au pied, le
 * nombre de reglages et le lien TOUT REMETTRE AU GRADE.</li>
 * </ul>
 * Les regles du serveur sont reproduites pour griser ce qui sera refuse (GERER_PERMISSIONS, grade strictement
 * superieur, pas de permission accordee qu'on n'a pas, chef non reglable) ; le serveur revérifie tout. Un joueur qui
 * ne gere pas les permissions ne voit que lui-meme, en lecture seule (il ne recoit que ses propres reglages).
 */
public final class EditeurJoueurs {
    // colonnes de 64 : les titres russes (« НАСЛЕДОВАТЬ », « РАЗРЕШЕНО ») ne se touchent plus
    public static final int LARGEUR_MEMBRES = 168, LIGNE_MEMBRE = 24, COL_W = 64, ECART = 12;
    /** Membre choisi : garde entre les reconstructions et entre l'onglet et l'ecran autonome. */
    @Nullable
    private static UUID selection;

    private final Hote hote;
    private final ClanSnapshot s;
    private final boolean seulementBase, gere;
    private final int x, y, w, h, accent, rx, rw;
    private final Font font = Minecraft.getInstance().font;
    private final List<ClanSnapshot.MembreVue> membres = new ArrayList<>();
    private final List<String> noms = new ArrayList<>();
    private final List<PermsUi.Ligne> lignes;
    private final Component[] libelles = new Component[Perm.values().length];
    private final String[] colonnes = new String[3];
    private final ListeMembres listeMembres;
    private final ListePerms listePerms;
    private final HzButton remettre;

    public EditeurJoueurs(Hote hote, ClanSnapshot s, int x, int y, int w, int h, boolean seulementBase) {
        this.hote = hote;
        this.s = s;
        this.seulementBase = seulementBase;
        this.gere = s.jePeux(Perm.GERER_PERMISSIONS);
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.accent = s.accent();
        this.rx = x + LARGEUR_MEMBRES + ECART;
        this.rw = w - LARGEUR_MEMBRES - ECART;
        this.lignes = PermsUi.lignes(seulementBase);
        for (Perm p : Perm.values()) libelles[p.ordinal()] = Ui.tr(Ui.FONT, p.libelle());
        for (Perm.Etat e : Perm.Etat.values()) colonnes[e.ordinal()] = Component.translatable(e.libelle()).getString();
        // membres : chef, puis par grade, en ligne, nom ; un joueur qui ne gere pas ne voit que lui
        for (ClanSnapshot.MembreVue m : s.membres()) if (gere || m.uuid().equals(s.moi())) membres.add(m);
        membres.sort((a, b) -> {
            int c = Integer.compare(a.grade().ordinal(), b.grade().ordinal());
            if (c != 0) return c;
            c = Boolean.compare(b.enLigne(), a.enLigne());
            return c != 0 ? c : a.nom().compareToIgnoreCase(b.nom());
        });
        for (ClanSnapshot.MembreVue m : membres) noms.add(Ui.ellipsis(font, m.nom(), Ui.FONT, LARGEUR_MEMBRES - 30 - 30));
        if (cible() == null) selection = parDefaut();
        listeMembres = hote.ajouter(new ListeMembres(x, y + 13, LARGEUR_MEMBRES, h - 13));
        listeMembres.accent(accent);
        listeMembres.setContentHeight(membres.size() * LIGNE_MEMBRE);
        listePerms = hote.ajouter(new ListePerms(rx, y + 15, rw, h - 15 - 16));
        listePerms.accent(accent);
        majHauteur();
        Component lib = Component.translatable("gui.permissions.tout_remettre");
        int lw = (int) Ui.spacedWidth(font, lib.getString().toUpperCase(Locale.ROOT), Ui.SMALL, 0.8f) + 2;
        remettre = hote.ajouter(new HzButton(rx + rw - 4 - lw, y + h - 12, lw, 12, lib, accent, this::toutRemettre).look(HzButton.Look.LINK));
        majRemettre();
    }

    // ------------------------------------------------------------------ etat
    @Nullable
    public ClanSnapshot.MembreVue cible() {
        if (selection == null) return null;
        for (ClanSnapshot.MembreVue m : membres) if (m.uuid().equals(selection)) return m;
        return null;
    }

    /** Premier membre reglable, sinon le premier qui n'est pas le chef, sinon le premier. */
    @Nullable
    private UUID parDefaut() {
        for (ClanSnapshot.MembreVue m : membres) if (reglable(m)) return m.uuid();
        for (ClanSnapshot.MembreVue m : membres) if (m.grade() != Grade.CHEF) return m.uuid();
        return membres.isEmpty() ? null : membres.get(0).uuid();
    }

    /** Vrai si je peux regler ce membre (comme le serveur : GERER_PERMISSIONS et grade strictement superieur). */
    public boolean reglable(ClanSnapshot.MembreVue m) {
        return gere && m.grade() != Grade.CHEF && s.monGrade().domine(m.grade());
    }

    /**
     * Cle de la raison pour laquelle cet etat sera refuse (null = permis), d'apres les reglages affiches r du membre :
     * chef, pas le droit de gerer, hierarchie, ou permission accordee qu'on n'a pas.
     */
    @Nullable
    public String refus(ClanSnapshot.MembreVue m, ReglagesPerms r, Perm p, Perm.Etat e) {
        if (m.grade() == Grade.CHEF) return "gui.permissions.refus.chef";
        if (!gere) return "gui.permissions.refus.gerer";
        if (!s.monGrade().domine(m.grade())) return "gui.permissions.refus.hierarchie";
        boolean accorde = e == Perm.Etat.AUTORISE
                || (e == Perm.Etat.HERITE && r.etat(p) == Perm.Etat.REFUSE && s.permsDe(m.grade()).contains(p));
        if (accorde && !s.jePeux(p)) return "gui.permissions.refus.accorder";
        return null;
    }

    private void choisir(ClanSnapshot.MembreVue m) {
        if (m.uuid().equals(selection)) return;
        selection = m.uuid();
        Ui.son(Sons.UI_CLIC, 0.45f);
        majHauteur();
        majRemettre();
    }

    private void majHauteur() {
        ClanSnapshot.MembreVue m = cible();
        listePerms.setContentHeight(m == null || m.grade() == Grade.CHEF ? 0 : PermsUi.hauteur(lignes));
    }

    /** Reglages personnels du membre dans le perimetre affiche (toutes les permissions, ou celles de la base). */
    private int nombreReglages(ReglagesPerms r) {
        int n = 0;
        for (PermsUi.Ligne l : lignes) if (l.perm() != null && r.etat(l.perm()) != Perm.Etat.HERITE) n++;
        return n;
    }

    /** Remet au grade toutes les permissions affichees qui peuvent l'etre. */
    private void toutRemettre() {
        ClanSnapshot.MembreVue m = cible();
        if (m == null || !reglable(m)) return;
        ReglagesPerms r = PermsLocales.reglages(s, m.uuid());
        for (PermsUi.Ligne l : lignes) {
            Perm p = l.perm();
            if (p == null || r.etat(p) == Perm.Etat.HERITE || refus(m, r, p, Perm.Etat.HERITE) != null) continue;
            PermsLocales.definir(s, m.uuid(), p, Perm.Etat.HERITE);
        }
        majRemettre();
    }

    /** Le lien n'est visible que s'il y a quelque chose a remettre (appele a chaque image : changements optimistes). */
    private void majRemettre() {
        ClanSnapshot.MembreVue m = cible();
        boolean visible = false;
        if (m != null && reglable(m)) {
            ReglagesPerms r = PermsLocales.reglages(s, m.uuid());
            for (PermsUi.Ligne l : lignes) {
                Perm p = l.perm();
                if (p != null && r.etat(p) != Perm.Etat.HERITE && refus(m, r, p, Perm.Etat.HERITE) == null) {
                    visible = true;
                    break;
                }
            }
        }
        remettre.visible = visible;
        remettre.active = visible;
    }

    // ------------------------------------------------------------------ rendu des parties fixes
    /** En-tetes, pied et indications (avant les widgets). */
    public void render(GuiGraphics g, int mouseX, int mouseY) {
        majRemettre();
        Ui.section(g, font, Component.translatable("gui.permissions.membres").getString(), x, y + 2, LARGEUR_MEMBRES - 6);
        ClanSnapshot.MembreVue m = cible();
        // en-tete de droite : nom + grade du membre choisi, titres des trois colonnes
        if (m != null) {
            Component nom = Ui.lit(Ui.ellipsis(font, m.nom(), Ui.BOLD, 120), Ui.BOLD);
            g.drawString(font, nom, rx, y, Ui.argb(Ui.TEXT, 255), false);
            g.drawString(font, Ui.tr(Ui.SMALL, m.grade().libelle()), rx + font.width(nom) + 6, y + 2,
                    Ui.argb(PermsUi.couleurGrade(m.grade(), accent), 255), false);
        } else {
            g.drawString(font, Ui.tr(Ui.FONT, "gui.permissions.choisir"), rx, y, Ui.argb(Ui.MUTED, 255), false);
        }
        if (m != null && m.grade() != Grade.CHEF) {
            for (Perm.Etat e : Perm.Etat.values()) {
                int c = e == Perm.Etat.AUTORISE ? Ui.mix(Ui.OK, Ui.MUTED, 0.35f) : e == Perm.Etat.REFUSE ? Ui.mix(Ui.DANGER, Ui.MUTED, 0.35f) : Ui.MUTED;
                Ui.spacedCentered(g, font, colonnes[e.ordinal()], Ui.SMALL, listePerms.colX(e.ordinal()), y + 2, Ui.argb(c, 255), 0.6f);
            }
        }
        Ui.hairline(g, rx, rx + rw, y + 12, 0xFFFFFF, 40, false, true);
        // pied de droite
        int py = y + h - 10;
        if (m == null || m.grade() == Grade.CHEF) return;
        if (!gere) {
            PermsUi.cadenas(g, rx, py - 1, Ui.FAINT, 220);
            g.drawString(font, Ui.tr(Ui.SMALL, "gui.permissions.vos_permissions"), rx + 11, py, Ui.argb(Ui.FAINT, 255), false);
        } else if (!reglable(m)) {
            PermsUi.cadenas(g, rx, py - 1, Ui.FAINT, 220);
            g.drawString(font, Ui.tr(Ui.SMALL, "gui.permissions.refus.hierarchie"), rx + 11, py, Ui.argb(Ui.FAINT, 255), false);
        } else {
            int n = nombreReglages(PermsLocales.reglages(s, m.uuid()));
            Component t = n == 0 ? Ui.tr(Ui.SMALL, "gui.permissions.reglages.aucun", Component.translatable(m.grade().libelle()))
                    : n == 1 ? Ui.tr(Ui.SMALL, "gui.permissions.reglages.un")
                    : Ui.tr(Ui.SMALL, "gui.permissions.reglages.plusieurs", n);
            g.drawString(font, t, rx, py, Ui.argb(n == 0 ? Ui.FAINT : Ui.MUTED, 255), false);
        }
    }

    // ------------------------------------------------------------------ liste des membres
    private final class ListeMembres extends HzScroll {
        ListeMembres(int x, int y, int w, int h) {
            super(x, y, w, h);
        }

        @Override
        protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int premiere = Math.max(0, getScroll() / LIGNE_MEMBRE);
            int derniere = Math.min(membres.size(), (getScroll() + getHeight()) / LIGNE_MEMBRE + 1);
            int lw = getWidth() - 4;
            if (membres.isEmpty()) {
                g.drawString(font, Ui.tr(Ui.SMALL, "gui.permissions.aucun_membre"), getX() + 4, getY() + 6, Ui.argb(Ui.FAINT, 255), false);
                return;
            }
            for (int i = premiere; i < derniere; i++) {
                ClanSnapshot.MembreVue m = membres.get(i);
                int ly = getY() + i * LIGNE_MEMBRE;
                boolean choisi = m.uuid().equals(selection), hover = PermsUi.dans(mouseX, mouseY, getX(), ly, lw, LIGNE_MEMBRE);
                boolean reglable = reglable(m);
                if (choisi) {
                    Ui.soft(g, getX(), ly + 1, lw, LIGNE_MEMBRE - 2, Ui.argb(accent, 46));
                    g.fill(getX(), ly + 5, getX() + 2, ly + LIGNE_MEMBRE - 5, Ui.argb(accent, 235));
                } else if (hover) {
                    Ui.soft(g, getX(), ly + 1, lw, LIGNE_MEMBRE - 2, Ui.argb(0xFFFFFF, 12));
                }
                PermsUi.tete(g, m.uuid(), getX() + 8, ly + 4, 16);
                int cNom = choisi || reglable || m.uuid().equals(s.moi()) ? Ui.TEXT : Ui.MUTED;
                g.drawString(font, Ui.lit(noms.get(i), Ui.FONT), getX() + 30, ly + 3, Ui.argb(cNom, 255), false);
                g.drawString(font, Ui.tr(Ui.SMALL, m.grade().libelle()), getX() + 30, ly + 14, Ui.argb(PermsUi.couleurGrade(m.grade(), accent), 220), false);
                int droite = getX() + lw - 6;
                if (m.grade() == Grade.CHEF) {
                    Icons.draw(g, Icons.CHEF, droite - 10, ly + 7, 10, Ui.OR, 230);
                } else if (gere && !reglable) {
                    PermsUi.cadenas(g, droite - 8, ly + 8, Ui.FAINT, 200);
                } else {
                    int n = nombreReglages(PermsLocales.reglages(s, m.uuid()));
                    if (n > 0) {
                        String t = String.valueOf(n);
                        int tw = font.width(Ui.lit(t, Ui.SMALL)) + 8;
                        Ui.soft(g, droite - tw, ly + 7, tw, 10, Ui.argb(accent, 46));
                        Ui.softOutline(g, droite - tw + 1, ly + 8, tw - 2, 8, Ui.argb(accent, 110));
                        Ui.centered(g, font, Ui.lit(t, Ui.SMALL), droite - tw / 2f, ly + 8.5f, Ui.argb(Ui.mix(accent, 0xFFFFFF, 0.4f), 255));
                    }
                }
                if (i < membres.size() - 1 && !choisi) Ui.hairline(g, getX() + 6, getX() + lw, ly + LIGNE_MEMBRE - 1, 0xFFFFFF, 12, false, true);
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button != 0 || !dansZone(mx, my)) return false;
            int i = (int) ((my + getScroll() - getY()) / LIGNE_MEMBRE);
            if (i >= 0 && i < membres.size()) choisir(membres.get(i));
            return true;
        }
    }

    // ------------------------------------------------------------------ permissions du membre choisi
    private final class ListePerms extends HzScroll {
        ListePerms(int x, int y, int w, int h) {
            super(x, y, w, h);
        }

        /** Centre (x absolu) de la colonne d'etat i (0 herite, 1 autorise, 2 refuse). */
        int colX(int i) {
            return getX() + getWidth() - 8 - (2 - i) * COL_W - COL_W / 2;
        }

        @Override
        protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            ClanSnapshot.MembreVue m = cible();
            if (m == null) return;
            if (m.grade() == Grade.CHEF) {
                int cx = getX() + getWidth() / 2, cy = getY() + getScroll() + getHeight() / 2 - 18;
                Icons.draw(g, Icons.CHEF, cx - 8, cy, 16, Ui.OR, 235);
                Ui.centered(g, font, Ui.tr(Ui.BOLD, "gui.permissions.chef_titre"), cx, cy + 22, Ui.argb(Ui.TEXT, 255));
                Ui.centered(g, font, Ui.tr(Ui.SMALL, "gui.permissions.chef_sous"), cx, cy + 35, Ui.argb(Ui.FAINT, 255));
                return;
            }
            ReglagesPerms r = PermsLocales.reglages(s, m.uuid());
            EnumSet<Perm> duGrade = s.permsDe(m.grade());
            int haut = getY() + getScroll(), bas = haut + getHeight(), lw = getWidth() - 4;
            List<Component> info = null;
            for (PermsUi.Ligne l : lignes) {
                int ly = getY() + l.y();
                if (ly + l.h() < haut || ly > bas) continue;
                if (l.perm() == null) {
                    PermsUi.titre(g, font, l.groupe(), getX() + 2, ly, lw - 4);
                    continue;
                }
                Perm p = l.perm();
                Perm.Etat e = r.etat(p);
                boolean parLeGrade = duGrade.contains(p), effectif = e == Perm.Etat.HERITE ? parLeGrade : e == Perm.Etat.AUTORISE;
                if (e == Perm.Etat.AUTORISE) Ui.soft(g, getX(), ly, lw, l.h(), Ui.argb(Ui.OK, 18));
                else if (e == Perm.Etat.REFUSE) Ui.soft(g, getX(), ly, lw, l.h(), Ui.argb(Ui.DANGER, 18));
                if (PermsUi.dans(mouseX, mouseY, getX(), ly, lw, l.h())) Ui.soft(g, getX(), ly, lw, l.h(), Ui.argb(0xFFFFFF, 8));
                g.drawString(font, libelles[p.ordinal()], getX() + 6, ly + 3, effectif ? Ui.argb(Ui.TEXT, 235) : Ui.argb(Ui.MUTED, 210), false);
                for (Perm.Etat col : Perm.Etat.values()) {
                    int cx = colX(col.ordinal());
                    String refus = refus(m, r, p, col);
                    // l'etat actif reste net meme en lecture seule (il faut pouvoir le lire) ; les autres s'estompent
                    boolean permis = col == e || refus == null;
                    boolean survol = PermsUi.dans(mouseX, mouseY, cx - COL_W / 2 + 8, ly, COL_W - 16, l.h());
                    PermsUi.etat(g, cx, ly + 2, col, col == e, parLeGrade, survol, permis);
                    if (survol) info = infobulle(m, col, parLeGrade, col == e ? (reglable(m) ? null : refus) : refus);
                }
            }
            if (info != null) hote.infobulle(info);
        }

        /** Infobulle d'une pastille : l'etat, son effet, et la raison d'un refus. */
        private List<Component> infobulle(ClanSnapshot.MembreVue m, Perm.Etat col, boolean parLeGrade, @Nullable String refus) {
            List<Component> l = new ArrayList<>(3);
            switch (col) {
                case HERITE -> {
                    l.add(Ui.tr(Ui.FONT, "gui.permissions.info.herite"));
                    l.add(Ui.tr(Ui.SMALL, "gui.permissions.info.herite_valeur", Component.translatable(m.grade().libelle()),
                            Component.translatable(parLeGrade ? "gui.permissions.valeur.oui" : "gui.permissions.valeur.non")).withStyle(st -> st.withColor(Ui.MUTED)));
                }
                case AUTORISE -> {
                    l.add(Ui.tr(Ui.FONT, "gui.permissions.info.autorise", m.nom()));
                    l.add(Ui.tr(Ui.SMALL, "gui.permissions.info.quel_que_soit").withStyle(st -> st.withColor(Ui.MUTED)));
                }
                case REFUSE -> {
                    l.add(Ui.tr(Ui.FONT, "gui.permissions.info.refuse", m.nom()));
                    l.add(Ui.tr(Ui.SMALL, "gui.permissions.info.quel_que_soit").withStyle(st -> st.withColor(Ui.MUTED)));
                }
            }
            if (refus != null) l.add(Ui.tr(Ui.SMALL, refus).withStyle(st -> st.withColor(Ui.AMBRE)));
            return l;
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button != 0 || !dansZone(mx, my)) return false;
            ClanSnapshot.MembreVue m = cible();
            if (m == null || m.grade() == Grade.CHEF) return true;
            double cy = my + getScroll();
            for (PermsUi.Ligne l : lignes) {
                if (l.perm() == null) continue;
                int ly = getY() + l.y();
                if (cy < ly || cy >= ly + l.h()) continue;
                for (Perm.Etat col : Perm.Etat.values()) {
                    int cx = colX(col.ordinal());
                    if (mx < cx - COL_W / 2 + 8 || mx >= cx + COL_W / 2 - 8) continue;
                    ReglagesPerms r = PermsLocales.reglages(s, m.uuid());
                    if (r.etat(l.perm()) == col || refus(m, r, l.perm(), col) != null) return true;
                    PermsLocales.definir(s, m.uuid(), l.perm(), col);
                    Ui.son(Sons.UI_CLIC, 0.5f);
                    return true;
                }
                return true;
            }
            return true;
        }
    }

    // ------------------------------------------------------------------ banc d'essai (coordonnees d'interface)
    /** Centre de la ligne du membre (par nom), apres defilement pour la rendre visible ; null si absent. */
    @Nullable
    public double[] centreMembre(String nom) {
        for (int i = 0; i < membres.size(); i++) {
            if (!membres.get(i).nom().equalsIgnoreCase(nom)) continue;
            montrer(listeMembres, i * LIGNE_MEMBRE, LIGNE_MEMBRE);
            return new double[]{listeMembres.getX() + LARGEUR_MEMBRES / 2.0, listeMembres.getY() + i * LIGNE_MEMBRE - listeMembres.getScroll() + LIGNE_MEMBRE / 2.0};
        }
        return null;
    }

    /** Centre de la pastille (permission, etat) du membre choisi, apres defilement ; null si la permission n'est pas affichee. */
    @Nullable
    public double[] centreEtat(Perm p, Perm.Etat e) {
        for (PermsUi.Ligne l : lignes) {
            if (l.perm() != p) continue;
            montrer(listePerms, l.y(), l.h());
            return new double[]{listePerms.colX(e.ordinal()), listePerms.getY() + l.y() - listePerms.getScroll() + l.h() / 2.0};
        }
        return null;
    }

    /** Centre de la liste des membres (gauche) ou des permissions (droite), pour la molette. */
    public double[] centreListe(boolean droite) {
        HzScroll l = droite ? listePerms : listeMembres;
        return new double[]{l.getX() + l.getWidth() / 2.0, l.getY() + l.getHeight() / 2.0};
    }

    private static void montrer(HzScroll l, int y, int h) {
        if (y < l.getScroll()) l.setScroll(y);
        else if (y + h > l.getScroll() + l.getHeight()) l.setScroll(y + h - l.getHeight());
    }
}
