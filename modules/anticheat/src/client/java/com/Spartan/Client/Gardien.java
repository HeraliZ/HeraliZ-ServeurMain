package com.Spartan.Client;

import com.Spartan.Networks.GardienPacket;
import com.Spartan.Networks.ModMessages;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Rapports du Gardien, l'anticheat du launcher HeraliZ (launcher/app/assets/js/gardien.js) : pendant la partie, le
 * launcher regarde depuis l'extérieur du jeu les programmes de triche connus et les jars du jeu, et écrit le résultat,
 * signé, dans {@code spartan/gardien.txt} du dossier du jeu.
 *
 * <p>Depuis 2.10.0 (audit du 02/11), le jeu ne juge plus rien : toutes les 15 s, il relit le fichier et le transmet TEL
 * QUEL au serveur ({@link GardienPacket}), qui vérifie lui-même la signature du launcher (clé publique liée au jeton du
 * site), le numéro du rapport et le défi de la connexion (GardienPreuve et GardienServeur, jamais dans ce jar). Le jeu
 * n'a plus aucune clé secrète : il ne peut pas fabriquer de rapport. Il recopie seulement le défi reçu du serveur dans
 * {@code spartan/gardien-defi.txt}, que le launcher reprend dans ses rapports.
 *
 * <p>Ce qui part : « absent » (jeu lancé sans le Gardien), « fichier » (pas de rapport), « lecture » (fichier
 * illisible) ou « ok » avec la ligne et la signature écrites par le launcher.
 *
 * <p>20/11 (audit des images par seconde) : les fichiers sont lus et écrits sur un fil à part, plus sur le fil du jeu (qui
 * dessine aussi les images) : sous Windows, un antivirus qui examine le fichier que le launcher vient d'écrire pouvait
 * retenir l'image. Le rapport part ensuite du fil du jeu, comme avant ; même contenu, même rythme.
 */
final class Gardien {
    private Gardien() {
    }

    private static final int INTERVALLE_TICKS = 300; // 15 s
    /** Clé publique du Gardien de ce lancement (launcher 1.16.0 ou plus récent) : base64url, 43 caractères. */
    private static final Pattern CLE_PUBLIQUE = Pattern.compile("^[A-Za-z0-9_-]{43}$");
    /** Ancien launcher (1.14 à 1.15) : clé HMAC ; ses rapports sont transmis aussi, le serveur ne peut pas les prouver. */
    private static final Pattern CLE_ANCIENNE = Pattern.compile("^[0-9a-f]{64}$");
    private static final Pattern DEFI = Pattern.compile("^[0-9a-f]{32}$");
    /** Lectures et écritures des deux fichiers, dans l'ordre des demandes (un seul fil, créé au premier usage). */
    private static final ExecutorService FICHIERS = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Spartan-Gardien");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });

    /** Clé publique du Gardien, ou "" : envoyée au serveur avec le jeton de lancement. */
    static final String CLE = lireCle();
    private static final boolean ACTIF = !CLE.isEmpty() || ancienLauncher();
    private static int ticks;

    private static String lireCle() {
        String v = System.getenv("SPARTAN_GARDIEN_PUB");
        return v != null && CLE_PUBLIQUE.matcher(v.trim()).matches() ? v.trim() : "";
    }

    private static boolean ancienLauncher() {
        String v = System.getenv("SPARTAN_GARDIEN_CLE");
        return v != null && CLE_ANCIENNE.matcher(v.trim()).matches();
    }

    private static Path dossier() {
        return FMLPaths.GAMEDIR.get().resolve("spartan");
    }

    /** Appelé à chaque tick du client connecté ; {@code nouvelleConnexion} au premier. */
    static void tick(boolean nouvelleConnexion) {
        if (nouvelleConnexion) {
            ticks = 0;
            FICHIERS.execute(Gardien::effacerDefi); // défi de la connexion précédente : périmé
            if (!ACTIF) {
                envoyer("absent", "", "");
                return;
            }
        }
        if (!ACTIF) {
            return;
        }
        // premier rapport après 10 s (le launcher a déjà écrit), puis toutes les 15 s
        if (++ticks == 200 || (ticks > 200 && (ticks - 200) % INTERVALLE_TICKS == 0)) {
            FICHIERS.execute(Gardien::transmettre);
        }
    }

    /** Défi reçu du serveur (thread du jeu) : écrit pour le launcher (fil à part), qui le reprend dans son prochain rapport. */
    static void defi(String defi) {
        if (!ACTIF || defi == null || !DEFI.matcher(defi).matches()) {
            return;
        }
        FICHIERS.execute(() -> ecrireDefi(defi));
    }

    private static void ecrireDefi(String defi) {
        try {
            Path d = dossier();
            Files.createDirectories(d);
            Path tmp = d.resolve("gardien-defi.txt.tmp");
            Files.writeString(tmp, defi + "\n", StandardCharsets.UTF_8);
            try {
                Files.move(tmp, d.resolve("gardien-defi.txt"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, d.resolve("gardien-defi.txt"), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception ignore) {
            // fichier verrouillé (antivirus) : le prochain défi, une minute plus tard, réessaie
        }
    }

    private static void effacerDefi() {
        try {
            Files.deleteIfExists(dossier().resolve("gardien-defi.txt"));
        } catch (Exception ignore) {
            // sans importance : le serveur reconnaît un défi d'une autre connexion
        }
    }

    /** Fil des fichiers : lit le rapport du launcher, puis l'envoie depuis le fil du jeu. */
    private static void transmettre() {
        String[] r = lire(dossier().resolve("gardien.txt"));
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().getConnection() != null) {
                envoyer(r[0], r[1], r[2]);
            }
        });
    }

    /**
     * Rapport du launcher : {état, ligne, signature} ; état « fichier » (absent), « lecture » (illisible) ou « ok » (les
     * deux premières lignes, 4 Ko lus au plus).
     */
    static String[] lire(Path fichier) {
        String texte;
        try (InputStream in = Files.newInputStream(fichier)) {
            texte = new String(in.readNBytes(4096), StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            return new String[]{"fichier", "", ""};
        } catch (Exception e) {
            return new String[]{"lecture", "", ""};
        }
        String[] lignes = texte.split("\n");
        return new String[]{"ok", lignes[0].trim(), lignes.length > 1 ? lignes[1].trim() : ""};
    }

    private static void envoyer(String etat, String ligne, String signature) {
        ModMessages.sendToServer(new GardienPacket(etat, ligne, signature, ProcessHandle.current().pid()));
    }
}
