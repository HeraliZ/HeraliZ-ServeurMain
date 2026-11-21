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
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
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
 * Copie des dessins des skins reçue du serveur (21/11, docs/SKINS_PROTEGES.md), gardée en mémoire seulement : jamais écrite
 * sur le disque. À la réception (fil du réseau), les morceaux sont seulement mis bout à bout ; quand la copie est complète,
 * le fil du rendu fabrique les programmes des skins depuis elle ({@link CamoShaders}, étape 2 : le jar des joueurs n'a plus
 * aucun dessin). Ouverture (décompression, empreinte annoncée par le serveur vérifiée) une seule fois par copie, et pas de
 * nouveau à une reconnexion qui apporte la même copie. Source des programmes : un {@link ResourceProvider} en mémoire
 * ({@link #fournisseur}). Rien reçu {@value #ATTENTE_MS} ms après l'arrivée sur un serveur : une ligne au journal (les skins
 * restent sans effet, ou gardent les dessins reçus plus tôt dans la session).
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

   // connexion en cours (fil du jeu) : heure d'arrivée sur le serveur, absence de copie déjà notée ; copie reçue pendant
   // cette connexion (fil du réseau, remis à faux au départ : la copie peut arriver avant que le jeu voie l'arrivée)
   private static long arriveA = -1L;
   private static boolean absenceNotee;
   private static volatile boolean recueCetteConnexion;
   /** Attente de la copie après l'arrivée sur un serveur avant de noter son absence au journal (ms). */
   static final long ATTENTE_MS = 30_000L;

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
      recueCetteConnexion = true;
      LOG.info("[Camos] dessins des skins reçus du serveur : {} fichiers, {} Ko ({} Ko compressés, {} paquet(s)), empreinte {} ;"
            + " gardés en mémoire seulement", p.fichiers(), DessinsCopie.ko(p.brut()), DessinsCopie.ko(total), n,
         p.empreinte().length() > 12 ? p.empreinte().substring(0, 12) : p.empreinte());
      // étape 2 : les programmes des skins sont fabriqués depuis cette copie, sur le fil du rendu
      CamoShaders.copieRecue();
   }

   /** Vrai si une copie complète a été reçue du serveur. */
   static boolean recue() {
      return copie != null;
   }

   /** Empreinte que le serveur annonce pour la dernière copie complète reçue ; null : aucune. */
   @Nullable
   static String empreinteAnnoncee() {
      Copie c = copie;
      return c == null ? null : c.empreinte();
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
      // reconnexion au même serveur : même copie annoncée, déjà ouverte et vérifiée : rien à refaire
      if (ouverte != null && fichiers != null && c.empreinte().equals(ouverte.empreinte())) {
         ouverte = c;
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
         LOG.error("[Camos] copie des dessins des skins illisible ({}) : rien n'est refait (skins sans effet, ou dessins reçus"
            + " plus tôt gardés)", ex.toString());
      }
      return fichiers;
   }

   /** Arrivée sur un serveur (fil du jeu, bus de Forge, branché par PartieClient). */
   static void arrivee(ClientPlayerNetworkEvent.LoggingIn e) {
      arriveA = System.currentTimeMillis();
      absenceNotee = false;
   }

   /** Départ du serveur (fil du jeu) : la connexion suivante doit recevoir sa propre copie. */
   static void depart(ClientPlayerNetworkEvent.LoggingOut e) {
      arriveA = -1L;
      recueCetteConnexion = false;
   }

   /** Chaque tick du jeu : {@value #ATTENTE_MS} ms après l'arrivée sans copie reçue, une ligne au journal (une fois). */
   static void tick(TickEvent.ClientTickEvent e) {
      if (e.phase != TickEvent.Phase.END || arriveA < 0L || absenceNotee || recueCetteConnexion
         || System.currentTimeMillis() - arriveA < ATTENTE_MS) {
         return;
      }
      absenceNotee = true;
      LOG.warn("[Camos] aucun dessin des skins reçu de ce serveur {} s après l'arrivée : {}", ATTENTE_MS / 1000L,
         copie == null ? "les skins s'affichent sans effet" : "les skins gardent les dessins reçus plus tôt dans la session");
   }

   /**
    * Ressources pour la compilation des programmes des skins : nos dessins viennent seulement de la copie (étape 2 : ils ne
    * sont plus dans le jar ; un pack de ressources ne peut pas les remplacer) ; le reste (includes de Minecraft : fog.glsl,
    * light.glsl) vient du jeu.
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
