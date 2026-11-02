package com.Spartan.integrity;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * Preuve du Gardien (audit du 02/11, point 2) : le SERVEUR vérifie lui-même les rapports du launcher ; le jeu du joueur ne
 * fait plus que les transmettre tels quels. Java pur (aucune classe de Minecraft) : testé hors du jeu.
 *
 * <p>Chaîne de confiance, sans rien changer au site :
 * <ol>
 *   <li>à chaque lancement, le launcher (1.16.0 ou plus récent, launcher/app/assets/js/gardien.js) tire une paire de clés
 *   Ed25519 ; la clé privée ne quitte jamais sa mémoire (ni disque, ni jeu) ;</li>
 *   <li>il demande le jeton de lancement au site avec, à la place de l'empreinte du dossier mods,
 *   {@link #engagement(String, String)} = SHA-256 de « gardien-v2|empreinte des mods|clé publique » : le site signe ce
 *   texte comme d'habitude (64 caractères hexadécimaux), sans savoir ce qu'il contient ;</li>
 *   <li>le jeu envoie au serveur le jeton, l'empreinte de ses mods et la clé publique (variable SPARTAN_GARDIEN_PUB) ;
 *   le serveur, qui vérifie déjà la signature du site, recalcule l'engagement : la clé publique est alors PROUVÉE pour ce
 *   compte et ce lancement (un jeu modifié qui annonce une autre clé voit son jeton refusé) ;</li>
 *   <li>chaque rapport du launcher (ligne v2) est signé avec la clé privée et porte le dernier défi tiré par le serveur
 *   pour cette connexion (fraîcheur et lien avec la connexion) ; le serveur vérifie la signature, le numéro (jamais en
 *   arrière) et le défi.</li>
 * </ol>
 * Un jeu modifié ne peut plus fabriquer un rapport « propre » : il n'a pas la clé privée. Il peut seulement se taire
 * (alerte « Gardien muet ») ou renvoyer un vieux rapport (défi périmé : muet aussi).
 *
 * <p>Ligne v2 (launcher) : {@code v2|n|t|pid|défi|lecture|outil,outil|jars changés}, signature Ed25519 en base64url.
 * Ligne v1 (launchers 1.14 à 1.15, signée par une clé que le jeu connaît) : {@code v1|n|t|pid|lecture|outils|jars} ; le
 * serveur ne peut pas la prouver, il la lit seulement ({@link #lireSansPreuve}).
 */
public final class GardienPreuve {
    /** Clé publique Ed25519 brute (32 octets) en base64url sans remplissage. */
    public static final Pattern CLE = Pattern.compile("^[A-Za-z0-9_-]{43}$");
    /** Défi tiré par le serveur : 16 octets en hexadécimal. */
    public static final Pattern DEFI = Pattern.compile("^[0-9a-f]{32}$");
    /** Noms d'outils de la liste du launcher trouvés (jamais la liste des programmes du joueur). */
    private static final Pattern OUTILS = Pattern.compile("^[a-z0-9 ._,-]{0,300}$");
    /** En-tête DER d'une clé publique Ed25519 (SubjectPublicKeyInfo, OID 1.3.101.112). */
    private static final byte[] SPKI_ED25519 = {0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00};

    private GardienPreuve() {
    }

    /** Rapport lu. {@code defi} vaut « - » si le launcher n'en avait pas (ou ligne v1). */
    public record Rapport(int version, long n, long t, long pid, String defi, boolean lecture, String outils, int jars) {
    }

    /** Rapport lu, ou raison du refus (« signature », « format »…), jamais les deux. */
    public record Lu(Rapport rapport, String refus) {
        static Lu refus(String raison) {
            return new Lu(null, raison);
        }

        public boolean ok() {
            return rapport != null;
        }
    }

    /**
     * Empreinte signée par le site pour un launcher 1.16.0 ou plus récent : lie la clé publique du Gardien au jeton (même
     * calcul que {@code Gardien.engagement} de launcher/app/assets/js/gardien.js).
     */
    public static String engagement(String empreinteMods, String clePublique) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(
                    ("gardien-v2|" + empreinteMods + "|" + clePublique).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Clé publique Ed25519 à partir de sa forme base64url (43 caractères), ou null si elle est illisible. */
    public static PublicKey cle(String base64url) {
        if (base64url == null || !CLE.matcher(base64url).matches()) {
            return null;
        }
        try {
            byte[] brute = Base64.getUrlDecoder().decode(base64url);
            if (brute.length != 32) {
                return null;
            }
            byte[] der = new byte[SPKI_ED25519.length + 32];
            System.arraycopy(SPKI_ED25519, 0, der, 0, SPKI_ED25519.length);
            System.arraycopy(brute, 0, der, SPKI_ED25519.length, 32);
            return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(der));
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            return null;
        }
    }

    /** Vérifie la signature d'une ligne v2 avec la clé prouvée, puis la lit. */
    public static Lu verifier(PublicKey cle, String ligne, String signature) {
        if (cle == null || ligne == null || signature == null || ligne.isEmpty() || ligne.length() > 512) {
            return Lu.refus("format");
        }
        byte[] sig;
        try {
            sig = Base64.getUrlDecoder().decode(signature.trim());
        } catch (IllegalArgumentException e) {
            return Lu.refus("signature");
        }
        if (sig.length != 64) {
            return Lu.refus("signature");
        }
        try {
            Signature v = Signature.getInstance("Ed25519");
            v.initVerify(cle);
            v.update(ligne.getBytes(StandardCharsets.UTF_8));
            if (!v.verify(sig)) {
                return Lu.refus("signature");
            }
        } catch (GeneralSecurityException | RuntimeException e) {
            return Lu.refus("signature");
        }
        Lu lu = lire(ligne);
        // une ligne v1 signée par la clé v2 n'existe pas chez un launcher honnête
        return lu.ok() && lu.rapport().version() != 2 ? Lu.refus("format") : lu;
    }

    /** Lit une ligne (v1 ou v2) SANS rien prouver : ancien launcher, ou serveur sans clé des jetons. */
    public static Lu lireSansPreuve(String ligne) {
        return ligne == null || ligne.length() > 512 ? Lu.refus("format") : lire(ligne);
    }

    private static Lu lire(String ligne) {
        String[] c = ligne.split("\\|", -1);
        boolean v2 = c.length == 8 && c[0].equals("v2");
        boolean v1 = c.length == 7 && c[0].equals("v1");
        if (!v1 && !v2) {
            return Lu.refus("format");
        }
        int i = v2 ? 5 : 4; // champ « lecture »
        long n;
        long t;
        long pid;
        int jars;
        try {
            n = Long.parseLong(c[1]);
            t = Long.parseLong(c[2]);
            pid = Long.parseLong(c[3]);
            jars = Integer.parseInt(c[i + 2]);
        } catch (NumberFormatException e) {
            return Lu.refus("format");
        }
        String defi = v2 ? c[4] : "-";
        if (n < 0 || jars < 0 || (!defi.equals("-") && !DEFI.matcher(defi).matches())) {
            return Lu.refus("format");
        }
        String outils = OUTILS.matcher(c[i + 1]).matches() ? c[i + 1] : "";
        return new Lu(new Rapport(v2 ? 2 : 1, n, t, pid, defi, c[i].equals("ok"), outils, jars), null);
    }
}
