package fr.heraliz.casino.config;

import fr.heraliz.casino.HeralizCasino;
import fr.heraliz.casino.game.cards.BlackjackRules;
import fr.heraliz.casino.game.crash.CrashRules;
import fr.heraliz.casino.game.duel.DuelRules;
import fr.heraliz.casino.game.mines.MinesRules;
import fr.heraliz.casino.game.plinko.PlinkoRules;
import fr.heraliz.casino.game.race.RaceRules;
import fr.heraliz.casino.game.roulette.RouletteRules;
import fr.heraliz.casino.game.slot.RottenRules;
import fr.heraliz.casino.machine.MachineType;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Partie serveur de la configuration du casino : lecture et ecriture de config/heralizcasino/casino.json, reglages
 * changes par les operateurs (Tablette du Directeur, /casino). Le contenu des reglages et leur envoi aux joueurs restent
 * dans {@link CasinoConfig} (commun).
 */
public final class CasinoConfigServeur {
    private CasinoConfigServeur() {
    }

    public static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve(HeralizCasino.MODID).resolve("casino.json");
    }

    /** Lecture (cree le fichier avec les valeurs par defaut s'il n'existe pas ; complete les champs manquants). */
    public static synchronized String load() {
        Path f = file();
        CasinoConfig.Settings s;
        String error = null;
        try {
            Files.createDirectories(f.getParent());
            if (Files.exists(f)) {
                try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                    s = CasinoConfig.GSON.fromJson(r, CasinoConfig.Settings.class);
                }
                if (s == null) s = new CasinoConfig.Settings();
                s.migrate();
            } else {
                s = new CasinoConfig.Settings();
            }
            s.sanitized();
            try (Writer w = Files.newBufferedWriter(f, StandardCharsets.UTF_8)) {
                CasinoConfig.GSON.toJson(s, w);
            }
        } catch (Exception e) {
            HeralizCasino.LOGGER.error("[HeralizCasino] Lecture de {} impossible, reglages par defaut utilises", f, e);
            error = e.getMessage();
            s = new CasinoConfig.Settings().sanitized();
        }
        CasinoConfig.apply(s);
        return error;
    }

    /** Remplace la section d'une machine (JSON envoye par un operateur), puis sauvegarde le fichier. */
    public static synchronized void applySection(MachineType t, String json) throws IOException {
        CasinoConfig.Settings s = CasinoConfig.copy();
        switch (t) {
            case CAISSE -> s.caisse = CasinoConfig.GSON.fromJson(json, CasinoConfig.Cashier.class);
            case ROTTEN -> s.rotten = CasinoConfig.GSON.fromJson(json, RottenRules.class);
            case OLYMPE -> s.portesOlympe = CasinoConfig.GSON.fromJson(json, fr.heraliz.casino.game.slot.OlympeRules.class);
            case LOUP -> s.souffleLoup = CasinoConfig.GSON.fromJson(json, fr.heraliz.casino.game.slot.LoupRules.class);
            case NICHE -> s.laNiche = CasinoConfig.GSON.fromJson(json, fr.heraliz.casino.game.slot.NicheRules.class);
            case TABLEAU, TABLEAU_PERDANTS, TABLEAU_PARIEURS, ATM -> {
            }
            case PLINKO -> s.plinko = CasinoConfig.GSON.fromJson(json, PlinkoRules.class);
            case BLACKJACK -> s.blackjack = CasinoConfig.GSON.fromJson(json, BlackjackRules.class);
            case ROULETTE -> s.roulette = CasinoConfig.GSON.fromJson(json, RouletteRules.class);
            case CRASH -> s.crash = CasinoConfig.GSON.fromJson(json, CrashRules.class);
            case MINES -> s.mines = CasinoConfig.GSON.fromJson(json, MinesRules.class);
            case COURSE -> s.course = CasinoConfig.GSON.fromJson(json, RaceRules.class);
            case PIERRE_FEUILLE_CISEAUX -> s.pierreFeuilleCiseaux = CasinoConfig.GSON.fromJson(json, DuelRules.class);
            case BATAILLE_NAVALE -> s.batailleNavale = CasinoConfig.GSON.fromJson(json, DuelRules.class);
        }
        CasinoConfig.apply(s.sanitized());
        save();
    }

    /** Remplace la lumiere des machines (JSON {"rotten": 10, ...} envoye par un operateur), puis sauvegarde. */
    public static synchronized void applyLights(String json) throws IOException {
        java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<java.util.Map<String, Integer>>() {
        }.getType();
        java.util.Map<String, Integer> in = CasinoConfig.GSON.fromJson(json, type);
        if (in == null) throw new IOException("lumieres vides");
        CasinoConfig.Settings s = CasinoConfig.copy();
        java.util.Map<String, Integer> m = new java.util.LinkedHashMap<>(s.lumieres);
        m.putAll(in);
        s.lumieres = m;
        CasinoConfig.apply(s.sanitized());
        save();
    }

    /** Lumiere d'une machine, ou de toutes (t = null) ; level < 0 : reglage d'usine. */
    public static synchronized void setLight(@javax.annotation.Nullable MachineType t, int level) throws IOException {
        CasinoConfig.Settings s = CasinoConfig.copy();
        java.util.Map<String, Integer> m = new java.util.LinkedHashMap<>(s.lumieres);
        for (MachineType x : MachineType.values()) if (t == null || x == t) m.put(x.id, level < 0 ? x.light : level);
        s.lumieres = m;
        CasinoConfig.apply(s.sanitized());
        save();
    }

    public static void save() throws IOException {
        Path f = file();
        Files.createDirectories(f.getParent());
        try (Writer w = Files.newBufferedWriter(f, StandardCharsets.UTF_8)) {
            CasinoConfig.GSON.toJson(CasinoConfig.get(), w);
        }
    }
}
