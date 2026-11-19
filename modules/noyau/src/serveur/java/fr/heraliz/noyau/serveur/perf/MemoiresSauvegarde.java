package fr.heraliz.noyau.serveur.perf;

import com.mojang.serialization.DataResult;
import fr.heraliz.noyau.NoyauConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Ce que la sauvegarde de level.dat refaisait pour rien (19/11, audit des lenteurs, point C2 ; F2 de l'audit des réglages) :
 * à chaque sauvegarde automatique, sur le fil du serveur,
 * <ul>
 *    <li>Forge refabriquait la partie « fml » (liste des mods et registres : des dizaines de milliers d'identifiants, mis en
 *    NBT un par un ; {@code InstantaneRegistresMixin} ne gardait que l'instantané, pas sa mise en NBT) :
 *    {@code FmlEnMemoireMixin} la garde après la première sauvegarde et la resert telle quelle ;</li>
 *    <li>Minecraft réencodait les réglages de génération des mondes ({@code WorldGenSettings.encode}, codecs de toutes les
 *    dimensions) : {@code ReglagesGenerationMixin} garde le résultat tant que ses entrées sont les mêmes objets (mêmes
 *    options, même registre des dimensions, figé au démarrage), et {@link LevelDatFond} ne le recopie plus.</li>
 * </ul>
 * Rien de tout ça ne change pendant que le serveur tourne : même contenu de level.dat. Gardé pour le serveur en cours
 * seulement (jeu solo : un serveur par monde ouvert). Personne ne modifie ces NBT après leur fabrication : ils ne sont que
 * lus (et écrits en fond par {@link LevelDatFond}). Réglage : {@code [perf] registres_en_memoire}. Fil du serveur.
 */
public final class MemoiresSauvegarde {
   private MemoiresSauvegarde() {
   }

   private static Tag fml;
   private static Object fmlServeur;

   private static DataResult<?> reglages;
   private static Object reglagesOptions, reglagesDimensions, reglagesServeur;
   /** Le NBT des réglages de génération gardé (le même objet à chaque sauvegarde), ou null. */
   @Nullable
   private static Tag reglagesTag;

   private static boolean actif(@Nullable MinecraftServer srv) {
      return NoyauConfig.registresEnMemoire && srv != null && srv.isSameThread();
   }

   /** La partie « fml » gardée pour ce serveur, ou null (Forge la fabrique). */
   @Nullable
   public static Tag fml() {
      MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
      if (!actif(srv) || fml == null || fmlServeur != srv) return null;
      Allege.fmlResservis++;
      return fml;
   }

   /** Forge vient de fabriquer la partie « fml » de {@code niveau} : gardée pour les sauvegardes suivantes. */
   public static void garderFml(CompoundTag niveau) {
      MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
      Tag t = niveau.get("fml");
      if (actif(srv) && t != null) {
         fml = t;
         fmlServeur = srv;
      }
   }

   /**
    * Réglages de génération : le résultat gardé si les entrées sont les mêmes objets que la dernière fois, sinon celui que
    * {@code encoder} fabrique (et qui est gardé). {@code dimensions} : le registre des dimensions (ou l'objet qui le porte).
    */
   @SuppressWarnings("unchecked")
   public static <T> DataResult<T> reglages(Object options, Object dimensions, java.util.function.Supplier<DataResult<T>> encoder) {
      MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
      if (!actif(srv)) return encoder.get();
      if (reglages != null && reglagesServeur == srv && reglagesOptions == options && reglagesDimensions == dimensions) {
         Allege.reglagesResservis++;
         return (DataResult<T>) reglages;
      }
      DataResult<T> r = encoder.get();
      Optional<T> valeur = r.result();
      if (valeur.isPresent() && valeur.get() instanceof Tag t) {
         reglages = r;
         reglagesOptions = options;
         reglagesDimensions = dimensions;
         reglagesServeur = srv;
         reglagesTag = t;
      }
      return r;
   }

   /** Le NBT des réglages de génération gardé (resservi tel quel : {@link LevelDatFond} ne le recopie pas). */
   @Nullable
   public static Tag reglagesTag() {
      return reglagesTag;
   }

   /** Arrêt du serveur : rien n'est gardé pour le suivant. */
   public static void oublier() {
      fml = null;
      fmlServeur = null;
      reglages = null;
      reglagesOptions = reglagesDimensions = reglagesServeur = null;
      reglagesTag = null;
   }
}
