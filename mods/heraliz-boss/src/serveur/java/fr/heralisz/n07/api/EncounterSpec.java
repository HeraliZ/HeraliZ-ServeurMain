package fr.heralisz.n07.api;

import fr.heralisz.n07.api.scene.SkipMode;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Reglages d'un combat pilote (aucun ne touche la config globale ni le comportement autonome du boss). Construire avec
 * {@link #builder(ServerLevel)}. Par defaut : equilibrage Zombies (ZombiesBalance), pas de butin ni d'XP, statue gardee
 * 20 min, pas de musique du boss, pas de laisse (le boss reste dans l'arene), pas de terminal ni de minuteur.
 */
public final class EncounterSpec {
   public final ServerLevel level;
   /** Salle ou se bat le boss (null : position libre spawnPos / spawnYaw). */
   public final PlacedHall hall;
   public final Vec3 spawnPos;
   public final float spawnYaw;
   public final Supplier<List<ServerPlayer>> players;
   public final N07Events events;
   public final int playerCount, round;
   public final boolean papOnBossHits;
   /** PV virtuels fixes (&gt; 0) ; sinon ZombiesBalance.virtualHp(joueurs engages, manche). */
   public final int virtualHp;
   /** Les PV suivent le nombre de joueurs engages (pic des 30 dernieres secondes) plutot que playerCount fixe. */
   public final boolean hpFollowsEngaged;
   public final float hitCap, dpsCap, damageMultiplier;
   public final DamageRemap damageRemap;
   public final Predicate<Player> targetable;
   public final boolean loot, remains, music, leash, blackoutPhase3;
   public final int remainsLifeTicks;
   public final AABB arena;
   public final Consumer<Entity> marker;
   /** Passage des cinematiques d'apparition et de mort (les phases ne se passent pas). */
   public final SkipMode skipMode;

   private EncounterSpec(Builder b) {
      level = b.level;
      hall = b.hall;
      spawnPos = b.spawnPos;
      spawnYaw = b.spawnYaw;
      players = b.players;
      events = b.events != null ? b.events : new N07Events() {
      };
      playerCount = Math.max(1, b.playerCount);
      round = b.round;
      papOnBossHits = b.papOnBossHits;
      virtualHp = b.virtualHp;
      hpFollowsEngaged = b.hpFollowsEngaged;
      hitCap = b.hitCap > 0 ? b.hitCap : ZombiesBalance.hitCap();
      dpsCap = b.dpsCap > 0 ? b.dpsCap : ZombiesBalance.dpsCap(playerCount);
      damageMultiplier = b.damageMultiplier;
      damageRemap = b.damageRemap != null ? b.damageRemap : b.zombiesDamage ? ZombiesBalance.remap(b.damageScale, b.maxHit) : null;
      targetable = b.targetable != null ? b.targetable : p -> true;
      loot = b.loot;
      remains = b.remains;
      remainsLifeTicks = b.remainsLifeTicks;
      music = b.music;
      leash = b.leash;
      blackoutPhase3 = b.blackoutPhase3;
      arena = b.arena != null ? b.arena : b.hall != null ? b.hall.interior() : null;
      marker = b.marker;
      skipMode = b.skipMode;
   }

   public static Builder builder(ServerLevel level) {
      return new Builder(level);
   }

   public static final class Builder {
      private final ServerLevel level;
      private PlacedHall hall;
      private Vec3 spawnPos;
      private float spawnYaw;
      private Supplier<List<ServerPlayer>> players = List::of;
      private N07Events events;
      private int playerCount = 1, round = 20, virtualHp;
      private boolean papOnBossHits = true, hpFollowsEngaged, zombiesDamage = true;
      private float hitCap, dpsCap, damageMultiplier = 1F, damageScale = 1F, maxHit = ZombiesBalance.MAX_HIT;
      private DamageRemap damageRemap;
      private Predicate<Player> targetable;
      private boolean loot, remains = true, music, leash, blackoutPhase3 = true;
      private int remainsLifeTicks = 20 * 60 * 20;
      private AABB arena;
      private Consumer<Entity> marker;
      private SkipMode skipMode = SkipMode.MAJORITY;

      private Builder(ServerLevel level) {
         this.level = level;
      }

      /** Le boss sort de la cuve de cette salle (et l'arene = l'interieur du hall). */
      public Builder hall(PlacedHall h) {
         hall = h;
         return this;
      }

      /** Sans salle : position et lacet d'apparition. */
      public Builder spawnAt(Vec3 pos, float yaw) {
         spawnPos = pos;
         spawnYaw = yaw;
         return this;
      }

      /** Joueurs du combat (spectateurs des cinematiques, compte des PV). Relu a chaque usage. */
      public Builder players(Supplier<List<ServerPlayer>> s) {
         players = s;
         return this;
      }

      public Builder events(N07Events e) {
         events = e;
         return this;
      }

      /** Nombre de joueurs et manche atteinte : PV et plafonds par defaut (ZombiesBalance). */
      public Builder players(int count, int round) {
         this.playerCount = count;
         this.round = round;
         return this;
      }

      /** false si le jeu n'applique PAS le bonus Pack-a-Punch aux tirs sur le boss (PV x 0,4). */
      public Builder papOnBossHits(boolean on) {
         papOnBossHits = on;
         return this;
      }

      /** PV virtuels fixes (remplace le calcul par joueurs / manche). */
      public Builder virtualHp(int hp) {
         virtualHp = hp;
         return this;
      }

      /** Les PV suivent les joueurs reellement engages (au lieu du nombre donne). */
      public Builder hpFollowsEngaged(boolean on) {
         hpFollowsEngaged = on;
         return this;
      }

      public Builder hitCap(float v) {
         hitCap = v;
         return this;
      }

      public Builder dpsCap(float v) {
         dpsCap = v;
         return this;
      }

      /** Multiplicateur des degats du boss avant remappage (comme damageMultiplier de la config). */
      public Builder damageMultiplier(float v) {
         damageMultiplier = v;
         return this;
      }

      /** Degats Zombies par defaut : echelle (ex. ZombiesBalance.damageScaleForRound(manche)) et plafond par coup. */
      public Builder zombiesDamage(float scale, float maxHit) {
         zombiesDamage = true;
         damageScale = scale;
         this.maxHit = maxHit;
         return this;
      }

      /** Remappage libre des degats (remplace la table Zombies). */
      public Builder damageRemap(DamageRemap r) {
         damageRemap = r;
         return this;
      }

      /** Degats d'origine du boss (pas de remappage). */
      public Builder rawDamage() {
         zombiesDamage = false;
         damageRemap = null;
         return this;
      }

      /** Joueurs que le boss peut viser / blesser (ex. exclure les joueurs a terre). */
      public Builder targetable(Predicate<Player> p) {
         targetable = p;
         return this;
      }

      public Builder loot(boolean on) {
         loot = on;
         return this;
      }

      /** Statue de trinitite apres la mort (necessaire a l'outro) et sa duree (ticks). */
      public Builder remains(boolean on, int lifeTicks) {
         remains = on;
         remainsLifeTicks = lifeTicks;
         return this;
      }

      public Builder music(boolean on) {
         music = on;
         return this;
      }

      /** Laisse d'origine (retour + soin hors de portee) ; false par defaut : il reste dans l'arene. */
      public Builder leash(boolean on) {
         leash = on;
         return this;
      }

      public Builder arena(AABB box) {
         arena = box;
         return this;
      }

      /** Coupure de courant dans le hall pendant la cinematique de phase 3 (plafonniers eteints, sirenes seules). */
      public Builder blackoutPhase3(boolean on) {
         blackoutPhase3 = on;
         return this;
      }

      /** Appele sur chaque entite creee par le combat (boss, projectiles, statue) : ex. poser la marque du jeu. */
      public Builder marker(Consumer<Entity> m) {
         marker = m;
         return this;
      }

      public Builder skipMode(SkipMode m) {
         skipMode = m;
         return this;
      }

      public EncounterSpec build() {
         if (hall == null && spawnPos == null) throw new IllegalStateException("EncounterSpec : hall ou spawnAt requis");
         return new EncounterSpec(this);
      }
   }
}
