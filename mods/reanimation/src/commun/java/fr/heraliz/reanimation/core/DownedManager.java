package fr.heraliz.reanimation.core;

import javax.annotation.Nullable;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Face commune de l'état « à terre » (docs/METHODE_CLIENT_SERVEUR.md, piège n°17). La logique (saignement, fouille,
 * réanimation, exécutions, sauvegarde) est dans {@code DownedManagerServeur} (partie serveur), qui se branche ici par
 * {@link Moteur} au chargement.
 * <p>
 * Mêmes noms et mêmes signatures qu'avant : d'autres mods les appellent par réflexion, des deux côtés (heralisz_matchs
 * résout {@code get}, {@code kill}, {@code reviveNow} et {@link DownedState} aussi sur le client ; ses bancs d'essai
 * appellent {@code startSearch} et {@code giveUp}). Sans partie serveur (monde client, ou jar -client en solo), chaque
 * méthode rend ce que l'original rendait côté client : personne n'est à terre.
 */
public final class DownedManager {
   public static final ResourceKey<DamageType> BLEED_OUT = damage("bleed_out");
   public static final ResourceKey<DamageType> GIVE_UP = damage("give_up");
   public static final ResourceKey<DamageType> DISCONNECT = damage("disconnect");
   public static final ResourceKey<DamageType> EXECUTION = damage("execution");

   /** Branché par {@code DownedManagerServeur.installer()} ; null sans partie serveur. */
   @Nullable
   static volatile Moteur moteur;

   /** Ce que la partie serveur fournit (corps d'origine dans DownedManagerServeur). */
   interface Moteur {
      @Nullable
      DownedState get(Entity e);

      int flags(Entity e);

      void kill(DownedState s, ResourceKey<DamageType> type);

      boolean reviveNow(LivingEntity e);

      void giveUp(LivingEntity e);

      void startSearch(LivingEntity actor, LivingEntity target);

      void startRevive(LivingEntity actor, LivingEntity target);

      void searchClosed(LivingEntity target, Player player);

      boolean isSearching(Player player, LivingEntity target);
   }

   private DownedManager() {
   }

   private static ResourceKey<DamageType> damage(String name) {
      return ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation("reanimation", name));
   }

   @Nullable
   public static DownedState get(Entity e) {
      Moteur m = moteur;
      return m == null ? null : m.get(e);
   }

   public static boolean isDowned(Entity e) {
      return get(e) != null;
   }

   static int flags(Entity e) {
      Moteur m = moteur;
      return m == null ? 0 : m.flags(e);
   }

   public static void kill(DownedState s, ResourceKey<DamageType> type) {
      Moteur m = moteur;
      if (m != null) {
         m.kill(s, type);
      }
   }

   public static boolean reviveNow(LivingEntity e) {
      Moteur m = moteur;
      return m != null && m.reviveNow(e);
   }

   public static void giveUp(LivingEntity e) {
      Moteur m = moteur;
      if (m != null) {
         m.giveUp(e);
      }
   }

   public static void startSearch(LivingEntity actor, LivingEntity target) {
      Moteur m = moteur;
      if (m != null) {
         m.startSearch(actor, target);
      }
   }

   public static void startRevive(LivingEntity actor, LivingEntity target) {
      Moteur m = moteur;
      if (m != null) {
         m.startRevive(actor, target);
      }
   }

   public static void searchClosed(LivingEntity target, Player player) {
      Moteur m = moteur;
      if (m != null) {
         m.searchClosed(target, player);
      }
   }

   public static boolean isSearching(Player player, LivingEntity target) {
      Moteur m = moteur;
      return m != null && m.isSearching(player, target);
   }
}
