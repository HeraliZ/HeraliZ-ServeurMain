package fr.heraliz.armory.client.cible;

import com.mojang.blaze3d.platform.Window;
import fr.heraliz.armory.HeralizArmory;
import fr.heraliz.armory.network.PaquetsCible;
import fr.heraliz.noyau.client.kit.Formes;
import fr.heraliz.noyau.client.kit.Palette;
import fr.heraliz.noyau.client.kit.PoliceHud;
import fr.heraliz.noyau.client.kit.TexteHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Chiffres de dégâts au-dessus de la cible d'essai (03/11, demande du propriétaire) : à chaque touche, le nombre de dégâts
 * reçus (ce qu'aurait perdu un joueur) s'écrit juste au-dessus de sa tête, monte d'un demi-bloc et s'efface en 1 s. Tête :
 * doré ; corps, mêlée, explosion : blanc. Les plombs d'un même tir de fusil à pompe s'additionnent en un seul chiffre. Vu
 * du tireur seulement (seul lui reçoit les coups). Écrits sur l'écran depuis le 18/11 (voir plus bas). Aucun coût sans
 * chiffre en cours : les écouteurs sortent tout de suite ; au plus {@link #MAX} chiffres à la fois.
 * <p>08/11 : aussi les dégâts infligés à un AUTRE JOUEUR (PaquetsCible.Degats, décidés par le serveur), même dessin en plus
 * petit et plus bas, juste au-dessus de sa tête ; doré à la tête ; plombs d'un même tir additionnés.</p>
 * <p>18/11 (retour du propriétaire : « les nombres qui apparaissent sont flous, on dirait qu'ils sont dessinés avec du code ;
 * je veux un truc bien propre, en gras, bien visible, net ») : ils étaient écrits DANS le monde, en tout petit (une lettre
 * tracée 8 fois plus fin, réduite par la carte graphique selon la distance : traits grignotés, bords qui scintillent). Ils
 * sont maintenant écrits SUR L'ÉCRAN, à l'endroit du monde où ils apparaissent : Oswald Bold du HUD (la police des chiffres
 * du noyau) à sa vraie taille, posée sur les pixels de l'écran, contour sombre d'un pixel (lisible sur la neige comme sur un
 * mur sombre). Même taille à toute distance ; cachés derrière la caméra. La position à l'écran est calculée pendant le
 * rendu du monde ({@link #placer}), le dessin se fait avec le HUD ({@link #dessiner}).</p>
 */
@Mod.EventBusSubscriber(modid = HeralizArmory.MOD_ID, value = Dist.CLIENT)
public final class ChiffresCible {
    private ChiffresCible() {}

    private static final long DUREE_MS = 1000L;
    private static final double MONTEE = 0.5D, AU_DESSUS = 0.35D;
    private static final int MAX = 16;
    private static final int OR = 0xFFC94A, BLANC = 0xFFFFFF, CONTOUR = 0x0A0B0E;
    /** Polices des chiffres : cible d'essai (Oswald Bold 11), joueurs touchés (Oswald Bold 8, plus discrets). */
    private static final PoliceHud TAILLE = PoliceHud.TITRE, TAILLE_JOUEUR = PoliceHud.CHIFFRE_PETIT;
    private static final double AU_DESSUS_JOUEUR = 0.15D;
    /** Montée à l'écran (unités d'interface) pendant la vie du chiffre, en plus de sa montée dans le monde. */
    private static final float MONTEE_ECRAN = 6F;

    private static final class Chiffre {
        final double x, y, z;
        final long tir;
        final int entite;
        float valeur;
        long debut;
        int couleur;
        final TexteHud texte;
        /** Place à l'écran (unités d'interface) calculée à l'image en cours ; faux : derrière la caméra. */
        float sx, sy;
        boolean visible;

        Chiffre(double x, double y, double z, int entite, long tir, float valeur, long debut, int couleur, PoliceHud police) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.entite = entite;
            this.tir = tir;
            this.valeur = valeur;
            this.debut = debut;
            this.couleur = couleur;
            this.texte = new TexteHud(police).texte(TextesCible.nombre(valeur));
        }
    }

    private static final List<Chiffre> CHIFFRES = new ArrayList<>();

    /** Un coup reçu du serveur (fil du jeu). */
    static void ajouter(PaquetsCible.Touche t) {
        if (t.plafond() && t.recu() <= 0.0F && !memeTir(t.entite(), t.tir())) return;
        ajouter(t.entite(), t.tir(), t.recu(), t.zone() == PaquetsCible.TETE, TAILLE, AU_DESSUS);
    }

    /** Dégâts infligés à un autre joueur (08/11), fil du jeu. */
    static void ajouter(PaquetsCible.Degats d) {
        if (d.recu() <= 0.0F) return;
        ajouter(d.entite(), d.tir(), d.recu(), d.tete(), TAILLE_JOUEUR, AU_DESSUS_JOUEUR);
    }

    private static boolean memeTir(int entite, long tir) {
        if (tir < 0 || CHIFFRES.isEmpty()) return false;
        Chiffre d = CHIFFRES.get(CHIFFRES.size() - 1);
        return d.entite == entite && d.tir == tir;
    }

    private static void ajouter(int entite, long tir, float recu, boolean tete, PoliceHud taille, double auDessus) {
        long maintenant = System.currentTimeMillis();
        int couleur = tete ? OR : BLANC;
        // plomb d'un tir déjà affiché : même chiffre, additionné (plafond atteint : rien de plus)
        if (memeTir(entite, tir)) {
            Chiffre d = CHIFFRES.get(CHIFFRES.size() - 1);
            d.valeur += recu;
            d.texte.texte(TextesCible.nombre(d.valeur));
            d.debut = maintenant;
            if (couleur == OR) d.couleur = OR;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Entity cible = mc.level == null ? null : mc.level.getEntity(entite);
        if (cible == null) return;
        // un peu de côté au hasard, pour que deux chiffres de suite ne se recouvrent pas
        double dx = (mc.level.random.nextDouble() - 0.5D) * 0.4D, dz = (mc.level.random.nextDouble() - 0.5D) * 0.4D;
        if (CHIFFRES.size() >= MAX) CHIFFRES.remove(0);
        CHIFFRES.add(new Chiffre(cible.getX() + dx, cible.getY() + cible.getBbHeight() + auDessus, cible.getZ() + dz, entite, tir,
                recu, maintenant, couleur, taille));
    }

    private static final Matrix4f VUE = new Matrix4f();
    private static final Vector4f POINT = new Vector4f();

    /**
     * Pendant le rendu du monde : place à l'écran de chaque chiffre (projection de la caméra, balancement de la marche
     * compris), en unités d'interface. Rien n'est alloué.
     */
    @SubscribeEvent
    public static void placer(RenderLevelStageEvent e) {
        if (CHIFFRES.isEmpty() || e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        long maintenant = System.currentTimeMillis();
        CHIFFRES.removeIf(c -> maintenant - c.debut > DUREE_MS);
        if (CHIFFRES.isEmpty()) return;
        Vec3 cam = e.getCamera().getPosition();
        VUE.set(e.getProjectionMatrix()).mul(e.getPoseStack().last().pose());
        Window w = Minecraft.getInstance().getWindow();
        float gw = w.getGuiScaledWidth(), gh = w.getGuiScaledHeight();
        for (Chiffre c : CHIFFRES) {
            float t = (maintenant - c.debut) / (float) DUREE_MS;
            POINT.set((float) (c.x - cam.x), (float) (c.y + MONTEE * t - cam.y), (float) (c.z - cam.z), 1F);
            VUE.transform(POINT);
            c.visible = POINT.w > 0.05F;
            if (!c.visible) continue;
            c.sx = (POINT.x / POINT.w * 0.5F + 0.5F) * gw;
            c.sy = (0.5F - POINT.y / POINT.w * 0.5F) * gh - MONTEE_ECRAN * t;
        }
    }

    /** Avec le HUD : chaque chiffre à sa place, contour sombre d'un pixel d'écran puis le chiffre. */
    @SubscribeEvent
    public static void dessiner(RenderGuiEvent.Post e) {
        if (CHIFFRES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        long maintenant = System.currentTimeMillis();
        GuiGraphics g = e.getGuiGraphics();
        float px = Formes.pixel();
        for (Chiffre c : CHIFFRES) {
            if (!c.visible) continue;
            float t = Mth.clamp((maintenant - c.debut) / (float) DUREE_MS, 0F, 1F);
            float a = 1F - t * t;
            int fond = Palette.argb(CONTOUR, a * 0.85F);
            float x = Formes.caler(c.sx), y = Formes.caler(c.sy);
            c.texte.ajouter(g, x - px, y, fond, TexteHud.CENTRE, false);
            c.texte.ajouter(g, x + px, y, fond, TexteHud.CENTRE, false);
            c.texte.ajouter(g, x, y - px, fond, TexteHud.CENTRE, false);
            c.texte.ajouter(g, x, y + px, fond, TexteHud.CENTRE, false);
            c.texte.ajouter(g, x, y, Palette.argb(c.couleur, a), TexteHud.CENTRE, false);
        }
        g.flush();
    }
}
