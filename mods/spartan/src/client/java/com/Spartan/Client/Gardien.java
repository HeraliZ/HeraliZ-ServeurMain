package com.Spartan.Client;

import com.Spartan.Networks.ClientReportPacket;
import com.Spartan.Networks.ModMessages;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Rapports du Gardien, l'anticheat du launcher HeraliZ (launcher/app/assets/js/gardien.js) : pendant la partie, le
 * launcher regarde depuis l'extérieur du jeu les programmes de triche connus et les jars du jeu, et écrit le résultat,
 * signé, dans {@code spartan/gardien.txt} du dossier du jeu. La clé de signature, tirée au hasard à chaque lancement,
 * n'arrive au jeu que par la variable {@code SPARTAN_GARDIEN_CLE}.
 *
 * <p>Toutes les 15 s, le fichier est relu et vérifié (signature, numéro de processus du jeu, numéro du rapport qui ne
 * recule jamais, heure) ; le résultat part au serveur ({@code ClientReportPacket} « gardien »), qui juge. Ligne :
 * {@code v1|n|t|pid|lecture|outil,outil|jars changés} (même format que {@code Gardien.ligne} du launcher).
 *
 * <p>Ce qui part : {@code absent} (jeu lancé sans le Gardien : ancien launcher, autre lanceur), {@code muet:…}
 * (plus de rapport récent : launcher fermé de force), {@code invalide:…} (signature fausse, fichier d'une autre partie,
 * rapport rejoué) ou {@code ok;lecture=…;outils=…;jars=…}. Rien d'autre : ni liste des programmes ni chemins.
 */
final class Gardien {
    private Gardien() {
    }

    private static final int INTERVALLE_TICKS = 300; // 15 s
    private static final long VIEUX_MS = 50_000L; // le launcher écrit toutes les 10 à 20 s
    private static final Pattern CLE = Pattern.compile("^[0-9a-f]{64}$");
    private static final Pattern OUTILS = Pattern.compile("^[a-z0-9 ._,-]{0,160}$");

    private static final byte[] CLE_JEU = lireCle();
    private static long dernierN;
    private static int ticks;

    private static byte[] lireCle() {
        String v = System.getenv("SPARTAN_GARDIEN_CLE");
        if (v == null || !CLE.matcher(v.trim()).matches()) {
            return null;
        }
        return HexFormat.of().parseHex(v.trim());
    }

    /** Appelé à chaque tick du client connecté ; {@code nouvelleConnexion} au premier. */
    static void tick(boolean nouvelleConnexion) {
        if (nouvelleConnexion) {
            ticks = 0;
            if (CLE_JEU == null) {
                envoyer("absent");
                return;
            }
        }
        if (CLE_JEU == null) {
            return;
        }
        // premier rapport après 10 s (le launcher a déjà écrit), puis toutes les 15 s
        if (++ticks == 200 || (ticks > 200 && (ticks - 200) % INTERVALLE_TICKS == 0)) {
            envoyer(lire());
        }
    }

    private static void envoyer(String detail) {
        ModMessages.sendToServer(new ClientReportPacket("gardien", detail.length() > 200 ? detail.substring(0, 200) : detail));
    }

    static String lire() {
        Path f = FMLPaths.GAMEDIR.get().resolve("spartan").resolve("gardien.txt");
        String texte;
        try (InputStream in = Files.newInputStream(f)) {
            texte = new String(in.readNBytes(4096), StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            return "muet:fichier";
        } catch (Exception e) {
            return "muet:lecture";
        }
        return juger(texte, CLE_JEU, ProcessHandle.current().pid(), System.currentTimeMillis());
    }

    /** Vérifie un rapport du launcher (séparé de la lecture pour les tests). */
    static String juger(String texte, byte[] cle, long pidJeu, long maintenant) {
        String[] lignes = texte.split("\n");
        if (lignes.length < 2) {
            return "invalide:format";
        }
        String ligne = lignes[0].trim();
        String sig = lignes[1].trim();
        byte[] attendu;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(cle, "HmacSHA256"));
            attendu = mac.doFinal(ligne.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return "invalide:hmac";
        }
        byte[] recu;
        try {
            recu = HexFormat.of().parseHex(sig);
        } catch (IllegalArgumentException e) {
            return "invalide:signature";
        }
        if (!MessageDigest.isEqual(attendu, recu)) {
            return "invalide:signature";
        }
        String[] c = ligne.split("\\|", -1);
        if (c.length != 7 || !c[0].equals("v1")) {
            return "invalide:format";
        }
        long n, t, pid;
        int jars;
        try {
            n = Long.parseLong(c[1]);
            t = Long.parseLong(c[2]);
            pid = Long.parseLong(c[3]);
            jars = Integer.parseInt(c[6]);
        } catch (NumberFormatException e) {
            return "invalide:format";
        }
        if (pid != pidJeu) {
            return "invalide:pid";
        }
        if (n < dernierN) {
            return "invalide:rejoue";
        }
        dernierN = n;
        if (maintenant - t > VIEUX_MS) {
            return "muet:vieux";
        }
        String lecture = c[4].equals("ok") ? "ok" : "impossible";
        String outils = OUTILS.matcher(c[5]).matches() ? c[5] : "";
        return "ok;lecture=" + lecture + ";outils=" + outils + ";jars=" + Math.max(0, jars);
    }

    /** Pour les tests : oublie le dernier rapport vu. */
    static void oublier() {
        dernierN = 0;
    }
}
