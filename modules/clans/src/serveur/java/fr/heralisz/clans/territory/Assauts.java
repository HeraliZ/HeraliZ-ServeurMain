package fr.heralisz.clans.territory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.heralisz.clans.HeraliszClans;
import fr.heralisz.clans.model.Clan;
import fr.heralisz.clans.model.Json;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Assaut des morts pendant une capture : quand un clan capture une zone, 3 vagues de zombies de plus en plus dures
 * apparaissent autour des capteurs (a 0 %, 33 % et 66 % de la barre par defaut) et les attaquent, pour les pousser
 * hors de la zone. L'assaut s'arrete quand la barre retombe a 0 ou que la zone est prise : les zombies restants
 * disparaissent quelques secondes apres. Rien n'est sauvegarde : un zombie d'assaut retrouve apres un redemarrage est
 * retire. Reglages dans {@code config/heralisz_clans/assaut.json} (ecrit s'il manque, relu par /territoire recharger).
 */
@Mod.EventBusSubscriber(modid = HeraliszClans.MODID)
public final class Assauts {
    public static final String TAG = "heraliz_assaut";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final UUID PV_ID = UUID.fromString("5b1c3e0a-6f3d-4a52-9d2e-3a7c1b9e0a11");
    private static final UUID DEGATS_ID = UUID.fromString("5b1c3e0a-6f3d-4a52-9d2e-3a7c1b9e0a12");
    /** Zombies d'assaut vivants, toutes zones (un zombie tague hors de cette liste est retire a son chargement). */
    private static final Set<UUID> SUIVIS = new HashSet<>();

    /** max : au plus N de ce type par vague (0 = sans limite). Dans « fixes », poids = nombre exact pose a chaque fois. */
    private record Entite(String id, int poids, int max) {
        Entite(String id, int poids) {
            this(id, poids, 0);
        }
    }

    /** fixes : zombies poses en plus, toujours en meme nombre (ex. 3 Brutes a la derniere vague du niveau 3). */
    private record Vague(int nombre, double parJoueur, double parValeur, double pv, double degats, List<Entite> entites, List<Entite> fixes) {
    }

    private static final String[] NOMS = {"facile", "normal", "dur"};

    private static boolean actif = true;
    private static double[] seuils = {0, 0.33, 0.66};
    private static int distanceMin = 14, distanceMax = 24, maxVivants = 40, disparitionS = 20;
    /** Vagues par difficulte de la zone (index 0 = niveau 1). */
    private static List<List<Vague>> difficultes = List.of(defautVagues(1), defautVagues(2), defautVagues(3));

    /** Un assaut en cours sur une zone. */
    private static final class Assaut {
        final UUID clan;
        final List<Vague> liste;
        int vagues;
        final List<UUID> zombies = new ArrayList<>();
        /** Heure de fin (ms) ; 0 tant que l'assaut dure. */
        long finLe;

        Assaut(UUID clan, List<Vague> liste) {
            this.clan = clan;
            this.liste = liste;
        }
    }

    private final Map<String, Assaut> actifs = new HashMap<>();
    private final List<Assaut> finissants = new ArrayList<>();
    private final RandomSource alea = RandomSource.create();

    private static List<Vague> listeDe(Zone z) {
        return difficultes.get(Math.max(1, Math.min(3, z.difficulte)) - 1);
    }

    // ------------------------------------------------------------------ reglages
    private static final String Z = "minecraft:zombie", HUSK = "minecraft:husk", CRAWLER = "mutantszombies:crawler",
            BLISTER = "mutantszombies:blister_zombie", BRUTE = "mutantszombies:zombie_brute";

    /** Valeurs par defaut ; pas de Spitter (mutantszombies:spitter) : son crachat est une boule de slime (choix du proprietaire). */
    private static List<Vague> defautVagues(int difficulte) {
        List<Vague> l = new ArrayList<>();
        switch (difficulte) {
            case 1 -> {
                l.add(new Vague(3, 1, 0.2, 1.0, 1.0, List.of(new Entite(Z, 8), new Entite(HUSK, 2)), List.of()));
                l.add(new Vague(4, 1, 0.2, 1.1, 1.05, List.of(new Entite(Z, 6), new Entite(CRAWLER, 2)), List.of()));
                l.add(new Vague(5, 1, 0.3, 1.2, 1.1, List.of(new Entite(Z, 5), new Entite(CRAWLER, 2), new Entite(BLISTER, 2)), List.of()));
            }
            case 3 -> {
                l.add(new Vague(6, 2, 0.4, 1.25, 1.1, List.of(new Entite(Z, 6), new Entite(HUSK, 2), new Entite(CRAWLER, 3)), List.of()));
                l.add(new Vague(9, 2, 0.5, 1.5, 1.25, List.of(new Entite(Z, 4), new Entite(CRAWLER, 4), new Entite(BLISTER, 3)), List.of()));
                l.add(new Vague(8, 2, 0.5, 1.75, 1.4, List.of(new Entite(Z, 4), new Entite(CRAWLER, 3), new Entite(BLISTER, 3)),
                        List.of(new Entite(BRUTE, 3))));
            }
            default -> {
                l.add(new Vague(4, 1, 0.3, 1.0, 1.0, List.of(new Entite(Z, 8), new Entite(HUSK, 2)), List.of()));
                l.add(new Vague(6, 1.5, 0.4, 1.25, 1.15, List.of(new Entite(Z, 6), new Entite(CRAWLER, 4)), List.of()));
                l.add(new Vague(8, 2, 0.5, 1.5, 1.3, List.of(new Entite(Z, 5), new Entite(CRAWLER, 2), new Entite(BLISTER, 3),
                        new Entite(BRUTE, 2, 1)), List.of()));
            }
        }
        return l;
    }

    private static JsonArray entitesJson(List<Entite> l, boolean fixes) {
        JsonArray en = new JsonArray();
        for (Entite e : l) {
            JsonObject x = new JsonObject();
            x.addProperty("id", e.id);
            x.addProperty(fixes ? "nombre" : "poids", e.poids);
            if (e.max > 0) x.addProperty("max", e.max);
            en.add(x);
        }
        return en;
    }

    private static JsonObject defautJson() {
        JsonObject o = new JsonObject();
        o.addProperty("_aide", "Vagues de zombies pendant une capture de zone, par difficulte de la zone (/territoire difficulte <id> <1-3>). "
                + "seuils = barre de capture (0 a 1) ou chaque vague part ; nombre de zombies = nombre + par_joueur x (capteurs - 1) "
                + "+ par_valeur x valeur de la zone, tires dans entites (poids, max = au plus N de ce type) ; fixes = poses en plus, "
                + "toujours ce nombre ; pv et degats = multiplicateurs. Pas de Spitter : son crachat est une boule de slime.");
        o.addProperty("actif", true);
        JsonArray s = new JsonArray();
        for (double v : new double[]{0, 0.33, 0.66}) s.add(v);
        o.add("seuils", s);
        o.addProperty("distance_min", 14);
        o.addProperty("distance_max", 24);
        o.addProperty("max_vivants", 40);
        o.addProperty("disparition_s", 20);
        JsonArray ds = new JsonArray();
        for (int d = 1; d <= 3; d++) {
            JsonObject dj = new JsonObject();
            dj.addProperty("niveau", d);
            dj.addProperty("nom", NOMS[d - 1]);
            JsonArray va = new JsonArray();
            for (Vague v : defautVagues(d)) {
                JsonObject w = new JsonObject();
                w.addProperty("nombre", v.nombre);
                w.addProperty("par_joueur", v.parJoueur);
                w.addProperty("par_valeur", v.parValeur);
                w.addProperty("pv", v.pv);
                w.addProperty("degats", v.degats);
                w.add("entites", entitesJson(v.entites, false));
                if (!v.fixes.isEmpty()) w.add("fixes", entitesJson(v.fixes, true));
                va.add(w);
            }
            dj.add("vagues", va);
            ds.add(dj);
        }
        o.add("difficultes", ds);
        return o;
    }

    private static List<Entite> lireEntites(JsonObject w, String cle, boolean fixes) {
        List<Entite> en = new ArrayList<>();
        for (JsonElement x : Json.arr(w, cle)) {
            if (!x.isJsonObject()) continue;
            JsonObject xo = x.getAsJsonObject();
            String id = Json.str(xo, "id", "");
            int poids = Math.min(16, Json.integer(xo, fixes ? "nombre" : "poids", 1));
            int maxType = Math.max(0, Json.integer(xo, "max", 0));
            if (!id.isEmpty() && poids > 0) en.add(new Entite(id, poids, maxType));
        }
        return en;
    }

    private static List<Vague> lireVagues(JsonArray a) {
        List<Vague> lues = new ArrayList<>();
        for (JsonElement e : a) {
            if (!e.isJsonObject()) continue;
            JsonObject w = e.getAsJsonObject();
            List<Entite> en = lireEntites(w, "entites", false);
            List<Entite> fx = lireEntites(w, "fixes", true);
            if (en.isEmpty() && fx.isEmpty()) continue;
            lues.add(new Vague(Math.max(0, Json.integer(w, "nombre", 4)), Math.max(0, Json.dbl(w, "par_joueur", 1)),
                    Math.max(0, Json.dbl(w, "par_valeur", 0)), Math.max(0.1, Json.dbl(w, "pv", 1)), Math.max(0.1, Json.dbl(w, "degats", 1)),
                    en, fx));
        }
        return lues;
    }

    /** Charge assaut.json (l'ecrit avec les valeurs par defaut s'il manque) ; illisible = valeurs par defaut. */
    public static void charger() {
        Path f = FMLPaths.CONFIGDIR.get().resolve("heralisz_clans").resolve("assaut.json");
        try {
            if (!Files.exists(f)) {
                Files.createDirectories(f.getParent());
                Files.writeString(f, GSON.toJson(defautJson()), StandardCharsets.UTF_8);
                HeraliszClans.LOG.info("[clans] assaut.json ecrit avec les valeurs par defaut");
            }
            JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            boolean a = Json.bool(o, "actif", true);
            int dMin = Math.max(4, Json.integer(o, "distance_min", 14));
            int dMax = Math.max(dMin + 1, Json.integer(o, "distance_max", 24));
            int max = Math.max(1, Json.integer(o, "max_vivants", 40));
            int disp = Math.max(0, Json.integer(o, "disparition_s", 20));
            List<List<Vague>> ds = new ArrayList<>(List.of(defautVagues(1), defautVagues(2), defautVagues(3)));
            JsonArray da = Json.arr(o, "difficultes");
            if (da.size() > 0) {
                for (int i = 0; i < Math.min(3, da.size()); i++) {
                    if (!da.get(i).isJsonObject()) continue;
                    JsonObject dj = da.get(i).getAsJsonObject();
                    int niveau = Math.max(1, Math.min(3, Json.integer(dj, "niveau", i + 1)));
                    List<Vague> l = lireVagues(Json.arr(dj, "vagues"));
                    if (!l.isEmpty()) ds.set(niveau - 1, l);
                }
            } else {
                // ancien fichier (une seule liste « vagues ») : elle sert au niveau 2
                List<Vague> l = lireVagues(Json.arr(o, "vagues"));
                if (!l.isEmpty()) ds.set(1, l);
            }
            int n = 0;
            for (List<Vague> l : ds) n = Math.max(n, l.size());
            double[] s = new double[n];
            JsonArray sa = Json.arr(o, "seuils");
            for (int i = 0; i < n; i++) {
                double def = (double) i / n;
                s[i] = i < sa.size() && sa.get(i).isJsonPrimitive() ? Math.max(0, Math.min(0.99, sa.get(i).getAsDouble())) : def;
            }
            actif = a;
            distanceMin = dMin;
            distanceMax = dMax;
            maxVivants = max;
            disparitionS = disp;
            difficultes = ds;
            seuils = s;
            HeraliszClans.LOG.info("[clans] assaut.json charge : {} / {} / {} vague(s), {}", ds.get(0).size(), ds.get(1).size(), ds.get(2).size(),
                    actif ? "actif" : "coupe");
        } catch (Exception ex) {
            HeraliszClans.LOG.error("[clans] assaut.json illisible, valeurs par defaut gardees", ex);
        }
    }

    // ------------------------------------------------------------------ cycle (Territoires, toutes les 10 ticks)
    /**
     * @param capteurEnCours clan dont la barre est en cours (null : barre a 0)
     * @param capteur        clan qui fait avancer la barre a ce cycle (null : personne)
     * @param enCapture      la barre avance en ce moment
     * @param capteurs joueurs du clan capteur presents dans la zone
     */
    void cycle(ServerLevel level, Zone zone, @Nullable UUID capteurEnCours, float progression, @Nullable Clan capteur, boolean enCapture,
               List<ServerPlayer> capteurs, List<ServerPlayer> presents, long now) {
        Assaut a = actifs.get(zone.id);
        if (a != null && (capteurEnCours == null || !capteurEnCours.equals(a.clan))) {
            finir(zone.id, now, level, presents, false);
            a = null;
        }
        if (!actif) return;
        if (a == null && enCapture && capteur != null) {
            a = new Assaut(capteur.id, listeDe(zone));
            actifs.put(zone.id, a);
        }
        if (a == null) return;
        a.zombies.removeIf(u -> {
            Entity en = level.getEntity(u);
            if (en == null || !en.isAlive()) {
                SUIVIS.remove(u);
                return true;
            }
            return false;
        });
        if (enCapture && a.vagues < a.liste.size() && a.vagues < seuils.length && progression >= seuils[a.vagues] && !capteurs.isEmpty()) {
            lancerVague(level, zone, a, capteurs, presents);
        }
        // les zombies sans cible repartent vers le capteur le plus proche
        if (!capteurs.isEmpty()) {
            for (UUID u : a.zombies) {
                if (level.getEntity(u) instanceof Mob m && (m.getTarget() == null || !m.getTarget().isAlive())) {
                    ServerPlayer p = plusProche(m, capteurs);
                    if (p != null) m.setTarget(p);
                }
            }
        }
    }

    /** Fin d'assaut annoncee par Territoires au moment ou la zone est prise. */
    void prise(String zoneId, ServerLevel level, List<ServerPlayer> presents, long now) {
        if (actifs.containsKey(zoneId)) finir(zoneId, now, level, presents, true);
    }

    private void finir(String zoneId, long now, ServerLevel level, List<ServerPlayer> presents, boolean victoire) {
        Assaut a = actifs.remove(zoneId);
        if (a == null) return;
        a.finLe = now + disparitionS * 1000L;
        if (!a.zombies.isEmpty()) finissants.add(a);
        if (victoire && a.vagues > 0) {
            for (ServerPlayer p : presents) {
                p.sendSystemMessage(Component.translatableWithFallback("heralisz_clans.assaut.fin", "§7Les morts se dispersent…"));
            }
        }
    }

    /** Chaque tick : retire les zombies des assauts finis une fois le delai passe. */
    void tick(ServerLevel level, long now) {
        if (finissants.isEmpty()) return;
        Iterator<Assaut> it = finissants.iterator();
        while (it.hasNext()) {
            Assaut a = it.next();
            if (now < a.finLe) continue;
            for (UUID u : a.zombies) {
                SUIVIS.remove(u);
                Entity en = level.getEntity(u);
                if (en != null && en.isAlive()) {
                    level.sendParticles(ParticleTypes.LARGE_SMOKE, en.getX(), en.getY() + 1, en.getZ(), 8, 0.3, 0.5, 0.3, 0.02);
                    en.discard();
                }
            }
            it.remove();
        }
    }

    /** Arret du serveur : plus aucun zombie d'assaut. */
    void arreter(ServerLevel level) {
        for (Assaut a : actifs.values()) finissants.add(a);
        actifs.clear();
        for (Assaut a : finissants) a.finLe = 0;
        tick(level, Long.MAX_VALUE);
    }

    private void lancerVague(ServerLevel level, Zone zone, Assaut a, List<ServerPlayer> capteurs, List<ServerPlayer> presents) {
        Vague v = a.liste.get(a.vagues);
        a.vagues++;
        int n = (int) Math.round(v.nombre + v.parJoueur * (capteurs.size() - 1) + v.parValeur * zone.valeur);
        n = Math.min(n, maxVivants - a.zombies.size());
        int poses = 0;
        Map<String, Integer> deja = new HashMap<>();
        for (int i = 0; i < n; i++) {
            ServerPlayer cible = capteurs.get(alea.nextInt(capteurs.size()));
            if (poser(level, v, cible, a, deja)) poses++;
        }
        // zombies fixes (ex. 3 Brutes) : toujours poses, dans la limite des vivants
        for (Entite fx : v.fixes) {
            for (int i = 0; i < fx.poids && a.zombies.size() < maxVivants; i++) {
                ServerPlayer cible = capteurs.get(alea.nextInt(capteurs.size()));
                if (poserType(level, v, cible, a, fx)) poses++;
            }
        }
        HeraliszClans.LOG.info("[clans] assaut sur {} : vague {}/{}, {} zombie(s)", zone.id, a.vagues, a.liste.size(), poses);
        Component msg = Component.translatableWithFallback("heralisz_clans.assaut.vague", "§c☠ Vague %s/%s : les morts arrivent sur %s !",
                String.valueOf(a.vagues), String.valueOf(a.liste.size()), zone.nom);
        for (ServerPlayer p : presents) {
            p.sendSystemMessage(msg);
            p.playNotifySound(a.vagues >= a.liste.size() ? SoundEvents.WITHER_SPAWN : SoundEvents.ZOMBIE_VILLAGER_CONVERTED,
                    SoundSource.HOSTILE, a.vagues >= a.liste.size() ? 0.5f : 1f, 0.8f);
        }
    }

    private boolean poser(ServerLevel level, Vague v, ServerPlayer cible, Assaut a, Map<String, Integer> deja) {
        Entite choisie = tirer(v, deja);
        if (choisie == null || !poserType(level, v, cible, a, choisie)) return false;
        deja.merge(choisie.id, 1, Integer::sum);
        return true;
    }

    private boolean poserType(ServerLevel level, Vague v, ServerPlayer cible, Assaut a, Entite choisie) {
        EntityType<?> type = EntityType.byString(choisie.id).orElse(null);
        if (type == null) return false;
        for (int essai = 0; essai < 10; essai++) {
            double ang = alea.nextDouble() * Math.PI * 2;
            double d = distanceMin + alea.nextDouble() * (distanceMax - distanceMin);
            int x = (int) Math.floor(cible.getX() + Math.cos(ang) * d);
            int z = (int) Math.floor(cible.getZ() + Math.sin(ang) * d);
            if (!level.hasChunkAt(new BlockPos(x, 0, z))) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            // pas sur un toit loin au-dessus ni au fond d'un trou : a peu pres a la hauteur du capteur
            if (Math.abs(y - cible.getY()) > 8) continue;
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.getFluidState(pos.below()).isEmpty() || !level.getFluidState(pos).isEmpty()) continue;
            Entity en = type.create(level);
            if (!(en instanceof Mob m)) return false;
            m.moveTo(x + 0.5, y, z + 0.5, alea.nextFloat() * 360f, 0);
            if (!level.noCollision(m)) continue;
            m.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null, null);
            renforcer(m, v);
            m.addTag(TAG);
            m.setTarget(cible);
            SUIVIS.add(m.getUUID());
            if (!level.addFreshEntity(m)) {
                SUIVIS.remove(m.getUUID());
                return false;
            }
            a.zombies.add(m.getUUID());
            level.sendParticles(ParticleTypes.LARGE_SMOKE, x + 0.5, y + 0.5, z + 0.5, 12, 0.4, 0.6, 0.4, 0.02);
            return true;
        }
        return false;
    }

    private static void renforcer(Mob m, Vague v) {
        if (v.pv != 1) {
            AttributeInstance pv = m.getAttribute(Attributes.MAX_HEALTH);
            if (pv != null) {
                pv.addPermanentModifier(new AttributeModifier(PV_ID, "heraliz_assaut_pv", v.pv - 1, AttributeModifier.Operation.MULTIPLY_TOTAL));
                m.setHealth(m.getMaxHealth());
            }
        }
        if (v.degats != 1) {
            AttributeInstance dg = m.getAttribute(Attributes.ATTACK_DAMAGE);
            if (dg != null) dg.addPermanentModifier(new AttributeModifier(DEGATS_ID, "heraliz_assaut_degats", v.degats - 1,
                    AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    @Nullable
    private Entite tirer(Vague v, Map<String, Integer> deja) {
        int total = 0;
        List<Entite> types = new ArrayList<>();
        List<Integer> poids = new ArrayList<>();
        for (Entite e : v.entites) {
            if (EntityType.byString(e.id).isEmpty()) continue;
            if (e.max > 0 && deja.getOrDefault(e.id, 0) >= e.max) continue;
            types.add(e);
            poids.add(e.poids);
            total += e.poids;
        }
        if (total <= 0) return null;
        int r = alea.nextInt(total);
        for (int i = 0; i < types.size(); i++) {
            r -= poids.get(i);
            if (r < 0) return types.get(i);
        }
        return types.get(0);
    }

    @Nullable
    private static ServerPlayer plusProche(Mob m, List<ServerPlayer> l) {
        ServerPlayer best = null;
        double bd = Double.MAX_VALUE;
        for (ServerPlayer p : l) {
            if (!p.isAlive() || p.level() != m.level()) continue;
            double d = p.distanceToSqr(m);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    /** Un zombie d'assaut d'avant un redemarrage (ou d'un assaut oublie) est retire a son chargement. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide()) return;
        Entity en = e.getEntity();
        if (en.getTags().contains(TAG) && !SUIVIS.contains(en.getUUID())) e.setCanceled(true);
    }
}
