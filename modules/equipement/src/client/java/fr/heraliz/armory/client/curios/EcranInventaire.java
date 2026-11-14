package fr.heraliz.armory.client.curios;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heraliz.armory.HeralizArmory;
import fr.heraliz.noyau.client.BadgeRarete;
import fr.heraliz.noyau.client.effets.EffetsJoueur;
import fr.heraliz.noyau.client.effets.IconeEffet;
import fr.heraliz.noyau.client.kit.CaseVerre;
import fr.heraliz.noyau.client.kit.CasesEcran;
import fr.heraliz.noyau.client.kit.EcranVerre;
import fr.heraliz.noyau.client.kit.Echelle;
import fr.heraliz.noyau.client.kit.FondVerre;
import fr.heraliz.noyau.client.kit.Formes;
import fr.heraliz.noyau.client.kit.InfobulleVerre;
import fr.heraliz.noyau.client.kit.InventaireVerre;
import fr.heraliz.noyau.client.kit.Palette;
import fr.heraliz.noyau.client.kit.PoliceHud;
import fr.heraliz.noyau.client.kit.TexteHud;
import fr.heraliz.noyau.client.kit.Ui;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import top.theillusivec4.curios.api.client.ICuriosScreen;
import top.theillusivec4.curios.client.gui.CuriosScreenV2;
import top.theillusivec4.curios.common.inventory.CurioSlot;
import top.theillusivec4.curios.common.inventory.container.CuriosContainerV2;

/**
 * L'inventaire du joueur, D1 « Verre » (rendu final validé par le propriétaire le 14/11), à la place de l'écran de
 * Curios ({@link InventaireCurios}) :
 * <ul>
 *     <li>un panneau de verre (380 x 226) sur le monde flouté, comme les écrans du clan ; en haut « INVENTAIRE » et la
 *     fine bande des effets (icônes dans un anneau fin, bonus de loot en doré tout à gauche), détail au survol ;</li>
 *     <li>à gauche, le personnage entre ses cases d'équipement (cases de 22) : casque, masque, gilet, plastron, jambières,
 *     bottes ; sac, corps, mains, brassard, genoux ;</li>
 *     <li>à droite, « Sur toi » (27 cases) et « Accès rapide » (la barre), puis la carte du sac à dos porté (Ouvrir, ou
 *     sa touche, B par défaut) ;</li>
 *     <li>plus d'artisanat 2 x 2 (bloqué par le serveur à part) ni de main gauche (retirée le 31/10), ni d'aides.</li>
 * </ul>
 * Le serveur garde le même menu de Curios (CuriosContainerV2) : mêmes cases aux mêmes numéros, mêmes règles
 * (anti-duplication comprise) ; seules les places des cases changent chez le joueur ({@link CasesEcran}). Quand Curios
 * refait ses cases (changement de ses cases), la disposition est refaite. Rien n'est alloué à chaque image (textes et
 * infobulles gardés, boucles par numéro).
 */
public final class EcranInventaire extends AbstractContainerScreen<CuriosContainerV2> implements ICuriosScreen, EcranVerre {
    // ------------------------------------------------------------------ cotes du rendu validé (unités, depuis le panneau)
    static final int L = 380, H = 226;
    private static final float TITRE_CY = 11.5f, ETIQUETTE_CY = 33f;
    private static final int SEPARATION = 22;
    /** Équipement : cases de 22 (objet à 3 du bord), colonnes gauche et droite, 6 rangées au pas de 30. */
    private static final int EQ_CADRE = 22, EQ_PAD = 3, EQ_Y0 = 40, EQ_PAS = 30, COL_G = 12, COL_D = 134;
    /** Puits du personnage, pieds et échelle du personnage. */
    private static final int PUITS_X0 = 40, PUITS_X1 = 128, PIEDS = 184, ECHELLE = 54;
    /** « Sur toi » + « Accès rapide », carte du sac. */
    private static final int G_X = 172, G_Y0 = 40, BARRE_Y = 130, SAC_Y = 177, SAC_H = 35;
    private static final int CADRE = InventaireVerre.CADRE, PAD = InventaireVerre.PAD;
    /** Rangée (0 en haut, colonne de gauche) des cases d'armure, par case de l'inventaire : 36 pieds … 39 tête. */
    private static final int[] RANG_ARMURE = {5, 4, 3, 0};
    /** Cases Curios : identifiant, colonne (0 gauche, 1 droite), rangée. */
    private static final String[] CURIOS = {"mask", "belt", "back", "body", "hands", "bracelet", "knees"};
    private static final int[] CURIOS_COL = {0, 0, 1, 1, 1, 1, 1}, CURIOS_RANG = {1, 2, 0, 1, 2, 3, 4};
    private static final int SAC = 2;
    /** Cases Curios en plus (une 2e case d'un même type…) : sous le dernier « genoux », puis au pied du personnage. */
    private static final int[] EXTRAS_X = {COL_D, PUITS_X0 + 3, PUITS_X0 + 33, PUITS_X0 + 63};

    private final InventaireVerre inventaire = new InventaireVerre();
    private final TexteHud titre = new TexteHud(PoliceHud.TEXTE_GRAS, 1.4f), equipement = new TexteHud(PoliceHud.MINI_GRAS, 0.9f),
            sacEtiquette = new TexteHud(PoliceHud.MINI_GRAS, 0.9f), sacNom = new TexteHud(PoliceHud.TEXTE_GRAS),
            sacRarete = new TexteHud(PoliceHud.MINI), ouvrir = new TexteHud(PoliceHud.MINI_GRAS, 0.9f),
            toucheSac = new TexteHud(PoliceHud.MINI_GRAS), sansSac = new TexteHud(PoliceHud.MINI);
    private final InfobulleVerre.Bulle bulle = new InfobulleVerre.Bulle();
    private final List<Component> lignesBulle = new ArrayList<>(3);
    private long signature;
    private Slot caseSac;
    private ItemStack sacVu;
    private int sacCouleur = -1;
    /** Bouton « Ouvrir » de la carte du sac (depuis le panneau), largeur 0 : pas de bouton. */
    private int boutonX, boutonY, boutonL, boutonH;
    /** Effets de la bande : numéro dans la liste du noyau et centre (x absolu), effet survolé (-1 : aucun). */
    private final int[] effetsI = new int[EffetsJoueur.MAX];
    private final float[] effetsX = new float[EffetsJoueur.MAX];
    private int nEffets, effetSurvole = -1;
    private MobEffectInstance bulleEffet;
    private int bulleSecondes = -1;
    private static boolean extrasSignales;

    public EcranInventaire(CuriosContainerV2 menu, Inventory inv, Component titre) {
        super(menu, inv, titre);
        imageWidth = L;
        imageHeight = H;
        inventaire.disposer(G_X, G_Y0, BARRE_Y);
    }

    private static String t(String cle, String francais) {
        return Component.translatableWithFallback("gui.heraliz_armory.inventaire." + cle, francais).getString();
    }

    @Override
    protected void init() {
        Echelle.forcer(this, L, H);
        super.init();
        titre.texte(t("titre", "Inventaire"));
        equipement.texte(t("equipement", "Équipement"));
        sacEtiquette.texte(t("sac", "Sac à dos"));
        ouvrir.texte(t("ouvrir", "Ouvrir"));
        sansSac.texte(t("sans_sac", "Aucun sac à dos"));
        KeyMapping k = SacClient.touche();
        toucheSac.texte(k == null ? "" : k.getTranslatedKeyMessage().getString());
        inventaire.textes();
        sacVu = null;
        bulle.oublier();
        bulleEffet = null;
        disposer();
    }

    @Override
    public void removed() {
        Echelle.restaurer();
        super.removed();
    }

    // ------------------------------------------------------------------ places des cases
    private void disposer() {
        Inventory inv = minecraft.player.getInventory();
        boolean[] pris = new boolean[CURIOS.length];
        int extras = 0;
        caseSac = null;
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            if (CasesEcran.cachee(s) || inventaire.placer(s, inv)) continue;
            if (s.container == inv) {
                int cs = s.getContainerSlot();
                if (cs >= 36 && cs <= 39) equipement(s, 0, RANG_ARMURE[cs - 36]);
                else CasesEcran.cacher(menu, i);
                continue;
            }
            if (s instanceof CurioSlot c && !c.isCosmetic()) {
                int k = indexCurio(c.getIdentifier());
                if (k >= 0 && !pris[k]) {
                    pris[k] = true;
                    equipement(s, CURIOS_COL[k], CURIOS_RANG[k]);
                    if (k == SAC) caseSac = s;
                } else if (extras < EXTRAS_X.length) {
                    CasesEcran.placer(s, EXTRAS_X[extras] + EQ_PAD, EQ_Y0 + 5 * EQ_PAS + EQ_PAD);
                    extras++;
                } else {
                    CasesEcran.cacher(menu, i);
                    if (!extrasSignales) {
                        extrasSignales = true;
                        HeralizArmory.LOGGER.warn("[armurerie] inventaire : trop de cases Curios pour l'écran ({} cachée)", c.getIdentifier());
                    }
                }
                continue;
            }
            // artisanat 2 x 2 et son résultat (bloqués par le serveur), cases cosmétiques : cachés
            CasesEcran.cacher(menu, i);
        }
        signature = CasesEcran.signature(menu);
    }

    private static int indexCurio(String id) {
        for (int k = 0; k < CURIOS.length; k++) {
            if (CURIOS[k].equals(id)) return k;
        }
        return -1;
    }

    private static void equipement(Slot s, int col, int rang) {
        CasesEcran.placer(s, (col == 0 ? COL_G : COL_D) + EQ_PAD, EQ_Y0 + rang * EQ_PAS + EQ_PAD);
    }

    /** Toute la case est sensible : 22 pour l'équipement (à gauche de la grille), 20 pour « Sur toi » et la barre. */
    @Override
    protected boolean isHovering(int x, int y, int w, int h, double mx, double my) {
        if (w == 16 && h == 16) {
            int m = x < G_X ? EQ_PAD : PAD;
            double rx = mx - leftPos, ry = my - topPos;
            return rx >= x - m && rx < x + 16 + m && ry >= y - m && ry < y + 16 + m;
        }
        return super.isHovering(x, y, w, h, mx, my);
    }

    // ------------------------------------------------------------------ dessin
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        try {
            if (CasesEcran.signature(menu) != signature) disposer();
            FondVerre.dessiner(g, width, height);
            super.render(g, mx, my, pt);
            if (effetSurvole >= 0 && menu.getCarried().isEmpty()) bulleEffet(g);
            else renderTooltip(g, mx, my);
        } catch (RuntimeException ex) {
            // filet : jamais de plantage du jeu à cause de l'inventaire ; l'écran de Curios prend la suite (même menu)
            InventaireCurios.panneVerre(ex);
            minecraft.setScreen(new CuriosScreenV2(menu, minecraft.player.getInventory(), title));
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        // tout est dessiné par renderBg
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = leftPos, y = topPos;
        Ui.glass(g, x, y, L, H, 205);
        Matrix4f m = g.pose().last().pose();
        VertexConsumer vc = Formes.tampon(g);
        Formes.rect(vc, m, x + 10, y + SEPARATION, x + L - 10, y + SEPARATION + Formes.pixel(), Palette.argb(0xFFFFFF, 23));
        // puits du personnage : verre léger et halo
        float ph = 5 * EQ_PAS + EQ_CADRE;
        Formes.rect(vc, m, x + PUITS_X0 + 1, y + EQ_Y0, x + PUITS_X1 - 1, y + EQ_Y0 + ph, Palette.argb(0xFFFFFF, 9));
        Formes.rect(vc, m, x + PUITS_X0, y + EQ_Y0 + 1, x + PUITS_X0 + 1, y + EQ_Y0 + ph - 1, Palette.argb(0xFFFFFF, 9));
        Formes.rect(vc, m, x + PUITS_X1 - 1, y + EQ_Y0 + 1, x + PUITS_X1, y + EQ_Y0 + ph - 1, Palette.argb(0xFFFFFF, 9));
        Formes.halo(vc, m, x + (PUITS_X0 + PUITS_X1) / 2f, y + EQ_Y0 + ph * 0.45f, 60f, Palette.argb(0xFFFFFF, 10));
        // cases
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            if (!s.isActive()) continue;
            boolean grande = s.x < G_X;
            int pad = grande ? EQ_PAD : PAD;
            CaseVerre.dessiner(vc, m, x + s.x - pad, y + s.y - pad, grande ? EQ_CADRE : CADRE, isHovering(s.x, s.y, 16, 16, mx, my));
        }
        carteSac(vc, m, x, y, mx, my);
        anneauxEffets(vc, m, x, y, mx, my);
        g.flush();
        // icônes des effets, puis leurs points rouges
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        EffetsJoueur.Liste l = EffetsJoueur.INVENTAIRE;
        float cy = y + TITRE_CY;
        boolean point = false;
        for (int k = 0; k < nEffets; k++) {
            IconeEffet.icone(g, l.effet(effetsI[k]).getEffect(), effetsX[k], cy);
            point |= l.categorie(effetsI[k]) == EffetsJoueur.SOIN;
        }
        RenderSystem.disableBlend();
        if (point) {
            float p = EffetsJoueur.pulsation();
            vc = Formes.tampon(g);
            for (int k = 0; k < nEffets; k++) {
                if (l.categorie(effetsI[k]) == EffetsJoueur.SOIN) IconeEffet.point(vc, m, effetsX[k], cy, p);
            }
            g.flush();
        }
        // personnage (il suit la souris, comme dans l'inventaire de Minecraft)
        int px = x + (PUITS_X0 + PUITS_X1) / 2, py = y + PIEDS;
        InventoryScreen.renderEntityInInventoryFollowsMouse(g, px, py, ECHELLE, (float) px - mx, (float) (py - ECHELLE * 5 / 3) - my, minecraft.player);
        // textes
        int attenue = Palette.argb(Palette.ATTENUE, 255);
        titre.ajouter(g, x + 12, y + TITRE_CY, Palette.argb(Palette.TEXTE, 255), TexteHud.GAUCHE, false);
        equipement.ajouter(g, x + COL_G, y + ETIQUETTE_CY, attenue, TexteHud.GAUCHE, false);
        sacEtiquette.ajouter(g, x + G_X, y + SAC_Y - InventaireVerre.ETIQUETTE, attenue, TexteHud.GAUCHE, false);
        inventaire.etiquettes(g, x, y);
        textesSac(g, x, y);
        g.flush();
        if (sacVu != null && !sacVu.isEmpty()) g.renderItem(sacVu, x + G_X + 8, y + SAC_Y + SAC_H / 2 - 8);
    }

    // ------------------------------------------------------------------ carte du sac à dos
    private void carteSac(VertexConsumer vc, Matrix4f m, int x, int y, int mx, int my) {
        ItemStack sac = caseSac == null ? ItemStack.EMPTY : caseSac.getItem();
        if (sacVu == null || !ItemStack.isSameItemSameTags(sacVu, sac)) majSac(sac);
        float x0 = x + G_X, y0 = y + SAC_Y, w = 8 * InventaireVerre.PAS + CADRE;
        Formes.rect(vc, m, x0 + 1, y0, x0 + w - 1, y0 + SAC_H, Palette.argb(0xFFFFFF, 11));
        Formes.rect(vc, m, x0, y0 + 1, x0 + 1, y0 + SAC_H - 1, Palette.argb(0xFFFFFF, 11));
        Formes.rect(vc, m, x0 + w - 1, y0 + 1, x0 + w, y0 + SAC_H - 1, Palette.argb(0xFFFFFF, 11));
        if (boutonL <= 0) return;
        boolean survol = surBouton(mx, my);
        float bx = x + boutonX, by = y + boutonY;
        Formes.rect(vc, m, bx + 3, by, bx + boutonL - 3, by + boutonH, Palette.argb(0xFFFFFF, survol ? 46 : 26));
        Formes.disque(vc, m, bx + 3, by + boutonH / 2f, boutonH / 2f, Palette.argb(0xFFFFFF, survol ? 46 : 26), Formes.pixel());
        Formes.disque(vc, m, bx + boutonL - 3, by + boutonH / 2f, boutonH / 2f, Palette.argb(0xFFFFFF, survol ? 46 : 26), Formes.pixel());
        if (!toucheSac.vide()) {
            float kx = bx + boutonL - 14, ky = by + 2;
            Formes.rect(vc, m, kx + 1, ky, kx + 9, ky + 10, Palette.argb(0xFFFFFF, 41));
            Formes.rect(vc, m, kx, ky + 1, kx + 1, ky + 9, Palette.argb(0xFFFFFF, 41));
            Formes.rect(vc, m, kx + 9, ky + 1, kx + 10, ky + 9, Palette.argb(0xFFFFFF, 41));
        }
    }

    /** Textes de la carte, refaits seulement quand le sac porté change. */
    private void majSac(ItemStack sac) {
        sacVu = sac.copy();
        boutonL = 0;
        sacCouleur = -1;
        if (sac.isEmpty()) {
            sacNom.texte("");
            sacRarete.texte("");
            return;
        }
        sacNom.texte(sac.getHoverName().getString());
        int c = CaseVerre.couleurRarete(sac);
        int rang = c < 0 ? -1 : BadgeRarete.rangCouleur(c);
        sacCouleur = c;
        sacRarete.texte(rang < 0 ? "" : BadgeRarete.nom(rang));
        boutonL = Math.round(ouvrir.largeur()) + 7 + (toucheSac.vide() ? 7 : 4 + 10 + 4);
        boutonH = 14;
        boutonX = G_X + 8 * InventaireVerre.PAS + CADRE - 6 - boutonL;
        boutonY = SAC_Y + SAC_H / 2 - boutonH / 2;
    }

    private void textesSac(GuiGraphics g, int x, int y) {
        float cy = y + SAC_Y + SAC_H / 2f;
        if (sacVu == null || sacVu.isEmpty()) {
            sansSac.ajouter(g, x + G_X + 12, cy, Palette.argb(Palette.ATTENUE, 255), TexteHud.GAUCHE, false);
            return;
        }
        boolean rarete = !sacRarete.vide();
        sacNom.ajouter(g, x + G_X + 32, rarete ? cy - 4.5f : cy, Palette.argb(Palette.TEXTE, 255), TexteHud.GAUCHE, false);
        if (rarete) sacRarete.ajouter(g, x + G_X + 32, cy + 5.5f, Palette.argb(Palette.clair(sacCouleur, 0.2f), 255), TexteHud.GAUCHE, false);
        if (boutonL <= 0) return;
        float bx = x + boutonX, bcy = y + boutonY + boutonH / 2f;
        ouvrir.ajouter(g, bx + 7, bcy, Palette.argb(Palette.TEXTE, 255), TexteHud.GAUCHE, false);
        if (!toucheSac.vide()) toucheSac.ajouter(g, bx + boutonL - 9, bcy, Palette.argb(Palette.TEXTE, 255), TexteHud.CENTRE, false);
    }

    private boolean surBouton(double mx, double my) {
        double rx = mx - leftPos, ry = my - topPos;
        return boutonL > 0 && rx >= boutonX && rx < boutonX + boutonL && ry >= boutonY && ry < boutonY + boutonH;
    }

    private boolean ouvrirSac() {
        if (sacVu == null || sacVu.isEmpty() || !menu.getCarried().isEmpty()) return false;
        SacClient.ouvrir();
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int bouton) {
        if (bouton == 0 && surBouton(mx, my) && ouvrirSac()) return true;
        return super.mouseClicked(mx, my, bouton);
    }

    @Override
    public boolean keyPressed(int touche, int code, int modifs) {
        KeyMapping k = SacClient.touche();
        if (k != null && k.matches(touche, code) && ouvrirSac()) return true;
        return super.keyPressed(touche, code, modifs);
    }

    // ------------------------------------------------------------------ bande des effets
    /** Anneaux de la bande (de droite à gauche ; le bonus de loot tout à gauche, un peu à part) et effet survolé. */
    private void anneauxEffets(VertexConsumer vc, Matrix4f m, int x, int y, int mx, int my) {
        EffetsJoueur.Liste l = EffetsJoueur.INVENTAIRE;
        float cy = y + TITRE_CY, cx = x + L - 12 - 6.5f, limite = x + 12 + titre.largeur() + 16;
        nEffets = 0;
        effetSurvole = -1;
        for (int passe = 0; passe < 2; passe++) {
            for (int i = 0; i < l.taille(); i++) {
                boolean loot = l.categorie(i) == EffetsJoueur.LOOT;
                if (loot != (passe == 1)) continue;
                if (loot && nEffets > 0) cx -= 4;
                if (cx - 6.5f < limite) break;
                effetsI[nEffets] = i;
                effetsX[nEffets] = cx;
                nEffets++;
                cx -= IconeEffet.PAS;
            }
        }
        for (int k = 0; k < nEffets; k++) {
            int i = effetsI[k];
            IconeEffet.anneau(vc, m, effetsX[k], cy, l.categorie(i), l.reste(i), false);
            if (Math.abs(mx - effetsX[k]) <= 7f && Math.abs(my - cy) <= 7f) {
                effetSurvole = k;
                IconeEffet.survol(vc, m, effetsX[k], cy);
            }
        }
    }

    /** Détail de l'effet survolé : sous la bande, dans le panneau ; lignes refaites seulement quand elles changent. */
    private void bulleEffet(GuiGraphics g) {
        EffetsJoueur.Liste l = EffetsJoueur.INVENTAIRE;
        int k = effetSurvole, i = effetsI[k];
        if (i >= l.taille()) return;
        MobEffectInstance e = l.effet(i);
        int cat = l.categorie(i), secondes = (e.getDuration() + 19) / 20;
        if (e != bulleEffet || secondes != bulleSecondes) {
            bulleEffet = e;
            bulleSecondes = secondes;
            lignesBulle.clear();
            lignesBulle.add(nomEffet(e, cat).withStyle(st -> st.withFont(Ui.BOLD).withColor(Palette.clair(IconeEffet.couleur(cat), 0.3f))));
            String aide = switch (cat) {
                case EffetsJoueur.SOIN -> t("effet.soin", "À soigner : ne part pas tout seul");
                case EffetsJoueur.ZONE -> t("effet.zone", "Effet de zone : il s'arrête quand tu en sors");
                case EffetsJoueur.LOOT -> t("effet.loot", "Plus d'objets dans les conteneurs");
                default -> null;
            };
            if (aide != null) lignesBulle.add(Ui.lit(aide, Ui.FONT).withStyle(st -> st.withColor(Palette.ATTENUE)));
            if (cat != EffetsJoueur.SOIN && cat != EffetsJoueur.ZONE && !EffetsJoueur.sansFin(e)) {
                String reste = Component.translatableWithFallback("gui.heraliz_armory.inventaire.effet.encore", "Encore %s", duree(secondes)).getString();
                lignesBulle.add(Ui.lit(reste, Ui.FONT).withStyle(st -> st.withColor(Palette.TEXTE)));
            }
            bulle.remplir(e, font, lignesBulle);
        }
        int gauche = Math.min(Math.round(effetsX[k]) - 8, leftPos + L - 12 - bulle.largeur());
        bulle.dessiner(g, font, Math.max(leftPos + 8, gauche), topPos + SEPARATION + 8, IconeEffet.couleur(cat));
    }

    private static MutableComponent nomEffet(MobEffectInstance e, int cat) {
        int niveau = e.getAmplifier() + 1;
        if (cat == EffetsJoueur.LOOT) {
            return Component.translatableWithFallback("effect.heraliz_grades.bonus_loot.niveau", "Bonus de loot +%s %%", String.valueOf(niveau));
        }
        MutableComponent c = e.getEffect().getDisplayName().copy();
        if (niveau >= 2 && niveau <= 10) c.append(CommonComponents.SPACE).append(Component.translatable("enchantment.level." + niveau));
        return c;
    }

    /** « 0:48 », « 12:05 », « 1 h 52 ». */
    private static String duree(int secondes) {
        if (secondes >= 3600) {
            int h = secondes / 3600, min = (secondes % 3600) / 60;
            return Component.translatableWithFallback("gui.heraliz_armory.inventaire.effet.heures", "%s h %s", String.valueOf(h),
                    (min < 10 ? "0" : "") + min).getString();
        }
        int min = secondes / 60, s = secondes % 60;
        return min + ":" + (s < 10 ? "0" : "") + s;
    }
}
