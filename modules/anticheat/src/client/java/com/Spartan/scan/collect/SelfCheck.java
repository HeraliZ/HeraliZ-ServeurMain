package com.Spartan.scan.collect;

import com.Spartan.scan.ScanReport;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;
import java.util.regex.Pattern;

/**
 * L'anticheat a-t-il été modifié EN MÉMOIRE ?
 *
 * Le défi tournant vérifie le jar sur le disque ; un client de triche qui veut
 * neutraliser Spartan sans toucher au jar le modifie au chargement (Mixin,
 * transformateur ASM). Mixin laisse des traces visibles par réflexion : une
 * annotation @MixinMerged sur chaque méthode fusionnée, et des méthodes
 * ajoutées au nom caractéristique (« handler$zza000$… »).
 */
public final class SelfCheck {
    private static final String MIXIN_MERGED = "org.spongepowered.asm.mixin.transformer.meta.MixinMerged";
    /** Méthode ajoutée par Mixin : préfixe$xxx000$nom. */
    private static final Pattern MIXIN_METHOD = Pattern.compile(".*\\$[a-z]{3}\\d{3}\\$.*");

    private SelfCheck() {
    }

    public static void collect(ScanReport r, List<String> classNames, ClassLoader loader) {
        for (String name : classNames) {
            if (r.tamper.size() >= ScanReport.MAX_TAMPER) {
                return;
            }
            Class<?> c;
            try {
                c = Class.forName(name, false, loader);
            } catch (Throwable t) {
                continue; // classe propre à l'autre côté (client / serveur)
            }
            String found = inspect(c);
            if (found != null) {
                r.tamper.add(ScanReport.cut(c.getName() + " : " + found, 250));
            }
        }
    }

    /** Description de la trace Mixin trouvée dans la classe, ou null. */
    static String inspect(Class<?> c) {
        Method[] methods;
        try {
            methods = c.getDeclaredMethods();
        } catch (Throwable t) {
            return null; // dépendance absente de ce côté : rien à juger
        }
        for (Method m : methods) {
            if (MIXIN_METHOD.matcher(m.getName()).matches()) {
                return "méthode ajoutée " + m.getName();
            }
            for (Annotation a : m.getDeclaredAnnotations()) {
                if (a.annotationType().getName().equals(MIXIN_MERGED)) {
                    return "méthode " + m.getName() + " fusionnée par Mixin";
                }
            }
        }
        return null;
    }
}
