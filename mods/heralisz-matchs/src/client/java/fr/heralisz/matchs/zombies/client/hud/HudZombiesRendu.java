package fr.heralisz.matchs.zombies.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.client.kit.Ui;
import fr.heralisz.matchs.moteur.client.HudMoteur;
import fr.heralisz.matchs.zombies.client.boisson.Boisson;
import fr.heralisz.matchs.zombies.jeu.api.HudZombies;
import fr.heralisz.matchs.zombies.jeu.api.TypeAtout;
import fr.heralisz.matchs.zombies.jeu.api.TypeBonus;
import fr.heralisz.matchs.zombies.jeu.api.ZombiesApi;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix4f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * HUD du mode zombies facon Black Ops 6 (D-049, maquette validee : {@code work/zombies_v3/hud/LISEZMOI.md}), lu dans
 * {@link HudZombies} (etat) et {@link EffetsHud} (evenements). Positions en unites d'interface, calculees d'apres la
 * taille de l'ecran (verifie aux echelles 2, 3 et 4 ; « auto » en 1080p = 4, le cas le plus serre) :
 * <ul>
 *     <li>en haut a droite : numero de manche a la craie rouge (batons de 1 a 5, chiffres ensuite), hauteur 11 % de
 *     l'ecran ; le nouveau baton se trace a la craie blanche, les chiffres arrivent en grand puis virent au rouge ;</li>
 *     <li>en haut a gauche : objectif (glisse depuis la gauche, coche verte et texte barre quand il est rempli), lore
 *     d'intro dessous ;</li>
 *     <li>en haut au centre : boussole (±90°, losanges rouges des zombies proches, losange dore de la cible de l'objectif
 *     avec sa distance (0.2.3), nom du lieu dessous), cachee pendant
 *     une barre de boss ; alerte « coequipier a terre » juste dessous ;</li>
 *     <li>a droite, au milieu : bonus actifs (icone, secondes, jauge fine) ;</li>
 *     <li>en bas a gauche : carte du joueur (case kit, points en gros, plaques et materiaux, barre de vie seulement
 *     apres un coup, PV « facon CoD » = vie x 5), coequipiers au-dessus ;</li>
 *     <li>en bas au centre : atouts, au-dessus de la barre d'objets ;</li>
 *     <li>au viseur : « +60 » gagnes a droite, invite d'achat dessous (inchangee) ;</li>
 *     <li>au-dessus du HUD de TaCZ : nom de l'arme tenue et losanges dores du niveau Pack-a-Punch. Le coin du HUD de
 *     TaCZ (de w-117 a w-4, de h-46 a h-22) reste vide.</li>
 * </ul>
 * Le fil des eliminations et les invitations de groupe du moteur descendent sous la manche ({@link HudMoteur#hautFil}),
 * le chat de Minecraft remonte au-dessus de la carte ({@link #hautBloc}) et les icones d'effets sont masquees
 * ({@code ZbClientForge}). Charte HeraliZ : DM Sans, capitales espacees. Textes en cache ({@link Libelle}) : aucune
 * allocation par image en regime etabli.
 */
public final class HudZombiesRendu implements IGuiOverlay {
    public static final HudZombiesRendu INSTANCE = new HudZombiesRendu();

    private static final ResourceLocation CHIFFRES = HeraliszMatchs.id("textures/gui/zombies/v2/craie_chiffres.png");
    private static final ResourceLocation CHIFFRES_B = HeraliszMatchs.id("textures/gui/zombies/v2/craie_chiffres_blanc.png");
    private static final ResourceLocation BATONS = HeraliszMatchs.id("textures/gui/zombies/v2/craie_batons.png");
    private static final ResourceLocation BATONS_B = HeraliszMatchs.id("textures/gui/zombies/v2/craie_batons_blanc.png");
    private static final ResourceLocation MATERIAUX = HeraliszMatchs.id("textures/gui/zombies/v2/materiaux.png");
    private static final ResourceLocation KIT = HeraliszMatchs.id("textures/gui/zombies/v2/kit.png");
    private static final ResourceLocation PLAQUE = HeraliszMatchs.id("textures/gui/zombies/v2/plaque.png");
    private static final ResourceLocation[] ICONE_ATOUT = new ResourceLocation[TypeAtout.values().length];
    private static final ResourceLocation[] ICONE_BONUS = new ResourceLocation[TypeBonus.values().length];

    // ------------------------------------------------------------------ regles de placement (unites d'interface)
    /** Marges gauche / droite et haut ; barre d'objets (DayZ) ; hauteur du HUD de TaCZ au-dessus de la barre. */
    private static final int MARGE = 8, HAUT = 6, BARRE_OBJETS = 22, TACZ_H = 46;
    private static final int ROUGE_VIE = 0xC8281E, ROUGE_VIE_CLAIR = 0xFF6A55, SOMBRE = 0x0B0C0E, BLEU_PLAQUE = 0x9EC9FF;
    /** PV affiches « facon CoD » : 20 points de vie = 100 PV, 50 (Blindage) = 250 PV. */
    private static final int PV_PAR_POINT = 5;
    /** Rayon (blocs, a l'horizontale) des zombies montres sur la boussole ; rayon de la barre de boss (BossHud). */
    private static final double RAYON_BOUSSOLE = 24, RAYON_BOSS = 72;
    /** Copies des valeurs des enums (values() alloue a chaque appel). */
    private static final TypeAtout[] ATOUTS = TypeAtout.values();
    private static final TypeBonus[] BONUS = TypeBonus.values();
    private static final int[] CHIFFRES_TAMPON = new int[6];
    private static final String[] CAPS_FR = {"N", "NE", "E", "SE", "S", "SO", "O", "NO"};
    private static final String[] CAPS_EN = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

    static {
        for (TypeAtout a : TypeAtout.values()) ICONE_ATOUT[a.ordinal()] = HeraliszMatchs.id("textures/gui/zombies/v2/atout_" + a.id() + ".png");
        for (TypeBonus b : TypeBonus.values()) ICONE_BONUS[b.ordinal()] = HeraliszMatchs.id("textures/gui/zombies/v2/bonus_" + b.id() + ".png");
        // fil des eliminations et invitations de groupe du moteur : sous le numero de manche pendant une partie
        HudMoteur.hautFil = h -> HudZombies.actif() ? HAUT + hauteurManche(h) + 8 : -1;
    }

    /** Hauteur du numero de manche : 11 % de la hauteur de l'ecran, bornee 26..42 (30 a l'echelle 4 en 1080p). */
    public static int hauteurManche(int h) {
        return Mth.clamp(Math.round(h * 0.11F), 26, 42);
    }

    /** Largeur de la carte du joueur : 128, sans jamais toucher la barre d'objets (w/2 - 112). */
    public static int largeurCarte(int w) {
        return Math.min(128, w / 2 - 112);
    }

    /** Haut du bloc du joueur (carte + coequipiers au-dessus), pour remonter le chat de Minecraft au-dessus. */
    public static int hautBloc(int h, int coequipiers) {
        return coequipiers > 0 ? h - 41 - 12 * coequipiers : h - 37;
    }

    // ------------------------------------------------------------------ boussole (donnees du tick client)
    /** Interface des boss HeraliZ : N-07 (heralisz_n07 1.1.0) d'abord, ancien nom (heralisz_bosses) ensuite. */
    @Nullable
    private static final Class<?> BOSS_INFO = classe("fr.heralisz.n07.api.BossInfo") != null ? classe("fr.heralisz.n07.api.BossInfo")
            : classe("fr.heralisz.bosses.api.BossInfo");
    /** BossInfo.dormant() : un boss endormi (N-07 dans sa cuve avant l'intro) n'a pas de barre, la boussole reste. */
    @Nullable
    private static final java.lang.reflect.Method BOSS_DORMANT = methode(BOSS_INFO, "dormant");
    private static final float[] ENNEMIS = new float[16];
    private static int nbEnnemis;
    private static boolean bossProche;
    private static long bossVanillaMs = -100000;

    @Nullable
    private static Class<?> classe(String nom) {
        try {
            return Class.forName(nom);
        } catch (ClassNotFoundException | LinkageError ex) {
            return null;
        }
    }

    @Nullable
    private static java.lang.reflect.Method methode(@Nullable Class<?> c, String nom) {
        if (c == null) return null;
        try {
            return c.getMethod(nom);
        } catch (NoSuchMethodException | LinkageError ex) {
            return null;
        }
    }

    /** Vrai si l'entite est un boss HeraliZ eveille (sa barre de vie occupe le haut de l'ecran). */
    private static boolean bossEveille(Entity en) {
        if (BOSS_INFO == null || !BOSS_INFO.isInstance(en)) return false;
        if (BOSS_DORMANT == null) return true;
        try {
            return !Boolean.TRUE.equals(BOSS_DORMANT.invoke(en));
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return true;
        }
    }

    /**
     * Toutes les 5 ticks (ZbClientForge) : caps des zombies proches (vus par le client, aucun paquet) et presence d'un
     * boss HeraliZ eveille (N-07) a portee de sa barre (la boussole se cache alors : la barre prend le haut de l'ecran).
     */
    public static void tickBoussole(Minecraft mc) {
        nbEnnemis = 0;
        bossProche = false;
        if (!HudZombies.actif() || mc.level == null || mc.player == null) return;
        double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
        double r2 = RAYON_BOUSSOLE * RAYON_BOUSSOLE, b2 = RAYON_BOSS * RAYON_BOSS;
        for (Entity en : mc.level.entitiesForRendering()) {
            if (!(en instanceof LivingEntity le) || en instanceof Player || !le.isAlive()) continue;
            double dx = en.getX() - px, dz = en.getZ() - pz, dy = en.getY() - py;
            double d2 = dx * dx + dz * dz;
            if (!bossProche && d2 < b2 && Math.abs(dy) < RAYON_BOSS && bossEveille(en)) bossProche = true;
            if (!(en instanceof Enemy) || d2 > r2 || Math.abs(dy) > 10 || nbEnnemis >= ENNEMIS.length) continue;
            // cap : 0 = nord (-Z), 90 = est (+X)
            ENNEMIS[nbEnnemis++] = (float) Math.toDegrees(Math.atan2(dx, -dz));
        }
    }

    /** Une barre de boss vanilla est dessinee (CustomizeGuiOverlayEvent.BossEventProgress). */
    public static void barreBossVanilla() {
        bossVanillaMs = Util.getMillis();
    }

    // ------------------------------------------------------------------ caches de textes
    private final Libelle[] coeqNom = new Libelle[4], coeqPts = new Libelle[4], coeqTerre = new Libelle[4];
    private final int[] coeqPtsVal = new int[4], coeqSecVal = new int[4];
    private final Libelle[] secBonus = new Libelle[TypeBonus.values().length];
    private final int[] secBonusVal = new int[TypeBonus.values().length], bonusMax = new int[TypeBonus.values().length];
    private final Libelle inviteTexte = new Libelle(), inviteTouche = new Libelle(), invitePrix = new Libelle(), point = new Libelle();
    private final Libelle refus = new Libelle(), achat = new Libelle();
    private final Libelle aTerre = new Libelle(), aTerreSous = new Libelle(), mort = new Libelle(), mortSous = new Libelle(),
            aTerreSec = new Libelle(), reanimation = new Libelle();
    private final Libelle banTitre = new Libelle(), banSous = new Libelle();
    private final Libelle[] coeq = new Libelle[4];
    private final int[] coeqSec = new int[4];
    private final String[] coeqNomVu = new String[4];
    private final Libelle objectifTitre = new Libelle(), objectifAtteint = new Libelle(), boiteTexte = new Libelle();
    // carte du joueur
    private final Libelle moiNom = new Libelle(), moiTerre = new Libelle(), moiPts = new Libelle(), pv = new Libelle(), pvUnite = new Libelle();
    private final Libelle kits = new Libelle(), materiaux = new Libelle(), plaques = new Libelle(), deltaMat = new Libelle();
    private int moiPtsVal = Integer.MIN_VALUE, moiTerreSec = -1, pvVal = -1, kitsVal = -1, materiauxVal = -1, plaquesVal = -1;
    private long deltaMatVu = Long.MIN_VALUE;
    // boussole
    private final Libelle[] caps = new Libelle[8];
    private final Libelle lieu = new Libelle();
    /** Distance du repere d'objectif sur la boussole (0.2.3), recalculee quand le nombre de metres change. */
    private final Libelle repereDist = new Libelle();
    private int repereDistVu = Integer.MIN_VALUE;
    // arme tenue (nom + Pack-a-Punch)
    private final Libelle armeNom = new Libelle();
    private ItemStack armeVue = ItemStack.EMPTY;
    private boolean armeTacz;
    private int armePap;
    private long versionInvite = -1;
    private long inviteDepuisMs;
    private boolean inviteVisible;
    private int refusVu = -1, achatVu = -1, banVu = -1, boiteVu = -1;
    private int aTerreTotal = 30, aTerreSecVu = -1, moiTerreTotal = 30;
    private boolean etaitActif;
    private long derniereImage;
    // objectif : lignes coupees (recalculees seulement quand le texte ou la largeur changent)
    private String objectifSource = "";
    @Nullable
    private Component objectifVu;
    private int objectifMaxW = -1;
    private List<FormattedCharSequence> objectifLignes = List.of();
    private int[] objectifLargeurs = new int[0];
    private int objectifLargeur;
    private String ancienSource = "";
    private List<FormattedCharSequence> ancienLignes = List.of();
    private int[] ancienLargeurs = new int[0];
    private int ancienLargeur;
    // intro : lignes coupees, et partie deja ecrite de chaque ligne (reconstruite seulement quand elle s'allonge)
    private long introVu = -1;
    private int introLargeurMax = -1, introLargeur;
    private final List<String> introLignes = new ArrayList<>();
    private Component[] introVisible = new Component[0];
    private int[] introK = new int[0], introW = new int[0];
    // vie : trainee des degats, instant du dernier coup, barre montree (cachee a pleine vie), instant du retour a pleine vie
    private float vieTrainee = -1;
    private float vieAvant = -1;
    private long coupMs = -100000;
    private boolean vieMontree;
    private long viePleineMs = -1;

    private HudZombiesRendu() {
        for (int i = 0; i < 4; i++) {
            coeqNom[i] = new Libelle();
            coeqPts[i] = new Libelle();
            coeqTerre[i] = new Libelle();
            coeq[i] = new Libelle();
            coeqPtsVal[i] = Integer.MIN_VALUE;
            coeqSecVal[i] = -1;
            coeqSec[i] = -1;
        }
        for (int i = 0; i < secBonus.length; i++) {
            secBonus[i] = new Libelle();
            secBonusVal[i] = -1;
        }
        for (int i = 0; i < caps.length; i++) caps[i] = new Libelle();
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partiel, int w, int h) {
        if (!HudZombies.actif()) {
            if (etaitActif) {
                EffetsHud.INSTANCE.vider();
                etaitActif = false;
                vieTrainee = -1;
                vieAvant = -1;
                vieMontree = false;
                viePleineMs = -1;
            }
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) return;
        // cinematique du boss N-07 a l'ecran (intro, phases, mort, outro) : aucun HUD BO6 par-dessus le cadre
        if (fr.heralisz.matchs.zombies.compat.N07Client.cinematique()) return;
        if (!etaitActif) {
            etaitActif = true;
            EffetsHud.INSTANCE.mancheApres = HudZombies.manche();
        }
        Font font = mc.font;
        long now = Util.getMillis();
        float dtS = Mth.clamp((now - derniereImage) / 1000F, 0, 0.1F);
        derniereImage = now;
        UUID moi = mc.player.getUUID();
        HudZombies.Joueur jm = HudZombies.joueur(moi);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        int hm = hauteurManche(h);
        voile(g, w, h, jm, now);
        carte(g, font, w, h, moi, jm, now, dtS);
        atouts(g, jm, now, w, h);
        manche(g, now, w, hm);
        int basObjectif = objectif(g, font, w, now);
        intro(g, font, now, MARGE, basObjectif + 8, w);
        boussole(g, font, w, partiel, now);
        coequipiersATerre(g, font, w, moi);
        bonus(g, font, w, h, HAUT + hm + 8, now);
        nomArme(g, font, w, h, mc);
        popups(g, w, h, moi, now, mc.font);
        invite(g, font, w, h, now);
        aTerre(g, font, w, h, jm, now);
        banniere(g, font, w, h, now);
        teleport(g, w, h, now);
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }

    /** v3 : eclair bleu-blanc a l'arrivee d'une teleportation (0,9 s : blanc franc puis voile bleu qui s'efface). */
    private static void teleport(GuiGraphics g, int w, int h, long now) {
        long dt = now - EffetsHud.INSTANCE.teleportMs;
        if (dt < 0 || dt >= 900) return;
        float k = dt < 120 ? 1 : 1 - (dt - 120) / 780F;
        int blanc = (int) (230 * k * k), bleu = (int) (150 * k);
        g.fill(0, 0, w, h, (bleu << 24) | 0x7FB8FF);
        if (blanc > 2) g.fill(0, 0, w, h, (blanc << 24) | 0xF4FAFF);
    }

    // ================================================================== outils de dessin
    /** Degrade horizontal (couleurs ARGB a gauche et a droite), coordonnees flottantes. */
    private static void degradeH(GuiGraphics g, float x0, float y0, float x1, float y1, int gauche, int droite) {
        if (x1 <= x0 || y1 <= y0) return;
        Matrix4f m = g.pose().last().pose();
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
        sommet(vc, m, x0, y0, gauche);
        sommet(vc, m, x0, y1, gauche);
        sommet(vc, m, x1, y1, droite);
        sommet(vc, m, x1, y0, droite);
        g.flush();
    }

    private static void sommet(VertexConsumer vc, Matrix4f m, float x, float y, int argb) {
        vc.vertex(m, x, y, 0).color((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, argb >>> 24).endVertex();
    }

    /** Voile sombre du coin bas gauche (degrade radial approche par une grille), sans cadre : lisible sur la neige. */
    private static void voileCoin(GuiGraphics g, int x0, int y0, int x1, int y1, int alpha) {
        if (x1 <= x0 || y1 <= y0) return;
        Matrix4f m = g.pose().last().pose();
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
        int nx = 8, ny = 6;
        for (int i = 0; i < nx; i++) {
            for (int j = 0; j < ny; j++) {
                float u0 = i / (float) nx, u1 = (i + 1) / (float) nx, v0 = j / (float) ny, v1 = (j + 1) / (float) ny;
                float xa = x0 + (x1 - x0) * u0, xb = x0 + (x1 - x0) * u1, ya = y0 + (y1 - y0) * v0, yb = y0 + (y1 - y0) * v1;
                sommet(vc, m, xa, ya, Ui.argb(0, alphaCoin(u0, v0, alpha)));
                sommet(vc, m, xa, yb, Ui.argb(0, alphaCoin(u0, v1, alpha)));
                sommet(vc, m, xb, yb, Ui.argb(0, alphaCoin(u1, v1, alpha)));
                sommet(vc, m, xb, ya, Ui.argb(0, alphaCoin(u1, v0, alpha)));
            }
        }
        g.flush();
    }

    private static int alphaCoin(float u, float v, int alpha) {
        float t = Mth.clamp(1 - Mth.sqrt(u * u + (1 - v) * (1 - v)), 0, 1);
        return (int) (alpha * t * t * (3 - 2 * t));
    }

    /** Petit losange plein (boussole, niveau Pack-a-Punch). */
    private static void losange(GuiGraphics g, float cx, float cy, float r, int argb) {
        Matrix4f m = g.pose().last().pose();
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
        sommet(vc, m, cx, cy - r, argb);
        sommet(vc, m, cx - r, cy, argb);
        sommet(vc, m, cx, cy + r, argb);
        sommet(vc, m, cx + r, cy, argb);
        g.flush();
    }

    /** Texte avec une ombre douce (decalee de 0,75), alignement 0 = gauche, 1 = centre, 2 = droite. */
    private static void texte(GuiGraphics g, Font font, Libelle l, float x, float y, int argb, int ombre, int align) {
        float xx = align == 1 ? x - l.largeur() / 2F : align == 2 ? x - l.largeur() : x;
        if (ombre > 3) l.dessiner(g, font, xx + 0.75F, y + 0.75F, Ui.argb(0, ombre));
        l.dessiner(g, font, xx, y, argb);
    }

    // ================================================================== manche (craie rouge, en haut a droite)
    /** Largeur du nombre a la craie (meme calcul que {@link #craie}). */
    private static int largeurCraie(int n, int haut) {
        if (n <= 0) return 0;
        if (n <= 5) return Math.round(haut * 192F / 144F);
        int cw = Math.round(haut * 96F / 144F);
        int pas = Math.round(cw * 0.8F);
        int nb = 0;
        for (int v = Math.min(n, 999999); v > 0; v /= 10) nb++;
        return nb * pas + (cw - pas);
    }

    /** Dessine le nombre a la craie (batons jusqu'a 5) a partir du bord gauche ; rend la largeur. */
    private static int craie(GuiGraphics g, int x, int y, int haut, int n, boolean blanc, float alpha) {
        if (n <= 0 || alpha <= 0.01F) return 0;
        RenderSystem.setShaderColor(1, 1, 1, Mth.clamp(alpha, 0, 1));
        if (n <= 5) {
            int lw = Math.round(haut * 192F / 144F);
            g.blit(blanc ? BATONS_B : BATONS, x, y, lw, haut, (n - 1) * 192, 0, 192, 144, 960, 144);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            return lw;
        }
        int cw = Math.round(haut * 96F / 144F);
        int pas = Math.round(cw * 0.8F);
        int nb = 0;
        for (int v = Math.min(n, 999999); v > 0 && nb < CHIFFRES_TAMPON.length; v /= 10) CHIFFRES_TAMPON[nb++] = v % 10;
        int cx = x;
        for (int i = nb - 1; i >= 0; i--) {
            g.blit(blanc ? CHIFFRES_B : CHIFFRES, cx, y, cw, haut, CHIFFRES_TAMPON[i] * 96, 0, 96, 144, 960, 144);
            cx += pas;
        }
        RenderSystem.setShaderColor(1, 1, 1, 1);
        return cx - x + (cw - pas);
    }

    /**
     * Nombre a la craie colle au bord droit {@code xr} : rouge, puis la meme craie blanche par-dessus ({@code blanc}
     * 0..1). {@code revele} &lt; 1 (batons) : le dernier baton se trace de gauche a droite (le compte precedent est deja
     * la). {@code echelle} : agrandi depuis le coin haut droit.
     */
    private static void craieDroite(GuiGraphics g, int xr, int y, int hm, int n, float alpha, float blanc, float revele, float echelle) {
        if (n <= 0 || alpha <= 0.01F) return;
        int x = xr - largeurCraie(n, hm);
        boolean zoom = Math.abs(echelle - 1) > 0.001F;
        if (zoom) {
            g.pose().pushPose();
            g.pose().translate(xr, y, 0);
            g.pose().scale(echelle, echelle, 1);
            g.pose().translate(-xr, -y, 0);
        }
        for (int couche = 0; couche < 2; couche++) {
            boolean b = couche == 1;
            float a = b ? alpha * blanc : alpha;
            if (a <= 0.01F) continue;
            if (n <= 5 && revele < 0.999F && !zoom) {
                if (n > 1) craie(g, x, y, hm, n - 1, b, a);
                int lw = Math.round(hm * 192F / 144F);
                int coupe = Math.round(lw * Mth.clamp(revele, 0, 1));
                if (coupe > 0) {
                    g.enableScissor(x, y, x + coupe, y + hm);
                    craie(g, x, y, hm, n, b, a);
                    g.disableScissor();
                }
            } else {
                craie(g, x, y, hm, n, b, a);
            }
        }
        if (zoom) g.pose().popPose();
    }

    /**
     * Transition de manche (maquette hud_changement_manche.png, 3,4 s) :
     * <ul>
     *     <li>batons (manches 1 a 5) : le compte clignote blanc / rouge, puis le nouveau baton se trace d'un trait a la
     *     craie blanche, puis le blanc vire au rouge ;</li>
     *     <li>chiffres (6 et plus) : l'ancien nombre clignote et s'efface, le nouveau arrive en blanc et en grand, se
     *     pose, puis vire au rouge.</li>
     * </ul>
     * Fin de manche : le nombre pulse en blanc. Manche speciale (Rodeurs) : il pulse en rouge.
     */
    private void manche(GuiGraphics g, long now, int w, int hm) {
        EffetsHud e = EffetsHud.INSTANCE;
        long dt = now - e.mancheDebutMs;
        int n = HudZombies.manche();
        int xr = w - MARGE, y = HAUT;
        if (e.mancheDebut && dt >= 0 && dt < 3400) {
            int avant = e.mancheAvant, apres = e.mancheApres;
            boolean flash = (dt / 140) % 2 == 0;
            if (apres <= 5) {
                if (dt < 900) {
                    craieDroite(g, xr, y, hm, avant, 1, flash ? 1 : 0.35F, 1, 1);
                } else if (dt < 1800) {
                    float rev = Mth.clamp((dt - 1000) / 500F, 0, 1);
                    float bl = Math.min(1, 0.6F + 0.4F * Mth.clamp((dt - 900) / 600F, 0, 1));
                    craieDroite(g, xr, y, hm, apres, 1, bl, rev, 1);
                } else {
                    craieDroite(g, xr, y, hm, apres, 1, 1 - (dt - 1800) / 1600F, 1, 1);
                }
            } else {
                if (dt < 300) {
                    craieDroite(g, xr, y, hm, avant, 1, flash ? 1 : 0.35F, 1, 1);
                } else if (dt < 900) {
                    craieDroite(g, xr, y, hm, avant, 1 - (dt - 300) / 600F, flash ? 0.7F : 0.2F, 1, 1);
                } else if (dt < 1800) {
                    float a = Mth.clamp((dt - 900) / 220F, 0, 1);
                    float s = 1 + 0.25F * (1 - Mth.clamp((dt - 1000) / 700F, 0, 1));
                    craieDroite(g, xr, y, hm, apres, a, 1, 1, s);
                } else {
                    craieDroite(g, xr, y, hm, apres, 1, 1 - (dt - 1800) / 1600F, 1, 1);
                }
            }
            return;
        }
        if (!e.mancheDebut && dt >= 0 && dt < 4500) {
            float pulse = 0.5F + 0.5F * Mth.sin(dt / 160F);
            craieDroite(g, xr, y, hm, n, 1, pulse * (1 - dt / 4500F), 1, 1);
            return;
        }
        craieDroite(g, xr, y, hm, n, HudZombies.speciale() ? 0.85F + 0.15F * Mth.sin(now / 200F) : 1, 0, 1, 1);
    }

    // ================================================================== atouts (bas centre, au-dessus de la barre d'objets)
    private void atouts(GuiGraphics g, HudZombies.Joueur jm, long now, int w, int h) {
        if (jm == null || jm.atouts() == 0) return;
        int taille = 16, pas = 19;
        int nb = 0;
        for (TypeAtout a : ATOUTS) {
            // pendant la boisson, l'atout n'est pas encore « bu »
            if ((jm.atouts() & a.bit()) != 0 && now - EffetsHud.INSTANCE.atoutMs[a.ordinal()] >= 0) nb++;
        }
        if (nb == 0) return;
        int x0 = w / 2 - (nb * pas - (pas - taille)) / 2;
        int y = h - BARRE_OBJETS - 6 - taille;
        int k = 0;
        for (TypeAtout a : ATOUTS) {
            if ((jm.atouts() & a.bit()) == 0) continue;
            long da = now - EffetsHud.INSTANCE.atoutMs[a.ordinal()];
            if (da < 0) continue;
            float s = da < 700 ? 1 + 0.5F * Mth.sin(da / 700F * Mth.PI) * (1 - da / 700F) : 1;
            float al = Mth.clamp(da / 150F, 0, 1);
            g.pose().pushPose();
            g.pose().translate(x0 + k * pas + taille / 2F, y + taille / 2F, 0);
            g.pose().scale(s, s, 1);
            Ui.soft(g, -taille / 2 - 1, -taille / 2 - 1, taille + 2, taille + 2, Ui.argb(SOMBRE, (int) (150 * al)));
            RenderSystem.setShaderColor(1, 1, 1, al);
            g.blit(ICONE_ATOUT[a.ordinal()], -taille / 2, -taille / 2, taille, taille, 0, 0, 64, 64, 64, 64);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            g.pose().popPose();
            k++;
        }
    }

    /**
     * Horodatage de {@link HudZombies} (horloge murale, {@code System.currentTimeMillis}) ramene sur l'horloge des
     * effets du HUD ({@code Util.getMillis}, monotone) : les deux ne partagent pas la meme origine.
     */
    private static long versHorloge(long murMs, long now) {
        return murMs <= 0 ? Long.MIN_VALUE / 4 : now - (System.currentTimeMillis() - murMs);
    }

    // ================================================================== lore d'intro (machine a ecrire, sous l'objectif)
    private void intro(GuiGraphics g, Font font, long now, int x, int yHaut, int w) {
        List<Component> lignes = HudZombies.intro();
        if (lignes.isEmpty()) return;
        long depuis = HudZombies.introDepuisMs();
        int maxW = Mth.clamp(Math.round(w * 0.3F), 130, 230);
        if (introVu != depuis || introLargeurMax != maxW) {
            introVu = depuis;
            introLargeurMax = maxW;
            introLignes.clear();
            for (Component c : lignes) {
                for (var morceau : font.getSplitter().splitLines(Component.literal(c.getString()).withStyle(st -> st.withFont(Ui.FONT)), maxW, Style.EMPTY)) {
                    introLignes.add(morceau.getString());
                }
            }
            int n = introLignes.size();
            introVisible = new Component[n];
            introK = new int[n];
            introW = new int[n];
            introLargeur = 0;
            for (String s : introLignes) introLargeur = Math.max(introLargeur, Math.min(maxW, font.width(Component.literal(s).withStyle(st -> st.withFont(Ui.FONT)))));
        }
        long dt = now - versHorloge(depuis, now);
        int total = 0;
        for (int i = 0; i < introLignes.size(); i++) total += introLignes.get(i).length();
        long ecrit = 35L * total;
        long fin = ecrit + 5500;
        if (dt < 0 || dt > fin + 1200) return;
        float a = dt > fin ? 1 - (dt - fin) / 1200F : 1;
        int nCar = (int) (dt / 35);
        int lh = 11;
        int y = yHaut + 4;
        int n = introLignes.size();
        Ui.soft(g, x - 4, y - 4, introLargeur + 12, n * lh + 6, Ui.argb(SOMBRE, (int) (120 * a)));
        g.fill(x - 4, y - 4, x - 3, y + n * lh + 2, Ui.argb(ROUGE_VIE, (int) (220 * a)));
        for (int i = 0; i < n; i++) {
            String s = introLignes.get(i);
            int k = Math.min(s.length(), Math.max(0, nCar));
            nCar -= s.length();
            if (k > 0) {
                if (introK[i] != k || introVisible[i] == null) {
                    introK[i] = k;
                    introVisible[i] = Component.literal(s.substring(0, k)).withStyle(st -> st.withFont(Ui.FONT));
                    introW[i] = font.width(introVisible[i]);
                }
                g.drawString(font, introVisible[i], x + 2, y, Ui.argb(0xE6E1D6, (int) (240 * a)), false);
                if (k < s.length() && (now / 300) % 2 == 0) {
                    int cx = x + 2 + introW[i];
                    g.fill(cx + 1, y, cx + 2, y + 9, Ui.argb(0xE6E1D6, (int) (200 * a)));
                }
            }
            y += lh;
        }
    }

    // ================================================================== carte du joueur (bas gauche) et coequipiers
    private void carte(GuiGraphics g, Font font, int w, int h, UUID moi, @Nullable HudZombies.Joueur jm, long now, float dtS) {
        if (jm == null) return;
        List<HudZombies.Joueur> js = HudZombies.joueurs();
        int autres = 0;
        for (int i = 0; i < js.size() && i < 4; i++) if (!js.get(i).id().equals(moi)) autres++;
        autres = Math.min(3, autres);
        int lc = largeurCarte(w);
        int tEq = 22;
        int x0 = MARGE + tEq + 5, xr = MARGE + lc, yb = h - MARGE;
        // voile sombre du coin, sans cadre
        int hautBloc = yb - 34 - (autres > 0 ? autres * 12 + 4 : 0);
        voileCoin(g, 0, hautBloc - 30, xr + 70, h, 125);
        // case d'equipement : kit d'auto-reanimation
        int ye = yb - tEq;
        Ui.soft(g, MARGE, ye, tEq, tEq, Ui.argb(Ui.VERRE, 175));
        Ui.softOutline(g, MARGE + 1, ye + 1, tEq - 2, tEq - 2, Ui.argb(0xFFFFFF, 30));
        int nk = jm.autoReanimations();
        RenderSystem.setShaderColor(0.49F, 0.89F, 0.63F, nk > 0 ? 1 : 0.25F);
        g.blit(KIT, MARGE + 5, ye + 4, 12, 12, 0, 0, 32, 32, 32, 32);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        if (kitsVal != nk) {
            kitsVal = nk;
            kits.definir(font, Integer.toString(nk), Ui.BOLD, 0);
        }
        g.pose().pushPose();
        g.pose().translate(MARGE + tEq - 2.5F, ye + tEq - 9.5F, 0);
        g.pose().scale(0.8F, 0.8F, 1);
        kits.droite(g, font, 0, 0, Ui.argb(nk > 0 ? Ui.TEXT : Ui.FAINT, 255));
        g.pose().popPose();
        // points en gros, couleur du joueur
        int c = jm.couleur() & 0xFFFFFF;
        if (moiPtsVal != jm.points()) {
            moiPtsVal = jm.points();
            moiPts.definir(font, Ui.nombre(jm.points()), Ui.BOLD, 0);
        }
        g.pose().pushPose();
        g.pose().translate(x0, yb - 12.5F, 0);
        g.pose().scale(1.5F, 1.5F, 1);
        texte(g, font, moiPts, 0, 0, Ui.argb(Ui.mix(c, 0xFFFFFF, 0.55F), 255), 90, 0);
        g.pose().popPose();
        ressources(g, font, xr, yb, jm, now);
        // barre de vie (cachee a pleine vie) ou compte a rebours rouge a terre
        int yv = yb - 19;
        float va = vie(g, x0, xr, yv, jm, now, dtS);
        // ligne du pseudo (+ PV a droite quand la barre est la)
        int yn = yv - 10;
        g.fill(x0, yn + 2, x0 + 4, yn + 6, Ui.argb(c, 255));
        moiNom.definir(font, jm.nom(), Ui.SMALL, 0);
        if (jm.etat() == HudZombies.A_TERRE) {
            if (moiTerreSec != jm.secondes()) {
                moiTerreSec = jm.secondes();
                moiTerre.definir(font, jm.nom() + "  ·  " + Component.translatable("zombies.hud.a_terre_sec", jm.secondes()).getString(), Ui.SMALL, 0);
            }
            texte(g, font, moiTerre, x0 + 7, yn, Ui.argb(Ui.DANGER, 255), 80, 0);
        } else {
            texte(g, font, moiNom, x0 + 7, yn, Ui.argb(Ui.mix(Ui.MUTED, 0xFFFFFF, 0.35F), 255), 80, 0);
            if (va > 0.01F && jm.etat() == HudZombies.DEBOUT) {
                var p = Minecraft.getInstance().player;
                int valeur = p == null ? 0 : Mth.ceil(Math.max(0, p.getHealth()) * PV_PAR_POINT);
                if (pvVal != valeur) {
                    pvVal = valeur;
                    pv.definir(font, Integer.toString(valeur), Ui.BOLD, 0);
                }
                if (pvUnite.vide()) pvUnite.definir(font, Component.translatable("zombies.hud.pv").getString(), Ui.SMALL, 0);
                texte(g, font, pvUnite, xr, yn + 0.5F, Ui.argb(Ui.MUTED, (int) (230 * va)), 0, 2);
                texte(g, font, pv, xr - pvUnite.largeur() - 2, yn - 0.5F, Ui.argb(Ui.mix(ROUGE_VIE_CLAIR, 0xFFFFFF, 0.35F), (int) (255 * va)),
                        (int) (90 * va), 2);
            }
        }
        // coequipiers au-dessus, plus petits
        float y = yn - 5;
        int k = 0;
        for (int i = 0; i < js.size() && k < 3; i++) {
            HudZombies.Joueur j = js.get(i);
            if (j.id().equals(moi)) continue;
            y -= 11;
            coequipier(g, font, k, j, x0, xr, y);
            y -= 1;
            k++;
        }
    }

    /** Une ligne de coequipier : couleur, pseudo, points ; « A TERRE 23 s » en rouge ; pseudo barre s'il est mort. */
    private void coequipier(GuiGraphics g, Font font, int k, HudZombies.Joueur j, int x0, int xr, float y) {
        int cj = j.couleur() & 0xFFFFFF;
        boolean mortJ = j.etat() == HudZombies.MORT;
        float al = mortJ ? 0.45F : 1;
        g.fill(x0, (int) (y + 1.5F), x0 + 2, (int) (y + 8.5F), Ui.argb(cj, (int) (255 * al)));
        coeqNom[k].definir(font, j.nom(), Ui.SMALL, 0);
        if (j.etat() == HudZombies.A_TERRE) {
            texte(g, font, coeqNom[k], x0 + 5, y, Ui.argb(Ui.mix(Ui.DANGER, 0xFFFFFF, 0.2F), 255), 80, 0);
            if (coeqSecVal[k] != j.secondes()) {
                coeqSecVal[k] = j.secondes();
                coeqTerre[k].definir(font, Component.translatable("zombies.hud.a_terre_sec", j.secondes()).getString(), Ui.SMALL, 0.4F);
            }
            g.pose().pushPose();
            g.pose().translate(x0 + 5 + coeqNom[k].largeur() + 4, y + 0.5F, 0);
            g.pose().scale(0.85F, 0.85F, 1);
            coeqTerre[k].dessiner(g, font, 0, 0, Ui.argb(Ui.DANGER, 255));
            g.pose().popPose();
        } else {
            texte(g, font, coeqNom[k], x0 + 5, y, Ui.argb(Ui.mix(Ui.MUTED, 0xFFFFFF, 0.2F), (int) (255 * al)), (int) (70 * al), 0);
            if (mortJ) g.fill(x0 + 5, (int) y + 4, (int) (x0 + 5 + coeqNom[k].largeur()), (int) y + 5, Ui.argb(Ui.MUTED, 200));
        }
        if (coeqPtsVal[k] != j.points()) {
            coeqPtsVal[k] = j.points();
            coeqPts[k].definir(font, Ui.nombre(j.points()), Ui.BOLD, 0);
        }
        texte(g, font, coeqPts[k], xr, y - 0.5F, Ui.argb(Ui.mix(cj, 0xFFFFFF, 0.6F), (int) (240 * al)), (int) (80 * al), 2);
    }

    /** Plaques d'armure et materiaux alignes a droite de la ligne des points ; « +3 » flottant a la recolte. */
    private void ressources(GuiGraphics g, Font font, int xr, int yb, HudZombies.Joueur jm, long now) {
        EffetsHud e = EffetsHud.INSTANCE;
        float x = xr;
        float ty = yb - 8.5F;
        // plaques
        if (plaquesVal != jm.plaques()) {
            plaquesVal = jm.plaques();
            plaques.definir(font, Integer.toString(plaquesVal), Ui.BOLD, 0);
        }
        int cp = jm.plaques() > 0 ? Ui.mix(BLEU_PLAQUE, 0xFFFFFF, 0.25F) : Ui.FAINT;
        texte(g, font, plaques, x, ty, Ui.argb(cp, 245), 90, 2);
        x -= plaques.largeur() + 2 + 9;
        RenderSystem.setShaderColor(0.62F, 0.79F, 1F, jm.plaques() > 0 ? 1 : 0.4F);
        g.blit(PLAQUE, Math.round(x), yb - 8, 9, 9, 0, 0, 32, 32, 32, 32);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        x -= 7;
        // materiaux
        if (materiauxVal != jm.materiaux()) {
            materiauxVal = jm.materiaux();
            materiaux.definir(font, Integer.toString(materiauxVal), Ui.BOLD, 0);
        }
        long dm = now - e.materiauxMs;
        float eclat = dm >= 0 && dm < 600 ? 1 - dm / 600F : 0;
        int cm = jm.materiaux() > 0 || eclat > 0 ? Ui.mix(Ui.mix(Ui.OR, 0xFFFFFF, 0.25F), 0xFFFFFF, eclat) : Ui.FAINT;
        texte(g, font, materiaux, x, ty, Ui.argb(cm, 245), 90, 2);
        x -= materiaux.largeur() + 2 + 9;
        RenderSystem.setShaderColor(0.91F, 0.76F, 0.42F, jm.materiaux() > 0 ? 1 : 0.4F);
        g.blit(MATERIAUX, Math.round(x), yb - 8, 9, 9, 0, 0, 32, 32, 32, 32);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        if (dm >= 0 && dm < 1200 && e.materiauxDelta != 0) {
            if (deltaMatVu != e.materiauxMs) {
                deltaMatVu = e.materiauxMs;
                deltaMat.definir(font, (e.materiauxDelta > 0 ? "+" : "−") + Math.abs(e.materiauxDelta), Ui.BOLD, 0);
            }
            float p = dm / 1200F;
            texte(g, font, deltaMat, x + 4.5F, ty - 9 - 6 * p, Ui.argb(Ui.OR, (int) (255 * (1 - p * p))), (int) (120 * (1 - p * p)), 1);
        }
    }

    /**
     * Barre de vie de la carte (3 d'epaisseur, de x0 a xr) : cachee a pleine vie (la place reste reservee), apparait
     * au premier coup avec un eclair, trainee claire des degats qui rattrape la barre, reste tant que la vie n'est pas
     * pleine, puis s'efface 1,5 s apres le retour a pleine vie (en 0,6 s). Si Blindage monte le maximum, la vie n'est
     * plus pleine : la barre apparait, se remplit, puis s'efface. A terre : compte a rebours rouge a la place. Rend
     * l'opacite de la barre (pour les PV affiches a droite du pseudo).
     */
    private float vie(GuiGraphics g, int x0, int xr, int yv, HudZombies.Joueur jm, long now, float dtS) {
        var p = Minecraft.getInstance().player;
        int bw = xr - x0;
        if (jm.etat() == HudZombies.A_TERRE) {
            int sec = jm.secondes();
            if (moiTerreSec < 0 || sec > moiTerreTotal) moiTerreTotal = Math.max(1, sec);
            float frac = Mth.clamp(sec / (float) moiTerreTotal, 0, 1);
            g.fill(x0, yv, xr, yv + 3, Ui.argb(0x3A0E0C, 230));
            g.fill(x0, yv, x0 + Math.round(bw * frac), yv + 3, Ui.argb(Ui.DANGER, 245));
            vieAvant = -1;
            vieTrainee = -1;
            vieMontree = false;
            viePleineMs = -1;
            return 0;
        }
        moiTerreSec = -1;
        moiTerreTotal = 30;
        if (p == null || jm.etat() != HudZombies.DEBOUT || p.getMaxHealth() <= 0) return 0;
        float max = p.getMaxHealth();
        float vie = Mth.clamp(p.getHealth(), 0, max);
        if (vieTrainee < 0) vieTrainee = vie;
        if (vieAvant >= 0 && vie < vieAvant - 0.01F) coupMs = now;
        vieAvant = vie;
        // la trainee claire rejoint la vie apres un court delai
        if (now - coupMs > 450) vieTrainee = Math.max(vie, vieTrainee - max * 0.6F * dtS);
        if (vie > vieTrainee) vieTrainee = vie;
        boolean pleine = vie >= max - 0.01F && vieTrainee <= vie + 0.01F;
        if (!pleine) {
            vieMontree = true;
            viePleineMs = -1;
        } else if (vieMontree && viePleineMs < 0) {
            viePleineMs = now;
        }
        float va = 0;
        if (vieMontree) {
            if (viePleineMs < 0) {
                va = 1;
            } else {
                long d = now - viePleineMs;
                va = d < 1500 ? 1 : 1 - (d - 1500) / 600F;
                if (va <= 0) {
                    vieMontree = false;
                    va = 0;
                }
            }
        }
        if (va <= 0.01F) return 0;
        float f = vie / max, ft = vieTrainee / max;
        float bat = f < 0.35F ? 0.6F + 0.4F * Mth.sin(now / 140F) : 0;
        long dc = now - coupMs;
        float flash = dc >= 0 && dc < 260 ? 1 - dc / 260F : 0;
        int a = (int) (245 * va);
        Ui.soft(g, x0 - 1, yv - 1, bw + 2, 5, Ui.argb(SOMBRE, (int) (a * 0.7F)));
        g.fill(x0, yv, xr, yv + 3, Ui.argb(0x3A0E0C, a));
        int wv = Math.round(bw * f), wt = Math.round(bw * ft);
        if (wt > wv) g.fill(x0 + wv, yv, x0 + wt, yv + 3, Ui.argb(0xF2D2C8, (int) (a * 0.85F)));
        int c = Ui.mix(ROUGE_VIE, ROUGE_VIE_CLAIR, Math.max(bat * 0.6F, flash));
        if (wv > 0) {
            g.fill(x0, yv, x0 + wv, yv + 3, Ui.argb(c, a));
            g.fill(x0, yv, x0 + wv, yv + 1, Ui.argb(0xFFFFFF, (int) (50 * va)));
        }
        // graduations tous les 10 points de vie (50 PV)
        int n = Mth.clamp((int) (max / 10), 1, 10);
        for (int i = 1; i < n; i++) {
            int gx = x0 + Math.round(bw * i * 10F / max);
            g.fill(gx, yv, gx + 1, yv + 3, Ui.argb(SOMBRE, (int) (200 * va)));
        }
        // eclair du coup recu : la barre entiere blanchit un instant
        if (flash > 0) g.fill(x0 - 1, yv - 1, xr + 1, yv + 4, Ui.argb(0xFFFFFF, (int) (90 * flash)));
        return va;
    }

    // ================================================================== « +60 » gagnes (a droite du viseur)
    private void popups(GuiGraphics g, int w, int h, UUID moi, long now, Font font) {
        EffetsHud e = EffetsHud.INSTANCE;
        float cx = w / 2F, cy = h / 2F;
        int rang = 0;
        int dernier = e.dernierPoint();
        for (int k = 0; k < EffetsHud.MAX_POINTS && rang < 4; k++) {
            int i = (dernier - k + EffetsHud.MAX_POINTS) % EffetsHud.MAX_POINTS;
            if (e.ptJoueur[i] == null || !e.ptJoueur[i].equals(moi)) continue;
            long dt = now - e.ptDebut[i];
            if (dt < 0 || dt > 1100) continue;
            float p = dt / 1100F;
            float a = 1 - p * p;
            float x = cx + 8 + 6 * (float) Math.sqrt(p);
            float y = cy - 12 - rang * 10 - 4 * p;
            float s = e.ptFort[i] ? 1.15F : 1;
            Component c = e.ptTexte[i];
            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            g.pose().scale(s, s, 1);
            g.drawString(font, c, 1, 1, Ui.argb(0, (int) (150 * a)), false);
            g.drawString(font, c, 0, 0, Ui.argb(e.ptCouleur[i], (int) (255 * a)), false);
            g.pose().popPose();
            rang++;
        }
    }

    // ================================================================== bonus actifs (colonne a droite)
    private void bonus(GuiGraphics g, Font font, int w, int h, int yFil, long now) {
        int nb = 0;
        for (TypeBonus b : BONUS) {
            int s = HudZombies.bonusSecondes(b);
            int i = b.ordinal();
            if (s <= 0) bonusMax[i] = 0;
            else {
                bonusMax[i] = Math.max(bonusMax[i], s);
                nb++;
            }
        }
        if (nb == 0) return;
        int taille = 18, pas = 24;
        int bh = nb * pas - (pas - taille);
        int haut = Math.round(h * 0.47F - bh / 2F);
        haut = Math.max(haut, yFil + 48);
        haut = Math.min(haut, h - TACZ_H - 8 - bh);
        int x = w - MARGE - taille;
        int y = haut;
        for (TypeBonus b : BONUS) {
            int s = HudZombies.bonusSecondes(b);
            if (s <= 0) continue;
            int i = b.ordinal();
            boolean visible = s > 5 || (now / 220) % 2 == 0;
            if (visible) g.blit(ICONE_BONUS[i], x, y, taille, taille, 0, 0, 64, 64, 64, 64);
            if (secBonusVal[i] != s) {
                secBonusVal[i] = s;
                secBonus[i].definir(font, Integer.toString(s), Ui.BOLD, 0);
            }
            int coul = s <= 5 ? Ui.DANGER : Ui.TEXT;
            texte(g, font, secBonus[i], x - 5, y + 4.5F, Ui.argb(coul, 235), 110, 2);
            // jauge fine sous l'icone
            g.fill(x + 1, y + taille + 1, x + taille - 1, y + taille + 2, Ui.argb(0xFFFFFF, 30));
            int lw = Math.round((taille - 2) * Mth.clamp(s / (float) Math.max(1, bonusMax[i]), 0, 1));
            if (lw > 0) g.fill(x + 1, y + taille + 1, x + 1 + lw, y + taille + 2, Ui.argb(coul, 220));
            y += pas;
        }
    }

    // ================================================================== objectif (haut gauche)
    /** Rend le bas du bloc de l'objectif (pour placer le lore d'intro dessous), {@code HAUT - 8} s'il n'y en a pas. */
    private int objectif(GuiGraphics g, Font font, int w, long now) {
        EffetsHud e = EffetsHud.INSTANCE;
        Component texte = HudZombies.objectif();
        int maxW = Mth.clamp(Math.round(w * 0.28F), 120, 170);
        if (objectifTitre.vide()) objectifTitre.definir(font, Component.translatable("zombies.hud.objectif").getString(), Ui.SMALL, 0.8F);
        if (objectifAtteint.vide()) objectifAtteint.definir(font, Component.translatable("zombies.hud.objectif_atteint").getString(), Ui.SMALL, 0.8F);
        // le composant ne change d'instance qu'a la reception d'un objectif : le texte n'est relu qu'a ce moment
        if (texte != objectifVu || maxW != objectifMaxW) {
            objectifVu = texte;
            String s = texte.getString();
            // l'ancien objectif reste affiche (coche) si le jeu l'a annonce rempli
            if (!s.equals(objectifSource) && !objectifSource.isEmpty() && e.objectifRempliMs > now - 2000) {
                ancienSource = objectifSource;
                ancienLignes = objectifLignes;
                ancienLargeurs = objectifLargeurs;
                ancienLargeur = objectifLargeur;
            }
            objectifSource = s;
            objectifMaxW = maxW;
            objectifLignes = s.isEmpty() ? List.of() : font.split(Component.empty().withStyle(Style.EMPTY.withFont(Ui.FONT)).append(texte), maxW);
            objectifLargeurs = new int[objectifLignes.size()];
            objectifLargeur = 0;
            for (int i = 0; i < objectifLignes.size(); i++) {
                objectifLargeurs[i] = font.width(objectifLignes.get(i));
                objectifLargeur = Math.max(objectifLargeur, objectifLargeurs[i]);
            }
        }
        int x = MARGE, y = HAUT;
        int bas = objectifLignes.isEmpty() ? HAUT - 8 : y - 2 + 12 + objectifLignes.size() * 10;
        // objectif rempli : coche verte et texte barre 1,2 s, puis il repart vers la gauche
        long dr = now - e.objectifRempliMs;
        if (!ancienSource.isEmpty() && dr >= 0 && dr < 1600) {
            float p = dr < 1200 ? 0 : (dr - 1200) / 400F;
            blocObjectif(g, font, x - 30 * p, y, ancienLignes, ancienLargeurs, ancienLargeur, 1 - p, true, 0);
            return Math.max(bas, y - 2 + 12 + ancienLignes.size() * 10);
        }
        if (objectifLignes.isEmpty()) return bas;
        long da = now - Math.max(versHorloge(HudZombies.objectifDepuisMs(), now), e.objectifRempliMs + 1600);
        if (da < 0) return bas;
        float entree = Mth.clamp(da / 350F, 0, 1);
        float glisse = -30 * (1 - entree) * (1 - entree);
        float a = (float) Math.pow(entree, 0.7);
        // reflet bref a l'apparition
        float halo = da < 900 ? 1 - da / 900F : 0;
        blocObjectif(g, font, x + glisse, y, objectifLignes, objectifLargeurs, objectifLargeur, a, false, halo);
        return bas;
    }

    private void blocObjectif(GuiGraphics g, Font font, float x, int y, List<FormattedCharSequence> lignes, int[] largeurs, int largeur, float a,
                              boolean fait, float halo) {
        if (a <= 0.01F) return;
        int bh = 12 + lignes.size() * 10;
        g.pose().pushPose();
        g.pose().translate(x, 0, 0);
        degradeH(g, -2, y - 2, largeur + 16, y - 2 + bh, Ui.argb(SOMBRE, (int) (120 * a)), Ui.argb(SOMBRE, 0));
        g.fill(-2, y - 2, -1, y - 2 + bh, Ui.argb(fait ? Ui.OK : ROUGE_VIE, (int) (230 * a)));
        Libelle titre = fait ? objectifAtteint : objectifTitre;
        int ct = fait ? Ui.mix(Ui.OK, 0xFFFFFF, 0.3F) : Ui.mix(Ui.MUTED, 0xFFFFFF, halo * 0.6F);
        titre.dessiner(g, font, 5, y, Ui.argb(ct, (int) (230 * a)));
        int yy = y + 11;
        for (int i = 0; i < lignes.size(); i++) {
            int c = fait ? 0x9BE7B4 : Ui.mix(0xE9E6DE, 0xFFFFFF, halo);
            g.drawString(font, lignes.get(i), 5, yy, Ui.argb(c, (int) (240 * a)), false);
            if (fait) g.fill(5, yy + 4, 5 + largeurs[i], yy + 5, Ui.argb(0x9BE7B4, (int) (200 * a)));
            yy += 10;
        }
        if (fait) {
            // coche verte a droite du titre
            int cx = 5 + Math.round(titre.largeur()) + 5;
            int c = Ui.argb(Ui.OK, (int) (255 * a));
            g.fill(cx, y + 3, cx + 2, y + 5, c);
            g.fill(cx + 2, y + 5, cx + 4, y + 7, c);
            for (int k = 0; k < 4; k++) g.fill(cx + 4 + k, y + 4 - k * 2, cx + 6 + k, y + 6 - k * 2, c);
        }
        g.pose().popPose();
    }

    // ================================================================== boussole (haut centre)
    private void boussole(GuiGraphics g, Font font, int w, float partiel, long now) {
        if (bossProche || now - bossVanillaMs < 300) return;
        var p = Minecraft.getInstance().player;
        if (p == null) return;
        int bw = Mth.clamp(Math.round(w * 0.30F), 150, 230);
        float cx = w / 2F;
        int y = HAUT + 1;
        Ui.band(g, Math.round(cx - bw / 2F - 14), y - 2, Math.round(cx + bw / 2F + 14), y + 22, 0x000000, 105, 34);
        // cap : 0 = nord, 90 = est (lacet de Minecraft : 0 = sud, 90 = ouest)
        float cap = Mth.wrapDegrees(p.getViewYRot(partiel) + 180);
        String[] noms = Ui.french() ? CAPS_FR : CAPS_EN;
        for (int k = 0; k < 24; k++) {
            int ang = k * 15;
            float rel = Mth.wrapDegrees(ang - cap);
            if (Math.abs(rel) > 90) continue;
            float x = cx + rel / 90F * bw / 2F;
            float r = rel / 90F;
            float fa = 1 - r * r;
            if (ang % 45 == 0) {
                int i = ang / 45;
                boolean cardinal = ang % 90 == 0;
                caps[i].definir(font, noms[i], cardinal ? Ui.BOLD : Ui.SMALL, 0);
                int c = cardinal ? Ui.TEXT : Ui.mix(Ui.MUTED, 0xFFFFFF, 0.25F);
                texte(g, font, caps[i], x, cardinal ? y - 0.5F : y + 0.5F, Ui.argb(c, (int) (255 * fa)), (int) (150 * fa), 1);
            } else {
                int xi = Math.round(x);
                g.fill(xi, y + 3, xi + 1, y + 6, Ui.argb(0xFFFFFF, (int) (110 * fa)));
            }
        }
        // zombies proches
        for (int i = 0; i < nbEnnemis; i++) {
            float rel = Mth.wrapDegrees(ENNEMIS[i] - cap);
            if (Math.abs(rel) > 90) continue;
            losange(g, cx + rel / 90F * bw / 2F, y + 10.5F, 1.6F, Ui.argb(Ui.DANGER, 235));
        }
        // repere de l'objectif (0.2.3) : losange dore cercle de noir, distance a cote ; hors du champ (±90°) : colle au bord
        net.minecraft.world.phys.Vec3 obj = HudZombies.objectifCible();
        if (obj != null) {
            double dx = obj.x - p.getX(), dz = obj.z - p.getZ(), dy = obj.y - (p.getY() + 1);
            double d = Math.sqrt(dx * dx + dz * dz + dy * dy);
            if (d > 2.5) {
                float rel = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(dx, -dz)) - cap);
                boolean bord = Math.abs(rel) > 90;
                float xr = cx + Mth.clamp(rel, -90, 90) / 90F * bw / 2F;
                float pulse = 0.85F + 0.15F * Mth.sin(now / 160F);
                losange(g, xr, y + 10.5F, 4.4F, Ui.argb(0x000000, 200));
                losange(g, xr, y + 10.5F, 3.3F, Ui.argb(Ui.OR, (int) (255 * pulse)));
                losange(g, xr, y + 10.5F, 1.2F, Ui.argb(0xFFF4D6, 255));
                int m = (int) Math.round(d);
                int cle = m * 4 + (dy > 3 ? 1 : dy < -3 ? 2 : 0) + (bord ? (rel > 0 ? 1000000 : 2000000) : 0);
                if (cle != repereDistVu) {
                    repereDistVu = cle;
                    String t = m + " m" + (dy > 3 ? " ▲" : dy < -3 ? " ▼" : "");
                    if (bord) t = rel > 0 ? t + " ▶" : "◀ " + t;
                    repereDist.definir(font, t, Ui.SMALL, 0);
                }
                // texte vers l'interieur de la bande (jamais hors de la boussole)
                boolean aDroite = xr < cx + bw / 2F - repereDist.largeur() - 8;
                texte(g, font, repereDist, aDroite ? xr + 6 : xr - 6, y + 6.5F, Ui.argb(Ui.OR, 240), 150, aDroite ? 0 : 2);
            }
        }
        // repere central
        int xc = Math.round(cx);
        g.fill(xc, y + 9, xc + 1, y + 12, Ui.argb(0xFFFFFF, 230));
        // nom du lieu (zone de la carte)
        String nom = HudZombies.lieu();
        if (!nom.isEmpty()) {
            lieu.definir(font, nom, Ui.SMALL, 1.0F);
            texte(g, font, lieu, cx, y + 14, Ui.argb(Ui.mix(Ui.MUTED, 0xFFFFFF, 0.3F), 235), 150, 1);
        }
    }

    // ================================================================== nom de l'arme et Pack-a-Punch (au-dessus de TaCZ)
    private void nomArme(GuiGraphics g, Font font, int w, int h, Minecraft mc) {
        if (mc.player == null) return;
        ItemStack st = mc.player.getMainHandItem();
        if (st != armeVue) {
            // une nouvelle pile (autre arme, ou meme arme resynchronisee par le serveur) : nom et niveau relus une fois
            armeVue = st;
            ResourceLocation id = st.isEmpty() ? null : ForgeRegistries.ITEMS.getKey(st.getItem());
            // une arme a feu de TaCZ (tag GunId), pas une boite de munitions ni un accessoire
            armeTacz = id != null && "tacz".equals(id.getNamespace()) && st.getTag() != null && st.getTag().contains("GunId");
            armePap = armeTacz ? ZombiesApi.niveauPap(st) : 0;
            if (armeTacz) armeNom.definir(font, st.getHoverName().getString().replace("★", "").trim(), Ui.SMALL, 0.8F);
        }
        if (!armeTacz || armeNom.vide()) return;
        int yb = h - TACZ_H - 12;
        float xr = w - 10;
        if (armePap > 0) {
            for (int i = 0; i < armePap; i++) losange(g, xr - 3 - i * 7, yb + 4, 2.6F, Ui.argb(Ui.OR, 240));
            xr -= armePap * 7 + 3;
        }
        texte(g, font, armeNom, xr, yb, Ui.argb(Ui.TEXT, 230), 130, 2);
    }

    // ================================================================== invite d'achat
    private void invite(GuiGraphics g, Font font, int w, int h, long now) {
        EffetsHud e = EffetsHud.INSTANCE;
        Component texte = HudZombies.invite();
        if (texte == null || Boisson.actif()) {
            inviteVisible = false;
        } else {
            if (!inviteVisible) {
                inviteVisible = true;
                inviteDepuisMs = now;
            }
            if (versionInvite != HudZombies.version()) {
                versionInvite = HudZombies.version();
                inviteTexte.definir(font, texte, Ui.FONT);
                inviteTouche.definir(font, Minecraft.getInstance().options.keySwapOffhand.getTranslatedKeyMessage().getString(), Ui.BOLD, 0.4F);
                invitePrix.definir(font, HudZombies.inviteCout() > 0 ? Ui.nombre(HudZombies.inviteCout()) : "", Ui.BOLD, 0);
                point.definir(font, "·", Ui.BOLD, 0);
            }
            float a = Mth.clamp((now - inviteDepuisMs) / 150F, 0, 1);
            int tw = (int) Math.max(12, inviteTouche.largeur() + 8);
            boolean prix = !invitePrix.vide();
            int lw = (int) (8 + tw + 7 + inviteTexte.largeur() + (prix ? 7 + point.largeur() + 7 + invitePrix.largeur() : 0) + 9);
            int ph = 20;
            int px = w / 2 - lw / 2, py = h / 2 + 22;
            long dr = now - e.refusMs;
            if (dr >= 0 && dr < 420) px += (int) (Mth.sin(dr / 26F) * 4 * (1 - dr / 420F));
            Ui.glass(g, px, py, lw, ph, (int) (190 * a));
            int kx = px + 8;
            Ui.soft(g, kx, py + 4, tw, 12, Ui.argb(0xFFFFFF, (int) (34 * a)));
            Ui.softOutline(g, kx + 1, py + 5, tw - 2, 10, Ui.argb(0xFFFFFF, (int) (70 * a)));
            inviteTouche.centre(g, font, kx + tw / 2F + 0.5F, py + 6, Ui.argb(Ui.TEXT, (int) (255 * a)));
            float cx = kx + tw + 7;
            inviteTexte.dessiner(g, font, cx, py + 6, Ui.argb(Ui.TEXT, (int) (255 * a)));
            cx += inviteTexte.largeur();
            if (prix) {
                cx += 7;
                point.dessiner(g, font, cx, py + 6, Ui.argb(Ui.MUTED, (int) (255 * a)));
                cx += point.largeur() + 7;
                invitePrix.dessiner(g, font, cx, py + 6, Ui.argb(HudZombies.inviteAbordable() ? Ui.OR : Ui.DANGER, (int) (255 * a)));
            }
            int prog = HudZombies.progression();
            if (prog >= 0) Ui.bar(g, px + 6, py + ph + 3, lw - 12, 2, prog / 100F, Ui.OR);
        }
        // refus (rouge) et achat (vert), sous l'invite, qui s'effacent
        long dr = now - e.refusMs;
        if (dr >= 0 && dr < 1600 && !e.refusTexte.isEmpty()) {
            if (refusVu != e.refusVersion) {
                refusVu = e.refusVersion;
                refus.definir(font, e.refusTexte, Ui.FONT, 0);
            }
            float ar = fondu(dr, 1600);
            refus.centre(g, font, w / 2F + 0.75F, h / 2F + 50.75F, Ui.argb(0x000000, (int) (170 * ar)));
            refus.centre(g, font, w / 2F, h / 2F + 50, Ui.argb(Ui.DANGER, (int) (255 * ar)));
        }
        long da = now - e.achatMs;
        if (da >= 0 && da < 1300 && !e.achatTexte.isEmpty()) {
            if (achatVu != e.achatVersion) {
                achatVu = e.achatVersion;
                achat.definir(font, e.achatTexte, Ui.BOLD, 0.6F);
            }
            float aa = fondu(da, 1300);
            float ya = h / 2F + 50 - da / 130F;
            achat.centre(g, font, w / 2F + 0.75F, ya + 0.75F, Ui.argb(0x000000, (int) (170 * aa)));
            achat.centre(g, font, w / 2F, ya, Ui.argb(Ui.mix(Ui.OK, 0xFFFFFF, 0.25F), (int) (255 * aa)));
        }
        // resultat de la boite : rarete en couleur au-dessus du viseur
        long db = now - e.boiteMs;
        if (db >= 0 && db < 2600 && !e.boiteTexte.isEmpty()) {
            if (boiteVu != e.boiteVersion) {
                boiteVu = e.boiteVersion;
                boiteTexte.definir(font, e.boiteTexte, Ui.BOLD, 0.7F);
            }
            float ab = fondu(db, 2600);
            float s = 1.0F + 0.25F * Math.max(0, 1 - db / 220F);
            g.pose().pushPose();
            g.pose().translate(w / 2F, h / 2F - 34, 0);
            g.pose().scale(s, s, 1);
            boiteTexte.centre(g, font, 0.75F, 0.75F, Ui.argb(0x000000, (int) (170 * ab)));
            boiteTexte.centre(g, font, 0, 0, Ui.argb(Ui.mix(e.boiteCouleur, 0xFFFFFF, 0.15F), (int) (255 * ab)));
            g.pose().popPose();
        }
    }

    /** Opacite d'un message ephemere : pleine jusqu'a 60 % de sa duree, puis fondu lineaire. */
    private static float fondu(long dt, int duree) {
        float p = dt / (float) duree;
        return p < 0.6F ? 1F : Mth.clamp((1 - p) / 0.4F, 0, 1);
    }

    // ================================================================== a terre / mort / blessure
    private void voile(GuiGraphics g, int w, int h, HudZombies.Joueur jm, long now) {
        if (jm == null) return;
        if (jm.etat() == HudZombies.DEBOUT) {
            blessure(g, w, h, now);
            return;
        }
        if (jm.etat() == HudZombies.MORT) {
            g.fillGradient(0, 0, w, h, 0x90000000, 0xB0000000);
            return;
        }
        float bat = 0.5F + 0.5F * Mth.sin(now / 260F);
        int a = (int) (70 + 60 * bat);
        g.fill(0, 0, w, h, 0x40000000);
        int bande = h / 4;
        g.fillGradient(0, 0, w, bande, Ui.argb(0x8A0000, a), 0x008A0000);
        g.fillGradient(0, h - bande, w, h, 0x008A0000, Ui.argb(0x8A0000, a));
        int cols = 10, lb = w / 8;
        for (int i = 0; i < cols; i++) {
            int al = (int) (a * (1 - i / (float) cols) * 0.8F);
            int x0 = lb * i / cols, x1 = lb * (i + 1) / cols;
            g.fill(x0, 0, x1, h, Ui.argb(0x8A0000, al));
            g.fill(w - x1, 0, w - x0, h, Ui.argb(0x8A0000, al));
        }
    }

    /** Bords rouges discrets quand la vie est basse (la barre de vie dit l'essentiel), battement sous un tiers. */
    private static void blessure(GuiGraphics g, int w, int h, long now) {
        var p = Minecraft.getInstance().player;
        if (p == null || p.getMaxHealth() <= 0) return;
        float manque = 1 - Mth.clamp(p.getHealth() / p.getMaxHealth(), 0, 1);
        if (manque < 0.4F) return;
        float bat = manque > 0.66F ? 0.75F + 0.25F * Mth.sin(now / 180F) : 1;
        int a = (int) (150 * Mth.clamp((manque - 0.4F) / 0.55F, 0, 1) * bat);
        if (a <= 2) return;
        int bande = (int) (h * (0.10F + 0.12F * manque));
        g.fillGradient(0, 0, w, bande, Ui.argb(0x7A0000, a), 0x007A0000);
        g.fillGradient(0, h - bande, w, h, 0x007A0000, Ui.argb(0x7A0000, a));
        int cols = 10, lb = (int) (w * (0.06F + 0.08F * manque));
        for (int i = 0; i < cols; i++) {
            int al = (int) (a * (1 - i / (float) cols) * 0.8F);
            int x0 = lb * i / cols, x1 = lb * (i + 1) / cols;
            g.fill(x0, 0, x1, h, Ui.argb(0x7A0000, al));
            g.fill(w - x1, 0, w - x0, h, Ui.argb(0x7A0000, al));
        }
    }

    private void aTerre(GuiGraphics g, Font font, int w, int h, HudZombies.Joueur jm, long now) {
        if (jm == null) return;
        if (jm.etat() == HudZombies.MORT) {
            if (mort.vide()) {
                mort.definir(font, Component.translatable("zombies.hud.mort").getString(), Ui.BOLD, 2.0F);
                mortSous.definir(font, Component.translatable("zombies.hud.mort.sous").getString(), Ui.FONT, 0);
            }
            g.pose().pushPose();
            g.pose().translate(w / 2F, h * 0.36F, 0);
            g.pose().scale(2, 2, 1);
            mort.centre(g, font, 0, 0, Ui.argb(Ui.TEXT, 240));
            g.pose().popPose();
            mortSous.centre(g, font, w / 2F, h * 0.36F + 26, Ui.argb(Ui.MUTED, 230));
            return;
        }
        if (jm.etat() != HudZombies.A_TERRE) {
            aTerreSecVu = -1;
            return;
        }
        int sec = jm.secondes();
        if (aTerreSecVu < 0) {
            aTerreTotal = Math.max(sec, 1);
            // seul dans la partie, on n'est a terre qu'avec Reanimation rapide (ou un kit) : on se releve seul
            boolean seul = HudZombies.joueurs().size() <= 1;
            aTerre.definir(font, Component.translatable("zombies.hud.a_terre").getString(), Ui.BOLD, 2.0F);
            aTerreSous.definir(font, Component.translatable(seul ? "zombies.hud.a_terre.seul" : "zombies.hud.a_terre.sous").getString(), Ui.FONT, 0);
        }
        if (sec != aTerreSecVu) {
            aTerreSecVu = sec;
            aTerreSec.definir(font, Integer.toString(sec), Ui.BOLD, 0);
        }
        float cy = h * 0.34F;
        g.pose().pushPose();
        g.pose().translate(w / 2F, cy, 0);
        g.pose().scale(2, 2, 1);
        aTerre.centre(g, font, 0, 0, Ui.argb(0xFFDADA, 245));
        g.pose().popPose();
        aTerreSous.centre(g, font, w / 2F, cy + 24, Ui.argb(Ui.MUTED, 235));
        int rx = w / 2, ry = (int) (cy + 58);
        float frac = Mth.clamp(sec / (float) aTerreTotal, 0, 1);
        int seg = 48;
        for (int i = 0; i < seg; i++) {
            float ang = -Mth.HALF_PI + i / (float) seg * Mth.TWO_PI;
            int sx = rx + Math.round(Mth.cos(ang) * 16), sy = ry + Math.round(Mth.sin(ang) * 16);
            boolean plein = i / (float) seg < frac;
            g.fill(sx - 1, sy - 1, sx + 1, sy + 1, plein ? Ui.argb(Ui.DANGER, 255) : Ui.argb(0xFFFFFF, 40));
        }
        aTerreSec.centre(g, font, rx + 0.5F, ry - 4, Ui.argb(Ui.TEXT, 255));
        int prog = HudZombies.progression();
        if (prog >= 0) {
            if (reanimation.vide()) reanimation.definir(font, Component.translatable("zombies.hud.reanimation").getString(), Ui.SMALL, 1.0F);
            reanimation.centre(g, font, w / 2F, ry + 26, Ui.argb(Ui.OK, 255));
            Ui.bar(g, w / 2 - 50, ry + 37, 100, 3, prog / 100F, Ui.OK);
        }
    }

    // ================================================================== coequipiers a terre (alerte, haut centre sous la boussole)
    private void coequipiersATerre(GuiGraphics g, Font font, int w, UUID moi) {
        List<HudZombies.Joueur> js = HudZombies.joueurs();
        int y = 34, k = 0;
        for (int i = 0; i < js.size() && k < 4; i++) {
            HudZombies.Joueur j = js.get(i);
            if (j.id().equals(moi) || j.etat() != HudZombies.A_TERRE) continue;
            if (coeqSec[k] != j.secondes() || !j.nom().equals(coeqNomVu[k])) {
                coeqSec[k] = j.secondes();
                coeqNomVu[k] = j.nom();
                coeq[k].definir(font, Component.translatable("zombies.hud.coequipier", j.nom(), j.secondes()).getString(), Ui.SMALL, 0.6F);
            }
            int lw = (int) coeq[k].largeur() + 16;
            int x = w / 2 - lw / 2;
            Ui.soft(g, x, y, lw, 13, Ui.argb(0x3A0A0A, 190));
            Ui.softOutline(g, x + 1, y + 1, lw - 2, 11, Ui.argb(Ui.DANGER, 120));
            coeq[k].dessiner(g, font, x + 8, y + 3, Ui.argb(0xFFD0D0, 255));
            y += 16;
            k++;
        }
    }

    // ================================================================== banniere
    private void banniere(GuiGraphics g, Font font, int w, int h, long now) {
        EffetsHud e = EffetsHud.INSTANCE;
        long dt = now - e.banDebutMs;
        if (dt < 0 || dt > 2900) return;
        if (banVu != e.banVersion) {
            banVu = e.banVersion;
            banTitre.definir(font, e.banTitre, Ui.BOLD, 1.6F);
            banSous.definir(font, e.banSous, Ui.FONT, 0);
        }
        float a = dt < 220 ? dt / 220F : dt > 2300 ? 1 - (dt - 2300) / 600F : 1;
        float s = Math.min(2.0F, (w - 40) / Math.max(1F, banTitre.largeur())) + 0.25F * (1 - Mth.clamp(dt / 260F, 0, 1));
        float y = h * 0.2F;
        g.pose().pushPose();
        g.pose().translate(w / 2F, y, 0);
        g.pose().scale(s, s, 1);
        banTitre.centre(g, font, 0, 0, Ui.argb(Ui.mix(e.banCouleur, 0xFFFFFF, 0.2F), (int) (255 * a)));
        g.pose().popPose();
        int lw = (int) (banTitre.largeur() * s * Mth.clamp(dt / 400F, 0, 1));
        Ui.hairline(g, w / 2 - lw / 2, w / 2 + lw / 2, (int) (y + 9 * s + 4), e.banCouleur, (int) (160 * a), true, true);
        if (!banSous.vide()) banSous.centre(g, font, w / 2F, y + 9 * s + 9, Ui.argb(Ui.MUTED, (int) (235 * a)));
    }
}
