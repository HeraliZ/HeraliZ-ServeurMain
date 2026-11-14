package fr.heraliz.noyau.client.effets;

import java.util.Collection;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.client.extensions.common.IClientMobEffectExtensions;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Effets du joueur tels que nos HUD les montrent (choix du propriétaire du 14/11 : V2 à l'écran de jeu, fine bande dans
 * l'inventaire D1), relus une fois par tick ({@link #tick}) et rangés :
 * <ol start="0">
 *     <li>{@link #SOIN} : à soigner, ne part pas tout seul (nocif sans fin : saignement, jambe cassée…), anneau rouge plein
 *     et point rouge ;</li>
 *     <li>{@link #ZONE} : posé par une zone (nocif « ambiant », renouvelé tant qu'on y reste), anneau ambre plein ;</li>
 *     <li>{@link #MALUS} : autre effet nocif, anneau rouge qui se vide ;</li>
 *     <li>{@link #BONUS} : effet utile, anneau vert qui se vide (le plus court d'abord) ;</li>
 *     <li>{@link #LOOT} : bonus de loot ({@code heraliz_grades:bonus_loot}), anneau doré, seulement dans l'inventaire
 *     (choix du 11/11).</li>
 * </ol>
 * Montrés : à l'écran de jeu ({@link #HUD}), ceux que leur mod laisse voir à l'écran et qui ont leur icône ou sont nocifs
 * (le saignement de geckolib222 n'avait pas d'icône : on le voyait seulement dans l'inventaire) ; dans l'inventaire
 * ({@link #INVENTAIRE}), ceux que leur mod laisse voir dans l'inventaire, mêmes règles, plus le bonus de loot. Anneau qui
 * se vide : temps qui reste sur la plus longue durée vue depuis l'arrivée de l'effet (remise à neuf s'il est repris).
 * <p>Rien n'est alloué à chaque image (tableaux fixes) ; au tick, seulement le parcours des effets du joueur.
 */
public final class EffetsJoueur {
    private EffetsJoueur() {
    }

    public static final int SOIN = 0, ZONE = 1, MALUS = 2, BONUS = 3, LOOT = 4;
    /** Au-delà d'un jour : effet « jusqu'au soin » (saignement, jambe cassée : 2 147 483 647 ticks). */
    private static final int SANS_FIN = 20 * 60 * 60 * 24;
    /** Nombre d'effets gardés par liste. */
    public static final int MAX = 24;
    private static final ResourceLocation BONUS_LOOT = new ResourceLocation("heraliz_grades", "bonus_loot");

    /** Effets d'une liste, rangés (à soigner, zone, malus, bonus du plus court au plus long, bonus de loot). */
    public static final class Liste {
        private int n;
        private final MobEffectInstance[] effets = new MobEffectInstance[MAX];
        private final int[] categories = new int[MAX];
        private final float[] restes = new float[MAX];

        public int taille() {
            return n;
        }

        public MobEffectInstance effet(int i) {
            return effets[i];
        }

        public int categorie(int i) {
            return categories[i];
        }

        /** Part du temps qui reste (0 à 1 ; 1 pour un effet sans fin ou de zone). */
        public float reste(int i) {
            return restes[i];
        }

        private void vider() {
            for (int i = 0; i < n; i++) effets[i] = null;
            n = 0;
        }

        private void ajouter(MobEffectInstance e, int cat, float reste) {
            if (n >= MAX) return;
            int j = n;
            while (j > 0 && avant(cat, e, categories[j - 1], effets[j - 1])) {
                effets[j] = effets[j - 1];
                categories[j] = categories[j - 1];
                restes[j] = restes[j - 1];
                j--;
            }
            effets[j] = e;
            categories[j] = cat;
            restes[j] = reste;
            n++;
        }

        private static boolean avant(int c, MobEffectInstance e, int c2, MobEffectInstance e2) {
            if (c != c2) return c < c2;
            return (c == BONUS || c == MALUS) && e.getDuration() < e2.getDuration();
        }
    }

    /** À l'écran de jeu, dans l'inventaire. */
    public static final Liste HUD = new Liste(), INVENTAIRE = new Liste();

    /** Plus longue durée vue de chaque effet présent (anneau qui se vide). */
    private static final MobEffect[] SUIVIS = new MobEffect[64];
    private static final int[] MAXI = new int[64];
    private static final boolean[] VU = new boolean[64];
    private static int nSuivis;
    private static MobEffect bonusLoot;
    private static boolean bonusLootCherche;

    /** Relecture des effets du joueur, une fois par tick (fin du tick du jeu). */
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        HUD.vider();
        INVENTAIRE.vider();
        LocalPlayer p = Minecraft.getInstance().player;
        Collection<MobEffectInstance> actifs = p == null ? null : p.getActiveEffects();
        if (actifs == null || actifs.isEmpty()) {
            oublier();
            return;
        }
        for (int k = 0; k < nSuivis; k++) VU[k] = false;
        for (MobEffectInstance i : actifs) {
            int cat = categorie(i);
            float reste = reste(i, cat);
            boolean nocif = i.getEffect().getCategory() == MobEffectCategory.HARMFUL;
            IClientMobEffectExtensions ext = IClientMobEffectExtensions.of(i);
            if (cat != LOOT && ext.isVisibleInGui(i) && (i.showIcon() || nocif)) HUD.ajouter(i, cat, reste);
            if (ext.isVisibleInInventory(i) && (i.showIcon() || nocif || cat == LOOT)) INVENTAIRE.ajouter(i, cat, reste);
        }
        int w = 0;
        for (int k = 0; k < nSuivis; k++) {
            if (!VU[k]) continue;
            SUIVIS[w] = SUIVIS[k];
            MAXI[w] = MAXI[k];
            w++;
        }
        for (int k = w; k < nSuivis; k++) SUIVIS[k] = null;
        nSuivis = w;
    }

    private static void oublier() {
        for (int k = 0; k < nSuivis; k++) SUIVIS[k] = null;
        nSuivis = 0;
    }

    /** Catégorie d'un effet ({@link #SOIN} … {@link #LOOT}). */
    public static int categorie(MobEffectInstance i) {
        MobEffect e = i.getEffect();
        if (e == bonusLoot()) return LOOT;
        boolean nocif = e.getCategory() == MobEffectCategory.HARMFUL;
        if (nocif && sansFin(i)) return SOIN;
        if (nocif && i.isAmbient()) return ZONE;
        return nocif ? MALUS : BONUS;
    }

    /** Vrai pour un effet sans fin (ou de plus d'un jour). */
    public static boolean sansFin(MobEffectInstance i) {
        return i.isInfiniteDuration() || i.getDuration() > SANS_FIN;
    }

    /** L'effet « Bonus de loot » de HeraliZ Grades (null sans ce mod). */
    public static MobEffect bonusLoot() {
        if (!bonusLootCherche) {
            bonusLootCherche = true;
            bonusLoot = ForgeRegistries.MOB_EFFECTS.getValue(BONUS_LOOT);
        }
        return bonusLoot;
    }

    private static float reste(MobEffectInstance i, int cat) {
        int d = i.getDuration();
        int max = suivre(i.getEffect(), d);
        if (cat == SOIN || cat == ZONE || sansFin(i) || max <= 0) return 1f;
        return Math.max(0f, Math.min(1f, d / (float) max));
    }

    private static int suivre(MobEffect e, int d) {
        for (int k = 0; k < nSuivis; k++) {
            if (SUIVIS[k] != e) continue;
            VU[k] = true;
            if (d > MAXI[k]) MAXI[k] = d;
            return MAXI[k];
        }
        if (nSuivis < SUIVIS.length) {
            SUIVIS[nSuivis] = e;
            MAXI[nSuivis] = d;
            VU[nSuivis] = true;
            nSuivis++;
        }
        return d;
    }

    /** Pulsation du point rouge « à soigner » : de 60 à 100 %, un souffle toutes les 1,6 s. */
    public static float pulsation() {
        double t = (Util.getMillis() % 1600L) / 1600.0;
        return 0.8f + 0.2f * (float) Math.cos(t * Math.PI * 2.0);
    }
}
