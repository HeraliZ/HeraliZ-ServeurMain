package fr.heraliz.noyau.serveur.perf;

import fr.heraliz.noyau.NoyauConfig;
import fr.heraliz.noyau.api.EcrituresFond;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Fichiers {@code data/} des mondes écrits en fond (19/11, audit des lenteurs, point C2) : à chaque sauvegarde automatique,
 * chaque donnée de monde ({@code SavedData}) sans écriture à elle était relevée, compressée ET écrite sur le fil du serveur,
 * ouverture du fichier comprise (relevé à 01:36:00 le 19/11 : {@code SavedData.save → NbtIo.writeCompressed →
 * FileOutputStream.open0}) : une vingtaine de nos données (territoires, corps, recycleurs, récolte, casino, quêtes...), celles
 * de Minecraft et des mods des autres auteurs.
 * <p>Ici ({@code DonneesEnFondMixin}, à la place du {@code NbtIo.writeCompressed} de {@code SavedData.save(File)}) : le
 * relevé reste celui de Minecraft, sur le fil du serveur ; il est COPIÉ tout de suite (une donnée qui glisse un de ses
 * propres NBT dans le relevé peut le changer ensuite : la copie, elle, n'est à personne) ; la compression et l'écriture
 * (fichier à côté puis remplacement : un plantage ne laisse jamais un fichier abîmé) partent sur la voie « donnees » de
 * l'écrivain en fond du noyau, dans l'ordre, le plus récent relevé d'un fichier seulement. Écriture ratée : la donnée
 * redevient « à sauver » (Minecraft, lui, l'oubliait). Lecture d'un fichier encore en file ({@code DonneesLectureMixin},
 * monde rechargé) : attendue. Pendant l'arrêt du serveur, ou hors du fil du serveur : tout de suite, après les écritures en
 * file de ce fichier. Réglage : {@code [perf] donnees_en_fond}.
 */
public final class DonneesFond {
   private DonneesFond() {
   }

   private static final EcrituresFond.Voie VOIE = EcrituresFond.voie("donnees");

   /**
    * À la place de {@code NbtIo.writeCompressed(contenu, fichier)} dans {@code SavedData.save(File)} : vrai si l'écriture
    * part en fond, faux si Minecraft doit écrire tout de suite (fait après les écritures en file de ce fichier).
    */
   public static boolean garder(SavedData donnee, CompoundTag contenu, File fichier) {
      Path cible = fichier.toPath();
      MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
      if (!NoyauConfig.donneesEnFond || srv == null || !srv.isRunning() || !srv.isSameThread() || EcrituresFond.arret()) {
         VOIE.attendre(cible);   // jamais deux versions du même fichier dans le désordre
         return false;
      }
      CompoundTag copie = contenu.copy();
      VOIE.ecrire(cible, () -> ecrire(copie, cible), () -> srv.execute(donnee::setDirty));
      Allege.donneesEnFond++;
      return true;
   }

   /** Avant la lecture d'un fichier data/ : l'écriture encore en file de ce fichier est attendue. */
   public static void avantLecture(File fichier) {
      VOIE.attendre(fichier.toPath());
   }

   /** Fil d'écriture : fichier à côté, puis remplacement. */
   private static void ecrire(CompoundTag contenu, Path cible) throws IOException {
      Path tmp = cible.resolveSibling(cible.getFileName() + ".tmp");
      NbtIo.writeCompressed(contenu, tmp.toFile());
      try {
         Files.move(tmp, cible, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException ex) {
         Files.move(tmp, cible, StandardCopyOption.REPLACE_EXISTING);
      }
   }
}
