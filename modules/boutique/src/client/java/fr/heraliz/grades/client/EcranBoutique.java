package fr.heraliz.grades.client;

import com.mojang.math.Axis;
import fr.heraliz.grades.client.boutique.Trace;
import fr.heraliz.grades.client.boutique.Trace.P;
import fr.heraliz.grades.client.kit.Echelle;
import fr.heraliz.grades.client.kit.SonsUi;
import fr.heraliz.grades.reseau.ReseauBoutique;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static fr.heraliz.grades.client.boutique.Trace.a;
import static fr.heraliz.grades.client.boutique.Trace.mix;

/**
 * Boutique en jeu (/boutique), maquette « boutique premium » validée le 23/10 (maquettes/boutique_premium) : canevas de
 * 640 x 360 unités (échelle forcée par {@link Echelle}), style du créateur de personnage (voile sombre sur le monde, verre,
 * bord d'un pixel, accent rouge). Cinq onglets : Grades (trois cartes, durée et achat dans le panneau de droite), Offre
 * bêta test, Clés (Gemme, Camo, Emotes : les vrais objets des clés), Mes clés (25/10 : les clés du compte du joueur, à
 * quoi elles servent, comment les obtenir ; fait sans maquette, au style de l'onglet Clés) et Sur mesure (demande au
 * staff sur Discord).
 * <p>
 * Toutes les mesures ci-dessous sont recopiées dans {@code outils/apercu_boutique.py}, qui refait les images de l'écran
 * dans les trois langues et refuse tout texte qui dépasse : changer l'un, c'est changer l'autre. L'écran n'affiche que ce
 * que le serveur envoie et ne fait que DEMANDER un achat (deux clics : « Acheter » puis « Confirmer ») : prix et solde sont
 * vérifiés par le serveur et le site.
 */
public final class EcranBoutique extends Screen {
    // ------------------------------------------------------------------ mesures de la maquette
    private static final int GW = 640, GH = 360, M = 16, HAUT = 86, BAS = 336;
    private static final int ACCENT = 0xE3201B, WHITE = 0xFFFFFF, LIGHT = 0xECEEF2, OPTION = 0xC3C8D0, MUTED = 0x8F95A0, FAINT = 0x5A5F69,
            PINK = 0xF08A86, NIGHT = 0x050608, GLASS = 0x0D0F14, GOLD = 0xF2C14E, DISCORD = 0x5865F2, OK = 0x6FD08C, DANGER = 0xFF8A80,
            NOIR_BADGE = 0x10131A;
    private static final String LIEN_PB = "https://heraliz.fr/shop", LIEN_DISCORD = "https://discord.gg/heraliz";

    private static final P TITRE = Trace.p("bq_titre", 13, 1.93F), NOM_GRAND = Trace.p("bq_titre", 13, 1.4F), NOM = Trace.p("bq_nom", 11, 1.3F),
            BOUTON = Trace.p("bq_bouton", 9, 0.85F), ONGLET = Trace.p("bq_bouton", 9, 1.05F), MINI = Trace.p("bq_mini", 6.5F, 0.56F),
            MINI_NUM = Trace.p("bq_mini", 6.5F, 0.5F), TEXTE = Trace.p("bq_texte", 7.5F, 0), TEXTE_GRAS = Trace.p("bq_bouton", 9, 0),
            PETIT = Trace.p("bq_petit", 6.5F, 0), PETIT_GRAS = Trace.p("bq_mini", 6.5F, 0), MICRO = Trace.p("bq_micro", 5.6F, 0.4F),
            CHIFFRE_GRAND = Trace.p("bq_titre", 13, 0), CHIFFRE = Trace.p("bq_nom", 11, 0), PB_GRAND = Trace.p("bq_bouton", 9, 0),
            PB_MOYEN = Trace.p("bq_texte_gras", 7.5F, 0), PB_PETIT = Trace.p("bq_mini", 6.5F, 0), BETA_GROS = Trace.p("bq_titre", 13, 4),
            DEVIS = Trace.p("bq_mini", 6.5F, 0.4F), ETAPE = Trace.p("bq_micro", 5.6F, 0);

    private static final String[] GRADES = {"rescape", "traqueur", "roi_de_la_zone"};
    private static final String[] CAISSES = {"gemme", "camo", "spectacle"};
    /**
     * Onglet « Mes clés » (25/10) : les clés qu'on peut obtenir aujourd'hui, toujours montrées (quêtes, vote, événement,
     * puis les trois de la boutique) ; les autres caisses seulement si le joueur en a (données par le staff). Mesures
     * recopiées dans outils/apercu_boutique.py (ecran_mes_cles).
     */
    private static final fr.heraliz.crates.crate.CrateType[] CLES_TOUJOURS = {fr.heraliz.crates.crate.CrateType.QUETE,
            fr.heraliz.crates.crate.CrateType.VOTE, fr.heraliz.crates.crate.CrateType.EVENT, fr.heraliz.crates.crate.CrateType.GEMME,
            fr.heraliz.crates.crate.CrateType.CAMO, fr.heraliz.crates.crate.CrateType.SPECTACLE};
    private static final int CLES_PAR_PAGE = 6;
    private static final float MC_GAP_X = 9, MC_GAP_Y = 8, MC_ICONE = 40, MC_TEXTE = 78;
    private static final Map<String, Integer> RANG = Map.of("rescape", 1, "traqueur", 2, "roi_de_la_zone", 3);
    /**
     * Avantages officiels des grades (23/10) : clé de langue av.<clé> et son argument (null : aucun). « tp » : secondes de
     * moins sur les recharges de la base et du /tpa (26/10 : 8 min sans grade).
     */
    private static final Map<String, String[][]> AVANTAGES = Map.of(
            "rescape", new String[][]{{"coffre_vip", "3"}, {"kit", null}, {"tp", "30"}, {"casino", null}, {"zombies", null}},
            "traqueur", new String[][]{{"coffre_vip", "6"}, {"kit", null}, {"nick", null}, {"tp", "60"}, {"casino", null}, {"zombies", null}},
            "roi_de_la_zone", new String[][]{{"coffre_vip", "9"}, {"kit", null}, {"nick", null}, {"chat", null}, {"tp", "90"}, {"casino", null},
                    {"zombies", null}});
    private static final String[][] AVANTAGES_BETA = {{"vie", null}, {"cles", "10"}, {"sur_mesure", null}, {"coffre_vip", "9"}, {"kit", null},
            {"nick", null}, {"chat", null}, {"tp", "90"}, {"casino", null}, {"zombies", null}};
    /** Cartes « sur mesure » : nom, couleur. */
    private static final String[] SM = {"camo", "finisher", "emote"};
    private static final int[] SM_COULEUR = {0xB57BFF, 0xFF5A4E, 0x7FA8FF};
    private static final float SM_IW = 180.67F, SM_IH = 112;

    private static ResourceLocation tex(String nom) {
        return new ResourceLocation("heraliz_grades", "textures/gui/boutique/" + nom + ".png");
    }

    private static final ResourceLocation TEX_PB = tex("pb"), TEX_PIECE = tex("piece"), TEX_DISCORD = tex("discord");

    // ------------------------------------------------------------------ état
    private ReseauBoutique.EtatBoutique etat;
    private String onglet = "grades";
    private int gradeChoisi = 1;
    private final Map<String, Integer> choix = new HashMap<>();
    /** Article armé par un premier clic sur « Acheter » (le second confirme), et quand. */
    private String arme;
    private long armeA;
    private boolean attente;
    private String message = "";
    private boolean messageOk;
    private long messageA;
    private boolean succesBeta;
    /** Page de l'onglet « Mes clés » (6 clés par page). */
    private int pageCles;
    private int ox, oy, sx, sy;
    private final List<Zone> zones = new ArrayList<>();

    private record Zone(float x, float y, float w, float h, Runnable action) {
        boolean dedans(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private EcranBoutique(ReseauBoutique.EtatBoutique e) {
        super(Component.translatable("gui.heraliz_grades.boutique.titre"));
        etat = e;
    }

    public static void recevoir(ReseauBoutique.EtatBoutique e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof EcranBoutique b) {
            b.recevoirEtat(e);
        } else if (e.ouvrir()) {
            SonsUi.ouvrir(0.6F);
            mc.setScreen(new EcranBoutique(e));
        }
    }

    private void recevoirEtat(ReseauBoutique.EtatBoutique e) {
        long ancien = etat.solde();
        etat = new ReseauBoutique.EtatBoutique(false, e.solde() >= 0 ? e.solde() : ancien, e.offres(), e.message(), e.ok());
        attente = false;
        arme = null;
        if (!e.message().isEmpty()) {
            message = e.message();
            messageOk = e.ok();
            messageA = System.currentTimeMillis();
            if (e.ok()) SonsUi.ouvrir(0.8F);
            if (e.ok() && e.message().equals("achete_beta")) succesBeta = true;
        }
    }

    private static String t(String cle, Object... args) {
        return Component.translatable("gui.heraliz_grades.boutique." + cle, args).getString();
    }

    private static String nomGrade(String g) {
        return Component.translatable("grade.heraliz_grades." + g).getString();
    }

    /** 12 450 (espace insécable en français), 12,450 sinon. */
    private static String nombre(long v) {
        String s = String.format(Locale.ROOT, "%,d", v);
        return fr.heraliz.grades.client.kit.Ui.french() ? s.replace(',', ' ') : s;
    }

    private List<ReseauBoutique.Offre> offres(String groupe) {
        List<ReseauBoutique.Offre> l = new ArrayList<>();
        for (ReseauBoutique.Offre o : etat.offres()) if (o.groupe().equals(groupe)) l.add(o);
        l.sort(Comparator.comparingInt(ReseauBoutique.Offre::quantite));
        return l;
    }

    private int couleur(String groupe, int defaut) {
        List<ReseauBoutique.Offre> l = offres(groupe);
        return l.isEmpty() ? defaut : l.get(0).couleur();
    }

    private List<String> onglets() {
        List<String> l = new ArrayList<>(List.of("grades"));
        if (!offres("beta").isEmpty()) l.add("beta");
        l.add("cles");
        l.add("mes_cles");
        l.add("sur_mesure");
        return l;
    }

    private int choix(String groupe, int defaut) {
        int n = offres(groupe).size();
        return Math.max(0, Math.min(n - 1, choix.getOrDefault(groupe, defaut)));
    }

    // ------------------------------------------------------------------ mise en page
    @Override
    protected void init() {
        Echelle.forcer(this);
        // canevas 640 x 360 centré (la fenêtre « 1080p » sans sa barre de titre fait 640 x 352 : 4 unités hors champ en haut et en bas)
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

    private void lien(String url) {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new ConfirmLinkScreen(ok -> {
            if (ok) Util.getPlatform().openUri(url);
            mc.setScreen(this);
        }, url, true));
    }

    /** Premier clic : arme l'achat ; second clic (dans les 5 s) : la demande part au serveur. */
    private void acheter(ReseauBoutique.Offre o) {
        if (o == null || attente) return;
        if (o.id().equals(arme) && System.currentTimeMillis() - armeA < 5000) {
            attente = true;
            arme = null;
            SonsUi.clic(0.8F);
            ReseauBoutique.CANAL.sendToServer(new ReseauBoutique.Acheter(o.id()));
        } else {
            arme = o.id();
            armeA = System.currentTimeMillis();
            SonsUi.clic(0.6F);
        }
    }

    private boolean armee(ReseauBoutique.Offre o) {
        return o != null && o.id().equals(arme) && System.currentTimeMillis() - armeA < 5000;
    }

    private String libelleAchat(ReseauBoutique.Offre o) {
        if (attente) return t("attente");
        return t(armee(o) ? "confirmer_achat" : "acheter", nombre(o.prix()));
    }

    private ReseauBoutique.Offre selection() {
        switch (onglet) {
            case "grades" -> {
                List<ReseauBoutique.Offre> l = offres(GRADES[gradeChoisi]);
                return l.isEmpty() ? null : l.get(choix(GRADES[gradeChoisi], l.size() - 1));
            }
            case "beta" -> {
                List<ReseauBoutique.Offre> l = offres("beta");
                return l.isEmpty() ? null : l.get(0);
            }
            default -> {
                return null;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        if (b == 0) {
            double x = mx - ox, y = my - oy;
            for (int i = zones.size() - 1; i >= 0; i--) {
                if (zones.get(i).dedans(x, y)) {
                    zones.get(i).action().run();
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, b);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 256 && succesBeta) {
            succesBeta = false;
            return true;
        }
        if ((key == 262 || key == 263) && onglet.equals("mes_cles") && !succesBeta) {
            int n = pagesMesCles();
            int p = Math.floorMod(pageCles + (key == 262 ? 1 : -1), n);
            if (p != pageCles) {
                pageCles = p;
                SonsUi.clic(0.6F);
            }
            return true;
        }
        if ((key == 257 || key == 335) && !succesBeta) {
            ReseauBoutique.Offre o = selection();
            if (o != null && peutPayer(o)) acheter(o);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (onglet.equals("mes_cles") && !succesBeta && delta != 0) {
            int n = pagesMesCles();
            int p = Math.max(0, Math.min(n - 1, pageCles + (delta < 0 ? 1 : -1)));
            if (p != pageCles) {
                pageCles = p;
                SonsUi.clic(0.6F);
            }
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    private boolean peutPayer(ReseauBoutique.Offre o) {
        return etat.solde() < 0 || etat.solde() >= o.prix();
    }

    private void zone(float x, float y, float w, float h, Runnable r) {
        zones.add(new Zone(x, y, w, h, r));
    }

    private boolean survol(float x, float y, float w, float h) {
        double mx = sx - ox, my = sy - oy;
        return !succesBeta && mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ------------------------------------------------------------------ dessin commun
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        sx = mx;
        sy = my;
        zones.clear();
        voile(g);
        if (!onglets().contains(onglet)) onglet = "grades";
        g.pose().pushPose();
        g.pose().translate(ox, oy, 0);
        Font f = font;
        entete(g, f);
        switch (onglet) {
            case "beta" -> beta(g, f);
            case "cles" -> cles(g, f);
            case "mes_cles" -> mesCles(g, f);
            case "sur_mesure" -> surMesure(g, f);
            default -> grades(g, f);
        }
        if (succesBeta) {
            zones.clear();
            succes(g, f);
        }
        g.pose().popPose();
    }

    /** Le monde reste visible derrière un voile sombre (plus sombre sur les côtés) : celui du créateur de personnage. */
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

    private static void filetHaut(GuiGraphics g, float x, float y, float w, int c) {
        float mid = x + w / 2;
        Trace.gradH(g, x + 10, y, mid, y + 2 / 3F, a(c, 0), a(c, 255));
        Trace.gradH(g, mid, y, x + w - 10, y + 2 / 3F, a(c, 255), a(c, 0));
    }

    private void bouton(GuiGraphics g, float x, float y, float w, float h, boolean actif, int c) {
        if (actif) {
            float hv = survol(x, y, w, h) ? 1 : 0;
            Trace.gradH(g, x, y, x + w, y + h, a(c, 77 + 34 * hv), a(c, 31 + 26 * hv));
            Trace.bord(g, x, y, w, h, 1 / 3F, a(c, 140 + 60 * hv));
        } else {
            Trace.rect(g, x, y, x + w, y + h, a(WHITE, 13));
            Trace.bord(g, x, y, w, h, 1 / 3F, a(WHITE, 31));
        }
    }

    private static void piece(GuiGraphics g, float x, float y, float d) {
        Trace.image(g, TEX_PIECE, x, y, d, d, 1);
    }

    /** Pièce, montant et « PB », alignés à droite sur xd ; rend la largeur. */
    private float prix(GuiGraphics g, Font f, long n, float xd, float cy, P p, P pp, int c, float ico) {
        String lab = nombre(n);
        float w = ico + 2 + Trace.largeur(f, lab, p) + 1.5F + Trace.largeur(f, "PB", pp);
        float x = xd - w;
        piece(g, x, cy - ico / 2, ico);
        float lw = Trace.m(g, f, lab, x + ico + 2, cy, p, a(WHITE, 255), 0);
        Trace.texte(g, f, "PB", x + ico + 2 + lw + 1.5F, cy + p.cap() / 2 - pp.cap(), pp, a(c, 255));
        return w;
    }

    private float puce(GuiGraphics g, Font f, String txt, float x, float cy, int c) {
        float w = Trace.largeur(f, txt, PETIT_GRAS) + 5;
        Trace.rect(g, x, cy - 4, x + w, cy + 4, a(c, 50));
        Trace.bord(g, x, cy - 4, w, 8, 1 / 3F, a(c, 140));
        Trace.c(g, f, txt, x + w / 2, cy, PETIT_GRAS, a(mix(c, WHITE, 0.3F), 255), 0);
        return w;
    }

    private void entete(GuiGraphics g, Font f) {
        Trace.texte(g, f, Trace.tenir(f, t("titre"), TITRE, 200), M, 20, TITRE, a(LIGHT, 255));
        Trace.texte(g, f, Trace.tenir(f, t("sous." + onglet), TEXTE, 430), M, 37, TEXTE, a(MUTED, 255));
        // solde de PB, toujours en vue
        float cw = 158, ch = 30, cx = GW - M - cw, cy = 14;
        Trace.gradH(g, cx, cy, cx + cw, cy + ch, a(GOLD, 40), a(GOLD, 12));
        Trace.bord(g, cx, cy, cw, ch, 1 / 3F, a(GOLD, 110));
        Trace.lueur(g, cx + 16, cy + ch / 2, 18, 15, a(GOLD, 60));
        Trace.ajuster(g, TEX_PB, cx + 4, cy + 3, 24, 24, false, 1);
        Trace.texte(g, f, Trace.tenir(f, t("ton_solde"), MINI, cw - 33 - 48), cx + 33, cy + 6, MINI, a(mix(GOLD, WHITE, 0.35F), 220));
        String solde = etat.solde() < 0 ? "—" : nombre(etat.solde());
        float w = Trace.texte(g, f, solde, cx + 33, cy + 15.5F, CHIFFRE_GRAND, a(WHITE, 255));
        Trace.texte(g, f, "PB", cx + 33 + w + 2.5F, cy + 15.5F + CHIFFRE_GRAND.cap() - PB_GRAND.cap(), PB_GRAND, a(GOLD, 255));
        float bx = cx + cw - 44;
        boolean hv = survol(bx, cy, 44, ch);
        if (hv) Trace.rect(g, bx, cy + 1 / 3F, cx + cw - 1 / 3F, cy + ch - 1 / 3F, a(GOLD, 30));
        Trace.rect(g, bx, cy + 5, bx + 1 / 3F, cy + ch - 5, a(GOLD, 70));
        Trace.rect(g, bx + 22 - 4.5F, cy + 10.5F - 0.67F, bx + 22 + 4.5F, cy + 10.5F + 0.67F, a(GOLD, 255));
        Trace.rect(g, bx + 22 - 0.67F, cy + 10.5F - 4.5F, bx + 22 + 0.67F, cy + 10.5F + 4.5F, a(GOLD, 255));
        Trace.c(g, f, t("recharger"), bx + 22, cy + 22, MICRO, a(mix(GOLD, WHITE, 0.3F), 230), 42);
        zone(bx, cy, 44, ch, () -> lien(LIEN_PB));
        // onglets
        float ty = 54, th = 22, x = M;
        List<String> l = onglets();
        for (int i = 0; i < l.size(); i++) {
            String o = l.get(i);
            String lab = t("onglet." + o);
            float tw = Trace.largeur(f, lab, ONGLET) + 21.33F + 12;
            boolean sel = o.equals(onglet), hvo = !sel && survol(x, ty, tw, th);
            if (sel) {
                Trace.gradH(g, x, ty, x + tw * 0.75F, ty + th, a(ACCENT, 51), a(ACCENT, 10));
                Trace.gradH(g, x + tw * 0.75F, ty, x + tw, ty + th, a(ACCENT, 10), a(ACCENT, 0));
                Trace.bord(g, x, ty, tw, th, 1 / 3F, a(ACCENT, 92));
                Trace.rect(g, x + 4, ty + th - 1.33F, x + tw - 4, ty + th, a(ACCENT, 255));
            } else {
                Trace.rect(g, x, ty, x + tw, ty + th, a(WHITE, hvo ? 16 : 8));
                Trace.bord(g, x, ty, tw, th, 1 / 3F, a(WHITE, hvo ? 40 : 22));
            }
            Trace.m(g, f, String.format(Locale.ROOT, "%02d", i + 1), x + 6.67F, ty + th / 2, MINI_NUM, a(sel ? PINK : FAINT, 255), 0);
            Trace.m(g, f, lab, x + 21.33F, ty + th / 2, ONGLET, a(sel ? WHITE : hvo ? WHITE : OPTION, 255), 0);
            if (o.equals("beta")) Trace.disque(g, x + tw - 3.5F, ty + 3.5F, 2.2F, a(couleur("beta", 0xA970FF), 255));
            zone(x, ty, tw, th, () -> {
                if (!onglet.equals(o)) {
                    onglet = o;
                    arme = null;
                    SonsUi.clic(0.6F);
                }
            });
            x += tw + 4;
        }
        Trace.rect(g, M, ty + th + 4, GW - M, ty + th + 4 + 1 / 3F, a(WHITE, 23));
    }

    /** Touches et note du pied ; un message d'achat récent prend la place de la note. */
    private void pied(GuiGraphics g, Font f, String[][] touches, String note) {
        float cy = 348, x = M;
        for (String[] k : touches) {
            String kl = t("touche." + k[0]).toUpperCase(Locale.ROOT);
            float tw = Trace.largeur(f, kl, MINI_NUM), w = Math.max(9, tw + 4.67F);
            Trace.rect(g, x, cy - 4.5F, x + w, cy + 4.5F, a(WHITE, 30));
            Trace.bord(g, x, cy - 4.5F, w, 9, 1 / 3F, a(WHITE, 61));
            Trace.c(g, f, kl, x + w / 2, cy, MINI_NUM, a(WHITE, 255), 0);
            x += w + 3.5F;
            x += Trace.m(g, f, t("pied." + k[1]), x, cy, PETIT, a(MUTED, 255), 0) + 10;
        }
        long age = System.currentTimeMillis() - messageA;
        if (!message.isEmpty() && age < 6000) {
            float al = 255 * Math.min(1, (6000 - age) / 600F);
            String cle = message.startsWith("achete") ? "msg.achete" : "msg." + message;
            Trace.d(g, f, t(cle), GW - M, cy, PETIT_GRAS, a(messageOk ? OK : DANGER, Math.max(20, al)), GW - M - x - 8);
        } else {
            Trace.d(g, f, t("note." + note), GW - M, cy, PETIT, a(FAINT, 255), GW - M - x - 8);
        }
    }

    private static String avantage(String[] av) {
        return av[1] == null ? t("av." + av[0]) : t("av." + av[0], av[1]);
    }

    /** Coche et texte (deux lignes au plus) de chaque avantage ; rend le y suivant. */
    private float listeAvantages(GuiGraphics g, Font f, String[][] items, float x, float y, float w, int c) {
        for (String[] av : items) {
            List<String> ls = Trace.lignes(f, avantage(av), TEXTE, w - 9);
            Trace.coche(g, x, y - 2.2F, 4.6F, 1.0F, a(c, 255));
            for (int k = 0; k < Math.min(2, ls.size()); k++) Trace.m(g, f, ls.get(k), x + 9, y + k * 9, TEXTE, a(LIGHT, 255), w - 9);
            y += 10 + 9 * (Math.min(2, ls.size()) - 1);
        }
        return y;
    }

    // ------------------------------------------------------------------ 1. Grades
    private void grades(GuiGraphics g, Font f) {
        float cw = 138, gap = 9;
        for (int i = 0; i < GRADES.length; i++) {
            String gr = GRADES[i];
            List<ReseauBoutique.Offre> os = offres(gr);
            int col = couleur(gr, 0x6FA8FF);
            float x = M + i * (cw + gap), y = HAUT, h = BAS - HAUT;
            boolean s = i == gradeChoisi, hv = !s && survol(x, y, cw, h);
            Trace.rect(g, x, y, x + cw, y + h, a(GLASS, s ? 205 : 180));
            Trace.gradV(g, x, y, x + cw, y + 160, a(col, s ? 52 : hv ? 34 : 22), a(col, 0));
            Trace.lueur(g, x + cw / 2, y + 112, cw * 0.48F, 78, a(col, s ? 70 : hv ? 45 : 30));
            Trace.bord(g, x, y, cw, h, s ? 2 / 3F : 1 / 3F, s ? a(col, 200) : a(WHITE, hv ? 60 : 26));
            if (s) filetHaut(g, x, y, cw, col);
            Trace.m(g, f, t("grade"), x + 8, y + 10, MINI, a(mix(col, WHITE, 0.4F), 230), cw - 40);
            int rang = RANG.get(gr);
            for (int k = 0; k < 3; k++) {
                Trace.losange(g, x + cw - 8 - 2.4F - 13 + k * 6.5F, y + 10, 2.4F, k < rang ? a(col, 255) : a(WHITE, 40));
            }
            Trace.lueur(g, x + cw / 2, y + 172, 44, 5, a(0, 150));
            Trace.ajuster(g, tex("grade_" + gr), x + 6, y + 20, cw - 12, 154, true, s || hv ? 1 : 0.78F);
            Trace.gradV(g, x + 1 / 3F, y + 150, x + cw - 1 / 3F, y + 176, a(GLASS, 0), a(GLASS, 200));
            Trace.rect(g, x + 1 / 3F, y + 176, x + cw - 1 / 3F, y + h - 1 / 3F, a(GLASS, 200));
            Trace.c(g, f, nomGrade(gr), x + cw / 2, y + 186, NOM, a(col, 255), cw - 12);
            Trace.c(g, f, t("resume." + gr), x + cw / 2, y + 199, PETIT, a(s ? OPTION : MUTED, 255), cw - 12);
            Trace.rect(g, x + 12, y + 209, x + cw - 12, y + 209 + 1 / 3F, a(WHITE, 26));
            if (!os.isEmpty()) {
                Trace.c(g, f, t("a_partir_de"), x + cw / 2, y + 217, MICRO, a(MUTED, 255), cw - 12);
                String lab = nombre(os.get(0).prix());
                float tw = 11 + Trace.largeur(f, lab, CHIFFRE) + 1.5F + Trace.largeur(f, "PB", PB_MOYEN) + 2 + Trace.largeur(f, t("par_mois"), PETIT);
                float px0 = x + cw / 2 - tw / 2;
                piece(g, px0, y + 229 - 4.5F, 9);
                float w = Trace.m(g, f, lab, px0 + 11, y + 229, CHIFFRE, a(WHITE, 255), 0);
                Trace.texte(g, f, "PB", px0 + 11 + w + 1.5F, y + 229 + CHIFFRE.cap() / 2 - PB_MOYEN.cap(), PB_MOYEN, a(GOLD, 255));
                Trace.texte(g, f, t("par_mois"), px0 + 11 + w + 1.5F + Trace.largeur(f, "PB", PB_MOYEN) + 2, y + 229 + CHIFFRE.cap() / 2 - PETIT.cap(),
                        PETIT, a(MUTED, 255));
            }
            if (s) {
                String lab = t("choisi");
                float bw = Trace.largeur(f, lab, MINI) + 14, bx = x + cw / 2 - bw / 2, by = y + h - 1;
                Trace.rect(g, bx, by - 5, bx + bw, by + 5, a(col, 255));
                Trace.coche(g, bx + 4, by - 2.6F, 4.8F, 1.1F, a(NOIR_BADGE, 255));
                Trace.m(g, f, lab, bx + 11, by, MINI, a(NOIR_BADGE, 255), 0);
            }
            final int idx = i;
            zone(x, y, cw, h, () -> {
                if (gradeChoisi != idx) {
                    gradeChoisi = idx;
                    arme = null;
                    SonsUi.clic(0.7F);
                }
            });
        }
        panneauAchat(g, f, M + 3 * cw + 2 * gap + 10);
        pied(g, f, new String[][]{{"clic", "choisir"}, {"entree", "acheter"}, {"echap", "fermer"}}, "grades");
    }

    private void panneauAchat(GuiGraphics g, Font f, float px) {
        String gr = GRADES[gradeChoisi];
        int col = couleur(gr, 0x6FA8FF);
        float pw = GW - M - px, py = HAUT, ph = BAS - HAUT;
        verre(g, px, py, pw, ph, 199);
        filetHaut(g, px, py, pw, ACCENT);
        float cx = px + 10, cwid = pw - 20;
        Trace.texte(g, f, Trace.tenir(f, t("ton_choix"), MINI, cwid), cx, py + 10, MINI, a(MUTED, 255));
        String nom = nomGrade(gr);
        P p = Trace.largeur(f, nom, NOM_GRAND) <= cwid ? NOM_GRAND : NOM;
        Trace.texte(g, f, Trace.tenir(f, nom, p, cwid), cx, py + 20 + (p == NOM_GRAND ? 0 : 1), p, a(col, 255));
        float y = listeAvantages(g, f, AVANTAGES.get(gr), cx, py + 41, cwid, col);
        Trace.rect(g, cx, y - 3, cx + cwid, y - 3 + 1 / 3F, a(WHITE, 23));
        y += 3;
        Trace.texte(g, f, Trace.tenir(f, t("duree"), MINI, cwid), cx, y, MINI, a(MUTED, 255));
        y += 8;
        List<ReseauBoutique.Offre> os = offres(gr);
        if (os.isEmpty()) return;
        int duree = choix(gr, os.size() - 1);
        double p1 = os.get(0).prix() / (double) Math.max(1, os.get(0).quantite()); // prix d'un mois
        float oh = 24;
        for (int j = 0; j < os.size(); j++) {
            ReseauBoutique.Offre o = os.get(j);
            boolean s = j == duree, hv = !s && survol(cx, y, cwid, oh);
            if (s) {
                Trace.gradH(g, cx, y, cx + cwid, y + oh, a(ACCENT, 60), a(ACCENT, 14));
                Trace.bord(g, cx, y, cwid, oh, 1 / 3F, a(ACCENT, 140));
            } else {
                Trace.rect(g, cx, y, cx + cwid, y + oh, a(WHITE, hv ? 18 : 10));
                Trace.bord(g, cx, y, cwid, oh, 1 / 3F, a(WHITE, hv ? 50 : 30));
            }
            float rcy = y + oh / 2;
            Trace.anneau(g, cx + 9, rcy, 3.6F - 0.75F, 3.6F, s ? a(WHITE, 255) : a(MUTED, 160));
            if (s) Trace.disque(g, cx + 9, rcy, 1.9F, a(ACCENT, 255));
            int q = o.quantite();
            String sous = q <= 1 ? null : t("soit_mois", nombre(Math.round(o.prix() / (double) q)));
            float ly = sous == null ? rcy : rcy - 4;
            String lab = q <= 1 ? t("mois.1") : t("mois.n", q);
            float wp = prix(g, f, o.prix(), cx + cwid - 6, rcy, TEXTE_GRAS, PB_PETIT, GOLD, 8.5F);
            float w = Trace.m(g, f, lab, cx + 17, ly, TEXTE_GRAS, a(s ? WHITE : OPTION, 255), cwid - 17 - wp - 10);
            if (q > 1) {
                long pct = Math.round(100 - 100.0 * o.prix() / (q * p1));
                if (pct > 0) puce(g, f, "−" + pct + " %", cx + 17 + w + 4, ly, OK);
                Trace.m(g, f, sous, cx + 17, rcy + 5, PETIT, a(MUTED, 255), cwid - 17 - wp - 8);
            }
            final int jj = j, dd = duree;
            zone(cx, y, cwid, oh, () -> {
                if (dd != jj) {
                    choix.put(gr, jj);
                    arme = null;
                    SonsUi.clic(0.6F);
                }
            });
            y += oh + 3;
        }
        y += 1;
        ReseauBoutique.Offre o = os.get(duree);
        if (etat.solde() >= 0) {
            Trace.m(g, f, t("ton_solde"), cx, y + 4, PETIT, a(MUTED, 255), cwid - 50);
            Trace.d(g, f, nombre(etat.solde()) + " PB", cx + cwid, y + 4, PETIT_GRAS, a(OPTION, 255), 0);
            boolean assez = etat.solde() >= o.prix();
            Trace.m(g, f, assez ? t("apres_achat") : t("manque", nombre(o.prix() - etat.solde())), cx, y + 13, assez ? PETIT : PETIT_GRAS,
                    a(assez ? MUTED : DANGER, 255), cwid - 50);
            if (assez) Trace.d(g, f, nombre(etat.solde() - o.prix()) + " PB", cx + cwid, y + 13, PETIT_GRAS, a(GOLD, 255), 0);
        }
        float bh = 22, by = py + ph - 10 - bh;
        boolean ok = peutPayer(o) && !attente;
        bouton(g, cx, by, cwid, bh, ok, ACCENT);
        Trace.c(g, f, libelleAchat(o), cx + cwid / 2, by + bh / 2, BOUTON, a(ok ? WHITE : MUTED, 255), cwid - 8);
        if (ok) zone(cx, by, cwid, bh, () -> acheter(o));
        else if (!attente) zone(cx, by, cwid, bh, () -> lien(LIEN_PB));
    }

    // ------------------------------------------------------------------ 2. Offre bêta test
    private void beta(GuiGraphics g, Font f) {
        List<ReseauBoutique.Offre> os = offres("beta");
        if (os.isEmpty()) return;
        ReseauBoutique.Offre o = os.get(0);
        int beta = o.couleur();
        float h = BAS - HAUT, sxx = M, sw = 300;
        Trace.rect(g, sxx, HAUT, sxx + sw, BAS, a(GLASS, 190));
        Trace.gradV(g, sxx, HAUT, sxx + sw, BAS, a(beta, 70), a(beta, 0));
        Trace.lueur(g, sxx + sw / 2, HAUT + 120, 150, 120, a(beta, 80));
        Trace.bord(g, sxx, HAUT, sw, h, 2 / 3F, a(beta, 200));
        filetHaut(g, sxx, HAUT, sw, beta);
        float cx0 = sxx + sw / 2, cy0 = HAUT + 118, r = 92;
        for (int k = 0; k < 36; k++) {
            double a0 = k / 36.0 * 2 * Math.PI, a1 = a0 + Math.PI / 60;
            Trace.trait(g, cx0 + r * (float) Math.sin(a0), cy0 - r * (float) Math.cos(a0), cx0 + r * (float) Math.sin(a1), cy0 - r * (float) Math.cos(a1),
                    0.6F, a(beta, 120));
        }
        Trace.lueur(g, sxx + sw / 2, BAS - 30, 80, 7, a(0, 170));
        Trace.ajuster(g, tex("grade_beta"), sxx + 20, HAUT + 28, sw - 40, h - 58, true, 1);
        Trace.gradV(g, sxx + 1, BAS - 40, sxx + sw - 1, BAS - 2 / 3F, a(GLASS, 0), a(GLASS, 230));
        String chip = t("beta.chip");
        float rw = Trace.largeur(f, chip, MINI) + 10;
        Trace.rect(g, sxx + 10, HAUT + 10, sxx + 10 + rw, HAUT + 20, a(beta, 255));
        Trace.m(g, f, chip, sxx + 15, HAUT + 15, MINI, a(WHITE, 255), 0);
        Trace.c(g, f, "BETA", sxx + sw / 2, BAS - 13, BETA_GROS, a(WHITE, 255), 0);

        float px = sxx + sw + 10, pw = GW - M - px;
        verre(g, px, HAUT, pw, h, 199);
        filetHaut(g, px, HAUT, pw, beta);
        float cx = px + 12, cw = pw - 24;
        Trace.texte(g, f, Trace.tenir(f, t("beta.sur"), MINI, cw), cx, HAUT + 11, MINI, a(mix(beta, WHITE, 0.35F), 255));
        Trace.texte(g, f, Trace.tenir(f, t("beta.titre"), NOM_GRAND, cw), cx, HAUT + 21, NOM_GRAND, a(WHITE, 255));
        float y = HAUT + 39;
        List<String> ls = Trace.lignes(f, t("beta.texte"), TEXTE, cw);
        for (int k = 0; k < Math.min(3, ls.size()); k++) {
            Trace.texte(g, f, ls.get(k), cx, y, TEXTE, a(OPTION, 255));
            y += 10;
        }
        y += 6;
        float colw = (cw - 8) / 2;
        for (int rr = 0; rr < AVANTAGES_BETA.length; rr += 2) {
            float max = 0;
            for (int k = 0; k < 2 && rr + k < AVANTAGES_BETA.length; k++) {
                float yy = listeAvantages(g, f, new String[][]{AVANTAGES_BETA[rr + k]}, cx + k * (colw + 8), y, colw, beta);
                max = Math.max(max, yy - y);
            }
            y += max;
        }
        Trace.rect(g, cx, y - 2, cx + cw, y - 2 + 1 / 3F, a(WHITE, 23));
        y += 4;
        Trace.texte(g, f, Trace.tenir(f, t("beta.vaut"), MINI, cw), cx, y, MINI, a(MUTED, 255));
        y += 10;
        List<ReseauBoutique.Offre> roi = offres("roi_de_la_zone");
        if (!roi.isEmpty()) {
            long[] prix = {roi.get(0).prix(), roi.get(roi.size() - 1).prix(), roi.get(roi.size() - 1).prix() * 4};
            String[] cles = {"beta.ligne1", "beta.ligne3", "beta.ligne_an"};
            for (int k = 0; k < 3; k++) {
                Trace.m(g, f, t(cles[k]), cx, y, TEXTE, a(MUTED, 255), cw - 60);
                Trace.d(g, f, nombre(prix[k]) + " PB", cx + cw, y, TEXTE, a(MUTED, 255), 0);
                y += 9.5F;
            }
        }
        y += 1;
        Trace.rect(g, cx, y, cx + cw, y + 22, a(beta, 30));
        Trace.bord(g, cx, y, cw, 22, 1 / 3F, a(beta, 140));
        Trace.m(g, f, t("beta.prix"), cx + 8, y + 11, TEXTE_GRAS, a(WHITE, 255), cw - 100);
        prix(g, f, o.prix(), cx + cw - 8, y + 11, CHIFFRE, PB_MOYEN, mix(beta, WHITE, 0.4F), 10);
        float bh = 22, by = BAS - 10 - bh;
        boolean assez = peutPayer(o);
        if (!assez) {
            Trace.m(g, f, t("manque", nombre(o.prix() - etat.solde())), cx, by - 7, PETIT_GRAS, a(DANGER, 255), cw / 2);
            String lien = t("recharger_site");
            float lw = Trace.largeur(f, lien, PETIT_GRAS);
            boolean hv = survol(cx + cw - lw, by - 12, lw, 10);
            Trace.d(g, f, lien, cx + cw, by - 7, PETIT_GRAS, a(hv ? mix(GOLD, WHITE, 0.4F) : GOLD, 255), cw / 2);
            zone(cx + cw - lw, by - 12, lw, 10, () -> lien(LIEN_PB));
        }
        boolean ok = assez && !attente;
        bouton(g, cx, by, cw, bh, ok, ACCENT);
        Trace.c(g, f, libelleAchat(o), cx + cw / 2, by + bh / 2, BOUTON, a(ok ? WHITE : MUTED, 255), cw - 8);
        if (ok) zone(cx, by, cw, bh, () -> acheter(o));
        pied(g, f, new String[][]{{"clic", "choisir"}, {"echap", "fermer"}}, "beta");
    }

    /** Fenêtre après l'achat du BETA : la demande d'emote ou de finisher se fait sur Discord. */
    private void succes(GuiGraphics g, Font f) {
        Trace.rect(g, -ox, -oy, width - ox, height - oy, a(0, 150));
        int beta = couleur("beta", 0xA970FF);
        float w = 300, x = (GW - w) / 2, cw = w - 28;
        List<String> ls = Trace.lignes(f, t("succes.texte"), TEXTE, cw);
        float h = 30 + 12 + ls.size() * 10 + 12 + 22 + 12, y = (GH - h) / 2;
        verre(g, x, y, w, h, 240);
        Trace.bord(g, x, y, w, h, 2 / 3F, a(beta, 200));
        filetHaut(g, x, y, w, beta);
        Trace.texte(g, f, Trace.tenir(f, t("succes.titre"), NOM, cw), x + 14, y + 14, NOM, a(WHITE, 255));
        float ty = y + 34;
        for (String l : ls) {
            Trace.texte(g, f, l, x + 14, ty, TEXTE, a(OPTION, 255));
            ty += 10;
        }
        float by = y + h - 12 - 22, bw = (cw - 8) / 2;
        boolean hv = survol(x + 14, by, bw, 22);
        Trace.gradH(g, x + 14, by, x + 14 + bw, by + 22, a(DISCORD, hv ? 240 : 210), a(DISCORD, hv ? 190 : 150));
        Trace.bord(g, x + 14, by, bw, 22, 1 / 3F, a(mix(DISCORD, WHITE, 0.4F), 220));
        Trace.c(g, f, t("succes.demande"), x + 14 + bw / 2, by + 11, BOUTON, a(WHITE, 255), bw - 8);
        bouton(g, x + 22 + bw, by, bw, 22, false, ACCENT);
        Trace.c(g, f, t("succes.plus_tard"), x + 22 + bw + bw / 2, by + 11, BOUTON, a(OPTION, 255), bw - 8);
        zone(x + 14, by, bw, 22, () -> {
            succesBeta = false;
            lien(LIEN_DISCORD);
        });
        zone(x + 22 + bw, by, bw, 22, () -> succesBeta = false);
    }

    // ------------------------------------------------------------------ 3. Clés
    private void cles(GuiGraphics g, Font f) {
        float gap = 9, cw = (GW - 2 * M - 2 * gap) / 3;
        for (int i = 0; i < CAISSES.length; i++) {
            String ca = CAISSES[i];
            List<ReseauBoutique.Offre> os = offres(ca);
            int col = couleur(ca, 0x6FA8FF);
            float x = M + i * (cw + gap);
            Trace.rect(g, x, HAUT, x + cw, BAS, a(GLASS, 195));
            Trace.gradV(g, x, HAUT, x + cw, HAUT + 150, a(col, 46), a(col, 0));
            Trace.bord(g, x, HAUT, cw, BAS - HAUT, 1 / 3F, a(WHITE, 26));
            filetHaut(g, x, HAUT, cw, col);
            Trace.m(g, f, t("caisse"), x + 10, HAUT + 11, MINI, a(mix(col, WHITE, 0.4F), 230), (cw - 20) / 2 - 4);
            // clés déjà sur son compte (25/10)
            int possede = fr.heraliz.crates.cles.SoldeCles.de(fr.heraliz.crates.crate.CrateType.byId(ca));
            Trace.d(g, f, t("possede", nombre(possede)), x + cw - 10, HAUT + 11, MINI, a(possede > 0 ? WHITE : MUTED, 230), (cw - 20) / 2 - 4);
            Trace.lueur(g, x + cw / 2, HAUT + 60, 50, 42, a(col, 85));
            Trace.anneau(g, x + cw / 2, HAUT + 60, 37 - 0.25F, 37 + 0.25F, a(col, 60));
            Trace.anneau(g, x + cw / 2, HAUT + 60, 29 - 0.25F, 29 + 0.25F, a(col, 35));
            ItemStack cle = os.isEmpty() ? ItemStack.EMPTY : os.get(0).icone();
            objet(g, cle, x + cw / 2, HAUT + 60, 4.4F);
            Trace.c(g, f, t("cle." + ca), x + cw / 2, HAUT + 111, NOM, a(WHITE, 255), cw - 16);
            Trace.c(g, f, t("cle_desc." + ca), x + cw / 2, HAUT + 124, PETIT, a(OPTION, 255), cw - 14);
            float y = HAUT + 135;
            Trace.rect(g, x + 10, y, x + cw - 10, y + 1 / 3F, a(WHITE, 23));
            y += 8;
            float oh = 30;
            int sel = choix(ca, 0);
            for (int j = 0; j < os.size() && j < 3; j++) {
                ReseauBoutique.Offre o = os.get(j);
                boolean s = j == sel;
                float bx = x + 10, bw = cw - 20;
                boolean hv = !s && survol(bx, y, bw, oh);
                if (s) {
                    Trace.gradH(g, bx, y, bx + bw, y + oh, a(ACCENT, 60), a(ACCENT, 14));
                    Trace.bord(g, bx, y, bw, oh, 1 / 3F, a(ACCENT, 140));
                } else {
                    Trace.rect(g, bx, y, bx + bw, y + oh, a(WHITE, hv ? 18 : 10));
                    Trace.bord(g, bx, y, bw, oh, 1 / 3F, a(WHITE, hv ? 50 : 30));
                }
                int n = Math.max(1, o.quantite());
                for (int k = Math.min(n, 3) - 1; k >= 0; k--) objet(g, o.icone(), bx + 11.5F + k * 4, y + oh / 2, 0.85F);
                float lx = bx + 30, ly = n == 1 ? y + oh / 2 : y + oh / 2 - 4.2F;
                float wp = prix(g, f, o.prix(), bx + bw - 6, y + oh / 2, TEXTE_GRAS, PB_PETIT, GOLD, 8.5F);
                float w = Trace.m(g, f, t(n == 1 ? "n_cle" : "n_cles", n), lx, ly, TEXTE_GRAS, a(s ? WHITE : OPTION, 255), bw - 30 - wp - 8);
                if (n > 1 && !os.isEmpty()) {
                    long plein = os.get(0).prix() * n / Math.max(1, os.get(0).quantite());
                    long pct = Math.round(100 - 100.0 * o.prix() / plein);
                    if (pct > 0) puce(g, f, "−" + pct + " %", lx + w + 4, ly, OK);
                    Trace.m(g, f, t("au_lieu", nombre(plein)), lx, y + oh / 2 + 5.2F, PETIT, a(MUTED, 255), bw - 30 - wp - 8);
                }
                final int jj = j, ss = sel;
                zone(bx, y, bw, oh, () -> {
                    if (ss != jj) {
                        choix.put(ca, jj);
                        arme = null;
                        SonsUi.clic(0.6F);
                    }
                });
                y += oh + 4;
            }
            if (os.isEmpty()) continue;
            ReseauBoutique.Offre o = os.get(sel);
            float bh = 20, by = BAS - 10 - bh;
            boolean ok = peutPayer(o) && !attente;
            bouton(g, x + 10, by, cw - 20, bh, ok, ACCENT);
            Trace.c(g, f, libelleAchat(o), x + cw / 2, by + bh / 2, BOUTON, a(ok ? WHITE : MUTED, 255), cw - 28);
            if (ok) zone(x + 10, by, cw - 20, bh, () -> acheter(o));
            else if (!attente) zone(x + 10, by, cw - 20, bh, () -> lien(LIEN_PB));
        }
        pied(g, f, new String[][]{{"clic", "choisir"}, {"entree", "acheter"}, {"echap", "fermer"}}, "cles");
    }

    // ------------------------------------------------------------------ 3 bis. Mes clés
    /** Clés montrées : celles qu'on peut obtenir, puis les autres que le joueur possède. */
    private static List<fr.heraliz.crates.crate.CrateType> listeMesCles() {
        List<fr.heraliz.crates.crate.CrateType> l = new ArrayList<>(List.of(CLES_TOUJOURS));
        for (fr.heraliz.crates.crate.CrateType t : fr.heraliz.crates.crate.CrateType.values()) {
            if (!l.contains(t) && fr.heraliz.crates.cles.SoldeCles.de(t) > 0) l.add(t);
        }
        return l;
    }

    private static int pagesMesCles() {
        return Math.max(1, (listeMesCles().size() + CLES_PAR_PAGE - 1) / CLES_PAR_PAGE);
    }

    private static String sourceCle(fr.heraliz.crates.crate.CrateType t) {
        return switch (t) {
            case QUETE, VOTE, EVENT, GEMME, CAMO, SPECTACLE -> t("mes_cles.src." + t.id);
            default -> t("mes_cles.src.autre");
        };
    }

    /**
     * Les clés du compte du joueur (envoyées par le serveur) : la vraie clé dessinée par le jeu, son nom, combien il en a,
     * la caisse qu'elle ouvre et comment l'obtenir. Deux colonnes de trois ; pages au clavier (flèches) ou à la molette.
     */
    private void mesCles(GuiGraphics g, Font f) {
        List<fr.heraliz.crates.crate.CrateType> l = listeMesCles();
        int pages = pagesMesCles();
        pageCles = Math.max(0, Math.min(pages - 1, pageCles));
        float cw = (GW - 2 * M - MC_GAP_X) / 2, ch = (BAS - HAUT - 2 * MC_GAP_Y) / 3;
        for (int k = 0; k < CLES_PAR_PAGE; k++) {
            int i = pageCles * CLES_PAR_PAGE + k;
            if (i >= l.size()) break;
            fr.heraliz.crates.crate.CrateType t = l.get(i);
            int col = t.glow & 0xFFFFFF, n = fr.heraliz.crates.cles.SoldeCles.de(t);
            float x = M + (k % 2) * (cw + MC_GAP_X), y = HAUT + (k / 2) * (ch + MC_GAP_Y);
            Trace.rect(g, x, y, x + cw, y + ch, a(GLASS, 195));
            Trace.gradH(g, x, y, x + 120, y + ch, a(col, n > 0 ? 46 : 22), a(col, 0));
            Trace.bord(g, x, y, cw, ch, 1 / 3F, a(WHITE, 26));
            Trace.rect(g, x, y + 6, x + 4 / 3F, y + ch - 6, a(col, n > 0 ? 255 : 110));
            float icx = x + MC_ICONE, icy = y + ch / 2;
            Trace.lueur(g, icx, icy, 30, 26, a(col, n > 0 ? 80 : 40));
            Trace.anneau(g, icx, icy, 24 - 0.25F, 24 + 0.25F, a(col, 60));
            objet(g, new ItemStack(fr.heraliz.crates.registry.ModRegistry.key(t)), icx, icy, 2.4F);
            float tx = x + MC_TEXTE, xr = x + cw - 10, tw = xr - tx;
            String cpt = "×" + nombre(n);
            float cwid = Trace.largeur(f, cpt, CHIFFRE);
            Trace.d(g, f, cpt, xr, y + 16, CHIFFRE, a(n > 0 ? WHITE : FAINT, 255), 0);
            String nom = Component.translatable("item.heralizcrates." + t.keyId()).getString();
            Trace.m(g, f, nom, tx, y + 16, NOM, a(mix(col, WHITE, 0.25F), 255), tw - cwid - 8);
            String caisse = Component.translatable("block.heralizcrates." + t.blockId()).getString();
            Trace.m(g, f, t("mes_cles.ouvre", caisse), tx, y + 29, PETIT_GRAS, a(OPTION, 255), tw);
            Trace.rect(g, tx, y + 36, xr, y + 36 + 1 / 3F, a(WHITE, 23));
            Trace.m(g, f, t("mes_cles.obtenir"), tx, y + 43, MICRO, a(MUTED, 255), tw);
            List<String> ls = Trace.lignes(f, sourceCle(t), TEXTE, tw);
            for (int j = 0; j < Math.min(2, ls.size()); j++) Trace.m(g, f, ls.get(j), tx, y + 53 + j * 9, TEXTE, a(LIGHT, 255), tw);
        }
        if (!fr.heraliz.crates.cles.SoldeCles.disponible()) {
            piedTexte(g, f, pages > 1, t("mes_cles.indispo"), DANGER);
        } else if (pages > 1) {
            piedTexte(g, f, true, t("mes_cles.page", pageCles + 1, pages), MUTED);
        } else {
            pied(g, f, new String[][]{{"echap", "fermer"}}, "mes_cles");
        }
    }

    /** Pied de l'onglet « Mes clés » : touches (flèches si plusieurs pages) et un texte à droite. */
    private void piedTexte(GuiGraphics g, Font f, boolean fleches, String texte, int couleur) {
        String[][] touches = fleches ? new String[][]{{"fleches", "page"}, {"echap", "fermer"}} : new String[][]{{"echap", "fermer"}};
        float cy = 348, x = M;
        for (String[] k : touches) {
            String kl = t("touche." + k[0]).toUpperCase(Locale.ROOT);
            float tw = Trace.largeur(f, kl, MINI_NUM), w = Math.max(9, tw + 4.67F);
            Trace.rect(g, x, cy - 4.5F, x + w, cy + 4.5F, a(WHITE, 30));
            Trace.bord(g, x, cy - 4.5F, w, 9, 1 / 3F, a(WHITE, 61));
            Trace.c(g, f, kl, x + w / 2, cy, MINI_NUM, a(WHITE, 255), 0);
            x += w + 3.5F;
            x += Trace.m(g, f, t("pied." + k[1]), x, cy, PETIT, a(MUTED, 255), 0) + 10;
        }
        Trace.d(g, f, texte, GW - M, cy, PETIT_GRAS, a(couleur, 255), GW - M - x - 8);
    }

    /** L'objet (la vraie clé de HeraliZ Caisses, avec son modèle 3D) dessiné par le jeu, centré, penché comme la maquette. */
    private static void objet(GuiGraphics g, ItemStack st, float cx, float cy, float echelle) {
        if (st == null || st.isEmpty()) return;
        g.pose().pushPose();
        g.pose().translate(cx, cy, 100);
        g.pose().mulPose(Axis.ZP.rotationDegrees(28));
        g.pose().scale(echelle, echelle, echelle);
        g.renderItem(st, -8, -8);
        g.pose().popPose();
    }

    // ------------------------------------------------------------------ 4. Sur mesure
    private void surMesure(GuiGraphics g, Font f) {
        float gap = 9, cw = (GW - 2 * M - 2 * gap) / 3, ch = 180;
        for (int i = 0; i < SM.length; i++) {
            int col = SM_COULEUR[i];
            float x = M + i * (cw + gap);
            Trace.rect(g, x, HAUT, x + cw, HAUT + ch, a(GLASS, 200));
            Trace.image(g, tex("sur_mesure_" + SM[i]), x + 8, HAUT + 8, SM_IW, SM_IH, 1);
            Trace.gradV(g, x + 8, HAUT + 8 + SM_IH - 34, x + 8 + SM_IW, HAUT + 8 + SM_IH, a(GLASS, 0), a(GLASS, 200));
            Trace.bord(g, x + 8, HAUT + 8, SM_IW, SM_IH, 1 / 3F, a(col, 90));
            Trace.bord(g, x, HAUT, cw, ch, 1 / 3F, a(WHITE, 26));
            filetHaut(g, x, HAUT, cw, col);
            String vie = t("a_vie");
            float rw = Trace.largeur(f, vie, MINI) + 10;
            Trace.rect(g, x + 14, HAUT + 14, x + 14 + rw, HAUT + 24, a(col, 230));
            Trace.m(g, f, vie, x + 19, HAUT + 19, MINI, a(WHITE, 255), 0);
            float y = HAUT + 128;
            Trace.texte(g, f, Trace.tenir(f, t("sm." + SM[i] + ".nom"), NOM, cw - 20), x + 10, y, NOM, a(col, 255));
            List<String> ls = Trace.lignes(f, t("sm." + SM[i] + ".texte"), TEXTE, cw - 20);
            for (int k = 0; k < Math.min(2, ls.size()); k++) Trace.texte(g, f, ls.get(k), x + 10, y + 17 + k * 10, TEXTE, a(LIGHT, 255));
            Trace.texte(g, f, Trace.tenir(f, t("sur_devis"), DEVIS, cw - 20), x + 10, y + 41, DEVIS, a(MUTED, 255));
        }
        float y = HAUT + ch + 9, bh = BAS - y, x = M, w = GW - 2 * M;
        Trace.rect(g, x, y, x + w, y + bh, a(GLASS, 205));
        Trace.gradH(g, x, y, x + w * 0.6F, y + bh, a(DISCORD, 60), a(DISCORD, 0));
        Trace.bord(g, x, y, w, bh, 1 / 3F, a(DISCORD, 150));
        float lx = x + 24, ly = y + bh / 2;
        Trace.disque(g, lx, ly, 13, a(DISCORD, 255));
        Trace.image(g, TEX_DISCORD, lx - 13, ly - 13, 26, 26, 1);
        float tx = x + 46, bw = 158, bhh = 24, bx = x + w - 12 - bw, by = y + bh / 2 - bhh / 2;
        Trace.texte(g, f, Trace.tenir(f, t("discord.titre"), ONGLET, bx - tx - 10), tx, y + 12, ONGLET, a(WHITE, 255));
        for (int k = 0; k < 3; k++) {
            float cy = y + 30 + k * 10.5F;
            Trace.disque(g, tx + 3.5F, cy, 3.6F, a(DISCORD, 255));
            Trace.c(g, f, String.valueOf(k + 1), tx + 3.5F, cy, ETAPE, a(WHITE, 255), 0);
            Trace.m(g, f, t("discord.etape" + (k + 1)), tx + 11, cy, TEXTE, a(OPTION, 255), bx - tx - 21);
        }
        boolean hv = survol(bx, by, bw, bhh);
        Trace.gradH(g, bx, by, bx + bw, by + bhh, a(DISCORD, hv ? 240 : 210), a(DISCORD, hv ? 190 : 150));
        Trace.bord(g, bx, by, bw, bhh, 1 / 3F, a(mix(DISCORD, WHITE, 0.4F), 220));
        Trace.c(g, f, t("discord.bouton"), bx + bw / 2, by + bhh / 2 - 3.5F, BOUTON, a(WHITE, 255), bw - 10);
        Trace.c(g, f, "discord.gg/heraliz", bx + bw / 2, by + bhh / 2 + 6.5F, PETIT, a(mix(DISCORD, WHITE, 0.7F), 255), 0);
        zone(bx, by, bw, bhh, () -> lien(LIEN_DISCORD));
        pied(g, f, new String[][]{{"clic", "discord"}, {"echap", "fermer"}}, "sur_mesure");
    }
}
