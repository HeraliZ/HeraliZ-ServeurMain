package fr.heraliz.grades.client;

import fr.heraliz.grades.client.boutique.Trace;
import fr.heraliz.grades.client.boutique.Trace.P;
import fr.heraliz.grades.client.kit.Echelle;
import fr.heraliz.grades.client.kit.SonsUi;
import fr.heraliz.grades.hdv.RegleHdv;
import fr.heraliz.grades.reseau.ReseauHdv;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static fr.heraliz.grades.client.boutique.Trace.a;

/**
 * Hôtel des ventes (Grades 2.9.0), d'après les maquettes validées le 28/10 ({@code maquettes/hotel_ventes/}, 1920 x 1080 :
 * toutes les mesures divisées par 3 sur le canevas 640 x 360 forcé par {@link Echelle}). Onglets Acheter (filtres, liste
 * paginée par le serveur, détail de l'offre, achat en deux clics), Vendre (case de l'inventaire, monnaie, prix, durée),
 * Mes ventes (en cours, vendues, expirées, à récupérer). Textes DM Sans à leur vraie taille ({@link Trace}, une police par
 * taille), posés sur un point de l'écran. L'écran ne décide rien : il envoie des identifiants et un prix, le serveur relit.
 */
public final class EcranHdv extends Screen {
    private static final int GW = 640, GH = 360;
    private static final int OR = 0xE7C15A, OR_SOMBRE = 0x3A321A, TEXTE = 0xECEEF2, GRIS = 0x8B9099, LIGNE = 0x262A33, CLAIR = 0xC9CCD2,
            BORD = 0x343944, PB_C = 0xA970FF, ROUGE = 0xEF5458, VERT = 0x62C46A, FOND = 0x0E1014, NUIT = 0x07080B, SOMBRE = 0x1B1608;
    private static final int[] RARETE = {0xE9EBEF, 0x62C46A, 0x4F9DFF, 0xB47BFF, 0xF2B33D, 0xEF5458};
    private static final int SANS_RARETE = 0x8E939B;

    private static final P TITRE = Trace.p("bq_nom", 11, 0), SOUS = Trace.p("bq_petit", 6.5F, 1.33F), TXT = Trace.p("bq_petit", 6.5F, 0),
            GRAS = Trace.p("bq_mini", 6.5F, 0), ONGLET = Trace.p("hv_onglet", 6, 1.33F), LABEL = Trace.p("hv_label", 5, 1.33F),
            PETIT = Trace.p("hv_petit", 17 / 3F, 0), NOM_GRAND = Trace.p("bq_bouton", 9, 0), PAIE = Trace.p("bq_texte_gras", 7.5F, 0),
            BOUTON = Trace.p("bq_mini", 6.5F, 2), BOUTON_PETIT = Trace.p("hv_label", 5, 1.33F), TOUCHE = Trace.p("hv_label", 5, 1),
            RARE = Trace.p("hv_onglet", 6, 1), BADGE = Trace.p("hv_badge", 11 / 3F, 0), PASTILLE = Trace.p("hv_label", 5, 0);

    private static final ResourceLocation TEX_PIECE = new ResourceLocation("heraliz_grades", "textures/gui/boutique/piece.png");

    private ReseauHdv.Etat etat;
    private ReseauHdv.Page page;
    private List<ReseauHdv.LigneMv> mes = new ArrayList<>();
    private int onglet;
    // filtres de la liste
    private String recherche = "";
    private List<String> idsRecherche = new ArrayList<>();
    private int categorie, devise, tri, numPage;
    private long rechercheChangeeA = -1, dernierePage, derniereMes;
    private String choisie;
    private String armee;
    private long armeeA;
    private boolean attente;
    // vendre
    private int slot = -1, deviseVente = RegleHdv.HERALS, duree = -1;
    private String prix = "";
    private int focus;   // 0 rien, 1 recherche, 2 prix
    private int defile;
    // message de la dernière action
    private String message = "";
    private boolean messageOk;
    private long messageA;
    private int ox, oy, sx, sy;
    private final List<Zone> zones = new ArrayList<>();

    private record Zone(float x, float y, float w, float h, Runnable action) {
        boolean dedans(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private EcranHdv(ReseauHdv.Etat e) {
        super(Component.translatable("gui.heraliz_grades.hdv.titre"));
        etat = e;
    }

    // ------------------------------------------------------------------ réception
    public static void recevoir(ReseauHdv.Etat e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof EcranHdv h) {
            h.etat = e;
            h.attente = false;
            if (!e.message().isEmpty()) {
                h.message = e.message();
                h.messageOk = e.ok();
                h.messageA = System.currentTimeMillis();
                h.armee = null;
                if (e.ok()) SonsUi.ouvrir(0.7F);
                if (h.onglet == 0) h.demanderPage();
                if (e.ok() && (e.message().startsWith("en_vente"))) {
                    h.slot = -1;
                    h.prix = "";
                }
            }
        } else if (e.ouvrir()) {
            SonsUi.ouvrir(0.6F);
            EcranHdv h = new EcranHdv(e);
            mc.setScreen(h);
            h.demanderPage();
        }
    }

    public static void recevoirPage(ReseauHdv.Page p) {
        if (Minecraft.getInstance().screen instanceof EcranHdv h) {
            h.page = p;
            h.numPage = p.page();
            if (h.choisie != null && p.lignes().stream().noneMatch(l -> l.id().equals(h.choisie))) h.choisie = null;
            if (h.choisie == null && !p.lignes().isEmpty()) h.choisie = p.lignes().get(0).id();
        }
    }

    public static void recevoirMes(ReseauHdv.MesVentes m) {
        if (Minecraft.getInstance().screen instanceof EcranHdv h) {
            h.mes = m.lignes();
            h.attente = false;
        }
    }

    // ------------------------------------------------------------------ demandes
    private void demanderPage() {
        dernierePage = System.currentTimeMillis();
        ReseauHdv.CANAL.sendToServer(new ReseauHdv.Liste(recherche, idsRecherche, categorie, devise, tri, numPage));
    }

    private void demanderMes() {
        derniereMes = System.currentTimeMillis();
        ReseauHdv.CANAL.sendToServer(new ReseauHdv.Action(ReseauHdv.MES_VENTES, "", 0, 0, 0, 0, 0));
    }

    private static void action(int type, String id, int slot, int devise, long prix, int duree, int nombre) {
        ReseauHdv.CANAL.sendToServer(new ReseauHdv.Action(type, id, slot, devise, prix, duree, nombre));
    }

    /** Objets dont le nom, dans la langue du joueur, contient la recherche (le serveur ne connaît pas les traductions). */
    private static List<String> objetsQuiCorrespondent(String q) {
        List<String> r = new ArrayList<>();
        if (q.length() < 2) return r;
        for (Item it : ForgeRegistries.ITEMS) {
            String n = RegleHdv.normaliser(new ItemStack(it).getHoverName().getString());
            if (n.contains(q)) {
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(it);
                if (id != null) r.add(id.toString());
                if (r.size() >= 64) break;
            }
        }
        return r;
    }

    // ------------------------------------------------------------------ outils
    private static String t(String cle, Object... args) {
        return Component.translatable("gui.heraliz_grades.hdv." + cle, args).getString();
    }

    private static String nombre(long v) {
        String s = String.format(Locale.ROOT, "%,d", v);
        return fr.heraliz.grades.client.kit.Ui.french() ? s.replace(',', ' ') : s;
    }

    private static int couleur(int rarete) {
        return rarete < 0 || rarete >= RARETE.length ? SANS_RARETE : RARETE[rarete];
    }

    private static String nom(ItemStack st) {
        String n = ChatFormatting.stripFormatting(st.getHoverName().getString());
        return n == null ? "" : n;
    }

    private static String duree(long ms) {
        long min = Math.max(1, ms / 60_000);
        if (min >= 60) return t("heures", min / 60);
        return t("minutes", min);
    }

    private static String devise(int d) {
        return d == RegleHdv.PB ? "PB" : t("herals");
    }

    private void zone(float x, float y, float w, float h, Runnable r) {
        zones.add(new Zone(x, y, w, h, r));
    }

    private boolean survol(float x, float y, float w, float h) {
        double mx = sx - ox, my = sy - oy;
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ------------------------------------------------------------------ écran
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
        action(ReseauHdv.FERMER, "", 0, 0, 0, 0, 0);
        super.removed();
    }

    @Override
    public void tick() {
        long now = System.currentTimeMillis();
        if (rechercheChangeeA > 0 && now - rechercheChangeeA > 300) {
            rechercheChangeeA = -1;
            idsRecherche = objetsQuiCorrespondent(RegleHdv.normaliser(recherche).trim());
            numPage = 0;
            demanderPage();
        }
        if (onglet == 0 && now - dernierePage > 10_000) demanderPage();
        if (onglet == 2 && now - derniereMes > 10_000) demanderMes();
        if (slot >= 0 && Minecraft.getInstance().player != null && Minecraft.getInstance().player.getInventory().getItem(slot).isEmpty()) slot = -1;
    }

    private void changerOnglet(int o) {
        if (onglet == o) return;
        onglet = o;
        focus = o == 1 && slot >= 0 ? 2 : 0;
        armee = null;
        defile = 0;
        SonsUi.clic(0.6F);
        if (o == 0) demanderPage();
        if (o == 2) demanderMes();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        if (b == 0) {
            double x = mx - ox, y = my - oy;
            int avant = focus;
            for (int i = zones.size() - 1; i >= 0; i--) {
                if (zones.get(i).dedans(x, y)) {
                    zones.get(i).action().run();
                    return true;
                }
            }
            if (avant == focus) focus = 0;
        }
        return super.mouseClicked(mx, my, b);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (delta == 0) return false;
        if (onglet == 0 && page != null) {
            int p = Math.max(0, Math.min(page.pages() - 1, numPage + (delta < 0 ? 1 : -1)));
            if (p != numPage) {
                numPage = p;
                SonsUi.clic(0.5F);
                demanderPage();
            }
            return true;
        }
        if (onglet == 2) {
            defile = Math.max(0, Math.min(Math.max(0, mes.size() - 6), defile + (delta < 0 ? 1 : -1)));
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (focus == 1 && recherche.length() < 32 && c >= ' ' && c != 127 && c != '§') {
            recherche += c;
            rechercheChangeeA = System.currentTimeMillis();
            return true;
        }
        if (focus == 2 && c >= '0' && c <= '9' && prix.length() < 9) {
            prix = prix.equals("0") ? String.valueOf(c) : prix + c;
            return true;
        }
        return super.charTyped(c, mods);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_BACKSPACE && focus == 1) {
            if (!recherche.isEmpty()) {
                recherche = recherche.substring(0, recherche.length() - 1);
                rechercheChangeeA = System.currentTimeMillis();
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE && focus == 2) {
            if (!prix.isEmpty()) prix = prix.substring(0, prix.length() - 1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (onglet == 1) mettreEnVente();
            else if (onglet == 0) acheter();
            return true;
        }
        if (focus != 0 && key != GLFW.GLFW_KEY_ESCAPE) return true;   // la touche de l'inventaire ne ferme pas pendant la saisie
        return super.keyPressed(key, scan, mods);
    }

    // ------------------------------------------------------------------ actions
    private ReseauHdv.LigneOffre offreChoisie() {
        if (page == null || choisie == null) return null;
        for (ReseauHdv.LigneOffre l : page.lignes()) if (l.id().equals(choisie)) return l;
        return null;
    }

    private boolean peutPayer(ReseauHdv.LigneOffre o) {
        if (o.devise() == RegleHdv.PB) return etat.pbActif() && (etat.pb() < 0 || etat.pb() >= o.total());
        return etat.herals() >= o.total();
    }

    private boolean armee(ReseauHdv.LigneOffre o) {
        return o != null && o.id().equals(armee) && System.currentTimeMillis() - armeeA < 5000;
    }

    /** Premier clic : arme l'achat ; second (dans les 5 s) : la demande part au serveur. */
    private void acheter() {
        ReseauHdv.LigneOffre o = offreChoisie();
        if (o == null || attente || o.mienne() || !peutPayer(o)) return;
        if (armee(o)) {
            attente = true;
            armee = null;
            SonsUi.clic(0.8F);
            action(ReseauHdv.ACHETER, o.id(), 0, o.devise(), o.prix(), 0, 0);
        } else {
            armee = o.id();
            armeeA = System.currentTimeMillis();
            SonsUi.clic(0.6F);
        }
    }

    private ItemStack objetVente() {
        if (slot < 0 || Minecraft.getInstance().player == null) return ItemStack.EMPTY;
        return Minecraft.getInstance().player.getInventory().getItem(slot);
    }

    private long plafond(ItemStack st) {
        if (deviseVente == RegleHdv.HERALS) return etat.prixHeralsMax();
        int r = RegleHdv.rarete(st) + 1;
        List<Long> pl = etat.plafondsPb();
        return pl.isEmpty() ? 0 : pl.get(Math.max(0, Math.min(pl.size() - 1, r)));
    }

    private long prixVente() {
        try {
            return prix.isEmpty() ? 0 : Long.parseLong(prix);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private int dureeChoisie() {
        return duree >= 0 && duree < etat.durees().size() ? duree : Math.max(0, etat.durees().size() - 1);
    }

    private boolean venteValide() {
        ItemStack st = objetVente();
        long p = prixVente();
        if (st.isEmpty() || RegleHdv.refus(st) != RegleHdv.OK || p < 1 || p > plafond(st) || attente) return false;
        if (etat.mesEnVente() >= etat.ventesMax()) return false;
        return deviseVente != RegleHdv.PB || etat.pbActif();
    }

    private void mettreEnVente() {
        if (!venteValide()) return;
        ItemStack st = objetVente();
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(st.getItem());
        if (id == null) return;
        attente = true;
        SonsUi.clic(0.8F);
        action(ReseauHdv.VENDRE, id.toString(), slot, deviseVente, prixVente(), dureeChoisie(), st.getCount());
    }

    // ------------------------------------------------------------------ dessin
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        sx = mx;
        sy = my;
        zones.clear();
        voile(g);
        g.pose().pushPose();
        g.pose().translate(ox, oy, 0);
        Font f = font;
        Trace.rect(g, 40, 20, 600, 340, a(FOND, 219));
        Trace.bord(g, 40, 20, 560, 320, 1 / 3F, a(0x2C3039, 255));
        entete(g, f);
        switch (onglet) {
            case 1 -> vendre(g, f);
            case 2 -> mesVentes(g, f);
            default -> acheterOnglet(g, f);
        }
        g.pose().popPose();
    }

    private void voile(GuiGraphics g) {
        Trace.rect(g, 0, 0, width, height, a(NUIT, 170));
        Trace.gradV(g, 0, 0, width, height * 0.35F, a(0, 80), a(0, 0));
        Trace.gradV(g, 0, height * 0.65F, width, height, a(0, 0), a(0, 110));
    }

    private void entete(GuiGraphics g, Font f) {
        // logo : marteau de commissaire-priseur dans un carré or
        Trace.rect(g, 56, 31.33F, 78, 53.33F, a(OR_SOMBRE, 255));
        Trace.bord(g, 56, 31.33F, 22, 22, 2 / 3F, a(OR, 255));
        Trace.trait(g, 60.67F, 47.33F, 69.33F, 41.33F, 2, a(OR, 255));
        Trace.trait(g, 66.33F, 38.67F, 73, 34, 4.67F, a(OR, 255));
        Trace.m(g, f, t("titre"), 88.33F, 36, TITRE, a(TEXTE, 255), 260);
        Trace.m(g, f, t("sous", nombre(etat.offres())), 88.33F, 51.67F, SOUS, a(GRIS, 255), 270);
        // soldes, alignés à droite avant la croix
        float x = 575;
        String pb = etat.pb() < 0 || !etat.pbActif() ? "—" : nombre(etat.pb());
        x -= solde(g, f, x, pb, "PB", true) + 4.67F;
        solde(g, f, x, nombre(etat.herals()), t("herals"), false);
        boolean hv = survol(576, 37, 10, 10);
        Trace.c(g, f, "×", 581, 42, TXT, a(hv ? TEXTE : GRIS, 255), 0);
        zone(576, 37, 10, 10, this::onClose);
        Trace.rect(g, 48, 64, 592, 64 + 1 / 3F, a(LIGNE, 255));
        // onglets
        float ox2 = 56;
        for (int i = 0; i < 3; i++) {
            String lab = t("onglet." + i);
            float tw = Trace.largeur(f, lab, ONGLET);
            int n = i == 2 ? etat.aRecuperer() : 0;
            String pastille = n > 0 ? String.valueOf(n) : "";
            float pw = pastille.isEmpty() ? 0 : Math.max(6.67F, Trace.largeur(f, pastille, PASTILLE) + 4) + 4;
            float w = tw + pw + 20;
            boolean sel = onglet == i, hvo = !sel && survol(ox2, 64, w, 19.33F);
            Trace.m(g, f, lab, ox2 + 10, 73.67F, ONGLET, a(sel || hvo ? TEXTE : GRIS, 255), 0);
            if (!pastille.isEmpty()) {
                float px = ox2 + 10 + tw + 4, pwi = pw - 4;
                Trace.rect(g, px + 1.5F, 70.33F, px + pwi - 1.5F, 77, a(OR, 255));
                Trace.disque(g, px + 1.5F, 73.67F, 3.33F, a(OR, 255));
                Trace.disque(g, px + pwi - 1.5F, 73.67F, 3.33F, a(OR, 255));
                Trace.c(g, f, pastille, px + pwi / 2, 73.67F, PASTILLE, a(SOMBRE, 255), 0);
            }
            if (sel) Trace.rect(g, ox2 + 10, 82.67F, ox2 + 10 + tw + pw, 83.67F, a(OR, 255));
            int o = i;
            zone(ox2, 64, w, 19.33F, () -> changerOnglet(o));
            ox2 += w;
        }
        Trace.rect(g, 56, 83.33F, 584, 83.33F + 1 / 3F, a(LIGNE, 255));
    }

    /** Une case de solde, alignée à droite sur xd ; rend sa largeur. */
    private float solde(GuiGraphics g, Font f, float xd, String valeur, String unite, boolean pb) {
        float iw = pb ? 7.33F : 6.67F;
        float w = 5.33F + iw + 3.33F + Trace.largeur(f, valeur, GRAS) + 2.5F + Trace.largeur(f, unite, TXT) + 5.33F;
        float x = xd - w, y = 34.67F, h = 15.33F;
        Trace.rect(g, x, y, xd, y + h, a(0xFFFFFF, 8));
        Trace.bord(g, x, y, w, h, 1 / 3F, a(BORD, 255));
        float cx = x + 5.33F;
        if (pb) badgePb(g, f, cx, y + h / 2, iw);
        else piece(g, cx, y + h / 2, iw);
        cx += iw + 3.33F;
        cx += Trace.m(g, f, valeur, cx, y + h / 2, GRAS, a(TEXTE, 255), 0) + 2.5F;
        Trace.m(g, f, unite, cx, y + h / 2, TXT, a(TEXTE, 255), 0);
        return w;
    }

    private static void piece(GuiGraphics g, float x, float cy, float d) {
        Trace.image(g, TEX_PIECE, x, cy - d / 2, d, d, 1);
    }

    private static void badgePb(GuiGraphics g, Font f, float x, float cy, float d) {
        Trace.rect(g, x + 0.6F, cy - d / 2, x + d - 0.6F, cy + d / 2, a(PB_C, 255));
        Trace.rect(g, x, cy - d / 2 + 0.6F, x + d, cy + d / 2 - 0.6F, a(PB_C, 255));
        Trace.c(g, f, "PB", x + d / 2, cy, BADGE, a(0xFFFFFF, 255), 0);
    }

    /** Icône de la monnaie (pièce ou badge PB) posée à gauche en x. */
    private static void icone(GuiGraphics g, Font f, int devise, float x, float cy) {
        if (devise == RegleHdv.PB) badgePb(g, f, x, cy, 7.33F);
        else piece(g, x, cy, 6.67F);
    }

    /** Prix aligné à droite sur xd : nombre gras puis icône ; rend la largeur. */
    private static float prix(GuiGraphics g, Font f, long v, int devise, float xd, float cy, int couleur) {
        float iw = devise == RegleHdv.PB ? 7.33F : 6.67F;
        icone(g, f, devise, xd - iw, cy);
        float w = Trace.d(g, f, nombre(v), xd - iw - 3.33F, cy, GRAS, a(couleur, 255), 0);
        return w + iw + 3.33F;
    }

    /** Case d'objet : fond teinté de la rareté, trait en bas, objet. */
    private void caseObjet(GuiGraphics g, ItemStack st, float x, float y, float s, boolean grise) {
        int r = st.isEmpty() ? -2 : RegleHdv.rarete(st);
        int c = r == -2 ? 0x2A2E36 : couleur(r);
        Trace.gradV(g, x, y, x + s, y + s, a(0xFFFFFF, 5), a(c, st.isEmpty() ? 8 : 40));
        Trace.rect(g, x, y + s - 1, x + s, y + s, a(c, 255));
        if (st.isEmpty()) return;
        float k = Math.max(0.5F, (s - 2.5F) / 16F);
        g.pose().pushPose();
        g.pose().translate(x + s / 2 - 8 * k, y + s / 2 - 8 * k, 0);
        g.pose().scale(k, k, 1);
        g.renderItem(st, 0, 0);
        g.pose().popPose();
        if (grise) Trace.rect(g, x, y, x + s, y + s, a(FOND, 165));
    }

    private void pied(GuiGraphics g, Font f, String[][] touches, String note) {
        Trace.rect(g, 56, 314.67F, 584, 314.67F + 1 / 3F, a(LIGNE, 255));
        float cy = 327.33F, x = 56;
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        for (String[] k : touches) {
            String kl = t("touche." + k[0]);
            float w = Trace.largeur(f, kl, TOUCHE) + 6.67F;
            Trace.bord(g, x, cy - 4.5F, w, 9, 1 / 3F, a(0x555B66, 255));
            Trace.c(g, f, kl, x + w / 2, cy, TOUCHE, a(TEXTE, 255), 0);
            x += w + 4;
            x += Trace.m(g, f, t("pied." + k[1]), x, cy, TXT, a(CLAIR, 255), 0) + 12;
        }
        long age = System.currentTimeMillis() - messageA;
        if (!message.isEmpty() && age < 7000) {
            float al = 255 * Math.min(1, (7000 - age) / 600F);
            Trace.d(g, f, t("msg." + message), 584, cy, GRAS, a(messageOk ? VERT : ROUGE, Math.max(20, al)), 584 - x - 6);
        } else {
            Trace.d(g, f, t("note." + note), 584, cy, TXT, a(GRIS, 255), 584 - x - 6);
        }
        g.pose().popPose();
    }

    private void label(GuiGraphics g, Font f, String s, float x, float cy, int c, float maxw) {
        Trace.m(g, f, s, x, cy, LABEL, a(c, 255), maxw);
    }

    /** Rangée de choix (une seule case allumée) ; rend l'index cliqué par l'action. */
    private void choix(GuiGraphics g, Font f, float x, float y, float w, float h, String[] libelles, int actif, P p, java.util.function.IntConsumer clic) {
        float cw = w / libelles.length;
        for (int i = 0; i < libelles.length; i++) {
            float cx = x + i * cw;
            boolean on = i == actif, hv = !on && survol(cx, y, cw, h);
            if (on) Trace.rect(g, cx, y, cx + cw, y + h, a(OR, 31));
            else if (hv) Trace.rect(g, cx, y, cx + cw, y + h, a(0xFFFFFF, 8));
            if (i > 0) Trace.rect(g, cx, y, cx + 1 / 3F, y + h, a(BORD, 255));
            Trace.c(g, f, libelles[i], cx + cw / 2, y + h / 2, p, a(on ? TEXTE : GRIS, 255), cw - 4);
            int k = i;
            zone(cx, y, cw, h, () -> {
                SonsUi.clic(0.5F);
                clic.accept(k);
            });
        }
        Trace.bord(g, x, y, w, h, 1 / 3F, a(BORD, 255));
    }

    private void bouton(GuiGraphics g, Font f, float x, float y, float w, float h, String s, boolean actif, boolean plein, P p, Runnable r) {
        boolean hv = actif && survol(x, y, w, h);
        if (plein && actif) {
            Trace.rect(g, x, y, x + w, y + h, a(Trace.mix(OR, 0xFFFFFF, hv ? 0.12F : 0), 255));
            Trace.c(g, f, s, x + w / 2, y + h / 2, p, a(SOMBRE, 255), w - 6);
        } else {
            Trace.rect(g, x, y, x + w, y + h, a(0xFFFFFF, hv ? 10 : 0));
            Trace.bord(g, x, y, w, h, 1 / 3F, a(actif ? 0x4A4F59 : 0x2E323A, 255));
            Trace.c(g, f, s, x + w / 2, y + h / 2, p, a(actif ? CLAIR : 0x5A5F69, 255), w - 6);
        }
        if (actif) zone(x, y, w, h, r);
    }

    // ------------------------------------------------------------------ 1. Acheter
    private void acheterOnglet(GuiGraphics g, Font f) {
        // recherche
        boolean fr = focus == 1;
        Trace.rect(g, 56, 99.67F, 156, 116.33F, a(0, 64));
        Trace.bord(g, 56, 99.67F, 100, 16.67F, 1 / 3F, a(fr ? OR : BORD, fr ? 200 : 255));
        Trace.anneau(g, 64, 107.33F, 2, 2.67F, a(0x6C717B, 255));
        Trace.trait(g, 65.8F, 109.2F, 67.6F, 111, 2 / 3F, a(0x6C717B, 255));
        if (recherche.isEmpty() && !fr) {
            Trace.m(g, f, t("rechercher"), 71, 108, TXT, a(0x6C717B, 255), 82);
        } else {
            String s = recherche;
            while (s.length() > 1 && Trace.largeur(f, s, TXT) > 80) s = s.substring(1);
            float w = Trace.m(g, f, s, 71, 108, TXT, a(TEXTE, 255), 0);
            if (fr && (System.currentTimeMillis() / 500) % 2 == 0) Trace.rect(g, 71 + w + 0.6F, 104, 71 + w + 1.2F, 112, a(OR, 255));
        }
        zone(56, 99.67F, 100, 16.67F, () -> focus = 1);
        // catégories
        label(g, f, t("categories"), 56, 127, GRIS, 100);
        for (int i = 0; i < RegleHdv.CATEGORIES; i++) {
            float y = 133.67F + i * 15.33F;
            boolean on = i == categorie, hv = !on && survol(56, y, 100, 15.33F);
            if (on) {
                Trace.rect(g, 56, y, 156, y + 15.33F, a(OR, 20));
                Trace.rect(g, 56, y, 57, y + 15.33F, a(OR, 255));
            } else if (hv) Trace.rect(g, 56, y, 156, y + 15.33F, a(0xFFFFFF, 8));
            int n = page == null || i >= page.parCategorie().size() ? 0 : page.parCategorie().get(i);
            float nw = Trace.d(g, f, nombre(n), 150.67F, y + 7.67F, PETIT, a(GRIS, 255), 0);
            Trace.m(g, f, t("cat." + i), 61.33F, y + 7.67F, TXT, a(on ? TEXTE : CLAIR, 255), 150.67F - 61.33F - nw - 4);
            int k = i;
            zone(56, y, 100, 15.33F, () -> {
                if (categorie != k) {
                    categorie = k;
                    numPage = 0;
                    SonsUi.clic(0.5F);
                    demanderPage();
                }
            });
        }
        label(g, f, t("payer_en"), 56, 251.67F, GRIS, 100);
        choix(g, f, 56, 258.67F, 100, 14, new String[]{t("tout"), t("herals_maj"), "PB"}, devise, PETIT, k -> {
            devise = k;
            numPage = 0;
            demanderPage();
        });
        label(g, f, t("trier_par"), 56, 283.67F, GRIS, 100);
        choix(g, f, 56, 290.67F, 100, 14, new String[]{t("tri.0"), t("tri.1"), t("tri.2")}, tri, PETIT, k -> {
            tri = k;
            numPage = 0;
            demanderPage();
        });
        liste(g, f);
        detail(g, f);
        pied(g, f, new String[][]{{"echap", "fermer"}, {"molette", "defiler"}, {"clic2", "acheter"}}, "acheter");
    }

    private void liste(GuiGraphics g, Font f) {
        float x0 = 166, x1 = 430.67F, y0 = 99.67F, y1 = 314;
        Trace.rect(g, x0, y0, x1, y1, a(0xFFFFFF, 5));
        Trace.bord(g, x0, y0, x1 - x0, y1 - y0, 1 / 3F, a(LIGNE, 255));
        label(g, f, t("col.objet"), 197.33F, 108.67F, GRIS, 85);
        label(g, f, t("col.vendeur"), 286.67F, 108.67F, GRIS, 50);
        label(g, f, t("col.reste"), 340, 108.67F, GRIS, 40);
        Trace.d(g, f, t("col.prix"), 422.67F, 108.67F, LABEL, a(GRIS, 255), 40);
        Trace.rect(g, x0, 117, x1, 117 + 1 / 3F, a(LIGNE, 255));
        if (page == null || page.lignes().isEmpty()) {
            Trace.c(g, f, page == null ? t("chargement") : t("aucune"), (x0 + x1) / 2, 200, TXT, a(GRIS, 255), x1 - x0 - 20);
        } else {
            for (int i = 0; i < page.lignes().size(); i++) {
                ReseauHdv.LigneOffre o = page.lignes().get(i);
                float y = 117.33F + i * 22;
                int c = couleur(RegleHdv.rarete(o.objet()));
                boolean sel = o.id().equals(choisie), hv = !sel && survol(x0, y, x1 - x0, 22);
                if (sel) {
                    Trace.rect(g, x0, y, x1, y + 22, a(c, 20));
                    Trace.rect(g, x0, y, x0 + 1, y + 22, a(c, 255));
                } else if (hv) Trace.rect(g, x0, y, x1, y + 22, a(0xFFFFFF, 6));
                Trace.rect(g, x0, y + 22 - 1 / 3F, x1, y + 22, a(0xFFFFFF, 8));
                caseObjet(g, o.objet(), 173.33F, y + 2, 18, false);
                float pw = prix(g, f, o.prix(), o.devise(), 422.67F, y + 11, TEXTE);
                String q = o.objet().getCount() > 1 ? "×" + o.objet().getCount() : "";
                float qw = q.isEmpty() ? 0 : Trace.largeur(f, q, TXT) + 2.67F;
                float nw = Trace.m(g, f, nom(o.objet()), 197.33F, y + 11, GRAS, a(c, 255), 286.67F - 197.33F - 4 - qw);
                if (!q.isEmpty()) Trace.m(g, f, q, 197.33F + nw + 2.67F, y + 11, TXT, a(GRIS, 255), 0);
                Trace.m(g, f, o.mienne() ? t("toi") : o.vendeur(), 286.67F, y + 11, TXT, a(GRIS, 255), 50);
                Trace.m(g, f, duree(o.resteMs()), 340, y + 11, TXT, a(GRIS, 255), 422.67F - 340 - pw - 4);
                String id = o.id();
                zone(x0, y, x1 - x0, 22, () -> {
                    if (!id.equals(choisie)) {
                        choisie = id;
                        armee = null;
                        SonsUi.clic(0.5F);
                    }
                });
            }
        }
        Trace.rect(g, x0, 297.33F, x1, 297.33F + 1 / 3F, a(LIGNE, 255));
        int p = page == null ? 0 : page.page() + 1, n = page == null ? 1 : page.pages();
        Trace.c(g, f, t("page", p, n), (x0 + x1) / 2, 305.67F, PETIT, a(GRIS, 255), x1 - x0 - 10);
    }

    private void detail(GuiGraphics g, Font f) {
        float x0 = 440.67F, x1 = 583.33F, y0 = 99.67F, y1 = 314, ix = 449.33F, xd = 574.67F;
        Trace.rect(g, x0, y0, x1, y1, a(0xFFFFFF, 5));
        Trace.bord(g, x0, y0, x1 - x0, y1 - y0, 1 / 3F, a(LIGNE, 255));
        label(g, f, t("offre_choisie"), ix, 112, OR, xd - ix);
        ReseauHdv.LigneOffre o = offreChoisie();
        if (o == null) {
            Trace.c(g, f, t("choisir"), (x0 + x1) / 2, 200, TXT, a(GRIS, 255), x1 - x0 - 16);
            return;
        }
        ItemStack st = o.objet();
        int r = RegleHdv.rarete(st), c = couleur(r);
        caseObjet(g, st, ix, 122, 36.67F, false);
        String q = st.getCount() > 1 ? " ×" + st.getCount() : "";
        Trace.m(g, f, nom(st) + q, 492.67F, 130.33F, NOM_GRAND, a(c, 255), xd - 492.67F);
        if (r >= 0) Trace.m(g, f, t("rarete." + r).toUpperCase(Locale.ROOT), 492.67F, 143, RARE, a(c, 255), xd - 492.67F);
        Trace.m(g, f, o.mienne() ? t("ta_vente") : t("vendu_par", o.vendeur()), 492.67F, 152.33F, PETIT, a(GRIS, 255), xd - 492.67F);
        // caractéristiques
        float y = 173;
        for (String[] kv : caracteristiques(st)) {
            float lw = Trace.m(g, f, kv[0], ix, y, TXT, a(GRIS, 255), 50);
            Trace.d(g, f, kv[1], xd, y, TXT, a(TEXTE, 255), xd - ix - lw - 6);
            y += 14.67F;
            if (y > 205) break;
        }
        long apres;
        boolean pb = o.devise() == RegleHdv.PB;
        String u = devise(o.devise());
        if (pb) {
            kv(g, f, t("prix_vendeur"), nombre(o.prix()) + " PB", ix, xd, 223.67F, TXT, TEXTE);
            kv(g, f, t("frais", etat.fraisPct()), "+ " + nombre(o.total() - o.prix()) + " PB", ix, xd, 238.33F, TXT, TEXTE);
        } else {
            kv(g, f, t("prix_vendeur"), nombre(o.prix()) + " " + u, ix, xd, 223.67F, TXT, TEXTE);
            kv(g, f, t("sans_frais"), "0", ix, xd, 238.33F, TXT, GRIS);
        }
        kv(g, f, t("tu_paies"), nombre(o.total()) + " " + u, ix, xd, 254.33F, PAIE, pb ? PB_C : OR);
        long solde = pb ? etat.pb() : etat.herals();
        apres = solde - o.total();
        kv(g, f, t("solde_apres"), solde < 0 ? "—" : nombre(apres) + " " + u, ix, xd, 269.33F, TXT, apres < 0 && solde >= 0 ? ROUGE : TEXTE);
        boolean peut = !o.mienne() && peutPayer(o) && !attente;
        String lib = attente ? t("attente") : o.mienne() ? t("ta_vente_bouton") : !peutPayer(o) ? t("solde_bas")
                : t(armee(o) ? "confirmer" : "acheter", nombre(o.total()) + " " + (pb ? "PB" : u));
        bouton(g, f, ix, 277.67F, xd - ix, 19.67F, lib, peut, true, BOUTON, this::acheter);
        Trace.c(g, f, t("deux_clics"), (ix + xd) / 2, 305.33F, PETIT, a(GRIS, 255), xd - ix);
    }

    private void kv(GuiGraphics g, Font f, String k, String v, float x, float xd, float cy, P p, int c) {
        float lw = Trace.m(g, f, k, x, cy, p, a(p == PAIE ? TEXTE : GRIS, 255), (xd - x) * 0.6F);
        Trace.d(g, f, v, xd, cy, p, a(c, 255), xd - x - lw - 6);
    }

    /** Usure, accessoires et munitions d'une arme ; usure d'un objet abîmable ; sinon la catégorie. */
    private static List<String[]> caracteristiques(ItemStack st) {
        List<String[]> l = new ArrayList<>();
        CompoundTag t = st.getTag();
        boolean arme = t != null && t.contains("GunId", Tag.TAG_STRING);
        if (arme) {
            int u = t.contains("HzUsureEtat", Tag.TAG_ANY_NUMERIC) ? Math.max(0, Math.min(1000, t.getInt("HzUsureEtat"))) : 1000;
            l.add(new String[]{t("usure"), (u / 10) + " %"});
            List<String> acc = new ArrayList<>();
            for (String k : t.getAllKeys()) {
                if (!k.startsWith("Attachment") || k.equals("AttachmentId") || !t.contains(k, Tag.TAG_COMPOUND)) continue;
                ItemStack a = ItemStack.of(t.getCompound(k));
                if (!a.isEmpty()) acc.add(nom(a));
            }
            l.add(new String[]{t("accessoires"), acc.isEmpty() ? t("aucun") : String.join(", ", acc)});
            int balles = t.getInt("GunCurrentAmmoCount") + (t.getBoolean("HasBulletInBarrel") ? 1 : 0);
            l.add(new String[]{t("munitions"), balles <= 0 ? t("dechargee") : t("balles", balles)});
        } else {
            if (st.isDamageableItem() && st.getMaxDamage() > 0) {
                l.add(new String[]{t("usure"), (100 * (st.getMaxDamage() - st.getDamageValue()) / st.getMaxDamage()) + " %"});
            }
            l.add(new String[]{t("quantite"), "×" + st.getCount()});
            l.add(new String[]{t("categorie"), t("cat." + RegleHdv.categorie(st))});
        }
        return l;
    }

    // ------------------------------------------------------------------ 2. Vendre
    private void vendre(GuiGraphics g, Font f) {
        var joueur = Minecraft.getInstance().player;
        float x0 = 56, x1 = 374, y0 = 99.67F, y1 = 314, ix = 65.33F;
        Trace.rect(g, x0, y0, x1, y1, a(0xFFFFFF, 5));
        Trace.bord(g, x0, y0, x1 - x0, y1 - y0, 1 / 3F, a(LIGNE, 255));
        label(g, f, t("etape1"), ix, 112.33F, OR, x1 - ix - 9);
        Trace.m(g, f, t("grises"), ix, 123.33F, TXT, a(GRIS, 255), x1 - ix - 9);
        ItemStack survole = ItemStack.EMPTY;
        int refusSurvole = 0;
        if (joueur != null) {
            for (int k = 0; k < 36; k++) {
                int ligne = k < 27 ? k / 9 : 3, col = k % 9;
                int s = k < 27 ? k + 9 : k - 27;   // inventaire (9 à 35) puis barre d'objets (0 à 8)
                float x = ix + col * 26.67F, y = 134.67F + ligne * 26.67F + (ligne == 3 ? 2.67F : 0);
                ItemStack st = joueur.getInventory().getItem(s);
                int refus = RegleHdv.refus(st);
                boolean bloque = !st.isEmpty() && refus != RegleHdv.OK;
                caseObjet(g, st, x, y, 24, bloque);
                if (!st.isEmpty() && st.getCount() > 1) {
                    Trace.d(g, f, String.valueOf(st.getCount()), x + 22.67F, y + 20, PASTILLE, a(0xFFFFFF, bloque ? 120 : 255), 0);
                }
                if (s == slot) Trace.bord(g, x - 2 / 3F, y - 2 / 3F, 25.33F, 25.33F, 2 / 3F, a(OR, 255));
                if (!st.isEmpty() && survol(x, y, 24, 24)) {
                    survole = st;
                    refusSurvole = refus;
                }
                if (!st.isEmpty() && !bloque) {
                    int sl = s;
                    zone(x, y, 24, 24, () -> {
                        if (slot != sl) {
                            slot = sl;
                            prix = "";
                            focus = 2;
                            SonsUi.clic(0.5F);
                        }
                    });
                }
            }
        }
        // objet grisé survolé : pourquoi il ne se vend pas
        if (!survole.isEmpty() && refusSurvole != RegleHdv.OK) {
            caseObjet(g, survole, ix, 290, 13.33F, true);
            List<String> ls = Trace.lignes(f, nom(survole) + " · " + t("refus." + refusSurvole), TXT, x1 - ix - 30);
            for (int i = 0; i < Math.min(2, ls.size()); i++) {
                Trace.m(g, f, ls.get(i), ix + 18, ls.size() > 1 ? 292.67F + i * 8 : 296.67F, TXT, a(GRIS, 255), x1 - ix - 30);
            }
        } else if (!survole.isEmpty()) {
            Trace.m(g, f, nom(survole), ix, 296.67F, TXT, a(GRIS, 255), x1 - ix - 9);
        }
        prixVente(g, f);
        pied(g, f, new String[][]{{"clic", "choisir"}, {"entree", "mettre"}, {"echap", "fermer"}}, "vendre");
    }

    private void prixVente(GuiGraphics g, Font f) {
        float x0 = 384, x1 = 584, y0 = 99.67F, y1 = 314, ix = 393.33F, xd = 574.67F;
        Trace.rect(g, x0, y0, x1, y1, a(0xFFFFFF, 5));
        Trace.bord(g, x0, y0, x1 - x0, y1 - y0, 1 / 3F, a(LIGNE, 255));
        String compte = t("ventes", etat.mesEnVente(), etat.ventesMax());
        float cw = Trace.d(g, f, compte, xd, 111.67F, LABEL, a(etat.mesEnVente() >= etat.ventesMax() ? ROUGE : GRIS, 255), 0);
        label(g, f, t("etape2"), ix, 111.67F, OR, xd - ix - cw - 6);
        ItemStack st = objetVente();
        if (st.isEmpty()) {
            Trace.c(g, f, t("choisir_objet"), (x0 + x1) / 2, 200, TXT, a(GRIS, 255), x1 - x0 - 16);
            return;
        }
        int c = couleur(RegleHdv.rarete(st));
        caseObjet(g, st, ix, 121.33F, 26.67F, false);
        String q = st.getCount() > 1 ? " ×" + st.getCount() : "";
        Trace.m(g, f, nom(st) + q, 426, 125, NOM_GRAND, a(c, 255), xd - 426);
        List<String> car = new ArrayList<>();
        for (String[] kv : caracteristiques(st)) car.add(kv[0] + " " + kv[1]);
        Trace.m(g, f, String.join(" · ", car.subList(0, Math.min(2, car.size()))), 426, 136.67F, PETIT, a(GRIS, 255), xd - 426);
        CompoundTag tg = st.getTag();
        if (tg != null && tg.contains("GunId", Tag.TAG_STRING)) Trace.m(g, f, t("dechargee_vente"), 426, 145.33F, PETIT, a(GRIS, 255), xd - 426);
        label(g, f, t("monnaie"), ix, 156.33F, GRIS, 100);
        choix(g, f, ix, 163, xd - ix, 14.33F, new String[]{t("herals_maj"), "PB"}, deviseVente, PETIT, k -> deviseVente = k);
        label(g, f, t("prix"), ix, 184.67F, GRIS, 100);
        boolean fp = focus == 2;
        long plafond = plafond(st), p = prixVente();
        boolean trop = p > plafond;
        Trace.rect(g, ix, 191.67F, xd, 208.67F, a(0, 77));
        Trace.bord(g, ix, 191.67F, xd - ix, 17, 1 / 3F, a(trop ? ROUGE : fp ? OR : 0x4A4F59, 255));
        float w = prix.isEmpty() && !fp ? 0 : Trace.m(g, f, prix, ix + 6, 200.17F, NOM_GRAND, a(TEXTE, 255), 140);
        if (prix.isEmpty() && !fp) Trace.m(g, f, "0", ix + 6, 200.17F, NOM_GRAND, a(0x5A5F69, 255), 0);
        if (fp && (System.currentTimeMillis() / 500) % 2 == 0) Trace.rect(g, ix + 6 + w + 1.5F, 195.5F, ix + 6 + w + 2.17F, 204.83F, a(OR, 255));
        icone(g, f, deviseVente, xd - 6 - (deviseVente == RegleHdv.PB ? 7.33F : 6.67F), 200.17F);
        zone(ix, 191.67F, xd - ix, 17, () -> focus = 2);
        label(g, f, t("duree"), ix, 216, GRIS, 100);
        String[] ds = new String[etat.durees().size()];
        for (int i = 0; i < ds.length; i++) ds[i] = t("heures", etat.durees().get(i));
        choix(g, f, ix, 223, xd - ix, 14.67F, ds, dureeChoisie(), PETIT, k -> duree = k);
        String max = deviseVente == RegleHdv.PB ? t("plafond_pb", nombre(plafond)) : t("plafond_herals", nombre(plafond));
        if (deviseVente == RegleHdv.PB && !etat.pbActif()) max = t("msg.pb_ferme");
        Trace.m(g, f, max, ix, 246, PETIT, a(trop ? ROUGE : GRIS, 255), xd - ix);
        Trace.rect(g, ix, 252, xd, 252 + 1 / 3F, a(LIGNE, 255));
        long total = RegleHdv.total(deviseVente, Math.max(0, p), etat.fraisPct());
        String u = deviseVente == RegleHdv.PB ? "PB" : t("herals");
        kv(g, f, t("acheteur_paie"), nombre(total) + " " + u, ix, xd, 259.33F, TXT, TEXTE);
        kv(g, f, t("tu_recois"), nombre(Math.max(0, p)) + " " + u, ix, xd, 279.33F, PAIE, deviseVente == RegleHdv.PB ? PB_C : OR);
        boolean ok = venteValide();
        bouton(g, f, ix, 291.33F, xd - ix, 20.67F, attente ? t("attente") : t("mettre"), ok, true, BOUTON, this::mettreEnVente);
    }

    // ------------------------------------------------------------------ 3. Mes ventes
    private void mesVentes(GuiGraphics g, Font f) {
        long recup = mes.stream().filter(l -> l.action() && l.genre() != ReseauHdv.EN_VENTE).count();
        label(g, f, t("resume", etat.mesEnVente(), etat.ventesMax(), recup), 56, 108.33F, GRIS, 400);
        bouton(g, f, 470.33F, 99.67F, 113.67F, 17.33F, t("tout_recuperer"), recup > 0 && !attente, true, BOUTON_PETIT, () -> {
            attente = true;
            SonsUi.clic(0.8F);
            action(ReseauHdv.RECUPERER, "", 0, 0, 0, 0, 0);
        });
        float x0 = 56, x1 = 584, y0 = 124, y1 = 314;
        Trace.rect(g, x0, y0, x1, y1, a(0xFFFFFF, 5));
        Trace.bord(g, x0, y0, x1 - x0, y1 - y0, 1 / 3F, a(LIGNE, 255));
        label(g, f, t("col.objet"), 88, 132.67F, GRIS, 200);
        label(g, f, t("col.etat"), 346, 132.67F, GRIS, 80);
        Trace.d(g, f, t("col.prix"), 490.67F, 132.67F, LABEL, a(GRIS, 255), 60);
        Trace.rect(g, x0, 141, x1, 141 + 1 / 3F, a(LIGNE, 255));
        if (mes.isEmpty()) {
            Trace.c(g, f, t("aucune_vente"), (x0 + x1) / 2, 220, TXT, a(GRIS, 255), x1 - x0 - 20);
        }
        defile = Math.max(0, Math.min(Math.max(0, mes.size() - 6), defile));
        for (int i = defile; i < Math.min(mes.size(), defile + 6); i++) {
            ReseauHdv.LigneMv l = mes.get(i);
            float y = 141.33F + (i - defile) * 28;
            Trace.rect(g, x0, y + 28 - 1 / 3F, x1, y + 28, a(0xFFFFFF, 10));
            int c = couleur(RegleHdv.rarete(l.objet()));
            caseObjet(g, l.objet(), 64, y + 5, 18, false);
            String q = l.objet().getCount() > 1 ? "×" + l.objet().getCount() : "";
            float qw = q.isEmpty() ? 0 : Trace.largeur(f, q, TXT) + 2.67F;
            float nw = Trace.m(g, f, nom(l.objet()), 88, y + 14, GRAS, a(c, 255), 340 - 88 - qw);
            if (!q.isEmpty()) Trace.m(g, f, q, 88 + nw + 2.67F, y + 14, TXT, a(GRIS, 255), 0);
            int ce = switch (l.genre()) {
                case ReseauHdv.EN_VENTE, ReseauHdv.RESERVEE -> OR;
                case ReseauHdv.EXPIRE -> ROUGE;
                case ReseauHdv.RETIRE, ReseauHdv.ACHETE -> 0x4F9DFF;
                default -> VERT;
            };
            label(g, f, t("etat." + l.genre()), 346, y + 9.67F, ce, 140);
            String sous = switch (l.genre()) {
                case ReseauHdv.EN_VENTE -> t("reste", duree(l.tempsMs()));
                case ReseauHdv.RESERVEE -> t("en_achat");
                case ReseauHdv.VENDU_HERALS -> t("a_qui", l.autre(), duree(l.tempsMs()));
                case ReseauHdv.VENDU_PB_VERSE -> t("pb_verses");
                case ReseauHdv.VENDU_PB_ATTENTE -> t("pb_en_route");
                case ReseauHdv.EXPIRE -> t("personne");
                case ReseauHdv.ACHETE -> t("achete_a", l.autre());
                default -> t("retiree");
            };
            Trace.m(g, f, sous, 346, y + 18, PETIT, a(GRIS, 255), 140);
            boolean grisPrix = l.genre() == ReseauHdv.EXPIRE || l.genre() == ReseauHdv.RETIRE;
            prix(g, f, l.prix(), l.devise(), 491.33F, y + 14, grisPrix ? GRIS : TEXTE);
            float bx = 499.33F, bw = 76.67F, by = y + 6, bh = 16;
            String id = l.id();
            switch (l.genre()) {
                case ReseauHdv.EN_VENTE -> bouton(g, f, bx, by, bw, bh, t("retirer"), !attente, false, BOUTON_PETIT, () -> {
                    attente = true;
                    SonsUi.clic(0.7F);
                    action(ReseauHdv.RETIRER, id, 0, 0, 0, 0, 0);
                });
                case ReseauHdv.VENDU_PB_VERSE -> Trace.c(g, f, t("deja_verse"), bx + bw / 2, y + 14, PETIT, a(GRIS, 255), bw);
                case ReseauHdv.VENDU_PB_ATTENTE, ReseauHdv.RESERVEE -> Trace.c(g, f, t("patiente"), bx + bw / 2, y + 14, PETIT, a(GRIS, 255), bw);
                default -> bouton(g, f, bx, by, bw, bh, t("recuperer"), l.action() && !attente, true, BOUTON_PETIT, () -> {
                    attente = true;
                    SonsUi.clic(0.7F);
                    action(ReseauHdv.RECUPERER, id, 0, 0, 0, 0, 0);
                });
            }
        }
        if (mes.size() > 6) {
            float h = 172.67F, th = h * 6 / mes.size(), ty = 141.33F + (h - th) * defile / Math.max(1, mes.size() - 6);
            Trace.rect(g, 582, ty, 583, ty + th, a(0xFFFFFF, 60));
        }
        pied(g, f, new String[][]{{"clic", "recuperer"}, {"echap", "fermer"}}, "mes_ventes");
    }
}
