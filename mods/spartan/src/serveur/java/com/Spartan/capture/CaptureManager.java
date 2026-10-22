package com.Spartan.capture;

import com.Spartan.Networks.CaptureRequestPacket;
import com.Spartan.Networks.ModMessages;
import com.Spartan.Spartan;
import com.Spartan.alert.DiscordWebhook;
import com.Spartan.scan.ScanTransfer;
import com.Spartan.util.Text;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Captures d'écran des joueurs, SEULEMENT pour ceux qui les ont autorisées dans le launcher (le jeu signale l'accord à la
 * connexion, ClientReportPacket « capture »). Demandées par le serveur (touche des menus de triche, /spartan capture),
 * reçues en morceaux, rangées dans {@code screenshots/<pseudo>/} à la racine du serveur et envoyées au salon privé du
 * staff. Une capture à la fois par joueur, au plus une toutes les 10 s ; une réponse non demandée est ignorée.
 */
public final class CaptureManager {
    private static final long ECART_MS = 10_000;
    private static final long DELAI_MS = 30_000;
    private static final SecureRandom ALEA = new SecureRandom();
    private static final DateTimeFormatter HORODATE = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private static final Map<UUID, Boolean> ACCORD = new ConcurrentHashMap<>();
    private static final Map<UUID, Etat> ETATS = new ConcurrentHashMap<>();

    private static final class Etat {
        int id;
        long demandee;
        String raison;
        ScanTransfer.Assembly assemblage;
    }

    private CaptureManager() {
    }

    /** Accord signalé par le jeu du joueur (une fois par connexion). */
    public static void accord(ServerPlayer p, boolean oui) {
        ACCORD.putIfAbsent(p.getUUID(), oui);
    }

    public static boolean autorise(ServerPlayer p) {
        return Boolean.TRUE.equals(ACCORD.get(p.getUUID()));
    }

    /** @return null si la demande est partie, sinon la raison du refus */
    public static String demander(ServerPlayer p, String raison) {
        if (!autorise(p)) {
            return "le joueur n'a pas autorisé les captures d'écran";
        }
        long now = System.currentTimeMillis();
        Etat e = ETATS.computeIfAbsent(p.getUUID(), u -> new Etat());
        if (e.id != 0 && now - e.demandee < DELAI_MS) {
            return "une capture est déjà en cours";
        }
        if (now - e.demandee < ECART_MS) {
            return "une capture a déjà été prise il y a moins de 10 s";
        }
        int id;
        do {
            id = ALEA.nextInt();
        } while (id == 0);
        e.id = id;
        e.demandee = now;
        e.raison = raison;
        e.assemblage = null;
        ModMessages.sendToPlayer(new CaptureRequestPacket(id), p);
        return null;
    }

    public static void onChunk(ServerPlayer p, int id, int index, int total, byte[] data) {
        Etat e = ETATS.get(p.getUUID());
        if (e == null || e.id == 0 || id != e.id || !autorise(p)) {
            return;
        }
        if (System.currentTimeMillis() - e.demandee > DELAI_MS) {
            e.id = 0;
            return;
        }
        byte[] jpg;
        try {
            if (e.assemblage == null) {
                e.assemblage = new ScanTransfer.Assembly(id, total);
            }
            jpg = e.assemblage.accept(id, index, total, data);
        } catch (RuntimeException ex) {
            e.id = 0;
            e.assemblage = null;
            return;
        }
        if (jpg == null) {
            return;
        }
        String raison = e.raison;
        e.id = 0;
        e.assemblage = null;
        if (jpg.length < 4 || (jpg[0] & 0xFF) != 0xFF || (jpg[1] & 0xFF) != 0xD8) {
            Spartan.LOGGER.warn("[Spartan] Capture de {} refusée : ce n'est pas une image JPEG.", p.getName().getString());
            return;
        }
        enregistrer(p, raison, jpg);
    }

    private static void enregistrer(ServerPlayer p, String raison, byte[] jpg) {
        String nom = Text.oneLine(p.getName().getString());
        String dossier = nom.replaceAll("[^A-Za-z0-9_]", "_");
        String fichier = dossier + "_" + LocalDateTime.now().format(HORODATE) + ".jpg";
        Path cible = FMLPaths.GAMEDIR.get().resolve("screenshots").resolve(dossier).resolve(fichier);
        Util.ioPool().execute(() -> {
            try {
                Files.createDirectories(cible.getParent());
                Files.write(cible, jpg);
            } catch (Exception ex) {
                Spartan.LOGGER.warn("[Spartan] Capture de {} non enregistrée : {}", nom, ex.getMessage());
            }
        });
        Spartan.LOGGER.info("[Spartan] Capture d'écran de {} ({}) : screenshots/{}/{}", nom, raison, dossier, fichier);
        Component msg = Component.literal("📷 ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(nom).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" — capture d'écran reçue (" + raison + ") : screenshots/" + dossier + "/"
                        + fichier).withStyle(ChatFormatting.GRAY));
        for (ServerPlayer staff : p.server.getPlayerList().getPlayers()) {
            if (staff.hasPermissions(2)) {
                staff.sendSystemMessage(msg);
            }
        }
        DiscordWebhook.sendCapture(p.getUUID().toString(), nom, raison, fichier, jpg);
    }

    public static void clear(UUID id) {
        ACCORD.remove(id);
        ETATS.remove(id);
    }
}
