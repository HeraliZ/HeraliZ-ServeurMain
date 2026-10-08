package fr.heralisz.matchs.zombies.contenu;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.heralisz.matchs.HeraliszMatchs;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
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
import java.util.Map;
import java.util.Set;

/**
 * Decors cuits de The Giant (D-053) : lit une fois {@code data/heralisz_matchs/deco_giant/formes.json} (genere par
 * {@code tools/matchs/zombies/gen_decor_giant.py}, dans le jar, lisible des deux cotes) : taille du prop en cases, nombre
 * de cases ({@code part}), proprietes booleennes facultatives, lumiere par etat et boites de collision par case (pixels,
 * pour {@code facing=south}, tournees ici vers les 3 autres faces) ; 0.2.3 : remplacees par les collisions FINES du modele
 * ({@link CollisionsFines}, {@code collisions/<id>.json}) quand elles existent.
 * <p>Lumiere : un prop ne s'eclaire que par UNE case (la case « lumiere », voir {@link Bloc#partLumiere}) au lieu de toutes
 * ses cases (un generateur de 360 cases ferait 360 sources) : moins de calculs d'eclairage a la pose et au courant. Meme
 * regle que le generateur de carte ({@code tools/matchs/cartes/giant_pose.py}), qui verifie que cette case est posee.
 */
public final class FormesDecoGiant {
    private FormesDecoGiant() {
    }

    /**
     * Un decor : {@code taille} = largeur, hauteur, profondeur vue de face (en cases ; {@code pas} blocs par case pour le
     * decor lointain), {@code parts} = nombre de cases, proprietes booleennes, lumieres ({@code ""} = toujours,
     * {@code "allume=true"} = dans cet etat), formes {@code [part][Direction.get2DDataValue()]} (null = pas de collision).
     */
    public record Bloc(String id, int largeur, int hauteur, int profondeur, int pas, int parts, List<String> proprietes,
                       Map<String, Integer> lumieres, VoxelShape[][] formes) {
        /** Case qui porte la lumiere du prop : colonne du milieu, rang du haut, rang du milieu (ou du dos si pair). */
        public int partLumiere() {
            if (parts <= 1) return 0;
            int a = (largeur - 1) / 2, b = hauteur - 1, c = (profondeur - 1) / 2;
            return a + largeur * (b + hauteur * c);
        }

        /** Vrai si le decor emet de la lumiere dans au moins un etat. */
        public boolean lumineux() {
            for (int v : lumieres.values()) if (v > 0) return true;
            return false;
        }

        /** Niveau de lumiere de l'etat (seulement sur la case lumiere). */
        public int lumiere(BlockState s, Property<Integer> part) {
            if (lumieres.isEmpty() || s.getValue(part) != partLumiere()) return 0;
            int n = 0;
            for (Map.Entry<String, Integer> e : lumieres.entrySet()) {
                String k = e.getKey();
                if (k.isEmpty()) {
                    n = Math.max(n, e.getValue());
                    continue;
                }
                int eg = k.indexOf('=');
                if (eg <= 0) continue;
                String nom = k.substring(0, eg), val = k.substring(eg + 1);
                for (Property<?> p : s.getProperties()) {
                    if (p.getName().equals(nom) && String.valueOf(s.getValue(p)).equals(val)) n = Math.max(n, e.getValue());
                }
            }
            return n;
        }

        /** Forme de la case {@code part} tournee vers {@code face2D} (index Direction.get2DDataValue). */
        public VoxelShape forme(int part, int face2D) {
            if (part < 0 || part >= formes.length || formes[part] == null) return Shapes.empty();
            return formes[part][face2D & 3];
        }
    }

    private static final Map<String, Bloc> BLOCS = new LinkedHashMap<>();
    /** Decors hors d'atteinte (au loin, sur les toits) : une boite englobante par case au lieu des collisions fines. */
    private static final Set<String> LOINTAINS = Set.of("zb_deco_tete_robot", "zb_deco_horloge", "zb_deco_welcome");

    static {
        charger();
    }

    private static void charger() {
        long t0 = System.nanoTime();
        int fins = 0;
        try (InputStream in = FormesDecoGiant.class.getResourceAsStream("/data/heralisz_matchs/deco_giant/formes.json")) {
            if (in == null) {
                HeraliszMatchs.LOG.error("[zombies] deco_giant/formes.json absent : aucun decor de The Giant");
                return;
            }
            JsonObject racine = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("blocs");
            for (Map.Entry<String, JsonElement> e : racine.entrySet()) {
                JsonObject o = e.getValue().getAsJsonObject();
                int parts = Math.max(1, o.get("parts").getAsInt());
                JsonArray t = o.getAsJsonArray("taille");
                int w = t.get(0).getAsInt(), h = t.get(1).getAsInt(), d = t.get(2).getAsInt();
                int pas = o.has("pas_case") ? o.get("pas_case").getAsInt() : 1;
                List<String> props = new ArrayList<>();
                if (o.has("proprietes")) props.addAll(o.getAsJsonObject("proprietes").keySet());
                Map<String, Integer> lum = new LinkedHashMap<>();
                if (o.has("lumiere")) for (Map.Entry<String, JsonElement> l : o.getAsJsonObject("lumiere").entrySet()) lum.put(l.getKey(), l.getValue().getAsInt());
                VoxelShape[][] f = new VoxelShape[Math.max(2, parts)][];
                if (o.has("formes")) {
                    for (Map.Entry<String, JsonElement> pe : o.getAsJsonObject("formes").entrySet()) {
                        int part = Integer.parseInt(pe.getKey());
                        if (part < 0 || part >= f.length) continue;
                        VoxelShape[] parFace = {Shapes.empty(), Shapes.empty(), Shapes.empty(), Shapes.empty()};
                        for (JsonElement be : pe.getValue().getAsJsonArray()) {
                            JsonArray a = be.getAsJsonArray();
                            double x0 = a.get(0).getAsDouble(), y0 = a.get(1).getAsDouble(), z0 = a.get(2).getAsDouble();
                            double x1 = a.get(3).getAsDouble(), y1 = a.get(4).getAsDouble(), z1 = a.get(5).getAsDouble();
                            ajouter(parFace, x0, y0, z0, x1, y1, z1);
                        }
                        for (int k = 0; k < 4; k++) parFace[k] = parFace[k].optimize();
                        f[part] = parFace;
                    }
                }
                // 0.2.3 : collisions FINES du modele (collisions/<id>.json) a la place des boites posees a la main
                if (finesDe(e.getKey(), w, h, d, pas, f)) fins++;
                // decor lointain, hors d'atteinte des joueurs : une seule boite par case (aucun cout de physique inutile)
                if (LOINTAINS.contains(e.getKey()) || pas > 1) {
                    for (VoxelShape[] parFace : f) {
                        if (parFace == null) continue;
                        for (int k = 0; k < 4; k++) parFace[k] = CollisionsFines.englobante(parFace[k]);
                    }
                } else {
                    for (VoxelShape[] parFace : f) {
                        if (parFace == null) continue;
                        for (int k = 0; k < 4; k++) parFace[k] = CollisionsFines.simplifier(parFace[k]);
                    }
                }
                BLOCS.put(e.getKey(), new Bloc(e.getKey(), w, h, d, pas, parts, Collections.unmodifiableList(props), Collections.unmodifiableMap(lum), f));
            }
        } catch (Exception ex) {
            HeraliszMatchs.LOG.error("[zombies] deco_giant/formes.json illisible : decors de The Giant sans forme", ex);
        }
        HeraliszMatchs.LOG.debug("[zombies] {} decors de The Giant lus en {} us ({} avec collisions fines)", BLOCS.size(), (System.nanoTime() - t0) / 1000, fins);
    }

    /**
     * Remplace les formes du decor par ses collisions fines si {@code collisions/<id>.json} (ou sans le prefixe
     * {@code zb_deco_}) existe : case (dx, dy, dz) comptee depuis le coin (gauche, bas, dos) vu de face ; {@code pas_case} 1 :
     * part = dx + largeur * (dy + hauteur * dz) ; decor lointain ({@code pas_case} n > 1) : la case du morceau (A, B, C) est
     * le bloc (n A + n/2, n B, n C + n/2), les autres blocs n'existent pas dans le monde. Un part sans case fine n'a plus de
     * collision (le modele y est vide). Rend vrai si des collisions fines ont ete lues.
     */
    private static boolean finesDe(String id, int w, int h, int d, int pas, VoxelShape[][] f) {
        CollisionsFines.Modele m = CollisionsFines.premier(id, id.startsWith("zb_deco_") ? id.substring(8) : id);
        if (m == null) return false;
        java.util.Arrays.fill(f, null);
        int dec = pas > 1 ? pas / 2 : 0;
        for (Map.Entry<net.minecraft.core.BlockPos, VoxelShape[]> e : m.formes().entrySet()) {
            net.minecraft.core.BlockPos c = e.getKey();
            int x = c.getX() - dec, y = c.getY(), z = c.getZ() - dec;
            if (x < 0 || y < 0 || z < 0 || x % pas != 0 || y % pas != 0 || z % pas != 0) continue;
            int a = x / pas, b = y / pas, cc = z / pas;
            if (a >= w || b >= h || cc >= d) continue;
            int part = a + w * (b + h * cc);
            if (part < f.length) f[part] = e.getValue();
        }
        return true;
    }

    /**
     * Ajoute une boite (pixels, repere {@code facing=south}) aux 4 orientations, tournee autour du centre de la case
     * (sens horaire vu du dessus) : west (x, z) -> (16 - z, x), north -> (16 - x, 16 - z), east -> (z, 16 - x).
     * Index = Direction.get2DDataValue() : S 0, W 1, N 2, E 3.
     */
    public static void ajouter(VoxelShape[] parFace, double x0, double y0, double z0, double x1, double y1, double z1) {
        x0 = borne(x0);
        x1 = borne(x1);
        y0 = borne(y0);
        y1 = borne(y1);
        z0 = borne(z0);
        z1 = borne(z1);
        if (x1 <= x0 || y1 <= y0 || z1 <= z0) return;
        parFace[0] = Shapes.or(parFace[0], Block.box(x0, y0, z0, x1, y1, z1));
        parFace[1] = Shapes.or(parFace[1], Block.box(16 - z1, y0, x0, 16 - z0, y1, x1));
        parFace[2] = Shapes.or(parFace[2], Block.box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0));
        parFace[3] = Shapes.or(parFace[3], Block.box(z0, y0, 16 - x1, z1, y1, 16 - x0));
    }

    private static double borne(double v) {
        return Math.max(0, Math.min(16, v));
    }

    @Nullable
    public static Bloc bloc(String id) {
        return BLOCS.get(id);
    }

    /** Identifiants (sans espace de noms), dans l'ordre du fichier. */
    public static Set<String> ids() {
        return BLOCS.keySet();
    }
}
