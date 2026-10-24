package fr.heralisz.utils.zombies;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.heralisz.utils.HeraliszUtils;
import fr.heralisz.utils.zombies.ZombiesSurvie.Tenue;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Apparition des zombies de la survie sur la carte (overworld) : un zombie de Minecraft qui apparait (naturellement ou a
 * la creation du terrain) est remplace par un {@link ZombieSurvie} dont la tenue depend de l'endroit :
 * <ul>
 * <li>arctique (ville « arctique » de zones.json, zones « froid » de Decors) : arctique seulement ;</li>
 * <li>prison : prisonniers seulement ; aerodrome et base militaire : militaires seulement ; dans ces trois-la, les
 * zombies de Mutants & Zombies sont remplaces aussi ;</li>
 * <li>zones contaminees (villes gaz et radio, zones « gaz » / « radiation » de Decors) : surtout des hazmat ;</li>
 * <li>ailleurs : une des 5 tenues de ville.</li>
 * </ul>
 * Les zombies poses par une commande, un oeuf ou l'assaut des drapeaux ne changent pas. Villes et zones relues quand
 * leur fichier change (toutes les 10 s au plus) ; l'evenement peut venir des fils de creation du terrain : seules des
 * listes figees sont lues.
 */
@Mod.EventBusSubscriber(modid = HeraliszUtils.MODID)
public final class SpawnZombiesServeur {
    private SpawnZombiesServeur() {
    }

    private enum Genre { ARCTIQUE, PRISON, MILITAIRE, CONTAMINE }

    private record Cercle(double x, double z, double r, Genre g) {
        boolean dedans(double px, double pz) {
            double dx = px - x, dz = pz - z;
            return dx * dx + dz * dz <= r * r;
        }
    }

    private record Poly(double[] xs, double[] zs, Genre g) {
        boolean dedans(double px, double pz) {
            boolean in = false;
            for (int i = 0, j = xs.length - 1; i < xs.length; j = i++) {
                if ((zs[i] > pz) != (zs[j] > pz) && px < (xs[j] - xs[i]) * (pz - zs[i]) / (zs[j] - zs[i]) + xs[i]) in = !in;
            }
            return in;
        }
    }

    private static volatile List<Cercle> villes = List.of();
    private static volatile List<Poly> zones = List.of();
    private static long dateVilles = -1, dateZones = -1;

    @SubscribeEvent
    public static void demarrage(ServerStartedEvent e) {
        dateVilles = dateZones = -1;
        relire(e.getServer());
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase == TickEvent.Phase.END && e.getServer().getTickCount() % 200 == 0) relire(e.getServer());
    }

    @SubscribeEvent
    public static void apparition(MobSpawnEvent.FinalizeSpawn e) {
        Mob m = e.getEntity();
        if (e.isSpawnCancelled() || m instanceof ZombieSurvie || !ZombiesConfig.ACTIF.get()) return;   // refusé avant (PlafondZombies)
        switch (e.getSpawnType()) {
            case NATURAL, CHUNK_GENERATION, REINFORCEMENT, PATROL -> {
            }
            default -> {
                return;
            }
        }
        ServerLevelAccessor acc = e.getLevel();
        ServerLevel sl = acc.getLevel();
        if (sl.dimension() != Level.OVERWORLD) return;
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(m.getType());
        if (id == null) return;
        Genre g = genre(e.getX(), e.getZ());
        boolean ferme = g == Genre.ARCTIQUE || g == Genre.PRISON || g == Genre.MILITAIRE;
        if (!dans(ZombiesConfig.REMPLACES.get(), id) && !(ferme && dans(ZombiesConfig.MUTANTS.get(), id))) return;
        ZombieSurvie z = ZombiesSurvie.ZOMBIE.get().create(sl);
        if (z == null) return;
        e.setSpawnCancelled(true);
        var rnd = sl.getRandom();
        z.tenue(tenue(g, rnd));
        z.moveTo(e.getX(), e.getY(), e.getZ(), m.getYRot(), 0f);
        z.finalizeSpawn(acc, acc.getCurrentDifficultyAt(BlockPos.containing(e.getX(), e.getY(), e.getZ())), e.getSpawnType(), null, null);
        acc.addFreshEntityWithPassengers(z);
    }

    private static Tenue tenue(Genre g, net.minecraft.util.RandomSource rnd) {
        if (g == null) return Tenue.de(rnd.nextInt(Tenue.VILLES));
        return switch (g) {
            case ARCTIQUE -> Tenue.ARCTIQUE;
            case PRISON -> Tenue.PRISONNIER;
            case MILITAIRE -> Tenue.MILITAIRE;
            case CONTAMINE -> rnd.nextDouble() < ZombiesConfig.PART_HAZMAT.get() ? Tenue.HAZMAT : Tenue.de(rnd.nextInt(Tenue.VILLES));
        };
    }

    /** Zombie HeraliZ pose sans tenue (vagues des drapeaux, /summon) : tenue de l'endroit ou il arrive. */
    @SubscribeEvent
    public static void arrivee(net.minecraftforge.event.entity.EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide()) return;
        // sur la carte : jamais de zombie de Minecraft (zombie, desert, villageois, noye), d'ou qu'il vienne
        if (ZombiesConfig.ACTIF.get() && e.getLevel().dimension() == Level.OVERWORLD && !(e.getEntity() instanceof ZombieSurvie)
                && interdit(e.getEntity().getType())) {
            e.setCanceled(true);
            return;
        }
        if (!(e.getEntity() instanceof ZombieSurvie z) || z.tenueChoisie) return;
        Genre g = e.getLevel().dimension() == Level.OVERWORLD ? genre(z.getX(), z.getZ()) : null;
        z.tenue(tenue(g, z.getRandom()));
    }

    /** Types interdits sur la carte deja jugés (chaque entité qui arrive passe ici : balles, objets au sol, créatures) ;
     *  vidé quand la liste du réglage change. */
    private static final Map<EntityType<?>, Boolean> INTERDIT = new ConcurrentHashMap<>();
    private static volatile List<? extends String> interditsLus;

    private static boolean interdit(EntityType<?> type) {
        List<? extends String> l = ZombiesConfig.INTERDITS.get();
        if (l != interditsLus) {
            INTERDIT.clear();
            interditsLus = l;
        }
        Boolean b = INTERDIT.get(type);
        if (b == null) {
            ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(type);
            b = id != null && dans(l, id);
            INTERDIT.put(type, b);
        }
        return b;
    }

    private static boolean dans(List<? extends String> liste, ResourceLocation id) {
        String s = id.toString();
        for (String l : liste) {
            if (l.equals(s) || (l.endsWith(":*") && l.substring(0, l.length() - 2).equals(id.getNamespace()))) return true;
        }
        return false;
    }

    /** Genre de l'endroit : ville d'abord (prison, militaire...), puis zones de Decors. null : ville ordinaire. */
    private static Genre genre(double x, double z) {
        for (Cercle c : villes) if (c.dedans(x, z)) return c.g;
        for (Poly p : zones) if (p.dedans(x, z)) return p.g;
        return null;
    }

    private static void relire(MinecraftServer srv) {
        Path fv = FMLPaths.CONFIGDIR.get().resolve("heralisz_clans").resolve("zones.json");
        Path fz = srv.getWorldPath(LevelResource.ROOT).resolve("data").resolve("heraliz_zones.json");
        try {
            long d = Files.exists(fv) ? Files.getLastModifiedTime(fv).toMillis() : 0;
            if (d != dateVilles) {
                dateVilles = d;
                List<Cercle> l = new ArrayList<>();
                if (d != 0) {
                    Set<String> ar = Set.copyOf(ZombiesConfig.ARCTIQUE.get()), pr = Set.copyOf(ZombiesConfig.PRISON.get()),
                            mi = Set.copyOf(ZombiesConfig.MILITAIRE.get()), co = Set.copyOf(ZombiesConfig.CONTAMINEES.get());
                    try (Reader in = Files.newBufferedReader(fv, StandardCharsets.UTF_8)) {
                        for (JsonElement el : JsonParser.parseReader(in).getAsJsonArray()) {
                            JsonObject v = el.getAsJsonObject();
                            String id = v.has("id") ? v.get("id").getAsString() : "";
                            Genre g = ar.contains(id) ? Genre.ARCTIQUE : pr.contains(id) ? Genre.PRISON : mi.contains(id) ? Genre.MILITAIRE
                                    : co.contains(id) ? Genre.CONTAMINE : null;
                            if (g != null) l.add(new Cercle(v.get("x").getAsDouble(), v.get("z").getAsDouble(),
                                    v.has("rayon") ? v.get("rayon").getAsDouble() : 0, g));
                        }
                    }
                }
                villes = List.copyOf(l);
                HeraliszUtils.LOG.info("Zombies de la survie : {} villes a tenue", l.size());
            }
        } catch (Exception ex) {
            HeraliszUtils.LOG.warn("Zombies de la survie : villes illisibles ({})", fv, ex);
        }
        try {
            long d = Files.exists(fz) ? Files.getLastModifiedTime(fz).toMillis() : 0;
            if (d != dateZones) {
                dateZones = d;
                List<Poly> l = new ArrayList<>();
                if (d != 0) {
                    try (Reader in = Files.newBufferedReader(fz, StandardCharsets.UTF_8)) {
                        for (JsonElement el : JsonParser.parseReader(in).getAsJsonObject().getAsJsonArray("zones")) {
                            JsonObject o = el.getAsJsonObject();
                            String genre = o.has("genre") ? o.get("genre").getAsString() : "";
                            Genre g = switch (genre) {
                                case "froid" -> Genre.ARCTIQUE;
                                case "gaz", "radiation" -> Genre.CONTAMINE;
                                default -> null;
                            };
                            JsonArray pts = o.getAsJsonArray("points");
                            if (g == null || pts == null || pts.size() < 3) continue;
                            double[] xs = new double[pts.size()], zs = new double[pts.size()];
                            for (int i = 0; i < pts.size(); i++) {
                                JsonArray p = pts.get(i).getAsJsonArray();
                                xs[i] = p.get(0).getAsDouble();
                                zs[i] = p.get(1).getAsDouble();
                            }
                            l.add(new Poly(xs, zs, g));
                        }
                    }
                }
                zones = List.copyOf(l);
                HeraliszUtils.LOG.info("Zombies de la survie : {} zones de Decors", l.size());
            }
        } catch (Exception ex) {
            HeraliszUtils.LOG.warn("Zombies de la survie : zones illisibles ({})", fz, ex);
        }
    }
}
