package fr.heraliz.quetes.client;

import fr.heraliz.quetes.Langue;
import fr.heraliz.quetes.Texte;
import net.minecraft.client.resources.language.I18n;

/** Un {@link Texte} dans la langue du joueur : texte écrit dans sa langue, ou clé traduite avec ses arguments. */
public final class TexteClient {
    private TexteClient() {
    }

    public static String str(Texte t) {
        if (t == null || t.vide()) return "";
        if (t.cleTraduction() != null) {
            Object[] a = new Object[t.arguments().size()];
            for (int i = 0; i < a.length; i++) a[i] = str(t.arguments().get(i));
            return I18n.get(t.cleTraduction(), a);
        }
        return t.choisir(Langue.code());
    }

    public static String tr(String cle, Object... args) {
        return I18n.get(cle, args);
    }

    /** « 850 m », « 1,2 km » (virgule en français et en russe). */
    public static String distance(int m, boolean approx) {
        String s;
        if (m < 1000) s = tr("heraliz_quetes.distance.m", m);
        else {
            String km = String.format(java.util.Locale.ROOT, "%.1f", m / 1000.0);
            if (!Langue.code().startsWith("en")) km = km.replace('.', ',');
            s = tr("heraliz_quetes.distance.km", km);
        }
        return approx ? "≈ " + s : s;
    }
}
