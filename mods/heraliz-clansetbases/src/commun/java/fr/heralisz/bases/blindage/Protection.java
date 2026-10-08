package fr.heralisz.bases.blindage;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.heralisz.bases.HeraliszBases;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Table des niveaux de blindage (BASES_SPEC §5) : {@code data/heralisz_bases/protection.json} (genere par
 * tools/bases/gen_blindage.py), ou sa copie editable {@code config/heralisz_bases/protection.json} si elle existe.
 * <ul>
 *   <li>{@code niveaux} : id de bloc -> niveau 1..8 (les {@code cfm:*} des anciens niveaux, les portes HBM de
 *   heralisz_bunker = 8, {@code blindage_1..8}) ; un bloc d'un mod absent est ignore ;</li>
 *   <li>{@code parties} : blocs-pieces d'une structure multibloc (portes HBM) qui prennent le niveau et les coups de leur
 *   controleur ;</li>
 *   <li>{@code sources} : coups et rayon par source d'explosion (ThrowableId de heralisz_tactical, ex.
 *   {@code heralisz_grenade:c4}, ou id d'entite, ex. {@code minecraft:tnt}), {@code defaut} sinon.</li>
 * </ul>
 * Lecture sans allocation ({@link #niveau(BlockState)} : table par identite de bloc). Le serveur envoie la table brute
 * aux clients (infobulle d'objet, clic droit accroupi sans effet de bloc).
 */
public final class Protection {
    private Protection() {
    }

    /** Coups portes par une explosion de cette source et rayon d'effet (blocs, centre a centre). */
    public record Source(int coups, float rayon) {
    }

    public static final ResourceLocation FICHIER = HeraliszBases.id("protection.json");
    private static final Source AUCUNE = new Source(0, 0f);

    private static volatile Map<Block, Integer> niveaux = new IdentityHashMap<>();
    private static volatile Set<Block> parties = Collections.emptySet();
    private static volatile Map<String, Source> sources = Map.of("heralisz_grenade:c4", new Source(1, 2.5f));
    private static volatile Source defaut = AUCUNE;
    /** Table brute (id -> niveau) telle que lue : envoyee aux clients. */
    private static volatile Map<String, Integer> brut = Map.of();
    private static volatile String origine = "defaut";

    /** Niveau de protection du bloc (0 = aucun). */
    public static int niveau(BlockState s) {
        return niveau(s.getBlock());
    }

    public static int niveau(Block b) {
        if (b instanceof BlindageBlock bb) return bb.niveau;
        Integer n = niveaux.get(b);
        return n == null ? 0 : n;
    }

    /** Bloc-piece d'une structure (niveau et coups de son controleur). */
    public static boolean partie(BlockState s) {
        return parties.contains(s.getBlock());
    }

    /** Source d'explosion par id (ThrowableId ou id d'entite), « defaut » sinon. */
    public static Source source(String id) {
        Source s = id == null ? null : sources.get(id);
        return s == null ? defaut : s;
    }

    public static Map<String, Integer> brut() {
        return brut;
    }

    public static int taille() {
        return niveaux.size();
    }

    public static int nombreSources() {
        return sources.size();
    }

    public static String origine() {
        return origine;
    }

    /** Copie editable de la table. */
    public static Path copieEditable() {
        return FMLPaths.CONFIGDIR.get().resolve(HeraliszBases.MODID).resolve("protection.json");
    }

    /**
     * Charge la table (serveur) : copie editable si elle existe, sinon le fichier du datapack du mod. En cas d'erreur la
     * table precedente est gardee. @return vrai si une table a ete lue
     */
    public static boolean charger(MinecraftServer server) {
        try {
            Path p = copieEditable();
            if (Files.isRegularFile(p)) {
                try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                    appliquer(JsonParser.parseReader(r).getAsJsonObject(), p.toString());
                }
                return true;
            }
            Optional<Resource> res = server.getResourceManager().getResource(FICHIER);
            if (res.isEmpty()) {
                HeraliszBases.LOG.warn("[bases] table de protection {} absente : seuls les blindage_1..8 sont proteges", FICHIER);
                return false;
            }
            try (Reader r = res.get().openAsReader()) {
                appliquer(JsonParser.parseReader(r).getAsJsonObject(), FICHIER.toString());
            }
            return true;
        } catch (Exception ex) {
            HeraliszBases.LOG.error("[bases] table de protection illisible (table precedente gardee)", ex);
            return false;
        }
    }

    private static void appliquer(JsonObject o, String depuis) {
        Map<String, Integer> lus = new LinkedHashMap<>();
        if (o.has("niveaux") && o.get("niveaux").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("niveaux").entrySet()) {
                try {
                    int n = e.getValue().getAsInt();
                    if (n > 0) lus.put(e.getKey().toLowerCase(Locale.ROOT), Math.min(64, n));
                } catch (RuntimeException ignore) {
                    // entree illisible ignoree
                }
            }
        }
        java.util.List<String> pieces = new java.util.ArrayList<>();
        if (o.has("parties") && o.get("parties").isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray("parties")) pieces.add(e.getAsString().toLowerCase(Locale.ROOT));
        }
        Map<String, Source> src = new HashMap<>();
        Source def = AUCUNE;
        if (o.has("sources") && o.get("sources").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("sources").entrySet()) {
                if (!e.getValue().isJsonObject()) continue;
                JsonObject s = e.getValue().getAsJsonObject();
                Source x = new Source(s.has("coups") ? Math.max(0, s.get("coups").getAsInt()) : 0,
                        s.has("rayon") ? Math.max(0f, Math.min(8f, s.get("rayon").getAsFloat())) : 0f);
                if (e.getKey().equals("defaut")) def = x;
                else src.put(e.getKey().toLowerCase(Locale.ROOT), x);
            }
        }
        installer(lus, pieces);
        sources = Map.copyOf(src);
        defaut = def;
        origine = depuis;
        HeraliszBases.LOG.info("[bases] table de protection : {} blocs connus ({} lus), {} parties, {} sources ({})", niveaux.size(),
                lus.size(), parties.size(), src.size(), depuis);
    }

    /** Resout les ids lus en blocs (serveur ou client) ; les blindage_1..8 sont toujours proteges a leur niveau. */
    public static void installer(Map<String, Integer> lus, List<String> pieces) {
        Map<Block, Integer> n = blindagesSeuls();
        for (Map.Entry<String, Integer> e : lus.entrySet()) {
            ResourceLocation rl = ResourceLocation.tryParse(e.getKey());
            if (rl == null || !ForgeRegistries.BLOCKS.containsKey(rl)) continue;
            n.put(ForgeRegistries.BLOCKS.getValue(rl), e.getValue());
        }
        Set<Block> p = Collections.newSetFromMap(new IdentityHashMap<>());
        for (String s : pieces) {
            ResourceLocation rl = ResourceLocation.tryParse(s);
            if (rl != null && ForgeRegistries.BLOCKS.containsKey(rl)) p.add(ForgeRegistries.BLOCKS.getValue(rl));
        }
        niveaux = n;
        parties = p;
        brut = Map.copyOf(lus);
        partiesBrutes = List.copyOf(pieces);
    }

    private static volatile List<String> partiesBrutes = List.of();

    public static List<String> partiesBrutes() {
        return partiesBrutes;
    }

    private static Map<Block, Integer> blindagesSeuls() {
        Map<Block, Integer> m = new IdentityHashMap<>();
        try {
            for (int i = 1; i <= BlindageRegistry.NIVEAUX; i++) m.put(BlindageRegistry.bloc(i), i);
        } catch (RuntimeException | ExceptionInInitializerError ex) {
            // registres pas encore prets (chargement de classe precoce) : rempli au premier chargement
        }
        return m;
    }
}
