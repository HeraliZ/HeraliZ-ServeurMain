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
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * La carte du monde (maquettes validées : vues satellite et topographique, zoom sur une ville), au style des écrans de
 * clan (panneaux de verre, DM Sans, canevas 640 x 360).
 * <ul>
 *     <li>Fond : vue de dessus du monde ({@link Fond}) ; hors de l'emprise, voile sombre et bord en tirets ; sans image,
 *     fond neutre et message.</li>
 *     <li>Calques (panneau de gauche, chacun se coupe d'un clic) : territoires des clans (cercle, bannière du clan,
 *     état, capture en cours), extractions ouvertes, lieux nommés (villes, villages, points d'intérêt), membres de son
 *     clan, pings du clan, grille de 500 m (colonnes A, B... et lignes 1, 2...).</li>
 *     <li>Le joueur : flèche dans sa direction et cône de vue. Jamais d'autres joueurs que ceux de son clan (envoyés par
 *     le serveur).</li>
 *     <li>Souris : glisser = déplacer, molette = zoom autour du curseur, clic = choisir une zone (fiche à droite), clic
 *     droit = marquer un point pour son clan (ping) ou retirer son propre ping. Espace = revenir sur soi ; la touche de
 *     la carte ou Échap = fermer.</li>
 * </ul>
 */
public final class EcranCarte extends Screen {
    static final String SATELLITE = "satellite", RELIEF = "relief";
    private static final int TEXTE = 0xECEEF2, GRIS = 0x8F95A0, PALE = 0x5A5F69, NEUTRE = 0x6FA8FF, EXTRACTION = 0x50E6A0,
            EXTRACTION_TEXTE = 0x7DF0B8, ROUGE = 0xFF6B6B, VERT = 0x5FD08A, AMBRE = 0xE8B34A, PROTEGEE = 0x7FD3FF, TENUE = 0x9AA3B2,
            FOND = 0x07090C, VERRE = 0x0D0F14;
    private static final float MARGE = 8, PANNEAU_L = 144, FICHE_L = 132, LIGNE_CALQUE = 11.5f, LIGNE_ZONE = 13f;
    private static final int GRILLE = 500;
    private static final float K_MAX = 3f;
    private static final float[] ECHELLES = {5, 10, 20, 25, 50, 100, 200, 250, 500, 1000, 2000, 5000};

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
    private List<Sources.Allie> allies = List.of();
    private List<Sources.Extraction> ouvertes = List.of();
    private List<Lieu> lieuxIci = List.of();
    @Nullable
    private Sources.MonClan monClan;
    @Nullable
    private Fond.Info fond;
    private boolean fondIci;
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

    public EcranCarte() {
        super(Component.translatable("heraliz_carte.titre"));
    }

    // =================================================================== ouverture, fermeture
    @Override
    protected void init() {
        Echelle.forcer(this);
        unPixel = 1f / (float) minecraft.getWindow().getGuiScale();
        ox = (MARGE + PANNEAU_L + width) / 2f;
        oy = height / 2f;
        if (derniereImage == 0) {
            territoires = CarteConfig.TERRITOIRES.get();
            extractions = CarteConfig.EXTRACTIONS.get();
            lieux = CarteConfig.LIEUX.get();
            clan = CarteConfig.CLAN.get();
            pings = CarteConfig.PINGS.get();
            grille = CarteConfig.GRILLE.get();
            relief = CarteConfig.RELIEF.get();
            Player p = minecraft.player;
            if (p != null) {
                cx = cxCible = p.getX();
                cz = czCible = p.getZ();
            }
            fond = Fond.info();
            k = kCible = Mth.clamp(zoomMemo > 0 ? zoomMemo : 0.25f, kMin(), K_MAX);
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

    // =================================================================== projection
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

    /** Zoom minimal : un peu plus loin que toute la carte (sans fond : environ 8 km de large). */
    private float kMin() {
        Fond.Info f = fond;
        if (f == null) return 0.05f;
        float ajuste = Math.min((width - MARGE * 2 - PANNEAU_L - 8) / (float) f.largeur(), (height - MARGE * 2) / (float) f.hauteur());
        return Math.max(0.005f, ajuste * 0.8f);
    }

    /** Taille des marqueurs et des étiquettes : un peu plus grands quand on zoome (comme la maquette). */
    private float e() {
        return 1f + Mth.clamp((k - 0.08f) / 0.6f, 0f, 1f) * 0.25f;
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

    /** Garde le centre de la vue près de la carte (on ne se perd pas dans le vide). */
    private void borner() {
        Fond.Info f = fond;
        if (f == null || !fondIci) return;
        cxCible = Mth.clamp(cxCible, f.x0() - 300, f.x0() + f.largeur() + 300);
        czCible = Mth.clamp(czCible, f.z0() - 300, f.z0() + f.hauteur() + 300);
    }

    private void centrer(double x, double z) {
        cxCible = x;
        czCible = z;
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
        zones = Sources.zones();
        allies = Sources.allies();
        monClan = Sources.monClan();
        ouvertes = new ArrayList<>();
        for (Sources.Extraction x : Sources.extractions()) if (x.dimension().equals(dim)) ouvertes.add(x);
        lieuxIci = new ArrayList<>();
        for (Lieu l : LieuxClient.lieux()) if (l.dimension().equals(dim)) lieuxIci.add(l);
        px = Mth.lerp(pt, p.xo, p.getX());
        pz = Mth.lerp(pt, p.zo, p.getZ());
        cap = (float) Math.toRadians(p.getViewYRot(pt) + 180f);
        heureDeJeu = minecraft.level.getGameTime();
        if (selection != null && zone(selection) == null) selection = null;
        cibles.clear();
        gr = g;

        g.fill(0, 0, width, height, 0xFF000000 | FOND);
        String vue = relief ? RELIEF : SATELLITE;
        if (fondIci && !fond.a(vue)) vue = fond.a(SATELLITE) ? SATELLITE : RELIEF;
        if (fondIci && fond.a(vue)) Fond.dessiner(g, fond, vue, ox, oy, cx, cz, k, width, height);

        float e = e();
        // ---- formes
        Formes.debut(g);
        if (fondIci) voile();
        if (grille) grilleTraits();
        if (territoires) zonesFormes(e);
        if (extractions) extractionsFormes(e);
        if (pings) pingsFormes(e);
        joueurFormes(e);
        if (clan) alliesFormes(e);
        vignette();
        Formes.fin();
        // ---- icônes
        Icones.debut(g);
        if (territoires) drapeauxIcones(e);
        if (lieux) lieuxIcones(e);
        if (extractions) extractionsIcones(e);
        if (pings) pingsIcones(e);
        joueurIcones(e);
        Icones.fin();
        // ---- bannières des clans au centre des zones
        if (territoires) zonesBannieres(e);
        // ---- étiquettes (un seul envoi)
        g.drawManaged(() -> {
            if (grille) grilleEtiquettes();
            if (lieux) lieuxEtiquettes(e);
            if (territoires) zonesEtiquettes(e);
            if (extractions) extractionsEtiquettes(e);
            if (pings) pingsEtiquettes(e);
            if (clan) alliesEtiquettes(e);
        });
        // ---- panneaux
        if (!fondIci) messageFond(g);
        panneauGauche(g, mx, my);
        basculeVue(g, mx, my);
        ficheZone(g, mx, my);
        barreBas(g, mx, my);
        boussole(g);
        if (menu) menuMarquer(g, mx, my);
        else infobulle(g, mx, my);
    }

    // =================================================================== calques : formes
    private void voile() {
        Fond.Info f = fond;
        float x0 = sx(f.x0()), y0 = sy(f.z0()), x1 = sx(f.x0() + f.largeur()), y1 = sy(f.z0() + f.hauteur());
        int v = 0xC706080B;
        Formes.rect(0, 0, width, Math.max(0, y0), v);
        Formes.rect(0, Math.min(height, y1), width, height, v);
        Formes.rect(0, Math.max(0, y0), Math.max(0, x0), Math.min(height, y1), v);
        Formes.rect(Math.min(width, x1), Math.max(0, y0), width, Math.min(height, y1), v);
        Formes.cadreTirets(x0, y0, x1, y1, unPixel * 1.5f, 1.4f, 2f, 0x47FFFFFF);
    }

    /** Origine de la grille (colonne A, ligne 1) : coin de la carte arrondi à 500 m. */
    private int grilleX0() {
        return fondIci ? Math.floorDiv(fond.x0(), GRILLE) * GRILLE : 0;
    }

    private int grilleZ0() {
        return fondIci ? Math.floorDiv(fond.z0(), GRILLE) * GRILLE : 0;
    }

    private void grilleTraits() {
        double xa = mondeX(0), xb = mondeX(width), za = mondeZ(0), zb = mondeZ(height);
        float haut = 0, bas = height, gauche = 0, droite = width;
        if (fondIci) {
            haut = Math.max(0, sy(fond.z0()));
            bas = Math.min(height, sy(fond.z0() + fond.hauteur()));
            gauche = Math.max(0, sx(fond.x0()));
            droite = Math.min(width, sx(fond.x0() + fond.largeur()));
        }
        int c = 0x24FFFFFF;
        float ep = unPixel;
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

    private static String colonne(int i) {
        if (i < 0) return "";
        StringBuilder sb = new StringBuilder();
        int n = i;
        do {
            sb.insert(0, (char) ('A' + n % 26));
            n = n / 26 - 1;
        } while (n >= 0);
        return sb.toString();
    }

    private void grilleEtiquettes() {
        if (!fondIci) return;
        int gx0 = grilleX0(), gz0 = grilleZ0();
        double xa = mondeX(0), xb = mondeX(width), za = mondeZ(0), zb = mondeZ(height);
        float g0 = MARGE + PANNEAU_L + 6;
        for (long x = (long) Math.floor(xa / GRILLE) * GRILLE; x <= xb; x += GRILLE) {
            if (x < gx0 || x >= fond.x0() + fond.largeur()) continue;
            float X = sx(x + GRILLE / 2.0);
            if (X < g0 || X > width - 4) continue;
            Texte.espaceeCentree(gr, font, colonne((int) ((x - gx0) / GRILLE)), Ui.SMALL, X, 6, Formes.argb(TEXTE, 0.8f), 0.7f, 0.6f, true);
        }
        for (long z = (long) Math.floor(za / GRILLE) * GRILLE; z <= zb; z += GRILLE) {
            if (z < gz0 || z >= fond.z0() + fond.hauteur()) continue;
            float Y = sy(z + GRILLE / 2.0);
            if (Y < 14 || Y > height - 30) continue;
            Texte.espacee(gr, font, String.valueOf((z - gz0) / GRILLE + 1), Ui.SMALL, g0, Y - 3, Formes.argb(TEXTE, 0.8f), 0.7f, 0.6f, true);
        }
    }

    private boolean horsEcran(float x, float y, float r) {
        return x + r < 0 || y + r < 0 || x - r > width || y - r > height;
    }

    private int couleurZone(Sources.Zone z) {
        return z.libre() ? NEUTRE : z.proprioCouleur();
    }

    private void zonesFormes(float e) {
        float attenue = k > 0.33f ? 0.35f : 1f;
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
                float a = z.libre() ? 0.07f : z.monClan() ? 0.20f : 0.16f;
                Formes.disque(X, Y, R, Formes.argb(c, a * attenue));
            }
            if (z.libre()) {
                Formes.anneauPointille(X, Y, R, 0.7f, 3.3f, 2.7f, Formes.argb(c, 0.8f));
                // petit point bleu au centre d'une zone libre (pas de bannière)
                Formes.disque(X, Y - 3.2f * e, 2.3f * e, 0xFF0D0F14);
                Formes.disque(X, Y - 3.2f * e, 1.7f * e, 0xFF000000 | NEUTRE);
            } else {
                Formes.anneau(X, Y, R, 1f, Formes.argb(c, 0.95f));
            }
            if ("CAPTURE".equals(z.etat()) && !z.capteurTag().isEmpty()) {
                float r2 = R + 2.4f, a0 = (float) (-Math.PI / 2);
                Formes.anneau(X, Y, r2, 2.4f, 0x73000000);
                Formes.arc(X, Y, r2, 1.7f, a0, a0 + (float) (Math.PI * 2 * z.progression()), Formes.argb(z.capteurCouleur(), 1f));
            }
            if (z.id().equals(selection)) Formes.anneau(X, Y, R + 0.9f, unPixel * 2, 0xCCFFFFFF);
        }
    }

    private void extractionsFormes(float e) {
        for (Sources.Extraction x : ouvertes) {
            float X = sx(x.x()), Y = sy(x.z()), R = Math.max(x.rayon() * k, 4.7f * e);
            if (horsEcran(X, Y, R + 8)) continue;
            Formes.disque(X, Y, R, Formes.argb(EXTRACTION, 0.12f));
            Formes.anneauPointille(X, Y, R, 0.7f, 2f, 2f, Formes.argb(EXTRACTION, 1f));
        }
    }

    private void pingsFormes(float e) {
        for (int i = 0; i < PingsClient.n; i++) {
            PingsClient.Visuel v = PingsClient.PINGS[i];
            float X = sx(v.x), Y = sy(v.z);
            if (horsEcran(X, Y, 10 * e)) continue;
            Formes.anneau(X, Y, 8f * e, 0.7f, Formes.argb(v.type.couleur, 0.6f));
        }
    }

    private void joueurFormes(float e) {
        float X = sx(px), Y = sy(pz);
        Formes.cone(X, Y, 55f * e, cap, 0.87f, Formes.argb(0xFFFFFF, 0.28f));
        Formes.disque(X, Y, 6.7f * e, Formes.argb(0xFFFFFF, 0.18f));
    }

    private int couleurClan() {
        return monClan != null ? monClan.couleur() : Ui.accent();
    }

    private void alliesFormes(float e) {
        int c = couleurClan();
        for (Sources.Allie a : allies) {
            float X = sx(a.x()), Y = sy(a.z());
            if (horsEcran(X, Y, 4)) continue;
            Formes.disque(X, Y, 3.7f * e, 0xFFFFFFFF);
            Formes.disque(X, Y, 3f * e, 0xFF000000 | c);
        }
    }

    /** Coins de l'écran un peu assombris (comme la maquette). */
    private void vignette() {
        float l = Math.min(width, height) * 0.22f;
        int a = 0x6B000000, z = 0;
        Formes.degrade(0, 0, width, l, a, a, z, z);
        Formes.degrade(0, height - l, width, height, z, z, a, a);
        Formes.degrade(0, 0, l, height, a, z, z, a);
        Formes.degrade(width - l, 0, width, height, z, a, a, z);
    }

    // =================================================================== calques : icônes
    private void drapeauxIcones(float e) {
        if (k <= 0.33f) return;
        for (Sources.Zone z : zones) {
            if (!z.drapeau()) continue;
            float X = sx(z.drapeauX() + 0.5), Y = sy(z.drapeauZ() + 0.5);
            if (horsEcran(X, Y, 10)) continue;
            Icones.encadree(Icones.DRAPEAU, X, Y, 5f * e, couleurZone(z), 1f);
        }
    }

    private void lieuxIcones(float e) {
        for (Lieu l : lieuxIci) {
            if (l.type().texteSeul()) continue;
            float X = sx(l.x() + 0.5), Y = sy(l.z() + 0.5);
            if (horsEcran(X, Y, 10)) continue;
            Icones.encadree(Icones.index(l.type().icone), X, Y, 4.7f * e, l.type().couleur, 1f);
        }
    }

    private void extractionsIcones(float e) {
        for (Sources.Extraction x : ouvertes) {
            float X = sx(x.x()), Y = sy(x.z());
            if (horsEcran(X, Y, 10)) continue;
            Icones.encadree(Icones.SORTIE, X, Y, 5f * e, EXTRACTION, 1f);
        }
    }

    private void pingsIcones(float e) {
        for (int i = 0; i < PingsClient.n; i++) {
            PingsClient.Visuel v = PingsClient.PINGS[i];
            float X = sx(v.x), Y = sy(v.z);
            if (horsEcran(X, Y, 10)) continue;
            Icones.icone(Icones.LOSANGE, X, Y, 5.4f * e, 0xFF0D0F14);
            Icones.icone(Icones.LOSANGE, X, Y, 4.5f * e, 0xFF000000 | v.type.couleur);
        }
    }

    private void joueurIcones(float e) {
        float X = sx(px), Y = sy(pz);
        Icones.tournee(Icones.FLECHE, X, Y, 7.2f * e, cap, 0xFF0D0F14);
        Icones.tournee(Icones.FLECHE, X, Y, 6f * e, cap, 0xFFFFFFFF);
    }

    private void zonesBannieres(float e) {
        for (Sources.Zone z : zones) {
            if (z.libre() || z.proprioBanniere() == null) continue;
            float X = sx(z.x() + 0.5), Y = sy(z.z() + 0.5);
            if (horsEcran(X, Y, 12) || sousPanneau(X, Y)) continue;
            int w = Math.round(8 * e), h = Math.round(12 * e);
            Sources.banniere(gr, z.proprioBanniere(), Math.round(X - w / 2f), Math.round(Y - 9 * e), w, h);
        }
    }

    // =================================================================== calques : étiquettes
    /** GuiGraphics de l'image en cours (les étiquettes sont dessinées dans un seul envoi). */
    private GuiGraphics gr;

    private void lieuxEtiquettes(float e) {
        for (Lieu l : lieuxIci) {
            float X = sx(l.x() + 0.5), Y = sy(l.z() + 0.5);
            if (horsEcran(X, Y, 40) || sousPanneau(X, Y)) continue;
            switch (l.type()) {
                case VILLE -> Texte.espaceeCentree(gr, font, l.nom(), Ui.BOLD, X, Y - 3 * e, Formes.argb(TEXTE, 0.95f), 0.6f * e, 1.4f * e, true);
                case VILLAGE -> Texte.espaceeCentree(gr, font, l.nom(), Ui.SMALL, X, Y - 2 * e, Formes.argb(TEXTE, 0.78f), 0.62f * e, 0.8f * e, true);
                default -> Texte.espaceeCentree(gr, font, l.nom(), Ui.SMALL, X, Y + 6.5f * e, Formes.argb(l.type().couleur, 1f), 0.6f * e,
                        0.7f * e, true);
            }
        }
    }

    private void zonesEtiquettes(float e) {
        for (Sources.Zone z : zones) {
            float X = sx(z.x() + 0.5), Y = sy(z.z() + 0.5);
            if (horsEcran(X, Y, 40) || sousPanneau(X, Y)) continue;
            int c = couleurZone(z);
            float yNom = Y + (z.libre() ? 0.5f : 4.5f) * e;
            if (k > 0.33f && z.drapeau()) {
                Texte.espaceeCentree(gr, font, I18n.get("heraliz_carte.panneau.drapeau"), Ui.SMALL, sx(z.drapeauX() + 0.5),
                        sy(z.drapeauZ() + 0.5) + 6f * e, Formes.argb(c, 1f), 0.55f * e, 0.6f * e, true);
            }
            Texte.espaceeCentree(gr, font, z.nom(), Ui.BOLD, X, yNom, 0xFFFFFFFF, 0.62f * e, 1.6f * e, true);
            String sous;
            int cs;
            switch (z.etat()) {
                case "CAPTURE" -> {
                    sous = I18n.get("heraliz_carte.sous.capture", z.capteurNom(), Math.round(z.progression() * 100) + " %");
                    cs = z.capteurCouleur();
                }
                case "CONTESTE" -> {
                    sous = I18n.get("heraliz_carte.etat.contestee");
                    cs = 0xFF8080;
                }
                case "PROTEGEE" -> {
                    sous = I18n.get("heraliz_carte.sous.protegee", z.proprioTag(), duree(z.protegeeMs()));
                    cs = c;
                }
                default -> {
                    sous = z.libre() ? I18n.get("heraliz_carte.etat.libre") : "[" + z.proprioTag() + "] " + z.proprioNom();
                    cs = z.libre() ? 0xB9C8E6 : c;
                }
            }
            Texte.espaceeCentree(gr, font, sous, Ui.SMALL, X, yNom + 7f * e, Formes.argb(cs, 1f), 0.5f * e, 0.6f * e, true);
        }
    }

    private void extractionsEtiquettes(float e) {
        for (Sources.Extraction x : ouvertes) {
            float X = sx(x.x()), Y = sy(x.z());
            if (horsEcran(X, Y, 40) || sousPanneau(X, Y)) continue;
            String t = I18n.get("heraliz_carte.extraction", x.nom());
            int s = x.secondesRestantes(heureDeJeu);
            if (s >= 0) t += " · " + duree(s * 1000L);
            Texte.espaceeCentree(gr, font, t, Ui.SMALL, X, Y + 7f * e, Formes.argb(EXTRACTION_TEXTE, 1f), 0.55f * e, 0.6f * e, true);
        }
    }

    private void pingsEtiquettes(float e) {
        for (int i = 0; i < PingsClient.n; i++) {
            PingsClient.Visuel v = PingsClient.PINGS[i];
            float X = sx(v.x), Y = sy(v.z);
            if (horsEcran(X, Y, 40) || sousPanneau(X, Y)) continue;
            String t = I18n.get(v.type.cle()) + " · " + metres(Math.hypot(v.x - px, v.z - pz));
            Texte.espaceeCentree(gr, font, t, Ui.SMALL, X, Y - 13f * e, Formes.argb(v.type.couleur, 1f), 0.55f * e, 0.6f * e, true);
        }
    }

    private void alliesEtiquettes(float e) {
        for (Sources.Allie a : allies) {
            float X = sx(a.x()), Y = sy(a.z());
            if (horsEcran(X, Y, 40) || sousPanneau(X, Y)) continue;
            Texte.espacee(gr, font, a.nom(), Ui.SMALL, X + 5f * e, Y - 2.2f * e, 0xFFFFFFFF, 0.6f * e, 0.6f * e, true);
        }
    }

    // =================================================================== panneaux
    /** Vrai si le point de l'écran tombe sous un panneau (les étiquettes de la carte n'y sont pas dessinées). */
    private boolean sousPanneau(float x, float y) {
        if (x < MARGE + PANNEAU_L + 4) return true;
        if (y > basY0 - 2 && x > basX0 - 2 && x < basX1 + 2) return true;
        if (x > vueX0 - 2 && y < MARGE + 20) return true;
        return selection != null && x > width - MARGE - FICHE_L - 2 && y > MARGE + 22 && y < ficheY1 + 2;
    }

    private void messageFond(GuiGraphics g) {
        boolean absent = fond == null;
        String titre = I18n.get(absent ? "heraliz_carte.fond.absent.titre" : "heraliz_carte.fond.autre_monde.titre");
        String texte = I18n.get(absent ? "heraliz_carte.fond.absent" : "heraliz_carte.fond.autre_monde");
        float w = Math.max(Texte.largeurEspacee(font, titre, Ui.BOLD, 0.75f, 1.4f), Texte.largeur(font, texte, Ui.FONT, 0.68f)) + 24;
        float x = ox - w / 2, y = MARGE + 30;
        Ui.glass(g, Math.round(x), Math.round(y), Math.round(w), 34);
        Texte.espaceeCentree(g, font, titre, Ui.BOLD, ox, y + 8, Formes.argb(TEXTE, 1f), 0.75f, 1.4f, false);
        Texte.ecrire(g, font, texte, Ui.FONT, ox - Texte.largeur(font, texte, Ui.FONT, 0.68f) / 2, y + 20, Formes.argb(GRIS, 1f), 0.68f);
    }

    private void panneauGauche(GuiGraphics g, int mx, int my) {
        float x = MARGE, y = MARGE, w = PANNEAU_L, h = height - 2 * MARGE;
        Ui.glass(g, (int) x, (int) y, (int) w, (int) h);
        float xi = x + 10, xd = x + w - 10;
        Texte.espacee(g, font, I18n.get("heraliz_carte.titre"), Ui.BOLD, xi, y + 9, Formes.argb(TEXTE, 1f), 1.25f, 3f, false);
        String nom = LieuxClient.nomCarte().isEmpty() ? "HeraliZ" : LieuxClient.nomCarte();
        Texte.espacee(g, font, I18n.get("heraliz_carte.monde", nom), Ui.SMALL, xi, y + 25, Formes.argb(GRIS, 1f), 0.8f, 0.9f, false);
        // ---- calques
        float ys = y + 38;
        section(g, I18n.get("heraliz_carte.calques"), xi, ys, xd);
        float yl = ys + 10;
        int nClan = monClan == null ? 0 : allies.size() + 1;
        calque(g, mx, my, yl, couleurClan(), "heraliz_carte.calque.territoires", Sources.clansPresents() ? String.valueOf(zones.size()) : "—",
                territoires, () -> territoires = !territoires);
        calque(g, mx, my, yl + LIGNE_CALQUE, EXTRACTION, "heraliz_carte.calque.extractions", String.valueOf(ouvertes.size()), extractions,
                () -> extractions = !extractions);
        calque(g, mx, my, yl + LIGNE_CALQUE * 2, 0xF5D76E, "heraliz_carte.calque.lieux", String.valueOf(lieuxIci.size()), lieux,
                () -> lieux = !lieux);
        calque(g, mx, my, yl + LIGNE_CALQUE * 3, 0xFFFFFF, "heraliz_carte.calque.clan", nClan == 0 ? "—" : String.valueOf(nClan), clan,
                () -> clan = !clan);
        calque(g, mx, my, yl + LIGNE_CALQUE * 4, ROUGE, "heraliz_carte.calque.pings", String.valueOf(PingsClient.n), pings,
                () -> pings = !pings);
        calque(g, mx, my, yl + LIGNE_CALQUE * 5, TENUE, "heraliz_carte.calque.grille", Ui.nombre(GRILLE) + " m", grille,
                () -> grille = !grille);
        // ---- territoires
        float yt = yl + LIGNE_CALQUE * 6 + 6;
        section(g, I18n.get("heraliz_carte.territoires"), xi, yt, xd);
        listeY0 = yt + 9;
        listeY1 = y + h - 38;
        if (zones.isEmpty()) {
            Texte.ecrire(g, font, I18n.get("heraliz_carte.aucun_territoire"), Ui.FONT, xi, listeY0 + 3, Formes.argb(PALE, 1f), 0.66f);
        } else {
            int visibles = Math.max(1, (int) ((listeY1 - listeY0) / LIGNE_ZONE));
            defilement = Mth.clamp(defilement, 0, Math.max(0, zones.size() - visibles));
            int debut = (int) defilement;
            g.enableScissor((int) x, (int) listeY0, (int) (x + w), (int) listeY1);
            for (int i = debut; i < zones.size() && i < debut + visibles + 1; i++) {
                Sources.Zone z = zones.get(i);
                float ly = listeY0 + (i - debut) * LIGNE_ZONE;
                ligneZone(g, mx, my, z, xi - 4, ly, xd + 4);
            }
            g.disableScissor();
            if (zones.size() > visibles) {
                // ascenseur discret
                float hb = listeY1 - listeY0, part = visibles / (float) zones.size();
                float yb = listeY0 + hb * (defilement / zones.size());
                g.fill((int) (x + w - 4), (int) yb, (int) (x + w - 3), (int) (yb + hb * part), 0x40FFFFFF);
            }
        }
        // ---- mon clan
        float yc = y + h - 32;
        Ui.hairline(g, (int) xi, (int) xd, (int) yc, 0xFFFFFF, 20, false, false);
        if (monClan != null) {
            Sources.banniere(g, monClan.banniere(), (int) xi, (int) yc + 6, 12, 18);
            String n = Texte.couper(font, monClan.nom(), Ui.BOLD, 0.72f, xd - xi - 50);
            Texte.ecrire(g, font, n, Ui.BOLD, xi + 17, yc + 7, Formes.argb(TEXTE, 1f), 0.72f);
            Texte.ecrire(g, font, "[" + monClan.tag() + "]", Ui.FONT, xi + 19 + Texte.largeur(font, n, Ui.BOLD, 0.72f), yc + 7,
                    Formes.argb(GRIS, 1f), 0.72f);
            Texte.espacee(g, font, I18n.get("heraliz_carte.clan.resume", String.valueOf(monClan.territoires()), String.valueOf(monClan.enLigne())),
                    Ui.SMALL, xi + 17, yc + 17, Formes.argb(GRIS, 1f), 0.62f, 0.7f, false);
        } else {
            Texte.espacee(g, font, I18n.get("heraliz_carte.clan.aucun"), Ui.SMALL, xi, yc + 11, Formes.argb(PALE, 1f), 0.7f, 0.8f, false);
        }
    }

    private void section(GuiGraphics g, String titre, float x, float y, float xd) {
        Texte.espacee(g, font, titre, Ui.SMALL, x, y, Formes.argb(GRIS, 1f), 0.75f, 1.0f, false);
        float tw = Texte.largeurEspacee(font, titre, Ui.SMALL, 0.75f, 1.0f);
        Ui.hairline(g, (int) (x + tw + 6), (int) xd, (int) (y + 3), 0xFFFFFF, 46, false, true);
    }

    private void calque(GuiGraphics g, int mx, int my, float y, int couleur, String cle, String compte, boolean actif, Runnable bascule) {
        float x0 = MARGE + 6, x1 = MARGE + PANNEAU_L - 6;
        boolean survol = mx >= x0 && mx < x1 && my >= y - 1 && my < y + LIGNE_CALQUE - 1;
        if (survol) Ui.soft(g, (int) x0, (int) (y - 1), (int) (x1 - x0), (int) LIGNE_CALQUE, 0x0FFFFFFF);
        Ui.pastille(g, (int) (x0 + 4), (int) (y + 1.5f), 7, couleur, actif, survol);
        Texte.ecrire(g, font, I18n.get(cle), Ui.FONT, x0 + 16, y + 1, Formes.argb(actif ? TEXTE : GRIS, 1f), 0.66f);
        Texte.droite(g, font, compte, Ui.FONT, x1 - 4, y + 1.3f, Formes.argb(GRIS, 1f), 0.62f);
        cibles.add(new Cible(x0, y - 1, x1, y + LIGNE_CALQUE - 1, () -> {
            bascule.run();
            SonsUi.clic(0.4f);
        }));
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
            Ui.soft(g, (int) x0, (int) y, (int) (x1 - x0), (int) LIGNE_ZONE - 1, 0x12FFFFFF);
            Ui.softOutline(g, (int) x0 + 1, (int) y + 1, (int) (x1 - x0) - 2, (int) LIGNE_ZONE - 3, 0x24FFFFFF);
        } else if (survol) {
            Ui.soft(g, (int) x0, (int) y, (int) (x1 - x0), (int) LIGNE_ZONE - 1, 0x0AFFFFFF);
        }
        int c = couleurZone(z);
        g.fill((int) x0 + 4, (int) y + 2, (int) x0 + 6, (int) y + 10, 0xFF000000 | c);
        String etat = libelleEtat(z);
        float le = Texte.largeurEspacee(font, etat, Ui.SMALL, 0.6f, 0.5f);
        float maxNom = x1 - x0 - 16 - le - (z.libre() ? 0 : 26);
        String nom = Texte.couper(font, z.nom(), Ui.FONT, 0.68f, maxNom);
        Texte.ecrire(g, font, nom, Ui.FONT, x0 + 10, y + 2.5f, Formes.argb(TEXTE, 1f), 0.68f);
        if (!z.libre()) {
            float xc = x0 + 13 + Texte.largeur(font, nom, Ui.FONT, 0.68f);
            float lc = Texte.largeurEspacee(font, z.proprioTag(), Ui.SMALL, 0.55f, 0.5f) + 6;
            Ui.soft(g, Math.round(xc), Math.round(y + 2), Math.round(lc), 9, Formes.argb(c, 0.14f));
            Ui.softOutline(g, Math.round(xc) + 1, Math.round(y + 3), Math.round(lc) - 2, 7, Formes.argb(c, 0.5f));
            Texte.espacee(g, font, z.proprioTag(), Ui.SMALL, xc + 3, y + 4, Formes.argb(c, 1f), 0.55f, 0.5f, false);
        }
        Texte.espaceeDroite(g, font, etat, Ui.SMALL, x1 - 4, y + 3.5f, Formes.argb(couleurEtat(z), 1f), 0.6f, 0.5f);
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
        float ls = Texte.largeurEspacee(font, sat, Ui.BOLD, 0.6f, 1.5f) + 14, lr = Texte.largeurEspacee(font, rel, Ui.BOLD, 0.6f, 1.5f) + 14;
        float w = ls + lr + 6, x = width - MARGE - w, y = MARGE;
        vueX0 = x;
        Ui.glass(g, Math.round(x), (int) y, Math.round(w), 19);
        bouton(g, mx, my, sat, x + 2, y + 2, ls, 15, !relief, () -> relief = false);
        bouton(g, mx, my, rel, x + 4 + ls, y + 2, lr, 15, relief, () -> relief = true);
    }

    private void bouton(GuiGraphics g, int mx, int my, String texte, float x, float y, float w, float h, boolean actif, Runnable action) {
        boolean survol = mx >= x && mx < x + w && my >= y && my < y + h;
        if (actif) Ui.soft(g, Math.round(x), Math.round(y), Math.round(w), Math.round(h), 0x1FFFFFFF);
        else if (survol) Ui.soft(g, Math.round(x), Math.round(y), Math.round(w), Math.round(h), 0x0DFFFFFF);
        Texte.espaceeCentree(g, font, texte, Ui.BOLD, x + w / 2, y + h / 2 - 3.2f, Formes.argb(actif ? 0xFFFFFF : GRIS, 1f), 0.6f, 1.5f, false);
        cibles.add(new Cible(x, y, x + w, y + h, () -> {
            action.run();
            SonsUi.clic(0.4f);
        }));
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
        float w = FICHE_L, x = width - MARGE - w, y = MARGE + 25;
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
        couleurs.add(TEXTE);
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
        float h = 40 + (capture ? 30 : 0) + lignes.size() * 12.5f + 26;
        ficheY1 = y + h;
        Ui.glass(g, Math.round(x), Math.round(y), Math.round(w), Math.round(h));
        float xi = x + 9, xd = x + w - 9;
        // en-tête : bannière (propriétaire, sinon clan qui capture), nom, état
        Object ban = !z.libre() ? z.proprioBanniere() : capture ? z.capteurBanniere() : null;
        if (ban != null) {
            Sources.banniere(g, ban, Math.round(xi), Math.round(y + 8), 15, 22);
        } else {
            Icones.debut(g);
            Icones.icone(Icones.ANNEAU, xi + 7, y + 18, 6.5f, Formes.argb(NEUTRE, 0.9f));
            Icones.fin();
        }
        String nom = Texte.couper(font, z.nom(), Ui.BOLD, 0.85f, xd - xi - 22);
        Texte.espacee(g, font, nom, Ui.BOLD, xi + 22, y + 9, Formes.argb(TEXTE, 1f), 0.85f, 1.3f, false);
        Ui.chip(g, font, libelleEtat(z), Math.round(xi + 22), Math.round(y + 21), ce);
        float yy = y + 38;
        if (capture) {
            String t = I18n.get("heraliz_carte.panneau.prennent", z.capteurNom());
            Texte.ecrire(g, font, Texte.couper(font, t, Ui.FONT, 0.66f, xd - xi), Ui.FONT, xi, yy, Formes.argb(z.capteurCouleur(), 1f), 0.66f);
            Ui.bar(g, Math.round(xi), Math.round(yy + 11), Math.round(xd - xi), 3, z.progression(), z.capteurCouleur());
            Texte.espacee(g, font, Math.round(z.progression() * 100) + " %", Ui.SMALL, xi, yy + 17, Formes.argb(GRIS, 1f), 0.62f, 0.7f, false);
            yy += 30;
        }
        for (int i = 0; i < lignes.size(); i++) {
            String[] l = lignes.get(i);
            Texte.ecrire(g, font, l[0], Ui.FONT, xi, yy + 2, Formes.argb(GRIS, 1f), 0.66f);
            Texte.droite(g, font, Texte.couper(font, l[1], Ui.FONT, 0.66f, (xd - xi) * 0.55f), Ui.FONT, xd, yy + 2, Formes.argb(couleurs.get(i), 1f), 0.66f);
            Ui.hairline(g, Math.round(xi), Math.round(xd), Math.round(yy + 11.5f), 0xFFFFFF, 14, false, false);
            yy += 12.5f;
        }
        // bouton : lancer le ravitaillement (zone de son clan, prêt, permission) ou marquer la zone pour son clan
        float by = yy + 6, bh = 15;
        boolean ravitailler = z.monClan() && z.ravitaillementMs() == 0 && z.peutRavitailler();
        String libelle = I18n.get(ravitailler ? "heraliz_carte.panneau.lancer" : "heraliz_carte.panneau.marquer");
        boolean survol = mx >= xi && mx < xd && my >= by && my < by + bh;
        if (ravitailler) {
            Ui.soft(g, Math.round(xi), Math.round(by), Math.round(xd - xi), Math.round(bh), 0xFF000000 | (survol ? Ui.mix(c, 0xFFFFFF, 0.15f) : c));
        } else {
            if (survol) Ui.soft(g, Math.round(xi), Math.round(by), Math.round(xd - xi), Math.round(bh), 0x14FFFFFF);
            Ui.softOutline(g, Math.round(xi) + 1, Math.round(by) + 1, Math.round(xd - xi) - 2, Math.round(bh) - 2, 0x40FFFFFF);
        }
        Texte.espaceeCentree(g, font, libelle, Ui.BOLD, (xi + xd) / 2, by + 4.3f, ravitailler ? 0xFF0D0F14 : 0xFFFFFFFF, 0.56f, 1.3f, false);
        cibles.add(new Cible(xi, by, xd, by + bh, () -> {
            if (ravitailler) Sources.ravitailler(z.id());
            else marquer(PingType.ALLER, z.drapeau() ? z.drapeauX() : z.x(), z.drapeau() ? z.drapeauZ() : z.z());
            SonsUi.clic(0.5f);
        }));
        // un clic ailleurs dans la fiche ne traverse pas vers la carte
        cibles.add(0, new Cible(x, y, x + w, y + h, () -> {
        }));
    }

    private void barreBas(GuiGraphics g, int mx, int my) {
        double wx = mondeX(mx), wz = mondeZ(my);
        String xs = Ui.nombre((long) Math.floor(wx)), zs = Ui.nombre((long) Math.floor(wz));
        float s = 0.62f;
        // largeurs réservées : la barre ne bouge pas quand les nombres changent sous la souris
        float lnum = Math.max(Texte.largeur(font, "-00 000", Ui.FONT, s), Math.max(Texte.largeur(font, xs, Ui.FONT, s), Texte.largeur(font, zs, Ui.FONT, s)));
        // échelle : la plus grande longueur ronde qui tient dans ~42 unités
        float metres = ECHELLES[0];
        for (float m : ECHELLES) if (m * k <= 42) metres = m;
        float barre = metres * k;
        String echelle = metres >= 1000 ? Ui.nombre((long) (metres / 1000)) + " km" : Ui.nombre((long) metres) + " m";
        String carre = null;
        if (fondIci) {
            boolean dedans = wx >= fond.x0() && wz >= fond.z0() && wx < fond.x0() + fond.largeur() && wz < fond.z0() + fond.hauteur();
            carre = dedans ? colonne((int) Math.floor((wx - grilleX0()) / GRILLE)) + (int) (Math.floor((wz - grilleZ0()) / GRILLE) + 1) : "—";
        }
        String touche = PartieClient.TOUCHE.getTranslatedKeyMessage().getString();
        String[][] aides = {{I18n.get("heraliz_carte.bas.clic"), I18n.get("heraliz_carte.bas.choisir")},
                {I18n.get("heraliz_carte.bas.clic_droit"), I18n.get("heraliz_carte.bas.marquer")},
                {I18n.get("heraliz_carte.bas.molette"), I18n.get("heraliz_carte.bas.zoom")},
                {touche, I18n.get("heraliz_carte.bas.fermer")}};
        // largeur totale
        float lx = Texte.largeur(font, "X", Ui.SMALL, 0.62f) + 3 + lnum + 7 + Texte.largeur(font, "Z", Ui.SMALL, 0.62f) + 3 + lnum;
        float lcarre = Math.max(Texte.largeur(font, "AA00", Ui.FONT, s), carre == null ? 0 : Texte.largeur(font, carre, Ui.FONT, s));
        float lc = carre == null ? 0 : Texte.largeurEspacee(font, I18n.get("heraliz_carte.bas.carre"), Ui.SMALL, 0.55f, 0.8f) + 3 + lcarre;
        float le = 42 + 4 + Texte.largeur(font, "2 000 m", Ui.FONT, s);
        float la = 0;
        for (String[] a : aides) la += Texte.largeur(font, a[0], Ui.BOLD, 0.5f) + 6 + 2 + Texte.largeur(font, a[1], Ui.FONT, 0.55f) + 7;
        float sep = 13;
        float w = 14 + lx + sep + (carre == null ? 0 : lc + sep) + le + sep + la;
        float centre = (MARGE + PANNEAU_L + width) / 2f;
        float x = Math.max(MARGE + PANNEAU_L + 6, centre - w / 2), y = height - MARGE - 19;
        basX0 = x;
        basX1 = x + w;
        basY0 = y;
        Ui.glass(g, Math.round(x), Math.round(y), Math.round(w), 19);
        float cxb = x + 7, ty = y + 6.3f;
        Texte.ecrire(g, font, "X", Ui.SMALL, cxb, ty + 0.6f, Formes.argb(GRIS, 1f), 0.62f);
        cxb += Texte.largeur(font, "X", Ui.SMALL, 0.62f) + 3;
        Texte.ecrire(g, font, xs, Ui.FONT, cxb, ty, Formes.argb(TEXTE, 1f), s);
        cxb += lnum + 7;
        Texte.ecrire(g, font, "Z", Ui.SMALL, cxb, ty + 0.6f, Formes.argb(GRIS, 1f), 0.62f);
        cxb += Texte.largeur(font, "Z", Ui.SMALL, 0.62f) + 3;
        Texte.ecrire(g, font, zs, Ui.FONT, cxb, ty, Formes.argb(TEXTE, 1f), s);
        cxb += lnum;
        cxb = separateur(g, cxb, y, sep);
        if (carre != null) {
            Texte.espacee(g, font, I18n.get("heraliz_carte.bas.carre"), Ui.SMALL, cxb, ty + 1, Formes.argb(GRIS, 1f), 0.55f, 0.8f, false);
            cxb += Texte.largeurEspacee(font, I18n.get("heraliz_carte.bas.carre"), Ui.SMALL, 0.55f, 0.8f) + 3;
            Texte.ecrire(g, font, carre, Ui.FONT, cxb, ty, Formes.argb(TEXTE, 1f), s);
            cxb += lcarre;
            cxb = separateur(g, cxb, y, sep);
        }
        // barre d'échelle : trait blanc avec deux petites pattes
        int bx0 = Math.round(cxb), bx1 = Math.round(cxb + barre), byb = Math.round(y + 12);
        g.fill(bx0, byb, bx1, byb + 1, 0xFFFFFFFF);
        g.fill(bx0, byb - 3, bx0 + 1, byb + 1, 0xFFFFFFFF);
        g.fill(bx1 - 1, byb - 3, bx1, byb + 1, 0xFFFFFFFF);
        Texte.ecrire(g, font, echelle, Ui.FONT, cxb + barre + 4, ty, Formes.argb(TEXTE, 1f), s);
        cxb += le;
        cxb = separateur(g, cxb, y, sep);
        for (String[] a : aides) {
            float lt = Texte.largeur(font, a[0], Ui.BOLD, 0.5f) + 6;
            Ui.soft(g, Math.round(cxb), Math.round(y + 5), Math.round(lt), 9, 0x1FFFFFFF);
            Texte.ecrire(g, font, a[0], Ui.BOLD, cxb + 3, y + 6.8f, 0xFFFFFFFF, 0.5f);
            cxb += lt + 2;
            Texte.ecrire(g, font, a[1], Ui.FONT, cxb, ty + 0.4f, Formes.argb(GRIS, 1f), 0.55f);
            cxb += Texte.largeur(font, a[1], Ui.FONT, 0.55f) + 7;
        }
    }

    private float separateur(GuiGraphics g, float x, float y, float sep) {
        float m = x + sep / 2;
        g.fill(Math.round(m), Math.round(y + 5), Math.round(m) + 1, Math.round(y + 14), 0x24FFFFFF);
        return x + sep;
    }

    private void boussole(GuiGraphics g) {
        float X = width - 19, Y = height - 19;
        Formes.debut(g);
        Formes.disque(X, Y, 8.7f, Formes.argb(VERRE, 0.8f));
        Formes.anneau(X, Y, 8.7f, unPixel, 0x2EFFFFFF);
        Formes.fin();
        Icones.debut(g);
        Icones.icone(Icones.NORD, X, Y - 2.2f, 5.5f, 0xFFFF6B6B);
        Icones.fin();
        Texte.espaceeCentree(g, font, "N", Ui.SMALL, X, Y + 2.6f, 0xFFFFFFFF, 0.5f, 0, false);
    }

    // =================================================================== menu « marquer ici »
    private void menuMarquer(GuiGraphics g, int mx, int my) {
        PingType[] types = PingType.values();
        String titre = I18n.get("heraliz_carte.menu.titre");
        float w = Math.max(100, Texte.largeurEspacee(font, titre, Ui.SMALL, 0.62f, 0.9f) + 16), h = 19 + types.length * 12 + 4;
        float x = Mth.clamp(menuX, 4, width - w - 4), y = Mth.clamp(menuY, 4, height - h - 4);
        Ui.glass(g, Math.round(x), Math.round(y), Math.round(w), Math.round(h));
        Texte.espacee(g, font, titre, Ui.SMALL, x + 8, y + 7, Formes.argb(GRIS, 1f), 0.62f, 0.9f, false);
        // un clic dans le menu ne traverse pas ; seules ses cibles comptent tant qu'il est ouvert
        menuDebut = cibles.size();
        cibles.add(new Cible(x, y, x + w, y + h, () -> {
        }));
        Icones.debut(g);
        for (int i = 0; i < types.length; i++) {
            float ly = y + 18 + i * 12;
            Icones.icone(Icones.LOSANGE, x + 12, ly + 5, 3.6f, 0xFF000000 | types[i].couleur);
        }
        Icones.fin();
        for (int i = 0; i < types.length; i++) {
            PingType t = types[i];
            float ly = y + 18 + i * 12;
            boolean survol = mx >= x + 3 && mx < x + w - 3 && my >= ly - 1 && my < ly + 11;
            if (survol) Ui.soft(g, Math.round(x + 3), Math.round(ly - 1), Math.round(w - 6), 12, 0x14FFFFFF);
            Texte.ecrire(g, font, I18n.get(t.cle()), Ui.FONT, x + 21, ly + 1.2f, Formes.argb(survol ? 0xFFFFFF : TEXTE, 1f), 0.66f);
            cibles.add(new Cible(x + 3, ly - 1, x + w - 3, ly + 11, () -> {
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
                if (Math.hypot(sx(v.x) - mx, sy(v.z) - my) > 6 * e) continue;
                lignes.add(Ui.lit(I18n.get(v.type.cle()), Ui.BOLD));
                lignes.add(Ui.lit(I18n.get("heraliz_carte.ping.de", v.nom), Ui.FONT));
                lignes.add(Ui.lit(metres(Math.hypot(v.x - px, v.z - pz)), Ui.FONT));
                if (v.moi) lignes.add(Ui.lit(I18n.get("heraliz_carte.ping.retirer"), Ui.SMALL));
                accent = v.type.couleur;
            }
        }
        if (lignes.isEmpty() && extractions) {
            for (Sources.Extraction x : ouvertes) {
                if (Math.hypot(sx(x.x()) - mx, sy(x.z()) - my) > 6 * e) continue;
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
                if (l.type().texteSeul() || Math.hypot(sx(l.x() + 0.5) - mx, sy(l.z() + 0.5) - my) > 6 * e) continue;
                lignes.add(Ui.lit(l.nom(), Ui.BOLD));
                lignes.add(Ui.lit(I18n.get(l.type().cle()), Ui.FONT));
                lignes.add(Ui.lit(metres(Math.hypot(l.x() + 0.5 - px, l.z() + 0.5 - pz)), Ui.FONT));
                accent = l.type().couleur;
                break;
            }
        }
        if (lignes.isEmpty() && clan) {
            for (Sources.Allie a : allies) {
                if (Math.hypot(sx(a.x()) - mx, sy(a.z()) - my) > 5 * e) continue;
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
        return selection != null && x > width - MARGE - FICHE_L && y > MARGE + 25 && y < ficheY1;
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
            // clic droit sur son propre ping : le retirer ; ailleurs : menu « marquer ici »
            float e = e();
            for (int i = 0; i < PingsClient.n; i++) {
                PingsClient.Visuel v = PingsClient.PINGS[i];
                if (v.moi && Math.hypot(sx(v.x) - x, sy(v.z) - y) <= 6 * e) {
                    if (minecraft.getConnection() != null) PingNet.versServeur(new Paquets.AnnulerC2S(v.id));
                    SonsUi.clic(0.4f);
                    return true;
                }
            }
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
        // le point du monde sous le curseur reste sous le curseur
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
