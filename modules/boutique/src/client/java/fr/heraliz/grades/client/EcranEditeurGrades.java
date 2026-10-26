package fr.heraliz.grades.client;

import fr.heraliz.grades.Grade;
import fr.heraliz.grades.PermsGrades;
import fr.heraliz.grades.client.kit.Echelle;
import fr.heraliz.grades.client.kit.HzButton;
import fr.heraliz.grades.client.kit.HzField;
import fr.heraliz.grades.client.kit.SonsUi;
import fr.heraliz.grades.client.kit.Ui;
import fr.heraliz.grades.reseau.ReseauGrades;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Éditeur des grades (2.0.0, maquettes validées le 22/10 ; style des écrans de clan, canevas 640 x 360) : à gauche la
 * liste des grades et la création d'un grade, à droite la fiche du grade choisi en quatre onglets (Apparence,
 * Permissions, Kits, Joueurs). L'écran ne fait qu'envoyer des demandes : le serveur vérifie tout (op, /login, bornes),
 * enregistre et renvoie l'état. Les objets glissés dans un kit sont COPIÉS depuis l'inventaire (ils restent au joueur).
 */
public final class EcranEditeurGrades extends Screen {
    private enum Onglet { APPARENCE, PERMISSIONS, KITS, JOUEURS }

    private static final int[] PALETTE = {0xE5484D, 0xFF8A3D, 0xFFC23A, 0xF5C842, 0x5FD08A, 0x2EC4B6, 0x8FD3FF, 0x4C7DFF, 0x9146FF, 0xB48CFF, 0xFF6FB5, 0xA0A6B0};
    private static final int LIGNE = 19, CASE = 18;
    /** Nom du grade en tête de fiche : police grasse 14 à sa vraie taille (jamais de texte agrandi, il deviendrait flou). */
    private static final ResourceLocation NOM_FICHE = new ResourceLocation("heraliz_grades", "ui_fiche");

    private ReseauGrades.EtatEditeur etat;
    private String choisi = Grade.SURVIVANT.id;
    private Onglet onglet = Onglet.APPARENCE;
    private int defile;
    private String message = "";
    private long messageA;

    // copie de travail du grade choisi
    private int couleur, ordre;
    private Grade.Type type;
    private boolean gras, prefixe, colonne;
    private final Map<String, Integer> perms = new LinkedHashMap<>();
    private String fr = "", en = "", ru = "";

    // kit choisi
    private int kitChoisi;
    private String kitNom = "";
    private int kitRecharge = 24;
    private final ItemStack[] kitObjets = new ItemStack[27];

    // état chargé (grade et kit) : Échap demande confirmation si la copie de travail a changé
    private String instantaneGrade = "", instantaneKit = "";
    private final ItemStack[] kitDepart = new ItemStack[27];
    private int ticks, confirmerA = -1000;

    private int ox, oy;
    private HzField champFr, champEn, champRu, champHexa, champNouveau, champKit;

    private EcranEditeurGrades(ReseauGrades.EtatEditeur e) {
        super(Component.translatable("gui.heraliz_grades.editeur.titre"));
        etat = e;
        Arrays.fill(kitObjets, ItemStack.EMPTY);
        charger();
    }

    /** Paquet du serveur : ouvre l'éditeur, ou met à jour celui qui est ouvert (après une action). */
    public static void recevoir(ReseauGrades.EtatEditeur e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof EcranEditeurGrades ed) {
            ed.etat = e;
            if (!e.message().isEmpty()) {
                ed.message = e.message();
                ed.messageA = System.currentTimeMillis();
            }
            if (Grade.parId(ed.choisi) == null) ed.choisi = Grade.SURVIVANT.id;
            ed.charger();
            ed.rebuildWidgets();
        } else if (e.ouvrir()) {
            SonsUi.ouvrir(0.6F);
            mc.setScreen(new EcranEditeurGrades(e));
        }
    }

    private static String t(String cle, Object... args) {
        return Component.translatable("gui.heraliz_grades.editeur." + cle, args).getString();
    }

    private Grade grade() {
        Grade g = Grade.parId(choisi);
        return g != null ? g : Grade.SURVIVANT;
    }

    /** Recharge la copie de travail depuis le grade choisi (et son kit). */
    private void charger() {
        Grade g = grade();
        couleur = g.couleur;
        ordre = g.ordre;
        type = g.type;
        gras = g.gras;
        prefixe = g.prefixe;
        colonne = g.colonneStaff;
        fr = g.nomFr;
        en = g.nomEn;
        ru = g.nomRu;
        perms.clear();
        perms.putAll(g.perms);
        chargerKit();
        instantaneGrade = signatureGrade();
    }

    private String signatureGrade() {
        return fr + "|" + en + "|" + ru + "|" + couleur + "|" + type + "|" + gras + "|" + prefixe + "|" + colonne + "|" + ordre + "|" + new java.util.TreeMap<>(perms);
    }

    /** Vrai si le grade ou le kit en cours a été modifié sans être enregistré. */
    private boolean modifie() {
        if (!signatureGrade().equals(instantaneGrade) || !(kitNom + "|" + kitRecharge).equals(instantaneKit)) return true;
        for (int i = 0; i < 27; i++) if (!ItemStack.matches(kitObjets[i], kitDepart[i])) return true;
        return false;
    }

    private List<ReseauGrades.KitEdite> kits() {
        List<ReseauGrades.KitEdite> l = new ArrayList<>();
        for (ReseauGrades.KitEdite k : etat.kits()) if (k.grade().equals(choisi)) l.add(k);
        return l;
    }

    private void chargerKit() {
        List<ReseauGrades.KitEdite> l = kits();
        Arrays.fill(kitObjets, ItemStack.EMPTY);
        if (kitChoisi >= 0 && kitChoisi < l.size()) {
            ReseauGrades.KitEdite k = l.get(kitChoisi);
            kitNom = k.nom();
            kitRecharge = k.rechargeH();
            for (int i = 0; i < Math.min(27, k.objets().size()); i++) kitObjets[i] = k.objets().get(i).copy();
        } else {
            kitChoisi = -1;
            kitNom = t("kit_nouveau_nom");
            kitRecharge = 24;
        }
        instantaneKit = kitNom + "|" + kitRecharge;
        for (int i = 0; i < 27; i++) kitDepart[i] = kitObjets[i].copy();
    }

    // ------------------------------------------------------------------ mise en page
    @Override
    protected void init() {
        Echelle.forcer(this);
        ox = (width - 640) / 2;
        oy = (height - 360) / 2;
        Grade g = grade();
        // gauche : création d'un grade
        champNouveau = addRenderableWidget(new HzField(ox + 22, oy + 318, 102, 16, 24, s -> s.matches("[a-z0-9_]*")).placeholder(t("nouveau_id")));
        addRenderableWidget(new HzButton(ox + 128, oy + 318, 26, 16, Component.literal("+"), 0xB98CFF, this::creer).look(HzButton.Look.PRIMARY));
        // bas : actions
        int by = oy + 322;
        // largeurs : « SUPPRIMER LE GRADE » (116 px) et « ENREGISTRER » (74 px) ne touchent plus leur bord
        if (onglet == Onglet.APPARENCE || onglet == Onglet.PERMISSIONS) {
            if (!g.interne) addRenderableWidget(new HzButton(ox + 184, by, 128, 16, Component.literal(t("supprimer_grade")), Ui.DANGER, this::supprimerGrade).danger());
            addRenderableWidget(new HzButton(ox + 462, by, 64, 16, Component.literal(t("annuler")), Ui.MUTED, () -> {
                charger();
                rebuildWidgets();
            }));
            addRenderableWidget(new HzButton(ox + 532, by, 84, 16, Component.literal(t("enregistrer")), couleur, this::enregistrerGrade).look(HzButton.Look.PRIMARY));
        } else if (onglet == Onglet.KITS) {
            if (kitChoisi >= 0) addRenderableWidget(new HzButton(ox + 184, by, 120, 16, Component.literal(t("supprimer_kit")), Ui.DANGER, this::supprimerKit).danger());
            addRenderableWidget(new HzButton(ox + 462, by, 64, 16, Component.literal(t("annuler")), Ui.MUTED, () -> {
                chargerKit();
                rebuildWidgets();
            }));
            addRenderableWidget(new HzButton(ox + 532, by, 84, 16, Component.literal(t("enregistrer")), couleur, this::enregistrerKit).look(HzButton.Look.PRIMARY));
        }
        // onglet Apparence : champs
        if (onglet == Onglet.APPARENCE) {
            champFr = addRenderableWidget(new HzField(ox + 184, oy + 60, 200, 16, 32, HzField.TOUT));
            champFr.setValue(fr);
            champFr.setResponder(v -> fr = v);
            champEn = addRenderableWidget(new HzField(ox + 184, oy + 92, 96, 16, 32, HzField.TOUT));
            champEn.setValue(en);
            champEn.setResponder(v -> en = v);
            champRu = addRenderableWidget(new HzField(ox + 288, oy + 92, 96, 16, 32, HzField.TOUT));
            champRu.setValue(ru);
            champRu.setResponder(v -> ru = v);
            champHexa = addRenderableWidget(new HzField(ox + 184, oy + 176, 70, 16, 6, HzField.HEXA));
            champHexa.setValue(String.format(Locale.ROOT, "%06X", couleur));
            champHexa.setResponder(v -> {
                if (v.length() == 6) couleur = Integer.parseInt(v, 16);
            });
        } else if (onglet == Onglet.KITS) {
            champKit = addRenderableWidget(new HzField(ox + 184, oy + 82 + decalageKits(), 150, 16, 32, HzField.TOUT));
            champKit.setValue(kitNom);
            champKit.setResponder(v -> kitNom = v);
        }
    }

    // ------------------------------------------------------------------ actions
    private void envoyer(ReseauGrades.ActionEditeur.Genre genre, Grade.Def def, ReseauGrades.KitEdite kit) {
        SonsUi.clic(0.6F);
        ReseauGrades.CANAL.sendToServer(new ReseauGrades.ActionEditeur(genre, choisi, def, kit));
    }

    private void creer() {
        String id = champNouveau.getValue();
        if (!Grade.idValide(id)) {
            message = t("id_invalide");
            messageA = System.currentTimeMillis();
            return;
        }
        choisi = id;
        SonsUi.clic(0.6F);
        ReseauGrades.CANAL.sendToServer(new ReseauGrades.ActionEditeur(ReseauGrades.ActionEditeur.Genre.CREER_GRADE, id, null, null));
    }

    private void enregistrerGrade() {
        Grade g = grade();
        envoyer(ReseauGrades.ActionEditeur.Genre.ENREGISTRER_GRADE,
                new Grade.Def(g.id, g.interne, fr, en, ru, couleur, type, g.rang, ordre, gras, prefixe, colonne, new LinkedHashMap<>(perms)), null);
    }

    private void supprimerGrade() {
        envoyer(ReseauGrades.ActionEditeur.Genre.SUPPRIMER_GRADE, null, null);
        choisi = Grade.SURVIVANT.id;
    }

    private void enregistrerKit() {
        List<ItemStack> objets = new ArrayList<>();
        for (ItemStack s : kitObjets) if (!s.isEmpty()) objets.add(s);
        List<ReseauGrades.KitEdite> l = kits();
        String id = kitChoisi >= 0 && kitChoisi < l.size() ? l.get(kitChoisi).id() : "";
        envoyer(ReseauGrades.ActionEditeur.Genre.ENREGISTRER_KIT, null, new ReseauGrades.KitEdite(choisi, id, kitNom, kitRecharge, objets));
        if (kitChoisi < 0) kitChoisi = l.size();
    }

    private void supprimerKit() {
        List<ReseauGrades.KitEdite> l = kits();
        if (kitChoisi < 0 || kitChoisi >= l.size()) return;
        envoyer(ReseauGrades.ActionEditeur.Genre.SUPPRIMER_KIT, null, l.get(kitChoisi));
        kitChoisi = 0;
    }

    // ------------------------------------------------------------------ souris
    private static boolean dans(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        if (super.mouseClicked(mx, my, b)) return true;
        // liste des grades
        List<Grade> grades = Grade.values();
        for (int i = defile; i < Math.min(grades.size(), defile + visibles()); i++) {
            int y = oy + 40 + (i - defile) * LIGNE;
            if (dans(mx, my, ox + 18, y, 138, LIGNE - 2)) {
                if (!grades.get(i).id.equals(choisi)) {
                    choisi = grades.get(i).id;
                    kitChoisi = 0;
                    charger();
                    SonsUi.clic(0.6F);
                    rebuildWidgets();
                }
                return true;
            }
        }
        // onglets
        Onglet[] os = Onglet.values();
        for (int i = 0; i < os.length; i++) {
            if (dans(mx, my, ox + 358 + i * 65, oy + 18, 62, 16)) {
                if (onglet != os[i]) {
                    onglet = os[i];
                    SonsUi.clic(0.6F);
                    rebuildWidgets();
                }
                return true;
            }
        }
        return switch (onglet) {
            case APPARENCE -> clicApparence(mx, my);
            case PERMISSIONS -> clicPermissions(mx, my, b);
            case KITS -> clicKits(mx, my, b);
            case JOUEURS -> false;
        };
    }

    private int visibles() {
        return 13;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx < ox + 165) {
            int max = Math.max(0, Grade.values().size() - visibles());
            defile = Math.max(0, Math.min(max, defile - (int) Math.signum(delta)));
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    private boolean clicApparence(double mx, double my) {
        for (int i = 0; i < PALETTE.length; i++) {
            if (dans(mx, my, ox + 184 + (i % 6) * 17, oy + 134 + (i / 6) * 17, 14, 14)) {
                couleur = PALETTE[i];
                if (champHexa != null) champHexa.setValue(String.format(Locale.ROOT, "%06X", couleur));
                SonsUi.clic(0.5F);
                return true;
            }
        }
        if (!grade().interne) {
            Grade.Type[] familles = {Grade.Type.STAFF, Grade.Type.BOUTIQUE, Grade.Type.SPECIAL};
            for (int i = 0; i < familles.length; i++) {
                if (dans(mx, my, ox + 184 + i * 68, oy + 214, 64, 15)) {
                    type = familles[i];
                    SonsUi.clic(0.5F);
                    return true;
                }
            }
        }
        if (dans(mx, my, ox + 580, oy + 60, 30, 14)) gras = !gras;
        else if (dans(mx, my, ox + 580, oy + 82, 30, 14)) prefixe = !prefixe;
        else if (dans(mx, my, ox + 580, oy + 104, 30, 14)) colonne = !colonne;
        else if (dans(mx, my, ox + 530, oy + 126, 14, 14)) ordre = Math.max(0, ordre - 1);
        else if (dans(mx, my, ox + 596, oy + 126, 14, 14)) ordre = Math.min(999, ordre + 1);
        else return false;
        SonsUi.clic(0.5F);
        return true;
    }

    /** Ligne d'une permission : colonne (0 déplacements, 1 coffres) et rang dans la colonne. */
    private int[] place(PermsGrades.Perm p) {
        int col = p.groupe().equals("coffres") ? 1 : 0, n = 0;
        for (PermsGrades.Perm q : PermsGrades.TOUTES) {
            if (q == p) break;
            if ((q.groupe().equals("coffres") ? 1 : 0) == col) n++;
        }
        return new int[]{ox + 184 + col * 218, oy + 62 + n * 34};
    }

    private boolean clicPermissions(double mx, double my, int b) {
        boolean survivant = grade() == Grade.SURVIVANT;
        for (PermsGrades.Perm p : PermsGrades.TOUTES) {
            int[] xy = place(p);
            int x = xy[0], y = xy[1], droite = x + 210;
            Integer v = perms.get(p.cle());
            int base = Grade.SURVIVANT.perms.getOrDefault(p.cle(), p.defaut());
            if (p.sorte() == PermsGrades.Sorte.OUI_NON) {
                if (dans(mx, my, droite - 34, y + 6, 30, 14)) {
                    perms.put(p.cle(), (v == null ? base : v) >= 1 ? 0 : 1);
                    SonsUi.clic(0.5F);
                    return true;
                }
            } else {
                if (dans(mx, my, droite - 96, y + 6, 14, 14) || dans(mx, my, droite - 18, y + 6, 14, 14)) {
                    int sens = mx >= droite - 18 ? 1 : -1;
                    int depart = v == null ? base : v;
                    perms.put(p.cle(), p.borner(depart + sens * p.pas()));
                    SonsUi.clic(0.5F);
                    return true;
                }
            }
            // clic sur la valeur : « comme Survivant » (réglage retiré), sauf pour Survivant
            if (!survivant && v != null && dans(mx, my, x, y, 100, 26)) {
                perms.remove(p.cle());
                SonsUi.clic(0.5F);
                return true;
            }
        }
        return false;
    }

    /** Texte d'une puce de kit (le dernier = « + Nouveau »), coupé à la largeur choisie par {@link #puces()}. */
    private String nomPuce(List<ReseauGrades.KitEdite> l, int i) {
        String n = i < l.size() ? l.get(i).nom() : "+ " + t("kit_nouveau");
        return Ui.ellipsis(font, n, Style.DEFAULT_FONT, nomPuceMax);
    }

    private int nomPuceMax = 148;

    /**
     * Puces des kits, sur plusieurs rangées dans la colonne de gauche (avant ox + 346 : le contenu du kit commence à
     * ox + 350) : {x, y, largeur} de chacune. Plus de 3 rangées : noms coupés plus court (2 puces par rangée).
     */
    private List<int[]> puces() {
        nomPuceMax = 148;
        List<int[]> out = rangerPuces();
        if (out.get(out.size() - 1)[1] > oy + 90) {
            nomPuceMax = 65;
            out = rangerPuces();
        }
        return out;
    }

    private List<int[]> rangerPuces() {
        List<ReseauGrades.KitEdite> l = kits();
        List<int[]> out = new ArrayList<>();
        int x = ox + 184, y = oy + 50;
        for (int i = 0; i <= l.size(); i++) {
            int w = font.width(nomPuce(l, i)) + 14;
            if (x > ox + 184 && x + w > ox + 346) {
                x = ox + 184;
                y += 20;
            }
            out.add(new int[]{x, y, w});
            x += w + 4;
        }
        return out;
    }

    /** Hauteur ajoutée par les rangées de puces en plus de la première (le reste de la colonne descend d'autant). */
    private int decalageKits() {
        List<int[]> p = puces();
        return p.get(p.size() - 1)[1] - (oy + 50);
    }

    private boolean clicKits(double mx, double my, int b) {
        // puces des kits
        List<ReseauGrades.KitEdite> l = kits();
        List<int[]> p = puces();
        for (int i = 0; i <= l.size(); i++) {
            if (dans(mx, my, p.get(i)[0], p.get(i)[1], p.get(i)[2], 16)) {
                kitChoisi = i < l.size() ? i : -1;
                chargerKit();
                SonsUi.clic(0.6F);
                rebuildWidgets();
                return true;
            }
        }
        // recharge
        int dy = decalageKits();
        if (dans(mx, my, ox + 270, oy + 108 + dy, 14, 14)) {
            kitRecharge = kitRecharge > 24 ? kitRecharge - 24 : Math.max(1, kitRecharge - 1);
            SonsUi.clic(0.5F);
            return true;
        }
        if (dans(mx, my, ox + 320, oy + 108 + dy, 14, 14)) {
            kitRecharge = kitRecharge >= 24 ? Math.min(720, kitRecharge + 24) : kitRecharge + 1;
            SonsUi.clic(0.5F);
            return true;
        }
        // contenu du kit : clic droit = vider la case
        for (int i = 0; i < 27; i++) {
            if (dans(mx, my, ox + 350 + (i % 9) * CASE, oy + 70 + (i / 9) * CASE, CASE, CASE)) {
                if (b == 1 || hasShiftDown()) kitObjets[i] = ItemStack.EMPTY;
                SonsUi.clic(0.5F);
                return true;
            }
        }
        // inventaire : clic = copie dans la première case libre du kit
        var inv = Minecraft.getInstance().player.getInventory().items;
        for (int i = 0; i < 36; i++) {
            int slot = i < 27 ? i + 9 : i - 27;
            int sx = ox + 350 + (i % 9) * CASE, sy = oy + 140 + (i / 9) * CASE + (i >= 27 ? 4 : 0);
            if (dans(mx, my, sx, sy, CASE, CASE)) {
                ItemStack s = inv.get(slot);
                if (s.isEmpty()) return true;
                for (int k = 0; k < 27; k++) {
                    if (kitObjets[k].isEmpty()) {
                        kitObjets[k] = s.copy();
                        SonsUi.clic(0.6F);
                        return true;
                    }
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            // 1er Échap : quitte le champ en cours de saisie
            if (getFocused() instanceof HzField h && h.isFocused()) {
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
        // modifications pas enregistrées : un 2e Échap dans les 3 s pour quitter
        if (modifie() && ticks - confirmerA > 60) {
            confirmerA = ticks;
            return;
        }
        super.onClose();
    }

    @Override
    public void tick() {
        ticks++;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        Echelle.restaurer();
        super.removed();
    }

    // ------------------------------------------------------------------ dessin
    private void interrupteur(GuiGraphics g, int x, int y, boolean on) {
        Ui.soft(g, x, y, 30, 14, Ui.argb(on ? Ui.OK : 0xFFFFFF, on ? 90 : 30));
        Ui.soft(g, on ? x + 18 : x + 2, y + 2, 10, 10, Ui.argb(on ? Ui.OK : Ui.MUTED, 255));
    }

    private void bouton(GuiGraphics g, int x, int y, String s, boolean survol) {
        Ui.softOutline(g, x, y, 14, 14, Ui.argb(0xFFFFFF, survol ? 90 : 40));
        Ui.centered(g, font, Ui.lit(s, Ui.BOLD), x + 7, y + 3, Ui.argb(Ui.MUTED, 255));
    }

    private void ligne(GuiGraphics g, int x, int y, int w, String nom, String aide) {
        Ui.soft(g, x, y, w, 26, Ui.argb(0xFFFFFF, 7));
        g.drawString(font, Ui.lit(nom, Ui.BOLD), x + 6, y + 4, Ui.argb(Ui.TEXT, 255), false);
        if (!aide.isEmpty()) g.drawString(font, Ui.lit(Ui.ellipsis(font, aide, Ui.SMALL, w - 110), Ui.SMALL), x + 6, y + 15, Ui.argb(Ui.MUTED, 255), false);
    }

    private static String valeur(PermsGrades.Perm p, int v) {
        return switch (p.sorte()) {
            // unités dans les langues du mod (« 5 min 30 », « 40 s »)
            case SECONDES -> v < 60 ? Component.translatable("gui.heraliz_grades.duree.s", v).getString()
                    : v % 60 == 0 ? Component.translatable("gui.heraliz_grades.duree.min", v / 60).getString()
                    : t("min_s", v / 60, String.format(Locale.ROOT, "%02d", v % 60));
            case LIGNES -> v <= 0 ? t("aucun_acces") : t("lignes", v);
            case NOMBRE -> String.valueOf(v);
            case OUI_NON -> v >= 1 ? t("oui") : t("non");
        };
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        Font f = font;
        Ui.fond(g, width, height);
        Grade gr = grade();

        // ---- gauche : liste
        Ui.glass(g, ox + 12, oy + 12, 150, 336);
        Ui.spaced(g, f, t("grades").toUpperCase(Locale.ROOT), Ui.SMALL, ox + 22, oy + 22, Ui.argb(Ui.MUTED, 255), 1.0f);
        List<Grade> grades = Grade.values();
        for (int i = defile; i < Math.min(grades.size(), defile + visibles()); i++) {
            Grade x = grades.get(i);
            int y = oy + 40 + (i - defile) * LIGNE;
            boolean sel = x.id.equals(choisi);
            if (sel) {
                Ui.soft(g, ox + 18, y, 138, LIGNE - 2, Ui.argb(0xFFFFFF, 18));
                Ui.softOutline(g, ox + 19, y + 1, 136, LIGNE - 4, Ui.argb(x.couleur, 140));
            } else if (dans(mx, my, ox + 18, y, 138, LIGNE - 2)) {
                Ui.soft(g, ox + 18, y, 138, LIGNE - 2, Ui.argb(0xFFFFFF, 10));
            }
            Ui.soft(g, ox + 25, y + 6, 6, 6, Ui.argb(x.couleur, 255));
            // famille d'abord : le nom est coupé à la place qu'elle laisse (fr « TOUT LE MONDE » 62 px, ru « ПЕРСОНАЛ » 47)
            String fam = t("famille_" + x.type.name().toLowerCase(Locale.ROOT)).toUpperCase(Locale.ROOT);
            int famW = (int) Math.ceil(Ui.spacedWidth(f, fam, Ui.SMALL, 0.4f));
            g.drawString(f, Ui.lit(Ui.ellipsis(f, x.nomLocal(), Ui.BOLD, 112 - famW), Ui.BOLD), ox + 36, y + 5, Ui.argb(x.couleur, 255), false);
            Ui.spacedRight(g, f, fam, Ui.SMALL, ox + 152, y + 6, Ui.argb(Ui.FAINT, 255), 0.4f);
        }
        Ui.spaced(g, f, t("nouveau").toUpperCase(Locale.ROOT), Ui.SMALL, ox + 22, oy + 306, Ui.argb(Ui.FAINT, 255), 0.6f);

        // ---- droite : fiche
        Ui.glass(g, ox + 170, oy + 12, 458, 336);
        Ui.soft(g, ox + 184, oy + 22, 8, 8, Ui.argb(couleur, 255));
        // gras 14, coupé à la même largeur (110 x 1,3) ; même milieu des capitales que l'ancien texte 11 agrandi 1,3 fois
        net(g, f, Ui.lit(Ui.ellipsis(f, gr.nomLocal(), NOM_FICHE, 143), NOM_FICHE), ox + 198, oy + 19 + 1.3f * milieu(11) - milieu(14),
                Ui.argb(couleur, 255));
        long nb = etat.membres().stream().filter(m -> m.grade().equals(gr.id)).count();
        if (gr.attribuable()) g.drawString(f, Ui.lit(t("joueurs_nb", nb), Ui.SMALL), ox + 198, oy + 33, Ui.argb(Ui.MUTED, 255), false);
        Onglet[] os = Onglet.values();
        for (int i = 0; i < os.length; i++) {
            int x = ox + 358 + i * 65;
            boolean on = os[i] == onglet;
            if (on) {
                Ui.soft(g, x, oy + 18, 62, 16, Ui.argb(couleur, 40));
                g.fill(x + 4, oy + 32, x + 58, oy + 33, Ui.argb(couleur, 255));
            } else if (dans(mx, my, x, oy + 18, 62, 16)) {
                Ui.soft(g, x, oy + 18, 62, 16, Ui.argb(0xFFFFFF, 10));
            }
            Ui.spacedCentered(g, f, t("onglet_" + os[i].name().toLowerCase(Locale.ROOT)).toUpperCase(Locale.ROOT), Ui.SMALL, x + 31, oy + 23,
                    Ui.argb(on ? Ui.TEXT : Ui.MUTED, 255), 0.8f);
        }
        Ui.hairline(g, ox + 184, ox + 614, oy + 42, 0xFFFFFF, 50, false, true);

        ItemStack survole = ItemStack.EMPTY;
        switch (onglet) {
            case APPARENCE -> dessinerApparence(g, mx, my, gr);
            case PERMISSIONS -> dessinerPermissions(g, mx, my, gr);
            case KITS -> survole = dessinerKits(g, mx, my);
            case JOUEURS -> dessinerJoueurs(g, gr);
        }
        if (ticks - confirmerA <= 60) {
            String q = Component.translatable("gui.heraliz_grades.quitter_confirmer").getString();
            g.drawString(f, Ui.lit(Ui.ellipsis(f, q, Ui.SMALL, 420), Ui.SMALL), ox + 184, oy + 308, Ui.argb(Ui.DANGER, 255), false);
        } else if (!message.isEmpty() && System.currentTimeMillis() - messageA < 8000) {
            g.drawString(f, Ui.lit(Ui.ellipsis(f, message, Ui.SMALL, 420), Ui.SMALL), ox + 184, oy + 308, Ui.argb(Ui.OR, 255), false);
        }
        super.render(g, mx, my, pt);
        if (!survole.isEmpty()) g.renderTooltip(f, survole, mx, my);
    }

    private void dessinerApparence(GuiGraphics g, int mx, int my, Grade gr) {
        Font f = font;
        Ui.spaced(g, f, t("nom_affiche").toUpperCase(Locale.ROOT), Ui.SMALL, ox + 184, oy + 50, Ui.argb(Ui.FAINT, 255), 0.6f);
        Ui.spaced(g, f, t("anglais").toUpperCase(Locale.ROOT), Ui.SMALL, ox + 184, oy + 82, Ui.argb(Ui.FAINT, 255), 0.6f);
        Ui.spaced(g, f, t("russe").toUpperCase(Locale.ROOT), Ui.SMALL, ox + 288, oy + 82, Ui.argb(Ui.FAINT, 255), 0.6f);
        g.drawString(f, Ui.lit(t("identifiant", gr.id), Ui.SMALL), ox + 184, oy + 112, Ui.argb(Ui.FAINT, 255), false);
        Ui.spaced(g, f, t("couleur").toUpperCase(Locale.ROOT), Ui.SMALL, ox + 184, oy + 124, Ui.argb(Ui.FAINT, 255), 0.6f);
        for (int i = 0; i < PALETTE.length; i++) {
            int x = ox + 184 + (i % 6) * 17, y = oy + 134 + (i / 6) * 17;
            Ui.soft(g, x, y, 14, 14, Ui.argb(PALETTE[i], 255));
            if ((PALETTE[i] & 0xFFFFFF) == (couleur & 0xFFFFFF)) Ui.softOutline(g, x - 1, y - 1, 16, 16, Ui.argb(0xFFFFFF, 255));
        }
        g.drawString(f, Ui.lit(t("ou_code"), Ui.SMALL), ox + 260, oy + 180, Ui.argb(Ui.MUTED, 255), false);
        Ui.spaced(g, f, t("famille").toUpperCase(Locale.ROOT), Ui.SMALL, ox + 184, oy + 202, Ui.argb(Ui.FAINT, 255), 0.6f);
        if (gr.interne) {
            String fixe = t("famille_fixe", t("famille_" + gr.type.name().toLowerCase(Locale.ROOT)));
            g.drawString(f, Ui.lit(Ui.ellipsis(f, fixe, Ui.SMALL, 208), Ui.SMALL), ox + 184, oy + 216, Ui.argb(Ui.MUTED, 255), false);
        } else {
            Grade.Type[] familles = {Grade.Type.STAFF, Grade.Type.BOUTIQUE, Grade.Type.SPECIAL};
            for (int i = 0; i < familles.length; i++) {
                int x = ox + 184 + i * 68;
                boolean on = type == familles[i];
                Ui.soft(g, x, oy + 214, 64, 15, Ui.argb(on ? couleur : 0xFFFFFF, on ? 50 : 10));
                Ui.spacedCentered(g, f, t("famille_" + familles[i].name().toLowerCase(Locale.ROOT)).toUpperCase(Locale.ROOT), Ui.SMALL, x + 32, oy + 218,
                        Ui.argb(on ? Ui.TEXT : Ui.MUTED, 255), 0.6f);
            }
            // aide sur 2 lignes au plus, dans la colonne de gauche (les options commencent à ox + 400)
            List<FormattedText> aide = f.getSplitter().splitLines(Ui.lit(t("famille_aide"), Ui.SMALL), 208, Style.EMPTY);
            for (int i = 0; i < Math.min(2, aide.size()); i++) {
                String s = aide.get(i).getString();
                if (i == 1 && aide.size() > 2) s = Ui.ellipsis(f, s + " " + aide.get(2).getString(), Ui.SMALL, 208);
                g.drawString(f, Ui.lit(s, Ui.SMALL), ox + 184, oy + 234 + i * 10, Ui.argb(Ui.MUTED, 255), false);
            }
        }
        // options
        Ui.spaced(g, f, t("options").toUpperCase(Locale.ROOT), Ui.SMALL, ox + 400, oy + 50, Ui.argb(Ui.FAINT, 255), 0.6f);
        ligne(g, ox + 400, oy + 54, 214, t("gras"), "");
        interrupteur(g, ox + 580, oy + 60, gras);
        ligne(g, ox + 400, oy + 76, 214, t("prefixe"), t("prefixe_aide"));
        interrupteur(g, ox + 580, oy + 82, prefixe);
        ligne(g, ox + 400, oy + 98, 214, t("colonne"), "");
        interrupteur(g, ox + 580, oy + 104, colonne);
        ligne(g, ox + 400, oy + 120, 214, t("rang"), t("rang_aide"));
        bouton(g, ox + 530, oy + 126, "−", dans(mx, my, ox + 530, oy + 126, 14, 14));
        Ui.centered(g, f, Ui.lit(String.valueOf(ordre), Ui.BOLD), ox + 570, oy + 129, Ui.argb(Ui.TEXT, 255));
        bouton(g, ox + 596, oy + 126, "+", dans(mx, my, ox + 596, oy + 126, 14, 14));
        // aperçu
        Ui.spaced(g, f, t("apercu").toUpperCase(Locale.ROOT), Ui.SMALL, ox + 400, oy + 156, Ui.argb(Ui.FAINT, 255), 0.6f);
        Ui.soft(g, ox + 400, oy + 166, 214, 40, Ui.argb(0x000000, 90));
        String pseudo = Minecraft.getInstance().player.getGameProfile().getName();
        // police du vrai chat et du vrai tab (DM Sans, ChatHeraliz / TabHeraliz) : plus la police pixel de Minecraft
        Component prefixeTexte = Component.literal(fr.toUpperCase(Locale.ROOT) + " ").withStyle(st -> st.withColor(couleur).withFont(gras ? Ui.BOLD : Ui.FONT));
        Component chat = Ui.in(Component.empty(), Ui.FONT).append(Component.literal("[HZ] ").withStyle(st -> st.withColor(Ui.MUTED))).append(prefixe ? prefixeTexte : Component.empty())
                .append(Component.literal(pseudo + " » " + t("apercu_message")).withStyle(st -> st.withColor(Ui.TEXT)));
        Component tab = Component.literal(t("apercu_tab") + " ").withStyle(st -> st.withColor(Ui.MUTED).withFont(Ui.FONT)).append(prefixe ? prefixeTexte : Component.empty())
                .append(Component.literal(pseudo).withStyle(st -> st.withColor(Ui.TEXT)));
        // aperçus coupés à la largeur du cadre (214 - 12)
        apercuCoupe(g, chat, ox + 406, oy + 173, 202);
        apercuCoupe(g, tab, ox + 406, oy + 189, 202);
    }

    /** Ligne en couleurs coupée avec « … » si elle dépasse la largeur. */
    private void apercuCoupe(GuiGraphics g, Component c, int x, int y, int largeur) {
        FormattedText vu = c;
        if (font.width(c) > largeur) {
            FormattedText points = Component.literal("…").withStyle(st -> st.withColor(Ui.MUTED));
            vu = FormattedText.composite(font.substrByWidth(c, largeur - font.width(points)), points);
        }
        g.drawString(font, Language.getInstance().getVisualOrder(vu), x, y, 0xFFFFFFFF, false);
    }

    private void dessinerPermissions(GuiGraphics g, int mx, int my, Grade gr) {
        Font f = font;
        Ui.spaced(g, f, t("groupe_deplacements").toUpperCase(Locale.ROOT), Ui.SMALL, ox + 184, oy + 50, Ui.argb(Ui.FAINT, 255), 0.6f);
        Ui.spaced(g, f, t("groupe_coffres").toUpperCase(Locale.ROOT), Ui.SMALL, ox + 402, oy + 50, Ui.argb(Ui.FAINT, 255), 0.6f);
        for (PermsGrades.Perm p : PermsGrades.TOUTES) {
            int[] xy = place(p);
            int x = xy[0], y = xy[1], droite = x + 210;
            Integer v = perms.get(p.cle());
            int base = Grade.SURVIVANT.perms.getOrDefault(p.cle(), p.defaut());
            ligne(g, x, y, 210, Component.translatable("heraliz_grades.perm." + p.cle()).getString(),
                    v == null ? t("comme_survivant", valeur(p, base)) : Component.translatable("heraliz_grades.perm." + p.cle() + ".aide").getString());
            if (p.sorte() == PermsGrades.Sorte.OUI_NON) {
                interrupteur(g, droite - 34, y + 6, (v == null ? base : v) >= 1);
                if (v == null) Ui.soft(g, droite - 34, y + 6, 30, 14, Ui.argb(0x000000, 90));
            } else {
                bouton(g, droite - 96, y + 6, "−", dans(mx, my, droite - 96, y + 6, 14, 14));
                Ui.centered(g, f, Ui.lit(valeur(p, v == null ? base : v), Ui.BOLD), droite - 50, y + 9, Ui.argb(v == null ? Ui.FAINT : Ui.TEXT, 255));
                bouton(g, droite - 18, y + 6, "+", dans(mx, my, droite - 18, y + 6, 14, 14));
            }
        }
        g.drawString(f, Ui.lit(gr == Grade.SURVIVANT ? t("perms_survivant") : t("perms_aide"), Ui.SMALL), ox + 184, oy + 286, Ui.argb(Ui.MUTED, 255), false);
        g.drawString(f, Ui.lit(t("perms_mojang"), Ui.SMALL), ox + 184, oy + 296, Ui.argb(0xFFC23A, 255), false);
    }

    private ItemStack dessinerKits(GuiGraphics g, int mx, int my) {
        Font f = font;
        ItemStack survole = ItemStack.EMPTY;
        List<ReseauGrades.KitEdite> l = kits();
        List<int[]> p = puces();
        for (int i = 0; i <= l.size(); i++) {
            int x = p.get(i)[0], y = p.get(i)[1], w = p.get(i)[2];
            boolean on = i < l.size() ? i == kitChoisi : kitChoisi < 0;
            Ui.soft(g, x, y, w, 16, Ui.argb(on ? couleur : 0xFFFFFF, on ? 50 : 10));
            g.drawString(f, Component.literal(nomPuce(l, i)), x + 7, y + 4, Ui.argb(on ? Ui.TEXT : Ui.MUTED, 255), false);
        }
        // le reste de la colonne descend sous la dernière rangée de puces
        int dy = decalageKits();
        Ui.spaced(g, f, t("kit_nom").toUpperCase(Locale.ROOT), Ui.SMALL, ox + 184, oy + 73 + dy, Ui.argb(Ui.FAINT, 255), 0.6f);
        ligne(g, ox + 184, oy + 102 + dy, 150, t("recharge"), "");
        bouton(g, ox + 270, oy + 108 + dy, "−", dans(mx, my, ox + 270, oy + 108 + dy, 14, 14));
        String r = kitRecharge >= 24 && kitRecharge % 24 == 0 ? t("jours", kitRecharge / 24) : t("heures", kitRecharge);
        Ui.centered(g, f, Ui.lit(r, Ui.BOLD), ox + 302, oy + 111 + dy, Ui.argb(Ui.TEXT, 255));
        bouton(g, ox + 320, oy + 108 + dy, "+", dans(mx, my, ox + 320, oy + 108 + dy, 14, 14));
        int ay = oy + 136 + dy;
        // coupé à la largeur mesurée dans la police où il est dessiné
        for (String s : f.getSplitter().splitLines(Ui.lit(t("kit_aide"), Ui.SMALL), 150, Style.EMPTY).stream().map(FormattedText::getString).toList()) {
            g.drawString(f, Ui.lit(s, Ui.SMALL), ox + 184, ay, Ui.argb(Ui.MUTED, 255), false);
            ay += 10;
        }
        // contenu
        Ui.spaced(g, f, t("kit_contenu").toUpperCase(Locale.ROOT), Ui.SMALL, ox + 350, oy + 61, Ui.argb(Ui.FAINT, 255), 0.6f);
        for (int i = 0; i < 27; i++) {
            int sx = ox + 350 + (i % 9) * CASE, sy = oy + 70 + (i / 9) * CASE;
            boolean s = dans(mx, my, sx, sy, CASE, CASE);
            Ui.soft(g, sx + 1, sy + 1, CASE - 2, CASE - 2, Ui.argb(0x000000, s ? 120 : 80));
            if (!kitObjets[i].isEmpty()) {
                g.renderItem(kitObjets[i], sx + 1, sy + 1);
                g.renderItemDecorations(f, kitObjets[i], sx + 1, sy + 1);
                if (s) survole = kitObjets[i];
            }
        }
        // inventaire du joueur
        Ui.spaced(g, f, t("ton_inventaire").toUpperCase(Locale.ROOT), Ui.SMALL, ox + 350, oy + 131, Ui.argb(Ui.FAINT, 255), 0.6f);
        var inv = Minecraft.getInstance().player.getInventory().items;
        for (int i = 0; i < 36; i++) {
            int slot = i < 27 ? i + 9 : i - 27;
            int sx = ox + 350 + (i % 9) * CASE, sy = oy + 140 + (i / 9) * CASE + (i >= 27 ? 4 : 0);
            boolean s = dans(mx, my, sx, sy, CASE, CASE);
            Ui.soft(g, sx + 1, sy + 1, CASE - 2, CASE - 2, Ui.argb(0x000000, s ? 110 : 60));
            ItemStack st = inv.get(slot);
            if (!st.isEmpty()) {
                g.renderItem(st, sx + 1, sy + 1);
                g.renderItemDecorations(f, st, sx + 1, sy + 1);
                if (s) survole = st;
            }
        }
        return survole;
    }

    private void dessinerJoueurs(GuiGraphics g, Grade gr) {
        Font f = font;
        if (!gr.attribuable()) {
            g.drawString(f, Ui.lit(gr.type == Grade.Type.SOUTIEN ? t("joueurs_soutien") : t("joueurs_tous"), Ui.SMALL), ox + 184, oy + 52, Ui.argb(Ui.MUTED, 255), false);
            return;
        }
        int y = oy + 52, n = 0, total = 0;
        for (ReseauGrades.Membre m : etat.membres()) {
            if (!m.grade().equals(gr.id)) continue;
            total++;
            if (n >= 22) continue;
            n++;
            int col = (n - 1) / 11, ly = y + ((n - 1) % 11) * 20;
            Ui.soft(g, ox + 184 + col * 218, ly, 210, 18, Ui.argb(0xFFFFFF, 7));
            Ui.soft(g, ox + 190 + col * 218, ly + 6, 6, 6, Ui.argb(m.enLigne() ? Ui.OK : Ui.FAINT, 255));
            g.drawString(f, Ui.lit(Ui.ellipsis(f, m.nom(), Ui.BOLD, 188), Ui.BOLD), ox + 202 + col * 218, ly + 5, Ui.argb(Ui.TEXT, 255), false);
        }
        // au-delà de 22 : le nombre de joueurs qui ne tiennent pas
        if (total > n) g.drawString(f, Ui.lit(Component.translatable("gui.heraliz_grades.tab.plus", total - n).getString(), Ui.SMALL), ox + 184, y + 224, Ui.argb(Ui.FAINT, 255), false);
        if (n == 0) g.drawString(f, Ui.lit(t("joueurs_aucun"), Ui.SMALL), ox + 184, y, Ui.argb(Ui.MUTED, 255), false);
        g.drawString(f, Ui.lit(t("joueurs_console", gr.id), Ui.SMALL), ox + 184, oy + 296, Ui.argb(Ui.MUTED, 255), false);
    }

    // ------------------------------------------------------------------ texte à sa vraie taille, posé net
    /** Milieu des capitales sous le point de dessin, polices DM Sans du mod (décalage 1) : taille x 0,49309 - 1. */
    private static float milieu(float taille) {
        return taille * 0.49309f - 1f;
    }

    /** Texte dessiné à sa vraie taille, posé sur un point entier de l'écran ; (x, y) = point de dessin. */
    private static void net(GuiGraphics g, Font f, Component c, float x, float y, int argb) {
        g.pose().pushPose();
        Ui.placer(g, x, y);
        g.drawString(f, c, 0, 0, argb, false);
        g.pose().popPose();
    }
}
