package fr.heraliz.noyau.client.kit;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/**
 * Case « en verre » de nos écrans (inventaire D1 validé le 14/11) : fond blanc à 4,5 % (10 % au survol), coins adoucis,
 * bord d'un pixel d'écran blanc à 7,5 % (80 % au survol) et halo léger au survol ; l'objet (16 x 16) au milieu. La couleur
 * de rareté (dégradé et trait sous l'objet) est celle de HeraliZ Loot, posée dans tous les écrans à cases
 * (RaretesClient) : rien à faire ici. Numéros de la barre d'objets : {@link #numero}.
 * <p>Formes envoyées au tampon sans le vider ({@link Formes}) ; textes gardés (rien n'est alloué à chaque image).
 */
public final class CaseVerre {
    private CaseVerre() {
    }

    private static final int FOND = 11, FOND_SURVOL = 26, BORD = 19, BORD_SURVOL = 204, HALO = 20;
    /** Numéros 1 à 9 de la barre d'objets. */
    private static final TexteHud[] NUMEROS = new TexteHud[9];

    /** Case de {@code t} unités de côté, coin haut gauche en (x, y). */
    public static void dessiner(VertexConsumer vc, Matrix4f m, float x, float y, float t, boolean survol) {
        int f = Palette.argb(0xFFFFFF, survol ? FOND_SURVOL : FOND);
        Formes.rect(vc, m, x + 1, y, x + t - 1, y + t, f);
        Formes.rect(vc, m, x, y + 1, x + 1, y + t - 1, f);
        Formes.rect(vc, m, x + t - 1, y + 1, x + t, y + t - 1, f);
        float px = Formes.pixel();
        int b = Palette.argb(0xFFFFFF, survol ? BORD_SURVOL : BORD);
        Formes.rect(vc, m, x + 1.5f, y, x + t - 1.5f, y + px, b);
        Formes.rect(vc, m, x + 1.5f, y + t - px, x + t - 1.5f, y + t, b);
        Formes.rect(vc, m, x, y + 1.5f, x + px, y + t - 1.5f, b);
        Formes.rect(vc, m, x + t - px, y + 1.5f, x + t, y + t - 1.5f, b);
        if (survol) Formes.halo(vc, m, x + t / 2f, y + t / 2f, t * 0.8f, Palette.argb(0xFFFFFF, HALO));
    }

    /** Numéro (1 à 9) d'une case de la barre d'objets, en haut à gauche de la case de coin (x, y) ; texte envoyé au tampon. */
    public static void numero(GuiGraphics g, int n, float x, float y) {
        if (n < 1 || n > 9) return;
        TexteHud t = NUMEROS[n - 1];
        if (t == null) t = NUMEROS[n - 1] = new TexteHud(PoliceHud.MINI).texte(String.valueOf(n));
        t.ajouter(g, x + 2.4f, y + 3.8f, Palette.argb(Palette.TEXTE, 115), TexteHud.GAUCHE, false);
    }

    // ------------------------------------------------------------------ rareté (HeraliZ Loot, sans dépendance)
    private static MethodHandle couleurLoot;
    private static boolean lootCherche;

    /**
     * Couleur (RVB) de la rareté HeraliZ de l'objet, -1 sans rareté ou sans HeraliZ Loot. Même source que la barre
     * d'objets façon DayZ ({@code fr.heraliz.loot.client.RaretesClient.couleur}, par réflexion). Sans allocation.
     */
    public static int couleurRarete(ItemStack st) {
        if (st.isEmpty()) return -1;
        if (!lootCherche) {
            lootCherche = true;
            try {
                Method mt = Class.forName("fr.heraliz.loot.client.RaretesClient").getMethod("couleur", ItemStack.class);
                couleurLoot = MethodHandles.lookup().unreflect(mt);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
                couleurLoot = null;
            }
        }
        if (couleurLoot == null) return -1;
        try {
            return (int) couleurLoot.invokeExact(st);
        } catch (Throwable ex) {
            couleurLoot = null;
            return -1;
        }
    }
}
