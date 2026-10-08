package fr.heralisz.matchs.zombies.contenu;

import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Optional;

/**
 * Silhouette a la craie d'une arme au mur ({@code textures/entity/zombies/craie_<categorie>.png}) : d'abord le type
 * declare par le pack TaCZ ({@code CommonGunIndex.getType()}, par reflexion tolerante), sinon une heuristique sur
 * l'identifiant. Categories : fusil, pompe, smg, precision, pistolet, double, mitrailleuse.
 */
public final class CategoriesArmes {
    private CategoriesArmes() {
    }

    public static final String FUSIL = "fusil", POMPE = "pompe", SMG = "smg", PRECISION = "precision", PISTOLET = "pistolet",
            DOUBLE = "double", MITRAILLEUSE = "mitrailleuse";
    public static final String[] TOUTES = {FUSIL, POMPE, SMG, PRECISION, PISTOLET, DOUBLE, MITRAILLEUSE};

    private static boolean pret;
    private static Method index, type;

    /** Categorie de l'arme (jamais null). */
    public static String de(String gunId) {
        String id = gunId == null ? "" : gunId.toLowerCase(Locale.ROOT);
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        if (path.startsWith("db_") || path.contains("double") || path.contains("sawed")) return DOUBLE;
        String t = typeTacz(id);
        if (t != null) {
            switch (t) {
                case "pistol":
                    return PISTOLET;
                case "sniper":
                    return PRECISION;
                case "shotgun":
                    return POMPE;
                case "smg":
                    return SMG;
                case "mg":
                case "lmg":
                    return MITRAILLEUSE;
                case "rifle":
                    return heuristique(path, FUSIL);
                default:
                    break;
            }
        }
        return heuristique(path, FUSIL);
    }

    private static String heuristique(String p, String defaut) {
        if (has(p, "m870", "spas", "aa12", "ksg", "m1014", "shotgun", "saiga", "pump")) return POMPE;
        if (has(p, "uzi", "ump", "mp5", "mp7", "mp9", "vector", "p90", "mac10", "mac_10", "pp19", "bizon", "smg", "thompson", "tmp"))
            return SMG;
        if (has(p, "awp", "m200", "m700", "m24", "kar98", "mk14", "sks", "svd", "m110", "m82", "barrett", "springfield", "mosin",
                "sniper", "dmr", "mini14", "m14"))
            return PRECISION;
        if (has(p, "glock", "deagle", "1911", "p320", "usp", "cz75", "b93r", "m9", "p226", "revolver", "python", "pistol", "five"))
            return PISTOLET;
        if (has(p, "m249", "rpk", "pkm", "m60", "mg42", "lmg", "minigun")) return MITRAILLEUSE;
        return defaut;
    }

    private static boolean has(String p, String... motifs) {
        for (String m : motifs) if (p.contains(m)) return true;
        return false;
    }

    private static String typeTacz(String gunId) {
        try {
            if (!pret) {
                pret = true;
                Class<?> api = Class.forName("com.tacz.guns.api.TimelessAPI");
                index = api.getMethod("getCommonGunIndex", ResourceLocation.class);
                type = Class.forName("com.tacz.guns.resource.index.CommonGunIndex").getMethod("getType");
            }
            if (index == null || type == null) return null;
            ResourceLocation rl = ResourceLocation.tryParse(gunId);
            if (rl == null) return null;
            Object o = index.invoke(null, rl);
            if (o instanceof Optional<?> opt && opt.isPresent()) {
                Object t = type.invoke(opt.get());
                return t == null ? null : t.toString().toLowerCase(Locale.ROOT);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            index = null;
        }
        return null;
    }
}
