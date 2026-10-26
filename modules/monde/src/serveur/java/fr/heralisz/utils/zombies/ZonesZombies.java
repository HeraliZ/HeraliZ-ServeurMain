package fr.heralisz.utils.zombies;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.heralisz.utils.HeraliszUtils;
import fr.heralisz.utils.zombies.ZombiesSurvie.Tenue;
import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Zones de population des zombies posées EN JEU par le staff ({@code /zombies zone ajouter <id> <type> <rayon>} à l'endroit
 * où il se tient) : commissariats, bases militaires, zones dangereuses, villages, zones calmes. Gardées hors de config/ (la
 * mise en ligne réécrit config/) : {@code heraliz_zombies/zones.json} à la racine du serveur. Une zone change la capacité et
 * la recharge des secteurs qu'elle couvre ({@link ReservesZombies}), la tenue et la part de costauds des zombies qui y
 * apparaissent ({@link Peuplement}). Fil serveur seulement.
 */
public final class ZonesZombies {
    private ZonesZombies() {
    }

    /** Effet de chaque type de zone. */
    public enum Type {
        /** commissariat : zombies en tenue de police */
        POLICE(1.3, 1.0, 0.0, 1, "police"),
        /** base, aérodrome, caserne : militaires, plus de costauds */
        MILITAIRE(1.4, 1.2, 0.20, 2, "militaire"),
        /** zone de très haute difficulté : plus de costauds, plus lente à se repeupler */
        DANGER(1.2, 1.5, 0.30, 3, "danger"),
        /** village hors des cercles de villes : capacité de 3 par secteur */
        VILLAGE(1.0, 1.0, 0.0, 0, "village"),
        /** zone calme (autour d'un camp, d'une route...) : quatre fois moins de zombies */
        CALME(0.25, 1.0, 0.0, 0, "calme");

        /** multiplicateur de la capacité des secteurs */
        public final double capacite;
        /** multiplicateur du temps de recharge */
        public final double regen;
        /** part de costauds en plus */
        public final double costauds;
        /** priorité quand deux zones se chevauchent (la plus haute gagne) */
        public final int priorite;
        public final String nom;

        Type(double capacite, double regen, double costauds, int priorite, String nom) {
            this.capacite = capacite;
            this.regen = regen;
            this.costauds = costauds;
            this.priorite = priorite;
            this.nom = nom;
        }

        @Nullable
        public static Type parNom(String s) {
            for (Type t : values()) if (t.nom.equals(s.toLowerCase(Locale.ROOT))) return t;
            return null;
        }
    }

    public record Zone(String id, Type type, double x, double z, double rayon) {
        boolean dedans(double px, double pz) {
            double dx = px - x, dz = pz - z;
            return dx * dx + dz * dz <= rayon * rayon;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static volatile List<Zone> zones = List.of();
    @Nullable
    private static Path fichier;

    static void charger(MinecraftServer srv) {
        fichier = srv.getServerDirectory().toPath().resolve("heraliz_zombies").resolve("zones.json");
        List<Zone> l = new ArrayList<>();
        if (Files.exists(fichier)) {
            try {
                JsonObject o = JsonParser.parseString(Files.readString(fichier, StandardCharsets.UTF_8)).getAsJsonObject();
                for (JsonElement el : o.getAsJsonArray("zones")) {
                    JsonObject z = el.getAsJsonObject();
                    Type t = Type.parNom(z.get("type").getAsString());
                    if (t == null) continue;
                    l.add(new Zone(z.get("id").getAsString(), t, z.get("x").getAsDouble(), z.get("z").getAsDouble(),
                            z.get("rayon").getAsDouble()));
                }
            } catch (Exception ex) {
                // fichier illisible : jamais écrasé (copie à côté), aucune zone en attendant
                HeraliszUtils.LOG.error("[zombies] {} illisible, aucune zone de zombies (copie gardee a cote)", fichier, ex);
                try {
                    Files.copy(fichier, fichier.resolveSibling("zones.json.illisible-" + System.currentTimeMillis()), StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception ignore) {
                    // rien de plus
                }
            }
        }
        zones = List.copyOf(l);
        HeraliszUtils.LOG.info("[zombies] {} zone(s) de zombies", l.size());
    }

    private static void sauver() {
        if (fichier == null) return;
        JsonObject o = new JsonObject();
        JsonArray a = new JsonArray();
        for (Zone z : zones) {
            JsonObject j = new JsonObject();
            j.addProperty("id", z.id());
            j.addProperty("type", z.type().nom);
            j.addProperty("x", Math.round(z.x() * 10) / 10.0);
            j.addProperty("z", Math.round(z.z() * 10) / 10.0);
            j.addProperty("rayon", z.rayon());
            a.add(j);
        }
        o.add("zones", a);
        try {
            Files.createDirectories(fichier.getParent());
            Path tmp = fichier.resolveSibling("zones.json.tmp");
            Files.writeString(tmp, GSON.toJson(o), StandardCharsets.UTF_8);
            Files.move(tmp, fichier, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception ex) {
            HeraliszUtils.LOG.error("[zombies] {} : ecriture impossible", fichier, ex);
        }
    }

    /** La zone de plus haute priorité qui contient ce point, ou null. */
    @Nullable
    static Zone ici(double x, double z) {
        Zone best = null;
        for (Zone c : zones) {
            if (c.dedans(x, z) && (best == null || c.type().priorite > best.type().priorite)) best = c;
        }
        return best;
    }

    static List<Zone> toutes() {
        return zones;
    }

    /** Ajoute (ou remplace, même identifiant) une zone ; écrit le fichier tout de suite. */
    static void ajouter(Zone z) {
        List<Zone> l = new ArrayList<>(zones);
        l.removeIf(x -> x.id().equalsIgnoreCase(z.id()));
        l.add(z);
        zones = List.copyOf(l);
        sauver();
        ReservesZombies.vider();
    }

    static boolean retirer(String id) {
        List<Zone> l = new ArrayList<>(zones);
        if (!l.removeIf(x -> x.id().equalsIgnoreCase(id))) return false;
        zones = List.copyOf(l);
        sauver();
        ReservesZombies.vider();
        return true;
    }

    /** Tenue imposée par la zone (police, militaire), sinon null (tenue de l'endroit). */
    @Nullable
    static Tenue tenue(@Nullable Zone z) {
        if (z == null) return null;
        return switch (z.type()) {
            case POLICE -> Tenue.POLICE;
            case MILITAIRE -> Tenue.MILITAIRE;
            default -> null;
        };
    }
}
