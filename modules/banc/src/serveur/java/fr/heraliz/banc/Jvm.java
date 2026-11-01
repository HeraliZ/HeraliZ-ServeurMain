package fr.heraliz.banc;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;

/**
 * La machine Java qui fait VRAIMENT tourner le serveur (01/11) : au premier banc, Java était lancé avec 11 Go, mais le
 * serveur disait 3996 Mo au plus. Mohist installe Forge au premier démarrage puis se relance lui-même dans une autre
 * machine Java ({@code java -jar mohist.jar}, sans les réglages de la ligne de commande : tas = 1/4 de la mémoire de la
 * machine). Écrit au démarrage ({@code [banc] JVM {json}}) et dans chaque mesure : numéro du processus et de son parent
 * (qui l'a lancé), arguments réels ({@code RuntimeMXBean.getInputArguments}), mémoire maximale, ramasse-miettes, et les
 * distances du serveur (vue, simulation).
 */
final class Jvm {
    private Jvm() {
    }

    static JsonObject infos(MinecraftServer srv) {
        JsonObject o = new JsonObject();
        RuntimeMXBean rt = ManagementFactory.getRuntimeMXBean();
        ProcessHandle moi = ProcessHandle.current();
        o.addProperty("pid", moi.pid());
        moi.parent().ifPresent(p -> {
            o.addProperty("parent_pid", p.pid());
            p.info().command().ifPresent(c -> o.addProperty("parent_programme", c));
            p.info().commandLine().ifPresent(c -> o.addProperty("parent_commande", c.length() > 400 ? c.substring(0, 400) + "…" : c));
        });
        JsonArray a = new JsonArray();
        for (String s : rt.getInputArguments()) a.add(s);
        o.add("arguments", a);
        Runtime r = Runtime.getRuntime();
        o.addProperty("memoire_max_mo", r.maxMemory() >> 20);
        o.addProperty("memoire_reservee_mo", r.totalMemory() >> 20);
        o.addProperty("coeurs", r.availableProcessors());
        o.addProperty("java", System.getProperty("java.version") + " " + System.getProperty("java.vm.name"));
        o.addProperty("jdk_java_options", System.getenv("JDK_JAVA_OPTIONS") != null);
        JsonArray gc = new JsonArray();
        for (GarbageCollectorMXBean g : ManagementFactory.getGarbageCollectorMXBeans()) gc.add(g.getName());
        o.add("ramasse_miettes", gc);
        if (srv != null && srv.getPlayerList() != null) {
            o.addProperty("vue", srv.getPlayerList().getViewDistance());
            o.addProperty("simulation", srv.getPlayerList().getSimulationDistance());
            o.addProperty("max_joueurs", srv.getPlayerList().getMaxPlayers());
        }
        return o;
    }
}
