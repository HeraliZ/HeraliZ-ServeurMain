package fr.heralisz.n07.dungeon;

import fr.heralisz.n07.HeraliszN07;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;

import java.util.Locale;

/**
 * Les donjons d'invocation : un par boss. Chacun a son sanctuaire (l'objet du rituel), son objet d'invocation, sa structure
 * (data/heralisz_n07/structures/dungeon_&lt;id&gt;.nbt) et le déroulé de son rituel, en ticks comptés depuis l'arrivée du joueur
 * devant le sanctuaire.
 */
public enum DungeonKind {
   /** Laboratoire du Secteur 7 : terminal à carte devant l'îlot où le Sujet N-07 flotte dans sa cuve de confinement ; la carte
    * lance les bobines, qui tournent de plus en plus vite et frappent la cuve d'arcs électriques jusqu'à ce qu'il se réveille. */
   N07("n07", "shrine_n07", true, 1.3, 6.0, 12, 24, 100, 26, 1.0F, 6);

   /** Argument de la commande et nom de la structure. */
   public final String id;
   /** Modèle / animations / textures du sanctuaire (geo/block/&lt;model&gt;.geo.json). */
   public final String model;
   /** true : le boss dort sur place (statue figée) ; false : il apparaît à la fin du rituel. */
   public final boolean dormant;
   /** Distance (blocs) du joueur au centre du sanctuaire pendant le rituel. */
   public final double stand;
   /** Distance par défaut du boss derrière le sanctuaire (si la structure ne la précise pas). */
   public final double bossDist;
   /** L'objet entre dans le sanctuaire (il est consommé). */
   public final int insert;
   /** Le sanctuaire accepte (écran vert, flamme, runes, pouls...). */
   public final int grant;
   /** Le boss se réveille. */
   public final int end;
   /** Début de l'éveil du donjon (blocs rituels allumés de proche en proche). */
   public final int lights;
   /** Ticks par bloc de distance pour cet éveil. */
   public final float lightSpeed;
   /** Portée du rendu du sanctuaire (blocs). */
   public final int renderRadius;

   DungeonKind(String id, String model, boolean dormant, double stand, double bossDist, int insert, int grant, int end, int lights,
               float lightSpeed, int renderRadius) {
      this.id = id;
      this.model = model;
      this.dormant = dormant;
      this.stand = stand;
      this.bossDist = bossDist;
      this.insert = insert;
      this.grant = grant;
      this.end = end;
      this.lights = lights;
      this.lightSpeed = lightSpeed;
      this.renderRadius = renderRadius;
   }

   public EntityType<? extends Mob> bossType() {
      return switch (this) {
         case N07 -> HeraliszN07.SUJET_N07.get();
      };
   }

   public Item item() {
      return switch (this) {
         case N07 -> DungeonRegistry.N07_KEYCARD.get();
      };
   }

   public String structure() {
      return "dungeon_" + name().toLowerCase(Locale.ROOT);
   }

   public static DungeonKind byId(String id) {
      for (DungeonKind k : values()) if (k.id.equalsIgnoreCase(id) || k.name().equalsIgnoreCase(id)) return k;
      return null;
   }
}
