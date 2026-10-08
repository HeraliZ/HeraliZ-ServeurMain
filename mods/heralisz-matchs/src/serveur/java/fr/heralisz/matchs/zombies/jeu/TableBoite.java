package fr.heralisz.matchs.zombies.jeu;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.zombies.jeu.api.PlanZombies;
import fr.heralisz.matchs.zombies.jeu.api.Rarete;
import net.minecraft.util.RandomSource;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tirage de la boite mystere par raretes (ZOMBIES_V2_SPEC §2) : on tire d'abord la rarete (poids par palier), puis une
 * arme de ce palier (poids par entree), sans les armes deja possedees. Une entree = une arme TaCZ et ses accessoires
 * reels : les accessoires montent la rarete (AK nue = rare, AK + viseur = epique, AK + viseur + munitions a degats =
 * legendaire). Le palier mythique ne contient que l'arme unique de la carte (tres rare, jamais au mur).
 * <p>Source : {@code data/heralisz_matchs/zombies/boite.json} ({@link ConfigZombies}) ; sinon l'ancien champ de carte
 * {@code boite_tirage: [{gun, poids}]} (palier deduit du type TaCZ) ; sinon une petite table par defaut. Chaque arme
 * absente des packs charges, et chaque variante dont un accessoire n'est pas compatible d'apres l'index TaCZ de l'arme,
 * est ecartee a la construction (journal). Immuable apres construction, mise en cache par carte jusqu'au
 * prochain {@code /reload}.
 * <p>Format de {@code boite.json} :
 * <pre>
 * {"chargeurs_reserve": 4,
 *  "raretes": {"commun": 38, "rare": 32, "epique": 19, "legendaire": 9.5, "mythique": 1.5},
 *  "armes": [{"gun": "tacz:ak47", "rarete": "rare", "poids": 10,
 *             "variantes": [{"rarete": "epique", "poids": 6, "accessoires": {"scope": "tacz:sight_t2"}}, ...]}, ...]}
 * </pre>
 */
final class TableBoite {
    /** Une arme tirable : id TaCZ, accessoires (emplacement -> id), rarete et poids dans son palier. */
    record Tirage(String gunId, Map<String, String> accessoires, Rarete rarete, int poids) {
    }

    /** Poids des paliers par defaut (somme 100). */
    private static final double[] POIDS_DEFAUT = {38, 32, 19, 9.5, 1.5};
    private static final Map<String, TableBoite> CACHE = new HashMap<>();

    private final EnumMap<Rarete, List<Tirage>> paliers = new EnumMap<>(Rarete.class);
    private final double[] poids = new double[Rarete.values().length];
    private final int chargeursReserve;
    @Nullable
    private final String armeCarte;
    private final List<Tirage> toutes = new ArrayList<>();

    private TableBoite(List<Tirage> tirages, double[] poidsRaretes, int chargeursReserve, @Nullable String armeCarte) {
        for (Rarete r : Rarete.values()) paliers.put(r, new ArrayList<>());
        for (Tirage t : tirages) paliers.get(t.rarete()).add(t);
        for (Rarete r : Rarete.values()) {
            List<Tirage> l = paliers.get(r);
            poids[r.ordinal()] = l.isEmpty() ? 0 : Math.max(0, poidsRaretes[r.ordinal()]);
            paliers.put(r, List.copyOf(l));
            toutes.addAll(l);
        }
        this.chargeursReserve = chargeursReserve;
        this.armeCarte = armeCarte;
    }

    /** Oublie les tables en cache (rechargement des donnees). */
    static synchronized void invalider() {
        CACHE.clear();
    }

    /** Table de la carte, filtree sur les armes et accessoires reellement charges (mise en cache). */
    static synchronized TableBoite pour(JsonObject meta, String carte, @Nullable PlanZombies.ArmeCarte proposee) {
        TableBoite t = CACHE.get(carte);
        if (t == null) {
            t = construire(meta, carte, proposee);
            CACHE.put(carte, t);
        }
        return t;
    }

    private static TableBoite construire(JsonObject meta, String carte, @Nullable PlanZombies.ArmeCarte proposee) {
        List<Tirage> brut = new ArrayList<>();
        double[] pr = POIDS_DEFAUT.clone();
        int reserve = 4;
        JsonObject cfg = ConfigZombies.boite();
        if (cfg != null) {
            reserve = entier(cfg, "chargeurs_reserve", 4);
            JsonElement r = cfg.get("raretes");
            if (r != null && r.isJsonObject()) {
                for (Rarete ra : Rarete.values()) {
                    JsonElement v = r.getAsJsonObject().get(ra.id());
                    try {
                        if (v != null) pr[ra.ordinal()] = v.getAsDouble();
                    } catch (RuntimeException ignored) {
                    }
                }
            }
            lireArmes(cfg.get("armes"), brut);
        }
        if (brut.isEmpty()) {
            // ancien champ de carte : [{gun, poids}] (palier deduit du type)
            JsonElement t = meta.get("boite_tirage");
            if (t != null && t.isJsonArray()) {
                for (JsonElement e : t.getAsJsonArray()) {
                    if (!e.isJsonObject()) continue;
                    JsonObject o = e.getAsJsonObject();
                    try {
                        String g = o.get("gun").getAsString();
                        brut.add(new Tirage(g, Map.of(), palierParType(g), Math.max(1, entier(o, "poids", 5))));
                    } catch (RuntimeException ignored) {
                    }
                }
            }
        }
        if (brut.isEmpty()) for (String[] d : DEFAUT) brut.add(new Tirage(d[0], Map.of(), Rarete.parId(d[1], Rarete.RARE), Integer.parseInt(d[2])));

        // arme unique de la carte : la configuration fait foi, la carte seulement si aucun candidat n'est installe
        String wonder = null;
        for (String g : ConfigZombies.armesCarte()) {
            if (Tacz.existe(g)) {
                wonder = g;
                break;
            }
        }
        if (wonder == null && proposee != null && Tacz.existe(proposee.gunId())) wonder = proposee.gunId();
        brut.removeIf(x -> x.rarete() == Rarete.MYTHIQUE);
        if (wonder != null) {
            // accessoires de l'arme de la carte : seuls les compatibles sont gardes (l'arme reste tirable quoi qu'il arrive)
            Map<String, String> acc = new LinkedHashMap<>();
            for (Map.Entry<String, String> a : ConfigZombies.accessoiresArmeCarte(wonder).entrySet()) {
                if (Tacz.compatible(wonder, a.getValue()) && a.getKey().equals(Tacz.typeAccessoire(a.getValue()))) acc.put(a.getKey(), a.getValue());
            }
            brut.add(new Tirage(wonder, Map.copyOf(acc), Rarete.MYTHIQUE, 1));
        }
        final String armeCarte = wonder;
        // les armes de la boite ne doublonnent jamais l'arme de la carte dans un autre palier
        if (armeCarte != null) brut.removeIf(x -> x.gunId().equals(armeCarte) && x.rarete() != Rarete.MYTHIQUE);

        List<Tirage> ok = new ArrayList<>(brut.size());
        List<String> absentes = new ArrayList<>(), refusees = new ArrayList<>();
        for (Tirage e : brut) {
            if (!Tacz.existe(e.gunId())) {
                if (!absentes.contains(e.gunId())) absentes.add(e.gunId());
                continue;
            }
            if (!e.accessoires().isEmpty()) {
                boolean tous = true;
                for (Map.Entry<String, String> a : e.accessoires().entrySet()) {
                    if (!Tacz.compatible(e.gunId(), a.getValue()) || !a.getKey().equals(Tacz.typeAccessoire(a.getValue()))) {
                        tous = false;
                        refusees.add(e.gunId() + "+" + a.getValue());
                        break;
                    }
                }
                if (!tous) continue;
            }
            ok.add(e);
        }
        if (!absentes.isEmpty()) HeraliszMatchs.LOG.info("[zombies] {} : armes de boite absentes des packs, ignorees {}", carte, absentes);
        if (!refusees.isEmpty()) HeraliszMatchs.LOG.warn("[zombies] {} : variantes de boite aux accessoires incompatibles, ignorees {}", carte, refusees);
        TableBoite tb = new TableBoite(ok, pr, Math.max(1, reserve), armeCarte);
        HeraliszMatchs.LOG.info("[zombies] {} : boite {} armes (commun {}, rare {}, epique {}, legendaire {}, mythique {} = {})", carte, ok.size(),
                tb.paliers.get(Rarete.COMMUN).size(), tb.paliers.get(Rarete.RARE).size(), tb.paliers.get(Rarete.EPIQUE).size(),
                tb.paliers.get(Rarete.LEGENDAIRE).size(), tb.paliers.get(Rarete.MYTHIQUE).size(), armeCarte);
        return tb;
    }

    private static void lireArmes(@Nullable JsonElement armes, List<Tirage> out) {
        if (armes == null || !armes.isJsonArray()) return;
        for (JsonElement e : armes.getAsJsonArray()) {
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            String g;
            try {
                g = o.get("gun").getAsString();
            } catch (RuntimeException ex) {
                continue;
            }
            Rarete base = Rarete.parId(texte(o, "rarete"), palierParType(g));
            Map<String, String> acc = accessoires(o.get("accessoires"));
            out.add(new Tirage(g, acc, acc.isEmpty() ? base : texte(o, "rarete") != null ? base : base.monter(crans(acc)), Math.max(1, entier(o, "poids", 5))));
            JsonElement v = o.get("variantes");
            if (v != null && v.isJsonArray()) {
                for (JsonElement ve : v.getAsJsonArray()) {
                    if (!ve.isJsonObject()) continue;
                    JsonObject vo = ve.getAsJsonObject();
                    Map<String, String> va = accessoires(vo.get("accessoires"));
                    if (va.isEmpty()) continue;
                    Rarete r = Rarete.parId(texte(vo, "rarete"), base.monter(crans(va)));
                    out.add(new Tirage(g, va, r, Math.max(1, entier(vo, "poids", 3))));
                }
            }
        }
    }

    /** Crans de rarete gagnes par des accessoires : viseur +1, munitions a degats +1. */
    private static int crans(Map<String, String> acc) {
        int n = 0;
        if (acc.containsKey("scope")) n++;
        String em = acc.get("extended_mag");
        if (em != null && em.contains("ammo_mod")) n++;
        return n;
    }

    private static Map<String, String> accessoires(@Nullable JsonElement e) {
        Map<String, String> m = new LinkedHashMap<>();
        if (e == null || !e.isJsonObject()) return m;
        for (Map.Entry<String, JsonElement> x : e.getAsJsonObject().entrySet()) {
            try {
                m.put(x.getKey().toLowerCase(java.util.Locale.ROOT), x.getValue().getAsString());
            } catch (RuntimeException ignored) {
            }
        }
        return Map.copyOf(m);
    }

    /** Palier par defaut d'une arme nue selon son type TaCZ. */
    static Rarete palierParType(String gunId) {
        return switch (Tacz.type(gunId)) {
            case "pistol" -> Rarete.COMMUN;
            case "smg", "shotgun" -> Rarete.RARE;
            case "rifle" -> Rarete.RARE;
            case "sniper" -> Rarete.EPIQUE;
            case "mg" -> Rarete.LEGENDAIRE;
            default -> Rarete.RARE;
        };
    }

    // ------------------------------------------------------------------ tirage
    boolean vide() {
        return toutes.isEmpty();
    }

    int chargeursReserve() {
        return chargeursReserve;
    }

    /** Arme unique de la carte retenue (installee), ou null. */
    @Nullable
    String armeCarte() {
        return armeCarte;
    }

    int taille(Rarete r) {
        return paliers.get(r).size();
    }

    /** Tirage pondere par paliers en excluant les armes deja possedees (rend null si rien n'est possible). */
    @Nullable
    Tirage tirer(RandomSource alea, Collection<String> exclues) {
        double total = 0;
        for (Rarete r : Rarete.values()) if (possible(r, exclues)) total += poids[r.ordinal()];
        if (total <= 0) return toutes.isEmpty() ? null : toutes.get(alea.nextInt(toutes.size()));
        double x = alea.nextDouble() * total;
        for (Rarete r : Rarete.values()) {
            if (!possible(r, exclues)) continue;
            x -= poids[r.ordinal()];
            if (x < 0) return tirerDans(r, alea, exclues);
        }
        return tirerDans(Rarete.RARE, alea, exclues);
    }

    /** Tirage dans un palier donne (banc, recompense) ; null si le palier est vide ou entierement exclu. */
    @Nullable
    Tirage tirerDans(Rarete r, RandomSource alea, Collection<String> exclues) {
        List<Tirage> l = paliers.get(r);
        int t = 0;
        for (Tirage e : l) if (!exclues.contains(e.gunId())) t += e.poids();
        if (t <= 0) return null;
        int k = alea.nextInt(t);
        for (Tirage e : l) {
            if (exclues.contains(e.gunId())) continue;
            k -= e.poids();
            if (k < 0) return e;
        }
        return null;
    }

    private boolean possible(Rarete r, Collection<String> exclues) {
        if (poids[r.ordinal()] <= 0) return false;
        for (Tirage e : paliers.get(r)) if (!exclues.contains(e.gunId())) return true;
        return false;
    }

    /** Arme quelconque (paliers ponderes) pour l'animation de la roulette. */
    @Nullable
    Tirage auHasard(RandomSource alea) {
        return tirer(alea, List.of());
    }

    // ------------------------------------------------------------------ outils
    @Nullable
    private static String texte(JsonObject o, String cle) {
        try {
            JsonElement e = o.get(cle);
            return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static int entier(JsonObject o, String cle, int defaut) {
        try {
            JsonElement e = o.get(cle);
            return e != null && e.isJsonPrimitive() ? e.getAsInt() : defaut;
        } catch (RuntimeException ex) {
            return defaut;
        }
    }

    /** Table de secours (sans boite.json) : armes du pack TaCZ par defaut, nues. */
    private static final String[][] DEFAUT = {
            {"tacz:glock_17", "commun", "10"}, {"tacz:cz75", "commun", "8"}, {"tacz:p320", "commun", "8"}, {"tacz:b93r", "commun", "6"},
            {"tacz:uzi", "commun", "8"}, {"tacz:deagle", "rare", "6"}, {"tacz:ump45", "rare", "8"}, {"tacz:vector45", "rare", "8"},
            {"tacz:p90", "rare", "8"}, {"tacz:m870", "rare", "7"}, {"tacz:db_long", "rare", "5"}, {"tacz:ak47", "rare", "10"},
            {"tacz:m4a1", "rare", "10"}, {"tacz:m16a1", "rare", "6"}, {"tacz:mk14", "epique", "6"}, {"tacz:spr15hb", "epique", "5"},
            {"tacz:scar_h", "epique", "6"}, {"tacz:aa12", "epique", "4"}, {"tacz:m249", "legendaire", "5"}, {"tacz:rpk", "legendaire", "5"}
    };
}
