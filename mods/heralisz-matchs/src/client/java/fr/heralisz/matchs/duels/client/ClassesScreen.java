package fr.heralisz.matchs.duels.client;

import fr.heralisz.matchs.client.kit.Echelle;
import fr.heralisz.matchs.client.kit.HzButton;
import fr.heralisz.matchs.client.kit.HzField;
import fr.heralisz.matchs.client.kit.HzIcon;
import fr.heralisz.matchs.client.kit.HzScroll;
import fr.heralisz.matchs.client.kit.Icons;
import fr.heralisz.matchs.client.kit.SonsUi;
import fr.heralisz.matchs.client.kit.Ui;
import fr.heralisz.matchs.duels.armes.Catalogue;
import fr.heralisz.matchs.duels.classes.ClassePerso;
import fr.heralisz.matchs.duels.net.PaquetsDuels;
import fr.heralisz.matchs.reseau.MatchsNet;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ecran CLASSES ({@code /duel classes}) : 5 emplacements de classes personnalisees (a gauche), le detail de la classe
 * choisie (au centre : nom, arme principale, ses accessoires par emplacement, secondaire, melee, 2 soins, letale +
 * tactique, tenue) et le selecteur (a droite : grille d'icones, filtres par type pour l'arme principale, jauges de
 * l'arme). Seuls les accessoires permis par TaCZ pour l'arme choisie sont proposes ; changer d'arme retire ceux qui ne
 * vont plus. Aucun camo, skin, sticker ni pendentif. ENREGISTRER envoie la classe ; le serveur l'assainit et renvoie la
 * liste. Brouillons gardes par emplacement tant que l'ecran est ouvert.
 * <p>Panneau min(600, l-16) x min(340, h-12) centre, echelle forcee (canevas 640 x 360).
 */
public class ClassesScreen extends Screen implements EcranDuels {
    private static final int W = 600, H = 340;
    private static final int LIGNE_Y = 64, PAS = 30;
    /** Detail : decalage de la colonne des valeurs (libelles a gauche), largeur des valeurs, demi-case, cases d'accessoires. */
    private static final int DX = 68, VW = 144, DEMI = 70, PAS_ACC = 24, ACC = 22;
    private static final String[] CHAMPS = {"principale", "accessoires", "secondaire", "melee", "soins", "grenades", "tenue"};
    /** Filtres de types de l'arme principale (id de filtre -> types TaCZ). */
    private static final String[][] FILTRES = {{"tout"}, {"rifle"}, {"smg"}, {"shotgun"}, {"sniper"}, {"mg"}, {"pistol"}};

    @Nullable
    private final Screen retour;
    private final ClassePerso[] serveur = new ClassePerso[ClassePerso.EMPLACEMENTS];
    private final ClassePerso[] brouillons = new ClassePerso[ClassePerso.EMPLACEMENTS];
    private int emplacement;
    private int versionVue = -1;
    private int enregistre = -1;
    /** Champ du selecteur ouvert : principale, acc:scope..., secondaire, melee, soin0, soin1, letale, tactique, tenue. */
    private String champ = "principale";
    private int filtre;
    private int px, py, pw, ph, mx0, rx0, rw;
    private HzField nom;
    private Grille grille;
    private final List<Cellule> cellules = new ArrayList<>();
    private int survol = -1;
    private float maxDegats = 1, maxRpm = 1, maxChargeur = 1;

    /** Case du selecteur. */
    private record Cellule(String id, String nom, String court, ItemStack icone) {
    }

    /** Textes de la classe courante, prepares a chaque modification. */
    private final Map<String, String> textes = new LinkedHashMap<>();
    private final Map<String, ItemStack> icones = new LinkedHashMap<>();
    private String[] nomsEmplacements = new String[ClassePerso.EMPLACEMENTS];
    private String[] armesEmplacements = new String[ClassePerso.EMPLACEMENTS];
    private String titre = "", sousTitre = "", note = "", titreSelecteur = "", modifie = "";
    private final String[] libelles = new String[CHAMPS.length];

    public ClassesScreen(@Nullable Screen retour) {
        super(Component.translatable("duel.classes.titre"));
        this.retour = retour;
        this.emplacement = Math.max(0, Math.min(ClassePerso.EMPLACEMENTS - 1, DuelsClient.derniere()));
        synchroniser();
    }

    private Catalogue cat() {
        Catalogue c = DuelsClient.catalogue();
        return c.vide() ? Catalogue.factice() : c;
    }

    /** Recale les brouillons sur les classes recues (garde les brouillons modifies, sauf l'emplacement enregistre). */
    private void synchroniser() {
        List<ClassePerso> l = DuelsClient.classes();
        for (int i = 0; i < ClassePerso.EMPLACEMENTS; i++) {
            ClassePerso s = i < l.size() ? l.get(i) : ClassePerso.vide(i + 1);
            boolean intact = brouillons[i] == null || brouillons[i].equals(serveur[i]) || i == enregistre;
            serveur[i] = s;
            if (intact) brouillons[i] = s;
        }
        enregistre = -1;
        versionVue = DuelsClient.versionClasses();
        Catalogue c = cat();
        maxDegats = 1;
        maxRpm = 1;
        maxChargeur = 1;
        for (Catalogue.Arme a : c.armes()) {
            maxDegats = Math.max(maxDegats, a.degats());
            maxRpm = Math.max(maxRpm, a.rpm());
            maxChargeur = Math.max(maxChargeur, a.chargeur());
        }
    }

    private ClassePerso classe() {
        return brouillons[emplacement];
    }

    // ------------------------------------------------------------------ construction
    @Override
    protected void init() {
        Echelle.forcer(this);
        initiales.clear();
        for (String emp : Catalogue.EMPLACEMENTS) initiales.put(emp, net.minecraft.client.resources.language.I18n.get("duel.emplacement.court." + emp));
        pw = Math.min(W, width - 16);
        ph = Math.min(H, height - 12);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        mx0 = px + 164;
        rx0 = px + 388;
        rw = pw - 404;
        int accent = Ui.accent();
        titre = Component.translatable("duel.classes.titre").getString();
        sousTitre = Component.translatable("duel.classes.sous_titre").getString();
        note = Component.translatable("duel.classes.note").getString();
        modifie = Component.translatable("duel.classes.modifie").getString();
        for (int i = 0; i < CHAMPS.length; i++) libelles[i] = Component.translatable("duel.classes.champ." + CHAMPS[i]).getString();
        addRenderableWidget(new HzIcon(px + pw - 24, py + 9, 12, Icons.CROIX, Component.translatable("gui.fermer"), this::onClose));
        nom = addRenderableWidget(new HzField(mx0 + DX, py + 40, VW, 15, ClassePerso.NOM_MAX, HzField.TOUT).accent(accent).remplacerAuFocus(false));
        nom.setValue(Rendu.nomClasse(classe().nom()));
        nom.setResponder(v -> {
            brouillons[emplacement] = classe().avecNom(v);
            majTextes();
        });
        // pied
        int fy = py + ph - 30;
        addRenderableWidget(new HzButton(px + pw - 16 - 120, fy, 120, 20, Component.translatable("duel.classes.enregistrer"), accent, this::enregistrer)
                .look(HzButton.Look.PRIMARY)).active = !classe().equals(serveur[emplacement]);
        addRenderableWidget(new HzButton(px + pw - 16 - 120 - 76, fy + 5, 64, 10, Component.translatable("duel.classes.retablir"), accent, () -> {
            brouillons[emplacement] = serveur[emplacement];
            rebuildWidgets();
        }).look(HzButton.Look.LINK)).active = !classe().equals(serveur[emplacement]);
        // filtres (arme principale)
        int gy = py + 54;
        if (champ.equals("principale")) {
            int cx = rx0, cy = py + 52;
            for (int i = 0; i < FILTRES.length; i++) {
                String lib = Component.translatable("duel.classes.filtre." + FILTRES[i][0]).getString();
                int w = (int) Ui.spacedWidth(font, lib.toUpperCase(Locale.ROOT), Ui.SMALL, 0.6f) + 10;
                if (cx + w > rx0 + rw) {
                    cx = rx0;
                    cy += 14;
                }
                int k = i;
                HzButton b = addRenderableWidget(new HzButton(cx, cy, w, 11, Component.literal(lib), filtre == i ? accent : 0x8F95A0, () -> {
                    filtre = k;
                    rebuildWidgets();
                }).look(HzButton.Look.PILL));
                b.active = true;
                cx += w + 4;
            }
            gy = cy + 16;
        }
        int gh = py + ph - 44 - gy - (estArme() ? 50 : 0);
        grille = addRenderableWidget(new Grille(rx0, gy, rw, Math.max(48, gh)));
        construireCellules();
        majTextes();
    }

    private boolean estArme() {
        return champ.equals("principale") || champ.equals("secondaire");
    }

    /** Cases du selecteur pour le champ ouvert. */
    private void construireCellules() {
        cellules.clear();
        Catalogue c = cat();
        boolean optionnel = !champ.equals("principale");
        if (optionnel) cellules.add(new Cellule("", Component.translatable("duel.classes.aucun").getString(), Component.translatable("duel.classes.aucun").getString(),
                ItemStack.EMPTY));
        switch (champ) {
            case "principale" -> {
                for (Catalogue.Arme a : c.armes()) if (filtreAccepte(a.type())) ajouterArme(a);
            }
            case "secondaire" -> {
                for (Catalogue.Arme a : c.armes()) if (a.pistolet()) ajouterArme(a);
            }
            case "melee" -> ajouterObjets(c.melees());
            case "soin0", "soin1" -> ajouterObjets(c.soins());
            case "letale" -> ajouterObjets(c.letales());
            case "tactique" -> ajouterObjets(c.tactiques());
            case "tenue" -> ajouterObjets(c.tenues());
            default -> {
                if (champ.startsWith("acc:")) {
                    for (Catalogue.Accessoire a : c.accessoiresDe(classe().principale(), champ.substring(4))) {
                        String n = Rendu.nom(a.nom(), a.id());
                        cellules.add(new Cellule(a.id(), n, Ui.ellipsis(font, n, Ui.SMALL, 44), a.icone()));
                    }
                }
            }
        }
        grille.setContentHeight(((cellules.size() + 3) / 4) * 46);
        titreSelecteur = champ.startsWith("acc:") ? Component.translatable("duel.classes.selecteur.accessoire", Rendu.emplacement(champ.substring(4))).getString()
                : Component.translatable("duel.classes.selecteur." + champ).getString();
    }

    private boolean filtreAccepte(String type) {
        String f = FILTRES[filtre][0];
        return f.equals("tout") || f.equals(type) || (f.equals("mg") && !List.of("rifle", "smg", "shotgun", "sniper", "pistol").contains(type));
    }

    private void ajouterArme(Catalogue.Arme a) {
        String n = Rendu.nom(a.nom(), a.id());
        cellules.add(new Cellule(a.id(), n, Ui.ellipsis(font, n, Ui.SMALL, 44), a.icone()));
    }

    private void ajouterObjets(List<Catalogue.Objet> l) {
        for (Catalogue.Objet o : l) {
            String n = Rendu.nom(o.nom(), o.id());
            cellules.add(new Cellule(o.id(), n, Ui.ellipsis(font, n, Ui.SMALL, 44), o.icone()));
        }
    }

    /** Valeur actuelle du champ ouvert (id), pour entourer la case choisie. */
    private String valeurChamp() {
        ClassePerso k = classe();
        return switch (champ) {
            case "principale" -> k.principale();
            case "secondaire" -> k.secondaire();
            case "melee" -> k.melee();
            case "soin0" -> k.soins().size() > 0 ? k.soins().get(0) : "";
            case "soin1" -> k.soins().size() > 1 ? k.soins().get(1) : "";
            case "letale" -> k.letale();
            case "tactique" -> k.tactique();
            case "tenue" -> k.tenue();
            default -> champ.startsWith("acc:") ? k.accessoires().getOrDefault(champ.substring(4), "") : "";
        };
    }

    /** Applique un choix du selecteur au brouillon. */
    private void appliquer(String id) {
        ClassePerso k = classe();
        Catalogue c = cat();
        String p = k.principale(), s = k.secondaire(), m = k.melee(), l = k.letale(), t = k.tactique(), te = k.tenue();
        Map<String, String> acc = new LinkedHashMap<>(k.accessoires());
        List<String> so = new ArrayList<>(k.soins());
        switch (champ) {
            case "principale" -> {
                p = id;
                // accessoires qui ne vont plus sur la nouvelle arme : retires
                acc.entrySet().removeIf(e -> !c.accessoirePermis(id, e.getKey(), e.getValue()));
            }
            case "secondaire" -> s = id;
            case "melee" -> m = id;
            case "soin0", "soin1" -> {
                int i = champ.equals("soin0") ? 0 : 1;
                while (so.size() <= i) so.add("");
                so.set(i, id);
                so.removeIf(String::isEmpty);
            }
            case "letale" -> l = id;
            case "tactique" -> t = id;
            case "tenue" -> te = id;
            default -> {
                if (champ.startsWith("acc:")) {
                    if (id.isEmpty()) acc.remove(champ.substring(4));
                    else acc.put(champ.substring(4), id);
                }
            }
        }
        brouillons[emplacement] = new ClassePerso(k.nom(), p, acc, s, m, so, l, t, te);
        SonsUi.clic(0.5f);
        rebuildWidgets();
    }

    private void enregistrer() {
        enregistre = emplacement;
        if (minecraft != null && minecraft.getConnection() != null) {
            MatchsNet.versServeur(new PaquetsDuels.ClasseC2S(emplacement, classe()));
        } else {
            serveur[emplacement] = classe();
            rebuildWidgets();
        }
    }

    /** Ouvre le selecteur d'un champ (clic sur une ligne, ou etape du banc). */
    void ouvrirSelecteur(String c) {
        champ = c;
        filtre = 0;
        rebuildWidgets();
    }

    /** Change d'emplacement (clic sur une carte de gauche, ou etape du banc). */
    void emplacement(int n) {
        emplacement = Math.max(0, Math.min(ClassePerso.EMPLACEMENTS - 1, n));
        champ = "principale";
        filtre = 0;
        rebuildWidgets();
    }

    // ------------------------------------------------------------------ textes
    private void majTextes() {
        Catalogue c = cat();
        textes.clear();
        icones.clear();
        ClassePerso k = classe();
        Catalogue.Arme ap = c.arme(k.principale()), as = c.arme(k.secondaire());
        mettre("principale", ap == null ? "" : Rendu.nom(ap.nom(), ap.id()), ap == null ? ItemStack.EMPTY : ap.icone());
        textes.put("principale_type", ap == null ? "" : Rendu.type(ap.type()));
        mettre("secondaire", as == null ? "" : Rendu.nom(as.nom(), as.id()), as == null ? ItemStack.EMPTY : as.icone());
        objet("melee", c.melees(), k.melee());
        objet("soin0", c.soins(), k.soins().size() > 0 ? k.soins().get(0) : "");
        objet("soin1", c.soins(), k.soins().size() > 1 ? k.soins().get(1) : "");
        objet("letale", c.letales(), k.letale());
        objet("tactique", c.tactiques(), k.tactique());
        objet("tenue", c.tenues(), k.tenue());
        for (String emp : Catalogue.EMPLACEMENTS) {
            String id = k.accessoires().getOrDefault(emp, "");
            Catalogue.Accessoire a = id.isEmpty() ? null : c.accessoire(id);
            mettre("acc:" + emp, a == null ? "" : Rendu.nom(a.nom(), a.id()), a == null ? ItemStack.EMPTY : a.icone());
            textes.put("acc_permis:" + emp, c.accessoiresDe(k.principale(), emp).isEmpty() ? "" : "1");
            textes.put("acc_lib:" + emp, Rendu.emplacement(emp));
        }
        for (int i = 0; i < ClassePerso.EMPLACEMENTS; i++) {
            ClassePerso b = brouillons[i];
            String n = b.nom().isEmpty() ? Component.translatable("duel.classes.sans_nom").getString() : Rendu.nomClasse(b.nom());
            nomsEmplacements[i] = Ui.ellipsis(font, n, Ui.BOLD, 72);
            Catalogue.Arme a = c.arme(b.principale());
            String an = a == null ? Component.translatable("duel.classes.aucune_arme").getString() : Rendu.nom(a.nom(), a.id());
            armesEmplacements[i] = Ui.ellipsis(font, an, Ui.SMALL, 72);
            iconesEmplacements[i] = a == null ? null : a.icone();
        }
        // versions abregees affichees dans les cases (calculees ici, pas a chaque image)
        for (String cle : new String[]{"principale", "secondaire", "melee", "tenue"}) courts.put(cle, Ui.ellipsis(font, textes.getOrDefault(cle, ""), Ui.FONT, VW - 64));
        for (String cle : new String[]{"soin0", "soin1", "letale", "tactique"}) courts.put(cle, Ui.ellipsis(font, textes.getOrDefault(cle, ""), Ui.SMALL, DEMI - 28));
        libNom = Component.translatable("duel.classes.champ.nom").getString();
    }

    private final Map<String, String> courts = new LinkedHashMap<>();
    private final ItemStack[] iconesEmplacements = new ItemStack[ClassePerso.EMPLACEMENTS];
    private String libNom = "";
    private static final String[] CLE_ACC = new String[6], CLE_PERMIS = new String[6];

    static {
        for (int i = 0; i < 6; i++) {
            CLE_ACC[i] = "acc:" + Catalogue.EMPLACEMENTS.get(i);
            CLE_PERMIS[i] = "acc_permis:" + Catalogue.EMPLACEMENTS.get(i);
        }
    }

    private void mettre(String cle, String nom, ItemStack icone) {
        textes.put(cle, nom);
        icones.put(cle, icone);
    }

    private void objet(String cle, List<Catalogue.Objet> l, String id) {
        Catalogue.Objet o = Catalogue.chercher(l, id);
        mettre(cle, o == null ? "" : Rendu.nom(o.nom(), o.id()), o == null ? ItemStack.EMPTY : o.icone());
    }

    // ------------------------------------------------------------------ rendu
    @Override
    public void tick() {
        if (nom != null) nom.tick();
        if (DuelsClient.versionClasses() != versionVue) {
            synchroniser();
            rebuildWidgets();
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        Ui.fond(g, width, height);
        Ui.glass(g, px, py, pw, ph);
        Font f = font;
        int accent = Ui.accent();
        Ui.spaced(g, f, titre, Ui.BOLD, px + 16, py + 11, Ui.argb(Ui.TEXT, 255), 1.6f);
        Ui.spaced(g, f, sousTitre, Ui.SMALL, px + 100, py + 13, Ui.argb(Ui.MUTED, 255), 0.4f);
        Ui.hairline(g, px + 8, px + pw - 8, py + 28, 0xFFFFFF, 40, true, true);
        // emplacements
        for (int i = 0; i < ClassePerso.EMPLACEMENTS; i++) {
            int x = px + 16, y = py + 40 + i * 52, w = 136, h = 46;
            boolean sel = i == emplacement, hover = mx >= x && mx < x + w && my >= y && my < y + h;
            Ui.soft(g, x, y, w, h, sel ? Ui.argb(accent, 40) : Ui.argb(0xFFFFFF, hover ? 14 : 6));
            Ui.softOutline(g, x + 1, y + 1, w - 2, h - 2, sel ? Ui.argb(accent, 200) : Ui.argb(0xFFFFFF, 22));
            Ui.gros(g, f, Ui.lit(String.valueOf(i + 1), Ui.BOLD), x + 8, y + 8, 1.6f, Ui.argb(sel ? accent : Ui.FAINT, 255));
            g.drawString(f, Ui.lit(nomsEmplacements[i], Ui.BOLD), x + 24, y + 10, Ui.argb(Ui.TEXT, 255), false);
            g.drawString(f, Ui.lit(armesEmplacements[i], Ui.SMALL), x + 24, y + 24, Ui.argb(Ui.MUTED, 255), false);
            if (iconesEmplacements[i] != null) Rendu.icone(g, iconesEmplacements[i], x + w - 34, y + 9, 28, armesEmplacements[i], 255);
            if (brouillons[i] != serveur[i] && !brouillons[i].equals(serveur[i])) Ui.point(g, x + w - 9, y + 4, Ui.AMBRE, 255);
        }
        // detail
        Ui.section(g, f, libNom, mx0, py + 44, 52);
        for (int r = 0; r < CHAMPS.length; r++) ligne(g, f, r, mx, my, accent);
        // selecteur
        Ui.section(g, f, titreSelecteur, rx0, py + 40, rw);
        if (cellules.isEmpty() || (cellules.size() == 1 && cellules.get(0).id().isEmpty() && champ.startsWith("acc:"))) {
            g.drawString(f, Ui.tr(Ui.SMALL, cat().vide() ? "duel.classes.catalogue_vide" : "duel.classes.rien"), rx0 + 2, grille.getY() + 4,
                    Ui.argb(Ui.FAINT, 255), false);
        }
        if (estArme()) jauges(g, f);
        // pied
        int fy = py + ph - 30;
        Ui.hairline(g, px + 16, px + pw - 16, fy - 8, 0xFFFFFF, 30, false, true);
        Ui.spaced(g, f, note, Ui.SMALL, px + 16, fy + 7, Ui.argb(Ui.FAINT, 255), 0.4f);
        if (!classe().equals(serveur[emplacement])) Ui.spacedRight(g, f, modifie, Ui.SMALL, px + pw - 16 - 120 - 84, fy + 7, Ui.argb(Ui.AMBRE, 255), 0.5f);
        super.render(g, mx, my, pt);
        // infobulle d'une case
        if (survol >= 0 && survol < cellules.size()) {
            Cellule c = cellules.get(survol);
            Ui.tooltip(g, f, List.of(Ui.lit(c.nom(), Ui.BOLD)), mx, my, accent);
        }
    }

    /** Ligne r du detail : libelle a gauche, valeur(s) cliquable(s) a droite. */
    private void ligne(GuiGraphics g, Font f, int r, int mx, int my, int accent) {
        int y = py + LIGNE_Y + r * PAS;
        int vx = mx0 + DX, vw = VW;
        Ui.spaced(g, f, libelles[r], Ui.SMALL, mx0, y + 9, Ui.argb(Ui.MUTED, 255), 0.6f);
        switch (CHAMPS[r]) {
            case "accessoires" -> {
                for (int i = 0; i < Catalogue.EMPLACEMENTS.size(); i++) {
                    String emp = Catalogue.EMPLACEMENTS.get(i);
                    int bx = vx + i * PAS_ACC;
                    boolean permis = !textes.getOrDefault(CLE_PERMIS[i], "").isEmpty();
                    boolean actif = champ.equals(CLE_ACC[i]);
                    boolean hover = permis && mx >= bx && mx < bx + ACC && my >= y && my < y + 24;
                    boite(g, bx, y, ACC, 24, actif, hover, permis, accent);
                    ItemStack ic = icones.getOrDefault(CLE_ACC[i], ItemStack.EMPTY);
                    String n = textes.getOrDefault(CLE_ACC[i], "");
                    if (!n.isEmpty()) Rendu.icone(g, ic, bx + 3, y + 3, 17, n, 255);
                    else Ui.centered(g, f, Ui.lit(initialeEmplacement(emp), Ui.SMALL), bx + ACC / 2f, y + 9, Ui.argb(permis ? Ui.MUTED : Ui.FAINT, permis ? 255 : 120));
                }
            }
            case "soins" -> {
                demi(g, f, vx, y, DEMI, "soin0", mx, my, accent);
                demi(g, f, vx + DEMI + 4, y, DEMI, "soin1", mx, my, accent);
            }
            case "grenades" -> {
                demi(g, f, vx, y, DEMI, "letale", mx, my, accent);
                demi(g, f, vx + DEMI + 4, y, DEMI, "tactique", mx, my, accent);
            }
            default -> {
                String cle = CHAMPS[r];
                boolean actif = champ.equals(cle);
                boolean hover = mx >= vx && mx < vx + vw && my >= y && my < y + 24;
                boite(g, vx, y, vw, 24, actif, hover, true, accent);
                String n = textes.getOrDefault(cle, "");
                if (n.isEmpty()) {
                    g.drawString(f, Ui.tr(Ui.SMALL, "duel.classes.vide"), vx + 8, y + 9, Ui.argb(Ui.FAINT, 255), false);
                } else {
                    Rendu.icone(g, icones.getOrDefault(cle, ItemStack.EMPTY), vx + 4, y + 3, 18, n, 255);
                    g.drawString(f, Ui.lit(courts.getOrDefault(cle, n), Ui.FONT), vx + 27, y + 8, Ui.argb(Ui.TEXT, 255), false);
                    if (cle.equals("principale")) {
                        String t = textes.getOrDefault("principale_type", "");
                        if (!t.isEmpty()) Ui.spacedRight(g, f, t, Ui.SMALL, vx + vw - 6, y + 9, Ui.argb(Ui.MUTED, 220), 0.5f);
                    }
                }
            }
        }
    }

    /** Abreviation traduite d'un emplacement d'accessoire (cle duel.emplacement.court.<emp>, lue une fois par init). */
    private String initialeEmplacement(String emp) {
        return initiales.getOrDefault(emp, "·");
    }

    private final Map<String, String> initiales = new LinkedHashMap<>();

    private void demi(GuiGraphics g, Font f, int x, int y, int w, String cle, int mx, int my, int accent) {
        boolean actif = champ.equals(cle);
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + 24;
        boite(g, x, y, w, 24, actif, hover, true, accent);
        String n = textes.getOrDefault(cle, "");
        if (n.isEmpty()) {
            g.drawString(f, Ui.tr(Ui.SMALL, "duel.classes.vide"), x + 6, y + 9, Ui.argb(Ui.FAINT, 255), false);
            return;
        }
        Rendu.icone(g, icones.getOrDefault(cle, ItemStack.EMPTY), x + 3, y + 3, 18, n, 255);
        g.drawString(f, Ui.lit(courts.getOrDefault(cle, n), Ui.SMALL), x + 24, y + 9, Ui.argb(Ui.TEXT, 255), false);
    }

    private static void boite(GuiGraphics g, int x, int y, int w, int h, boolean actif, boolean hover, boolean permis, int accent) {
        if (!permis) {
            Ui.soft(g, x, y, w, h, Ui.argb(0xFFFFFF, 3));
            Ui.softOutline(g, x + 1, y + 1, w - 2, h - 2, Ui.argb(0xFFFFFF, 12));
            return;
        }
        Ui.soft(g, x, y, w, h, actif ? Ui.argb(accent, 34) : Ui.argb(0xFFFFFF, hover ? 16 : 7));
        Ui.softOutline(g, x + 1, y + 1, w - 2, h - 2, actif ? Ui.argb(accent, 220) : hover ? Ui.argb(accent, 140) : Ui.argb(0xFFFFFF, 30));
    }

    /** Jauges de l'arme survolee (ou de l'arme du champ) sous la grille. */
    private void jauges(GuiGraphics g, Font f) {
        Catalogue c = cat();
        String id = survol >= 0 && survol < cellules.size() ? cellules.get(survol).id() : valeurChamp();
        Catalogue.Arme a = c.arme(id);
        int y = grille.getY() + grille.getHeight() + 6;
        if (a == null) return;
        g.drawString(f, Ui.lit(Ui.ellipsis(f, Rendu.nom(a.nom(), a.id()), Ui.BOLD, rw - 50), Ui.BOLD), rx0, y, Ui.argb(Ui.TEXT, 255), false);
        Ui.spacedRight(g, f, Rendu.type(a.type()), Ui.SMALL, rx0 + rw, y + 1, Ui.argb(Ui.MUTED, 255), 0.5f);
        jauge(g, f, y + 13, "duel.classes.jauge.degats", a.degats() / maxDegats);
        jauge(g, f, y + 24, "duel.classes.jauge.cadence", a.rpm() / maxRpm);
        jauge(g, f, y + 35, "duel.classes.jauge.chargeur", a.chargeur() / maxChargeur);
    }

    private void jauge(GuiGraphics g, Font f, int y, String cle, float r) {
        g.drawString(f, Ui.tr(Ui.SMALL, cle), rx0, y, Ui.argb(Ui.MUTED, 255), false);
        Ui.bar(g, rx0 + 62, y + 2, rw - 62, 3, r, Ui.accent());
    }

    // ------------------------------------------------------------------ souris, clavier
    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        if (super.mouseClicked(mx, my, b)) return true;
        if (b != 0) return false;
        // emplacements
        for (int i = 0; i < ClassePerso.EMPLACEMENTS; i++) {
            int x = px + 16, y = py + 40 + i * 52;
            if (mx >= x && mx < x + 136 && my >= y && my < y + 46) {
                SonsUi.clic(0.5f);
                emplacement(i);
                return true;
            }
        }
        // lignes du detail
        int vx = mx0 + DX;
        for (int r = 0; r < CHAMPS.length; r++) {
            int y = py + LIGNE_Y + r * PAS;
            if (my < y || my >= y + 24) continue;
            String c = CHAMPS[r];
            switch (c) {
                case "accessoires" -> {
                    for (int i = 0; i < Catalogue.EMPLACEMENTS.size(); i++) {
                        int bx = vx + i * PAS_ACC;
                        if (mx >= bx && mx < bx + ACC && !textes.getOrDefault(CLE_PERMIS[i], "").isEmpty()) return clicChamp(CLE_ACC[i]);
                    }
                }
                case "soins" -> {
                    if (mx >= vx && mx < vx + DEMI) return clicChamp("soin0");
                    if (mx >= vx + DEMI + 4 && mx < vx + VW) return clicChamp("soin1");
                }
                case "grenades" -> {
                    if (mx >= vx && mx < vx + DEMI) return clicChamp("letale");
                    if (mx >= vx + DEMI + 4 && mx < vx + VW) return clicChamp("tactique");
                }
                default -> {
                    if (mx >= vx && mx < vx + VW) return clicChamp(c);
                }
            }
        }
        return false;
    }

    private boolean clicChamp(String c) {
        SonsUi.clic(0.5f);
        ouvrirSelecteur(c);
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int b) {
        for (var c : children()) c.mouseReleased(mx, my, b);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (nom != null && nom.isFocused()) {
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
        SonsUi.fermer(0.6f);
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

    @Override
    public String journal() {
        return "panneau " + px + "," + py + " " + pw + "x" + ph + " emplacements x" + (px + 16) + " y" + (py + 40) + "+52k (136x46) ; detail x" + (mx0 + DX)
                + " y" + (py + LIGNE_Y) + "+30r ; grille " + grille.getX() + "," + grille.getY() + " " + grille.getWidth() + "x" + grille.getHeight()
                + " cases 48x46 ; champ " + champ + " ; " + cellules.size() + " cases ; classe " + classe();
    }

    // ------------------------------------------------------------------ grille du selecteur
    private final class Grille extends HzScroll {
        Grille(int x, int y, int w, int h) {
            super(x, y, w, h);
        }

        @Override
        protected void renderContent(GuiGraphics g, int mx, int my, float pt) {
            int accent = Ui.accent();
            String valeur = valeurChamp();
            survol = -1;
            for (int i = 0; i < cellules.size(); i++) {
                int x = getX() + (i % 4) * 48, y = getY() + (i / 4) * 46;
                if (y + 46 < getY() + getScroll() || y > getY() + getScroll() + getHeight()) continue;
                Cellule c = cellules.get(i);
                boolean sel = c.id().equals(valeur);
                boolean hover = mx >= x && mx < x + 46 && my >= y && my < y + 44;
                if (hover) survol = i;
                Ui.soft(g, x, y, 46, 44, sel ? Ui.argb(accent, 40) : Ui.argb(0xFFFFFF, hover ? 16 : 6));
                Ui.softOutline(g, x + 1, y + 1, 44, 42, sel ? Ui.argb(accent, 220) : hover ? Ui.argb(accent, 120) : Ui.argb(0xFFFFFF, 18));
                if (c.id().isEmpty()) {
                    Icons.draw(g, Icons.CROIX, x + 15, y + 8, 16, Ui.MUTED, 200);
                } else {
                    Rendu.icone(g, c.icone(), x + 11, y + 4, 24, c.nom(), 255);
                }
                Ui.centered(g, font, Ui.lit(c.court(), Ui.SMALL), x + 23, y + 33, Ui.argb(sel ? Ui.TEXT : Ui.MUTED, 255));
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            if (!dansZone(mx, my) || b != 0) return false;
            double cy = my + getScroll() - getY(), cx = mx - getX();
            int col = (int) (cx / 48), row = (int) (cy / 46);
            if (col < 0 || col > 3) return true;
            int i = row * 4 + col;
            if (i >= 0 && i < cellules.size()) {
                String id = cellules.get(i).id();
                net.minecraft.client.Minecraft.getInstance().execute(() -> appliquer(id));
            }
            return true;
        }
    }
}
