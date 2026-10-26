package fr.heraliz.grades.client;

import fr.heraliz.grades.Grade;
import fr.heraliz.grades.bon.Bon;
import fr.heraliz.grades.bon.ObjetBon;
import fr.heraliz.grades.bon.ReseauBon;
import fr.heraliz.grades.client.kit.Echelle;
import fr.heraliz.grades.client.kit.HzButton;
import fr.heraliz.grades.client.kit.HzField;
import fr.heraliz.grades.client.kit.SonsUi;
import fr.heraliz.grades.client.kit.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Éditeur des bons (2.5.0, op seulement ; style des écrans de clan, canevas 640 x 360) : à gauche l'aperçu, le nom, la
 * couleur et la rareté ; à droite les effets du bon et l'ajout d'un effet ; « Créer le bon » demande au serveur, qui
 * revérifie tout (droit, valeurs) et met le bon dans l'inventaire. Textes posés au point près (règle du texte net).
 */
public final class EcranBon extends Screen {
    private static final int[] PALETTE = {0xFFFFFF, 0xE8C36A, 0xFFC23A, 0xF2B83A, 0xE39B5E, 0xFF6B6B, 0xFF3333,
            0x5FD08A, 0x5CBF4A, 0x8FD3FF, 0x3F8CFF, 0xB48CFF, 0xA64DFF, 0x9146FF};
    private static final Bon.Type[] TYPES = Bon.Type.values();

    private int ox, oy;
    private String nom = "", hexa = "E8C36A", rarete = "rare";
    private int couleur = 0xE8C36A;
    private final List<Bon.Effet> effets = new ArrayList<>();
    private Bon.Type type = Bon.Type.HERALS;
    private String nombre = "", duree = "", commande = "";
    private int gradeChoisi;
    private String erreur = "", message = "";
    private boolean messageOk;
    private long messageA;
    /** Etat au depart (ou au dernier bon cree) : Echap demande confirmation s'il a change. */
    private String instantane;
    private int ticks, confirmerA = -1000;

    private EcranBon(Bon.Donnees depart) {
        super(Component.translatable("gui.heraliz_grades.bon.titre"));
        if (depart != null) {
            nom = depart.nom();
            couleur = depart.couleur() & 0xFFFFFF;
            hexa = String.format(Locale.ROOT, "%06X", couleur);
            rarete = depart.rarete();
            effets.addAll(depart.effets());
        }
        instantane = signature();
    }

    /** Tout ce que l'op a saisi, pour savoir si quelque chose a change. */
    private String signature() {
        return nom + "|" + couleur + "|" + rarete + "|" + effets + "|" + nombre + "|" + duree + "|" + commande;
    }

    /** Paquet du serveur : ouvre l'éditeur, ou donne la réponse à une création. */
    public static void recevoir(ReseauBon.Editeur e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof EcranBon ed) {
            if (!e.message().isEmpty()) {
                ed.message = e.message();
                ed.messageOk = e.ok();
                ed.messageA = System.currentTimeMillis();
                // bon cree : rien n'est perdu en quittant
                if (e.ok()) ed.instantane = ed.signature();
            }
        } else if (e.ouvrir()) {
            SonsUi.ouvrir(0.6F);
            mc.setScreen(new EcranBon(e.depart()));
        }
    }

    private static String t(String cle, Object... args) {
        return Component.translatable("gui.heraliz_grades.bon." + cle, args).getString();
    }

    private static List<Grade> grades() {
        List<Grade> l = new ArrayList<>();
        for (Grade g : Grade.values()) if (g.type == Grade.Type.BOUTIQUE) l.add(g);
        l.sort(Comparator.comparingInt(g -> g.rang));
        return l;
    }

    private Bon.Donnees donnees() {
        return new Bon.Donnees(nom.trim(), couleur, rarete, new ArrayList<>(effets));
    }

    /** L'objet tel qu'il sera (aperçu et infobulle). */
    private ItemStack apercu() {
        ItemStack st = new ItemStack(ObjetBon.BON.get());
        Bon.Donnees d = donnees();
        if (!d.nom().isEmpty() || !d.effets().isEmpty()) {
            Bon.ecrire(st, new Bon.Donnees(d.nom().isEmpty() ? t("nom_defaut") : d.nom(), d.couleur(), d.rarete(), d.effets()), "", "");
        }
        return st;
    }

    // ------------------------------------------------------------------ mise en page
    @Override
    protected void init() {
        Echelle.forcer(this);
        ox = (width - 640) / 2;
        oy = (height - 360) / 2;
        int l = ox + 20, r = ox + 270, top = oy + 54;

        // gauche : nom, couleur, rareté
        HzField champNom = addRenderableWidget(new HzField(l + 12, top + 72, 216, 16, Bon.MAX_NOM, s -> s.indexOf('§') < 0)
                .placeholder(t("nom_defaut")).remplacerAuFocus(false));
        champNom.setValue(nom);
        champNom.setResponder(v -> nom = v);
        HzField champHexa = addRenderableWidget(new HzField(l + 12, top + 110, 64, 16, 6, HzField.HEXA).transform(s -> s.toUpperCase(Locale.ROOT)));
        champHexa.setValue(hexa);
        champHexa.setResponder(v -> {
            hexa = v;
            if (v.length() == 6) couleur = Integer.parseInt(v, 16);
        });
        // raretes sur 2 colonnes de 106 : les noms tiennent en entier dans les trois langues (ru « Легендарный » 67 px)
        for (int i = 0; i < Bon.RARETES.length; i++) {
            String id = Bon.RARETES[i];
            int bx = l + 12 + (i % 2) * 110, by = top + 152 + (i / 2) * 18;
            HzButton b = new HzButton(bx, by, 106, 14, Component.literal(t("rarete." + (id.isEmpty() ? "aucune" : id))), Bon.COULEURS_RARETES[i], () -> {
                rarete = id;
                rebuildWidgets();
            });
            addRenderableWidget(b.look(HzButton.Look.PILL).choisi(id.equals(rarete)));
        }

        // droite : effets du bon
        for (int i = 0; i < effets.size(); i++) {
            int idx = i;
            addRenderableWidget(new HzButton(r + 300, top + 24 + i * 16, 38, 12, Component.literal(t("retirer")), Ui.DANGER, () -> {
                effets.remove(idx);
                rebuildWidgets();
            }).look(HzButton.Look.LINK).danger());
        }
        // ajout d'un effet
        for (int i = 0; i < TYPES.length; i++) {
            Bon.Type ty = TYPES[i];
            HzButton b = new HzButton(r + 12 + i * 65, top + 168, 62, 14, Component.literal(t("type." + ty.id())), Ui.OR, () -> {
                if (type != ty) {
                    type = ty;
                    nombre = "";
                    duree = "";
                    erreur = "";
                    rebuildWidgets();
                }
            });
            addRenderableWidget(b.look(HzButton.Look.PILL).choisi(ty == type));
        }
        int fy = top + 188;
        switch (type) {
            case HERALS, PB -> {
                HzField f = addRenderableWidget(new HzField(r + 12, fy, 140, 16, 10, HzField.NOMBRE).placeholder(t("montant"))
                        .suffix(type == Bon.Type.PB ? "PB" : t("herals")));
                f.setValue(nombre);
                f.setResponder(v -> nombre = v);
            }
            case GRADE -> {
                List<Grade> gs = grades();
                if (gradeChoisi >= gs.size()) gradeChoisi = 0;
                Component nomGrade = gs.isEmpty() ? Component.literal("-") : gs.get(gradeChoisi).nomSansCouleur();
                int cg = gs.isEmpty() ? Ui.MUTED : gs.get(gradeChoisi).couleur;
                addRenderableWidget(new HzButton(r + 12, fy, 150, 16, nomGrade, cg, () -> {
                    if (!gs.isEmpty()) gradeChoisi = (gradeChoisi + 1) % gs.size();
                    rebuildWidgets();
                }).accent(cg));
                HzField f = addRenderableWidget(new HzField(r + 170, fy, 84, 16, 8, s -> s.matches("[0-9\\p{L}]*")).placeholder(t("duree_grade_aide")));
                f.setValue(duree);
                f.setResponder(v -> duree = v);
            }
            case BONUS_LOOT -> {
                HzField p = addRenderableWidget(new HzField(r + 12, fy, 64, 16, 2, HzField.NOMBRE).placeholder("10").suffix("%"));
                p.setValue(nombre);
                p.setResponder(v -> nombre = v);
                HzField f = addRenderableWidget(new HzField(r + 84, fy, 100, 16, 8, s -> s.matches("[0-9\\p{L}]*")).placeholder(t("duree_bonus_aide")));
                f.setValue(duree);
                f.setResponder(v -> duree = v);
            }
            case COMMANDE -> {
                HzField f = addRenderableWidget(new HzField(r + 12, fy, 246, 16, Bon.MAX_COMMANDE, s -> s.indexOf('§') < 0)
                        .placeholder(t("commande_aide")).remplacerAuFocus(false));
                f.setValue(commande);
                f.setResponder(v -> commande = v);
            }
        }
        HzButton ajouter = addRenderableWidget(new HzButton(r + 266, fy, 72, 16, Component.literal(t("ajouter")), Ui.OR, this::ajouter));
        ajouter.active = effets.size() < Bon.MAX_EFFETS;
        addRenderableWidget(new HzButton(r + 12, top + 258, 326, 20, Component.literal(t("creer")), couleurAffichee(), this::creer)
                .look(HzButton.Look.PRIMARY));
    }

    private int couleurAffichee() {
        return couleur & 0xFFFFFF;
    }

    private static long lireNombre(String s) {
        try {
            return s.isEmpty() ? 0 : Long.parseLong(s);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private void ajouter() {
        Bon.Effet e = switch (type) {
            case HERALS, PB -> new Bon.Effet(type, lireNombre(nombre), "", "");
            case GRADE -> {
                List<Grade> gs = grades();
                yield new Bon.Effet(type, 0, gs.isEmpty() ? "" : gs.get(Math.min(gradeChoisi, gs.size() - 1)).id, duree.trim().toLowerCase(Locale.ROOT));
            }
            case BONUS_LOOT -> new Bon.Effet(type, lireNombre(nombre), "", duree.trim().toLowerCase(Locale.ROOT));
            case COMMANDE -> new Bon.Effet(type, 0, commande.trim().startsWith("/") ? commande.trim().substring(1) : commande.trim(), "");
        };
        String err = Bon.erreur(e);
        if (err != null) {
            erreur = t("erreur." + err);
            return;
        }
        if (effets.size() >= Bon.MAX_EFFETS) {
            erreur = t("erreur.trop_effets");
            return;
        }
        SonsUi.clic(0.6F);
        effets.add(e);
        erreur = "";
        nombre = "";
        duree = "";
        commande = "";
        rebuildWidgets();
    }

    private void creer() {
        Bon.Donnees d = donnees();
        String err = Bon.erreur(d);
        if (err != null) {
            message = err;
            messageOk = false;
            messageA = System.currentTimeMillis();
            return;
        }
        message = "";
        ReseauBon.CANAL.sendToServer(new ReseauBon.Creer(d));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        int l = ox + 20, top = oy + 54;
        for (int i = 0; i < PALETTE.length; i++) {
            int sx = l + 86 + (i % 7) * 16, sy = top + 110 + (i / 7) * 9;
            if (mx >= sx && mx < sx + 14 && my >= sy && my < sy + 7) {
                couleur = PALETTE[i];
                hexa = String.format(Locale.ROOT, "%06X", couleur);
                SonsUi.clic(0.6F);
                rebuildWidgets();
                return true;
            }
        }
        return super.mouseClicked(mx, my, b);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            // 1er Echap : quitte le champ en cours de saisie
            if (getFocused() instanceof HzField f && f.isFocused()) {
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
        // saisie pas encore transformee en bon : un 2e Echap dans les 3 s pour quitter
        if (!signature().equals(instantane) && ticks - confirmerA > 60) {
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
    private void texte(GuiGraphics g, Component c, int x, int y, int rgb) {
        g.drawString(font, c, x, y, Ui.argb(rgb, 255), false);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        Font f = font;
        Ui.fond(g, width, height);
        int l = ox + 20, r = ox + 270, top = oy + 54;

        // en-tête
        Component titre = Ui.lit(t("titre").toUpperCase(Locale.ROOT), Ui.BOLD);
        texte(g, titre, ox + 20, oy + 24, Ui.TEXT);
        Ui.spaced(g, f, t("sous_titre"), Ui.SMALL, ox + 20 + f.width(titre) + 12, oy + 26, Ui.argb(Ui.MUTED, 255), 1.0f);
        Ui.hairline(g, ox + 20, ox + 620, oy + 44, 0xFFFFFF, 60, false, true);

        // gauche
        Ui.glass(g, l, top, 240, 286);
        Ui.section(g, f, t("apercu"), l + 12, top + 10, 216);
        ItemStack st = apercu();
        Ui.soft(g, l + 12, top + 24, 22, 22, Ui.argb(0xFFFFFF, 10));
        Ui.softOutline(g, l + 13, top + 25, 20, 20, Ui.argb(0xFFFFFF, 30));
        g.renderItem(st, l + 15, top + 27);
        boolean survolApercu = mx >= l + 12 && mx < l + 34 && my >= top + 24 && my < top + 46;
        String n = nom.isBlank() ? t("nom_defaut") : nom.trim();
        texte(g, Ui.lit(Ui.ellipsis(f, n, Ui.BOLD, 180), Ui.BOLD), l + 42, top + 26, couleurAffichee());
        int ir = 0;
        for (int i = 0; i < Bon.RARETES.length; i++) if (Bon.RARETES[i].equals(rarete)) ir = i;
        texte(g, Ui.lit(t("rarete." + (rarete.isEmpty() ? "aucune" : rarete)), Ui.SMALL), l + 42, top + 38, Bon.COULEURS_RARETES[ir]);

        Ui.section(g, f, t("nom"), l + 12, top + 60, 216);
        Ui.section(g, f, t("couleur"), l + 12, top + 98, 216);
        for (int i = 0; i < PALETTE.length; i++) {
            int sx = l + 86 + (i % 7) * 16, sy = top + 110 + (i / 7) * 9;
            boolean choisi = PALETTE[i] == couleur;
            Ui.soft(g, sx, sy, 14, 7, Ui.argb(PALETTE[i], 255));
            if (choisi) Ui.softOutline(g, sx, sy, 14, 7, Ui.argb(0xFFFFFF, 230));
            else if (mx >= sx && mx < sx + 14 && my >= sy && my < sy + 7) Ui.softOutline(g, sx, sy, 14, 7, Ui.argb(0xFFFFFF, 90));
        }
        Ui.section(g, f, t("rarete"), l + 12, top + 140, 216);
        texte(g, Ui.lit(t("aide_op"), Ui.SMALL), l + 12, top + 250, Ui.FAINT);
        texte(g, Ui.lit(t("aide_modele"), Ui.SMALL), l + 12, top + 262, Ui.FAINT);

        // droite
        Ui.glass(g, r, top, 350, 286);
        Ui.section(g, f, t("effets", effets.size(), Bon.MAX_EFFETS), r + 12, top + 10, 326);
        if (effets.isEmpty()) texte(g, Ui.lit(t("aucun_effet"), Ui.SMALL), r + 14, top + 28, Ui.FAINT);
        for (int i = 0; i < effets.size(); i++) {
            Bon.Effet e = effets.get(i);
            int y = top + 24 + i * 16;
            Ui.soft(g, r + 10, y - 2, 330, 15, Ui.argb(0xFFFFFF, i % 2 == 0 ? 8 : 4));
            String ligne = Bon.texteEffet(e).getString();
            if (e.type() == Bon.Type.COMMANDE) ligne = "/" + e.valeur();
            texte(g, Ui.lit(Ui.ellipsis(f, ligne, Ui.FONT, 276), Ui.FONT), r + 16, y + 1, Ui.TEXT);
        }
        Ui.section(g, f, t("ajouter_effet"), r + 12, top + 156, 326);
        String aide = switch (type) {
            case HERALS -> t("aide.herals");
            case PB -> t("aide.pb");
            case GRADE -> t("aide.grade");
            case BONUS_LOOT -> t("aide.bonus_loot");
            case COMMANDE -> t("aide.commande");
        };
        texte(g, Ui.lit(Ui.ellipsis(f, aide, Ui.SMALL, 326), Ui.SMALL), r + 12, top + 210, Ui.MUTED);
        if (!erreur.isEmpty()) texte(g, Ui.lit(Ui.ellipsis(f, erreur, Ui.SMALL, 326), Ui.SMALL), r + 12, top + 222, Ui.DANGER);
        if (ticks - confirmerA <= 60) {
            String q = Component.translatable("gui.heraliz_grades.quitter_confirmer").getString();
            texte(g, Ui.lit(Ui.ellipsis(f, q, Ui.FONT, 326), Ui.FONT), r + 12, top + 242, Ui.DANGER);
        } else if (!message.isEmpty() && System.currentTimeMillis() - messageA < 8000) {
            String m = messageOk ? t("reponse." + message) : t("erreur." + message);
            texte(g, Ui.lit(Ui.ellipsis(f, m, Ui.FONT, 326), Ui.FONT), r + 12, top + 242, messageOk ? Ui.OK : Ui.DANGER);
        }

        super.render(g, mx, my, pt);
        if (survolApercu) g.renderTooltip(f, st, mx, my);
    }
}
