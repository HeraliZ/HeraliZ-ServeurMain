package fr.heraliz.crac.heraliz;

import java.awt.image.BufferedImage;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.heraliz.crac.Crac;
import fr.heraliz.crac.bases.MondeBases;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.loading.FMLPaths;
import org.jetbrains.annotations.Nullable;

/**
 * Zones où l'on ne construit pas sur la carte principale (CRac 1.16.0, carte validée par le propriétaire le 21/10) :
 * près du loot des villes, des villages et des maisons isolées, gaz / radiation / froid, tout le nord enneigé, le long
 * des rails, autour de la plateforme pétrolière. Masque calculé par {@code outils/zones_construction.py} et déposé avec
 * les configs du serveur : {@code config/crac/zones_construction.json} (coin nord-ouest, taille d'une case en blocs) et
 * {@code config/crac/zones_construction_masque.png} (blanc = interdit). Relu tout seul quand le fichier change. Ni
 * l'armoire ni une pièce ne s'y posent, en survie ; le créatif passe. Sans fichier : aucune zone (avertissement).
 */
public final class ZonesConstruction {
    private static final Path DOSSIER = FMLPaths.CONFIGDIR.get().resolve("crac");
    private static final Path JSON = DOSSIER.resolve("zones_construction.json");

    private static long lu = Long.MIN_VALUE;
    private static boolean[] masque;
    private static int x0, z0, pas = 4, largeur, hauteur;
    private static boolean averti;

    private ZonesConstruction() {
    }

    /** Pourquoi on ne construit pas en {@code pos}, ou null (permis, autre monde, créatif). */
    @Nullable
    public static Component refus(ServerLevel level, BlockPos pos, @Nullable Player joueur) {
        if (joueur != null && joueur.getAbilities().instabuild || !MondeBases.estBases(level) || !interdit(pos.getX(), pos.getZ())) {
            return null;
        }
        return Component.translatableWithFallback("crac.zone.interdite",
            "On ne construit pas ici : trop près d'une ville, d'un lieu à loot, des rails, ou dans une zone dangereuse ou enneigée.")
            .withStyle(ChatFormatting.RED);
    }

    /** Case interdite (hors du masque : permis). */
    public static synchronized boolean interdit(int x, int z) {
        charger();
        if (masque == null) {
            return false;
        }
        int i = Math.floorDiv(x - x0, pas), j = Math.floorDiv(z - z0, pas);
        return i >= 0 && j >= 0 && i < largeur && j < hauteur && masque[j * largeur + i];
    }

    private static void charger() {
        long t;
        try {
            t = Files.exists(JSON) ? Files.getLastModifiedTime(JSON).toMillis() : -1L;
        } catch (Exception e) {
            t = -1L;
        }
        if (t == lu) {
            return;
        }
        lu = t;
        masque = null;
        if (t < 0) {
            if (!averti) {
                averti = true;
                Crac.LOGGER.warn("[CRac] {} absent : aucune zone interdite à la construction", JSON);
            }
            return;
        }
        try (Reader r = Files.newBufferedReader(JSON, StandardCharsets.UTF_8)) {
            JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
            x0 = o.get("x0").getAsInt();
            z0 = o.get("z0").getAsInt();
            pas = Math.max(1, o.get("pas").getAsInt());
            String nom = o.has("masque") ? o.get("masque").getAsString() : "zones_construction_masque.png";
            BufferedImage img = javax.imageio.ImageIO.read(DOSSIER.resolve(nom).toFile());
            largeur = img.getWidth();
            hauteur = img.getHeight();
            boolean[] m = new boolean[largeur * hauteur];
            int n = 0;
            for (int j = 0; j < hauteur; j++) {
                for (int i = 0; i < largeur; i++) {
                    if ((img.getRGB(i, j) & 0xFF) > 127) {
                        m[j * largeur + i] = true;
                        n++;
                    }
                }
            }
            masque = m;
            Crac.LOGGER.info("[CRac] zones interdites à la construction : {} x {} cases de {} blocs, {} % interdit",
                largeur, hauteur, pas, Math.round(100.0 * n / Math.max(1, m.length)));
        } catch (Exception e) {
            Crac.LOGGER.error("[CRac] zones interdites à la construction illisibles ({}) : aucune zone", JSON, e);
        }
    }
}
