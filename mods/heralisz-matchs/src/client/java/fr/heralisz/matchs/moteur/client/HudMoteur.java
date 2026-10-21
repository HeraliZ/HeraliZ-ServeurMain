package fr.heralisz.matchs.moteur.client;

import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.client.kit.SonsUi;
import fr.heralisz.matchs.client.kit.Ui;
import fr.heralisz.matchs.moteur.MoteurSons;
import fr.heralisz.matchs.moteur.api.Annonces;
import fr.heralisz.matchs.moteur.api.Hud;
import fr.heralisz.matchs.moteur.api.KillFeed;
import fr.heralisz.matchs.moteur.net.PaquetsMoteur;
import fr.heralisz.matchs.reseau.MatchsNet;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * HUD du moteur (MATCHS_SPEC §1.8, §1.4, §1.6, §1.9), a l'echelle du joueur, meme charte que les clans (DM Sans,
 * verre, capitales espacees, aucun element vanilla) :
 * <ul>
 *     <li>bandeau de match en haut au centre : titre, temps (decompte local), pastilles d'equipes (vivants en points),
 *     score ;</li>
 *     <li>grande annonce centrale animee (apparition en zoom, bande sombre, filets d'accent ; styles VICTOIRE or,
 *     DEFAITE rouge desature, FINALE plus grande, MANCHE craie rouge) avec son maison ;</li>
 *     <li>compte a rebours central (chiffre qui pulse, tic chaque seconde, « COMBAT ! » au depart) ;</li>
 *     <li>fil des eliminations en haut a droite (5 lignes, 5 s, fondu) ;</li>
 *     <li>indicateur discret « EN COMBAT 12 s » avec une jauge fine, coin bas droit (hors match) ;</li>
 *     <li>bandeau spectateur en bas (cible, fleches gauche/droite) ; la barre vanilla du spectateur est masquee ;</li>
 *     <li>toasts d'invitation de groupe a droite.</li>
 * </ul>
 * HUD compact ({@link #compact}, duels) : bandeau reduit, annonces et compte a rebours en petit en haut de l'ecran.
 * Les textes sont mis en forme a la reception ou au changement de valeur (une fois par seconde au plus), jamais a
 * chaque image.
 */
public final class HudMoteur {
    private HudMoteur() {
    }

    /**
     * Haut du fil des eliminations selon la hauteur de l'ecran ; negatif = place par defaut (y = 6). Un mode de jeu dont
     * le HUD occupe le haut droit le redefinit (zombies : sous le numero de manche). Les invitations de groupe suivent.
     */
    public static java.util.function.IntUnaryOperator hautFil = h -> -1;

    /**
     * HUD compact (16/10, Matchs 0.11.0, duels : « les UI sont trop grosses, elles gênent pendant le combat ») : branché
     * par le mode de jeu qui le veut (vrai pendant un duel). Bandeau reduit ({@link #ECHELLE_COMPACTE}), annonces et
     * compte a rebours en petit, en haut de l'ecran sous le bandeau ({@link #Y_ANNONCE_COMPACTE}, jamais au milieu),
     * bandeau du spectateur reduit. Les autres modes (ClashWar, zombies) gardent les grandes annonces.
     */
    public static java.util.function.BooleanSupplier compact = () -> false;
    /**
     * Vrai quand le mode de jeu dessine son propre bandeau (ClashWar : pastilles des manches, role ; 21/10) : celui du
     * moteur n'est alors pas dessine.
     */
    public static java.util.function.BooleanSupplier masquerBandeau = () -> false;
    /** Echelle du bandeau en HUD compact. */
    public static final float ECHELLE_COMPACTE = 0.72f;
    /** Bas du bandeau en HUD compact (y, echelle du joueur) : le HUD du mode (etat de la bombe) se place dessous. */
    public static final int BAS_BANDEAU_COMPACT = 40;
    /** Haut des annonces et du compte a rebours en HUD compact (sous la ligne d'etat du mode). */
    public static final int Y_ANNONCE_COMPACTE = 58;

    public static void enregistrer(IEventBus modBus) {
        modBus.addListener(HudMoteur::overlays);
        MinecraftForge.EVENT_BUS.addListener(HudMoteur::avantOverlay);
        MinecraftForge.EVENT_BUS.addListener(HudMoteur::tickMasque);
        MinecraftForge.EVENT_BUS.addListener(HudMoteur::touche);
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> MoteurClient.vider());
        CartesMasquees.enregistrer();
    }

    private static void overlays(RegisterGuiOverlaysEvent e) {
        e.registerAboveAll("moteur", HudMoteur::dessiner);
    }

    /** HUD masque (un de nos ecrans, ou F1) : la file des annonces s'ecoule quand meme (voir {@link #avancerAnnonce}). */
    private static void tickMasque(net.minecraftforge.event.TickEvent.ClientTickEvent e) {
        if (e.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && (mc.options.hideGui || ecranANous())) avancerAnnonce(Util.getMillis());
    }

    /** Masque la barre vanilla du spectateur (et son infobulle) pendant le mode spectateur de match. */
    private static void avantOverlay(RenderGuiOverlayEvent.Pre e) {
        // nos ecrans masquent tout le HUD derriere eux (charte)
        if (ecranANous()) {
            e.setCanceled(true);
            return;
        }
        if (!MoteurClient.spectateur()) return;
        if (e.getOverlay() == VanillaGuiOverlay.HOTBAR.type() || e.getOverlay() == VanillaGuiOverlay.ITEM_NAME.type()) e.setCanceled(true);
    }

    /** Fleches gauche/droite en spectateur : cible precedente/suivante (le serveur revalide). */
    private static void touche(InputEvent.Key e) {
        if (e.getAction() != GLFW.GLFW_PRESS || !MoteurClient.spectateur() || Minecraft.getInstance().screen != null) return;
        if (e.getKey() == GLFW.GLFW_KEY_LEFT) MatchsNet.versServeur(new PaquetsMoteur.SpectateurC2S(-1));
        else if (e.getKey() == GLFW.GLFW_KEY_RIGHT) MatchsNet.versServeur(new PaquetsMoteur.SpectateurC2S(1));
    }

    // ------------------------------------------------------------------ sons
    private static final Map<String, SoundEvent> SONS = new HashMap<>();

    @Nullable
    private static SoundEvent son(String id) {
        if (id == null || id.isEmpty()) return null;
        return SONS.computeIfAbsent(id, k -> {
            ResourceLocation rl = ResourceLocation.tryParse(k);
            return rl == null ? null : ForgeRegistries.SOUND_EVENTS.getValue(rl);
        });
    }

    static void jouer(SoundEvent s, float volume) {
        SonsUi.jouer(s, volume);
    }

    static void sonElimination() {
        jouer(MoteurSons.ELIMINATION.get(), 0.5f);
    }

    static void sonInvitation() {
        jouer(MoteurSons.INVITATION.get(), 0.8f);
    }

    /** Oublie les caches d'affichage (deconnexion). */
    static void oublier() {
        courante = null;
        bandeauCache = null;
        compteMontre = Integer.MIN_VALUE;
        for (int i = 0; i < RENDUS.length; i++) RENDUS[i] = null;
    }

    // ------------------------------------------------------------------ rendu
    /** Vrai si un ecran du moteur est ouvert (le HUD est alors masque). */
    public static boolean ecranANous() {
        var s = Minecraft.getInstance().screen;
        return s instanceof ChoixClasseScreen || s instanceof GroupeScreen;
    }

    private static void dessiner(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || ecranANous()) return;
        long now = Util.getMillis();
        Font font = mc.font;
        boolean petit = compact.getAsBoolean();
        // le mode de jeu peut dessiner son propre bandeau (ClashWar)
        boolean bandeau = !masquerBandeau.getAsBoolean();
        if (bandeau && petit) {
            // bandeau reduit autour du haut de l'ecran (memes formes, plus petites)
            g.pose().pushPose();
            g.pose().translate(w / 2f, 1, 0);
            g.pose().scale(ECHELLE_COMPACTE, ECHELLE_COMPACTE, 1);
            g.pose().translate(-w / 2f, 0, 0);
            dessinerBandeau(g, font, w);
            g.pose().popPose();
        } else if (bandeau) {
            dessinerBandeau(g, font, w);
        }
        int yFil = hautFil.applyAsInt(h);
        if (yFil < 0) yFil = 6;
        dessinerFil(g, font, w, yFil, now);
        dessinerToasts(g, font, w, yFil, now);
        dessinerCombat(g, font, w, h);
        if (petit) {
            // bandeau du spectateur reduit autour du bas de l'ecran
            float k = 0.8f;
            g.pose().pushPose();
            g.pose().translate(w / 2f, h, 0);
            g.pose().scale(k, k, 1);
            g.pose().translate(-w / 2f, -h, 0);
            dessinerSpectateur(g, font, w, h);
            g.pose().popPose();
            dessinerComptePetit(g, font, w);
            dessinerAnnoncePetite(g, font, w, now);
        } else {
            dessinerSpectateur(g, font, w, h);
            dessinerCompte(g, font, w, h);
            dessinerAnnonce(g, font, w, h, now);
        }
    }

    // ---------------------------------------------------------------- bandeau
    @Nullable
    private static Hud.Bandeau bandeauCache;
    private static final TexteEspace TITRE_BANDEAU = new TexteEspace(Ui.SMALL, 1.2f);
    private static final TexteEspace SCORE = new TexteEspace(Ui.BOLD, 1.0f);
    private static MutableComponent[] nomsPastilles = new MutableComponent[0];
    private static MutableComponent[] comptesPastilles = new MutableComponent[0];
    private static int tempsMontre = Integer.MIN_VALUE;
    private static MutableComponent tempsC = Component.empty();
    private static final int PASTILLE_W = 96, PASTILLE_H = 22;

    private static void preparerBandeau(Hud.Bandeau b, Font font) {
        bandeauCache = b;
        TITRE_BANDEAU.texte(b.titre().getString());
        SCORE.texte(b.score().getString());
        int n = b.equipes().size();
        nomsPastilles = new MutableComponent[n];
        comptesPastilles = new MutableComponent[n];
        for (int i = 0; i < n; i++) {
            Hud.Pastille p = b.equipes().get(i);
            nomsPastilles[i] = Ui.lit(Ui.ellipsis(font, p.nom(), Ui.BOLD, PASTILLE_W - 34), Ui.BOLD);
            comptesPastilles[i] = Ui.lit(p.vivants() + "/" + p.total(), Ui.SMALL);
        }
        tempsMontre = Integer.MIN_VALUE;
    }

    private static void dessinerBandeau(GuiGraphics g, Font font, int w) {
        Hud.Bandeau b = MoteurClient.bandeau();
        if (b == null) return;
        if (b != bandeauCache) preparerBandeau(b, font);
        int cx = w / 2, y = 5;
        if (!TITRE_BANDEAU.source().isEmpty()) {
            int lt = (int) TITRE_BANDEAU.largeur() / 2 + 40;
            Ui.band(g, cx - lt, y - 3, cx + lt, y + 9, 0x000000, 120, 36);
            TITRE_BANDEAU.centre(g, cx, y, Ui.argb(0xC9CDD6, 255));
        }
        int ty = y + 10;
        int secs = MoteurClient.bandeauSecondes();
        int tw = 0;
        if (secs >= 0) {
            if (secs != tempsMontre) {
                tempsMontre = secs;
                tempsC = Ui.lit((secs / 60) + ":" + (secs % 60 < 10 ? "0" : "") + (secs % 60), Ui.BOLD);
            }
            tw = 52;
            Ui.glass(g, cx - tw / 2, ty, tw, PASTILLE_H, 190);
            int col = secs <= 10 ? Ui.DANGER : Ui.TEXT;
            g.pose().pushPose();
            g.pose().translate(cx, ty + 5, 0);
            g.pose().scale(1.4f, 1.4f, 1);
            g.drawString(font, tempsC, -font.width(tempsC) / 2, 0, Ui.argb(col, 255), false);
            g.pose().popPose();
        }
        List<Hud.Pastille> eq = b.equipes();
        int n = eq.size();
        int gap = 6;
        if (n <= 2) {
            if (n >= 1) pastille(g, font, cx - tw / 2 - gap - PASTILLE_W, ty, eq.get(0), 0, false);
            if (n == 2) pastille(g, font, cx + tw / 2 + gap, ty, eq.get(1), 1, true);
        } else {
            int total = n * PASTILLE_W + (n - 1) * 4;
            int x0 = cx - total / 2;
            for (int i = 0; i < n; i++) pastille(g, font, x0 + i * (PASTILLE_W + 4), ty + PASTILLE_H + 4, eq.get(i), i, false);
        }
        if (!SCORE.source().isEmpty()) {
            int sy = ty + PASTILLE_H + (n > 2 ? PASTILLE_H + 8 : 4);
            SCORE.centre(g, cx, sy, Ui.argb(Ui.TEXT, 230));
        }
    }

    private static void pastille(GuiGraphics g, Font font, int x, int y, Hud.Pastille p, int i, boolean barreADroite) {
        Ui.glass(g, x, y, PASTILLE_W, PASTILLE_H, 170);
        int c = p.couleur();
        if (barreADroite) g.fill(x + PASTILLE_W - 3, y + 3, x + PASTILLE_W - 1, y + PASTILLE_H - 3, Ui.argb(c, 255));
        else g.fill(x + 1, y + 3, x + 3, y + PASTILLE_H - 3, Ui.argb(c, 255));
        int tx = x + 8;
        if (i < nomsPastilles.length) g.drawString(font, nomsPastilles[i], tx, y + 3, Ui.argb(Ui.mix(c, 0xFFFFFF, 0.55f), 255), false);
        if (i < comptesPastilles.length) {
            MutableComponent cc = comptesPastilles[i];
            g.drawString(font, cc, x + PASTILLE_W - 8 - font.width(cc), y + 4, Ui.argb(Ui.MUTED, 255), false);
        }
        int tot = Math.min(8, p.total());
        for (int k = 0; k < tot; k++) {
            int dx = tx + k * 7, dy = y + PASTILLE_H - 7;
            boolean vivant = k < p.vivants();
            Ui.soft(g, dx, dy, 5, 3, vivant ? Ui.argb(c, 255) : Ui.argb(0xFFFFFF, 34));
        }
    }

    // ---------------------------------------------------------------- annonce
    @Nullable
    private static Annonces.Annonce courante;
    private static long debutAnnonce;
    private static final TexteEspace TITRE_ANNONCE = new TexteEspace(Ui.BOLD, 1.6f);
    private static MutableComponent sousTitre = Component.empty();

    /**
     * Fait avancer la file des annonces (activation, son, expiration) et rend l'annonce en cours, ou null. Appele aussi
     * quand un de nos ecrans masque le HUD : une annonce recue pendant l'ecran de choix s'ecoule derriere lui au lieu
     * de s'afficher en retard par-dessus le compte a rebours.
     */
    @Nullable
    private static Annonces.Annonce avancerAnnonce(long now) {
        Annonces.Annonce a = MoteurClient.annonces().peekFirst();
        if (a == null) {
            courante = null;
            return null;
        }
        if (a != courante) {
            courante = a;
            debutAnnonce = now;
            TITRE_ANNONCE.texte(a.titre().getString());
            sousTitre = Ui.in(a.sousTitre(), Ui.FONT);
            TITRE_PETIT.texte(a.titre().getString());
            SOUS_PETIT.texte(a.sousTitre().getString());
            SoundEvent s = son(a.son());
            if (s != null) jouer(s, 1f);
        }
        if ((now - debutAnnonce) / 50f >= a.dureeTicks()) {
            MoteurClient.annonces().pollFirst();
            courante = null;
            return null;
        }
        return a;
    }

    private static void dessinerAnnonce(GuiGraphics g, Font font, int w, int h, long now) {
        Annonces.Annonce a = avancerAnnonce(now);
        if (a == null) return;
        // le compte a rebours a la priorite (meme zone de l'ecran) : l'annonce s'ecoule dessous sans etre dessinee
        if (MoteurClient.compteSecondes() > 0) return;
        float age = (now - debutAnnonce) / 50f;
        float alpha = Math.min(1f, age / 5f);
        float reste = a.dureeTicks() - age;
        if (reste < 12) alpha = Math.min(alpha, reste / 12f);
        float pop = 1f - Math.min(1f, age / 7f);
        pop = pop * pop;
        int accent = a.couleur();
        float echelle;
        int couleurTitre;
        switch (a.style()) {
            case VICTOIRE -> {
                echelle = 3.0f;
                couleurTitre = Ui.mix(Ui.OR, 0xFFFFFF, 0.25f);
            }
            case DEFAITE -> {
                echelle = 2.7f;
                couleurTitre = 0xC9CBD1;
            }
            case FINALE -> {
                echelle = 3.4f;
                couleurTitre = Ui.mix(accent, 0xFFFFFF, 0.35f);
            }
            case MANCHE -> {
                echelle = 4.2f;
                couleurTitre = 0xC8322D;
            }
            default -> {
                echelle = 2.6f;
                couleurTitre = 0xF4F5F8;
            }
        }
        // un titre long (« FINALE : [NOVA] CONTRE [RVN] 2 », « [NOVA] REMPORTE LE CLASHWAR ») sortait de l'ecran :
        // l'echelle est bornee pour que le titre tienne dans la largeur moins les marges
        float largeurTitre = Math.max(1f, TITRE_ANNONCE.largeur());
        echelle = Math.min(echelle, (w - 48f) / largeurTitre);
        echelle *= 1f + 0.35f * pop;
        int cx = w / 2, yc = (int) (h * 0.30f);
        int a255 = (int) (255 * alpha);
        // bande sombre et filets d'accent
        int demi = (int) (9 * echelle / 2.6f) + 14;
        Ui.band(g, 0, yc - demi, w, yc + demi + 12, 0x000000, (int) (120 * alpha), w / 3);
        int lf = (int) (Math.min(w * 0.42f, TITRE_ANNONCE.largeur() * echelle * 0.75f + 60) * (1f - pop * 0.6f));
        Ui.hairline(g, cx - lf, cx + lf, yc - demi + 2, accent, (int) (210 * alpha), true, true);
        Ui.hairline(g, cx - lf, cx + lf, yc + demi + 9, accent, (int) (150 * alpha), true, true);
        if (a.style() == Annonces.Style.FINALE || a.style() == Annonces.Style.VICTOIRE) {
            Ui.band(g, cx - lf, yc - 2, cx + lf, yc + 2, accent, (int) (60 * alpha), lf / 2);
        }
        // titre
        float jx = 0, jy = 0;
        if (a.style() == Annonces.Style.MANCHE && age < 10) {
            jx = (float) Math.sin(age * 3.1) * (10 - age) * 0.15f;
            jy = (float) Math.cos(age * 2.3) * (10 - age) * 0.1f;
        }
        g.pose().pushPose();
        g.pose().translate(cx + jx, yc - 4.5f * echelle + jy, 0);
        g.pose().scale(echelle, echelle, 1);
        if (a.style() == Annonces.Style.MANCHE) TITRE_ANNONCE.centre(g, 0.4f, 0.4f, Ui.argb(0x3A0A08, (int) (a255 * 0.8f)));
        TITRE_ANNONCE.centre(g, 0, 0, Ui.argb(couleurTitre, Math.max(4, a255)));
        g.pose().popPose();
        if (!a.sousTitre().getString().isEmpty()) {
            int cs = a.style() == Annonces.Style.DEFAITE ? 0x9AA0AA : Ui.mix(accent, 0xFFFFFF, 0.4f);
            g.pose().pushPose();
            g.pose().translate(cx, yc + 4.5f * echelle - 2, 0);
            g.pose().scale(1.25f, 1.25f, 1);
            g.drawString(font, sousTitre, -font.width(sousTitre) / 2, 0, Ui.argb(cs, Math.max(4, a255)), false);
            g.pose().popPose();
        }
    }

    /** Annonce en HUD compact : titre (capitales espacees) et sous-titre sur une seule ligne. */
    private static final fr.heralisz.matchs.client.kit.TexteCentre TITRE_PETIT = new fr.heralisz.matchs.client.kit.TexteCentre(Ui.BOLD, 1.0f);
    private static final fr.heralisz.matchs.client.kit.TexteCentre SOUS_PETIT = new fr.heralisz.matchs.client.kit.TexteCentre(Ui.FONT);

    /**
     * Annonce en HUD compact (maquette 1 du ClashWar validee le 21/10) : une pastille de verre d'une seule ligne en haut au
     * centre, sous le bandeau et la ligne d'etat du mode ({@link #Y_ANNONCE_COMPACTE}) : titre en capitales espacees de
     * la couleur de l'annonce, sous-titre plus discret a sa suite, contour fin de l'accent. Texte centre au dixieme de
     * pixel dans la pastille ({@code TexteCentre}) ; fondus doux, jamais au milieu de l'ecran.
     */
    private static void dessinerAnnoncePetite(GuiGraphics g, Font font, int w, long now) {
        Annonces.Annonce a = avancerAnnonce(now);
        if (a == null || MoteurClient.compteSecondes() > 0) return;
        float age = (now - debutAnnonce) / 50f;
        float alpha = Math.min(1f, age / 4f);
        float reste = a.dureeTicks() - age;
        if (reste < 10) alpha = Math.min(alpha, reste / 10f);
        if (alpha < 0.03f) return;
        int accent = a.couleur();
        int couleurTitre = switch (a.style()) {
            case VICTOIRE -> Ui.mix(Ui.OR, 0xFFFFFF, 0.25f);
            case DEFAITE -> Ui.mix(Ui.DANGER, 0xFFFFFF, 0.2f);
            case FINALE -> Ui.mix(accent, 0xFFFFFF, 0.35f);
            case MANCHE -> Ui.mix(0xC8322D, 0xFFFFFF, 0.15f);
            default -> Ui.mix(accent, 0xFFFFFF, 0.3f);
        };
        float k = 0.65f, ecart = 6f;
        boolean aSous = !SOUS_PETIT.source().isEmpty();
        float lt = TITRE_PETIT.largeur(k), ls = aSous ? SOUS_PETIT.largeur(k) : 0;
        float contenu = lt + (aSous ? ecart + ls : 0);
        int ph = 15, pw = (int) Math.min(w - 40, Math.ceil(contenu) + 20);
        int x = Math.round(w / 2f - pw / 2f), y = Y_ANNONCE_COMPACTE;
        int a255 = Math.max(4, (int) (255 * alpha));
        Ui.soft(g, x, y, pw, ph, Ui.argb(Ui.VERRE, (int) (160 * alpha)));
        Ui.softOutline(g, x + 1, y + 1, pw - 2, ph - 2, Ui.argb(accent, (int) (110 * alpha)));
        float cy = y + ph / 2f, tx = w / 2f - contenu / 2f;
        TITRE_PETIT.gauche(g, tx, cy, k, Ui.argb(couleurTitre, a255));
        if (aSous) SOUS_PETIT.gauche(g, tx + lt + ecart, cy, k, Ui.argb(0xB7BDC8, a255));
    }

    // ---------------------------------------------------------------- compte a rebours
    private static int compteMontre = Integer.MIN_VALUE;
    private static MutableComponent compteC = Component.empty();
    private static final TexteEspace TITRE_COMPTE = new TexteEspace(Ui.SMALL, 1.4f);
    private static final TexteEspace GO = new TexteEspace(Ui.BOLD, 1.6f);

    private static void dessinerCompte(GuiGraphics g, Font font, int w, int h) {
        int r = MoteurClient.compteSecondes();
        long fin = MoteurClient.depuisFinCompteMs();
        if (r < 0 && (fin < 0 || fin > 900)) {
            compteMontre = Integer.MIN_VALUE;
            return;
        }
        int cx = w / 2, cy = (int) (h * 0.46f);
        if (r > 0) {
            if (r != compteMontre) {
                compteMontre = r;
                compteC = Ui.lit(Integer.toString(r), Ui.BOLD);
                jouer(MoteurSons.COMPTE.get(), 0.7f);
                Component t = MoteurClient.compteTitre();
                TITRE_COMPTE.texte(t == null ? "" : t.getString());
            }
            float f = MoteurClient.compteFractionMs() / 1000f;
            float e = 1f - (1f - f) * (1f - f);
            float s = 3.6f + 1.4f * (1f - e);
            int a = (int) (255 * (1f - 0.35f * e));
            Ui.band(g, cx - 120, cy - 30, cx + 120, cy + 34, 0x000000, 90, 60);
            TITRE_COMPTE.centre(g, cx, cy - 26, Ui.argb(0xC9CDD6, 255));
            g.pose().pushPose();
            g.pose().translate(cx, cy - 4 * s + 6, 0);
            g.pose().scale(s, s, 1);
            g.drawString(font, compteC, -font.width(compteC) / 2, 0, Ui.argb(r <= 3 ? Ui.mix(Ui.DANGER, 0xFFFFFF, 0.25f) : Ui.TEXT, a), false);
            g.pose().popPose();
        } else {
            if (compteMontre != 0) {
                compteMontre = 0;
                GO.texte(Component.translatable("matchs.compte.go").getString());
                jouer(MoteurSons.DEPART.get(), 0.9f);
            }
            long d = r == 0 ? MoteurClient.compteFractionMs() : 1000 + Math.max(0, fin);
            float t = Math.min(1f, d / 1900f);
            int a = (int) (255 * (1f - t));
            float s = 2.8f + 0.6f * t;
            g.pose().pushPose();
            g.pose().translate(cx, cy - 4 * s + 6, 0);
            g.pose().scale(s, s, 1);
            GO.centre(g, 0, 0, Ui.argb(Ui.mix(Ui.accent(), 0xFFFFFF, 0.3f), Math.max(4, a)));
            g.pose().popPose();
        }
    }

    /** Compte a rebours en HUD compact : chiffre en gras dans un petit panneau, en haut au centre (meme place que les annonces). */
    private static void dessinerComptePetit(GuiGraphics g, Font font, int w) {
        int r = MoteurClient.compteSecondes();
        long fin = MoteurClient.depuisFinCompteMs();
        if (r < 0 && (fin < 0 || fin > 900)) {
            compteMontre = Integer.MIN_VALUE;
            return;
        }
        int cx = w / 2, y = Y_ANNONCE_COMPACTE;
        if (r > 0) {
            if (r != compteMontre) {
                compteMontre = r;
                compteC = Ui.lit(Integer.toString(r), Ui.BOLD);
                jouer(MoteurSons.COMPTE.get(), 0.7f);
                Component t = MoteurClient.compteTitre();
                TITRE_COMPTE.texte(t == null ? "" : t.getString());
            }
            int pw = (int) Math.max(46, TITRE_COMPTE.largeur() * 0.8f + 18);
            Ui.glass(g, cx - pw / 2, y, pw, 26, 150);
            g.pose().pushPose();
            g.pose().translate(cx, y + 3, 0);
            g.pose().scale(0.8f, 0.8f, 1);
            TITRE_COMPTE.centre(g, 0, 0, Ui.argb(0xC9CDD6, 230));
            g.pose().popPose();
            g.pose().pushPose();
            g.pose().translate(cx, y + 12, 0);
            g.pose().scale(1.1f, 1.1f, 1);
            g.drawString(font, compteC, -font.width(compteC) / 2, 0, Ui.argb(r <= 3 ? Ui.mix(Ui.DANGER, 0xFFFFFF, 0.25f) : Ui.TEXT, 235), false);
            g.pose().popPose();
        } else {
            if (compteMontre != 0) {
                compteMontre = 0;
                GO.texte(Component.translatable("matchs.compte.go").getString());
                jouer(MoteurSons.DEPART.get(), 0.9f);
            }
            long d = r == 0 ? MoteurClient.compteFractionMs() : 1000 + Math.max(0, fin);
            float t = Math.min(1f, d / 1900f);
            int a = (int) (230 * (1f - t));
            GO.centre(g, cx, y + 4, Ui.argb(Ui.mix(Ui.accent(), 0xFFFFFF, 0.3f), Math.max(4, a)));
        }
    }

    // ---------------------------------------------------------------- fil des eliminations
    /** Ligne mise en forme (une fois, a l'arrivee). */
    private static final class Rendu {
        KillFeed.Ligne ligne;
        MutableComponent tueur, victime, arme;
        int wt, wv, wa, largeur;
    }

    private static final Rendu[] RENDUS = new Rendu[MoteurClient.FIL_MAX];
    private static final long FIL_DUREE = 5000, FIL_FONDU = 500;

    @Nullable
    private static Rendu rendu(KillFeed.Ligne l, Font font) {
        for (Rendu r : RENDUS) if (r != null && r.ligne == l) return r;
        int libre = -1;
        for (int i = 0; i < RENDUS.length; i++) {
            if (RENDUS[i] == null || !MoteurClient.fil().contains(RENDUS[i].ligne)) {
                libre = i;
                break;
            }
        }
        if (libre < 0) return null;
        Rendu r = new Rendu();
        r.ligne = l;
        r.tueur = Ui.lit(nomAffiche(l.tueur()), Ui.BOLD);
        r.victime = Ui.lit(nomAffiche(l.victime()), Ui.BOLD);
        r.arme = Ui.lit(l.arme(), Ui.SMALL);
        r.wt = l.tueur().isEmpty() ? 0 : font.width(r.tueur);
        r.wv = font.width(r.victime);
        r.wa = l.arme().isEmpty() ? 0 : font.width(r.arme);
        r.largeur = 10 + (r.wt > 0 ? r.wt + 12 : 10) + r.wv + (r.wa > 0 ? r.wa + 10 : 0) + (l.tete() ? 11 : 0);
        RENDUS[libre] = r;
        return r;
    }

    private static void dessinerFil(GuiGraphics g, Font font, int w, int yHaut, long now) {
        // retrait des lignes expirees (en tete : les plus anciennes)
        while (!MoteurClient.filMs().isEmpty() && now - MoteurClient.filMs().peekFirst() > FIL_DUREE) {
            MoteurClient.fil().pollFirst();
            MoteurClient.filMs().pollFirst();
        }
        if (MoteurClient.fil().isEmpty()) return;
        Iterator<KillFeed.Ligne> it = MoteurClient.fil().iterator();
        Iterator<Long> itMs = MoteurClient.filMs().iterator();
        int y = yHaut;
        int xr = w - 6;
        while (it.hasNext() && itMs.hasNext()) {
            KillFeed.Ligne l = it.next();
            long ms = itMs.next();
            Rendu r = rendu(l, font);
            if (r == null) continue;
            long age = now - ms;
            float a = Math.min(1f, age / 150f);
            if (age > FIL_DUREE - FIL_FONDU) a = Math.max(0f, (FIL_DUREE - age) / (float) FIL_FONDU);
            int a255 = Math.max(4, (int) (255 * a));
            int x = xr - r.largeur;
            Ui.glass(g, x, y, r.largeur, 14, (int) (160 * a));
            int tx = x + 5;
            if (r.wt > 0) {
                g.drawString(font, r.tueur, tx, y + 3, Ui.argb(Ui.mix(l.couleurTueur(), 0xFFFFFF, 0.35f), a255), false);
                tx += r.wt + 4;
                fleche(g, tx, y + 4, Ui.argb(Ui.MUTED, a255));
                tx += 8;
            } else {
                // environnement : petit crane stylise (deux points)
                Ui.soft(g, tx, y + 4, 5, 5, Ui.argb(Ui.MUTED, a255));
                tx += 10;
            }
            g.drawString(font, r.victime, tx, y + 3, Ui.argb(Ui.mix(l.couleurVictime(), 0xFFFFFF, 0.35f), a255), false);
            tx += r.wv;
            if (r.wa > 0) {
                tx += 4;
                g.fill(tx, y + 6, tx + 2, y + 8, Ui.argb(Ui.FAINT, a255));
                tx += 6;
                g.drawString(font, r.arme, tx, y + 4, Ui.argb(Ui.MUTED, a255), false);
                tx += r.wa;
            }
            if (l.tete()) mire(g, tx + 3, y + 3, Ui.argb(Ui.OR, a255));
            y += 16;
        }
    }

    /** Petite fleche ▸ dessinee (3 x 5). */
    private static void fleche(GuiGraphics g, int x, int y, int argb) {
        g.fill(x, y, x + 1, y + 5, argb);
        g.fill(x + 1, y + 1, x + 2, y + 4, argb);
        g.fill(x + 2, y + 2, x + 3, y + 3, argb);
    }

    /** Mire du tir a la tete (7 x 7). */
    private static void mire(GuiGraphics g, int x, int y, int argb) {
        g.fill(x + 3, y, x + 4, y + 2, argb);
        g.fill(x + 3, y + 5, x + 4, y + 7, argb);
        g.fill(x, y + 3, x + 2, y + 4, argb);
        g.fill(x + 5, y + 3, x + 7, y + 4, argb);
        g.fill(x + 3, y + 3, x + 4, y + 4, argb);
    }

    /**
     * Bas (y, echelle du joueur) de ce que le moteur dessine dans le coin haut droit : numero de manche des zombies
     * ({@link #hautFil}), fil des eliminations, invitation de groupe ; 0 si rien. Les autres HUD de ce coin (defis de
     * duel) se placent dessous. Meme calcul que {@link #dessiner}, sans rien dessiner ni allouer.
     */
    public static int basHautDroit(int h, long now) {
        int yFil = hautFil.applyAsInt(h);
        int bas = Math.max(0, yFil);
        if (yFil < 0) yFil = 6;
        int lignes = MoteurClient.fil().size();
        if (lignes > 0) bas = Math.max(bas, yFil + lignes * 16 - 2);
        MoteurClient.Toast t = MoteurClient.toasts().peekLast();
        if (t != null && now - t.recuMs() <= TOAST_DUREE) bas = Math.max(bas, yFil + lignes * 16 + 4 + 40);
        return bas;
    }

    // ---------------------------------------------------------------- toasts
    private static final long TOAST_DUREE = 8000;
    private static final TexteEspace TOAST_TITRE = new TexteEspace(Ui.SMALL, 1.0f);
    @Nullable
    private static MoteurClient.Toast toastPrepare;
    private static MutableComponent toastLigne = Component.empty(), toastSous = Component.empty();

    private static void dessinerToasts(GuiGraphics g, Font font, int w, int yHaut, long now) {
        while (!MoteurClient.toasts().isEmpty() && now - MoteurClient.toasts().peekFirst().recuMs() > TOAST_DUREE) MoteurClient.toasts().pollFirst();
        MoteurClient.Toast t = MoteurClient.toasts().peekLast();
        if (t == null) return;
        if (t != toastPrepare) {
            toastPrepare = t;
            TOAST_TITRE.texte(Component.translatable("matchs.groupe.toast.titre").getString());
            toastLigne = Ui.tr(Ui.FONT, "matchs.groupe.toast.texte", t.de());
            toastSous = Ui.tr(Ui.SMALL, "matchs.groupe.toast.sous");
        }
        long age = now - t.recuMs();
        float a = Math.min(1f, age / 250f);
        if (age > TOAST_DUREE - 500) a = Math.max(0, (TOAST_DUREE - age) / 500f);
        int tw = 164, th = 40;
        int x = w - tw - 6 + (int) ((1f - Math.min(1f, age / 250f)) * 30), y = yHaut + MoteurClient.fil().size() * 16 + 4;
        int a255 = Math.max(4, (int) (255 * a));
        Ui.glass(g, x, y, tw, th, (int) (200 * a));
        int acc = Ui.accent();
        g.fill(x + 1, y + 3, x + 3, y + th - 3, Ui.argb(acc, a255));
        TOAST_TITRE.dessiner(g, x + 9, y + 5, Ui.argb(Ui.mix(acc, 0xFFFFFF, 0.3f), a255));
        g.drawString(font, toastLigne, x + 9, y + 15, Ui.argb(Ui.TEXT, a255), false);
        g.drawString(font, toastSous, x + 9, y + 27, Ui.argb(Ui.MUTED, a255), false);
        long reste = Math.max(0, t.secondes() * 1000L - age);
        Ui.bar(g, x + 9, y + th - 3, tw - 18, 1, reste / (t.secondes() * 1000f), acc);
    }

    // ---------------------------------------------------------------- tag de combat
    private static int combatMontre = -1;
    private static MutableComponent combatC = Component.empty();
    /** Largeur des secondes affichees et largeur de reference « 00 s » (la pastille ne change pas de taille). */
    private static int combatLargeurValeur, combatLargeurRef;
    private static final TexteEspace COMBAT_TITRE = new TexteEspace(Ui.SMALL, 1.0f);
    private static long combatDebut;

    /**
     * Indicateur discret du tag de combat (hors match), coin bas droit au-dessus du compteur de munitions de TaCZ :
     * point rouge qui pulse, « EN COMBAT » en petites capitales espacees, secondes en gras, jauge fine qui se vide ;
     * fondu a l'apparition et dans la derniere demi-seconde. Aucun message dans le chat.
     */
    private static void dessinerCombat(GuiGraphics g, Font font, int w, int h) {
        long reste = MoteurClient.combatRestantMs();
        if (reste <= 0 || MoteurClient.bandeau() != null || MoteurClient.enMatch()) {
            combatMontre = -1;
            return;
        }
        long now = Util.getMillis();
        int s = (int) ((reste + 999) / 1000);
        if (s != combatMontre) {
            if (combatMontre < 0) {
                combatDebut = now;
                COMBAT_TITRE.texte(Component.translatable("matchs.hud.combat_titre").getString());
                combatLargeurRef = font.width(Ui.lit("00 s", Ui.BOLD));
            }
            combatMontre = s;
            combatC = Ui.lit(s + " s", Ui.BOLD);
            combatLargeurValeur = font.width(combatC);
        }
        int cw = 17 + (int) Math.ceil(COMBAT_TITRE.largeur()) + 10 + Math.max(combatLargeurRef, combatLargeurValeur) + 8, ch = 19;
        int x = w - 8 - cw, y = h - 50 - ch;
        float a = Math.min(Math.min(1f, (now - combatDebut) / 180f), Math.min(1f, reste / 450f));
        int a255 = Math.max(4, (int) (255 * a));
        Ui.glass(g, x, y, cw, ch, (int) (180 * a));
        float pulse = 0.6f + 0.4f * (float) Math.cos((now % 1000) / 1000.0 * Math.PI * 2);
        Ui.soft(g, x + 7, y + 6, 5, 5, Ui.argb(Ui.DANGER, (int) (a255 * pulse)));
        COMBAT_TITRE.dessiner(g, x + 17, y + 5.5f, Ui.argb(Ui.mix(Ui.DANGER, 0xFFFFFF, 0.5f), a255));
        g.drawString(font, combatC, x + cw - 8 - combatLargeurValeur, y + 5, Ui.argb(Ui.TEXT, a255), false);
        if (a > 0.5f) Ui.bar(g, x + 6, y + ch - 4, cw - 12, 1, MoteurClient.combatRatio(), Ui.DANGER);
    }

    // ---------------------------------------------------------------- spectateur
    private static final TexteEspace SPEC_TITRE = new TexteEspace(Ui.SMALL, 1.2f);
    private static String specCible = null;
    private static MutableComponent specNom = Component.empty(), specAide = Component.empty();

    private static void dessinerSpectateur(GuiGraphics g, Font font, int w, int h) {
        if (!MoteurClient.spectateur()) return;
        String cible = MoteurClient.spectateurCible();
        if (!cible.equals(specCible)) {
            specCible = cible;
            SPEC_TITRE.texte(Component.translatable("matchs.spectateur.titre").getString());
            specNom = cible.isEmpty() ? Ui.tr(Ui.BOLD, "matchs.spectateur.libre") : Ui.lit(cible, Ui.BOLD);
            specAide = Ui.tr(Ui.SMALL, "matchs.spectateur.aide");
        }
        int bw = Math.max(170, font.width(specNom) + 70), bh = 34;
        int x = w / 2 - bw / 2, y = h - bh - 22;
        Ui.glass(g, x, y, bw, bh, 190);
        SPEC_TITRE.centre(g, w / 2f, y + 5, Ui.argb(Ui.MUTED, 255));
        int col = cible.isEmpty() ? Ui.TEXT : Ui.mix(MoteurClient.spectateurCouleur(), 0xFFFFFF, 0.35f);
        g.pose().pushPose();
        g.pose().translate(w / 2f, y + 15, 0);
        g.pose().scale(1.2f, 1.2f, 1);
        g.drawString(font, specNom, -font.width(specNom) / 2, 0, Ui.argb(col, 255), false);
        g.pose().popPose();
        if (MoteurClient.spectateurNombre() > 1) {
            int a = Ui.argb(Ui.TEXT, 200);
            // fleches gauche / droite dessinees
            int ly = y + 15;
            g.fill(x + 12, ly + 2, x + 13, ly + 3, a);
            g.fill(x + 13, ly + 1, x + 14, ly + 4, a);
            g.fill(x + 14, ly, x + 15, ly + 5, a);
            fleche(g, x + bw - 15, ly, a);
        }
        g.drawString(font, specAide, w / 2 - font.width(specAide) / 2, y + bh + 3, Ui.argb(Ui.FAINT, 255), false);
    }

    /** Journal (debug) : etat du HUD. */
    public static void journal() {
        Annonces.Annonce a = MoteurClient.annonces().peekFirst();
        HeraliszMatchs.LOG.info("[matchs] HUD : bandeau {}, annonces {} (premiere {} {}, age {} ticks), fil {}, toasts {}, combat {} s"
                        + " (jauge {}), en match {}, cartes masquees {}",
                MoteurClient.bandeau() != null, MoteurClient.annonces().size(), a == null ? "-" : a.titre().getString(),
                a == null ? "" : a.style(), courante == null ? -1 : (Util.getMillis() - debutAnnonce) / 50, MoteurClient.fil().size(),
                MoteurClient.toasts().size(), MoteurClient.combatSecondes(), String.format(java.util.Locale.ROOT, "%.2f", MoteurClient.combatRatio()),
                MoteurClient.enMatch(), MoteurClient.cartesMasquees());
    }

    /**
     * Nom du fil des eliminations : un pseudo tel quel, ou une cle de traduction envoyee par le serveur (cible du mode
     * entrainement : aucun pseudo ne contient de point).
     */
    public static String nomAffiche(String nom) {
        return nom.indexOf('.') > 0 && I18n.exists(nom) ? I18n.get(nom) : nom;
    }
}
