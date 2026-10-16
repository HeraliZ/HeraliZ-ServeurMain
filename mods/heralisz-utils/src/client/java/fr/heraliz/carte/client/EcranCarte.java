package fr.heraliz.carte.client;

import fr.heraliz.carte.CarteConfig;
import fr.heraliz.carte.Lieu;
import fr.heraliz.ping.PingType;
import fr.heraliz.ping.client.PingsClient;
import fr.heraliz.ping.client.gui.kit.Echelle;
import fr.heraliz.ping.net.Paquets;
import fr.heraliz.ping.net.PingNet;
import fr.heralisz.utils.client.kit.SonsUi;
import fr.heralisz.utils.client.kit.Ui;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.border.WorldBorder;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * La carte du monde, au style des écrans de clan (panneaux de verre, DM Sans, canevas 640 x 360). Version « AAA »
 * (proposition 1 « Verre » des maquettes du 16/10, validée ; icônes au trait fin sur petite pastille ronde en 2.14.1) :
 * textes lisibles (jamais moins de 7,5 unités d'interface), étiquettes avec halo qui ne se chevauchent pas, carte coupée à la bordure du monde.
 * <ul>
 *     <li>Fond : vue de dessus du monde ({@link Fond}), coupée à la <b>bordure</b> : bordure du monde envoyée par le
 *     serveur, bordure écrite dans carte.json et emprise des images (la plus petite des trois). Dehors : fond sombre
 *     uni, rien d'autre ; le déplacement et le zoom ne sortent pas de la bordure. Sans image : fond neutre et message.</li>
 *     <li>Calques (panneau de gauche, chacun se coupe d'un clic) : territoires des clans (cercle, bannière, état, capture
 *     en cours), extractions ouvertes, lieux nommés (villes, villages, points d'intérêt), membres de son clan, pings du
 *     clan, grille de 500 m (colonnes A, B... et lignes 1, 2... depuis le coin nord-ouest de la bordure).</li>
 *     <li>Territoires et lieux dont le centre est hors de la bordure ne sont pas montrés (zones de l'ancienne carte) :
 *     la liste dit combien, à reposer en jeu ({@code /territoire drapeau} ou {@code /territoire deplacer}).</li>
 *     <li>Le joueur : flèche dans sa direction et cône de vue. Jamais d'autres joueurs que ceux de son clan (envoyés par
 *     le serveur).</li>
 *     <li>Souris : glisser = déplacer, molette ou boutons + / − = zoom, clic = choisir une zone (fiche à droite), clic
 *     droit = marquer un point pour son clan (ping) ou retirer son propre ping. Espace = revenir sur soi ; la touche de
 *     la carte ou Échap = fermer.</li>
 * </ul>
 */
public final class EcranCarte extends Screen {
    static final String SATELLITE = "satellite", RELIEF = "relief";
    private static final int TEXTE = 0xECEEF2, GRIS = 0x8F95A0, PALE = 0x5A5F69, NEUTRE = 0x6FA8FF, EXTRACTION = 0x50E6A0,
            EXTRACTION_TEXTE = 0x7DF0B8, ROUGE = 0xFF6B6B, VERT = 0x5FD08A, AMBRE = 0xE8B34A, PROTEGEE = 0x7FD3FF, TENUE = 0x9AA3B2,
            FOND = 0x07090C, VERRE = 0x0D0F14;
    /** Géométrie (unités d'interface du canevas 640 x 360). */
    private static final float MARGE = 8, PANNEAU_L = 176, FICHE_L = 170, LIGNE_CALQUE = 14, LIGNE_ZONE = 18, BAS_H = 22, VUE_H = 21,
            BOUTON = 21;
    /**
     * Tailles des textes, en échelle des polices du kit (DM Sans 11 pour {@link Ui#FONT} et {@link Ui#BOLD}) : capitales
     * 7,5, texte 8,5 à 9,5, titres 12,5 à 14 unités. Rien sous 7,5 : en 1080p, c'est 22 px d'écran.
     */
    private static final float S_CAPS = 0.68f, S_PETIT = 0.77f, S_TEXTE = 0.82f, S_NOM = 0.86f, S_TITRE_FICHE = 1.14f, S_TITRE = 1.27f;
    private static final int GRILLE = 500;
    private static final float K_MAX = 3f;
    private static final float[] ECHELLES = {5, 10, 20, 25, 50, 100, 200, 250, 500, 1000, 2000, 5000};
    /** Bordure « par défaut » de Minecraft (immense) : au-delà, on l'ignore. */
    private static final double BORDURE_MAX = 1.0E6;

    /** Zoom retenu d'une ouverture à l'autre pendant la partie (-1 : première ouverture). */
    private static float zoomMemo = -1f;

    private double cx, cz, cxCible, czCible;
    private float k, kCible;
    private long derniereImage, derniereDemande;
    private boolean glisse, aBouge;
    private double glisseX, glisseY;
    @Nullable
    private String selection;
    private boolean menu;
    private float menuX, menuY;
    private int menuMondeX, menuMondeZ;
    private float defilement;
    /** Première cible du menu « marquer ici » dans la liste de l'image. */
    private int menuDebut;
    private float ox, oy, unPixel = 1f / 3f;
    private float listeY0, listeY1, ficheY1, basX0, basX1, basY0, vueX0;
    private boolean territoires, extractions, lieux, clan, pings, grille, relief;

    private List<Sources.Zone> zones = List.of();
    private int zonesHors;
    private List<Sources.Allie> allies = List.of();
    private List<Sources.Extraction> ouvertes = List.of();
    private List<Lieu> lieuxIci = List.of();
    @Nullable
    private Sources.MonClan monClan;
    @Nullable
    private Fond.Info fond;
    private boolean fondIci;
    /** Rectangle de la carte en blocs (x0, z0, x1, z1) : bordure ∩ emprise du fond ; null si rien ne la limite. */
    @Nullable
    private double[] lim;
    private double px, pz;
    private float cap;
    private long heureDeJeu;

    /** Zone cliquable de l'image (reconstruite à chaque image ; la dernière ajoutée est au-dessus). */
    private record Cible(float x0, float y0, float x1, float y1, Runnable action) {
        boolean dans(double x, double y) {
            return x >= x0 && x < x1 && y >= y0 && y < y1;
        }
    }

    private final List<Cible> cibles = new ArrayList<>();
    /** Rectangles déjà pris par une étiquette de la carte cette image (les suivantes qui les chevauchent sont omises). */
    private final List<float[]> places = new ArrayList<>();
    /**
     * Étiquettes de l'image, mises en page d'abord puis dessinées en deux temps : les fonds (pastilles) puis tous les
     * textes d'un seul envoi. Dans un envoi groupé, Minecraft ne garantit pas l'ordre entre aplats et textes : une
     * pastille pourrait recouvrir son texte.
     */
    private final List<Runnable> fonds = new ArrayList<>(), textes = new ArrayList<>();

    public EcranCarte() {
        super(Component.translatable("heraliz_carte.titre"));
    }

    // =================================================================== ouverture, fermeture
    @Override
    protected void init() {
        Echelle.forcer(this);
        unPixel = 1f / (float) minecraft.getWindow().getGuiScale();
        ox = (PANNEAU_L + width - BOUTON) / 2f;
        oy = (height - BAS_H - MARGE) / 2f;
        if (derniereImage == 0) {
            territoires = CarteConfig.TERRITOIRES.get();
            extractions = CarteConfig.EXTRACTIONS.get();
            lieux = CarteConfig.LIEUX.get();
            clan = CarteConfig.CLAN.get();
            pings = CarteConfig.PINGS.get();
            grille = CarteConfig.GRILLE.get();
            relief = CarteConfig.RELIEF.get();
            fond = Fond.info();
            fondIci = fond != null && minecraft.level != null && fond.dimension().equals(minecraft.level.dimension().location());
            limites();
            Player p = minecraft.player;
            if (p != null) {
                cx = cxCible = p.getX();
                cz = czCible = p.getZ();
            }
            k = kCible = Mth.clamp(zoomMemo > 0 ? zoomMemo : 0.25f, kMin(), K_MAX);
            borner();
            cx = cxCible;
            cz = czCible;
            derniereImage = Util.getMillis();
            Sources.demanderClans();
            derniereDemande = derniereImage;
            SonsUi.ouvrir(0.5f);
        }
    }

    @Override
    public void onClose() {
        SonsUi.fermer(0.5f);
        super.onClose();
    }

    @Override
    public void removed() {
        zoomMemo = kCible;
        CarteConfig.TERRITOIRES.set(territoires);
        CarteConfig.EXTRACTIONS.set(extractions);
        CarteConfig.LIEUX.set(lieux);
        CarteConfig.CLAN.set(clan);
        CarteConfig.PINGS.set(pings);
        CarteConfig.GRILLE.set(grille);
        CarteConfig.RELIEF.set(relief);
        CarteConfig.CLIENT_SPEC.save();
        Echelle.restaurer();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g) {
        // le fond est dessiné par render (la carte couvre tout l'écran)
    }

    // =================================================================== projection, bordure
    private float sx(double x) {
        return (float) (ox + (x - cx) * k);
    }

    private float sy(double z) {
        return (float) (oy + (z - cz) * k);
    }

    private double mondeX(double x) {
        return cx + (x - ox) / k;
    }

    private double mondeZ(double y) {
        return cz + (y - oy) / k;
    }

    /**
     * Rectangle de la carte : la bordure du monde du joueur (envoyée par le serveur ; ignorée si c'est celle, immense, de
     * Minecraft par défaut), la bordure de carte.json et l'emprise des images, la plus petite des trois.
     */
    private void limites() {
        double x0 = -Double.MAX_VALUE, z0 = -Double.MAX_VALUE, x1 = Double.MAX_VALUE, z1 = Double.MAX_VALUE;
        boolean borne = false;
        if (minecraft != null && minecraft.level != null) {
            WorldBorder wb = minecraft.level.getWorldBorder();
            if (wb.getSize() < BORDURE_MAX) {
                x0 = wb.getMinX();
                z0 = wb.getMinZ();
                x1 = wb.getMaxX();
                z1 = wb.getMaxZ();
                borne = true;
            }
        }
        Fond.Info f = fond;
        if (f != null && fondIci) {
            x0 = Math.max(x0, f.x0());
            z0 = Math.max(z0, f.z0());
            x1 = Math.min(x1, f.x0() + f.largeur());
            z1 = Math.min(z1, f.z0() + f.hauteur());
            int[] b = f.bordure();
            if (b != null) {
                x0 = Math.max(x0, b[0]);
                z0 = Math.max(z0, b[1]);
                x1 = Math.min(x1, b[2]);
                z1 = Math.min(z1, b[3]);
            }
            borne = true;
        }
        lim = borne && x1 > x0 && z1 > z0 ? new double[]{x0, z0, x1, z1} : null;
    }

    /** Le point (x, z) du monde est dans la carte (toujours vrai sans limite). */
    private boolean dansCarte(double x, double z) {
        double[] l = lim;
        return l == null || (x >= l[0] && x <= l[2] && z >= l[1] && z <= l[3]);
    }

    /** Largeur et hauteur utiles pour la carte (entre le panneau de gauche, la barre du bas et les bords). */
    private float aireL() {
        return width - MARGE * 3 - PANNEAU_L - BOUTON;
    }

    private float aireH() {
        return height - MARGE * 3 - BAS_H;
    }

    /** Zoom minimal : toute la carte tient à l'écran (sans limite : environ 8 km de large). */
    private float kMin() {
        double[] l = lim;
        if (l == null) return 0.05f;
        float ajuste = (float) Math.min(aireL() / (l[2] - l[0]), aireH() / (l[3] - l[1]));
        return Math.max(0.005f, Math.min(K_MAX, ajuste * 0.96f));
    }

    /**
     * Facteur d'agrandissement des marqueurs : à peine plus grands quand on zoome (0,92 en vue d'ensemble, 1,1 de près),
     * pour garder des icônes fines et une carte peu chargée.
     */
    private float e() {
        return 0.92f + zoom01() * 0.18f;
    }

    /** Fondu de 0 (z ≤ a) à 1 (z ≥ b), adouci : les marqueurs apparaissent avec le zoom au lieu de surgir. */
    static float fondu(float z, float a, float b) {
        float t = Mth.clamp((z - a) / (b - a), 0f, 1f);
        return t * t * (3 - 2 * t);
    }

    /**
     * Visibilité d'un lieu selon le zoom : les grands lieux (villes, aérodromes, bases militaires, ports, centrales)
     * toujours ; villages et petits points d'intérêt (usine, hôpital, gare, point) seulement quand on s'approche.
     */
    private float visibilite(Lieu l) {
        return switch (l.type()) {
            case VILLE, AERODROME, MILITAIRE, PORT, CENTRALE -> 1f;
            default -> fondu(zoom01(), 0.08f, 0.28f);
        };
    }

    /** Opacité des drapeaux des territoires (seulement de près). */
    private float visibiliteDrapeaux() {
        return fondu(k, 0.28f, 0.40f);
    }

    /** Demi-côté des pastilles des lieux, des extractions et des drapeaux. */
    private static final float R_LIEU = 5.2f, R_EXTRACTION = 5.8f, R_DRAPEAU = 4.4f;

    /** 0 en vue d'ensemble, 1 de près (sert à la taille des noms de villes). */
    private float zoom01() {
        return Mth.clamp((k - 0.1f) / 0.5f, 0f, 1f);
    }

    private void animer(long maintenant) {
        float dt = Math.min(100f, maintenant - derniereImage);
        derniereImage = maintenant;
        float t = 1f - (float) Math.exp(-dt / 70f);
        if (Math.abs(kCible - k) < kCible * 0.002f) k = kCible;
        else k = (float) Math.exp(Mth.lerp(t, Math.log(k), Math.log(kCible)));
        if (Math.abs(cxCible - cx) * k < 0.05 && Math.abs(czCible - cz) * k < 0.05) {
            cx = cxCible;
            cz = czCible;
        } else {
            cx += (cxCible - cx) * t;
            cz += (czCible - cz) * t;
        }
    }

    /**
     * Garde la vue dans la carte : si la carte entière tient à l'écran sur un axe, elle y est centrée ; sinon le centre
     * reste assez loin du bord pour ne jamais montrer au-delà de la bordure.
     */
    private void borner() {
        double[] l = lim;
        if (l == null) return;
        double demiL = aireL() / 2.0 / kCible, demiH = aireH() / 2.0 / kCible;
        cxCible = l[2] - l[0] <= demiL * 2 ? (l[0] + l[2]) / 2 : Mth.clamp(cxCible, l[0] + demiL, l[2] - demiL);
        czCible = l[3] - l[1] <= demiH * 2 ? (l[1] + l[3]) / 2 : Mth.clamp(czCible, l[1] + demiH, l[3] - demiH);
    }

    private void centrer(double x, double z) {
        cxCible = x;
        czCible = z;
        borner();
    }

    /** Zoom autour du centre de la carte (boutons + et −). */
    private void zoomer(float facteur) {
        kCible = Mth.clamp(kCible * facteur, kMin(), K_MAX);
        borner();
    }

    // =================================================================== image
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        Player p = minecraft.player;
        if (p == null || minecraft.level == null) {
            onClose();
            return;
        }
        long maintenant = Util.getMillis();
        animer(maintenant);
        if (maintenant - derniereDemande >= 1000) {
            derniereDemande = maintenant;
            Sources.demanderClans();
        }
        // ce que montre la carte cette image
        ResourceLocation dim = minecraft.level.dimension().location();
        fond = Fond.info();
        fondIci = fond != null && fond.dimension().equals(dim);
        limites();
        zones = new ArrayList<>();
        zonesHors = 0;
        for (Sources.Zone z : Sources.zones()) {
            if (dansCarte(z.x() + 0.5, z.z() + 0.5)) zones.add(z);
            else zonesHors++;
        }
        allies = Sources.allies();
        monClan = Sources.monClan();
        ouvertes = new ArrayList<>();
        for (Sources.Extraction x : Sources.extractions()) if (x.dimension().equals(dim) && dansCarte(x.x(), x.z())) ouvertes.add(x);
        lieuxIci = new ArrayList<>();
        for (Lieu l : LieuxClient.lieux()) if (l.dimension().equals(dim) && dansCarte(l.x() + 0.5, l.z() + 0.5)) lieuxIci.add(l);
        px = Mth.lerp(pt, p.xo, p.getX());
        pz = Mth.lerp(pt, p.zo, p.getZ());
        cap = (float) Math.toRadians(p.getViewYRot(pt) + 180f);
        heureDeJeu = minecraft.level.getGameTime();
        if (selection != null && zone(selection) == null) selection = null;
        cibles.clear();
        places.clear();
        fonds.clear();
        textes.clear();
        gr = g;

        g.fill(0, 0, width, height, 0xFF000000 | FOND);
        String vue = relief ? RELIEF : SATELLITE;
        if (fondIci && !fond.a(vue)) vue = fond.a(SATELLITE) ? SATELLITE : RELIEF;
        if (fondIci && fond.a(vue)) Fond.dessiner(g, fond, vue, ox, oy, cx, cz, k, width, height);

        float e = e();
        Icones.echelle(1f / unPixel);
        // ---- formes
        Formes.debut(g);
        if (grille) grilleTraits();
        if (territoires) zonesFormes(e);
        if (extractions) extractionsFormes(e);
        if (pings) pingsFormes(e);
        joueurFormes(e);
        horsCarte();
        vignette();
        Formes.fin();
        // ---- icônes
        Icones.debut(g);
        if (territoires) drapeauxIcones(e);
        if (lieux) lieuxIcones(e);
        if (extractions) extractionsIcones(e);
        if (pings) pingsIcones(e);
        if (clan) alliesIcones(e);
        joueurIcones(e);
        Icones.fin();
        // ---- bannières des clans au centre des zones
        if (territoires) zonesBannieres(e);
        // ---- étiquettes : mise en page (les plus importantes d'abord, les suivantes ne les recouvrent pas), puis les
        // fonds des pastilles, puis tous les textes en un seul envoi
        if (grille) grilleEtiquettes();
        if (lieux) lieuxEtiquettes(e, true);
        if (territoires) zonesEtiquettes(e);
        if (lieux) lieuxEtiquettes(e, false);
        if (extractions) extractionsEtiquettes(e);
        if (pings) pingsEtiquettes(e);
        if (clan) alliesEtiquettes(e);
        for (Runnable r : fonds) r.run();
        g.drawManaged(() -> {
            for (Runnable r : textes) r.run();
        });
        // ---- panneaux
        if (!fondIci) messageFond(g);
        panneauGauche(g, mx, my);
        basculeVue(g, mx, my);
        ficheZone(g, mx, my);
        barreBas(g, mx, my);
        boutonsZoom(g, mx, my);
        boussole(g);
        if (menu) menuMarquer(g, mx, my);
        else infobulle(g, mx, my);
    }

    // =================================================================== calques : formes
    /**
     * Dehors de la carte : fond sombre uni (rien au-delà de la bordure), ombre intérieure et filet clair le long du bord.
     * Dessiné APRÈS les calques : rien ne dépasse de la bordure.
     */
    private void horsCarte() {
        double[] l = lim;
        if (l == null) return;
        float x0 = sx(l[0]), y0 = sy(l[1]), x1 = sx(l[2]), y1 = sy(l[3]);
        int v = 0xFF000000 | FOND;
        Formes.rect(0, 0, width, Math.max(0, y0), v);
        Formes.rect(0, Math.min(height, y1), width, height, v);
        Formes.rect(0, Math.max(0, y0), Math.max(0, x0), Math.min(height, y1), v);
        Formes.rect(Math.min(width, x1), Math.max(0, y0), width, Math.min(height, y1), v);
        // trame discrète de points dehors (on voit que c'est « hors carte », pas un trou)
        float pas = 12;
        int point = 0x16FFFFFF;
        float t = unPixel * 1.5f;
        for (float gx = pas / 2; gx < width; gx += pas) {
            for (float gy = pas / 2; gy < height; gy += pas) {
                if (gx >= x0 && gx <= x1 && gy >= y0 && gy <= y1) continue;
                Formes.rect(gx, gy, gx + t, gy + t, point);
            }
        }
        // ombre intérieure et filet
        float o = 6;
        int a = 0x59000000, z = 0;
        Formes.degrade(x0, y0, x1, y0 + o, a, a, z, z);
        Formes.degrade(x0, y1 - o, x1, y1, z, z, a, a);
        Formes.degrade(x0, y0, x0 + o, y1, a, z, z, a);
        Formes.degrade(x1 - o, y0, x1, y1, z, a, a, z);
        float ep = unPixel * 1.5f;
        int c = 0x5CFFFFFF;
        Formes.rect(x0 - ep, y0 - ep, x1 + ep, y0, c);
        Formes.rect(x0 - ep, y1, x1 + ep, y1 + ep, c);
        Formes.rect(x0 - ep, y0, x0, y1, c);
        Formes.rect(x1, y0, x1 + ep, y1, c);
    }

    /** Origine de la grille (colonne A, ligne 1) : coin nord-ouest de la carte, arrondi à 500 m. */
    private int grilleX0() {
        return lim != null ? (int) Math.floor(lim[0] / GRILLE) * GRILLE : 0;
    }

    private int grilleZ0() {
        return lim != null ? (int) Math.floor(lim[1] / GRILLE) * GRILLE : 0;
    }

    private void grilleTraits() {
        double xa = mondeX(0), xb = mondeX(width), za = mondeZ(0), zb = mondeZ(height);
        float haut = 0, bas = height, gauche = 0, droite = width;
        if (lim != null) {
            haut = Math.max(0, sy(lim[1]));
            bas = Math.min(height, sy(lim[3]));
            gauche = Math.max(0, sx(lim[0]));
            droite = Math.min(width, sx(lim[2]));
        }
        int c = 0x2EFFFFFF;
        float ep = unPixel * 1.5f;
        for (long x = (long) Math.floor(xa / GRILLE) * GRILLE; x <= xb; x += GRILLE) {
            float X = sx(x);
            if (X < gauche - 1 || X > droite + 1) continue;
            Formes.rect(X - ep / 2, haut, X + ep / 2, bas, c);
        }
        for (long z = (long) Math.floor(za / GRILLE) * GRILLE; z <= zb; z += GRILLE) {
            float Y = sy(z);
            if (Y < haut - 1 || Y > bas + 1) continue;
            Formes.rect(gauche, Y - ep / 2, droite, Y + ep / 2, c);
        }
    }

    static String colonne(int i) {
        if (i < 0) return "";
        StringBuilder sb = new StringBuilder();
        int n = i;
        do {
            sb.insert(0, (char) ('A' + n % 26));
            n = n / 26 - 1;
        } while (n >= 0);
        return sb.toString();
    }

    /** Lettres et chiffres de la grille, collés au bord haut et au bord gauche de la carte visible. */
    private void grilleEtiquettes() {
        if (lim == null) return;
        int gx0 = grilleX0(), gz0 = grilleZ0();
        double xa = mondeX(0), xb = mondeX(width), za = mondeZ(0), zb = mondeZ(height);
        float gauche = Math.max(MARGE + PANNEAU_L + 8, sx(lim[0]) + 7), haut = Math.max(MARGE + 4, sy(lim[1]) + 8);
        int c = Formes.argb(TEXTE, 0.85f);
        for (long x = (long) Math.floor(xa / GRILLE) * GRILLE; x <= xb; x += GRILLE) {
            if (x < gx0 || x >= lim[2]) continue;
            float X = sx(x + GRILLE / 2.0);
            if (X < gauche + 6 || X > vueX0 - 6 && haut < MARGE + VUE_H + 6 || X > width - 6) continue;
            String lettre = colonne((int) ((x - gx0) / GRILLE));
            textes.add(() -> Texte.etiquette(gr, font, lettre, Ui.BOLD, X, haut, c, 0.73f, 0, false, 0.6f));
        }
        for (long z = (long) Math.floor(za / GRILLE) * GRILLE; z <= zb; z += GRILLE) {
            if (z < gz0 || z >= lim[3]) continue;
            float Y = sy(z + GRILLE / 2.0);
            if (Y < haut + 8 || Y > height - BAS_H - MARGE - 8) continue;
            String chiffre = String.valueOf((z - gz0) / GRILLE + 1);
            textes.add(() -> Texte.etiquette(gr, font, chiffre, Ui.BOLD, gauche, Y, c, 0.73f, 0, false, 0.6f));
        }
    }

    private boolean horsEcran(float x, float y, float r) {
        return x + r < 0 || y + r < 0 || x - r > width || y - r > height;
    }

    private int couleurZone(Sources.Zone z) {
        return z.libre() ? NEUTRE : z.proprioCouleur();
    }

    private void zonesFormes(float e) {
        float attenue = k > 0.33f ? 0.4f : 1f;
        for (Sources.Zone z : zones) {
            float X = sx(z.x() + 0.5), Y = sy(z.z() + 0.5), R = z.rayon() * k;
            if (horsEcran(X, Y, R + 4)) continue;
            int c = couleurZone(z);
            if (!z.libre()) {
                Formes.anneau(X, Y, R, 3.4f, Formes.argb(c, 0.10f));
                Formes.anneau(X, Y, R, 1.8f, Formes.argb(c, 0.16f));
            }
            if ("CONTESTE".equals(z.etat())) {
                Formes.hachures(X, Y, R, 2.4f, Formes.argb(c, 0.32f * attenue + 0.05f), Formes.argb(ROUGE, 0.30f * attenue + 0.05f));
            } else {
                float a = z.libre() ? 0.08f : z.monClan() ? 0.20f : 0.17f;
                Formes.disque(X, Y, R, Formes.argb(c, a * attenue));
            }
            if (z.libre()) {
                Formes.anneauPointille(X, Y, R, 1f, 3.3f, 2.7f, Formes.argb(c, 0.85f));
            } else {
                Formes.anneau(X, Y, R, 1.2f, Formes.argb(c, 0.95f));
            }
            if ("CAPTURE".equals(z.etat()) && !z.capteurTag().isEmpty()) {
                float r2 = R + 2.6f, a0 = (float) (-Math.PI / 2);
                Formes.anneau(X, Y, r2, 2.6f, 0x80000000);
                Formes.arc(X, Y, r2, 1.9f, a0, a0 + (float) (Math.PI * 2 * z.progression()), Formes.argb(z.capteurCouleur(), 1f));
            }
            if (z.id().equals(selection)) Formes.anneau(X, Y, R + 1.2f, 0.8f, 0xE6FFFFFF);
        }
    }

    private void extractionsFormes(float e) {
        for (Sources.Extraction x : ouvertes) {
            float X = sx(x.x()), Y = sy(x.z()), R = Math.max(x.rayon() * k, 7.5f * e);
            if (horsEcran(X, Y, R + 8)) continue;
            Formes.disque(X, Y, R, Formes.argb(EXTRACTION, 0.12f));
            Formes.anneauPointille(X, Y, R, 0.7f, 2.2f, 1.8f, Formes.argb(EXTRACTION, 0.85f));
        }
    }

    private void pingsFormes(float e) {
        for (int i = 0; i < PingsClient.n; i++) {
            PingsClient.Visuel v = PingsClient.PINGS[i];
            float X = sx(v.x), Y = sy(v.z);
            if (horsEcran(X, Y, 10 * e)) continue;
            Formes.anneau(X, Y, 7f * e, 0.7f, Formes.argb(v.type.couleur, 0.5f));
        }
    }

    private void joueurFormes(float e) {
        float X = sx(px), Y = sy(pz);
        Formes.cone(X, Y, 50f * e, cap, 0.9f, Formes.argb(0xFFFFFF, 0.22f));
    }

    private int couleurClan() {
        return monClan != null ? monClan.couleur() : Ui.accent();
    }

    /** Membres du clan : petit point de la couleur du clan, liseré blanc et ombre douce (icônes lissées). */
    private void alliesIcones(float e) {
        int c = couleurClan();
        for (Sources.Allie a : allies) {
            float X = sx(a.x()), Y = sy(a.z());
            if (horsEcran(X, Y, 6)) continue;
            Icones.icone(Icones.OMBRE, X, Y, 4.6f * e, Formes.argb(0x000000, 0.5f));
            Icones.icone(Icones.DISQUE, X, Y, 3.7f * e, 0xFFFFFFFF);
            Icones.icone(Icones.DISQUE, X, Y, 2.9f * e, 0xFF000000 | c);
        }
    }

    /** Coins de l'écran un peu assombris. */
    private void vignette() {
        float l = Math.min(width, height) * 0.22f;
        int a = 0x5A000000, z = 0;
        Formes.degrade(0, 0, width, l, a, a, z, z);
        Formes.degrade(0, height - l, width, height, z, z, a, a);
        Formes.degrade(0, 0, l, height, a, z, z, a);
        Formes.degrade(width - l, 0, width, height, z, a, a, z);
    }

    // =================================================================== calques : icônes
    private void drapeauxIcones(float e) {
        float a = visibiliteDrapeaux();
        if (a <= 0.01f) return;
        for (Sources.Zone z : zones) {
            if (!z.drapeau()) continue;
            float X = sx(z.drapeauX() + 0.5), Y = sy(z.drapeauZ() + 0.5);
            if (horsEcran(X, Y, 10)) continue;
            Icones.pastille(Icones.DRAPEAU, X, Y, R_DRAPEAU * e, couleurZone(z), a);
        }
    }

    private void lieuxIcones(float e) {
        for (Lieu l : lieuxIci) {
            if (l.type().texteSeul()) continue;
            float X = sx(l.x() + 0.5), Y = sy(l.z() + 0.5);
            if (horsEcran(X, Y, 10)) continue;
            Icones.pastille(Icones.index(l.type().icone), X, Y, R_LIEU * e, l.type().couleur, visibilite(l));
        }
    }

    private void extractionsIcones(float e) {
        for (Sources.Extraction x : ouvertes) {
            float X = sx(x.x()), Y = sy(x.z());
            if (horsEcran(X, Y, 10)) continue;
            Icones.pastille(Icones.SORTIE, X, Y, R_EXTRACTION * e, EXTRACTION, 1f);
        }
    }

    /** Pings : losange de la couleur du type, fin liseré blanc et ombre douce (plus de gros contour noir). */
    private void pingsIcones(float e) {
        for (int i = 0; i < PingsClient.n; i++) {
            PingsClient.Visuel v = PingsClient.PINGS[i];
            float X = sx(v.x), Y = sy(v.z);
            if (horsEcran(X, Y, 10)) continue;
            Icones.icone(Icones.OMBRE, X, Y, 6f * e, Formes.argb(0x000000, 0.5f));
            Icones.icone(Icones.LOSANGE, X, Y, 4.9f * e, Formes.argb(0xFFFFFF, 0.9f));
            Icones.icone(Icones.LOSANGE, X, Y, 4.2f * e, 0xFF000000 | v.type.couleur);
        }
    }

    /** Le joueur : halo léger, pastille sombre au bord blanc fin et flèche dans sa direction. */
    private void joueurIcones(float e) {
        float X = sx(px), Y = sy(pz);
        Icones.icone(Icones.HALO, X, Y, 9f * e, Formes.argb(0xFFFFFF, 0.22f));
        Icones.icone(Icones.OMBRE, X, Y, 7f * e, Formes.argb(0x000000, 0.55f));
        Icones.icone(Icones.PASTILLE, X, Y, 6.2f * e, Formes.argb(0x101319, 0.94f));
        Icones.icone(Icones.PASTILLE_BORD, X, Y, 6.2f * e, 0xFFFFFFFF);
        Icones.tournee(Icones.FLECHE, X, Y, 3.6f * e, cap, 0xFFFFFFFF);
    }

    private void zonesBannieres(float e) {
        for (Sources.Zone z : zones) {
            if (z.libre() || z.proprioBanniere() == null || k < 0.12f) continue;
            float X = sx(z.x() + 0.5), Y = sy(z.z() + 0.5);
            if (horsEcran(X, Y, 12) || sousPanneau(X, Y)) continue;
            int w = Math.round(8 * e), h = Math.round(12 * e);
            Sources.banniere(gr, z.proprioBanniere(), Math.round(X - w / 2f), Math.round(Y - 26 * e), w, h);
        }
    }

    // =================================================================== calques : étiquettes
    /** GuiGraphics de l'image en cours (les étiquettes sont dessinées dans un seul envoi). */
    private GuiGraphics gr;

    /** Réserve la place d'une étiquette (centre, largeur, hauteur) ; faux si elle en chevaucherait une autre. */
    private boolean placer(float cx, float cy, float w, float h) {
        float x0 = cx - w / 2 - 1.5f, y0 = cy - h / 2 - 0.5f, x1 = cx + w / 2 + 1.5f, y1 = cy + h / 2 + 0.5f;
        for (float[] p : places) if (x0 < p[2] && x1 > p[0] && y0 < p[3] && y1 > p[1]) return false;
        places.add(new float[]{x0, y0, x1, y1});
        return true;
    }

    /** Étiquette posée si la place est libre (halo sombre, centrée en (X, Y)). */
    private boolean etiquette(String s, ResourceLocation police, float X, float Y, int argb, float echelle, float espacement,
                              boolean capitales, float halo) {
        // presque transparente : rien (Minecraft dessine opaque un texte d'alpha < 4)
        if ((argb >>> 24) < 10) return false;
        float w = Texte.largeurEtiquette(font, s, police, echelle, espacement, capitales), h = Texte.em(police) * echelle;
        if (sousPanneau(X - w / 2, Y) || sousPanneau(X + w / 2, Y) || !placer(X, Y, w, h)) return false;
        textes.add(() -> Texte.etiquette(gr, font, s, police, X, Y, argb, echelle, espacement, capitales, halo));
        return true;
    }

    /**
     * Noms des lieux. villes = vrai : villes seulement (posées les premières, en grandes capitales qui grossissent avec le
     * zoom) ; faux : villages et points d'intérêt (après les territoires).
     */
    private void lieuxEtiquettes(float e, boolean villes) {
        float z = zoom01();
        for (Lieu l : lieuxIci) {
            if ((l.type() == Lieu.Type.VILLE) != villes) continue;
            float X = sx(l.x() + 0.5), Y = sy(l.z() + 0.5);
            if (horsEcran(X, Y, 60)) continue;
            switch (l.type()) {
                case VILLE -> etiquette(l.nom(), Ui.BOLD, X, Y, 0xFFFFFFFF, (9.5f + 4.5f * z) / 11f, 1.1f + 0.4f * z, true, 0.9f);
                // villages : seulement quand on s'approche, en fondu
                case VILLAGE -> etiquette(l.nom(), Ui.BOLD, X, Y, Formes.argb(0xE1E5EC, visibilite(l)), (8.2f + 2.6f * z) / 11f, 0, false, 0.8f);
                default -> {
                    // nom sous l'icône : grands lieux dès la vue d'ensemble, les autres de près, toujours en fondu
                    boolean grand = l.type() == Lieu.Type.AERODROME || l.type() == Lieu.Type.MILITAIRE || l.type() == Lieu.Type.PORT;
                    float a = (grand ? 1f : fondu(z, 0.32f, 0.5f)) * visibilite(l);
                    etiquette(l.nom(), Ui.BOLD, X, Y + R_LIEU * e + 5f, Formes.argb(l.type().couleur, a), 0.73f, 0, false, 0.7f);
                }
            }
        }
    }

    /** Texte de la pastille d'une zone (sous son nom) et sa couleur. */
    private String pastilleZone(Sources.Zone z) {
        return switch (z.etat()) {
            case "CAPTURE" -> "[" + z.capteurTag() + "] " + Math.round(z.progression() * 100) + " %";
            case "CONTESTE" -> I18n.get("heraliz_carte.etat.contestee");
            case "PROTEGEE" -> I18n.get("heraliz_carte.sous.protegee", z.proprioTag(), duree(z.protegeeMs()));
            default -> z.libre() ? I18n.get("heraliz_carte.etat.libre") : "[" + z.proprioTag() + "] " + libelleEtat(z);
        };
    }

    private int couleurPastille(Sources.Zone z) {
        return switch (z.etat()) {
            case "CAPTURE" -> z.capteurCouleur();
            case "CONTESTE" -> 0xFF8080;
            case "PROTEGEE" -> PROTEGEE;
            default -> z.libre() ? 0xB9C8E6 : couleurZone(z);
        };
    }

    /**
     * Territoires : nom au centre (omis si une ville du même endroit l'a déjà écrit) et pastille d'état dessous, sur fond
     * sombre ; « Drapeau » sous le drapeau quand on zoome.
     */
    private void zonesEtiquettes(float e) {
        float z01 = zoom01();
        for (Sources.Zone z : zones) {
            float X = sx(z.x() + 0.5), Y = sy(z.z() + 0.5);
            if (horsEcran(X, Y, 60)) continue;
            int c = couleurZone(z);
            float ad = visibiliteDrapeaux();
            if (ad > 0.04f && z.drapeau()) {
                etiquette(I18n.get("heraliz_carte.panneau.drapeau"), Ui.BOLD, sx(z.drapeauX() + 0.5), sy(z.drapeauZ() + 0.5) + R_DRAPEAU * e + 4.5f,
                        Formes.argb(c, ad), S_CAPS, 0.5f, true, 0.6f);
            }
            etiquette(z.nom(), Ui.BOLD, X, Y, 0xFFFFFFFF, (9.5f + 4.5f * z01) / 11f, 1.1f + 0.4f * z01, true, 0.9f);
            // pastille d'état : fond sombre, contour de la couleur, capitales. En vue d'ensemble, seulement les états qui
            // demandent l'attention (capture, contestée) ; les autres apparaissent en fondu quand on s'approche
            String s = pastilleZone(z);
            int cs = couleurPastille(z);
            boolean urgent = "CAPTURE".equals(z.etat()) || "CONTESTE".equals(z.etat());
            float ap = urgent ? 1f : fondu(z01, 0.15f, 0.35f);
            if (ap < 0.05f) continue;
            // sous le nom (demi-hauteur du nom + 2) : avant, la pastille touchait le nom d'une ville du même endroit et
            // n'était jamais posée
            float w = Texte.largeurEspacee(font, s, Ui.BOLD, S_CAPS, 0.5f) + 8, h = 10.5f, py = Y + (9.5f + 4.5f * z01) / 2 + 2 + h / 2;
            if (sousPanneau(X - w / 2, py) || sousPanneau(X + w / 2, py) || !placer(X, py, w, h)) continue;
            fonds.add(() -> {
                Ui.soft(gr, Math.round(X - w / 2), Math.round(py - h / 2), Math.round(w), Math.round(h), Formes.argb(0x0A0C10, 0.85f * ap));
                Ui.softOutline(gr, Math.round(X - w / 2) + 1, Math.round(py - h / 2) + 1, Math.round(w) - 2, Math.round(h) - 2,
                        Formes.argb(cs, 0.5f * ap));
            });
            textes.add(() -> Texte.espaceeCentree(gr, font, s, Ui.BOLD, X, py - 11 * S_CAPS / 2, Formes.argb(cs, ap), S_CAPS, 0.5f, false));
        }
    }

    private void extractionsEtiquettes(float e) {
        for (Sources.Extraction x : ouvertes) {
            float X = sx(x.x()), Y = sy(x.z());
            if (horsEcran(X, Y, 60)) continue;
            String t = I18n.get("heraliz_carte.extraction", x.nom());
            int s = x.secondesRestantes(heureDeJeu);
            if (s >= 0) t += " · " + duree(s * 1000L);
            etiquette(t, Ui.BOLD, X, Y + Math.max(x.rayon() * k, 7.5f * e) + 5.5f, Formes.argb(EXTRACTION_TEXTE, 1f), 0.73f, 0, false, 0.7f);
        }
    }

    private void pingsEtiquettes(float e) {
        for (int i = 0; i < PingsClient.n; i++) {
            PingsClient.Visuel v = PingsClient.PINGS[i];
            float X = sx(v.x), Y = sy(v.z);
            if (horsEcran(X, Y, 60)) continue;
            String t = I18n.get(v.type.cle()) + " · " + metres(Math.hypot(v.x - px, v.z - pz));
            etiquette(t, Ui.BOLD, X, Y - 7.5f * e - 5, Formes.argb(v.type.couleur, 1f), 0.73f, 0, false, 0.7f);
        }
    }

    private void alliesEtiquettes(float e) {
        for (Sources.Allie a : allies) {
            float X = sx(a.x()), Y = sy(a.z());
            if (horsEcran(X, Y, 60)) continue;
            float w = Texte.largeur(font, a.nom(), Ui.BOLD, 0.73f);
            etiquette(a.nom(), Ui.BOLD, X + 4.8f * e + w / 2, Y, 0xFFFFFFFF, 0.73f, 0, false, 0.7f);
        }
    }

    // =================================================================== panneaux
    /** Vrai si le point de l'écran tombe sous un panneau (les étiquettes de la carte n'y sont pas dessinées). */
    private boolean sousPanneau(float x, float y) {
        if (x < MARGE + PANNEAU_L + 4) return true;
        if (y > basY0 - 2 && x > basX0 - 2 && x < basX1 + 2) return true;
        if (x > vueX0 - 2 && y < MARGE + VUE_H + 2) return true;
        if (x > width - MARGE - BOUTON - 3 && y > height - MARGE - BAS_H - 6 - 2 * BOUTON - 3) return true;
        return selection != null && x > width - MARGE - FICHE_L - 2 && y > MARGE + VUE_H + 4 && y < ficheY1 + 2;
    }

    private void messageFond(GuiGraphics g) {
        boolean absent = fond == null;
        String titre = I18n.get(absent ? "heraliz_carte.fond.absent.titre" : "heraliz_carte.fond.autre_monde.titre");
        String texte = I18n.get(absent ? "heraliz_carte.fond.absent" : "heraliz_carte.fond.autre_monde");
        float w = Math.max(Texte.largeurEspacee(font, titre, Ui.BOLD, 1f, 1.4f), Texte.largeur(font, texte, Ui.FONT, S_TEXTE)) + 28;
        float x = ox - w / 2, y = MARGE + 34;
        Ui.glass(g, Math.round(x), Math.round(y), Math.round(w), 40);
        Texte.espaceeCentree(g, font, titre, Ui.BOLD, ox, y + 9, Formes.argb(TEXTE, 1f), 1f, 1.4f, false);
        Texte.ecrire(g, font, texte, Ui.FONT, ox - Texte.largeur(font, texte, Ui.FONT, S_TEXTE) / 2, y + 25, Formes.argb(GRIS, 1f), S_TEXTE);
    }

    /** Petit interrupteur Activé / Désactivé (pastille arrondie et bouton rond). */
    private static void interrupteur(float x, float y, boolean actif, int couleur, boolean survol) {
        float w = 13, h = 7.5f, r = h / 2;
        int fond = actif ? Formes.argb(couleur, survol ? 1f : 0.88f) : Formes.argb(0xFFFFFF, survol ? 0.16f : 0.10f);
        Formes.rect(x + r, y, x + w - r, y + h, fond);
        Formes.disque(x + r, y + r, r, fond);
        Formes.disque(x + w - r, y + r, r, fond);
        Formes.disque(actif ? x + w - r : x + r, y + r, r - 0.95f, actif ? 0xFFFFFFFF : 0xFF969BA5);
    }

    private void panneauGauche(GuiGraphics g, int mx, int my) {
        float x = MARGE, y = MARGE, w = PANNEAU_L, h = height - 2 * MARGE;
        Ui.glass(g, (int) x, (int) y, (int) w, (int) h);
        float xi = x + 11, xd = x + w - 11;
        // ---- calques : survol, interrupteurs et icônes, puis textes
        String[] cles = {"heraliz_carte.calque.territoires", "heraliz_carte.calque.extractions", "heraliz_carte.calque.lieux",
                "heraliz_carte.calque.clan", "heraliz_carte.calque.pings", "heraliz_carte.calque.grille"};
        int nClan = monClan == null ? 0 : allies.size() + 1;
        String[] comptes = {Sources.clansPresents() ? String.valueOf(zones.size()) : "—", String.valueOf(ouvertes.size()),
                String.valueOf(lieuxIci.size()), nClan == 0 ? "—" : String.valueOf(nClan), String.valueOf(PingsClient.n), Ui.nombre(GRILLE) + " m"};
        int[] couleurs = {couleurClan(), EXTRACTION, 0xF5D76E, couleurClan(), ROUGE, NEUTRE};
        int[] icones = {Icones.DRAPEAU, Icones.SORTIE, Icones.index("etoile"), Icones.DISQUE, Icones.LOSANGE, Icones.CIBLE};
        boolean[] actifs = {territoires, extractions, lieux, clan, pings, grille};
        Runnable[] bascules = {() -> territoires = !territoires, () -> extractions = !extractions, () -> lieux = !lieux, () -> clan = !clan,
                () -> pings = !pings, () -> grille = !grille};
        float ys = y + 44;
        float yl = ys + 8;
        float x0 = x + 5, x1 = x + w - 5;
        boolean[] survols = new boolean[cles.length];
        for (int i = 0; i < cles.length; i++) {
            float ly = yl + i * LIGNE_CALQUE;
            survols[i] = mx >= x0 && mx < x1 && my >= ly && my < ly + LIGNE_CALQUE - 1;
            if (survols[i]) Ui.soft(g, Math.round(x0), Math.round(ly), Math.round(x1 - x0), Math.round(LIGNE_CALQUE - 1), 0x12FFFFFF);
            Runnable b = bascules[i];
            cibles.add(new Cible(x0, ly, x1, ly + LIGNE_CALQUE - 1, () -> {
                b.run();
                SonsUi.clic(0.4f);
            }));
        }
        Formes.debut(g);
        for (int i = 0; i < cles.length; i++) {
            float ly = yl + i * LIGNE_CALQUE;
            interrupteur(xd - 13, ly + (LIGNE_CALQUE - 1 - 7.5f) / 2, actifs[i], couleurs[i], survols[i]);
        }
        Formes.fin();
        Icones.debut(g);
        for (int i = 0; i < cles.length; i++) {
            float ly = yl + i * LIGNE_CALQUE;
            Icones.icone(icones[i], xi + 3.5f, ly + (LIGNE_CALQUE - 1) / 2f, 3.6f, Formes.argb(couleurs[i], actifs[i] ? 1f : 0.45f));
        }
        Icones.fin();
        Texte.espacee(g, font, I18n.get("heraliz_carte.titre"), Ui.BOLD, xi, y + 10, Formes.argb(TEXTE, 1f), S_TITRE, 2.6f, false);
        String nom = LieuxClient.nomCarte().isEmpty() ? "HeraliZ" : LieuxClient.nomCarte();
        Texte.ecrire(g, font, Texte.couper(font, I18n.get("heraliz_carte.monde", nom), Ui.FONT, S_PETIT, xd - xi), Ui.FONT, xi, y + 26,
                Formes.argb(GRIS, 1f), S_PETIT);
        section(g, I18n.get("heraliz_carte.calques"), xi, ys - 3.5f, xd);
        for (int i = 0; i < cles.length; i++) {
            float ly = yl + i * LIGNE_CALQUE, ty = ly + (LIGNE_CALQUE - 1) / 2f - 11 * S_TEXTE / 2;
            String lib = Texte.couper(font, I18n.get(cles[i]), Ui.FONT, S_TEXTE, xd - 13 - 26 - (xi + 11));
            Texte.ecrire(g, font, lib, Ui.FONT, xi + 11, ty, Formes.argb(actifs[i] ? TEXTE : GRIS, 1f), S_TEXTE);
            Texte.droite(g, font, comptes[i], Ui.FONT, xd - 18, ty + 0.3f, Formes.argb(GRIS, 1f), S_PETIT);
        }
        // ---- territoires
        float yt = yl + LIGNE_CALQUE * cles.length + 8;
        section(g, I18n.get("heraliz_carte.territoires"), xi, yt, xd);
        listeY0 = yt + 8;
        listeY1 = y + h - 44 - (zonesHors > 0 ? 12 : 0);
        if (zones.isEmpty()) {
            Texte.ecrire(g, font, Texte.couper(font, I18n.get("heraliz_carte.aucun_territoire"), Ui.FONT, S_PETIT, xd - xi), Ui.FONT, xi,
                    listeY0 + 4, Formes.argb(PALE, 1f), S_PETIT);
        } else {
            int visibles = Math.max(1, (int) ((listeY1 - listeY0) / LIGNE_ZONE));
            defilement = Mth.clamp(defilement, 0, Math.max(0, zones.size() - visibles));
            int debut = (int) defilement;
            g.enableScissor((int) x, (int) listeY0, (int) (x + w), (int) listeY1);
            for (int i = debut; i < zones.size() && i < debut + visibles + 1; i++) {
                Sources.Zone z = zones.get(i);
                float ly = listeY0 + (i - debut) * LIGNE_ZONE;
                ligneZone(g, mx, my, z, x0, ly, x1);
            }
            g.disableScissor();
            if (zones.size() > visibles) {
                // ascenseur discret
                float hb = listeY1 - listeY0, part = visibles / (float) zones.size();
                float yb = listeY0 + hb * (defilement / zones.size());
                g.fill((int) (x + w - 4), (int) yb, (int) (x + w - 3), (int) (yb + hb * part), 0x40FFFFFF);
            }
        }
        if (zonesHors > 0) {
            // territoires de l'ancienne carte (centre hors de la bordure) : cachés, à reposer en jeu
            Texte.ecrire(g, font, Texte.couper(font, I18n.get("heraliz_carte.territoires_hors", String.valueOf(zonesHors)), Ui.FONT, S_PETIT,
                    xd - xi), Ui.FONT, xi, listeY1 + 2, Formes.argb(AMBRE, 0.9f), S_PETIT);
        }
        // ---- mon clan
        float yc = y + h - 38;
        Ui.hairline(g, (int) xi, (int) xd, (int) yc, 0xFFFFFF, 34, false, false);
        if (monClan != null) {
            Sources.banniere(g, monClan.banniere(), (int) xi, (int) yc + 7, 13, 20);
            String n = Texte.couper(font, monClan.nom(), Ui.BOLD, S_NOM, xd - xi - 50);
            Texte.ecrire(g, font, n, Ui.BOLD, xi + 19, yc + 8, Formes.argb(TEXTE, 1f), S_NOM);
            Texte.ecrire(g, font, "[" + monClan.tag() + "]", Ui.FONT, xi + 22 + Texte.largeur(font, n, Ui.BOLD, S_NOM), yc + 8.4f,
                    Formes.argb(GRIS, 1f), S_TEXTE);
            String resume = I18n.get("heraliz_carte.clan.resume", String.valueOf(monClan.territoires()), String.valueOf(monClan.enLigne()));
            Texte.espacee(g, font, Texte.couper(font, resume, Ui.BOLD, S_CAPS, xd - xi - 30), Ui.BOLD, xi + 19, yc + 20, Formes.argb(GRIS, 1f),
                    S_CAPS, 0.6f, false);
        } else {
            Texte.espacee(g, font, I18n.get("heraliz_carte.clan.aucun"), Ui.BOLD, xi, yc + 14, Formes.argb(PALE, 1f), S_CAPS, 0.8f, false);
        }
    }

    /** Titre de section : capitales espacées et filet qui s'efface à droite. */
    private void section(GuiGraphics g, String titre, float x, float y, float xd) {
        Texte.espacee(g, font, titre, Ui.BOLD, x, y - 11 * S_CAPS / 2, Formes.argb(GRIS, 1f), S_CAPS, 1.1f, false);
        float tw = Texte.largeurEspacee(font, titre, Ui.BOLD, S_CAPS, 1.1f);
        Ui.hairline(g, (int) (x + tw + 6), (int) xd, Math.round(y), 0xFFFFFF, 46, false, true);
    }

    private int couleurEtat(Sources.Zone z) {
        return switch (z.etat()) {
            case "CAPTURE" -> AMBRE;
            case "CONTESTE" -> ROUGE;
            case "PROTEGEE" -> PROTEGEE;
            case "TENUE" -> TENUE;
            default -> NEUTRE;
        };
    }

    private String libelleEtat(Sources.Zone z) {
        return I18n.get(switch (z.etat()) {
            case "CAPTURE" -> "heraliz_carte.etat.capture";
            case "CONTESTE" -> "heraliz_carte.etat.contestee";
            case "PROTEGEE" -> "heraliz_carte.etat.protegee";
            case "TENUE" -> "heraliz_carte.etat.tenue";
            default -> "heraliz_carte.etat.libre";
        });
    }

    private void ligneZone(GuiGraphics g, int mx, int my, Sources.Zone z, float x0, float y, float x1) {
        boolean sel = z.id().equals(selection);
        boolean survol = my >= listeY0 && my < listeY1 && mx >= x0 && mx < x1 && my >= y && my < y + LIGNE_ZONE - 1;
        if (sel) {
            Ui.soft(g, (int) x0, (int) y, (int) (x1 - x0), (int) LIGNE_ZONE - 1, 0x14FFFFFF);
            Ui.softOutline(g, (int) x0 + 1, (int) y + 1, (int) (x1 - x0) - 2, (int) LIGNE_ZONE - 3, 0x28FFFFFF);
        } else if (survol) {
            Ui.soft(g, (int) x0, (int) y, (int) (x1 - x0), (int) LIGNE_ZONE - 1, 0x0CFFFFFF);
        }
        int c = couleurZone(z);
        float milieu = y + (LIGNE_ZONE - 1) / 2f;
        g.fill((int) x0 + 4, Math.round(milieu - 4.5f), (int) x0 + 6, Math.round(milieu + 4.5f), 0xFF000000 | c);
        // état à droite : « 62 % » pendant une capture (court), sinon le libellé
        boolean capture = "CAPTURE".equals(z.etat());
        String etat = capture ? Math.round(z.progression() * 100) + " %" : libelleEtat(z);
        float le = Texte.largeurEspacee(font, etat, Ui.BOLD, S_CAPS, 0.6f);
        float lc = z.libre() ? 0 : Texte.largeurEspacee(font, z.proprioTag(), Ui.BOLD, S_CAPS, 0.5f) + 7;
        float maxNom = x1 - x0 - 16 - le - (lc > 0 ? lc + 6 : 0);
        String nom = Texte.couper(font, z.nom(), Ui.BOLD, S_NOM, maxNom);
        float ty = milieu - 11 * S_NOM / 2;
        Texte.ecrire(g, font, nom, Ui.BOLD, x0 + 11, ty, Formes.argb(TEXTE, 1f), S_NOM);
        if (lc > 0) {
            float xc = x0 + 14 + Texte.largeur(font, nom, Ui.BOLD, S_NOM);
            Ui.soft(g, Math.round(xc), Math.round(milieu - 5), Math.round(lc), 10, Formes.argb(c, 0.16f));
            Ui.softOutline(g, Math.round(xc) + 1, Math.round(milieu - 4), Math.round(lc) - 2, 8, Formes.argb(c, 0.55f));
            Texte.espacee(g, font, z.proprioTag(), Ui.BOLD, xc + 3.5f, milieu - 11 * S_CAPS / 2, Formes.argb(Ui.mix(c, 0xFFFFFF, 0.2f), 1f),
                    S_CAPS, 0.5f, false);
        }
        Texte.espaceeDroite(g, font, etat, Ui.BOLD, x1 - 6, milieu - 11 * S_CAPS / 2, Formes.argb(couleurEtat(z), 1f), S_CAPS, 0.6f);
        if (y >= listeY0 && y + LIGNE_ZONE <= listeY1 + 1) {
            cibles.add(new Cible(x0, y, x1, y + LIGNE_ZONE - 1, () -> {
                selection = z.id();
                centrer(z.x() + 0.5, z.z() + 0.5);
                SonsUi.clic(0.4f);
            }));
        }
    }

    private void basculeVue(GuiGraphics g, int mx, int my) {
        String sat = I18n.get("heraliz_carte.vue.satellite"), rel = I18n.get("heraliz_carte.vue.relief");
        float s = 0.73f;
        float ls = Texte.largeurEspacee(font, sat, Ui.BOLD, s, 1.3f) + 16, lr = Texte.largeurEspacee(font, rel, Ui.BOLD, s, 1.3f) + 16;
        float w = ls + lr + 6, x = width - MARGE - w, y = MARGE;
        vueX0 = x;
        Ui.glass(g, Math.round(x), (int) y, Math.round(w), (int) VUE_H);
        bouton(g, mx, my, sat, x + 3, y + 3, ls, VUE_H - 6, !relief, () -> relief = false);
        bouton(g, mx, my, rel, x + 3 + ls, y + 3, lr, VUE_H - 6, relief, () -> relief = true);
    }

    private void bouton(GuiGraphics g, int mx, int my, String texte, float x, float y, float w, float h, boolean actif, Runnable action) {
        boolean survol = mx >= x && mx < x + w && my >= y && my < y + h;
        if (actif) Ui.soft(g, Math.round(x), Math.round(y), Math.round(w), Math.round(h), 0x24FFFFFF);
        else if (survol) Ui.soft(g, Math.round(x), Math.round(y), Math.round(w), Math.round(h), 0x10FFFFFF);
        Texte.espaceeCentree(g, font, texte, Ui.BOLD, x + w / 2, y + h / 2 - 11 * 0.73f / 2, Formes.argb(actif ? 0xFFFFFF : GRIS, 1f), 0.73f,
                1.3f, false);
        cibles.add(new Cible(x, y, x + w, y + h, () -> {
            action.run();
            SonsUi.clic(0.4f);
        }));
    }

    /** Boutons + et − (au-dessus de la boussole). */
    private void boutonsZoom(GuiGraphics g, int mx, int my) {
        float x = width - MARGE - BOUTON, y = height - MARGE - BAS_H - 6 - 2 * BOUTON;
        Ui.glass(g, Math.round(x), Math.round(y), Math.round(BOUTON), Math.round(2 * BOUTON));
        String[] signes = {"+", "−"};
        float[] facteurs = {1.5f, 1f / 1.5f};
        for (int i = 0; i < 2; i++) {
            float by = y + i * BOUTON;
            boolean survol = mx >= x && mx < x + BOUTON && my >= by && my < by + BOUTON;
            if (survol) Ui.soft(g, Math.round(x + 2), Math.round(by + 2), Math.round(BOUTON - 4), Math.round(BOUTON - 4), 0x14FFFFFF);
            float s = 1.1f;
            Texte.ecrire(g, font, signes[i], Ui.BOLD, x + BOUTON / 2 - Texte.largeur(font, signes[i], Ui.BOLD, s) / 2, by + BOUTON / 2 - 11 * s / 2,
                    Formes.argb(survol ? 0xFFFFFF : TEXTE, 1f), s);
            float f = facteurs[i];
            cibles.add(new Cible(x, by, x + BOUTON, by + BOUTON, () -> {
                zoomer(f);
                SonsUi.clic(0.35f);
            }));
        }
        Ui.hairline(g, Math.round(x + 5), Math.round(x + BOUTON - 5), Math.round(y + BOUTON), 0xFFFFFF, 40, false, false);
    }

    @Nullable
    private Sources.Zone zone(String id) {
        for (Sources.Zone z : zones) if (z.id().equals(id)) return z;
        return null;
    }

    private void ficheZone(GuiGraphics g, int mx, int my) {
        Sources.Zone z = selection == null ? null : zone(selection);
        if (z == null) {
            ficheY1 = 0;
            return;
        }
        float w = FICHE_L, x = width - MARGE - w, y = MARGE + VUE_H + 6;
        int c = couleurZone(z), ce = couleurEtat(z);
        // contenu, pour connaître la hauteur
        List<String[]> lignes = new ArrayList<>();
        List<Integer> couleurs = new ArrayList<>();
        lignes.add(new String[]{I18n.get("heraliz_carte.panneau.proprietaire"),
                z.libre() ? I18n.get("heraliz_carte.panneau.aucun") : z.proprioNom()});
        couleurs.add(z.libre() ? TEXTE : c);
        if (!z.libre()) {
            lignes.add(new String[]{I18n.get("heraliz_carte.panneau.tenue_depuis"), duree(z.tenueDepuisMs())});
            couleurs.add(TEXTE);
        }
        if (z.protegeeMs() > 0) {
            lignes.add(new String[]{I18n.get("heraliz_carte.panneau.protection"), duree(z.protegeeMs())});
            couleurs.add(PROTEGEE);
        }
        lignes.add(new String[]{I18n.get("heraliz_carte.panneau.bonus"), I18n.get(z.libre() ? "heraliz_carte.panneau.bonus_capture"
                : "heraliz_carte.panneau.bonus_valeur", String.valueOf(z.bonusLoot()))});
        couleurs.add(z.libre() ? TEXTE : VERT);
        lignes.add(new String[]{I18n.get("heraliz_carte.panneau.niveau"), "T" + Math.max(1, z.valeur())});
        couleurs.add(TEXTE);
        if (z.monClan()) {
            boolean pret = z.ravitaillementMs() == 0;
            lignes.add(new String[]{I18n.get("heraliz_carte.panneau.ravitaillement"), pret ? I18n.get("heraliz_carte.panneau.pret")
                    : I18n.get("heraliz_carte.panneau.dans", duree(z.ravitaillementMs()))});
            couleurs.add(pret ? VERT : TEXTE);
        }
        lignes.add(new String[]{I18n.get("heraliz_carte.panneau.rayon"), metres(z.rayon())});
        couleurs.add(TEXTE);
        lignes.add(new String[]{I18n.get("heraliz_carte.panneau.distance"), metres(Math.hypot(z.x() + 0.5 - px, z.z() + 0.5 - pz))});
        couleurs.add(TEXTE);
        boolean capture = "CAPTURE".equals(z.etat()) && !z.capteurTag().isEmpty();
        float h = 44 + (capture ? 32 : 0) + lignes.size() * 14 + 30;
        ficheY1 = y + h;
        Ui.glass(g, Math.round(x), Math.round(y), Math.round(w), Math.round(h));
        float xi = x + 10, xd = x + w - 10;
        // en-tête : bannière (propriétaire, sinon clan qui capture), nom, état, niveau
        Object ban = !z.libre() ? z.proprioBanniere() : capture ? z.capteurBanniere() : null;
        if (ban != null) {
            Sources.banniere(g, ban, Math.round(xi), Math.round(y + 9), 15, 24);
        } else {
            Icones.debut(g);
            Icones.icone(Icones.ANNEAU, xi + 7.5f, y + 20, 7f, Formes.argb(NEUTRE, 0.9f));
            Icones.fin();
        }
        String nom = Texte.couper(font, z.nom(), Ui.BOLD, S_TITRE_FICHE, xd - xi - 22);
        Texte.espacee(g, font, nom, Ui.BOLD, xi + 22, y + 9, Formes.argb(TEXTE, 1f), S_TITRE_FICHE, 1.3f, false);
        float pw = pastille(g, libelleEtat(z), xi + 22, y + 24, ce);
        pastille(g, "T" + Math.max(1, z.valeur()), xi + 26 + pw, y + 24, 0xB478FF);
        float yy = y + 42;
        if (capture) {
            String t = I18n.get("heraliz_carte.panneau.prennent", z.capteurNom());
            Texte.ecrire(g, font, Texte.couper(font, t, Ui.FONT, S_TEXTE, xd - xi), Ui.FONT, xi, yy, Formes.argb(z.capteurCouleur(), 1f), S_TEXTE);
            Ui.bar(g, Math.round(xi), Math.round(yy + 12), Math.round(xd - xi), 3, z.progression(), z.capteurCouleur());
            Texte.espacee(g, font, Math.round(z.progression() * 100) + " %", Ui.BOLD, xi, yy + 18, Formes.argb(GRIS, 1f), S_CAPS, 0.7f, false);
            yy += 32;
        }
        for (int i = 0; i < lignes.size(); i++) {
            String[] l = lignes.get(i);
            float ty = yy + 7 - 11 * S_TEXTE / 2;
            Texte.ecrire(g, font, l[0], Ui.FONT, xi, ty, Formes.argb(GRIS, 1f), S_TEXTE);
            Texte.droite(g, font, Texte.couper(font, l[1], Ui.BOLD, S_TEXTE, (xd - xi) * 0.55f), Ui.BOLD, xd, ty, Formes.argb(couleurs.get(i), 1f),
                    S_TEXTE);
            Ui.hairline(g, Math.round(xi), Math.round(xd), Math.round(yy + 13.5f), 0xFFFFFF, 16, false, false);
            yy += 14;
        }
        // bouton : lancer le ravitaillement (zone de son clan, prêt, permission) ou marquer la zone pour son clan
        float by = yy + 7, bh = 17;
        boolean ravitailler = z.monClan() && z.ravitaillementMs() == 0 && z.peutRavitailler();
        String libelle = I18n.get(ravitailler ? "heraliz_carte.panneau.lancer" : "heraliz_carte.panneau.marquer");
        boolean survol = mx >= xi && mx < xd && my >= by && my < by + bh;
        if (ravitailler) {
            Ui.soft(g, Math.round(xi), Math.round(by), Math.round(xd - xi), Math.round(bh), 0xFF000000 | (survol ? Ui.mix(c, 0xFFFFFF, 0.15f) : c));
        } else {
            Ui.soft(g, Math.round(xi), Math.round(by), Math.round(xd - xi), Math.round(bh), survol ? 0x1FFFFFFF : 0x10FFFFFF);
            Ui.softOutline(g, Math.round(xi) + 1, Math.round(by) + 1, Math.round(xd - xi) - 2, Math.round(bh) - 2, 0x48FFFFFF);
        }
        String lib = Texte.couper(font, libelle, Ui.BOLD, 0.73f, xd - xi - 10);
        Texte.espaceeCentree(g, font, lib, Ui.BOLD, (xi + xd) / 2, by + bh / 2 - 11 * 0.73f / 2, ravitailler ? 0xFF0D0F14 : 0xFFFFFFFF, 0.73f,
                1.1f, false);
        cibles.add(new Cible(xi, by, xd, by + bh, () -> {
            if (ravitailler) Sources.ravitailler(z.id());
            else marquer(PingType.ALLER, z.drapeau() ? z.drapeauX() : z.x(), z.drapeau() ? z.drapeauZ() : z.z());
            SonsUi.clic(0.5f);
        }));
        // un clic ailleurs dans la fiche ne traverse pas vers la carte
        cibles.add(0, new Cible(x, y, x + w, y + h, () -> {
        }));
    }

    /** Pastille d'état (capitales sur fond teinté) ; rend sa largeur. */
    private float pastille(GuiGraphics g, String texte, float x, float y, int rgb) {
        float w = Texte.largeurEspacee(font, texte, Ui.BOLD, S_CAPS, 0.6f) + 10, h = 10.5f;
        Ui.soft(g, Math.round(x), Math.round(y), Math.round(w), Math.round(h), Formes.argb(rgb, 0.16f));
        Ui.softOutline(g, Math.round(x) + 1, Math.round(y) + 1, Math.round(w) - 2, Math.round(h) - 2, Formes.argb(rgb, 0.5f));
        Texte.espacee(g, font, texte, Ui.BOLD, x + 5, y + h / 2 - 11 * S_CAPS / 2, Formes.argb(Ui.mix(rgb, 0xFFFFFF, 0.3f), 1f), S_CAPS, 0.6f, false);
        return w;
    }

    private void barreBas(GuiGraphics g, int mx, int my) {
        double wx = mondeX(mx), wz = mondeZ(my);
        String xs = Ui.nombre((long) Math.floor(wx)), zs = Ui.nombre((long) Math.floor(wz));
        float s = 0.86f, sl = 0.73f;
        // largeurs réservées : la barre ne bouge pas quand les nombres changent sous la souris
        float lnum = Math.max(Texte.largeur(font, "-00 000", Ui.FONT, s), Math.max(Texte.largeur(font, xs, Ui.FONT, s), Texte.largeur(font, zs, Ui.FONT, s)));
        // échelle : la plus grande longueur ronde qui tient dans ~44 unités
        float metres = ECHELLES[0];
        for (float m : ECHELLES) if (m * k <= 44) metres = m;
        float barre = metres * k;
        String echelle = metres >= 1000 ? Ui.nombre((long) (metres / 1000)) + " km" : Ui.nombre((long) metres) + " m";
        String carre = null;
        if (lim != null) {
            carre = dansCarte(wx, wz) ? colonne((int) Math.floor((wx - grilleX0()) / GRILLE)) + (int) (Math.floor((wz - grilleZ0()) / GRILLE) + 1) : "—";
        }
        String touche = PartieClient.TOUCHE.getTranslatedKeyMessage().getString();
        String[][] aides = {{I18n.get("heraliz_carte.bas.clic"), I18n.get("heraliz_carte.bas.choisir")},
                {I18n.get("heraliz_carte.bas.clic_droit"), I18n.get("heraliz_carte.bas.marquer")},
                {I18n.get("heraliz_carte.bas.espace"), I18n.get("heraliz_carte.bas.sur_moi")},
                {touche, I18n.get("heraliz_carte.bas.fermer")}};
        String lcarreTitre = I18n.get("heraliz_carte.bas.carre");
        // largeurs
        float lx = Texte.largeur(font, "X", Ui.BOLD, sl) + 4 + lnum + 8 + Texte.largeur(font, "Z", Ui.BOLD, sl) + 4 + lnum;
        float lcarre = Math.max(Texte.largeur(font, "AA00", Ui.BOLD, s), carre == null ? 0 : Texte.largeur(font, carre, Ui.BOLD, s));
        float lc = carre == null ? 0 : Texte.largeurEspacee(font, lcarreTitre, Ui.BOLD, S_CAPS, 0.8f) + 5 + lcarre;
        float le = 44 + 5 + Texte.largeur(font, "2 000 m", Ui.FONT, S_TEXTE);
        float sep = 16;
        float base = 20 + lx + sep + (carre == null ? 0 : lc + sep) + le;
        float dispo = width - MARGE * 3 - PANNEAU_L - BOUTON - 8;
        // aides : autant qu'il en tient
        List<String[]> montrees = new ArrayList<>();
        float la = 0;
        for (String[] a : aides) {
            float l = Texte.largeurEspacee(font, a[0], Ui.BOLD, S_CAPS, 0.5f) + 8 + 3 + Texte.largeur(font, a[1], Ui.FONT, S_PETIT) + 9;
            if (base + sep + la + l > dispo) break;
            montrees.add(a);
            la += l;
        }
        float w = base + (montrees.isEmpty() ? 0 : sep + la);
        float centre = (MARGE + PANNEAU_L + width - BOUTON - MARGE) / 2f;
        float x = Math.max(MARGE * 2 + PANNEAU_L, centre - w / 2), y = height - MARGE - BAS_H;
        basX0 = x;
        basX1 = x + w;
        basY0 = y;
        Ui.glass(g, Math.round(x), Math.round(y), Math.round(w), Math.round(BAS_H));
        float cxb = x + 10, ym = y + BAS_H / 2f;
        float tyv = ym - 11 * s / 2, tyl = ym - 11 * sl / 2;
        Texte.ecrire(g, font, "X", Ui.BOLD, cxb, tyl, Formes.argb(GRIS, 1f), sl);
        cxb += Texte.largeur(font, "X", Ui.BOLD, sl) + 4;
        Texte.ecrire(g, font, xs, Ui.FONT, cxb, tyv, Formes.argb(TEXTE, 1f), s);
        cxb += lnum + 8;
        Texte.ecrire(g, font, "Z", Ui.BOLD, cxb, tyl, Formes.argb(GRIS, 1f), sl);
        cxb += Texte.largeur(font, "Z", Ui.BOLD, sl) + 4;
        Texte.ecrire(g, font, zs, Ui.FONT, cxb, tyv, Formes.argb(TEXTE, 1f), s);
        cxb += lnum;
        cxb = separateur(g, cxb, y, sep);
        if (carre != null) {
            Texte.espacee(g, font, lcarreTitre, Ui.BOLD, cxb, ym - 11 * S_CAPS / 2, Formes.argb(GRIS, 1f), S_CAPS, 0.8f, false);
            cxb += Texte.largeurEspacee(font, lcarreTitre, Ui.BOLD, S_CAPS, 0.8f) + 5;
            Texte.ecrire(g, font, carre, Ui.BOLD, cxb, tyv, Formes.argb(TEXTE, 1f), s);
            cxb += lcarre;
            cxb = separateur(g, cxb, y, sep);
        }
        // barre d'échelle : trait blanc avec deux petites pattes
        int bx0 = Math.round(cxb), bx1 = Math.round(cxb + barre), byb = Math.round(ym + 2);
        g.fill(bx0, byb, bx1, byb + 1, 0xFFFFFFFF);
        g.fill(bx0, byb - 4, bx0 + 1, byb + 1, 0xFFFFFFFF);
        g.fill(bx1 - 1, byb - 4, bx1, byb + 1, 0xFFFFFFFF);
        Texte.ecrire(g, font, echelle, Ui.FONT, cxb + barre + 5, ym - 11 * S_TEXTE / 2, Formes.argb(TEXTE, 1f), S_TEXTE);
        cxb += le;
        if (!montrees.isEmpty()) {
            cxb = separateur(g, cxb, y, sep);
            for (String[] a : montrees) {
                float lt = Texte.largeurEspacee(font, a[0], Ui.BOLD, S_CAPS, 0.5f) + 8;
                Ui.soft(g, Math.round(cxb), Math.round(ym - 5.5f), Math.round(lt), 11, 0x22FFFFFF);
                Ui.softOutline(g, Math.round(cxb) + 1, Math.round(ym - 4.5f), Math.round(lt) - 2, 9, 0x30FFFFFF);
                Texte.espacee(g, font, a[0], Ui.BOLD, cxb + 4, ym - 11 * S_CAPS / 2, 0xFFFFFFFF, S_CAPS, 0.5f, false);
                cxb += lt + 3;
                Texte.ecrire(g, font, a[1], Ui.FONT, cxb, ym - 11 * S_PETIT / 2, Formes.argb(GRIS, 1f), S_PETIT);
                cxb += Texte.largeur(font, a[1], Ui.FONT, S_PETIT) + 9;
            }
        }
    }

    private float separateur(GuiGraphics g, float x, float y, float sep) {
        float m = x + sep / 2;
        g.fill(Math.round(m), Math.round(y + 6), Math.round(m) + 1, Math.round(y + BAS_H - 6), 0x2AFFFFFF);
        return x + sep;
    }

    private void boussole(GuiGraphics g) {
        float r = BOUTON / 2f, X = width - MARGE - r, Y = height - MARGE - BAS_H / 2f;
        Formes.debut(g);
        Formes.disque(X, Y, r, Formes.argb(VERRE, 0.82f));
        Formes.anneau(X, Y, r - unPixel / 2, unPixel, 0x30FFFFFF);
        Formes.fin();
        Icones.debut(g);
        Icones.icone(Icones.NORD, X, Y - 3f, 6f, 0xFFFF6B6B);
        Icones.fin();
        Texte.espaceeCentree(g, font, "N", Ui.BOLD, X, Y + 1.6f, 0xFFFFFFFF, S_CAPS, 0, false);
    }

    // =================================================================== menu « marquer ici »
    private void menuMarquer(GuiGraphics g, int mx, int my) {
        PingType[] types = PingType.values();
        String titre = I18n.get("heraliz_carte.menu.titre");
        float lh = 14;
        float lmax = 0;
        for (PingType t : types) lmax = Math.max(lmax, Texte.largeur(font, I18n.get(t.cle()), Ui.FONT, S_TEXTE));
        float w = Math.max(Math.max(110, lmax + 34), Texte.largeurEspacee(font, titre, Ui.BOLD, S_CAPS, 0.9f) + 18), h = 22 + types.length * lh + 4;
        float x = Mth.clamp(menuX, 4, width - w - 4), y = Mth.clamp(menuY, 4, height - h - 4);
        Ui.glass(g, Math.round(x), Math.round(y), Math.round(w), Math.round(h));
        Texte.espacee(g, font, titre, Ui.BOLD, x + 9, y + 8, Formes.argb(GRIS, 1f), S_CAPS, 0.9f, false);
        // un clic dans le menu ne traverse pas ; seules ses cibles comptent tant qu'il est ouvert
        menuDebut = cibles.size();
        cibles.add(new Cible(x, y, x + w, y + h, () -> {
        }));
        for (int i = 0; i < types.length; i++) {
            float ly = y + 20 + i * lh;
            boolean survol = mx >= x + 3 && mx < x + w - 3 && my >= ly && my < ly + lh;
            if (survol) Ui.soft(g, Math.round(x + 3), Math.round(ly), Math.round(w - 6), Math.round(lh), 0x16FFFFFF);
        }
        Icones.debut(g);
        for (int i = 0; i < types.length; i++) {
            float ly = y + 20 + i * lh;
            Icones.icone(Icones.LOSANGE, x + 13, ly + lh / 2, 4f, 0xFF000000 | types[i].couleur);
        }
        Icones.fin();
        for (int i = 0; i < types.length; i++) {
            PingType t = types[i];
            float ly = y + 20 + i * lh;
            boolean survol = mx >= x + 3 && mx < x + w - 3 && my >= ly && my < ly + lh;
            Texte.ecrire(g, font, I18n.get(t.cle()), Ui.FONT, x + 23, ly + lh / 2 - 11 * S_TEXTE / 2, Formes.argb(survol ? 0xFFFFFF : TEXTE, 1f), S_TEXTE);
            cibles.add(new Cible(x + 3, ly, x + w - 3, ly + lh, () -> {
                marquer(t, menuMondeX, menuMondeZ);
                menu = false;
                SonsUi.clic(0.5f);
            }));
        }
    }

    /** Ping du clan sur un point de la carte (le serveur vérifie tout et choisit la hauteur). */
    private void marquer(PingType type, int x, int z) {
        if (minecraft.getConnection() != null) PingNet.versServeur(new Paquets.CarteC2S(type.id(), x, z));
    }

    // =================================================================== infobulle
    private void infobulle(GuiGraphics g, int mx, int my) {
        if (surPanneau(mx, my)) return;
        float e = e();
        List<Component> lignes = new ArrayList<>();
        int accent = Ui.accent();
        if (pings) {
            for (int i = 0; i < PingsClient.n && lignes.isEmpty(); i++) {
                PingsClient.Visuel v = PingsClient.PINGS[i];
                if (Math.hypot(sx(v.x) - mx, sy(v.z) - my) > 7 * e) continue;
                lignes.add(Ui.lit(I18n.get(v.type.cle()), Ui.BOLD));
                lignes.add(Ui.lit(I18n.get("heraliz_carte.ping.de", v.nom), Ui.FONT));
                lignes.add(Ui.lit(metres(Math.hypot(v.x - px, v.z - pz)), Ui.FONT));
                if (v.moi) lignes.add(Ui.lit(I18n.get("heraliz_carte.ping.retirer"), Ui.FONT));
                accent = v.type.couleur;
            }
        }
        if (lignes.isEmpty() && extractions) {
            for (Sources.Extraction x : ouvertes) {
                if (Math.hypot(sx(x.x()) - mx, sy(x.z()) - my) > 7 * e) continue;
                lignes.add(Ui.lit(I18n.get("heraliz_carte.extraction", x.nom()), Ui.BOLD));
                int s = x.secondesRestantes(heureDeJeu);
                lignes.add(Ui.lit(s < 0 ? I18n.get("heraliz_carte.extraction.permanente") : I18n.get("heraliz_carte.extraction.ferme", duree(s * 1000L)), Ui.FONT));
                lignes.add(Ui.lit(I18n.get("heraliz_carte.extraction.tenir", String.valueOf(x.secondesATenir())), Ui.FONT));
                lignes.add(Ui.lit(metres(Math.hypot(x.x() - px, x.z() - pz)), Ui.FONT));
                accent = EXTRACTION;
                break;
            }
        }
        if (lignes.isEmpty() && lieux) {
            for (Lieu l : lieuxIci) {
                if (l.type().texteSeul() || visibilite(l) < 0.5f || Math.hypot(sx(l.x() + 0.5) - mx, sy(l.z() + 0.5) - my) > 7 * e) continue;
                lignes.add(Ui.lit(l.nom(), Ui.BOLD));
                lignes.add(Ui.lit(I18n.get(l.type().cle()), Ui.FONT));
                lignes.add(Ui.lit(metres(Math.hypot(l.x() + 0.5 - px, l.z() + 0.5 - pz)), Ui.FONT));
                accent = l.type().couleur;
                break;
            }
        }
        if (lignes.isEmpty() && clan) {
            for (Sources.Allie a : allies) {
                if (Math.hypot(sx(a.x()) - mx, sy(a.z()) - my) > 6 * e) continue;
                lignes.add(Ui.lit(a.nom(), Ui.BOLD));
                lignes.add(Ui.lit(metres(Math.hypot(a.x() - px, a.z() - pz)), Ui.FONT));
                accent = couleurClan();
                break;
            }
        }
        if (!lignes.isEmpty()) Ui.tooltip(g, font, lignes, mx, my, accent);
    }

    // =================================================================== souris, clavier
    private boolean surPanneau(double x, double y) {
        for (Cible c : cibles) if (c.dans(x, y)) return true;
        if (x < MARGE + PANNEAU_L) return true;
        if (y >= basY0 && x >= basX0 && x < basX1) return true;
        if (x >= width - MARGE - BOUTON && y >= height - MARGE - BAS_H - 6 - 2 * BOUTON) return true;
        return selection != null && x > width - MARGE - FICHE_L && y > MARGE + VUE_H + 6 && y < ficheY1;
    }

    @Override
    public boolean mouseClicked(double x, double y, int bouton) {
        if (menu) {
            for (int i = cibles.size() - 1; i >= Math.min(menuDebut, cibles.size()); i--) {
                if (cibles.get(i).dans(x, y)) {
                    cibles.get(i).action().run();
                    return true;
                }
            }
            menu = false;
            return true;
        }
        if (bouton == 0) {
            for (int i = cibles.size() - 1; i >= 0; i--) {
                if (cibles.get(i).dans(x, y)) {
                    cibles.get(i).action().run();
                    return true;
                }
            }
            if (surPanneau(x, y)) return true;
            glisse = true;
            aBouge = false;
            glisseX = x;
            glisseY = y;
            return true;
        }
        if (bouton == 1 && !surPanneau(x, y)) {
            // clic droit sur son propre ping : le retirer ; ailleurs (dans la carte) : menu « marquer ici »
            float e = e();
            for (int i = 0; i < PingsClient.n; i++) {
                PingsClient.Visuel v = PingsClient.PINGS[i];
                if (v.moi && Math.hypot(sx(v.x) - x, sy(v.z) - y) <= 7 * e) {
                    if (minecraft.getConnection() != null) PingNet.versServeur(new Paquets.AnnulerC2S(v.id));
                    SonsUi.clic(0.4f);
                    return true;
                }
            }
            if (!dansCarte(mondeX(x), mondeZ(y))) return true;
            menu = true;
            menuDebut = Integer.MAX_VALUE; // ses cibles arrivent à la prochaine image
            menuX = (float) x;
            menuY = (float) y;
            menuMondeX = (int) Math.floor(mondeX(x));
            menuMondeZ = (int) Math.floor(mondeZ(y));
            SonsUi.clic(0.35f);
            return true;
        }
        return super.mouseClicked(x, y, bouton);
    }

    @Override
    public boolean mouseDragged(double x, double y, int bouton, double dx, double dy) {
        if (glisse && bouton == 0) {
            if (Math.abs(x - glisseX) + Math.abs(y - glisseY) > 2) aBouge = true;
            cx -= dx / k;
            cz -= dy / k;
            cxCible = cx;
            czCible = cz;
            borner();
            cx = cxCible;
            cz = czCible;
            return true;
        }
        return super.mouseDragged(x, y, bouton, dx, dy);
    }

    @Override
    public boolean mouseReleased(double x, double y, int bouton) {
        if (glisse && bouton == 0) {
            glisse = false;
            if (!aBouge) choisirZone(x, y);
            return true;
        }
        return super.mouseReleased(x, y, bouton);
    }

    /** Clic sur la carte : la plus petite zone sous le curseur devient la sélection (rien dessous : plus de sélection). */
    private void choisirZone(double x, double y) {
        if (!territoires) {
            selection = null;
            return;
        }
        double wx = mondeX(x), wz = mondeZ(y);
        Sources.Zone choix = null;
        for (Sources.Zone z : zones) {
            double d = Math.hypot(wx - (z.x() + 0.5), wz - (z.z() + 0.5));
            if (d <= z.rayon() && (choix == null || z.rayon() < choix.rayon())) choix = z;
        }
        selection = choix == null ? null : choix.id();
        if (choix != null) SonsUi.clic(0.4f);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        if (x >= MARGE && x < MARGE + PANNEAU_L && y >= listeY0 && y < listeY1) {
            defilement -= (float) delta;
            return true;
        }
        if (surPanneau(x, y)) return true;
        float nk = Mth.clamp(kCible * (float) Math.pow(1.25, delta), kMin(), K_MAX);
        // le point du monde sous le curseur reste sous le curseur (puis la vue est gardée dans la carte)
        double wx = cxCible + (x - ox) / kCible, wz = czCible + (y - oy) / kCible;
        kCible = nk;
        cxCible = wx - (x - ox) / nk;
        czCible = wz - (y - oy) / nk;
        borner();
        return true;
    }

    @Override
    public boolean keyPressed(int touche, int scancode, int modificateurs) {
        if (PartieClient.TOUCHE.matches(touche, scancode)) {
            onClose();
            return true;
        }
        if (touche == GLFW.GLFW_KEY_ESCAPE && menu) {
            menu = false;
            return true;
        }
        if (touche == GLFW.GLFW_KEY_SPACE) {
            centrer(px, pz);
            return true;
        }
        return super.keyPressed(touche, scancode, modificateurs);
    }

    // =================================================================== formats
    /** « 1 860 m » (espace de milliers en français). */
    private static String metres(double d) {
        return Ui.nombre(Math.round(d)) + " m";
    }

    /** Durée courte : « 2 j 3 h », « 3 h 12 », « 7 min », « 45 s ». */
    static String duree(long ms) {
        long s = Math.max(0, ms / 1000), min = s / 60, h = min / 60, j = h / 24;
        if (j >= 1) return I18n.get("heraliz_carte.duree.j", String.valueOf(j), String.valueOf(h % 24));
        if (h >= 1) return I18n.get("heraliz_carte.duree.h", String.valueOf(h), String.format(Locale.ROOT, "%02d", min % 60));
        if (min >= 1) return I18n.get("heraliz_carte.duree.min", String.valueOf(min));
        return I18n.get("heraliz_carte.duree.s", String.valueOf(s));
    }
}
