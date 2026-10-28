package fr.heraliz.grades.client;

import fr.heraliz.grades.Grade;
import fr.heraliz.grades.client.boutique.Trace;
import fr.heraliz.grades.client.boutique.Trace.P;
import fr.heraliz.grades.client.kit.Echelle;
import fr.heraliz.grades.client.kit.SonsUi;
import fr.heraliz.grades.reseau.ReseauGrades;
import fr.heraliz.grades.reseau.ReseauStaff;
import fr.heraliz.grades.staff.ActionStaff;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import static fr.heraliz.grades.client.boutique.Trace.a;

/**
 * Panel du staff (touche P, 25/10), maquettes {@code maquettes/staff_chat/1_} à {@code 3_} validées : canevas de 640 x 360
 * unités au style de la boutique (voile sombre sur le monde, verre, accent rouge). Onglets : Joueurs (liste des joueurs en
 * ligne, fiche du joueur choisi, actions sur lui, raison), Modération, Monde, Événements, Serveur (cartes d'actions en
 * trois colonnes), Historique (journal du staff) et Permissions (Fondateur seulement : ce que chaque grade peut faire).
 * <p>
 * L'écran n'affiche que ce que le serveur envoie (actions permises à ce grade) et ne fait que DEMANDER : droits, cible,
 * durée, raison, tout est revérifié par le serveur ({@code PanelStaff}). Les actions lourdes demandent deux clics.
 */
public final class EcranStaff extends Screen {
    // ------------------------------------------------------------------ mesures (maquette en 1920 x 1080 : divisées par 3)
    private static final int GW = 640, GH = 360, M = 16, HAUT = 80, BAS = 332;
    private static final int LX = 16, LW = 170, RX = 194, RW = 430;
    private static final int ACCENT = 0xE3201B, WHITE = 0xFFFFFF, LIGHT = 0xECEEF2, MUTED = 0x8F95A0, FAINT = 0x5A5F69, NIGHT = 0x050608,
            GLASS = 0x0D0F14, OK = 0x6FD08C, DANGER = 0xFF8A80, GOLD = 0xF2C14E;
    private static final int BLEU = 0x5B8DEF, VERT = 0x4CAF7A, ROUGE = 0xE5484D, ORANGE = 0xFF8A3D, TEAL = 0x5BC8D6, VIOLET = 0xB48CFF;
    private static final Map<String, Integer> COULEUR_SECTION = Map.ofEntries(Map.entry("moderation", BLEU), Map.entry("aide", VERT),
            Map.entry("chat", BLEU), Map.entry("moi", VERT), Map.entry("heure", TEAL), Map.entry("meteo", TEAL), Map.entry("extractions", TEAL),
            Map.entry("largages", VIOLET), Map.entry("boss", ROUGE), Map.entry("serveur", ORANGE), Map.entry("performances", BLEU),
            Map.entry("nettoyage", VERT));
    private static final String[] RAISONS = {"insultes", "spam", "pub", "comportement", "triche"};
    private static final Grade[] COLONNES = {Grade.GUIDE, Grade.HELPER, Grade.MODERATEUR, Grade.ADMIN, Grade.FONDATEUR};

    private static final P TITRE = Trace.p("bq_titre", 13, 1.93F), NOM = Trace.p("bq_titre", 13, 1.4F), ONGLET = Trace.p("bq_bouton", 9, 1.05F),
            BOUTON = Trace.p("bq_bouton", 9, 0.85F), CARTE = Trace.p("bq_bouton", 9, 0), TEXTE = Trace.p("bq_texte", 7.5F, 0),
            VALEUR = Trace.p("bq_texte_gras", 7.5F, 0), PETIT = Trace.p("bq_petit", 6.5F, 0), PETIT_GRAS = Trace.p("bq_mini", 6.5F, 0),
            MINI = Trace.p("bq_mini", 6.5F, 0.56F), MICRO = Trace.p("bq_micro", 5.6F, 0.4F);
    private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

    // ------------------------------------------------------------------ état
    private static final class Saisie {
        String v = "";
        final int max;

        Saisie(int max) {
            this.max = max;
        }
    }

    private record Zone(float x, float y, float w, float h, Runnable action) {
        boolean dedans(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private ReseauStaff.Etat etat;
    private ReseauStaff.Fiche fiche;
    private String onglet = "joueurs";
    private UUID choisi;
    private long ficheDemandee;
    private ActionStaff action;
    private final Map<ActionStaff, String> options = new EnumMap<>(ActionStaff.class);
    private final Saisie recherche = new Saisie(16), texte = new Saisie(ReseauStaff.MAX_TEXTE), rechercheCmd = new Saisie(24);
    /** Onglet « Commandes du Fondateur » : racines lues dans l'arbre des commandes reçu du serveur (cache), commande survolée. */
    private List<String> racinesCmd;
    private String rechercheCmdVue;
    private List<String> filtreesCmd = List.of();
    private String survolCmd, survolCmdAvant;
    private float defCmds;
    private Saisie focus;
    private ActionStaff arme;
    private long armeA;
    private boolean attente;
    private String gradeChoisi;
    private boolean retirerGrade;
    private float defListe, defHisto, defPerms;
    private String message = "";
    private boolean messageOk;
    private long messageA;
    private int ox, oy, sx, sy;
    private final List<Zone> zones = new ArrayList<>();

    private EcranStaff(ReseauStaff.Etat e) {
        super(Component.translatable("gui.heraliz_grades.staff.titre"));
        etat = e;
    }

    static void recevoir(ReseauStaff.Etat e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof EcranStaff s) {
            s.recevoirEtat(e);
        } else if (e.ouvrir()) {
            SonsUi.ouvrir(0.6F);
            mc.setScreen(new EcranStaff(e));
        }
    }

    static void recevoirFiche(ReseauStaff.Fiche f) {
        if (Minecraft.getInstance().screen instanceof EcranStaff s && f.uuid().equals(s.choisi)) s.fiche = f;
    }

    private void recevoirEtat(ReseauStaff.Etat e) {
        etat = e;
        attente = false;
        if (!e.message().isEmpty()) {
            message = e.message();
            messageOk = e.ok();
            messageA = System.currentTimeMillis();
            if (e.ok()) {
                SonsUi.ouvrir(0.7F);
                if (e.message().startsWith("fait|")) texte.v = "";
            }
        }
        // l'action choisie n'est plus permise (Fondateur qui change les droits) : on la lâche
        if (action != null && !permise(action)) action = null;
        if (choisi != null) ficheDemandee = 0;
    }

    // ------------------------------------------------------------------ textes
    private static String t(String cle, Object... args) {
        return Component.translatable("gui.heraliz_grades.staff." + cle, args).getString();
    }

    private static String nomAction(ActionStaff a) {
        return Component.translatable("heraliz_grades.staff.action." + a.id).getString();
    }

    private static String aideAction(ActionStaff a) {
        return Component.translatable("heraliz_grades.staff.action." + a.id + ".aide").getString();
    }

    private static String nomOption(String code) {
        return Component.translatable("heraliz_grades.staff.opt." + code).getString();
    }

    private static String nomGrade(String id) {
        Grade g = Grade.parId(id);
        return g != null ? g.nom().getString() : id;
    }

    private static int couleurGrade(String id) {
        Grade g = Grade.parId(id);
        return g != null ? g.couleur : MUTED;
    }

    /** Réponse du serveur : « clé|arg|arg » -> texte dans la langue du joueur. */
    private static String lireMessage(String m) {
        String[] p = m.split("\\|", -1);
        Object[] args = new Object[p.length - 1];
        for (int i = 1; i < p.length; i++) {
            ActionStaff a = ActionStaff.parId(p[i]);
            args[i - 1] = p[0].equals("fait") && a != null ? nomAction(a) : p[i];
        }
        return Component.translatable("heraliz_grades.staff.msg." + p[0], args).getString();
    }

    private boolean permise(ActionStaff a) {
        return etat.permises().contains(a.id);
    }

    private List<ActionStaff> permises(ActionStaff.Onglet o) {
        List<ActionStaff> l = new ArrayList<>();
        for (ActionStaff a : ActionStaff.values()) if (a.onglet == o && permise(a)) l.add(a);
        return l;
    }

    private List<String> onglets() {
        List<String> l = new ArrayList<>();
        l.add("joueurs");
        for (ActionStaff.Onglet o : ActionStaff.Onglet.values()) {
            if (o != ActionStaff.Onglet.JOUEURS && !permises(o).isEmpty()) l.add(o.name().toLowerCase(Locale.ROOT));
        }
        l.add("historique");
        if (!etat.masques().isEmpty()) {
            l.add("permissions");
            // 28/10 : commandes données à un grade, commande par commande (Fondateur seulement, comme Permissions)
            l.add("commandes");
        }
        return l;
    }

    // ------------------------------------------------------------------ mise en page
    @Override
    protected void init() {
        Echelle.forcer(this);
        ox = (width - GW) / 2;
        oy = (height - GH) / 2;
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

    /**
     * Découpe (liste, historique, permissions, commandes) en coordonnées du canevas. 28/10 : le {@code enableScissor} de
     * Minecraft 1.20.1 ne suit PAS la translation de la pose (ox, oy) : la découpe tombait à côté de la liste dès que la
     * fenêtre n'avait pas exactement 640 x 360 unités (1366 x 768, 1600 x 900, fenêtre 1080p avec sa barre de titre…) et
     * coupait les pseudos des joueurs en ligne (en partie ou en entier).
     */
    private void decoupe(GuiGraphics g, float x0, float y0, float x1, float y1) {
        g.enableScissor(Math.round(ox + x0), Math.round(oy + y0), Math.round(ox + x1), Math.round(oy + y1));
    }

    private void zone(float x, float y, float w, float h, Runnable r) {
        zones.add(new Zone(x, y, w, h, r));
    }

    private boolean survol(float x, float y, float w, float h) {
        double mx = sx - ox, my = sy - oy;
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void dire(String cle, boolean ok) {
        message = cle;
        messageOk = ok;
        messageA = System.currentTimeMillis();
    }

    // ------------------------------------------------------------------ actions
    private boolean armee(ActionStaff a) {
        return a == arme && System.currentTimeMillis() - armeA < 5000;
    }

    private String option(ActionStaff a) {
        if (a.options == null) return "";
        String o = options.get(a);
        return o != null && a.options.contains(o) ? o : a.options.get(0);
    }

    private boolean texteDemande(ActionStaff a) {
        return a.sanction() || a == ActionStaff.MESSAGE || a == ActionStaff.ANNONCE;
    }

    private void lancer(ActionStaff a) {
        if (a == null || attente || !permise(a)) return;
        if (a.cible && choisi == null) {
            dire("local|choisis_joueur", false);
            return;
        }
        if (texteDemande(a) && texte.v.isBlank()) {
            dire(a.sanction() ? "raison_vide" : "texte_vide", false);
            focus = texte;
            return;
        }
        if (a == ActionStaff.DONNER_GRADE && (gradeChoisi == null || !etat.grades().contains(gradeChoisi))) {
            dire("local|choisis_grade", false);
            return;
        }
        if (a.danger && !armee(a)) {
            arme = a;
            armeA = System.currentTimeMillis();
            SonsUi.clic(0.6F);
            return;
        }
        arme = null;
        attente = true;
        SonsUi.clic(0.8F);
        String opt = a == ActionStaff.DONNER_GRADE ? gradeChoisi : option(a);
        ReseauStaff.CANAL.sendToServer(new ReseauStaff.Agir(a.id, a.cible ? choisi : null, opt, texte.v.strip(), retirerGrade));
    }

    private void choisirJoueur(UUID u) {
        if (u.equals(choisi)) return;
        choisi = u;
        fiche = null;
        ficheDemandee = 0;
        arme = null;
        SonsUi.clic(0.5F);
    }

    // ------------------------------------------------------------------ entrées
    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        if (b == 0) {
            double x = mx - ox, y = my - oy;
            Saisie avant = focus;
            focus = null;
            for (int i = zones.size() - 1; i >= 0; i--) {
                if (zones.get(i).dedans(x, y)) {
                    zones.get(i).action().run();
                    return true;
                }
            }
            if (avant != null && focus == null) return true;
        }
        return super.mouseClicked(mx, my, b);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        double x = mx - ox, y = my - oy;
        float pas = (float) -delta * 15;
        if ("joueurs".equals(onglet) && x >= LX && x < LX + LW && y >= HAUT && y < BAS) defListe = Math.max(0, defListe + pas);
        else if ("historique".equals(onglet)) defHisto = Math.max(0, defHisto + pas);
        else if ("permissions".equals(onglet)) defPerms = Math.max(0, defPerms + pas);
        else if ("commandes".equals(onglet)) defCmds = Math.max(0, defCmds + pas);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            // Échap quitte d'abord le champ de saisie, puis le panel
            if (focus != null) {
                focus = null;
                return true;
            }
            return super.keyPressed(key, scan, mods);
        }
        if (focus != null) {
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (!focus.v.isEmpty()) focus.v = focus.v.substring(0, focus.v.offsetByCodePoints(focus.v.length(), -1));
                return true;
            }
            if (key == GLFW.GLFW_KEY_V && (mods & GLFW.GLFW_MOD_CONTROL) != 0) {
                String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
                if (clip != null) ajouter(clip.replace("\n", " ").replace("\r", ""));
                return true;
            }
        } else if (PanelClient.TOUCHE.matches(key, scan)) {
            onClose();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if ("joueurs".equals(onglet)) lancer(action);
            else if ("serveur".equals(onglet) && focus == texte && permise(ActionStaff.ANNONCE)) lancer(ActionStaff.ANNONCE);
            return true;
        }
        return focus != null || super.keyPressed(key, scan, mods);
    }

    private void ajouter(String s) {
        if (focus == null) return;
        StringBuilder b = new StringBuilder(focus.v);
        for (char c : s.toCharArray()) {
            if (c < 32 || c == '§' || c == '|' || b.length() >= focus.max) continue;
            b.append(c);
        }
        focus.v = b.toString();
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (focus == null || c < 32 || c == '§') return false;
        ajouter(String.valueOf(c));
        return true;
    }

    // ------------------------------------------------------------------ dessin commun
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        sx = mx;
        sy = my;
        zones.clear();
        voile(g);
        if (!onglets().contains(onglet)) onglet = "joueurs";
        g.pose().pushPose();
        g.pose().translate(ox, oy, 0);
        Font f = font;
        entete(g, f);
        switch (onglet) {
            case "joueurs" -> joueurs(g, f);
            case "historique" -> historique(g, f);
            case "permissions" -> permissions(g, f);
            case "commandes" -> commandes(g, f);
            default -> colonnes(g, f, ActionStaff.Onglet.valueOf(onglet.toUpperCase(Locale.ROOT)));
        }
        pied(g, f);
        g.pose().popPose();
    }

    private void voile(GuiGraphics g) {
        float[] at = {0, 0.24F, 0.46F, 0.62F, 1};
        int[] al = {235, 215, 185, 200, 232};
        for (int i = 0; i < at.length - 1; i++) {
            Trace.gradH(g, width * at[i], 0, width * at[i + 1], height, a(NIGHT, al[i]), a(NIGHT, al[i + 1]));
        }
        Trace.gradV(g, 0, 0, width, height * 0.3F, a(0, 90), a(0, 0));
        Trace.gradV(g, 0, height * 0.6F, width, height, a(0, 0), a(0, 120));
    }

    private static void verre(GuiGraphics g, float x, float y, float w, float h, int alpha) {
        Trace.rect(g, x, y, x + w, y + h, a(GLASS, alpha));
        Trace.bord(g, x, y, w, h, 1 / 3F, a(WHITE, 26));
        Trace.rect(g, x + 1 / 3F, y + 1 / 3F, x + w - 1 / 3F, y + 2 / 3F, a(WHITE, 41));
    }

    private void entete(GuiGraphics g, Font f) {
        float tw = Trace.texte(g, f, t("commandes".equals(onglet) ? "titre_commandes" : "titre"), M, 17, TITRE, a(LIGHT, 255));
        // badge du grade
        String gr = nomGrade(etat.grade());
        int c = couleurGrade(etat.grade());
        float bw = Trace.largeur(f, gr, PETIT_GRAS) + 10, bx = M + tw + 8;
        Trace.rect(g, bx, 16.5F, bx + bw, 27.5F, a(c, 40));
        Trace.bord(g, bx, 16.5F, bw, 11, 1 / 3F, a(c, 200));
        Trace.c(g, f, gr.toUpperCase(Locale.ROOT), bx + bw / 2, 22, PETIT_GRAS, a(c, 255), 0);
        String sous = "permissions".equals(onglet) ? t("sous_permissions")
                : "commandes".equals(onglet) ? (survolCmdAvant != null ? "/" + survolCmdAvant + " · " + descriptionCmd(survolCmdAvant) : t("sous_commandes"))
                : t("sous", etat.permises().size());
        Trace.texte(g, f, Trace.tenir(f, sous, TEXTE, GW - 2 * M), M, 35, TEXTE, a(MUTED, 255));
        float x = M;
        // 28/10 : marge des onglets réduite si la rangée ne tient pas (Fondateur : 8 onglets ; russe)
        List<String> ogs = onglets();
        float texteTotal = 0;
        for (String o : ogs) texteTotal += Trace.largeur(f, t("onglet." + o), ONGLET);
        float marge = Math.max(6, Math.min(16, (GW - 2 * M - 4 * (ogs.size() - 1) - texteTotal) / ogs.size()));
        for (String o : ogs) {
            String nom = t("onglet." + o);
            float w = Trace.largeur(f, nom, ONGLET) + marge;
            boolean actif = o.equals(onglet), hv = survol(x, 50, w, 20);
            Trace.rect(g, x, 50, x + w, 70, a(actif ? ACCENT : WHITE, actif ? 46 : hv ? 18 : 9));
            Trace.bord(g, x, 50, w, 20, 1 / 3F, a(actif ? ACCENT : WHITE, actif ? 160 : 30));
            Trace.c(g, f, nom, x + w / 2, 60, ONGLET, a(actif ? WHITE : hv ? LIGHT : MUTED, 255), 0);
            if (actif) Trace.rect(g, x + 4, 68, x + w - 4, 68 + 2 / 3F, a(ACCENT, 255));
            String cible = o;
            zone(x, 50, w, 20, () -> {
                if (!cible.equals(onglet)) {
                    onglet = cible;
                    arme = null;
                    focus = null;
                    SonsUi.clic(0.5F);
                }
            });
            x += w + 4;
        }
        Trace.rect(g, M, 74, GW - M, 74 + 1 / 3F, a(WHITE, 23));
    }

    private void pied(GuiGraphics g, Font f) {
        float x = M, cy = 348;
        String[][] touches = "permissions".equals(onglet) || "commandes".equals(onglet) ? new String[][]{{t("t_clic"), t("cocher")}, {t("t_echap"), t("fermer")}}
                : new String[][]{{"P", t("panel")}, {t("t_echap"), t("fermer")}, {t("t_entree"), t("valider")}};
        for (String[] k : touches) {
            float kw = Trace.largeur(f, k[0], PETIT_GRAS) + 6;
            Trace.bord(g, x, cy - 5, kw, 10, 1 / 3F, a(WHITE, 90));
            Trace.c(g, f, k[0], x + kw / 2, cy, PETIT_GRAS, a(LIGHT, 255), 0);
            x += kw + 4;
            x += Trace.m(g, f, k[1], x, cy, PETIT, a(MUTED, 255), 0) + 12;
        }
        long age = System.currentTimeMillis() - messageA;
        if (!message.isEmpty() && age < 7000) {
            String m = message.startsWith("local|") ? t(message.substring(6)) : lireMessage(message);
            int c = messageOk ? OK : DANGER;
            Trace.m(g, f, m, x + 8, cy, TEXTE, a(c, 255 * Math.min(1, (7000 - age) / 600F)), GW - M - x - 160);
        }
        Trace.d(g, f, t("pied", nomGrade(etat.grade()), etat.permises().size()), GW - M, cy, PETIT, a(FAINT, 255), 150);
    }

    private void champ(GuiGraphics g, Font f, Saisie s, float x, float y, float w, float h, String invite) {
        boolean actif = focus == s, hv = survol(x, y, w, h);
        Trace.rect(g, x, y, x + w, y + h, a(0, actif ? 150 : hv ? 115 : 90));
        Trace.bord(g, x, y, w, h, 1 / 3F, a(actif ? ACCENT : WHITE, actif ? 200 : hv ? 70 : 40));
        float tx = x + 5, cy = y + h / 2, place = w - 10;
        if (s.v.isEmpty()) Trace.m(g, f, invite, tx, cy, TEXTE, a(FAINT, 255), place);
        String vu = s.v;
        while (!vu.isEmpty() && Trace.largeur(f, vu, TEXTE) > place - 3) vu = vu.substring(1);
        float tw = vu.isEmpty() ? 0 : Trace.m(g, f, vu, tx, cy, TEXTE, a(LIGHT, 255), 0);
        if (actif && (System.currentTimeMillis() / 500) % 2 == 0) Trace.rect(g, tx + tw + 1, cy - 4, tx + tw + 1 + 2 / 3F, cy + 4, a(ACCENT, 230));
        zone(x, y, w, h, () -> focus = s);
    }

    /** Petites puces de choix (durées, raisons, grades…) ; rend la fin en x. */
    private float puces(GuiGraphics g, Font f, float x, float cy, float maxX, List<String> codes, String choisi, Function<String, String> libelle,
                        Function<String, Integer> couleur, Consumer<String> choisir) {
        for (String code : codes) {
            String nom = libelle.apply(code);
            float w = Trace.largeur(f, nom, PETIT_GRAS) + 8;
            if (x + w > maxX) break;
            boolean sel = code.equals(choisi), hv = survol(x, cy - 5.5F, w, 11);
            int c = couleur.apply(code);
            Trace.rect(g, x, cy - 5.5F, x + w, cy + 5.5F, a(sel ? c : WHITE, sel ? 60 : hv ? 22 : 10));
            Trace.bord(g, x, cy - 5.5F, w, 11, 1 / 3F, a(sel ? c : WHITE, sel ? 220 : 60));
            Trace.c(g, f, nom, x + w / 2, cy, PETIT_GRAS, a(sel ? WHITE : LIGHT, 255), 0);
            zone(x, cy - 5.5F, w, 11, () -> {
                choisir.accept(code);
                SonsUi.clic(0.4F);
            });
            x += w + 4;
        }
        return x;
    }

    /** Carte d'action : titre, aide (ou « clique encore pour confirmer »), couleur de sa section (rouge si lourde). */
    private void carte(GuiGraphics g, Font f, float x, float y, float w, float h, ActionStaff a, boolean choisie, Runnable clic) {
        int c = a.danger ? ROUGE : COULEUR_SECTION.getOrDefault(a.section, BLEU);
        boolean hv = survol(x, y, w, h), ar = armee(a);
        Trace.gradH(g, x, y, x + w, y + h, a(c, choisie || ar ? 70 : hv ? 48 : 30), a(c, choisie || ar ? 30 : hv ? 20 : 10));
        Trace.bord(g, x, y, w, h, choisie || ar ? 2 / 3F : 1 / 3F, a(c, choisie || ar ? 255 : hv ? 170 : 110));
        float ty = h >= 30 ? y + 10 : y + h / 2 - 3.5F;
        Trace.m(g, f, nomAction(a), x + 8, ty, CARTE, a(WHITE, 255), w - 16);
        String sous = ar ? t("confirmer") : aideAction(a);
        Trace.m(g, f, sous, x + 8, ty + (h >= 30 ? 11 : 8.5F), PETIT, a(ar ? DANGER : MUTED, 255), w - 16);
        zone(x, y, w, h, clic);
    }

    // ------------------------------------------------------------------ onglet Joueurs
    private List<PlayerInfo> joueursEnLigne() {
        List<PlayerInfo> l = new ArrayList<>();
        if (Minecraft.getInstance().getConnection() == null) return l;
        String q = recherche.v.strip().toLowerCase(Locale.ROOT);
        // joueurs listés seulement (comme le tab) : pas les entrées cachées que d'autres mods ou plugins ajoutent pour des skins
        for (PlayerInfo p : Minecraft.getInstance().getConnection().getListedOnlinePlayers()) {
            String n = p.getProfile().getName();
            if (n == null || n.isBlank()) continue;
            if (q.isEmpty() || n.toLowerCase(Locale.ROOT).contains(q)) l.add(p);
        }
        l.sort((x, y) -> x.getProfile().getName().compareToIgnoreCase(y.getProfile().getName()));
        return l;
    }

    private void joueurs(GuiGraphics g, Font f) {
        List<PlayerInfo> liste = joueursEnLigne();
        Set<UUID> enLigne = new HashSet<>();
        if (Minecraft.getInstance().getConnection() != null) {
            for (PlayerInfo p : Minecraft.getInstance().getConnection().getListedOnlinePlayers()) enLigne.add(p.getProfile().getId());
        }
        if (choisi != null && !enLigne.contains(choisi)) {
            choisi = null;
            fiche = null;
        }
        if (choisi != null && System.currentTimeMillis() - ficheDemandee > 2000) {
            ficheDemandee = System.currentTimeMillis();
            ReseauStaff.CANAL.sendToServer(new ReseauStaff.DemandeFiche(choisi));
        }
        // ---- liste
        verre(g, LX, HAUT, LW, BAS - HAUT, 199);
        Trace.texte(g, f, t("en_ligne"), LX + 8, HAUT + 7, MINI, a(MUTED, 255));
        Trace.d(g, f, String.valueOf(enLigne.size()), LX + LW - 8, HAUT + 9.5F, PETIT_GRAS, a(LIGHT, 255), 0);
        champ(g, f, recherche, LX + 8, HAUT + 18, LW - 16, 14, t("rechercher"));
        float y0 = HAUT + 38, hLigne = 15, visibles = (BAS - 6 - y0) / hLigne;
        defListe = Math.min(defListe, Math.max(0, (liste.size() - visibles) * hLigne));
        Set<UUID> muets = new HashSet<>(etat.muets());
        decoupe(g, LX + 1, y0, LX + LW - 1, BAS - 4);
        for (int i = 0; i < liste.size(); i++) {
            float y = y0 + i * hLigne - defListe;
            if (y + hLigne < y0 || y > BAS) continue;
            PlayerInfo p = liste.get(i);
            UUID u = p.getProfile().getId();
            boolean sel = u.equals(choisi), hv = survol(LX + 4, y, LW - 8, hLigne);
            if (sel) {
                Trace.rect(g, LX + 4, y, LX + LW - 4, y + hLigne, a(ACCENT, 50));
                Trace.rect(g, LX + 4, y, LX + 5, y + hLigne, a(ACCENT, 255));
            } else if (hv) {
                Trace.rect(g, LX + 4, y, LX + LW - 4, y + hLigne, a(WHITE, 14));
            }
            float x = LX + 10, cy = y + hLigne / 2;
            ReseauGrades.LigneTab info = TabHeraliz.info(u);
            if (info != null && !info.clan().isEmpty()) {
                x += Trace.m(g, f, "[" + info.clan() + "]", x, cy, VALEUR, a(info.couleurClan() == 0 ? BLEU : info.couleurClan(), 255), 0) + 3;
            }
            int ping = p.getLatency();
            String ms = t("ms", ping);
            float pw = Trace.largeur(f, ms, PETIT_GRAS);
            float droite = LX + LW - 10 - pw;
            Trace.d(g, f, ms, LX + LW - 10, cy, PETIT_GRAS, a(ping < 60 ? OK : ping < 150 ? GOLD : DANGER, 255), 0);
            if (muets.contains(u)) {
                float mw = Trace.largeur(f, t("muet"), PETIT_GRAS);
                Trace.d(g, f, t("muet"), droite - 6, cy, PETIT_GRAS, a(GOLD, 255), 0);
                droite -= mw + 6;
            }
            if (info != null && info.cache()) {
                Trace.d(g, f, t("cache"), droite - 6, cy, PETIT_GRAS, a(VIOLET, 255), 0);
                droite -= Trace.largeur(f, t("cache"), PETIT_GRAS) + 6;
            }
            // largeur jamais nulle ou négative (Trace.tenir ne coupe rien en dessous de 0 : le pseudo passait sous le ping)
            Trace.m(g, f, p.getProfile().getName(), x, cy, TEXTE, a(sel ? WHITE : LIGHT, 255), Math.max(14, droite - x - 6));
            if (y >= y0 - 1 && y + hLigne <= BAS - 3) zone(LX + 4, y, LW - 8, hLigne, () -> choisirJoueur(u));
        }
        g.disableScissor();
        if (liste.isEmpty()) Trace.c(g, f, t("personne"), LX + LW / 2F, y0 + 20, PETIT, a(FAINT, 255), LW - 16);
        // ---- fiche
        verre(g, RX, HAUT, RW, 51, 199);
        if (choisi == null) {
            Trace.m(g, f, t("choisis"), RX + 12, HAUT + 25.5F, TEXTE, a(MUTED, 255), RW - 24);
        } else {
            ficheJoueur(g, f, liste);
        }
        // ---- actions
        List<ActionStaff> moderation = new ArrayList<>(), aide = new ArrayList<>();
        for (ActionStaff a : permises(ActionStaff.Onglet.JOUEURS)) ("moderation".equals(a.section) ? moderation : aide).add(a);
        float y = 139;
        float cw = (RW - 3 * 5) / 4F;
        for (List<ActionStaff> groupe : List.of(moderation, aide)) {
            if (groupe.isEmpty()) continue;
            Trace.texte(g, f, t("section." + groupe.get(0).section), RX, y, MINI, a(MUTED, 255));
            y += 8;
            for (int i = 0; i < groupe.size(); i++) {
                ActionStaff a = groupe.get(i);
                float x = RX + (i % 4) * (cw + 5), yy = y + (i / 4) * 25;
                carte(g, f, x, yy, cw, 22, a, a == action, () -> {
                    if (action != a) {
                        action = a;
                        arme = null;
                        SonsUi.clic(0.5F);
                        if (texteDemande(a)) focus = texte;
                    }
                });
            }
            y += ((groupe.size() + 3) / 4) * 25 + 6;
        }
        basJoueurs(g, f);
    }

    private void ficheJoueur(GuiGraphics g, Font f, List<PlayerInfo> liste) {
        String nom = fiche != null ? fiche.nom() : "";
        if (nom.isEmpty()) {
            for (PlayerInfo p : liste) if (p.getProfile().getId().equals(choisi)) nom = p.getProfile().getName();
        }
        float x = RX + 12 + Trace.texte(g, f, nom, RX + 12, HAUT + 9, NOM, a(WHITE, 255));
        ReseauGrades.LigneTab info = TabHeraliz.info(choisi);
        if (info != null && !info.clan().isEmpty()) {
            Trace.texte(g, f, "[" + info.clan() + "]", x + 5, HAUT + 10, VALEUR, a(info.couleurClan() == 0 ? BLEU : info.couleurClan(), 255));
        }
        if (fiche != null && !fiche.gradeStaff().isEmpty()) {
            Trace.d(g, f, nomGrade(fiche.gradeStaff()), RX + RW - 12, HAUT + 13.5F, PETIT_GRAS, a(couleurGrade(fiche.gradeStaff()), 255), 0);
        } else if (fiche != null && !fiche.gradeBoutique().isEmpty()) {
            Trace.d(g, f, nomGrade(fiche.gradeBoutique()), RX + RW - 12, HAUT + 13.5F, PETIT_GRAS, a(couleurGrade(fiche.gradeBoutique()), 255), 0);
        }
        if (fiche == null) return;
        String[][] col = {
                {t("f_monde"), Component.translatableWithFallback("heraliz_grades.staff.monde." + fiche.monde().replace(':', '.'), fiche.monde()).getString()
                        + " · " + fiche.x() + " " + fiche.y() + " " + fiche.z()},
                {t("f_en_jeu"), duree(fiche.enJeuMs())},
                {t("f_sanctions"), fiche.sanctions() == 0 ? t("aucune") : t("sanctions_n", fiche.sanctions())},
                {t("f_combat"), fiche.combatMs() > 0 ? t("oui_s", (fiche.combatMs() + 999) / 1000) : t("non")}};
        float[] cx = {RX + 12, RX + 168, RX + 248, RX + 340};
        for (int i = 0; i < col.length; i++) {
            float w = (i + 1 < cx.length ? cx[i + 1] : RX + RW - 12) - cx[i] - 8;
            Trace.texte(g, f, Trace.tenir(f, col[i][0], MICRO, w), cx[i], HAUT + 29, MICRO, a(MUTED, 255));
            int c = i == 3 && fiche.combatMs() > 0 ? DANGER : i == 2 && fiche.sanctions() > 0 ? GOLD : LIGHT;
            Trace.texte(g, f, Trace.tenir(f, col[i][1], VALEUR, w), cx[i], HAUT + 39, VALEUR, a(c, 255));
        }
    }

    private String duree(long ms) {
        long min = ms / 60_000;
        if (min < 60) return t("duree_min", min);
        return t("duree_h", min / 60, String.format(Locale.ROOT, "%02d", min % 60));
    }

    private void basJoueurs(GuiGraphics g, Font f) {
        float yb = 304, bh = 22, bw = 128, bx = RX + RW - bw;
        if (action == null) {
            Trace.m(g, f, t("choisis_action"), RX, yb + bh / 2, PETIT, a(FAINT, 255), RW - bw - 10);
            bouton(g, f, bx, yb, bw, bh, t("valider_vide"), false, null);
            return;
        }
        ActionStaff a = action;
        if (a == ActionStaff.DONNER_GRADE) {
            Trace.texte(g, f, t("grade"), RX, 263, MINI, a(MUTED, 255));
            puces(g, f, RX, 278, RX + RW, etat.grades(), gradeChoisi, EcranStaff::nomGrade, EcranStaff::couleurGrade, c -> gradeChoisi = c);
            Trace.m(g, f, t("grade_note"), RX, 293, PETIT, a(FAINT, 255), RW);
            puces(g, f, RX, yb + bh / 2, bx - 8, List.of("donner", "retirer"), retirerGrade ? "retirer" : "donner", c -> t(c),
                    c -> c.equals("retirer") ? ROUGE : VERT, c -> retirerGrade = c.equals("retirer"));
        } else {
            if (texteDemande(a)) {
                Trace.texte(g, f, t(a.sanction() ? "raison" : "message"), RX, 263, MINI, a(MUTED, 255));
                champ(g, f, texte, RX, 270, RW, 14, t(a.sanction() ? "raison_invite" : "message_invite"));
                if (a.sanction()) {
                    List<String> codes = List.of(RAISONS);
                    puces(g, f, RX, 293, RX + RW, codes, "", c -> Component.translatable("heraliz_grades.staff.raison." + c).getString(), c -> MUTED,
                            c -> texte.v = Component.translatable("heraliz_grades.staff.raison." + c).getString());
                }
            } else {
                Trace.m(g, f, aideAction(a), RX, 277, TEXTE, a(MUTED, 255), RW);
            }
            float x = RX;
            if (a.options != null) {
                x += Trace.m(g, f, t("duree"), x, yb + bh / 2, PETIT_GRAS, a(MUTED, 255), 0) + 6;
                x = puces(g, f, x, yb + bh / 2, bx - 8, a.options, option(a), EcranStaff::nomOption, c -> a.danger ? ROUGE : BLEU,
                        c -> options.put(a, c));
            } else {
                Trace.m(g, f, t("note"), x, yb + bh / 2, PETIT, a(FAINT, 255), bx - x - 10);
            }
        }
        String libelle;
        if (attente) libelle = t("attente");
        else if (armee(a)) libelle = t("bouton_confirmer");
        else if (a == ActionStaff.DONNER_GRADE) libelle = t(retirerGrade ? "bouton_retirer" : "bouton_donner", gradeChoisi == null ? "…" : nomGrade(gradeChoisi));
        else libelle = nomAction(a) + (a.options != null ? " " + nomOption(option(a)) : "");
        bouton(g, f, bx, yb, bw, bh, libelle, !attente, () -> lancer(a));
    }

    private void bouton(GuiGraphics g, Font f, float x, float y, float w, float h, String libelle, boolean actif, Runnable r) {
        boolean hv = actif && survol(x, y, w, h);
        Trace.gradH(g, x, y, x + w, y + h, a(ACCENT, actif ? (hv ? 150 : 110) : 30), a(ACCENT, actif ? (hv ? 90 : 60) : 15));
        Trace.bord(g, x, y, w, h, 1 / 3F, a(ACCENT, actif ? (hv ? 255 : 200) : 60));
        Trace.c(g, f, libelle, x + w / 2, y + h / 2, BOUTON, a(actif ? WHITE : MUTED, 255), w - 10);
        if (actif && r != null) zone(x, y, w, h, r);
    }

    // ------------------------------------------------------------------ onglets en colonnes
    private void colonnes(GuiGraphics g, Font f, ActionStaff.Onglet o) {
        Map<String, List<ActionStaff>> sections = new java.util.LinkedHashMap<>();
        for (ActionStaff a : permises(o)) sections.computeIfAbsent(a.section, k -> new ArrayList<>()).add(a);
        float cw = 197, top = HAUT, hCol = 202;
        int i = 0;
        for (Map.Entry<String, List<ActionStaff>> e : sections.entrySet()) {
            if (i >= 3) break;
            float x = M + i * (cw + 8.5F);
            verre(g, x, top, cw, hCol, 199);
            Trace.texte(g, f, t("section." + e.getKey()), x + 10, top + 9, MINI, a(MUTED, 255));
            float y = top + 20;
            for (ActionStaff a : e.getValue()) {
                // carte à choix (durée, type, boss) : plus haute, les puces sous le titre et l'aide
                float h = a.options != null ? 40 : 32;
                if (y + h > top + hCol - 4) break;
                carte(g, f, x + 8, y, cw - 16, h, a, false, () -> lancer(a));
                if (a.options != null) {
                    puces(g, f, x + 16, y + 33, x + cw - 16, a.options, option(a), EcranStaff::nomOption, c -> a.danger ? ROUGE : BLEU,
                            c -> options.put(a, c));
                }
                y += h + 6;
            }
            // texte de l'annonce et de la raison du redémarrage : sous les cartes de la colonne « serveur »
            if ("serveur".equals(e.getKey()) && (permise(ActionStaff.ANNONCE) || permise(ActionStaff.REDEMARRAGE)) && y + 24 < top + hCol) {
                Trace.texte(g, f, t("texte_annonce"), x + 10, y + 2, MINI, a(MUTED, 255));
                champ(g, f, texte, x + 8, y + 9, cw - 16, 14, t("texte_invite"));
            }
            i++;
        }
        // dernières actions du staff
        float by = top + hCol + 6;
        verre(g, M, by, GW - 2 * M, BAS - by, 199);
        Trace.texte(g, f, t("dernieres"), M + 10, by + 8, MINI, a(MUTED, 255));
        List<ReseauStaff.Ligne> h = etat.historique();
        for (int k = 0; k < Math.min(3, h.size()); k++) ligneHisto(g, f, h.get(k), M + 10, by + 22 + k * 11, GW - 2 * M - 20);
        if (h.isEmpty()) Trace.m(g, f, t("histo_vide"), M + 10, by + 22, PETIT, a(FAINT, 255), GW - 2 * M - 20);
    }

    private void ligneHisto(GuiGraphics g, Font f, ReseauStaff.Ligne l, float x, float cy, float w) {
        float x0 = x;
        x += Trace.m(g, f, HEURE.format(Instant.ofEpochMilli(l.ms())), x, cy, PETIT_GRAS, a(LIGHT, 255), 0) + 6;
        x += Trace.m(g, f, l.staff(), x, cy, PETIT_GRAS, a(couleurGrade(l.grade()), 255), 0) + 3;
        x += Trace.m(g, f, "(" + nomGrade(l.grade()) + ")", x, cy, PETIT, a(MUTED, 255), 0) + 5;
        ActionStaff a = ActionStaff.parId(l.action());
        // actions sans carte (28/10 : commande donnée à un grade, commande utilisée) : nom dans les langues du mod
        StringBuilder s = new StringBuilder(a != null ? nomAction(a)
                : Component.translatableWithFallback("heraliz_grades.staff.histo." + l.action(), l.action()).getString());
        if (!l.cible().isEmpty()) s.append(" · ").append(l.cible());
        if (!l.detail().isEmpty()) s.append(" · ").append(l.detail());
        if (!l.raison().isEmpty()) s.append(" — « ").append(l.raison()).append(" »");
        Trace.m(g, f, s.toString(), x, cy, PETIT, a(LIGHT, 255), x0 + w - x);
    }

    // ------------------------------------------------------------------ historique
    private void historique(GuiGraphics g, Font f) {
        verre(g, M, HAUT, GW - 2 * M, BAS - HAUT, 199);
        Trace.texte(g, f, t("histo_titre"), M + 10, HAUT + 8, MINI, a(MUTED, 255));
        List<ReseauStaff.Ligne> h = etat.historique();
        float y0 = HAUT + 22, pas = 12;
        float visibles = (BAS - 6 - y0) / pas;
        defHisto = Math.min(defHisto, Math.max(0, (h.size() - visibles) * pas));
        decoupe(g, M + 1, y0 - 2, GW - M - 1, BAS - 4);
        for (int i = 0; i < h.size(); i++) {
            float cy = y0 + i * pas + pas / 2 - defHisto;
            if (cy < y0 - pas || cy > BAS) continue;
            if (i % 2 == 0) Trace.rect(g, M + 4, cy - pas / 2, GW - M - 4, cy + pas / 2, a(WHITE, 6));
            ligneHisto(g, f, h.get(i), M + 10, cy, GW - 2 * M - 20);
        }
        g.disableScissor();
        if (h.isEmpty()) Trace.m(g, f, t("histo_vide"), M + 10, y0 + 6, PETIT, a(FAINT, 255), GW - 2 * M - 20);
    }

    // ------------------------------------------------------------------ permissions (Fondateur)
    private void permissions(GuiGraphics g, Font f) {
        verre(g, M, HAUT, GW - 2 * M, BAS - HAUT, 199);
        float[] cx = {300, 364, 428, 492, 556};
        for (int r = 0; r < COLONNES.length; r++) {
            Trace.c(g, f, COLONNES[r].nom().getString().toUpperCase(Locale.ROOT), cx[r], HAUT + 11, PETIT_GRAS, a(COLONNES[r].couleur, 255), 60);
        }
        float y0 = HAUT + 22, pas = 16;
        ActionStaff[] toutes = ActionStaff.values();
        float visibles = (BAS - 6 - y0) / pas;
        defPerms = Math.min(defPerms, Math.max(0, (toutes.length - visibles) * pas));
        decoupe(g, M + 1, y0, GW - M - 1, BAS - 4);
        String ongletVu = null;
        for (int i = 0; i < toutes.length; i++) {
            ActionStaff a = toutes[i];
            float y = y0 + i * pas - defPerms, cy = y + pas / 2;
            if (y + pas < y0 || y > BAS) continue;
            if (i % 2 == 0) Trace.rect(g, M + 4, y, GW - M - 4, y + pas, a(WHITE, 7));
            String og = t("onglet." + a.onglet.name().toLowerCase(Locale.ROOT));
            float nx = M + 12;
            if (!og.equals(ongletVu)) {
                Trace.m(g, f, og, nx, cy, MICRO, a(FAINT, 255), 60);
                ongletVu = og;
            }
            Trace.m(g, f, nomAction(a), nx + 64, cy, TEXTE, a(LIGHT, 255), cx[0] - nx - 64 - 30);
            int masque = etat.masques().getOrDefault(a.id, 0);
            for (int r = 0; r < COLONNES.length; r++) {
                int rang = r + 1;
                boolean coche = a.verrou ? rang == ActionStaff.RANG_MAX : (masque & 1 << rang) != 0;
                boolean fixe = a.verrou || rang == ActionStaff.RANG_MAX;
                int c = COLONNES[r].couleur;
                float bx = cx[r] - 5, by = cy - 5;
                boolean hv = !fixe && survol(bx - 3, by - 3, 16, 16);
                Trace.rect(g, bx, by, bx + 10, by + 10, a(coche ? c : WHITE, coche ? (fixe ? 40 : 70) : hv ? 25 : 10));
                Trace.bord(g, bx, by, 10, 10, 1 / 3F, a(coche ? c : WHITE, coche ? (fixe ? 120 : 230) : hv ? 120 : 60));
                if (coche) Trace.coche(g, bx + 2, by + 2, 6, 1.1F, a(WHITE, fixe ? 150 : 255));
                if (!fixe && y >= y0 - 1 && y + pas <= BAS - 3) {
                    zone(bx - 3, by - 3, 16, 16, () -> {
                        boolean apres = (etat.masques().getOrDefault(a.id, 0) & 1 << rang) == 0;
                        Map<String, Integer> m = new HashMap<>(etat.masques());
                        m.put(a.id, apres ? m.getOrDefault(a.id, 0) | 1 << rang : m.getOrDefault(a.id, 0) & ~(1 << rang));
                        etat = new ReseauStaff.Etat(false, etat.rang(), etat.grade(), etat.permises(), m, etat.grades(), etat.historique(), etat.muets(), "",
                                true, etat.commandes());
                        SonsUi.clic(0.5F);
                        ReseauStaff.CANAL.sendToServer(new ReseauStaff.Permission(a.id, rang, apres));
                    });
                }
            }
        }
        g.disableScissor();
    }

    // ------------------------------------------------------------------ commandes du Fondateur (28/10)
    /** Description d'une commande : texte des langues du mod, sinon texte générique (plugin ou mod inconnu). */
    private static String descriptionCmd(String racine) {
        return Component.translatableWithFallback("heraliz_grades.staff.cmd." + racine, t(racine.startsWith("/") ? "cmd_worldedit" : "cmd_inconnue"))
                .getString();
    }

    /**
     * Toutes les commandes du serveur : racines de l'arbre des commandes que le serveur envoie (le Fondateur est op 4 : il
     * les reçoit toutes, plugins compris), sans les doublons à espace de noms ({@code minecraft:tp}), plus celles déjà
     * données à un grade (plugin retiré depuis) pour pouvoir les décocher.
     */
    private List<String> racinesCmd() {
        if (racinesCmd == null) {
            java.util.TreeSet<String> s = new java.util.TreeSet<>();
            var co = Minecraft.getInstance().getConnection();
            if (co != null) {
                for (var n : co.getCommands().getRoot().getChildren()) {
                    String nom = n.getName();
                    if (nom.indexOf(':') >= 0) continue;
                    String r = fr.heraliz.grades.staff.CommandesStaff.racine(nom);
                    if (r.equals(nom)) s.add(r);
                }
            }
            s.addAll(etat.commandes().keySet());
            racinesCmd = new ArrayList<>(s);
            rechercheCmdVue = null;
        }
        String q = rechercheCmd.v.strip().toLowerCase(Locale.ROOT);
        if (!q.equals(rechercheCmdVue)) {
            rechercheCmdVue = q;
            List<String> l = new ArrayList<>();
            for (String r : racinesCmd) {
                if (q.isEmpty() || r.contains(q) || descriptionCmd(r).toLowerCase(Locale.ROOT).contains(q)) l.add(r);
            }
            filtreesCmd = l;
        }
        return filtreesCmd;
    }

    private void commandes(GuiGraphics g, Font f) {
        verre(g, M, HAUT, GW - 2 * M, BAS - HAUT, 199);
        survolCmdAvant = survolCmd;
        survolCmd = null;
        // colonnes : Guide, Helpeur, Modérateur, Admin, Fondateur (toujours tout : op 4)
        float[] cx = {388, 440, 492, 544, 596};
        champ(g, f, rechercheCmd, M + 8, HAUT + 4, 150, 13, t("cmd_rechercher"));
        List<String> liste = racinesCmd();
        Trace.m(g, f, t("cmd_nombre", liste.size()), M + 166, HAUT + 10.5F, PETIT, a(FAINT, 255), cx[0] - 26 - M - 166 - 4);
        for (int r = 0; r < COLONNES.length; r++) {
            Trace.c(g, f, COLONNES[r].nom().getString().toUpperCase(Locale.ROOT), cx[r], HAUT + 10.5F, PETIT_GRAS, a(COLONNES[r].couleur, 255), 50);
        }
        float y0 = HAUT + 22, pas = 16, nx = M + 12, dx = M + 100;
        float visibles = (BAS - 6 - y0) / pas;
        defCmds = Math.min(defCmds, Math.max(0, (liste.size() - visibles) * pas));
        decoupe(g, M + 1, y0, GW - M - 1, BAS - 4);
        for (int i = 0; i < liste.size(); i++) {
            String racine = liste.get(i);
            float y = y0 + i * pas - defCmds, cy = y + pas / 2;
            if (y + pas < y0 || y > BAS) continue;
            boolean dedans = y >= y0 - 1 && y + pas <= BAS - 3;
            boolean hvLigne = dedans && survol(M + 4, y, GW - 2 * M - 8, pas);
            if (hvLigne) survolCmd = racine;
            if (hvLigne) Trace.rect(g, M + 4, y, GW - M - 4, y + pas, a(WHITE, 16));
            else if (i % 2 == 0) Trace.rect(g, M + 4, y, GW - M - 4, y + pas, a(WHITE, 7));
            boolean donnable = fr.heraliz.grades.staff.CommandesStaff.donnable(racine);
            Trace.m(g, f, "/" + racine, nx, cy, VALEUR, a(donnable ? LIGHT : MUTED, 255), dx - nx - 6);
            Trace.m(g, f, descriptionCmd(racine), dx, cy, PETIT, a(MUTED, 255), cx[0] - 30 - dx);
            if (!donnable) {
                // jamais donnable (op, execute, permissions…) : cadenas à la place des cases des grades
                Trace.c(g, f, t("cmd_bloquee"), (cx[0] + cx[3]) / 2, cy, PETIT, a(FAINT, 255), cx[3] - cx[0] + 40);
            }
            int masque = etat.commandes().getOrDefault(racine, 0);
            for (int r = 0; r < COLONNES.length; r++) {
                int rang = r + 1;
                boolean fonda = rang == ActionStaff.RANG_MAX;
                if (!donnable && !fonda) continue;
                boolean coche = fonda || (masque & 1 << rang) != 0;
                int c = COLONNES[r].couleur;
                float bx = cx[r] - 5, by = cy - 5;
                boolean hv = !fonda && dedans && survol(bx - 3, by - 3, 16, 16);
                Trace.rect(g, bx, by, bx + 10, by + 10, a(coche ? c : WHITE, coche ? (fonda ? 40 : 70) : hv ? 25 : 10));
                Trace.bord(g, bx, by, 10, 10, 1 / 3F, a(coche ? c : WHITE, coche ? (fonda ? 120 : 230) : hv ? 120 : 60));
                if (coche) Trace.coche(g, bx + 2, by + 2, 6, 1.1F, a(WHITE, fonda ? 150 : 255));
                if (!fonda && dedans) {
                    zone(bx - 3, by - 3, 16, 16, () -> {
                        int avant = etat.commandes().getOrDefault(racine, 0);
                        boolean apres = (avant & 1 << rang) == 0;
                        Map<String, Integer> m = new HashMap<>(etat.commandes());
                        int nouveau = apres ? avant | 1 << rang : avant & ~(1 << rang);
                        if (nouveau == 0) m.remove(racine);
                        else m.put(racine, nouveau);
                        etat = new ReseauStaff.Etat(false, etat.rang(), etat.grade(), etat.permises(), etat.masques(), etat.grades(), etat.historique(),
                                etat.muets(), "", true, m);
                        SonsUi.clic(0.5F);
                        ReseauStaff.CANAL.sendToServer(new ReseauStaff.PermissionCommande(racine, rang, apres));
                    });
                }
            }
        }
        g.disableScissor();
        if (liste.isEmpty()) Trace.m(g, f, t("cmd_aucune"), M + 12, y0 + 8, PETIT, a(FAINT, 255), GW - 2 * M - 24);
    }
}
