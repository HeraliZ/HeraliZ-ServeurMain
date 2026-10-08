package com.Spartan.evidence;

import com.Spartan.check.movement.MoveSample;
import com.Spartan.config.SpartanConfig;
import com.Spartan.util.AsyncIO;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * « Boîte noire » : enregistre en continu les dernières secondes de déplacement
 * de chaque joueur, TELLES QUE SON CLIENT LES A ENVOYÉES (position, regard,
 * déplacement, sol déclaré par le client et sol réel calculé par le serveur,
 * vie). Les téléportations sont marquées comme telles (pas de fausse vitesse).
 * Quand une détection se déclenche, on fige ce tampon dans un fichier JSON que
 * l'admin peut rejouer.
 *
 * Ce n'est PAS une capture de l'écran du joueur (illégal, supprimé) : ce sont
 * uniquement les données de jeu que le serveur reçoit déjà.
 *
 * L'écriture disque se fait hors du thread serveur (AsyncIO) et le dossier est
 * élagué pour ne pas saturer le disque.
 */
public class EvidenceRecorder {
    private static final Map<UUID, Deque<Frame>> BUFFERS = new ConcurrentHashMap<>();

    private EvidenceRecorder() {
    }

    private record Frame(long t, double x, double y, double z, float yaw, float pitch,
                         double dx, double dy, double dz, boolean claimedGround, boolean serverGround,
                         boolean teleport, float health) {
    }

    /** Un déplacement revendiqué par le client (moteur de mouvement). */
    public static void recordSample(ServerPlayer p, MoveSample s, float yaw, float pitch) {
        add(p, new Frame(System.currentTimeMillis(), s.to().x, s.to().y, s.to().z, yaw, pitch,
                s.dx(), s.dy(), s.dz(), s.claimedGround(), s.groundTo(), false, p.getHealth()));
    }

    /** Téléportation confirmée par le client : saut de position imposé par le serveur. */
    public static void recordTeleport(ServerPlayer p, Vec3 target) {
        add(p, new Frame(System.currentTimeMillis(), target.x, target.y, target.z, p.getYRot(), p.getXRot(),
                0, 0, 0, false, false, true, p.getHealth()));
    }

    private static void add(ServerPlayer p, Frame f) {
        if (!SpartanConfig.EVIDENCE_ENABLED.get()) {
            return;
        }
        int cap = SpartanConfig.EVIDENCE_TICKS.get();
        Deque<Frame> buf = BUFFERS.computeIfAbsent(p.getUUID(), k -> new ArrayDeque<>());
        buf.addLast(f);
        while (buf.size() > cap) {
            buf.removeFirst();
        }
    }

    /**
     * Fige le tampon et renvoie le nom du fichier (écrit en asynchrone).
     * Le JSON est construit sur le thread serveur (lecture du tampon), l'écriture
     * disque et l'élagage partent sur le thread I/O dédié.
     */
    public static String dump(ServerPlayer p, String reason) {
        if (!SpartanConfig.EVIDENCE_ENABLED.get()) {
            return null;
        }
        Deque<Frame> buf = BUFFERS.get(p.getUUID());
        if (buf == null || buf.isEmpty()) {
            return null;
        }

        JsonObject root = new JsonObject();
        root.addProperty("player", p.getName().getString());
        root.addProperty("uuid", p.getUUID().toString());
        root.addProperty("reason", reason);
        root.addProperty("dimension", p.level().dimension().location().toString());
        root.addProperty("capturedAt", System.currentTimeMillis());

        JsonArray frames = new JsonArray();
        for (Frame f : buf) {
            JsonObject o = new JsonObject();
            o.addProperty("t", f.t);
            o.addProperty("x", round(f.x));
            o.addProperty("y", round(f.y));
            o.addProperty("z", round(f.z));
            o.addProperty("yaw", f.yaw);
            o.addProperty("pitch", f.pitch);
            o.addProperty("dx", round(f.dx));
            o.addProperty("dy", round(f.dy));
            o.addProperty("dz", round(f.dz));
            o.addProperty("claimedGround", f.claimedGround);
            o.addProperty("serverGround", f.serverGround);
            if (f.teleport) {
                o.addProperty("teleport", true);
            }
            o.addProperty("health", f.health);
            frames.add(o);
        }
        root.add("frames", frames);

        String fn = sanitize(p.getName().getString()) + "_" + sanitize(reason) + "_"
                + System.currentTimeMillis() + ".json";
        int maxFiles = SpartanConfig.EVIDENCE_MAX_FILES.get();

        AsyncIO.submit(() -> {
            File dir = new File(FMLPaths.CONFIGDIR.get().toFile(), "spartan/evidence");
            dir.mkdirs();
            try (FileWriter w = new FileWriter(new File(dir, fn))) {
                new GsonBuilder().setPrettyPrinting().create().toJson(root, w);
            } catch (IOException e) {
                com.Spartan.Spartan.LOGGER.warn("[Spartan] Impossible d'écrire la preuve : {}", e.getMessage());
            }
            prune(dir, maxFiles);
        });
        return fn;
    }

    /**
     * Écrit une preuve JSON déjà sérialisée (contrôle d'intégrité…) dans le même dossier,
     * avec le même élagage.
     * @return le nom du fichier
     */
    public static String writeJson(String playerName, String kind, String json) {
        String fn = sanitize(playerName) + "_" + sanitize(kind) + "_" + System.currentTimeMillis() + ".json";
        int maxFiles = SpartanConfig.EVIDENCE_MAX_FILES.get();
        AsyncIO.submit(() -> {
            File dir = new File(FMLPaths.CONFIGDIR.get().toFile(), "spartan/evidence");
            dir.mkdirs();
            try (FileWriter w = new FileWriter(new File(dir, fn), StandardCharsets.UTF_8)) {
                w.write(json);
            } catch (IOException e) {
                com.Spartan.Spartan.LOGGER.warn("[Spartan] Impossible d'écrire la preuve : {}", e.getMessage());
            }
            prune(dir, maxFiles);
        });
        return fn;
    }

    public static void clear(UUID uuid) {
        BUFFERS.remove(uuid);
    }

    private static void prune(File dir, int maxFiles) {
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null || files.length <= maxFiles) {
            return;
        }
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        for (int i = 0; i < files.length - maxFiles; i++) {
            if (!files[i].delete()) {
                com.Spartan.Spartan.LOGGER.warn("[Spartan] Impossible de supprimer l'ancienne preuve {}", files[i].getName());
            }
        }
    }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    private static String sanitize(String s) {
        return s.replaceAll("[^a-zA-Z0-9_-]", "_").toLowerCase(Locale.ROOT);
    }
}
