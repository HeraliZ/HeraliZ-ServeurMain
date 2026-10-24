package com.Spartan.serveur;

import com.Spartan.Spartan;
import com.Spartan.capture.CaptureManager;
import com.Spartan.check.CheckManager;
import com.Spartan.config.SpartanConfig;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;

/**
 * Juge les rapports du Gardien, l'anticheat du launcher HeraliZ (client/Gardien.java, launcher gardien.js), section
 * {@code [gardien]} de la config. Le serveur décide seul : alertes « Gardien » (programme de triche ouvert, jar changé
 * pendant la partie, rapport trafiqué, jeu lancé sans Gardien) et « Gardien muet » (plus de rapport : launcher fermé de
 * force, ou jeu modifié qui ne relit plus le fichier). Une alerte par fait et par connexion ; jamais d'expulsion, sauf
 * {@code required} pour un jeu lancé sans Gardien.
 */
public final class GardienServeur {
    private GardienServeur() {
    }

    private static final long ECART_MS = 5_000L;
    private static final Pattern OUTIL = Pattern.compile("^[a-z0-9 ._-]{1,32}$");

    private static final class Etat {
        final long arrivee = System.currentTimeMillis();
        long dernier;
        boolean absent, muet, silenceSignale, lectureSignalee;
        int jars;
        final Set<String> outils = new HashSet<>();
        final Set<String> invalides = new HashSet<>();
    }

    private static final Map<UUID, Etat> ETATS = new ConcurrentHashMap<>();

    public static void oublier(UUID id) {
        ETATS.remove(id);
    }

    private static boolean fictif(ServerPlayer p) {
        return p instanceof FakePlayer || p.getTags().contains("heraliz_bot");
    }

    /** Rapport reçu du jeu du joueur (thread du serveur). */
    static void recevoir(ServerPlayer player, String detail) {
        if (!SpartanConfig.GARDIEN_ENABLED.get() || fictif(player)) {
            return;
        }
        Etat e = ETATS.computeIfAbsent(player.getUUID(), u -> new Etat());
        long now = System.currentTimeMillis();
        if (e.dernier != 0 && now - e.dernier < ECART_MS) {
            return; // un jeu modifié qui inonderait le serveur de rapports
        }
        e.dernier = now;
        e.silenceSignale = false;
        String nom = player.getName().getString();

        if (detail.equals("absent")) {
            if (e.absent) {
                return;
            }
            e.absent = true;
            if (SpartanConfig.GARDIEN_REQUIRED.get()) {
                Spartan.LOGGER.info("[Spartan] {} refusé : jeu lancé sans le Gardien du launcher.", nom);
                player.connection.disconnect(Component.translatableWithFallback("spartan.expulsion.gardien",
                        "Lancez le jeu depuis le launcher HeraliZ à jour (anticheat du launcher requis)."));
                return;
            }
            CheckManager.flag("Gardien", player, "jeu lancé sans le Gardien du launcher (launcher plus ancien que 1.14.0"
                    + " ou autre lanceur)");
            return;
        }
        if (detail.startsWith("muet:")) {
            if (!e.muet) {
                e.muet = true;
                CheckManager.flag("Gardien muet", player, "le launcher ne surveille plus le jeu (" + court(detail.substring(5))
                        + ") : launcher fermé de force pendant la partie ?");
            }
            return;
        }
        if (detail.startsWith("invalide:")) {
            String raison = court(detail.substring(9));
            if (e.invalides.add(raison)) {
                CheckManager.flag("Gardien", player, "rapport du launcher refusé (" + raison
                        + ") : fichier du Gardien trafiqué ou recopié d'une autre partie");
                capture(player, "rapport du Gardien refusé");
            }
            return;
        }
        if (!detail.startsWith("ok;")) {
            return;
        }
        e.muet = false;
        String lecture = champ(detail, "lecture");
        String outils = champ(detail, "outils");
        int jars;
        try {
            jars = Integer.parseInt(champ(detail, "jars"));
        } catch (NumberFormatException ex) {
            jars = 0;
        }
        if ("impossible".equals(lecture) && !e.lectureSignalee) {
            e.lectureSignalee = true;
            CheckManager.flag("Gardien", player, "le launcher n'arrive pas à lister les programmes du PC (indice faible)");
        }
        List<String> nouveaux = new ArrayList<>();
        List<String> ignores = SpartanConfig.getGardienIgnoredTools();
        if (outils != null && !outils.isEmpty()) {
            for (String o : outils.split(",")) {
                String t = o.trim();
                if (OUTIL.matcher(t).matches() && !ignores.contains(t) && e.outils.add(t)) {
                    nouveaux.add(t);
                }
            }
        }
        if (!nouveaux.isEmpty()) {
            CheckManager.flag("Gardien", player, "programme de triche ou d'injection ouvert sur le PC : "
                    + String.join(", ", nouveaux));
            capture(player, "programme de triche ouvert (" + String.join(", ", nouveaux) + ")");
        }
        if (jars > e.jars) {
            e.jars = jars;
            CheckManager.flag("Gardien", player, jars + " jar(s) du jeu changé(s), ajouté(s) ou retiré(s) pendant la partie");
        }
    }

    /** Silence : plus de rapport depuis {@code silenceSeconds} (une fois par seconde, thread du serveur). */
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0 || !SpartanConfig.GARDIEN_ENABLED.get()) {
            return;
        }
        long now = System.currentTimeMillis();
        long silence = SpartanConfig.GARDIEN_SILENCE_SECONDS.get() * 1000L;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (fictif(p)) {
                continue;
            }
            Etat e = ETATS.computeIfAbsent(p.getUUID(), u -> new Etat());
            if (e.absent || e.silenceSignale) {
                continue;
            }
            long depuis = e.dernier == 0 ? e.arrivee + 30_000L : e.dernier;
            if (now - depuis > silence) {
                e.silenceSignale = true;
                CheckManager.flag("Gardien muet", p, e.dernier == 0
                        ? "aucun rapport du Gardien depuis la connexion (jeu modifié qui ne l'envoie pas ?)"
                        : "plus aucun rapport du Gardien depuis " + (now - e.dernier) / 1000 + " s (jeu ou launcher arrêté de force ?)");
            }
        }
    }

    private static void capture(ServerPlayer player, String raison) {
        if (SpartanConfig.GARDIEN_CAPTURE.get()) {
            CaptureManager.demander(player, raison);
        }
    }

    /** Valeur d'un champ « nom=valeur » du rapport, ou null. */
    static String champ(String detail, String nom) {
        for (String morceau : detail.split(";")) {
            if (morceau.startsWith(nom + "=")) {
                return morceau.substring(nom.length() + 1);
            }
        }
        return null;
    }

    private static String court(String s) {
        String t = s.replaceAll("[^a-z0-9_-]", "");
        return t.length() > 24 ? t.substring(0, 24) : t;
    }
}
