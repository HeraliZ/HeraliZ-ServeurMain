package fr.heralisz.matchs.zombies.contenu;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.heralisz.matchs.HeraliszMatchs;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Collisions FINES des modeles de The Giant (heralisz_matchs 0.2.3, contrat §1) : lit une fois dans le jar
 * {@code data/heralisz_matchs/collisions/<modele>.json} (genere par {@code tools/matchs/zombies/decor_giant_collisions.py}) :
 * <pre>{"origine": "sud", "cellules": {"dx,dy,dz": [[x0, y0, z0, x1, y1, z1], ...]}}</pre>
 * Boites en pixels (0..16) relatives a la case, modele tourne vers le sud ; machines : (dx, dy, dz) relatif a la case
 * maitresse ({@code MachinesV3.decaler}) ; decors : bloc compte depuis le coin (gauche, bas, dos) du prop vu de face.
 * <p>Chaque case donne ses 4 formes tournees (index {@code Direction.get2DDataValue()} : S 0, W 1, N 2, E 3), construites
 * UNE fois ici (rotation de {@link FormesDecoGiant#ajouter}) : aucun calcul en jeu, aucun bloc-entite. Fichier absent =
 * null (le jeu garde l'ancienne forme). Une origine autre que « sud » est ramenee au sud a la lecture.
 */
public final class CollisionsFines {
    private CollisionsFines() {
    }

    /** Collisions d'un modele : case (BlockPos relatif) -> 4 formes tournees ; {@code boites} = boites brutes (sud). */
    public record Modele(String id, Map<BlockPos, VoxelShape[]> formes, Map<BlockPos, List<double[]>> boites, int nbBoites) {
        @Nullable
        public VoxelShape[] case_(int dx, int dy, int dz) {
            return formes.get(new BlockPos(dx, dy, dz));
        }
    }

    private static final Map<String, Modele> CACHE = new LinkedHashMap<>();
    private static final Modele ABSENT = new Modele("", Map.of(), Map.of(), 0);

    /** Modele de collisions {@code collisions/<id>.json}, ou null s'il n'existe pas (lu une fois, garde en cache). */
    @Nullable
    public static synchronized Modele modele(String id) {
        Modele m = CACHE.get(id);
        if (m == null) {
            m = lire(id);
            CACHE.put(id, m == null ? ABSENT : m);
        }
        return m == ABSENT ? null : m;
    }

    /** Premier modele trouve parmi ces noms (ex. {@code zb_deco_radio} puis {@code radio}), ou null. */
    @Nullable
    public static Modele premier(String... ids) {
        for (String id : ids) {
            Modele m = modele(id);
            if (m != null) return m;
        }
        return null;
    }

    @Nullable
    private static Modele lire(String id) {
        String chemin = "/data/heralisz_matchs/collisions/" + id + ".json";
        try (InputStream in = CollisionsFines.class.getResourceAsStream(chemin)) {
            if (in == null) return null;
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            int quarts = quartsVersSud(o.has("origine") ? o.get("origine").getAsString() : "sud");
            JsonObject c = o.getAsJsonObject("cellules");
            Map<BlockPos, VoxelShape[]> formes = new LinkedHashMap<>();
            Map<BlockPos, List<double[]>> boites = new LinkedHashMap<>();
            int nb = 0;
            if (c != null) {
                for (Map.Entry<String, JsonElement> e : c.entrySet()) {
                    String[] k = e.getKey().split(",");
                    if (k.length != 3 || !e.getValue().isJsonArray()) continue;
                    int dx = Integer.parseInt(k[0].trim()), dy = Integer.parseInt(k[1].trim()), dz = Integer.parseInt(k[2].trim());
                    List<double[]> l = new ArrayList<>();
                    for (JsonElement be : e.getValue().getAsJsonArray()) {
                        if (!be.isJsonArray() || be.getAsJsonArray().size() < 6) continue;
                        JsonArray a = be.getAsJsonArray();
                        double[] b = {a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble(), a.get(3).getAsDouble(),
                                a.get(4).getAsDouble(), a.get(5).getAsDouble()};
                        for (int q = 0; q < quarts; q++) b = quartBoite(b);
                        l.add(b);
                    }
                    for (int q = 0; q < quarts; q++) {
                        int t = dx;
                        dx = -dz;
                        dz = t;
                    }
                    BlockPos p = new BlockPos(dx, dy, dz);
                    boites.put(p, Collections.unmodifiableList(l));
                    formes.put(p, tourner(l));
                    nb += l.size();
                }
            }
            HeraliszMatchs.LOG.debug("[zombies] collisions fines {} : {} cases, {} boites", id, formes.size(), nb);
            return new Modele(id, Collections.unmodifiableMap(formes), Collections.unmodifiableMap(boites), nb);
        } catch (Exception ex) {
            HeraliszMatchs.LOG.warn("[zombies] collisions fines {} illisibles : {} (ancienne forme gardee)", id, ex.toString());
            return null;
        }
    }

    /** Au-dela de ce nombre de boites dans une case, la forme est simplifiee sur une grille de 4 px (cout de physique borne). */
    public static final int BOITES_MAX = 24;

    /** Les 4 formes tournees (S, W, N, E) d'une liste de boites du repere sud, optimisees (et simplifiees si trop fines). */
    public static VoxelShape[] tourner(List<double[]> l) {
        VoxelShape[] f = {Shapes.empty(), Shapes.empty(), Shapes.empty(), Shapes.empty()};
        for (double[] b : l) FormesDecoGiant.ajouter(f, b[0], b[1], b[2], b[3], b[4], b[5]);
        for (int i = 0; i < 4; i++) f[i] = simplifier(f[i].optimize());
        return f;
    }

    /**
     * Forme d'au plus {@link #BOITES_MAX} boites : sinon, chaque cube de 4 px touche par la forme devient plein (au plus
     * 2 px de plus sur une paroi fine), puis les cubes sont fusionnes. Calcule une fois au chargement.
     */
    public static VoxelShape simplifier(VoxelShape s) {
        if (s.isEmpty() || s.toAabbs().size() <= BOITES_MAX) return s;
        VoxelShape r = Shapes.empty();
        for (int x = 0; x < 16; x += 4) {
            for (int y = 0; y < 16; y += 4) {
                for (int z = 0; z < 16; z += 4) {
                    VoxelShape v = net.minecraft.world.level.block.Block.box(x, y, z, x + 4, y + 4, z + 4);
                    if (Shapes.joinIsNotEmpty(s, v, net.minecraft.world.phys.shapes.BooleanOp.AND)) r = Shapes.or(r, v);
                }
            }
        }
        return r.optimize();
    }

    /** Une seule boite englobante (decor lointain, hors d'atteinte : tete du robot, horloge, panneau Welcome). */
    public static VoxelShape englobante(VoxelShape s) {
        return s.isEmpty() ? s : Shapes.create(s.bounds());
    }

    /** Quarts de tour (sens S -> W -> N -> E) pour ramener un modele tourne vers {@code origine} au repere sud. */
    private static int quartsVersSud(String origine) {
        return switch (origine.toLowerCase(Locale.ROOT)) {
            case "ouest", "west" -> 3;
            case "nord", "north" -> 2;
            case "est", "east" -> 1;
            default -> 0;
        };
    }

    /** Un quart de tour d'une boite (comme la face ouest de {@link FormesDecoGiant#ajouter}) : (x, z) -> (16 - z, x). */
    private static double[] quartBoite(double[] b) {
        return new double[]{16 - b[5], b[1], b[0], 16 - b[2], b[4], b[3]};
    }

    /** Cle de deduplication d'une liste de boites (arrondie au 1/100 de px). */
    static String cle(List<double[]> l) {
        StringBuilder s = new StringBuilder();
        for (double[] b : l) {
            for (double v : b) s.append(Math.round(v * 100)).append(',');
            s.append(';');
        }
        return s.toString();
    }
}
