package fr.heraliz.emote.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.heraliz.emote.HeralizEmote;
import net.minecraftforge.fml.loading.FMLPaths;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;

/**
 * Copies the emotes bundled in the jar into Emotecraft's emotes folder ({@code .minecraft/emotes}
 * unless Emotecraft's config says otherwise), as {@code heraliz_<id>.json}, its icon and its music
 * ({@code .nbs}, which Emotecraft plays with the emote), and deletes those of emotes the mod dropped.
 *
 * <p>A small record of what was installed ({@code config/heraliz_emote-installed.properties})
 * keeps the player in charge: an emote they deleted or edited is left alone until a new version of
 * the mod changes that emote.
 */
public final class EmoteInstaller {
    private static final String RESOURCES = "/assets/heraliz_emote/emotes/";
    private static final String PREFIX = "heraliz_";

    private EmoteInstaller() {
    }

    public static void install() {
        try {
            Path dir = emotesDirectory();
            Files.createDirectories(dir);
            Path recordFile = FMLPaths.CONFIGDIR.get().resolve("heraliz_emote-installed.properties");
            Properties record = new Properties();
            if (Files.isRegularFile(recordFile)) {
                try (Reader r = Files.newBufferedReader(recordFile, StandardCharsets.UTF_8)) {
                    record.load(r);
                }
            }
            int written = 0;
            int kept = 0;
            int removed = 0;
            List<String> ids = index();
            // emotes dropped from the mod: delete the files installed for them, unless the player changed them
            for (String name : record.stringPropertyNames()) {
                if (!name.startsWith(PREFIX)) continue;
                int dot = name.lastIndexOf('.');
                String id = dot < 0 ? "" : name.substring(PREFIX.length(), dot);
                if (ids.contains(id)) continue;
                Path target = dir.resolve(name);
                if (Files.isRegularFile(target) && sha1(Files.readAllBytes(target)).equals(record.getProperty(name))) {
                    Files.delete(target);
                    removed++;
                }
                record.remove(name);
            }
            for (String id : ids) {
                for (String ext : new String[]{".json", ".png", ".nbs"}) {
                    byte[] bundled = resource(id + ext);
                    String name = PREFIX + id + ext;
                    Path target = dir.resolve(name);
                    if (bundled == null) {
                        // this emote no longer has that file (a song that was dropped...)
                        String old = record.getProperty(name);
                        if (old != null && Files.isRegularFile(target) && sha1(Files.readAllBytes(target)).equals(old)) {
                            Files.delete(target);
                            removed++;
                        }
                        record.remove(name);
                        continue;
                    }
                    String hash = sha1(bundled);
                    boolean sameVersionAsLastTime = hash.equals(record.getProperty(name));
                    if (Files.isRegularFile(target)) {
                        byte[] current = Files.readAllBytes(target);
                        if (sha1(current).equals(hash)) {
                            record.setProperty(name, hash);
                            continue;
                        }
                        if (sameVersionAsLastTime) {
                            kept++; // edited by the player
                            continue;
                        }
                    } else if (sameVersionAsLastTime) {
                        kept++; // deleted by the player
                        continue;
                    }
                    Files.write(target, bundled);
                    record.setProperty(name, hash);
                    written++;
                }
            }
            try (Writer w = Files.newBufferedWriter(recordFile, StandardCharsets.UTF_8)) {
                record.store(w, "Heraliz Emote: emotes installed in " + dir);
            }
            HeralizEmote.LOGGER.info("Heraliz emotes in {}: {} written, {} removed, {} left as the player changed them", dir, written,
                    removed, kept);
        } catch (Exception e) {
            HeralizEmote.LOGGER.error("Could not install the Heraliz emotes", e);
        }
    }

    /** Emotecraft's emotes folder: {@code emotesDirectory} in config/emotecraft.json, "emotes" by default. */
    public static Path emotesDirectory() {
        Path game = FMLPaths.GAMEDIR.get();
        String folder = "emotes";
        String configDir = System.getProperty("emotecraftConfigDir", "config");
        Path config = game.resolve(configDir).resolve("emotecraft.json");
        if (Files.isRegularFile(config)) {
            try {
                JsonElement root = JsonParser.parseReader(new StringReader(Files.readString(config, StandardCharsets.UTF_8)));
                if (root.isJsonObject()) {
                    JsonObject o = root.getAsJsonObject();
                    if (o.has("emotesDirectory") && !o.get("emotesDirectory").getAsString().isBlank()) {
                        folder = o.get("emotesDirectory").getAsString();
                    }
                }
            } catch (Exception e) {
                HeralizEmote.LOGGER.warn("Could not read {}, using the default emotes folder", config);
            }
        }
        return game.resolve(folder);
    }

    public static List<String> index() throws IOException {
        byte[] data = resource("index.txt");
        List<String> ids = new ArrayList<>();
        if (data == null) return ids;
        for (String line : new String(data, StandardCharsets.UTF_8).split("\\R")) {
            line = line.trim();
            if (!line.isEmpty()) ids.add(line);
        }
        return ids;
    }

    @Nullable
    public static byte[] resource(String name) throws IOException {
        try (InputStream in = EmoteInstaller.class.getResourceAsStream(RESOURCES + name)) {
            return in == null ? null : in.readAllBytes();
        }
    }

    private static String sha1(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
    }
}
