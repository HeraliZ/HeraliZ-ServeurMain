package com.Spartan.Client;

import com.Spartan.Networks.BuildPacket;
import com.Spartan.Networks.ClientReportPacket;
import com.Spartan.Networks.LaunchTokenPacket;
import com.Spartan.Networks.ModMessages;
import com.Spartan.Networks.ModsPacket;
import com.Spartan.Networks.ResourcePackPacket;
import com.Spartan.Networks.TestPacket;
import com.Spartan.Spartan;
import com.Spartan.Utils.ComponentInfo;
import com.Spartan.Utils.ModUtils;
import com.Spartan.Utils.ResourcePackUtil;
import com.Spartan.integrity.Fingerprint;
import com.Spartan.integrity.ModsHasher;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.nio.file.Paths;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Partie client : envoie au serveur les informations de connexion, puis
 * surveille l'intégrité du jeu.
 *
 * Changement par rapport à la 1.0.0 : le scan des threads tournait 20 fois par
 * seconde et fermait le jeu en silence, sans que les admins le sachent.
 * Maintenant il tourne toutes les 5 s et SIGNALE au serveur, qui décide et
 * alerte le staff (avec preuve).
 */
@Mod.EventBusSubscriber(modid = Spartan.MOD_ID, value = Dist.CLIENT)
public class ClientEvents {
    private static final int SCAN_INTERVAL_TICKS = 100; // 5 s

    private static boolean wasConnected = false;
    private static boolean launchSent = false;
    private static boolean threadsReported = false;
    private static int ticks = 0;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        // Empreinte du dossier mods calculée en arrière-plan dès l'écran titre.
        ModsHasher.startIfNeeded(FMLPaths.MODSDIR.get());

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            wasConnected = false;
            return;
        }

        if (!wasConnected) {
            wasConnected = true;
            launchSent = false;
            threadsReported = false;
            ticks = 0;
            ModMessages.sendToServer(new BuildPacket(Fingerprint.buildId()));
            ModMessages.sendToServer(new ResourcePackPacket(ResourcePackUtil.getActiveResourcePacks()));
            ModMessages.sendToServer(new ModsPacket(ModUtils.getActiveMods()));
            ModMessages.sendToServer(new TestPacket(ComponentInfo.getSystemUUID()));
            ModMessages.sendToServer(new TestPacket(ComponentInfo.getHWID()));
            reportJavaAgents();
            // Accord pour les captures d'écran, donné (ou non) dans le launcher.
            ModMessages.sendToServer(new ClientReportPacket("capture", CaptureEcran.autorisee() ? "oui" : "non"));
            String shader = ShaderProbe.activeShaderpack();
            if (shader != null) {
                ModMessages.sendToServer(new ClientReportPacket("shader", shader));
            }
            String jvm = JvmProbe.missingHardening();
            if (jvm != null) {
                ModMessages.sendToServer(new ClientReportPacket("jvm", jvm));
            }
        }

        // Jeton du launcher + empreinte réelle des mods, dès que l'empreinte est prête.
        if (!launchSent) {
            String modsHash = ModsHasher.getIfReady();
            if (modsHash != null) {
                launchSent = true;
                ModMessages.sendToServer(new LaunchTokenPacket(launchToken(), modsHash));
            }
        }

        if (++ticks % SCAN_INTERVAL_TICKS == 0) {
            scanThreads();
        }
    }

    /** Jeton passé par le launcher officiel : -Dspartan.launchToken=… (ou variable d'environnement). */
    private static String launchToken() {
        String t = System.getProperty("spartan.launchToken");
        if (t == null || t.isBlank()) {
            t = System.getenv("SPARTAN_LAUNCH_TOKEN");
        }
        return t == null ? "" : t.trim();
    }

    /** Signale les agents Java chargés au lancement (nom de fichier uniquement). */
    private static void reportJavaAgents() {
        for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (!arg.startsWith("-javaagent:")) {
                continue;
            }
            String spec = arg.substring("-javaagent:".length());
            int eq = spec.indexOf('=');
            String jar = eq >= 0 ? spec.substring(0, eq) : spec;
            String name;
            try {
                Path file = Paths.get(jar).getFileName();
                name = file != null ? file.toString() : jar;
            } catch (Exception e) {
                name = jar;
            }
            ModMessages.sendToServer(new ClientReportPacket("agent", name));
        }
    }

    /** Compte les threads sans nom (énumération légère, sans capture des piles). */
    private static void scanThreads() {
        if (threadsReported) {
            return;
        }
        ThreadGroup root = Thread.currentThread().getThreadGroup();
        while (root.getParent() != null) {
            root = root.getParent();
        }
        Thread[] threads = new Thread[root.activeCount() * 2 + 16];
        int n = root.enumerate(threads, true);
        int anonymous = 0;
        for (int i = 0; i < n; i++) {
            String name = threads[i].getName();
            if (name == null || name.isBlank()) {
                anonymous++;
            }
        }
        if (anonymous > 0) {
            threadsReported = true;
            ModMessages.sendToServer(new ClientReportPacket("thread", anonymous + " thread(s)"));
        }
    }
}
