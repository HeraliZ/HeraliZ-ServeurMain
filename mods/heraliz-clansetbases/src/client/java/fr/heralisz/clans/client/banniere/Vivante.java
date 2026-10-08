package fr.heralisz.clans.client.banniere;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.heralisz.clans.HeraliszClans;
import fr.heralisz.clans.model.Banniere;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Banniere « vivante » de l'atelier et de l'apercu de creation (spec §3) : une banniere qui change a chaque geste.
 * <p>
 * Strategie : la composition se fait sur un fil de fond (« HeraliZ banniere ») ; le fil de rendu ne fait que
 * l'envoi GL du resultat (une texture, une fois par composition) et le blit. Pendant une manipulation (cle changee il y a
 * moins de {@value #CALME_MS} ms), la composition est faite a demi-resolution (4 fois moins de pixels, affichee en
 * filtrage lineaire) ; {@value #CALME_MS} ms apres le dernier changement, la pleine resolution suit. Une seule
 * composition en vol a la fois : si la banniere change pendant qu'elle tourne, la suivante part des que la premiere est
 * envoyee (la plus recente gagne, les etats intermediaires sont sautes).
 * <p>
 * Seul le tout premier affichage attend la composition (demi-resolution) pour ne jamais montrer un cadre vide.
 * Mesures : temps passe sur le fil de rendu et temps de composition, journalises par fenetres de 2 s pendant les
 * manipulations quand le banc d'essai tourne (« [clans] banniere vivante »), lisibles par {@link #mesures()}.
 */
@OnlyIn(Dist.CLIENT)
public final class Vivante {
    private Vivante() {
    }

    /** Delai sans changement avant la pleine resolution. */
    public static final long CALME_MS = 120;
    /** En dessous de ce nombre de pixels, la pleine resolution est assez rapide : pas de demi-resolution. */
    private static final int SEUIL_DEMI = 160 * 240;
    private static final ResourceLocation PLEINE = HeraliszClans.id("banniere/vivante");
    private static final ResourceLocation DEMI = HeraliszClans.id("banniere/vivante_demi");
    private static final boolean BANC = System.getProperty("heralisz.clans.harness") != null;

    @Nullable
    private static TextureBanniere pleine, demi;
    @Nullable
    private static ExecutorService fil;
    @Nullable
    private static Future<?> enVol;
    @Nullable
    private static Tache tache;

    /** Derniere cle vue (et quand elle a change) ; ce qui est affiche. */
    private static String vueCle = "";
    private static long changement;
    private static String afficheCle = "";
    private static int afficheW, afficheH;
    private static boolean afficheDemi, rien = true;

    /** Une composition : entree immuable, sortie dans les tampons du fil de fond (lus apres la fin seulement). */
    private static final class Tache implements Runnable {
        final Banniere banniere;
        final String cle;
        final int cibleW, cibleH, w, h;
        final boolean demi;
        final byte[] atlas;
        long nanos;

        Tache(Banniere b, int cibleW, int cibleH, boolean demi, byte[] atlas) {
            this.banniere = b;
            this.cle = b.cle();
            this.cibleW = cibleW;
            this.cibleH = cibleH;
            this.demi = demi;
            this.w = demi ? Math.max(1, (cibleW + 1) / 2) : cibleW;
            this.h = demi ? Math.max(1, (cibleH + 1) / 2) : cibleH;
            this.atlas = atlas;
        }

        @Override
        public void run() {
            long t0 = System.nanoTime();
            int n = Rendu.emballer(banniere, FOND_CALQUES);
            int np = w * h;
            if (fondPixels.length < np) fondPixels = new int[np];
            FOND.banniere(fondPixels, w, h, banniere.couleurA, banniere.couleurB, banniere.degrade.ordinal(), FOND_CALQUES, n, atlas);
            fondTampon = Rendu.convertir(fondPixels, np, fondTampon);
            nanos = System.nanoTime() - t0;
        }
    }

    // tampons du fil de fond (jamais touches par le fil de rendu pendant une composition)
    private static final Composeur FOND = new Composeur();
    private static final int[] FOND_CALQUES = new int[Composeur.MAX_CALQUES * Composeur.CHAMPS];
    private static int[] fondPixels = new int[0];
    @Nullable
    private static ByteBuffer fondTampon;

    // mesures (fil de rendu)
    private static long fenetreDebut, rendusNanos, rendusMax, compoNanos, compoMax;
    private static int images, compositions, compositionsDemi;
    private static String derniereMesure = "";

    /** Dessine la banniere vivante dans (x, y, w, h) GUI, composee a (W, H) pixels physiques. */
    public static void dessiner(GuiGraphics g, Banniere b, int x, int y, int w, int h, int W, int H) {
        long t0 = System.nanoTime();
        long now = Util.getMillis();
        String cle = b.cle();
        if (!cle.equals(vueCle)) {
            vueCle = cle;
            changement = now;
        }
        recuperer();
        boolean pleineAJour = !afficheDemi && afficheCle.equals(cle) && afficheW == W && afficheH == H;
        if (enVol == null && !pleineAJour) {
            boolean bouge = now - changement < CALME_MS && W * H > SEUIL_DEMI;
            boolean demiAJour = afficheDemi && afficheCle.equals(cle) && afficheW == W && afficheH == H;
            if (!(bouge && demiAJour)) lancer(b, W, H, bouge || rien && W * H > SEUIL_DEMI);
        }
        if (rien && enVol != null) {
            // tout premier affichage : on attend (une fois) plutot que de montrer un cadre vide
            try {
                enVol.get();
            } catch (Exception ignored) {
            }
            recuperer();
        }
        if (!rien) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            if (afficheDemi && demi != null) g.blit(DEMI, x, y, w, h, 0, 0, demi.largeur(), demi.hauteur(), demi.largeur(), demi.hauteur());
            else if (pleine != null) g.blit(PLEINE, x, y, w, h, 0, 0, pleine.largeur(), pleine.hauteur(), pleine.largeur(), pleine.hauteur());
        }
        mesurer(System.nanoTime() - t0, now);
    }

    /** Envoie le resultat d'une composition terminee (fil de rendu). */
    private static void recuperer() {
        if (enVol == null || !enVol.isDone()) return;
        Tache t = tache;
        try {
            enVol.get();
        } catch (Exception ex) {
            HeraliszClans.LOG.error("[clans] composition de la banniere vivante", ex);
            t = null;
        }
        enVol = null;
        tache = null;
        if (t == null || fondTampon == null) return;
        TextureBanniere tex = t.demi ? demi() : pleine();
        tex.envoyer(fondTampon, t.w, t.h);
        afficheCle = t.cle;
        afficheW = t.cibleW;
        afficheH = t.cibleH;
        afficheDemi = t.demi;
        rien = false;
        compositions++;
        if (t.demi) compositionsDemi++;
        compoNanos += t.nanos;
        compoMax = Math.max(compoMax, t.nanos);
    }

    private static void lancer(Banniere b, int W, int H, boolean demi) {
        if (fil == null) {
            fil = Executors.newSingleThreadExecutor(r -> {
                Thread th = new Thread(r, "HeraliZ banniere");
                th.setDaemon(true);
                th.setPriority(Thread.NORM_PRIORITY - 1);
                return th;
            });
        }
        tache = new Tache(b, W, H, demi, AtlasSdf.octets());
        enVol = fil.submit(tache);
    }

    private static TextureBanniere pleine() {
        if (pleine == null) {
            pleine = new TextureBanniere(false);
            Minecraft.getInstance().getTextureManager().register(PLEINE, pleine);
        }
        return pleine;
    }

    private static TextureBanniere demi() {
        if (demi == null) {
            demi = new TextureBanniere(true);
            Minecraft.getInstance().getTextureManager().register(DEMI, demi);
        }
        return demi;
    }

    private static void mesurer(long nanos, long now) {
        images++;
        rendusNanos += nanos;
        rendusMax = Math.max(rendusMax, nanos);
        if (now - fenetreDebut < 2000) return;
        if (compositions > 0) {
            derniereMesure = String.format(java.util.Locale.ROOT,
                    "rendu %.3f ms/image (max %.3f) sur %d images ; composition %.2f ms (max %.2f), %d dont %d a demi-resolution",
                    rendusNanos / 1e6 / images, rendusMax / 1e6, images, compoNanos / 1e6 / compositions, compoMax / 1e6,
                    compositions, compositionsDemi);
            if (BANC) HeraliszClans.LOG.info("[clans] banniere vivante : {}", derniereMesure);
        }
        fenetreDebut = now;
        images = compositions = compositionsDemi = 0;
        rendusNanos = rendusMax = compoNanos = compoMax = 0;
    }

    /** Derniere fenetre de mesure (2 s avec au moins une composition), vide sinon. */
    public static String mesures() {
        return derniereMesure;
    }

    /** Oublie les textures (deconnexion, rechargement) ; attend la composition en vol pour ne rien laisser derriere. */
    public static void vider() {
        if (enVol != null) {
            try {
                enVol.get();
            } catch (Exception ignored) {
            }
        }
        enVol = null;
        tache = null;
        Minecraft mc = Minecraft.getInstance();
        if (pleine != null) {
            mc.getTextureManager().release(PLEINE);
            pleine.close();
            pleine = null;
        }
        if (demi != null) {
            mc.getTextureManager().release(DEMI);
            demi.close();
            demi = null;
        }
        vueCle = afficheCle = "";
        afficheDemi = false;
        rien = true;
        fondPixels = new int[0];
        fondTampon = null;
    }
}
