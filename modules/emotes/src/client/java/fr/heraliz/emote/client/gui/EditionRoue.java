package fr.heraliz.emote.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import fr.heraliz.emote.client.DeblocagesClient;
import fr.heraliz.emote.client.gui.kit.Echelle;
import fr.heraliz.emote.client.gui.kit.HzField;
import fr.heraliz.emote.client.gui.kit.Net;
import fr.heraliz.emote.client.gui.kit.SonsUi;
import fr.heraliz.emote.client.gui.kit.Ui;
import io.github.kosmx.emotes.api.events.client.ClientEmoteAPI;
import io.github.kosmx.emotes.executor.EmoteInstance;
import io.github.kosmx.emotes.main.config.ClientConfig;
import io.github.kosmx.emotes.main.config.ClientSerializer;
import io.github.kosmx.emotes.main.screen.widget.ModernChooseWheel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Écran « Modifier la roue » des emotes (04/11, maquette validée par le propriétaire :
 * {@code maquettes/roue_emotes/modifier_la_roue.png}) : remplace le menu d'Emotecraft (« complètement dégueulasse » :
 * textes qui se chevauchent, style de Minecraft, boutons inutiles d'export et de dossier). Au style des écrans du clan,
 * comme la roue ({@link RoueEmotes}).
 * <ul>
 * <li>À gauche : les emotes (recherche, filtres Toutes / Débloquées / Dans la roue), icône, nom, description, et leur case
 * sur la page affichée (« Case 3 », ou « Page 2 ») ; emote à débloquer grisée avec un cadenas (elle ne se range pas).</li>
 * <li>À droite : la roue de la page (8 cases, 10 pages comme celle d'Emotecraft). Choisir une emote, puis cliquer sur une
 * case : elle y est rangée (et quitte l'autre case de la même page où elle était) ; clic sur une case pleine sans emote
 * choisie : on la reprend ; clic droit : la case est vidée. Molette sur la roue ou flèches : page ; molette sur la liste :
 * défilement.</li>
 * <li>Mêmes réglages qu'Emotecraft ({@code ClientConfig.fastMenuEmotes}), enregistrés en quittant l'écran par son propre
 * enregistrement ({@code ClientSerializer.saveConfig}) : rien ne change pour le reste (roue, touches d'Emotecraft).</li>
 * </ul>
 * Polices du mod à leur vraie taille, texte posé sur un point de l'écran (kit {@link Net}) ; canevas de 640 x 360 points.
 */
public final class EditionRoue extends Screen {
    private static final int L = 640, H = 360, PAGES = 10, CASES = 8;
    /** Panneau de la liste, ligne de la liste, roue (centre, rayon, côté d'une case), boutons du bas. */
    private static final float LX0 = 24, LY0 = 54, LX1 = 300, LY1 = 326, HL = 30, LISTE_Y = LY0 + 50;
    private static final float CXR = 470, CYR = 168, RAYON = 84, CASE = 52, BY = 304, BW = 104, BH = 20;
    private static final int BLANC = 0xFFFFFF;
    private static final ResourceLocation TITRE = new ResourceLocation("heraliz_emote", "ui_titre");

    private record Ligne(KeyframeAnimation anim, String nom, String desc, String cherche) {
    }

    private record Zone(float x0, float y0, float x1, float y1, Runnable action) {
        boolean dans(double x, double y) {
            return x >= x0 && x < x1 && y >= y0 && y < y1;
        }
    }

    @Nullable
    private final Screen parent;
    private final List<Ligne> toutes = new ArrayList<>();
    private final List<Ligne> montrees = new ArrayList<>();
    private final List<Zone> zones = new ArrayList<>();
    /** Emotes par identifiant (04/11, relecture : avant, la liste était parcourue pour chaque case à chaque image). */
    private final java.util.Map<UUID, Ligne> parId = new java.util.HashMap<>();
    /** Où est chaque emote de la roue : {page, case}, la page affichée d'abord ; refait à chaque changement. */
    private final java.util.Map<UUID, int[]> places = new java.util.HashMap<>();
    /** Nombre d'emotes de chaque filtre (Toutes, Débloquées, Dans la roue) ; refait à chaque changement. */
    private final int[] comptes = new int[3];
    /** Textes coupés à une largeur (le même calcul à chaque image sinon : des milliers de mesures de lettres). */
    private final java.util.Map<String, String> coupes = new java.util.HashMap<>();
    private float ox, oy;
    private int filtre;
    private int page;
    private float defil;
    private boolean modifie;
    @Nullable
    private UUID choisie;
    @Nullable
    private HzField recherche;
    @Nullable
    private Component statut;
    private long statutA;

    public EditionRoue(@Nullable Screen parent) {
        super(Component.translatable("heraliz_emote.edition.titre"));
        this.parent = parent;
        int p = ModernChooseWheel.fastMenuPage;
        this.page = p < 0 || p >= PAGES ? 0 : p;
    }

    @Override
    protected void init() {
        Echelle.forcer(this, L, H);
        ox = (width - L) / 2f;
        oy = (height - H) / 2f;
        if (toutes.isEmpty()) collecter();
        String texte = recherche == null ? "" : recherche.getValue();
        recherche = new HzField(Math.round(ox + LX0 + 8), Math.round(oy + LY0 + 8), Math.round(LX1 - LX0 - 16), 16, 40, HzField.TOUT)
                .placeholder(t("recherche"))
                .remplacerAuFocus(false)
                .accent(Ui.accent());
        recherche.setValue(texte);
        recherche.setResponder(s -> refiltrer());
        addRenderableWidget(recherche);
        setInitialFocus(recherche);
        coupes.clear();
        refiltrer();
    }

    @Override
    public void tick() {
        if (recherche != null) recherche.tick();   // curseur qui clignote
    }

    @Override
    public void removed() {
        ModernChooseWheel.fastMenuPage = page;
        if (modifie) {
            modifie = false;
            try {
                ClientSerializer.saveConfig();
            } catch (RuntimeException e) {
                fr.heraliz.emote.HeralizEmote.LOGGER.warn("[emotes] roue non enregistrée : {}", e.toString());
            }
        }
        Echelle.restaurer();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    // ------------------------------------------------------------------ données
    private void collecter() {
        for (KeyframeAnimation anim : ClientEmoteAPI.clientEmoteList()) {
            String nom = texte(anim.extraData.get("name"), "?");
            String desc = texte(anim.extraData.get("description"), "");
            Ligne l = new Ligne(anim, nom, desc, simple(nom + " " + desc));
            toutes.add(l);
            parId.put(anim.getUuid(), l);
        }
        toutes.sort(Comparator.comparing(l -> simple(l.nom())));
    }

    private static String texte(@Nullable Object brut, String secours) {
        if (!(brut instanceof String s) || s.isBlank()) return secours;
        try {
            MutableComponent c = Component.Serializer.fromJson(s);
            return c == null ? s : c.getString();
        } catch (RuntimeException e) {
            return s;
        }
    }

    /** Minuscules sans accents (recherche, tri). */
    private static String simple(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private void refiltrer() {
        recalculer();
        String q = recherche == null ? "" : simple(recherche.getValue().trim());
        montrees.clear();
        for (Ligne l : toutes) {
            if (!q.isEmpty() && !l.cherche().contains(q)) continue;
            if (filtre == 1 && verrou(l)) continue;
            if (filtre == 2 && !places.containsKey(l.anim().getUuid())) continue;
            montrees.add(l);
        }
        borner();
    }

    /** Places des emotes dans la roue et comptes des filtres, une fois par changement (jamais à chaque image). */
    private void recalculer() {
        places.clear();
        UUID[][] r = roue();
        if (r != null) {
            for (int k = 0; k < CASES; k++) if (r[page][k] != null) places.putIfAbsent(r[page][k], new int[]{page, k});
            for (int p = 0; p < PAGES; p++) {
                for (int k = 0; k < CASES; k++) if (r[p][k] != null) places.putIfAbsent(r[p][k], new int[]{p, k});
            }
        }
        java.util.Arrays.fill(comptes, 0);
        for (Ligne l : toutes) {
            comptes[0]++;
            if (!verrou(l)) comptes[1]++;
            if (places.containsKey(l.anim().getUuid())) comptes[2]++;
        }
    }

    private static boolean verrou(Ligne l) {
        return !DeblocagesClient.debloquee(l.anim().getUuid());
    }

    /** Les cases de la roue (10 pages de 8), créées ou agrandies si besoin ; null sans réglages d'Emotecraft. */
    @Nullable
    private static UUID[][] roue() {
        if (!(EmoteInstance.config instanceof ClientConfig cfg)) return null;
        UUID[][] r = cfg.fastMenuEmotes;
        if (r == null || r.length < PAGES) {
            UUID[][] n = new UUID[PAGES][];
            for (int i = 0; i < PAGES; i++) n[i] = r != null && i < r.length ? r[i] : null;
            r = n;
            cfg.fastMenuEmotes = r;
        }
        for (int i = 0; i < PAGES; i++) {
            if (r[i] == null) {
                r[i] = new UUID[CASES];
            } else if (r[i].length < CASES) {
                r[i] = java.util.Arrays.copyOf(r[i], CASES);
            }
        }
        return r;
    }

    @Nullable
    private UUID dansCase(int k) {
        UUID[][] r = roue();
        return r == null ? null : r[page][k];
    }

    private static final int[] NULLE_PART = {-1, -1};

    /** {page, case} où se trouve l'emote : d'abord la page affichée ; {-1, -1} si elle n'est pas dans la roue. */
    private int[] ou(UUID id) {
        return places.getOrDefault(id, NULLE_PART);
    }

    @Nullable
    private Ligne ligne(@Nullable UUID id) {
        return id == null ? null : parId.get(id);
    }

    /** Texte coupé à {@code max} points (gardé : le calcul n'est fait qu'une fois par texte et par largeur). */
    private String coupe(String s, ResourceLocation f, float max) {
        return coupes.computeIfAbsent(s + '\u0001' + f + '\u0001' + Math.round(max * 4), k -> Net.couper(font, s, f, max));
    }

    private void ranger(int k) {
        UUID[][] r = roue();
        if (r == null || choisie == null) return;
        Ligne l = ligne(choisie);
        if (l != null && verrou(l)) {
            dire(Component.translatable("heraliz_emote.menu.locked_tip"));
            return;
        }
        // déjà sur cette page dans une autre case : les deux cases sont échangées (sinon l'emote d'ici était perdue)
        UUID ici = r[page][k];
        for (int i = 0; i < CASES; i++) {
            if (i != k && choisie.equals(r[page][i])) r[page][i] = ici;
        }
        r[page][k] = choisie;
        choisie = null;
        modifie = true;
        SonsUi.clic(0.6F);
        refiltrer();
    }

    private void vider(int k) {
        UUID[][] r = roue();
        if (r == null || r[page][k] == null) return;
        r[page][k] = null;
        modifie = true;
        SonsUi.clic(0.5F);
        refiltrer();
    }

    private void changerPage(int sens) {
        page = Math.floorMod(page + sens, PAGES);
        SonsUi.clic(0.5F);
        refiltrer();
    }

    private void dire(Component c) {
        statut = c;
        statutA = System.currentTimeMillis();
    }

    // ------------------------------------------------------------------ géométrie
    private float hauteurListe() {
        return LY1 - 6 - LISTE_Y;
    }

    private void borner() {
        float max = Math.max(0, montrees.size() * HL - hauteurListe());
        defil = Math.max(0, Math.min(defil, max));
    }

    private boolean surListe(double mx, double my) {
        return mx >= ox + LX0 && mx < ox + LX1 && my >= oy + LISTE_Y && my < oy + LY1 - 6;
    }

    /** Coin haut gauche de la case {@code k} de la roue (k = 0 en haut, puis dans le sens des aiguilles d'une montre). */
    private float[] coinCase(int k) {
        double a = Math.toRadians(-90 + 45 * k);
        return new float[]{(float) (ox + CXR + Math.cos(a) * RAYON - CASE / 2), (float) (oy + CYR + Math.sin(a) * RAYON - CASE / 2)};
    }

    private int caseSous(double mx, double my) {
        for (int k = 0; k < CASES; k++) {
            float[] c = coinCase(k);
            if (mx >= c[0] && mx < c[0] + CASE && my >= c[1] && my < c[1] + CASE) return k;
        }
        return -1;
    }

    // ------------------------------------------------------------------ souris et touches
    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        // un clic ailleurs que dans la recherche lui retire le clavier (flèches : pages de la roue)
        if (recherche != null && recherche.isFocused() && !recherche.isMouseOver(mx, my)) {
            recherche.setFocused(false);
            setFocused(null);
        }
        if (super.mouseClicked(mx, my, b)) return true;
        if (b == 0) {
            for (Zone z : new ArrayList<>(zones)) {
                if (z.dans(mx, my)) {
                    z.action().run();
                    return true;
                }
            }
        }
        int k = caseSous(mx, my);
        if (k >= 0) {
            if (b == 1) {
                vider(k);
            } else if (b == 0 && choisie != null) {
                ranger(k);
            } else if (b == 0 && ligne(dansCase(k)) != null) {
                // reprise : la case pleine devient l'emote choisie (une emote qui n'existe plus est montrée vide et ne se
                // reprend pas ; une emote à débloquer reste où elle est)
                choisir(ligne(dansCase(k)));
            }
            return true;
        }
        if (b == 0 && surListe(mx, my)) {
            int i = (int) Math.floor((my - (oy + LISTE_Y) + defil) / HL);
            if (i >= 0 && i < montrees.size()) {
                Ligne l = montrees.get(i);
                if (l.anim().getUuid().equals(choisie)) {
                    choisie = null;
                    SonsUi.clic(0.5F);
                } else {
                    choisir(l);
                }
                return true;
            }
        }
        return false;
    }

    /** Emote choisie (refusée, avec le message, si elle est à débloquer : elle ne se range pas dans la roue). */
    private void choisir(Ligne l) {
        if (verrou(l)) {
            dire(Component.translatable("heraliz_emote.menu.locked_tip"));
            return;
        }
        choisie = l.anim().getUuid();
        SonsUi.clic(0.5F);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (surListe(mx, my)) {
            defil -= (float) delta * HL;
            borner();
        } else if (delta != 0) {
            changerPage(delta > 0 ? -1 : 1);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int touche, int scan, int mods) {
        boolean saisie = recherche != null && recherche.isFocused();
        if (!saisie && (touche == GLFW.GLFW_KEY_LEFT || touche == GLFW.GLFW_KEY_RIGHT)) {
            changerPage(touche == GLFW.GLFW_KEY_LEFT ? -1 : 1);
            return true;
        }
        return super.keyPressed(touche, scan, mods);
    }

    // ------------------------------------------------------------------ rendu
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        zones.clear();
        Ui.fond(g, width, height);
        int accent = Ui.accent();

        Net.texte(g, font, t("titre"), TITRE, ox + 24, oy + 22, Ui.TEXT);
        Net.texte(g, font, t("sous_titre"), Ui.SMALL, ox + 24, oy + 38, Ui.MUTED);

        liste(g, mx, my, accent);
        roueDessin(g, mx, my, accent);
        bas(g, mx, my, accent);
        super.render(g, mx, my, pt);
    }

    private void liste(GuiGraphics g, int mx, int my, int accent) {
        float x0 = ox + LX0, y0 = oy + LY0, x1 = ox + LX1, y1 = oy + LY1;
        Net.rect(g, x0, y0, x1, y1, Ui.argb(Ui.VERRE, Ui.VERRE_ALPHA));
        Net.cadre(g, x0, y0, x1 - x0, y1 - y0, Ui.argb(BLANC, 34));

        // filtres
        float fx = x0 + 8, fy = y0 + 38;
        String[] noms = {t("filtre.toutes"), t("filtre.debloquees"), t("filtre.roue")};
        for (int i = 0; i < noms.length; i++) {
            int n = comptes[i];
            String s = noms[i] + "  " + n;
            float w = Net.largeur(font, s, Ui.SMALL) + 12;
            boolean actif = filtre == i, sv = mx >= fx && mx < fx + w && my >= fy - 6 && my < fy + 6;
            Net.rect(g, fx, fy - 6, fx + w, fy + 6, actif ? Ui.argb(accent, 60) : Ui.argb(BLANC, sv ? 20 : 8));
            Net.cadre(g, fx, fy - 6, w, 12, actif ? Ui.argb(accent, 200) : Ui.argb(BLANC, sv ? 90 : 34));
            Net.centre(g, font, s, Ui.SMALL, fx + w / 2, fy, actif ? Ui.TEXT : Ui.MUTED);
            final int f = i;
            zones.add(new Zone(fx, fy - 6, fx + w, fy + 6, () -> {
                filtre = f;
                defil = 0;
                SonsUi.clic(0.5F);
                refiltrer();
            }));
            fx += w + 5;
        }

        // lignes (coupées aux bords de la liste)
        float lt = oy + LISTE_Y, lb = oy + LY1 - 6;
        g.flush();
        g.enableScissor(Math.round(x0), Math.round(lt), Math.round(x1), Math.round(lb));
        if (montrees.isEmpty()) {
            Net.centre(g, font, Component.translatable("heraliz_emote.menu.empty").getString(), Ui.SMALL, (x0 + x1) / 2, lt + 20, Ui.FAINT);
        }
        int premiere = (int) Math.floor(defil / HL);
        for (int i = Math.max(0, premiere); i < montrees.size(); i++) {
            float ly = lt + i * HL - defil;
            if (ly > lb) break;
            ligneDessin(g, montrees.get(i), x0, ly, x1, mx, my, accent);
        }
        g.flush();
        g.disableScissor();

        // barre de défilement
        float total = montrees.size() * HL, vue = hauteurListe();
        if (total > vue) {
            float bx = x1 - 6, haut = lb - lt, h = Math.max(16, haut * vue / total), y = lt + (haut - h) * (defil / (total - vue));
            Net.rect(g, bx, lt, bx + 2, lb, Ui.argb(BLANC, 18));
            Net.rect(g, bx, y, bx + 2, y + h, Ui.argb(BLANC, 90));
        }
    }

    private void ligneDessin(GuiGraphics g, Ligne l, float x0, float ly, float x1, int mx, int my, int accent) {
        UUID id = l.anim().getUuid();
        boolean choix = id.equals(choisie), verrou = verrou(l);
        boolean sv = mx >= x0 + 6 && mx < x1 - 12 && my >= ly && my < ly + HL - 2 && surListe(mx, my);
        if (choix) {
            Net.rect(g, x0 + 6, ly, x1 - 12, ly + HL - 2, Ui.argb(accent, 40));
            Net.cadre(g, x0 + 6, ly, x1 - x0 - 18, HL - 2, Ui.argb(accent, 190));
            Net.rect(g, x0 + 6, ly + 3, x0 + 8, ly + HL - 5, Ui.argb(accent, 255));
        } else if (sv) {
            Net.rect(g, x0 + 6, ly, x1 - 12, ly + HL - 2, Ui.argb(BLANC, 10));
        }
        // étiquette à droite : case de la page affichée, autre page, ou à débloquer
        int[] o = ou(id);
        String e = verrou ? t("a_debloquer") : o[0] == page ? t("case", o[1] + 1) : o[0] >= 0 ? t("page_court", o[0] + 1) : null;
        float we = e == null ? 0 : Net.largeur(font, e, Ui.SMALL) + 10;
        if (e != null) {
            int c = verrou ? Ui.FAINT : accent;
            float ex = x1 - 18 - we, ey = ly + HL / 2 - 1;
            Net.rect(g, ex, ey - 6, ex + we, ey + 6, Ui.argb(c, 30));
            Net.cadre(g, ex, ey - 6, we, 12, Ui.argb(c, 120));
            Net.centre(g, font, e, Ui.SMALL, ex + we / 2, ey, c);
        }
        float tx = x0 + 42, place = x1 - 18 - (e == null ? 0 : we + 6) - tx;
        Net.texte(g, font, coupe(l.nom(), Ui.BOLD, place), Ui.BOLD, tx, ly + 9, verrou ? Ui.FAINT : Ui.TEXT);
        Net.texte(g, font, coupe(l.desc(), Ui.SMALL, place), Ui.SMALL, tx, ly + 20, verrou ? Ui.FAINT : Ui.MUTED);
        icone(g, l.anim(), x0 + 12, ly + 2, 24, verrou ? 0.45f : 1f, verrou);
        if (verrou) cadenas(g, x0 + 28, ly + 17);
    }

    private void roueDessin(GuiGraphics g, int mx, int my, int accent) {
        float cx = ox + CXR, cy = oy + CYR;
        Net.centre(g, font, t("roue_titre"), Ui.BOLD, cx, cy - RAYON - CASE / 2 - 20, Ui.TEXT);
        Net.capsCentre(g, font, t("page", page + 1, PAGES), Ui.SMALL, 0.8f, cx, cy - RAYON - CASE / 2 - 9, Ui.MUTED);
        Net.anneau(g, cx, cy, RAYON, Net.point(), Ui.argb(BLANC, 22));
        int survol = caseSous(mx, my);
        for (int k = 0; k < CASES; k++) caseDessin(g, k, k == survol, accent);

        // milieu : emote choisie, page
        Net.disque(g, cx, cy, 36, Ui.argb(Ui.VERRE, 235));
        Net.anneau(g, cx, cy, 36, Net.point(), Ui.argb(BLANC, 40));
        Ligne l = ligne(choisie);
        if (l != null) {
            icone(g, l.anim(), cx - 11, cy - 27, 22, 1f, false);
            Net.centre(g, font, coupe(l.nom(), Ui.BOLD, 64), Ui.BOLD, cx, cy + 2, Ui.TEXT);
            Net.capsCentre(g, font, t("choisie"), Ui.SMALL, 0.6f, cx, cy + 12, accent);
        } else {
            Net.centre(g, font, t("choisir"), Ui.SMALL, cx, cy - 4, Ui.MUTED);
        }
        fleche(g, "←", cx - 20, cy + 24, mx, my, () -> changerPage(-1));
        Net.centre(g, font, (page + 1) + " / " + PAGES, Ui.SMALL, cx, cy + 24, Ui.TEXT);
        fleche(g, "→", cx + 20, cy + 24, mx, my, () -> changerPage(1));
    }

    private void caseDessin(GuiGraphics g, int k, boolean sv, int accent) {
        float[] c = coinCase(k);
        float x = c[0], y = c[1];
        UUID id = dansCase(k);
        Ligne l = ligne(id);
        boolean placer = sv && choisie != null;
        Net.rect(g, x, y, x + CASE, y + CASE, Ui.argb(Ui.VERRE, 225));
        if (sv) {
            Net.rect(g, x, y, x + CASE, y + CASE, Ui.argb(accent, 46));
            Net.cadre(g, x, y, CASE, CASE, Ui.argb(accent, 230));
            Net.rect(g, x + 2, y, x + CASE - 2, y + 2, Ui.argb(accent, 255));
        } else {
            Net.cadre(g, x, y, CASE, CASE, Ui.argb(BLANC, 34));
        }
        Net.texte(g, font, String.valueOf(k + 1), Ui.SMALL, x + 4, y + 7, Ui.FAINT);
        if (placer) {
            Ligne ch = ligne(choisie);
            if (ch != null) icone(g, ch.anim(), x + CASE / 2 - 14, y + 7, 28, 0.55f, false);
            Net.centre(g, font, t("placer"), Ui.SMALL, x + CASE / 2, y + CASE - 8, accent);
        } else if (l != null) {
            boolean verrou = verrou(l);
            icone(g, l.anim(), x + CASE / 2 - 14, y + 7, 28, verrou ? 0.45f : 1f, verrou);
            Net.centre(g, font, coupe(l.nom(), Ui.SMALL, CASE - 6), Ui.SMALL, x + CASE / 2, y + CASE - 8,
                    sv ? Ui.TEXT : verrou ? Ui.FAINT : Ui.MUTED);
        } else {
            // case vide : carré en pointillés et « + »
            float a = x + 13, b = y + 13, w = CASE - 26;
            int pc = Ui.argb(BLANC, 40);
            for (float i = 0; i < w; i += 4) {
                Net.rect(g, a + i, b, a + Math.min(w, i + 2), b + Net.point(), pc);
                Net.rect(g, a + i, b + w - Net.point(), a + Math.min(w, i + 2), b + w, pc);
                Net.rect(g, a, b + i, a + Net.point(), b + Math.min(w, i + 2), pc);
                Net.rect(g, a + w - Net.point(), b + i, a + w, b + Math.min(w, i + 2), pc);
            }
            Net.centre(g, font, "+", Ui.BOLD, x + CASE / 2, y + CASE / 2, Ui.FAINT);
        }
    }

    private void bas(GuiGraphics g, int mx, int my, int accent) {
        float cx = ox + CXR, y = oy + CYR + RAYON + CASE / 2 + 12;
        if (statut != null && System.currentTimeMillis() - statutA < 4000) {
            Net.centre(g, font, Net.couper(font, statut.getString(), Ui.SMALL, 300), Ui.SMALL, cx, y, Ui.DANGER);
        } else {
            String[][] aides = {{rt("t.clic"), t("aide.ranger")}, {t("t.droit"), t("aide.vider")}, {rt("t.molette"), rt("k.page")}};
            float total = -12;
            for (String[] a : aides) total += Net.capsLargeur(font, a[0], Ui.SMALL, 0.8f) + 8 + 4 + Net.largeur(font, a[1], Ui.SMALL) + 12;
            float x = cx - total / 2;
            for (String[] a : aides) {
                float w = Net.capsLargeur(font, a[0], Ui.SMALL, 0.8f) + 8;
                Net.rect(g, x, y - 5, x + w, y + 5, Ui.argb(BLANC, 18));
                Net.cadre(g, x, y - 5, w, 10, Ui.argb(BLANC, 60));
                Net.capsCentre(g, font, a[0], Ui.SMALL, 0.8f, x + w / 2, y, Ui.TEXT);
                x += w + 4;
                x += Net.texte(g, font, a[1], Ui.SMALL, x, y, Ui.MUTED) + 12;
            }
        }
        float by = oy + BY;
        bouton(g, t("retour"), cx - BW - 4, by, BLANC, false, mx, my, this::onClose);
        // « Terminé » ferme tout ; « Retour à la roue » revient à la roue (04/11, relecture : les deux faisaient la même chose)
        bouton(g, t("termine"), cx + 4, by, accent, true, mx, my, () -> Minecraft.getInstance().setScreen(null));
    }

    private void bouton(GuiGraphics g, String txt, float x, float y, int c, boolean plein, int mx, int my, Runnable action) {
        boolean sv = mx >= x && mx < x + BW && my >= y && my < y + BH;
        if (plein) {
            Net.rect(g, x, y, x + BW, y + BH, Ui.argb(c, sv ? 90 : 60));
            Net.cadre(g, x, y, BW, BH, Ui.argb(c, 220));
        } else {
            Net.rect(g, x, y, x + BW, y + BH, Ui.argb(BLANC, sv ? 20 : 6));
            Net.cadre(g, x, y, BW, BH, Ui.argb(BLANC, sv ? 170 : 90));
        }
        Net.capsCentre(g, font, txt, Ui.SMALL, 1.0f, x + BW / 2, y + BH / 2, Ui.TEXT);
        zones.add(new Zone(x, y, x + BW, y + BH, () -> {
            SonsUi.clic(0.6F);
            action.run();
        }));
    }

    private void fleche(GuiGraphics g, String s, float x, float y, int mx, int my, Runnable action) {
        boolean sv = mx >= x - 8 && mx < x + 8 && my >= y - 7 && my < y + 7;
        Net.centre(g, font, s, Ui.BOLD, x, y, sv ? Ui.TEXT : Ui.MUTED);
        zones.add(new Zone(x - 8, y - 7, x + 8, y + 7, action));
    }

    /** Icône de l'emote (ou initiale sur une pastille), {@code alpha} et grisée pour une emote à débloquer. */
    private void icone(GuiGraphics g, KeyframeAnimation anim, float x, float y, int taille, float alpha, boolean gris) {
        EmoteIcons.Icon ic = EmoteIcons.get(anim);
        int ix = Math.round(x), iy = Math.round(y);
        if (ic == null) {
            Net.rect(g, ix, iy, ix + taille, iy + taille, Ui.argb(Ui.mix(Ui.accent(), Ui.VERRE, 0.45f), Math.round(255 * alpha)));
            String n = texte(anim.extraData.get("name"), "?");
            Net.centre(g, font, n.isEmpty() ? "?" : n.substring(0, 1).toUpperCase(Locale.ROOT), Ui.BOLD, ix + taille / 2f, iy + taille / 2f, Ui.TEXT);
            return;
        }
        g.flush();   // les formes d'avant passent dessous
        RenderSystem.enableBlend();
        if (gris) {
            RenderSystem.setShaderColor(0.42f, 0.44f, 0.48f, alpha);
        } else {
            RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
        }
        g.blit(ic.texture(), ix, iy, taille, taille, 0, 0, ic.width(), ic.height(), ic.width(), ic.height());
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private static void cadenas(GuiGraphics g, float x, float y) {
        int c = Ui.argb(0xD8DCE4, 235);
        Net.rect(g, x - 1, y - 1, x + 9, y + 10, Ui.argb(Ui.VERRE, 210));
        Net.cadre(g, x + 1.5f, y, 5, 5, c);
        Net.rect(g, x, y + 4, x + 8, y + 9, c);
    }

    private static String t(String cle, Object... args) {
        return Component.translatable("heraliz_emote.edition." + cle, args).getString();
    }

    /** Textes partagés avec la roue. */
    private static String rt(String cle) {
        return Component.translatable("heraliz_emote.roue." + cle).getString();
    }
}
