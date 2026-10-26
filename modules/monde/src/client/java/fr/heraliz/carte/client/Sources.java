package fr.heraliz.carte.client;

import fr.heraliz.carte.HeralizCarte;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ce que la carte lit chez nos autres mods, par réflexion (aucune dépendance de compilation ; un mod absent ou une
 * lecture qui échoue rend « rien » et la carte cache simplement le calque) :
 * <ul>
 *     <li>HeraliZ Clans : {@code fr.heralisz.clans.api.CarteApi} (territoires, membres de son clan, son clan, dessin des
 *     bannières, demande au serveur, ravitaillement) ;</li>
 *     <li>HeraliZ Extraction : {@code com.heraliz.extraction.api.ExtractionApi} (extractions ouvertes).</li>
 * </ul>
 * Les listes sont refaites seulement quand la version de l'API change. Fil du client.
 */
final class Sources {
    private Sources() {
    }

    /** Une zone de clan vue par la carte. etat : NEUTRE, TENUE, CAPTURE, CONTESTE ou PROTEGEE. */
    record Zone(String id, String nom, int x, int z, int rayon, boolean drapeau, int drapeauX, int drapeauZ, String proprioTag,
                String proprioNom, int proprioCouleur, @Nullable Object proprioBanniere, String etat, String capteurTag, String capteurNom,
                int capteurCouleur, @Nullable Object capteurBanniere, float progression, long tenueDepuisMs, long protegeeMs, int valeur,
                int bonusLoot, boolean monClan, long ravitaillementMs, boolean peutRavitailler) {
        boolean libre() {
            return proprioTag.isEmpty();
        }
    }

    record Allie(String nom, double x, double z) {
    }

    record MonClan(String tag, String nom, int couleur, @Nullable Object banniere, int enLigne, int territoires) {
    }

    /** Une extraction ouverte (secondes restantes : -1 si permanente). */
    record Extraction(String nom, ResourceLocation dimension, double x, double z, float rayon, int secondesATenir, long finTick) {
        int secondesRestantes(long heureDeJeu) {
            return finTick < 0 ? -1 : (int) Math.max(0L, (finTick - heureDeJeu) / 20L);
        }
    }

    // ------------------------------------------------------------------ réflexion
    private static final Map<Class<?>, Map<String, Method>> ACCESSEURS = new HashMap<>();

    @Nullable
    private static Object lire(Object o, String nom) throws ReflectiveOperationException {
        Map<String, Method> m = ACCESSEURS.computeIfAbsent(o.getClass(), c -> new HashMap<>());
        Method me = m.get(nom);
        if (me == null) {
            me = o.getClass().getMethod(nom);
            m.put(nom, me);
        }
        return me.invoke(o);
    }

    private static String chaine(Object o, String nom) throws ReflectiveOperationException {
        Object v = lire(o, nom);
        return v == null ? "" : v.toString();
    }

    private static int entier(Object o, String nom) throws ReflectiveOperationException {
        Object v = lire(o, nom);
        return v instanceof Number n ? n.intValue() : 0;
    }

    private static long longue(Object o, String nom) throws ReflectiveOperationException {
        Object v = lire(o, nom);
        return v instanceof Number n ? n.longValue() : 0L;
    }

    private static double reel(Object o, String nom) throws ReflectiveOperationException {
        Object v = lire(o, nom);
        return v instanceof Number n ? n.doubleValue() : 0.0;
    }

    private static boolean booleen(Object o, String nom) throws ReflectiveOperationException {
        return Boolean.TRUE.equals(lire(o, nom));
    }

    // ------------------------------------------------------------------ clans
    private static boolean clansCherches, clansAbsents;
    private static Method cVersion, cZones, cAllies, cMonClan, cDemander, cRavitailler, cDessiner;
    private static int versionClans = Integer.MIN_VALUE;
    private static List<Zone> zones = List.of();
    private static List<Allie> allies = List.of();
    @Nullable
    private static MonClan monClan;

    private static boolean clans() {
        if (!clansCherches) {
            clansCherches = true;
            try {
                Class<?> c = Class.forName("fr.heralisz.clans.api.CarteApi");
                cVersion = c.getMethod("version");
                cZones = c.getMethod("zones");
                cAllies = c.getMethod("allies");
                cMonClan = c.getMethod("monClan");
                cDemander = c.getMethod("demander");
                cRavitailler = c.getMethod("ravitailler", String.class);
                cDessiner = c.getMethod("dessinerBanniere", Object.class, Object.class, int.class, int.class, int.class, int.class);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                clansAbsents = true;
                HeralizCarte.LOG.info("[carte] territoires des clans indisponibles (mod des clans absent ou trop ancien)");
            }
        }
        return !clansAbsents;
    }

    /** Vrai si le mod des clans (2.4.0 ou plus) est là : calques territoires et clan possibles. */
    static boolean clansPresents() {
        return clans();
    }

    /** Relit les territoires si le serveur a répondu depuis la dernière fois. */
    private static void majClans() {
        if (!clans()) return;
        try {
            int v = ((Number) cVersion.invoke(null)).intValue();
            if (v == versionClans) return;
            versionClans = v;
            List<Zone> zs = new ArrayList<>();
            if (cZones.invoke(null) instanceof List<?> l) {
                for (Object z : l) {
                    if (z == null) continue;
                    zs.add(new Zone(chaine(z, "id"), chaine(z, "nom"), entier(z, "x"), entier(z, "z"), entier(z, "rayon"), booleen(z, "drapeau"),
                            entier(z, "drapeauX"), entier(z, "drapeauZ"), chaine(z, "proprioTag"), chaine(z, "proprioNom"),
                            entier(z, "proprioCouleur") & 0xFFFFFF, lire(z, "proprioBanniere"), chaine(z, "etat"), chaine(z, "capteurTag"),
                            chaine(z, "capteurNom"), entier(z, "capteurCouleur") & 0xFFFFFF, lire(z, "capteurBanniere"),
                            (float) reel(z, "progression"), longue(z, "tenueDepuisMs"), longue(z, "protegeeMs"), entier(z, "valeur"),
                            entier(z, "bonusLoot"), booleen(z, "monClan"), longue(z, "ravitaillementMs"), booleen(z, "peutRavitailler")));
                }
            }
            List<Allie> as = new ArrayList<>();
            if (cAllies.invoke(null) instanceof List<?> l) {
                for (Object a : l) if (a != null) as.add(new Allie(chaine(a, "nom"), reel(a, "x"), reel(a, "z")));
            }
            Object m = cMonClan.invoke(null);
            zones = List.copyOf(zs);
            allies = List.copyOf(as);
            monClan = m == null ? null : new MonClan(chaine(m, "tag"), chaine(m, "nom"), entier(m, "couleur") & 0xFFFFFF, lire(m, "banniere"),
                    entier(m, "enLigne"), entier(m, "territoires"));
        } catch (ReflectiveOperationException | RuntimeException e) {
            clansAbsents = true;
            zones = List.of();
            allies = List.of();
            monClan = null;
            HeralizCarte.LOG.warn("[carte] lecture des territoires impossible : calque coupé ({})", e.toString());
        }
    }

    static List<Zone> zones() {
        majClans();
        return zones;
    }

    static List<Allie> allies() {
        majClans();
        return allies;
    }

    @Nullable
    static MonClan monClan() {
        majClans();
        return monClan;
    }

    /** Demande l'état des territoires au serveur (la carte l'appelle au plus une fois par seconde). */
    static void demanderClans() {
        if (!clans()) return;
        try {
            cDemander.invoke(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            clansAbsents = true;
        }
    }

    static void ravitailler(String zoneId) {
        if (!clans()) return;
        try {
            cRavitailler.invoke(null, zoneId);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // rien : le bouton n'a simplement pas d'effet
        }
    }

    /** Dessine une bannière de clan (rien sans le mod des clans ou sans bannière). */
    static void banniere(Object guiGraphics, @Nullable Object banniere, int x, int y, int w, int h) {
        if (banniere == null || !clans()) return;
        try {
            cDessiner.invoke(null, guiGraphics, banniere, x, y, w, h);
        } catch (ReflectiveOperationException | RuntimeException e) {
            cDessiner = null;
            clansAbsents = true;
        }
    }

    // ------------------------------------------------------------------ extractions
    private static boolean extractionsCherchees, extractionsAbsentes;
    private static Method eVersion, eOuvertes;
    private static int versionExtractions = Integer.MIN_VALUE;
    private static List<Extraction> extractions = List.of();

    static List<Extraction> extractions() {
        if (!extractionsCherchees) {
            extractionsCherchees = true;
            try {
                Class<?> c = Class.forName("com.heraliz.extraction.api.ExtractionApi");
                eVersion = c.getMethod("version");
                eOuvertes = c.getMethod("ouvertes");
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                extractionsAbsentes = true;
            }
        }
        if (extractionsAbsentes) return List.of();
        try {
            int v = ((Number) eVersion.invoke(null)).intValue();
            if (v != versionExtractions) {
                versionExtractions = v;
                List<Extraction> out = new ArrayList<>();
                if (eOuvertes.invoke(null) instanceof List<?> l) {
                    for (Object e : l) {
                        if (e == null) continue;
                        Object dim = lire(e, "dimension");
                        out.add(new Extraction(chaine(e, "libelle"), dim instanceof ResourceLocation r ? r : new ResourceLocation("minecraft", "overworld"),
                                reel(e, "x"), reel(e, "z"), (float) reel(e, "rayon"), entier(e, "secondesATenir"), longue(e, "finTick")));
                    }
                }
                extractions = List.copyOf(out);
            }
            return extractions;
        } catch (ReflectiveOperationException | RuntimeException e) {
            extractionsAbsentes = true;
            return List.of();
        }
    }

    /** Déconnexion : les listes se refont à la prochaine lecture. */
    static void vider() {
        versionClans = Integer.MIN_VALUE;
        versionExtractions = Integer.MIN_VALUE;
        zones = List.of();
        allies = List.of();
        monClan = null;
        extractions = List.of();
    }
}
