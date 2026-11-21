package fr.heralisz.camo.client;

import com.mojang.logging.LogUtils;
import fr.heralisz.camo.DessinsCopie;
import fr.heralisz.camo.HeraliszCamo;
import fr.heralisz.camo.network.DessinsPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;

/**
 * Copie des dessins des skins reçue du serveur (21/11, docs/SKINS_PROTEGES.md, étape 1), gardée en mémoire seulement :
 * jamais écrite sur le disque. À la réception (fil du réseau), les morceaux sont seulement mis bout à bout : rien n'est
 * décompressé ni compilé, le jeu continue de dessiner avec les dessins de son jar. La copie n'est ouverte (décompressée,
 * empreinte vérifiée) que sur demande du staff ({@code /camos dessins} : {@link CamoShaders#refaireDepuisServeur}), puis
 * sert de source aux programmes des skins par un {@link ResourceProvider} en mémoire ({@link #fournisseur}).
 */
public final class DessinsRecus {
   private static final Logger LOG = LogUtils.getLogger();

   /** Une copie complète (octets compressés) et ce que le serveur en annonce. */
   private record Copie(byte[] compresse, int fichiers, int brut, String empreinte, int parties) {
   }

   /** Dernière copie complète reçue (gardée pour la session ; remplacée par celle d'une connexion suivante). */
   @Nullable
   private static volatile Copie copie;

   // assemblage en cours (fil du réseau, sous verrou)
   @Nullable
   private static byte[][] morceaux;
   private static String empreinteEnCours = "";
   private static int recus;

   // copie ouverte (fil du jeu) : fichiers décompressés de la copie {@code ouverte}, null si elle est illisible
   @Nullable
   private static Copie ouverte;
   @Nullable
   private static volatile Map<ResourceLocation, byte[]> fichiers;

   /** « Pack » des fichiers de la copie : seulement la source des ressources données au compilateur (nom des erreurs). */
   private static final PackResources PACK = new PackCopie();

   private DessinsRecus() {
   }

   /** Un morceau de la copie (fil du réseau) : rangé ; la copie est prête quand tous sont là. */
   static synchronized void morceau(DessinsPacket p) {
      if (p.parties() < 1 || p.parties() > DessinsCopie.PARTIES_MAX || p.partie() < 0 || p.partie() >= p.parties()
         || p.total() < 0 || p.total() > DessinsCopie.PARTIES_MAX * DessinsCopie.TAILLE_PARTIE) {
         LOG.warn("[Camos] dessins des skins : morceau du serveur hors limites, ignoré");
         return;
      }
      if (morceaux == null || morceaux.length != p.parties() || !empreinteEnCours.equals(p.empreinte())) {
         morceaux = new byte[p.parties()][];
         empreinteEnCours = p.empreinte();
         recus = 0;
      }
      if (morceaux[p.partie()] == null) {
         morceaux[p.partie()] = p.octets();
         recus++;
      }
      if (recus < morceaux.length) {
         return;
      }
      long total = 0;
      for (byte[] m : morceaux) total += m.length;
      if (total != p.total()) {
         LOG.warn("[Camos] dessins des skins : {} octets reçus au lieu de {}, copie ignorée", total, p.total());
         morceaux = null;
         return;
      }
      byte[] tout = new byte[(int) total];
      int pos = 0;
      for (byte[] m : morceaux) {
         System.arraycopy(m, 0, tout, pos, m.length);
         pos += m.length;
      }
      int n = morceaux.length;
      morceaux = null;
      copie = new Copie(tout, p.fichiers(), p.brut(), p.empreinte(), n);
      LOG.info("[Camos] dessins des skins reçus du serveur : {} fichiers, {} Ko ({} Ko compressés, {} paquet(s)), empreinte {} ;"
            + " gardés en mémoire seulement", p.fichiers(), DessinsCopie.ko(p.brut()), DessinsCopie.ko(total), n,
         p.empreinte().length() > 12 ? p.empreinte().substring(0, 12) : p.empreinte());
   }

   /** Vrai si une copie complète a été reçue du serveur. */
   static boolean recue() {
      return copie != null;
   }

   /**
    * Fichiers de la copie (fil du jeu) : décompressés et vérifiés (empreinte annoncée par le serveur) une fois par copie.
    * Null sans copie, ou si elle est illisible (journal).
    */
   @Nullable
   static Map<ResourceLocation, byte[]> fichiers() {
      Copie c = copie;
      if (c == null) {
         return null;
      }
      if (c == ouverte) {
         return fichiers;
      }
      ouverte = c;
      fichiers = null;
      try {
         SortedMap<String, byte[]> f = DessinsCopie.deballer(c.compresse());
         if (!DessinsCopie.empreinte(f).equals(c.empreinte())) {
            throw new IOException("empreinte différente de celle annoncée par le serveur");
         }
         Map<ResourceLocation, byte[]> m = new HashMap<>();
         for (Map.Entry<String, byte[]> e : f.entrySet()) {
            ResourceLocation rl = ResourceLocation.tryParse(e.getKey());
            if (rl != null) m.put(rl, e.getValue());
         }
         fichiers = Map.copyOf(m);
      } catch (IOException | RuntimeException ex) {
         LOG.error("[Camos] copie des dessins des skins illisible ({}) : les dessins du jeu restent en place", ex.toString());
      }
      return fichiers;
   }

   /**
    * Ressources pour la compilation des programmes des skins : nos dessins viennent seulement de la copie (comme à
    * l'étape 2, où ils ne seront plus dans le jar) ; le reste (includes de Minecraft : fog.glsl, light.glsl) vient du jeu.
    */
   static ResourceProvider fournisseur(Map<ResourceLocation, byte[]> f, ResourceProvider jeu) {
      return rl -> {
         byte[] octets = f.get(rl);
         if (octets != null) {
            return Optional.of(new Resource(PACK, () -> new ByteArrayInputStream(octets)));
         }
         return aNous(rl) ? Optional.empty() : jeu.getResource(rl);
      };
   }

   /** Ressource que seule la copie du serveur doit donner. */
   static boolean aNous(ResourceLocation rl) {
      return rl.getNamespace().equals(HeraliszCamo.MODID) && rl.getPath().startsWith("shaders/")
         || rl.getNamespace().equals("minecraft") && rl.getPath().equals("shaders/include/heralisz_camo.glsl");
   }

   /** Les fichiers de la copie ouverte vus comme un pack (jamais ajouté aux packs du jeu). */
   private static final class PackCopie implements PackResources {
      @Nullable
      @Override
      public IoSupplier<InputStream> getRootResource(String... chemin) {
         return null;
      }

      @Nullable
      @Override
      public IoSupplier<InputStream> getResource(PackType type, ResourceLocation rl) {
         Map<ResourceLocation, byte[]> f = fichiers;
         byte[] octets = type == PackType.CLIENT_RESOURCES && f != null ? f.get(rl) : null;
         return octets == null ? null : () -> new ByteArrayInputStream(octets);
      }

      @Override
      public void listResources(PackType type, String espace, String chemin, PackResources.ResourceOutput sortie) {
         Map<ResourceLocation, byte[]> f = fichiers;
         if (type != PackType.CLIENT_RESOURCES || f == null) {
            return;
         }
         for (Map.Entry<ResourceLocation, byte[]> e : f.entrySet()) {
            ResourceLocation rl = e.getKey();
            byte[] octets = e.getValue();
            if (rl.getNamespace().equals(espace) && rl.getPath().startsWith(chemin + "/")) {
               sortie.accept(rl, () -> new ByteArrayInputStream(octets));
            }
         }
      }

      @Override
      public Set<String> getNamespaces(PackType type) {
         Map<ResourceLocation, byte[]> f = fichiers;
         Set<String> espaces = new HashSet<>();
         if (type == PackType.CLIENT_RESOURCES && f != null) {
            for (ResourceLocation rl : f.keySet()) espaces.add(rl.getNamespace());
         }
         return espaces;
      }

      @Nullable
      @Override
      public <T> T getMetadataSection(MetadataSectionSerializer<T> lecteur) {
         return null;
      }

      @Override
      public String packId() {
         return "heralisz_camo (copie du serveur)";
      }

      @Override
      public void close() {
      }
   }
}
