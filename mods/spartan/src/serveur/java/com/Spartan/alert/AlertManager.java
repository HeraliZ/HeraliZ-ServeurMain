package com.Spartan.alert;

import com.Spartan.Spartan;
import com.Spartan.config.SpartanConfig;
import com.Spartan.util.AsyncIO;
import com.Spartan.util.Text;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Point unique de sortie des alertes de l'anti-cheat.
 * Chaque détection appelle AlertManager : le staff en jeu, le fichier de log
 * et Discord reçoivent la même information.
 */
public class AlertManager {
    private static final SimpleDateFormat TS = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private AlertManager() {
    }

    public static void dispatch(ServerPlayer player, String check, int vl, String details, String evidenceFile) {
        String playerName = Text.oneLine(player.getName().getString());
        check = Text.oneLine(check);
        details = Text.oneLine(details);
        String line = "[Spartan] " + playerName + " — " + check + " (VL " + vl + ") : " + details;

        // Console serveur
        Spartan.LOGGER.warn(line);

        // Staff en jeu (op niveau 2+)
        if (SpartanConfig.ALERT_INGAME.get()) {
            Component msg = Component.literal("⚠ ").withStyle(ChatFormatting.RED)
                    .append(Component.literal(playerName).withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(" — " + check + " (VL " + vl + ")").withStyle(ChatFormatting.RED))
                    .append(Component.literal(details == null || details.isBlank() ? "" : " | " + details)
                            .withStyle(ChatFormatting.GRAY));
            for (ServerPlayer staff : player.server.getPlayerList().getPlayers()) {
                if (staff.hasPermissions(2)) {
                    staff.sendSystemMessage(msg);
                }
            }
        }

        // Fichier de log
        if (SpartanConfig.ALERT_LOGFILE.get()) {
            writeLog(line + (evidenceFile == null ? "" : " [preuve: " + evidenceFile + "]"));
        }

        // Discord
        DiscordWebhook.send(playerName, check, vl, details, evidenceFile);
    }

    public static final int COLOR_BAN = 0x8E44AD;
    public static final int COLOR_UNBAN = 0x27AE60;
    public static final int COLOR_INFO = 0x3498DB;

    /** Information staff (ban, déban, compte secondaire…) : console + staff en jeu + log + Discord. */
    public static void info(MinecraftServer server, String title, String message, int color) {
        title = Text.oneLine(title);
        message = Text.oneLine(message);
        String line = "[Spartan] " + title + " — " + message;
        Spartan.LOGGER.info(line);

        if (SpartanConfig.ALERT_INGAME.get() && server != null) {
            Component msg = Component.literal("[Spartan] ").withStyle(ChatFormatting.DARK_AQUA)
                    .append(Component.literal(title + " — ").withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(message).withStyle(ChatFormatting.GRAY));
            for (ServerPlayer staff : server.getPlayerList().getPlayers()) {
                if (staff.hasPermissions(2)) {
                    staff.sendSystemMessage(msg);
                }
            }
        }
        if (SpartanConfig.ALERT_LOGFILE.get()) {
            writeLog(line);
        }
        DiscordWebhook.sendInfo(title, message, color);
    }

    private static final long MAX_LOG_BYTES = 10L * 1024 * 1024; // 10 Mo, puis rotation

    private static void writeLog(String line) {
        // Horodatage pris sur le thread serveur, écriture sur le thread I/O.
        String stamped = TS.format(new Date()) + " " + line.replace('\n', ' ').replace('\r', ' ')
                + System.lineSeparator();
        AsyncIO.submit(() -> {
            try {
                File dir = new File(FMLPaths.CONFIGDIR.get().toFile(), "spartan");
                dir.mkdirs();
                File log = new File(dir, "alerts.log");
                if (log.length() > MAX_LOG_BYTES) {
                    File old = new File(dir, "alerts.log.1");
                    old.delete();
                    log.renameTo(old);
                }
                try (FileWriter w = new FileWriter(log, true)) {
                    w.write(stamped);
                }
            } catch (IOException e) {
                Spartan.LOGGER.warn("[Spartan] Impossible d'écrire alerts.log : {}", e.getMessage());
            }
        });
    }
}
