package fr.heralisz.camo.serveur;

import com.mojang.logging.LogUtils;
import fr.heralisz.camo.DessinsCopie;
import fr.heralisz.camo.HeraliszCamo;
import fr.heralisz.camo.network.CamoNetwork;
import fr.heralisz.camo.network.DessinsBilanPacket;
import fr.heralisz.camo.network.DessinsPacket;
import fr.heralisz.camo.network.DessinsRefairePacket;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Dessins des skins protégés, étape 1 (21/11, docs/SKINS_PROTEGES.md) : le serveur a sa copie des sources des programmes
 * des skins (camos, effets 3D, finitions des couteaux), mise dans son jar par la fabrication ({@link DessinsCopie#DOSSIER}).
 * Lue et compressée une seule fois, au premier démarrage ; envoyée à chaque joueur à sa connexion (les mêmes octets pour
 * tous, en un ou quelques paquets). Par défaut le jeu n'en fait rien : il dessine avec les dessins de son jar.
 * {@code /camos dessins} (staff) demande au jeu de ce joueur de refaire ses programmes depuis la copie reçue (essai de
 * l'étape 2). Jamais les sources dans le journal : seulement les nombres, les tailles et l'empreinte.
 */
final class DessinsServeur {
   private static final Logger LOG = LogUtils.getLogger();

   /** Copie prête à l'envoi : morceaux compressés et ce qu'on en annonce au jeu. */
   private record Copie(List<byte[]> parties, int total, int fichiers, int brut, String empreinte) {
   }

   /** Null : pas encore lue, ou absente ou illisible dans le jar (journal). */
   @Nullable
   private static volatile Copie copie;
   private static boolean lue;
   /** Joueurs à qui {@code /camos dessins} a été envoyé, en attente de leur résultat (fil du serveur). */
   private static final Set<UUID> EN_ATTENTE = new HashSet<>();

   private DessinsServeur() {
   }

   /** Démarrage du serveur : lecture des fichiers de la copie dans le jar et compression, une seule fois par programme. */
   static void charger() {
      if (lue) {
         return;
      }
      lue = true;
      long debut = System.nanoTime();
      try {
         IModFileInfo info = ModList.get().getModFileById(HeraliszCamo.MODID);
         Path racine = info == null ? null : info.getFile().findResource(DessinsCopie.DOSSIER);
         if (racine == null || !Files.isDirectory(racine)) {
            LOG.warn("[Camos] dessins des skins absents du jar ({}) : rien ne sera envoyé aux joueurs", DessinsCopie.DOSSIER);
            return;
         }
         SortedMap<String, byte[]> fichiers = new TreeMap<>();
         long brut = 0;
         try (Stream<Path> tout = Files.walk(racine)) {
            for (Path f : tout.filter(Files::isRegularFile).toList()) {
               // <espace>/<chemin> -> « espace:chemin », le nom de la ressource chez le joueur
               String rel = racine.relativize(f).toString().replace('\\', '/');
               int i = rel.indexOf('/');
               if (i <= 0) {
                  continue;
               }
               byte[] octets = Files.readAllBytes(f);
               fichiers.put(rel.substring(0, i) + ":" + rel.substring(i + 1), octets);
               brut += octets.length;
            }
         }
         if (fichiers.isEmpty()) {
            LOG.warn("[Camos] dessins des skins : dossier {} vide dans le jar, rien ne sera envoyé aux joueurs", DessinsCopie.DOSSIER);
            return;
         }
         byte[] compresse = DessinsCopie.emballer(fichiers);
         List<byte[]> parties = DessinsCopie.decouper(compresse);
         if (parties.size() > DessinsCopie.PARTIES_MAX) {
            LOG.error("[Camos] dessins des skins trop gros ({} Ko compressés) : rien ne sera envoyé aux joueurs", DessinsCopie.ko(compresse.length));
            return;
         }
         String empreinte = DessinsCopie.empreinte(fichiers);
         copie = new Copie(List.copyOf(parties), compresse.length, fichiers.size(), (int) brut, empreinte);
         LOG.info("[Camos] dessins des skins : {} fichiers ({} Ko) compressés en {} Ko, {} paquet(s) par joueur, empreinte {}, {} ms",
            fichiers.size(), DessinsCopie.ko(brut), DessinsCopie.ko(compresse.length), parties.size(), empreinte.substring(0, 12),
            (System.nanoTime() - debut) / 1_000_000L);
      } catch (IOException | RuntimeException ex) {
         LOG.error("[Camos] dessins des skins illisibles dans le jar : rien ne sera envoyé aux joueurs", ex);
      }
   }

   /** Connexion d'un joueur : la copie entière (les mêmes octets pour tous, rien n'est refait). */
   static void envoyer(ServerPlayer p) {
      Copie c = copie;
      if (c == null || p instanceof FakePlayer) {
         return;
      }
      int n = c.parties().size();
      for (int i = 0; i < n; i++) {
         CamoNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p),
            new DessinsPacket(i, n, c.total(), c.fichiers(), c.brut(), c.empreinte(), c.parties().get(i)));
      }
      LOG.info("[Camos] dessins des skins envoyés à {} : {} Ko en {} paquet(s)", p.getGameProfile().getName(), DessinsCopie.ko(c.total()), n);
   }

   /** {@code /camos dessins} : le jeu de ce joueur refait ses programmes des skins depuis la copie reçue à sa connexion. */
   static int refaire(CommandSourceStack source, ServerPlayer p) {
      if (copie == null) {
         source.sendFailure(Component.translatableWithFallback("heralisz_camo.commande.dessins.absents",
            "Le serveur n'a pas la copie des dessins des skins : voir son journal."));
         return 0;
      }
      EN_ATTENTE.add(p.getUUID());
      CamoNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new DessinsRefairePacket());
      LOG.info("[Camos] /camos dessins : le jeu de {} refait ses programmes des skins depuis la copie du serveur", p.getGameProfile().getName());
      source.sendSuccess(() -> Component.translatableWithFallback("heralisz_camo.commande.dessins.demande",
         "Ton jeu refait les dessins des skins depuis la copie reçue du serveur : un bandeau donnera le résultat.")
         .withStyle(ChatFormatting.GRAY), false);
      return 1;
   }

   /** Résultat de {@code /camos dessins} chez le joueur, noté seulement s'il répond à une demande du serveur. */
   static void bilan(CamoNetwork.Recu<DessinsBilanPacket> r) {
      ServerPlayer p = r.joueur();
      if (p == null || !EN_ATTENTE.remove(p.getUUID())) {
         return;
      }
      DessinsBilanPacket b = r.paquet();
      String nom = p.getGameProfile().getName();
      if (b.etat() == DessinsBilanPacket.FAIT) {
         LOG.info("[Camos] /camos dessins chez {} : {} programmes prêts, {} en échec, {} ms", nom,
            Math.max(0, Math.min(b.prets(), 10_000)), Math.max(0, Math.min(b.echecs(), 10_000)), Math.max(0, b.ms()));
      } else {
         LOG.warn("[Camos] /camos dessins chez {} : {}, ses dessins du jeu restent en place", nom,
            b.etat() == DessinsBilanPacket.RIEN ? "aucune copie reçue" : "copie illisible chez lui");
      }
   }

   /** Déconnexion ou arrêt : plus de demande en attente pour ce joueur. */
   static void oublier(UUID joueur) {
      EN_ATTENTE.remove(joueur);
   }

   static void vider() {
      EN_ATTENTE.clear();
   }
}
