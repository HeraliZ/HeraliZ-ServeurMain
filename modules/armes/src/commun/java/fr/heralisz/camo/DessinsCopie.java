package fr.heralisz.camo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * Copie des dessins des skins que le serveur envoie aux joueurs (21/11, docs/SKINS_PROTEGES.md, étape 1) : les sources des
 * programmes des camos, des effets 3D et des finitions des couteaux, mises dans les jars -serveur et -complet sous
 * {@value #DOSSIER} par la fabrication (modules/armes/build.gradle). Format commun aux deux côtés : les fichiers
 * (« espace:chemin » → octets) dans l'ordre du tri, compressés (Deflate), découpés en morceaux de {@value #TAILLE_PARTIE}
 * octets au plus (un paquet du serveur vers un joueur ne doit pas dépasser 1 Mo). L'empreinte (SHA-256 des noms et des
 * octets) annoncée par le serveur est vérifiée par le jeu quand il ouvre la copie.
 */
public final class DessinsCopie {
   /** Dossier de la copie dans les jars -serveur et -complet (hors des assets : jamais lu comme une ressource du jeu). */
   public static final String DOSSIER = "heralisz_camo_dessins";
   /** Taille la plus grande d'un morceau (octets compressés) : sous la limite d'un paquet vers un joueur (1 Mo). */
   public static final int TAILLE_PARTIE = 900_000;
   /** Morceaux au plus par copie. */
   public static final int PARTIES_MAX = 32;
   /** Garde-fous de la lecture : nombre de fichiers et octets une fois décompressés. */
   public static final int FICHIERS_MAX = 4096, OCTETS_MAX = 32 * 1024 * 1024;
   /** Numéro du format (premier octet décompressé). */
   private static final int FORMAT = 1;

   private DessinsCopie() {
   }

   /** Fichiers compressés en un seul bloc (ordre du tri des noms). */
   public static byte[] emballer(SortedMap<String, byte[]> fichiers) throws IOException {
      ByteArrayOutputStream sortie = new ByteArrayOutputStream();
      Deflater d = new Deflater(Deflater.BEST_COMPRESSION);
      try {
         try (DataOutputStream out = new DataOutputStream(new DeflaterOutputStream(sortie, d))) {
            out.writeByte(FORMAT);
            out.writeInt(fichiers.size());
            for (Map.Entry<String, byte[]> e : fichiers.entrySet()) {
               out.writeUTF(e.getKey());
               out.writeInt(e.getValue().length);
               out.write(e.getValue());
            }
         }
      } finally {
         d.end();
      }
      return sortie.toByteArray();
   }

   /** Lit un bloc fait par {@link #emballer} ; IOException s'il est abîmé ou dépasse les garde-fous. */
   public static SortedMap<String, byte[]> deballer(byte[] compresse) throws IOException {
      SortedMap<String, byte[]> fichiers = new TreeMap<>();
      try (DataInputStream in = new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(compresse)))) {
         if (in.readUnsignedByte() != FORMAT) {
            throw new IOException("format inconnu");
         }
         int n = in.readInt();
         if (n < 0 || n > FICHIERS_MAX) {
            throw new IOException("nombre de fichiers hors limites : " + n);
         }
         long total = 0;
         for (int i = 0; i < n; i++) {
            String nom = in.readUTF();
            int taille = in.readInt();
            total += taille;
            if (taille < 0 || total > OCTETS_MAX) {
               throw new IOException("copie trop grosse");
            }
            byte[] octets = new byte[taille];
            in.readFully(octets);
            fichiers.put(nom, octets);
         }
      }
      return fichiers;
   }

   /** Empreinte SHA-256 (hexadécimal) : noms, tailles et octets des fichiers, dans l'ordre du tri. */
   public static String empreinte(SortedMap<String, byte[]> fichiers) {
      try {
         MessageDigest md = MessageDigest.getInstance("SHA-256");
         for (Map.Entry<String, byte[]> e : fichiers.entrySet()) {
            byte[] nom = e.getKey().getBytes(StandardCharsets.UTF_8);
            md.update(ByteBuffer.allocate(8).putInt(nom.length).putInt(e.getValue().length).array());
            md.update(nom);
            md.update(e.getValue());
         }
         return HexFormat.of().formatHex(md.digest());
      } catch (NoSuchAlgorithmException ex) {
         throw new IllegalStateException(ex);
      }
   }

   /** Morceaux de {@value #TAILLE_PARTIE} octets au plus (au moins un). */
   public static List<byte[]> decouper(byte[] compresse) {
      List<byte[]> morceaux = new ArrayList<>();
      for (int i = 0; i < compresse.length; i += TAILLE_PARTIE) {
         morceaux.add(Arrays.copyOfRange(compresse, i, Math.min(compresse.length, i + TAILLE_PARTIE)));
      }
      if (morceaux.isEmpty()) {
         morceaux.add(new byte[0]);
      }
      return morceaux;
   }

   /** Taille lisible pour le journal (Ko arrondis au-dessus). */
   public static long ko(long octets) {
      return (octets + 1023) / 1024;
   }
}
